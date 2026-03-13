package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.ComparisonHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComparisonHistoryRepository extends JpaRepository<ComparisonHistory, Long> {
    List<ComparisonHistory> findAllByOrderByCreatedAtDesc();
    Optional<ComparisonHistory> findTopByRunIdOrderByCreatedAtDesc(String runId);
    Optional<ComparisonHistory> findTopByRunIdIsNotNullAndCreatedAtAfterOrderByCreatedAtDesc(Date createdAt);

    @Query("select h from ComparisonHistory h " +
            "where (:mode is null or h.mode = :mode) " +
            "and (:startDate is null or h.createdAt >= :startDate) " +
            "and (:endDate is null or h.createdAt <= :endDate) " +
            "and (:historyType is null or h.historyType = :historyType or (:historyType = 'EVOLUTION' and h.historyType is null)) " +
            "order by h.createdAt desc")
    List<ComparisonHistory> findFiltered(@Param("mode") String mode,
                                         @Param("startDate") Date startDate,
                                         @Param("endDate") Date endDate,
                                         @Param("historyType") String historyType);
}
