package com.example.detectbackupransomware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Modelo de datos sensibles que se deben inspeccionar en los backups.
 * Define qué tipos de datos sensibles buscar (emails, datos bancarios, etc.).
 */
@Entity
@Table(name = "sensitive_data_models", indexes = {
    @Index(name = "idx_sensitive_data_type", columnList = "data_type"),
    @Index(name = "idx_sensitive_data_active", columnList = "active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensitiveDataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private SensitiveDataType dataType;

    @Column(name = "table_name", nullable = false, length = 255)
    private String tableName;

    @Column(name = "column_name", nullable = false, length = 255)
    private String columnName;

    @Column(name = "detection_query", columnDefinition = "TEXT")
    private String detectionQuery;

    @Column(name = "dlp_info_type", length = 100)
    private String dlpInfoType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum SensitiveDataType {
        EMAIL,
        PHONE,
        CREDIT_CARD,
        BANK_ACCOUNT,
        SSN,
        PASSPORT,
        DRIVER_LICENSE,
        IP_ADDRESS,
        PASSWORD_HASH,
        API_KEY,
        SECRET_TOKEN,
        PERSONAL_NAME,
        ADDRESS,
        DATE_OF_BIRTH,
        FINANCIAL_DATA
    }
}

