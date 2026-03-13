package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.PreprocessHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

@Repository
public interface PreprocessHistoryRepository extends JpaRepository<PreprocessHistory, Long> {
    Optional<PreprocessHistory> findTopByRunIdOrderByCreatedAtDesc(String runId);
    Optional<PreprocessHistory> findTopByRunIdIsNotNullAndCreatedAtAfterOrderByCreatedAtDesc(Date createdAt);
}
