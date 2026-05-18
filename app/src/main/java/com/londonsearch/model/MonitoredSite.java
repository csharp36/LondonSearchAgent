package com.londonsearch.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.time.Instant;
import java.util.Map;

@DynamoDbBean
public class MonitoredSite {

    private String id;
    private String name;
    private String baseUrl;
    private String searchUrlTemplate;
    private String scraperType;
    private Boolean enabled;
    private Instant lastCheckedAt;
    private String lastChangeHash;
    private String tier;
    private Map<String, String> cssSelectors;
    private Instant selectorsGeneratedAt;
    private String selectorsModel;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getSearchUrlTemplate() { return searchUrlTemplate; }
    public void setSearchUrlTemplate(String searchUrlTemplate) { this.searchUrlTemplate = searchUrlTemplate; }
    public String getScraperType() { return scraperType; }
    public void setScraperType(String scraperType) { this.scraperType = scraperType; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public String getLastChangeHash() { return lastChangeHash; }
    public void setLastChangeHash(String lastChangeHash) { this.lastChangeHash = lastChangeHash; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public Map<String, String> getCssSelectors() { return cssSelectors; }
    public void setCssSelectors(Map<String, String> cssSelectors) { this.cssSelectors = cssSelectors; }
    public Instant getSelectorsGeneratedAt() { return selectorsGeneratedAt; }
    public void setSelectorsGeneratedAt(Instant selectorsGeneratedAt) { this.selectorsGeneratedAt = selectorsGeneratedAt; }
    public String getSelectorsModel() { return selectorsModel; }
    public void setSelectorsModel(String selectorsModel) { this.selectorsModel = selectorsModel; }
}
