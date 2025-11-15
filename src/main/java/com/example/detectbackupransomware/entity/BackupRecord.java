package com.example.detectbackupransomware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad para registrar backups y resultados de detección.
 */
@Entity
@Table(name = "backup_records", indexes = {
    @Index(name = "idx_backup_created_at", columnList = "created_at"),
    @Index(name = "idx_backup_status", columnList = "status"),
    @Index(name = "idx_backup_ransomware_detected", columnList = "ransomware_detected")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "backup_id", nullable = false, unique = true, length = 100)
    private String backupId;

    @Column(name = "database_instance", length = 255)
    private String databaseInstance;

    @Column(name = "database_name", length = 100)
    private String databaseName;

    @Column(name = "backup_location", length = 500)
    private String backupLocation;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private BackupStatus status;

    @Column(name = "ransomware_detected")
    private Boolean ransomwareDetected;

    @Column(name = "threat_details", columnDefinition = "TEXT")
    private String threatDetails;

    @Column(name = "database_compromised")
    private Boolean databaseCompromised;

    @Column(name = "previous_backup_restored")
    private Boolean previousBackupRestored;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = BackupStatus.PENDING;
        }
    }

    public enum BackupStatus {
        PENDING,
        EXPORTING,
        INSPECTING,
        VERIFYING,
        COMPLETED,
        FAILED,
        CANCELLED,
        RESTORED
    }
}

