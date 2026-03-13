package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.BackupHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

@Repository
public interface BackupHistoryRepository extends JpaRepository<BackupHistory, Long> {
    Optional<BackupHistory> findFirstByBackupTimeAndStatusOrderByIdDesc(Date backupTime, String status);
}
