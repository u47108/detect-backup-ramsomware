package com.example.detectbackupransomware.service;

import com.example.detectbackupransomware.dto.BackupRequest;
import com.example.detectbackupransomware.dto.BackupResult;
import com.example.detectbackupransomware.entity.BackupRecord;
import com.example.detectbackupransomware.repository.BackupRecordRepository;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BackupOrchestrationService Tests")
class BackupOrchestrationServiceTest {

    @Mock
    private CloudSqlExportService exportService;

    @Mock
    private RansomwareDetectionService detectionService;

    @Mock
    private DatabaseVerificationService verificationService;

    @Mock
    private BackupRestorationService restorationService;

    @Mock
    private AlertService alertService;

    @Mock
    private BackupRecordRepository backupRecordRepository;

    @Mock
    private Storage storage;

    @InjectMocks
    private BackupOrchestrationService orchestrationService;

    private BackupRequest backupRequest;

    @BeforeEach
    void setUp() {
        backupRequest = BackupRequest.builder()
                .databaseInstance("test-instance")
                .databaseName("test-db")
                .backupBucket("test-bucket")
                .backupPrefix("backups/")
                .requestedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should complete backup successfully without ransomware")
    void testSuccessfulBackup() {
        // Given
        BackupResult exportResult = BackupResult.builder()
                .backupId("backup-123")
                .backupLocation("gs://bucket/backup.sql")
                .backupDate(LocalDateTime.now())
                .status(BackupRecord.BackupStatus.EXPORTING)
                .build();

        BackupResult detectionResult = BackupResult.builder()
                .backupId("backup-123")
                .ransomwareDetected(false)
                .status(BackupRecord.BackupStatus.INSPECTING)
                .build();

        when(exportService.exportToCloudStorage(any())).thenReturn(exportResult);
        when(detectionService.inspectForRansomware(any(), any())).thenReturn(detectionResult);
        when(storage.get(anyString(), anyString())).thenReturn(mock(com.google.cloud.storage.Blob.class));
        when(storage.get(anyString(), anyString()).exists()).thenReturn(true);
        when(storage.get(anyString(), anyString()).getContent()).thenReturn("test data".getBytes());

        // When
        BackupResult result = orchestrationService.startAutomaticBackup(backupRequest);

        // Then
        assertNotNull(result);
        assertFalse(result.getRansomwareDetected());
        assertEquals(BackupRecord.BackupStatus.COMPLETED, result.getStatus());

        verify(exportService, times(1)).exportToCloudStorage(any());
        verify(detectionService, times(1)).inspectForRansomware(any(), any());
        verify(alertService, times(1)).confirmDataNotCompromised(any());
        verify(restorationService, never()).restorePreviousBackup(any(), anyString());
    }

    @Test
    @DisplayName("Should detect ransomware and restore previous backup")
    void testRansomwareDetectedAndRestore() {
        // Given
        BackupResult exportResult = BackupResult.builder()
                .backupId("backup-123")
                .backupLocation("gs://bucket/backup.sql")
                .backupDate(LocalDateTime.now())
                .build();

        BackupResult detectionResult = BackupResult.builder()
                .backupId("backup-123")
                .ransomwareDetected(true)
                .threatDetails("Ransomware pattern detected")
                .status(BackupRecord.BackupStatus.INSPECTING)
                .build();

        BackupResult verificationResult = BackupResult.builder()
                .backupId("backup-123")
                .databaseCompromised(true)
                .build();

        BackupResult restoreResult = BackupResult.builder()
                .backupId("backup-123")
                .previousBackupRestored(true)
                .status(BackupRecord.BackupStatus.CANCELLED)
                .build();

        when(exportService.exportToCloudStorage(any())).thenReturn(exportResult);
        when(detectionService.inspectForRansomware(any(), any())).thenReturn(detectionResult);
        when(verificationService.verifyDatabaseChanges(any(), anyString())).thenReturn(verificationResult);
        when(restorationService.restorePreviousBackup(any(), anyString())).thenReturn(restoreResult);
        when(storage.get(anyString(), anyString())).thenReturn(mock(com.google.cloud.storage.Blob.class));
        when(storage.get(anyString(), anyString()).exists()).thenReturn(true);
        when(storage.get(anyString(), anyString()).getContent()).thenReturn("test data".getBytes());

        // When
        BackupResult result = orchestrationService.startAutomaticBackup(backupRequest);

        // Then
        assertNotNull(result);
        assertTrue(result.getRansomwareDetected());
        assertTrue(result.getDatabaseCompromised());
        assertEquals(BackupRecord.BackupStatus.CANCELLED, result.getStatus());

        verify(alertService, times(1)).sendRansomwareAlert(any());
        verify(alertService, times(1)).confirmDatabaseCompromised(any());
        verify(restorationService, times(1)).restorePreviousBackup(any(), anyString());
    }
}

