package com.londonsearch.repository;

import com.londonsearch.model.SearchConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;

import java.util.List;
import java.util.Optional;

@Repository
public class SearchConfigRepository {

    private final DynamoDbTable<SearchConfig> table;

    public SearchConfigRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.search-configs}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(SearchConfig.class));
    }

    public void save(SearchConfig config) { table.putItem(config); }

    public Optional<SearchConfig> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id).build()));
    }

    public List<SearchConfig> findAll() {
        return table.scan().items().stream().toList();
    }

    public void delete(String id) {
        table.deleteItem(Key.builder().partitionValue(id).build());
    }
}
