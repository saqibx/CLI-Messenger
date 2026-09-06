package com.devchat.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;


@Configuration
public class TopicConfig {

    @Bean
    public NewTopic devChatTopic(@Value("${devchat.topic}") String topic,
                                 @Value("${devchat.topic-partitions:3}") int partitions,
                                 @Value("${devchat.topic-replicas:1}") short replicas) {

        NewTopic newTopic = TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();

        return newTopic;
    }
}
