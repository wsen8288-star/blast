package com.blastfurnace.backend.service;

import com.blastfurnace.backend.dto.DeploymentHistoryDTO;
import com.blastfurnace.backend.dto.DeploymentRequest;
import com.blastfurnace.backend.dto.DeploymentResponse;
import com.blastfurnace.backend.dto.ServiceInfoDTO;
import com.blastfurnace.backend.model.AuditLog;
import com.blastfurnace.backend.model.DeploymentStatus;
import com.blastfurnace.backend.model.ModelDeployment;
import com.blastfurnace.backend.model.ModelService;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.repository.AuditLogRepository;
import com.blastfurnace.backend.repository.ModelDeploymentRepository;
import com.blastfurnace.backend.repository.ModelServiceRepository;
import com.blastfurnace.backend.repository.ModelTrainingRepository;
import com.blastfurnace.backend.service.trainer.ModelTrainer;
import com.blastfurnace.backend.service.trainer.ModelTrainerFactory;
import com.blastfurnace.backend.service.trainer.TrainingResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
public class ModelDeploymentService {
    private static final Set<String> REMOVED_RUNTIME_CONFIG_KEYS = Set.of("predictionInterval", "confidenceThreshold");
    private final ModelTrainingRepository modelTrainingRepository;
    private final ModelDeploymentRepository modelDeploymentRepository;
    private final ModelServiceRepository modelServiceRepository;
    private final AuditLogRepository auditLogRepository;
    private final ModelStoreService modelStoreService;
    private final ModelTrainerFactory modelTrainerFactory;
    private final UploadedDataNormalizer uploadedDataNormalizer;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final ThreadPoolExecutor executorService = new ThreadPoolExecutor(
            2,
            4,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(32),
            new ThreadPoolExecutor.AbortPolicy()
    );
    private final ConcurrentHashMap<Long, Future<?>> deploymentFutures = new ConcurrentHashMap<>();
    private static final Map<String, BiConsumer<ProductionData, Double>> FEATURE_SETTER_MAP = buildFeatureSetterMap();

    @PersistenceContext
    private EntityManager entityManager;

    @PostConstruct
    public void ensureDeploymentStatusColumn() {
        new TransactionTemplate(transactionManager).execute(status -> {
            try {
                entityManager.createNativeQuery("ALTER TABLE model_deployment MODIFY COLUMN status VARCHAR(20) NOT NULL")
                        .executeUpdate();
            } catch (Exception ignored) {
            }
            return null;
        });
    }

