# Phase 4: Alerts & Operations — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add email alerts (only when new properties found), smart links for one-click portal access from email, application-level cost guards, and CDK infrastructure for EventBridge scheduling and SES — making the system capable of running autonomously.

**Architecture:** `AlertService` interface (mock logs to console, SES sends real email) triggers after pipeline runs with new matches. Smart links use UUID tokens stored in `AlertHistory` DynamoDB table with 24-hour TTL. The `/alert/{token}` endpoint validates the token and creates an authenticated session. A `CostGuard` checks a configurable budget before each pipeline run. CDK adds EventBridge scheduled rule (4x daily) and the AlertHistory table.

**Tech Stack:** AWS SDK SES (already in version catalog), DynamoDB AlertHistory table, EventBridge Scheduler CDK construct

---

## File Structure

```
app/src/main/java/com/londonsearch/
├── model/
│   └── AlertRecord.java                # DynamoDB entity for alert history
├── repository/
│   └── AlertRepository.java            # CRUD for AlertRecord
├── alert/
│   ├── AlertService.java               # Interface: send alert email
│   ├── MockAlertService.java           # Logs email content to console
│   ├── SesAlertService.java            # Real SES implementation
│   ├── SmartLinkService.java           # Token generation + validation
│   └── AlertController.java            # GET /alert/{token} — smart link handler
├── agent/
│   ├── CostGuard.java                  # Budget check before pipeline run
│   └── AgentPipelineService.java       # MODIFIED: add alert + cost guard
├── config/
│   └── SecurityConfig.java             # MODIFIED: /alert/** already permitted
├── repository/
│   └── TableInitializer.java           # MODIFIED: add AlertRecord table
```

```
infra/src/main/java/com/londonsearch/infra/
├── DataStack.java                      # MODIFIED: add AlertHistory table
├── PortalStack.java                    # MODIFIED: grant AlertHistory access
├── ScheduleStack.java                  # NEW: EventBridge + SES config
├── InfraApp.java                       # MODIFIED: add ScheduleStack
```

---

## Task 1: AlertRecord Model + Repository

**Files:**
- Create: `app/src/main/java/com/londonsearch/model/AlertRecord.java`
- Create: `app/src/main/java/com/londonsearch/repository/AlertRepository.java`
- Modify: `app/src/main/java/com/londonsearch/repository/TableInitializer.java`
- Modify: `app/src/main/resources/application.yml`

- [ ] **Step 1: Create AlertRecord entity**

```java
// app/src/main/java/com/londonsearch/model/AlertRecord.java
package com.londonsearch.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;

@DynamoDbBean
public class AlertRecord {

    private String id;
    private List<String> propertyIds;
    private String emailSentTo;
    private Instant sentAt;
    private String smartLinkToken;
    private Instant tokenExpiresAt;
    private Integer newPropertyCount;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getPropertyIds() { return propertyIds; }
    public void setPropertyIds(List<String> propertyIds) { this.propertyIds = propertyIds; }

    public String getEmailSentTo() { return emailSentTo; }
    public void setEmailSentTo(String emailSentTo) { this.emailSentTo = emailSentTo; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public String getSmartLinkToken() { return smartLinkToken; }
    public void setSmartLinkToken(String smartLinkToken) { this.smartLinkToken = smartLinkToken; }

    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public Integer getNewPropertyCount() { return newPropertyCount; }
    public void setNewPropertyCount(Integer newPropertyCount) { this.newPropertyCount = newPropertyCount; }
}
```

- [ ] **Step 2: Create AlertRepository**

```java
// app/src/main/java/com/londonsearch/repository/AlertRepository.java
package com.londonsearch.repository;

import com.londonsearch.model.AlertRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;

import java.util.List;
import java.util.Optional;

@Repository
public class AlertRepository {

    private final DynamoDbTable<AlertRecord> table;

    public AlertRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.alerts}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(AlertRecord.class));
    }

    public void save(AlertRecord alert) {
        table.putItem(alert);
    }

    public Optional<AlertRecord> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id).build()));
    }

    public Optional<AlertRecord> findByToken(String token) {
        // Scan for token match — acceptable at alert volume (<100 records)
        return table.scan().items().stream()
                .filter(a -> token.equals(a.getSmartLinkToken()))
                .findFirst();
    }

    public List<AlertRecord> findAll() {
        return table.scan().items().stream().toList();
    }
}
```

