# Phase 1: Foundation & Portal Shell — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a working web portal with authentication, card grid property feed, property detail view, and search/site configuration — backed by DynamoDB with seed data — deployable to AWS via CDK.

**Architecture:** Multi-module Gradle project. `app/` contains the Spring Boot portal (Thymeleaf + HTMX + Tailwind). `infra/` contains CDK stacks for VPC, DynamoDB, S3, ECS Fargate, and ALB. Local development uses DynamoDB Local via Docker Compose.

**Tech Stack:** Java 21, Spring Boot 3.5.14, AWS SDK v2 (2.44.5), AWS CDK (2.248.0), DynamoDB Enhanced Client, Thymeleaf, HTMX 2.0, Tailwind CSS v4, Docker

---

## File Structure

```
LondonSearchAgent/
├── settings.gradle.kts                          # Multi-module project settings
├── build.gradle.kts                             # Root build — shared config
├── cdk.json                                     # CDK toolkit configuration
├── docker-compose.yml                           # DynamoDB Local for dev
├── gradle/
│   └── libs.versions.toml                       # Version catalog
├── app/
│   ├── build.gradle.kts                         # Spring Boot + AWS deps
│   ├── Dockerfile                               # ARM64 container for ECS
│   └── src/
│       ├── main/
│       │   ├── java/com/londonsearch/
│       │   │   ├── LondonSearchApplication.java
│       │   │   ├── config/
│       │   │   │   ├── DynamoDbConfig.java      # DynamoDB client beans
│       │   │   │   └── SecurityConfig.java      # Shared-secret auth
│       │   │   ├── model/
│       │   │   │   ├── Property.java            # DynamoDbBean entity
│       │   │   │   ├── Listing.java             # DynamoDbBean entity
│       │   │   │   ├── SearchConfig.java        # DynamoDbBean entity
│       │   │   │   └── MonitoredSite.java       # DynamoDbBean entity
│       │   │   ├── repository/
│       │   │   │   ├── PropertyRepository.java
│       │   │   │   ├── ListingRepository.java
│       │   │   │   ├── SearchConfigRepository.java
│       │   │   │   └── MonitoredSiteRepository.java
│       │   │   ├── controller/
│       │   │   │   ├── FeedController.java      # Unified feed + filtering
│       │   │   │   ├── PropertyController.java  # Property detail view
│       │   │   │   └── ConfigController.java    # Search & site config
│       │   │   └── seed/
│       │   │       └── DataSeeder.java          # Dev seed data loader
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-local.yml        # Local DynamoDB endpoint
│       │       ├── static/
│       │       │   └── css/
│       │       │       └── input.css            # Tailwind entry point
│       │       └── templates/
│       │           ├── layout.html              # Base layout
│       │           ├── login.html
│       │           ├── feed.html                # Card grid feed
│       │           ├── property-detail.html
│       │           ├── config/
│       │           │   ├── search.html          # Search criteria tab
│       │           │   └── sites.html           # Monitored sites tab
│       │           └── fragments/
│       │               ├── property-card.html   # Reusable card component
│       │               └── filter-pills.html    # Area filter pills
│       └── test/
│           └── java/com/londonsearch/
│               ├── LondonSearchApplicationTests.java
│               ├── repository/
│               │   ├── PropertyRepositoryTest.java
│               │   └── SearchConfigRepositoryTest.java
│               └── controller/
│                   ├── FeedControllerTest.java
│                   └── SecurityTest.java
├── infra/
│   ├── build.gradle.kts                         # CDK dependencies
│   └── src/main/java/com/londonsearch/infra/
│       ├── InfraApp.java                        # CDK entry point
│       ├── NetworkStack.java                    # VPC + endpoints
│       ├── DataStack.java                       # DynamoDB + S3
│       └── PortalStack.java                     # ECS Fargate + ALB
```

---

## Task 1: Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `infra/build.gradle.kts`
- Create: `cdk.json`

- [ ] **Step 1: Create the Gradle version catalog**

```toml
# gradle/libs.versions.toml
[versions]
spring-boot = "3.5.14"
spring-dependency-management = "1.1.7"
aws-sdk = "2.44.5"
aws-cdk = "2.248.0"
constructs = "10.4.2"
htmx-spring = "4.0.1"

[libraries]
aws-dynamodb-enhanced = { module = "software.amazon.awssdk:dynamodb-enhanced" }
aws-url-connection-client = { module = "software.amazon.awssdk:url-connection-client" }
aws-s3 = { module = "software.amazon.awssdk:s3" }
aws-ses = { module = "software.amazon.awssdk:ses" }
aws-secretsmanager = { module = "software.amazon.awssdk:secretsmanager" }
aws-cdk-lib = { module = "software.amazon.awscdk:aws-cdk-lib", version.ref = "aws-cdk" }
constructs = { module = "software.constructs:constructs", version.ref = "constructs" }
htmx-spring-boot = { module = "io.github.wimdeblauwe:htmx-spring-boot-thymeleaf", version.ref = "htmx-spring" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
```

- [ ] **Step 2: Create the root build file**

```kotlin
// build.gradle.kts
plugins {
    java
}

allprojects {
    group = "com.londonsearch"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}
```

- [ ] **Step 3: Create settings.gradle.kts**

```kotlin
// settings.gradle.kts
rootProject.name = "LondonSearchAgent"
include("app", "infra")
```

- [ ] **Step 4: Create the app build file**

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom("software.amazon.awssdk:bom:${libs.versions.aws.sdk.get()}")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation(libs.htmx.spring.boot)
    implementation(libs.aws.dynamodb.enhanced)
    implementation(libs.aws.url.connection.client)
    implementation(libs.aws.s3)

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 5: Create the infra build file**

```kotlin
// infra/build.gradle.kts
plugins {
    application
}

application {
    mainClass = "com.londonsearch.infra.InfraApp"
}

dependencies {
    implementation(libs.aws.cdk.lib)
    implementation(libs.constructs)
}
```

- [ ] **Step 6: Create cdk.json**

```json
{
  "app": "cd infra && ../gradlew -q run",
  "context": {
    "@aws-cdk/core:stackRelativeExports": true
  }
}
```

- [ ] **Step 7: Create source directories**

Run:
```bash
mkdir -p app/src/main/java/com/londonsearch/{config,model,repository,controller,seed}
mkdir -p app/src/main/resources/{static/css,templates/{config,fragments}}
mkdir -p app/src/test/java/com/londonsearch/{repository,controller}
mkdir -p infra/src/main/java/com/londonsearch/infra
```

- [ ] **Step 8: Verify the build compiles**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL (no source files yet, but Gradle config is valid)

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/ app/build.gradle.kts infra/build.gradle.kts cdk.json
git commit -m "feat: scaffold multi-module Gradle project with Spring Boot and CDK"
```

---

## Task 2: Spring Boot Application Skeleton

**Files:**
- Create: `app/src/main/java/com/londonsearch/LondonSearchApplication.java`
- Create: `app/src/main/resources/application.yml`
- Create: `app/src/main/resources/application-local.yml`
- Create: `app/src/test/java/com/londonsearch/LondonSearchApplicationTests.java`

- [ ] **Step 1: Create the main application class**

```java
// app/src/main/java/com/londonsearch/LondonSearchApplication.java
package com.londonsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LondonSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LondonSearchApplication.class, args);
    }
}
```

- [ ] **Step 2: Create application.yml**

```yaml
# app/src/main/resources/application.yml
server:
  port: 8080

spring:
  threads:
    virtual:
      enabled: true
  thymeleaf:
    cache: false

app:
  password: ${APP_PASSWORD:changeme}
  aws:
    region: ${AWS_REGION:eu-west-2}
    dynamodb:
      endpoint: ${DYNAMODB_ENDPOINT:}
    tables:
      properties: ${PROPERTIES_TABLE:Properties}
      listings: ${LISTINGS_TABLE:Listings}
      search-configs: ${SEARCH_CONFIGS_TABLE:SearchConfigs}
      monitored-sites: ${MONITORED_SITES_TABLE:MonitoredSites}
```

- [ ] **Step 3: Create application-local.yml**

```yaml
# app/src/main/resources/application-local.yml
app:
  aws:
    region: eu-west-2
    dynamodb:
      endpoint: http://localhost:8000
  seed-data: true
```

- [ ] **Step 4: Create the context loads test**

```java
// app/src/test/java/com/londonsearch/LondonSearchApplicationTests.java
package com.londonsearch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LondonSearchApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

Create `app/src/test/resources/application-test.yml`:

```yaml
# app/src/test/resources/application-test.yml
app:
  password: testpassword
  aws:
    region: eu-west-2
    dynamodb:
      endpoint: http://localhost:8000
  seed-data: false
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/
git commit -m "feat: add Spring Boot application skeleton with config profiles"
```

---

## Task 3: Docker Compose + DynamoDB Config

**Files:**
- Create: `docker-compose.yml`
- Create: `app/src/main/java/com/londonsearch/config/DynamoDbConfig.java`

- [ ] **Step 1: Create docker-compose.yml for DynamoDB Local**

```yaml
# docker-compose.yml
services:
  dynamodb-local:
    image: amazon/dynamodb-local:latest
    container_name: london-search-dynamodb
    ports:
      - "8000:8000"
    command: ["-jar", "DynamoDBLocal.jar", "-sharedDb", "-inMemory"]
```

- [ ] **Step 2: Start DynamoDB Local**

Run: `docker compose up -d`

Expected: Container `london-search-dynamodb` starts on port 8000

- [ ] **Step 3: Write the failing test for DynamoDB config**

```java
// app/src/test/java/com/londonsearch/config/DynamoDbConfigTest.java
package com.londonsearch.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DynamoDbConfigTest {

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private DynamoDbEnhancedClient enhancedClient;

    @Test
    void dynamoDbClientBeanExists() {
        assertThat(dynamoDbClient).isNotNull();
    }

    @Test
    void enhancedClientBeanExists() {
        assertThat(enhancedClient).isNotNull();
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.londonsearch.config.DynamoDbConfigTest"`

Expected: FAIL — no qualifying bean of type DynamoDbClient

- [ ] **Step 5: Create DynamoDbConfig**

```java
// app/src/main/java/com/londonsearch/config/DynamoDbConfig.java
package com.londonsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    @Value("${app.aws.region}")
    private String region;

    @Value("${app.aws.dynamodb.endpoint:}")
    private String endpoint;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:test --tests "com.londonsearch.config.DynamoDbConfigTest"`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add docker-compose.yml app/src/
git commit -m "feat: add DynamoDB Local dev setup and client configuration"
```

---

## Task 4: DynamoDB Entity Models

**Files:**
- Create: `app/src/main/java/com/londonsearch/model/Property.java`
- Create: `app/src/main/java/com/londonsearch/model/Listing.java`
- Create: `app/src/main/java/com/londonsearch/model/SearchConfig.java`
- Create: `app/src/main/java/com/londonsearch/model/MonitoredSite.java`

- [ ] **Step 1: Create the Property entity**

```java
// app/src/main/java/com/londonsearch/model/Property.java
package com.londonsearch.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;

@DynamoDbBean
public class Property {

    private String id;
    private String address;
    private String normalizedAddress;
    private String area;
    private Integer bedrooms;
    private Integer bathrooms;
    private Integer price;
    private String currency;
    private Integer pricePerMonth;
    private Integer sqft;
    private String propertyType;
    private String furnishing;
    private String availableFrom;
    private String description;
    private String aiSummary;
    private Integer matchScore;
    private String status;
    private Instant firstSeenAt;
    private Instant lastUpdatedAt;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getNormalizedAddress() { return normalizedAddress; }
    public void setNormalizedAddress(String normalizedAddress) { this.normalizedAddress = normalizedAddress; }

