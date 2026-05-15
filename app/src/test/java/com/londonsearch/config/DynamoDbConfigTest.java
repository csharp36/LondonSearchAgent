package com.londonsearch.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DynamoDbConfigTest {

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private DynamoDbEnhancedClient enhancedClient;

    @Test
    void dynamoDbClientBeanExists() {
        assertThat(dynamoDbClient).isNotNull();
    }

    @Test
    void enhancedClientBeanExists() {
        assertThat(enhancedClient).isNotNull();
    }
}
