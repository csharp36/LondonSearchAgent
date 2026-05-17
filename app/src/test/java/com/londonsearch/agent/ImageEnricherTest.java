package com.londonsearch.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageEnricherTest {

    @Test
    void propertyWithExistingImagesIsSkipped() {
        ImageEnricher enricher = spy(new ImageEnricher());

        ExtractedProperty withImages = new ExtractedProperty(
                "42 Baker Street", "£5,000 pcm", "2", "1", null, "Flat",
                null, null, "https://example.com/listing",
                List.of("https://example.com/photo.jpg"), null, null, null, null, null);

        List<ExtractedProperty> result = enricher.enrich(List.of(withImages));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(withImages);
        verify(enricher, never()).fetchOgImages(anyString());
    }

    @Test
    void propertyWithNullListingUrlIsSkipped() {
        ImageEnricher enricher = spy(new ImageEnricher());

        ExtractedProperty noUrl = new ExtractedProperty(
                "42 Baker Street", "£5,000 pcm", "2", "1", null, "Flat",
                null, null, null, null, null, null, null, null, null);

        List<ExtractedProperty> result = enricher.enrich(List.of(noUrl));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(noUrl);
        verify(enricher, never()).fetchOgImages(anyString());
    }

    @Test
    void propertyWithBlankListingUrlIsSkipped() {
        ImageEnricher enricher = spy(new ImageEnricher());

        ExtractedProperty blankUrl = new ExtractedProperty(
                "42 Baker Street", "£5,000 pcm", "2", "1", null, "Flat",
                null, null, "   ", null, null, null, null, null, null);

        List<ExtractedProperty> result = enricher.enrich(List.of(blankUrl));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(blankUrl);
        verify(enricher, never()).fetchOgImages(anyString());
    }

    @Test
    void propertyWithNoImagesGetsEnriched() {
        ImageEnricher enricher = spy(new ImageEnricher());
        doReturn(List.of("https://example.com/og-image.jpg"))
                .when(enricher).fetchOgImages("https://example.com/listing/42");

        ExtractedProperty noImages = new ExtractedProperty(
                "42 Baker Street", "£5,000 pcm", "2", "1", null, "Flat",
                null, null, "https://example.com/listing/42",
                null, null, null, null, null, null);

        List<ExtractedProperty> result = enricher.enrich(List.of(noImages));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).imageUrls()).containsExactly("https://example.com/og-image.jpg");
        assertThat(result.get(0).address()).isEqualTo("42 Baker Street");
    }

    @Test
    void propertyWithEmptyImagesGetsEnriched() {
        ImageEnricher enricher = spy(new ImageEnricher());
        doReturn(List.of("https://example.com/og.jpg"))
                .when(enricher).fetchOgImages("https://example.com/listing/99");

        ExtractedProperty emptyImages = new ExtractedProperty(
                "99 Mount Street", "£8,000 pcm", "3", "2", null, "Flat",
                null, null, "https://example.com/listing/99",
                List.of(), null, null, null, null, null);

        List<ExtractedProperty> result = enricher.enrich(List.of(emptyImages));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).imageUrls()).containsExactly("https://example.com/og.jpg");
    }

    @Test
    void fetchReturnsEmptyLeavesPropertyUnchanged() {
        ImageEnricher enricher = spy(new ImageEnricher());
        doReturn(List.of()).when(enricher).fetchOgImages("https://example.com/listing/42");

        ExtractedProperty noImages = new ExtractedProperty(
                "42 Baker Street", "£5,000 pcm", "2", "1", null, "Flat",
                null, null, "https://example.com/listing/42",
                null, null, null, null, null, null);

        List<ExtractedProperty> result = enricher.enrich(List.of(noImages));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(noImages);
    }

    @Test
    void multiplePropertiesMixedBehavior() {
        ImageEnricher enricher = spy(new ImageEnricher());
        doReturn(List.of("https://example.com/enriched.jpg"))
                .when(enricher).fetchOgImages("https://example.com/listing/2");

        ExtractedProperty hasImages = new ExtractedProperty(
                "Property 1", "£5,000 pcm", "2", "1", null, "Flat",
                null, null, "https://example.com/listing/1",
                List.of("https://example.com/existing.jpg"), null, null, null, null, null);

        ExtractedProperty needsEnrichment = new ExtractedProperty(
                "Property 2", "£6,000 pcm", "3", "2", null, "Flat",
                null, null, "https://example.com/listing/2",
                null, null, null, null, null, null);

        ExtractedProperty noUrl = new ExtractedProperty(
                "Property 3", "£4,000 pcm", "1", "1", null, "Flat",
                null, null, null, null, null, null, null, null, null);

        List<ExtractedProperty> result = enricher.enrich(List.of(hasImages, needsEnrichment, noUrl));

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isSameAs(hasImages);
        assertThat(result.get(1).imageUrls()).containsExactly("https://example.com/enriched.jpg");
        assertThat(result.get(2)).isSameAs(noUrl);
    }
}
