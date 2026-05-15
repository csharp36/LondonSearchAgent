# LondonSearchAgent Design Spec

A personal AI-powered property search agent that monitors ~19 UK estate agent and aggregator sites for rental properties in Mayfair, Marylebone, and South Kensington, deduplicates listings across sites, and surfaces matches through a web portal and email alerts.

## Motivation

The UK rental market is fragmented. Unlike the US MLS system where a single agent can access all inventory, each UK estate agent only lists properties they are authorized to rent. This means a renter must manually check 15-20 sites to see the full picture. This agent eliminates that manual work, adds cross-site deduplication, and applies AI-powered preference matching that goes beyond what any single site offers.

## Architecture

Hybrid architecture with two independently deployable components sharing DynamoDB as the data layer.

### Component 1: The Agent (AWS AgentCore)

A Java-based AgentCore agent invoked on a schedule by Amazon EventBridge (4x daily). Uses AgentCore's tool-use framework where each property site is registered as a tool the agent can call.

Pipeline per invocation: Scrape > Extract > Normalize > Deduplicate > Persist > Alert.

Models via Amazon Bedrock:
- Amazon Nova Micro: HTML-to-structured-data extraction
- Amazon Nova Lite: data normalization, text-based dedup scoring
- Claude Sonnet (via Bedrock): image-based dedup (vision), preference matching, recommendation/summarization, Knight Frank timeline interpretation

Headless browser (Playwright for Java) for sites requiring JavaScript rendering or authenticated sessions. Knight Frank credentials stored in AWS Secrets Manager.

### Component 2: The Portal (Spring Boot)

Spring Boot web application deployed on ECS Fargate. Simple shared-secret password authentication. Reads from DynamoDB to display the unified property feed. Provides configuration UI for managing search criteria and monitored sites. Generates smart links for email alerts.

### Shared Infrastructure

- **DynamoDB** — property data, search configs, dedup mappings, alert history
- **S3** — property images for cross-site comparison and portal display
- **SES** — email alerts when new matches are found
- **EventBridge** — schedules agent invocations (4x daily)
- **CloudWatch** — logging and monitoring
- **AWS Budgets** — cost controls and kill switch
- **CDK (Java)** — infrastructure as code

### Architecture Diagram

```
EventBridge (4x/day)
       |
       v
+---------------------+     +--------------+
|   AgentCore Agent    |---->|   DynamoDB   |<----+-------------------+
|                      |     |              |     |  Spring Boot      |
|  +---------------+   |     |  - Properties|     |  Portal (ECS)     |
|  | Tool: KnightF |   |     |  - Configs   |     |                   |
|  | Tool: Rightmov|   |     |  - Dedup Map |     |  - Unified Feed   |
|  | Tool: OnTheMkt|   |     |  - Alerts    |     |  - Config UI      |
|  | Tool: Savills |   |     +--------------+     |  - Detail View    |
|  | Tool: Foxtons |   |            |              +-------------------+
|  | Tool: ...     |   |     +--------------+            |
|  +---------------+   |     |     S3       |      Password Auth
|                      |     |  (images)    |
|  Bedrock (Nova/Claude|     +--------------+
|  Secrets Manager     |
|  SES (alerts)        |
+---------------------+
```

## Data Model (DynamoDB)

### Properties Table

Represents a deduplicated property — one entry regardless of how many sites list it.

- **PK:** `PROP#<canonical-id>` (generated UUID)
- **SK:** `META`
- **Attributes:** `address`, `normalizedAddress`, `area` (Mayfair | Marylebone | S.Kensington | Other), `bedrooms`, `bathrooms`, `price`, `currency`, `pricePerMonth`, `sqft`, `propertyType`, `furnishing` (Furnished | Part-furnished | Unfurnished), `availableFrom`, `description`, `aiSummary`, `matchScore` (0-100, how well it fits preferences), `status` (new | seen | saved | dismissed), `firstSeenAt`, `lastUpdatedAt`
- **GSI ByArea:** PK: `area`, SK: `firstSeenAt`
- **GSI ByStatus:** PK: `status`, SK: `firstSeenAt`

### Listings Table

One entry per source site per property. This powers the aggregate detail view.

- **PK:** `PROP#<canonical-id>`
- **SK:** `LISTING#<site>#<site-listing-id>`
- **Attributes:** `siteUrl`, `siteName`, `originalPrice`, `originalAddress`, `listingUrl`, `imageUrls[]`, `floorPlanUrl`, `agentName`, `agentPhone`, `scrapedAt`, `rawData` (full extracted JSON)

