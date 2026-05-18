package com.londonsearch.agent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class SiteFetcher {

    private static final Logger log = LoggerFactory.getLogger(SiteFetcher.class);

    private final int timeoutSeconds;
    private final String userAgent;

    public SiteFetcher(
            @Value("${app.agent.fetch.timeout-seconds:30}") int timeoutSeconds,
            @Value("${app.agent.fetch.user-agent:Mozilla/5.0}") String userAgent) {
        this.timeoutSeconds = timeoutSeconds;
        this.userAgent = userAgent;
    }

    public Optional<FetchResult> fetch(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutSeconds * 1000)
                    .get();
            String html = doc.html();
            String hash = computeHash(html);
            return Optional.of(new FetchResult(html, hash, url));
        } catch (IOException e) {
            log.error("Failed to fetch {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    public static String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static boolean hasChanged(String newHash, String previousHash) {
        return previousHash == null || !previousHash.equals(newHash);
    }

    /** Strips scripts, styles, SVGs and other boilerplate to reduce HTML size for LLM extraction. */
    public static String stripBoilerplate(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style, noscript, svg, link[rel=stylesheet], link[rel=preload], link[rel=prefetch]").remove();
        doc.select("[style*=display:none], [style*=display: none], [hidden]").remove();
        doc.head().empty();
        return doc.body().html();
    }

    public record FetchResult(String html, String hash, String url) {}
}
