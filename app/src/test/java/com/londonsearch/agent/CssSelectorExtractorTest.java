package com.londonsearch.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CssSelectorExtractorTest {

    private final CssSelectorExtractor extractor = new CssSelectorExtractor();

    private static final String SAMPLE_HTML = """
            <html><body>
              <div class="listing-card">
                <span class="address">15 Mount Street, Mayfair, London W1K 2RN</span>
                <span class="price">£7,500 pcm</span>
                <span class="bedrooms">3</span>
                <span class="bathrooms">2</span>
                <span class="sqft">1,200</span>
                <span class="type">Flat</span>
                <span class="furnishing">Furnished</span>
                <span class="description">Stunning Mayfair apartment with views.</span>
                <a class="link" href="/listings/mount-street">View listing</a>
                <img class="photo" src="https://example.com/photo1.jpg" />
                <a class="floorplan" href="https://example.com/floorplan.pdf">Floor plan</a>
                <span class="available">1st August 2026</span>
                <span class="agent-name">John Smith</span>
                <span class="agent-phone">020 7123 4567</span>
                <span class="agent-email">john@example.com</span>
              </div>
              <div class="listing-card">
                <span class="address">42 Baker Street, Marylebone, London W1U 3BW</span>
                <span class="price">£5,000 pcm</span>
              </div>
            </body></html>
            """;

    private static final Map<String, String> FULL_SELECTORS = Map.ofEntries(
            Map.entry("listingContainer", ".listing-card"),
            Map.entry("address", ".address"),
            Map.entry("price", ".price"),
            Map.entry("bedrooms", ".bedrooms"),
            Map.entry("bathrooms", ".bathrooms"),
            Map.entry("sqft", ".sqft"),
            Map.entry("propertyType", ".type"),
            Map.entry("furnishing", ".furnishing"),
            Map.entry("description", ".description"),
            Map.entry("listingUrl", ".link @href"),
            Map.entry("imageUrl", ".photo @src"),
            Map.entry("floorPlanUrl", ".floorplan @href"),
            Map.entry("availableFrom", ".available"),
            Map.entry("agentName", ".agent-name"),
            Map.entry("agentPhone", ".agent-phone"),
            Map.entry("agentEmail", ".agent-email")
    );

    @Test
    void extractsAllFieldsFromValidHtml() {
        List<ExtractedProperty> results = extractor.extract(SAMPLE_HTML, FULL_SELECTORS);

        assertThat(results).hasSize(2);
        ExtractedProperty first = results.get(0);
        assertThat(first.address()).isEqualTo("15 Mount Street, Mayfair, London W1K 2RN");
        assertThat(first.price()).isEqualTo("£7,500 pcm");
        assertThat(first.bedrooms()).isEqualTo("3");
        assertThat(first.bathrooms()).isEqualTo("2");
        assertThat(first.sqft()).isEqualTo("1,200");
        assertThat(first.propertyType()).isEqualTo("Flat");
        assertThat(first.furnishing()).isEqualTo("Furnished");
        assertThat(first.description()).isEqualTo("Stunning Mayfair apartment with views.");
        assertThat(first.listingUrl()).isEqualTo("/listings/mount-street");
        assertThat(first.imageUrls()).containsExactly("https://example.com/photo1.jpg");
        assertThat(first.floorPlanUrl()).isEqualTo("https://example.com/floorplan.pdf");
        assertThat(first.availableFrom()).isEqualTo("1st August 2026");
        assertThat(first.agentName()).isEqualTo("John Smith");
        assertThat(first.agentPhone()).isEqualTo("020 7123 4567");
        assertThat(first.agentEmail()).isEqualTo("john@example.com");
    }

    @Test
    void missingFieldsReturnNull() {
        List<ExtractedProperty> results = extractor.extract(SAMPLE_HTML, FULL_SELECTORS);

        assertThat(results).hasSize(2);
        ExtractedProperty second = results.get(1);
        assertThat(second.address()).isEqualTo("42 Baker Street, Marylebone, London W1U 3BW");
        assertThat(second.price()).isEqualTo("£5,000 pcm");
        assertThat(second.bedrooms()).isNull();
        assertThat(second.bathrooms()).isNull();
        assertThat(second.sqft()).isNull();
        assertThat(second.propertyType()).isNull();
        assertThat(second.furnishing()).isNull();
        assertThat(second.description()).isNull();
        assertThat(second.listingUrl()).isNull();
        assertThat(second.imageUrls()).isEmpty();
        assertThat(second.floorPlanUrl()).isNull();
        assertThat(second.availableFrom()).isNull();
        assertThat(second.agentName()).isNull();
        assertThat(second.agentPhone()).isNull();
        assertThat(second.agentEmail()).isNull();
    }

    @Test
    void wrongSelectorsReturnEmptyList() {
        Map<String, String> badSelectors = Map.of(
                "listingContainer", ".no-such-element",
                "address", ".address",
                "price", ".price"
        );

        List<ExtractedProperty> results = extractor.extract(SAMPLE_HTML, badSelectors);

        assertThat(results).isEmpty();
    }

    @Test
    void nullOrEmptySelectorMapReturnsEmpty() {
        assertThat(extractor.extract(SAMPLE_HTML, null)).isEmpty();
        assertThat(extractor.extract(SAMPLE_HTML, Map.of())).isEmpty();
    }

    @Test
    void multipleImagesCollected() {
        String htmlWithMultipleImages = """
                <html><body>
                  <div class="listing-card">
                    <span class="address">8 Onslow Gardens, South Kensington, London SW7 3AQ</span>
                    <span class="price">£9,000 pcm</span>
                    <img class="photo" src="https://example.com/img1.jpg" />
                    <img class="photo" src="https://example.com/img2.jpg" />
                    <img class="photo" src="https://example.com/img3.jpg" />
                  </div>
                </body></html>
                """;

        Map<String, String> selectors = Map.of(
                "listingContainer", ".listing-card",
                "address", ".address",
                "price", ".price",
                "imageUrl", ".photo @src"
        );

        List<ExtractedProperty> results = extractor.extract(htmlWithMultipleImages, selectors);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).imageUrls()).containsExactly(
                "https://example.com/img1.jpg",
                "https://example.com/img2.jpg",
                "https://example.com/img3.jpg"
        );
    }
}
