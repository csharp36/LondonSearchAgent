# LondonSearchAgent — Development Guide

## Prerequisites

- Java 21 (Temurin/Corretto recommended)
- Docker Desktop (for DynamoDB Local)
- Gradle 9.5+ (wrapper included)
- AWS CLI (for deployment only)
- Node.js (for CDK CLI, deployment only)

## Local Development

### 1. Start DynamoDB Local

```bash
docker compose up -d
```

This starts `amazon/dynamodb-local` on port 8000 with in-memory storage.

### 2. Run the Application

```bash
./gradlew :app:bootRun --args='--spring.profiles.active=local'
```

App starts at http://localhost:8080, password: `changeme`

The `local` profile:
- Points DynamoDB to `http://localhost:8000`
- Uses `mock` extractor (no AWS credentials needed)
- Seeds 2 search configs and 19 monitored sites on startup
- Returns hardcoded test properties from MockExtractor

### 3. With Real Bedrock (requires AWS credentials)

```bash
SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/*.jar \
  --app.agent.extractor=bedrock \
  --app.agent.bedrock.region=us-east-1
```

## Build

```bash
# Build the Spring Boot fat JAR
./gradlew :app:clean :app:bootJar

# Build + test
./gradlew :app:build

# Compile CDK infra
./gradlew :infra:compileJava
```

## Testing

```bash
# Run all tests (requires DynamoDB Local running)
./gradlew :app:test

# Run specific test class
./gradlew :app:test --tests "com.londonsearch.agent.BedrockExtractorTest"

# Run tests matching a pattern
./gradlew :app:test --tests "*Normalizer*"
```

**Test infrastructure:**
- DynamoDB Local must be running (`docker compose up -d`)
- Tests use `application-test.yml` with `Test_*` table names
- Mock extractor beans load automatically via `@ActiveProfiles("test")`
- Integration tests use `@SpringBootTest` with full context
- Unit tests are plain JUnit 5 with Mockito (no Spring context)

### Test Coverage Summary

| Package | Test Classes | Tests | What's Covered |
|---------|-------------|-------|----------------|
| agent/ | 10 | ~70 | Pipeline, extraction parsing, dedup, scoring, normalizer, images, cost guard |
| controller/ | 2 | ~8 | Security (auth flows), feed page |
| config/ | 1 | 2 | DynamoDB bean creation |
| repository/ | 1 | 4 | Property CRUD |
| root | 1 | 1 | Context loads |

## Configuration

All config in `app/src/main/resources/application.yml` with env var overrides:

| Config | Env Var | Default |
|--------|---------|---------|
| Password | `APP_PASSWORD` | changeme |
| AWS Region | `AWS_REGION` | eu-west-2 |
| DynamoDB Endpoint | `DYNAMODB_ENDPOINT` | (empty = AWS) |
| Extractor Mode | `EXTRACTOR_TYPE` | mock |
| Bedrock Region | `BEDROCK_REGION` | us-east-1 |
| Extraction Model | `BEDROCK_MODEL_ID` | amazon.nova-micro-v1:0 |
| Assessment Model | `BEDROCK_INTELLIGENCE_MODEL_ID` | us.anthropic.claude-sonnet-4-6 |
| Alert Email To | `ALERT_EMAIL` | csharp36@gmail.com |
| Alert Email From | `ALERT_EMAIL_FROM` | noreply@londonsearchagent.com |
| Portal Base URL | `PORTAL_BASE_URL` | http://localhost:8080 |
| Cost Guard | `COST_GUARD_ENABLED` | false |

## Key Development Patterns

### Conditional Beans
`@ConditionalOnProperty(name = "app.agent.extractor")` switches implementations:
- `mock` (default): MockExtractor, MockIntelligence, MockAlertService
- `bedrock` (prod): BedrockExtractor, BedrockIntelligence, SesAlertService

### Virtual Threads
Pipeline runs async via `Thread.startVirtualThread()` in PipelineProgressService. Progress tracked with `AtomicReference<PipelineStatus>`.

### DynamoDB Enhanced Client
All repositories use `@DynamoDbBean` annotated models with `DynamoDbTable<T>` for type-safe operations. Tables auto-created on local/test profiles by `TableInitializer`.

## Triggering a Pipeline Run

**Via UI:** Login -> Config -> Pipeline Progress -> "Run Scan"
**Via API:** `curl -X POST http://localhost:8080/agent/run-async`
**Monitor:** `curl http://localhost:8080/agent/progress`
