package com.londonsearch.infra;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.*;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

public class DataStack extends Stack {

    private final TableV2 propertiesTable;
    private final TableV2 listingsTable;
    private final TableV2 searchConfigsTable;
    private final TableV2 monitoredSitesTable;
    private final Bucket imagesBucket;

    public DataStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        this.propertiesTable = TableV2.Builder.create(this, "Properties")
                .tableName("Properties")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billing(Billing.onDemand())
                .pointInTimeRecovery(true)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        propertiesTable.addGlobalSecondaryIndex(GlobalSecondaryIndexPropsV2.builder()
                .indexName("area-firstSeenAt-index")
                .partitionKey(Attribute.builder().name("area").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("firstSeenAt").type(AttributeType.STRING).build())
                .build());

        propertiesTable.addGlobalSecondaryIndex(GlobalSecondaryIndexPropsV2.builder()
                .indexName("status-firstSeenAt-index")
                .partitionKey(Attribute.builder().name("status").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("firstSeenAt").type(AttributeType.STRING).build())
                .build());

        this.listingsTable = TableV2.Builder.create(this, "Listings")
                .tableName("Listings")
                .partitionKey(Attribute.builder().name("propertyId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("siteListingId").type(AttributeType.STRING).build())
                .billing(Billing.onDemand())
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        this.searchConfigsTable = TableV2.Builder.create(this, "SearchConfigs")
                .tableName("SearchConfigs")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billing(Billing.onDemand())
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        this.monitoredSitesTable = TableV2.Builder.create(this, "MonitoredSites")
                .tableName("MonitoredSites")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billing(Billing.onDemand())
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        this.imagesBucket = Bucket.Builder.create(this, "Images")
                .versioned(false)
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();
    }

    public TableV2 getPropertiesTable() { return propertiesTable; }
    public TableV2 getListingsTable() { return listingsTable; }
    public TableV2 getSearchConfigsTable() { return searchConfigsTable; }
    public TableV2 getMonitoredSitesTable() { return monitoredSitesTable; }
    public Bucket getImagesBucket() { return imagesBucket; }
}
