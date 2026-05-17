# ADR-005: Address-Based Deduplication Strategy

**Status:** Accepted  
**Date:** 2026-05-16  
**Context:** The same property appears on multiple estate agent websites with slightly different address formats, postcodes, and descriptions. Without deduplication, the database fills with duplicates and the UI becomes unusable.

## Decision

Deduplicate properties using a combined similarity score on normalized addresses: `0.6 × Jaccard(tokens) + 0.4 × Levenshtein ratio`. Match threshold is 0.6. Scores ≥0.9 are high-confidence matches; 0.6–0.9 are logged as medium-confidence.

## Rationale

- Jaccard token similarity handles word reordering ("42 Baker Street, London W1U" vs "Baker Street 42, W1U London")
- Levenshtein ratio handles minor spelling differences and abbreviations ("St" vs "Street")
- The 60/40 weighting favors token overlap (more robust for addresses) while still penalizing character-level differences
- Threshold 0.6 is intentionally permissive to avoid duplicates — false positives (merging different properties) are less harmful than false negatives (showing duplicates)
- When a match is found, a new Listing is added to the existing Property rather than creating a duplicate Property

## Consequences

- Properties at similar addresses but different flats (e.g., Flat 1 and Flat 2 at the same building) may be incorrectly merged
- Each new property is compared against all existing properties (O(n) scan) — acceptable at current scale (<200 properties) but will need indexing if the database grows significantly
- Medium-confidence matches (0.6–0.9) are logged for manual review but automatically merged
