package com.example.detectbackupransomware.service;

import com.example.detectbackupransomware.dto.BackupResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RansomwareDetectionService Tests")
class RansomwareDetectionServiceTest {

    @InjectMocks
    private RansomwareDetectionService detectionService;

    private BackupResult backupResult;

    @BeforeEach
    void setUp() {
        backupResult = BackupResult.builder()
                .backupId("test-backup")
                .backupLocation("gs://bucket/backup.sql")
                .build();
    }

    @Test
    @DisplayName("Should detect ransomware in backup content")
    void testDetectRansomwareInContent() {
        // Given
        byte[] suspiciousData = "RANSOMWARE DECRYPT YOUR FILES PAY BITCOIN".getBytes();

        // When
        BackupResult result = detectionService.inspectForRansomware(backupResult, suspiciousData);

        // Then
        assertNotNull(result);
        assertTrue(result.getRansomwareDetected());
        assertNotNull(result.getThreatDetails());
    }

    @Test
    @DisplayName("Should not detect ransomware in clean backup")
    void testCleanBackup() {
        // Given
        byte[] cleanData = "SELECT * FROM users;".getBytes();

        // When
        BackupResult result = detectionService.inspectForRansomware(backupResult, cleanData);

        // Then
        assertNotNull(result);
        assertFalse(result.getRansomwareDetected());
    }

    @Test
    @DisplayName("Should detect suspicious extension in filename")
    void testSuspiciousExtension() {
        // Given
        backupResult.setBackupLocation("gs://bucket/file.encrypted");
        byte[] data = "normal data".getBytes();

        // When
        BackupResult result = detectionService.inspectForRansomware(backupResult, data);

        // Then
        assertNotNull(result);
        assertTrue(result.getRansomwareDetected());
    }
}

