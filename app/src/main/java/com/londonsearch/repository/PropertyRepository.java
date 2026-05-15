package com.londonsearch.repository;

import com.londonsearch.model.Property;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;

@Repository
public class PropertyRepository {

    private final DynamoDbTable<Property> table;

    public PropertyRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.properties}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Property.class));
    }

    public void save(Property property) {
        table.putItem(property);
    }

    public Optional<Property> findById(String id) {
        Key key = Key.builder().partitionValue(id).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<Property> findAll() {
        return table.scan().items().stream().toList();
    }

    public List<Property> findByArea(String area) {
        DynamoDbIndex<Property> index = table.index("area-firstSeenAt-index");
        QueryConditional condition = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(area).build());
        return index.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    public List<Property> findByStatus(String status) {
        DynamoDbIndex<Property> index = table.index("status-firstSeenAt-index");
        QueryConditional condition = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(status).build());
        return index.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    public void delete(String id) {
        Key key = Key.builder().partitionValue(id).build();
        table.deleteItem(key);
    }
}