### Images Table

Property images stored in S3 with metadata for cross-site comparison.

- **PK:** `PROP#<canonical-id>`
- **SK:** `IMG#<hash>`
- **Attributes:** `s3Key`, `sourceListings[]` (which listings contained this image), `imageType` (photo | floorplan | epc), `embedding` (vector for similarity matching)

### SearchConfigs Table

User-defined search criteria. Multiple searches supported, each can be active or paused.

- **PK:** `CONFIG#<config-id>`
- **SK:** `META`
- **Attributes:** `name`, `areas[]`, `minBeds`, `maxBeds`, `minPrice`, `maxPrice`, `minBaths`, `furnishing[]`, `propertyTypes[]`, `additionalCriteria` (free text for AI interpretation, e.g., "must have outdoor space, walkable to restaurants, avoid noisy streets"), `enabled`, `createdAt`

### MonitoredSites Table

Configurable list of estate agent sites to scrape. Ships with ~19 pre-configured sites.

- **PK:** `SITE#<site-id>`
- **SK:** `META`
- **Attributes:** `name`, `baseUrl`, `searchUrlTemplate`, `scraperType` (static | js-rendered | authenticated), `enabled`, `lastCheckedAt`, `lastChangeHash`, `checkFrequency`

### DedupMappings Table

Records cross-site matches with confidence scores and reasoning.

- **PK:** `DEDUP#<canonical-id>`
- **SK:** `MATCH#<other-canonical-id>`
- **Attributes:** `confidenceScore`, `matchReasons[]` (address-match | image-match | metadata-match), `mergedAt`, `modelUsed`

### AlertHistory Table

Tracks sent alerts and their associated smart link tokens.

- **PK:** `ALERT#<date>`
- **SK:** `<timestamp>#<alert-id>`
- **Attributes:** `propertyIds[]`, `emailSentTo`, `sentAt`, `smartLinkToken`, `tokenExpiresAt`

## Agent Pipeline

### Stage 1: Change Detection (No AI)

For each enabled site in MonitoredSites, fetch the search results page. Hash the content and compare against `lastChangeHash`. Skip sites with no changes. For authenticated sites (Knight Frank account), use Playwright to log in via Secrets Manager credentials and navigate to the timeline view.

### Stage 2: HTML Extraction (Nova Micro)

For pages that have changed, pass HTML content to Nova Micro. Extract all property listings into structured JSON: address, price, bedrooms, bathrooms, sqft, furnishing, description, imageUrls, listingUrl. Fallback: if Nova Micro returns malformed data, escalate to Nova Lite for that page.

### Stage 3: Normalization (Nova Lite)

Normalize addresses to a canonical format. Convert currencies and standardize price periods (pw to pcm). Convert unit measurements (sq m to sq ft). Classify properties into target areas (Mayfair, Marylebone, S.Kensington, Other). Apply structured filters from SearchConfigs (beds, baths, price range, furnishing) — discard non-matching properties before more expensive stages.

### Stage 4: Text-Based Dedup (Nova Lite)

Compare normalized addresses and metadata against existing Properties in DynamoDB. Score similarity on: normalized address (fuzzy match), price (within 5%), bedrooms, bathrooms, sqft. High-confidence text matches (>0.9) auto-merge immediately. Medium-confidence matches (0.6-0.9) flagged for image verification in Stage 5.

### Stage 5: Image-Based Dedup (Claude Sonnet — vision)

Only called for medium-confidence text matches from Stage 4. Download images from both listings, send to Claude Sonnet with vision. The model compares room layouts, fixtures, views, and architectural details. Returns confidence score and reasoning. This is the most expensive call but only fires for ambiguous cases.

### Stage 6: Preference Matching (Claude Sonnet)

For new/updated properties, score against active SearchConfigs. Handles the `additionalCriteria` free text — contextual judgments that no filter checkbox could replicate:
- Walkability to restaurants (reasoning about the address and neighborhood)
- Proximity to noise sources (reasoning about street location, nearby venues from description/photos)
- Natural light quality (reasoning about orientation, floor level, window sizes from photos)
- Proximity to tube stations (geographic knowledge)
- Neighborhood character (family-friendly, nightlife-heavy, etc.)

Generates `aiSummary` — a concise assessment of the property's strengths and weaknesses relative to preferences, including specific observations about location context.

