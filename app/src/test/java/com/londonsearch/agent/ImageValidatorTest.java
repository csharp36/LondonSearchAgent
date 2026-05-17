package com.londonsearch.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageValidatorTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<Void> httpResponse;

    private ImageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImageValidator();
        ReflectionTestUtils.setField(validator, "httpClient", httpClient);
    }

    @Test
    void validImageUrlIsKept() throws Exception {
        stubHttpResponse(200, "image/jpeg");

        List<ExtractedProperty> input = List.of(propertyWithImages("https://example.com/photo.jpg"));
        List<ExtractedProperty> result = validator.validate(input);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).imageUrls()).containsExactly("https://example.com/photo.jpg");
    }

    @Test
    void notFoundUrlIsStripped() throws Exception {
        stubHttpResponse(404, "text/html");

        List<ExtractedProperty> input = List.of(propertyWithImages("https://example.com/missing.jpg"));
        List<ExtractedProperty> result = validator.validate(input);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).imageUrls()).isEmpty();
    }

    @Test
    void nonImageContentTypeIsStripped() throws Exception {
        stubHttpResponse(200, "text/html");

        List<ExtractedProperty> input = List.of(propertyWithImages("https://example.com/not-image.html"));
        List<ExtractedProperty> result = validator.validate(input);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).imageUrls()).isEmpty();
    }

    @Test
    void exceptionOnRequestStripsUrl() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new java.io.IOException("Connection refused"));

        List<ExtractedProperty> input = List.of(propertyWithImages("https://example.com/timeout.jpg"));
        List<ExtractedProperty> result = validator.validate(input);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).imageUrls()).isEmpty();
    }

    @Test
    void propertyWithNoImagesPassedThrough() throws Exception {
        ExtractedProperty noImages = new ExtractedProperty(
                "42 Baker Street", "£5,000 pcm", "2", "1", null, "Flat",
                null, null, null, null, null, null, null, null, null);

        List<ExtractedProperty> result = validator.validate(List.of(noImages));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(noImages);
        verifyNoInteractions(httpClient);
    }

    @Test
    void propertyWithEmptyImageListPassedThrough() throws Exception {
        ExtractedProperty emptyImages = new ExtractedProperty(
                "42 Baker Street", "£5,000 pcm", "2", "1", null, "Flat",
                null, null, null, List.of(), null, null, null, null, null);

        List<ExtractedProperty> result = validator.validate(List.of(emptyImages));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(emptyImages);
        verifyNoInteractions(httpClient);
    }

    @Test
    void mixOfValidAndInvalidKeepsOnlyValid() throws Exception {
        // First call succeeds (valid image), second call returns 404
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());
        when(httpResponse.statusCode()).thenReturn(200, 404);
        HttpHeaders headers = HttpHeaders.of(
                Map.of("content-type", List.of("image/png")), (k, v) -> true);
        when(httpResponse.headers()).thenReturn(headers);

        ExtractedProperty mixed = new ExtractedProperty(
                "42 Baker Street", "£5,000 pcm", "2", "1", null, "Flat",
                null, null, null,
                List.of("https://example.com/valid.jpg", "https://example.com/broken.jpg"),
                null, null, null, null, null);

        List<ExtractedProperty> result = validator.validate(List.of(mixed));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).imageUrls()).containsExactly("https://example.com/valid.jpg");
    }

    @Test
    void allValidUrlsPreservesOriginalObject() throws Exception {
        stubHttpResponse(200, "image/jpeg");

        ExtractedProperty original = propertyWithImages("https://example.com/photo.jpg");
        List<ExtractedProperty> result = validator.validate(List.of(original));

        assertThat(result.get(0)).isSameAs(original);
    }

    private void stubHttpResponse(int statusCode, String contentType) throws Exception {
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());
        when(httpResponse.statusCode()).thenReturn(statusCode);
        if (statusCode == 200) {
            HttpHeaders headers = HttpHeaders.of(
                    Map.of("content-type", List.of(contentType)), (k, v) -> true);
            when(httpResponse.headers()).thenReturn(headers);
        }
    }

    private ExtractedProperty propertyWithImages(String... urls) {
        return new ExtractedProperty(
                "42 Baker Street", "£5,000 pcm", "2", "1", null, "Flat",
                null, null, "https://example.com/listing",
                List.of(urls), null, null, null, null, null);
    }
}
