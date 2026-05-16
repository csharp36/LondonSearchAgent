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
        assertThat(scorer.score(property, config)).isGreaterThanOrEqualTo(90);
    }

    @Test
    void wrongAreaScoresLower() {
        property.setArea("Other");
        assertThat(scorer.score(property, config)).isLessThan(70);
    }

    @Test
    void tooFewBedroomsScoresLower() {
        property.setBedrooms(1);
        assertThat(scorer.score(property, config)).isLessThan(80);
    }

    @Test
    void priceAboveMaxScoresLower() {
        property.setPricePerMonth(12000);
        assertThat(scorer.score(property, config)).isLessThan(80);
    }

    @Test
    void wrongFurnishingScoresLower() {
        property.setFurnishing("Unfurnished");
        assertThat(scorer.score(property, config)).isLessThan(90);
    }

    @Test
    void nullFieldsHandledGracefully() {
        property.setBedrooms(null);
        property.setBathrooms(null);
        property.setPricePerMonth(null);
        property.setFurnishing(null);
        int score = scorer.score(property, config);
        assertThat(score).isBetween(0, 100);
    }
}
