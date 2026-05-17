# LondonSearchAgent

An AI-powered London rental property aggregator that scrapes 19 estate agent websites, extracts listings using AWS Bedrock, deduplicates and scores them, and presents everything in a clean dashboard.

**Live at [londonsearch.mandati.ai](https://londonsearch.mandati.ai)**

## What It Does

- Scrapes 19 UK estate agent websites (Rightmove, Foxtons, Hamptons, Savills, Chestertons, and more)
- Extracts structured property data from raw HTML using **Amazon Nova Micro** (Bedrock)
- Deduplicates properties across sites using address similarity matching
- Scores each property against your search criteria (area, bedrooms, price, furnishing)
- Generates AI assessments using **Claude Sonnet** (Bedrock) with location insights and value analysis
- Sends email alerts for new high-scoring properties with one-click smart links
- Proxies property images server-side to bypass hotlink protection

## Architecture

```
                                    +------------------+
                                    |   Cloudflare     |
                                    |  (SSL + CDN)     |
                                    +--------+---------+
                                             |
+------------------+                +--------+---------+
|  EventBridge     |  (planned)     |   ALB            |
|  (4x daily cron) +--------------->|   (HTTP :80)     |
+------------------+                +--------+---------+
                                             |
                                    +--------+---------+
                                    |   Fargate        |
                                    |   Spring Boot    |
                                    |   + Chromium     |
                                    +---+---------+----+
                                        |         |
                              +---------+--+  +---+----------+
                              |  DynamoDB  |  |   Bedrock    |
                              |  (5 tables)|  |  Nova Micro  |
                              +------------+  |  Claude Sonn.|
                                              +--------------+
```

**CDK Stacks:** Network (VPC) | Data (DynamoDB + S3) | Portal (Fargate + ALB) | Schedule (EventBridge)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (virtual threads) |
| Framework | Spring Boot 3.5 |
| Frontend | Thymeleaf + Tailwind CSS + HTMX |
| Database | Amazon DynamoDB |
| AI Extraction | Amazon Nova Micro (Bedrock) |
| AI Assessment | Claude Sonnet (Bedrock, cross-region) |
| Scraping | Jsoup (HTTP) + Playwright (JS-rendered sites) |
| Infrastructure | AWS CDK (Java), ECS Fargate, ALB |
| Build | Gradle (Kotlin DSL) |

## Getting Started

### Prerequisites

- Java 21+
- Docker (for DynamoDB Local)
- AWS CLI (for production deployment)

### Local Development

```bash
# Start DynamoDB Local
docker compose up -d

# Run the app with mock extractors (no AWS credentials needed)
./gradlew :app:bootRun --args='--spring.profiles.active=local'

# Open http://localhost:8080 (password: changeme)
```

### With Real Bedrock

```bash
# Ensure AWS credentials are configured, then:
SPRING_PROFILES_ACTIVE=local \
EXTRACTOR_TYPE=bedrock \
BEDROCK_REGION=us-east-1 \
./gradlew :app:bootRun
```

### Production Deployment

```bash
# Build the application jar
./gradlew :app:clean :app:bootJar

# Deploy to AWS (requires CDK bootstrap + credentials)
export CDK_DEFAULT_ACCOUNT=<account-id>
export CDK_DEFAULT_REGION=us-east-1
export LONDONSEARCH_PASSWORD=<your-password>
rm -rf infra/cdk.out
cdk deploy LondonSearch-Portal --exclusively --require-approval never
```

## Pipeline

The scraping pipeline runs on demand (via "Scan Now" in the UI or `POST /agent/run-async`) and processes all enabled monitored sites:

1. **Expand URLs** — fill in search criteria placeholders ({area}, {minBeds}, {maxPrice}, etc.)
2. **Fetch** — HTTP via Jsoup, or headless Chromium via Playwright for JS-rendered sites
3. **Extract** — send HTML to Nova Micro, get back structured JSON (address, price, beds, images, etc.)
4. **Enrich** — fetch og:image from listing pages when extraction misses images
5. **Normalize** — parse prices (pcm/pw/pa), normalize addresses, convert UK dates to ISO
6. **Deduplicate** — match against existing properties using Jaccard + Levenshtein similarity
7. **Score** — 60% structured criteria + 40% Claude Sonnet AI assessment
8. **Alert** — email top 5 new properties via SES with smart link for one-click access

## Project Structure

```
app/                          # Spring Boot application
  src/main/java/com/londonsearch/
    agent/                    # Pipeline: fetchers, extractors, normalizer, dedup, scoring
    alert/                    # Email alerts with smart link tokens
    config/                   # Spring config: Bedrock, DynamoDB, Security
    controller/               # Web + REST controllers
    model/                    # DynamoDB entities
    repository/               # Data access layer
    seed/                     # Startup data seeder (search configs + 19 sites)

infra/                        # AWS CDK infrastructure
  src/main/java/com/londonsearch/infra/
    NetworkStack.java         # VPC, endpoints
    DataStack.java            # DynamoDB tables, S3 bucket
    PortalStack.java          # ECS Fargate, ALB
    ScheduleStack.java        # EventBridge cron rules

docs/
  adr/                        # Architecture Decision Records
```

## Monitored Sites

| Tier | Sites | Method |
|------|-------|--------|
| Aggregators | Rightmove, OnTheMarket, Zoopla | HTTP / Playwright |
| Tier 1 | Knight Frank, Savills, Foxtons, Chestertons, Hamptons, Winkworth, Dexters, Benham & Reeves | HTTP |
| Tier 2 | Wetherell, Knightsbridge Prime, Hudsons, Carter Jonas | HTTP / Playwright |

## Documentation

- [CLAUDE.md](CLAUDE.md) — detailed project context for AI assistants
- [docs/adr/](docs/adr/) — Architecture Decision Records covering key technical choices

## License

Private project — not open source.
