# ADR-006: Conditional Bean Loading for Local vs Production

**Status:** Accepted  
**Date:** 2026-05-16  
**Context:** The application needs to run locally without AWS credentials for development and testing, while using real AWS services (Bedrock, SES, DynamoDB) in production.

## Decision

Use Spring's `@ConditionalOnProperty` on `app.agent.extractor` to load different bean implementations:

- `extractor=mock` (default, `matchIfMissing=true`): MockExtractor, MockIntelligence, MockAlertService
- `extractor=bedrock`: BedrockExtractor, BedrockIntelligence, SesAlertService, BedrockConfig

Combined with Spring profiles:
- `local` profile: DynamoDB Local on localhost:8000, TableInitializer creates tables at startup
- `prod` profile: Real DynamoDB (no endpoint override), DataSeeder upserts configs/sites at startup

## Rationale

- Developers can run the full application locally with `docker compose up` (DynamoDB Local) without any AWS credentials
- The mock implementations return realistic-looking data for UI development
- Switching to real Bedrock locally is a single env var: `EXTRACTOR_TYPE=bedrock`
- No test doubles or DI frameworks needed — Spring handles the wiring

## Consequences

- Mock data is static and doesn't test the actual extraction/assessment quality
- The `BedrockConfig` bean (which creates the `BedrockRuntimeClient`) is only instantiated when `extractor=bedrock`, preventing credential errors locally
- Two separate implementations must be kept in sync if the interface changes
