package com.londonsearch.repository;

import com.londonsearch.model.Listing;
import com.londonsearch.model.MonitoredSite;
import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

@Component
@Profile({"local", "test"})
@Order(1)
public class TableInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TableInitializer.class);

    private final DynamoDbEnhancedClient enhancedClient;
    private final String propertiesTable;
    private final String listingsTable;
    private final String searchConfigsTable;
    private final String monitoredSitesTable;

    public TableInitializer(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.properties}") String propertiesTable,
            @Value("${app.aws.tables.listings}") String listingsTable,
            @Value("${app.aws.tables.search-configs}") String searchConfigsTable,
            @Value("${app.aws.tables.monitored-sites}") String monitoredSitesTable) {
        this.enhancedClient = enhancedClient;
        this.propertiesTable = propertiesTable;
        this.listingsTable = listingsTable;
        this.searchConfigsTable = searchConfigsTable;
        this.monitoredSitesTable = monitoredSitesTable;
    }

    @Override
    public void run(String... args) {
        createTable(propertiesTable, Property.class);
        createTable(listingsTable, Listing.class);
        createTable(searchConfigsTable, SearchConfig.class);
        createTable(monitoredSitesTable, MonitoredSite.class);
    }

    private <T> void createTable(String tableName, Class<T> beanClass) {
        try {
            DynamoDbTable<T> table = enhancedClient.table(tableName, TableSchema.fromBean(beanClass));
            table.createTable();
            log.info("Created table: {}", tableName);
        } catch (ResourceInUseException e) {
            log.debug("Table already exists: {}", tableName);
        }
    }
}
