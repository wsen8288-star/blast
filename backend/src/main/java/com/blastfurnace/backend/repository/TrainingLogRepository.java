package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.TrainingLog;
import com.blastfurnace.backend.model.ModelTraining;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TrainingLogRepository extends JpaRepository<TrainingLog, Long> {
    List<TrainingLog> findByTraining(ModelTraining training);
    List<TrainingLog> findByTrainingId(Long trainingId);
}