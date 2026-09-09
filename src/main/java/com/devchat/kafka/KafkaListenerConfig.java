package com.devchat.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;


@Configuration
@ConditionalOnProperty(name = "devchat.messaging", havingValue = "kafka")
public class KafkaListenerConfig {

    @Bean
    public ContainerCustomizer<String, String, ConcurrentMessageListenerContainer<String, String>> daemonListenerCustomizer() {

        return container -> {
            SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("devchat-kafka-");
            executor.setDaemon(true);
            container.getContainerProperties().setListenerTaskExecutor(executor);
        };
    }
}
