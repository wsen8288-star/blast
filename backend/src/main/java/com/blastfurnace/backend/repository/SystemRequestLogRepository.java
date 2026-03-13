package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.SystemRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public interface SystemRequestLogRepository extends JpaRepository<SystemRequestLog, Long>, JpaSpecificationExecutor<SystemRequestLog> {
    long deleteByCreatedAtBefore(Date cutoff);
}
