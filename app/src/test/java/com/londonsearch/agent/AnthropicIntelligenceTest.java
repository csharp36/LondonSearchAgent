package com.londonsearch.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnthropicIntelligenceTest {

    @Mock(answer = RETURNS_DEEP_STUBS)
    private AnthropicClient anthropicClient;

    private AnthropicIntelligence intelligence;
    private Property property;
    private SearchConfig config;

    @BeforeEach
    void setUp() {
        intelligence = new AnthropicIntelligence(anthropicClient, "claude-sonnet-4-20250514");

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
        var result = intelligence.parseAssessment("""
                SUMMARY: Excellent location on Baker Street in the heart of Marylebone. Well-priced for a 3-bed at £7,500 pcm with good transport links.
                SCORE: 75
                """, "Marylebone");

        assertThat(result.aiSummary()).startsWith("Excellent location");
        assertThat(result.aiScore()).isEqualTo(75);
    }

    @Test
    void scoreClampedTo100() {
        var result = intelligence.parseAssessment("""
                SUMMARY: Perfect property.
                SCORE: 150
                """, "Marylebone");

        assertThat(result.aiScore()).isEqualTo(100);
    }

    @Test
    void scoreWithTrailingNonNumeric() {
        var result = intelligence.parseAssessment("""
                SUMMARY: Good property in a prime area.
                SCORE: 85.
                """, "Marylebone");

        assertThat(result.aiScore()).isEqualTo(85);
    }

    @Test
    void scoreWithSlashNotationClamps() {
        var result = intelligence.parseAssessment("""
                SUMMARY: Good property.
                SCORE: 85/100
                """, "Marylebone");

        assertThat(result.aiScore()).isEqualTo(100);
    }

    @Test
    void scoreWithPercentSign() {
        var result = intelligence.parseAssessment("""
                SUMMARY: Decent property.
                SCORE: 60%
                """, "Marylebone");

        assertThat(result.aiScore()).isEqualTo(60);
    }

    @Test
    void missingScoreDefaultsTo50() {
        var result = intelligence.parseAssessment("""
                SUMMARY: Nice flat but no score provided for some reason.
                """, "Marylebone");

        assertThat(result.aiSummary()).isEqualTo("Nice flat but no score provided for some reason.");
        assertThat(result.aiScore()).isEqualTo(50);
    }

    @Test
    void missingSummaryDefaultsToFallback() {
        var result = intelligence.parseAssessment("""
                SCORE: 70
                """, "Marylebone");

        assertThat(result.aiSummary()).isEqualTo("AI assessment unavailable.");
        assertThat(result.aiScore()).isEqualTo(70);
    }

    @Test
    void completelyMalformedResponseDefaultsToBoth() {
        var result = intelligence.parseAssessment(
                "I don't understand the format you want.", "Marylebone");

        assertThat(result.aiSummary()).isEqualTo("AI assessment unavailable.");
        assertThat(result.aiScore()).isEqualTo(50);
    }

    @Test
    void apiExceptionReturnsDefault() {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
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

        // The API call will fail (mocked with deep stubs returns null),
        // but should not NPE — should return defaults
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenThrow(new RuntimeException("mock"));

        PropertyIntelligence.Assessment result = intelligence.assess(sparse, config);

        assertThat(result.aiSummary()).isEqualTo("AI assessment unavailable.");
        assertThat(result.aiScore()).isEqualTo(50);
    }

    @Test
    void nullConfigDoesNotCauseNpe() {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenThrow(new RuntimeException("mock"));

        PropertyIntelligence.Assessment result = intelligence.assess(property, null);

        assertThat(result.aiSummary()).isEqualTo("AI assessment unavailable.");
        assertThat(result.aiScore()).isEqualTo(50);
    }

    @Test
    void multilineSummaryTakesFirstLine() {
        var result = intelligence.parseAssessment("""
                SUMMARY: Great property in Marylebone with excellent transport links.
                It also has a lovely garden and period features throughout.
                SCORE: 80
                """, "Marylebone");

        assertThat(result.aiSummary()).isEqualTo("Great property in Marylebone with excellent transport links.");
        assertThat(result.aiScore()).isEqualTo(80);
    }
}