Produces a `matchScore` (0-100) reflecting how well the property fits all criteria, both structured and AI-interpreted.

### Stage 7: Persist & Alert (No AI)

Write new/updated properties and listings to DynamoDB. Upload images to S3. If any new matches were found, compose and send email alert via SES with smart links. Update `lastChangeHash` and `lastCheckedAt` for each processed site.

### Model Tiering Cost Controls

The tiering is configurable per pipeline stage. If Nova models underperform on extraction or normalization tasks, individual stages can be upgraded to a more capable model. Conversely, if costs need to be reduced, image dedup can be disabled or preference matching can be downgraded.

Per-run application-level guard: at the start of each agent run, query AWS Cost Explorer API for month-to-date spend. Above soft limit ($40): run in "cheap mode" (skip image dedup, Nova Micro only). Above hard limit ($50): log warning and exit without processing.

## Web Portal

### Technology

- Spring Boot 3.x with Java 21
- Thymeleaf for server-side rendering (keeps it simple, no separate SPA build)
- HTMX for dynamic interactions without a JavaScript framework
- Tailwind CSS for styling
- Deployed on ECS Fargate behind an Application Load Balancer

### Authentication

Simple shared-secret password authentication via a Spring Security filter. Single password stored as an environment variable (or Secrets Manager). Session-based — log in once, stay logged in until session expires. Smart links from email alerts include a 24-hour auth token that bypasses login.

### Pages

**Unified Feed (main page):**
- Card grid layout showing all matching properties across all monitored sites
- Each card: property photo, NEW badge, site count badge (e.g., "3 sites"), address, area tag, beds/baths/sqft, price, match score (color-coded: green >80%, yellow 60-80%), AI summary snippet
- Filter pills: All, Mayfair, Marylebone, S. Kensington, New Only, Saved
- Sorted by firstSeenAt descending (newest first), with option to sort by match score
- Clicking a card opens the property detail view

**Property Detail View:**
- Photo grid pulling best images across all source listings
- Stats bar: price, bedrooms, bathrooms, size, available from, match score
- AI Summary (purple accent) — the agent's contextual assessment including location insights, potential concerns, and price comparisons
- Full property description (best version across sources)
- Floor plan (if available from any source)
- Source listings panel — each site where this property appears, with listing date, price, and "View" link to original listing
- Match confidence panel — shows dedup reasoning (e.g., "KF <> Rightmove: 98% — address match + 8 matching photos")
- Agent contact information
- Save/Dismiss action buttons

**Configuration (tabbed):**

Search Criteria tab:
- List of saved searches on the left (each with Active/Paused toggle)
- Edit form on the right: name, areas (tag input), min/max bedrooms, min/max price, min bathrooms, furnishing (multi-select: Furnished/Part-furnished/Unfurnished), AI Preferences free-text box
- The AI Preferences box accepts natural language criteria for contextual matching (e.g., "walkable to restaurants", "quiet street", "good natural light", "avoid basement flats")

Monitored Sites tab:
- List of all configured sites with enable/disable toggles
- Each row: site name, URL, scraper type, last checked, last change detected
- "Add Site" button to configure new estate agent sites
- Base set of ~19 sites pre-configured

Alerts tab:
- Email address configuration
- Alert frequency preferences
- History of sent alerts with links to the properties included

Account tab:
- Password change
- Knight Frank credentials management (stored in Secrets Manager)
- AWS cost dashboard — current month-to-date spend, budget utilization bar
- Kill switch — manual "Pause Agent" / "Resume Agent" button
- Budget threshold configuration (soft limit, hard limit)

## Email Alerts

**Trigger:** End of any agent run where new matching properties were persisted. No email sent if no new matches.

**Content:**
- Subject: `LondonSearchAgent: 3 new properties in Mayfair, Marylebone`
- Summary count per area
- Top 5 properties by match score (if more than 5 new, show top 5 with "View all N new properties" link):
  - Address, area, price, beds/baths
  - One-line AI summary highlight
  - Match score
- Smart link at bottom: single URL to portal pre-filtered to this alert's properties

**Smart Link Mechanism:**
- Agent generates a UUID token stored in DynamoDB AlertHistory with 24-hour TTL
- Portal URL: `https://<portal-domain>/alert/<token>`
- Hitting the URL authenticates for that session and pre-filters the feed
- After 24 hours, token expires and link redirects to normal login

## Base Monitored Sites

