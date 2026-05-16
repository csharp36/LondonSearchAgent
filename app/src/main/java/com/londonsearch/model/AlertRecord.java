package com.londonsearch.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.time.Instant;
import java.util.List;

@DynamoDbBean
public class AlertRecord {

    private String id;
    private List<String> propertyIds;
    private String emailSentTo;
    private Instant sentAt;
    private String smartLinkToken;
    private Instant tokenExpiresAt;
    private Integer newPropertyCount;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getPropertyIds() { return propertyIds; }
    public void setPropertyIds(List<String> propertyIds) { this.propertyIds = propertyIds; }

    public String getEmailSentTo() { return emailSentTo; }
    public void setEmailSentTo(String emailSentTo) { this.emailSentTo = emailSentTo; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public String getSmartLinkToken() { return smartLinkToken; }
    public void setSmartLinkToken(String smartLinkToken) { this.smartLinkToken = smartLinkToken; }

    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public Integer getNewPropertyCount() { return newPropertyCount; }
    public void setNewPropertyCount(Integer newPropertyCount) { this.newPropertyCount = newPropertyCount; }
}
