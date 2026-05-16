package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.repository.PropertyRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class DeduplicationService {

    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.9;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.6;

    private final PropertyRepository propertyRepository;

    /** Package-private no-arg constructor for unit tests (no Spring context). */
    DeduplicationService() {
        this.propertyRepository = null;
    }

    public DeduplicationService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /**
     * Returns a similarity score between 0.0 and 1.0 combining:
     * - 60% Jaccard token similarity
     * - 40% Levenshtein ratio
     */
    public double addressSimilarity(String addr1, String addr2) {
        if (addr1 == null || addr2 == null) {
            return 0.0;
        }
        String a = addr1.toLowerCase().trim();
        String b = addr2.toLowerCase().trim();

        double jaccard = tokenSimilarity(a, b);
        double levenshtein = levenshteinRatio(a, b);

        return 0.6 * jaccard + 0.4 * levenshtein;
    }

    /** Returns true when score >= 0.9 (high confidence duplicate). */
    public boolean isHighConfidenceMatch(double score) {
        return score >= HIGH_CONFIDENCE_THRESHOLD;
    }

    /** Returns true when 0.6 <= score < 0.9 (medium confidence, worth reviewing). */
    public boolean isMediumConfidenceMatch(double score) {
        return score >= MEDIUM_CONFIDENCE_THRESHOLD && score < HIGH_CONFIDENCE_THRESHOLD;
    }

    /**
     * Scans all properties and returns the best fuzzy match above the medium threshold,
     * or empty if none found.
     */
    public Optional<DedupMatch> findMatch(String normalizedAddress) {
        if (propertyRepository == null || normalizedAddress == null) {
            return Optional.empty();
        }

        return propertyRepository.findAll().stream()
                .map(p -> {
                    double confidence = addressSimilarity(normalizedAddress, p.getNormalizedAddress());
                    return new DedupMatch(p, confidence);
                })
                .filter(m -> isMediumConfidenceMatch(m.confidence()) || isHighConfidenceMatch(m.confidence()))
                .max((a, b) -> Double.compare(a.confidence(), b.confidence()));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Jaccard similarity on word tokens (split on whitespace, commas, dots, slashes). */
    private double tokenSimilarity(String a, String b) {
        Set<String> tokensA = tokenize(a);
        Set<String> tokensB = tokenize(b);

        if (tokensA.isEmpty() && tokensB.isEmpty()) {
            return 1.0;
        }
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        return (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String s) {
        String[] parts = s.split("[\\s,./]+");
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    /** Levenshtein similarity ratio: 1.0 - (distance / max(len(a), len(b))). */
    private double levenshteinRatio(String a, String b) {
        int distance = levenshteinDistance(a, b);
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1.0;
        }
        return 1.0 - (double) distance / maxLen;
    }

    private int levenshteinDistance(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();

        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(prev[j] + 1, curr[j - 1] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[lenB];
    }

    /** Holds a candidate duplicate property and the confidence score. */
    public record DedupMatch(Property property, double confidence) {}
}
