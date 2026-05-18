# CSS Selector Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-page LLM extraction with CSS selectors generated once by a frontier model, falling back to LLM extraction when selectors break.

**Architecture:** 3-tier extraction strategy in `processSite()`: try stored CSS selectors (free/fast), escalate to frontier model selector generation if broken, fall back to current BedrockExtractor as last resort. Validate-before-overwrite ensures bad LLM calls never destroy working selectors.

**Tech Stack:** Jsoup (CSS selectors), Bedrock Converse API (Claude Sonnet for selector generation), existing DynamoDB Enhanced Client for MonitoredSite persistence.

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `app/src/main/java/com/londonsearch/model/MonitoredSite.java` | Add cssSelectors, selectorsGeneratedAt, selectorsModel fields |
| Modify | `app/src/main/java/com/londonsearch/model/Listing.java` | Add extractionMethod field |
| Create | `app/src/main/java/com/londonsearch/agent/CssSelectorExtractor.java` | Jsoup-based extraction using a selector map |
| Create | `app/src/main/java/com/londonsearch/agent/SelectorGeneratorService.java` | Frontier model selector generation + validate-before-overwrite |
| Modify | `app/src/main/java/com/londonsearch/agent/AgentPipelineService.java` | 3-tier extractWithStrategy, pass extractionMethod to saveListing |
| Create | `app/src/test/java/com/londonsearch/agent/CssSelectorExtractorTest.java` | Unit tests for CSS extraction |
| Create | `app/src/test/java/com/londonsearch/agent/SelectorGeneratorServiceTest.java` | Unit tests for generation + validation gate |
| Modify | `app/src/main/resources/application.yml` | Add selector generation model config |

---

### Task 1: Add Fields to MonitoredSite Model

**Files:**
- Modify: `app/src/main/java/com/londonsearch/model/MonitoredSite.java`

- [ ] **Step 1: Add cssSelectors, selectorsGeneratedAt, selectorsModel fields**

Add after the existing `tier` field at the end of the class:

```java
private java.util.Map<String, String> cssSelectors;
private Instant selectorsGeneratedAt;
private String selectorsModel;

public java.util.Map<String, String> getCssSelectors() { return cssSelectors; }
public void setCssSelectors(java.util.Map<String, String> cssSelectors) { this.cssSelectors = cssSelectors; }
public Instant getSelectorsGeneratedAt() { return selectorsGeneratedAt; }
public void setSelectorsGeneratedAt(Instant selectorsGeneratedAt) { this.selectorsGeneratedAt = selectorsGeneratedAt; }
public String getSelectorsModel() { return selectorsModel; }
public void setSelectorsModel(String selectorsModel) { this.selectorsModel = selectorsModel; }
```

Also add `import java.util.Map;` to the imports.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/londonsearch/model/MonitoredSite.java
git commit -m "feat: add CSS selector fields to MonitoredSite model"
```

---

### Task 2: Add extractionMethod Field to Listing Model

**Files:**
- Modify: `app/src/main/java/com/londonsearch/model/Listing.java`

- [ ] **Step 1: Add extractionMethod field**

Add after the `scrapedAt` field:

```java
private String extractionMethod;

public String getExtractionMethod() { return extractionMethod; }
public void setExtractionMethod(String extractionMethod) { this.extractionMethod = extractionMethod; }
```

- [ ] **Step 2: Verify compilation and existing tests pass**

Run: `./gradlew :app:test`
Expected: BUILD SUCCESSFUL (all existing tests pass — DynamoDB is schemaless, new fields don't break anything)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/londonsearch/model/Listing.java
git commit -m "feat: add extractionMethod field to Listing model"
```

---

### Task 3: Build CssSelectorExtractor

**Files:**
- Create: `app/src/test/java/com/londonsearch/agent/CssSelectorExtractorTest.java`
- Create: `app/src/main/java/com/londonsearch/agent/CssSelectorExtractor.java`

- [ ] **Step 1: Write failing tests**

Create `CssSelectorExtractorTest.java`:

