package com.example.detectbackupransomware.service;

import com.example.detectbackupransomware.dto.BackupRequest;
import com.example.detectbackupransomware.dto.BackupResult;
import com.example.detectbackupransomware.entity.BackupRecord;
import com.example.detectbackupransomware.repository.BackupRecordRepository;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Servicio orquestador que coordina todo el proceso de backup y detección de ransomware.
 * Implementa el flujo completo según el diagrama de secuencia.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupOrchestrationService {

    private final CloudSqlExportService exportService;
    private final RansomwareDetectionService detectionService;
    private final DatabaseVerificationService verificationService;
    private final BackupRestorationService restorationService;
    private final AlertService alertService;
    private final BackupRecordRepository backupRecordRepository;
    private final Storage storage;

    /**
     * Inicia el proceso completo de backup automático con detección de ransomware.
     * Sigue el flujo del diagrama de secuencia:
     * 1. Exportar datos de Cloud SQL a Cloud Storage
     * 2. Inspeccionar datos con Cloud DLP
     * 3. Si detecta ransomware: alertar y verificar base de datos
     * 4. Si base de datos comprometida: cancelar backup y restaurar anterior
     * 5. Confirmar resultado al usuario
     */
    @Transactional
    public BackupResult startAutomaticBackup(BackupRequest request) {
        log.info("Iniciando proceso de backup automático [Database: {}] [Instance: {}]", 
                request.getDatabaseName(), request.getDatabaseInstance());

        BackupRecord backupRecord = BackupRecord.builder()
                .backupId(java.util.UUID.randomUUID().toString())
                .databaseInstance(request.getDatabaseInstance())
                .databaseName(request.getDatabaseName())
                .status(BackupRecord.BackupStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        backupRecordRepository.save(backupRecord);

        BackupResult result = BackupResult.builder()
                .backupId(backupRecord.getBackupId())
                .backupDate(LocalDateTime.now())
                .build();

        try {
            // Paso 1: Verificar backup exportado desde Cloud SQL a Cloud Storage
            log.info("Paso 1: Verificando backup exportado desde Cloud SQL a Cloud Storage [BackupID: {}]", 
                    result.getBackupId());
            result = exportService.processExportedBackup(request);
            backupRecord.setStatus(BackupRecord.BackupStatus.EXPORTING);
            backupRecord.setBackupLocation(result.getBackupLocation());
            backupRecordRepository.save(backupRecord);

            // Paso 2: Inspeccionar backup en Cloud Storage con Cloud DLP Job
            log.info("Paso 2: Inspeccionando backup en Cloud Storage con Cloud DLP Job [BackupID: {}]", result.getBackupId());
            result = detectionService.inspectForRansomware(result);
            backupRecord.setRansomwareDetected(result.getRansomwareDetected());
            backupRecord.setThreatDetails(result.getThreatDetails());
            backupRecord.setStatus(BackupRecord.BackupStatus.INSPECTING);
            backupRecordRepository.save(backupRecord);

            // Paso 3: Si detecta ransomware, generar alerta y verificar base de datos
            if (Boolean.TRUE.equals(result.getRansomwareDetected())) {
                log.warn("⚠️ RANSOMWARE DETECTADO - Iniciando procedimiento de respuesta [BackupID: {}]", 
                        result.getBackupId());

                // Generar alerta sobre posible ataque
                alertService.sendRansomwareAlert(result);

                // Notificar sobre posible manipulación de datos
                alertService.sendDataManipulationAlert(result);

                // Paso 4: Verificar cambios en la base de datos
                log.info("Paso 4: Verificando cambios en la base de datos [BackupID: {}]", 
                        result.getBackupId());
                result = verificationService.verifyDatabaseChanges(result, request.getDatabaseName());
                backupRecord.setDatabaseCompromised(result.getDatabaseCompromised());
                backupRecord.setStatus(BackupRecord.BackupStatus.VERIFYING);
                backupRecordRepository.save(backupRecord);

                // Paso 5: Si detecta cambios inesperados, cancelar backup y restaurar anterior
                if (Boolean.TRUE.equals(result.getDatabaseCompromised())) {
                    log.error("⚠️ CAMBIOS INESPERADOS DETECTADOS - Cancelando backup y restaurando anterior [BackupID: {}]", 
                            result.getBackupId());

                    // Confirmar que la base de datos está comprometida
                    alertService.confirmDatabaseCompromised(result);

                    // Cancelar backup actual
                    result.setStatus(BackupRecord.BackupStatus.CANCELLED);
                    backupRecord.setStatus(BackupRecord.BackupStatus.CANCELLED);

                    // Restaurar backup anterior
                    result = restorationService.restorePreviousBackup(result, request.getDatabaseName());
                    backupRecord.setPreviousBackupRestored(result.getPreviousBackupRestored());
                    backupRecordRepository.save(backupRecord);

                    result.setMessage("Backup cancelado debido a detección de ransomware. " +
                            (Boolean.TRUE.equals(result.getPreviousBackupRestored()) ?
                                    "Backup anterior restaurado exitosamente." :
                                    "Error al restaurar backup anterior."));

                } else {
                    // No se detectaron cambios en la base de datos
                    log.info("No se detectaron cambios inesperados en la base de datos [BackupID: {}]", 
                            result.getBackupId());
                    alertService.confirmDataNotCompromised(result);
                    result.setStatus(BackupRecord.BackupStatus.COMPLETED);
                    backupRecord.setStatus(BackupRecord.BackupStatus.COMPLETED);
                    result.setMessage("Ransomware detectado en backup pero la base de datos no está comprometida.");
                }

            } else {
                // No se detectó ransomware
                log.info("✅ Backup completado sin anomalías [BackupID: {}]", result.getBackupId());
                alertService.confirmDataNotCompromised(result);
                result.setStatus(BackupRecord.BackupStatus.COMPLETED);
                backupRecord.setStatus(BackupRecord.BackupStatus.COMPLETED);
                result.setMessage("Backup completado sin anomalías");
            }

            backupRecord.setCompletedAt(LocalDateTime.now());
            backupRecordRepository.save(backupRecord);

            log.info("Proceso de backup finalizado [BackupID: {}] [Status: {}] [Ransomware: {}]", 
                    result.getBackupId(), result.getStatus(), result.getRansomwareDetected());

        } catch (Exception e) {
            log.error("Error en proceso de backup [BackupID: {}]: {}", result.getBackupId(), e.getMessage(), e);
            result.setStatus(BackupRecord.BackupStatus.FAILED);
            result.setMessage("Error en proceso de backup: " + e.getMessage());
            backupRecord.setStatus(BackupRecord.BackupStatus.FAILED);
            backupRecordRepository.save(backupRecord);
        }

        return result;
    }
}

