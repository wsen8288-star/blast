package com.blastfurnace.backend.service;

import com.blastfurnace.backend.dto.EvaluationResultVO;
import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelEvaluation;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.repository.ModelEvaluationRepository;
import com.blastfurnace.backend.repository.ModelTrainingRepository;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import com.blastfurnace.backend.service.trainer.ModelTrainer;
import com.blastfurnace.backend.service.trainer.ModelTrainerFactory;
import com.blastfurnace.backend.service.trainer.TrainingResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ModelEvaluationService {
    private final ModelTrainingRepository modelTrainingRepository;
    private final ProductionDataRepository productionDataRepository;
    private final ModelEvaluationRepository modelEvaluationRepository;
    private final ModelTrainerFactory modelTrainerFactory;
    private final UploadedFileService uploadedFileService;
    private final ModelTrainingService modelTrainingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvaluationResultVO evaluate(Long trainingId, String dataSource) {
        ModelTraining training = modelTrainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("训练任务不存在"));
        return evaluate(training, dataSource, training.getSelectedFeatures());
    }

    public EvaluationResultVO evaluate(ModelTraining training, String dataSource, String selectedFeatures) {
        String normalizedDataSource = normalizeDataSource(dataSource);
        List<ProductionData> evaluationData = getEvaluationData(training, normalizedDataSource);
        if (evaluationData.isEmpty()) {
            throw new IllegalArgumentException("评估数据为空。原因可能是：1. 训练时使用的上传文件已从内存中清除（如重启了服务器）；2. 数据库中无数据。请尝试重新上传数据并训练模型。");
        }

        ModelStoreService.StoredModel storedModel = modelTrainingService.getStoredModel(training.getId())
                .orElseThrow(() -> new IllegalStateException("模型未缓存，请重新训练后再评估"));
        if (selectedFeatures == null || selectedFeatures.isBlank()) {
            throw new IllegalArgumentException("训练记录缺少特征信息");
        }
        String[] featureArray = Arrays.stream(selectedFeatures.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
        ModelConfig modelConfig = training.getModelConfig();
        ModelTrainer trainer = modelTrainerFactory.getTrainer(training.getModelType());

        TrainingResult result = trainer.evaluate(evaluationData, training, modelConfig, featureArray, storedModel.getModel(), storedModel.getPreprocessor());
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getMessage());
        }

        ModelEvaluation evaluation = new ModelEvaluation();
        evaluation.setTrainingId(training.getId());
        evaluation.setModelType(training.getModelType());
        evaluation.setDataSource(normalizedDataSource);
        evaluation.setFeatures(training.getSelectedFeatures());
        evaluation.setR2(result.getR2Score());
        evaluation.setMae(result.getMae());
        evaluation.setRmse(result.getRmse());
        evaluation.setCreatedAt(new Date());
        try {
            evaluation.setTrueValuesJson(objectMapper.writeValueAsString(
                    result.getTrueValues() != null ? result.getTrueValues() : Collections.emptyList()
            ));
            evaluation.setPredictedValuesJson(objectMapper.writeValueAsString(
                    result.getPredictedValues() != null ? result.getPredictedValues() : Collections.emptyList()
            ));
            evaluation.setFeatureImportanceJson(objectMapper.writeValueAsString(
                    result.getFeatureImportance() != null ? result.getFeatureImportance() : Collections.emptyMap()
            ));
        } catch (Exception ignored) {
        }

        ModelEvaluation savedEvaluation = modelEvaluationRepository.save(evaluation);

        EvaluationResultVO response = new EvaluationResultVO();
        response.setEvaluation(savedEvaluation);
        response.setTrueValues(result.getTrueValues() != null ? result.getTrueValues() : Collections.emptyList());
        response.setPredictedValues(result.getPredictedValues() != null ? result.getPredictedValues() : Collections.emptyList());
        response.setFeatureImportance(result.getFeatureImportance() != null ? result.getFeatureImportance() : Collections.emptyMap());
        return response;
    }

    public List<ModelEvaluation> getHistory() {
        return modelEvaluationRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<ModelEvaluation> getById(Long id) {
        return modelEvaluationRepository.findById(id);
    }

    public void delete(Long id) {
        modelEvaluationRepository.deleteById(id);
    }

    public void deleteBatch(List<Long> ids) {
        modelEvaluationRepository.deleteAllById(ids);
    }

    private List<ProductionData> getEvaluationData(ModelTraining training, String dataSource) {
        String customDataId = training.getCustomDataId();
        List<ProductionData> allData;
        if (customDataId != null && !customDataId.isEmpty()) {
            allData = getDataFromUploadedFile(training);
        } else {
            allData = productionDataRepository.findAll();
            allData.sort(Comparator.comparing(ProductionData::getTimestamp).thenComparing(ProductionData::getId));
            if (training.getSplitHasTimestamp() == null) {
                training.setSplitHasTimestamp(true);
                modelTrainingRepository.save(training);
            }
        }

        if ("custom_data".equals(dataSource)) {
            return allData;
        }

        if (customDataId == null || customDataId.isEmpty()) {
            if (allData.isEmpty()) {
                return allData;
            }
        }
        if (allData.isEmpty()) {
            return allData;
        }
        com.blastfurnace.backend.service.trainer.DataSplitUtil.SplitResult split =
                com.blastfurnace.backend.service.trainer.DataSplitUtil.split(allData, training);
        if ("validation_data".equals(dataSource)) {
            return new ArrayList<>(split.validation());
        }
        if ("train_data".equals(dataSource)) {
            return new ArrayList<>(split.train());
        }
        if ("test_data".equals(dataSource)) {
            return new ArrayList<>(split.validation());
        }
        return new ArrayList<>(split.train());
    }

    private String normalizeDataSource(String dataSource) {
        if (dataSource == null || dataSource.isBlank()) {
            return "validation_data";
        }
        String value = dataSource.trim().toLowerCase(Locale.ROOT);
        if ("all_data".equals(value) || "full_data".equals(value) || "custom_data".equals(value)) {
            return "custom_data";
        }
        if ("train_data".equals(value) || "validation_data".equals(value) || "test_data".equals(value)) {
            return value;
        }
        return "validation_data";
    }

    private List<ProductionData> getDataFromUploadedFile(ModelTraining training) {
        String customDataId = training.getCustomDataId();
        List<Map<String, String>> uploadedData = null;

        byte[] dataBytes = training.getTrainingDataBytes();
        if (dataBytes != null && dataBytes.length > 0) {
            try {
                uploadedData = objectMapper.readValue(dataBytes, new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception ignored) {
                uploadedData = null;
            }
        }

        if (uploadedData == null || uploadedData.isEmpty()) {
            UploadedFileService.UploadedFileNormalized normalizedData = uploadedFileService.getNormalizedData(customDataId);
            uploadedData = normalizedData != null ? normalizedData.rows() : null;
        }

        if (uploadedData == null || uploadedData.isEmpty()) {
            List<Map<String, String>> raw = uploadedFileService.getUploadedData(customDataId);
            if (raw != null && !raw.isEmpty()) {
                uploadedData = UploadedDataNormalizer.normalize(raw).rows();
            }
        }

        if (uploadedData == null || uploadedData.isEmpty()) {
            return Collections.emptyList();
        }

        training.setSplitHasTimestamp(UploadedProductionDataMapper.hasUsableTimestamp(uploadedData));
        modelTrainingRepository.save(training);

        return UploadedProductionDataMapper.toProductionDataList(
                uploadedData,
                training.getSelectedFeatures(),
                training.getTargetVariable()
        );
    }

    private static String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
