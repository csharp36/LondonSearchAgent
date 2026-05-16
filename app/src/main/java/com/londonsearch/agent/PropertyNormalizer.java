package com.londonsearch.agent;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PropertyNormalizer {

    private static final Pattern PRICE_PATTERN = Pattern.compile("[£$]?([\\d,]+)");

    /** Patterns that indicate a hallucinated or placeholder address from AI extraction. */
    private static final List<Pattern> FAKE_ADDRESS_PATTERNS = List.of(
            Pattern.compile("\\b123\\s+(fake|main|test|sample|example)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b456\\s+(fake|main|test|sample|example|high)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b789\\s+(fake|main|test|sample|example)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bfake\\s+street\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsample\\s+(street|road|avenue|lane)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bexample\\s+(street|road|avenue|lane)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(tbd|tba|n/?a|unknown|placeholder)\\b", Pattern.CASE_INSENSITIVE)
    );

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

    /**
     * Returns true if the address looks like a hallucinated placeholder
     * rather than a real London address.
     */
    public boolean isFakeAddress(String address) {
        if (address == null || address.isBlank()) return true;
        for (Pattern p : FAKE_ADDRESS_PATTERNS) {
            if (p.matcher(address).find()) return true;
        }
        // Very short addresses (< 10 chars) are almost certainly garbage
        if (address.strip().length() < 10) return true;
        return false;
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
