# LondonSearchAgent — Infrastructure Architecture

## Overview

AWS infrastructure defined as Java CDK (2.248.0) in the `infra/` subproject. Four CloudFormation stacks deployed to account 710703498172 in us-east-1.

## Stack Dependency Graph

```
NetworkStack (VPC)
     |
     ├── DataStack (DynamoDB + S3)
     |        |
     └────────┼── PortalStack (ECS Fargate + ALB)
              |
              └── ScheduleStack (EventBridge rules)
```

## Stacks

### NetworkStack
- **VPC:** 2 AZs, no NAT gateways (cost optimization)
- **Gateway endpoints:** S3, DynamoDB (free, private access)
- **Interface endpoints:** ECR, ECR Docker, CloudWatch Logs (for Fargate image pulls and logging)
- **Note:** Dirty CloudFormation state from reverted Bedrock VPC endpoint — do not deploy

### DataStack
5 DynamoDB tables (on-demand billing, point-in-time recovery, RETAIN removal policy):

| Table | Partition Key | Sort Key | GSIs |
|-------|-------------|----------|------|
| Properties | id | — | area-firstSeenAt-index, status-firstSeenAt-index |
| Listings | propertyId | siteListingId | — |
| SearchConfigs | id | — | — |
| MonitoredSites | id | — | — |
| Alerts | id | — | — |

S3 bucket for images (versioning off, S3-managed encryption, all public access blocked).

### PortalStack
- **Compute:** ECS Fargate, 1 vCPU / 2GB RAM, 1 desired task
- **Container:** Docker image built from `../app` with `Platform.LINUX_AMD64` and `cacheDisabled(true)` (for ARM Mac cross-compile)
- **Load Balancer:** Internet-facing ALB, port 80
- **Networking:** Public subnet with public IP (tasks reach Bedrock via public internet)
- **Health check:** `GET /agent/ping`
- **IAM grants:** DynamoDB read-write (all 5 tables), S3 read-write (images bucket), Bedrock InvokeModel (wildcard regions for cross-region inference)
- **Environment variables:** SPRING_PROFILES_ACTIVE, AWS_REGION, EXTRACTOR_TYPE, BEDROCK_REGION, APP_PASSWORD, table names

### ScheduleStack
4 EventBridge rules at 06:00, 12:00, 18:00, 00:00 UTC. **Rules exist but have no targets wired** — pipeline is not auto-triggered.

## Deployment

```bash
./gradlew :app:clean :app:bootJar
rm -rf infra/cdk.out
export CDK_DEFAULT_ACCOUNT=710703498172 CDK_DEFAULT_REGION=us-east-1 LONDONSEARCH_PASSWORD=<password>
cdk deploy LondonSearch-Portal --exclusively --require-approval never
```

- Always use `--exclusively` to avoid the Network stack (dirty state)
- Always `rm -rf infra/cdk.out` first to avoid lock errors
- Always rebuild the jar before deploying (CDK hashes the Docker build context)
- Docker builds take ~5 min due to amd64 cross-compile on ARM Mac (QEMU)

## Production Access

- **URL:** https://londonsearch.mandati.ai (Cloudflare Flexible SSL -> ALB HTTP)
- **AWS:** Account 710703498172, region us-east-1, IAM user `londonsearchagent`
- **Logs:** CloudWatch log groups `LondonSearch-Portal-PortalTaskDef*`