- [ ] **Step 3: Add alerts table config to application.yml**

Add under `app.aws.tables:`:
```yaml
      alerts: ${ALERTS_TABLE:Alerts}
```

Also add under `app:`:
```yaml
  alert:
    email-to: ${ALERT_EMAIL:csharp36@gmail.com}
    portal-base-url: ${PORTAL_BASE_URL:http://localhost:8080}
```

- [ ] **Step 4: Add alerts table config to application-local.yml and application-test.yml**

No additional overrides needed — the defaults in application.yml work for local/test.

- [ ] **Step 5: Update TableInitializer to create Alerts table**

Read the current TableInitializer.java first, then add `AlertRecord.class` table creation alongside the existing 4 tables. Add the `alertsTable` name as a new constructor parameter from `${app.aws.tables.alerts}`.

- [ ] **Step 6: Verify it compiles and tests pass**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/londonsearch/model/AlertRecord.java app/src/main/java/com/londonsearch/repository/AlertRepository.java app/src/main/java/com/londonsearch/repository/TableInitializer.java app/src/main/resources/
git commit -m "feat: add AlertRecord model and repository with DynamoDB table"
```

---

## Task 2: Alert Service + Smart Link Service

**Files:**
- Create: `app/src/main/java/com/londonsearch/alert/AlertService.java`
- Create: `app/src/main/java/com/londonsearch/alert/MockAlertService.java`
- Create: `app/src/main/java/com/londonsearch/alert/SmartLinkService.java`

- [ ] **Step 1: Create AlertService interface**

```java
// app/src/main/java/com/londonsearch/alert/AlertService.java
package com.londonsearch.alert;

import com.londonsearch.model.Property;

import java.util.List;

public interface AlertService {
    void sendNewPropertiesAlert(List<Property> newProperties, String smartLinkToken);
}
```

- [ ] **Step 2: Create SmartLinkService**

```java
// app/src/main/java/com/londonsearch/alert/SmartLinkService.java
package com.londonsearch.alert;

import com.londonsearch.model.AlertRecord;
import com.londonsearch.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SmartLinkService {

    private final AlertRepository alertRepo;
    private final String portalBaseUrl;

    public SmartLinkService(
            AlertRepository alertRepo,
            @Value("${app.alert.portal-base-url}") String portalBaseUrl) {
        this.alertRepo = alertRepo;
        this.portalBaseUrl = portalBaseUrl;
    }

    /**
     * Generates a smart link token and saves the alert record.
     * Returns the full smart link URL.
     */
    public String generateSmartLink(List<String> propertyIds, String emailTo, int newCount) {
        String token = UUID.randomUUID().toString();

        AlertRecord record = new AlertRecord();
        record.setId(UUID.randomUUID().toString());
        record.setPropertyIds(propertyIds);
        record.setEmailSentTo(emailTo);
        record.setSentAt(Instant.now());
        record.setSmartLinkToken(token);
        record.setTokenExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        record.setNewPropertyCount(newCount);
        alertRepo.save(record);

        return portalBaseUrl + "/alert/" + token;
    }

    /**
     * Validates a smart link token. Returns the alert record if valid and not expired.
     */
    public Optional<AlertRecord> validateToken(String token) {
        return alertRepo.findByToken(token)
                .filter(alert -> alert.getTokenExpiresAt() != null
                        && alert.getTokenExpiresAt().isAfter(Instant.now()));
    }
}
```

- [ ] **Step 3: Create MockAlertService**

```java
// app/src/main/java/com/londonsearch/alert/MockAlertService.java
package com.londonsearch.alert;

