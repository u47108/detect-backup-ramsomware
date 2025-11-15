package com.example.detectbackupransomware.repository;

import com.example.detectbackupransomware.entity.BackupRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {

    Optional<BackupRecord> findByBackupId(String backupId);

    @Query("SELECT b FROM BackupRecord b WHERE b.databaseName = :databaseName " +
           "AND b.status = 'COMPLETED' ORDER BY b.createdAt DESC")
    List<BackupRecord> findLatestSuccessfulBackups(@Param("databaseName") String databaseName);

    @Query("SELECT b FROM BackupRecord b WHERE b.databaseName = :databaseName " +
           "AND b.createdAt < :beforeDate AND b.status = 'COMPLETED' " +
           "ORDER BY b.createdAt DESC")
    List<BackupRecord> findBackupsBeforeDate(@Param("databaseName") String databaseName,
                                              @Param("beforeDate") LocalDateTime beforeDate);

    @Query("SELECT COUNT(b) FROM BackupRecord b WHERE b.ransomwareDetected = true " +
           "AND b.createdAt >= :since")
    long countRansomwareDetectionsSince(@Param("since") LocalDateTime since);
}

