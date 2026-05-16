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
        assertThat(normalizer.parsePricePerMonth("£1,730 pw")).isEqualTo(7497);
    }

    @Test
    void parsePricePerMonth_pa() {
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

    @Test
    void isFakeAddress_detectsPlaceholders() {
        assertThat(normalizer.isFakeAddress("123 Fake Street, London")).isTrue();
        assertThat(normalizer.isFakeAddress("456 High Street")).isTrue();
        assertThat(normalizer.isFakeAddress("123 Main Street, London")).isTrue();
        assertThat(normalizer.isFakeAddress("Sample Road, London")).isTrue();
        assertThat(normalizer.isFakeAddress(null)).isTrue();
        assertThat(normalizer.isFakeAddress("")).isTrue();
        assertThat(normalizer.isFakeAddress("Short")).isTrue(); // too short
    }

    @Test
    void isFakeAddress_allowsRealAddresses() {
        assertThat(normalizer.isFakeAddress("15 Mount Street, Mayfair, London W1K 2RN")).isFalse();
        assertThat(normalizer.isFakeAddress("42 Baker Street, Marylebone, London W1U 3BW")).isFalse();
        assertThat(normalizer.isFakeAddress("8 Onslow Gardens, South Kensington, London SW7 3AQ")).isFalse();
    }
}
