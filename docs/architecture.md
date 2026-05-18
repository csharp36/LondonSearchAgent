# LondonSearchAgent — Architecture

## Executive Summary

A Spring Boot 3.5.14 monolith serving a Thymeleaf+HTMX web UI and an AI-powered property scraping pipeline. Uses AWS Bedrock for LLM extraction (Nova Micro) and assessment (Claude Sonnet), DynamoDB for persistence, and ECS Fargate for hosting. The application uses Java 21 virtual threads for async pipeline execution.

## System Architecture

```
                    Cloudflare (SSL)
                         |
                    ALB (HTTP:80)
                         |
                 ECS Fargate Task
                  ┌──────────────┐
                  │ Spring Boot  │
                  │   (Java 21)  │
                  │              │
                  │  Web UI      │──── Thymeleaf + HTMX + Tailwind
                  │  Pipeline    │──── Virtual Thread (async)
                  │  REST API    │──── /agent/*, /api/image
                  └──────┬───────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
     DynamoDB (5)    Bedrock         SES
     - Properties    - Nova Micro    - Email alerts
     - Listings      - Claude Sonnet
     - SearchConfigs
     - MonitoredSites
     - Alerts
```

## Application Layers

### Controller Layer (`controller/`)
6 controllers handling web UI routes and REST endpoints. Authentication via Spring Security with single-password auth and smart-link token bypass.

| Controller | Endpoints | Auth |
|-----------|-----------|------|
| FeedController | `GET /` | Yes |
| PropertyController | `GET/POST /property/{id}` | Yes |
| ConfigController | `GET/POST /config/**` | Yes |
| AccountController | `GET /config/account` | Yes |
| LoginController | `GET /login` | No |
| ImageProxyController | `GET /api/image` | No (domain allowlist) |

### Agent Layer (`agent/`)
Core pipeline orchestration. 18 classes implementing fetch -> extract -> normalize -> dedup -> score -> alert.

**Key classes:**
- `AgentPipelineService` — Main orchestrator. Processes sites, expands URL templates, coordinates all pipeline stages.
- `AgentController` — REST API for triggering pipeline (`/agent/run-async`, `/agent/progress`)
- `PipelineProgressService` — Async execution tracking via virtual threads and AtomicReference
- `BedrockExtractor` / `MockExtractor` — LLM-based HTML-to-JSON extraction (conditional beans)
- `BedrockIntelligence` / `MockIntelligence` — AI property assessment (conditional beans)
- `SiteFetcher` / `PlaywrightFetcher` — HTTP and headless browser fetching
- `PropertyNormalizer` — Price parsing (pw/pa/pcm), address normalization, area classification, fake address detection
- `DeduplicationService` — Fuzzy matching (0.6 Jaccard + 0.4 Levenshtein)
- `StructuredScorer` — Rule-based scoring against SearchConfig criteria
- `ImageValidator` / `ImageEnricher` — Image URL validation and og:image fallback
- `CostGuard` — Pipeline kill switch

### Alert Layer (`alert/`)
Email alerting with passwordless authentication.
- `SesAlertService` / `MockAlertService` — SES email with HTML template
- `SmartLinkService` — Generates 24-hour UUID tokens for passwordless access
- `AlertController` — Token validation and session creation

### Repository Layer (`repository/`)
DynamoDB Enhanced Client repositories. All use `@DynamoDbBean` annotated models with `putItem`/`getItem`/`scan`/`query` operations. `TableInitializer` creates tables on local/test profiles.

### Model Layer (`model/`)
5 DynamoDB entities: Property (with 2 GSIs), Listing (composite key), SearchConfig, MonitoredSite, AlertRecord.

### Configuration (`config/`)
- `SecurityConfig` — Single-password auth, CSRF with cookie repository, public agent/alert endpoints
- `DynamoDbConfig` — Client setup with local endpoint override support
- `BedrockConfig` — Conditional Bedrock client (only when extractor=bedrock)

## Dual-Mode Architecture

`@ConditionalOnProperty(name = "app.agent.extractor")` switches between:
- `mock` (default, local) — MockExtractor, MockIntelligence, MockAlertService
- `bedrock` (prod) — BedrockExtractor, BedrockIntelligence, SesAlertService

## Data Flow

```
MonitoredSite.searchUrlTemplate
  + SearchConfig.areas/beds/price
  = Expanded URLs (with pagination for Rightmove)
      |
      v
  SiteFetcher/PlaywrightFetcher → raw HTML
      |
      v
  SiteFetcher.stripBoilerplate() → stripped HTML (~60% smaller)
      |
      v
  BedrockExtractor.extract() → List<ExtractedProperty> (truncated to 300KB)
      |
      v
  resolveRelativeUrls() → absolute URLs using site baseUrl
      |
      v
  sanitizeListingUrls() → nullify hallucinated IDs (10+ digits)
      |
      v
  ImageValidator.validate() → strip broken image URLs (HTTP HEAD)
      |
      v
  ImageEnricher.enrich() → og:image fallback for imageless listings
      |
      v
  processExtractedProperties():
    - normalizeAddress() → lowercase, trimmed
    - isFakeAddress() → 8 regex patterns filter hallucinations
    - parsePricePerMonth() → pw/pa/pcm conversion
    - findMatch() → Jaccard+Levenshtein dedup (threshold 0.6)
    - If match: saveListing (add source to existing property)
    - If new: createProperty → scoreAndAssess → saveListing
      |
      v
  scoreAndAssess():
    - StructuredScorer.score() → 0-100 (area/beds/price/baths/furnishing)
    - BedrockIntelligence.assess() → SUMMARY + SCORE from Claude Sonnet
    - Combined: structured * 0.6 + AI * 0.4
      |
      v
  sendAlert():
    - Top 5 properties by score
    - SmartLinkService.generateSmartLink() → 24h UUID token
    - SesAlertService.sendNewPropertiesAlert() → HTML email via SES
```

## Testing Architecture

15 test classes, 93 test cases:

| Tier | Pattern | Count | Examples |
|------|---------|-------|---------|
| Unit | Plain JUnit 5, no Spring | 7 | DeduplicationServiceTest, PropertyNormalizerTest, BedrockExtractorTest (Mockito) |
| Integration | @SpringBootTest + DynamoDB Local | 5 | AgentPipelineServiceTest, PropertyRepositoryTest |
| Controller | @SpringBootTest + @AutoConfigureMockMvc | 2 | SecurityTest, FeedControllerTest |
| Context | @SpringBootTest | 1 | LondonSearchApplicationTests |

Test infrastructure: DynamoDB Local (docker-compose), separate `Test_*` table names, `@ActiveProfiles("test")`, mock extractor beans.
