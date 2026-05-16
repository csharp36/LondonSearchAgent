package com.londonsearch.controller;

import com.londonsearch.model.Listing;
import com.londonsearch.model.Property;
import com.londonsearch.repository.ListingRepository;
import com.londonsearch.repository.PropertyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Controller
public class PropertyController {

    private final PropertyRepository propertyRepository;
    private final ListingRepository listingRepository;

    public PropertyController(PropertyRepository propertyRepository, ListingRepository listingRepository) {
        this.propertyRepository = propertyRepository;
        this.listingRepository = listingRepository;
    }

    @GetMapping("/property/{id}")
    public String propertyDetail(@PathVariable String id, Model model) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));

        List<Listing> listings = listingRepository.findByPropertyId(id);

        // If status is "new", mark as "seen"
        if ("new".equals(property.getStatus())) {
            property.setStatus("seen");
            property.setLastUpdatedAt(Instant.now());
            propertyRepository.save(property);
        }

        // Collect all image URLs from listings
        List<String> allImages = new ArrayList<>();
        for (Listing l : listings) {
            if (l.getImageUrls() != null) {
                allImages.addAll(l.getImageUrls());
            }
        }

        // Format first seen date for display
        String firstSeenFormatted = null;
        if (property.getFirstSeenAt() != null) {
            firstSeenFormatted = DateTimeFormatter.ofPattern("dd MMM yyyy")
                    .withZone(ZoneId.of("Europe/London"))
                    .format(property.getFirstSeenAt());
        }

        model.addAttribute("property", property);
        model.addAttribute("listings", listings);
        model.addAttribute("listingCount", listings.size());
        model.addAttribute("images", allImages);
        model.addAttribute("firstSeenFormatted", firstSeenFormatted);

        return "property-detail";
    }

    @PostMapping("/property/{id}/status")
    public String updateStatus(@PathVariable String id, @RequestParam String status) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
        property.setStatus(status);
        property.setLastUpdatedAt(Instant.now());
        propertyRepository.save(property);
        return "redirect:/property/" + id;
    }
}
