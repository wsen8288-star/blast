package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.ModelEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ModelEvaluationRepository extends JpaRepository<ModelEvaluation, Long> {
    List<ModelEvaluation> findAllByOrderByCreatedAtDesc();
    List<ModelEvaluation> findByTrainingId(Long trainingId);
}
