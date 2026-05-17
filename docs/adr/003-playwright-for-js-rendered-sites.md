# ADR-003: Playwright for JS-Rendered Sites

**Status:** Accepted  
**Date:** 2026-05-16  
**Context:** Some estate agent websites (Zoopla, Carter Jonas, Savills) render property listings via JavaScript. Jsoup (HTTP + HTML parsing) cannot execute JavaScript, so these pages return empty or skeleton HTML.

## Decision

Use Playwright for Java with a headless Chromium browser for sites marked `scraperType=js-rendered` in the MonitoredSites table. All other sites use Jsoup (HTTP fetch).

## Rationale

- Playwright provides full browser rendering including JavaScript execution, network idle detection, and DOM snapshots
- The `scraperType` field on MonitoredSite allows per-site control without code changes
- Chromium is launched lazily as a singleton — one browser instance shared across all Playwright fetches in a pipeline run
- Jsoup remains the default for the majority of sites (faster, lighter, no browser overhead)

## Implementation Notes

- **Docker base image:** `eclipse-temurin:21-jre-jammy` (Ubuntu 22.04) — Alpine was rejected because Playwright's driver is a glibc-linked Node.js binary incompatible with musl
- **System Chromium rejected:** Ubuntu's `chromium-browser` package is a snap wrapper that doesn't work in Docker containers. Playwright manages its own Chromium binary instead.
- **System dependencies:** The full Playwright Chromium dependency list for Ubuntu 22.04 is installed via apt (libnss3, libdrm2, libgbm1, etc.) — these are needed for Playwright's bundled Chromium to run
- **Sandbox flags:** `--no-sandbox` and `--disable-setuid-sandbox` are passed to Chromium launch args (required when running as root in a container)
- **Fargate sizing:** Task runs with 1 vCPU / 2GB RAM to accommodate Chromium memory usage alongside the JVM

## Consequences

- Docker image is larger (~300MB vs ~180MB with Alpine) due to Ubuntu base and Chromium dependencies
- Docker builds are slower due to QEMU amd64 cross-compilation on ARM Macs
- First Playwright call in a new container downloads the Chromium binary (~130MB), adding ~30s to first use
- Sites with aggressive anti-bot detection (Zoopla) still block headless Chromium despite Playwright
