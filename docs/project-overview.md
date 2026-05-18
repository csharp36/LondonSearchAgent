# LondonSearchAgent — Project Overview

## Purpose

AI-powered London rental property aggregator. Scrapes 19 estate agent websites, extracts listings via AWS Bedrock (Nova Micro), deduplicates and scores them, generates AI assessments via Claude Sonnet, and presents everything in a web dashboard with email alerts.

## Repository Type

**Monorepo** — Gradle multi-project with two subprojects:

| Part | Path | Type | Language | Framework |
|------|------|------|----------|-----------|
| Application | `app/` | Web + Backend | Java 21 | Spring Boot 3.5.14 |
| Infrastructure | `infra/` | IaC | Java 21 | AWS CDK 2.248.0 |

## Architecture Pattern

Server-rendered web application with an embedded AI pipeline. The Spring Boot app serves both the web UI (Thymeleaf + HTMX) and the scraping/extraction pipeline. Infrastructure is defined as code using AWS CDK (Java).

## Technology Stack Summary

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Java | 21 (virtual threads) |
| Framework | Spring Boot | 3.5.14 |
| Template Engine | Thymeleaf + HTMX | 2.0.4 (CDN) |
| Styling | Tailwind CSS | CDN |
| Database | AWS DynamoDB | On-demand |
| AI (Extraction) | Amazon Nova Micro | v1:0 |
| AI (Assessment) | Claude Sonnet | 4.6 (cross-region) |
| HTML Parsing | Jsoup | 1.18.1 |
| Browser Automation | Playwright | 1.52.0 |
| Cloud | AWS (ECS Fargate, DynamoDB, S3, SES, Bedrock) | — |
| IaC | AWS CDK | 2.248.0 |
| Build | Gradle (Kotlin DSL) | 9.5.1 |
| Container | Docker (amd64 cross-compile) | — |

## Pipeline Flow

```
MonitoredSites (DynamoDB)
  -> URL expansion ({area}, {rightmoveCode}, pagination)
  -> Fetch (Jsoup HTTP or Playwright for JS-rendered)
  -> Strip HTML boilerplate (scripts, styles, SVGs)
  -> Extract (Bedrock Nova Micro — JSON from HTML, 300KB window)
  -> Resolve relative URLs to absolute
  -> Sanitize hallucinated listing URLs
  -> Validate images (HTTP HEAD, strip broken)
  -> Enrich images (og:image fallback)
  -> Normalize (address, price pw/pa/pcm, dates)
  -> Detect fake addresses (8 regex patterns)
  -> Deduplicate (Jaccard + Levenshtein, threshold 0.6)
  -> Score (structured 60% + AI 40%)
  -> AI Assessment (Claude Sonnet via Bedrock)
  -> Save to DynamoDB
  -> Email alert (SES, top 5 new properties with smart link)
```

## Monitored Sites

19 UK estate agent websites across three tiers:

- **Aggregators (3):** Rightmove (HTTP, paginated), OnTheMarket (HTTP), Zoopla (Playwright)
- **Tier 1 (9):** Knight Frank, Savills, Foxtons, Chestertons, Strutt & Parker, Hamptons, Winkworth, Dexters, Benham & Reeves
- **Tier 2 (4):** Wetherell, Knightsbridge Prime, Hudsons, Carter Jonas (Playwright)
- **Disabled (3):** JLL, Marsh & Parsons (403), Quintessentially (thin inventory)

## Scoring

Combined score = `structuredScore * 0.6 + aiScore * 0.4`

Structured scoring (max 100): area match (31), bedrooms (24), price range (24), bathrooms (10), furnishing (11).

## Production Environment

- **URL:** https://londonsearch.mandati.ai
- **AWS Account:** 710703498172, region us-east-1
- **Compute:** ECS Fargate (1 vCPU, 2GB RAM)
- **SSL:** Cloudflare Flexible SSL -> ALB HTTP
