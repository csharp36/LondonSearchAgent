package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedrockIntelligenceTest {

    @Mock
    private BedrockRuntimeClient bedrockClient;

    private BedrockIntelligence intelligence;
    private Property property;
    private SearchConfig config;

    @BeforeEach
    void setUp() {
        intelligence = new BedrockIntelligence(bedrockClient, "us.anthropic.claude-sonnet-4-6");

        property = new Property();
        property.setAddress("42 Baker Street, London W1U 3BW");
        property.setArea("Marylebone");
        property.setPropertyType("Flat");
        property.setBedrooms(3);
        property.setBathrooms(2);
        property.setPricePerMonth(7500);
        property.setFurnishing("Furnished");
        property.setDescription("Beautiful period flat");

        config = new SearchConfig();
        config.setAreas(List.of("Mayfair", "Marylebone", "South Kensington"));
        config.setAdditionalCriteria("Prefer high ceilings and natural light");
    }

    @Test
    void wellFormedResponse() {
        stubResponse("""
                SUMMARY: Excellent location on Baker Street in the heart of Marylebone. Well-priced for a 3-bed at £7,500 pcm with good transport links.
                SCORE: 75
                """);

        PropertyIntelligence.Assessment result = intelligence.assess(property, config);

        assertThat(result.aiSummary()).startsWith("Excellent location");
        assertThat(result.aiScore()).isEqualTo(75);
    }

    @Test
    void scoreClampedTo100() {
        stubResponse("""
                SUMMARY: Perfect property.
                SCORE: 150
                """);

        PropertyIntelligence.Assessment result = intelligence.assess(property, config);

        assertThat(result.aiScore()).isEqualTo(100);
    }

    @Test
    void scoreWithTrailingNonNumeric() {
        stubResponse("""
                SUMMARY: Good property in a prime area.
                SCORE: 85.
                """);

        PropertyIntelligence.Assessment result = intelligence.assess(property, config);

        assertThat(result.aiScore()).isEqualTo(85);
    }

    @Test
    void scoreWithSlashNotationClamps() {
        // "85/100" → replaceAll("[^0-9]","") → "85100" → clamped to 100
        stubResponse("""
                SUMMARY: Good property.
                SCORE: 85/100
                """);

        PropertyIntelligence.Assessment result = intelligence.assess(property, config);

        assertThat(result.aiScore()).isEqualTo(100);
    }

    @Test
    void scoreWithPercentSign() {
        stubResponse("""
                SUMMARY: Decent property.
                SCORE: 60%
                """);

        PropertyIntelligence.Assessment result = intelligence.assess(property, config);

        assertThat(result.aiScore()).isEqualTo(60);
    }

    @Test
    void missingScoreDefaultsTo50() {
        stubResponse("""
                SUMMARY: Nice flat but no score provided for some reason.
                """);

        PropertyIntelligence.Assessment result = intelligence.assess(property, config);

        assertThat(result.aiSummary()).isEqualTo("Nice flat but no score provided for some reason.");
        assertThat(result.aiScore()).isEqualTo(50);
    }

    @Test
    void missingSummaryDefaultsToFallback() {
        stubResponse("""
                SCORE: 70
                """);

        PropertyIntelligence.Assessment result = intelligence.assess(property, config);

        assertThat(result.aiSummary()).isEqualTo("AI assessment unavailable.");
        assertThat(result.aiScore()).isEqualTo(70);
    }

    @Test
    void completelyMalformedResponseDefaultsToBoth() {
        stubResponse("I don't understand the format you want.");

        PropertyIntelligence.Assessment result = intelligence.assess(property, config);

        assertThat(result.aiSummary()).isEqualTo("AI assessment unavailable.");
        assertThat(result.aiScore()).isEqualTo(50);
    }

    @Test
    void bedrockExceptionReturnsDefault() {
        when(bedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

        PropertyIntelligence.Assessment result = intelligence.assess(property, config);

        assertThat(result.aiSummary()).isEqualTo("AI assessment unavailable.");
        assertThat(result.aiScore()).isEqualTo(50);
    }

    @Test
    void nullPropertyFieldsDoNotCauseNpe() {
        Property sparse = new Property();
        sparse.setAddress(null);
        sparse.setArea(null);
        sparse.setPricePerMonth(null);
        sparse.setPrice(null);

        stubResponse("""
                SUMMARY: Limited data available.
                SCORE: 30
                """);

        PropertyIntelligence.Assessment result = intelligence.assess(sparse, config);

        assertThat(result.aiScore()).isEqualTo(30);
    }

    @Test
    void nullConfigDoesNotCauseNpe() {
        stubResponse("""
                SUMMARY: Assessment without config.
                SCORE: 55
                """);

        PropertyIntelligence.Assessment result = intelligence.assess(property, null);

        assertThat(result.aiScore()).isEqualTo(55);
    }

    @Test
    void multilineSummaryTakesFirstLine() {
        stubResponse("""
                SUMMARY: Great property in Marylebone with excellent transport links.
                It also has a lovely garden and period features throughout.
                SCORE: 80
                """);

        PropertyIntelligence.Assessment result = intelligence.assess(property, config);

        assertThat(result.aiSummary()).isEqualTo("Great property in Marylebone with excellent transport links.");
        assertThat(result.aiScore()).isEqualTo(80);
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
