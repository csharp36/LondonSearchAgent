package com.londonsearch.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private static final int HTML_TRUNCATION_CHARS = 200_000;

    private static final String SELECTOR_PROMPT = """
            You are an expert web scraping engineer. Examine the HTML below from the site "%s" and
            generate CSS selectors that will extract rental property listings.

            Return ONLY a JSON object (no markdown, no explanation) with these keys.
            If you cannot find a reliable selector for a field, omit that key.
            The "listingContainer" key is REQUIRED — it selects the repeating wrapper element for each listing.

            Keys and what they should select:
              listingContainer — repeating element wrapping each property card (REQUIRED)
              address          — the full street address text
              price            — the rental price text
              bedrooms         — number of bedrooms text
              bathrooms        — number of bathrooms text
              sqft             — square footage text
              propertyType     — property type text (Flat, House, etc.)
              furnishing       — furnishing status text
              description      — description or summary text
              listingUrl       — href of the link to the full listing (append " @href" to extract the attribute)
              imageUrl         — src of the main property photo (append " @src" to extract the attribute)
              floorPlanUrl     — href or src of the floor plan (append " @href" or " @src")
              availableFrom    — availability date text
              agentName        — letting agent name text
              agentPhone       — agent phone number text
              agentEmail       — agent email text

            The " @attr" convention extracts an HTML attribute rather than text content.
            Example: ".listing-link @href" extracts the href attribute of elements matching ".listing-link".

            HTML content:

            %s
            """;

    private final BedrockRuntimeClient bedrockClient;
    private final CssSelectorExtractor cssExtractor;
    private final String selectorModelId;

    public SelectorGeneratorService(
            BedrockRuntimeClient bedrockClient,
            CssSelectorExtractor cssExtractor,
            @Value("${app.agent.bedrock.selector-model-id}") String selectorModelId) {
        this.bedrockClient = bedrockClient;
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
            // Step 1: Truncate for the prompt (200K chars ~ Sonnet's 200K-token context)
            String truncatedHtml = html.length() > HTML_TRUNCATION_CHARS
                    ? html.substring(0, HTML_TRUNCATION_CHARS)
                    : html;

            // Step 2: Build prompt
            String prompt = String.format(SELECTOR_PROMPT, siteName, truncatedHtml);

            // Step 3: Call Bedrock Converse API
            ConverseResponse response = bedrockClient.converse(ConverseRequest.builder()
                    .modelId(selectorModelId)
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromText(prompt))
                            .build())
                    .inferenceConfig(InferenceConfiguration.builder()
                            .maxTokens(2000)
                            .temperature(0.0f)
                            .build())
                    .build());

            String responseText = response.output().message().content().get(0).text();

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
}
