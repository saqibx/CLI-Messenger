package com.devchat.kafka;

import com.devchat.model.ChatMessage;
import com.devchat.model.Conversation;
import com.devchat.model.Message;
import com.devchat.repo.ConversationRepository;
import com.devchat.ws.WsRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
public class ServerMessageListener {

    private static final Logger log = LoggerFactory.getLogger(ServerMessageListener.class);

    private final ObjectMapper objectMapper;

    private final ConversationRepository conversations;

    private final WsRegistry registry;


    public ServerMessageListener(ObjectMapper objectMapper, ConversationRepository conversations,
                                 WsRegistry registry) {
        this.objectMapper = objectMapper;
        this.conversations = conversations;
        this.registry = registry;
    }


    @KafkaListener(topics = "${devchat.topic}", groupId = "${devchat.group-id}")
    public void onMessage(String payload) {

        ChatMessage message;

        try {
            message = objectMapper.readValue(payload, ChatMessage.class);
        } catch (Exception e) {
            log.warn("Skipping malformed message: {}", payload, e);
            return;
        }

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
