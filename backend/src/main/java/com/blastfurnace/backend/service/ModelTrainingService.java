package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.DeploymentStatus;
import com.blastfurnace.backend.model.ModelDeployment;
import com.blastfurnace.backend.model.ModelService;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.model.TrainingLog;
import com.blastfurnace.backend.model.ModelEvaluation;
import com.blastfurnace.backend.repository.ModelConfigRepository;
import com.blastfurnace.backend.repository.ModelDeploymentRepository;
import com.blastfurnace.backend.repository.ModelEvaluationRepository;
import com.blastfurnace.backend.repository.ModelTrainingRepository;
import com.blastfurnace.backend.repository.ModelServiceRepository;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import com.blastfurnace.backend.repository.TrainingLogRepository;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.UploadedFileService;
import com.blastfurnace.backend.service.trainer.ModelTrainer;
import com.blastfurnace.backend.service.trainer.ModelTrainerFactory;
import com.blastfurnace.backend.service.trainer.TrainingResult;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
public class ModelTrainingService {

    private static final Logger log = LoggerFactory.getLogger(ModelTrainingService.class);
    private static final long PROGRESS_PERSIST_MIN_INTERVAL_MS = 1500L;
    private static final int PROGRESS_PERSIST_MIN_STEP = 5;

    private static final Map<String, BiConsumer<ProductionData, Double>> FEATURE_SETTER_MAP = createFeatureSetterMap();
    private static Map<String, BiConsumer<ProductionData, Double>> createFeatureSetterMap() {
        Map<String, BiConsumer<ProductionData, Double>> m = new HashMap<>();
        m.put("temperature", ProductionData::setTemperature);
        m.put("pressure", ProductionData::setPressure);
        m.put("windVolume", ProductionData::setWindVolume);
        m.put("coalInjection", ProductionData::setCoalInjection);
        m.put("materialHeight", ProductionData::setMaterialHeight);
        m.put("gasFlow", ProductionData::setGasFlow);
        m.put("oxygenLevel", ProductionData::setOxygenLevel);
        m.put("energyConsumption", ProductionData::setEnergyConsumption);
        m.put("hotMetalTemperature", ProductionData::setHotMetalTemperature);
        m.put("constantSignal", ProductionData::setConstantSignal);
        m.put("siliconContent", ProductionData::setSiliconContent);
        m.put("productionRate", ProductionData::setProductionRate);
        return Collections.unmodifiableMap(m);
    }

    private static boolean isAllowedSelectedFeature(String canonicalKey) {
        return "timestamp".equals(canonicalKey) || FEATURE_SETTER_MAP.containsKey(canonicalKey);
    }

