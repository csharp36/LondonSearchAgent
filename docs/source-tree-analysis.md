# LondonSearchAgent — Source Tree Analysis

```
LondonSearchAgent/
├── build.gradle.kts              # Root: Java 21 toolchain for all subprojects
├── settings.gradle.kts           # Declares app/ and infra/ subprojects
├── gradle/
│   └── libs.versions.toml        # Centralized version catalog
├── cdk.json                      # CDK entry: runs gradlew in infra/
├── docker-compose.yml            # DynamoDB Local for dev (port 8000)
├── CLAUDE.md                     # AI assistant project instructions
├── README.md                     # Project README
│
├── app/                          # ── Spring Boot Application ──
│   ├── build.gradle.kts          # Spring Boot plugin, all app dependencies
│   ├── Dockerfile                # eclipse-temurin:21-jre-jammy, linux/amd64
│   ├── .dockerignore
│   └── src/
│       ├── main/
│       │   ├── java/com/londonsearch/
│       │   │   ├── LondonSearchApplication.java    # @SpringBootApplication entry point
│       │   │   │
│       │   │   ├── agent/                          # Pipeline orchestration (18 classes)
│       │   │   │   ├── AgentPipelineService.java   #   Main pipeline: fetch→extract→dedup→score
│       │   │   │   ├── AgentController.java        #   REST: /agent/run-async, /agent/progress
│       │   │   │   ├── PipelineProgressService.java#   Async tracking via virtual threads
│       │   │   │   ├── BedrockExtractor.java       #   Nova Micro HTML→JSON extraction
│       │   │   │   ├── MockExtractor.java          #   Test data for local dev
│       │   │   │   ├── PropertyExtractor.java      #   Interface
│       │   │   │   ├── BedrockIntelligence.java    #   Claude Sonnet AI assessment
│       │   │   │   ├── MockIntelligence.java       #   Fixed scores for local dev
│       │   │   │   ├── PropertyIntelligence.java   #   Interface + Assessment record
│       │   │   │   ├── SiteFetcher.java            #   Jsoup HTTP + HTML stripping
│       │   │   │   ├── PlaywrightFetcher.java      #   Headless Chromium for JS sites
│       │   │   │   ├── PropertyNormalizer.java     #   Price/address/date normalization
│       │   │   │   ├── DeduplicationService.java   #   Jaccard + Levenshtein matching
│       │   │   │   ├── StructuredScorer.java       #   Rule-based scoring (0-100)
│       │   │   │   ├── ImageValidator.java         #   HTTP HEAD validation
│       │   │   │   ├── ImageEnricher.java          #   og:image fallback
│       │   │   │   ├── CostGuard.java              #   Pipeline kill switch
│       │   │   │   └── ExtractedProperty.java      #   Record: 15-field extraction result
│       │   │   │
│       │   │   ├── alert/                          # Email alerts (5 classes)
│       │   │   │   ├── AlertService.java           #   Interface
│       │   │   │   ├── SesAlertService.java        #   SES HTML email (prod)
│       │   │   │   ├── MockAlertService.java       #   Log-only (local)
│       │   │   │   ├── SmartLinkService.java       #   24h UUID token generation
│       │   │   │   └── AlertController.java        #   GET /alert/{token} → session
│       │   │   │
│       │   │   ├── config/                         # Spring configuration (3 classes)
│       │   │   │   ├── SecurityConfig.java         #   Single-password auth + CSRF
│       │   │   │   ├── DynamoDbConfig.java         #   Client + enhanced client beans
│       │   │   │   └── BedrockConfig.java          #   Conditional Bedrock client
│       │   │   │
│       │   │   ├── controller/                     # Web controllers (6 classes)
│       │   │   │   ├── FeedController.java         #   GET / — property feed
│       │   │   │   ├── PropertyController.java     #   Property detail + status update
│       │   │   │   ├── ConfigController.java       #   Search config + site management
│       │   │   │   ├── AccountController.java      #   Dashboard + alert history
│       │   │   │   ├── LoginController.java        #   GET /login
│       │   │   │   └── ImageProxyController.java   #   Image proxy with domain allowlist
│       │   │   │
│       │   │   ├── model/                          # DynamoDB entities (5 classes)
│       │   │   │   ├── Property.java               #   Core entity, 2 GSIs
│       │   │   │   ├── Listing.java                #   Composite key (propertyId + siteListingId)
│       │   │   │   ├── SearchConfig.java           #   Criteria for scoring + filtering
│       │   │   │   ├── MonitoredSite.java          #   Scraper configuration
│       │   │   │   └── AlertRecord.java            #   Email + smart link history
│       │   │   │
│       │   │   ├── repository/                     # DynamoDB repositories (6 classes)
│       │   │   │   ├── PropertyRepository.java     #   CRUD + findByArea/findByStatus (GSI)
│       │   │   │   ├── ListingRepository.java      #   findByPropertyId (query)
│       │   │   │   ├── SearchConfigRepository.java #   CRUD
│       │   │   │   ├── MonitoredSiteRepository.java#   CRUD
│       │   │   │   ├── AlertRepository.java        #   CRUD + findByToken (scan)
│       │   │   │   └── TableInitializer.java       #   Creates tables on local/test
│       │   │   │
│       │   │   └── seed/
│       │   │       └── DataSeeder.java             #   Seeds 2 configs + 19 monitored sites
│       │   │
│       │   └── resources/
│       │       ├── application.yml                 #   Prod config (env var overrides)
│       │       ├── application-local.yml           #   Local dev overrides
│       │       └── templates/                      #   Thymeleaf HTML templates
│       │           ├── layout.html                 #   Base layout with nav
│       │           ├── feed.html                   #   Property feed page
│       │           ├── property-detail.html        #   Single property view
│       │           ├── login.html                  #   Login form
│       │           ├── pipeline-progress.html      #   HTMX-polled progress
│       │           ├── config/
│       │           │   ├── account.html            #   Account dashboard
│       │           │   ├── search.html             #   Search config editor
│       │           │   └── sites.html              #   Site management
│       │           └── fragments/
│       │               ├── filter-pills.html       #   Area/status filter buttons
│       │               └── property-card.html      #   Property card component
│       │
│       └── test/
│           ├── java/com/londonsearch/              # 15 test classes, 93 tests
│           │   ├── agent/                          #   10 test classes
│           │   ├── controller/                     #   2 test classes (Security, Feed)
│           │   ├── config/                         #   1 test class (DynamoDbConfig)
│           │   ├── repository/                     #   1 test class (PropertyRepository)
│           │   └── LondonSearchApplicationTests.java
│           └── resources/
│               └── application-test.yml            #   Test_* table names, mock extractor
│
├── infra/                        # ── AWS CDK Infrastructure ──
│   ├── build.gradle.kts          # CDK dependencies, mainClass = InfraApp
│   └── src/main/java/com/londonsearch/infra/
│       ├── InfraApp.java         #   CDK app: wires all 4 stacks
│       ├── NetworkStack.java     #   VPC, 2 AZs, no NAT, VPC endpoints
│       ├── DataStack.java        #   5 DynamoDB tables + S3 bucket
│       ├── PortalStack.java      #   ECS Fargate + ALB (linux/amd64)
│       └── ScheduleStack.java    #   4 EventBridge rules (no targets)
│
└── docs/                         # ── Documentation ──
    ├── adr/                      #   10 Architecture Decision Records
    │   ├── 001-dual-model-bedrock-strategy.md
    │   ├── 002-fargate-public-subnet-no-nat.md
    │   ├── 003-playwright-for-js-rendered-sites.md
    │   ├── 004-image-proxy-for-hotlink-protection.md
    │   ├── 005-deduplication-strategy.md
    │   ├── 006-mock-vs-bedrock-dual-mode.md
    │   ├── 007-scoring-formula.md
    │   ├── 008-url-template-expansion.md
    │   ├── 009-single-password-auth.md
    │   └── 010-date-normalization.md
    └── superpowers/
        ├── specs/                #   Design spec
        └── plans/                #   5 phase implementation plans
```
