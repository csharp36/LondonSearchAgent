# Phase 2: Agent Pipeline — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working scrape-extract-normalize-persist pipeline that fetches property listings from real estate sites, extracts structured data via Bedrock Nova Micro, and writes them to DynamoDB so they appear in the portal.

**Architecture:** The agent pipeline lives in the existing `app/` project as a service layer (`com.londonsearch.agent` package). It uses Jsoup for HTML fetching, content hashing for change detection, and Amazon Bedrock (Nova Micro via Converse API) for AI-powered HTML extraction. A mock extractor enables development without Bedrock credentials. A REST endpoint triggers the pipeline manually; EventBridge scheduling comes in Phase 4.

**Tech Stack:** Jsoup 1.18.1 (HTML fetch/parse), AWS SDK BedrockRuntime (Converse API), Amazon Nova Micro (extraction model), OkHttp (HTTP client, transitive via Jsoup)

---

## File Structure

```
app/src/main/java/com/londonsearch/
├── agent/
│   ├── AgentPipelineService.java      # Orchestrates: fetch → extract → normalize → dedup → persist
│   ├── AgentController.java           # POST /agent/run — manual trigger endpoint
│   ├── SiteFetcher.java               # HTTP fetch with change detection (content hashing)
│   ├── PropertyExtractor.java         # Interface: HTML → List<ExtractedProperty>
│   ├── BedrockExtractor.java          # Real Bedrock Nova Micro implementation
│   ├── MockExtractor.java             # Dev stub returning hardcoded results
│   ├── PropertyNormalizer.java        # Address/price/area normalization
│   └── ExtractedProperty.java         # DTO for raw extracted data (before normalization)
├── config/
│   └── BedrockConfig.java             # BedrockRuntimeClient bean
```

```
app/src/test/java/com/londonsearch/
├── agent/
│   ├── SiteFetcherTest.java
│   ├── PropertyNormalizerTest.java
│   └── AgentPipelineServiceTest.java
```

---

## Task 1: Add Dependencies and Bedrock Config

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/londonsearch/config/BedrockConfig.java`
- Modify: `app/src/main/resources/application.yml`
- Modify: `app/src/main/resources/application-local.yml`
- Modify: `app/src/test/resources/application-test.yml`

- [ ] **Step 1: Add new library versions to the catalog**

Add these entries to `gradle/libs.versions.toml`:

In `[versions]`:
```toml
jsoup = "1.18.1"
```

In `[libraries]`:
```toml
jsoup = { module = "org.jsoup:jsoup", version.ref = "jsoup" }
aws-bedrockruntime = { module = "software.amazon.awssdk:bedrockruntime" }
```

- [ ] **Step 2: Add dependencies to app build file**

Add to the `dependencies` block in `app/build.gradle.kts`:

```kotlin
implementation(libs.jsoup)
implementation(libs.aws.bedrockruntime)
```

- [ ] **Step 3: Add agent config properties to application.yml**

Add to the bottom of `app/src/main/resources/application.yml`:

```yaml
  agent:
    extractor: ${EXTRACTOR_TYPE:mock}
    bedrock:
      region: ${BEDROCK_REGION:us-east-1}
      model-id: ${BEDROCK_MODEL_ID:amazon.nova-micro-v1:0}
    fetch:
      timeout-seconds: 30
      user-agent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
```

These go under the existing `app:` key, so the full `app:` block becomes:

```yaml
app:
  password: ${APP_PASSWORD:changeme}
  aws:
    region: ${AWS_REGION:eu-west-2}
    dynamodb:
      endpoint: ${DYNAMODB_ENDPOINT:}
    tables:
      properties: ${PROPERTIES_TABLE:Properties}
      listings: ${LISTINGS_TABLE:Listings}
      search-configs: ${SEARCH_CONFIGS_TABLE:SearchConfigs}
      monitored-sites: ${MONITORED_SITES_TABLE:MonitoredSites}
  agent:
    extractor: ${EXTRACTOR_TYPE:mock}
    bedrock:
      region: ${BEDROCK_REGION:us-east-1}
      model-id: ${BEDROCK_MODEL_ID:amazon.nova-micro-v1:0}
    fetch:
      timeout-seconds: 30
      user-agent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
```

- [ ] **Step 4: Add agent config to local profile**

Add to `app/src/main/resources/application-local.yml`:

```yaml
  agent:
    extractor: mock
```

Under the existing `app:` key.

- [ ] **Step 5: Add agent config to test profile**

Add to `app/src/test/resources/application-test.yml`:

```yaml
  agent:
    extractor: mock
