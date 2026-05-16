package com.londonsearch.agent;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DeduplicationServiceTest {
    private final DeduplicationService dedup = new DeduplicationService();

    @Test
    void exactMatchReturnsConfidence1() {
        double score = dedup.addressSimilarity("42 baker street, london w1u 3bw", "42 baker street, london w1u 3bw");
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void slightVariationReturnsHighConfidence() {
        double score = dedup.addressSimilarity("42 baker street, london w1u 3bw", "42 baker st, w1u 3bw");
        assertThat(score).isGreaterThan(0.6);
    }

    @Test
    void sameStreetDifferentNumberReturnsLow() {
        double score = dedup.addressSimilarity("42 baker street, london w1u 3bw", "99 baker street, london w1u 3bw");
        assertThat(score).isLessThan(0.9);
    }

    @Test
    void completelyDifferentAddressReturnsLow() {
        double score = dedup.addressSimilarity("42 baker street, london w1u 3bw", "8 onslow gardens, london sw7 3aq");
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
