# ADR-011: Extraction Strategy Evolution — From Dual-Model LLM to LLM-as-Compiler

**Date:** 2026-05-18
**Status:** Accepted
**Supersedes:** Portions of ADR-001 (Dual-Model Bedrock Strategy)

## Context

The original extraction pipeline (ADR-001) used two LLM models:
- **Nova Micro** (cheap, fast) — called on every page, every scan, to extract property listings from raw HTML
- **Claude Sonnet** (expensive, smart) — called once per new property to generate an AI assessment

This "cheap model for volume, expensive model for quality" split seemed economical. In practice, it had compounding problems that only surfaced as the system scaled from a handful of sites to 19 agents across hundreds of pages.

## Problems with Per-Page LLM Extraction

**1. Token window truncation.** Nova Micro supports 128K tokens (~340K chars). Estate agent pages are 400KB-1MB of HTML even after stripping scripts and styles. With a 300KB truncation limit, the LLM only saw the first ~70% of each page, missing listings at the bottom. Rightmove has 24 listings per page but we extracted 5-15.

**2. Hallucination.** The LLM invented data that didn't exist in the HTML — fake listing URLs (Zoopla's sequential 19-digit IDs), fabricated addresses, and imaginary image URLs. Each required a downstream filter (fake address detection, hallucinated URL sanitization, image validation via HTTP HEAD), turning the pipeline into a series of compensating controls for a fundamentally unreliable extraction step.

**3. Cost scales linearly with usage.** Every page, every scan, every day required a Bedrock API call. With 12 Rightmove pages + 30-40 other site pages × 4 scans/day, the extraction step alone cost ~$15-20/month and took 10+ minutes of wall time (10 seconds per LLM call, sequential).

**4. Non-determinism.** The same HTML produced different results on different runs. Properties appeared and disappeared between scans not because listings changed, but because the LLM's extraction was inconsistent.

## The Insight

During a code review session, we asked: "In a world where AI did not exist, how would you build this?" The answer was obvious — CSS selectors. Every web scraper before 2023 used them. They're free, instant, deterministic, and see the entire DOM.

The follow-up question was more interesting: "Could we use AI to *write* the CSS selectors instead of doing the extraction itself?" This reframes the LLM from a runtime dependency (called on every page) to a compile-time tool (called once per site to produce a reusable artifact).

## Decision

Replace per-page LLM extraction with a 4-tier strategy:

### Tier 0: `__NEXT_DATA__` JSON Extraction
Many modern estate agent sites (Foxtons, and potentially Zoopla) are Next.js SPAs that embed all property data as JSON in a `<script id="__NEXT_DATA__">` tag. This is structured data — no parsing ambiguity, no hallucination, no LLM needed. A recursive JSON tree search finds arrays of objects with property-like fields (address, price, bedrooms) and maps them directly to our data model.

**Cost:** Zero. **Speed:** Microseconds. **Reliability:** Perfect (it's the site's own data).

### Tier 1: Stored CSS Selectors
For sites with traditional HTML, CSS selectors stored on the MonitoredSite record are run via Jsoup against the full (untruncated) DOM. Every listing on the page is extracted, deterministically, for free.

**Cost:** Zero. **Speed:** Milliseconds. **Reliability:** Perfect until the site redesigns.

### Tier 2: Frontier Model Selector Generation
When CSS selectors don't exist yet (first run) or break (site redesign detected by the breakage heuristic), a frontier model (Claude Sonnet) is called once to generate new selectors.

The key innovation: **smart HTML sampling**. Instead of dumping 200KB of raw HTML into the prompt (which caused the LLM to guess at selectors), the system uses Jsoup to find repeating card elements in the DOM, then sends 2-3 sample cards (~10KB) to the LLM. The LLM sees focused, real DOM structure and produces accurate selectors using actual CSS classes from the page.

A **validate-before-overwrite** gate runs the generated selectors against the current HTML before storing them. If they don't extract valid results (properties with addresses and prices), they're rejected and the system falls back to Tier 3. This prevents bad LLM calls (error pages, captchas, hallucinated selectors) from destroying working selectors.

**Cost:** One Sonnet call per site, amortized over weeks/months. **Speed:** ~15 seconds (one-time). **Reliability:** Validated before use.

### Tier 3: Per-Page LLM Extraction (Legacy Fallback)
The original Nova Micro extraction remains as a last resort. If `__NEXT_DATA__` isn't present, no CSS selectors exist, and selector generation fails, the system falls back to the original behavior — stripped HTML, 300KB truncation, per-page LLM calls.

**Cost:** Per-page API call. **Speed:** ~10 seconds per page. **Reliability:** Subject to truncation and hallucination.

## The Shift in Thinking

The original architecture assumed the right question was "which model is cheapest for high-volume extraction?" The answer was Nova Micro — cheap per call, but the calls never stop.

The better question turned out to be "how do I avoid making the calls at all?" The expensive model (Sonnet) called once produces an artifact (CSS selectors) that eliminates the cheap model's calls entirely. The most cost-effective extraction is the one that doesn't use an LLM.

```
Before:  Every page × Every scan × Every day = Nova Micro call
After:   Once per site = Sonnet call → Free forever (until site changes)
```

This is the "LLM-as-compiler" pattern: use AI to generate code (selectors), not to be the code.

## Results

First scan comparison after implementing the full 4-tier strategy:

| Metric | LLM-Only (Before) | 4-Tier (After) |
|--------|-------------------|----------------|
| Total new properties | 95 | 247 |
| Rightmove extraction | 26 | 143 |
| Foxtons extraction | 7 | 31 |
| LLM calls per scan | ~50 | ~0 (after first run) |
| Extraction time | ~10 minutes | ~30 seconds |
| Hallucinated URLs | Common | Zero (CSS/JSON) |
| Cost per scan | ~$0.15 | ~$0.00 |

## Tracking

Each Listing records its `extractionMethod` field: `"json"`, `"css"`, `"css-generated"`, or `"llm-fallback"`. This makes it visible in the UI which sites are on which tier, and whether any are falling back to the legacy LLM path.

## Consequences

- CSS selectors are brittle to site redesigns, but the auto-healing (Tier 2) regenerates them within the same pipeline run
- The `__NEXT_DATA__` JSON structure is site-specific; the recursive search heuristic works for Foxtons but may need tuning for other Next.js sites
- Sites that are neither traditional HTML nor Next.js (e.g. heavy client-side rendering without `__NEXT_DATA__`) will still fall through to Tier 3
- The validate-before-overwrite gate means bad selector generations are safe (they don't break anything) but also mean some sites may never get selectors if the LLM consistently fails for their DOM structure
