package com.devchat.kafka;

import com.devchat.messaging.MessageBus;
import com.devchat.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
@ConditionalOnProperty(name = "devchat.messaging", havingValue = "kafka")
public class ServerMessageListener {

    private static final Logger log = LoggerFactory.getLogger(ServerMessageListener.class);

    private final ObjectMapper objectMapper;

    private final MessageBus bus;


    public ServerMessageListener(ObjectMapper objectMapper, MessageBus bus) {
        this.objectMapper = objectMapper;
        this.bus = bus;
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

        bus.deliver(message);
    }
}