```

Under the existing `app:` key.

- [ ] **Step 6: Create BedrockConfig**

```java
// app/src/main/java/com/londonsearch/config/BedrockConfig.java
package com.londonsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "bedrock")
public class BedrockConfig {

    @Value("${app.agent.bedrock.region}")
    private String bedrockRegion;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(bedrockRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
```

- [ ] **Step 7: Verify it compiles**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/londonsearch/config/BedrockConfig.java app/src/main/resources/ app/src/test/resources/
git commit -m "feat: add Bedrock and Jsoup dependencies with agent config properties"
```

---

## Task 2: ExtractedProperty DTO

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/ExtractedProperty.java`

- [ ] **Step 1: Create the DTO**

This is the intermediate data structure between raw HTML extraction and normalized Property entities. It holds whatever the AI extracts before normalization cleans it up.

```java
// app/src/main/java/com/londonsearch/agent/ExtractedProperty.java
package com.londonsearch.agent;

import java.util.List;

public record ExtractedProperty(
        String address,
        String price,
        String bedrooms,
        String bathrooms,
        String sqft,
        String propertyType,
        String furnishing,
        String description,
        String listingUrl,
        List<String> imageUrls,
        String floorPlanUrl,
        String agentName,
        String agentPhone,
        String agentEmail
) {}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/ExtractedProperty.java
git commit -m "feat: add ExtractedProperty DTO for raw extraction results"
```

---

## Task 3: Site Fetcher with Change Detection

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/SiteFetcher.java`
- Create: `app/src/test/java/com/londonsearch/agent/SiteFetcherTest.java`

- [ ] **Step 1: Write the failing test**

```java
// app/src/test/java/com/londonsearch/agent/SiteFetcherTest.java
package com.londonsearch.agent;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class SiteFetcherTest {

    @Test
    void computeHashReturnsSha256Hex() {
        String content = "<html><body>Test</body></html>";
        String hash = SiteFetcher.computeHash(content);
        assertThat(hash).hasSize(64); // SHA-256 hex = 64 chars
        assertThat(hash).matches("[a-f0-9]+");
    }

    @Test
    void sameContentProducesSameHash() {
        String content = "<html><body>Same content</body></html>";
        String hash1 = SiteFetcher.computeHash(content);
        String hash2 = SiteFetcher.computeHash(content);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void differentContentProducesDifferentHash() {
        String hash1 = SiteFetcher.computeHash("<html>Content A</html>");
        String hash2 = SiteFetcher.computeHash("<html>Content B</html>");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hasChangedReturnsTrueForNewHash() {
        boolean changed = SiteFetcher.hasChanged("newhash123", null);
        assertThat(changed).isTrue();
    }

    @Test
    void hasChangedReturnsTrueForDifferentHash() {
        boolean changed = SiteFetcher.hasChanged("newhash", "oldhash");
        assertThat(changed).isTrue();
    }

    @Test
    void hasChangedReturnsFalseForSameHash() {
        boolean changed = SiteFetcher.hasChanged("samehash", "samehash");
        assertThat(changed).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.SiteFetcherTest"`

Expected: FAIL — SiteFetcher does not exist

- [ ] **Step 3: Create SiteFetcher**

```java
// app/src/main/java/com/londonsearch/agent/SiteFetcher.java
package com.londonsearch.agent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class SiteFetcher {

    private static final Logger log = LoggerFactory.getLogger(SiteFetcher.class);

    private final int timeoutSeconds;
    private final String userAgent;

    public SiteFetcher(
            @Value("${app.agent.fetch.timeout-seconds:30}") int timeoutSeconds,
            @Value("${app.agent.fetch.user-agent:Mozilla/5.0}") String userAgent) {
        this.timeoutSeconds = timeoutSeconds;
        this.userAgent = userAgent;
    }

    /**
     * Fetches HTML from a URL. Returns Optional.empty() if the fetch fails.
     */
    public Optional<FetchResult> fetch(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutSeconds * 1000)
                    .get();

            String html = doc.html();
            String hash = computeHash(html);

            return Optional.of(new FetchResult(html, hash, url));
        } catch (IOException e) {
            log.error("Failed to fetch {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Computes SHA-256 hash of content for change detection.
     */
    public static String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Returns true if the content has changed since last check.
     */
    public static boolean hasChanged(String newHash, String previousHash) {
        return previousHash == null || !previousHash.equals(newHash);
    }

    public record FetchResult(String html, String hash, String url) {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.SiteFetcherTest"`

Expected: All 6 PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/SiteFetcher.java app/src/test/java/com/londonsearch/agent/SiteFetcherTest.java
git commit -m "feat: add SiteFetcher with HTTP fetch and content hash change detection"
```

---

## Task 4: Property Extractor Interface + Mock Implementation

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/PropertyExtractor.java`
- Create: `app/src/main/java/com/londonsearch/agent/MockExtractor.java`

- [ ] **Step 1: Create the PropertyExtractor interface**

```java
// app/src/main/java/com/londonsearch/agent/PropertyExtractor.java
package com.londonsearch.agent;

import java.util.List;

public interface PropertyExtractor {

    /**
     * Extracts property listings from HTML content.
     *
     * @param html the raw HTML of a search results page
     * @param siteName the name of the source site (for logging/context)
     * @return list of extracted properties (may be empty if extraction fails)
     */
    List<ExtractedProperty> extract(String html, String siteName);
}
```

- [ ] **Step 2: Create MockExtractor**

This returns realistic hardcoded results for development without Bedrock.

```java
// app/src/main/java/com/londonsearch/agent/MockExtractor.java
package com.londonsearch.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "mock", matchIfMissing = true)
public class MockExtractor implements PropertyExtractor {

    private static final Logger log = LoggerFactory.getLogger(MockExtractor.class);

    @Override
    public List<ExtractedProperty> extract(String html, String siteName) {
        log.info("MockExtractor: simulating extraction from {} ({} chars of HTML)", siteName, html.length());

        // Return mock data that looks like real Rightmove/OnTheMarket results
        return List.of(
                new ExtractedProperty(
                        "Flat 4, 18 Weymouth Street, London W1W 5BU",
                        "£6,500 pcm",
                        "2",
                        "2",
                        "850",
                        "Flat",
                        "Furnished",
                        "A stylish two bedroom apartment in this popular Marylebone street, moments from Regent's Park.",
                        "https://www." + siteName.toLowerCase().replace(" ", "") + ".co.uk/property/mock-001",
                        List.of(),
                        null,
                        siteName + " Lettings",
                        "020 7946 0001",
                        null
                ),
                new ExtractedProperty(
                        "23 Curzon Street, Mayfair, London W1J 7TN",
                        "£8,750 pcm",
                        "3",
                        "2",
                        "1,100",
                        "Flat",
                        "Furnished",
                        "An exceptional three bedroom apartment in the heart of Mayfair with views over the rooftops.",
                        "https://www." + siteName.toLowerCase().replace(" ", "") + ".co.uk/property/mock-002",
                        List.of(),
                        null,
                        siteName + " Lettings",
                        "020 7946 0002",
                        null
                )
        );
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/PropertyExtractor.java app/src/main/java/com/londonsearch/agent/MockExtractor.java
git commit -m "feat: add PropertyExtractor interface with MockExtractor for dev"
```

---

## Task 5: Bedrock Extractor Implementation

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/BedrockExtractor.java`

- [ ] **Step 1: Create BedrockExtractor**

This calls Bedrock Nova Micro to extract structured property data from HTML. It uses the AWS SDK Converse API directly.

```java
// app/src/main/java/com/londonsearch/agent/BedrockExtractor.java
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

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "bedrock")
public class BedrockExtractor implements PropertyExtractor {

