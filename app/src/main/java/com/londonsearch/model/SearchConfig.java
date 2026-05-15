package com.londonsearch.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.time.Instant;
import java.util.List;

@DynamoDbBean
public class SearchConfig {

    private String id;
    private String name;
    private List<String> areas;
    private Integer minBeds;
    private Integer maxBeds;
    private Integer minPrice;
    private Integer maxPrice;
    private Integer minBaths;
    private List<String> furnishing;
    private List<String> propertyTypes;
    private String additionalCriteria;
    private Boolean enabled;
    private Instant createdAt;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getAreas() { return areas; }
    public void setAreas(List<String> areas) { this.areas = areas; }
    public Integer getMinBeds() { return minBeds; }
    public void setMinBeds(Integer minBeds) { this.minBeds = minBeds; }
    public Integer getMaxBeds() { return maxBeds; }
    public void setMaxBeds(Integer maxBeds) { this.maxBeds = maxBeds; }
    public Integer getMinPrice() { return minPrice; }
    public void setMinPrice(Integer minPrice) { this.minPrice = minPrice; }
    public Integer getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Integer maxPrice) { this.maxPrice = maxPrice; }
    public Integer getMinBaths() { return minBaths; }
    public void setMinBaths(Integer minBaths) { this.minBaths = minBaths; }
    public List<String> getFurnishing() { return furnishing; }
    public void setFurnishing(List<String> furnishing) { this.furnishing = furnishing; }
    public List<String> getPropertyTypes() { return propertyTypes; }
    public void setPropertyTypes(List<String> propertyTypes) { this.propertyTypes = propertyTypes; }
    public String getAdditionalCriteria() { return additionalCriteria; }
    public void setAdditionalCriteria(String additionalCriteria) { this.additionalCriteria = additionalCriteria; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
