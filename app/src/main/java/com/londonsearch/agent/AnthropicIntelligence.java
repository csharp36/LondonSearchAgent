package com.londonsearch.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "bedrock")
public class AnthropicIntelligence implements PropertyIntelligence {

    private static final Logger log = LoggerFactory.getLogger(AnthropicIntelligence.class);

    private static final String ASSESSMENT_PROMPT = """
            You are a London property analyst helping a discerning buyer assess rental properties.

            Property details:
            - Address: %s
            - Area: %s
            - Type: %s
            - Bedrooms: %s
            - Bathrooms: %s
            - Price: £%,d pcm
            - Furnishing: %s
            - Size: %s sq ft
            - Description: %s

            Search criteria / additional requirements:
            %s

            Please assess this property in exactly this format:
            SUMMARY: <2-3 sentences covering location insights, key strengths/weaknesses, and how the price compares for this area>
            SCORE: <integer 0-100 reflecting how well this property meets the criteria>
            """;

    private final AnthropicClient anthropicClient;
    private final String modelId;

    public AnthropicIntelligence(
            AnthropicClient anthropicClient,
            @Value("${app.agent.anthropic.model-id:claude-sonnet-4-20250514}") String modelId) {
        this.anthropicClient = anthropicClient;
        this.modelId = modelId;
    }

    @Override
    public Assessment assess(Property property, SearchConfig config) {
        String area = property.getArea() != null ? property.getArea() : "Unknown";
        int price = property.getPricePerMonth() != null ? property.getPricePerMonth()
                : (property.getPrice() != null ? property.getPrice() : 0);
        String additionalCriteria = (config != null && config.getAdditionalCriteria() != null)
                ? config.getAdditionalCriteria()
                : "No additional criteria specified.";

        String prompt = String.format(
                ASSESSMENT_PROMPT,
                property.getAddress() != null ? property.getAddress() : "N/A",
                area,
                property.getPropertyType() != null ? property.getPropertyType() : "N/A",
                property.getBedrooms() != null ? property.getBedrooms() : "N/A",
                property.getBathrooms() != null ? property.getBathrooms() : "N/A",
                price,
                property.getFurnishing() != null ? property.getFurnishing() : "N/A",
                property.getSqft() != null ? property.getSqft() : "N/A",
                property.getDescription() != null ? property.getDescription() : "N/A",
                additionalCriteria
        );

        try {
            Message message = anthropicClient.messages().create(MessageCreateParams.builder()
                    .model(modelId)
                    .maxTokens(512L)
                    .temperature(0.3)
                    .addUserMessage(prompt)
                    .build());

            String responseText = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .findFirst()
                    .orElse("");

            return parseAssessment(responseText, area);

        } catch (Exception e) {
            log.error("AnthropicIntelligence: assessment failed for property in {}: {}", area, e.getMessage());
            return new Assessment("AI assessment unavailable.", 50);
        }
    }

    Assessment parseAssessment(String responseText, String area) {
        String summary = "AI assessment unavailable.";
        int score = 50;

        try {
            for (String line : responseText.lines().toList()) {
                String trimmed = line.trim();
                if (trimmed.startsWith("SUMMARY:")) {
                    summary = trimmed.substring("SUMMARY:".length()).trim();
                } else if (trimmed.startsWith("SCORE:")) {
                    String scoreStr = trimmed.substring("SCORE:".length()).trim();
                    score = Integer.parseInt(scoreStr.replaceAll("[^0-9]", ""));
                    score = Math.max(0, Math.min(100, score));
                }
            }
        } catch (Exception e) {
            log.warn("AnthropicIntelligence: failed to parse assessment response for area {}: {}", area, e.getMessage());
        }

        log.info("AnthropicIntelligence: assessed property in {} — score {}", area, score);
        return new Assessment(summary, score);
    }
}
