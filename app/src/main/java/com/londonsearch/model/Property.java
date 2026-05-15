package com.londonsearch.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.time.Instant;

@DynamoDbBean
public class Property {

    private String id;
    private String address;
    private String normalizedAddress;
    private String area;
    private Integer bedrooms;
    private Integer bathrooms;
    private Integer price;
    private String currency;
    private Integer pricePerMonth;
    private Integer sqft;
    private String propertyType;
    private String furnishing;
    private String availableFrom;
    private String description;
    private String aiSummary;
    private Integer matchScore;
    private String status;
    private Instant firstSeenAt;
    private Instant lastUpdatedAt;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getNormalizedAddress() { return normalizedAddress; }
    public void setNormalizedAddress(String normalizedAddress) { this.normalizedAddress = normalizedAddress; }

    @DynamoDbSecondaryPartitionKey(indexNames = "area-firstSeenAt-index")
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public Integer getBedrooms() { return bedrooms; }
    public void setBedrooms(Integer bedrooms) { this.bedrooms = bedrooms; }

    public Integer getBathrooms() { return bathrooms; }
    public void setBathrooms(Integer bathrooms) { this.bathrooms = bathrooms; }

    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getPricePerMonth() { return pricePerMonth; }
    public void setPricePerMonth(Integer pricePerMonth) { this.pricePerMonth = pricePerMonth; }

    public Integer getSqft() { return sqft; }
    public void setSqft(Integer sqft) { this.sqft = sqft; }

    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }

    public String getFurnishing() { return furnishing; }
    public void setFurnishing(String furnishing) { this.furnishing = furnishing; }

    public String getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(String availableFrom) { this.availableFrom = availableFrom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public Integer getMatchScore() { return matchScore; }
    public void setMatchScore(Integer matchScore) { this.matchScore = matchScore; }

    @DynamoDbSecondaryPartitionKey(indexNames = "status-firstSeenAt-index")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @DynamoDbSecondarySortKey(indexNames = {"area-firstSeenAt-index", "status-firstSeenAt-index"})
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}
