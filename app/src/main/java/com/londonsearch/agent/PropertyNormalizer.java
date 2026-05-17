package com.londonsearch.agent;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PropertyNormalizer {

    private static final Pattern PRICE_PATTERN = Pattern.compile("[£$]?([\\d,]+)");

    /** Patterns that indicate a hallucinated or placeholder address from AI extraction. */
    private static final List<Pattern> FAKE_ADDRESS_PATTERNS = List.of(
            // Sequential round numbers (123, 456, 789) are a strong hallucination signal
            Pattern.compile("^(123|456|789)\\s+", Pattern.CASE_INSENSITIVE),
            // Generic/fake street names
            Pattern.compile("\\b(fake|sample|example|real|test)\\s+(street|road|avenue|lane|place|way)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(tbd|tba|n/?a|unknown|placeholder)\\b", Pattern.CASE_INSENSITIVE),
            // URLs used as addresses
            Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE),
            // Numeric-only addresses (Foxtons property IDs like "5267199, SW7")
            Pattern.compile("^\\d{6,}", Pattern.CASE_INSENSITIVE),
            // Postcode + long number (Foxtons IDs embedded: "W1K 7894631")
            Pattern.compile("\\b[A-Z]{1,2}\\d[A-Z\\d]?\\s*\\d{5,}\\b", Pattern.CASE_INSENSITIVE),
            // Address is just a postcode with no street (e.g. "SW7 3AA" or "W1K, London")
            Pattern.compile("^[A-Z]{1,2}\\d[A-Z\\d]?\\s*\\d?[A-Z]{0,2}\\s*[,.]?\\s*(london)?$", Pattern.CASE_INSENSITIVE)
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

    /**
     * Normalizes date strings from UK estate agent sites to ISO format (yyyy-MM-dd).
     * Handles DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY, and ISO format.
     * Returns the original string for non-date values like "Available now".
     */
    public String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        String trimmed = dateStr.strip();

        // Already ISO format
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) return trimmed;

        // DD/MM/YYYY or DD-MM-YYYY or DD.MM.YYYY (British format)
        Matcher m = Pattern.compile("(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4})").matcher(trimmed);
        if (m.find()) {
            int first = Integer.parseInt(m.group(1));
            int second = Integer.parseInt(m.group(2));
            int year = Integer.parseInt(m.group(3));
            // UK format: DD/MM/YYYY — day first, month second
            int day = first;
            int month = second;
            // Sanity check: if month > 12, swap (might be MM/DD/YYYY)
            if (month > 12 && day <= 12) {
                month = first;
                day = second;
            }
            try {
                return LocalDate.of(year, month, day).format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                return trimmed;
            }
        }

        // Try parsing common text formats like "1 July 2026" or "1st July 2026"
        String cleaned = trimmed.replaceAll("(\\d+)(st|nd|rd|th)", "$1");
        for (String pattern : List.of("d MMMM yyyy", "d MMM yyyy", "MMMM d yyyy", "MMM d yyyy")) {
            try {
                LocalDate date = LocalDate.parse(cleaned, DateTimeFormatter.ofPattern(pattern, java.util.Locale.UK));
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {}
        }

        // Non-date string like "Available now" — return as-is
        return trimmed;
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
