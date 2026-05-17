package com.londonsearch.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates image URLs by performing HTTP HEAD requests.
 * Strips URLs that return non-200 or non-image content types,
 * allowing the downstream ImageEnricher to kick in with og:image fallback.
 */
@Service
public class ImageValidator {

    private static final Logger log = LoggerFactory.getLogger(ImageValidator.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /**
     * Validates image URLs on each ExtractedProperty. Strips URLs that fail
     * HTTP validation (non-200 status or non-image content type).
     */
    public List<ExtractedProperty> validate(List<ExtractedProperty> properties) {
        List<ExtractedProperty> validated = new ArrayList<>();
        for (ExtractedProperty ep : properties) {
            if (ep.imageUrls() == null || ep.imageUrls().isEmpty()) {
                validated.add(ep);
                continue;
            }

            List<String> validUrls = new ArrayList<>();
            for (String url : ep.imageUrls()) {
                if (isValidImageUrl(url)) {
                    validUrls.add(url);
                }
            }

            if (validUrls.size() == ep.imageUrls().size()) {
                validated.add(ep);
            } else {
                log.info("ImageValidator: {} — kept {}/{} image URLs for {}",
                        validUrls.isEmpty() ? "all invalid" : "some invalid",
                        validUrls.size(), ep.imageUrls().size(), ep.address());
                validated.add(new ExtractedProperty(
                        ep.address(), ep.price(), ep.bedrooms(), ep.bathrooms(),
                        ep.sqft(), ep.propertyType(), ep.furnishing(), ep.description(),
                        ep.listingUrl(), validUrls, ep.floorPlanUrl(), ep.availableFrom(),
                        ep.agentName(), ep.agentPhone(), ep.agentEmail()
                ));
            }
        }
        return validated;
    }

    private boolean isValidImageUrl(String url) {
        try {
            URI uri = URI.create(url);
            String referer = uri.getScheme() + "://" + uri.getHost() + "/";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")
                    .header("Referer", referer)
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() != 200) {
                log.debug("ImageValidator: {} returned {}", url, response.statusCode());
                return false;
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.startsWith("image/")) {
                log.debug("ImageValidator: {} returned non-image content-type {}", url, contentType);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.debug("ImageValidator: {} failed: {}", url, e.getMessage());
            return false;
        }
    }
}
