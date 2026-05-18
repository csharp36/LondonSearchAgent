package com.londonsearch.controller;

import com.londonsearch.repository.MonitoredSiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Proxies property images through our server to bypass hotlink protection
 * and provide a consistent fallback for broken URLs.
 * Only allows requests to known estate agent domains and their CDNs.
 */
@RestController
public class ImageProxyController {

    private static final Logger log = LoggerFactory.getLogger(ImageProxyController.class);

    /** CDN domains used by estate agents for property images. */
    private static final Set<String> CDN_DOMAINS = Set.of(
            "assets.foxtons.co.uk",
            "web-prod-page-assets.foxtons.co.uk",
            "assets.savills.com",
            "media.onthemarket.com",
            "media.rightmove.co.uk",
            "mr0.homeflow-assets.co.uk",
            "mr1.homeflow-assets.co.uk",
            "mr2.homeflow-assets.co.uk",
            "mr3.homeflow-assets.co.uk",
            "lid.zoocdn.com",
            "st.zoocdn.com",
            "lc.zoocdn.com"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Set<String> allowedDomains;

    public ImageProxyController(MonitoredSiteRepository siteRepo) {
        // Build allowlist from monitored sites + known CDNs
        Set<String> siteDomains = siteRepo.findAll().stream()
                .map(site -> {
                    try { return URI.create(site.getBaseUrl()).getHost(); }
                    catch (Exception e) { return null; }
                })
                .filter(host -> host != null)
                .collect(Collectors.toSet());
        siteDomains.addAll(CDN_DOMAINS);
        this.allowedDomains = Set.copyOf(siteDomains);
        log.info("ImageProxy: allowlist loaded with {} domains", allowedDomains.size());
    }

    @GetMapping("/api/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();

            // Reject requests to domains not in the allowlist
            if (host == null || !isAllowedDomain(host)) {
                log.debug("ImageProxy: blocked request to non-allowed domain: {}", host);
                return ResponseEntity.status(403).build();
            }

            String referer = uri.getScheme() + "://" + host + "/";

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

    /** Checks if the host matches any allowed domain (exact match or subdomain). */
    private boolean isAllowedDomain(String host) {
        if (allowedDomains.contains(host)) return true;
        // Allow subdomains of monitored sites (e.g. assets.foxtons.co.uk matches foxtons.co.uk)
        for (String allowed : allowedDomains) {
            if (host.endsWith("." + allowed)) return true;
        }
        return false;
    }
}