    @DynamoDbSecondaryPartitionKey(indexNames = "area-firstSeenAt-index")
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public Integer getBedrooms() { return bedrooms; }
    public void setBedrooms(Integer bedrooms) { this.bedrooms = bedrooms; }

    public Integer getBathrooms() { return bathrooms; }
    public void setBathrooms(Integer bathrooms) { this.bathrooms = bathrooms; }

    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getPricePerMonth() { return pricePerMonth; }
    public void setPricePerMonth(Integer pricePerMonth) { this.pricePerMonth = pricePerMonth; }

    public Integer getSqft() { return sqft; }
    public void setSqft(Integer sqft) { this.sqft = sqft; }

    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }

    public String getFurnishing() { return furnishing; }
    public void setFurnishing(String furnishing) { this.furnishing = furnishing; }

    public String getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(String availableFrom) { this.availableFrom = availableFrom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public Integer getMatchScore() { return matchScore; }
    public void setMatchScore(Integer matchScore) { this.matchScore = matchScore; }

    @DynamoDbSecondaryPartitionKey(indexNames = "status-firstSeenAt-index")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @DynamoDbSecondarySortKey(indexNames = {"area-firstSeenAt-index", "status-firstSeenAt-index"})
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}
```

- [ ] **Step 2: Create the Listing entity**

```java
// app/src/main/java/com/londonsearch/model/Listing.java
package com.londonsearch.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;

@DynamoDbBean
public class Listing {

    private String propertyId;
    private String siteListingId;
    private String siteName;
    private String siteUrl;
    private String originalPrice;
    private String originalAddress;
    private String listingUrl;
    private List<String> imageUrls;
    private String floorPlanUrl;
    private String agentName;
    private String agentPhone;
    private String agentEmail;
    private Instant scrapedAt;

    @DynamoDbPartitionKey
    public String getPropertyId() { return propertyId; }
    public void setPropertyId(String propertyId) { this.propertyId = propertyId; }

    @DynamoDbSortKey
    public String getSiteListingId() { return siteListingId; }
    public void setSiteListingId(String siteListingId) { this.siteListingId = siteListingId; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }

    public String getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(String originalPrice) { this.originalPrice = originalPrice; }

    public String getOriginalAddress() { return originalAddress; }
    public void setOriginalAddress(String originalAddress) { this.originalAddress = originalAddress; }

    public String getListingUrl() { return listingUrl; }
    public void setListingUrl(String listingUrl) { this.listingUrl = listingUrl; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public String getFloorPlanUrl() { return floorPlanUrl; }
    public void setFloorPlanUrl(String floorPlanUrl) { this.floorPlanUrl = floorPlanUrl; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getAgentPhone() { return agentPhone; }
    public void setAgentPhone(String agentPhone) { this.agentPhone = agentPhone; }

    public String getAgentEmail() { return agentEmail; }
    public void setAgentEmail(String agentEmail) { this.agentEmail = agentEmail; }

    public Instant getScrapedAt() { return scrapedAt; }
    public void setScrapedAt(Instant scrapedAt) { this.scrapedAt = scrapedAt; }
}
```

- [ ] **Step 3: Create the SearchConfig entity**

```java
// app/src/main/java/com/londonsearch/model/SearchConfig.java
package com.londonsearch.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;

@DynamoDbBean
public class SearchConfig {

    private String id;
    private String name;
    private List<String> areas;
    private Integer minBeds;
    private Integer maxBeds;
    private Integer minPrice;
    private Integer maxPrice;
    private Integer minBaths;
    private List<String> furnishing;
    private List<String> propertyTypes;
    private String additionalCriteria;
    private Boolean enabled;
    private Instant createdAt;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getAreas() { return areas; }
    public void setAreas(List<String> areas) { this.areas = areas; }

    public Integer getMinBeds() { return minBeds; }
    public void setMinBeds(Integer minBeds) { this.minBeds = minBeds; }

    public Integer getMaxBeds() { return maxBeds; }
    public void setMaxBeds(Integer maxBeds) { this.maxBeds = maxBeds; }

    public Integer getMinPrice() { return minPrice; }
    public void setMinPrice(Integer minPrice) { this.minPrice = minPrice; }

    public Integer getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Integer maxPrice) { this.maxPrice = maxPrice; }

    public Integer getMinBaths() { return minBaths; }
    public void setMinBaths(Integer minBaths) { this.minBaths = minBaths; }

    public List<String> getFurnishing() { return furnishing; }
    public void setFurnishing(List<String> furnishing) { this.furnishing = furnishing; }

    public List<String> getPropertyTypes() { return propertyTypes; }
    public void setPropertyTypes(List<String> propertyTypes) { this.propertyTypes = propertyTypes; }

    public String getAdditionalCriteria() { return additionalCriteria; }
    public void setAdditionalCriteria(String additionalCriteria) { this.additionalCriteria = additionalCriteria; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: Create the MonitoredSite entity**

```java
// app/src/main/java/com/londonsearch/model/MonitoredSite.java
package com.londonsearch.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;

@DynamoDbBean
public class MonitoredSite {

    private String id;
    private String name;
    private String baseUrl;
    private String searchUrlTemplate;
    private String scraperType;
    private Boolean enabled;
    private Instant lastCheckedAt;
    private String lastChangeHash;
    private String tier;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getSearchUrlTemplate() { return searchUrlTemplate; }
    public void setSearchUrlTemplate(String searchUrlTemplate) { this.searchUrlTemplate = searchUrlTemplate; }

    public String getScraperType() { return scraperType; }
    public void setScraperType(String scraperType) { this.scraperType = scraperType; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }

    public String getLastChangeHash() { return lastChangeHash; }
    public void setLastChangeHash(String lastChangeHash) { this.lastChangeHash = lastChangeHash; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
}
```

- [ ] **Step 5: Verify models compile**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/londonsearch/model/
git commit -m "feat: add DynamoDB entity models for Property, Listing, SearchConfig, MonitoredSite"
```

---

## Task 5: Repository Layer

**Files:**
- Create: `app/src/main/java/com/londonsearch/repository/PropertyRepository.java`
- Create: `app/src/main/java/com/londonsearch/repository/ListingRepository.java`
- Create: `app/src/main/java/com/londonsearch/repository/SearchConfigRepository.java`
- Create: `app/src/main/java/com/londonsearch/repository/MonitoredSiteRepository.java`
- Create: `app/src/main/java/com/londonsearch/repository/TableInitializer.java`
- Create: `app/src/test/java/com/londonsearch/repository/PropertyRepositoryTest.java`
- Create: `app/src/test/java/com/londonsearch/repository/SearchConfigRepositoryTest.java`

- [ ] **Step 1: Create TableInitializer to create tables on startup (for local dev)**

```java
// app/src/main/java/com/londonsearch/repository/TableInitializer.java
package com.londonsearch.repository;

import com.londonsearch.model.Listing;
import com.londonsearch.model.MonitoredSite;
import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

@Component
@Profile({"local", "test"})
public class TableInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TableInitializer.class);

    private final DynamoDbEnhancedClient enhancedClient;
    private final String propertiesTable;
    private final String listingsTable;
    private final String searchConfigsTable;
    private final String monitoredSitesTable;

    public TableInitializer(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.properties}") String propertiesTable,
            @Value("${app.aws.tables.listings}") String listingsTable,
            @Value("${app.aws.tables.search-configs}") String searchConfigsTable,
            @Value("${app.aws.tables.monitored-sites}") String monitoredSitesTable) {
        this.enhancedClient = enhancedClient;
        this.propertiesTable = propertiesTable;
        this.listingsTable = listingsTable;
        this.searchConfigsTable = searchConfigsTable;
        this.monitoredSitesTable = monitoredSitesTable;
    }

    @Override
    public void run(String... args) {
        createTable(propertiesTable, Property.class);
        createTable(listingsTable, Listing.class);
        createTable(searchConfigsTable, SearchConfig.class);
        createTable(monitoredSitesTable, MonitoredSite.class);
    }

    private <T> void createTable(String tableName, Class<T> beanClass) {
        try {
            DynamoDbTable<T> table = enhancedClient.table(tableName, TableSchema.fromBean(beanClass));
            table.createTable();
            log.info("Created table: {}", tableName);
        } catch (ResourceInUseException e) {
            log.debug("Table already exists: {}", tableName);
        }
    }
}
```

- [ ] **Step 2: Write failing tests for PropertyRepository**

```java
// app/src/test/java/com/londonsearch/repository/PropertyRepositoryTest.java
package com.londonsearch.repository;

