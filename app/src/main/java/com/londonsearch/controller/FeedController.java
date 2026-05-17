package com.londonsearch.controller;

import com.londonsearch.model.Listing;
import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import com.londonsearch.repository.ListingRepository;
import com.londonsearch.repository.PropertyRepository;
import com.londonsearch.repository.SearchConfigRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class FeedController {

    private static final List<String> AREAS = List.of("Mayfair", "Marylebone", "South Kensington");

    private final PropertyRepository propertyRepository;
    private final ListingRepository listingRepository;
    private final SearchConfigRepository searchConfigRepository;

    public FeedController(PropertyRepository propertyRepository,
                          ListingRepository listingRepository,
                          SearchConfigRepository searchConfigRepository) {
        this.propertyRepository = propertyRepository;
        this.listingRepository = listingRepository;
        this.searchConfigRepository = searchConfigRepository;
    }

    @GetMapping("/")
    public String feed(@RequestParam(required = false) String area,
                       @RequestParam(required = false, defaultValue = "all") String filter,
                       @RequestParam(required = false, defaultValue = "score") String sort,
                       Model model) {

        List<Property> properties;

        if (area != null && !area.isBlank()) {
            properties = propertyRepository.findByArea(area);
        } else if ("new".equals(filter)) {
            properties = propertyRepository.findByStatus("new");
        } else if ("saved".equals(filter)) {
            properties = propertyRepository.findByStatus("saved");
        } else {
            properties = propertyRepository.findAll();
        }

        // Apply area filter on top of status filter when both are provided
        if (area != null && !area.isBlank() && !"all".equals(filter) && !"all".equals(filter)) {
            if ("new".equals(filter)) {
                properties = properties.stream()
                        .filter(p -> "new".equals(p.getStatus()))
                        .toList();
            } else if ("saved".equals(filter)) {
                properties = properties.stream()
                        .filter(p -> "saved".equals(p.getStatus()))
                        .toList();
            }
        }

        // Sort
        if ("score".equals(sort)) {
            properties = properties.stream()
                    .sorted(Comparator.comparingInt(
                            (Property p) -> p.getMatchScore() == null ? 0 : p.getMatchScore()
                    ).reversed())
                    .toList();
        } else {
            // Default: sort by firstSeenAt desc
            properties = properties.stream()
                    .sorted(Comparator.comparing(
                            p -> p.getFirstSeenAt() == null ? java.time.Instant.EPOCH : p.getFirstSeenAt(),
                            Comparator.reverseOrder()
                    ))
                    .toList();
        }

        // Calculate listing counts and first image per property
        Map<String, Integer> listingCounts = new HashMap<>();
        Map<String, String> propertyImages = new HashMap<>();
        for (Property p : properties) {
            List<Listing> listings = listingRepository.findByPropertyId(p.getId());
            listingCounts.put(p.getId(), listings.size());
            // Find first available image from any listing
            for (Listing l : listings) {
                if (l.getImageUrls() != null && !l.getImageUrls().isEmpty()) {
                    propertyImages.put(p.getId(), l.getImageUrls().get(0));
                    break;
                }
            }
        }

        // Filter out properties with 0 listings — every property must have at least one source
        properties = properties.stream()
                .filter(p -> listingCounts.getOrDefault(p.getId(), 0) > 0)
                .toList();

        // Apply price filtering from active search config
        SearchConfig activeConfig = searchConfigRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .findFirst().orElse(null);
        if (activeConfig != null) {
            Integer minPrice = activeConfig.getMinPrice();
            Integer maxPrice = activeConfig.getMaxPrice();
            if (minPrice != null || maxPrice != null) {
                properties = properties.stream()
                        .filter(p -> {
                            Integer price = p.getPricePerMonth();
                            if (price == null) return true; // keep properties with unknown price
                            if (minPrice != null && price < minPrice) return false;
                            if (maxPrice != null && price > maxPrice) return false;
                            return true;
                        })
                        .toList();
            }
        }

        // Calculate area counts from the filtered properties (with listings, in price range)
        // Use allFiltered = all properties with listings and in price range (no area/status filter)
        List<Property> allFromDb = propertyRepository.findAll();
        List<Property> allFiltered = allFromDb.stream()
                .filter(p -> listingCounts.containsKey(p.getId())
                        ? listingCounts.get(p.getId()) > 0
                        : listingRepository.findByPropertyId(p.getId()).size() > 0)
                .toList();
        if (activeConfig != null) {
            Integer min = activeConfig.getMinPrice();
            Integer max = activeConfig.getMaxPrice();
            if (min != null || max != null) {
                allFiltered = allFiltered.stream()
                        .filter(p -> {
                            Integer price = p.getPricePerMonth();
                            if (price == null) return true;
                            if (min != null && price < min) return false;
                            if (max != null && price > max) return false;
                            return true;
                        })
                        .toList();
            }
        }

        Map<String, Integer> areaCounts = new HashMap<>();
        for (Property p : allFiltered) {
            String propArea = p.getArea();
            if (propArea != null) {
                areaCounts.merge(propArea, 1, Integer::sum);
            }
        }
        for (String a : AREAS) {
            areaCounts.putIfAbsent(a, 0);
        }

        int newCount = (int) allFiltered.stream().filter(p -> "new".equals(p.getStatus())).count();
        int savedCount = (int) allFiltered.stream().filter(p -> "saved".equals(p.getStatus())).count();
        int totalCount = allFiltered.size();

        model.addAttribute("properties", properties);
        model.addAttribute("listingCounts", listingCounts);
        model.addAttribute("propertyImages", propertyImages);
        model.addAttribute("areas", AREAS);
        model.addAttribute("areaCounts", areaCounts);
        model.addAttribute("mayfairCount", areaCounts.getOrDefault("Mayfair", 0));
        model.addAttribute("maryleboneCount", areaCounts.getOrDefault("Marylebone", 0));
        model.addAttribute("southKensingtonCount", areaCounts.getOrDefault("South Kensington", 0));
        model.addAttribute("newCount", newCount);
        model.addAttribute("savedCount", savedCount);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("selectedArea", area != null ? area : "");
        model.addAttribute("selectedFilter", filter);
        model.addAttribute("selectedSort", sort);

        return "feed";
    }
}
