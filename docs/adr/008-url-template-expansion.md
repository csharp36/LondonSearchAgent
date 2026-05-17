# ADR-008: Parameterized URL Templates for Monitored Sites

**Status:** Accepted  
**Date:** 2026-05-16  
**Context:** Each estate agent website has a different URL pattern for property search results. URLs need to include the user's search criteria (area, bedrooms, price range) and generate multiple URLs for multi-area searches.

## Decision

Store URL templates with placeholders in the MonitoredSites DynamoDB table. The pipeline expands these at runtime using the active SearchConfig:

- `{area}` → generates one URL per area in the config (lowercased, hyphenated)
- `{minBeds}`, `{maxBeds}`, `{minPrice}`, `{maxPrice}` → from SearchConfig fields
- `{rightmoveCode}` → special case for Rightmove's REGION identifiers (Mayfair=87523, Marylebone=87522, South Kensington=85252)

Example: `https://www.foxtons.co.uk/properties-to-rent/{area}/{minBeds}-bedrooms` with areas [Mayfair, Marylebone] expands to two URLs.

## Rationale

- URL patterns are stored in DynamoDB, not code — sites can be added/modified without redeploying
- The DataSeeder provides the initial 19 sites with researched URL patterns
- Per-area expansion means targeted searches instead of broad "all London" queries, improving extraction quality
- Rightmove's non-standard region codes are handled as a special case rather than adding generic mapping infrastructure

## Consequences

- URL templates were initially seeded with incorrect patterns and had to be updated (Hamptons 404, Winkworth 404, Foxtons marked js-rendered when HTTP works)
- The DataSeeder now always upserts on boot to ensure URL corrections are applied on deploy
- Sites that don't support URL-based filtering (Wetherell, Knightsbridge Prime) use static URLs without placeholders
- Adding a new area requires updating both the SearchConfig and potentially the Rightmove code map
