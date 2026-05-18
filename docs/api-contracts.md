# LondonSearchAgent — API Contracts

## Authentication

Single shared password via Spring Security form login. CSRF enabled with cookie-based tokens except for `/agent/**` and `/invocations`.

Public endpoints: `/login`, `/agent/**`, `/api/image`, `/alert/**`, `/actuator/health`
Protected endpoints: everything else (requires authenticated session)

## Web UI Endpoints

### Feed

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/` | Yes | Property feed with area/status filters and sort |
| GET | `/property/{id}` | Yes | Property detail page (marks new->seen) |
| POST | `/property/{id}/status` | Yes | Update property status (saved/dismissed) |
| GET | `/login` | No | Login page |

**GET /** params: `area` (String), `filter` ("all"/"new"/"saved"), `sort` ("score"/"date")

### Configuration

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/config/search` | Yes | Search criteria management |
| POST | `/config/search/save` | Yes | Create/update search config |
| POST | `/config/search/{id}/toggle` | Yes | Enable/disable search config |
| POST | `/config/search/{id}/delete` | Yes | Delete search config |
| GET | `/config/sites` | Yes | Monitored sites management |
| POST | `/config/sites/add` | Yes | Add new monitored site |
| POST | `/config/sites/{id}/toggle` | Yes | Enable/disable site |
| POST | `/config/sites/{id}/delete` | Yes | Delete site |
| GET | `/config/account` | Yes | Account dashboard / alert history |
| GET | `/config/pipeline/progress` | Yes | Pipeline progress page (polls /agent/progress) |

### POST /config/search/save params:
`id` (optional), `name`, `areas` (comma-separated), `minBeds`, `maxBeds`, `minPrice`, `maxPrice`, `minBaths`, `furnishing` (list), `additionalCriteria`, `action` ("scan" redirects to progress page)

## Pipeline API

| Method | Path | Auth | CSRF | Purpose |
|--------|------|------|------|---------|
| POST | `/agent/run` | No | No | Synchronous pipeline run |
| POST | `/agent/run-async` | No | No | Async pipeline run (returns immediately) |
| GET | `/agent/progress` | No | — | Pipeline status JSON (polled by UI) |
| GET | `/agent/ping` | No | — | Health check (ALB target) |
| POST | `/agent/invocations` | No | No | SageMaker-compatible invocation endpoint |

### GET /agent/progress response:
```json
{
  "phase": "running|complete|error|idle",
  "sitesProcessed": 5,
  "sitesTotal": 16,
  "currentSite": "Rightmove",
  "siteResults": ["Rightmove: 15 new, 3 updated", ...],
  "newProperties": 15,
  "updatedProperties": 3,
  "error": null,
  "startedAt": "2026-05-18T01:47:35Z"
}
```

### POST /agent/run response:
```json
{
  "status": "success|completed_with_errors",
  "sitesProcessed": 16,
  "sitesSkipped": 0,
  "newProperties": 52,
  "updatedProperties": 48,
  "errors": []
}
```

## Utility Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/image?url=` | No | Image proxy (domain allowlist enforced) |
| GET | `/alert/{token}` | No | Smart link — validates token, grants session, redirects to feed |

### GET /api/image
- Proxies images from allowed estate agent domains to bypass hotlink protection
- Allowed domains: all MonitoredSite.baseUrl hosts + known CDN domains (Rightmove media, Zoopla CDN, Foxtons assets, Savills, OnTheMarket, Homeflow)
- Returns 403 for non-allowed domains, 404 for fetch failures or non-image content
- Caches responses for 24 hours

### GET /alert/{token}
- Validates UUID token against AlertRecord table (full-table scan with filter)
- Tokens expire after 24 hours
- On success: creates authenticated session with ROLE_USER, redirects to `/?filter=new`
- On failure: redirects to `/login?expired`
