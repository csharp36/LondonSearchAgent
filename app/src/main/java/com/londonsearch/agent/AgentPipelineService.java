package com.londonsearch.agent;

import com.londonsearch.alert.AlertService;
import com.londonsearch.alert.SmartLinkService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AgentPipelineService.class);

    private final SiteFetcher siteFetcher;
    private final PropertyExtractor extractor;
    private final PropertyNormalizer normalizer;
    private final PropertyRepository propertyRepo;
    private final ListingRepository listingRepo;
    private final MonitoredSiteRepository siteRepo;
    private final DeduplicationService deduplicationService;
    private final StructuredScorer structuredScorer;
    private final PropertyIntelligence intelligence;
    private final SearchConfigRepository searchConfigRepo;
    private final AlertService alertService;
    private final SmartLinkService smartLinkService;
    private final String alertEmailTo;
    private final CostGuard costGuard;
    private final ImageEnricher imageEnricher;

    public AgentPipelineService(SiteFetcher siteFetcher,
                                 PropertyExtractor extractor,
                                 PropertyNormalizer normalizer,
                                 PropertyRepository propertyRepo,
                                 ListingRepository listingRepo,
                                 MonitoredSiteRepository siteRepo,
                                 DeduplicationService deduplicationService,
                                 StructuredScorer structuredScorer,
                                 PropertyIntelligence intelligence,
                                 SearchConfigRepository searchConfigRepo,
                                 AlertService alertService,
                                 SmartLinkService smartLinkService,
                                 @Value("${app.alert.email-to}") String alertEmailTo,
                                 CostGuard costGuard,
                                 ImageEnricher imageEnricher) {
        this.siteFetcher = siteFetcher;
        this.extractor = extractor;
        this.normalizer = normalizer;
        this.propertyRepo = propertyRepo;
        this.listingRepo = listingRepo;
        this.siteRepo = siteRepo;
        this.deduplicationService = deduplicationService;
        this.structuredScorer = structuredScorer;
        this.intelligence = intelligence;
        this.searchConfigRepo = searchConfigRepo;
        this.alertService = alertService;
        this.smartLinkService = smartLinkService;
        this.alertEmailTo = alertEmailTo;
        this.costGuard = costGuard;
        this.imageEnricher = imageEnricher;
    }

    public RunResult runFullPipeline() {
        if (!costGuard.canProceed()) {
            log.warn("Pipeline blocked by cost guard");
            return new RunResult(0, 0, 0, 0, List.of("Pipeline blocked by cost guard"));
        }
        log.info("Starting agent pipeline run");
        List<MonitoredSite> sites = siteRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .toList();

        int totalNew = 0, totalUpdated = 0, sitesProcessed = 0, sitesSkipped = 0;
        List<String> errors = new ArrayList<>();
        List<String> allNewPropertyIds = new ArrayList<>();

        for (MonitoredSite site : sites) {
            try {
                SiteResult result = processSite(site);
                if (result.skipped()) {
                    sitesSkipped++;
                } else {
                    sitesProcessed++;
                    totalNew += result.pipelineResult().newProperties();
                    totalUpdated += result.pipelineResult().updatedProperties();
                    allNewPropertyIds.addAll(result.pipelineResult().newPropertyIds());
                }
            } catch (Exception e) {
                log.error("Error processing site {}: {}", site.getName(), e.getMessage());
                errors.add(site.getName() + ": " + e.getMessage());
            }
        }

        if (!allNewPropertyIds.isEmpty()) {
            sendAlert(allNewPropertyIds);
        }

        log.info("Pipeline complete: {} sites processed, {} skipped, {} new, {} updated",
                sitesProcessed, sitesSkipped, totalNew, totalUpdated);
        return new RunResult(sitesProcessed, sitesSkipped, totalNew, totalUpdated, errors);
    }

    public SiteResult processSite(MonitoredSite site) {
        // Skip JS-rendered sites — Jsoup can't execute JavaScript
        if ("js-rendered".equals(site.getScraperType())) {
            log.debug("Skipping {} — requires JS rendering (not supported yet)", site.getName());
            return new SiteResult(true, new PipelineResult(0, 0, List.of()));
        }

        String template = site.getSearchUrlTemplate() != null ? site.getSearchUrlTemplate() : site.getBaseUrl();

        // Build targeted URLs — if template contains {area}, expand to one URL per search config area
        List<String> urls = expandUrls(template);

        List<ExtractedProperty> allExtracted = new ArrayList<>();
        String lastHash = null;

        for (String url : urls) {
            Optional<SiteFetcher.FetchResult> fetchResult = siteFetcher.fetch(url);
            if (fetchResult.isEmpty()) continue;

            SiteFetcher.FetchResult result = fetchResult.get();
            lastHash = result.hash();

            List<ExtractedProperty> extracted = extractor.extract(result.html(), site.getName());
            log.info("{}: extracted {} properties from {}", site.getName(), extracted.size(), url);
            allExtracted.addAll(extracted);
        }

        if (allExtracted.isEmpty()) {
            if (lastHash != null) updateSiteHash(site, lastHash);
            return new SiteResult(urls.isEmpty(), new PipelineResult(0, 0, List.of()));
        }

        // Enrich listings that have no images by fetching og:image from their listing pages
        allExtracted = imageEnricher.enrich(allExtracted);

        PipelineResult pipelineResult = processExtractedProperties(allExtracted, site.getName(), site.getBaseUrl());
        if (lastHash != null) updateSiteHash(site, lastHash);
        return new SiteResult(false, pipelineResult);
    }

    /** Rightmove REGION codes for target areas. */
    private static final java.util.Map<String, String> RIGHTMOVE_CODES = java.util.Map.of(
            "mayfair", "87523",
            "marylebone", "87522",
            "south-kensington", "85252"
    );

    /** Expands URL templates with search config values. If template contains {area}, generates one URL per area. */
    private List<String> expandUrls(String template) {
        if (!template.contains("{")) return List.of(template);

        SearchConfig config = searchConfigRepo.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .findFirst().orElse(null);
        if (config == null) return List.of(template);

        // Replace non-area placeholders
        String base = template
                .replace("{minBeds}", config.getMinBeds() != null ? config.getMinBeds().toString() : "1")
                .replace("{maxBeds}", config.getMaxBeds() != null ? config.getMaxBeds().toString() : "5")
                .replace("{minPrice}", config.getMinPrice() != null ? config.getMinPrice().toString() : "0")
                .replace("{maxPrice}", config.getMaxPrice() != null ? config.getMaxPrice().toString() : "100000");

        // Handle Rightmove's special {rightmoveCode} placeholder
        if (base.contains("{rightmoveCode}")) {
            List<String> areas = config.getAreas();
            if (areas == null || areas.isEmpty()) return List.of();
            return areas.stream()
                    .map(area -> {
                        String slug = area.toLowerCase().replace(" ", "-");
                        String code = RIGHTMOVE_CODES.getOrDefault(slug, "87490"); // fallback to London
                        return base.replace("{rightmoveCode}", code);
                    })
                    .toList();
        }

        if (!base.contains("{area}")) return List.of(base);

        // Expand one URL per target area
        List<String> areas = config.getAreas();
        if (areas == null || areas.isEmpty()) {
            return List.of(base.replace("{area}", "london"));
        }
        return areas.stream()
                .map(area -> base.replace("{area}", area.toLowerCase().replace(" ", "-")))
                .toList();
    }

    public PipelineResult processExtractedProperties(List<ExtractedProperty> extracted,
                                                      String siteName, String siteBaseUrl) {
        int newCount = 0, updatedCount = 0;
        List<String> newPropertyIds = new ArrayList<>();

        for (ExtractedProperty ep : extracted) {
            String normalizedAddr = normalizer.normalizeAddress(ep.address());
            if (normalizedAddr == null || normalizedAddr.isBlank()) continue;

            // Skip hallucinated/placeholder addresses
            if (normalizer.isFakeAddress(ep.address())) {
                log.warn("Skipping fake/placeholder address: {}", ep.address());
                continue;
            }

            // Skip entries with no parseable price (garbage extraction)
            Integer pricePerMonth = normalizer.parsePricePerMonth(ep.price());
            if (pricePerMonth == null) {
                log.debug("Skipping {} — no parseable price", ep.address());
                continue;
            }

            Optional<DeduplicationService.DedupMatch> match = deduplicationService.findMatch(normalizedAddr);

            if (match.isPresent()) {
                Property prop = match.get().property();
                double confidence = match.get().confidence();
                if (deduplicationService.isMediumConfidenceMatch(confidence)) {
                    log.info("Medium confidence match ({}) for: {} ↔ {}",
                            String.format("%.2f", confidence), normalizedAddr, prop.getNormalizedAddress());
                }
                saveListing(prop.getId(), ep, siteName, siteBaseUrl);
                updatedCount++;
            } else {
                Property prop = createProperty(ep, normalizedAddr);
                propertyRepo.save(prop);
                scoreAndAssess(prop);
                saveListing(prop.getId(), ep, siteName, siteBaseUrl);
                newPropertyIds.add(prop.getId());
                newCount++;
            }
        }

        return new PipelineResult(newCount, updatedCount, newPropertyIds);
    }

    private Property createProperty(ExtractedProperty ep, String normalizedAddr) {
        Property prop = new Property();
        prop.setId(UUID.randomUUID().toString());
        prop.setAddress(ep.address());
        prop.setNormalizedAddress(normalizedAddr);
        prop.setArea(normalizer.classifyArea(ep.address()));
        prop.setBedrooms(normalizer.parseInteger(ep.bedrooms()));
        prop.setBathrooms(normalizer.parseInteger(ep.bathrooms()));
        prop.setPricePerMonth(normalizer.parsePricePerMonth(ep.price()));
        prop.setPrice(prop.getPricePerMonth());
        prop.setCurrency("GBP");
        prop.setSqft(normalizer.parseInteger(ep.sqft()));
        prop.setPropertyType(ep.propertyType());
        prop.setFurnishing(ep.furnishing());
        prop.setDescription(ep.description());
        prop.setAvailableFrom(ep.availableFrom());
        prop.setStatus("new");
        prop.setFirstSeenAt(Instant.now());
        prop.setLastUpdatedAt(Instant.now());
        return prop;
    }

    private void saveListing(String propertyId, ExtractedProperty ep,
                              String siteName, String siteBaseUrl) {
        String siteListingId = siteName.toLowerCase().replace(" ", "") + "#" +
                UUID.randomUUID().toString().substring(0, 8);

        List<Listing> existingListings = listingRepo.findByPropertyId(propertyId);
        boolean alreadyHasListingFromSite = existingListings.stream()
                .anyMatch(l -> siteName.equals(l.getSiteName()));
        if (alreadyHasListingFromSite) return;

        Listing listing = new Listing();
        listing.setPropertyId(propertyId);
        listing.setSiteListingId(siteListingId);
        listing.setSiteName(siteName);
        listing.setSiteUrl(siteBaseUrl);
        listing.setOriginalPrice(ep.price());
        listing.setOriginalAddress(ep.address());
        listing.setListingUrl(ep.listingUrl());
        listing.setImageUrls(ep.imageUrls() != null ? ep.imageUrls() : List.of());
        listing.setFloorPlanUrl(ep.floorPlanUrl());
        listing.setAgentName(ep.agentName());
        listing.setAgentPhone(ep.agentPhone());
        listing.setAgentEmail(ep.agentEmail());
        listing.setScrapedAt(Instant.now());
        listingRepo.save(listing);
    }

    private void scoreAndAssess(Property property) {
        List<SearchConfig> configs = searchConfigRepo.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .toList();
        if (configs.isEmpty()) return;

        SearchConfig primaryConfig = configs.get(0);
        int structuredScore = structuredScorer.score(property, primaryConfig);
        PropertyIntelligence.Assessment assessment = intelligence.assess(property, primaryConfig);
        int combinedScore = (int) Math.round(structuredScore * 0.6 + assessment.aiScore() * 0.4);

        property.setMatchScore(combinedScore);
        property.setAiSummary(assessment.aiSummary());
        property.setLastUpdatedAt(Instant.now());
        propertyRepo.save(property);
    }

    private void updateSiteHash(MonitoredSite site, String hash) {
        site.setLastChangeHash(hash);
        site.setLastCheckedAt(Instant.now());
        siteRepo.save(site);
    }

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

    public record PipelineResult(int newProperties, int updatedProperties, List<String> newPropertyIds) {}
    public record SiteResult(boolean skipped, PipelineResult pipelineResult) {}
    public record RunResult(int sitesProcessed, int sitesSkipped, int newProperties,
                            int updatedProperties, List<String> errors) {}
}
