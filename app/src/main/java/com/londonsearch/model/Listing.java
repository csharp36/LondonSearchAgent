package com.londonsearch.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.time.Instant;
import java.util.List;

@DynamoDbBean
public class Listing {

    private String propertyId;
    private String siteListingId;
    private String siteName;
    private String siteUrl;
    private String originalPrice;
    private String originalAddress;
    private String listingUrl;
    private List<String> imageUrls;
    private String floorPlanUrl;
    private String agentName;
    private String agentPhone;
    private String agentEmail;
    private Instant scrapedAt;

    @DynamoDbPartitionKey
    public String getPropertyId() { return propertyId; }
    public void setPropertyId(String propertyId) { this.propertyId = propertyId; }

    @DynamoDbSortKey
    public String getSiteListingId() { return siteListingId; }
    public void setSiteListingId(String siteListingId) { this.siteListingId = siteListingId; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
    public String getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(String originalPrice) { this.originalPrice = originalPrice; }
    public String getOriginalAddress() { return originalAddress; }
    public void setOriginalAddress(String originalAddress) { this.originalAddress = originalAddress; }
    public String getListingUrl() { return listingUrl; }
    public void setListingUrl(String listingUrl) { this.listingUrl = listingUrl; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public String getFloorPlanUrl() { return floorPlanUrl; }
    public void setFloorPlanUrl(String floorPlanUrl) { this.floorPlanUrl = floorPlanUrl; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentPhone() { return agentPhone; }
    public void setAgentPhone(String agentPhone) { this.agentPhone = agentPhone; }
    public String getAgentEmail() { return agentEmail; }
    public void setAgentEmail(String agentEmail) { this.agentEmail = agentEmail; }
    public Instant getScrapedAt() { return scrapedAt; }
    public void setScrapedAt(Instant scrapedAt) { this.scrapedAt = scrapedAt; }
}
