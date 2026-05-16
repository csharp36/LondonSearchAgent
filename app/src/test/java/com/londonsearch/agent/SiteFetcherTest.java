package com.londonsearch.agent;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SiteFetcherTest {

    @Test
    void computeHashReturnsSha256Hex() {
        String content = "<html><body>Test</body></html>";
        String hash = SiteFetcher.computeHash(content);
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[a-f0-9]+");
    }

    @Test
    void sameContentProducesSameHash() {
        String content = "<html><body>Same content</body></html>";
        assertThat(SiteFetcher.computeHash(content)).isEqualTo(SiteFetcher.computeHash(content));
    }

    @Test
    void differentContentProducesDifferentHash() {
        assertThat(SiteFetcher.computeHash("<html>A</html>"))
                .isNotEqualTo(SiteFetcher.computeHash("<html>B</html>"));
    }

    @Test
    void hasChangedReturnsTrueForNewHash() {
        assertThat(SiteFetcher.hasChanged("newhash", null)).isTrue();
    }

    @Test
    void hasChangedReturnsTrueForDifferentHash() {
        assertThat(SiteFetcher.hasChanged("new", "old")).isTrue();
    }

    @Test
    void hasChangedReturnsFalseForSameHash() {
        assertThat(SiteFetcher.hasChanged("same", "same")).isFalse();
    }
}