```java
package com.londonsearch.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CssSelectorExtractorTest {

    private static final String SAMPLE_HTML = """
            <html><body>
            <div class="property-card">
              <h2 class="address">42 Baker Street, London W1U 3BW</h2>
              <span class="price">£7,500 pcm</span>
              <span class="beds">3</span>
              <span class="baths">2</span>
              <span class="sqft">1200</span>
              <span class="type">Flat</span>
              <span class="furnishing">Furnished</span>
              <p class="desc">Beautiful period flat</p>
              <a class="listing-link" href="/property/123">View</a>
              <img class="photo" src="https://example.com/img1.jpg"/>
              <span class="available">2026-07-01</span>
              <span class="agent-name">Foxtons</span>
              <span class="agent-phone">020 7123 4567</span>
            </div>
            <div class="property-card">
              <h2 class="address">15 Mount Street, Mayfair W1K 2RN</h2>
              <span class="price">£5,000 pcm</span>
              <span class="beds">2</span>
              <span class="baths">1</span>
              <a class="listing-link" href="/property/456">View</a>
              <img class="photo" src="https://example.com/img2.jpg"/>
            </div>
            </body></html>
            """;

    private static final Map<String, String> SELECTORS = Map.ofEntries(
            Map.entry("listingContainer", ".property-card"),
            Map.entry("address", ".address"),
            Map.entry("price", ".price"),
            Map.entry("bedrooms", ".beds"),
            Map.entry("bathrooms", ".baths"),
            Map.entry("sqft", ".sqft"),
            Map.entry("propertyType", ".type"),
            Map.entry("furnishing", ".furnishing"),
            Map.entry("description", ".desc"),
            Map.entry("listingUrl", ".listing-link @href"),
            Map.entry("imageUrl", ".photo @src"),
            Map.entry("availableFrom", ".available"),
            Map.entry("agentName", ".agent-name"),
            Map.entry("agentPhone", ".agent-phone"),
            Map.entry("agentEmail", ".agent-email")
    );

    @Test
    void extractsAllFieldsFromValidHtml() {
        CssSelectorExtractor extractor = new CssSelectorExtractor();
        List<ExtractedProperty> results = extractor.extract(SAMPLE_HTML, SELECTORS);

        assertThat(results).hasSize(2);

        ExtractedProperty first = results.get(0);
        assertThat(first.address()).isEqualTo("42 Baker Street, London W1U 3BW");
        assertThat(first.price()).isEqualTo("£7,500 pcm");
        assertThat(first.bedrooms()).isEqualTo("3");
        assertThat(first.bathrooms()).isEqualTo("2");
        assertThat(first.sqft()).isEqualTo("1200");
        assertThat(first.propertyType()).isEqualTo("Flat");
        assertThat(first.furnishing()).isEqualTo("Furnished");
        assertThat(first.description()).isEqualTo("Beautiful period flat");
        assertThat(first.listingUrl()).isEqualTo("/property/123");
        assertThat(first.imageUrls()).containsExactly("https://example.com/img1.jpg");
        assertThat(first.availableFrom()).isEqualTo("2026-07-01");
        assertThat(first.agentName()).isEqualTo("Foxtons");
        assertThat(first.agentPhone()).isEqualTo("020 7123 4567");
    }

    @Test
    void missingFieldsReturnNull() {
        CssSelectorExtractor extractor = new CssSelectorExtractor();
        List<ExtractedProperty> results = extractor.extract(SAMPLE_HTML, SELECTORS);

        ExtractedProperty second = results.get(1);
        assertThat(second.address()).isEqualTo("15 Mount Street, Mayfair W1K 2RN");
        assertThat(second.sqft()).isNull();
        assertThat(second.furnishing()).isNull();
        assertThat(second.agentEmail()).isNull();
    }

    @Test
    void wrongSelectorsReturnEmptyList() {
        CssSelectorExtractor extractor = new CssSelectorExtractor();
        Map<String, String> badSelectors = Map.of(
                "listingContainer", ".nonexistent-class",
                "address", ".also-nonexistent");

        List<ExtractedProperty> results = extractor.extract(SAMPLE_HTML, badSelectors);
        assertThat(results).isEmpty();
    }

    @Test
    void nullOrEmptySelectorMapReturnsEmpty() {
        CssSelectorExtractor extractor = new CssSelectorExtractor();

        assertThat(extractor.extract(SAMPLE_HTML, null)).isEmpty();
        assertThat(extractor.extract(SAMPLE_HTML, Map.of())).isEmpty();
    }

    @Test
    void multipleImagesCollected() {
        String html = """
                <html><body>
                <div class="card">
                  <h2 class="addr">10 Downing Street</h2>
                  <span class="price">£10,000 pcm</span>
                  <img class="photo" src="https://example.com/a.jpg"/>
                  <img class="photo" src="https://example.com/b.jpg"/>
                  <img class="photo" src="https://example.com/c.jpg"/>
                </div>
                </body></html>
                """;
        Map<String, String> selectors = Map.of(
                "listingContainer", ".card",
                "address", ".addr",
                "price", ".price",
                "imageUrl", ".photo @src");

        CssSelectorExtractor extractor = new CssSelectorExtractor();
        List<ExtractedProperty> results = extractor.extract(html, selectors);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).imageUrls()).containsExactly(
                "https://example.com/a.jpg",
                "https://example.com/b.jpg",
                "https://example.com/c.jpg");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.CssSelectorExtractorTest"`
