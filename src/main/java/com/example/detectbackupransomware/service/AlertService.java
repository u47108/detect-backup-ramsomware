package com.example.detectbackupransomware.service;

import com.example.detectbackupransomware.dto.BackupResult;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Servicio para generar alertas mediante Cloud Monitoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    @Value("${gcp.monitoring.project-id:${GOOGLE_CLOUD_PROJECT}}")
    private String projectId;

    @Value("${app.alerts.enabled:true}")
    private boolean alertsEnabled;

    /**
     * Genera una alerta sobre posible ataque de ransomware.
     */
    public void sendRansomwareAlert(BackupResult backupResult) {
        if (!alertsEnabled) {
            log.debug("Alertas deshabilitadas, no se enviará alerta");
            return;
        }

        log.warn("🚨 Generando alerta de ransomware [BackupID: {}]", backupResult.getBackupId());

        try {
            String alertMessage = String.format(
                    "⚠️ RANSOMWARE DETECTADO EN BACKUP\n" +
                    "Backup ID: %s\n" +
                    "Database: %s\n" +
                    "Threat Details: %s\n" +
                    "Database Compromised: %s\n" +
                    "Time: %s",
                    backupResult.getBackupId(),
                    backupResult.getBackupLocation() != null ? extractDatabaseName(backupResult.getBackupLocation()) : "Unknown",
                    backupResult.getThreatDetails() != null ? backupResult.getThreatDetails() : "Unknown threat",
                    backupResult.getDatabaseCompromised() != null && backupResult.getDatabaseCompromised() ? "YES" : "NO",
                    backupResult.getBackupDate()
            );

            // Enviar a Cloud Monitoring
            sendToCloudMonitoring(backupResult, alertMessage);

            // También loguear para Cloud Logging
            log.error("RANSOMWARE ALERT: {}", alertMessage);

        } catch (Exception e) {
            log.error("Error al enviar alerta de ransomware: {}", e.getMessage(), e);
        }
    }

    /**
     * Notifica sobre posible manipulación de datos.
     */
    public void sendDataManipulationAlert(BackupResult backupResult) {
        if (!alertsEnabled) {
            return;
        }

        log.warn("Generando alerta de manipulación de datos [BackupID: {}]", backupResult.getBackupId());

        try {
            String alertMessage = String.format(
                    "⚠️ POSIBLE MANIPULACIÓN DE DATOS DETECTADA\n" +
                    "Backup ID: %s\n" +
                    "Database: %s\n" +
                    "Details: %s",
                    backupResult.getBackupId(),
                    extractDatabaseName(backupResult.getBackupLocation()),
                    backupResult.getThreatDetails()
            );

            sendToCloudMonitoring(backupResult, alertMessage);
            log.error("DATA MANIPULATION ALERT: {}", alertMessage);

        } catch (Exception e) {
            log.error("Error al enviar alerta de manipulación: {}", e.getMessage(), e);
        }
    }

    /**
     * Confirma que la base de datos está comprometida.
     */
    public void confirmDatabaseCompromised(BackupResult backupResult) {
        if (!alertsEnabled) {
            return;
        }

        log.error("🚨 CONFIRMANDO: Base de datos comprometida [BackupID: {}]", backupResult.getBackupId());

        try {
            String alertMessage = String.format(
                    "🚨 BASE DE DATOS COMPROMETIDA CONFIRMADA\n" +
                    "Backup ID: %s\n" +
                    "Restoration: %s\n" +
                    "Action Required: Immediate intervention",
                    backupResult.getBackupId(),
                    backupResult.getPreviousBackupRestored() != null && backupResult.getPreviousBackupRestored() ? 
                            "Previous backup restored" : "Restoration failed"
            );

            sendToCloudMonitoring(backupResult, alertMessage);
            log.error("DATABASE COMPROMISED CONFIRMATION: {}", alertMessage);

        } catch (Exception e) {
            log.error("Error al confirmar compromiso de base de datos: {}", e.getMessage(), e);
        }
    }

    /**
     * Confirma que los datos no están comprometidos.
     */
    public void confirmDataNotCompromised(BackupResult backupResult) {
        log.info("Confirmando que los datos no están comprometidos [BackupID: {}]", 
                backupResult.getBackupId());

        // No necesitamos alertar en este caso, solo loguear
        log.info("Data integrity confirmed for backup: {}", backupResult.getBackupId());
    }

    /**
     * Envía alerta a Cloud Monitoring.
     */
    private void sendToCloudMonitoring(BackupResult backupResult, String message) {
        if (projectId == null || projectId.isEmpty()) {
            log.debug("Project ID no configurado, omitiendo Cloud Monitoring");
            return;
        }

        try (MetricServiceClient metricClient = MetricServiceClient.create()) {
            // Crear una métrica custom para alertas de ransomware
            String metricType = "custom.googleapis.com/backup/ransomware_detected";

            Point point = Point.newBuilder()
                    .setInterval(TimeInterval.newBuilder()
                            .setEndTime(com.google.protobuf.Timestamp.newBuilder()
                                    .setSeconds(java.time.Instant.now().getEpochSecond())
                                    .build())
                            .build())
                    .setValue(TypedValue.newBuilder().setBoolValue(true).build())
                    .build();

            TimeSeries timeSeries = TimeSeries.newBuilder()
                    .setMetric(Metric.newBuilder()
                            .setType(metricType)
                            .putAllLabels(createLabels(backupResult))
                            .build())
                    .addPoints(point)
                    .build();

            ProjectName name = ProjectName.of(projectId);
            CreateTimeSeriesRequest request = CreateTimeSeriesRequest.newBuilder()
                    .setName(name.toString())
                    .addTimeSeries(timeSeries)
                    .build();

            metricClient.createTimeSeries(request);

            log.info("Alerta enviada a Cloud Monitoring [Metric: {}]", metricType);

        } catch (Exception e) {
            log.warn("No se pudo enviar a Cloud Monitoring (puede requerir configuración): {}", 
                    e.getMessage());
        }
    }

    /**
     * Crea labels para la métrica de Cloud Monitoring.
     */
    private Map<String, String> createLabels(BackupResult backupResult) {
        Map<String, String> labels = new HashMap<>();
        labels.put("backup_id", backupResult.getBackupId());
        labels.put("ransomware_detected", String.valueOf(backupResult.getRansomwareDetected()));
        labels.put("database_compromised", String.valueOf(backupResult.getDatabaseCompromised()));
        if (backupResult.getThreatDetails() != null) {
            labels.put("threat_type", backupResult.getThreatDetails().length() > 100 ?
                    backupResult.getThreatDetails().substring(0, 100) : backupResult.getThreatDetails());
        }
        return labels;
    }

    /**
     * Extrae el nombre de la base de datos de la ubicación del backup.
     */
    private String extractDatabaseName(String backupLocation) {
        if (backupLocation == null) {
            return "Unknown";
        }
        // Extraer nombre de base de datos del path
        String[] parts = backupLocation.split("/");
        for (String part : parts) {
            if (part.contains("_") && !part.contains("gs://")) {
                return part.split("_")[0];
            }
        }
        return "Unknown";
    }
}

