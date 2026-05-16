# Phase 5: Scale Out — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace placeholder template URLs with real working search URLs for all monitored sites, add an Account tab to the portal for managing settings and viewing alert history, implement real SES email sending for AWS deployment, and add the `/ping` health endpoint for AgentCore compatibility.

**Architecture:** DataSeeder updated with concrete, tested URLs for each property site. The pipeline's `processSite` method uses URLs as-is (no template resolution needed — each MonitoredSite entry has a ready-to-fetch URL). Account tab reads from AlertRepository and CostGuard for status display. SesAlertService uses AWS SES SDK for production email delivery.

**Tech Stack:** AWS SES SDK (already in version catalog), existing Spring Boot portal

---

## File Structure

```
app/src/main/java/com/londonsearch/
├── seed/
│   └── DataSeeder.java                # MODIFIED: real search URLs for all sites
├── alert/
│   └── SesAlertService.java           # Real SES email implementation
├── controller/
│   └── AccountController.java         # Account tab: alert history, budget, agent status
├── agent/
│   └── AgentController.java           # MODIFIED: add GET /ping for AgentCore
├── config/
│   └── SecurityConfig.java            # MODIFIED: allow /ping without auth
```

```
app/src/main/resources/templates/
├── config/
│   └── account.html                   # Account tab template
```

---

## Task 1: Fix Monitored Site URLs

**Files:**
- Modify: `app/src/main/java/com/londonsearch/seed/DataSeeder.java`

- [ ] **Step 1: Read the current DataSeeder `seedMonitoredSites` method**

Read the file to understand the current template-based URLs.

- [ ] **Step 2: Replace all template URLs with concrete, working search URLs**

The user originally provided these working URLs as examples:
- Rightmove: `https://www.rightmove.co.uk/property-to-rent/find.html?locationIdentifier=REGION%5E87490&minBedrooms=2&maxBedrooms=3&minPrice=5000&maxPrice=9000&propertyTypes=flat&mustHave=&dontShow=&furnishTypes=&keywords=`
- Knight Frank: `https://www.knightfrank.co.uk/properties/residential/to-let/uk-greater-london-london/all-types/2-2-beds;pricemax=7900;availability=available;baths=1`
- Savills: `https://search.savills.com/com/en/list?SearchList=Id_51730+Category_TownVillageCity&Tenure=GRS_T_R&SortOrder=SO_PCDD&MinPrice=5000&MaxPrice=10000&Currency=GBP&Period=Month&Bedrooms=GRS_B_2`
- OnTheMarket: `https://www.onthemarket.com/to-rent/property/london/?min-bedrooms=2&max-bedrooms=3&min-price=5000&max-price=9000`

Update each `saveMonitoredSite` call to use a real URL. For sites where we can't determine the exact URL format, use the base URL (homepage) — the pipeline will fetch whatever HTML is there and the mock extractor doesn't care about the HTML content anyway. When the Bedrock extractor is enabled, it'll extract whatever listings are on the page.

Key changes for each site:
- **Rightmove**: Use the search URL with `REGION%5E87490` (London) and bed/price filters
- **OnTheMarket**: Use `/to-rent/property/london/` with filters
- **Zoopla**: Use `/to-rent/property/london/` (note: Zoopla often returns 403 to scrapers)
- **Knight Frank**: Use the user's provided URL format
- **Savills**: Use `search.savills.com` with the user's provided format
- **Foxtons**: Use `https://www.foxtons.co.uk/properties-to-rent/london/` 
- **Chestertons**: Use `https://www.chestertons.co.uk/en-gb/residential-lettings/london`
- **All others**: Use their base URL or a best-guess lettings page URL

For sites known to require JS rendering, keep `scraperType` as `"js-rendered"`. For sites that block simple HTTP, note this in comments but keep them enabled (the pipeline handles fetch failures gracefully).

- [ ] **Step 3: Verify compilation and tests pass**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/londonsearch/seed/DataSeeder.java
git commit -m "feat: replace template URLs with real search URLs for all monitored sites"
```

---

## Task 2: Account Tab

**Files:**
- Create: `app/src/main/java/com/londonsearch/controller/AccountController.java`
- Create: `app/src/main/resources/templates/config/account.html`

- [ ] **Step 1: Create AccountController**

```java
// app/src/main/java/com/londonsearch/controller/AccountController.java
package com.londonsearch.controller;

