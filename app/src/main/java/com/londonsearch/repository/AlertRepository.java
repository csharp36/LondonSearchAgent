package com.londonsearch.repository;

import com.londonsearch.model.AlertRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AlertRepository {

    private final DynamoDbTable<AlertRecord> table;

    public AlertRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.aws.tables.alerts}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(AlertRecord.class));
    }

    public void save(AlertRecord alertRecord) {
        table.putItem(alertRecord);
    }

    public Optional<AlertRecord> findById(String id) {
        Key key = Key.builder().partitionValue(id).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public Optional<AlertRecord> findByToken(String token) {
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("smartLinkToken = :token")
                        .expressionValues(Map.of(":token", AttributeValue.fromS(token)))
                        .build())
                .build();

        return table.scan(scanRequest).items().stream().findFirst();
    }

    public List<AlertRecord> findAll() {
        return table.scan().items().stream().toList();
    }
}
