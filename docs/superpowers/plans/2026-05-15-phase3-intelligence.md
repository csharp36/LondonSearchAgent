# Phase 3: Intelligence — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add fuzzy address deduplication, structured preference scoring, and AI-powered summaries/scoring via Claude Sonnet so that properties in the portal have meaningful match scores and contextual AI assessments.

**Architecture:** Three new services integrate into the existing `AgentPipelineService` pipeline. `DeduplicationService` replaces exact address matching with fuzzy confidence-scored matching. `StructuredScorer` evaluates properties against SearchConfig's structured criteria (beds, price, area). `PropertyIntelligence` (interface with mock/Bedrock implementations) calls Claude Sonnet to generate AI summaries and score free-text `additionalCriteria` like "walkable to restaurants." The combined score (60% structured + 40% AI) produces the final `matchScore`.

**Tech Stack:** Bedrock Converse API (Claude Sonnet via `amazon.nova-lite-v1:0` for dedup, `anthropic.claude-sonnet-4-20250514` for intelligence), existing AWS SDK BedrockRuntime dependency

---

## File Structure

```
app/src/main/java/com/londonsearch/agent/
├── DeduplicationService.java          # Fuzzy address matching with confidence scoring
├── StructuredScorer.java              # Score property vs SearchConfig structured criteria
├── PropertyIntelligence.java          # Interface: generate AI summary + score
├── MockIntelligence.java              # Dev stub with realistic hardcoded results
├── BedrockIntelligence.java           # Claude Sonnet implementation
├── AgentPipelineService.java          # MODIFIED: wire in dedup, scoring, intelligence
```

```
app/src/test/java/com/londonsearch/agent/
├── DeduplicationServiceTest.java
├── StructuredScorerTest.java
├── AgentPipelineServiceTest.java      # MODIFIED: verify scoring and summaries
```

---

