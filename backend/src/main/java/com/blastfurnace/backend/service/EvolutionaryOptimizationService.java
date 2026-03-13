package com.blastfurnace.backend.service;

import com.blastfurnace.backend.dto.EvolutionResult;
import com.blastfurnace.backend.dto.EvolutionaryOptimizationRequest;
import com.blastfurnace.backend.dto.OptimizationSolutionDTO;
import com.blastfurnace.backend.model.DeploymentStatus;
import com.blastfurnace.backend.model.ModelDeployment;
import com.blastfurnace.backend.model.ModelService;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.optimization.Chromosome;
import com.blastfurnace.backend.optimization.GeneticOptimizer;
import com.blastfurnace.backend.optimization.NSGA2Optimizer;
import com.blastfurnace.backend.optimization.ParameterRanges;
import com.blastfurnace.backend.optimization.ParameterRanges.Range;
import com.blastfurnace.backend.repository.ModelDeploymentRepository;
import com.blastfurnace.backend.repository.ModelServiceRepository;
import com.blastfurnace.backend.repository.ModelTrainingRepository;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import com.blastfurnace.backend.service.trainer.ModelTrainer;
import com.blastfurnace.backend.service.trainer.ModelTrainerFactory;
import com.blastfurnace.backend.service.trainer.TrainingResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class EvolutionaryOptimizationService {
    private static final Logger log = LoggerFactory.getLogger(EvolutionaryOptimizationService.class);
    private static final double PRODUCTION_MIN = 30.0;
    private static final double PRODUCTION_MAX = 100.0;
    private static final double ENERGY_MIN = 1000.0;
    private static final double ENERGY_MAX = 2000.0;
    private static final double TEMP_MIN = 1300.0;
    private static final double TEMP_MAX = 1600.0;
    private static final double PRESSURE_MIN = 150.0;
    private static final double PRESSURE_MAX = 300.0;
    private static final double WIND_VOLUME_MIN = 3000.0;
    private static final double WIND_VOLUME_MAX = 6000.0;
    private static final double COAL_INJECTION_MIN = 100.0;
    private static final double COAL_INJECTION_MAX = 200.0;
    private static final double GAS_FLOW_MIN = 2000.0;
    private static final double GAS_FLOW_MAX = 5000.0;
    private static final double OXYGEN_MIN = 21.0;
    private static final double OXYGEN_MAX = 25.0;
    private static final double HEIGHT_MIN = 2.0;
    private static final double HEIGHT_MAX = 5.0;
    private static final Set<String> EXCLUDED_SEARCH_FEATURES = Set.of(
            "productionRate",
            "energyConsumption",
            "hotMetalTemperature",
            "siliconContent",
            "constantSignal",
            "timestamp"
    );

    private final ModelTrainingService modelTrainingService;
    private final ModelServiceRepository modelServiceRepository;
    private final ModelDeploymentRepository modelDeploymentRepository;
    private final ModelTrainingRepository modelTrainingRepository;
    private final ModelTrainerFactory modelTrainerFactory;
    private final ComparisonHistoryService comparisonHistoryService;
    private final ProductionDataRepository productionDataRepository;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;
    private final GeneticOptimizer geneticOptimizer = new GeneticOptimizer();
    private final NSGA2Optimizer nsga2Optimizer = new NSGA2Optimizer();
    private final ThreadPoolExecutor optimizationExecutor = new ThreadPoolExecutor(
            1,
            2,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(32),
            new ThreadPoolExecutor.AbortPolicy()
    );
    private final Map<String, OptimizationTaskState> optimizationTasks = new ConcurrentHashMap<>();

    public EvolutionResult startEvolutionaryOptimization(EvolutionaryOptimizationRequest config) {
        if (config == null) {
            throw new IllegalArgumentException("演化配置不能为空");
        }
        String runId = resolveRunId(config.getRunId());
        config.setRunId(runId);
        OptimizationTaskState existingState = optimizationTasks.get(runId);
        if (existingState != null && "running".equals(existingState.status)) {
            return existingState.toResult();
        }
        OptimizationTaskState taskState = new OptimizationTaskState(runId);
        optimizationTasks.put(runId, taskState);
        try {
            optimizationExecutor.submit(() -> executeEvolutionTask(config, taskState));
        } catch (Exception e) {
            taskState.status = "failed";
            taskState.progress = 100;
            taskState.message = "任务提交失败: " + e.getMessage();
            taskState.finishedAt = System.currentTimeMillis();
        }
        return taskState.toResult();
    }

    public EvolutionResult getOptimizationProgress(String runId) {
        String lookupRunId = runId == null ? null : runId.trim();
        OptimizationTaskState taskState = lookupRunId == null || lookupRunId.isEmpty() ? null : optimizationTasks.get(lookupRunId);
        if (taskState == null) {
            EvolutionResult result = new EvolutionResult();
            result.setRunId(runId);
            result.setStatus("not_found");
            result.setProgress(100);
            result.setMessage("任务不存在或已过期");
            return result;
        }
        return taskState.toResult();
    }

    public EvolutionResult getOptimizationResult(String runId) {
        String lookupRunId = runId == null ? null : runId.trim();
        OptimizationTaskState taskState = lookupRunId == null || lookupRunId.isEmpty() ? null : optimizationTasks.get(lookupRunId);
        if (taskState == null) {
            EvolutionResult result = new EvolutionResult();
            result.setRunId(runId);
            result.setStatus("not_found");
            result.setProgress(100);
            result.setMessage("任务不存在或已过期");
            return result;
        }
        if (taskState.result != null) {
            return taskState.result;
        }
        return taskState.toResult();
    }

    private void executeEvolutionTask(EvolutionaryOptimizationRequest config, OptimizationTaskState taskState) {
        try {
            taskState.progress = 10;
            taskState.message = "演化任务执行中";
            EvolutionResult result = runEvolutionaryOptimization(config, taskState);
            result.setRunId(taskState.runId);
            result.setStatus("completed");
            result.setProgress(100);
            result.setMessage("演化任务完成");
            result.setStartedAt(taskState.startedAt);
            result.setFinishedAt(System.currentTimeMillis());
            taskState.status = "completed";
            taskState.progress = 100;
            taskState.message = "演化任务完成";
            taskState.finishedAt = result.getFinishedAt();
            taskState.result = result;
        } catch (Exception e) {
            taskState.status = "failed";
            taskState.progress = 100;
            taskState.message = e.getMessage();
            taskState.finishedAt = System.currentTimeMillis();
            EvolutionResult failed = taskState.toResult();
            failed.setMessage(e.getMessage());
            taskState.result = failed;
            log.error("演化任务失败, runId={}", taskState.runId, e);
        }
    }

    private String resolveRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return runId.trim();
    }

    private EvolutionResult runEvolutionaryOptimization(EvolutionaryOptimizationRequest config, OptimizationTaskState taskState) {
        String runId = config.getRunId();
        int maxIterations = Math.max(1, sysConfigService.getInt("evo_max_iterations", 30));
        int maxPopulation = Math.max(1, sysConfigService.getInt("evo_max_population", 40));

        int requestedIterations = config.getGenerations() != null ? config.getGenerations() : maxIterations;
        int requestedPopulation = config.getPopulationSize() != null ? config.getPopulationSize() : maxPopulation;

        int generations = Math.min(Math.max(1, requestedIterations), maxIterations);
        int populationSize = Math.min(Math.max(1, requestedPopulation), maxPopulation);
        String mode = config.getMode() != null ? config.getMode() : "BALANCED";

        taskState.progress = 25;
        double[] weights = resolveWeights(mode);
        Long serviceId = resolveServiceId(config.getServiceId());
        Function<ProductionData, PredictionOutcome> productionPredictor = resolvePredictor(serviceId, "productionRate");
        Function<ProductionData, PredictionOutcome> energyPredictor = resolvePredictor(serviceId, "energyConsumption");
        Function<ProductionData, PredictionOutcome> hotMetalTempPredictor = resolvePredictor(serviceId, "hotMetalTemperature");
        Function<ProductionData, PredictionOutcome> siliconPredictor = resolvePredictor(serviceId, "siliconContent");

        ProductionData baselineData = resolveBaselineData(config.getBaselineDataId());
        String furnaceId = config.getFurnaceId();
        String defaultFurnaceId = sysConfigService.getString("system_default_furnace_id", "BF-001");
        String furnaceKey = baselineData != null && baselineData.getFurnaceId() != null
                ? baselineData.getFurnaceId()
                : (furnaceId != null && !furnaceId.isBlank() ? furnaceId : defaultFurnaceId);

        List<ProductionData> recentData = productionDataRepository.findByFurnaceIdOrderByTimestampDesc(furnaceKey);
        if (recentData.size() > 120) {
            recentData = recentData.subList(0, 120);
        }
        ProductionData latestData = baselineData != null
                ? baselineData
                : recentData.stream()
                .findFirst()
                .orElseGet(() -> {
                    ProductionData defaultData = new ProductionData();
                    defaultData.setFurnaceId(furnaceKey);
                    defaultData.setTemperature(1400.0);
                    defaultData.setPressure(200.0);
                    defaultData.setWindVolume(5000.0);
                    defaultData.setCoalInjection(150.0);
                    defaultData.setGasFlow(3000.0);
                    defaultData.setOxygenLevel(22.0);
                    defaultData.setMaterialHeight(3.5);
                    defaultData.setProductionRate(55.0);
                    defaultData.setEnergyConsumption(1450.0);
                    return defaultData;
                });
        Range productionRange = resolveRange(
                recentData,
                ProductionData::getProductionRate,
                valueOrDefault(latestData.getProductionRate(), 0.0),
                PRODUCTION_MIN,
                PRODUCTION_MAX
        );
        Range energyRange = resolveRange(
                recentData,
                ProductionData::getEnergyConsumption,
                valueOrDefault(latestData.getEnergyConsumption(), 0.0),
                ENERGY_MIN,
                ENERGY_MAX
        );

        ModelTraining productionTraining = resolveTrainingForTarget(serviceId, "productionRate");
        ModelTraining energyTraining = resolveTrainingForTarget(serviceId, "energyConsumption");
        ModelTraining hotMetalTempTraining = resolveTrainingForTarget(serviceId, "hotMetalTemperature");
        ModelTraining siliconTraining = resolveTrainingForTarget(serviceId, "siliconContent");
        List<String> searchFeatures = resolveSearchFeatures(productionTraining, energyTraining, hotMetalTempTraining, siliconTraining);
        if (searchFeatures.isEmpty()) {
            throw new IllegalStateException("未找到可用于寻优的特征集合（请检查部署模型的 selectedFeatures）");
        }
        ParameterRanges parameterRanges = resolveParameterRanges(recentData, latestData, searchFeatures);
        Range costRange = resolveCostRange();
        double productionScale = resolveProductionScale(latestData, productionPredictor);

        IndustrialDataContract.ParameterSpec hotMetalTempSpec = IndustrialDataContract.findByAnyKey("hotMetalTemperature");
        IndustrialDataContract.ParameterSpec siliconSpec = IndustrialDataContract.findByAnyKey("siliconContent");
        if (hotMetalTempSpec == null || siliconSpec == null) {
            throw new IllegalStateException("缺少质量约束参数定义（hotMetalTemperature 或 siliconContent）");
        }
        
        Map<String, Double> baselineGenes = new HashMap<>();
        Map<String, Double> seedGenes = new HashMap<>();
        for (String feature : searchFeatures) {
            Range range = parameterRanges.getRange(feature);
            if (range == null) {
                continue;
            }
            Double baselineValueBoxed = getFeatureValue(latestData, feature);
            double baselineValue = baselineValueBoxed != null ? baselineValueBoxed : (range.min() + range.max()) / 2.0;
            double clamped = clampValue(baselineValue, range.min(), range.max());
            baselineGenes.put(feature, clamped);
            seedGenes.put(feature, clamped);
        }
        Chromosome seed = new Chromosome(seedGenes);

        taskState.progress = 55;
        Map<String, CachedEvaluation> evaluationCache = new ConcurrentHashMap<>();
        Function<Chromosome, CachedEvaluation> cachedEval = chromosome -> evaluationCache.computeIfAbsent(
                buildCacheKey(chromosome),
                key -> evaluateWithConstraints(chromosome, latestData, weights, productionPredictor, energyPredictor, hotMetalTempPredictor, siliconPredictor, productionScale, productionRange, energyRange, hotMetalTempSpec, siliconSpec, parameterRanges)
        );
        EvolutionResult evolutionResult = nsga2Optimizer.optimizeTopWithSeed(
                generations,
                populationSize,
                chromosome -> cachedEval.apply(chromosome).toNsgaEvaluation(),
                3,
                seed,
                parameterRanges,
                chromosome -> buildSolutionFromChromosome(chromosome, cachedEval.apply(chromosome), costRange, weights, mode, latestData, parameterRanges, searchFeatures, baselineGenes)
        );
        taskState.progress = 85;
        evolutionResult.setSearchFeatures(searchFeatures);
        evolutionResult.setRanges(parameterRanges.getRanges());
        evolutionResult.setBaselineGenes(baselineGenes);

        List<OptimizationSolutionDTO> results = evolutionResult.getTopSolutions();

        double baselineProduction = valueOrDefault(latestData.getProductionRate(), 0.0);
        String baselineModelUsed = "DeployedModel";
        if (baselineProduction <= 0.001) {
            PredictionOutcome baselineOutcome = predictProductionOutcome(latestData, productionPredictor, productionScale, productionRange);
            baselineProduction = baselineOutcome.value();
            baselineModelUsed = baselineOutcome.modelUsed();
        }
        double baselineEnergy = valueOrDefault(latestData.getEnergyConsumption(), 0.0);
        if (baselineEnergy <= 0.001) {
            baselineEnergy = clampValue(predictEnergyOutcome(latestData, energyPredictor, energyRange).value(), energyRange.min(), energyRange.max());
        }
        double baselineHotMetalTemp = valueOrDefault(latestData.getHotMetalTemperature(), 0.0);
        if (baselineHotMetalTemp <= 0.001) {
            baselineHotMetalTemp = clampValue(predictQualityOutcome(latestData, hotMetalTempPredictor, hotMetalTempSpec).value(), hotMetalTempSpec.min(), hotMetalTempSpec.max());
        }
        double baselineSilicon = valueOrDefault(latestData.getSiliconContent(), 0.0);
        if (baselineSilicon <= 0.001) {
            baselineSilicon = clampValue(predictQualityOutcome(latestData, siliconPredictor, siliconSpec).value(), siliconSpec.min(), siliconSpec.max());
        }
        double baselineProdNorm = normalize(baselineProduction, productionRange.min(), productionRange.max());
        double baselineEnergyNorm = 1.0 - normalize(baselineEnergy, energyRange.min(), energyRange.max());
        double baselineFitness = weights[0] * baselineProdNorm + weights[1] * baselineEnergyNorm;
        double baselineProdScore = baselineProdNorm * 100.0;
        double baselineEnergyScore = baselineEnergyNorm * 100.0;
        double baselineConfidence = calculateConfidence(latestData, latestData, parameterRanges);
        double baselineStability = calculateStabilityScore(latestData, latestData, parameterRanges);
        double baselineCostValue = computeCostValue(baselineEnergy, valueOrDefault(latestData.getGasFlow(), 0.0), valueOrDefault(latestData.getOxygenLevel(), 21.0));
        double baselineCost = calculateCostScore(baselineCostValue, costRange);
        double baselineViolation = constraintViolation(baselineHotMetalTemp, hotMetalTempSpec) + constraintViolation(baselineSilicon, siliconSpec);
        
        OptimizationSolutionDTO baselineSolution = new OptimizationSolutionDTO();
        baselineSolution.setTemperature(valueOrDefault(latestData.getTemperature(), 0.0));
        baselineSolution.setPressure(valueOrDefault(latestData.getPressure(), 0.0));
        baselineSolution.setWindVolume(valueOrDefault(latestData.getWindVolume(), 0.0));
        baselineSolution.setCoalInjection(valueOrDefault(latestData.getCoalInjection(), 0.0));
        baselineSolution.setGasFlow(valueOrDefault(latestData.getGasFlow(), 0.0));
        baselineSolution.setOxygenLevel(valueOrDefault(latestData.getOxygenLevel(), 0.0));
        baselineSolution.setMaterialHeight(valueOrDefault(latestData.getMaterialHeight(), 0.0));
        baselineSolution.setGenes(Map.copyOf(baselineGenes));
        Map<String, Double> baselineDeltas = new HashMap<>();
        for (String key : searchFeatures) {
            baselineDeltas.put(key, 0.0);
        }
        baselineSolution.setDeltas(baselineDeltas);
        baselineSolution.setPredictedProduction(baselineProduction);
        baselineSolution.setEstimatedEnergy(baselineEnergy);
        baselineSolution.setPredictedHotMetalTemperature(baselineHotMetalTemp);
        baselineSolution.setPredictedSiliconContent(baselineSilicon);
        baselineSolution.setConstraintViolation(baselineViolation);
        baselineSolution.setFitness(baselineFitness);
        baselineSolution.setProductionScore(baselineProdScore);
        baselineSolution.setEnergyScore(baselineEnergyScore);
        baselineSolution.setStabilityScore(baselineStability);
        baselineSolution.setCostScore(baselineCost);
        baselineSolution.setConfidence(baselineConfidence);
        baselineSolution.setExplanation("当前实际工况");
        baselineSolution.setModelUsed(baselineModelUsed);

        saveComparisonHistory(mode, results.get(0), baselineSolution, results, latestData, evolutionResult, runId);
        taskState.progress = 95;
        
        return evolutionResult;
    }

    private static final class OptimizationTaskState {
        private final String runId;
        private final long startedAt;
        private volatile long finishedAt;
        private volatile String status;
        private volatile Integer progress;
        private volatile String message;
        private volatile EvolutionResult result;

        private OptimizationTaskState(String runId) {
            this.runId = runId;
            this.startedAt = System.currentTimeMillis();
            this.status = "running";
            this.progress = 0;
            this.message = "任务已提交";
            this.finishedAt = 0L;
        }

        private EvolutionResult toResult() {
            EvolutionResult progressResult = new EvolutionResult();
            progressResult.setRunId(runId);
            progressResult.setStatus(status);
            progressResult.setProgress(progress);
            progressResult.setMessage(message);
            progressResult.setStartedAt(startedAt);
            progressResult.setFinishedAt(finishedAt > 0 ? finishedAt : null);
            return progressResult;
        }
    }

    private double evaluateFitness(
            Chromosome chromosome,
            ProductionData baseline,
            double[] weights,
            Function<ProductionData, PredictionOutcome> productionPredictor,
            Function<ProductionData, PredictionOutcome> energyPredictor,
            double productionScale,
            Range productionRange,
            Range energyRange,
            ParameterRanges parameterRanges
    ) {
        ProductionData data = toProductionData(chromosome, baseline);
        double production = predictProductionOutcome(data, productionPredictor, productionScale, productionRange).value();
        double energy = clampValue(predictEnergyOutcome(data, energyPredictor, energyRange).value(), energyRange.min(), energyRange.max());
        return calculateFitness(production, energy, weights, productionRange, energyRange);
    }

    private double calculateFitness(double production, double energy, double[] weights, Range productionRange, Range energyRange) {
        double productionScore = normalize(production, productionRange.min(), productionRange.max());
        double energyScore = 1.0 - normalize(energy, energyRange.min(), energyRange.max());
        return weights[0] * productionScore + weights[1] * energyScore;
    }

    private double[] resolveWeights(String mode) {
        double productionWeight;
        double energyWeight;
        if ("HIGH_YIELD".equals(mode)) {
            productionWeight = getConfigDouble("evo_weight_high_yield_production", 0.9);
            energyWeight = getConfigDouble("evo_weight_high_yield_energy", 0.1);
        } else if ("LOW_ENERGY".equals(mode)) {
            productionWeight = getConfigDouble("evo_weight_low_energy_production", 0.2);
            energyWeight = getConfigDouble("evo_weight_low_energy_energy", 0.8);
        } else {
            productionWeight = getConfigDouble("evo_weight_balanced_production", 0.6);
            energyWeight = getConfigDouble("evo_weight_balanced_energy", 0.4);
        }
        return normalizeWeights(productionWeight, energyWeight);
    }

    private double[] normalizeWeights(double productionWeight, double energyWeight) {
        double p = Math.max(0.0, productionWeight);
        double e = Math.max(0.0, energyWeight);
        double sum = p + e;
        if (sum <= 1e-9) {
            return new double[]{0.6, 0.4};
        }
        return new double[]{p / sum, e / sum};
    }

    private double getConfigDouble(String key, double defaultValue) {
        String raw = sysConfigService.getString(key, String.valueOf(defaultValue));
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private List<String> resolveSearchFeatures(ModelTraining productionTraining, ModelTraining energyTraining) {
        Set<String> features = new HashSet<>();
        features.addAll(parseSelectedFeatures(productionTraining));
        features.addAll(parseSelectedFeatures(energyTraining));
        features.removeAll(EXCLUDED_SEARCH_FEATURES);
        return features.stream().filter(value -> value != null && !value.isBlank()).sorted().toList();
    }

    private List<String> resolveSearchFeatures(ModelTraining productionTraining, ModelTraining energyTraining, ModelTraining hotMetalTempTraining, ModelTraining siliconTraining) {
        Set<String> features = new HashSet<>();
        features.addAll(parseSelectedFeatures(productionTraining));
        features.addAll(parseSelectedFeatures(energyTraining));
        features.addAll(parseSelectedFeatures(hotMetalTempTraining));
        features.addAll(parseSelectedFeatures(siliconTraining));
        features.removeAll(EXCLUDED_SEARCH_FEATURES);
        return features.stream().filter(value -> value != null && !value.isBlank()).sorted().toList();
    }

    private List<String> parseSelectedFeatures(ModelTraining training) {
        if (training == null) {
            return List.of();
        }
        String selected = training.getSelectedFeatures();
        if (selected == null || selected.isBlank()) {
            return List.of();
        }
        return Arrays.stream(selected.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private ModelTraining resolveTrainingForTarget(Long serviceId, String expectedTarget) {
        try {
            return resolveDeployedTrainingForTarget(serviceId, expectedTarget);
        } catch (IllegalStateException e) {
            if (!isTargetMismatchError(e)) {
                throw e;
            }
            ModelTraining training = modelTrainingRepository.findTop1ByStatusAndTargetVariableOrderByEndTimeDesc("completed", expectedTarget);
            if (training == null) {
                throw new IllegalStateException("未找到可用的" + expectedTarget + "模型（请训练并完成 " + expectedTarget + " 目标的模型）");
            }
            return training;
        }
    }

    private ModelTraining resolveDeployedTrainingForTarget(Long serviceId, String expectedTarget) {
        ModelService service = modelServiceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalStateException("模型服务不存在: " + serviceId));
        if (!"running".equals(service.getStatus())) {
            throw new IllegalStateException("模型服务未处于运行状态: " + serviceId);
        }
        if (service.getDeploymentId() == null) {
            throw new IllegalStateException("模型服务缺少部署记录: " + serviceId);
        }
        ModelDeployment deployment = modelDeploymentRepository.findById(service.getDeploymentId())
                .orElseThrow(() -> new IllegalStateException("部署记录不存在: " + service.getDeploymentId()));
        if (deployment.getStatus() != DeploymentStatus.COMPLETED) {
            throw new IllegalStateException("部署未完成，无法用于演化计算: " + deployment.getStatus().getValue());
        }
        if (deployment.getTrainingId() == null) {
            throw new IllegalStateException("部署记录缺少训练任务");
        }
        ModelTraining primaryTraining = modelTrainingRepository.findById(deployment.getTrainingId())
                .orElseThrow(() -> new IllegalStateException("训练任务不存在: " + deployment.getTrainingId()));
        if (!"completed".equals(primaryTraining.getStatus())) {
            throw new IllegalStateException("训练任务未完成，无法用于演化计算: " + primaryTraining.getStatus());
        }
        if (primaryTraining.getTargetVariable() == null || primaryTraining.getTargetVariable().isBlank()) {
            throw new IllegalStateException("训练任务缺少预测目标");
        }
        if (expectedTarget == null || expectedTarget.isBlank() || expectedTarget.equals(primaryTraining.getTargetVariable())) {
            return primaryTraining;
        }
        ModelTraining secondaryTraining = null;
        if (deployment.getSecondaryTrainingId() != null) {
            secondaryTraining = modelTrainingRepository.findById(deployment.getSecondaryTrainingId())
                    .orElseThrow(() -> new IllegalStateException("辅助训练任务不存在: " + deployment.getSecondaryTrainingId()));
        }
        if (secondaryTraining != null) {
            if (!"completed".equals(secondaryTraining.getStatus())) {
                throw new IllegalStateException("辅助训练任务未完成，无法用于演化计算: " + secondaryTraining.getStatus());
            }
            if (secondaryTraining.getTargetVariable() == null || secondaryTraining.getTargetVariable().isBlank()) {
                throw new IllegalStateException("辅助训练任务缺少预测目标");
            }
            if (expectedTarget.equals(secondaryTraining.getTargetVariable())) {
                return secondaryTraining;
            }
            throw new IllegalStateException(
                    "当前部署模型预测目标为 " + primaryTraining.getTargetVariable() + "（辅助目标: " + secondaryTraining.getTargetVariable() + "），无法用于" + expectedTarget + "演化计算"
            );
        }
        throw new IllegalStateException("当前部署模型预测目标为 " + primaryTraining.getTargetVariable() + "，无法用于" + expectedTarget + "演化计算");
    }

    private double normalize(double value, double min, double max) {
        if (max <= min) {
            return 0.0;
        }
        double normalized = (value - min) / (max - min);
        if (normalized < 0.0) {
            return 0.0;
        }
        if (normalized > 1.0) {
            return 1.0;
        }
        return normalized;
    }

    private ProductionData toProductionData(Chromosome chromosome, ProductionData baseline) {
        if (baseline == null) {
            throw new IllegalArgumentException("baseline 不能为空");
        }
        ProductionData data = copyProductionData(baseline);
        if (chromosome == null || chromosome.getGenes().isEmpty()) {
            return data;
        }
        for (Map.Entry<String, Double> entry : chromosome.getGenes().entrySet()) {
            String feature = entry.getKey();
            Double value = entry.getValue();
            if (feature == null || value == null) {
                continue;
            }
            applyFeatureValue(data, feature, value);
        }
        return data;
    }

    private ProductionData copyProductionData(ProductionData source) {
        ProductionData data = new ProductionData();
        data.setId(source.getId());
        data.setFurnaceId(source.getFurnaceId());
        data.setTimestamp(source.getTimestamp());
        data.setTemperature(source.getTemperature());
        data.setPressure(source.getPressure());
        data.setWindVolume(source.getWindVolume());
        data.setCoalInjection(source.getCoalInjection());
        data.setMaterialHeight(source.getMaterialHeight());
        data.setGasFlow(source.getGasFlow());
        data.setOxygenLevel(source.getOxygenLevel());
        data.setProductionRate(source.getProductionRate());
        data.setEnergyConsumption(source.getEnergyConsumption());
        data.setHotMetalTemperature(source.getHotMetalTemperature());
        data.setConstantSignal(source.getConstantSignal());
        data.setSiliconContent(source.getSiliconContent());
        data.setStatus(source.getStatus());
        data.setOperator(source.getOperator());
        data.setCollectionHistoryId(source.getCollectionHistoryId());
        return data;
    }

    private void applyFeatureValue(ProductionData data, String feature, double value) {
        switch (feature) {
            case "temperature" -> data.setTemperature(value);
            case "pressure" -> data.setPressure(value);
            case "windVolume" -> data.setWindVolume(value);
            case "coalInjection" -> data.setCoalInjection(value);
            case "materialHeight" -> data.setMaterialHeight(value);
            case "gasFlow" -> data.setGasFlow(value);
            case "oxygenLevel" -> data.setOxygenLevel(value);
            case "hotMetalTemperature" -> data.setHotMetalTemperature(value);
            case "siliconContent" -> data.setSiliconContent(value);
            case "productionRate" -> data.setProductionRate(value);
            case "energyConsumption" -> data.setEnergyConsumption(value);
            case "constantSignal" -> data.setConstantSignal(value);
            default -> {
            }
        }
    }

    private Double getFeatureValue(ProductionData data, String feature) {
        if (data == null || feature == null) {
            return null;
        }
        return switch (feature) {
            case "temperature" -> data.getTemperature();
            case "pressure" -> data.getPressure();
            case "windVolume" -> data.getWindVolume();
            case "coalInjection" -> data.getCoalInjection();
            case "materialHeight" -> data.getMaterialHeight();
            case "gasFlow" -> data.getGasFlow();
            case "oxygenLevel" -> data.getOxygenLevel();
            case "hotMetalTemperature" -> data.getHotMetalTemperature();
            case "siliconContent" -> data.getSiliconContent();
            case "productionRate" -> data.getProductionRate();
            case "energyConsumption" -> data.getEnergyConsumption();
            case "constantSignal" -> data.getConstantSignal();
            default -> null;
        };
    }

    private Long resolveServiceId(Long serviceId) {
        if (serviceId != null) {
            return serviceId;
        }
        List<ModelService> services = modelServiceRepository.findAllByOrderByIdDesc();
        Optional<ModelService> running = services.stream()
                .filter(service -> "running".equals(service.getStatus()))
                .findFirst();
        return running.map(ModelService::getId)
                .orElseThrow(() -> new IllegalStateException("未找到运行中的已部署模型，请先完成模型部署并启动服务"));
    }

    private ProductionData resolveBaselineData(Long baselineDataId) {
        if (baselineDataId == null) {
            return null;
        }
        return productionDataRepository.findById(baselineDataId)
                .orElseThrow(() -> new IllegalArgumentException("基准数据不存在"));
    }

    private PredictionOutcome predictProductionOutcome(ProductionData data, Function<ProductionData, PredictionOutcome> predictor, double scale, Range range) {
        PredictionOutcome outcome = predictor.apply(data);
        double raw = requirePositivePrediction(outcome.value(), "产量");
        double clamped = clampValue(raw * scale, range.min(), range.max());
        return new PredictionOutcome(clamped, outcome.modelUsed());
    }

    private PredictionOutcome predictEnergyOutcome(ProductionData data, Function<ProductionData, PredictionOutcome> energyPredictor, Range range) {
        PredictionOutcome outcome = energyPredictor.apply(data);
        double raw = requirePositivePrediction(outcome.value(), "能耗");
        double clamped = clampValue(raw, range.min(), range.max());
        return new PredictionOutcome(clamped, outcome.modelUsed());
    }

    private PredictionOutcome predictQualityOutcome(ProductionData data, Function<ProductionData, PredictionOutcome> predictor, IndustrialDataContract.ParameterSpec spec) {
        PredictionOutcome outcome = predictor.apply(data);
        double raw = requirePositivePrediction(outcome.value(), spec.label());
        double clamped = clampValue(raw, spec.min(), spec.max());
        return new PredictionOutcome(clamped, outcome.modelUsed());
    }

    private record CachedEvaluation(
            double production,
            String productionModel,
            double energy,
            String energyModel,
            double hotMetalTemperature,
            String hotMetalTempModel,
            double siliconContent,
            String siliconModel,
            double productionNorm,
            double energyNorm,
            double[] objectives,
            double constraintViolation,
            double preferenceFitness
    ) {
        private NSGA2Optimizer.Evaluation toNsgaEvaluation() {
            return new NSGA2Optimizer.Evaluation(objectives, constraintViolation, preferenceFitness);
        }
    }

    private CachedEvaluation evaluateWithConstraints(
            Chromosome chromosome,
            ProductionData baseline,
            double[] weights,
            Function<ProductionData, PredictionOutcome> productionPredictor,
            Function<ProductionData, PredictionOutcome> energyPredictor,
            Function<ProductionData, PredictionOutcome> hotMetalTempPredictor,
            Function<ProductionData, PredictionOutcome> siliconPredictor,
            double productionScale,
            Range productionRange,
            Range energyRange,
            IndustrialDataContract.ParameterSpec hotMetalTempSpec,
            IndustrialDataContract.ParameterSpec siliconSpec,
            ParameterRanges parameterRanges
    ) {
        ProductionData data = toProductionData(chromosome, baseline);
        PredictionOutcome prodOutcome = predictProductionOutcome(data, productionPredictor, productionScale, productionRange);
        double production = prodOutcome.value();
        PredictionOutcome energyOutcome = predictEnergyOutcome(data, energyPredictor, energyRange);
        double energy = clampValue(energyOutcome.value(), energyRange.min(), energyRange.max());
        PredictionOutcome hotOutcome = predictQualityOutcome(data, hotMetalTempPredictor, hotMetalTempSpec);
        PredictionOutcome siliconOutcome = predictQualityOutcome(data, siliconPredictor, siliconSpec);
        double hotMetalTemp = hotOutcome.value();
        double silicon = siliconOutcome.value();
        double prodNorm = normalize(production, productionRange.min(), productionRange.max());
        double energyNorm = normalize(energy, energyRange.min(), energyRange.max());
        double[] objectives = new double[]{1.0 - prodNorm, energyNorm};
        double violation = constraintViolation(hotMetalTemp, hotMetalTempSpec) + constraintViolation(silicon, siliconSpec);
        double preferenceFitness = weights[0] * prodNorm + weights[1] * (1.0 - energyNorm);
        return new CachedEvaluation(
                production,
                prodOutcome.modelUsed(),
                energy,
                energyOutcome.modelUsed(),
                hotMetalTemp,
                hotOutcome.modelUsed(),
                silicon,
                siliconOutcome.modelUsed(),
                prodNorm,
                energyNorm,
                objectives,
                violation,
                preferenceFitness
        );
    }

    private double constraintViolation(double value, IndustrialDataContract.ParameterSpec spec) {
        if (spec == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0;
        }
        double min = spec.warningMin();
        double max = spec.warningMax();
        if (value >= min && value <= max) {
            return 0.0;
        }
        double span = Math.max(1e-9, spec.max() - spec.min());
        if (value < min) {
            return (min - value) / span;
        }
        return (value - max) / span;
    }

    private String buildCacheKey(Chromosome chromosome) {
        if (chromosome == null || chromosome.getGenes().isEmpty()) {
            return "";
        }
        return chromosome.getGenes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + String.format(java.util.Locale.ROOT, "%.6f", entry.getValue()))
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
    }

    private double calculateConfidence(ProductionData data, ProductionData baseline, ParameterRanges ranges) {
        double deviationSum = 0.0;
        int count = 0;
        for (String feature : ranges.keys()) {
            Range range = ranges.getRange(feature);
            if (range == null) {
                continue;
            }
            Double value = data != null ? getFeatureValue(data, feature) : null;
            Double base = baseline != null ? getFeatureValue(baseline, feature) : null;
            if (value == null || base == null) {
                continue;
            }
            deviationSum += normalizedDeviation(value, base, range);
            count++;
        }
        double averageDeviation = count > 0 ? deviationSum / count : 0.2;
        double confidence = 1.0 - 0.8 * averageDeviation;
        Range temperatureRange = ranges.getRange("temperature");
        Double temperature = data != null ? data.getTemperature() : null;
        double baselineTemp = valueOrDefault(baseline != null ? baseline.getTemperature() : null, 1300.0);
        double tempValue = valueOrDefault(temperature, baselineTemp);
        double tempThreshold = Math.max(1300.0, baselineTemp * 1.1);
        if (temperatureRange != null && tempValue > tempThreshold) {
            double tempSpan = Math.max(1.0, temperatureRange.max() - temperatureRange.min());
            double exceedRatio = (tempValue - tempThreshold) / tempSpan;
            confidence *= clampValue(1.0 - exceedRatio * 0.6, 0.4, 1.0);
        }
        return clampValue(confidence, 0.3, 1.0);
    }

    private double valueOrDefault(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    private Range resolveRange(
            List<ProductionData> data,
            Function<ProductionData, Double> extractor,
            double fallbackValue,
            double defaultMin,
            double defaultMax
    ) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (ProductionData item : data) {
            Double value = extractor.apply(item);
            if (value != null && value > 0) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        
        // Ensure the range includes the fallback value if valid
        if (fallbackValue > 0) {
            min = Math.min(min, fallbackValue);
            max = Math.max(max, fallbackValue);
        }

        // Expand to include default limits (Broadening the range)
        if (Double.isInfinite(min)) min = defaultMin;
        if (Double.isInfinite(max)) max = defaultMax;
        
        // Ensure min/max are within reasonable bounds of defaults
        // Instead of shrinking to data, we expand to defaults if data is narrow
        min = Math.min(min, defaultMin);
        max = Math.max(max, defaultMax);

        if (min < 0) min = 0;
        
        return new Range(min, max);
    }

    private ParameterRanges resolveParameterRanges(List<ProductionData> recentData, ProductionData latestData, List<String> searchFeatures) {
        Map<String, Range> ranges = new HashMap<>();
        for (String feature : searchFeatures) {
            if (feature == null || feature.isBlank()) {
                continue;
            }
            Double fallbackBoxed = latestData != null ? getFeatureValue(latestData, feature) : null;
            double fallbackValue = fallbackBoxed != null ? fallbackBoxed : 0.0;
            Range defaults = resolveDefaultRange(feature, fallbackValue);
            Range range = resolveRange(
                    recentData,
                    item -> getFeatureValue(item, feature),
                    fallbackValue,
                    defaults.min(),
                    defaults.max()
            );
            ranges.put(feature, range);
        }
        return new ParameterRanges(ranges);
    }

    private Range resolveDefaultRange(String feature, double fallbackValue) {
        IndustrialDataContract.ParameterSpec spec = IndustrialDataContract.findByAnyKey(feature);
        if (spec != null) {
            return new Range(spec.min(), spec.max());
        }
        return switch (feature) {
            case "temperature" -> new Range(TEMP_MIN, TEMP_MAX);
            case "pressure" -> new Range(PRESSURE_MIN, PRESSURE_MAX);
            case "windVolume" -> new Range(WIND_VOLUME_MIN, WIND_VOLUME_MAX);
            case "coalInjection" -> new Range(COAL_INJECTION_MIN, COAL_INJECTION_MAX);
            case "gasFlow" -> new Range(GAS_FLOW_MIN, GAS_FLOW_MAX);
            case "oxygenLevel" -> new Range(OXYGEN_MIN, OXYGEN_MAX);
            case "materialHeight" -> new Range(HEIGHT_MIN, HEIGHT_MAX);
            default -> {
                double min = fallbackValue > 0 ? Math.max(0.0, fallbackValue * 0.7) : 0.0;
                double max = fallbackValue > 0 ? fallbackValue * 1.3 : 1.0;
                if (max <= min) {
                    max = min + 1.0;
                }
                yield new Range(min, max);
            }
        };
    }

    private double normalizedDeviation(double value, double baseline, Range range) {
        double span = Math.max(1.0, range.max() - range.min());
        return Math.min(1.0, Math.abs(value - baseline) / span);
    }

    private double resolveProductionScale(ProductionData latestData, Function<ProductionData, PredictionOutcome> predictor) {
        double actual = valueOrDefault(latestData.getProductionRate(), 0.0);
        if (actual <= 0.001) {
            return 1.0;
        }
        double predicted = predictor.apply(latestData).value();
        if (predicted <= 0.001) {
            return 1.0;
        }
        return clampValue(actual / predicted, 0.1, 10.0);
    }

    private double clampValue(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private double requirePositivePrediction(double value, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
            throw new IllegalStateException(label + "预测值无效: " + value);
        }
        return value;
    }

    private OptimizationSolutionDTO buildSolutionFromChromosome(
            Chromosome chromosome,
            CachedEvaluation evaluation,
            Range costRange,
            double[] weights,
            String mode,
            ProductionData baseline,
            ParameterRanges parameterRanges,
            List<String> searchFeatures,
            Map<String, Double> baselineGenes
    ) {
        ProductionData data = toProductionData(chromosome, baseline);
        double production = evaluation.production();
        double energy = evaluation.energy();
        double hotMetalTemp = evaluation.hotMetalTemperature();
        double silicon = evaluation.siliconContent();
        double fitness = evaluation.preferenceFitness();
        double productionScore = evaluation.productionNorm() * 100.0;
        double energyScore = (1.0 - evaluation.energyNorm()) * 100.0;
        double costValue = computeCostValue(energy, data.getGasFlow(), data.getOxygenLevel());
        double confidence = calculateConfidence(data, baseline, parameterRanges);
        double stabilityScore = calculateStabilityScore(data, baseline, parameterRanges);
        double costScore = calculateCostScore(costValue, costRange);
        String explanation = generateAnalysisReport(baseline, production, energy, costValue, stabilityScore, mode, parameterRanges);
        Map<String, Double> genes = new HashMap<>();
        Map<String, Double> deltas = new HashMap<>();
        for (String feature : searchFeatures) {
            Double valueBoxed = getFeatureValue(data, feature);
            if (valueBoxed == null) {
                continue;
            }
            genes.put(feature, valueBoxed);
            Double baselineValue = baselineGenes != null ? baselineGenes.get(feature) : null;
            if (baselineValue != null) {
                deltas.put(feature, valueBoxed - baselineValue);
            }
        }
        OptimizationSolutionDTO dto = new OptimizationSolutionDTO();
        dto.setTemperature(valueOrDefault(data.getTemperature(), 0.0));
        dto.setPressure(valueOrDefault(data.getPressure(), 0.0));
        dto.setWindVolume(valueOrDefault(data.getWindVolume(), 0.0));
        dto.setCoalInjection(valueOrDefault(data.getCoalInjection(), 0.0));
        dto.setGasFlow(valueOrDefault(data.getGasFlow(), 0.0));
        dto.setOxygenLevel(valueOrDefault(data.getOxygenLevel(), 0.0));
        dto.setMaterialHeight(valueOrDefault(data.getMaterialHeight(), 0.0));
        dto.setGenes(genes);
        dto.setDeltas(deltas);
        dto.setPredictedProduction(production);
        dto.setEstimatedEnergy(energy);
        dto.setPredictedHotMetalTemperature(hotMetalTemp);
        dto.setPredictedSiliconContent(silicon);
        dto.setConstraintViolation(evaluation.constraintViolation());
        dto.setFitness(fitness);
        dto.setProductionScore(productionScore);
        dto.setEnergyScore(energyScore);
        dto.setStabilityScore(stabilityScore);
        dto.setCostScore(costScore);
        dto.setConfidence(confidence);
        dto.setExplanation(explanation);
        dto.setModelUsed(evaluation.productionModel() + "|" + evaluation.energyModel() + "|" + evaluation.hotMetalTempModel() + "|" + evaluation.siliconModel());
        return dto;
    }

    private Range resolveCostRange() {
        double[] weights = normalizeWeights(
                getConfigDouble("evo_cost_energy_weight", 0.7),
                getConfigDouble("evo_cost_oxygen_weight", 0.3)
        );
        double oxygenFactor = getConfigDouble("evo_cost_oxygen_factor", 20.0);
        double oxygenMaxDelta = Math.max(0.0, getConfigDouble("evo_cost_oxygen_max_delta", 5.0));
        double minCost = ENERGY_MIN * weights[0];
        double maxCost = ENERGY_MAX * weights[0] + (oxygenMaxDelta * oxygenFactor) * weights[1];
        return new Range(minCost, maxCost);
    }

    private double calculateStabilityScore(ProductionData data, ProductionData baseline, ParameterRanges ranges) {
        double weightedDev = 0.0;
        double weightSum = 0.0;

        weightedDev += stabilityDeviation(data, baseline, ranges, "temperature", 1.0, 1400.0);
        weightSum += 1.0;
        weightedDev += stabilityDeviation(data, baseline, ranges, "pressure", 1.5, 200.0);
        weightSum += 1.5;
        weightedDev += stabilityDeviation(data, baseline, ranges, "gasFlow", 1.0, 3000.0);
        weightSum += 1.0;

        double avgDev = weightSum > 0 ? weightedDev / weightSum : 0.5;
        return clampValue((1.0 - avgDev) * 100.0, 0.0, 100.0);
    }

    private double stabilityDeviation(ProductionData data, ProductionData baseline, ParameterRanges ranges, String feature, double weight, double fallback) {
        Range range = ranges.getRange(feature);
        if (range == null) {
            range = resolveDefaultRange(feature, fallback);
        }
        double value = valueOrDefault(getFeatureValue(data, feature), baseline != null ? valueOrDefault(getFeatureValue(baseline, feature), fallback) : fallback);
        double mid = (range.min() + range.max()) / 2.0;
        double halfSpan = Math.max(1e-9, (range.max() - range.min()) / 2.0);
        double dev = Math.abs(value - mid) / halfSpan;
        return dev * weight;
    }

    private double calculateCostScore(double costValue, Range costRange) {
        double score = 1.0 - normalize(costValue, costRange.min(), costRange.max());
        return clampValue(score * 100.0, 0.0, 100.0);
    }

    private double computeCostValue(Double energyConsumption, Double gasFlow) {
        // 此方法签名保留兼容性，但逻辑需依赖 oxygenLevel，此处无法获取 oxygenLevel
        // 需要重载或修改调用处。由于是 private 方法，可以直接修改签名。
        // 但 buildSolutionFromChromosome 调用了它。
        return computeCostValue(energyConsumption, gasFlow, 21.0); // 默认氧气含量
    }

    private double computeCostValue(Double energyConsumption, Double gasFlow, Double oxygenLevel) {
        double energy = energyConsumption != null ? energyConsumption : 0.0;
        double oxygenBase = getConfigDouble("evo_cost_oxygen_base", 21.0);
        double oxygenFactor = getConfigDouble("evo_cost_oxygen_factor", 20.0);
        double oxy = oxygenLevel != null ? oxygenLevel : oxygenBase;
        double oxyCost = Math.max(0, (oxy - oxygenBase) * oxygenFactor);
        double[] weights = normalizeWeights(
                getConfigDouble("evo_cost_energy_weight", 0.7),
                getConfigDouble("evo_cost_oxygen_weight", 0.3)
        );
        return energy * weights[0] + oxyCost * weights[1];
    }

    private String generateAnalysisReport(ProductionData baseline, double production, double energy, double costValue, double stabilityScore, String mode, ParameterRanges ranges) {
        double baselineProduction = baseline != null ? valueOrDefault(baseline.getProductionRate(), 0.0) : 0.0;
        double baselineEnergy = baseline != null ? valueOrDefault(baseline.getEnergyConsumption(), 0.0) : 0.0;
        double baselineCostValue = computeCostValue(baselineEnergy, baseline != null ? baseline.getGasFlow() : null, baseline != null ? baseline.getOxygenLevel() : null);
        double baselineStability = baseline != null ? calculateStabilityScore(baseline, baseline, ranges) : stabilityScore;

        double productionChange = calculateChangePercent(production, baselineProduction);
        double costChange = calculateChangePercent(costValue, baselineCostValue);
        double stabilityDelta = stabilityScore - baselineStability;

        return String.format("与基准相比，产量%s%.1f%%，成本%s%.1f%%，稳定性变化%.1f分。模式=%s",
                productionChange >= 0 ? "提升" : "下降",
                Math.abs(productionChange),
                costChange >= 0 ? "上升" : "下降",
                Math.abs(costChange),
                stabilityDelta,
                mode);
    }

    private double calculateChangePercent(double value, double baseline) {
        if (baseline <= 0.0) {
            return 0.0;
        }
        return (value - baseline) / baseline * 100.0;
    }

    private void saveComparisonHistory(String mode, OptimizationSolutionDTO best, OptimizationSolutionDTO baseline, List<OptimizationSolutionDTO> allResults, ProductionData latestData, EvolutionResult evolutionResult, String runId) {
        String result = best.getFitness() >= baseline.getFitness() ? "智能方案更优" : "当前工况更优";
        
        List<OptimizationSolutionDTO> saveList = new ArrayList<>();
        saveList.add(best);
        saveList.add(baseline);
        for (int i = 1; i < allResults.size(); i++) {
            saveList.add(allResults.get(i));
        }

        String payload = "";
        try {
            Map<String, Object> payloadBody = new java.util.HashMap<>();
            payloadBody.put("solutions", saveList);
            if (evolutionResult.getSearchFeatures() != null) {
                payloadBody.put("searchFeatures", evolutionResult.getSearchFeatures());
            }
            if (evolutionResult.getRanges() != null) {
                payloadBody.put("ranges", evolutionResult.getRanges());
            }
            if (evolutionResult.getBaselineGenes() != null) {
                payloadBody.put("baselineGenes", evolutionResult.getBaselineGenes());
            }
            Map<String, Object> evolutionProcess = new java.util.HashMap<>();
            evolutionProcess.put("maxFitness", evolutionResult.getMaxFitnessHistory());
            evolutionProcess.put("avgFitness", evolutionResult.getAvgFitnessHistory());
            evolutionProcess.put("bestSolutions", evolutionResult.getBestSolutionsHistory());
            payloadBody.put("evolutionProcess", evolutionProcess);
            payloadBody.put("baselineTime", latestData != null && latestData.getTimestamp() != null
                    ? latestData.getTimestamp().getTime()
                    : null);
            payloadBody.put("baselineDataId", latestData != null ? latestData.getId() : null);
            payloadBody.put("baselineFurnaceId", latestData != null ? latestData.getFurnaceId() : null);
            payload = objectMapper.writeValueAsString(payloadBody);
        } catch (JsonProcessingException ignored) {
        }
        String createdLabel = formatTime(new Date());
        String baselineTime = latestData != null && latestData.getTimestamp() != null
                ? formatTime(latestData.getTimestamp())
                : createdLabel;
        String furnace = latestData != null && latestData.getFurnaceId() != null ? "高炉" + latestData.getFurnaceId() : "未知高炉";
        String schemeA = "演化方案-" + furnace + "-" + mode + "-" + createdLabel;
        String schemeB = "基准工况-" + furnace + "-" + baselineTime;
        comparisonHistoryService.saveHistory(mode, schemeA, schemeB, result, best.getFitness(), baseline.getFitness(), payload, runId);
    }

    private String formatTime(Date date) {
        if (date == null) {
            return "未知时间";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private Function<ProductionData, PredictionOutcome> preparePredictor(Long serviceId, String expectedTarget) {
        ModelService service = modelServiceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalStateException("模型服务不存在: " + serviceId));
        if (!"running".equals(service.getStatus())) {
            throw new IllegalStateException("模型服务未处于运行状态: " + serviceId);
        }
        if (service.getDeploymentId() == null) {
            throw new IllegalStateException("模型服务缺少部署记录: " + serviceId);
        }
        ModelDeployment deployment = modelDeploymentRepository.findById(service.getDeploymentId())
                .orElseThrow(() -> new IllegalStateException("部署记录不存在: " + service.getDeploymentId()));
        if (deployment.getStatus() != DeploymentStatus.COMPLETED) {
            throw new IllegalStateException("部署未完成，无法用于演化计算: " + deployment.getStatus().getValue());
        }
        if (deployment.getTrainingId() == null) {
            throw new IllegalStateException("部署记录缺少训练任务");
        }
        ModelTraining primaryTraining = modelTrainingRepository.findById(deployment.getTrainingId())
                .orElseThrow(() -> new IllegalStateException("训练任务不存在: " + deployment.getTrainingId()));
        if (!"completed".equals(primaryTraining.getStatus())) {
            throw new IllegalStateException("训练任务未完成，无法用于演化计算: " + primaryTraining.getStatus());
        }
        if (primaryTraining.getTargetVariable() == null || primaryTraining.getTargetVariable().isBlank()) {
            throw new IllegalStateException("训练任务缺少预测目标");
        }
        ModelTraining training = primaryTraining;
        if (expectedTarget != null && !expectedTarget.isBlank() && !expectedTarget.equals(primaryTraining.getTargetVariable())) {
            ModelTraining secondaryTraining = null;
            if (deployment.getSecondaryTrainingId() != null) {
                secondaryTraining = modelTrainingRepository.findById(deployment.getSecondaryTrainingId())
                        .orElseThrow(() -> new IllegalStateException("辅助训练任务不存在: " + deployment.getSecondaryTrainingId()));
            }
            if (secondaryTraining != null) {
                if (!"completed".equals(secondaryTraining.getStatus())) {
                    throw new IllegalStateException("辅助训练任务未完成，无法用于演化计算: " + secondaryTraining.getStatus());
                }
                if (secondaryTraining.getTargetVariable() == null || secondaryTraining.getTargetVariable().isBlank()) {
                    throw new IllegalStateException("辅助训练任务缺少预测目标");
                }
                if (expectedTarget.equals(secondaryTraining.getTargetVariable())) {
                    training = secondaryTraining;
                } else {
                    throw new IllegalStateException(
                            "当前部署模型预测目标为 " + primaryTraining.getTargetVariable() + "（辅助目标: " + secondaryTraining.getTargetVariable() + "），无法用于" + expectedTarget + "演化计算"
                    );
                }
            } else {
                throw new IllegalStateException("当前部署模型预测目标为 " + primaryTraining.getTargetVariable() + "，无法用于" + expectedTarget + "演化计算");
            }
        }

        ModelTraining chosenTraining = training;
        var storedModel = modelTrainingService.getStoredModel(chosenTraining.getId())
                .orElseThrow(() -> new IllegalStateException("模型缓存不可用: trainingId=" + chosenTraining.getId()));
        ModelTrainer trainer = modelTrainerFactory.getTrainer(storedModel.getModelType());
        String sourceLabel = chosenTraining.getId().equals(primaryTraining.getId()) ? "主模型" : "辅助模型";
        String modelUsed = sourceLabel + ":" + formatModelName(storedModel.getModelType());
        String[] features = resolveFeatureArray(storedModel.getFeatures(), chosenTraining.getSelectedFeatures());
        return data -> {
            TrainingResult result = trainer.evaluate(
                    List.of(data),
                    chosenTraining,
                    chosenTraining.getModelConfig(),
                    features,
                    storedModel.getModel(),
                    storedModel.getPreprocessor()
            );
            Double prediction = null;
            if (result.getPredictedValues() != null && !result.getPredictedValues().isEmpty()) {
                prediction = result.getPredictedValues().get(0);
            }
            if (prediction == null) {
                throw new IllegalStateException("模型预测结果为空");
            }
            String label = expectedTarget != null && !expectedTarget.isBlank() ? expectedTarget : "目标值";
            return new PredictionOutcome(requirePositivePrediction(prediction, label), modelUsed);
        };
    }

    private String[] resolveFeatureArray(String[] storedFeatures, String selectedFeatures) {
        if (storedFeatures != null && storedFeatures.length > 0) {
            return storedFeatures;
        }
        if (selectedFeatures == null || selectedFeatures.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(selectedFeatures.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }

    private Function<ProductionData, PredictionOutcome> prepareLatestCompletedPredictor(String targetVariable) {
        ModelTraining training = modelTrainingRepository.findTop1ByStatusAndTargetVariableOrderByEndTimeDesc("completed", targetVariable);
        if (training == null) {
            throw new IllegalStateException("未找到可用的" + targetVariable + "模型（请训练并完成 " + targetVariable + " 目标的模型）");
        }
        var storedModel = modelTrainingService.getStoredModel(training.getId())
                .orElseThrow(() -> new IllegalStateException(targetVariable + "模型缓存不可用: trainingId=" + training.getId()));
        ModelTrainer trainer = modelTrainerFactory.getTrainer(storedModel.getModelType());
        String modelUsed = "最新模型回退:" + formatModelName(storedModel.getModelType());
        String[] features = resolveFeatureArray(storedModel.getFeatures(), training.getSelectedFeatures());
        return data -> {
            TrainingResult result = trainer.evaluate(
                    List.of(data),
                    training,
                    training.getModelConfig(),
                    features,
                    storedModel.getModel(),
                    storedModel.getPreprocessor()
            );
            Double prediction = null;
            if (result.getPredictedValues() != null && !result.getPredictedValues().isEmpty()) {
                prediction = result.getPredictedValues().get(0);
            }
            if (prediction == null) {
                throw new IllegalStateException(targetVariable + "模型预测结果为空");
            }
            return new PredictionOutcome(requirePositivePrediction(prediction, targetVariable), modelUsed);
        };
    }

    private boolean isTargetMismatchError(IllegalStateException e) {
        String message = e.getMessage();
        return message != null && message.contains("无法用于");
    }

    private Function<ProductionData, PredictionOutcome> resolvePredictor(Long serviceId, String targetVariable) {
        try {
            return preparePredictor(serviceId, targetVariable);
        } catch (IllegalStateException e) {
            if (!isTargetMismatchError(e)) {
                throw e;
            }
            return prepareLatestCompletedPredictor(targetVariable);
        }
    }

    private String formatModelName(String modelType) {
        if (modelType == null || modelType.isBlank()) {
            return "Model";
        }
        String normalized = modelType.trim().replace('-', '_');
        String[] parts = normalized.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase());
            }
        }
        return builder.length() > 0 ? builder.toString() : modelType;
    }

    private record PredictionOutcome(double value, String modelUsed) {
    }
}
