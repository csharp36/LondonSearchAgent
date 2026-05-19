package com.londonsearch.repository;

import com.londonsearch.model.Listing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;

@Repository
public class ListingRepository {

    private final DynamoDbTable<Listing> table;

    public ListingRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.listings}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Listing.class));
    }

    public void save(Listing listing) {
        table.putItem(listing);
    }

    public List<Listing> findByPropertyId(String propertyId) {
        QueryConditional condition = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(propertyId).build());
        return table.query(condition).items().stream().toList();
    }

    public Optional<Listing> findByPropertyIdAndSiteListingId(String propertyId, String siteListingId) {
        Key key = Key.builder().partitionValue(propertyId).sortValue(siteListingId).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public void delete(String propertyId, String siteListingId) {
        Key key = Key.builder().partitionValue(propertyId).sortValue(siteListingId).build();
        table.deleteItem(key);
    }

    public void deleteAll() {
        table.scan().items().forEach(l -> delete(l.getPropertyId(), l.getSiteListingId()));
    }
}
