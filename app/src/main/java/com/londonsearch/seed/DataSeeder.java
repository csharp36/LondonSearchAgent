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

    private final PropertyRepository propertyRepository;
    private final ListingRepository listingRepository;
    private final SearchConfigRepository searchConfigRepository;
    private final MonitoredSiteRepository monitoredSiteRepository;

    public DataSeeder(
            PropertyRepository propertyRepository,
            ListingRepository listingRepository,
            SearchConfigRepository searchConfigRepository,
            MonitoredSiteRepository monitoredSiteRepository) {
        this.propertyRepository = propertyRepository;
        this.listingRepository = listingRepository;
        this.searchConfigRepository = searchConfigRepository;
        this.monitoredSiteRepository = monitoredSiteRepository;
    }

    @Override
    public void run(String... args) {
        // Skip seeding if data already exists (prevents duplicates on restart)
        if (!propertyRepository.findAll().isEmpty()) {
            log.info("Seed data already exists, skipping.");
            return;
        }
        log.info("Seeding development data...");
        seedProperties();
        seedSearchConfigs();
        seedMonitoredSites();
        log.info("Development seed data loaded successfully.");
    }

    private void seedProperties() {
        Instant now = Instant.now();

        // Property 1: 42 Baker Street - Marylebone
        Property p1 = new Property();
        p1.setId("prop-baker-street-42");
        p1.setAddress("42 Baker Street W1U");
        p1.setNormalizedAddress("42 baker street w1u");
        p1.setArea("Marylebone");
        p1.setBedrooms(3);
        p1.setBathrooms(2);
        p1.setPrice(7500);
        p1.setCurrency("GBP");
        p1.setPricePerMonth(7500);
        p1.setSqft(1200);
        p1.setPropertyType("Flat");
        p1.setFurnishing("Furnished");
        p1.setMatchScore(92);
        p1.setStatus("new");
        p1.setAvailableFrom("2026-06-01");
        p1.setAiSummary("An excellent Marylebone flat on the iconic Baker Street, offering generous proportions at 1,200 sqft with premium finishes throughout. The building benefits from a 24-hour concierge and is moments from Baker Street tube, making commuting effortless. At £7,500/month furnished, this represents strong value for the area given the bedroom count and finish level.");
        p1.setDescription("Stunning three-bedroom apartment in the heart of Marylebone. The property features an open-plan reception with floor-to-ceiling windows, a fully-equipped kitchen, and a large master bedroom with en-suite. Located moments from Baker Street station and Marylebone High Street.");
        p1.setFirstSeenAt(now.minus(1, ChronoUnit.DAYS));
        p1.setLastUpdatedAt(now);
        propertyRepository.save(p1);

        // Listings for Property 1
        saveListing(p1.getId(), "kf-baker-42", "Knight Frank", "https://knightfrank.com",
                "£7,500 pcm", "42 Baker Street, London W1U",
                "https://knightfrank.com/properties/london/marylebone/42-baker-street",
                "James Whitfield", "+44 20 7861 5000", "j.whitfield@knightfrank.com", now.minus(1, ChronoUnit.DAYS));
        saveListing(p1.getId(), "rm-baker-42", "Rightmove", "https://rightmove.co.uk",
                "£7,500 pcm", "42 Baker Street, Marylebone, London W1U",
                "https://rightmove.co.uk/properties/12345678",
                "Knight Frank London", "+44 20 7861 5000", null, now.minus(1, ChronoUnit.DAYS));
        saveListing(p1.getId(), "sv-baker-42", "Savills", "https://savills.com",
                "£7,500 per calendar month", "42 Baker Street W1U 3BW",
                "https://savills.com/residential-sales/england/london/marylebone/baker-street-42",
                "Charlotte Evans", "+44 20 7499 8644", "c.evans@savills.com", now.minus(23, ChronoUnit.HOURS));

        // Property 2: 15 Mount Street - Mayfair
        Property p2 = new Property();
        p2.setId("prop-mount-street-15");
        p2.setAddress("15 Mount Street W1K");
        p2.setNormalizedAddress("15 mount street w1k");
        p2.setArea("Mayfair");
        p2.setBedrooms(2);
        p2.setBathrooms(2);
        p2.setPrice(8200);
        p2.setCurrency("GBP");
        p2.setPricePerMonth(8200);
        p2.setSqft(950);
        p2.setPropertyType("Flat");
        p2.setFurnishing("Furnished");
        p2.setMatchScore(78);
        p2.setStatus("new");
        p2.setAvailableFrom("2026-05-20");
        p2.setAiSummary("A prestigious Mayfair address on Mount Street, one of London's most coveted residential streets lined with terracotta mansion blocks and luxury boutiques. The 950 sqft layout is compact for the price but the location premium is undeniable, with The Connaught hotel and Mount Street Gardens a short stroll away. The 78% match score reflects the slightly above-budget ask, though the two bathrooms and Mayfair postcode justify consideration.");
        p2.setDescription("Exceptional two-bedroom apartment on the prestigious Mount Street in the heart of Mayfair. The property occupies the third floor of a handsome period building and features a reception room, fully fitted kitchen, two double bedrooms, and two bathrooms. Available furnished to a high standard.");
        p2.setFirstSeenAt(now.minus(3, ChronoUnit.HOURS));
        p2.setLastUpdatedAt(now);
        propertyRepository.save(p2);

        saveListing(p2.getId(), "wth-mount-15", "Wetherell", "https://wetherell.co.uk",
                "£8,200 per calendar month", "15 Mount Street, Mayfair, London W1K",
                "https://wetherell.co.uk/properties/mayfair/mount-street/15",
                "Peter Wetherell", "+44 20 7529 5588", "enquiries@wetherell.co.uk", now.minus(3, ChronoUnit.HOURS));

        // Property 3: 8 Onslow Gardens - South Kensington
        Property p3 = new Property();
        p3.setId("prop-onslow-gardens-8");
        p3.setAddress("8 Onslow Gardens SW7");
        p3.setNormalizedAddress("8 onslow gardens sw7");
        p3.setArea("South Kensington");
        p3.setBedrooms(3);
        p3.setBathrooms(2);
        p3.setPrice(6800);
        p3.setCurrency("GBP");
        p3.setPricePerMonth(6800);
        p3.setSqft(1450);
        p3.setPropertyType("Flat");
        p3.setFurnishing("Part-furnished");
        p3.setMatchScore(88);
        p3.setStatus("seen");
        p3.setAvailableFrom("2026-07-01");
        p3.setAiSummary("One of the better value propositions in South Kensington, this 1,450 sqft flat on the quiet garden square of Onslow Gardens offers exceptional space for £6,800/month. The part-furnished status gives flexibility to add personal touches, and the garden square setting is particularly tranquil. The 88% match score is held back only by the July availability and part-furnished condition; otherwise this would be a top pick.");
        p3.setDescription("Spacious three-bedroom apartment set on a beautiful garden square in South Kensington. This elegant property retains many period features including high ceilings and original fireplaces, whilst offering a modern kitchen and bathrooms. The property is part-furnished and benefits from a large reception room overlooking the communal gardens.");
        p3.setFirstSeenAt(now.minus(2, ChronoUnit.DAYS));
        p3.setLastUpdatedAt(now.minus(1, ChronoUnit.DAYS));
        propertyRepository.save(p3);

        saveListing(p3.getId(), "ch-onslow-8", "Chestertons", "https://chestertons.com",
                "£6,800 pcm", "8 Onslow Gardens, South Kensington, London SW7",
                "https://chestertons.com/en-gb/properties/south-kensington/onslow-gardens/8",
                "Sophie Hartley", "+44 20 7225 2233", "southkensington@chestertons.com", now.minus(2, ChronoUnit.DAYS));
        saveListing(p3.getId(), "otm-onslow-8", "OnTheMarket", "https://onthemarket.com",
                "£6,800 per month", "8 Onslow Gardens SW7 3NL",
                "https://onthemarket.com/details/property-12345/",
                "Chestertons South Kensington", "+44 20 7225 2233", null, now.minus(2, ChronoUnit.DAYS));

        // Property 4: 22 Harley Street - Marylebone
        Property p4 = new Property();
        p4.setId("prop-harley-street-22");
        p4.setAddress("22 Harley Street W1G");
        p4.setNormalizedAddress("22 harley street w1g");
        p4.setArea("Marylebone");
        p4.setBedrooms(2);
        p4.setBathrooms(1);
        p4.setPrice(5900);
        p4.setCurrency("GBP");
        p4.setPricePerMonth(5900);
        p4.setSqft(800);
        p4.setPropertyType("Flat");
        p4.setFurnishing("Unfurnished");
        p4.setMatchScore(85);
        p4.setStatus("new");
        p4.setAvailableFrom("2026-06-15");
        p4.setAiSummary("Harley Street is best known for its medical practices but the residential conversions here offer characterful Georgian architecture at a relative discount to neighbouring Wimpole Street. At 800 sqft unfurnished, this two-bedroom flat represents solid value for Marylebone at £5,900/month, particularly given the high ceilings and period proportions. The single bathroom is the main limitation for the price point.");
        p4.setDescription("Charming two-bedroom flat on the historic Harley Street, occupying part of a beautifully converted Georgian townhouse. Features include high ceilings, large sash windows, and a well-proportioned reception room. Being offered unfurnished to allow tenants to bring their own furniture and personalise the space.");
        p4.setFirstSeenAt(now.minus(5, ChronoUnit.HOURS));
        p4.setLastUpdatedAt(now);
        propertyRepository.save(p4);

        // Property 5: 7 Thurloe Place - South Kensington
        Property p5 = new Property();
        p5.setId("prop-thurloe-place-7");
        p5.setAddress("7 Thurloe Place SW7");
        p5.setNormalizedAddress("7 thurloe place sw7");
        p5.setArea("South Kensington");
        p5.setBedrooms(2);
        p5.setBathrooms(2);
        p5.setPrice(7200);
        p5.setCurrency("GBP");
        p5.setPricePerMonth(7200);
        p5.setSqft(1050);
        p5.setPropertyType("Flat");
        p5.setFurnishing("Furnished");
        p5.setMatchScore(90);
        p5.setStatus("new");
        p5.setAvailableFrom("2026-06-01");
        p5.setAiSummary("Thurloe Place sits in an enviable position directly opposite the Victoria and Albert Museum, making this one of the most culturally vibrant addresses in South Kensington. At 1,050 sqft with two bathrooms and full furnishings, the £7,200/month ask is competitive for this immediate vicinity. The 90% match score reflects the excellent condition and location, making this a priority viewing candidate.");
        p5.setDescription("Beautifully presented two-bedroom apartment on prestigious Thurloe Place, directly opposite the Victoria and Albert Museum. The flat has been recently refurbished to a high standard and features a large reception room, designer kitchen, two double bedrooms each with en-suite facilities, and private roof terrace access.");
        p5.setFirstSeenAt(now.minus(8, ChronoUnit.HOURS));
        p5.setLastUpdatedAt(now);
        propertyRepository.save(p5);

        // Property 6: 3 Carlos Place - Mayfair
        Property p6 = new Property();
        p6.setId("prop-carlos-place-3");
        p6.setAddress("3 Carlos Place W1K");
        p6.setNormalizedAddress("3 carlos place w1k");
        p6.setArea("Mayfair");
        p6.setBedrooms(3);
        p6.setBathrooms(3);
        p6.setPrice(9000);
        p6.setCurrency("GBP");
        p6.setPricePerMonth(9000);
        p6.setSqft(1800);
        p6.setPropertyType("Flat");
        p6.setFurnishing("Furnished");
        p6.setMatchScore(75);
        p6.setStatus("saved");
        p6.setAvailableFrom("2026-08-01");
        p6.setAiSummary("Carlos Place is arguably Mayfair's most exclusive short cul-de-sac, home to The Connaught hotel and some of the most prestigious residential addresses in London. At 1,800 sqft with three bathrooms, the space is genuinely exceptional for central Mayfair. The 75% score reflects the premium above budget and August availability, but for buyers seeking the very best Mayfair address this is hard to surpass.");
        p6.setDescription("An outstanding three-bedroom lateral apartment on the most prestigious cul-de-sac in Mayfair. Set within a beautifully maintained period building, the accommodation extends to approximately 1,800 sqft and features three double bedrooms, three bathrooms, a formal reception room, and a separate dining room. Available furnished to the highest specification.");
        p6.setFirstSeenAt(now.minus(4, ChronoUnit.DAYS));
        p6.setLastUpdatedAt(now.minus(2, ChronoUnit.DAYS));
        propertyRepository.save(p6);

        // Property 7: 11 Montagu Square - Marylebone
        Property p7 = new Property();
        p7.setId("prop-montagu-square-11");
        p7.setAddress("11 Montagu Square W1H");
        p7.setNormalizedAddress("11 montagu square w1h");
        p7.setArea("Marylebone");
        p7.setBedrooms(3);
        p7.setBathrooms(2);
        p7.setPrice(7800);
        p7.setCurrency("GBP");
        p7.setPricePerMonth(7800);
        p7.setSqft(1350);
        p7.setPropertyType("Flat");
        p7.setFurnishing("Furnished");
        p7.setMatchScore(87);
        p7.setStatus("new");
        p7.setAvailableFrom("2026-06-01");
        p7.setAiSummary("Montagu Square is one of Marylebone's most sought-after addresses, a quiet Georgian garden square with strong residential character and excellent connections via Marble Arch and Edgware Road. This 1,350 sqft flat is well-proportioned with three good bedrooms and benefits from views over the central gardens. At £7,800/month fully furnished, it compares favourably to similar square-facing properties in the immediate area.");
        p7.setDescription("Superb three-bedroom apartment on one of Marylebone's finest garden squares. The property occupies the first floor of a handsome Georgian building and offers well-proportioned accommodation throughout. The master bedroom overlooks the square's communal gardens, and the property comes fully furnished to a very high standard throughout.");
        p7.setFirstSeenAt(now.minus(12, ChronoUnit.HOURS));
        p7.setLastUpdatedAt(now);
        propertyRepository.save(p7);

        log.info("Seeded 7 properties");
    }

    private void saveListing(String propertyId, String siteListingId, String siteName, String siteUrl,
                              String originalPrice, String originalAddress, String listingUrl,
                              String agentName, String agentPhone, String agentEmail, Instant scrapedAt) {
        Listing listing = new Listing();
        listing.setPropertyId(propertyId);
        listing.setSiteListingId(siteListingId);
        listing.setSiteName(siteName);
        listing.setSiteUrl(siteUrl);
        listing.setOriginalPrice(originalPrice);
        listing.setOriginalAddress(originalAddress);
        listing.setListingUrl(listingUrl);
        listing.setAgentName(agentName);
        listing.setAgentPhone(agentPhone);
        listing.setAgentEmail(agentEmail);
        listing.setScrapedAt(scrapedAt);
        listingRepository.save(listing);
    }

    private void seedSearchConfigs() {
        Instant now = Instant.now();

        SearchConfig primary = new SearchConfig();
        primary.setId("config-primary-london");
        primary.setName("Primary London Search");
        primary.setAreas(List.of("Mayfair", "Marylebone", "South Kensington"));
        primary.setMinBeds(2);
        primary.setMaxBeds(4);
        primary.setMinPrice(5000);
        primary.setMaxPrice(9000);
        primary.setMinBaths(1);
        primary.setFurnishing(List.of("Furnished", "Part-furnished"));
        primary.setPropertyTypes(List.of("Flat", "Apartment"));
        primary.setAdditionalCriteria("Must have concierge or porter. Prefer garden square or period building. Minimum 900 sqft.");
        primary.setEnabled(true);
        primary.setCreatedAt(now.minus(7, ChronoUnit.DAYS));
        searchConfigRepository.save(primary);

        SearchConfig backup = new SearchConfig();
        backup.setId("config-backup-chelsea");
        backup.setName("Backup Chelsea");
        backup.setAreas(List.of("Chelsea", "Kensington"));
        backup.setMinBeds(2);
        backup.setMaxBeds(3);
        backup.setMinPrice(5500);
        backup.setMaxPrice(8000);
        backup.setMinBaths(1);
        backup.setFurnishing(List.of("Furnished"));
        backup.setPropertyTypes(List.of("Flat", "Apartment", "Maisonette"));
        backup.setAdditionalCriteria("Chelsea preferred. Close to tube. Private or communal garden a strong plus.");
        backup.setEnabled(false);
        backup.setCreatedAt(now.minus(3, ChronoUnit.DAYS));
        searchConfigRepository.save(backup);

        log.info("Seeded 2 search configs");
    }

    private void seedMonitoredSites() {
        // Aggregators
        saveMonitoredSite("site-rightmove", "Rightmove", "https://www.rightmove.co.uk",
                "https://www.rightmove.co.uk/property-to-rent/find.html?locationIdentifier=REGION%5E87490&minBedrooms=2&maxBedrooms=3&minPrice=5000&maxPrice=9000&propertyTypes=flat&channel=RENT",
                "js-rendered", true, "aggregator");

        saveMonitoredSite("site-onthemarket", "OnTheMarket", "https://www.onthemarket.com",
                "https://www.onthemarket.com/to-rent/2-bed-property/london/?min-price=5000&max-price=9000",
                "http", true, "aggregator");

        saveMonitoredSite("site-zoopla", "Zoopla", "https://www.zoopla.co.uk",
                "https://www.zoopla.co.uk/to-rent/property/2-bedrooms/london/?price_min=5000&price_max=9000",
                "js-rendered", true, "aggregator");

        // Tier 1
        saveMonitoredSite("site-knight-frank", "Knight Frank", "https://www.knightfrank.co.uk",
                "https://www.knightfrank.co.uk/properties/residential/to-let/uk-greater-london-london/all-types/2-3-beds;pricemax=9000;pricemin=5000;availability=available",
                "authenticated", true, "tier1");

        saveMonitoredSite("site-savills", "Savills", "https://www.savills.co.uk",
                "https://search.savills.com/list?SearchList=Id_51730+Category_TownVillageCity&Tenure=GRS_T_R&SortOrder=SO_PCDD&MinPrice=5000&MaxPrice=9000&MinBedrooms=2&MaxBedrooms=3&Category=GRS_CAT_RES&Currency=GBP&Period=Month",
                "js-rendered", true, "tier1");

        saveMonitoredSite("site-foxtons", "Foxtons", "https://www.foxtons.co.uk",
                "https://www.foxtons.co.uk/properties-to-rent/london/2-bedrooms",
                "js-rendered", true, "tier1");

        saveMonitoredSite("site-chestertons", "Chestertons", "https://www.chestertons.co.uk",
                "https://www.chestertons.co.uk/london/mayfair/lettings",
                "http", true, "tier1");

        saveMonitoredSite("site-strutt-parker", "Strutt & Parker", "https://www.struttandparker.com",
                "https://www.struttandparker.com/properties/residential/to-let/london",
                "http", true, "tier1");

        saveMonitoredSite("site-jll", "JLL Residential", "https://www.jll.co.uk",
                "https://residential.jll.co.uk/properties-to-rent/london",
                "http", true, "tier1");

        saveMonitoredSite("site-marsh-parsons", "Marsh & Parsons", "https://www.marshandparsons.co.uk",
                "https://www.marshandparsons.co.uk/properties-to-rent/london/mayfair/",
                "http", true, "tier1");

        saveMonitoredSite("site-hamptons", "Hamptons", "https://www.hamptons.co.uk",
                "https://www.hamptons.co.uk/properties/to-let/london",
                "http", true, "tier1");

        saveMonitoredSite("site-winkworth", "Winkworth", "https://www.winkworth.co.uk",
                "https://www.winkworth.co.uk/properties/to-let/london",
                "http", true, "tier1");

        saveMonitoredSite("site-dexters", "Dexters", "https://www.dexters.co.uk",
                "https://www.dexters.co.uk/property-lettings/properties-to-rent-in-mayfair",
                "http", true, "tier1");

        saveMonitoredSite("site-benham-reeves", "Benham & Reeves", "https://www.benhams.com",
                "https://www.benhams.com/properties-to-rent/london",
                "http", true, "tier1");

        // Tier 2
        saveMonitoredSite("site-wetherell", "Wetherell", "https://www.wetherell.co.uk",
                "https://www.wetherell.co.uk/properties/lettings",
                "http", true, "tier2");

        saveMonitoredSite("site-knightsbridge-prime", "Knightsbridge Prime Property", "https://knightsbridgeprimeproperty.com",
                "https://knightsbridgeprimeproperty.com/lettings/",
                "http", true, "tier2");

        saveMonitoredSite("site-quintessentially", "Quintessentially Estates", "https://quintessentiallyestates.com",
                "https://quintessentiallyestates.com/lettings-agents-london/",
                "http", true, "tier2");

        saveMonitoredSite("site-hudsons", "Hudsons Property", "https://www.hudsonsproperty.com",
                "https://www.hudsonsproperty.com/property-lettings/",
                "http", true, "tier2");

        saveMonitoredSite("site-carter-jonas", "Carter Jonas", "https://www.carterjonas.co.uk",
                "https://www.carterjonas.co.uk/properties/residential/to-let/london",
                "http", true, "tier2");

        log.info("Seeded 19 monitored sites");
    }

    private void saveMonitoredSite(String id, String name, String baseUrl, String searchUrlTemplate,
                                    String scraperType, boolean enabled, String tier) {
        MonitoredSite site = new MonitoredSite();
        site.setId(id);
        site.setName(name);
        site.setBaseUrl(baseUrl);
        site.setSearchUrlTemplate(searchUrlTemplate);
        site.setScraperType(scraperType);
        site.setEnabled(enabled);
        site.setTier(tier);
        monitoredSiteRepository.save(site);
    }
}
