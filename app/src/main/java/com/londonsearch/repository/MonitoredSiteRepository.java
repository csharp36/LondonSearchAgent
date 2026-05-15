package com.londonsearch.repository;

import com.londonsearch.model.MonitoredSite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;

import java.util.List;
import java.util.Optional;

@Repository
public class MonitoredSiteRepository {

    private final DynamoDbTable<MonitoredSite> table;

    public MonitoredSiteRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.monitored-sites}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(MonitoredSite.class));
    }

    public void save(MonitoredSite site) { table.putItem(site); }

    public Optional<MonitoredSite> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id).build()));
    }

    public List<MonitoredSite> findAll() {
        return table.scan().items().stream().toList();
    }

    public void delete(String id) {
        table.deleteItem(Key.builder().partitionValue(id).build());
    }
}
