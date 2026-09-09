package com.devchat.messaging;

import com.devchat.kafka.MessagePublisher;
import com.devchat.model.ChatMessage;
import com.devchat.model.Conversation;
import com.devchat.model.Message;
import com.devchat.repo.ConversationRepository;
import com.devchat.ws.WsRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;


@Service
public class MessageBus {

    private final ObjectMapper objectMapper;

    private final ConversationRepository conversations;

    private final WsRegistry registry;

    private final MessagePublisher kafkaPublisher;

    private final boolean useKafka;


    public MessageBus(ObjectMapper objectMapper, ConversationRepository conversations,
                      WsRegistry registry, @Nullable MessagePublisher kafkaPublisher,
                      @Value("${devchat.messaging:inprocess}") String mode) {

        this.objectMapper = objectMapper;
        this.conversations = conversations;
        this.registry = registry;
        this.kafkaPublisher = kafkaPublisher;
        this.useKafka = "kafka".equalsIgnoreCase(mode);
    }


    public void publish(ChatMessage message) {

        if (useKafka && kafkaPublisher != null) {
            kafkaPublisher.publish(message);
        } else {
            deliver(message);
        }
    }


    public void deliver(ChatMessage message) {

        Conversation conv = conversations.findConversation(message.conversationId()).orElse(null);

        if (conv == null || conv.getMembers() == null) {
            return;
        }

        String event = toEvent(message);

        for (String member : conv.getMembers()) {
            registry.sendToUser(member, event);
        }
    }


    private String toEvent(ChatMessage message) {

        boolean isFile = Message.KIND_FILE.equals(message.kind());

        ObjectNode node = objectMapper.createObjectNode();

        String type;
        if (isFile) {
            type = "file";
        } else {
            type = "message";
        }
        node.put("type", type);

        node.put("conversationId", message.conversationId());
        node.put("from", message.from());
        node.put("timestamp", message.timestamp().toEpochMilli());

        if (isFile) {
            node.put("fileName", message.fileName());
            node.put("fileId", message.fileId());
        } else {
            node.put("text", message.text());
        }

        return node.toString();
    }
}