    @Transactional
    public DeploymentResponse deploy(DeploymentRequest request, String operator) {
        ModelTraining training = modelTrainingRepository.findById(request.getTrainingId())
                .orElseThrow(() -> new IllegalArgumentException("训练任务不存在"));
        
        if (request.getSecondaryTrainingId() != null) {
            modelTrainingRepository.findById(request.getSecondaryTrainingId())
                    .orElseThrow(() -> new IllegalArgumentException("辅助训练任务不存在"));
        }

        ModelDeployment deployment = new ModelDeployment();
        deployment.setTrainingId(training.getId());
        deployment.setSecondaryTrainingId(request.getSecondaryTrainingId());

        String modelDisplayName = toModelDisplayName(training.getModelType());
        if (request.getSecondaryTrainingId() != null) {
            ModelTraining secondary = modelTrainingRepository.findById(request.getSecondaryTrainingId()).orElse(null);
            if (secondary != null) {
                modelDisplayName += " + " + toModelDisplayName(secondary.getModelType());
            }
        }

        deployment.setName(request.getName() != null ? request.getName() : ("部署-" + training.getId()));
        deployment.setVersion(request.getVersion() != null ? request.getVersion() : "1.0.0");
        deployment.setEnvironment(request.getEnvironment() != null ? request.getEnvironment() : "production");
        deployment.setDeployTime(new Date());
        deployment.setStatus(DeploymentStatus.RUNNING);
        deployment.setDescription(request.getDescription());
        String sanitizedConfig = sanitizeConfigJsonString(request.getConfig());
        deployment.setConfig(sanitizedConfig);
        deployment.setLogs(formatTime(new Date()) + " - 部署开始");
        deployment = modelDeploymentRepository.save(deployment);
        if (operator != null && !operator.isBlank()) {
            appendDeploymentLog(deployment, "操作人: " + operator + " - 发起部署");
        }

        ModelService service = new ModelService();
        service.setDeploymentId(deployment.getId());
        service.setName(deployment.getName());
        service.setModelName(modelDisplayName);
        service.setEnvironment(deployment.getEnvironment());
        service.setStatus("running");
        service.setVersion(deployment.getVersion());
        service.setLastHeartbeat(new Date());
        service.setServiceConfig(sanitizedConfig);
        service.setUrl("http://localhost:8080/api/optimization/model/deployment/predict/pending");
        service = modelServiceRepository.save(service);
        service.setUrl("http://localhost:8080/api/optimization/model/deployment/predict/" + service.getId());
        modelServiceRepository.save(service);
        // archiveOtherRunningServices(service.getId(), operator); // 取消互斥逻辑

        Long deploymentId = deployment.getId();
        Long trainingId = training.getId();
        Long secondaryTrainingId = request.getSecondaryTrainingId();
        Long serviceId = service.getId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runDeploymentAsync(deploymentId, trainingId, secondaryTrainingId, serviceId);
                }
            });
        } else {
            runDeploymentAsync(deploymentId, trainingId, secondaryTrainingId, serviceId);
        }
        recordAudit(operator, "DEPLOY", "deployment", deployment.getId(), "success", "发起部署");

        DeploymentResponse response = new DeploymentResponse();
        response.setDeploymentId(deployment.getId());
        response.setDeploymentStatus(deployment.getStatus().getValue());
        response.setDeploymentProgress(20);
        response.setDeployedModelName(modelDisplayName);
        response.setDeployedEnvironment(deployment.getEnvironment());
        response.setDeploymentTime(formatTime(deployment.getDeployTime()));
        response.setServiceUrl(service.getUrl());
        response.setServiceStatus(service.getStatus());
        response.setApiVersion(deployment.getVersion());
        response.setDeploymentLogs(List.of(deployment.getLogs()));
        return response;
    }

    public boolean stopService(Long serviceId) {
        return stopService(serviceId, null);
    }

    public boolean stopService(Long serviceId, String operator) {
        Optional<ModelService> optionalService = modelServiceRepository.findById(serviceId);
        if (optionalService.isEmpty()) {
            return false;
        }
        ModelService service = optionalService.get();
        Optional<ModelDeployment> optionalDeployment = modelDeploymentRepository.findById(service.getDeploymentId());
        if (optionalDeployment.isPresent()) {
            ModelDeployment deployment = optionalDeployment.get();
            appendDeploymentLog(deployment, buildOperatorMessage(operator, "服务已停止"));
            modelStoreService.remove(deployment.getTrainingId());
        }
        service.setStatus("stopped");
        modelServiceRepository.save(service);
        recordAudit(operator, "STOP_SERVICE", "service", serviceId, "success", "服务已停止");
        return true;
    }

    @Transactional
    public ServiceInfoDTO startService(Long serviceId, String operator) {
        return updateServiceStatus(serviceId, "running", buildOperatorMessage(operator, "服务已启动"), true, false, operator, "START_SERVICE");
    }

    @Transactional
    public ServiceInfoDTO restartService(Long serviceId, String operator) {
        return updateServiceStatus(serviceId, "running", buildOperatorMessage(operator, "服务已重启"), true, true, operator, "RESTART_SERVICE");
    }

    @Transactional
    public ServiceInfoDTO updateServiceName(Long serviceId, String newName, String operator) {
        Optional<ModelService> optionalService = modelServiceRepository.findById(serviceId);
        if (optionalService.isEmpty()) {
            throw new IllegalArgumentException("服务不存在");
        }
        ModelService service = optionalService.get();
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("服务名称不能为空");
        }
        String oldName = service.getName();
        service.setName(newName);
        modelServiceRepository.save(service);
        
        ModelDeployment deployment = modelDeploymentRepository.findById(service.getDeploymentId())
                .orElseThrow(() -> new IllegalArgumentException("部署记录不存在"));
        deployment.setName(newName);
        modelDeploymentRepository.save(deployment);
        
        appendDeploymentLog(deployment, buildOperatorMessage(operator, "修改服务名称: " + oldName + " -> " + newName));
        recordAudit(operator, "UPDATE_SERVICE_NAME", "service", serviceId, "success", "修改服务名称: " + oldName + " -> " + newName);
        String targetVariable = modelTrainingRepository.findById(deployment.getTrainingId())
                .map(ModelTraining::getTargetVariable)
                .orElse(null);
        return toServiceInfo(service, deployment.getTrainingId(), targetVariable);
    }

    @Transactional
    public Map<String, Object> updateServiceConfig(Long serviceId, Map<String, Object> config, String operator) {
        Optional<ModelService> optionalService = modelServiceRepository.findById(serviceId);
        if (optionalService.isEmpty()) {
            throw new IllegalArgumentException("服务不存在");
        }
        ModelService service = optionalService.get();
        Map<String, Object> mergedConfig = parseConfig(service.getServiceConfig());
        if (config != null) {
            mergedConfig.putAll(config);
        }
        mergedConfig = sanitizeRuntimeConfig(mergedConfig);
        applyServiceFields(service, mergedConfig);
        service.setServiceConfig(writeConfig(mergedConfig));
        modelServiceRepository.save(service);
        modelDeploymentRepository.findById(service.getDeploymentId())
                .ifPresent(deployment -> appendDeploymentLog(deployment, buildOperatorMessage(operator, "服务配置已更新")));
        recordAudit(operator, "UPDATE_SERVICE_CONFIG", "service", serviceId, "success", "服务配置已更新");
        return mergedConfig;
    }

    public Map<String, Object> getServiceConfig(Long serviceId) {
        ModelService service = modelServiceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("服务不存在"));
        String original = service.getServiceConfig();
        String sanitized = sanitizeConfigJsonString(original);
        if (!Objects.equals(original, sanitized)) {
            service.setServiceConfig(sanitized);
            modelServiceRepository.save(service);
        }
        return sanitizeRuntimeConfig(parseConfig(sanitized));
    }

    public List<String> getServiceLogs(Long serviceId) {
        Optional<ModelService> optionalService = modelServiceRepository.findById(serviceId);
        if (optionalService.isEmpty()) {
            throw new IllegalArgumentException("服务不存在");
        }
        ModelService service = optionalService.get();
        ModelDeployment deployment = modelDeploymentRepository.findById(service.getDeploymentId())
                .orElseThrow(() -> new IllegalArgumentException("部署记录不存在"));
        String logs = deployment.getLogs();
        if (logs == null || logs.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.asList(logs.split("\n"));
    }

    @Transactional
    public Map<String, Object> cancelDeployment(Long deploymentId, String operator) {
        ModelDeployment deployment = modelDeploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("部署记录不存在"));
        if (deployment.getStatus() != DeploymentStatus.RUNNING) {
            Map<String, Object> result = new HashMap<>();
            result.put("deploymentId", deploymentId);
            result.put("status", deployment.getStatus().getValue());
            result.put("message", "部署未处于进行中状态");
            recordAudit(operator, "CANCEL", "deployment", deploymentId, "rejected", "部署未处于进行中状态");
            return result;
        }
        deployment.setStatus(DeploymentStatus.CANCELED);
        modelDeploymentRepository.save(deployment);
        appendDeploymentLog(deployment, buildOperatorMessage(operator, "部署已取消"));
        Future<?> future = deploymentFutures.remove(deploymentId);
        if (future != null) {
            future.cancel(true);
        }
        List<ModelService> services = modelServiceRepository.findByDeploymentId(deploymentId);
        for (ModelService service : services) {
            service.setStatus("stopped");
            modelServiceRepository.save(service);
        }
        modelStoreService.remove(deployment.getTrainingId());
        Map<String, Object> result = new HashMap<>();
        result.put("deploymentId", deploymentId);
        result.put("status", deployment.getStatus().getValue());
        result.put("message", "部署已取消");
        recordAudit(operator, "CANCEL", "deployment", deploymentId, "success", "部署已取消");
        return result;
    }

    @Transactional
    public void deleteDeploymentHistory(Long deploymentId, String operator) {
        ModelDeployment deployment = modelDeploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("部署记录不存在"));
        if (deployment.getStatus() == DeploymentStatus.RUNNING) {
            throw new IllegalStateException("部署进行中，无法删除");
        }
        List<ModelService> services = modelServiceRepository.findByDeploymentId(deploymentId);
        if (!services.isEmpty()) {
            for (ModelService service : services) {
                service.setStatus("stopped");
            }
            modelServiceRepository.saveAll(services);
            modelServiceRepository.deleteAll(services);
        }
        modelStoreService.remove(deployment.getTrainingId());
        modelDeploymentRepository.deleteById(deploymentId);
        recordAudit(operator, "DELETE", "deployment", deploymentId, "success", "删除部署历史");
    }

    @Transactional
    public void deleteDeploymentHistoryBatch(List<Long> deploymentIds, String operator) {
        if (deploymentIds == null || deploymentIds.isEmpty()) {
            return;
        }
        for (Long deploymentId : deploymentIds) {
            deleteDeploymentHistory(deploymentId, operator);
        }
    }

    public DeploymentResponse retryDeployment(Long deploymentId, String operator) {
        ModelDeployment deployment = modelDeploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("部署记录不存在"));
        if (deployment.getStatus() == DeploymentStatus.RUNNING) {
            throw new IllegalStateException("部署进行中，无法重试");
        }
        DeploymentRequest request = new DeploymentRequest();
        request.setTrainingId(deployment.getTrainingId());
        request.setEnvironment(deployment.getEnvironment());
        request.setName(deployment.getName());
        request.setVersion(deployment.getVersion());
        request.setDescription(deployment.getDescription());
        request.setConfig(deployment.getConfig());
        DeploymentResponse response = deploy(request, operator);
        recordAudit(operator, "RETRY", "deployment", deploymentId, "success", "部署已重试");
        return response;
    }

    public Map<String, Object> checkServiceHealth(Long serviceId) {
        ModelService service = modelServiceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("服务不存在"));
        ModelDeployment deployment = modelDeploymentRepository.findById(service.getDeploymentId())
                .orElseThrow(() -> new IllegalArgumentException("部署记录不存在"));
        Long trainingId = deployment.getTrainingId();
        boolean modelReady = modelStoreService.get(trainingId).isPresent();
        boolean autoRecover = deployment.getStatus() == DeploymentStatus.COMPLETED;
        if (!modelReady && ("running".equals(service.getStatus()) || autoRecover)) {
            try {
                ensureModelLoaded(deployment);
                modelReady = modelStoreService.get(trainingId).isPresent();
                if (modelReady && autoRecover && "stopped".equals(service.getStatus())) {
                    service.setStatus("running");
                }
            } catch (Exception e) {
                appendDeploymentLog(deployment, "服务健康检查加载模型失败: " + e.getMessage());
            }
        }
        if (!modelReady && "running".equals(service.getStatus())) {
            service.setStatus("stopped");
        }
        String heartbeat = formatTime(new Date());
        String health = (modelReady && "running".equals(service.getStatus())) ? "healthy" : "unhealthy";
        service.setLastHeartbeat(new Date());
        modelServiceRepository.save(service);
        String previousHealth = Optional.ofNullable(parseConfig(service.getServiceConfig()).get("healthStatus"))
                .map(Object::toString)
                .orElse("");
        if (!health.equals(previousHealth)) {
            Map<String, Object> config = parseConfig(service.getServiceConfig());
            config.put("healthStatus", health);
            service.setServiceConfig(writeConfig(config));
            modelServiceRepository.save(service);
            appendDeploymentLog(deployment, "服务健康状态变更: " + health);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("serviceId", serviceId);
        result.put("status", service.getStatus());
        result.put("health", health);
        result.put("lastHeartbeat", heartbeat);
        return result;
    }

    public Map<String, Object> predict(Long serviceId, Map<String, Object> input) {
        Optional<ModelService> optionalService = modelServiceRepository.findById(serviceId);
        if (optionalService.isEmpty()) {
            throw new IllegalArgumentException("服务不存在");
        }
        ModelService service = optionalService.get();
        if (!"running".equals(service.getStatus())) {
            throw new IllegalStateException("服务未运行");
        }
        Map<String, Object> runtimeConfig = parseConfig(service.getServiceConfig());
        if (input == null) {
            throw new IllegalArgumentException("预测输入不能为空");
        }
        ModelDeployment deployment = modelDeploymentRepository.findById(service.getDeploymentId())
                .orElseThrow(() -> new IllegalArgumentException("部署记录不存在"));
        
        // Primary Prediction
        Map<String, Object> primaryResult = runSinglePrediction(deployment.getTrainingId(), input, runtimeConfig, false);
        
        Map<String, Object> response = new HashMap<>(primaryResult);
        response.put("serviceId", serviceId);
        response.put("input", input);

        // Secondary Prediction
        if (deployment.getSecondaryTrainingId() != null) {
            try {
                Map<String, Object> secondaryResult = runSinglePrediction(deployment.getSecondaryTrainingId(), input, runtimeConfig, true);
                response.put("secondaryPrediction", secondaryResult.get("prediction"));
                response.put("rawSecondaryPrediction", secondaryResult.get("rawPrediction"));
                response.put("secondaryAlert", secondaryResult.get("alert"));
                response.put("secondaryAlertMessage", secondaryResult.get("alertMessage"));
                response.put("secondaryModelType", secondaryResult.get("modelType"));
            } catch (Exception e) {
                // If secondary fails, log it but maybe don't fail the whole request? 
                // For now, let's fail if it's critical. User said "Composite Deployment", so both should work.
                throw new IllegalStateException("辅助模型预测失败: " + e.getMessage(), e);
            }
        }

        service.setLastHeartbeat(new Date());
        modelServiceRepository.save(service);
        return response;
    }

    private Map<String, Object> runSinglePrediction(Long trainingId, Map<String, Object> input, Map<String, Object> runtimeConfig, boolean isSecondary) {
        Map<String, Object> normalizedInput = normalizeInput(input);
        ModelStoreService.StoredModel storedModel = modelStoreService.get(trainingId).orElseGet(() -> {
            ModelTraining training = modelTrainingRepository.findById(trainingId)
                    .orElseThrow(() -> new IllegalArgumentException("训练任务不存在"));
            ModelTrainer trainer = modelTrainerFactory.getTrainer(training.getModelType());
            return deserializeAndCache(training, trainer);
        });

        ModelTraining training = modelTrainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("训练任务不存在"));
        final String targetVariable = training.getTargetVariable() == null || training.getTargetVariable().isBlank()
                ? "productionRate"
                : uploadedDataNormalizer.toCanonicalKey(training.getTargetVariable().trim());
        String[] features = Arrays.stream(getFeatureArray(training.getSelectedFeatures()))
                .map(UploadedDataNormalizer::toCanonicalKey)
                .filter(value -> !value.isEmpty())
                .filter(value -> !"productionRate".equals(value))
                .filter(value -> !"energyConsumption".equals(value))
                .distinct()
                .toArray(String[]::new);
        List<String> requiredFeatures = Arrays.stream(features)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .filter(value -> !targetVariable.equals(value))
                .toList();
        
        if (!requiredFeatures.isEmpty()) {
            List<String> missing = new ArrayList<>();
            for (String feature : requiredFeatures) {
                Object value = resolveInputValue(normalizedInput, feature);
                if (!hasInputValue(value)) {
                    missing.add(feature);
                }
            }
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("缺少预测特征: " + String.join(", ", missing));
            }
        }

        ModelTrainer trainer = modelTrainerFactory.getTrainer(training.getModelType());
        var result = evaluateSingle(training, trainer, features, storedModel, normalizedInput);
        if (!result.isSuccess()) {
            throw new IllegalStateException("模型推理失败: " + result.getMessage());
        }

        Double prediction = null;
        if (result.getPredictedValues() != null && !result.getPredictedValues().isEmpty()) {
            prediction = result.getPredictedValues().get(0);
        } else {
            throw new IllegalStateException("模型推理失败: 未返回预测值");
        }
        Double rawPrediction = prediction;
        
        // Alarm logic (only for primary for now, or maybe secondary has different limits?)
        // Assuming config has "alarmLowerLimit" for primary. 
        // If secondary needs limits, config needs to be nested or prefixed.
        // For simplicity, I'll only apply alarm logic to primary if !isSecondary, 
        // OR check if config has "secondaryAlarmLowerLimit".
        
        Double lowerLimit = null;
        Double upperLimit = null;
        if (!isSecondary) {
            lowerLimit = parseDouble(runtimeConfig.get("alarmLowerLimit"));
            upperLimit = parseDouble(runtimeConfig.get("alarmUpperLimit"));
        } else {
             // Maybe support secondary limits later
             lowerLimit = parseDouble(runtimeConfig.get("secondaryAlarmLowerLimit"));
             upperLimit = parseDouble(runtimeConfig.get("secondaryAlarmUpperLimit"));
        }

        boolean alert = false;
        String alertMessage = "";
        if (rawPrediction != null) {
            if (lowerLimit != null && rawPrediction < lowerLimit) {
                alert = true;
                alertMessage = "低于下限";
            } else if (upperLimit != null && rawPrediction > upperLimit) {
                alert = true;
                alertMessage = "高于上限";
            }
        }

        boolean safetyHold = false;
        Object safetyHoldValue = runtimeConfig.getOrDefault("safetyHold", runtimeConfig.get("autoFallback"));
        if (safetyHoldValue instanceof Boolean bool) {
            safetyHold = bool;
        } else if (safetyHoldValue != null) {
            safetyHold = Boolean.parseBoolean(safetyHoldValue.toString());
        }

        boolean fallbackApplied = false;
        boolean fallbackSkippedMissingActual = false;
        if (alert && safetyHold && rawPrediction != null && !isSecondary) {
            Double actual = parseDouble(resolveInputValue(normalizedInput, targetVariable));
            if (actual != null) {
                prediction = actual;
                fallbackApplied = true;
                alertMessage = "警报: 预测值超出安全阈值 [已触发安全熔断，回退到熔断回退值]";
            } else {
                fallbackSkippedMissingActual = true;
                alertMessage = "警报: 预测值超出安全阈值 [未提供熔断回退值，未执行安全熔断回退]";
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("modelType", storedModel.getModelType());
        res.put("rawPrediction", rawPrediction);
        res.put("prediction", prediction);
        res.put("alert", alert);
        res.put("alertMessage", alertMessage);
        res.put("features", Arrays.asList(features));
        res.put("safetyHoldTriggered", fallbackApplied);
        res.put("safetyHoldSkipped", fallbackSkippedMissingActual);
        res.put("fallbackType", fallbackApplied ? "safety_hold_actual" : null);
        res.put("fallbackSource", fallbackApplied ? targetVariable : null);
        return res;
    }

    public Map<String, Object> explainPrediction(Long serviceId, Map<String, Object> input) {
        Optional<ModelService> optionalService = modelServiceRepository.findById(serviceId);
        if (optionalService.isEmpty()) {
            throw new IllegalArgumentException("服务不存在");
        }
        ModelService service = optionalService.get();
        if (!"running".equals(service.getStatus())) {
            throw new IllegalStateException("服务未运行");
        }
        if (input == null) {
            throw new IllegalArgumentException("预测输入不能为空");
        }
        Map<String, Object> runtimeConfig = parseConfig(service.getServiceConfig());
        ModelDeployment deployment = modelDeploymentRepository.findById(service.getDeploymentId())
                .orElseThrow(() -> new IllegalArgumentException("部署记录不存在"));
        Long trainingId = deployment.getTrainingId();
        ModelStoreService.StoredModel storedModel = modelStoreService.get(trainingId).orElseGet(() -> {
            ModelTraining training = modelTrainingRepository.findById(trainingId)
                    .orElseThrow(() -> new IllegalArgumentException("训练任务不存在"));
            ModelTrainer trainer = modelTrainerFactory.getTrainer(training.getModelType());
            return deserializeAndCache(training, trainer);
        });
        ModelTraining training = modelTrainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("训练任务不存在"));
        String[] features = Arrays.stream(getFeatureArray(training.getSelectedFeatures()))
                .map(UploadedDataNormalizer::toCanonicalKey)
                .filter(value -> !value.isEmpty())
                .filter(value -> !"productionRate".equals(value))
                .filter(value -> !"energyConsumption".equals(value))
                .distinct()
                .toArray(String[]::new);
        Map<String, Object> normalizedInput = normalizeInput(input);
        final String targetVariable = training.getTargetVariable() == null || training.getTargetVariable().isBlank()
                ? "productionRate"
                : uploadedDataNormalizer.toCanonicalKey(training.getTargetVariable().trim());
        List<String> requiredFeatures = Arrays.stream(features)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .filter(value -> !targetVariable.equals(value))
                .toList();
        if (!requiredFeatures.isEmpty()) {
            List<String> missing = new ArrayList<>();
            for (String feature : requiredFeatures) {
                Object value = resolveInputValue(normalizedInput, feature);
                if (!hasInputValue(value)) {
                    missing.add(feature);
                }
            }
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("缺少预测特征: " + String.join(", ", missing));
            }
        }
        ModelTrainer trainer = modelTrainerFactory.getTrainer(training.getModelType());
        TrainingResult baseResult = evaluateSingle(training, trainer, features, storedModel, normalizedInput);
        if (!baseResult.isSuccess()) {
            throw new IllegalStateException("解释性分析失败: " + baseResult.getMessage());
        }
        Double rawPrediction = null;
        if (baseResult.getPredictedValues() != null && !baseResult.getPredictedValues().isEmpty()) {
            rawPrediction = baseResult.getPredictedValues().get(0);
        } else {
            throw new IllegalStateException("解释性分析失败: 未返回预测值");
        }
        Double prediction = rawPrediction;
        Double lowerLimit = parseDouble(runtimeConfig.get("alarmLowerLimit"));
        Double upperLimit = parseDouble(runtimeConfig.get("alarmUpperLimit"));
        boolean alert = false;
        String alertMessage = "";
        if (rawPrediction != null) {
            if (lowerLimit != null && rawPrediction < lowerLimit) {
                alert = true;
                alertMessage = "低于下限";
            } else if (upperLimit != null && rawPrediction > upperLimit) {
                alert = true;
                alertMessage = "高于上限";
            }
        }
        boolean safetyHold = false;
        Object safetyHoldValue = runtimeConfig.getOrDefault("safetyHold", runtimeConfig.get("autoFallback"));
        if (safetyHoldValue instanceof Boolean bool) {
            safetyHold = bool;
        } else if (safetyHoldValue != null) {
            safetyHold = Boolean.parseBoolean(safetyHoldValue.toString());
        }
        boolean fallbackApplied = false;
        boolean fallbackSkippedMissingActual = false;
        if (alert && safetyHold && rawPrediction != null) {
            Double actual = parseDouble(resolveInputValue(normalizedInput, targetVariable));
            if (actual != null) {
                prediction = actual;
                fallbackApplied = true;
                alertMessage = "警报: 预测值超出安全阈值 [已触发安全熔断，强制维持当前工况不变]";
            } else {
                fallbackSkippedMissingActual = true;
                alertMessage = "警报: 预测值超出安全阈值 [未提供当前实际值，未执行安全熔断回退]";
            }
        }
        List<Map<String, Object>> sensitivity = new ArrayList<>();
        for (String feature : requiredFeatures) {
            Double baseValue = parseDouble(resolveInputValue(normalizedInput, feature));
            if (baseValue == null) {
                continue;
            }
            double step = Math.max(Math.abs(baseValue) * 0.01, 1d);
            double upValue = baseValue + step;
            double downValue = baseValue - step;
            Map<String, Object> upInput = new HashMap<>(normalizedInput);
            upInput.put(feature, upValue);
            Map<String, Object> downInput = new HashMap<>(normalizedInput);
            downInput.put(feature, downValue);
            TrainingResult upResult = evaluateSingle(training, trainer, features, storedModel, upInput);
            TrainingResult downResult = evaluateSingle(training, trainer, features, storedModel, downInput);
            if (!upResult.isSuccess()) {
                throw new IllegalStateException("解释性分析失败(上调): " + upResult.getMessage());
            }
            if (!downResult.isSuccess()) {
                throw new IllegalStateException("解释性分析失败(下调): " + downResult.getMessage());
            }
            Double upPrediction = upResult.getPredictedValues() != null && !upResult.getPredictedValues().isEmpty()
                    ? upResult.getPredictedValues().get(0)
                    : null;
            Double downPrediction = downResult.getPredictedValues() != null && !downResult.getPredictedValues().isEmpty()
                    ? downResult.getPredictedValues().get(0)
                    : null;
            Map<String, Object> row = new HashMap<>();
            row.put("feature", feature);
            row.put("baseValue", baseValue);
            row.put("step", step);
            row.put("upValue", upValue);
            row.put("downValue", downValue);
            row.put("upPrediction", upPrediction);
            row.put("downPrediction", downPrediction);
            if (rawPrediction != null) {
                row.put("upDelta", upPrediction != null ? upPrediction - rawPrediction : null);
                row.put("downDelta", downPrediction != null ? downPrediction - rawPrediction : null);
            }
            sensitivity.add(row);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("serviceId", serviceId);
        response.put("modelType", storedModel.getModelType());
        response.put("rawPrediction", rawPrediction);
        response.put("prediction", prediction);
        response.put("alert", alert);
        response.put("alertMessage", alertMessage);
        response.put("features", Arrays.asList(features));
        response.put("input", normalizedInput);
        response.put("featureImportance", baseResult.getFeatureImportance());
        response.put("sensitivity", sensitivity);
        response.put("safetyHoldTriggered", fallbackApplied);
        response.put("safetyHoldSkipped", fallbackSkippedMissingActual);
        response.put("fallbackType", fallbackApplied ? "safety_hold_actual" : null);
        response.put("fallbackSource", fallbackApplied ? targetVariable : null);
        return response;
    }

    public List<DeploymentHistoryDTO> getHistory() {
        List<ModelDeployment> deployments = modelDeploymentRepository.findAllByOrderByDeployTimeDesc();
        List<Long> deploymentIds = deployments.stream().map(ModelDeployment::getId).toList();
        Map<Long, ModelService> serviceMap = new HashMap<>();
        if (!deploymentIds.isEmpty()) {
            List<ModelService> services = modelServiceRepository.findByDeploymentIdIn(deploymentIds);
            for (ModelService service : services) {
                serviceMap.put(service.getDeploymentId(), service);
            }
        }
        List<DeploymentHistoryDTO> result = new ArrayList<>();
        for (ModelDeployment deployment : deployments) {
            DeploymentHistoryDTO dto = new DeploymentHistoryDTO();
            dto.setId(deployment.getId());
            dto.setModelName(modelTypeLabel(deployment.getTrainingId()));
            dto.setEnvironment(deployment.getEnvironment());
            dto.setName(deployment.getName());
            dto.setVersion(deployment.getVersion());
            dto.setDeployTime(formatTime(deployment.getDeployTime()));
            dto.setStatus(deployment.getStatus().getValue());
            String originalConfig = deployment.getConfig();
            String sanitizedConfig = sanitizeConfigJsonString(originalConfig);
            dto.setConfig(sanitizedConfig);
            if (!Objects.equals(originalConfig, sanitizedConfig)) {
                deployment.setConfig(sanitizedConfig);
                modelDeploymentRepository.save(deployment);
            }
            ModelService service = serviceMap.get(deployment.getId());
            if (service != null) {
                sanitizeServiceConfigIfNeeded(service);
            }
            dto.setServiceUrl(service != null ? service.getUrl() : "");
            result.add(dto);
        }
        return result;
    }

    public List<ServiceInfoDTO> getServices() {
        List<ModelService> services = modelServiceRepository.findAllByOrderByIdDesc();
        List<Long> deploymentIds = services.stream()
                .map(ModelService::getDeploymentId)
                .filter(id -> id != null)
                .toList();
        Map<Long, Long> trainingIdMap = new HashMap<>();
        Map<Long, String> targetVariableMap = new HashMap<>();
        if (!deploymentIds.isEmpty()) {
            Iterable<ModelDeployment> deployments = modelDeploymentRepository.findAllById(deploymentIds);
            for (ModelDeployment deployment : deployments) {
                trainingIdMap.put(deployment.getId(), deployment.getTrainingId());
            }
            List<Long> trainingIds = trainingIdMap.values().stream().filter(id -> id != null).distinct().toList();
            if (!trainingIds.isEmpty()) {
                Iterable<ModelTraining> trainings = modelTrainingRepository.findAllById(trainingIds);
                for (ModelTraining training : trainings) {
                    if (training != null && training.getId() != null) {
                        targetVariableMap.put(training.getId(), training.getTargetVariable());
                    }
                }
            }
        }
        List<ServiceInfoDTO> result = new ArrayList<>();
        for (ModelService service : services) {
            sanitizeServiceConfigIfNeeded(service);
            Long trainingId = trainingIdMap.get(service.getDeploymentId());
            result.add(toServiceInfo(service, trainingId, targetVariableMap.get(trainingId)));
        }
        return result;
    }

    private ServiceInfoDTO updateServiceStatus(
            Long serviceId,
            String status,
            String message,
            boolean ensureModel,
            boolean resetModel,
            String operator,
            String action
    ) {
        Optional<ModelService> optionalService = modelServiceRepository.findById(serviceId);
        if (optionalService.isEmpty()) {
            throw new IllegalArgumentException("服务不存在");
        }
        ModelService service = optionalService.get();
        ModelDeployment deployment = modelDeploymentRepository.findById(service.getDeploymentId())
                .orElseThrow(() -> new IllegalArgumentException("部署记录不存在"));
        if (resetModel) {
            modelStoreService.remove(deployment.getTrainingId());
        }
        if (ensureModel) {
            ensureModelLoaded(deployment);
        }
        service.setStatus(status);
        if ("running".equals(status)) {
            service.setLastHeartbeat(new Date());
        }
        modelServiceRepository.save(service);
        // if ("running".equals(status)) {
        //     archiveOtherRunningServices(service.getId(), operator);
        // }
        appendDeploymentLog(deployment, message);
        recordAudit(operator, action, "service", serviceId, "success", message);
        String targetVariable = modelTrainingRepository.findById(deployment.getTrainingId())
                .map(ModelTraining::getTargetVariable)
                .orElse(null);
        return toServiceInfo(service, deployment.getTrainingId(), targetVariable);
    }

    private void archiveOtherRunningServices(Long activeServiceId, String operator) {
        if (activeServiceId == null) {
            return;
        }
        List<ModelService> running = modelServiceRepository.findByStatus("running");
        if (running == null || running.isEmpty()) {
            return;
        }
        for (ModelService service : running) {
            if (service == null || activeServiceId.equals(service.getId())) {
                continue;
            }
            service.setStatus("stopped");
            modelServiceRepository.save(service);
            modelDeploymentRepository.findById(service.getDeploymentId()).ifPresent(deployment -> {
                appendDeploymentLog(deployment, buildOperatorMessage(operator, "已归档并下线（存在新的运行中模型）"));
                modelStoreService.remove(deployment.getTrainingId());
            });
        }
    }


    private ModelStoreService.StoredModel deserializeAndCache(ModelTraining training, ModelTrainer trainer) {
        try {
            Object model = trainer.deserializeModel(training.getModelBytes());
            Object preprocessor = trainer.deserializePreprocessor(training.getPreprocessorBytes());
            String[] features = getFeatureArray(training.getSelectedFeatures());
            ModelStoreService.StoredModel storedModel = new ModelStoreService.StoredModel(
                    training.getModelType(),
                    model,
                    preprocessor,
                    features
            );
            modelStoreService.save(training.getId(), storedModel);
            return storedModel;
        } catch (Exception e) {
            throw new IllegalStateException("模型反序列化失败: " + e.getMessage(), e);
        }
    }

    private void ensureModelLoaded(ModelDeployment deployment) {
        Long trainingId = deployment.getTrainingId();
        if (modelStoreService.get(trainingId).isPresent()) {
            return;
        }
        ModelTraining training = modelTrainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("训练任务不存在"));
        ModelTrainer trainer = modelTrainerFactory.getTrainer(training.getModelType());
        deserializeAndCache(training, trainer);
    }

    private ServiceInfoDTO toServiceInfo(ModelService service, Long trainingId, String targetVariable) {
        ServiceInfoDTO dto = new ServiceInfoDTO();
        dto.setId(service.getId());
        dto.setDeploymentId(service.getDeploymentId());
        dto.setTrainingId(trainingId);
        dto.setTargetVariable(targetVariable);
        dto.setName(service.getName());
        dto.setModelName(service.getModelName());
        dto.setEnvironment(service.getEnvironment());
        dto.setStatus(service.getStatus());
        dto.setUrl(service.getUrl());
        dto.setVersion(service.getVersion());
        dto.setServiceConfig(service.getServiceConfig());
        return dto;
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Map<String, Object> sanitizeRuntimeConfig(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> sanitized = new HashMap<>(source);
        REMOVED_RUNTIME_CONFIG_KEYS.forEach(sanitized::remove);
        return sanitized;
    }

    private String sanitizeConfigJsonString(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return configJson;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> sanitized = sanitizeRuntimeConfig(parsed);
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            return configJson;
        }
    }

    private void sanitizeServiceConfigIfNeeded(ModelService service) {
        if (service == null) {
            return;
        }
        String original = service.getServiceConfig();
        String sanitized = sanitizeConfigJsonString(original);
        if (!Objects.equals(original, sanitized)) {
            service.setServiceConfig(sanitized);
            modelServiceRepository.save(service);
        }
    }

    private String writeConfig(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new IllegalStateException("服务配置序列化失败: " + e.getMessage(), e);
        }
    }

    private void applyServiceFields(ModelService service, Map<String, Object> config) {
        Object name = config.get("name");
        if (name != null && !name.toString().isBlank()) {
            service.setName(name.toString());
        }
        Object environment = config.get("environment");
        if (environment != null && !environment.toString().isBlank()) {
            service.setEnvironment(environment.toString());
        }
        Object version = config.get("version");
        if (version != null && !version.toString().isBlank()) {
            service.setVersion(version.toString());
        }
    }

    private String buildOperatorMessage(String operator, String message) {
        if (operator == null || operator.isBlank()) {
            return message;
        }
        return "操作人: " + operator + " - " + message;
    }

    private void recordAudit(String username, String action, String targetType, Long targetId, String result, String detail) {
        if (targetId == null) {
            return;
        }
        String normalizedUsername = (username == null || username.isBlank()) ? "unknown" : username.trim();
        AuditLog auditLog = new AuditLog();
        auditLog.setUsername(normalizedUsername);
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setTime(new Date());
        auditLog.setResult(result);
        auditLog.setDetail(detail);
        auditLogRepository.save(auditLog);
    }

    private void runDeploymentAsync(Long deploymentId, Long trainingId, Long secondaryTrainingId, Long serviceId) {
        try {
            Future<?> future = executorService.submit(() -> {
                ModelDeployment deployment = null;
                try {
                    deployment = modelDeploymentRepository.findById(deploymentId).orElse(null);
                    if (deployment == null || deployment.getStatus() == DeploymentStatus.CANCELED) {
                        return;
                    }
                    appendDeploymentLog(deployment, "加载主模型文件");
                    ModelTraining training = modelTrainingRepository.findById(trainingId)
                            .orElseThrow(() -> new IllegalArgumentException("训练任务不存在"));
                    ModelTrainer trainer = modelTrainerFactory.getTrainer(training.getModelType());
                    deserializeAndCache(training, trainer);

                    if (secondaryTrainingId != null) {
                        appendDeploymentLog(deployment, "加载辅助模型文件");
                        ModelTraining secondaryTraining = modelTrainingRepository.findById(secondaryTrainingId)
                                .orElseThrow(() -> new IllegalArgumentException("辅助训练任务不存在"));
                        ModelTrainer secondaryTrainer = modelTrainerFactory.getTrainer(secondaryTraining.getModelType());
                        deserializeAndCache(secondaryTraining, secondaryTrainer);
                    }

                    if (isDeploymentCanceled(deploymentId) || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    appendDeploymentLog(deployment, "配置部署环境");
                    if (isDeploymentCanceled(deploymentId) || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    appendDeploymentLog(deployment, "启动服务实例");
                    if (isDeploymentCanceled(deploymentId) || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    appendDeploymentLog(deployment, "服务健康检查");
                    ensureServiceHealthy(serviceId, trainingId);
                    if (isDeploymentCanceled(deploymentId) || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    deployment.setStatus(DeploymentStatus.COMPLETED);
                    modelDeploymentRepository.save(deployment);
                    new TransactionTemplate(transactionManager).execute(status -> {
                        modelServiceRepository.findById(serviceId).ifPresent(service -> {
                            service.setStatus("running");
                            service.setLastHeartbeat(new Date());
                            modelServiceRepository.save(service);
                        });
                        return null;
                    });
                    appendDeploymentLog(deployment, "部署完成");
                } catch (Exception e) {
                    if (isDeploymentCanceled(deploymentId) || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    ModelDeployment latest = modelDeploymentRepository.findById(deploymentId).orElse(null);
                    if (latest == null) {
                        return;
                    }
                    latest.setStatus(DeploymentStatus.FAILED);
                    modelDeploymentRepository.save(latest);
                    modelServiceRepository.findById(serviceId).ifPresent(service -> {
                        service.setStatus("stopped");
                        modelServiceRepository.save(service);
                    });
                    appendDeploymentLog(latest, "部署失败: " + e.getMessage());
                } finally {
                    deploymentFutures.remove(deploymentId);
                }
            });
            deploymentFutures.put(deploymentId, future);
        } catch (RejectedExecutionException e) {
            ModelDeployment deployment = modelDeploymentRepository.findById(deploymentId).orElse(null);
            if (deployment == null || deployment.getStatus() == DeploymentStatus.CANCELED) {
                return;
            }
            deployment.setStatus(DeploymentStatus.FAILED);
            modelDeploymentRepository.save(deployment);
            modelServiceRepository.findById(serviceId).ifPresent(service -> {
                service.setStatus("stopped");
                modelServiceRepository.save(service);
            });
            appendDeploymentLog(deployment, "部署失败: 当前部署任务过多，请稍后重试");
        }
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

    private boolean isDeploymentCanceled(Long deploymentId) {
        return modelDeploymentRepository.findById(deploymentId)
                .map(item -> item.getStatus() == DeploymentStatus.CANCELED)
                .orElse(true);
    }

    private void ensureServiceHealthy(Long serviceId, Long trainingId) {
        if (modelStoreService.get(trainingId).isEmpty()) {
            throw new IllegalStateException("模型缓存不可用");
        }
        ModelService service = modelServiceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalStateException("服务实例不存在"));
        if (!"running".equals(service.getStatus())) {
            throw new IllegalStateException("服务未处于运行状态");
        }
    }

    private String[] getFeatureArray(String selectedFeatures) {
        if (selectedFeatures == null || selectedFeatures.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(selectedFeatures.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }

    private ProductionData buildProductionData(Map<String, Object> input) {
        ProductionData data = new ProductionData();
        Object furnaceIdValue = resolveInputValue(input, "furnaceId");
        data.setFurnaceId(String.valueOf(furnaceIdValue != null ? furnaceIdValue : "default"));
        data.setTimestamp(new Date());
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String canonicalKey = uploadedDataNormalizer.toCanonicalKey(entry.getKey());
            BiConsumer<ProductionData, Double> setter = FEATURE_SETTER_MAP.get(canonicalKey);
            if (setter == null) {
                continue;
            }
            Double value = parseDouble(entry.getValue());
            if (value != null) {
                setter.accept(data, value);
            }
        }
        return data;
    }

    private TrainingResult evaluateSingle(
            ModelTraining training,
            ModelTrainer trainer,
            String[] features,
            ModelStoreService.StoredModel storedModel,
            Map<String, Object> input
    ) {
        ProductionData data = buildProductionData(input);
        String targetVariable = training.getTargetVariable() == null || training.getTargetVariable().isBlank()
                ? "productionRate"
                : uploadedDataNormalizer.toCanonicalKey(training.getTargetVariable().trim());
        Double targetValue = parseDouble(resolveInputValue(input, targetVariable));
        if (targetValue == null) {
            targetValue = 0d;
        }
        trainer.setTargetValue(data, targetVariable, targetValue);
        return trainer.evaluate(
                List.of(data),
                training,
                training.getModelConfig(),
                features,
                storedModel.getModel(),
                storedModel.getPreprocessor()
        );
    }

    private Map<String, Object> normalizeInput(Map<String, Object> input) {
        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String rawKey = entry.getKey();
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }
            String canonicalKey = uploadedDataNormalizer.toCanonicalKey(rawKey);
            if (canonicalKey == null || canonicalKey.isBlank()) {
                canonicalKey = rawKey.trim();
            }
            normalized.put(canonicalKey, entry.getValue());
            if (!canonicalKey.equals(rawKey)) {
                normalized.putIfAbsent(rawKey, entry.getValue());
            }
        }
        return normalized;
    }

    private Object resolveInputValue(Map<String, Object> input, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String canonical = uploadedDataNormalizer.toCanonicalKey(key);
        if (input.containsKey(canonical)) {
            return input.get(canonical);
        }
        return input.get(key);
    }

    private boolean hasInputValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String str) {
            return !str.isBlank();
        }
        return true;
    }

    private static Map<String, BiConsumer<ProductionData, Double>> buildFeatureSetterMap() {
        Map<String, BiConsumer<ProductionData, Double>> map = new HashMap<>();
        map.put("temperature", ProductionData::setTemperature);
        map.put("pressure", ProductionData::setPressure);
        map.put("windVolume", ProductionData::setWindVolume);
        map.put("coalInjection", ProductionData::setCoalInjection);
        map.put("materialHeight", ProductionData::setMaterialHeight);
        map.put("gasFlow", ProductionData::setGasFlow);
        map.put("oxygenLevel", ProductionData::setOxygenLevel);
        map.put("productionRate", ProductionData::setProductionRate);
        map.put("energyConsumption", ProductionData::setEnergyConsumption);
        map.put("hotMetalTemperature", ProductionData::setHotMetalTemperature);
        map.put("constantSignal", ProductionData::setConstantSignal);
        map.put("siliconContent", ProductionData::setSiliconContent);
        return map;
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String str = value.toString().trim();
        if (str.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> buildDeploymentLogs() {
        String now = formatTime(new Date());
        List<String> logs = new ArrayList<>();
        logs.add(now + " - 部署开始");
        logs.add(now + " - 加载模型文件");
        logs.add(now + " - 配置部署环境");
        logs.add(now + " - 启动服务实例");
        return logs;
    }

    private List<String> buildCompletedLogs(List<String> baseLogs) {
        List<String> logs = new ArrayList<>(baseLogs);
        logs.add(formatTime(new Date()) + " - 部署完成");
        return logs;
    }

    private void appendDeploymentLog(ModelDeployment deployment, String message) {
        String logs = deployment.getLogs();
        String line = formatTime(new Date()) + " - " + message;
        if (logs == null || logs.isBlank()) {
            deployment.setLogs(line);
        } else {
            deployment.setLogs(logs + "\n" + line);
        }
        modelDeploymentRepository.save(deployment);
    }

    private String formatTime(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private String toModelDisplayName(String modelType) {
        return switch (modelType) {
            case "neural_network" -> "神经网络模型";
            case "random_forest" -> "随机森林模型";
            case "gradient_boosting" -> "梯度提升树模型";
            case "gpr" -> "高斯过程回归模型";
            default -> modelType;
        };
    }

    private String modelTypeLabel(Long trainingId) {
        return modelTrainingRepository.findById(trainingId)
                .map(ModelTraining::getModelType)
                .map(this::toModelDisplayName)
                .orElse("未知模型");
    }
}