import com.londonsearch.model.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PropertyRepositoryTest {

    @Autowired
    private PropertyRepository propertyRepository;

    private Property testProperty;

    @BeforeEach
    void setUp() {
        testProperty = new Property();
        testProperty.setId("test-prop-1");
        testProperty.setAddress("42 Baker Street, London W1U 3BW");
        testProperty.setNormalizedAddress("42 baker street, london w1u 3bw");
        testProperty.setArea("Marylebone");
        testProperty.setBedrooms(3);
        testProperty.setBathrooms(2);
        testProperty.setPrice(7500);
        testProperty.setCurrency("GBP");
        testProperty.setPricePerMonth(7500);
        testProperty.setSqft(1200);
        testProperty.setPropertyType("Flat");
        testProperty.setFurnishing("Furnished");
        testProperty.setStatus("new");
        testProperty.setMatchScore(92);
        testProperty.setFirstSeenAt(Instant.now());
        testProperty.setLastUpdatedAt(Instant.now());
    }

    @Test
    void saveAndFindById() {
        propertyRepository.save(testProperty);

        Optional<Property> found = propertyRepository.findById("test-prop-1");

        assertThat(found).isPresent();
        assertThat(found.get().getAddress()).isEqualTo("42 Baker Street, London W1U 3BW");
        assertThat(found.get().getBedrooms()).isEqualTo(3);
    }

    @Test
    void findByIdReturnsEmptyForMissing() {
        Optional<Property> found = propertyRepository.findById("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    void findAll() {
        propertyRepository.save(testProperty);

        Property second = new Property();
        second.setId("test-prop-2");
        second.setAddress("15 Mount Street, London W1K");
        second.setArea("Mayfair");
        second.setStatus("new");
        second.setFirstSeenAt(Instant.now());
        second.setLastUpdatedAt(Instant.now());
        propertyRepository.save(second);

        List<Property> all = propertyRepository.findAll();

        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void findByArea() {
        propertyRepository.save(testProperty);

        List<Property> marylebone = propertyRepository.findByArea("Marylebone");

        assertThat(marylebone).isNotEmpty();
        assertThat(marylebone.get(0).getArea()).isEqualTo("Marylebone");
    }

    @Test
    void findByStatus() {
        propertyRepository.save(testProperty);

        List<Property> newProps = propertyRepository.findByStatus("new");

        assertThat(newProps).isNotEmpty();
        assertThat(newProps.get(0).getStatus()).isEqualTo("new");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.londonsearch.repository.PropertyRepositoryTest"`

Expected: FAIL — PropertyRepository does not exist

- [ ] **Step 4: Create PropertyRepository**

```java
// app/src/main/java/com/londonsearch/repository/PropertyRepository.java
package com.londonsearch.repository;

import com.londonsearch.model.Property;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;

@Repository
public class PropertyRepository {

    private final DynamoDbTable<Property> table;

    public PropertyRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.properties}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Property.class));
    }

    public void save(Property property) {
        table.putItem(property);
    }

    public Optional<Property> findById(String id) {
        Key key = Key.builder().partitionValue(id).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<Property> findAll() {
        return table.scan().items().stream().toList();
    }

    public List<Property> findByArea(String area) {
        DynamoDbIndex<Property> index = table.index("area-firstSeenAt-index");
        QueryConditional condition = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(area).build());
        return index.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    public List<Property> findByStatus(String status) {
        DynamoDbIndex<Property> index = table.index("status-firstSeenAt-index");
        QueryConditional condition = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(status).build());
        return index.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    public void delete(String id) {
        Key key = Key.builder().partitionValue(id).build();
        table.deleteItem(key);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.londonsearch.repository.PropertyRepositoryTest"`

Expected: PASS (requires DynamoDB Local running via `docker compose up -d`)

- [ ] **Step 6: Create ListingRepository**

```java
// app/src/main/java/com/londonsearch/repository/ListingRepository.java
package com.londonsearch.repository;

import com.londonsearch.model.Listing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;

@Repository
public class ListingRepository {

    private final DynamoDbTable<Listing> table;

    public ListingRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.listings}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Listing.class));
    }

    public void save(Listing listing) {
        table.putItem(listing);
    }

    public List<Listing> findByPropertyId(String propertyId) {
        QueryConditional condition = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(propertyId).build());
        return table.query(condition).items().stream().toList();
    }

    public Optional<Listing> findByPropertyIdAndSiteListingId(String propertyId, String siteListingId) {
        Key key = Key.builder()
                .partitionValue(propertyId)
                .sortValue(siteListingId)
                .build();
        return Optional.ofNullable(table.getItem(key));
    }
}
```

- [ ] **Step 7: Create SearchConfigRepository**

```java
// app/src/main/java/com/londonsearch/repository/SearchConfigRepository.java
package com.londonsearch.repository;

import com.londonsearch.model.SearchConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.List;
import java.util.Optional;

@Repository
public class SearchConfigRepository {

    private final DynamoDbTable<SearchConfig> table;

    public SearchConfigRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.search-configs}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(SearchConfig.class));
    }

    public void save(SearchConfig config) {
        table.putItem(config);
    }

    public Optional<SearchConfig> findById(String id) {
        Key key = Key.builder().partitionValue(id).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<SearchConfig> findAll() {
        return table.scan().items().stream().toList();
    }

    public void delete(String id) {
        Key key = Key.builder().partitionValue(id).build();
        table.deleteItem(key);
    }
}
```

- [ ] **Step 8: Create MonitoredSiteRepository**

```java
// app/src/main/java/com/londonsearch/repository/MonitoredSiteRepository.java
package com.londonsearch.repository;

import com.londonsearch.model.MonitoredSite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.List;
import java.util.Optional;

@Repository
public class MonitoredSiteRepository {

    private final DynamoDbTable<MonitoredSite> table;

    public MonitoredSiteRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.monitored-sites}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(MonitoredSite.class));
    }

    public void save(MonitoredSite site) {
        table.putItem(site);
    }

    public Optional<MonitoredSite> findById(String id) {
        Key key = Key.builder().partitionValue(id).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<MonitoredSite> findAll() {
        return table.scan().items().stream().toList();
    }

    public void delete(String id) {
        Key key = Key.builder().partitionValue(id).build();
        table.deleteItem(key);
    }
}
```

- [ ] **Step 9: Run all tests**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 10: Commit**

```bash
git add app/src/
git commit -m "feat: add repository layer with TableInitializer for local dev"
```

---

## Task 6: Spring Security Authentication

**Files:**
- Create: `app/src/main/java/com/londonsearch/config/SecurityConfig.java`
- Create: `app/src/main/resources/templates/login.html`
- Create: `app/src/test/java/com/londonsearch/controller/SecurityTest.java`

- [ ] **Step 1: Write failing security test**

```java
// app/src/test/java/com/londonsearch/controller/SecurityTest.java
package com.londonsearch.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginPageIsAccessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void loginWithCorrectPasswordSucceeds() throws Exception {
        mockMvc.perform(post("/login")
                        .param("password", "testpassword")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        mockMvc.perform(post("/login")
                        .param("password", "wrongpassword")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.londonsearch.controller.SecurityTest"`

Expected: FAIL — no SecurityConfig, default Spring Security behavior

- [ ] **Step 3: Create SecurityConfig**

```java
// app/src/main/java/com/londonsearch/config/SecurityConfig.java
package com.londonsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.password}")
    private String appPassword;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/actuator/health").permitAll()
                        .requestMatchers("/alert/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("password")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return authentication -> {
            String password = authentication.getCredentials().toString();
            if (appPassword.equals(password)) {
                return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "user", password,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                );
            }
            throw new BadCredentialsException("Invalid password");
        };
    }
}
```

- [ ] **Step 4: Create the login page**

```html
<!-- app/src/main/resources/templates/login.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LondonSearchAgent — Login</title>
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-slate-900 text-slate-100 min-h-screen flex items-center justify-center">
    <div class="w-full max-w-sm">
        <h1 class="text-2xl font-bold text-center mb-8">LondonSearchAgent</h1>

        <div th:if="${param.error}" class="bg-red-900/50 border border-red-500 text-red-200 px-4 py-3 rounded mb-4">
            Invalid password. Please try again.
        </div>

        <div th:if="${param.logout}" class="bg-green-900/50 border border-green-500 text-green-200 px-4 py-3 rounded mb-4">
            You have been logged out.
        </div>

        <form th:action="@{/login}" method="post" class="bg-slate-800 rounded-lg p-6 shadow-lg">
            <label for="password" class="block text-sm text-slate-400 mb-2">Password</label>
            <input type="password" id="password" name="password" required autofocus
                   class="w-full bg-slate-700 border border-slate-600 rounded px-3 py-2 text-slate-100 focus:outline-none focus:border-blue-500 mb-4"
                   placeholder="Enter password">
            <button type="submit"
                    class="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded transition-colors">
                Sign In
            </button>
        </form>
    </div>
</body>
</html>
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.londonsearch.controller.SecurityTest"`

Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/
git commit -m "feat: add shared-secret authentication with login page"
```

---

## Task 7: Tailwind CSS + Base Layout

**Files:**
- Create: `app/src/main/resources/static/css/input.css`
- Create: `app/src/main/resources/templates/layout.html`

- [ ] **Step 1: Create Tailwind input CSS**

```css
/* app/src/main/resources/static/css/input.css */
@import "tailwindcss";
```

- [ ] **Step 2: For Phase 1, use Tailwind CDN in the layout**

We will switch to the standalone Tailwind CLI build step in a later phase. For now, the CDN keeps the dev loop fast.

```html
<!-- app/src/main/resources/templates/layout.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:hx="http://www.w3.org/1999/xhtml"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title th:text="${pageTitle} ?: 'LondonSearchAgent'">LondonSearchAgent</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://unpkg.com/htmx.org@2.0.4"></script>
    <meta name="_csrf" th:content="${_csrf.token}"/>
    <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
    <script>
        document.addEventListener('htmx:configRequest', function(event) {
            var csrfToken = document.querySelector('meta[name="_csrf"]').content;
            var csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
            event.detail.headers[csrfHeader] = csrfToken;
        });
    </script>
</head>
<body class="bg-slate-900 text-slate-100 min-h-screen">
    <!-- Navigation -->
    <nav class="bg-slate-800 border-b border-slate-700 px-6 py-3">
        <div class="max-w-7xl mx-auto flex items-center justify-between">
            <a th:href="@{/}" class="text-lg font-bold text-slate-100 hover:text-blue-400 transition-colors">
                LondonSearchAgent
            </a>
            <div class="flex items-center gap-4">
                <a th:href="@{/}" class="text-sm text-slate-300 hover:text-white transition-colors">Feed</a>
                <a th:href="@{/config/search}" class="text-sm text-slate-300 hover:text-white transition-colors">Settings</a>
                <form th:action="@{/logout}" method="post" class="inline">
                    <button type="submit" class="text-sm text-slate-400 hover:text-red-400 transition-colors">Logout</button>
                </form>
            </div>
        </div>
    </nav>

    <!-- Main content -->
    <main class="max-w-7xl mx-auto px-6 py-6">
        <div th:replace="~{:: content}">
            <!-- Page content injected here -->
        </div>
    </main>
</body>
</html>
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/resources/
git commit -m "feat: add Tailwind CDN base layout with HTMX and CSRF support"
```

---

## Task 8: Seed Data Loader

**Files:**
- Create: `app/src/main/java/com/londonsearch/seed/DataSeeder.java`

- [ ] **Step 1: Create the seed data loader**

```java
// app/src/main/java/com/londonsearch/seed/DataSeeder.java
package com.londonsearch.seed;

import com.londonsearch.model.Listing;
import com.londonsearch.model.MonitoredSite;
import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import com.londonsearch.repository.ListingRepository;
import com.londonsearch.repository.MonitoredSiteRepository;
import com.londonsearch.repository.PropertyRepository;
import com.londonsearch.repository.SearchConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@Profile("local")
@Order(2)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PropertyRepository propertyRepo;
    private final ListingRepository listingRepo;
    private final SearchConfigRepository searchConfigRepo;
    private final MonitoredSiteRepository siteRepo;

    public DataSeeder(PropertyRepository propertyRepo, ListingRepository listingRepo,
                      SearchConfigRepository searchConfigRepo, MonitoredSiteRepository siteRepo) {
        this.propertyRepo = propertyRepo;
        this.listingRepo = listingRepo;
        this.searchConfigRepo = searchConfigRepo;
        this.siteRepo = siteRepo;
    }

    @Override
    public void run(String... args) {
        log.info("Seeding development data...");
        seedProperties();
        seedSearchConfigs();
        seedMonitoredSites();
        log.info("Seed data loaded.");
    }

    private void seedProperties() {
        Instant now = Instant.now();

        // Property 1: Marylebone — 3 sites
        String prop1Id = "seed-prop-001";
        Property p1 = new Property();
        p1.setId(prop1Id);
        p1.setAddress("42 Baker Street, London W1U 3BW");
        p1.setNormalizedAddress("42 baker street, london w1u 3bw");
        p1.setArea("Marylebone");
        p1.setBedrooms(3);
        p1.setBathrooms(2);
        p1.setPrice(7500);
        p1.setCurrency("GBP");
        p1.setPricePerMonth(7500);
        p1.setSqft(1200);
        p1.setPropertyType("Flat");
        p1.setFurnishing("Furnished");
        p1.setAvailableFrom("1 Jul 2026");
        p1.setDescription("A beautifully presented three bedroom apartment located on the third floor of this sought-after portered building. The property benefits from a spacious reception room with balcony, fully fitted kitchen, three well-proportioned bedrooms, two bathrooms (one en-suite), and ample storage throughout.");
        p1.setAiSummary("Bright corner flat on the 3rd floor with a south-facing balcony. Recently refurbished kitchen and bathrooms. The living area is generous for the price point. 5-minute walk to Baker Street tube. Potential concern: no dedicated parking, street permit only. Listed £200 below comparable Knight Frank properties in this building.");
        p1.setMatchScore(92);
        p1.setStatus("new");
        p1.setFirstSeenAt(now.minus(2, ChronoUnit.DAYS));
        p1.setLastUpdatedAt(now);
        propertyRepo.save(p1);

        Listing l1a = new Listing();
        l1a.setPropertyId(prop1Id);
        l1a.setSiteListingId("knightfrank#KF-12345");
        l1a.setSiteName("Knight Frank");
        l1a.setListingUrl("https://www.knightfrank.co.uk/properties/residential/to-let/baker-street/KF-12345");
        l1a.setOriginalPrice("£7,500 pcm");
        l1a.setOriginalAddress("42 Baker Street, Marylebone, London W1U 3BW");
        l1a.setAgentName("Knight Frank Marylebone");
        l1a.setAgentPhone("020 3944 8444");
        l1a.setAgentEmail("marylebone.lettings@knightfrank.com");
        l1a.setImageUrls(List.of());
        l1a.setScrapedAt(now.minus(2, ChronoUnit.DAYS));
        listingRepo.save(l1a);

        Listing l1b = new Listing();
        l1b.setPropertyId(prop1Id);
        l1b.setSiteListingId("rightmove#RM-67890");
        l1b.setSiteName("Rightmove");
        l1b.setListingUrl("https://www.rightmove.co.uk/properties/67890");
        l1b.setOriginalPrice("£7,500 pcm");
        l1b.setOriginalAddress("Baker Street, London W1U");
        l1b.setImageUrls(List.of());
        l1b.setScrapedAt(now.minus(3, ChronoUnit.DAYS));
        listingRepo.save(l1b);

        Listing l1c = new Listing();
        l1c.setPropertyId(prop1Id);
        l1c.setSiteListingId("savills#SAV-11111");
        l1c.setSiteName("Savills");
        l1c.setListingUrl("https://search.savills.com/property-detail/11111");
        l1c.setOriginalPrice("£7,495 pcm");
        l1c.setOriginalAddress("42 Baker St, London W1U 3BW");
        l1c.setImageUrls(List.of());
        l1c.setScrapedAt(now.minus(1, ChronoUnit.DAYS));
        listingRepo.save(l1c);

        // Property 2: Mayfair — 1 site
        String prop2Id = "seed-prop-002";
        Property p2 = new Property();
        p2.setId(prop2Id);
        p2.setAddress("15 Mount Street, London W1K 2RN");
        p2.setNormalizedAddress("15 mount street, london w1k 2rn");
        p2.setArea("Mayfair");
        p2.setBedrooms(2);
        p2.setBathrooms(2);
        p2.setPrice(8200);
        p2.setCurrency("GBP");
        p2.setPricePerMonth(8200);
        p2.setSqft(950);
        p2.setPropertyType("Flat");
        p2.setFurnishing("Furnished");
        p2.setAvailableFrom("15 Jul 2026");
        p2.setDescription("An elegant period conversion apartment in the heart of Mayfair, moments from Green Park station. Features include high ceilings, sash windows, and a recently updated kitchen.");
        p2.setAiSummary("Elegant period conversion near Green Park. Strong restaurant access — Mount Street is one of Mayfair's best dining streets. High ceilings and original features. The kitchen is compact for the price. No outdoor space.");
        p2.setMatchScore(78);
        p2.setStatus("new");
        p2.setFirstSeenAt(now.minus(1, ChronoUnit.DAYS));
        p2.setLastUpdatedAt(now);
        propertyRepo.save(p2);

        Listing l2a = new Listing();
        l2a.setPropertyId(prop2Id);
        l2a.setSiteListingId("wetherell#WE-22222");
        l2a.setSiteName("Wetherell");
        l2a.setListingUrl("https://www.wetherell.co.uk/properties/lettings/22222");
        l2a.setOriginalPrice("£8,200 pcm");
        l2a.setOriginalAddress("15 Mount Street, Mayfair, London W1K 2RN");
        l2a.setAgentName("Wetherell Mayfair");
        l2a.setAgentPhone("020 7529 5566");
        l2a.setImageUrls(List.of());
        l2a.setScrapedAt(now.minus(1, ChronoUnit.DAYS));
        listingRepo.save(l2a);

        // Property 3: South Kensington — 2 sites
        String prop3Id = "seed-prop-003";
        Property p3 = new Property();
        p3.setId(prop3Id);
        p3.setAddress("8 Onslow Gardens, London SW7 3AQ");
        p3.setNormalizedAddress("8 onslow gardens, london sw7 3aq");
        p3.setArea("South Kensington");
        p3.setBedrooms(3);
        p3.setBathrooms(2);
        p3.setPrice(6800);
        p3.setCurrency("GBP");
        p3.setPricePerMonth(6800);
        p3.setSqft(1450);
        p3.setPropertyType("Flat");
        p3.setFurnishing("Part-furnished");
        p3.setAvailableFrom("1 Aug 2026");
        p3.setDescription("A spacious garden flat in a quiet residential crescent, recently refurbished to a high standard. Private garden access, two minutes from South Kensington tube.");
        p3.setAiSummary("Spacious garden flat, recently refurbished. Private garden access is rare at this price point. Onslow Gardens is a quiet crescent — minimal noise. Two minutes to South Kensington tube. Part-furnished means you will need some furniture.");
        p3.setMatchScore(88);
        p3.setStatus("seen");
        p3.setFirstSeenAt(now.minus(5, ChronoUnit.DAYS));
        p3.setLastUpdatedAt(now.minus(2, ChronoUnit.DAYS));
        propertyRepo.save(p3);

        Listing l3a = new Listing();
        l3a.setPropertyId(prop3Id);
        l3a.setSiteListingId("chestertons#CH-33333");
        l3a.setSiteName("Chestertons");
        l3a.setListingUrl("https://www.chestertons.co.uk/property/33333");
        l3a.setOriginalPrice("£6,800 pcm");
        l3a.setOriginalAddress("Onslow Gardens, South Kensington, SW7");
        l3a.setImageUrls(List.of());
        l3a.setScrapedAt(now.minus(5, ChronoUnit.DAYS));
        listingRepo.save(l3a);

        Listing l3b = new Listing();
        l3b.setPropertyId(prop3Id);
        l3b.setSiteListingId("onthemarket#OTM-44444");
        l3b.setSiteName("OnTheMarket");
        l3b.setListingUrl("https://www.onthemarket.com/details/44444");
        l3b.setOriginalPrice("£6,800 pcm");
        l3b.setOriginalAddress("8 Onslow Gardens, London, SW7 3AQ");
        l3b.setImageUrls(List.of());
        l3b.setScrapedAt(now.minus(4, ChronoUnit.DAYS));
        listingRepo.save(l3b);

        // Properties 4-7: additional variety
        seedAdditionalProperty("seed-prop-004", "22 Harley Street, London W1G 9PL", "Marylebone",
                2, 1, 5900, 800, "Flat", "Unfurnished", "new",
                "Modern penthouse with roof terrace overlooking Regent's Park. Open-plan living, underfloor heating.",
                "Modern penthouse with roof terrace — outdoor space is a major plus. Regent's Park views. Only 1 bathroom for 2 beds may be tight. Underfloor heating throughout.",
                85, now.minus(6, ChronoUnit.HOURS));

        seedAdditionalProperty("seed-prop-005", "7 Thurloe Place, London SW7 2RX", "South Kensington",
                2, 2, 7200, 1050, "Flat", "Furnished", "new",
                "Charming mansion flat opposite the V&A museum. Dual aspect reception, porter service.",
                "Prime location directly opposite the V&A. Porter service adds security and convenience. Thurloe Place can get busy with museum traffic during daytime. Well-proportioned rooms.",
                90, now.minus(3, ChronoUnit.HOURS));

        seedAdditionalProperty("seed-prop-006", "3 Carlos Place, London W1K 3AP", "Mayfair",
                3, 3, 9000, 1800, "Flat", "Furnished", "saved",
                "Exceptional lateral apartment in a prestigious Mayfair address. Three en-suite bedrooms, large entertaining space.",
                "Top of budget but exceptional space. Three en-suites is rare. Carlos Place is one of Mayfair's best addresses — quiet, central, seconds from Mount Street restaurants. At £9,000 this is at your maximum.",
                75, now.minus(3, ChronoUnit.DAYS));

        seedAdditionalProperty("seed-prop-007", "11 Montagu Square, London W1H 2LB", "Marylebone",
                3, 2, 7800, 1350, "Flat", "Furnished", "new",
                "Elegant garden square apartment with access to private communal gardens. Period features throughout.",
                "Beautiful garden square location — private gardens are a significant perk. Period features (cornicing, fireplaces) throughout. Montagu Square is residential and quiet. Good value for 3-bed Marylebone.",
                87, now.minus(1, ChronoUnit.HOURS));

        log.info("Seeded 7 properties with listings.");
    }

    private void seedAdditionalProperty(String id, String address, String area,
                                         int beds, int baths, int price, int sqft,
                                         String type, String furnishing, String status,
                                         String description, String aiSummary,
                                         int matchScore, Instant firstSeen) {
        Property p = new Property();
        p.setId(id);
        p.setAddress(address);
        p.setNormalizedAddress(address.toLowerCase());
        p.setArea(area);
        p.setBedrooms(beds);
        p.setBathrooms(baths);
        p.setPrice(price);
        p.setCurrency("GBP");
        p.setPricePerMonth(price);
        p.setSqft(sqft);
        p.setPropertyType(type);
        p.setFurnishing(furnishing);
        p.setStatus(status);
        p.setDescription(description);
        p.setAiSummary(aiSummary);
        p.setMatchScore(matchScore);
        p.setFirstSeenAt(firstSeen);
        p.setLastUpdatedAt(Instant.now());
        propertyRepo.save(p);

        Listing l = new Listing();
        l.setPropertyId(id);
        l.setSiteListingId("rightmove#RM-" + UUID.randomUUID().toString().substring(0, 8));
        l.setSiteName("Rightmove");
        l.setListingUrl("https://www.rightmove.co.uk/properties/" + id);
        l.setOriginalPrice("£" + String.format("%,d", price) + " pcm");
        l.setOriginalAddress(address);
        l.setImageUrls(List.of());
        l.setScrapedAt(firstSeen);
        listingRepo.save(l);
    }

    private void seedSearchConfigs() {
        SearchConfig primary = new SearchConfig();
        primary.setId("seed-config-001");
        primary.setName("Primary London Search");
        primary.setAreas(List.of("Mayfair", "Marylebone", "South Kensington"));
        primary.setMinBeds(2);
        primary.setMaxBeds(3);
        primary.setMinPrice(5000);
        primary.setMaxPrice(9000);
        primary.setMinBaths(1);
        primary.setFurnishing(List.of("Furnished", "Part-furnished"));
        primary.setAdditionalCriteria("Must have outdoor space or balcony. Prefer period conversions over new builds. Close to tube station. Good natural light. Avoid basement flats. Walkable to a variety of restaurants.");
        primary.setEnabled(true);
        primary.setCreatedAt(Instant.now());
        searchConfigRepo.save(primary);

        SearchConfig backup = new SearchConfig();
        backup.setId("seed-config-002");
        backup.setName("Backup — Chelsea");
        backup.setAreas(List.of("Chelsea"));
        backup.setMinBeds(2);
        backup.setMaxBeds(3);
        backup.setMinPrice(6000);
        backup.setMaxPrice(8000);
        backup.setMinBaths(1);
        backup.setFurnishing(List.of("Furnished"));
        backup.setAdditionalCriteria("");
        backup.setEnabled(false);
        backup.setCreatedAt(Instant.now());
        searchConfigRepo.save(backup);

        log.info("Seeded 2 search configs.");
    }

    private void seedMonitoredSites() {
        seedSite("Rightmove", "https://www.rightmove.co.uk", "static", "Aggregator", true);
        seedSite("OnTheMarket", "https://www.onthemarket.com", "static", "Aggregator", true);
        seedSite("Zoopla", "https://www.zoopla.co.uk", "static", "Aggregator", true);
        seedSite("Knight Frank", "https://www.knightfrank.co.uk", "authenticated", "Tier 1", true);
        seedSite("Savills", "https://search.savills.com", "js-rendered", "Tier 1", true);
        seedSite("Foxtons", "https://www.foxtons.co.uk", "js-rendered", "Tier 1", true);
        seedSite("Chestertons", "https://www.chestertons.co.uk", "static", "Tier 1", true);
        seedSite("Strutt & Parker", "https://www.struttandparker.com", "static", "Tier 1", true);
        seedSite("JLL Residential", "https://residential.jll.co.uk", "js-rendered", "Tier 1", true);
        seedSite("Marsh & Parsons", "https://www.marshandparsons.co.uk", "static", "Tier 1", true);
        seedSite("Hamptons", "https://www.hamptons.co.uk", "static", "Tier 1", true);
        seedSite("Winkworth", "https://www.winkworth.co.uk", "static", "Tier 1", true);
        seedSite("Dexters", "https://www.dexters.co.uk", "js-rendered", "Tier 1", true);
        seedSite("Benham & Reeves", "https://www.benhams.com", "static", "Tier 1", true);
        seedSite("Wetherell", "https://www.wetherell.co.uk", "static", "Tier 2", true);
        seedSite("Knightsbridge Prime Property", "https://knightsbridgeprimeproperty.com", "static", "Tier 2", true);
        seedSite("Quintessentially Estates", "https://quintessentiallyestates.com", "static", "Tier 2", true);
        seedSite("Hudsons Property", "https://www.hudsonsproperty.com", "static", "Tier 2", true);
        seedSite("Carter Jonas", "https://www.carterjonas.co.uk", "static", "Tier 2", true);

        log.info("Seeded 19 monitored sites.");
    }

    private void seedSite(String name, String baseUrl, String scraperType, String tier, boolean enabled) {
        MonitoredSite site = new MonitoredSite();
        site.setId(UUID.randomUUID().toString());
        site.setName(name);
        site.setBaseUrl(baseUrl);
        site.setScraperType(scraperType);
        site.setTier(tier);
        site.setEnabled(enabled);
        siteRepo.save(site);
    }
}
```

- [ ] **Step 2: Start DynamoDB Local and run the app**

Run:
```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

Expected: App starts on port 8080, logs show "Seeded 7 properties", "Seeded 2 search configs", "Seeded 19 monitored sites"

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/londonsearch/seed/
git commit -m "feat: add development seed data with 7 properties, 19 sites, 2 search configs"
```

---

## Task 9: Unified Feed Page

**Files:**
- Create: `app/src/main/java/com/londonsearch/controller/FeedController.java`
- Create: `app/src/main/resources/templates/feed.html`
- Create: `app/src/main/resources/templates/fragments/property-card.html`
- Create: `app/src/main/resources/templates/fragments/filter-pills.html`
- Create: `app/src/test/java/com/londonsearch/controller/FeedControllerTest.java`

- [ ] **Step 1: Write failing test for FeedController**

```java
// app/src/test/java/com/londonsearch/controller/FeedControllerTest.java
package com.londonsearch.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void feedPageLoads() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed"))
                .andExpect(model().attributeExists("properties"))
                .andExpect(model().attributeExists("areas"))
                .andExpect(model().attributeExists("selectedArea"))
                .andExpect(model().attributeExists("selectedFilter"));
    }

    @Test
    @WithMockUser
    void feedFiltersByArea() throws Exception {
        mockMvc.perform(get("/").param("area", "Mayfair"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedArea", "Mayfair"));
    }

    @Test
    @WithMockUser
    void feedFiltersByStatus() throws Exception {
        mockMvc.perform(get("/").param("filter", "new"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedFilter", "new"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.londonsearch.controller.FeedControllerTest"`

Expected: FAIL — FeedController does not exist

- [ ] **Step 3: Create FeedController**

```java
// app/src/main/java/com/londonsearch/controller/FeedController.java
package com.londonsearch.controller;

import com.londonsearch.model.Listing;
import com.londonsearch.model.Property;
import com.londonsearch.repository.ListingRepository;
import com.londonsearch.repository.PropertyRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class FeedController {

    private static final List<String> AREAS = List.of("Mayfair", "Marylebone", "South Kensington");

    private final PropertyRepository propertyRepo;
    private final ListingRepository listingRepo;

    public FeedController(PropertyRepository propertyRepo, ListingRepository listingRepo) {
        this.propertyRepo = propertyRepo;
        this.listingRepo = listingRepo;
    }

    @GetMapping("/")
    public String feed(
            @RequestParam(required = false) String area,
            @RequestParam(required = false, defaultValue = "all") String filter,
            @RequestParam(required = false, defaultValue = "date") String sort,
            Model model) {

        List<Property> properties;

        if (area != null && !area.isBlank()) {
            properties = propertyRepo.findByArea(area);
        } else if ("new".equals(filter)) {
            properties = propertyRepo.findByStatus("new");
        } else if ("saved".equals(filter)) {
            properties = propertyRepo.findByStatus("saved");
        } else {
            properties = propertyRepo.findAll();
        }

        if ("score".equals(sort)) {
            properties = properties.stream()
                    .sorted(Comparator.comparing(
                            (Property p) -> p.getMatchScore() != null ? p.getMatchScore() : 0)
                            .reversed())
                    .toList();
        } else {
            properties = properties.stream()
                    .sorted(Comparator.comparing(
                            (Property p) -> p.getFirstSeenAt() != null ? p.getFirstSeenAt() : java.time.Instant.EPOCH)
                            .reversed())
                    .toList();
        }

        // Count listings per property for the "X sites" badge
        Map<String, Long> listingCounts = properties.stream()
                .collect(Collectors.toMap(
                        Property::getId,
                        p -> (long) listingRepo.findByPropertyId(p.getId()).size()
                ));

        // Count properties per area for the filter pills
        Map<String, Long> areaCounts = propertyRepo.findAll().stream()
                .filter(p -> p.getArea() != null)
                .collect(Collectors.groupingBy(Property::getArea, Collectors.counting()));

        long newCount = propertyRepo.findByStatus("new").size();
        long savedCount = propertyRepo.findByStatus("saved").size();

        model.addAttribute("properties", properties);
        model.addAttribute("listingCounts", listingCounts);
        model.addAttribute("areas", AREAS);
        model.addAttribute("areaCounts", areaCounts);
        model.addAttribute("newCount", newCount);
        model.addAttribute("savedCount", savedCount);
        model.addAttribute("totalCount", propertyRepo.findAll().size());
        model.addAttribute("selectedArea", area);
        model.addAttribute("selectedFilter", filter);
        model.addAttribute("selectedSort", sort);

        return "feed";
    }
}
```

- [ ] **Step 4: Create the filter pills fragment**

```html
<!-- app/src/main/resources/templates/fragments/filter-pills.html -->
<div th:fragment="filterPills" class="flex gap-2 flex-wrap mb-6">
    <a th:href="@{/}"
       th:classappend="${selectedArea == null and selectedFilter == 'all'} ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-400 hover:text-slate-200'"
       class="px-3 py-1 rounded-full text-sm transition-colors">
        All (<span th:text="${totalCount}">0</span>)
    </a>
    <a th:each="areaName : ${areas}"
       th:href="@{/(area=${areaName})}"
       th:classappend="${selectedArea == areaName} ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-400 hover:text-slate-200'"
       class="px-3 py-1 rounded-full text-sm transition-colors">
        <span th:text="${areaName}">Area</span>
        (<span th:text="${areaCounts[areaName]} ?: 0">0</span>)
    </a>
    <div class="flex-1"></div>
    <a th:href="@{/(filter=new)}"
       th:classappend="${selectedFilter == 'new'} ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-400 hover:text-slate-200'"
       class="px-3 py-1 rounded-full text-sm transition-colors">
        New Only (<span th:text="${newCount}">0</span>)
    </a>
    <a th:href="@{/(filter=saved)}"
       th:classappend="${selectedFilter == 'saved'} ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-400 hover:text-slate-200'"
       class="px-3 py-1 rounded-full text-sm transition-colors">
        Saved (<span th:text="${savedCount}">0</span>)
    </a>
</div>
```

- [ ] **Step 5: Create the property card fragment**

```html
<!-- app/src/main/resources/templates/fragments/property-card.html -->
<a th:fragment="propertyCard(property, siteCount)"
   th:href="@{/property/{id}(id=${property.id})}"
   class="bg-slate-800 rounded-lg overflow-hidden hover:ring-1 hover:ring-blue-500 transition-all block">
    <!-- Image placeholder -->
    <div class="bg-gradient-to-br from-slate-700 to-slate-600 h-32 flex items-center justify-center relative">
        <span class="text-slate-500 text-sm">Property Photo</span>
        <span th:if="${property.status == 'new'}"
              class="absolute top-2 left-2 bg-green-500 text-white px-2 py-0.5 rounded text-xs font-medium">
            NEW
        </span>
        <span th:if="${siteCount != null and siteCount > 0}"
              class="absolute top-2 right-2 bg-black/60 text-white px-2 py-0.5 rounded text-xs">
            <span th:text="${siteCount}">1</span> site<span th:if="${siteCount > 1}">s</span>
        </span>
    </div>
    <!-- Card body -->
    <div class="p-3">
        <div class="font-semibold text-sm text-slate-100" th:text="${property.address}">Address</div>
        <div class="text-slate-400 text-xs mt-0.5" th:text="${property.area}">Area</div>
        <div class="flex gap-3 mt-2 text-slate-300 text-xs">
            <span th:text="${property.bedrooms} + ' bed'">3 bed</span>
            <span th:text="${property.bathrooms} + ' bath'">2 bath</span>
            <span th:if="${property.sqft != null}" th:text="${#numbers.formatInteger(property.sqft, 1, 'COMMA')} + ' sqft'">1,200 sqft</span>
        </div>
        <div class="flex justify-between items-center mt-2">
            <span class="text-blue-400 font-semibold text-sm"
                  th:text="'£' + ${#numbers.formatInteger(property.pricePerMonth, 1, 'COMMA')} + ' pcm'">£7,500 pcm</span>
            <span th:if="${property.matchScore != null}"
                  th:classappend="${property.matchScore >= 80} ? 'text-green-400' : 'text-yellow-400'"
                  class="text-xs font-semibold"
                  th:text="${property.matchScore} + '% match'">92% match</span>
        </div>
        <div th:if="${property.aiSummary != null}"
             class="text-slate-500 text-xs mt-2 italic line-clamp-2"
             th:text="${property.aiSummary}">
            AI summary snippet...
        </div>
    </div>
</a>
```

- [ ] **Step 6: Create the feed page**

```html
<!-- app/src/main/resources/templates/feed.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:hx="http://www.w3.org/1999/xhtml"
      th:replace="~{layout :: html(~{:: content})}">
<body>
<div th:fragment="content">
    <div class="flex justify-between items-center mb-4">
        <h1 class="text-xl font-bold">
            <span th:text="${#lists.size(properties)}">0</span> properties
        </h1>
        <div class="flex gap-2">
            <a th:href="@{/(sort=date, area=${selectedArea}, filter=${selectedFilter})}"
               th:classappend="${selectedSort == 'date'} ? 'text-blue-400' : 'text-slate-400'"
               class="text-sm hover:text-blue-300 transition-colors">
                Newest
            </a>
            <span class="text-slate-600">|</span>
            <a th:href="@{/(sort=score, area=${selectedArea}, filter=${selectedFilter})}"
               th:classappend="${selectedSort == 'score'} ? 'text-blue-400' : 'text-slate-400'"
               class="text-sm hover:text-blue-300 transition-colors">
                Best Match
            </a>
        </div>
    </div>

    <div th:replace="~{fragments/filter-pills :: filterPills}"></div>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div th:each="property : ${properties}"
             th:replace="~{fragments/property-card :: propertyCard(${property}, ${listingCounts[property.id]})}">
        </div>
    </div>

    <div th:if="${#lists.isEmpty(properties)}" class="text-center py-16 text-slate-500">
        No properties match your current filters.
    </div>
</div>
</body>
</html>
```

- [ ] **Step 7: Fix the layout template to accept content properly**

Update `layout.html` — replace the `<div th:replace="~{:: content}">` line:

```html
<!-- In layout.html, update the main section to: -->
<main class="max-w-7xl mx-auto px-6 py-6" th:insert="~{:: content}">
</main>
```

The full `layout.html` remains as defined in Task 7 with this one change to the `<main>` tag.

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.londonsearch.controller.FeedControllerTest"`

Expected: All PASS

- [ ] **Step 9: Start the app and verify in browser**

Run:
```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

Open `http://localhost:8080`, log in with password `changeme`. You should see the card grid with 7 seed properties, filter pills, and sort options.

- [ ] **Step 10: Commit**

```bash
git add app/src/
git commit -m "feat: add unified property feed with card grid, filters, and sorting"
```

---

## Task 10: Property Detail Page

**Files:**
- Create: `app/src/main/java/com/londonsearch/controller/PropertyController.java`
- Create: `app/src/main/resources/templates/property-detail.html`

- [ ] **Step 1: Create PropertyController**

```java
// app/src/main/java/com/londonsearch/controller/PropertyController.java
package com.londonsearch.controller;

import com.londonsearch.model.Listing;
import com.londonsearch.model.Property;
import com.londonsearch.repository.ListingRepository;
import com.londonsearch.repository.PropertyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class PropertyController {

    private final PropertyRepository propertyRepo;
    private final ListingRepository listingRepo;

    public PropertyController(PropertyRepository propertyRepo, ListingRepository listingRepo) {
        this.propertyRepo = propertyRepo;
        this.listingRepo = listingRepo;
    }

    @GetMapping("/property/{id}")
    public String propertyDetail(@PathVariable String id, Model model) {
        Property property = propertyRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));

        List<Listing> listings = listingRepo.findByPropertyId(id);

        // Mark as seen if currently new
        if ("new".equals(property.getStatus())) {
            property.setStatus("seen");
            property.setLastUpdatedAt(java.time.Instant.now());
            propertyRepo.save(property);
        }

        model.addAttribute("property", property);
        model.addAttribute("listings", listings);
        model.addAttribute("listingCount", listings.size());

        return "property-detail";
    }

    @PostMapping("/property/{id}/status")
    public String updateStatus(@PathVariable String id, @RequestParam String status, RedirectAttributes redirect) {
        Property property = propertyRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));

        property.setStatus(status);
        property.setLastUpdatedAt(java.time.Instant.now());
        propertyRepo.save(property);

        return "redirect:/property/" + id;
    }
}
```

- [ ] **Step 2: Create the property detail template**

```html
<!-- app/src/main/resources/templates/property-detail.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout :: html(~{:: content})}">
<body>
<div th:fragment="content">
    <!-- Top bar -->
    <div class="flex justify-between items-center mb-4">
        <a th:href="@{/}" class="text-slate-400 text-sm hover:text-slate-200 transition-colors">&larr; Back to feed</a>
        <div class="flex gap-2">
            <form th:action="@{/property/{id}/status(id=${property.id})}" method="post" class="inline">
                <input type="hidden" name="status" value="dismissed"/>
                <button type="submit"
                        th:classappend="${property.status == 'dismissed'} ? 'bg-slate-600' : ''"
                        class="bg-slate-700 border border-slate-600 px-4 py-1.5 rounded text-sm text-slate-300 hover:text-white transition-colors">
                    Dismiss
                </button>
            </form>
            <form th:action="@{/property/{id}/status(id=${property.id})}" method="post" class="inline">
                <input type="hidden" name="status" value="saved"/>
                <button type="submit"
                        th:classappend="${property.status == 'saved'} ? 'bg-blue-700' : ''"
                        class="bg-blue-600 px-4 py-1.5 rounded text-sm text-white hover:bg-blue-700 transition-colors">
                    Save
                </button>
            </form>
        </div>
    </div>

    <!-- Address header -->
    <h1 class="text-xl font-bold mb-1" th:text="${property.address}">Address</h1>
    <div class="text-slate-400 text-sm mb-6" th:text="${property.area}">Area</div>

    <!-- Photo grid placeholder -->
    <div class="grid grid-cols-3 gap-2 mb-6">
        <div class="col-span-2 bg-gradient-to-br from-slate-700 to-slate-600 h-48 rounded-lg flex items-center justify-center">
            <span class="text-slate-500 text-sm">Main Photo (best quality across sources)</span>
        </div>
        <div class="grid grid-rows-2 gap-2">
            <div class="bg-gradient-to-br from-slate-700 to-slate-600 rounded-lg flex items-center justify-center">
                <span class="text-slate-500 text-xs">Photo 2</span>
            </div>
            <div class="bg-gradient-to-br from-slate-700 to-slate-600 rounded-lg flex items-center justify-center">
                <span class="text-slate-500 text-xs">Photo 3</span>
            </div>
        </div>
    </div>

    <!-- Stats bar -->
    <div class="flex gap-6 p-4 bg-slate-800 rounded-lg mb-6 flex-wrap">
        <div>
            <div class="text-slate-500 text-xs uppercase">Price</div>
            <div class="text-blue-400 font-bold text-lg"
                 th:text="'£' + ${#numbers.formatInteger(property.pricePerMonth, 1, 'COMMA')} + ' pcm'">£7,500 pcm</div>
        </div>
        <div>
            <div class="text-slate-500 text-xs uppercase">Bedrooms</div>
            <div class="text-slate-100 font-semibold text-lg" th:text="${property.bedrooms}">3</div>
        </div>
        <div>
            <div class="text-slate-500 text-xs uppercase">Bathrooms</div>
            <div class="text-slate-100 font-semibold text-lg" th:text="${property.bathrooms}">2</div>
        </div>
        <div th:if="${property.sqft != null}">
            <div class="text-slate-500 text-xs uppercase">Size</div>
            <div class="text-slate-100 font-semibold text-lg"
                 th:text="${#numbers.formatInteger(property.sqft, 1, 'COMMA')} + ' sqft'">1,200 sqft</div>
        </div>
        <div th:if="${property.availableFrom != null}">
            <div class="text-slate-500 text-xs uppercase">Available</div>
            <div class="text-slate-100 font-semibold text-lg" th:text="${property.availableFrom}">1 Jul 2026</div>
        </div>
        <div th:if="${property.furnishing != null}">
            <div class="text-slate-500 text-xs uppercase">Furnishing</div>
            <div class="text-slate-100 font-semibold text-lg" th:text="${property.furnishing}">Furnished</div>
        </div>
        <div th:if="${property.matchScore != null}">
            <div class="text-slate-500 text-xs uppercase">Match</div>
            <div th:classappend="${property.matchScore >= 80} ? 'text-green-400' : 'text-yellow-400'"
                 class="font-bold text-lg"
                 th:text="${property.matchScore} + '%'">92%</div>
        </div>
    </div>

    <!-- Two column layout -->
    <div class="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <!-- Left column (3/5) -->
        <div class="lg:col-span-3">
            <!-- AI Summary -->
            <div th:if="${property.aiSummary != null}" class="mb-6">
                <div class="text-slate-400 text-xs uppercase mb-2 flex items-center gap-1.5">
                    <span class="bg-purple-500 w-2 h-2 rounded-full inline-block"></span>
                    AI Summary
                </div>
                <div class="text-slate-300 text-sm leading-relaxed bg-slate-800 p-3 rounded-lg border-l-2 border-purple-500"
                     th:text="${property.aiSummary}">
                    AI summary text...
                </div>
            </div>

            <!-- Description -->
            <div th:if="${property.description != null}" class="mb-6">
                <div class="text-slate-400 text-xs uppercase mb-2">Description</div>
                <div class="text-slate-300 text-sm leading-relaxed" th:text="${property.description}">
                    Property description...
                </div>
            </div>
        </div>

        <!-- Right column (2/5) -->
        <div class="lg:col-span-2">
            <!-- Source listings -->
            <div class="mb-6">
                <div class="text-slate-400 text-xs uppercase mb-2">
                    Found on <span th:text="${listingCount}">1</span> site<span th:if="${listingCount > 1}">s</span>
                </div>
                <div class="flex flex-col gap-2">
                    <div th:each="listing : ${listings}"
                         class="bg-slate-800 p-3 rounded-lg flex justify-between items-center">
                        <div>
                            <div class="text-slate-100 text-sm font-semibold" th:text="${listing.siteName}">Knight Frank</div>
                            <div class="text-slate-500 text-xs mt-0.5">
                                <span th:text="${listing.originalPrice}">£7,500 pcm</span>
                            </div>
                            <div th:if="${listing.agentName != null}" class="text-slate-500 text-xs mt-0.5" th:text="${listing.agentName}">Agent name</div>
                        </div>
                        <a th:href="${listing.listingUrl}" target="_blank" rel="noopener"
                           class="text-blue-400 text-xs hover:text-blue-300 transition-colors">
                            View &rarr;
                        </a>
                    </div>
                </div>
            </div>

            <!-- Agent contact (from first listing with contact info) -->
            <div th:with="contactListing=${listings.?[agentPhone != null][0]}"
                 th:if="${contactListing != null}" class="mb-6">
                <div class="text-slate-400 text-xs uppercase mb-2">Agent Contact</div>
                <div class="bg-slate-800 p-3 rounded-lg">
                    <div class="text-slate-100 text-sm font-semibold" th:text="${contactListing.agentName}">Agent</div>
                    <div th:if="${contactListing.agentPhone != null}"
                         class="text-slate-400 text-xs mt-1" th:text="${contactListing.agentPhone}">Phone</div>
                    <div th:if="${contactListing.agentEmail != null}"
                         class="text-blue-400 text-xs mt-0.5" th:text="${contactListing.agentEmail}">Email</div>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 3: Start the app and verify in browser**

Run:
```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

Navigate to `http://localhost:8080`, log in, click a property card. Verify the detail view shows stats bar, AI summary, description, source listings with "View →" links, and agent contact info.

- [ ] **Step 4: Commit**

```bash
git add app/src/
git commit -m "feat: add property detail page with aggregate view and source listings"
```

---

## Task 11: Configuration Pages

**Files:**
- Create: `app/src/main/java/com/londonsearch/controller/ConfigController.java`
- Create: `app/src/main/resources/templates/config/search.html`
- Create: `app/src/main/resources/templates/config/sites.html`

- [ ] **Step 1: Create ConfigController**

```java
// app/src/main/java/com/londonsearch/controller/ConfigController.java
package com.londonsearch.controller;

import com.londonsearch.model.MonitoredSite;
import com.londonsearch.model.SearchConfig;
import com.londonsearch.repository.MonitoredSiteRepository;
import com.londonsearch.repository.SearchConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/config")
public class ConfigController {

    private final SearchConfigRepository searchConfigRepo;
    private final MonitoredSiteRepository siteRepo;

    public ConfigController(SearchConfigRepository searchConfigRepo, MonitoredSiteRepository siteRepo) {
        this.searchConfigRepo = searchConfigRepo;
        this.siteRepo = siteRepo;
    }

    @GetMapping("/search")
    public String searchConfigs(
            @RequestParam(required = false) String edit,
            Model model) {
        List<SearchConfig> configs = searchConfigRepo.findAll();
        model.addAttribute("configs", configs);
        model.addAttribute("editId", edit);

        if (edit != null) {
            SearchConfig editing = searchConfigRepo.findById(edit)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            model.addAttribute("editing", editing);
        }

        return "config/search";
    }

    @PostMapping("/search/save")
    public String saveSearchConfig(
            @RequestParam(required = false) String id,
            @RequestParam String name,
            @RequestParam String areas,
            @RequestParam Integer minBeds,
            @RequestParam Integer maxBeds,
            @RequestParam Integer minPrice,
            @RequestParam Integer maxPrice,
            @RequestParam Integer minBaths,
            @RequestParam(required = false) List<String> furnishing,
            @RequestParam(required = false) String additionalCriteria) {

        SearchConfig config;
        if (id != null && !id.isBlank()) {
            config = searchConfigRepo.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } else {
            config = new SearchConfig();
            config.setId(UUID.randomUUID().toString());
            config.setEnabled(true);
            config.setCreatedAt(Instant.now());
        }

        config.setName(name);
        config.setAreas(Arrays.stream(areas.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setMinBeds(minBeds);
        config.setMaxBeds(maxBeds);
        config.setMinPrice(minPrice);
        config.setMaxPrice(maxPrice);
        config.setMinBaths(minBaths);
        config.setFurnishing(furnishing != null ? furnishing : List.of());
        config.setAdditionalCriteria(additionalCriteria != null ? additionalCriteria : "");

        searchConfigRepo.save(config);

        return "redirect:/config/search";
    }

    @PostMapping("/search/{id}/toggle")
    public String toggleSearchConfig(@PathVariable String id) {
        SearchConfig config = searchConfigRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        config.setEnabled(!Boolean.TRUE.equals(config.getEnabled()));
        searchConfigRepo.save(config);
        return "redirect:/config/search";
    }

    @PostMapping("/search/{id}/delete")
    public String deleteSearchConfig(@PathVariable String id) {
        searchConfigRepo.delete(id);
        return "redirect:/config/search";
    }

    @GetMapping("/sites")
    public String monitoredSites(Model model) {
        List<MonitoredSite> sites = siteRepo.findAll();
        model.addAttribute("sites", sites);
        return "config/sites";
    }

    @PostMapping("/sites/{id}/toggle")
    public String toggleSite(@PathVariable String id) {
        MonitoredSite site = siteRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        site.setEnabled(!Boolean.TRUE.equals(site.getEnabled()));
        siteRepo.save(site);
        return "redirect:/config/sites";
    }

    @PostMapping("/sites/add")
    public String addSite(
            @RequestParam String name,
            @RequestParam String baseUrl,
            @RequestParam String scraperType) {
        MonitoredSite site = new MonitoredSite();
        site.setId(UUID.randomUUID().toString());
        site.setName(name);
        site.setBaseUrl(baseUrl);
        site.setScraperType(scraperType);
        site.setEnabled(true);
        site.setTier("Custom");
        siteRepo.save(site);
        return "redirect:/config/sites";
    }

    @PostMapping("/sites/{id}/delete")
    public String deleteSite(@PathVariable String id) {
        siteRepo.delete(id);
        return "redirect:/config/sites";
    }
}
```

- [ ] **Step 2: Create the search config page**

```html
<!-- app/src/main/resources/templates/config/search.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout :: html(~{:: content})}">
<body>
<div th:fragment="content">
    <h1 class="text-xl font-bold mb-4">Settings</h1>

    <!-- Tab nav -->
    <div class="flex gap-0 mb-6 border-b border-slate-700">
        <a th:href="@{/config/search}" class="px-5 py-2.5 text-sm text-blue-400 border-b-2 border-blue-400 font-semibold">Search Criteria</a>
        <a th:href="@{/config/sites}" class="px-5 py-2.5 text-sm text-slate-400 hover:text-slate-200 transition-colors">Monitored Sites</a>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Left: saved searches -->
        <div>
            <div class="flex justify-between items-center mb-3">
                <div class="text-slate-400 text-xs uppercase">Saved Searches</div>
                <a th:href="@{/config/search(edit=new)}" class="bg-blue-600 text-white px-3 py-1 rounded text-xs hover:bg-blue-700 transition-colors">+ New Search</a>
            </div>

            <div th:each="config : ${configs}" class="mb-2">
                <div th:classappend="${config.id == editId} ? 'border-blue-500' : 'border-slate-700'"
                     class="bg-slate-800 border rounded-lg p-3.5">
                    <div class="flex justify-between items-center mb-2">
                        <div class="text-slate-100 font-semibold text-sm" th:text="${config.name}">Search Name</div>
                        <div class="flex gap-2 items-center">
                            <form th:action="@{/config/search/{id}/toggle(id=${config.id})}" method="post" class="inline">
                                <button type="submit"
                                        th:classappend="${config.enabled} ? 'bg-green-500 text-white' : 'bg-slate-600 text-slate-400'"
                                        class="px-2 py-0.5 rounded text-xs">
                                    <span th:text="${config.enabled} ? 'Active' : 'Paused'">Active</span>
                                </button>
                            </form>
                            <a th:href="@{/config/search(edit=${config.id})}"
                               class="text-slate-400 hover:text-blue-400 text-xs transition-colors">Edit</a>
                        </div>
                    </div>
                    <div class="flex flex-wrap gap-1.5 mb-2">
                        <span th:each="area : ${config.areas}"
                              class="bg-slate-700 px-2 py-0.5 rounded text-xs text-slate-300"
                              th:text="${area}">Area</span>
                    </div>
                    <div class="text-slate-400 text-xs">
                        <span th:text="${config.minBeds}">2</span>-<span th:text="${config.maxBeds}">3</span> beds
                        &middot; £<span th:text="${#numbers.formatInteger(config.minPrice, 1, 'COMMA')}">5,000</span>-£<span th:text="${#numbers.formatInteger(config.maxPrice, 1, 'COMMA')}">9,000</span> pcm
                        &middot; <span th:text="${config.minBaths}">1</span>+ bath
                    </div>
                    <div th:if="${config.additionalCriteria != null and !config.additionalCriteria.isEmpty()}"
                         class="text-slate-500 text-xs mt-1.5 italic"
                         th:text="'AI: &quot;' + ${config.additionalCriteria} + '&quot;'">
                    </div>
                </div>
            </div>
        </div>

        <!-- Right: edit form -->
        <div th:if="${editId != null}">
            <div class="text-slate-400 text-xs uppercase mb-3"
                 th:text="${editId == 'new'} ? 'New Search' : 'Edit: ' + ${editing?.name}">Edit</div>

            <form th:action="@{/config/search/save}" method="post" class="flex flex-col gap-3.5">
                <input th:if="${editId != 'new'}" type="hidden" name="id" th:value="${editing.id}"/>

                <div>
                    <label class="text-slate-400 text-xs block mb-1">Search Name</label>
                    <input type="text" name="name" required
                           th:value="${editing?.name}"
                           class="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-slate-100 text-sm focus:outline-none focus:border-blue-500">
                </div>

                <div>
                    <label class="text-slate-400 text-xs block mb-1">Areas (comma-separated)</label>
                    <input type="text" name="areas" required
                           th:value="${editing != null} ? ${#strings.listJoin(editing.areas, ', ')} : ''"
                           placeholder="Mayfair, Marylebone, South Kensington"
                           class="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-slate-100 text-sm focus:outline-none focus:border-blue-500">
                </div>

                <div class="grid grid-cols-2 gap-3">
                    <div>
                        <label class="text-slate-400 text-xs block mb-1">Min Bedrooms</label>
                        <input type="number" name="minBeds" required
                               th:value="${editing?.minBeds} ?: 2"
                               class="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-slate-100 text-sm focus:outline-none focus:border-blue-500">
                    </div>
                    <div>
                        <label class="text-slate-400 text-xs block mb-1">Max Bedrooms</label>
                        <input type="number" name="maxBeds" required
                               th:value="${editing?.maxBeds} ?: 3"
                               class="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-slate-100 text-sm focus:outline-none focus:border-blue-500">
                    </div>
                </div>

                <div class="grid grid-cols-2 gap-3">
                    <div>
                        <label class="text-slate-400 text-xs block mb-1">Min Price (pcm)</label>
                        <input type="number" name="minPrice" required
                               th:value="${editing?.minPrice} ?: 5000"
                               class="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-slate-100 text-sm focus:outline-none focus:border-blue-500">
                    </div>
                    <div>
                        <label class="text-slate-400 text-xs block mb-1">Max Price (pcm)</label>
                        <input type="number" name="maxPrice" required
                               th:value="${editing?.maxPrice} ?: 9000"
                               class="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-slate-100 text-sm focus:outline-none focus:border-blue-500">
                    </div>
                </div>

                <div>
                    <label class="text-slate-400 text-xs block mb-1">Min Bathrooms</label>
                    <input type="number" name="minBaths" required
                           th:value="${editing?.minBaths} ?: 1"
                           class="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-slate-100 text-sm focus:outline-none focus:border-blue-500">
                </div>

                <div>
                    <label class="text-slate-400 text-xs block mb-1">Furnishing</label>
                    <div class="flex gap-3">
                        <label class="flex items-center gap-1.5 text-sm text-slate-300">
                            <input type="checkbox" name="furnishing" value="Furnished"
                                   th:checked="${editing != null and editing.furnishing != null and editing.furnishing.contains('Furnished')}">
                            Furnished
                        </label>
                        <label class="flex items-center gap-1.5 text-sm text-slate-300">
                            <input type="checkbox" name="furnishing" value="Part-furnished"
                                   th:checked="${editing != null and editing.furnishing != null and editing.furnishing.contains('Part-furnished')}">
                            Part-furnished
                        </label>
                        <label class="flex items-center gap-1.5 text-sm text-slate-300">
                            <input type="checkbox" name="furnishing" value="Unfurnished"
                                   th:checked="${editing != null and editing.furnishing != null and editing.furnishing.contains('Unfurnished')}">
                            Unfurnished
                        </label>
                    </div>
                </div>

                <div>
                    <label class="text-slate-400 text-xs block mb-1">AI Preferences (natural language)</label>
                    <textarea name="additionalCriteria" rows="4"
                              class="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-slate-300 text-sm focus:outline-none focus:border-blue-500 leading-relaxed"
                              th:text="${editing?.additionalCriteria}"></textarea>
                </div>

                <div class="flex gap-2 justify-end">
                    <a th:href="@{/config/search}"
                       class="bg-slate-700 border border-slate-600 px-4 py-2 rounded text-sm text-slate-400 hover:text-white transition-colors">Cancel</a>
                    <button type="submit"
                            class="bg-blue-600 px-4 py-2 rounded text-sm text-white hover:bg-blue-700 transition-colors">Save</button>
                </div>
            </form>
        </div>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 3: Create the monitored sites page**

```html
<!-- app/src/main/resources/templates/config/sites.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout :: html(~{:: content})}">
<body>
<div th:fragment="content">
    <h1 class="text-xl font-bold mb-4">Settings</h1>

    <!-- Tab nav -->
    <div class="flex gap-0 mb-6 border-b border-slate-700">
        <a th:href="@{/config/search}" class="px-5 py-2.5 text-sm text-slate-400 hover:text-slate-200 transition-colors">Search Criteria</a>
        <a th:href="@{/config/sites}" class="px-5 py-2.5 text-sm text-blue-400 border-b-2 border-blue-400 font-semibold">Monitored Sites</a>
    </div>

    <!-- Add site form -->
    <form th:action="@{/config/sites/add}" method="post"
          class="bg-slate-800 rounded-lg p-4 mb-6 flex gap-3 items-end flex-wrap">
        <div class="flex-1 min-w-48">
            <label class="text-slate-400 text-xs block mb-1">Site Name</label>
            <input type="text" name="name" required placeholder="e.g. Plaza Estates"
                   class="w-full bg-slate-700 border border-slate-600 rounded px-3 py-1.5 text-slate-100 text-sm focus:outline-none focus:border-blue-500">
        </div>
        <div class="flex-1 min-w-48">
            <label class="text-slate-400 text-xs block mb-1">URL</label>
            <input type="url" name="baseUrl" required placeholder="https://..."
                   class="w-full bg-slate-700 border border-slate-600 rounded px-3 py-1.5 text-slate-100 text-sm focus:outline-none focus:border-blue-500">
        </div>
        <div>
            <label class="text-slate-400 text-xs block mb-1">Type</label>
            <select name="scraperType"
                    class="bg-slate-700 border border-slate-600 rounded px-3 py-1.5 text-slate-100 text-sm focus:outline-none focus:border-blue-500">
                <option value="static">Static</option>
                <option value="js-rendered">JS Rendered</option>
                <option value="authenticated">Authenticated</option>
            </select>
        </div>
        <button type="submit" class="bg-blue-600 px-4 py-1.5 rounded text-sm text-white hover:bg-blue-700 transition-colors">Add Site</button>
    </form>

    <!-- Sites table -->
    <div class="bg-slate-800 rounded-lg overflow-hidden">
        <table class="w-full text-sm">
            <thead>
                <tr class="border-b border-slate-700 text-slate-400 text-xs uppercase">
                    <th class="text-left px-4 py-3">Site</th>
                    <th class="text-left px-4 py-3">URL</th>
                    <th class="text-left px-4 py-3">Type</th>
                    <th class="text-left px-4 py-3">Tier</th>
                    <th class="text-center px-4 py-3">Status</th>
                    <th class="text-center px-4 py-3">Actions</th>
                </tr>
            </thead>
            <tbody>
                <tr th:each="site : ${sites}" class="border-b border-slate-700/50">
                    <td class="px-4 py-3 text-slate-100 font-medium" th:text="${site.name}">Site Name</td>
                    <td class="px-4 py-3">
                        <a th:href="${site.baseUrl}" target="_blank" rel="noopener"
                           class="text-blue-400 text-xs hover:text-blue-300 truncate block max-w-48"
                           th:text="${site.baseUrl}">URL</a>
                    </td>
                    <td class="px-4 py-3 text-slate-400" th:text="${site.scraperType}">static</td>
                    <td class="px-4 py-3 text-slate-400" th:text="${site.tier}">Tier 1</td>
                    <td class="px-4 py-3 text-center">
                        <form th:action="@{/config/sites/{id}/toggle(id=${site.id})}" method="post" class="inline">
                            <button type="submit"
                                    th:classappend="${site.enabled} ? 'bg-green-500/20 text-green-400' : 'bg-slate-600/50 text-slate-500'"
                                    class="px-2.5 py-0.5 rounded text-xs">
                                <span th:text="${site.enabled} ? 'Enabled' : 'Disabled'">Enabled</span>
                            </button>
                        </form>
                    </td>
                    <td class="px-4 py-3 text-center">
                        <form th:action="@{/config/sites/{id}/delete(id=${site.id})}" method="post" class="inline">
                            <button type="submit" class="text-red-400/60 hover:text-red-400 text-xs transition-colors">Remove</button>
                        </form>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 4: Start the app and verify in browser**

Run:
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

Navigate to `http://localhost:8080/config/search`. Verify:
- Two saved searches appear (Primary London Search and Backup — Chelsea)
- Clicking "Edit" shows the form pre-populated
- Navigate to Monitored Sites tab — 19 sites listed with enable/disable toggles
- Can add a new site via the form

- [ ] **Step 5: Commit**

```bash
git add app/src/
git commit -m "feat: add configuration pages for search criteria and monitored sites"
```

---

## Task 12: Dockerfile

**Files:**
- Create: `app/Dockerfile`

- [ ] **Step 1: Create the Dockerfile**

```dockerfile
# app/Dockerfile
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Build and test the container locally**

Run:
```bash
./gradlew :app:bootJar
docker build -t london-search-portal ./app
docker run --rm -p 8080:8080 \
  -e APP_PASSWORD=changeme \
  -e DYNAMODB_ENDPOINT=http://host.docker.internal:8000 \
  -e AWS_REGION=eu-west-2 \
  -e AWS_ACCESS_KEY_ID=dummy \
  -e AWS_SECRET_ACCESS_KEY=dummy \
  -e SPRING_PROFILES_ACTIVE=local \
  london-search-portal
```

Expected: App starts in the container, accessible at `http://localhost:8080`

- [ ] **Step 3: Add .dockerignore**

```
# app/.dockerignore
.gradle
build/classes
build/generated
build/tmp
src
```

- [ ] **Step 4: Commit**

```bash
git add app/Dockerfile app/.dockerignore
git commit -m "feat: add Dockerfile for portal container"
```

---

## Task 13: CDK Infrastructure Stacks

**Files:**
- Create: `infra/src/main/java/com/londonsearch/infra/InfraApp.java`
- Create: `infra/src/main/java/com/londonsearch/infra/NetworkStack.java`
- Create: `infra/src/main/java/com/londonsearch/infra/DataStack.java`
- Create: `infra/src/main/java/com/londonsearch/infra/PortalStack.java`

- [ ] **Step 1: Create the CDK app entry point**

```java
// infra/src/main/java/com/londonsearch/infra/InfraApp.java
package com.londonsearch.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class InfraApp {

    public static void main(String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build();

        StackProps stackProps = StackProps.builder().env(env).build();

        NetworkStack network = new NetworkStack(app, "LondonSearch-Network", stackProps);
        DataStack data = new DataStack(app, "LondonSearch-Data", stackProps);
        PortalStack portal = new PortalStack(app, "LondonSearch-Portal", stackProps,
                network.getVpc(), data.getPropertiesTable(), data.getListingsTable(),
                data.getSearchConfigsTable(), data.getMonitoredSitesTable(),
                data.getImagesBucket());

        app.synth();
    }
}
```

- [ ] **Step 2: Create NetworkStack**

```java
// infra/src/main/java/com/londonsearch/infra/NetworkStack.java
package com.londonsearch.infra;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.Map;

public class NetworkStack extends Stack {

    private final Vpc vpc;

    public NetworkStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        this.vpc = Vpc.Builder.create(this, "Vpc")
                .maxAzs(2)
                .natGateways(0)
                .gatewayEndpoints(Map.of(
                        "S3", GatewayVpcEndpointOptions.builder()
                                .service(GatewayVpcEndpointAwsService.S3)
                                .build(),
                        "DynamoDB", GatewayVpcEndpointOptions.builder()
                                .service(GatewayVpcEndpointAwsService.DYNAMODB)
                                .build()
                ))
                .build();

        vpc.addInterfaceEndpoint("Ecr", InterfaceVpcEndpointOptions.builder()
                .service(InterfaceVpcEndpointAwsService.ECR)
                .build());
        vpc.addInterfaceEndpoint("EcrDocker", InterfaceVpcEndpointOptions.builder()
                .service(InterfaceVpcEndpointAwsService.ECR_DOCKER)
                .build());
        vpc.addInterfaceEndpoint("CloudWatchLogs", InterfaceVpcEndpointOptions.builder()
                .service(InterfaceVpcEndpointAwsService.CLOUDWATCH_LOGS)
                .build());
    }

    public Vpc getVpc() {
        return vpc;
    }
}
```

- [ ] **Step 3: Create DataStack**

```java
// infra/src/main/java/com/londonsearch/infra/DataStack.java
package com.londonsearch.infra;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.*;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

public class DataStack extends Stack {

    private final TableV2 propertiesTable;
    private final TableV2 listingsTable;
    private final TableV2 searchConfigsTable;
    private final TableV2 monitoredSitesTable;
    private final Bucket imagesBucket;

    public DataStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        this.propertiesTable = TableV2.Builder.create(this, "Properties")
                .tableName("Properties")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billing(Billing.onDemand())
                .pointInTimeRecovery(true)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        propertiesTable.addGlobalSecondaryIndex(GlobalSecondaryIndexPropsV2.builder()
                .indexName("area-firstSeenAt-index")
                .partitionKey(Attribute.builder().name("area").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("firstSeenAt").type(AttributeType.STRING).build())
                .build());

        propertiesTable.addGlobalSecondaryIndex(GlobalSecondaryIndexPropsV2.builder()
                .indexName("status-firstSeenAt-index")
                .partitionKey(Attribute.builder().name("status").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("firstSeenAt").type(AttributeType.STRING).build())
                .build());

        this.listingsTable = TableV2.Builder.create(this, "Listings")
                .tableName("Listings")
                .partitionKey(Attribute.builder().name("propertyId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("siteListingId").type(AttributeType.STRING).build())
                .billing(Billing.onDemand())
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        this.searchConfigsTable = TableV2.Builder.create(this, "SearchConfigs")
                .tableName("SearchConfigs")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billing(Billing.onDemand())
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        this.monitoredSitesTable = TableV2.Builder.create(this, "MonitoredSites")
                .tableName("MonitoredSites")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billing(Billing.onDemand())
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        this.imagesBucket = Bucket.Builder.create(this, "Images")
                .versioned(false)
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .enforceSSL(true)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();
    }

    public TableV2 getPropertiesTable() { return propertiesTable; }
    public TableV2 getListingsTable() { return listingsTable; }
    public TableV2 getSearchConfigsTable() { return searchConfigsTable; }
    public TableV2 getMonitoredSitesTable() { return monitoredSitesTable; }
    public Bucket getImagesBucket() { return imagesBucket; }
}
```

- [ ] **Step 4: Create PortalStack**

```java
// infra/src/main/java/com/londonsearch/infra/PortalStack.java
package com.londonsearch.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.TableV2;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;

import java.util.Map;

public class PortalStack extends Stack {

    public PortalStack(Construct scope, String id, StackProps props,
                       Vpc vpc, TableV2 propertiesTable, TableV2 listingsTable,
                       TableV2 searchConfigsTable, TableV2 monitoredSitesTable,
                       Bucket imagesBucket) {
        super(scope, id, props);

        Cluster cluster = Cluster.Builder.create(this, "Cluster")
                .vpc(vpc)
                .build();

        ApplicationLoadBalancedFargateService service =
                ApplicationLoadBalancedFargateService.Builder.create(this, "Portal")
                        .cluster(cluster)
                        .cpu(512)
                        .memoryLimitMiB(1024)
                        .desiredCount(1)
                        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(ContainerImage.fromAsset("../app"))
                                .containerPort(8080)
                                .environment(Map.of(
                                        "SPRING_PROFILES_ACTIVE", "prod",
                                        "AWS_REGION", props.getEnv().getRegion(),
                                        "PROPERTIES_TABLE", propertiesTable.getTableName(),
                                        "LISTINGS_TABLE", listingsTable.getTableName(),
                                        "SEARCH_CONFIGS_TABLE", searchConfigsTable.getTableName(),
                                        "MONITORED_SITES_TABLE", monitoredSitesTable.getTableName()
                                ))
                                .build())
                        .publicLoadBalancer(true)
                        .build();

        propertiesTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        listingsTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        searchConfigsTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        monitoredSitesTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
        imagesBucket.grantReadWrite(service.getTaskDefinition().getTaskRole());

        CfnOutput.Builder.create(this, "PortalUrl")
                .value("http://" + service.getLoadBalancer().getLoadBalancerDnsName())
                .description("Portal URL")
                .build();
    }
}
```

- [ ] **Step 5: Verify CDK synthesizes**

Run: `cd infra && ../gradlew -q run -- ls 2>&1 || npx cdk synth --dry-run 2>&1 | head -20`

Or more simply:

Run: `./gradlew :infra:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add infra/ cdk.json
git commit -m "feat: add CDK infrastructure stacks for VPC, DynamoDB, S3, and ECS Fargate"
```

---

## Task 14: Final Integration Test

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test`

Expected: All tests PASS

- [ ] **Step 2: Run the full local stack end-to-end**

Run:
```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

Verify in browser at `http://localhost:8080`:
1. Login page appears — enter `changeme`
2. Feed page shows 7 property cards with area filter pills
3. Click a card — detail page shows stats, AI summary, source listings
4. Click "Save" on a property — status updates
5. Navigate to Settings > Search Criteria — see 2 saved searches, can edit
6. Navigate to Settings > Monitored Sites — see 19 sites, can toggle and add
7. Click "Logout" — redirected to login

- [ ] **Step 3: Commit any fixes**

If any issues were found during manual testing, fix and commit:
```bash
git add -A
git commit -m "fix: address issues found during integration testing"
```

- [ ] **Step 4: Tag Phase 1 complete**

```bash
git tag phase-1-foundation
```
