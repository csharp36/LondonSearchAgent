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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class FeedController {

    private static final List<String> AREAS = List.of("Mayfair", "Marylebone", "South Kensington");

    private final PropertyRepository propertyRepository;
    private final ListingRepository listingRepository;

    public FeedController(PropertyRepository propertyRepository, ListingRepository listingRepository) {
        this.propertyRepository = propertyRepository;
        this.listingRepository = listingRepository;
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

        // Calculate area counts from the properties already loaded
        List<Property> allProperties = propertyRepository.findAll();
        Map<String, Integer> areaCounts = new HashMap<>();
        for (Property p : allProperties) {
            String propArea = p.getArea();
            if (propArea != null) {
                areaCounts.merge(propArea, 1, Integer::sum);
            }
        }
        // Ensure all target areas have an entry
        for (String a : AREAS) {
            areaCounts.putIfAbsent(a, 0);
        }

        int newCount = (int) allProperties.stream().filter(p -> "new".equals(p.getStatus())).count();
        int savedCount = (int) allProperties.stream().filter(p -> "saved".equals(p.getStatus())).count();
        int totalCount = allProperties.size();

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
