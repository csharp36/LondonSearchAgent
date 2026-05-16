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
