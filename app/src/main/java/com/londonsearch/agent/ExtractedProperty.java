package com.londonsearch.agent;

import java.util.List;

public record ExtractedProperty(
        String address,
        String price,
        String bedrooms,
        String bathrooms,
        String sqft,
        String propertyType,
        String furnishing,
        String description,
        String listingUrl,
        List<String> imageUrls,
        String floorPlanUrl,
        String agentName,
        String agentPhone,
        String agentEmail
) {}
