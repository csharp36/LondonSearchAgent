package com.londonsearch.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Proxies property images through our server to bypass hotlink protection
 * and provide a consistent fallback for broken URLs.
 */
@RestController
public class ImageProxyController {

    private static final Logger log = LoggerFactory.getLogger(ImageProxyController.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @GetMapping("/api/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String url) {
        try {
            URI uri = URI.create(url);
            String referer = uri.getScheme() + "://" + uri.getHost() + "/";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")
                    .header("Referer", referer)
                    .header("Accept", "image/webp,image/avif,image/*,*/*")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200 && response.body().length > 0) {
                String contentType = response.headers().firstValue("content-type").orElse("image/jpeg");
                // Reject non-image responses (e.g. HTML error pages returned with 200)
                if (!contentType.startsWith("image/")) {
                    log.debug("ImageProxy: non-image content-type {} for {}", contentType, url);
                    return ResponseEntity.notFound().build();
                }
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                        .body(response.body());
            }
        } catch (Exception e) {
            log.debug("ImageProxy: failed to fetch {}: {}", url, e.getMessage());
        }

        // Return 302 to a 1x1 transparent pixel (signals the frontend onerror handler)
        return ResponseEntity.notFound().build();
    }
}
