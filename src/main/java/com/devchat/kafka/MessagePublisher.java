package com.devchat.kafka;

import com.devchat.model.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
public class MessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper;

    private final String topic;


    public MessagePublisher(KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            @Value("${devchat.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }


    public void publish(ChatMessage message) {

        try {
            String payload = objectMapper.writeValueAsString(message);
            String key = message.conversationId();
            kafkaTemplate.send(topic, key, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chat message", e);
        }
    }
}
