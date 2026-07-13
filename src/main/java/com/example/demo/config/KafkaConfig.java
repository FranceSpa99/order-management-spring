package com.example.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic orderCreatedTopic(
            @Value("${app.kafka.topics.order-created.name}") String name,
            @Value("${app.kafka.topics.order-created.partitions}") int partitions,
            @Value("${app.kafka.topics.order-created.replication-factor}") short replicationFactor) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic orderStatusChangedTopic(
            @Value("${app.kafka.topics.status-changed.name}") String name,
            @Value("${app.kafka.topics.status-changed.partitions}") int partitions,
            @Value("${app.kafka.topics.status-changed.replication-factor}") short replicationFactor) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }
}
