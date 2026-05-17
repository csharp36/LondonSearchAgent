# ADR-001: Dual-Model Bedrock Strategy

**Status:** Accepted  
**Date:** 2026-05-16  
**Context:** The pipeline needs LLM capabilities for two distinct tasks: extracting structured data from HTML (high volume, every page) and generating property assessments (lower volume, new properties only).

## Decision

Use two separate Bedrock models:

- **Amazon Nova Micro** (`amazon.nova-micro-v1:0`) for HTML extraction — called for every page fetched
- **Claude Sonnet** (`us.anthropic.claude-sonnet-4-6` cross-region inference profile) for AI property assessments — called only for newly created properties

## Rationale

- Nova Micro is significantly cheaper per token and fast enough for the structured extraction task (JSON array output from HTML)
- Claude Sonnet provides higher-quality reasoning for subjective property assessment and scoring
- Extraction is high-volume (19 sites × 3 areas × each run), while assessment only runs on new properties (typically 5-20 per run)
- Separating the models allows independent cost control and model upgrades

## Consequences

- Two IAM permissions needed (`bedrock:InvokeModel` on foundation-model and inference-profile ARNs)
- Cross-region routing means IAM must wildcard the region (`arn:aws:bedrock:*::foundation-model/*`) because Claude Sonnet routes to us-east-2 even when the client targets us-east-1
- Two different prompt engineering approaches: extraction prompt is rigid (JSON schema), assessment prompt is conversational
