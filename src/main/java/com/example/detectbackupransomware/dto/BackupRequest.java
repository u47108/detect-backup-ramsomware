package com.example.detectbackupransomware.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para solicitud de backup automático.
 * Representa un backup ya exportado desde Cloud SQL a Cloud Storage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupRequest {
    private String databaseInstance;
    private String databaseName;
    private String backupBucket;
    private String backupPrefix;
    private String backupLocation;  // Ubicación del backup en Cloud Storage (gs://bucket/path)
    private LocalDateTime requestedAt;
    private String requestedBy;
}