import com.londonsearch.model.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "mock", matchIfMissing = true)
public class MockAlertService implements AlertService {

    private static final Logger log = LoggerFactory.getLogger(MockAlertService.class);

    @Value("${app.alert.email-to}")
    private String emailTo;

    @Override
    public void sendNewPropertiesAlert(List<Property> newProperties, String smartLinkToken) {
        log.info("=== MOCK EMAIL ALERT ===");
        log.info("To: {}", emailTo);
        log.info("Subject: LondonSearchAgent: {} new properties", newProperties.size());
        log.info("---");
        for (Property p : newProperties) {
            log.info("  {} | {} | {} bed | £{} pcm | {}% match",
                    p.getAddress(),
                    p.getArea(),
                    p.getBedrooms(),
                    p.getPricePerMonth(),
                    p.getMatchScore());
            if (p.getAiSummary() != null) {
                log.info("  AI: {}", p.getAiSummary().length() > 80
                        ? p.getAiSummary().substring(0, 80) + "..."
                        : p.getAiSummary());
            }
        }
        log.info("---");
        log.info("Smart link: {}", smartLinkToken);
        log.info("=== END MOCK EMAIL ===");
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/londonsearch/alert/
git commit -m "feat: add AlertService with MockAlertService and SmartLinkService"
```

---

## Task 3: Smart Link Controller

**Files:**
- Create: `app/src/main/java/com/londonsearch/alert/AlertController.java`

- [ ] **Step 1: Create AlertController**

This handles the `/alert/{token}` URL from email smart links. It validates the token, creates an authenticated session, and redirects to the feed filtered to the alert's properties.

```java
// app/src/main/java/com/londonsearch/alert/AlertController.java
package com.londonsearch.alert;

import com.londonsearch.model.AlertRecord;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Optional;

@Controller
public class AlertController {

    private final SmartLinkService smartLinkService;

    public AlertController(SmartLinkService smartLinkService) {
        this.smartLinkService = smartLinkService;
    }

    @GetMapping("/alert/{token}")
    public String handleSmartLink(@PathVariable String token, HttpServletRequest request) {
        Optional<AlertRecord> alert = smartLinkService.validateToken(token);

        if (alert.isEmpty()) {
            return "redirect:/login?expired";
        }

        // Create authenticated session
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
        );

        // Redirect to feed filtered to new properties
        return "redirect:/?filter=new";
    }
}
```

- [ ] **Step 2: Verify it compiles and tests pass**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/londonsearch/alert/AlertController.java
git commit -m "feat: add smart link controller for token-based one-click portal access"
```

---

## Task 4: Wire Alerts into Pipeline

**Files:**
- Modify: `app/src/main/java/com/londonsearch/agent/AgentPipelineService.java`

- [ ] **Step 1: Read the current AgentPipelineService.java**

Read the file to understand the current constructor and `runFullPipeline` method.

- [ ] **Step 2: Add AlertService, SmartLinkService, and email config to constructor**

Add these new fields and constructor parameters:
- `AlertService alertService`
- `SmartLinkService smartLinkService`
- `@Value("${app.alert.email-to}") String alertEmailTo`

- [ ] **Step 3: Track new property IDs during pipeline run**

In `runFullPipeline`, add a `List<String> newPropertyIds = new ArrayList<>()` before the site loop. In `processExtractedProperties`, return the IDs of new properties. Update `PipelineResult` to include the new property IDs:

```java
public record PipelineResult(int newProperties, int updatedProperties, List<String> newPropertyIds) {}
```

Update all places that create `PipelineResult` to include the IDs.

- [ ] **Step 4: Send alert after pipeline completes**

At the end of `runFullPipeline`, after the for loop, add:

```java
// Collect all new property IDs across all sites
List<String> allNewPropertyIds = new ArrayList<>();
// (collect from each site result during the loop)

if (!allNewPropertyIds.isEmpty()) {
    sendAlert(allNewPropertyIds);
}
```

Add a private method:

```java
private void sendAlert(List<String> newPropertyIds) {
    try {
        List<Property> newProperties = newPropertyIds.stream()
                .map(id -> propertyRepo.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(
                        (Property p) -> p.getMatchScore() != null ? p.getMatchScore() : 0).reversed())
                .limit(5)
                .toList();

        if (newProperties.isEmpty()) return;

        String smartLink = smartLinkService.generateSmartLink(
                newPropertyIds, alertEmailTo, newPropertyIds.size());

        alertService.sendNewPropertiesAlert(newProperties, smartLink);
        log.info("Alert sent for {} new properties", newPropertyIds.size());
    } catch (Exception e) {
        log.error("Failed to send alert: {}", e.getMessage());
    }
}
```

- [ ] **Step 5: Run tests — fix any compilation issues**

Run: `./gradlew :app:test`

Expected: All PASS (some tests may need updating if they construct PipelineResult directly)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/AgentPipelineService.java
git commit -m "feat: send email alerts with smart links when pipeline finds new properties"
```

---

## Task 5: Cost Guard

**Files:**
- Create: `app/src/main/java/com/londonsearch/agent/CostGuard.java`
- Modify: `app/src/main/java/com/londonsearch/agent/AgentPipelineService.java`
- Modify: `app/src/main/resources/application.yml`

- [ ] **Step 1: Add cost guard config to application.yml**

Add under `app.agent:`:
```yaml
    cost-guard:
      enabled: ${COST_GUARD_ENABLED:false}
      monthly-budget: ${COST_GUARD_BUDGET:50}
```

- [ ] **Step 2: Create CostGuard**

```java
// app/src/main/java/com/londonsearch/agent/CostGuard.java
package com.londonsearch.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CostGuard {

    private static final Logger log = LoggerFactory.getLogger(CostGuard.class);

    private final boolean enabled;
    private final int monthlyBudget;

    public CostGuard(
            @Value("${app.agent.cost-guard.enabled:false}") boolean enabled,
            @Value("${app.agent.cost-guard.monthly-budget:50}") int monthlyBudget) {
        this.enabled = enabled;
        this.monthlyBudget = monthlyBudget;
    }

    /**
     * Checks whether the pipeline should proceed based on cost constraints.
     * Returns true if OK to proceed, false if budget is exceeded.
     *
     * In the current implementation, this is a configurable toggle.
     * Phase 5 will add AWS Cost Explorer API integration for real cost checking.
     */
    public boolean canProceed() {
        if (!enabled) {
            return true;
        }

        // TODO: In Phase 5, query AWS Cost Explorer API for month-to-date spend
        // For now, this is a manual kill switch — set COST_GUARD_ENABLED=true to block runs
        log.warn("Cost guard is enabled — pipeline blocked. Set COST_GUARD_ENABLED=false to resume.");
        return false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMonthlyBudget() {
        return monthlyBudget;
    }
}
```

- [ ] **Step 3: Wire CostGuard into AgentPipelineService**

Read the current file, add `CostGuard costGuard` to the constructor. At the start of `runFullPipeline()`, add:

```java
if (!costGuard.canProceed()) {
    log.warn("Pipeline blocked by cost guard");
    return new RunResult(0, 0, 0, 0, List.of("Pipeline blocked by cost guard"));
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/CostGuard.java app/src/main/java/com/londonsearch/agent/AgentPipelineService.java app/src/main/resources/application.yml
git commit -m "feat: add CostGuard with configurable kill switch for pipeline budget control"
```

---

## Task 6: CDK Updates

**Files:**
- Modify: `infra/src/main/java/com/londonsearch/infra/DataStack.java`
- Create: `infra/src/main/java/com/londonsearch/infra/ScheduleStack.java`
- Modify: `infra/src/main/java/com/londonsearch/infra/InfraApp.java`
- Modify: `infra/src/main/java/com/londonsearch/infra/PortalStack.java`

- [ ] **Step 1: Add AlertHistory table to DataStack**

Read the current DataStack.java. Add a new `TableV2` field:

```java
private final TableV2 alertsTable;

// In constructor:
this.alertsTable = TableV2.Builder.create(this, "Alerts")
        .tableName("Alerts")
        .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
        .billing(Billing.onDemand())
        .removalPolicy(RemovalPolicy.RETAIN)
        .build();
```

Add getter: `public TableV2 getAlertsTable() { return alertsTable; }`

- [ ] **Step 2: Create ScheduleStack**

```java
// infra/src/main/java/com/londonsearch/infra/ScheduleStack.java
package com.londonsearch.infra;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.events.CronOptions;
import software.constructs.Construct;

public class ScheduleStack extends Stack {

    public ScheduleStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        // 4x daily: 06:00, 12:00, 18:00, 00:00 UTC
        Rule.Builder.create(this, "AgentSchedule-0600")
                .schedule(Schedule.cron(CronOptions.builder().hour("6").minute("0").build()))
                .description("LondonSearchAgent pipeline run - 06:00 UTC")
                .build();

        Rule.Builder.create(this, "AgentSchedule-1200")
                .schedule(Schedule.cron(CronOptions.builder().hour("12").minute("0").build()))
                .description("LondonSearchAgent pipeline run - 12:00 UTC")
                .build();

        Rule.Builder.create(this, "AgentSchedule-1800")
                .schedule(Schedule.cron(CronOptions.builder().hour("18").minute("0").build()))
                .description("LondonSearchAgent pipeline run - 18:00 UTC")
                .build();

        Rule.Builder.create(this, "AgentSchedule-0000")
                .schedule(Schedule.cron(CronOptions.builder().hour("0").minute("0").build()))
                .description("LondonSearchAgent pipeline run - 00:00 UTC")
                .build();

        // Note: EventBridge targets (Lambda or ECS task to call /agent/run)
        // will be added when the AgentCore deployment is configured in Phase 5
    }
}
```

- [ ] **Step 3: Update PortalStack to grant alerts table access**

Read current PortalStack.java. Add `TableV2 alertsTable` parameter to the constructor. Add:
```java
alertsTable.grantReadWriteData(service.getTaskDefinition().getTaskRole());
```

Add `ALERTS_TABLE` to the environment variables map.

- [ ] **Step 4: Update InfraApp to wire ScheduleStack**

Read current InfraApp.java. Add:
```java
ScheduleStack schedule = new ScheduleStack(app, "LondonSearch-Schedule", stackProps);
```

Update the PortalStack constructor call to pass `data.getAlertsTable()`.

- [ ] **Step 5: Verify CDK compiles**

Run: `./gradlew :infra:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add infra/
git commit -m "feat: add CDK stacks for AlertHistory table, EventBridge schedule, and SES"
```

---

## Task 7: Integration Test

- [ ] **Step 1: Run full test suite**

Run: `./gradlew :app:test`
Expected: All PASS

- [ ] **Step 2: Build and start the app**

```bash
lsof -i :8080 -t | xargs kill -9 2>/dev/null; sleep 3
./gradlew :app:bootJar
SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/*.jar > /tmp/london-search.log 2>&1 &
```

Wait for startup.

- [ ] **Step 3: Trigger pipeline and verify alert**

```bash
curl -s -c /tmp/lsa.txt http://localhost:8080/login > /tmp/login.html
CSRF=$(grep -o 'name="_csrf" value="[^"]*"' /tmp/login.html | sed 's/name="_csrf" value="//' | sed 's/"$//')
curl -s -c /tmp/lsa.txt -b /tmp/lsa.txt -X POST "http://localhost:8080/login" -d "password=changeme&_csrf=$CSRF" -o /dev/null
curl -s -b /tmp/lsa.txt -X POST http://localhost:8080/agent/run
```

Check logs for "MOCK EMAIL ALERT" output showing property summaries and smart link URL.

- [ ] **Step 4: Test smart link**

From the logs, copy the smart link token. Open the URL in a browser (or curl it). It should redirect to the feed page without requiring login.

- [ ] **Step 5: Tag Phase 4**

```bash
git tag phase-4-alerts-operations
```
