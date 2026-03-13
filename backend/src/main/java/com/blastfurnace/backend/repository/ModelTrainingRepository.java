package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.ModelTraining;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModelTrainingRepository extends JpaRepository<ModelTraining, Long> {
    List<ModelTraining> findByStatus(String status);
    List<ModelTraining> findByModelType(String modelType);
    List<ModelTraining> findAllByOrderByStartTimeDesc();
    @Query("select new com.blastfurnace.backend.model.ModelTraining(" +
            "mt.id, mt.modelType, mt.trainingData, mt.epochs, mt.batchSize, mt.learningRate, mt.selectedFeatures, " +
            "mt.status, mt.progress, mt.targetVariable, mt.currentEpoch, mt.trainingLoss, mt.r2Score, mt.mae, mt.rmse, " +
            "mt.startTime, mt.endTime, mt.customDataId, mt.modelConfig) " +
            "from ModelTraining mt order by mt.startTime desc")
    List<ModelTraining> findHistoryWithoutBlobs();
    ModelTraining findTop1ByStatusAndTargetVariableOrderByEndTimeDesc(String status, String targetVariable);
    long countByModelConfig_Id(Long modelConfigId);
    Optional<ModelTraining> findTopByRunIdOrderByStartTimeDesc(String runId);
    Optional<ModelTraining> findTopByRunIdIsNotNullAndStartTimeAfterOrderByStartTimeDesc(Date startTime);
}