## Task 1: Deduplication Service

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/DeduplicationService.java`
- Create: `app/src/test/java/com/londonsearch/agent/DeduplicationServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// app/src/test/java/com/londonsearch/agent/DeduplicationServiceTest.java
package com.londonsearch.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeduplicationServiceTest {

    private final DeduplicationService dedup = new DeduplicationService();

    @Test
    void exactMatchReturnsConfidence1() {
        double score = dedup.addressSimilarity(
                "42 baker street, london w1u 3bw",
                "42 baker street, london w1u 3bw");
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void slightVariationReturnsHighConfidence() {
        // Missing "London" but same street and postcode
        double score = dedup.addressSimilarity(
                "42 baker street, london w1u 3bw",
                "42 baker st, w1u 3bw");
        assertThat(score).isGreaterThan(0.6);
    }

    @Test
    void sameStreetDifferentNumberReturnsLow() {
        double score = dedup.addressSimilarity(
                "42 baker street, london w1u 3bw",
                "99 baker street, london w1u 3bw");
        assertThat(score).isLessThan(0.9);
    }

    @Test
    void completelyDifferentAddressReturnsLow() {
        double score = dedup.addressSimilarity(
                "42 baker street, london w1u 3bw",
                "8 onslow gardens, london sw7 3aq");
        assertThat(score).isLessThan(0.5);
    }

    @Test
    void nullAddressReturnsZero() {
        assertThat(dedup.addressSimilarity(null, "42 baker street")).isEqualTo(0.0);
        assertThat(dedup.addressSimilarity("42 baker street", null)).isEqualTo(0.0);
    }

    @Test
    void isHighConfidenceMatch() {
        assertThat(dedup.isHighConfidenceMatch(0.95)).isTrue();
        assertThat(dedup.isHighConfidenceMatch(0.85)).isFalse();
    }

    @Test
    void isMediumConfidenceMatch() {
        assertThat(dedup.isMediumConfidenceMatch(0.75)).isTrue();
        assertThat(dedup.isMediumConfidenceMatch(0.5)).isFalse();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.DeduplicationServiceTest"`

Expected: FAIL — DeduplicationService does not exist

- [ ] **Step 3: Create DeduplicationService**

```java
// app/src/main/java/com/londonsearch/agent/DeduplicationService.java
package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.repository.PropertyRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DeduplicationService {

    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.9;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.6;

    private final PropertyRepository propertyRepo;

    public DeduplicationService(PropertyRepository propertyRepo) {
        this.propertyRepo = propertyRepo;
    }

    // No-arg constructor for unit tests that don't need the repo
    DeduplicationService() {
        this.propertyRepo = null;
    }

    /**
     * Finds an existing property matching the given normalized address.
     * Uses fuzzy matching with confidence scoring.
     */
    public Optional<DedupMatch> findMatch(String normalizedAddress) {
        if (normalizedAddress == null || normalizedAddress.isBlank()) {
            return Optional.empty();
        }

        Property bestMatch = null;
        double bestScore = 0.0;

        for (Property existing : propertyRepo.findAll()) {
            double score = addressSimilarity(normalizedAddress, existing.getNormalizedAddress());
            if (score > bestScore && score >= MEDIUM_CONFIDENCE_THRESHOLD) {
                bestScore = score;
                bestMatch = existing;
            }
        }

        if (bestMatch != null) {
            return Optional.of(new DedupMatch(bestMatch, bestScore));
        }
        return Optional.empty();
    }

    /**
     * Computes similarity between two normalized addresses.
     * Combines token overlap (Jaccard) with character-level similarity (Levenshtein ratio).
     * Returns 0.0 to 1.0.
     */
    public double addressSimilarity(String addr1, String addr2) {
        if (addr1 == null || addr2 == null) return 0.0;

        String a = addr1.toLowerCase().strip();
        String b = addr2.toLowerCase().strip();

        if (a.equals(b)) return 1.0;

        double tokenScore = tokenSimilarity(a, b);
        double charScore = levenshteinRatio(a, b);

        // Weight: 60% token overlap, 40% character similarity
        return 0.6 * tokenScore + 0.4 * charScore;
    }

    public boolean isHighConfidenceMatch(double score) {
        return score >= HIGH_CONFIDENCE_THRESHOLD;
    }

    public boolean isMediumConfidenceMatch(double score) {
        return score >= MEDIUM_CONFIDENCE_THRESHOLD && score < HIGH_CONFIDENCE_THRESHOLD;
    }

    /**
     * Jaccard similarity on word tokens.
     */
    private double tokenSimilarity(String a, String b) {
        var tokensA = java.util.Set.of(a.split("[\\s,./]+"));
        var tokensB = java.util.Set.of(b.split("[\\s,./]+"));

        long intersection = tokensA.stream().filter(tokensB::contains).count();
        long union = tokensA.size() + tokensB.size() - intersection;

        return union == 0 ? 0.0 : (double) intersection / union;
    }

    /**
     * Levenshtein distance as a ratio (1.0 = identical, 0.0 = completely different).
     */
    private double levenshteinRatio(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - ((double) levenshteinDistance(a, b) / maxLen);
    }

    private int levenshteinDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[b.length()];
    }

    public record DedupMatch(Property property, double confidence) {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.DeduplicationServiceTest"`

Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/DeduplicationService.java app/src/test/java/com/londonsearch/agent/DeduplicationServiceTest.java
git commit -m "feat: add DeduplicationService with fuzzy address matching and confidence scoring"
```

---

## Task 2: Structured Scorer

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/StructuredScorer.java`
- Create: `app/src/test/java/com/londonsearch/agent/StructuredScorerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// app/src/test/java/com/londonsearch/agent/StructuredScorerTest.java
package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredScorerTest {

    private final StructuredScorer scorer = new StructuredScorer();
    private Property property;
    private SearchConfig config;

    @BeforeEach
    void setUp() {
        property = new Property();
        property.setArea("Mayfair");
        property.setBedrooms(3);
        property.setBathrooms(2);
        property.setPricePerMonth(7500);
        property.setFurnishing("Furnished");

        config = new SearchConfig();
        config.setAreas(List.of("Mayfair", "Marylebone", "South Kensington"));
        config.setMinBeds(2);
        config.setMaxBeds(3);
        config.setMinPrice(5000);
        config.setMaxPrice(9000);
        config.setMinBaths(1);
        config.setFurnishing(List.of("Furnished", "Part-furnished"));
    }

    @Test
    void perfectMatchScoresHigh() {
        int score = scorer.score(property, config);
        assertThat(score).isGreaterThanOrEqualTo(90);
    }

    @Test
    void wrongAreaScoresLower() {
        property.setArea("Other");
        int score = scorer.score(property, config);
        assertThat(score).isLessThan(70);
    }

    @Test
    void tooFewBedroomsScoresLower() {
        property.setBedrooms(1);
        int score = scorer.score(property, config);
        assertThat(score).isLessThan(80);
    }

    @Test
    void priceAboveMaxScoresLower() {
        property.setPricePerMonth(12000);
        int score = scorer.score(property, config);
        assertThat(score).isLessThan(80);
    }

    @Test
    void wrongFurnishingScoresLower() {
        property.setFurnishing("Unfurnished");
        int score = scorer.score(property, config);
        assertThat(score).isLessThan(90);
    }

    @Test
    void nullFieldsHandledGracefully() {
        property.setBedrooms(null);
        property.setBathrooms(null);
        property.setPricePerMonth(null);
        property.setFurnishing(null);
        int score = scorer.score(property, config);
        assertThat(score).isGreaterThanOrEqualTo(0);
        assertThat(score).isLessThanOrEqualTo(100);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.StructuredScorerTest"`

Expected: FAIL — StructuredScorer does not exist

- [ ] **Step 3: Create StructuredScorer**

```java
// app/src/main/java/com/londonsearch/agent/StructuredScorer.java
package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StructuredScorer {

    /**
     * Scores a property against a SearchConfig's structured criteria.
     * Returns 0-100 representing how well the property matches.
     *
     * Criteria weights:
     * - Area match: 30 points
     * - Bedroom count: 25 points
     * - Price range: 25 points
     * - Bathroom count: 10 points
     * - Furnishing match: 10 points
     */
    public int score(Property property, SearchConfig config) {
        int total = 0;
        total += scoreArea(property.getArea(), config.getAreas());
        total += scoreBedrooms(property.getBedrooms(), config.getMinBeds(), config.getMaxBeds());
        total += scorePrice(property.getPricePerMonth(), config.getMinPrice(), config.getMaxPrice());
        total += scoreBathrooms(property.getBathrooms(), config.getMinBaths());
        total += scoreFurnishing(property.getFurnishing(), config.getFurnishing());
        return Math.min(100, Math.max(0, total));
    }

    private int scoreArea(String area, List<String> targetAreas) {
        if (area == null || targetAreas == null) return 15; // neutral
        return targetAreas.contains(area) ? 30 : 5;
    }

    private int scoreBedrooms(Integer beds, Integer minBeds, Integer maxBeds) {
        if (beds == null) return 12; // neutral
        if (minBeds != null && beds < minBeds) return 5;
        if (maxBeds != null && beds > maxBeds) return 10;
        return 25; // within range
    }

    private int scorePrice(Integer price, Integer minPrice, Integer maxPrice) {
        if (price == null) return 12; // neutral
        if (minPrice != null && price < minPrice) return 15; // under budget is OK-ish
        if (maxPrice != null && price > maxPrice) {
            // Over budget — penalty proportional to how much over
            double overRatio = (double) (price - maxPrice) / maxPrice;
            if (overRatio > 0.2) return 0; // more than 20% over
            return (int) (25 * (1 - overRatio * 5)); // graduated penalty
        }
        return 25; // within range
    }

    private int scoreBathrooms(Integer baths, Integer minBaths) {
        if (baths == null) return 5; // neutral
        if (minBaths != null && baths < minBaths) return 2;
        return 10;
    }

    private int scoreFurnishing(String furnishing, List<String> targetFurnishing) {
        if (furnishing == null || targetFurnishing == null || targetFurnishing.isEmpty()) return 5; // neutral
        return targetFurnishing.contains(furnishing) ? 10 : 3;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.londonsearch.agent.StructuredScorerTest"`

Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/StructuredScorer.java app/src/test/java/com/londonsearch/agent/StructuredScorerTest.java
git commit -m "feat: add StructuredScorer for property-vs-SearchConfig criteria matching"
```

---

## Task 3: PropertyIntelligence Interface + Mock

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/PropertyIntelligence.java`
- Create: `app/src/main/java/com/londonsearch/agent/MockIntelligence.java`

- [ ] **Step 1: Create the interface**

```java
// app/src/main/java/com/londonsearch/agent/PropertyIntelligence.java
package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;

public interface PropertyIntelligence {

    /**
     * Generates an AI assessment of a property against search criteria.
     *
     * @param property the property to assess
     * @param config the search config with additionalCriteria to evaluate against
     * @return assessment containing AI summary and AI score component (0-100)
     */
    Assessment assess(Property property, SearchConfig config);

    record Assessment(String aiSummary, int aiScore) {}
}
```

- [ ] **Step 2: Create MockIntelligence**

```java
// app/src/main/java/com/londonsearch/agent/MockIntelligence.java
package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "mock", matchIfMissing = true)
public class MockIntelligence implements PropertyIntelligence {

    private static final Logger log = LoggerFactory.getLogger(MockIntelligence.class);

    @Override
    public Assessment assess(Property property, SearchConfig config) {
        log.info("MockIntelligence: generating assessment for {}", property.getAddress());

        String area = property.getArea() != null ? property.getArea() : "the area";
        int beds = property.getBedrooms() != null ? property.getBedrooms() : 0;
        int price = property.getPricePerMonth() != null ? property.getPricePerMonth() : 0;

        String summary = String.format(
                "%d-bedroom %s in %s at £%,d pcm. %s %s",
                beds,
                property.getPropertyType() != null ? property.getPropertyType().toLowerCase() : "property",
                area,
                price,
                generateLocationInsight(area),
                generateFurnishingNote(property.getFurnishing())
        ).strip();

        // Mock AI score: 70-90 range based on area match
        int aiScore = "Other".equals(area) ? 60 : 80;

        return new Assessment(summary, aiScore);
    }

    private String generateLocationInsight(String area) {
        return switch (area) {
            case "Mayfair" -> "Strong restaurant access — one of London's premier dining destinations.";
            case "Marylebone" -> "Excellent village feel with independent shops along Marylebone High Street.";
            case "South Kensington" -> "Museum quarter with quiet residential streets. Good tube access.";
            default -> "Location outside primary target areas.";
        };
    }

    private String generateFurnishingNote(String furnishing) {
        if (furnishing == null) return "";
        return switch (furnishing) {
            case "Unfurnished" -> "Unfurnished — factor in furniture costs.";
            case "Part-furnished" -> "Part-furnished — some furniture provided but you may need additional items.";
            default -> "";
        };
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/PropertyIntelligence.java app/src/main/java/com/londonsearch/agent/MockIntelligence.java
git commit -m "feat: add PropertyIntelligence interface with MockIntelligence for dev"
```

---

## Task 4: Bedrock Intelligence (Claude Sonnet)

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/BedrockIntelligence.java`

- [ ] **Step 1: Create BedrockIntelligence**

```java
// app/src/main/java/com/londonsearch/agent/BedrockIntelligence.java
package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "bedrock")
public class BedrockIntelligence implements PropertyIntelligence {

    private static final Logger log = LoggerFactory.getLogger(BedrockIntelligence.class);

    private static final String ASSESSMENT_PROMPT = """
            You are a London property expert assisting someone relocating from the US.
            Assess this rental property against their preferences and provide:
            
            1. A concise 2-3 sentence summary covering:
               - Key strengths and weaknesses
               - Location-specific insights (walkability to restaurants, noise levels, tube proximity)
               - How it compares to similar properties in the area price-wise
               - Any concerns (basement flat, busy road, no outdoor space, etc.)
            
            2. An AI preference score from 0-100 based on how well the property matches these criteria:
               %s
            
            Property details:
            - Address: %s
            - Area: %s
            - Bedrooms: %d | Bathrooms: %d | Size: %s sqft
            - Price: £%,d pcm
            - Type: %s | Furnishing: %s
            - Description: %s
            
            Respond in this exact format (no other text):
            SUMMARY: <your 2-3 sentence summary>
            SCORE: <number 0-100>
            """;

    private final BedrockRuntimeClient bedrockClient;
    private final String intelligenceModelId;

    public BedrockIntelligence(
            BedrockRuntimeClient bedrockClient,
            @Value("${app.agent.bedrock.intelligence-model-id:anthropic.claude-sonnet-4-20250514}") String intelligenceModelId) {
        this.bedrockClient = bedrockClient;
        this.intelligenceModelId = intelligenceModelId;
    }

    @Override
    public Assessment assess(Property property, SearchConfig config) {
        log.info("BedrockIntelligence: assessing {} via Claude Sonnet", property.getAddress());

        String criteria = config.getAdditionalCriteria() != null ? config.getAdditionalCriteria() : "No specific AI preferences set.";
        String prompt = String.format(ASSESSMENT_PROMPT,
                criteria,
                property.getAddress(),
                property.getArea() != null ? property.getArea() : "Unknown",
                property.getBedrooms() != null ? property.getBedrooms() : 0,
                property.getBathrooms() != null ? property.getBathrooms() : 0,
                property.getSqft() != null ? String.valueOf(property.getSqft()) : "Unknown",
                property.getPricePerMonth() != null ? property.getPricePerMonth() : 0,
                property.getPropertyType() != null ? property.getPropertyType() : "Unknown",
                property.getFurnishing() != null ? property.getFurnishing() : "Unknown",
                property.getDescription() != null ? property.getDescription() : "No description available."
        );

        try {
            ConverseResponse response = bedrockClient.converse(ConverseRequest.builder()
                    .modelId(intelligenceModelId)
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromText(prompt))
                            .build())
                    .inferenceConfig(InferenceConfiguration.builder()
                            .maxTokens(512)
                            .temperature(0.3f)
                            .build())
                    .build());

            String responseText = response.output().message().content().get(0).text();
            return parseAssessment(responseText);

        } catch (Exception e) {
            log.error("BedrockIntelligence: assessment failed for {}: {}", property.getAddress(), e.getMessage());
            return new Assessment("AI assessment unavailable.", 50);
        }
    }

    private Assessment parseAssessment(String responseText) {
        String summary = "AI assessment generated.";
        int score = 50;

        String[] lines = responseText.split("\n");
        for (String line : lines) {
            line = line.strip();
            if (line.startsWith("SUMMARY:")) {
                summary = line.substring("SUMMARY:".length()).strip();
            } else if (line.startsWith("SCORE:")) {
                try {
                    score = Integer.parseInt(line.substring("SCORE:".length()).strip());
                    score = Math.min(100, Math.max(0, score));
                } catch (NumberFormatException ignored) {}
            }
        }

        return new Assessment(summary, score);
    }
}
```

- [ ] **Step 2: Add intelligence model config to application.yml**

Add under `app.agent.bedrock:` in `application.yml`:

```yaml
      intelligence-model-id: ${BEDROCK_INTELLIGENCE_MODEL_ID:anthropic.claude-sonnet-4-20250514}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/BedrockIntelligence.java app/src/main/resources/application.yml
