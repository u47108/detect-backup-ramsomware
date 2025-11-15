package com.example.detectbackupransomware.service;

import com.example.detectbackupransomware.dto.BackupResult;
import com.example.detectbackupransomware.entity.BackupRecord;
import com.example.detectbackupransomware.repository.BackupRecordRepository;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio para restaurar backups anteriores cuando se detecta compromiso.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupRestorationService {

    private final BackupRecordRepository backupRecordRepository;
    private final Storage storage;

    /**
     * Restaura el backup anterior más reciente que no esté comprometido.
     */
    public BackupResult restorePreviousBackup(BackupResult currentBackup, String databaseName) {
        log.warn("Iniciando restauración de backup anterior [CurrentBackupID: {}] [Database: {}]", 
                currentBackup.getBackupId(), databaseName);

        try {
            // Buscar backups anteriores que no estén comprometidos
            List<BackupRecord> previousBackups = backupRecordRepository
                    .findLatestSuccessfulBackups(databaseName);

            BackupRecord backupToRestore = previousBackups.stream()
                    .filter(b -> Boolean.FALSE.equals(b.getRansomwareDetected()))
                    .filter(b -> Boolean.FALSE.equals(b.getDatabaseCompromised()))
                    .filter(b -> b.getStatus() == BackupRecord.BackupStatus.COMPLETED)
                    .findFirst()
                    .orElse(null);

            if (backupToRestore == null) {
                log.error("No se encontró backup anterior seguro para restaurar [Database: {}]", databaseName);
                currentBackup.setPreviousBackupRestored(false);
                currentBackup.setMessage("No se encontró backup anterior seguro para restaurar");
                return currentBackup;
            }

            log.info("Restaurando backup anterior [BackupID: {}] [Location: {}]", 
                    backupToRestore.getBackupId(), backupToRestore.getBackupLocation());

            // En producción, esto usaría Cloud SQL Admin API para restaurar
            boolean restored = performRestore(backupToRestore);

            if (restored) {
                log.info("✅ Backup anterior restaurado exitosamente [BackupID: {}]", 
                        backupToRestore.getBackupId());
                
                // Marcar backup anterior como restaurado
                backupToRestore.setStatus(BackupRecord.BackupStatus.RESTORED);
                backupRecordRepository.save(backupToRestore);

                currentBackup.setPreviousBackupRestored(true);
                currentBackup.setStatus(BackupRecord.BackupStatus.CANCELLED);
                currentBackup.setMessage(String.format(
                        "Backup actual cancelado. Backup anterior restaurado: %s",
                        backupToRestore.getBackupId()
                ));
            } else {
                log.error("Error al restaurar backup anterior [BackupID: {}]", 
                        backupToRestore.getBackupId());
                currentBackup.setPreviousBackupRestored(false);
                currentBackup.setMessage("Error al restaurar backup anterior");
            }

        } catch (Exception e) {
            log.error("Error al restaurar backup anterior [BackupID: {}]: {}", 
                    currentBackup.getBackupId(), e.getMessage());
            currentBackup.setPreviousBackupRestored(false);
            currentBackup.setMessage("Error al restaurar backup anterior: " + e.getMessage());
        }

        return currentBackup;
    }

    /**
     * Realiza la restauración del backup.
     * En producción, esto usaría Cloud SQL Admin API.
     */
    private boolean performRestore(BackupRecord backupRecord) {
        try {
            // Verificar que el backup existe en Cloud Storage
            String[] gsPath = backupRecord.getBackupLocation().replace("gs://", "").split("/", 2);
            if (gsPath.length != 2) {
                log.error("Formato de ubicación de backup inválido: {}", backupRecord.getBackupLocation());
                return false;
            }

            String bucketName = gsPath[0];
            String objectName = gsPath[1];

            var blob = storage.get(bucketName, objectName);
            if (blob == null || !blob.exists()) {
                log.error("Backup no encontrado en Cloud Storage: {}", backupRecord.getBackupLocation());
                return false;
            }

            log.info("Backup encontrado en Cloud Storage, procediendo con restauración [Size: {} bytes]", 
                    blob.getSize());

            // En producción, aquí usarías Cloud SQL Admin API:
            // InstancesSqlServiceClient.restoreBackup(...)

            // Simulación de restauración exitosa
            return true;

        } catch (Exception e) {
            log.error("Error al restaurar backup: {}", e.getMessage());
            return false;
        }
    }
}

