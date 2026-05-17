# ADR-010: UK Date Normalization to ISO Format

**Status:** Accepted  
**Date:** 2026-05-17  
**Context:** UK estate agent websites display dates in DD/MM/YYYY format (British convention). The Bedrock extraction LLM sometimes passes these through as-is, and they were being stored as raw strings without normalization. This caused "01/07/2026" (July 1) to appear as if it were January 7.

## Decision

1. The Bedrock extraction prompt now requests ISO format dates (YYYY-MM-DD) and instructs the LLM to convert DD/MM/YYYY
2. A `normalizeDate()` method in `PropertyNormalizer` provides a safety net, parsing DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY, and text formats ("1 July 2026", "1st July 2026") into ISO format
3. Non-date strings like "Available now" pass through unchanged
4. The `availableFrom` field remains a `String` type (not `LocalDate`) to accommodate non-date values

## Rationale

- Belt-and-suspenders: prompt engineering reduces the problem, code normalization eliminates it
- UK-first assumption: if the day field is ≤12 and could be ambiguous, assume DD/MM/YYYY (British format) since all source websites are UK-based
- If month > 12, automatically swap to MM/DD/YYYY interpretation (handles edge cases where the LLM outputs American format)
- Storing as ISO string (`2026-07-01`) rather than the original format ensures consistent display and sorting

## Consequences

- Existing properties in the database still have old-format dates until re-scraped
- The `availableFrom` field is a string, not a date type — no date-based queries or sorting on this field in DynamoDB
- The normalizer cannot distinguish DD/MM/YYYY from MM/DD/YYYY when both values are ≤12 (e.g., "05/06/2026") — defaults to DD/MM (UK)