    private static String canonicalizeSelectedFeatureOrThrow(String rawFeature) {
        String trimmed = rawFeature == null ? "" : rawFeature.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("您勾选了不支持的训练特征: " + rawFeature);
        }
        String canonical = UploadedDataNormalizer.toCanonicalKey(trimmed);
        if (canonical == null || !isAllowedSelectedFeature(canonical)) {
            throw new IllegalArgumentException("您勾选了不支持的训练特征: " + rawFeature);
        }
        return canonical;
    }

    private static String validateAndNormalizeSelectedFeaturesOrThrow(String selectedFeaturesStr) {
        String s = selectedFeaturesStr == null ? "" : selectedFeaturesStr.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("selectedFeatures 为空，无法开始训练");
        }
        String[] normalized = Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(ModelTrainingService::canonicalizeSelectedFeatureOrThrow)
                .distinct()
                .toArray(String[]::new);
        if (normalized.length == 0) {
            throw new IllegalArgumentException("selectedFeatures 为空，无法开始训练");
        }
        Arrays.sort(normalized);
        return String.join(",", normalized);
    }

    private static String validateAndNormalizeInputFeaturesOrThrow(String selectedFeaturesStr, String targetVariable) {
        String s = selectedFeaturesStr == null ? "" : selectedFeaturesStr.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("selectedFeatures 为空，无法开始训练");
        }
        String target = targetVariable == null ? "" : targetVariable.trim();
        List<String> normalized = Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(ModelTrainingService::canonicalizeSelectedFeatureOrThrow)
                .toList();

        boolean hasForbidden = normalized.stream().anyMatch(key ->
                "productionRate".equals(key) || "energyConsumption".equals(key) || (!target.isEmpty() && target.equals(key))
        );
        if (hasForbidden) {
            throw new IllegalArgumentException("特征选择中不允许包含产量/能耗等目标列");
        }

        String[] dedup = normalized.stream().distinct().toArray(String[]::new);
        if (dedup.length == 0) {
            throw new IllegalArgumentException("selectedFeatures 为空，无法开始训练");
        }
        Arrays.sort(dedup);
        return String.join(",", dedup);
    }
    
    private final ModelTrainingRepository modelTrainingRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final TrainingLogRepository trainingLogRepository;
    private final ProductionDataRepository productionDataRepository;
    private final UploadedFileService uploadedFileService;
    private final ModelTrainerFactory modelTrainerFactory;
    private final ModelStoreService modelStoreService;
    private final ModelServiceRepository modelServiceRepository;
    private final ModelDeploymentRepository modelDeploymentRepository;
    private final ModelEvaluationRepository modelEvaluationRepository;
    private final ThreadPoolExecutor executorService = new ThreadPoolExecutor(
            2,
            4,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(32),
            new ThreadPoolExecutor.AbortPolicy()
    );
    private final ConcurrentHashMap<Long, ModelTraining> trainingTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Future<?>> trainingFutures = new ConcurrentHashMap<>();
    private final Set<Long> canceledTrainingIds = ConcurrentHashMap.newKeySet();
    private final RestTemplate restTemplate = new RestTemplate(); // 用于调用数据接口

    /**
     * 创建并启动训练任务
     */
    public ModelTraining startTraining(ModelTraining modelTraining, ModelConfig modelConfig) {
        // 为非神经网络模型设置默认配置（避免null字段导致数据库约束失败）
        if (modelConfig == null) {
            modelConfig = new ModelConfig();
        }
        
        // 检查并设置默认值，确保所有必填字段都有值
        String modelType = modelTraining.getModelType();
        if (modelTraining.getEpochs() == null || modelTraining.getEpochs() <= 0) {
            modelTraining.setEpochs(1);
        }
        if (modelTraining.getBatchSize() == null || modelTraining.getBatchSize() <= 0) {
            modelTraining.setBatchSize(1);
        }
        if (modelTraining.getLearningRate() == null || modelTraining.getLearningRate() <= 0) {
            modelTraining.setLearningRate(0.001);
        }
        if (modelConfig.getConfigName() == null || modelConfig.getConfigName().isEmpty()) {
            modelConfig.setConfigName(modelType + "_config_" + System.currentTimeMillis());
        }
        if (modelConfig.getHiddenLayers() == null) {
            // 非神经网络模型设置默认值1
            modelConfig.setHiddenLayers("neural_network".equals(modelType) ? 3 : 1);
        }
        if (modelConfig.getNeuronsPerLayer() == null || modelConfig.getNeuronsPerLayer().isEmpty()) {
            modelConfig.setNeuronsPerLayer("neural_network".equals(modelType) ? "128,64,32" : "64");
        }
        if (modelConfig.getActivationFunction() == null || modelConfig.getActivationFunction().isEmpty()) {
            // 神经网络使用前端传入的值，非神经网络使用默认值
            modelConfig.setActivationFunction("sigmoid");
        }
        if (modelConfig.getLossFunction() == null || modelConfig.getLossFunction().isEmpty()) {
            modelConfig.setLossFunction("mse");
        }
        if (modelConfig.getOptimizer() == null || modelConfig.getOptimizer().isEmpty()) {
            modelConfig.setOptimizer("adam");
        }
        if ("neural_network".equals(modelType) && modelConfig.getDropoutRate() == null) {
            modelConfig.setDropoutRate(0.1);
        }
        
        // 保存模型配置
        if (modelConfig.getId() == null) {
            modelConfig.setCreatedAt(new Date());
            modelConfig.setUpdatedAt(new Date());
            modelConfig = modelConfigRepository.save(modelConfig);
        }

        // 初始化训练任务
        modelTraining.setStatus("running");
        modelTraining.setProgress(0);
        modelTraining.setCurrentEpoch(0);
        modelTraining.setTrainingLoss(1.0);
        modelTraining.setR2Score(0.0);
        modelTraining.setMae(0.0);
        modelTraining.setRmse(0.0);
        modelTraining.setStartTime(new Date());
        modelTraining.setModelConfig(modelConfig);

        if (modelTraining.getSplitMode() == null || modelTraining.getSplitMode().isBlank()) {
            modelTraining.setSplitMode("auto");
        }
        if (modelTraining.getSplitRatio() == null) {
            modelTraining.setSplitRatio(0.8);
        }
        if (modelTraining.getSplitSeed() == null) {
            modelTraining.setSplitSeed(123L);
        }

        String rawTarget = modelTraining.getTargetVariable();
        String normalizedTarget = rawTarget == null ? "" : rawTarget.trim();
        if (normalizedTarget.isEmpty()) {
            modelTraining.setTargetVariable("productionRate");
        } else {
            String canonicalTarget = UploadedDataNormalizer.toCanonicalKey(normalizedTarget);
            if (canonicalTarget == null) {
                throw new IllegalArgumentException("未知预测目标: " + rawTarget);
            }
            modelTraining.setTargetVariable(canonicalTarget);
        }

        String originalSelected = modelTraining.getSelectedFeatures();
        if (originalSelected == null || originalSelected.isBlank()) {
            log.error("未提供特征选择(selectedFeatures)，拒绝开始训练");
            throw new IllegalArgumentException("selectedFeatures 为空，无法开始训练");
        }
        try {
            String normalizedJoined = validateAndNormalizeInputFeaturesOrThrow(originalSelected, modelTraining.getTargetVariable());
            log.info("规范化特征选择: {} -> {}", originalSelected, normalizedJoined);
            modelTraining.setSelectedFeatures(normalizedJoined);
        } catch (IllegalArgumentException e) {
            log.error("selectedFeatures 校验失败: {}", e.getMessage());
            throw e;
        }

        // 保存训练任务
        modelTraining = modelTrainingRepository.save(modelTraining);
        trainingTasks.put(modelTraining.getId(), modelTraining);

        // 记录训练开始日志
        TrainingLog startLog = new TrainingLog();
        startLog.setTraining(modelTraining);
        startLog.setTimestamp(new Date());
        startLog.setMessage("训练开始");
        startLog.setLogLevel("info");
        trainingLogRepository.save(startLog);

        // 记录训练参数
        TrainingLog paramsLog = new TrainingLog();
        paramsLog.setTraining(modelTraining);
        paramsLog.setTimestamp(new Date());
        
        String paramsMessage;
        if ("random_forest".equals(modelType)) {
            paramsMessage = String.format("训练参数: 模型类型=random_forest, 树的数量=%d, 最大深度=%d, 特征采样数=%d",
                    modelConfig.getTreeCount(), modelConfig.getMaxDepth(), modelConfig.getFeatureCount());
        } else if ("gradient_boosting".equals(modelType)) {
            paramsMessage = String.format("训练参数: 模型类型=gradient_boosting, 迭代次数=%d, 学习率=%.4f, 基础复杂度=%.2f",
                    modelConfig.getTreeCount(), modelConfig.getLearningRate(), modelConfig.getBaseComplexity());
        } else if ("gpr".equals(modelType)) {
            paramsMessage = String.format("训练参数: 模型类型=gpr, 迭代次数=%d, 批次大小=%d, 长度尺度=%.2f, 噪声水平=%.4f",
                    modelTraining.getEpochs(), modelTraining.getBatchSize(), modelConfig.getGprLengthScale(), modelConfig.getGprNoiseVariance());
        } else {
            // 默认神经网络参数
            paramsMessage = String.format("训练参数: 模型类型=%s, 轮数=%d, 批次大小=%d, 学习率=%.4f",
                    modelType, modelTraining.getEpochs(), 
                    modelTraining.getBatchSize(), modelTraining.getLearningRate());
        }
        
        paramsLog.setMessage(paramsMessage);
        paramsLog.setLogLevel("info");
        trainingLogRepository.save(paramsLog);

        // 创建final变量用于lambda表达式
        final ModelTraining finalTraining = modelTraining;
        canceledTrainingIds.remove(modelTraining.getId());
        
        try {
            Future<?> future = executorService.submit(() -> executeTraining(finalTraining));
            trainingFutures.put(modelTraining.getId(), future);
        } catch (RejectedExecutionException e) {
            modelTraining.setStatus("failed");
            modelTraining.setEndTime(new Date());
            modelTrainingRepository.save(modelTraining);
            trainingTasks.remove(modelTraining.getId());
            throw new IllegalStateException("当前训练任务过多，请稍后重试");
        }

        return modelTraining;
    }

    /**
     * 执行训练过程
     */
    private void executeTraining(ModelTraining modelTraining) {
        try {
            canceledTrainingIds.remove(modelTraining.getId());
            if (isTrainingCanceled(modelTraining.getId())) {
                modelTraining.setStatus("cancelled");
                modelTraining.setEndTime(new Date());
                modelTrainingRepository.save(modelTraining);
                return;
            }
            String normalizedSelected = validateAndNormalizeInputFeaturesOrThrow(modelTraining.getSelectedFeatures(), modelTraining.getTargetVariable());
            modelTraining.setSelectedFeatures(normalizedSelected);
            String[] selectedFeatures = normalizedSelected.split(",");

            // 获取训练数据
            List<ProductionData> trainingDataList;
            String customDataId = modelTraining.getCustomDataId();
            
            // 检查是否使用上传的文件数据
            if (customDataId != null && !customDataId.isEmpty()) {
                // 使用上传的文件数据进行训练
                trainingDataList = getTrainingDataFromUploadedFile(customDataId, modelTraining);
            } else {
                // 使用数据库中的数据进行训练
                trainingDataList = getDataByType(modelTraining.getTrainingData());
                if (modelTraining.getSplitHasTimestamp() == null) {
                    modelTraining.setSplitHasTimestamp(true);
                }
            }

            com.blastfurnace.backend.service.trainer.DataSplitUtil.SplitResult split =
                    com.blastfurnace.backend.service.trainer.DataSplitUtil.split(trainingDataList, modelTraining);
            modelTraining.setSplitModeUsed(split.usedMode().name().toLowerCase(java.util.Locale.ROOT));
            modelTrainingRepository.save(modelTraining);
            
            TrainingLog splitLog = new TrainingLog();
            splitLog.setTraining(modelTraining);
            splitLog.setTimestamp(new Date());
            splitLog.setMessage(String.format(
                    "数据切分: mode=%s, ratio=%.2f, seed=%d, hasTimestamp=%s",
                    modelTraining.getSplitModeUsed(),
                    modelTraining.getSplitRatio() != null ? modelTraining.getSplitRatio() : 0.8,
                    modelTraining.getSplitSeed() != null ? modelTraining.getSplitSeed() : 123L,
                    String.valueOf(modelTraining.getSplitHasTimestamp())
            ));
            splitLog.setLogLevel("info");
            trainingLogRepository.save(splitLog);

            // 记录获取到的数据量
            TrainingLog dataLog = new TrainingLog();
            dataLog.setTraining(modelTraining);
            dataLog.setTimestamp(new Date());
            dataLog.setMessage(String.format("获取训练数据成功，共 %d 条记录", trainingDataList.size()));
            dataLog.setLogLevel("info");
            trainingLogRepository.save(dataLog);
            
            // 记录选择的特征
            TrainingLog featureLog = new TrainingLog();
            featureLog.setTraining(modelTraining);
            featureLog.setTimestamp(new Date());
            featureLog.setMessage(String.format("使用特征: %s", String.join(", ", selectedFeatures)));
            featureLog.setLogLevel("info");
            trainingLogRepository.save(featureLog);
            
            // 获取模型配置
            ModelConfig modelConfig = modelTraining.getModelConfig();
            
            // 获取对应的训练器
            ModelTrainer trainer = modelTrainerFactory.getTrainer(modelTraining.getModelType());
            
            // 记录开始训练日志
            TrainingLog startTrainLog = new TrainingLog();
            startTrainLog.setTraining(modelTraining);
            startTrainLog.setTimestamp(new Date());
            startTrainLog.setMessage(String.format("开始使用%s训练模型", trainer.getModelType()));
            startTrainLog.setLogLevel("info");
            trainingLogRepository.save(startTrainLog);
            
            // 执行真实训练
            AtomicLong lastPersistAt = new AtomicLong(0L);
            AtomicInteger lastPersistedProgress = new AtomicInteger(-1);
            AtomicInteger lastPersistedEpoch = new AtomicInteger(0);
            java.util.function.Consumer<ModelTraining> progressCallback = (currentProgress) -> {
                if (Thread.currentThread().isInterrupted() || canceledTrainingIds.contains(modelTraining.getId())) {
                    throw new RuntimeException(new InterruptedException("训练已取消"));
                }
                int progress = currentProgress.getProgress() == null ? 0 : currentProgress.getProgress();
                int epoch = currentProgress.getCurrentEpoch() == null ? 0 : currentProgress.getCurrentEpoch();
                long now = System.currentTimeMillis();
                boolean shouldPersist =
                        progress >= 100
                                || now - lastPersistAt.get() >= PROGRESS_PERSIST_MIN_INTERVAL_MS
                                || progress - lastPersistedProgress.get() >= PROGRESS_PERSIST_MIN_STEP
                                || epoch - lastPersistedEpoch.get() >= 20;
                if (!shouldPersist) {
                    return;
                }
                modelTrainingRepository.save(currentProgress);
                lastPersistAt.set(now);
                lastPersistedProgress.set(progress);
                lastPersistedEpoch.set(epoch);
            };
            TrainingResult trainingResult = trainer.train(
                    trainingDataList,
                    modelTraining,
                    modelConfig,
                    selectedFeatures,
                    progressCallback
            );

            if (Thread.currentThread().isInterrupted() || isTrainingCanceled(modelTraining.getId())) {
                modelTraining.setStatus("cancelled");
                modelTraining.setEndTime(new Date());
                modelTrainingRepository.save(modelTraining);
                return;
            }
            
            // 更新训练状态
            if (trainingResult.isSuccess()) {
                modelTraining.setStatus("completed");
                modelTraining.setTrainingLoss(trainingResult.getTrainingLoss());
                modelTraining.setR2Score(trainingResult.getR2Score());
                modelTraining.setMae(trainingResult.getMae());
                modelTraining.setRmse(trainingResult.getRmse());
                modelTraining.setProgress(100);
                modelTraining.setCurrentEpoch(modelTraining.getEpochs());

                TrainingLog successLog = new TrainingLog();
                successLog.setTraining(modelTraining);
                successLog.setTimestamp(new Date());
                successLog.setMessage(String.format("训练成功: %s，损失: %.4f，R²: %.2f%%",
                        trainingResult.getMessage(), trainingResult.getTrainingLoss(), trainingResult.getR2Score() * 100));
                successLog.setLogLevel("info");
                trainingLogRepository.save(successLog);
            } else {
                modelTraining.setStatus("failed");

                TrainingLog failLog = new TrainingLog();
                failLog.setTraining(modelTraining);
                failLog.setTimestamp(new Date());
                failLog.setMessage(String.format("训练失败: %s", trainingResult.getMessage()));
                failLog.setLogLevel("error");
                trainingLogRepository.save(failLog);
            }

            modelTraining.setEndTime(new Date());
            modelTrainingRepository.save(modelTraining);

            if (trainingResult.isSuccess() && trainingResult.getModel() != null) {
                modelStoreService.save(modelTraining.getId(),
                        new ModelStoreService.StoredModel(modelTraining.getModelType(), trainingResult.getModel(), trainingResult.getPreprocessor(), trainingResult.getFeatures()));
                String modelType = modelTraining.getModelType();
                Object model = trainingResult.getModel();
                Object preprocessor = trainingResult.getPreprocessor();
                try {
                    byte[] modelBytes = trainer.serializeModel(model);
                    byte[] preprocessorBytes = trainer.serializePreprocessor(preprocessor);
                    modelTraining.setModelBytes(modelBytes);
                    modelTraining.setPreprocessorBytes(preprocessorBytes);
                    modelTraining.setModelSerializedAt(new Date());
                    modelTrainingRepository.save(modelTraining);
                    TrainingLog persistLog = new TrainingLog();
                    persistLog.setTraining(modelTraining);
                    persistLog.setTimestamp(new Date());
                    persistLog.setMessage(String.format("模型持久化完成: %s", modelType));
                    persistLog.setLogLevel("info");
                    trainingLogRepository.save(persistLog);
                } catch (Exception e) {
                    TrainingLog persistLog = new TrainingLog();
                    persistLog.setTraining(modelTraining);
                    persistLog.setTimestamp(new Date());
                    persistLog.setMessage("模型持久化失败: " + e.getMessage());
                    persistLog.setLogLevel("error");
                    trainingLogRepository.save(persistLog);
                }
            }

            TrainingLog completeLog = new TrainingLog();
            completeLog.setTraining(modelTraining);
            completeLog.setTimestamp(new Date());
            completeLog.setMessage("训练完成");
            completeLog.setLogLevel("info");
            trainingLogRepository.save(completeLog);

        } catch (Exception e) {
            if (isInterruptedError(e) || isTrainingCanceled(modelTraining.getId())) {
                Thread.currentThread().interrupt();
                modelTraining.setStatus("cancelled");
            } else {
                modelTraining.setStatus("failed");
            }
            modelTraining.setEndTime(new Date());
            modelTrainingRepository.save(modelTraining);

            // 记录训练失败日志
            TrainingLog errorLog = new TrainingLog();
            errorLog.setTraining(modelTraining);
            errorLog.setTimestamp(new Date());
            errorLog.setMessage("训练失败: " + e.getMessage());
            errorLog.setLogLevel("error");
            trainingLogRepository.save(errorLog);
        } finally {
            trainingTasks.remove(modelTraining.getId());
            trainingFutures.remove(modelTraining.getId());
            canceledTrainingIds.remove(modelTraining.getId());
        }
    }

    private boolean isTrainingCanceled(Long trainingId) {
        if (trainingId == null) {
            return true;
        }
        if (canceledTrainingIds.contains(trainingId)) {
            return true;
        }
        return modelTrainingRepository.findById(trainingId)
                .map(task -> "cancelled".equals(task.getStatus()))
                .orElse(true);
    }

    private boolean isInterruptedError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public Optional<ModelStoreService.StoredModel> getStoredModel(Long trainingId) {
        Optional<ModelStoreService.StoredModel> cached = modelStoreService.get(trainingId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<ModelTraining> trainingOptional = modelTrainingRepository.findById(trainingId);
        if (trainingOptional.isEmpty()) {
            return Optional.empty();
        }
        ModelTraining training = trainingOptional.get();
        if (training.getModelBytes() == null || training.getModelBytes().length == 0) {
            return Optional.empty();
        }
        ModelTrainer trainer = modelTrainerFactory.getTrainer(training.getModelType());
        try {
            Object model = trainer.deserializeModel(training.getModelBytes());
            Object preprocessor = trainer.deserializePreprocessor(training.getPreprocessorBytes());
            String[] features = training.getSelectedFeatures() != null ? training.getSelectedFeatures().split(",") : new String[0];
            ModelStoreService.StoredModel storedModel = new ModelStoreService.StoredModel(training.getModelType(), model, preprocessor, features);
            modelStoreService.save(trainingId, storedModel);
            return Optional.of(storedModel);
        } catch (Exception e) {
            throw new IllegalStateException("模型反序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从上传的文件中获取训练数据
     */
    private List<ProductionData> getTrainingDataFromUploadedFile(String customDataId, ModelTraining modelTraining) {
        try {
            UploadedFileService.UploadedFileNormalized normalizedData = uploadedFileService.getNormalizedData(customDataId);
            if (normalizedData == null || normalizedData.rows() == null || normalizedData.rows().isEmpty()) {
                log.error("获取上传文件数据失败: 文件数据不存在或已过期");
                throw new RuntimeException("获取上传文件数据失败: 文件数据不存在或已过期，请重新上传");
            }
            List<Map<String, String>> uploadedData = normalizedData.rows();
            
            if (uploadedData == null || uploadedData.isEmpty()) {
                log.error("获取上传文件数据失败: 文件数据不存在或已过期");
                throw new RuntimeException("获取上传文件数据失败: 文件数据不存在或已过期");
            }
            
            log.info("获取到上传文件数据，行数: {}", uploadedData.size());
            
            TrainingLog dataSourceLog = new TrainingLog();
            dataSourceLog.setTraining(modelTraining);
            dataSourceLog.setTimestamp(new Date());
            dataSourceLog.setMessage(String.format("使用上传文件数据，customDataId: %s, 获取到 %d 条记录", customDataId, uploadedData.size()));
            dataSourceLog.setLogLevel("info");
            trainingLogRepository.save(dataSourceLog);

            modelTraining.setSplitHasTimestamp(UploadedProductionDataMapper.hasUsableTimestamp(uploadedData));
            modelTrainingRepository.save(modelTraining);
            
            return UploadedProductionDataMapper.toProductionDataList(
                    uploadedData,
                    modelTraining.getSelectedFeatures(),
                    modelTraining.getTargetVariable()
            );
        } catch (Exception e) {
            log.error("处理上传文件数据失败", e);
            throw new RuntimeException("处理上传文件数据失败: " + e.getMessage());
        }
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
    
    /**
     * 辅助方法：安全解析Double值
     */
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
    
    /**
     * 获取训练数据 - 根据上传的文件和选择的特征进行处理
     */
    private List<ProductionData> getDataByType(String dataType) {
        // 简化处理：直接返回所有生产数据，实际项目中会根据上传的文件进行处理
        // 这里保留dataType参数是为了兼容现有代码结构
        // 后续可以扩展为根据customDataId查询上传的文件数据
        List<ProductionData> list = productionDataRepository.findAll();
        list.sort(java.util.Comparator
                .comparing(ProductionData::getTimestamp)
                .thenComparing(ProductionData::getId));
        return list;
    }

    /**
     * 获取训练任务状态
     */
    public Optional<ModelTraining> getTrainingStatus(Long trainingId) {
        return modelTrainingRepository.findById(trainingId);
    }

    /**
     * 获取训练日志
     */
    public List<TrainingLog> getTrainingLogs(Long trainingId) {
        return trainingLogRepository.findByTrainingId(trainingId);
    }

    /**
     * 保存模型配置
     */
    public ModelConfig saveModelConfig(ModelConfig modelConfig) {
        if (modelConfig.getId() == null) {
            modelConfig.setCreatedAt(new Date());
        }
        modelConfig.setUpdatedAt(new Date());
        return modelConfigRepository.save(modelConfig);
    }

    /**
     * 获取模型配置
     */
    public Optional<ModelConfig> getModelConfig(Long configId) {
        return modelConfigRepository.findById(configId);
    }

    /**
     * 获取所有模型配置
     */
    public List<ModelConfig> getAllModelConfigs() {
        return modelConfigRepository.findAll();
    }

    /**
     * 获取训练历史
     */
    public List<ModelTraining> getTrainingHistory() {
        return modelTrainingRepository.findHistoryWithoutBlobs();
    }

    /**
     * 取消训练任务
     */
    public boolean cancelTraining(Long trainingId) {
        Optional<ModelTraining> optionalTraining = modelTrainingRepository.findById(trainingId);
        if (optionalTraining.isPresent()) {
            ModelTraining modelTraining = optionalTraining.get();
            if (modelTraining.getStatus().equals("running")) {
                canceledTrainingIds.add(trainingId);
                modelTraining.setStatus("cancelled");
                modelTraining.setEndTime(new Date());
                modelTrainingRepository.save(modelTraining);
                trainingTasks.remove(trainingId);
                Future<?> future = trainingFutures.remove(trainingId);
                if (future != null) {
                    future.cancel(true);
                }
                return true;
            }
        }
        return false;
    }

    @PreDestroy
    public void shutdownExecutor() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
    
    /**
     * 删除训练历史
     */
    @org.springframework.transaction.annotation.Transactional
    public boolean deleteTrainingHistory(Long trainingId) {
        Optional<ModelTraining> optionalTraining = modelTrainingRepository.findById(trainingId);
        if (optionalTraining.isEmpty()) {
            return false;
        }
        Long configId = optionalTraining.map(ModelTraining::getModelConfig).map(ModelConfig::getId).orElse(null);

        List<TrainingLog> logs = trainingLogRepository.findByTrainingId(trainingId);
        if (!logs.isEmpty()) {
            trainingLogRepository.deleteAll(logs);
        }

        List<ModelEvaluation> evaluations = modelEvaluationRepository.findByTrainingId(trainingId);
        if (!evaluations.isEmpty()) {
            modelEvaluationRepository.deleteAll(evaluations);
        }

        modelTrainingRepository.deleteById(trainingId);

        if (configId != null && modelTrainingRepository.countByModelConfig_Id(configId) == 0) {
            modelConfigRepository.deleteById(configId);
        }
        return true;
    }

    /**
     * 批量删除训练历史
     */
    @org.springframework.transaction.annotation.Transactional
    public boolean deleteTrainingHistoryBatch(List<Long> trainingIds) {
        if (trainingIds == null || trainingIds.isEmpty()) {
            return true;
        }
        List<ModelTraining> trainings = modelTrainingRepository.findAllById(trainingIds);
        java.util.Set<Long> configIds = new java.util.HashSet<>();
        for (ModelTraining training : trainings) {
            if (training != null && training.getModelConfig() != null && training.getModelConfig().getId() != null) {
                configIds.add(training.getModelConfig().getId());
            }
        }

        for (Long id : trainingIds) {
            if (id == null) {
                continue;
            }
            List<TrainingLog> logs = trainingLogRepository.findByTrainingId(id);
            if (!logs.isEmpty()) {
                trainingLogRepository.deleteAll(logs);
            }
            List<ModelEvaluation> evaluations = modelEvaluationRepository.findByTrainingId(id);
            if (!evaluations.isEmpty()) {
                modelEvaluationRepository.deleteAll(evaluations);
            }
        }

        modelTrainingRepository.deleteAllById(trainingIds);

        for (Long configId : configIds) {
            if (configId != null && modelTrainingRepository.countByModelConfig_Id(configId) == 0) {
                modelConfigRepository.deleteById(configId);
            }
        }
        return true;
    }

    /**
     * 导出模型信息
     */
    public Map<String, Object> exportModel(Long trainingId) {
        Optional<ModelTraining> optionalTraining = modelTrainingRepository.findById(trainingId);
        if (optionalTraining.isPresent()) {
            ModelTraining training = optionalTraining.get();
            Map<String, Object> exportData = new HashMap<>();
            exportData.put("training", training);
            exportData.put("config", training.getModelConfig());
            exportData.put("exportedAt", new Date());
            return exportData;
        }
        return null;
    }

    public double predictProductionRate(ProductionData data) {
        return predictByActiveDeployedModel(data, "productionRate");
    }

    public double predictEnergyConsumption(ProductionData data) {
        return predictByActiveDeployedModel(data, "energyConsumption");
    }

    private double predictByActiveDeployedModel(ProductionData data, String expectedTargetVariable) {
        List<ModelService> services = modelServiceRepository.findAllByOrderByIdDesc();
        ModelService active = services.stream()
                .filter(service -> "running".equals(service.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到运行中的已部署模型服务"));
        if (active.getDeploymentId() == null) {
            throw new IllegalStateException("运行中的模型服务缺少部署记录");
        }
        ModelDeployment deployment = modelDeploymentRepository.findById(active.getDeploymentId())
                .orElseThrow(() -> new IllegalStateException("部署记录不存在"));
        if (deployment.getStatus() != DeploymentStatus.COMPLETED) {
            throw new IllegalStateException("部署未完成，无法推理: " + deployment.getStatus().getValue());
        }
        ModelTraining training = modelTrainingRepository.findById(deployment.getTrainingId())
                .orElseThrow(() -> new IllegalStateException("训练任务不存在"));
        if (!"completed".equals(training.getStatus())) {
            throw new IllegalStateException("训练任务未完成，无法推理: " + training.getStatus());
        }
        if (training.getTargetVariable() == null || training.getTargetVariable().isBlank()) {
            throw new IllegalStateException("训练任务缺少预测目标");
        }
        if (!expectedTargetVariable.equals(training.getTargetVariable())) {
            throw new IllegalStateException("当前部署模型预测目标为 " + training.getTargetVariable() + "，无法用于预测 " + expectedTargetVariable);
        }
        ModelStoreService.StoredModel storedModel = getStoredModel(training.getId())
                .orElseThrow(() -> new IllegalStateException("模型未就绪"));
        ModelTrainer trainer = modelTrainerFactory.getTrainer(storedModel.getModelType());
        String[] features = storedModel.getFeatures() != null && storedModel.getFeatures().length > 0
                ? storedModel.getFeatures()
                : (training.getSelectedFeatures() != null ? training.getSelectedFeatures().split(",") : new String[0]);
        TrainingResult result = trainer.evaluate(
                List.of(data),
                training,
                training.getModelConfig(),
                features,
                storedModel.getModel(),
                storedModel.getPreprocessor()
        );
        if (result.getPredictedValues() == null || result.getPredictedValues().isEmpty() || result.getPredictedValues().get(0) == null) {
            throw new IllegalStateException("模型预测结果为空");
        }
        return result.getPredictedValues().get(0);
    }
}
