# ADR-002: Fargate in Public Subnet Without NAT Gateway

**Status:** Accepted  
**Date:** 2026-05-16  
**Context:** The Fargate task needs outbound internet access to scrape estate agent websites and call Bedrock APIs. NAT Gateways cost ~$32/month minimum, which is significant for a personal project.

## Decision

Run the Fargate task in a public subnet with `assignPublicIp(true)` instead of using a NAT Gateway in a private subnet.

## Rationale

- NAT Gateway costs $0.045/hour (~$32/month) even when idle — more than the Fargate task itself for a single-task deployment
- A public IP on the Fargate task provides free outbound internet access
- VPC Gateway Endpoints for S3 and DynamoDB keep that traffic internal (free)
- Interface endpoints for ECR, ECR-Docker, and CloudWatch Logs allow container image pull and logging without internet
- The ALB provides the public ingress; the task's public IP is only used for outbound

## Consequences

- The task has a public IP, but security groups restrict inbound traffic to ALB health checks only
- If the architecture scales to multiple tasks or private services, a NAT Gateway may become necessary
- An earlier attempt to add a NAT Gateway caused a CloudFormation rollback that left dirty state on the Network stack — this approach avoids that entirely
