package com.londonsearch.agent;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PropertyNormalizer {

    private static final Pattern PRICE_PATTERN = Pattern.compile("[£$]?([\\d,]+)");

    private static final Map<String, String> POSTCODE_AREA_MAP = Map.ofEntries(
            Map.entry("W1K", "Mayfair"),
            Map.entry("W1J", "Mayfair"),
            Map.entry("W1S", "Mayfair"),
            Map.entry("W1U", "Marylebone"),
            Map.entry("W1W", "Marylebone"),
            Map.entry("W1G", "Marylebone"),
            Map.entry("W1H", "Marylebone"),
            Map.entry("NW1", "Marylebone"),
            Map.entry("SW7", "South Kensington"),
            Map.entry("SW3", "South Kensington")
    );

    public Integer parsePricePerMonth(String priceStr) {
        if (priceStr == null || priceStr.isBlank()) return null;
        Matcher matcher = PRICE_PATTERN.matcher(priceStr);
        if (!matcher.find()) return null;
        try {
            int amount = Integer.parseInt(matcher.group(1).replace(",", ""));
            String lower = priceStr.toLowerCase();
            if (lower.contains("pw") || lower.contains("per week") || lower.contains("p/w")) {
                return (int) Math.round(amount * 52.0 / 12.0);
            } else if (lower.contains("pa") || lower.contains("per annum") || lower.contains("p/a")) {
                return amount / 12;
            }
            return amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String normalizeAddress(String address) {
        if (address == null) return null;
        return address.strip().toLowerCase();
    }

    public String classifyArea(String address) {
        if (address == null) return "Other";
        String upper = address.toUpperCase();
        for (Map.Entry<String, String> entry : POSTCODE_AREA_MAP.entrySet()) {
            if (upper.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        String lower = address.toLowerCase();
        if (lower.contains("mayfair")) return "Mayfair";
        if (lower.contains("marylebone")) return "Marylebone";
        if (lower.contains("south kensington")) return "South Kensington";
        return "Other";
    }

    public Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.replace(",", "").strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
