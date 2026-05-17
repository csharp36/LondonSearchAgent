# LondonSearchAgent

An AI-powered London rental property aggregator. Scrapes 19 estate agent websites, extracts listings via AWS Bedrock (Nova Micro), deduplicates and scores them, generates AI assessments via Claude Sonnet, and presents everything in a web dashboard.

## Quick Start

```bash
# Local development (DynamoDB Local + mock extractor)
docker compose up -d
./gradlew :app:bootRun --args='--spring.profiles.active=local'
# App at http://localhost:8080, password: changeme

# With real Bedrock (needs AWS credentials)
SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/*.jar \
  --app.agent.extractor=bedrock \
  --app.agent.bedrock.region=us-east-1
```

## Production

- **URL:** https://londonsearch.mandati.ai (Cloudflare Flexible SSL → ALB HTTP)
- **AWS:** Account 710703498172, region us-east-1, IAM user `londonsearchagent`
- **Password:** Set via `LONDONSEARCH_PASSWORD` env var at CDK deploy time

### Deploying

```bash
./gradlew :app:clean :app:bootJar
rm -rf infra/cdk.out
export CDK_DEFAULT_ACCOUNT=710703498172 CDK_DEFAULT_REGION=us-east-1
cdk deploy LondonSearch-Portal --exclusively --require-approval never
```

- Always use `--exclusively` to avoid the Network stack (has dirty rollback state from a reverted Bedrock VPC endpoint change)
- Always `rm -rf infra/cdk.out` first if you get lock errors
- Always rebuild the jar before deploying — CDK hashes the Docker build context including the jar
- Docker builds are slow (~5 min) due to amd64 cross-compile on ARM Mac (QEMU)

## Architecture

### CDK Stacks (infra/)

| Stack | Purpose |
|-------|---------|
| **Network** | VPC, 2 AZs, no NAT, gateway endpoints for S3/DynamoDB, interface endpoints for ECR/Logs |
| **Data** | 5 DynamoDB tables (Properties, Listings, SearchConfigs, MonitoredSites, Alerts) + S3 images bucket |
| **Portal** | ECS Fargate (1 vCPU, 2GB), ALB, public subnet with public IP, Docker from `app/` |
| **Schedule** | 4 EventBridge rules (every 6h) — rules exist but have no targets wired yet |

### Pipeline Flow (AgentPipelineService)

```
MonitoredSites (DynamoDB) → URL expansion ({area}, {minBeds}, etc.)
  → Fetch (Jsoup HTTP or Playwright for js-rendered)
  → Extract (Bedrock Nova Micro — JSON array from HTML)
  → Image enrichment (og:image fallback for missing images)
  → Normalize (address, price, dates)
  → Deduplicate (Jaccard + Levenshtein, threshold 0.6)
  → Score (structured 60% + AI 40%)
  → AI Assessment (Claude Sonnet via Bedrock)
  → Save to DynamoDB
  → Email alert (SES, top 5 new properties with smart link)
```

### Bedrock Models

- **Extraction:** `amazon.nova-micro-v1:0` — cheap, high volume, parses HTML to JSON
- **Assessment:** `us.anthropic.claude-sonnet-4-6` — cross-region inference profile, scores and summarizes properties

### Dual-Mode Architecture

The `EXTRACTOR_TYPE` env var (`mock` or `bedrock`) controls which Spring beans load via `@ConditionalOnProperty`:
- `mock` (default, local): MockExtractor, MockIntelligence, MockAlertService
- `bedrock` (prod): BedrockExtractor, BedrockIntelligence, SesAlertService

## Tech Stack

- Java 21 with virtual threads
- Spring Boot 3.5.14 (Thymeleaf, Security, Actuator)
- AWS CDK 2.248.0 (Java)
- Gradle with Kotlin DSL + version catalog (`gradle/libs.versions.toml`)
- Tailwind CSS + HTMX 2.0.4 (both via CDN)
- Playwright 1.52.0 for JS-rendered sites
- Jsoup 1.18.1 for static HTML fetching

## Project Structure

```
app/src/main/java/com/londonsearch/
  agent/       # Pipeline: fetchers, extractors, normalizer, dedup, intelligence
  alert/       # SES email alerts with smart link tokens
  config/      # Spring config: Bedrock, DynamoDB, Security
  controller/  # Web (Thymeleaf) + REST controllers
  model/       # DynamoDB entities: Property, Listing, SearchConfig, MonitoredSite, Alert
  repository/  # DynamoDB enhanced client repositories
  seed/        # DataSeeder: search configs + 19 monitored sites

infra/src/main/java/com/londonsearch/infra/
  InfraApp.java       # CDK app entry point
  NetworkStack.java   # VPC
  DataStack.java      # DynamoDB + S3
  PortalStack.java    # Fargate + ALB
  ScheduleStack.java  # EventBridge cron rules (no targets yet)
```

## Key Endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /` | Yes | Property feed with area/status filters and sort |
| `GET /property/{id}` | Yes | Property detail (marks new→seen) |
| `POST /agent/run-async` | No | Trigger pipeline (async) |
| `GET /agent/progress` | No | Pipeline progress JSON (polled by UI) |
| `GET /agent/ping` | No | Health check (ALB target) |
| `GET /api/image?url=` | No | Image proxy (bypasses hotlink protection) |
| `GET /alert/{token}` | No | Smart link — grants session without password |

## Monitored Sites

19 UK estate agent websites organized into tiers:
- **Aggregators:** Rightmove (HTTP), OnTheMarket (HTTP), Zoopla (Playwright)
- **Tier 1:** Knight Frank, Savills, Foxtons, Chestertons, Strutt & Parker, Hamptons, Winkworth, Dexters, Benham & Reeves (all HTTP); JLL and Marsh & Parsons disabled (403)
- **Tier 2:** Wetherell, Knightsbridge Prime (HTTP); Hudsons (HTTP); Carter Jonas (Playwright); Quintessentially disabled (thin inventory)

## Known Issues

- Zoopla blocks headless Chromium (anti-bot detection)
- Strutt & Parker URL pattern returns 404 — needs research
- Hudsons Property area URL pattern returns 404 — needs research
- Some extracted image URLs are hallucinated by the LLM (404s)
- Change-detection hash is stored but not used to skip unchanged pages
- ScheduleStack EventBridge rules have no targets (not wired to pipeline)
- Network stack has dirty CloudFormation state — don't deploy it

## Scoring

Combined score = `structuredScore * 0.6 + aiScore * 0.4`

Structured scoring (max 100): area match (31), bedrooms (24), price range (24), bathrooms (10), furnishing (11).

Area classification: postcode prefix mapping (W1K/W1J/W1S→Mayfair, W1U/W1W/W1G/W1H/NW1→Marylebone, SW7/SW3→South Kensington), fallback to substring match, then "Other".
