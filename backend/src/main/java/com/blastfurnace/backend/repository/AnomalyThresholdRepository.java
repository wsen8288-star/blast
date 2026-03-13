package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.AnomalyThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnomalyThresholdRepository extends JpaRepository<AnomalyThreshold, Long> {
    Optional<AnomalyThreshold> findFirstByFurnaceIdAndParameterNameOrderByUpdateTimeDesc(String furnaceId, String parameterName);
    List<AnomalyThreshold> findByFurnaceId(String furnaceId);
    List<AnomalyThreshold> findByFurnaceIdAndParameterNameIn(String furnaceId, List<String> parameterNames);
}
