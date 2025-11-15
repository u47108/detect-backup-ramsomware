package com.example.detectbackupransomware.service;

import com.example.detectbackupransomware.dto.BackupRequest;
import com.example.detectbackupransomware.dto.BackupResult;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudSqlExportService Tests")
class CloudSqlExportServiceTest {

    @Mock
    private Storage storage;

    @Mock
    private Blob blob;

    @InjectMocks
    private CloudSqlExportService exportService;

    private BackupRequest backupRequest;

    @BeforeEach
    void setUp() {
        backupRequest = BackupRequest.builder()
                .databaseInstance("test-instance")
                .databaseName("test-db")
                .backupBucket("test-bucket")
                .backupPrefix("backups/")
                .backupLocation("gs://test-bucket/backups/test-db.sql.gz")
                .build();
    }

    @Test
    @DisplayName("Should process exported backup successfully")
    void testProcessExportedBackup() {
        // Given
        when(storage.get(anyString(), anyString())).thenReturn(blob);
        when(blob.exists()).thenReturn(true);
        when(blob.getSize()).thenReturn(1024L);

        // When
        BackupResult result = exportService.processExportedBackup(backupRequest);

        // Then
        assertNotNull(result);
        assertNotNull(result.getBackupLocation());
        assertEquals(com.example.detectbackupransomware.entity.BackupRecord.BackupStatus.EXPORTING, result.getStatus());
        assertFalse(result.getRansomwareDetected());
    }
}

