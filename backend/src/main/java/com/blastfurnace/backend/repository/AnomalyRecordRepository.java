package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.AnomalyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnomalyRecordRepository extends JpaRepository<AnomalyRecord, Long>, JpaSpecificationExecutor<AnomalyRecord> {

    @Query("SELECT ar.parameterName, COUNT(ar) FROM AnomalyRecord ar GROUP BY ar.parameterName")
    List<Object[]> countByParameterName();

    @Query(value = "SELECT DATE_FORMAT(detection_time, '%Y-%m-%d') as d, COUNT(*) as c FROM anomaly_records WHERE detection_time >= :startTime GROUP BY d ORDER BY d", nativeQuery = true)
    List<Object[]> countByDate(@Param("startTime") LocalDateTime startTime);

    boolean existsByFurnaceIdAndParameterNameAndRelatedDataId(String furnaceId, String parameterName, Long relatedDataId);

    @Query("SELECT ar.relatedDataId, ar.parameterName FROM AnomalyRecord ar " +
            "WHERE ar.furnaceId = :furnaceId " +
            "AND ar.relatedDataId IN :relatedDataIds " +
            "AND ar.parameterName IN :params")
    List<Object[]> findExistingPairs(@Param("furnaceId") String furnaceId,
                                     @Param("relatedDataIds") List<Long> relatedDataIds,
                                     @Param("params") List<String> params);

    long deleteByDetectionTimeBeforeAndStatusIn(LocalDateTime cutoff, List<Integer> statuses);
}
