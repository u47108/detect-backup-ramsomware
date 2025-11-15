package com.example.detectbackupransomware.service;

import com.example.detectbackupransomware.dto.BackupRequest;
import com.example.detectbackupransomware.dto.BackupResult;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Servicio para trabajar con backups exportados desde Cloud SQL a Cloud Storage.
 * Cloud SQL exporta automáticamente a Cloud Storage mediante Cloud SQL Admin API.
 * Este servicio verifica que el backup esté disponible y obtiene su ubicación.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudSqlExportService {

    private final Storage storage;

    @Value("${gcp.storage.backup-bucket:backup-bucket}")
    private String backupBucket;

    @Value("${gcp.storage.backup-prefix:backups/}")
    private String backupPrefix;

    @Value("${gcp.sql.instance-id:}")
    private String instanceId;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * Verifica y procesa un backup exportado desde Cloud SQL a Cloud Storage.
     * Asume que Cloud SQL ya ha exportado el backup mediante Cloud SQL Admin API.
     * El request debe contener la ubicación del backup en Cloud Storage.
     */
    public BackupResult processExportedBackup(BackupRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String datePath = now.format(DATE_FORMATTER);
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        String backupId = request.getBackupPrefix() != null ? 
                request.getBackupPrefix() : UUID.randomUUID().toString();
        
        log.info("Verificando backup exportado desde Cloud SQL [BackupID: {}] [Database: {}]", 
                backupId, request.getDatabaseName());

        try {
            // Determinar ubicación del backup
            // Si request tiene backupLocation, usarla; si no, construirla según patrón esperado
            String backupLocation;
            
            if (request.getBackupBucket() != null && !request.getBackupBucket().isEmpty()) {
                String fileName = String.format("%s%s/%s_%s.sql",
                        request.getBackupPrefix() != null ? request.getBackupPrefix() : backupPrefix,
                        now.format(DATE_FORMATTER),
                        request.getDatabaseName(),
                        now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
                backupLocation = String.format("gs://%s/%s", request.getBackupBucket(), fileName);
            } else {
                // Construir path esperado del backup de Cloud SQL
                // Cloud SQL exporta con formato: gs://bucket/backups/database/timestamp/database.sql.gz
                backupLocation = String.format("gs://%s/%s%s/%s/%s.sql.gz",
                        backupBucket,
                        backupPrefix,
                        now.format(DATE_FORMATTER),
                        request.getDatabaseName(),
                        now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            }

            // Verificar que el backup existe en Cloud Storage
            String[] gsPath = backupLocation.replace("gs://", "").split("/", 2);
            if (gsPath.length != 2) {
                throw new IllegalArgumentException("Formato de ubicación de backup inválido: " + backupLocation);
            }

            var blob = storage.get(gsPath[0], gsPath[1]);
            if (blob == null || !blob.exists()) {
                log.warn("Backup no encontrado en ubicación esperada: {}. Buscando el más reciente...", backupLocation);
                // Buscar el backup más reciente del database
                String latestBackup = findLatestBackup(request.getDatabaseName(), gsPath[0], backupPrefix);
                if (latestBackup == null) {
                    throw new RuntimeException("No se encontró backup en Cloud Storage para la base de datos: " + request.getDatabaseName());
                }
                backupLocation = latestBackup;
                // Actualizar gsPath para obtener el blob
                gsPath = backupLocation.replace("gs://", "").split("/", 2);
                blob = storage.get(gsPath[0], gsPath[1]);
            }

            final long blobSize = (blob != null && blob.exists()) ? blob.getSize() : 0;
            log.info("Backup encontrado [BackupID: {}] [Location: {}] [Size: {} bytes]", 
                    backupId, backupLocation, blobSize);

            return BackupResult.builder()
                    .backupId(backupId)
                    .backupLocation(backupLocation)
                    .backupDate(now)
                    .status(com.example.detectbackupransomware.entity.BackupRecord.BackupStatus.EXPORTING)
                    .ransomwareDetected(false)
                    .databaseCompromised(false)
                    .message("Backup exportado desde Cloud SQL verificado en Cloud Storage")
                    .build();

        } catch (Exception e) {
            log.error("Error al exportar datos [BackupID: {}]: {}", backupId, e.getMessage());
            throw new RuntimeException("Error al exportar datos: " + e.getMessage(), e);
        }
    }

    /**
     * Busca el backup más reciente de una base de datos en Cloud Storage.
     */
    private String findLatestBackup(String databaseName, String bucketName, String prefix) {
        try {
            var blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefix + databaseName),
                    Storage.BlobListOption.sortBy(Storage.BlobField.TIME_CREATED_DESCENDING));

            var iterator = blobs.iterateAll().iterator();
            if (iterator.hasNext()) {
                var latestBlob = iterator.next();
                return String.format("gs://%s/%s", bucketName, latestBlob.getName());
            }
        } catch (Exception e) {
            log.error("Error al buscar backup más reciente: {}", e.getMessage());
        }
        return null;
    }
}

