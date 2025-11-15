package com.example.detectbackupransomware.listener;

import com.example.detectbackupransomware.dto.BackupRequest;
import com.example.detectbackupransomware.service.BackupOrchestrationService;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener de Pub/Sub para recibir comandos de backup automático.
 * Procesa mensajes con formato JSON para iniciar backups.
 * Implementa deduplicación para evitar procesar el mismo backup múltiples veces.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.pubsub.enabled", havingValue = "true", matchIfMissing = true)
public class BackupRequestListener {

    private final BackupOrchestrationService orchestrationService;

    @Value("${app.pubsub.subscription:backup-request-subscription}")
    private String subscriptionName;

    // Deduplicación: almacena IDs de backups procesados
    private static final Set<String> processedBackupIds = ConcurrentHashMap.newKeySet();

    /**
     * Procesa mensajes de Pub/Sub para iniciar backups automáticos.
     * El mensaje debe contener información sobre la base de datos a respaldar.
     */
    public void handleMessage(BasicAcknowledgeablePubsubMessage message) {
        String messageId = message.getPubsubMessage().getMessageId();
        String messageData = message.getPubsubMessage().getData().toStringUtf8();

        log.info("Mensaje de backup recibido de Pub/Sub [ID: {}]", messageId);

        try {
            // Parsear mensaje JSON
            BackupRequest backupRequest = parseBackupRequest(messageData);

            if (backupRequest == null) {
                log.warn("Mensaje con formato inválido [ID: {}]", messageId);
                message.nack();
                return;
            }

            // Verificar deduplicación
            String deduplicationKey = generateDeduplicationKey(backupRequest);
            if (processedBackupIds.contains(deduplicationKey)) {
                log.warn("Backup ya procesado, ignorando mensaje [ID: {}] [Key: {}]", 
                        messageId, deduplicationKey);
                message.ack();
                cleanupOldKeys();
                return;
            }

            log.info("Iniciando proceso de backup [ID: {}] [Database: {}]", 
                    messageId, backupRequest.getDatabaseName());

            // Iniciar proceso de backup
            var result = orchestrationService.startAutomaticBackup(backupRequest);

            // Marcar como procesado
            processedBackupIds.add(deduplicationKey);

            log.info("Proceso de backup completado [ID: {}] [BackupID: {}] [Ransomware: {}]", 
                    messageId, result.getBackupId(), result.getRansomwareDetected());

            // Acknowledge del mensaje
            message.ack();

            // Limpiar claves antiguas
            cleanupOldKeys();

        } catch (Exception e) {
            log.error("Error al procesar mensaje de backup [ID: {}]: {}", 
                    messageId, e.getMessage(), e);
            // No hacer ack para permitir retry
            message.nack();
        }
    }

    /**
     * Parsea el mensaje JSON a BackupRequest.
     */
    private BackupRequest parseBackupRequest(String messageData) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                    new com.fasterxml.jackson.databind.ObjectMapper();
            BackupRequest request = mapper.readValue(messageData, BackupRequest.class);

            // Establecer valores por defecto
            if (request.getRequestedAt() == null) {
                request.setRequestedAt(LocalDateTime.now());
            }

            return request;

        } catch (Exception e) {
            log.error("Error al parsear mensaje JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Genera una clave de deduplicación basada en el request.
     */
    private String generateDeduplicationKey(BackupRequest request) {
        return String.format("%s_%s_%s",
                request.getDatabaseInstance(),
                request.getDatabaseName(),
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );
    }

    /**
     * Limpia claves antiguas para evitar crecimiento de memoria.
     */
    private void cleanupOldKeys() {
        // Mantener solo las últimas 1000 claves
        if (processedBackupIds.size() > 1000) {
            int toRemove = processedBackupIds.size() - 1000;
            var iterator = processedBackupIds.iterator();
            int removed = 0;
            while (iterator.hasNext() && removed < toRemove) {
                iterator.next();
                iterator.remove();
                removed++;
            }
            log.debug("Limpiadas {} claves antiguas de deduplicación", removed);
        }
    }
}