Expected: FAIL — `CssSelectorExtractor` class does not exist

- [ ] **Step 3: Write CssSelectorExtractor implementation**

Create `CssSelectorExtractor.java`:

```java
package com.londonsearch.agent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CssSelectorExtractor {

    private static final Logger log = LoggerFactory.getLogger(CssSelectorExtractor.class);

    public List<ExtractedProperty> extract(String html, Map<String, String> selectors) {
        if (selectors == null || selectors.isEmpty()) return List.of();

        String containerSelector = selectors.get("listingContainer");
        if (containerSelector == null) return List.of();

        Document doc = Jsoup.parse(html);
        Elements containers = doc.select(containerSelector);
        if (containers.isEmpty()) return List.of();

        List<ExtractedProperty> results = new ArrayList<>();
        for (Element container : containers) {
            String address = extractText(container, selectors.get("address"));
            String price = extractText(container, selectors.get("price"));
            if (address == null && price == null) continue;

            results.add(new ExtractedProperty(
                    address,
                    price,
                    extractText(container, selectors.get("bedrooms")),
                    extractText(container, selectors.get("bathrooms")),
                    extractText(container, selectors.get("sqft")),
                    extractText(container, selectors.get("propertyType")),
                    extractText(container, selectors.get("furnishing")),
                    extractText(container, selectors.get("description")),
                    extractValue(container, selectors.get("listingUrl")),
                    extractAllValues(container, selectors.get("imageUrl")),
                    extractValue(container, selectors.get("floorPlanUrl")),
                    extractText(container, selectors.get("availableFrom")),
                    extractText(container, selectors.get("agentName")),
                    extractText(container, selectors.get("agentPhone")),
                    extractText(container, selectors.get("agentEmail"))
            ));
        }

        log.info("CssSelectorExtractor: extracted {} listings from {} containers", results.size(), containers.size());
        return results;
    }

    private String extractText(Element container, String selector) {
        if (selector == null) return null;
        if (selector.contains(" @")) {
            return extractValue(container, selector);
        }
        Element el = container.selectFirst(selector);
        return el != null ? el.text().strip() : null;
    }

    private String extractValue(Element container, String selector) {
        if (selector == null) return null;
        int atIdx = selector.lastIndexOf(" @");
        if (atIdx < 0) return extractText(container, selector);

        String cssSelector = selector.substring(0, atIdx);
        String attribute = selector.substring(atIdx + 2);
        Element el = container.selectFirst(cssSelector);
        if (el == null) return null;
        String val = el.attr(attribute).strip();
        return val.isEmpty() ? null : val;
    }

    private List<String> extractAllValues(Element container, String selector) {
        if (selector == null) return null;
        int atIdx = selector.lastIndexOf(" @");
        if (atIdx < 0) return null;

        String cssSelector = selector.substring(0, atIdx);
        String attribute = selector.substring(atIdx + 2);
        Elements elements = container.select(cssSelector);
        if (elements.isEmpty()) return null;

        List<String> values = new ArrayList<>();
        for (Element el : elements) {
            String val = el.attr(attribute).strip();
            if (!val.isEmpty()) values.add(val);
        }
        return values.isEmpty() ? null : values;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.CssSelectorExtractorTest"`
