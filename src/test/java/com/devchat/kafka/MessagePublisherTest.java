package com.devchat.kafka;

import com.devchat.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


class MessagePublisherTest {

    @Test
    @SuppressWarnings("unchecked")
    void publishesJsonKeyedByChannel() throws Exception {

        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        MessagePublisher publisher = new MessagePublisher(template, mapper, "dev-chat");

        ChatMessage msg = ChatMessage.text("dm#alice#bob", "alice", "hello", Instant.parse("2026-08-23T10:15:30Z"));
        publisher.publish(msg);

        String expectedJson = mapper.writeValueAsString(msg);
        verify(template).send(eq("dev-chat"), eq("dm#alice#bob"), eq(expectedJson));

        ChatMessage roundTripped = mapper.readValue(expectedJson, ChatMessage.class);
        assertThat(roundTripped).isEqualTo(msg);
    }
}
