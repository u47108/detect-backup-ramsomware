package com.example.detectbackupransomware.config;

import com.example.detectbackupransomware.listener.BackupRequestListener;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;

/**
 * Configuración de Pub/Sub para recibir mensajes de backup.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.pubsub.enabled", havingValue = "true", matchIfMissing = true)
public class PubSubConfig {

    @Value("${app.pubsub.subscription:backup-request-subscription}")
    private String subscriptionName;

    @Bean
    public MessageChannel pubsubInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter messageChannelAdapter(
            MessageChannel pubsubInputChannel,
            PubSubTemplate pubSubTemplate) {
        
        PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(
                pubSubTemplate, subscriptionName);
        adapter.setOutputChannel(pubsubInputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        adapter.setPayloadType(String.class);

        log.info("Pub/Sub listener configurado para suscripción: {}", subscriptionName);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "pubsubInputChannel")
    public PubSubMessageHandler messageReceiver(BackupRequestListener listener) {
        return new PubSubMessageHandler(listener);
    }

    /**
     * Handler para procesar mensajes de Pub/Sub.
     */
    @RequiredArgsConstructor
    private static class PubSubMessageHandler {
        private final BackupRequestListener listener;

        public void handleMessage(BasicAcknowledgeablePubsubMessage message) {
            listener.handleMessage(message);
        }
    }
}

