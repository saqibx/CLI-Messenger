package com.devchat.repo;

import com.devchat.model.Conversation;
import com.devchat.model.ConversationMember;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Repository
public class ConversationRepository {

    private final DynamoDbTable<Conversation> conversations;

    private final DynamoDbTable<ConversationMember> members;


    public ConversationRepository(DynamoDbEnhancedClient enhanced, TableNames tables) {
        this.conversations = enhanced.table(tables.conversations(), TableSchema.fromBean(Conversation.class));
        this.members = enhanced.table(tables.conversationMembers(), TableSchema.fromBean(ConversationMember.class));
    }


    public void saveConversation(Conversation conversation) {
        conversations.putItem(conversation);
    }


    public Optional<Conversation> findConversation(String id) {
        Key key = Key.builder().partitionValue(id).build();
        Conversation found = conversations.getItem(key);
        return Optional.ofNullable(found);
    }


    public void saveMember(ConversationMember member) {
        members.putItem(member);
    }


    public boolean isMember(String username, String conversationId) {
        Key key = Key.builder().partitionValue(username).sortValue(conversationId).build();
        ConversationMember found = members.getItem(key);
        return found != null;
    }


    public Optional<ConversationMember> findMember(String username, String conversationId) {
        Key key = Key.builder().partitionValue(username).sortValue(conversationId).build();
        ConversationMember found = members.getItem(key);
        return Optional.ofNullable(found);
    }


    public List<ConversationMember> listMemberships(String username) {
        List<ConversationMember> result = new ArrayList<>();
        for (ConversationMember member : members.query(QueryConditional.keyEqualTo(k -> k.partitionValue(username))).items()) {
            result.add(member);
        }
        return result;
    }


    public void touchLastMessage(String conversationId, List<String> memberUsernames, long timestamp) {
        for (String username : memberUsernames) {
            Optional<ConversationMember> maybe = findMember(username, conversationId);
            if (maybe.isPresent()) {
                ConversationMember m = maybe.get();
                m.setLastMessageAt(timestamp);
                members.putItem(m);
            }
        }
    }
}
