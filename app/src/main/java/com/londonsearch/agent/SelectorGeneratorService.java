package com.londonsearch.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tier-2 extraction: asks Claude Sonnet to generate CSS selectors for a site,
 * then validates them via {@link CssSelectorExtractor} before returning.
 *
 * <p>The validate-before-overwrite gate ensures bad generations are rejected,
 * preserving any existing working selectors for the site.
 */
@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "bedrock")
public class SelectorGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SelectorGeneratorService.class);

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String SELECTOR_PROMPT = """
            You are an expert web scraping engineer. I need CSS selectors to extract property listings from "%s".

            Below are %d sample listing card(s) extracted from the page. Each card is wrapped in a parent element
            whose tag and classes are shown. Generate CSS selectors that work WITHIN each card element.

            Return ONLY a JSON object (no markdown, no explanation) with these keys:
              listingContainer — the CSS selector for the card wrapper (REQUIRED). I've identified it as "%s" — confirm or correct.
              address          — selector for the street address text
              price            — selector for the rental price text
              bedrooms         — selector for bedroom count
              bathrooms        — selector for bathroom count
              sqft             — selector for square footage
              propertyType     — selector for property type (Flat, House, etc.)
              furnishing       — selector for furnishing status
              description      — selector for description text
              listingUrl       — selector for the listing link, with " @href" suffix (e.g. "a.link @href")
              imageUrl         — selector for property image, with " @src" suffix (e.g. "img.photo @src")
              floorPlanUrl     — selector for floor plan, with " @href" or " @src" suffix
              availableFrom    — selector for availability date
              agentName        — selector for agent name
              agentPhone       — selector for agent phone
              agentEmail       — selector for agent email

            Rules:
            - Use ONLY CSS classes and attributes that you can see in the HTML samples below.
            - DO NOT guess or invent class names. Every selector must appear in the sample HTML.
            - Omit keys where no reliable selector exists.
            - The " @attr" suffix means extract an HTML attribute instead of text.

            Sample listing cards from the page:

            %s
            """;

    private final AnthropicClient anthropicClient;
    private final CssSelectorExtractor cssExtractor;
    private final String selectorModelId;

    public SelectorGeneratorService(
            AnthropicClient anthropicClient,
            CssSelectorExtractor cssExtractor,
            @Value("${app.agent.anthropic.model-id:claude-sonnet-4-20250514}") String selectorModelId) {
        this.anthropicClient = anthropicClient;
        this.cssExtractor = cssExtractor;
        this.selectorModelId = selectorModelId;
    }

    /**
     * Result record holding the generated selectors and the validated extraction results.
     */
    public record GenerationResult(Map<String, String> selectors, List<ExtractedProperty> results) {}

    /**
     * Ask Claude Sonnet to generate CSS selectors for the given HTML, then validate
     * them by running an actual extraction. Only returns a result when the extraction
     * yields at least one property with both a non-null address and a non-null price.
     *
     * @param html     Full page HTML (may be very large; truncated internally for the prompt)
     * @param siteName Human-readable site name for logging and prompting
     * @return Non-empty Optional when valid selectors were generated; empty otherwise
     */
    public Optional<GenerationResult> generateAndValidate(String html, String siteName) {
        try {
            // Step 1: Find repeating card elements and sample them for the prompt
            CardSample sample = findRepeatingCards(html);
            if (sample == null) {
                log.warn("SelectorGeneratorService: could not find repeating card pattern in {} HTML", siteName);
                return Optional.empty();
            }

            // Step 2: Build prompt with focused samples instead of full HTML dump
            String prompt = String.format(SELECTOR_PROMPT,
                    siteName, sample.count, sample.containerSelector, sample.sampleHtml);

            // Step 3: Call Anthropic Messages API
            Message message = anthropicClient.messages().create(MessageCreateParams.builder()
                    .model(selectorModelId)
                    .maxTokens(2000L)
                    .temperature(0.0)
                    .addUserMessage(prompt)
                    .build());

            String responseText = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .findFirst()
                    .orElse("");

            // Step 4: Parse response — strip markdown fences
            responseText = responseText.strip();
            if (responseText.startsWith("```json")) {
                responseText = responseText.substring(7);
            } else if (responseText.startsWith("```")) {
                responseText = responseText.substring(3);
            }
            if (responseText.endsWith("```")) {
                responseText = responseText.substring(0, responseText.length() - 3);
            }
            responseText = responseText.strip();

            // Extract the JSON object even if there is surrounding text
            int objStart = responseText.indexOf('{');
            int objEnd   = responseText.lastIndexOf('}');
            if (objStart < 0 || objEnd <= objStart) {
                log.warn("SelectorGeneratorService: no JSON object found in response for {}", siteName);
                return Optional.empty();
            }
            responseText = responseText.substring(objStart, objEnd + 1);

            // Step 5: Deserialize to Map<String, String>
            Map<String, String> selectors = objectMapper.readValue(
                    responseText, new TypeReference<>() {});

            // Step 6: Remove null/"null" values
            selectors.entrySet().removeIf(e -> e.getValue() == null || "null".equals(e.getValue()));

            // Step 7: listingContainer is required
            if (!selectors.containsKey("listingContainer")) {
                log.warn("SelectorGeneratorService: generated selectors missing listingContainer for {}", siteName);
                return Optional.empty();
            }

            // Step 8: Validate — run extraction against the FULL (non-truncated) HTML
            List<ExtractedProperty> results = cssExtractor.extract(html, selectors);

            // Step 9: Check validity — non-empty AND at least one address AND at least one price
            boolean hasAddress = results.stream().anyMatch(p -> p.address() != null);
            boolean hasPrice   = results.stream().anyMatch(p -> p.price() != null);

            if (results.isEmpty() || !hasAddress || !hasPrice) {
                log.warn("SelectorGeneratorService: generated selectors did not yield valid results for {} "
                        + "(results={}, hasAddress={}, hasPrice={})",
                        siteName, results.size(), hasAddress, hasPrice);
                return Optional.empty();
            }

            log.info("SelectorGeneratorService: successfully generated and validated selectors for {} "
                    + "({} results)", siteName, results.size());
            return Optional.of(new GenerationResult(selectors, results));

        } catch (Exception e) {
            log.error("SelectorGeneratorService: failed to generate selectors for {}: {}",
                    siteName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Finds repeating elements in the HTML that look like property listing cards.
     * Searches document-wide (not just siblings) for elements sharing the same tag+class.
     * Returns a sample of 2-3 cards for the LLM prompt, plus the container CSS selector.
     */
    CardSample findRepeatingCards(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style, noscript, svg, nav, footer, header").remove();

        // Find elements that repeat 3+ times anywhere in the document with the same tag+class
        Map<String, List<Element>> candidates = new LinkedHashMap<>();

        for (Element el : doc.select("*")) {
            if (el.classNames().isEmpty()) continue;
            String sig = el.tagName() + "." + el.classNames().stream().sorted().collect(Collectors.joining("."));
            candidates.computeIfAbsent(sig, k -> new ArrayList<>()).add(el);
        }

        // Filter to groups of 3+ that contain property-like text
        candidates.entrySet().removeIf(entry -> {
            if (entry.getValue().size() < 3) return true;
            Element sample = entry.getValue().get(0);
            String text = sample.text().toLowerCase();
            boolean hasPrice = text.contains("£") || text.contains("pcm") || text.contains("pw");
            boolean hasProperty = text.contains("bed") || text.contains("flat") || text.contains("apartment");
            boolean hasSubstance = text.length() > 50;
            return !(hasPrice || (hasProperty && hasSubstance));
        });

        if (candidates.isEmpty()) return null;

        // Pick the best candidate: prefer groups with both price and bed mentions
        Map.Entry<String, List<Element>> best = null;
        int bestScore = 0;
        for (var entry : candidates.entrySet()) {
            Element sample = entry.getValue().get(0);
            String text = sample.text().toLowerCase();
            int score = entry.getValue().size();
            if (text.contains("£")) score += 10;
            if (text.contains("bed")) score += 10;
            if (text.contains("pcm") || text.contains("pw")) score += 5;
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }

        if (best == null) return null;

        List<Element> cards = best.getValue();
        Element firstCard = cards.get(0);

        // Build CSS selector for the container
        String containerSelector = firstCard.tagName();
        if (!firstCard.classNames().isEmpty()) {
            containerSelector += "." + String.join(".", firstCard.classNames());
        }

        // Take 2-3 sample cards (outer HTML)
        int sampleCount = Math.min(3, cards.size());
        StringBuilder sampleHtml = new StringBuilder();
        for (int i = 0; i < sampleCount; i++) {
            sampleHtml.append("<!-- Card ").append(i + 1).append(" -->\n");
            sampleHtml.append(cards.get(i).outerHtml()).append("\n\n");
        }

        log.info("SelectorGeneratorService: found {} repeating cards matching '{}' (sampling {})",
                cards.size(), containerSelector, sampleCount);

        return new CardSample(containerSelector, sampleHtml.toString(), sampleCount, cards.size());
    }

    record CardSample(String containerSelector, String sampleHtml, int count, int totalCards) {}
}