Expected: BUILD SUCCESSFUL — all 5 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/CssSelectorExtractor.java \
       app/src/test/java/com/londonsearch/agent/CssSelectorExtractorTest.java
git commit -m "feat: add CssSelectorExtractor with Jsoup-based extraction"
```

---

### Task 4: Build SelectorGeneratorService

**Files:**
- Create: `app/src/test/java/com/londonsearch/agent/SelectorGeneratorServiceTest.java`
- Create: `app/src/main/java/com/londonsearch/agent/SelectorGeneratorService.java`
- Modify: `app/src/main/resources/application.yml`

- [ ] **Step 1: Add config property for selector generation model**

Add to `application.yml` under the existing `app.agent.bedrock` section:

```yaml
      selector-model-id: ${BEDROCK_SELECTOR_MODEL_ID:us.anthropic.claude-sonnet-4-6}
```

- [ ] **Step 2: Write failing tests**

Create `SelectorGeneratorServiceTest.java`:

```java
package com.londonsearch.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectorGeneratorServiceTest {

    @Mock
    private BedrockRuntimeClient bedrockClient;

    private SelectorGeneratorService service;

    private static final String SAMPLE_HTML = """
            <html><body>
            <div class="property-card">
              <h2 class="address">42 Baker Street, London W1U 3BW</h2>
              <span class="price">£7,500 pcm</span>
              <span class="beds">3</span>
              <a class="link" href="/property/123">View</a>
              <img class="photo" src="https://example.com/img.jpg"/>
            </div>
            </body></html>
            """;

    @BeforeEach
    void setUp() {
        service = new SelectorGeneratorService(
                bedrockClient,
                new CssSelectorExtractor(),
                "us.anthropic.claude-sonnet-4-6");
    }

    @Test
    void validSelectorsReturnResults() {
        stubResponse("""
                {
                  "listingContainer": ".property-card",
                  "address": ".address",
                  "price": ".price",
                  "bedrooms": ".beds",
                  "listingUrl": ".link @href",
                  "imageUrl": ".photo @src"
                }
                """);

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isPresent();
        assertThat(result.get().selectors()).containsKey("listingContainer");
        assertThat(result.get().selectors().get("address")).isEqualTo(".address");
        assertThat(result.get().results()).hasSize(1);
        assertThat(result.get().results().get(0).address()).isEqualTo("42 Baker Street, London W1U 3BW");
    }

    @Test
    void invalidSelectorsReturnEmpty() {
        stubResponse("""
                {
                  "listingContainer": ".nonexistent",
                  "address": ".also-missing",
                  "price": ".nope"
                }
                """);

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isEmpty();
    }

    @Test
    void malformedJsonResponseReturnEmpty() {
        stubResponse("I can't generate selectors for this page.");

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isEmpty();
    }

    @Test
    void bedrockExceptionReturnEmpty() {
        when(bedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isEmpty();
    }

    @Test
    void jsonWrappedInCodeFencesIsParsed() {
        stubResponse("""
                ```json
                {
                  "listingContainer": ".property-card",
                  "address": ".address",
                  "price": ".price"
                }
                ```
                """);

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isPresent();
        assertThat(result.get().results()).hasSize(1);
    }

    private void stubResponse(String responseText) {
        ConverseResponse response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText(responseText))
                                .build())
                        .build())
                .build();
        when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(response);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.SelectorGeneratorServiceTest"`
Expected: FAIL — `SelectorGeneratorService` class does not exist

- [ ] **Step 4: Write SelectorGeneratorService implementation**

Create `SelectorGeneratorService.java`:

