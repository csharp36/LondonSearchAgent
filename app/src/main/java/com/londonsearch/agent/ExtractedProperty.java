package com.londonsearch.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedProperty(
        @JsonProperty("address") String address,
        @JsonProperty("price") String price,
        @JsonProperty("bedrooms") String bedrooms,
        @JsonProperty("bathrooms") String bathrooms,
        @JsonProperty("sqft") String sqft,
        @JsonProperty("propertyType") String propertyType,
        @JsonProperty("furnishing") String furnishing,
        @JsonProperty("description") String description,
        @JsonProperty("listingUrl") String listingUrl,
        @JsonProperty("imageUrls") List<String> imageUrls,
        @JsonProperty("floorPlanUrl") String floorPlanUrl,
        @JsonProperty("agentName") String agentName,
        @JsonProperty("agentPhone") String agentPhone,
        @JsonProperty("agentEmail") String agentEmail
) {}
