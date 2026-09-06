package com.devchat.config;

import com.devchat.model.User;
import com.devchat.repo.TableNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.List;
import java.util.Set;


@Component
public class DynamoDbTableInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbTableInitializer.class);

    private final DynamoDbClient client;
    private final DynamoDbProperties props;
    private final TableNames tables;


    public DynamoDbTableInitializer(DynamoDbClient client, DynamoDbProperties props, TableNames tables) {
        this.client = client;
        this.props = props;
        this.tables = tables;
    }


    @Override
    public void afterPropertiesSet() {
        if (!props.isAutoCreateTables()) {
            return;
        }

        Set<String> existing = Set.copyOf(client.listTables().tableNames());

        GlobalSecondaryIndex emailIndex = GlobalSecondaryIndex.builder()
                .indexName(User.EMAIL_INDEX)
                .keySchema(KeySchemaElement.builder().attributeName("email").keyType(KeyType.HASH).build())
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();

        createIfMissing(existing, tables.users(),
                List.of(hashKey("username")),
                List.of(attr("username"), attr("email")),
                List.of(emailIndex));

        createIfMissing(existing, tables.contacts(),
                List.of(hashKey("owner"), rangeKey("contact")),
                List.of(attr("owner"), attr("contact")), List.of());

        createIfMissing(existing, tables.conversations(),
                List.of(hashKey("id")),
                List.of(attr("id")), List.of());

        createIfMissing(existing, tables.conversationMembers(),
                List.of(hashKey("username"), rangeKey("conversationId")),
                List.of(attr("username"), attr("conversationId")), List.of());

        createIfMissing(existing, tables.messages(),
                List.of(hashKey("conversationId"), rangeKey("sortKey")),
                List.of(attr("conversationId"), attr("sortKey")), List.of());

        createIfMissing(existing, tables.files(),
                List.of(hashKey("id")),
                List.of(attr("id")), List.of());
    }


    private void createIfMissing(Set<String> existing, String name,
                                 List<KeySchemaElement> keySchema,
                                 List<AttributeDefinition> attrs,
                                 List<GlobalSecondaryIndex> gsis) {
        if (existing.contains(name)) {
            return;
        }

        log.info("Creating DynamoDB table {}", name);

        CreateTableRequest.Builder req = CreateTableRequest.builder()
                .tableName(name)
                .keySchema(keySchema)
                .attributeDefinitions(attrs)
                .billingMode(BillingMode.PAY_PER_REQUEST);

        if (!gsis.isEmpty()) {
            req.globalSecondaryIndexes(gsis);
        }

        client.createTable(req.build());
        client.waiter().waitUntilTableExists(b -> b.tableName(name));
    }


    private static KeySchemaElement hashKey(String name) {
        return KeySchemaElement.builder().attributeName(name).keyType(KeyType.HASH).build();
    }


    private static KeySchemaElement rangeKey(String name) {
        return KeySchemaElement.builder().attributeName(name).keyType(KeyType.RANGE).build();
    }


    private static AttributeDefinition attr(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }
}
