# CSS Selector Extraction with LLM-as-Compiler

**Date:** 2026-05-18
**Status:** Approved

## Problem

The current pipeline sends every fetched page to Bedrock Nova Micro for LLM extraction. This is expensive (50+ API calls per scan), slow (10s per call), subject to hallucination (fake URLs, addresses), and limited by the 300KB token window (can't see all listings on large pages).

## Solution

Use a frontier LLM (Claude Sonnet) once per site to generate CSS selectors that Jsoup can run for free on every subsequent scan. The LLM becomes a compiler — expensive once to produce a cheap artifact, then the artifact runs for free until it breaks.

## Extraction Strategy — 3-Tier Fallback

```
Tier 1: CSS Selectors (free, fast, deterministic, full page)
  → Site has stored selectors? Run them via Jsoup.
  → Results valid? Done.
  → Breakage detected? Escalate to Tier 2.

Tier 2: Selector Generation (one-time frontier model call)
  → Send raw HTML to Claude Sonnet with selector generation prompt.
  → Validate candidate selectors against current HTML in memory.
  → Valid? Store selectors on MonitoredSite, return results.
  → Invalid? Escalate to Tier 3.

Tier 3: LLM Fallback (current behavior, per-page Nova Micro)
  → BedrockExtractor.extract() with stripped HTML.
  → Existing degraded path, works but slow/expensive/truncated.
```

## Breakage Detection

CSS extraction is considered "broken" if:
- Zero listings extracted
- All addresses are null/empty
- All prices are null/empty

## Validate-Before-Overwrite

When generating new selectors, the system does NOT overwrite stored selectors until the candidates prove they work:
1. Generate candidate selectors from frontier model
2. Run them against the current HTML (in memory)
3. Check extraction validity (>0 listings with addresses and prices)
4. Only if valid: store to MonitoredSite and use results
5. If invalid: keep existing selectors, fall back to Tier 3

This prevents bad LLM calls (e.g. site returning error page, captcha) from destroying working selectors.

## Data Model Changes

### MonitoredSite — new fields

| Field | Type | Description |
|-------|------|-------------|
| cssSelectors | Map<String,String> | CSS selector config per field |
| selectorsGeneratedAt | Instant | When selectors were last generated |
| selectorsModel | String | Model used for generation |

### Listing — new field

| Field | Type | Description |
|-------|------|-------------|
| extractionMethod | String | "css", "css-generated", or "llm-fallback" |

### CSS Selectors Schema

```json
{
  "listingContainer": ".propertyCard",
  "address": ".propertyCard-address",
  "price": ".propertyCard-priceValue",
  "bedrooms": ".property-info .beds",
  "bathrooms": ".property-info .baths",
  "sqft": ".property-info .sqft",
  "propertyType": ".property-type",
  "furnishing": ".furnishing-status",
  "description": ".propertyCard-description",
  "listingUrl": "a.propertyCard-link @href",
  "imageUrl": "img.property-img @src",
  "floorPlanUrl": null,
  "availableFrom": ".available-date",
  "agentName": ".agent-name",
  "agentPhone": ".agent-phone",
  "agentEmail": ".agent-email"
}
```

Convention: `@href` and `@src` suffixes indicate attribute extraction rather than text content.

## New Classes

### CssSelectorExtractor

- Pure Jsoup-based extraction using a selector map
- Accepts raw (unstripped) HTML — CSS selectors work on full DOM, no token window
- Implements extraction logic but NOT the `PropertyExtractor` interface (it's called explicitly, not via conditional beans)
- Returns `List<ExtractedProperty>` (same type as BedrockExtractor)

### SelectorGeneratorService

- Sends raw HTML + site name to Bedrock frontier model (Claude Sonnet)
- Prompt asks for CSS selectors in JSON format
- Parses response into selector map
- Validates by running CssSelectorExtractor against the same HTML
- Returns selectors + results if valid, empty if validation fails
- Uses existing BedrockRuntimeClient bean

## Pipeline Integration

`AgentPipelineService.processSite()` gains a new `extractWithStrategy()` method that replaces the direct `extractor.extract()` call:

```
fetch → extractWithStrategy(rawHtml, strippedHtml, site):
  ├── Tier 1: cssSelectorExtractor.extract(rawHtml, site.getCssSelectors())
  ├── Tier 2: selectorGenerator.generateAndValidate(rawHtml, site.getName())
  └── Tier 3: extractor.extract(strippedHtml, site.getName())
```

Key detail: Tiers 1 and 2 use raw HTML (full DOM). Tier 3 uses stripped HTML (for the token window). The `extractionMethod` string flows through to `saveListing()` and is stored on each Listing.

The `extractionMethod` is visible in the property detail page UI, allowing the user to identify sites that fell back to LLM extraction.

## Testing

### CssSelectorExtractorTest
- Sample HTML fixture with known listings
- Correct selectors → extracts expected data
- Wrong selectors → returns empty list
- Missing fields → partial extraction with nulls
- Null/empty selector map → returns empty

### SelectorGeneratorServiceTest
- Mock BedrockRuntimeClient
- Well-formed JSON response → valid selectors returned
- Malformed response → empty returned
- Valid selectors but invalid extraction results → rejects candidates (validate-before-overwrite)
- Bedrock exception → empty returned gracefully

## Cost Impact

| Metric | Before | After |
|--------|--------|-------|
| LLM calls per scan | ~50 (Nova Micro per page) | ~0 (CSS selectors, free) |
| LLM calls on selector break | 0 | 1 (Sonnet, one-time) |
| Extraction speed per page | ~10 seconds | ~10 milliseconds |
| Hallucination risk | Per-page | Zero (CSS extracts real DOM) |
| Token window limit | 300KB (misses listings) | None (full DOM visible) |
| Monthly Bedrock cost | ~$15-20 (4 scans/day) | ~$0.10 (occasional regeneration) |

## Success Criteria

1. Sites with generated CSS selectors extract more listings than BedrockExtractor did (full page vs truncated)
2. No hallucinated addresses or URLs from CSS-extracted listings
3. Pipeline completes faster (seconds vs minutes for extraction phase)
4. Breakage auto-heals: when a site changes, new selectors are generated and validated within the same pipeline run
5. `extractionMethod` visible per listing so degraded sites are identifiable
