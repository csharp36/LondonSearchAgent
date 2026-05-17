# ADR-004: Server-Side Image Proxy for Hotlink Protection

**Status:** Accepted  
**Date:** 2026-05-17  
**Context:** Property images extracted from estate agent websites often fail to load in the browser due to hotlink protection (HTTP 403), hallucinated URLs from the LLM (HTTP 404), or malformed URLs (double base URL prepending).

## Decision

Proxy all property images through a server-side endpoint (`GET /api/image?url=...`) that fetches images with the correct `Referer` header and serves them to the browser.

## Rationale

- **Hotlink protection bypass:** OnTheMarket and other sites return 403 when images are loaded from a different domain. The proxy sends a `Referer` header matching the source domain, which satisfies most hotlink checks.
- **Consistent fallback:** When an image URL returns an error (404, 403, timeout), the proxy returns 404, triggering the frontend's `onerror` handler which shows an address-text placeholder instead of a broken image icon.
- **Caching:** Images are served with `Cache-Control: max-age=86400, public` (24 hours), reducing repeated fetches for the same image.
- **URL sanitization:** The pipeline sanitizes image URLs at extraction time — fixing double-base-URL issues (e.g., `site.com//cdn.com/path` → `cdn.com/path`) and filtering out placeholder/pixel URLs.

## Consequences

- All image traffic passes through the Fargate task, adding bandwidth and CPU load
- The proxy is publicly accessible (`/api/image` is in the Security permitAll list) — could be abused as an open proxy. Consider adding URL allowlisting in the future.
- Browser caching mitigates repeated fetches, but the first load of a page with many images creates a burst of outbound requests from the server
- LLM-hallucinated image URLs (Dexters, Rightmove) still 404 — the proxy can't fix URLs that never existed
