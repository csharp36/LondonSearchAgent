# LondonSearchAgent — Data Models

## Overview

5 DynamoDB tables using the Enhanced Client mapper with `@DynamoDbBean` annotated POJOs. All tables use on-demand billing and point-in-time recovery.

## Entity Relationship

```
SearchConfig (criteria)          MonitoredSite (sources)
      |                                |
      | drives scoring + filtering     | drives scraping
      |                                |
      v                                v
  Property ◄──── 1:N ────► Listing
      |                    (one per source site)
      |
      | referenced by
      v
  AlertRecord (email history + smart link tokens)
```

## Property

**Table:** `Properties` | **PK:** `id` (String, UUID)

| Field | Type | Description |
|-------|------|-------------|
| id | String | UUID partition key |
| address | String | Raw address from extraction |
| normalizedAddress | String | Lowercase, trimmed for dedup matching |
| area | String | Classified: Mayfair, Marylebone, South Kensington, Other |
| bedrooms | Integer | |
| bathrooms | Integer | |
| price | Integer | Same as pricePerMonth (legacy field) |
| currency | String | Always "GBP" |
| pricePerMonth | Integer | Normalized monthly price (pw*52/12, pa/12) |
| sqft | Integer | |
| propertyType | String | Flat, House, Studio, Maisonette |
| furnishing | String | Furnished, Part-furnished, Unfurnished |
| availableFrom | String | ISO date or free text ("Available now") |
| description | String | First 200 chars from extraction |
| aiSummary | String | 2-3 sentence Claude Sonnet assessment |
| matchScore | Integer | Combined score 0-100 (structured*0.6 + AI*0.4) |
| status | String | new -> seen -> saved/dismissed |
| firstSeenAt | Instant | When first extracted |
| lastUpdatedAt | Instant | Last modification |

**GSI:** `area-firstSeenAt-index` (PK: area, SK: firstSeenAt) — used by FeedController area filter
**GSI:** `status-firstSeenAt-index` (PK: status, SK: firstSeenAt) — used by FeedController status filter

## Listing

**Table:** `Listings` | **PK:** `propertyId` (String) | **SK:** `siteListingId` (String)

| Field | Type | Description |
|-------|------|-------------|
| propertyId | String | FK to Property.id |
| siteListingId | String | Composite: `sitename#uuid8chars` |
| siteName | String | Human name (e.g. "Rightmove") |
| siteUrl | String | Base URL of monitored site |
| originalPrice | String | Raw price string as scraped |
| originalAddress | String | Raw address string as scraped |
| listingUrl | String | Absolute URL to listing page |
| imageUrls | List\<String\> | Validated property image URLs |
| floorPlanUrl | String | Floor plan image URL |
| agentName | String | |
| agentPhone | String | |
| agentEmail | String | |
| scrapedAt | Instant | When this listing was scraped |

One Property has many Listings (one per estate agent source). Pipeline prevents duplicate listings from the same site for the same property.

## SearchConfig

**Table:** `SearchConfigs` | **PK:** `id` (String, UUID)

| Field | Type | Description |
|-------|------|-------------|
| id | String | UUID partition key |
| name | String | Human label |
| areas | List\<String\> | Target areas (Mayfair, Marylebone, South Kensington) |
| minBeds / maxBeds | Integer | Bedroom range |
| minPrice / maxPrice | Integer | Monthly price range (GBP) |
| minBaths | Integer | Minimum bathrooms |
| furnishing | List\<String\> | Preferred furnishing types |
| propertyTypes | List\<String\> | Preferred property types |
| additionalCriteria | String | Free text for AI scoring prompt |
| enabled | Boolean | Only first enabled config is active |
| createdAt | Instant | |

Used by: pipeline URL expansion, structured scoring, feed price filtering, AI assessment prompt.

## MonitoredSite

**Table:** `MonitoredSites` | **PK:** `id` (String, UUID)

| Field | Type | Description |
|-------|------|-------------|
| id | String | UUID partition key |
| name | String | Estate agent name |
| baseUrl | String | Root URL (also used for image proxy allowlist) |
| searchUrlTemplate | String | URL pattern with {area}, {minBeds}, {rightmoveCode} etc. |
| scraperType | String | "http" (Jsoup), "js-rendered" (Playwright), "blocked" (skip) |
| enabled | Boolean | Pipeline skips disabled sites |
| lastCheckedAt | Instant | Updated each pipeline run |
| lastChangeHash | String | SHA-256 of last fetched content (stored but unused) |
| tier | String | aggregator, tier1, tier2 |

19 sites seeded by DataSeeder on startup.

## AlertRecord

**Table:** `Alerts` | **PK:** `id` (String, UUID)

| Field | Type | Description |
|-------|------|-------------|
| id | String | UUID partition key |
| propertyIds | List\<String\> | Top 5 property IDs included in email |
| emailSentTo | String | Recipient address |
| sentAt | Instant | Dispatch timestamp |
| smartLinkToken | String | UUID token for passwordless login |
| tokenExpiresAt | Instant | 24 hours after creation |
| newPropertyCount | Integer | Count at time of alert |

Token lookup uses full-table scan with filter expression (no GSI for smartLinkToken).
