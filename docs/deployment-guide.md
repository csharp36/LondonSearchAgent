# LondonSearchAgent — Deployment Guide

## Infrastructure

All AWS resources defined in `infra/` using CDK 2.248.0 (Java). Four stacks in account 710703498172, region us-east-1.

| Stack | Resources | Status |
|-------|-----------|--------|
| Network | VPC, 2 AZs, VPC endpoints | Deployed (do NOT redeploy — dirty CF state) |
| Data | 5 DynamoDB tables, S3 bucket | Deployed |
| Portal | ECS Fargate, ALB | Active — deploy target |
| Schedule | 4 EventBridge rules | Deployed (no targets wired) |

## Deploy Process

```bash
# 1. Build the application JAR
./gradlew :app:clean :app:bootJar

# 2. Clear CDK cache (prevents lock errors)
rm -rf infra/cdk.out

# 3. Set environment variables
export CDK_DEFAULT_ACCOUNT=710703498172
export CDK_DEFAULT_REGION=us-east-1
export LONDONSEARCH_PASSWORD=<your-password>

# 4. Deploy Portal stack only
cdk deploy LondonSearch-Portal --exclusively --require-approval never
```

**Total deploy time:** ~4-5 minutes (Docker cross-compile + CloudFormation update)

## Critical Notes

- **Always use `--exclusively`** — avoids the Network stack which has dirty CloudFormation rollback state
- **Always `rm -rf infra/cdk.out` first** — stale synthesis output can cause lock errors
- **Always rebuild the JAR** — CDK hashes the Docker build context including the JAR; stale JARs produce cache hits with old code
- **Docker cross-compile is slow** — `Platform.LINUX_AMD64` + `cacheDisabled(true)` forces QEMU emulation on ARM Mac (~5 min)

## Container

- **Base image:** `eclipse-temurin:21-jre-jammy` (Ubuntu 22.04)
- **Platform:** `linux/amd64` (explicit in CDK AssetImageProps)
- **Dependencies:** 27 Chromium system libraries for Playwright headless browser
- **Port:** 8080

## Production Environment

| Setting | Value |
|---------|-------|
| URL | https://londonsearch.mandati.ai |
| SSL | Cloudflare Flexible SSL -> ALB HTTP |
| AWS Account | 710703498172 |
| AWS Region | us-east-1 |
| IAM User | londonsearchagent |
| Compute | ECS Fargate, 1 vCPU, 2GB RAM |
| Health Check | GET /agent/ping |

## Environment Variables (set by CDK on ECS task)

```
SPRING_PROFILES_ACTIVE=prod
AWS_REGION=us-east-1
EXTRACTOR_TYPE=bedrock
BEDROCK_REGION=us-east-1
APP_PASSWORD=<from LONDONSEARCH_PASSWORD>
PROPERTIES_TABLE=Properties
LISTINGS_TABLE=Listings
SEARCH_CONFIGS_TABLE=SearchConfigs
MONITORED_SITES_TABLE=MonitoredSites
ALERTS_TABLE=Alerts
```

## Monitoring

- **Logs:** CloudWatch log groups `LondonSearch-Portal-PortalTaskDef*` (three groups from stack updates)
- **Health:** ALB target health check on `/agent/ping`
- **No dashboards or custom metrics configured**

### Checking Logs

```bash
# Find latest log stream
aws logs describe-log-streams \
  --log-group-name "LondonSearch-Portal-PortalTaskDefwebLogGroupF00FA41E-MGP7vtkNBSet" \
  --region us-east-1 --order-by LastEventTime --descending --limit 1

# Tail recent logs
aws logs get-log-events \
  --log-group-name "<group-name>" \
  --region us-east-1 \
  --log-stream-name "<stream-name>" \
  --limit 50 --query 'events[].message' --output text
```

## Data Management

### Clearing Tables for Re-scan

```bash
# Clear Properties
aws dynamodb scan --table-name Properties --region us-east-1 \
  --projection-expression "id" --query 'Items[].id.S' --output text \
  | tr '\t' '\n' | while read id; do
    aws dynamodb delete-item --table-name Properties --region us-east-1 \
      --key "{\"id\":{\"S\":\"$id\"}}"
  done

# Clear Listings (composite key)
aws dynamodb scan --table-name Listings --region us-east-1 \
  --projection-expression "propertyId, siteListingId" --output json \
  | python3 -c "import json,sys,subprocess; [subprocess.run(['aws','dynamodb','delete-item','--table-name','Listings','--region','us-east-1','--key',json.dumps({'propertyId':{'S':i['propertyId']['S']},'siteListingId':{'S':i['siteListingId']['S']}})],capture_output=True) for i in json.load(sys.stdin).get('Items',[])]"

# Trigger fresh scan
curl -X POST https://londonsearch.mandati.ai/agent/run-async
```
