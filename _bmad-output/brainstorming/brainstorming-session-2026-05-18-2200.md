---
stepsCompleted: [1, 2, 3]
inputDocuments: []
session_topic: 'Multi-tenant productization of LondonSearchAgent'
session_goals: 'Cost modeling, data architecture, authentication, usage limits, email digests, viability assessment'
selected_approach: 'ai-recommended'
techniques_used: ['first-principles-thinking', 'constraint-mapping']
ideas_generated: ['shared-property-catalog', 'per-user-ai-assessment', 'gated-ai-assessment', 'on-demand-lazy-loading', 'concierge-repositioning', 'stay-personal']
context_file: ''
---

# Brainstorming Session Results

**Facilitator:** Csharpl
**Date:** 2026-05-18

## Session Overview

**Topic:** Evolving LondonSearchAgent from a single-user personal tool into a multi-tenant hosted service

**Goals:**
- Cost model: understand per-user economics (Bedrock, DynamoDB, Fargate, SES)
- Data architecture: per-user isolation for search configs, results, scan history
- Authentication: social login (Apple, Google, Meta) vs alternatives
- Usage governance: per-user scan limits, rate controls
- User experience: email digests, onboarding, free tier definition
- Decision clarity: viability assessment and build order

### Session Setup

_Personal project that is working well for a single user. Now exploring multi-tenant hosting. Key concern is cost exposure — want to model economics before making code changes. Current architecture: single shared password, 5 DynamoDB tables, ECS Fargate, Bedrock AI extraction, SES email alerts._

## Technique Selection

**Approach:** AI-Recommended Techniques
**Techniques:** First Principles Thinking → Constraint Mapping (session concluded early with clear decision)

## Technique Execution Results

### Phase 1: First Principles Thinking

**Key Ideas Generated:**

**[FP #1]: Shared Property Catalog**
_Concept:_ Properties and Listings are global facts, scraped once. SearchConfigs, scores, and alerts are per-user overlays. One scan serves all users.
_Novelty:_ Cost of scraping and extraction is fixed regardless of user count.

**[FP #2]: Per-User AI Assessment**
_Concept:_ Each user gets personalized Claude Sonnet assessment tailored to their criteria, not a shared generic summary.
_Novelty:_ The AI summary becomes the product differentiator — "why this property matters to YOU."

**[FP #3]: Gated AI Assessment**
_Concept:_ Structured score (free math) gates the expensive Sonnet call. Only properties above threshold (~40+) get auto-assessed. Below-threshold properties get minimal treatment.
_Novelty:_ Cuts AI cost by ~60% while losing nothing users would have read.

**[FP #4]: On-Demand AI with Lazy Loading**
_Concept:_ Below-threshold properties show structured score only, with a "Get AI Assessment" button triggering a real-time Sonnet call (~10s spinner). Cached once generated.
_Novelty:_ Flips cost from "pay for everything upfront" to "pay only for what users click." Most users look at 10-20 properties.

**[FP #5]: Legal Constraint Is Load-Bearing**
_Concept:_ The business model depends on continued access to data sources that prohibit commercial scraping in their ToS. This isn't a risk to mitigate — it's a foundational constraint.
_Novelty:_ Rightmove actively pursues scrapers. Charging £99/month crosses from grey-area personal use to clear commercial exploitation.

**[FP #6]: Reposition from Aggregator to Concierge**
_Concept:_ Don't compete with Rightmove by aggregating. Partner with agents directly — they opt in because you deliver qualified, high-intent leads (people paying £99/month for property search).
_Novelty:_ Agents want access to YOUR audience instead of you scraping theirs. Legally clean but fundamentally different business.

**[FP #7]: Stay Personal**
_Concept:_ The project's real value is as a personal tool and learning vehicle for Fiserv AgentCore role. Commercialization introduces legal risk, payment infrastructure, and support obligations that don't serve those goals.
_Novelty:_ Sometimes the first-principles answer is "don't build the business."

### Phase 2: Constraint Mapping (Cost Model)

**Fixed costs:** ~$45/month (Fargate $35, DynamoDB $5-15, domain $1)
**Per-scan costs:** ~$0/scan (CSS/JSON extraction is free after LLM-as-compiler)
**Per-user variable:** ~$40/month (driven by AI assessments)
**Break-even at 10 users:** ~$45/user/month → £49/month minimum viable price
**Recommended price:** £99/month (luxury concierge positioning)

### Legal Risk Assessment

| Risk | Likelihood | Impact |
|------|-----------|--------|
| C&D from Rightmove | High (if charging) | Must comply, lose primary data source |
| IP infringement claim | Low-medium | Legal costs, potential damages |
| Customer refunds on shutdown | Certain if C&D received | Financial + reputational |

## Session Decision

**Decision: Stay personal. Do not commercialize.**

**Rationale:**
- Legal risk of commercial scraping is existential, not manageable
- The viable commercial path (agent partnerships) is a fundamentally different business
- Project's primary value is personal use + learning vehicle for Fiserv
- Architecture insights (shared catalog, gated AI, on-demand scoring) are banked for future reference if revisited

## Creative Facilitation Narrative

_Session reached a clear decision faster than expected. First Principles Thinking was the right technique — stripping away SaaS assumptions revealed that the shared property catalog architecture solves the cost problem elegantly, but the legal constraint makes the entire question moot for now. The user correctly identified that the agent-partnership model, while legally viable, is a different business entirely. Decision to stay personal was firm and well-reasoned._
