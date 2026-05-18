package com.londonsearch.agent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CssSelectorExtractor {

    private static final Logger log = LoggerFactory.getLogger(CssSelectorExtractor.class);

    /**
     * Extract properties from raw HTML using a map of CSS selectors.
     *
     * @param html      Raw HTML page content
     * @param selectors Map of field name → CSS selector. Selectors may end with
     *                  {@code  @attrName} to extract an attribute instead of text.
     *                  {@code listingContainer} is the repeating wrapper element.
     *                  {@code imageUrl} with an {@code @} attribute suffix collects
     *                  ALL matching elements into the imageUrls list.
     * @return List of extracted properties (never null)
     */
    public List<ExtractedProperty> extract(String html, Map<String, String> selectors) {
        if (selectors == null || selectors.isEmpty()) {
            return List.of();
        }

        String containerSelector = selectors.get("listingContainer");
        if (containerSelector == null || containerSelector.isBlank()) {
            return List.of();
        }

        Document doc = Jsoup.parse(html == null ? "" : html);
        Elements containers = doc.select(containerSelector);

        List<ExtractedProperty> results = new ArrayList<>();

        for (Element container : containers) {
            String address      = extractText(container, selectors.get("address"));
            String price        = extractText(container, selectors.get("price"));

            // Skip completely empty cards
            if (address == null && price == null) {
                continue;
            }

            String bedrooms     = extractText(container, selectors.get("bedrooms"));
            String bathrooms    = extractText(container, selectors.get("bathrooms"));
            String sqft         = extractText(container, selectors.get("sqft"));
            String propertyType = extractText(container, selectors.get("propertyType"));
            String furnishing   = extractText(container, selectors.get("furnishing"));
            String description  = extractText(container, selectors.get("description"));
            String listingUrl   = extractText(container, selectors.get("listingUrl"));
            String floorPlanUrl = extractText(container, selectors.get("floorPlanUrl"));
            String availableFrom = extractText(container, selectors.get("availableFrom"));
            String agentName    = extractText(container, selectors.get("agentName"));
            String agentPhone   = extractText(container, selectors.get("agentPhone"));
            String agentEmail   = extractText(container, selectors.get("agentEmail"));

            // imageUrl collects ALL matching elements
            List<String> imageUrls = extractAll(container, selectors.get("imageUrl"));

            results.add(new ExtractedProperty(
                    address, price, bedrooms, bathrooms, sqft,
                    propertyType, furnishing, description, listingUrl,
                    imageUrls, floorPlanUrl, availableFrom,
                    agentName, agentPhone, agentEmail
            ));
        }

        log.info("CssSelectorExtractor: extracted {} listings from {} containers",
                results.size(), containers.size());

        return results;
    }

    /**
     * Extract a single value from the first matching element.
     * Returns null if the selector is missing, matches nothing, or the value is blank.
     */
    private String extractText(Element container, String selectorSpec) {
        if (selectorSpec == null || selectorSpec.isBlank()) {
            return null;
        }

        String[] parts = splitSelectorSpec(selectorSpec);
        String cssSelector = parts[0];
        String attrName    = parts[1]; // null means text content

        Element el = container.selectFirst(cssSelector);
        if (el == null) {
            return null;
        }

        String value = (attrName != null) ? el.attr(attrName) : el.text();
        return value.isBlank() ? null : value;
    }

    /**
     * Extract values from ALL matching elements (used for imageUrl).
     * Returns an empty list if the selector is missing or matches nothing.
     */
    private List<String> extractAll(Element container, String selectorSpec) {
        if (selectorSpec == null || selectorSpec.isBlank()) {
            return List.of();
        }

        String[] parts = splitSelectorSpec(selectorSpec);
        String cssSelector = parts[0];
        String attrName    = parts[1];

        Elements elements = container.select(cssSelector);
        List<String> values = new ArrayList<>();
        for (Element el : elements) {
            String value = (attrName != null) ? el.attr(attrName) : el.text();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * Split a selector spec into [cssSelector, attrName].
     * attrName is null when no {@code @attr} suffix is present.
     * Example: {@code ".link @href"} → {@code [".link", "href"]}
     */
    private String[] splitSelectorSpec(String selectorSpec) {
        int atIdx = selectorSpec.lastIndexOf(" @");
        if (atIdx >= 0) {
            String css  = selectorSpec.substring(0, atIdx).strip();
            String attr = selectorSpec.substring(atIdx + 2).strip(); // skip " @"
            return new String[]{css, attr.isEmpty() ? null : attr};
        }
        return new String[]{selectorSpec.strip(), null};
    }
}
