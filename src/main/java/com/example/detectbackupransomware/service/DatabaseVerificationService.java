package com.example.detectbackupransomware.service;

import com.example.detectbackupransomware.dto.BackupResult;
import com.example.detectbackupransomware.entity.BackupRecord;
import com.example.detectbackupransomware.repository.BackupRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Servicio para verificar cambios inesperados en la base de datos Cloud SQL.
 * Compara con backups anteriores para detectar modificaciones sospechosas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseVerificationService {

    private final BackupRecordRepository backupRecordRepository;

    /**
     * Verifica si hay cambios inesperados en la base de datos.
     * Compara con backups anteriores para detectar modificaciones masivas sospechosas.
     */
    public BackupResult verifyDatabaseChanges(BackupResult backupResult, String databaseName) {
        log.info("Verificando cambios en la base de datos [BackupID: {}] [Database: {}]", 
                backupResult.getBackupId(), databaseName);

        try {
            // Obtener backup anterior exitoso
            var previousBackups = backupRecordRepository.findLatestSuccessfulBackups(databaseName);

            if (previousBackups.isEmpty()) {
                log.info("No hay backups anteriores para comparar [BackupID: {}]", backupResult.getBackupId());
                // Si no hay backups anteriores, no podemos detectar cambios
                backupResult.setDatabaseCompromised(false);
                backupResult.setStatus(BackupRecord.BackupStatus.VERIFYING);
                return backupResult;
            }

            // Verificar si hay cambios sospechosos
            // En producción, compararías el contenido de los backups
            boolean unexpectedChanges = detectUnexpectedChanges(backupResult, previousBackups);

            if (unexpectedChanges) {
                log.error("⚠️ CAMBIOS INESPERADOS DETECTADOS en base de datos [BackupID: {}]", 
                        backupResult.getBackupId());
                backupResult.setDatabaseCompromised(true);
            } else {
                log.info("✅ No se detectaron cambios inesperados [BackupID: {}]", 
                        backupResult.getBackupId());
                backupResult.setDatabaseCompromised(false);
            }

            backupResult.setStatus(BackupRecord.BackupStatus.VERIFYING);

        } catch (Exception e) {
            log.error("Error al verificar cambios en base de datos [BackupID: {}]: {}", 
                    backupResult.getBackupId(), e.getMessage());
            // Por seguridad, marcar como comprometida en caso de error
            backupResult.setDatabaseCompromised(true);
            backupResult.setThreatDetails(
                    (backupResult.getThreatDetails() != null ? backupResult.getThreatDetails() + " " : "") +
                    "Error en verificación: " + e.getMessage()
            );
        }

        return backupResult;
    }

    /**
     * Detecta cambios inesperados comparando con backups anteriores.
     * En producción, esto compararía el tamaño, estructura, y contenido de los backups.
     */
    private boolean detectUnexpectedChanges(BackupResult currentBackup, 
                                           java.util.List<BackupRecord> previousBackups) {
        if (previousBackups.isEmpty()) {
            return false;
        }

        BackupRecord lastBackup = previousBackups.get(0);

        // Verificar si el tiempo entre backups es sospechosamente largo o corto
        LocalDateTime timeDiff = currentBackup.getBackupDate();
        LocalDateTime lastBackupTime = lastBackup.getCreatedAt();

        // Si el último backup fue hace menos de 1 hora, podría ser sospechoso
        // (indicaría actividad anormal)
        long hoursSinceLastBackup = java.time.Duration.between(lastBackupTime, timeDiff).toHours();

        if (hoursSinceLastBackup < 1) {
            log.warn("Backup realizado muy pronto después del anterior [Hours: {}]", hoursSinceLastBackup);
            return true;
        }

        // Si el último backup también tenía ransomware detectado
        if (Boolean.TRUE.equals(lastBackup.getRansomwareDetected())) {
            log.warn("Backup anterior también tenía ransomware detectado");
            return true;
        }

        return false;
    }
}

