package com.londonsearch.agent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Attempts to enrich extracted properties with images by fetching their
 * listing page and extracting og:image / twitter:image meta tags.
 * These meta tags are server-rendered for social previews even on JS-heavy sites.
 */
@Service
public class ImageEnricher {

    private static final Logger log = LoggerFactory.getLogger(ImageEnricher.class);

    /**
     * For each property that has a listingUrl but no images, fetches the
     * listing page and tries to extract og:image or twitter:image.
     * Returns a new list with enriched copies.
     */
    public List<ExtractedProperty> enrich(List<ExtractedProperty> properties) {
        List<ExtractedProperty> enriched = new ArrayList<>();
        for (ExtractedProperty ep : properties) {
            if (hasImages(ep) || ep.listingUrl() == null || ep.listingUrl().isBlank()) {
                enriched.add(ep);
                continue;
            }
            List<String> images = fetchOgImages(ep.listingUrl());
            if (!images.isEmpty()) {
                enriched.add(new ExtractedProperty(
                        ep.address(), ep.price(), ep.bedrooms(), ep.bathrooms(),
                        ep.sqft(), ep.propertyType(), ep.furnishing(), ep.description(),
                        ep.listingUrl(), images, ep.floorPlanUrl(), ep.availableFrom(),
                        ep.agentName(), ep.agentPhone(), ep.agentEmail()
                ));
                log.debug("Enriched {} with {} images from og:image", ep.address(), images.size());
            } else {
                enriched.add(ep);
            }
        }
        return enriched;
    }

    private boolean hasImages(ExtractedProperty ep) {
        return ep.imageUrls() != null && !ep.imageUrls().isEmpty();
    }

    private List<String> fetchOgImages(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(10_000)
                    .followRedirects(true)
                    .get();

            List<String> images = new ArrayList<>();

            // Try og:image first (most reliable for property sites)
            Element ogImage = doc.selectFirst("meta[property=og:image]");
            if (ogImage != null && ogImage.hasAttr("content")) {
                String imgUrl = ogImage.attr("content").strip();
                if (!imgUrl.isBlank() && !imgUrl.contains("logo") && !imgUrl.contains("favicon")) {
                    images.add(imgUrl);
                }
            }

            // Try twitter:image as fallback
            if (images.isEmpty()) {
                Element twitterImage = doc.selectFirst("meta[name=twitter:image]");
                if (twitterImage != null && twitterImage.hasAttr("content")) {
                    String imgUrl = twitterImage.attr("content").strip();
                    if (!imgUrl.isBlank()) {
                        images.add(imgUrl);
                    }
                }
            }

            // Last resort: look for large property images in the page HTML
            if (images.isEmpty()) {
                for (Element img : doc.select("img[src]")) {
                    String src = img.attr("abs:src");
                    if (src.isBlank()) continue;
                    // Skip tiny images, icons, logos, tracking pixels
                    String lower = src.toLowerCase();
                    if (lower.contains("logo") || lower.contains("favicon") || lower.contains("icon")
                            || lower.contains("pixel") || lower.contains("spacer") || lower.contains("avatar")
                            || lower.contains("agent") || lower.contains("sprite") || lower.endsWith(".svg")) {
                        continue;
                    }
                    // Prefer images with property-related path segments
                    if (lower.contains("property") || lower.contains("photo") || lower.contains("image")
                            || lower.contains("listing") || lower.contains("gallery")) {
                        images.add(src);
                        if (images.size() >= 3) break;
                    }
                }
            }

            return images;
        } catch (Exception e) {
            log.debug("Failed to fetch og:image from {}: {}", url, e.getMessage());
            return List.of();
        }
    }
}
