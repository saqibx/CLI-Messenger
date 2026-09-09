package com.devchat.kafka;

import com.devchat.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "devchat.messaging=kafka",
        "devchat.topic=dev-chat",
        "devchat.group-id=devchat-test",
        "devchat.dynamodb.auto-create-tables=false"
})
@EmbeddedKafka(partitions = 1, topics = "dev-chat")
class KafkaRoundTripIT {

    @Autowired
    MessagePublisher publisher;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    ObjectMapper objectMapper;


    @Test
    void messageIsProducedAndConsumed() throws Exception {

        Map<String, Object> props = KafkaTestUtils.consumerProps("verifier", "true", broker);

        try (Consumer<String, String> consumer = consumerFactory.createConsumer("verifier", "test")) {

            consumer.subscribe(java.util.List.of("dev-chat"));
            KafkaTestUtils.getRecords(consumer, java.time.Duration.ofMillis(200));

            ChatMessage sent = ChatMessage.text("dm#alice#bob", "alice", "ship it", Instant.parse("2026-08-23T10:15:30Z"));
            publisher.publish(sent);

            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "dev-chat", java.time.Duration.ofSeconds(10));

            assertThat(record.key()).isEqualTo("dm#alice#bob");

            ChatMessage received = objectMapper.readValue(record.value(), ChatMessage.class);
            assertThat(received).isEqualTo(sent);
        }
    }
}
