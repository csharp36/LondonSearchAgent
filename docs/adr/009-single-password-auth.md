# ADR-009: Single-Password Authentication with Smart Links

**Status:** Accepted  
**Date:** 2026-05-16  
**Context:** This is a personal-use application for one user. A full user management system (registration, password reset, email verification) would be over-engineered.

## Decision

Use a single shared password for authentication, set via the `APP_PASSWORD` environment variable (default: `changeme`). The login form has only a password field — no username.

For email alerts, include a "smart link" (`/alert/{token}`) that grants an authenticated session without requiring the password. Tokens are UUID-based, stored in the Alerts DynamoDB table, and expire after 24 hours.

## Rationale

- Single user = single password. No user table, no password hashing, no session management beyond Spring Security's defaults.
- Smart links allow clicking through from email alerts directly to the dashboard without re-entering the password
- The password is injected at deploy time via `LONDONSEARCH_PASSWORD` env var in the CDK PortalStack
- API endpoints (`/agent/**`, `/api/image`, `/alert/**`) are publicly accessible without auth — they either need to be callable by EventBridge (future), the browser (image proxy), or email clients (smart links)

## Consequences

- Anyone with the password has full access — no role-based access control
- The password is stored in plaintext in the Fargate task definition environment variables (visible in AWS Console)
- Smart link tokens could be brute-forced in theory (UUID v4 = 122 bits of entropy, but no rate limiting on `/alert/`)
- If multi-user support is ever needed, this will require a full rewrite of the auth layer
