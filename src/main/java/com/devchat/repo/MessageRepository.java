package com.devchat.repo;

import com.devchat.model.Message;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.ArrayList;
import java.util.List;


@Repository
public class MessageRepository {

    private final DynamoDbTable<Message> table;


    public MessageRepository(DynamoDbEnhancedClient enhanced, TableNames tables) {
        this.table = enhanced.table(tables.messages(), TableSchema.fromBean(Message.class));
    }


    public void save(Message message) {
        table.putItem(message);
    }


    public List<Message> recent(String conversationId, int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(conversationId)))
                .scanIndexForward(false)
                .limit(limit)
                .build();

        List<Message> all = new ArrayList<>();
        for (Message message : table.query(request).items()) {
            if (all.size() >= limit) {
                break;
            }
            all.add(message);
        }

        List<Message> ordered = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            ordered.add(all.get(i));
        }
        return ordered;
    }
}