import com.londonsearch.agent.CostGuard;
import com.londonsearch.model.AlertRecord;
import com.londonsearch.model.MonitoredSite;
import com.londonsearch.repository.AlertRepository;
import com.londonsearch.repository.MonitoredSiteRepository;
import com.londonsearch.repository.PropertyRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;

@Controller
public class AccountController {

    private final AlertRepository alertRepo;
    private final PropertyRepository propertyRepo;
    private final MonitoredSiteRepository siteRepo;
    private final CostGuard costGuard;

    public AccountController(AlertRepository alertRepo, PropertyRepository propertyRepo,
                              MonitoredSiteRepository siteRepo, CostGuard costGuard) {
        this.alertRepo = alertRepo;
        this.propertyRepo = propertyRepo;
        this.siteRepo = siteRepo;
        this.costGuard = costGuard;
    }

    @GetMapping("/config/account")
    public String account(Model model) {
        // Alert history
        List<AlertRecord> alerts = alertRepo.findAll().stream()
                .sorted(Comparator.comparing(
                        (AlertRecord a) -> a.getSentAt() != null ? a.getSentAt() : java.time.Instant.EPOCH)
                        .reversed())
                .toList();

        // Stats
        long totalProperties = propertyRepo.findAll().size();
        long newProperties = propertyRepo.findByStatus("new").size();
        long savedProperties = propertyRepo.findByStatus("saved").size();
        long enabledSites = siteRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .count();
        long totalSites = siteRepo.findAll().size();

        model.addAttribute("alerts", alerts);
        model.addAttribute("totalProperties", totalProperties);
        model.addAttribute("newProperties", newProperties);
        model.addAttribute("savedProperties", savedProperties);
        model.addAttribute("enabledSites", enabledSites);
        model.addAttribute("totalSites", totalSites);
        model.addAttribute("costGuardEnabled", costGuard.isEnabled());
        model.addAttribute("monthlyBudget", costGuard.getMonthlyBudget());

        return "config/account";
    }
}
```

- [ ] **Step 2: Create account.html template**

Follow the same pattern as `config/search.html` and `config/sites.html` — use the layout, include tab navigation with "Search Criteria", "Monitored Sites", and "Account" (active).

The page should show:
- **Dashboard stats**: total properties, new count, saved count, enabled sites / total sites
- **Cost Guard status**: enabled/disabled badge, monthly budget display
- **Alert History**: table of past alerts showing date, new property count, email recipient, smart link status (expired or active)

Read the existing `config/search.html` to match the exact Thymeleaf layout pattern and tab navigation styling.

- [ ] **Step 3: Update search.html and sites.html tab navigation**

Add the "Account" tab link to both `config/search.html` and `config/sites.html`:
```html
<a th:href="@{/config/account}" class="px-5 py-2.5 text-sm text-slate-400 hover:text-slate-200 transition-colors">Account</a>
```

- [ ] **Step 4: Verify it compiles and runs**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/londonsearch/controller/AccountController.java app/src/main/resources/templates/config/
git commit -m "feat: add Account tab with dashboard stats, cost guard status, and alert history"
```

---

## Task 3: SES Alert Service

**Files:**
- Create: `app/src/main/java/com/londonsearch/alert/SesAlertService.java`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add SES dependency to app build**

Add to `app/build.gradle.kts` dependencies:
```kotlin
implementation(libs.aws.ses)
```

(The `aws-ses` library is already defined in `gradle/libs.versions.toml`.)

- [ ] **Step 2: Create SesAlertService**

