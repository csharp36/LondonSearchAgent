package com.londonsearch.agent;

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

    public AgentPipelineService(SiteFetcher siteFetcher,
                                 PropertyExtractor extractor,
                                 PropertyNormalizer normalizer,
                                 PropertyRepository propertyRepo,
                                 ListingRepository listingRepo,
                                 MonitoredSiteRepository siteRepo,
                                 DeduplicationService deduplicationService,
                                 StructuredScorer structuredScorer,
                                 PropertyIntelligence intelligence,
                                 SearchConfigRepository searchConfigRepo) {
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
    }

    public RunResult runFullPipeline() {
        log.info("Starting agent pipeline run");
        List<MonitoredSite> sites = siteRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .toList();

        int totalNew = 0, totalUpdated = 0, sitesProcessed = 0, sitesSkipped = 0;
        List<String> errors = new ArrayList<>();

        for (MonitoredSite site : sites) {
            try {
                SiteResult result = processSite(site);
                if (result.skipped()) {
                    sitesSkipped++;
                } else {
                    sitesProcessed++;
                    totalNew += result.pipelineResult().newProperties();
                    totalUpdated += result.pipelineResult().updatedProperties();
                }
            } catch (Exception e) {
                log.error("Error processing site {}: {}", site.getName(), e.getMessage());
                errors.add(site.getName() + ": " + e.getMessage());
            }
        }

        log.info("Pipeline complete: {} sites processed, {} skipped, {} new, {} updated",
                sitesProcessed, sitesSkipped, totalNew, totalUpdated);
        return new RunResult(sitesProcessed, sitesSkipped, totalNew, totalUpdated, errors);
    }

    public SiteResult processSite(MonitoredSite site) {
        String url = site.getSearchUrlTemplate() != null ? site.getSearchUrlTemplate() : site.getBaseUrl();

        Optional<SiteFetcher.FetchResult> fetchResult = siteFetcher.fetch(url);
        if (fetchResult.isEmpty()) {
            return new SiteResult(true, new PipelineResult(0, 0));
        }

        SiteFetcher.FetchResult result = fetchResult.get();

        if (!SiteFetcher.hasChanged(result.hash(), site.getLastChangeHash())) {
            log.info("No changes detected for {}", site.getName());
            return new SiteResult(true, new PipelineResult(0, 0));
        }

        List<ExtractedProperty> extracted = extractor.extract(result.html(), site.getName());
        if (extracted.isEmpty()) {
            log.warn("No properties extracted from {}", site.getName());
            updateSiteHash(site, result.hash());
            return new SiteResult(false, new PipelineResult(0, 0));
        }

        PipelineResult pipelineResult = processExtractedProperties(extracted, site.getName(), site.getBaseUrl());
        updateSiteHash(site, result.hash());
        return new SiteResult(false, pipelineResult);
    }

    public PipelineResult processExtractedProperties(List<ExtractedProperty> extracted,
                                                      String siteName, String siteBaseUrl) {
        int newCount = 0, updatedCount = 0;

        for (ExtractedProperty ep : extracted) {
            String normalizedAddr = normalizer.normalizeAddress(ep.address());
            if (normalizedAddr == null || normalizedAddr.isBlank()) continue;

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
                newCount++;
            }
        }

        return new PipelineResult(newCount, updatedCount);
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

    public record PipelineResult(int newProperties, int updatedProperties) {}
    public record SiteResult(boolean skipped, PipelineResult pipelineResult) {}
    public record RunResult(int sitesProcessed, int sitesSkipped, int newProperties,
                            int updatedProperties, List<String> errors) {}
}
