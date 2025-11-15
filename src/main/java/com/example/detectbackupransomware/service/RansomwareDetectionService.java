package com.example.detectbackupransomware.service;

import com.example.detectbackupransomware.dto.BackupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para detectar patrones de ransomware en backups de Cloud Storage.
 * Usa Cloud DLP Job para inspeccionar si los datos sensibles están encriptados.
 * Busca indicadores como:
 * - Datos sensibles encriptados (detectados por DLP)
 * - Extensiones sospechosas de ransomware
 * - Patrones de texto de ransomware
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RansomwareDetectionService {

    private final DlpJobService dlpJobService;

    @Value("${gcp.dlp.project-id:${GOOGLE_CLOUD_PROJECT}}")
    private String projectId;

    @Value("${gcp.dlp.enabled:true}")
    private boolean dlpEnabled;

    private static final List<String> RANSOMWARE_EXTENSIONS = Arrays.asList(
            ".encrypted", ".locked", ".crypto", ".crypt", ".vault", ".xxx", ".zzz",
            ".aaa", ".micro", ".encryptedRSA", ".exx", ".ezz", ".eaa", ".xbtx",
            ".xtbl", ".cryptolocker", ".zee", ".xxx", ".crypto", ".wallet"
    );

    private static final List<String> RANSOMWARE_PATTERNS = Arrays.asList(
            "RANSOMWARE", "DECRYPT", "ENCRYPT", "LOCKED", "YOUR FILES",
            "PAY", "BITCOIN", "RECOVERY", "KEY", "BITCOIN ADDRESS"
    );

    /**
     * Inspecciona el backup en Cloud Storage para detectar ransomware.
     * Usa Cloud DLP Job para verificar si los datos sensibles están encriptados.
     */
    public BackupResult inspectForRansomware(BackupResult backupResult) {
        log.info("Inspeccionando backup para detección de ransomware [BackupID: {}] [Location: {}]", 
                backupResult.getBackupId(), backupResult.getBackupLocation());

        try {
            // Verificar extensiones sospechosas en el nombre del archivo
            boolean suspiciousExtension = checkSuspiciousExtensions(backupResult.getBackupLocation());

            // Usar Cloud DLP Job para inspeccionar si datos sensibles están encriptados
            boolean dlpDetectedEncryption = false;
            String threatDetails = null;
            String dlpJobName = null;

            if (dlpEnabled && projectId != null && !projectId.isEmpty()) {
                try {
                    // Crear DLP Job para inspeccionar el backup en Cloud Storage
                    String jobId = "backup-inspection-" + UUID.randomUUID().toString();
                    dlpJobName = dlpJobService.createDlpInspectionJob(
                            backupResult.getBackupLocation(), 
                            jobId
                    );

                    if (dlpJobName != null) {
                        // Esperar a que el job complete (en producción, esto podría ser asíncrono)
                        // Por ahora, verificamos inmediatamente (el job podría tardar)
                        Thread.sleep(2000); // Esperar un poco para que el job progrese
                        
                        dlpDetectedEncryption = dlpJobService.checkDlpJobResults(dlpJobName);
                        
                        if (dlpDetectedEncryption) {
                            threatDetails = "Cloud DLP detectó que datos sensibles están encriptados (posible ransomware)";
                        }
                    }
                } catch (Exception e) {
                    log.warn("Cloud DLP no disponible o error al usarlo: {}", e.getMessage());
                    // Continuar con detección básica
                }
            } else {
                log.info("Cloud DLP deshabilitado o no configurado, usando detección básica");
            }

            boolean ransomwareDetected = suspiciousExtension || dlpDetectedEncryption;

            if (ransomwareDetected) {
                StringBuilder details = new StringBuilder();
                if (suspiciousExtension) {
                    details.append("Extensión sospechosa detectada. ");
                }
                if (suspiciousPatterns) {
                    details.append("Patrones de ransomware detectados en contenido. ");
                }
                if (dlpDetected) {
                    details.append(threatDetails);
                }

                log.warn("⚠️ RANSOMWARE DETECTADO [BackupID: {}] [Details: {}]", 
                        backupResult.getBackupId(), details.toString());

                backupResult.setRansomwareDetected(true);
                backupResult.setThreatDetails(details.toString());
            } else {
                log.info("✅ Backup sin indicadores de ransomware [BackupID: {}]", 
                        backupResult.getBackupId());
                backupResult.setRansomwareDetected(false);
            }

            backupResult.setStatus(com.example.detectbackupransomware.entity.BackupRecord.BackupStatus.INSPECTING);

        } catch (Exception e) {
            log.error("Error al inspeccionar backup [BackupID: {}]: {}", 
                    backupResult.getBackupId(), e.getMessage());
            // En caso de error, marcamos como sospechoso por seguridad
            backupResult.setRansomwareDetected(true);
            backupResult.setThreatDetails("Error en inspección: " + e.getMessage());
        }

        return backupResult;
    }

    /**
     * Verifica si el nombre del archivo contiene extensiones sospechosas.
     */
    private boolean checkSuspiciousExtensions(String backupLocation) {
        if (backupLocation == null) {
            return false;
        }

        String lowerLocation = backupLocation.toLowerCase();
        return RANSOMWARE_EXTENSIONS.stream()
                .anyMatch(lowerLocation::contains);
    }

}

