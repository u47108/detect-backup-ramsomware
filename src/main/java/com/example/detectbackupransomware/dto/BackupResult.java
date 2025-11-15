package com.example.detectbackupransomware.dto;

import com.example.detectbackupransomware.entity.BackupStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para resultado del proceso de backup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupResult {
    private String backupId;
    private BackupStatus status;
    private String backupLocation;
    private LocalDateTime backupDate;
    private Boolean ransomwareDetected;
    private String threatDetails;
    private Boolean databaseCompromised;
    private Boolean previousBackupRestored;
    private String message;
}

