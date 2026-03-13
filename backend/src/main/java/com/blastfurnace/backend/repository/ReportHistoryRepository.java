package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.ReportHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportHistoryRepository extends JpaRepository<ReportHistory, Long> {
    // 按时间倒序查询
    List<ReportHistory> findAllByOrderByCreateTimeDesc();
}