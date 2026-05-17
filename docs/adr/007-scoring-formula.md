# ADR-007: Hybrid Structured + AI Scoring Formula

**Status:** Accepted  
**Date:** 2026-05-16  
**Context:** Properties need a match score to rank them by relevance to the user's search criteria. Pure rule-based scoring misses subjective quality factors; pure AI scoring is expensive and inconsistent.

## Decision

Combined score = `structuredScore × 0.6 + aiScore × 0.4`, where:

**Structured score** (max 100, deterministic):
- Area match: 31 points (target area = full, Other = 0)
- Bedrooms: 24 points (in range = full, ±1 = half, else 0)
- Price: 24 points (in range = full, ±20% = half, else 0)
- Bathrooms: 10 points (≥ min = full, else 0)
- Furnishing: 11 points (matches preference = full, else 0)
- Null values get neutral half-score

**AI score** (0–100, from Claude Sonnet):
- Prompted to evaluate location quality, transport links, value for money, and lifestyle fit
- Returns a `SCORE:` line parsed as an integer
- Called only for new properties (not on updates)

## Rationale

- 60/40 weighting ensures objective criteria dominate (prevents AI hallucination from skewing scores wildly)
- Structured scoring is free and instant; AI scoring costs ~$0.01 per property but adds subjective insight
- The AI assessment also produces a `SUMMARY:` used in the UI, making the AI call dual-purpose
- Area match gets the highest weight (31) because location is the primary search criterion

## Consequences

- AI scores tend to cluster around 30–50 for most properties, so the structured score is the primary differentiator
- Properties with null fields (missing bedrooms, price) get inflated neutral scores rather than being penalized
- The formula may need retuning as real usage patterns emerge