git commit -m "feat: add BedrockIntelligence using Claude Sonnet for AI property assessments"
```

---

## Task 5: Wire Intelligence into Pipeline

**Files:**
- Modify: `app/src/main/java/com/londonsearch/agent/AgentPipelineService.java`

- [ ] **Step 1: Read the current AgentPipelineService.java to understand exact structure**

Read the file before modifying.

- [ ] **Step 2: Add new dependencies to the constructor**

The constructor currently takes: `SiteFetcher`, `PropertyExtractor`, `PropertyNormalizer`, `PropertyRepository`, `ListingRepository`, `MonitoredSiteRepository`.

Add three new parameters: `DeduplicationService`, `StructuredScorer`, `PropertyIntelligence`, and `SearchConfigRepository`.

- [ ] **Step 3: Replace findByNormalizedAddress with DeduplicationService**

In `processExtractedProperties`, replace the call to `findByNormalizedAddress(normalizedAddr)` with `deduplicationService.findMatch(normalizedAddr)`. Handle the `DedupMatch` result:
- If high confidence match (≥0.9): treat as existing property, add listing
- If medium confidence match (0.6-0.9): still treat as existing but log a warning
- If no match: create new property

- [ ] **Step 4: Add scoring and intelligence after creating new properties**

After `propertyRepo.save(prop)` in the new-property branch, add:

```java
// Score against active search configs
scoreAndAssess(prop);
```

Create a new private method `scoreAndAssess`:

```java
private void scoreAndAssess(Property property) {
    // Find the first enabled search config
    List<SearchConfig> configs = searchConfigRepo.findAll().stream()
            .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
            .toList();

    if (configs.isEmpty()) return;

    SearchConfig primaryConfig = configs.get(0);

    // Structured score (0-100)
    int structuredScore = structuredScorer.score(property, primaryConfig);

    // AI assessment
    PropertyIntelligence.Assessment assessment = intelligence.assess(property, primaryConfig);

    // Combined score: 60% structured + 40% AI
    int combinedScore = (int) Math.round(structuredScore * 0.6 + assessment.aiScore() * 0.4);

    property.setMatchScore(combinedScore);
    property.setAiSummary(assessment.aiSummary());
    property.setLastUpdatedAt(Instant.now());
    propertyRepo.save(property);
}
```

- [ ] **Step 5: Remove the old findByNormalizedAddress method**

Delete the private `findByNormalizedAddress` method since it's been replaced by `DeduplicationService.findMatch`.

- [ ] **Step 6: Run all tests**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/AgentPipelineService.java
git commit -m "feat: wire dedup, structured scoring, and AI intelligence into pipeline"
```