    private static final Logger log = LoggerFactory.getLogger(BedrockExtractor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EXTRACTION_PROMPT = """
            You are a property listing data extractor. Extract ALL rental property listings from the following HTML content.
            
            For each property listing found, extract these fields:
            - address: full street address including postcode
            - price: the rental price as shown (e.g., "£7,500 pcm" or "£1,730 pw")
            - bedrooms: number of bedrooms as a string
            - bathrooms: number of bathrooms as a string (use "0" if not specified)
            - sqft: square footage as a string (use null if not specified)
            - propertyType: "Flat", "House", "Studio", "Maisonette", or other
            - furnishing: "Furnished", "Part-furnished", "Unfurnished", or null if not specified
            - description: a brief description of the property (first 200 chars of any description text)
            - listingUrl: the URL link to the full listing detail page (make it absolute if relative)
            - imageUrls: array of image URLs (first 3 only, or empty array)
            - floorPlanUrl: URL of floor plan image, or null
            - agentName: name of the letting agent, or null
            - agentPhone: phone number of the agent, or null
            - agentEmail: email of the agent, or null
            
            Return ONLY a JSON array of objects. No markdown, no explanation, just the JSON array.
            If no listings are found, return an empty array: []
            
            HTML content from %s:
            
            %s
            """;

    private final BedrockRuntimeClient bedrockClient;
    private final String modelId;

    public BedrockExtractor(
            BedrockRuntimeClient bedrockClient,
            @Value("${app.agent.bedrock.model-id}") String modelId) {
        this.bedrockClient = bedrockClient;
        this.modelId = modelId;
    }

    @Override
    public List<ExtractedProperty> extract(String html, String siteName) {
        log.info("BedrockExtractor: extracting from {} ({} chars of HTML)", siteName, html.length());

        // Truncate HTML to fit context window (Nova Micro has 128K context)
        String truncatedHtml = html.length() > 100_000 ? html.substring(0, 100_000) : html;

        String prompt = String.format(EXTRACTION_PROMPT, siteName, truncatedHtml);

        try {
            ConverseResponse response = bedrockClient.converse(ConverseRequest.builder()
                    .modelId(modelId)
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromText(prompt))
                            .build())
                    .inferenceConfig(InferenceConfiguration.builder()
                            .maxTokens(4096)
                            .temperature(0.0f)
                            .build())
                    .build());

            String responseText = response.output().message().content().get(0).text();

            // Clean up response — strip markdown code fences if present
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

            List<ExtractedProperty> results = objectMapper.readValue(
                    responseText, new TypeReference<>() {});

            log.info("BedrockExtractor: extracted {} properties from {}", results.size(), siteName);
            return results;

        } catch (Exception e) {
            log.error("BedrockExtractor: extraction failed for {}: {}", siteName, e.getMessage());
            return List.of();
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/BedrockExtractor.java
git commit -m "feat: add BedrockExtractor using Nova Micro Converse API for HTML extraction"
```

---

## Task 6: Property Normalizer

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/PropertyNormalizer.java`
- Create: `app/src/test/java/com/londonsearch/agent/PropertyNormalizerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// app/src/test/java/com/londonsearch/agent/PropertyNormalizerTest.java
package com.londonsearch.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyNormalizerTest {

    private final PropertyNormalizer normalizer = new PropertyNormalizer();

    @Test
    void parsePricePerMonth_pcm() {
        assertThat(normalizer.parsePricePerMonth("£7,500 pcm")).isEqualTo(7500);
    }

    @Test
    void parsePricePerMonth_pw() {
        // £1,730 per week = £1,730 * 52 / 12 = £7,497 per month
        assertThat(normalizer.parsePricePerMonth("£1,730 pw")).isEqualTo(7497);
    }

    @Test
    void parsePricePerMonth_pa() {
        // £90,000 per annum = £7,500 per month
        assertThat(normalizer.parsePricePerMonth("£90,000 pa")).isEqualTo(7500);
    }

    @Test
    void parsePricePerMonth_plainNumber() {
        assertThat(normalizer.parsePricePerMonth("£6,800")).isEqualTo(6800);
    }

    @Test
    void parsePricePerMonth_returnsNullForUnparseable() {
        assertThat(normalizer.parsePricePerMonth("POA")).isNull();
        assertThat(normalizer.parsePricePerMonth(null)).isNull();
    }

    @Test
    void normalizeAddress_lowercasesTrimming() {
        assertThat(normalizer.normalizeAddress("  42 Baker Street, London W1U 3BW  "))
                .isEqualTo("42 baker street, london w1u 3bw");
    }

    @Test
    void normalizeAddress_handlesNull() {
        assertThat(normalizer.normalizeAddress(null)).isNull();
    }

    @Test
    void classifyArea_mayfair() {
        assertThat(normalizer.classifyArea("15 Mount Street, Mayfair, London W1K 2RN")).isEqualTo("Mayfair");
        assertThat(normalizer.classifyArea("23 Curzon Street, London W1J 7TN")).isEqualTo("Mayfair");
    }

    @Test
    void classifyArea_marylebone() {
        assertThat(normalizer.classifyArea("42 Baker Street, Marylebone, London W1U 3BW")).isEqualTo("Marylebone");
        assertThat(normalizer.classifyArea("18 Weymouth Street, London W1W 5BU")).isEqualTo("Marylebone");
    }

    @Test
    void classifyArea_southKensington() {
        assertThat(normalizer.classifyArea("8 Onslow Gardens, South Kensington, London SW7 3AQ")).isEqualTo("South Kensington");
        assertThat(normalizer.classifyArea("7 Thurloe Place, London SW7 2RX")).isEqualTo("South Kensington");
    }

    @Test
    void classifyArea_other() {
        assertThat(normalizer.classifyArea("1 Liverpool Street, London EC2M")).isEqualTo("Other");
    }

    @Test
    void parseInteger_validNumber() {
        assertThat(normalizer.parseInteger("3")).isEqualTo(3);
        assertThat(normalizer.parseInteger("1,200")).isEqualTo(1200);
    }

    @Test
    void parseInteger_returnsNullForInvalid() {
        assertThat(normalizer.parseInteger(null)).isNull();
        assertThat(normalizer.parseInteger("N/A")).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.PropertyNormalizerTest"`

Expected: FAIL — PropertyNormalizer does not exist

- [ ] **Step 3: Create PropertyNormalizer**

```java
// app/src/main/java/com/londonsearch/agent/PropertyNormalizer.java
package com.londonsearch.agent;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PropertyNormalizer {

    private static final Pattern PRICE_PATTERN = Pattern.compile("[£$]?([\\d,]+)");

    // W1K, W1J = Mayfair; W1U, W1W, W1G, W1H, NW1 = Marylebone; SW7, SW3 = South Kensington
    private static final Map<String, String> POSTCODE_AREA_MAP = Map.ofEntries(
            Map.entry("W1K", "Mayfair"),
            Map.entry("W1J", "Mayfair"),
            Map.entry("W1S", "Mayfair"),
            Map.entry("W1U", "Marylebone"),
            Map.entry("W1W", "Marylebone"),
            Map.entry("W1G", "Marylebone"),
            Map.entry("W1H", "Marylebone"),
            Map.entry("NW1", "Marylebone"),
            Map.entry("SW7", "South Kensington"),
            Map.entry("SW3", "South Kensington")
    );

    /**
     * Parses a price string into monthly rent in GBP.
     * Handles: "£7,500 pcm", "£1,730 pw", "£90,000 pa", "£6,800"
     */
    public Integer parsePricePerMonth(String priceStr) {
        if (priceStr == null || priceStr.isBlank()) return null;

        Matcher matcher = PRICE_PATTERN.matcher(priceStr);
        if (!matcher.find()) return null;

        try {
            int amount = Integer.parseInt(matcher.group(1).replace(",", ""));
            String lower = priceStr.toLowerCase();

            if (lower.contains("pw") || lower.contains("per week") || lower.contains("p/w")) {
                return (int) Math.round(amount * 52.0 / 12.0);
            } else if (lower.contains("pa") || lower.contains("per annum") || lower.contains("p/a")) {
                return amount / 12;
            }
            // Default: assume pcm
            return amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Normalizes an address: lowercase, trim whitespace.
     */
    public String normalizeAddress(String address) {
        if (address == null) return null;
        return address.strip().toLowerCase();
    }

    /**
     * Classifies a property address into target areas based on postcode and keywords.
     */
    public String classifyArea(String address) {
        if (address == null) return "Other";

        String upper = address.toUpperCase();

        // Check postcodes first (most reliable)
        for (Map.Entry<String, String> entry : POSTCODE_AREA_MAP.entrySet()) {
            if (upper.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Fallback: check area name keywords
        String lower = address.toLowerCase();
        if (lower.contains("mayfair")) return "Mayfair";
        if (lower.contains("marylebone")) return "Marylebone";
        if (lower.contains("south kensington")) return "South Kensington";

        return "Other";
    }

    /**
     * Parses a string that may contain a number, stripping commas.
     */
    public Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.replace(",", "").strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.PropertyNormalizerTest"`

Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/PropertyNormalizer.java app/src/test/java/com/londonsearch/agent/PropertyNormalizerTest.java
git commit -m "feat: add PropertyNormalizer for price parsing, address normalization, and area classification"
```

---

## Task 7: Agent Pipeline Service

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/AgentPipelineService.java`
- Create: `app/src/test/java/com/londonsearch/agent/AgentPipelineServiceTest.java`

- [ ] **Step 1: Write the pipeline test**

```java
// app/src/test/java/com/londonsearch/agent/AgentPipelineServiceTest.java
package com.londonsearch.agent;

import com.londonsearch.model.MonitoredSite;
import com.londonsearch.model.Property;
import com.londonsearch.repository.ListingRepository;
import com.londonsearch.repository.MonitoredSiteRepository;
import com.londonsearch.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AgentPipelineServiceTest {

    @Autowired
    private AgentPipelineService pipelineService;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private MonitoredSiteRepository siteRepository;

    @Test
    void pipelineServiceBeanExists() {
        assertThat(pipelineService).isNotNull();
    }

    @Test
    void processExtractedProperties_savesNewProperties() {
        List<ExtractedProperty> extracted = List.of(
                new ExtractedProperty(
                        "99 Test Street, London W1K 1AA",
                        "£7,000 pcm",
                        "2", "1", "900", "Flat", "Furnished",
                        "A test property in Mayfair",
                        "https://example.com/property/test-001",
                        List.of(), null, "Test Agent", null, null
                )
        );

        AgentPipelineService.PipelineResult result =
                pipelineService.processExtractedProperties(extracted, "TestSite", "https://example.com");

        assertThat(result.newProperties()).isEqualTo(1);
        assertThat(result.updatedProperties()).isEqualTo(0);

        // Verify property was saved
        List<Property> mayfairProps = propertyRepository.findByArea("Mayfair");
        boolean found = mayfairProps.stream()
                .anyMatch(p -> p.getAddress().equals("99 Test Street, London W1K 1AA"));
        assertThat(found).isTrue();
    }

    @Test
    void processExtractedProperties_skipsExistingByAddress() {
        // First insert
        List<ExtractedProperty> extracted = List.of(
                new ExtractedProperty(
                        "100 Dedup Street, London W1W 1BB",
                        "£5,000 pcm",
                        "1", "1", "500", "Flat", "Unfurnished",
                        "A test dedup property",
                        "https://example.com/property/dedup-001",
                        List.of(), null, null, null, null
                )
        );

        AgentPipelineService.PipelineResult result1 =
                pipelineService.processExtractedProperties(extracted, "SiteA", "https://sitea.com");
        assertThat(result1.newProperties()).isEqualTo(1);

        // Second insert of same address from different site
        AgentPipelineService.PipelineResult result2 =
                pipelineService.processExtractedProperties(extracted, "SiteB", "https://siteb.com");
        assertThat(result2.newProperties()).isEqualTo(0);
        assertThat(result2.updatedProperties()).isEqualTo(1); // adds new listing to existing property
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.AgentPipelineServiceTest"`

Expected: FAIL — AgentPipelineService does not exist

- [ ] **Step 3: Create AgentPipelineService**

```java
// app/src/main/java/com/londonsearch/agent/AgentPipelineService.java
package com.londonsearch.agent;

import com.londonsearch.model.Listing;
import com.londonsearch.model.MonitoredSite;
import com.londonsearch.model.Property;
import com.londonsearch.repository.ListingRepository;
import com.londonsearch.repository.MonitoredSiteRepository;
import com.londonsearch.repository.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AgentPipelineService.class);

    private final SiteFetcher siteFetcher;
    private final PropertyExtractor extractor;
    private final PropertyNormalizer normalizer;
    private final PropertyRepository propertyRepo;
    private final ListingRepository listingRepo;
    private final MonitoredSiteRepository siteRepo;

    public AgentPipelineService(SiteFetcher siteFetcher,
                                 PropertyExtractor extractor,
                                 PropertyNormalizer normalizer,
                                 PropertyRepository propertyRepo,
                                 ListingRepository listingRepo,
                                 MonitoredSiteRepository siteRepo) {
        this.siteFetcher = siteFetcher;
        this.extractor = extractor;
        this.normalizer = normalizer;
        this.propertyRepo = propertyRepo;
        this.listingRepo = listingRepo;
        this.siteRepo = siteRepo;
    }

    /**
     * Runs the full pipeline for all enabled monitored sites.
     */
    public RunResult runFullPipeline() {
        log.info("Starting agent pipeline run");
        List<MonitoredSite> sites = siteRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .toList();

        int totalNew = 0;
        int totalUpdated = 0;
        int sitesProcessed = 0;
        int sitesSkipped = 0;
        List<String> errors = new ArrayList<>();

        for (MonitoredSite site : sites) {
            try {
                SiteResult result = processSite(site);
                if (result.skipped()) {
                    sitesSkipped++;
                } else {
                    sitesProcessed++;
                    totalNew += result.pipelineResult().newProperties();
                    totalUpdated += result.pipelineResult().updatedProperties();
                }
            } catch (Exception e) {
                log.error("Error processing site {}: {}", site.getName(), e.getMessage());
                errors.add(site.getName() + ": " + e.getMessage());
            }
        }

        log.info("Pipeline complete: {} sites processed, {} skipped, {} new properties, {} updated",
                sitesProcessed, sitesSkipped, totalNew, totalUpdated);

        return new RunResult(sitesProcessed, sitesSkipped, totalNew, totalUpdated, errors);
    }

    /**
     * Processes a single site: fetch → check change → extract → normalize → persist.
     */
    public SiteResult processSite(MonitoredSite site) {
        String url = site.getSearchUrlTemplate() != null ? site.getSearchUrlTemplate() : site.getBaseUrl();

        Optional<SiteFetcher.FetchResult> fetchResult = siteFetcher.fetch(url);
        if (fetchResult.isEmpty()) {
            return new SiteResult(true, new PipelineResult(0, 0));
        }

        SiteFetcher.FetchResult result = fetchResult.get();

        // Change detection
        if (!SiteFetcher.hasChanged(result.hash(), site.getLastChangeHash())) {
            log.info("No changes detected for {}", site.getName());
            return new SiteResult(true, new PipelineResult(0, 0));
        }

        // Extract
        List<ExtractedProperty> extracted = extractor.extract(result.html(), site.getName());
        if (extracted.isEmpty()) {
            log.warn("No properties extracted from {}", site.getName());
            // Still update the hash so we don't re-process
            updateSiteHash(site, result.hash());
            return new SiteResult(false, new PipelineResult(0, 0));
        }

        // Process extracted properties
        PipelineResult pipelineResult = processExtractedProperties(extracted, site.getName(), site.getBaseUrl());

        // Update site metadata
        updateSiteHash(site, result.hash());

        return new SiteResult(false, pipelineResult);
    }

    /**
     * Processes a list of extracted properties: normalize, dedup, persist.
     * Public so it can be tested independently.
     */
    public PipelineResult processExtractedProperties(List<ExtractedProperty> extracted,
                                                      String siteName, String siteBaseUrl) {
        int newCount = 0;
        int updatedCount = 0;

        for (ExtractedProperty ep : extracted) {
            String normalizedAddr = normalizer.normalizeAddress(ep.address());
            if (normalizedAddr == null || normalizedAddr.isBlank()) continue;

            // Simple text-based dedup: check if we already have this address
            Optional<Property> existing = findByNormalizedAddress(normalizedAddr);

            if (existing.isPresent()) {
                // Property exists — add a new listing from this site
                Property prop = existing.get();
                saveListing(prop.getId(), ep, siteName, siteBaseUrl);
                updatedCount++;
            } else {
                // New property
                Property prop = createProperty(ep, normalizedAddr);
                propertyRepo.save(prop);
                saveListing(prop.getId(), ep, siteName, siteBaseUrl);
                newCount++;
            }
        }

        return new PipelineResult(newCount, updatedCount);
    }

    private Property createProperty(ExtractedProperty ep, String normalizedAddr) {
        Property prop = new Property();
        prop.setId(UUID.randomUUID().toString());
        prop.setAddress(ep.address());
        prop.setNormalizedAddress(normalizedAddr);
        prop.setArea(normalizer.classifyArea(ep.address()));
        prop.setBedrooms(normalizer.parseInteger(ep.bedrooms()));
        prop.setBathrooms(normalizer.parseInteger(ep.bathrooms()));
        prop.setPricePerMonth(normalizer.parsePricePerMonth(ep.price()));
        prop.setPrice(prop.getPricePerMonth());
        prop.setCurrency("GBP");
        prop.setSqft(normalizer.parseInteger(ep.sqft()));
        prop.setPropertyType(ep.propertyType());
        prop.setFurnishing(ep.furnishing());
        prop.setDescription(ep.description());
        prop.setStatus("new");
        prop.setFirstSeenAt(Instant.now());
        prop.setLastUpdatedAt(Instant.now());
        return prop;
    }

    private void saveListing(String propertyId, ExtractedProperty ep,
                              String siteName, String siteBaseUrl) {
        String siteListingId = siteName.toLowerCase().replace(" ", "") + "#" +
                UUID.randomUUID().toString().substring(0, 8);

        // Check if we already have a listing from this site for this property
        List<Listing> existingListings = listingRepo.findByPropertyId(propertyId);
        boolean alreadyHasListingFromSite = existingListings.stream()
                .anyMatch(l -> siteName.equals(l.getSiteName()));
        if (alreadyHasListingFromSite) return;

        Listing listing = new Listing();
        listing.setPropertyId(propertyId);
        listing.setSiteListingId(siteListingId);
        listing.setSiteName(siteName);
        listing.setSiteUrl(siteBaseUrl);
        listing.setOriginalPrice(ep.price());
        listing.setOriginalAddress(ep.address());
        listing.setListingUrl(ep.listingUrl());
        listing.setImageUrls(ep.imageUrls() != null ? ep.imageUrls() : List.of());
        listing.setFloorPlanUrl(ep.floorPlanUrl());
        listing.setAgentName(ep.agentName());
        listing.setAgentPhone(ep.agentPhone());
        listing.setAgentEmail(ep.agentEmail());
        listing.setScrapedAt(Instant.now());
        listingRepo.save(listing);
    }

    private Optional<Property> findByNormalizedAddress(String normalizedAddr) {
        // Scan all properties and match by normalized address
        // This is acceptable for the expected data volume (<1000 properties)
        return propertyRepo.findAll().stream()
                .filter(p -> normalizedAddr.equals(p.getNormalizedAddress()))
                .findFirst();
    }

    private void updateSiteHash(MonitoredSite site, String hash) {
        site.setLastChangeHash(hash);
        site.setLastCheckedAt(Instant.now());
        siteRepo.save(site);
    }

    public record PipelineResult(int newProperties, int updatedProperties) {}
    public record SiteResult(boolean skipped, PipelineResult pipelineResult) {}
    public record RunResult(int sitesProcessed, int sitesSkipped, int newProperties,
                            int updatedProperties, List<String> errors) {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.AgentPipelineServiceTest"`

Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/AgentPipelineService.java app/src/test/java/com/londonsearch/agent/AgentPipelineServiceTest.java
git commit -m "feat: add AgentPipelineService orchestrating fetch, extract, normalize, dedup, and persist"
```

---

## Task 8: Agent Trigger Controller

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/AgentController.java`

- [ ] **Step 1: Create the controller**

```java
// app/src/main/java/com/londonsearch/agent/AgentController.java
package com.londonsearch.agent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentPipelineService pipelineService;

    public AgentController(AgentPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /**
     * Triggers a full pipeline run across all enabled sites.
     * POST /agent/run
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runPipeline() {
        AgentPipelineService.RunResult result = pipelineService.runFullPipeline();

        return ResponseEntity.ok(Map.of(
                "status", result.errors().isEmpty() ? "success" : "completed_with_errors",
                "sitesProcessed", result.sitesProcessed(),
                "sitesSkipped", result.sitesSkipped(),
                "newProperties", result.newProperties(),
                "updatedProperties", result.updatedProperties(),
                "errors", result.errors()
        ));
    }

    /**
     * Mirrors the AgentCore invocation contract for future extraction.
     * POST /invocations
     */
    @PostMapping("/invocations")
    public ResponseEntity<Map<String, Object>> invocations(@RequestParam(required = false) String prompt) {
        AgentPipelineService.RunResult result = pipelineService.runFullPipeline();

        return ResponseEntity.ok(Map.of(
                "response", String.format("Processed %d sites. Found %d new properties, updated %d.",
                        result.sitesProcessed(), result.newProperties(), result.updatedProperties())
        ));
    }
}
```

- [ ] **Step 2: Update SecurityConfig to allow /agent/** for authenticated users**

Read the existing `SecurityConfig.java` and verify that `/agent/**` endpoints require authentication (they should by default since only `/login`, `/css/**`, `/js/**`, `/actuator/health`, and `/alert/**` are permitted). No changes needed — the existing `anyRequest().authenticated()` covers `/agent/**`.

- [ ] **Step 3: Verify it compiles and tests pass**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/AgentController.java
git commit -m "feat: add AgentController with manual pipeline trigger and AgentCore /invocations endpoint"
```

---

## Task 9: End-to-End Integration Test

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 2: Start the app and test the pipeline manually**

Run:
```bash
pkill -f "london-search.*\.jar" 2>/dev/null; sleep 2
./gradlew :app:bootJar && SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/*.jar &
sleep 12
```

- [ ] **Step 3: Trigger the pipeline via curl**

```bash
# Login first
curl -s -c /tmp/cookies.txt http://localhost:8080/login > /dev/null
CSRF=$(curl -s -b /tmp/cookies.txt http://localhost:8080/login | grep -o 'name="_csrf" content="[^"]*"' | head -1 | sed 's/.*content="//' | sed 's/"$//')
curl -s -c /tmp/cookies.txt -b /tmp/cookies.txt -X POST "http://localhost:8080/login" -d "password=changeme&_csrf=$CSRF" > /dev/null

# Trigger pipeline
curl -s -b /tmp/cookies.txt -X POST http://localhost:8080/agent/run \
  -H "X-XSRF-TOKEN: $(grep XSRF /tmp/cookies.txt | awk '{print $NF}')" \
  -H "Cookie: $(grep XSRF /tmp/cookies.txt | awk '{printf "XSRF-TOKEN=%s", $NF}')" | python3 -m json.tool
```

Expected: JSON response showing sites processed and any new properties found (with mock extractor, each site that responds will generate 2 mock properties).

- [ ] **Step 4: Verify new properties appear in the portal**

Open `http://localhost:8080` in the browser. You should see both the original 7 seed properties AND new properties extracted by the mock extractor from any sites that were reachable.

- [ ] **Step 5: Commit any fixes**

```bash
git add -A && git commit -m "fix: address issues found during Phase 2 integration testing"
```

- [ ] **Step 6: Tag Phase 2**

```bash
git tag phase-2-agent-pipeline
```