Pre-configured sites, all enabled by default. Users can disable any and add new ones.

### Aggregators

| Site | URL | Notes |
|---|---|---|
| Rightmove | rightmove.co.uk | Dominant UK portal, ~90% of agents list here |
| OnTheMarket | onthemarket.com | Some agents list here before Rightmove |
| Zoopla | zoopla.co.uk | Claims #1 London listing volume, some exclusive agents |

### Tier 1 — Major Direct Agents

| Site | Target Area Coverage |
|---|---|
| Knight Frank | Mayfair, Marylebone, S. Kensington (+ authenticated account timeline) |
| Savills | Pan-London premium |
| Foxtons | Mayfair, Marylebone (~34 exclusive listings at any time) |
| Chestertons | Mayfair, Marylebone, S. Kensington |
| Strutt & Parker | S. Kensington, premium London |
| JLL Residential | Mayfair, corporate relocation stock |
| Marsh & Parsons | Mayfair, Marylebone, S. Kensington |
| Hamptons | Marylebone, Kensington |
| Winkworth | Mayfair, Marylebone (dedicated PCL section) |
| Dexters | S. Kensington, Marylebone |
| Benham & Reeves | S. Kensington specialist |

### Tier 2 — Boutique PCL Specialists

| Site | Specialty |
|---|---|
| Wetherell | Mayfair-only since 1982 |
| Knightsbridge Prime Property | Mayfair, Marylebone, S. Kensington |
| Quintessentially Estates | Off-market luxury stock |
| Hudsons Property | Marylebone/W1 specialist |
| Carter Jonas | Christie's affiliated, prime lettings |

## Cost Controls

### AWS Budgets

- Monthly budget: $50
- Email alerts via SNS at $25 (50%), $40 (80%), $50 (100%)
- Budget Action at $50: automatically disables EventBridge rule and applies restrictive IAM policy blocking Bedrock invocations
- Resume via portal Account tab or AWS console

### Application-Level Guards

- Per-run: query Cost Explorer API at start of each agent invocation
- Above $40 (soft limit): run in "cheap mode" — skip image dedup, use Nova Micro only
- Above $50 (hard limit): log warning, exit without processing
- Soft and hard limits configurable via portal Account tab

### Estimated Monthly Cost

| Service | Estimate |
|---|---|
| Bedrock (model invocations) | ~$11 |
| ALB | ~$16 |
| ECS Fargate (portal) | ~$5 |
| DynamoDB (on-demand) | ~$2 |
| CloudWatch Logs | ~$1 |
| Secrets Manager | ~$0.80 |
| S3 (images) | ~$0.03 |
| SES (emails) | ~$0.01 |
| EventBridge | ~$0 |
| **Total** | **~$36/month** |

Cost-conscious infrastructure choices:
- Fargate Spot for the portal (tolerates brief interruptions)
- DynamoDB on-demand pricing (pay per request)
- S3 Intelligent-Tiering for property images
- No NAT Gateway — VPC endpoints for AWS services

## Deployment

### Infrastructure as Code

AWS CDK in Java. Stacks:
- **NetworkStack** — VPC, subnets, VPC endpoints (no NAT Gateway)
- **DataStack** — DynamoDB tables, S3 bucket
- **SecretsStack** — Secrets Manager entries (portal password, Knight Frank credentials)
- **AgentStack** — AgentCore agent definition, tool registrations, Bedrock model connections
- **PortalStack** — ECS Fargate service, ALB, ACM certificate, security groups
- **ScheduleStack** — EventBridge rule, SES configuration, SNS topics
- **BudgetStack** — AWS Budget, Budget Actions, SNS alerts

### Deployment Flow

```
Local development
    |
    +-- gradle build + docker build
    |
    +-- docker push to ECR
    |
    +-- cdk deploy
        +-- AgentCore agent updated
        +-- ECS service updated
        +-- Infrastructure changes applied
```

### Local Development

- Agent and portal can run locally against DynamoDB Local and LocalStack for S3/SES
- Bedrock calls go to real AWS (no local emulator) — use a test search config with 1-2 sites to minimize cost during development

## Non-Goals

- This is not a commercial product. Single-user, no multi-tenancy.
- No mobile app. The portal is responsive enough for phone use via email smart links.
- No property application/booking integration. The agent finds and surfaces properties; the user contacts agents directly.
- No historical price tracking or market analysis. This is a search tool, not an analytics platform.