---

## Task 6: Integration Test

- [ ] **Step 1: Run full test suite**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 2: Build and start the app**

```bash
pkill -f "london-search.*\.jar" 2>/dev/null; sleep 2
lsof -i :8080 -t | xargs kill -9 2>/dev/null; sleep 2
./gradlew :app:bootJar
SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/*.jar &
# Wait for startup
```

- [ ] **Step 3: Trigger the pipeline**

```bash
# Login and trigger
curl -s -c /tmp/lsa.txt http://localhost:8080/login > /tmp/login.html
CSRF=$(grep -o 'name="_csrf" value="[^"]*"' /tmp/login.html | sed 's/name="_csrf" value="//' | sed 's/"$//')
curl -s -c /tmp/lsa.txt -b /tmp/lsa.txt -X POST "http://localhost:8080/login" -d "password=changeme&_csrf=$CSRF" -o /dev/null
curl -s -b /tmp/lsa.txt -X POST http://localhost:8080/agent/run
```

Expected: JSON response showing properties processed with scoring.

- [ ] **Step 4: Verify in browser**

Open `http://localhost:8080`. New properties from the pipeline should now have:
- `matchScore` values (not null)
- `aiSummary` text (generated by MockIntelligence)
- Color-coded match scores on cards (green ≥80%, yellow 60-80%)

- [ ] **Step 5: Commit any fixes**

```bash
git add -A && git commit -m "fix: address issues found during Phase 3 integration testing"
```

- [ ] **Step 6: Tag Phase 3**

```bash
git tag phase-3-intelligence
```
