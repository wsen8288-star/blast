package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    long deleteByTimeBefore(Date cutoff);
}
