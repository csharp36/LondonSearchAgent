package com.londonsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts property listings from Next.js __NEXT_DATA__ JSON embedded in HTML.
 * Many modern estate agent sites (Foxtons, etc.) are SPAs that render from JSON —
 * this extractor bypasses DOM parsing entirely and reads the structured data directly.
 */
@Service
public class NextDataExtractor {

    private static final Logger log = LoggerFactory.getLogger(NextDataExtractor.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Attempts to extract properties from __NEXT_DATA__ JSON in the HTML.
     * Returns empty list if no __NEXT_DATA__ found or no properties detected.
     */
    public List<ExtractedProperty> extract(String html, String siteName) {
        try {
            Document doc = Jsoup.parse(html);
            Element script = doc.selectFirst("script#__NEXT_DATA__");
            if (script == null) return List.of();

            JsonNode root = objectMapper.readTree(script.data());
            List<JsonNode> propertyArrays = new ArrayList<>();
            findPropertyArrays(root, propertyArrays, 0);

            if (propertyArrays.isEmpty()) return List.of();

            // Use the largest array found (most likely the main results)
            JsonNode bestArray = propertyArrays.stream()
                    .max((a, b) -> a.size() - b.size())
                    .orElse(null);
            if (bestArray == null) return List.of();

            List<ExtractedProperty> results = new ArrayList<>();
            for (JsonNode node : bestArray) {
                ExtractedProperty ep = mapToProperty(node, siteName);
                if (ep != null) results.add(ep);
            }

            log.info("NextDataExtractor: extracted {} properties from __NEXT_DATA__ for {}", results.size(), siteName);
            return results;
        } catch (Exception e) {
            log.debug("NextDataExtractor: failed for {}: {}", siteName, e.getMessage());
            return List.of();
        }
    }

    private void findPropertyArrays(JsonNode node, List<JsonNode> found, int depth) {
        if (depth > 10 || node == null) return;

        if (node.isArray() && node.size() >= 2) {
            JsonNode first = node.get(0);
            if (first.isObject() && looksLikeProperty(first)) {
                found.add(node);
                return;
            }
        }

        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> findPropertyArrays(entry.getValue(), found, depth + 1));
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                findPropertyArrays(child, found, depth + 1);
            }
        }
    }

    private boolean looksLikeProperty(JsonNode node) {
        int score = 0;
        for (String key : List.of("address", "streetName", "price", "pricePcm", "pricePerMonth",
                "bedrooms", "beds", "bathrooms", "baths", "propertyType", "searchPropertyType",
                "listingUrl", "url", "propertyId", "instructionId", "rent", "postcode", "postcodeShort")) {
            if (node.has(key)) score++;
        }
        return score >= 2;
    }

    private ExtractedProperty mapToProperty(JsonNode node, String siteName) {
        String address = buildAddress(node);
        String price = buildPrice(node);
        if (address == null && price == null) return null;

        return new ExtractedProperty(
                address,
                price,
                textField(node, "bedrooms", "beds", "bedroom_count"),
                textField(node, "bathrooms", "baths", "bathroom_count"),
                textField(node, "sqft", "floorArea", "size"),
                textField(node, "propertyType", "searchPropertyType", "type", "typeGroup"),
                textField(node, "furnishing", "furnishedState", "furnished"),
                textField(node, "description", "summary", "shortDescription"),
                buildListingUrl(node, siteName),
                buildImageUrls(node),
                null, // floorPlanUrl
                textField(node, "availableFrom", "availableDate"),
                textField(node, "agentName", "officeName", "branchName"),
                textField(node, "agentPhone", "phone", "telephone"),
                textField(node, "agentEmail", "email")
        );
    }

    private String buildAddress(JsonNode node) {
        // Try direct address field
        String addr = textField(node, "address", "fullAddress", "displayAddress");
        if (addr != null) return addr;

        // Build from parts (Foxtons pattern: streetName + postcodeShort)
        String street = textField(node, "streetName", "addressLine1", "street");
        String postcode = textField(node, "postcodeShort", "postcode", "zipCode");
        String town = textField(node, "addressTown", "town", "city");

        // Try propertyBlob.addressLine1 (Foxtons nested pattern)
        if (street == null && node.has("propertyBlob")) {
            JsonNode blob = node.get("propertyBlob");
            street = textField(blob, "addressLine1", "streetName");
            if (town == null) town = textField(blob, "addressTown", "town");
        }

        if (street == null && postcode == null) return null;

        StringBuilder sb = new StringBuilder();
        if (street != null) sb.append(street);
        if (town != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(town);
        }
        if (postcode != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(postcode);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String buildPrice(JsonNode node) {
        // Try PCM first
        String pcm = textField(node, "pricePcm", "pricePerMonth", "rent", "price");
        if (pcm != null) {
            try {
                int amount = Integer.parseInt(pcm.replaceAll("[^0-9]", ""));
                return "£" + String.format("%,d", amount) + " pcm";
            } catch (NumberFormatException e) {
                return pcm;
            }
        }
        return textField(node, "price_text", "priceText", "displayPrice");
    }

    private String buildListingUrl(JsonNode node, String siteName) {
        String url = textField(node, "url", "listingUrl", "detailUrl", "link");
        if (url != null) return url;

        // Foxtons pattern: /properties/{instructionId}/lettings/{webName}
        String id = textField(node, "instructionId", "propertyId", "id");
        String webName = textField(node, "webName", "slug");
        if (id != null && webName != null) {
            return "/properties/" + id + "/lettings/" + webName;
        } else if (id != null) {
            return "/properties/" + id;
        }
        return null;
    }

    private List<String> buildImageUrls(JsonNode node) {
        // Try direct photos array
        for (String key : List.of("photos", "images", "imageUrls", "media")) {
            JsonNode arr = node.get(key);
            if (arr != null && arr.isArray() && arr.size() > 0) {
                return extractImageSrcs(arr);
            }
        }
        // Foxtons nested: propertyBlob.assetInfo.assets.photos
        if (node.has("propertyBlob")) {
            JsonNode photos = node.at("/propertyBlob/assetInfo/assets/photos");
            if (photos != null && photos.isArray() && photos.size() > 0) {
                return extractImageSrcs(photos);
            }
        }
        return null;
    }

    private List<String> extractImageSrcs(JsonNode arr) {
        List<String> urls = new ArrayList<>();
        for (JsonNode img : arr) {
            String src = null;
            if (img.isTextual()) {
                src = img.asText();
            } else if (img.isObject()) {
                src = textField(img, "src", "url", "original", "large", "medium");
            }
            if (src != null && !src.isBlank()) {
                urls.add(src);
                if (urls.size() >= 5) break;
            }
        }
        return urls.isEmpty() ? null : urls;
    }

    private String textField(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode val = node.get(key);
            if (val != null && !val.isNull()) {
                String text = val.asText().strip();
                if (!text.isEmpty() && !"null".equals(text) && !"0".equals(text)) {
                    return text;
                }
            }
        }
        return null;
    }
}