```java
package com.londonsearch.agent;

import com.fasterxml.jackson.core.type.TypeReference;
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

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "bedrock")
public class SelectorGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SelectorGeneratorService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GENERATION_PROMPT = """
            You are a web scraping expert. Analyze the following HTML from the property listing site "%s" and produce CSS selectors that can extract property listings.

            Return ONLY a JSON object with these keys. Each value is a CSS selector string.
            - listingContainer: selector for the repeating element that wraps each property listing
            - address: selector for the property address text (within the container)
            - price: selector for the rental price text
            - bedrooms: selector for the bedroom count
            - bathrooms: selector for the bathroom count
            - sqft: selector for the square footage
            - propertyType: selector for the property type (flat, house, etc.)
            - furnishing: selector for the furnishing status
            - description: selector for the property description text
            - listingUrl: selector for the link to the full listing, with " @href" suffix to extract the href attribute (e.g. "a.listing-link @href")
            - imageUrl: selector for property images, with " @src" suffix to extract src (e.g. "img.photo @src")
            - floorPlanUrl: selector for floor plan image with " @src" suffix, or null
            - availableFrom: selector for the availability date
            - agentName: selector for the letting agent name
            - agentPhone: selector for the agent phone number
            - agentEmail: selector for the agent email

            Rules:
            - Use the ACTUAL CSS classes and structure from the HTML below. Do not guess or invent selectors.
            - The listingContainer selector is critical — it must match the repeating element for each property.
            - Use null for any field that cannot be reliably extracted from this page's structure.
            - For attributes, append " @attributeName" to the selector (e.g. "a.link @href").
            - Prefer specific, stable selectors (classes over tag positions).
            - Return ONLY the JSON object. No explanation, no markdown wrapping.

            HTML:

            %s
            """;

    private final BedrockRuntimeClient bedrockClient;
    private final CssSelectorExtractor cssSelectorExtractor;
    private final String selectorModelId;

    public SelectorGeneratorService(
            BedrockRuntimeClient bedrockClient,
            CssSelectorExtractor cssSelectorExtractor,
            @Value("${app.agent.bedrock.selector-model-id:us.anthropic.claude-sonnet-4-6}") String selectorModelId) {
        this.bedrockClient = bedrockClient;
        this.cssSelectorExtractor = cssSelectorExtractor;
        this.selectorModelId = selectorModelId;
    }

    public Optional<GenerationResult> generateAndValidate(String html, String siteName) {
        try {
            // Truncate HTML for the selector generation prompt (Sonnet has 200K tokens)
            String truncatedHtml = html.length() > 200_000 ? html.substring(0, 200_000) : html;
            String prompt = String.format(GENERATION_PROMPT, siteName, truncatedHtml);

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

            String responseText = response.output().message().content().get(0).text().strip();

            // Strip markdown code fences if present
            if (responseText.startsWith("```json")) {
                responseText = responseText.substring(7);
            } else if (responseText.startsWith("```")) {
                responseText = responseText.substring(3);
            }
            if (responseText.endsWith("```")) {
                responseText = responseText.substring(0, responseText.length() - 3);
            }
            responseText = responseText.strip();

            // Extract JSON object
            int objStart = responseText.indexOf('{');
            int objEnd = responseText.lastIndexOf('}');
            if (objStart < 0 || objEnd <= objStart) {
                log.warn("SelectorGenerator: no JSON object in response for {}", siteName);
                return Optional.empty();
            }
            responseText = responseText.substring(objStart, objEnd + 1);

            Map<String, String> selectors = objectMapper.readValue(
                    responseText, new TypeReference<>() {});

            // Remove null values
            selectors.values().removeIf(v -> v == null || "null".equals(v));

            if (!selectors.containsKey("listingContainer")) {
                log.warn("SelectorGenerator: response missing listingContainer for {}", siteName);
                return Optional.empty();
            }

            // Validate: run the generated selectors against the same HTML
            List<ExtractedProperty> results = cssSelectorExtractor.extract(html, selectors);

            if (!isValidExtraction(results)) {
                log.warn("SelectorGenerator: generated selectors failed validation for {} (got {} results)", siteName, results.size());
                return Optional.empty();
            }

            log.info("SelectorGenerator: generated and validated {} selectors for {} ({} listings extracted)",
                    selectors.size(), siteName, results.size());
            return Optional.of(new GenerationResult(selectors, results));

        } catch (Exception e) {
            log.error("SelectorGenerator: failed for {}: {}", siteName, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isValidExtraction(List<ExtractedProperty> results) {
        if (results.isEmpty()) return false;
        boolean hasAddress = results.stream().anyMatch(r -> r.address() != null && !r.address().isBlank());
        boolean hasPrice = results.stream().anyMatch(r -> r.price() != null && !r.price().isBlank());
        return hasAddress && hasPrice;
    }

    public record GenerationResult(Map<String, String> selectors, List<ExtractedProperty> results) {}
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.SelectorGeneratorServiceTest"`
Expected: BUILD SUCCESSFUL — all 5 tests pass

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/SelectorGeneratorService.java \
       app/src/test/java/com/londonsearch/agent/SelectorGeneratorServiceTest.java \
       app/src/main/resources/application.yml
git commit -m "feat: add SelectorGeneratorService with validate-before-overwrite"
```

---

### Task 5: Integrate 3-Tier Extraction into Pipeline

**Files:**
- Modify: `app/src/main/java/com/londonsearch/agent/AgentPipelineService.java`

- [ ] **Step 1: Add SelectorGeneratorService and CssSelectorExtractor to constructor**

Add these fields and constructor parameters to `AgentPipelineService`:

```java
// New fields (add after existing fields)
private final CssSelectorExtractor cssSelectorExtractor;
private final SelectorGeneratorService selectorGeneratorService; // nullable in mock mode

// Add to constructor parameters:
//   CssSelectorExtractor cssSelectorExtractor,
//   @Autowired(required = false) SelectorGeneratorService selectorGeneratorService
// (required=false because SelectorGeneratorService is @ConditionalOnProperty bedrock)
```

Add `import org.springframework.beans.factory.annotation.Autowired;` to imports.

- [ ] **Step 2: Add ExtractionResult record and extractWithStrategy method**

Add inside `AgentPipelineService`, after the existing `expandUrls` method:

```java
record ExtractionResult(List<ExtractedProperty> properties, String method) {}

private ExtractionResult extractWithStrategy(String rawHtml, String strippedHtml, MonitoredSite site) {
    // Tier 1: Try existing CSS selectors
    if (site.getCssSelectors() != null && !site.getCssSelectors().isEmpty()) {
        List<ExtractedProperty> results = cssSelectorExtractor.extract(rawHtml, site.getCssSelectors());
        if (isValidExtraction(results)) {
            return new ExtractionResult(results, "css");
        }
        log.warn("{}: CSS selectors broke ({} results), escalating to selector generation",
                site.getName(), results.size());
    }

    // Tier 2: Generate new selectors via frontier model
    if (selectorGeneratorService != null) {
        var generated = selectorGeneratorService.generateAndValidate(rawHtml, site.getName());
        if (generated.isPresent()) {
            site.setCssSelectors(generated.get().selectors());
            site.setSelectorsGeneratedAt(java.time.Instant.now());
            site.setSelectorsModel("claude-sonnet");
            siteRepo.save(site);
            log.info("{}: generated new CSS selectors ({} listings)", site.getName(), generated.get().results().size());
            return new ExtractionResult(generated.get().results(), "css-generated");
        }
        log.warn("{}: selector generation failed, falling back to LLM extraction", site.getName());
    }

    // Tier 3: Fall back to per-page LLM extraction (current behavior)
    List<ExtractedProperty> results = extractor.extract(strippedHtml, site.getName());
    return new ExtractionResult(results, "llm-fallback");
}

private boolean isValidExtraction(List<ExtractedProperty> results) {
    if (results.isEmpty()) return false;
    boolean hasAddress = results.stream().anyMatch(r -> r.address() != null && !r.address().isBlank());
    boolean hasPrice = results.stream().anyMatch(r -> r.price() != null && !r.price().isBlank());
    return hasAddress && hasPrice;
}
```

- [ ] **Step 3: Update processSite to use extractWithStrategy and track extractionMethod**

Replace the extraction loop inside `processSite()`. Change this block:

```java
        for (String url : urls) {
            Optional<SiteFetcher.FetchResult> fetchResult = usePlaywright
                    ? playwrightFetcher.fetch(url)
                    : siteFetcher.fetch(url);
            if (fetchResult.isEmpty()) continue;

            SiteFetcher.FetchResult result = fetchResult.get();
            lastHash = result.hash();

            // Strip boilerplate HTML (scripts, styles, SVGs) to fit more listing data within the LLM's truncation window
            String strippedHtml = SiteFetcher.stripBoilerplate(result.html());
            List<ExtractedProperty> extracted = extractor.extract(strippedHtml, site.getName());
            log.info("{}: extracted {} properties from {} (raw {}KB → stripped {}KB)",
                    site.getName(), extracted.size(), url,
                    result.html().length() / 1024, strippedHtml.length() / 1024);
            allExtracted.addAll(extracted);
        }
```

To:

```java
        String extractionMethod = "llm-fallback";

        for (String url : urls) {
            Optional<SiteFetcher.FetchResult> fetchResult = usePlaywright
                    ? playwrightFetcher.fetch(url)
                    : siteFetcher.fetch(url);
            if (fetchResult.isEmpty()) continue;

            SiteFetcher.FetchResult result = fetchResult.get();
            lastHash = result.hash();

            String strippedHtml = SiteFetcher.stripBoilerplate(result.html());
            ExtractionResult extraction = extractWithStrategy(result.html(), strippedHtml, site);
            extractionMethod = extraction.method();
            log.info("{}: extracted {} properties via {} from {} (raw {}KB)",
                    site.getName(), extraction.properties().size(), extraction.method(), url,
                    result.html().length() / 1024);
            allExtracted.addAll(extraction.properties());

            // Once CSS selectors work for the first URL, skip Tier 2/3 for remaining pages
            // (selectors are now stored on the site and Tier 1 will pick them up)
        }
```

- [ ] **Step 4: Pass extractionMethod through to saveListing**

Update the `processExtractedProperties` method signature to accept `extractionMethod`:

```java
public PipelineResult processExtractedProperties(List<ExtractedProperty> extracted,
                                                  String siteName, String siteBaseUrl,
                                                  String extractionMethod) {
```

And the `saveListing` method signature:

```java
private void saveListing(String propertyId, ExtractedProperty ep,
                          String siteName, String siteBaseUrl, String extractionMethod) {
```

Add `listing.setExtractionMethod(extractionMethod);` in `saveListing` before `listingRepo.save(listing)`.

Update all call sites:
- In `processSite()`: `processExtractedProperties(allExtracted, site.getName(), site.getBaseUrl(), extractionMethod)`
- In `processExtractedProperties`: pass `extractionMethod` to both `saveListing` calls

- [ ] **Step 5: Verify compilation and all tests pass**

Run: `./gradlew :app:test`
Expected: BUILD SUCCESSFUL — all tests pass (the existing `AgentPipelineServiceTest` calls `processExtractedProperties` which now has a new parameter — update the call in the test to add `"llm-fallback"` as the fourth argument)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/AgentPipelineService.java
git commit -m "feat: integrate 3-tier CSS/generation/LLM extraction strategy"
```

---

### Task 6: Update Existing Tests for New Parameter

**Files:**
- Modify: `app/src/test/java/com/londonsearch/agent/AgentPipelineServiceTest.java`

- [ ] **Step 1: Update processExtractedProperties calls in test**

Find every call to `processExtractedProperties` in `AgentPipelineServiceTest.java` and add `"llm-fallback"` as the fourth argument. For example:

```java
// Before:
pipelineService.processExtractedProperties(extracted, "TestSite", "https://test.com");
// After:
pipelineService.processExtractedProperties(extracted, "TestSite", "https://test.com", "llm-fallback");
```

- [ ] **Step 2: Run full test suite**

Run: `./gradlew :app:test`
Expected: BUILD SUCCESSFUL — all tests pass

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/londonsearch/agent/AgentPipelineServiceTest.java
git commit -m "test: update pipeline test for extractionMethod parameter"
```

---

### Task 7: Final Verification

- [ ] **Step 1: Run full test suite one more time**

Run: `./gradlew :app:test`
Expected: BUILD SUCCESSFUL with all tests passing

- [ ] **Step 2: Verify compilation of the full project**

Run: `./gradlew :app:compileJava :infra:compileJava`
Expected: BUILD SUCCESSFUL for both subprojects

- [ ] **Step 3: Review git log**

Run: `git log --oneline -7`
Expected: 6 clean commits matching the tasks above

- [ ] **Step 4: Push**

```bash
git push
```
