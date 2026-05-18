# LondonSearchAgent — Documentation Index

## Project Overview

- **Type:** Monorepo with 2 parts (app + infra)
- **Primary Language:** Java 21
- **Architecture:** Spring Boot monolith with embedded AI pipeline
- **Production:** https://londonsearch.mandati.ai

## Quick Reference

### Application (`app/`)
- **Type:** Web + Backend
- **Tech Stack:** Spring Boot 3.5.14, Thymeleaf, HTMX, Tailwind CSS
- **AI:** Bedrock Nova Micro (extraction), Claude Sonnet 4.6 (assessment)
- **Database:** DynamoDB (5 tables)
- **Entry Point:** `LondonSearchApplication.java`

### Infrastructure (`infra/`)
- **Type:** Infrastructure as Code
- **Tech Stack:** AWS CDK 2.248.0 (Java)
- **Stacks:** Network, Data, Portal (Fargate), Schedule (EventBridge)
- **Entry Point:** `InfraApp.java`

## Generated Documentation

- [Project Overview](./project-overview.md)
- [Architecture — Application](./architecture.md)
- [Architecture — Infrastructure](./architecture-infra.md)
- [Source Tree Analysis](./source-tree-analysis.md)
- [API Contracts](./api-contracts.md)
- [Data Models](./data-models.md)
- [Development Guide](./development-guide.md)
- [Deployment Guide](./deployment-guide.md)

## Existing Documentation

### Architecture Decision Records (`adr/`)
- [ADR-001: Dual-Model Bedrock Strategy](./adr/001-dual-model-bedrock-strategy.md)
- [ADR-002: Fargate Public Subnet, No NAT](./adr/002-fargate-public-subnet-no-nat.md)
- [ADR-003: Playwright for JS-Rendered Sites](./adr/003-playwright-for-js-rendered-sites.md)
- [ADR-004: Image Proxy for Hotlink Protection](./adr/004-image-proxy-for-hotlink-protection.md)
- [ADR-005: Deduplication Strategy](./adr/005-deduplication-strategy.md)
- [ADR-006: Mock vs Bedrock Dual Mode](./adr/006-mock-vs-bedrock-dual-mode.md)
- [ADR-007: Scoring Formula](./adr/007-scoring-formula.md)
- [ADR-008: URL Template Expansion](./adr/008-url-template-expansion.md)
- [ADR-009: Single Password Auth](./adr/009-single-password-auth.md)
- [ADR-010: Date Normalization](./adr/010-date-normalization.md)

### Design & Planning
- [System Design Spec](./superpowers/specs/2026-05-15-london-search-agent-design.md)
- [Phase 1: Foundation Portal](./superpowers/plans/2026-05-15-phase1-foundation-portal.md)
- [Phase 2: Agent Pipeline](./superpowers/plans/2026-05-15-phase2-agent-pipeline.md)
- [Phase 3: Intelligence](./superpowers/plans/2026-05-15-phase3-intelligence.md)
- [Phase 4: Alerts & Operations](./superpowers/plans/2026-05-15-phase4-alerts-operations.md)
- [Phase 5: Scale Out](./superpowers/plans/2026-05-15-phase5-scale-out.md)

### Project Meta
- [CLAUDE.md](../CLAUDE.md) — AI assistant project instructions
- [README.md](../README.md) — Project README

## Getting Started

1. `docker compose up -d` — Start DynamoDB Local
2. `./gradlew :app:bootRun --args='--spring.profiles.active=local'` — Start app at http://localhost:8080
3. Login with password: `changeme`
4. Trigger a scan: POST http://localhost:8080/agent/run-async (uses mock extractor locally)