```java
// app/src/main/java/com/londonsearch/alert/SesAlertService.java
package com.londonsearch.alert;

import com.londonsearch.model.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "bedrock")
public class SesAlertService implements AlertService {

    private static final Logger log = LoggerFactory.getLogger(SesAlertService.class);

    private final SesClient sesClient;
    private final String emailTo;
    private final String emailFrom;

    public SesAlertService(
            @Value("${app.alert.email-to}") String emailTo,
            @Value("${app.alert.email-from:noreply@londonsearchagent.com}") String emailFrom,
            @Value("${app.aws.region}") String region) {
        this.emailTo = emailTo;
        this.emailFrom = emailFrom;
        this.sesClient = SesClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Override
    public void sendNewPropertiesAlert(List<Property> newProperties, String smartLinkUrl) {
        log.info("Sending SES alert for {} new properties to {}", newProperties.size(), emailTo);

        String subject = String.format("LondonSearchAgent: %d new properties", newProperties.size());

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Arial, sans-serif; background: #0f172a; color: #e2e8f0; padding: 20px;'>");
        html.append("<h2 style='color: #3b82f6;'>LondonSearchAgent</h2>");
        html.append(String.format("<p>%d new properties found matching your criteria:</p>", newProperties.size()));
        html.append("<hr style='border-color: #334155;'>");

        for (Property p : newProperties) {
            html.append("<div style='margin: 16px 0; padding: 12px; background: #1e293b; border-radius: 8px;'>");
            html.append(String.format("<strong style='color: #f1f5f9;'>%s</strong>", p.getAddress()));
            html.append(String.format("<br><span style='color: #94a3b8;'>%s</span>", p.getArea()));
            html.append(String.format("<br><span style='color: #3b82f6; font-weight: bold;'>£%,d pcm</span>", 
                    p.getPricePerMonth() != null ? p.getPricePerMonth() : 0));
            html.append(String.format(" · %d bed · %d bath",
                    p.getBedrooms() != null ? p.getBedrooms() : 0,
                    p.getBathrooms() != null ? p.getBathrooms() : 0));
            if (p.getMatchScore() != null) {
                String color = p.getMatchScore() >= 80 ? "#22c55e" : "#eab308";
                html.append(String.format(" · <span style='color: %s;'>%d%% match</span>", color, p.getMatchScore()));
            }
            if (p.getAiSummary() != null) {
                String summary = p.getAiSummary().length() > 150 
                        ? p.getAiSummary().substring(0, 150) + "..." 
                        : p.getAiSummary();
                html.append(String.format("<br><em style='color: #94a3b8; font-size: 0.9em;'>%s</em>", summary));
            }
            html.append("</div>");
        }

        html.append("<hr style='border-color: #334155;'>");
        html.append(String.format("<p><a href='%s' style='background: #3b82f6; color: white; padding: 10px 20px; "
                + "border-radius: 6px; text-decoration: none; display: inline-block;'>View in Portal</a></p>", smartLinkUrl));
        html.append("</body></html>");

        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .destination(Destination.builder().toAddresses(emailTo).build())
                    .source(emailFrom)
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(html.toString()).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build());

            log.info("SES alert sent successfully to {}", emailTo);
        } catch (Exception e) {
            log.error("Failed to send SES alert: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Add email-from config to application.yml**

Add under `app.alert:`:
```yaml
    email-from: ${ALERT_EMAIL_FROM:noreply@londonsearchagent.com}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/londonsearch/alert/SesAlertService.java app/src/main/resources/application.yml
git commit -m "feat: add SesAlertService for real email delivery via AWS SES"
```

---

## Task 4: AgentCore /ping Endpoint

**Files:**
- Modify: `app/src/main/java/com/londonsearch/agent/AgentController.java`
- Modify: `app/src/main/java/com/londonsearch/config/SecurityConfig.java`

- [ ] **Step 1: Add /ping endpoint to AgentController**

Read the current AgentController.java. Add:

```java
@GetMapping("/ping")
public ResponseEntity<Map<String, Object>> ping() {
    return ResponseEntity.ok(Map.of(
            "status", "Healthy",
            "time_of_last_update", Instant.now().getEpochSecond()
    ));
}
```

Add `import java.time.Instant;` if not already present.

- [ ] **Step 2: Allow /ping without auth in SecurityConfig**

Read the current SecurityConfig.java. Add `/ping` to the `permitAll()` matchers:

```java
.requestMatchers("/login", "/css/**", "/js/**", "/actuator/health", "/ping").permitAll()
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:test`

Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/londonsearch/agent/AgentController.java app/src/main/java/com/londonsearch/config/SecurityConfig.java
git commit -m "feat: add /ping health endpoint for AgentCore runtime compatibility"
```

---

## Task 5: Integration Test

- [ ] **Step 1: Run full test suite**

Run: `./gradlew :app:test`
Expected: All PASS

- [ ] **Step 2: Verify CDK compiles**

Run: `./gradlew :infra:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Build and start the app**

```bash
lsof -i :8080 -t | xargs kill -9 2>/dev/null; sleep 3
./gradlew :app:bootJar
SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/*.jar &
```

- [ ] **Step 4: Verify all endpoints**

```bash
# Health/ping
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/ping

# Login and check portal
# Feed, Detail, Config Search, Config Sites, Config Account
# Trigger pipeline
```

- [ ] **Step 5: Tag Phase 5**

```bash
git tag phase-5-scale-out
```
