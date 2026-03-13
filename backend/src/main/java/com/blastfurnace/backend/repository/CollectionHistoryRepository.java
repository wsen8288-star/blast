package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.CollectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Date;
import java.util.Optional;

public interface CollectionHistoryRepository extends JpaRepository<CollectionHistory, Long>, JpaSpecificationExecutor<CollectionHistory> {
    Optional<CollectionHistory> findTopByRunIdOrderByStartTimeDesc(String runId);
    Optional<CollectionHistory> findTopByRunIdIsNotNullAndStartTimeAfterOrderByStartTimeDesc(Date startTime);
}
