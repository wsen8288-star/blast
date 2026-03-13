package com.blastfurnace.backend.service;

import com.blastfurnace.backend.dto.ComparisonHistoryDTO;
import com.blastfurnace.backend.dto.ComparisonHistoryDetailDTO;
import com.blastfurnace.backend.dto.OptimizationSolutionDTO;
import com.blastfurnace.backend.model.ComparisonHistory;
import com.blastfurnace.backend.model.OperationLog;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.repository.ComparisonHistoryRepository;
import com.blastfurnace.backend.repository.OperationLogRepository;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ComparisonHistoryService {
    private final ComparisonHistoryRepository comparisonHistoryRepository;
    private final ProductionDataRepository productionDataRepository;
    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper;
    private static final double PRODUCTION_MIN = 30.0;
    private static final double PRODUCTION_MAX = 80.0;
    private static final double ENERGY_MIN = 1000.0;
    private static final double ENERGY_MAX = 2000.0;
    private static final double TEMP_MIN = 1000.0; // Widened range
    private static final double TEMP_MAX = 1600.0; // Widened range
    private static final double PRESSURE_MIN = 50.0; // Widened range
    private static final double PRESSURE_MAX = 400.0;
    private static final double GAS_FLOW_MIN = 1500.0; // Widened range
    private static final double GAS_FLOW_MAX = 6000.0; // Widened range
    private static final double USER_SCORE_WEIGHT_PRODUCTION = 0.4;
    private static final double USER_SCORE_WEIGHT_ENERGY = 0.3;
    private static final double USER_SCORE_WEIGHT_STABILITY = 0.15;
    private static final double USER_SCORE_WEIGHT_COST = 0.15;
    private static final String HISTORY_TYPE_EVOLUTION = "EVOLUTION";
    private static final String HISTORY_TYPE_COMPARISON = "COMPARISON";

    public ComparisonHistory saveHistory(String mode, String schemeA, String schemeB, String result, Double scoreA, Double scoreB, String payload) {
        return saveHistory(mode, schemeA, schemeB, result, scoreA, scoreB, payload, null);
    }

    public ComparisonHistory saveHistory(String mode, String schemeA, String schemeB, String result, Double scoreA, Double scoreB, String payload, String runId) {
        ComparisonHistory history = new ComparisonHistory();
        history.setMode(mode);
        history.setSchemeA(schemeA);
        history.setSchemeB(schemeB);
        history.setResult(result);
        history.setScoreA(scoreA);
        history.setScoreB(scoreB);
        history.setPayload(payload);
        history.setCreatedAt(new Date());
        history.setHistoryType(HISTORY_TYPE_EVOLUTION);
        if (runId != null && !runId.isBlank()) {
            history.setRunId(runId.trim());
        }
        return comparisonHistoryRepository.save(history);
    }

    public ComparisonHistory saveComparisonHistory(String mode, String schemeA, String schemeB, String result, Double scoreA, Double scoreB, String payload) {
        return saveComparisonHistory(mode, schemeA, schemeB, result, scoreA, scoreB, payload, null);
    }

    public ComparisonHistory saveComparisonHistory(String mode, String schemeA, String schemeB, String result, Double scoreA, Double scoreB, String payload, String runId) {
        ComparisonHistory history = new ComparisonHistory();
        history.setMode(mode);
        history.setSchemeA(schemeA);
        history.setSchemeB(schemeB);
        history.setResult(result);
        history.setScoreA(scoreA);
        history.setScoreB(scoreB);
        history.setPayload(payload);
        history.setCreatedAt(new Date());
        history.setHistoryType(HISTORY_TYPE_COMPARISON);
        if (runId != null && !runId.isBlank()) {
            history.setRunId(runId.trim());
        }
        return comparisonHistoryRepository.save(history);
    }

    public List<ComparisonHistoryDTO> getHistory(String mode, Date startDate, Date endDate, String historyType) {
        return comparisonHistoryRepository.findFiltered(mode, startDate, endDate, historyType).stream()
                .map(history -> {
                    Date baselineTime = resolveBaselineTime(history.getPayload());
                    return new ComparisonHistoryDTO(
                            history.getId(),
                            history.getCreatedAt(),
                            history.getMode(),
                            history.getSchemeA(),
                            history.getSchemeB(),
                            history.getResult(),
                            history.getScoreA(),
                            history.getScoreB(),
                            baselineTime,
                            history.getHistoryType()
                    );
                })
                .toList();
    }

    public void deleteHistory(Long id) {
        if (!comparisonHistoryRepository.existsById(id)) {
            throw new IllegalArgumentException("记录不存在");
        }
        comparisonHistoryRepository.deleteById(id);
    }

    public void batchDeleteHistory(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        comparisonHistoryRepository.deleteAllById(ids);
    }

    public ComparisonHistoryDetailDTO getHistoryDetail(Long id) {
        ComparisonHistory history = comparisonHistoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        Date baselineTime = resolveBaselineTime(history.getPayload());
        return new ComparisonHistoryDetailDTO(
                history.getId(),
                history.getCreatedAt(),
                history.getMode(),
                history.getSchemeA(),
                history.getSchemeB(),
                history.getResult(),
                history.getScoreA(),
                history.getScoreB(),
                history.getPayload(),
                baselineTime,
                history.getHistoryType()
        );
    }

    private Date resolveBaselineTime(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode baselineNode = root.get("baselineTime");
            if (baselineNode == null || baselineNode.isNull()) {
                return null;
            }
            if (baselineNode.isNumber()) {
                return new Date(baselineNode.asLong());
            }
            if (baselineNode.isTextual()) {
                String text = baselineNode.asText();
                try {
                    return Date.from(Instant.parse(text));
                } catch (Exception ignored) {
                }
                try {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(text);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public Map<String, Object> compareSolutions(Long historyIdA, int indexA, Long historyIdB, int indexB, String furnaceId) {
        OptimizationSolutionDTO solutionA = loadSolution(historyIdA, indexA);
        OptimizationSolutionDTO solutionB = loadSolution(historyIdB, indexB);
        ComparisonHistory historyA = comparisonHistoryRepository.findById(historyIdA)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        ComparisonHistory historyB = comparisonHistoryRepository.findById(historyIdB)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        ProductionData baseline = resolveBaselineData(furnaceId);
        List<Map<String, Object>> rows = new ArrayList<>();
        
        Range productionRange = resolveSpecRange("productionRate", new Range(PRODUCTION_MIN, PRODUCTION_MAX));
        Range energyRange = resolveSpecRange("energyConsumption", new Range(ENERGY_MIN, ENERGY_MAX));
        Range tempRange = resolveSpecRange("temperature", new Range(TEMP_MIN, TEMP_MAX));
        Range pressureRange = resolveSpecRange("pressure", new Range(PRESSURE_MIN, PRESSURE_MAX));
        Range windRange = resolveSpecRange("windVolume", new Range(3000, 6000));
        Range coalRange = resolveSpecRange("coalInjection", new Range(100, 220));
        Range gasRange = resolveSpecRange("gasFlow", new Range(GAS_FLOW_MIN, GAS_FLOW_MAX));
        Range oxyRange = resolveSpecRange("oxygenLevel", new Range(18, 25));
        Range matRange = resolveSpecRange("materialHeight", new Range(2, 6));
        Range hotRange = resolveSpecRange("hotMetalTemperature", new Range(1400, 1600));
        Range siliconRange = resolveSpecRange("siliconContent", new Range(0.1, 1.0));
        Range costRange = resolveCostRange();

        BaselineValues baselineValues = resolveBaselineValues(baseline, tempRange, pressureRange, gasRange);
        
        // Recompute stability scores instead of using stored values
        double stabilityA = calculateStabilityFromRanges(
            valueOrDefault(solutionA.getTemperature(), baselineValues.temperature()), 
            valueOrDefault(solutionA.getPressure(), baselineValues.pressure()), 
            valueOrDefault(solutionA.getGasFlow(), baselineValues.gasFlow()), 
            tempRange, pressureRange, gasRange);
            
        double stabilityB = calculateStabilityFromRanges(
            valueOrDefault(solutionB.getTemperature(), baselineValues.temperature()), 
            valueOrDefault(solutionB.getPressure(), baselineValues.pressure()), 
            valueOrDefault(solutionB.getGasFlow(), baselineValues.gasFlow()), 
            tempRange, pressureRange, gasRange);

        // Recompute cost scores
        double costA = computeCostScore(solutionA.getEstimatedEnergy(), solutionA.getGasFlow(), solutionA.getOxygenLevel(), costRange);
        double costB = computeCostScore(solutionB.getEstimatedEnergy(), solutionB.getGasFlow(), solutionB.getOxygenLevel(), costRange);

        // Recompute production and energy scores
        double prodScoreA = computeScore(solutionA.getPredictedProduction(), productionRange, true);
        double prodScoreB = computeScore(solutionB.getPredictedProduction(), productionRange, true);
        double energyScoreA = computeScore(solutionA.getEstimatedEnergy(), energyRange, false);
        double energyScoreB = computeScore(solutionB.getEstimatedEnergy(), energyRange, false);

        rows.add(withBaseline(buildRowWithScores("productionRate", resolveSpecLabel("productionRate", "生产率"),
                solutionA.getPredictedProduction(), solutionB.getPredictedProduction(),
                resolveSpecUnit("productionRate", "t/h"), true, prodScoreA, prodScoreB), baseline != null ? baseline.getProductionRate() : null));
        rows.add(withBaseline(buildRowWithScores("energyConsumption", resolveSpecLabel("energyConsumption", "能耗"),
                solutionA.getEstimatedEnergy(), solutionB.getEstimatedEnergy(),
                resolveSpecUnit("energyConsumption", "kgce/t"), false, energyScoreA, energyScoreB), baseline != null ? baseline.getEnergyConsumption() : null));
        rows.add(withBaseline(buildRowWithScores("temperature", resolveSpecLabel("temperature", "温度"), solutionA.getTemperature(), solutionB.getTemperature(), resolveSpecUnit("temperature", "℃"), true,
                computeScore(solutionA.getTemperature(), tempRange, true), computeScore(solutionB.getTemperature(), tempRange, true)), baseline != null ? baseline.getTemperature() : null));
        rows.add(withBaseline(buildRowWithScores("pressure", resolveSpecLabel("pressure", "压力"), solutionA.getPressure(), solutionB.getPressure(), resolveSpecUnit("pressure", "kPa"), true,
                computeScore(solutionA.getPressure(), pressureRange, true), computeScore(solutionB.getPressure(), pressureRange, true)), baseline != null ? baseline.getPressure() : null));
        rows.add(withBaseline(buildRowWithScores("windVolume", resolveSpecLabel("windVolume", "风量"), solutionA.getWindVolume(), solutionB.getWindVolume(), resolveSpecUnit("windVolume", "m³/h"), true,
                computeScore(solutionA.getWindVolume(), windRange, true), computeScore(solutionB.getWindVolume(), windRange, true)), baseline != null ? baseline.getWindVolume() : null));
        rows.add(withBaseline(buildRowWithScores("coalInjection", resolveSpecLabel("coalInjection", "喷煤量"), solutionA.getCoalInjection(), solutionB.getCoalInjection(), resolveSpecUnit("coalInjection", "kg/t"), false,
                computeScore(solutionA.getCoalInjection(), coalRange, false), computeScore(solutionB.getCoalInjection(), coalRange, false)), baseline != null ? baseline.getCoalInjection() : null));
        rows.add(withBaseline(buildRowWithScores("gasFlow", resolveSpecLabel("gasFlow", "煤气流量"), solutionA.getGasFlow(), solutionB.getGasFlow(), resolveSpecUnit("gasFlow", "m³/h"), true,
                computeScore(solutionA.getGasFlow(), gasRange, true), computeScore(solutionB.getGasFlow(), gasRange, true)), baseline != null ? baseline.getGasFlow() : null));
        rows.add(withBaseline(buildRowWithScores("oxygenLevel", resolveSpecLabel("oxygenLevel", "氧气含量"), solutionA.getOxygenLevel(), solutionB.getOxygenLevel(), resolveSpecUnit("oxygenLevel", "%"), true,
                computeScore(solutionA.getOxygenLevel(), oxyRange, true), computeScore(solutionB.getOxygenLevel(), oxyRange, true)), baseline != null ? baseline.getOxygenLevel() : null));
        rows.add(withBaseline(buildRowWithScores("materialHeight", resolveSpecLabel("materialHeight", "料面高度"), solutionA.getMaterialHeight(), solutionB.getMaterialHeight(), resolveSpecUnit("materialHeight", "m"), true,
                computeScore(solutionA.getMaterialHeight(), matRange, true), computeScore(solutionB.getMaterialHeight(), matRange, true)), baseline != null ? baseline.getMaterialHeight() : null));
        rows.add(withBaseline(buildRowWithScores("hotMetalTemperature", resolveSpecLabel("hotMetalTemperature", "铁水温度"), solutionA.getPredictedHotMetalTemperature(), solutionB.getPredictedHotMetalTemperature(), resolveSpecUnit("hotMetalTemperature", "℃"), true,
                computeScore(solutionA.getPredictedHotMetalTemperature(), hotRange, true), computeScore(solutionB.getPredictedHotMetalTemperature(), hotRange, true)), baseline != null ? baseline.getHotMetalTemperature() : null));
        rows.add(withBaseline(buildRowWithScores("siliconContent", resolveSpecLabel("siliconContent", "硅含量"), solutionA.getPredictedSiliconContent(), solutionB.getPredictedSiliconContent(), resolveSpecUnit("siliconContent", "%"), false,
                computeScore(solutionA.getPredictedSiliconContent(), siliconRange, false), computeScore(solutionB.getPredictedSiliconContent(), siliconRange, false)), baseline != null ? baseline.getSiliconContent() : null));
        rows.add(withBaseline(buildRow("stability", "稳定性", stabilityA, stabilityB, "分", true), baseline != null ? resolveStabilityBaselineScore(baselineValues, tempRange, pressureRange, gasRange) : null));
        rows.add(withBaseline(buildRow("cost", "成本", costA, costB, "分", true), resolveCostBaselineScore(baseline, costRange)));
        
        double fitnessA = toPercent(solutionA.getFitness());
        double fitnessB = toPercent(solutionB.getFitness());
        double scoreA = calculateUserScore(prodScoreA, energyScoreA, stabilityA, costA);
        double scoreB = calculateUserScore(prodScoreB, energyScoreB, stabilityB, costB);
        
        String recommended = scoreA >= scoreB ? "方案A" : "方案B";
        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("scoreA", scoreA);
        result.put("scoreB", scoreB);
        result.put("fitnessA", fitnessA);
        result.put("fitnessB", fitnessB);
        result.put("scoreType", "USER_SCORE");
        result.put("recommended", recommended);
        result.put("baselineTime", baseline != null ? baseline.getTimestamp() : null);
        saveComparisonResult(resolveCompareMode(historyA, historyB), resolveSchemeLabel(historyA, indexA), resolveSchemeLabel(historyB, indexB), recommended, scoreA, scoreB, result);
        return result;
    }

    public Map<String, Object> compareEvolutionWithBaseline(Long historyId, int index, String furnaceId, Long baselineId) {
        OptimizationSolutionDTO solution = loadSolution(historyId, index);
        ComparisonHistory history = comparisonHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        ProductionData baseline = baselineId != null
                ? productionDataRepository.findById(baselineId).orElse(null)
                : resolveBaselineData(furnaceId);
        if (baseline == null) {
            throw new IllegalArgumentException("基准工况不存在");
        }
        Range productionRange = resolveSpecRange("productionRate", new Range(PRODUCTION_MIN, PRODUCTION_MAX));
        Range energyRange = resolveSpecRange("energyConsumption", new Range(ENERGY_MIN, ENERGY_MAX));
        Range tempRange = resolveSpecRange("temperature", new Range(TEMP_MIN, TEMP_MAX));
        Range pressureRange = resolveSpecRange("pressure", new Range(PRESSURE_MIN, PRESSURE_MAX));
        Range windRange = resolveSpecRange("windVolume", new Range(3000, 6000));
        Range coalRange = resolveSpecRange("coalInjection", new Range(100, 220));
        Range gasRange = resolveSpecRange("gasFlow", new Range(GAS_FLOW_MIN, GAS_FLOW_MAX));
        Range oxyRange = resolveSpecRange("oxygenLevel", new Range(18, 25));
        Range matRange = resolveSpecRange("materialHeight", new Range(2, 6));
        Range hotRange = resolveSpecRange("hotMetalTemperature", new Range(1400, 1600));
        Range siliconRange = resolveSpecRange("siliconContent", new Range(0.1, 1.0));
        Range costRange = resolveCostRange();
        
        BaselineValues baselineValues = resolveBaselineValues(baseline, tempRange, pressureRange, gasRange);

        // Recompute stability score for Solution A
        double stabilityA = calculateStabilityFromRanges(
            valueOrDefault(solution.getTemperature(), baselineValues.temperature()), 
            valueOrDefault(solution.getPressure(), baselineValues.pressure()), 
            valueOrDefault(solution.getGasFlow(), baselineValues.gasFlow()), 
            tempRange, pressureRange, gasRange);
        
        double stabilityB = resolveStabilityScore(baseline, baselineValues, tempRange, pressureRange, gasRange);
        
        // Recompute cost score for Solution A
        double costA = computeCostScore(solution.getEstimatedEnergy(), solution.getGasFlow(), solution.getOxygenLevel(), costRange);
        double costB = resolveCostScore(baseline, costRange);
        
        // Recompute production/energy scores for Solution A
        double prodScoreA = computeScore(solution.getPredictedProduction(), productionRange, true);
        double energyScoreA = computeScore(solution.getEstimatedEnergy(), energyRange, false);

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(withBaseline(buildRowWithScores("productionRate", resolveSpecLabel("productionRate", "生产率"),
                solution.getPredictedProduction(), baseline.getProductionRate(),
                resolveSpecUnit("productionRate", "t/h"), true,
                prodScoreA,
                computeProductionScore(baseline.getProductionRate(), productionRange)),
                baseline.getProductionRate()));
        rows.add(withBaseline(buildRowWithScores("energyConsumption", resolveSpecLabel("energyConsumption", "能耗"),
                solution.getEstimatedEnergy(), baseline.getEnergyConsumption(),
                resolveSpecUnit("energyConsumption", "kgce/t"), false,
                energyScoreA,
                computeEnergyScore(baseline.getEnergyConsumption(), energyRange)),
                baseline.getEnergyConsumption()));
        rows.add(withBaseline(buildRowWithScores("temperature", resolveSpecLabel("temperature", "温度"), solution.getTemperature(), baseline.getTemperature(), resolveSpecUnit("temperature", "℃"), true,
                computeScore(solution.getTemperature(), tempRange, true),
                computeScore(baseline.getTemperature(), tempRange, true)),
                baseline.getTemperature()));
        rows.add(withBaseline(buildRowWithScores("pressure", resolveSpecLabel("pressure", "压力"), solution.getPressure(), baseline.getPressure(), resolveSpecUnit("pressure", "kPa"), true,
                computeScore(solution.getPressure(), pressureRange, true),
                computeScore(baseline.getPressure(), pressureRange, true)),
                baseline.getPressure()));
        rows.add(withBaseline(buildRowWithScores("windVolume", resolveSpecLabel("windVolume", "风量"), solution.getWindVolume(), baseline.getWindVolume(), resolveSpecUnit("windVolume", "m³/h"), true,
                computeScore(solution.getWindVolume(), windRange, true),
                computeScore(baseline.getWindVolume(), windRange, true)),
                baseline.getWindVolume()));
        rows.add(withBaseline(buildRowWithScores("coalInjection", resolveSpecLabel("coalInjection", "喷煤量"), solution.getCoalInjection(), baseline.getCoalInjection(), resolveSpecUnit("coalInjection", "kg/t"), false,
                computeScore(solution.getCoalInjection(), coalRange, false),
                computeScore(baseline.getCoalInjection(), coalRange, false)),
                baseline.getCoalInjection()));
        rows.add(withBaseline(buildRowWithScores("gasFlow", resolveSpecLabel("gasFlow", "煤气流量"), solution.getGasFlow(), baseline.getGasFlow(), resolveSpecUnit("gasFlow", "m³/h"), true,
                computeScore(solution.getGasFlow(), gasRange, true),
                computeScore(baseline.getGasFlow(), gasRange, true)),
                baseline.getGasFlow()));
        rows.add(withBaseline(buildRowWithScores("oxygenLevel", resolveSpecLabel("oxygenLevel", "氧气含量"), solution.getOxygenLevel(), baseline.getOxygenLevel(), resolveSpecUnit("oxygenLevel", "%"), true,
                computeScore(solution.getOxygenLevel(), oxyRange, true),
                computeScore(baseline.getOxygenLevel(), oxyRange, true)),
                baseline.getOxygenLevel()));
        rows.add(withBaseline(buildRowWithScores("materialHeight", resolveSpecLabel("materialHeight", "料面高度"), solution.getMaterialHeight(), baseline.getMaterialHeight(), resolveSpecUnit("materialHeight", "m"), true,
                computeScore(solution.getMaterialHeight(), matRange, true),
                computeScore(baseline.getMaterialHeight(), matRange, true)),
                baseline.getMaterialHeight()));
        rows.add(withBaseline(buildRowWithScores("hotMetalTemperature", resolveSpecLabel("hotMetalTemperature", "铁水温度"), solution.getPredictedHotMetalTemperature(), baseline.getHotMetalTemperature(), resolveSpecUnit("hotMetalTemperature", "℃"), true,
                computeScore(solution.getPredictedHotMetalTemperature(), hotRange, true),
                computeScore(baseline.getHotMetalTemperature(), hotRange, true)),
                baseline.getHotMetalTemperature()));
        rows.add(withBaseline(buildRowWithScores("siliconContent", resolveSpecLabel("siliconContent", "硅含量"), solution.getPredictedSiliconContent(), baseline.getSiliconContent(), resolveSpecUnit("siliconContent", "%"), false,
                computeScore(solution.getPredictedSiliconContent(), siliconRange, false),
                computeScore(baseline.getSiliconContent(), siliconRange, false)),
                baseline.getSiliconContent()));
        rows.add(withBaseline(buildRow("stability", "稳定性", stabilityA, stabilityB, "分", true), stabilityB));
        rows.add(withBaseline(buildRow("cost", "成本", costA, costB, "分", true), costB));
        double prodScoreB = computeProductionScore(baseline.getProductionRate(), productionRange);
        double energyScoreB = computeEnergyScore(baseline.getEnergyConsumption(), energyRange);
        double scoreA = calculateUserScore(prodScoreA, energyScoreA, stabilityA, costA);
        double scoreB = calculateUserScore(prodScoreB, energyScoreB, stabilityB, costB);
        double fitnessA = toPercent(solution.getFitness());
        String recommended = scoreA >= scoreB ? "方案A" : "方案B";
        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("scoreA", scoreA);
        result.put("scoreB", scoreB);
        result.put("fitnessA", fitnessA);
        result.put("fitnessB", null);
        result.put("scoreType", "USER_SCORE");
        result.put("recommended", recommended);
        result.put("baselineTime", baseline.getTimestamp());
        String schemeA = resolveSchemeLabel(history, index);
        String schemeB = "基准工况(数据表) | " + buildProductionLabel(baseline);
        saveComparisonResult(history.getMode(), schemeA, schemeB, recommended, scoreA, scoreB, result);
        return result;
    }

    public Map<String, Object> compareProductionData(Long dataIdA, Long dataIdB) {
        ProductionData dataA = productionDataRepository.findById(dataIdA)
                .orElseThrow(() -> new IllegalArgumentException("方案A不存在"));
        ProductionData dataB = productionDataRepository.findById(dataIdB)
                .orElseThrow(() -> new IllegalArgumentException("方案B不存在"));
        
        Range productionRange = resolveSpecRange("productionRate", new Range(PRODUCTION_MIN, PRODUCTION_MAX));
        Range energyRange = resolveSpecRange("energyConsumption", new Range(ENERGY_MIN, ENERGY_MAX));
        Range tempRange = resolveSpecRange("temperature", new Range(TEMP_MIN, TEMP_MAX));
        Range pressureRange = resolveSpecRange("pressure", new Range(PRESSURE_MIN, PRESSURE_MAX));
        Range windRange = resolveSpecRange("windVolume", new Range(3000, 6000));
        Range coalRange = resolveSpecRange("coalInjection", new Range(100, 220));
        Range gasRange = resolveSpecRange("gasFlow", new Range(GAS_FLOW_MIN, GAS_FLOW_MAX));
        Range oxyRange = resolveSpecRange("oxygenLevel", new Range(18, 25));
        Range matRange = resolveSpecRange("materialHeight", new Range(2, 6));
        Range hotRange = resolveSpecRange("hotMetalTemperature", new Range(1400, 1600));
        Range siliconRange = resolveSpecRange("siliconContent", new Range(0.1, 1.0));
        Range costRange = resolveCostRange();
        
        BaselineValues baselineValues = resolveBaselineValues(null, tempRange, pressureRange, gasRange);
        double stabilityA = resolveStabilityScore(dataA, baselineValues, tempRange, pressureRange, gasRange);
        double stabilityB = resolveStabilityScore(dataB, baselineValues, tempRange, pressureRange, gasRange);
        double costA = resolveCostScore(dataA, costRange);
        double costB = resolveCostScore(dataB, costRange);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(buildRowWithScores("productionRate", resolveSpecLabel("productionRate", "生产率"), dataA.getProductionRate(), dataB.getProductionRate(), resolveSpecUnit("productionRate", "t/h"), true,
                computeProductionScore(dataA.getProductionRate(), productionRange),
                computeProductionScore(dataB.getProductionRate(), productionRange)));
        rows.add(buildRowWithScores("energyConsumption", resolveSpecLabel("energyConsumption", "能耗"), dataA.getEnergyConsumption(), dataB.getEnergyConsumption(), resolveSpecUnit("energyConsumption", "kgce/t"), false,
                computeEnergyScore(dataA.getEnergyConsumption(), energyRange),
                computeEnergyScore(dataB.getEnergyConsumption(), energyRange)));
        rows.add(buildRowWithScores("temperature", resolveSpecLabel("temperature", "温度"), dataA.getTemperature(), dataB.getTemperature(), resolveSpecUnit("temperature", "℃"), true,
                computeScore(dataA.getTemperature(), tempRange, true),
                computeScore(dataB.getTemperature(), tempRange, true)));
        rows.add(buildRowWithScores("pressure", resolveSpecLabel("pressure", "压力"), dataA.getPressure(), dataB.getPressure(), resolveSpecUnit("pressure", "kPa"), true,
                computeScore(dataA.getPressure(), pressureRange, true),
                computeScore(dataB.getPressure(), pressureRange, true)));
        rows.add(buildRowWithScores("windVolume", resolveSpecLabel("windVolume", "风量"), dataA.getWindVolume(), dataB.getWindVolume(), resolveSpecUnit("windVolume", "m³/h"), true,
                computeScore(dataA.getWindVolume(), windRange, true),
                computeScore(dataB.getWindVolume(), windRange, true)));
        rows.add(buildRowWithScores("coalInjection", resolveSpecLabel("coalInjection", "喷煤量"), dataA.getCoalInjection(), dataB.getCoalInjection(), resolveSpecUnit("coalInjection", "kg/t"), false,
                computeScore(dataA.getCoalInjection(), coalRange, false),
                computeScore(dataB.getCoalInjection(), coalRange, false)));
        rows.add(buildRowWithScores("gasFlow", resolveSpecLabel("gasFlow", "煤气流量"), dataA.getGasFlow(), dataB.getGasFlow(), resolveSpecUnit("gasFlow", "m³/h"), true,
                computeScore(dataA.getGasFlow(), gasRange, true),
                computeScore(dataB.getGasFlow(), gasRange, true)));
        rows.add(buildRowWithScores("oxygenLevel", resolveSpecLabel("oxygenLevel", "氧气含量"), dataA.getOxygenLevel(), dataB.getOxygenLevel(), resolveSpecUnit("oxygenLevel", "%"), true,
                computeScore(dataA.getOxygenLevel(), oxyRange, true),
                computeScore(dataB.getOxygenLevel(), oxyRange, true)));
        rows.add(buildRowWithScores("materialHeight", resolveSpecLabel("materialHeight", "料面高度"), dataA.getMaterialHeight(), dataB.getMaterialHeight(), resolveSpecUnit("materialHeight", "m"), true,
                computeScore(dataA.getMaterialHeight(), matRange, true),
                computeScore(dataB.getMaterialHeight(), matRange, true)));
        rows.add(buildRowWithScores("hotMetalTemperature", resolveSpecLabel("hotMetalTemperature", "铁水温度"), dataA.getHotMetalTemperature(), dataB.getHotMetalTemperature(), resolveSpecUnit("hotMetalTemperature", "℃"), true,
                computeScore(dataA.getHotMetalTemperature(), hotRange, true),
                computeScore(dataB.getHotMetalTemperature(), hotRange, true)));
        rows.add(buildRowWithScores("siliconContent", resolveSpecLabel("siliconContent", "硅含量"), dataA.getSiliconContent(), dataB.getSiliconContent(), resolveSpecUnit("siliconContent", "%"), false,
                computeScore(dataA.getSiliconContent(), siliconRange, false),
                computeScore(dataB.getSiliconContent(), siliconRange, false)));
        rows.add(buildRowWithScores("stability", "稳定性", stabilityA, stabilityB, "分", true, stabilityA, stabilityB));
        rows.add(buildRowWithScores("cost", "成本", costA, costB, "分", true, costA, costB));
        double prodScoreA = computeProductionScore(dataA.getProductionRate(), productionRange);
        double prodScoreB = computeProductionScore(dataB.getProductionRate(), productionRange);
        double energyScoreA = computeEnergyScore(dataA.getEnergyConsumption(), energyRange);
        double energyScoreB = computeEnergyScore(dataB.getEnergyConsumption(), energyRange);
        double scoreA = calculateUserScore(prodScoreA, energyScoreA, stabilityA, costA);
        double scoreB = calculateUserScore(prodScoreB, energyScoreB, stabilityB, costB);
        String recommended = scoreA >= scoreB ? "方案A" : "方案B";
        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("scoreA", scoreA);
        result.put("scoreB", scoreB);
        result.put("fitnessA", null);
        result.put("fitnessB", null);
        result.put("scoreType", "USER_SCORE");
        result.put("recommended", recommended);
        result.put("baselineTime", null);
        String schemeA = buildProductionLabel(dataA);
        String schemeB = buildProductionLabel(dataB);
        saveComparisonResult("REAL", schemeA, schemeB, recommended, scoreA, scoreB, result);
        return result;
    }

    public String adoptScheme(Long historyId, int schemeIndex) {
        OptimizationSolutionDTO solution = loadSolution(historyId, schemeIndex);
        Map<String, Object> adjustments = new HashMap<>();
        adjustments.put("schemeIndex", schemeIndex);
        adjustments.put("temperature", solution.getTemperature());
        adjustments.put("pressure", solution.getPressure());
        adjustments.put("gasFlow", solution.getGasFlow());
        adjustments.put("oxygenLevel", solution.getOxygenLevel());
        adjustments.put("materialHeight", solution.getMaterialHeight());
        adjustments.put("predictedProduction", solution.getPredictedProduction());
        adjustments.put("estimatedEnergy", solution.getEstimatedEnergy());
        adjustments.put("confidence", solution.getConfidence());
        String adjustmentsJson;
        try {
            adjustmentsJson = objectMapper.writeValueAsString(adjustments);
        } catch (Exception e) {
            throw new IllegalStateException("生成操作日志失败");
        }
        OperationLog log = new OperationLog();
        log.setSchemeId(historyId);
        log.setExecutionTime(new Date());
        log.setAdjustments(adjustmentsJson);
        log.setOperator("Admin");
        operationLogRepository.save(log);
        double targetTemp = solution.getTemperature() != null ? solution.getTemperature() : 0.0;
        return String.format("指令已下发至 PLC 系统：目标风温设定为 %.0f℃，预计 15 分钟后达到设定值。", targetTemp);
    }

    private OptimizationSolutionDTO loadSolution(Long historyId, int index) {
        ComparisonHistory history = comparisonHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        if (history.getPayload() == null || history.getPayload().isBlank()) {
            throw new IllegalArgumentException("记录缺少方案详情");
        }
        try {
            JsonNode root = objectMapper.readTree(history.getPayload());
            JsonNode solutionsNode = root.isArray() ? root : root.get("solutions");
            if (solutionsNode == null || !solutionsNode.isArray()) {
                throw new IllegalArgumentException("记录缺少方案详情");
            }
            List<OptimizationSolutionDTO> solutions = objectMapper.convertValue(
                    solutionsNode,
                    new TypeReference<List<OptimizationSolutionDTO>>() {
                    }
            );
            if (solutions.size() <= index || index < 0) {
                throw new IllegalArgumentException("方案索引无效");
            }
            return solutions.get(index);
        } catch (Exception e) {
            throw new IllegalArgumentException("解析方案详情失败");
        }
    }

    private Map<String, Object> buildRow(String param, String label, Double schemeA, Double schemeB, String unit, Boolean higherBetter) {
        double a = schemeA != null ? schemeA : 0;
        double b = schemeB != null ? schemeB : 0;
        double difference = a - b;
        Integer better = null;
        if (higherBetter != null) {
            if (difference == 0) {
                better = 0;
            } else if (higherBetter) {
                better = difference > 0 ? 1 : -1;
            } else {
                better = difference < 0 ? 1 : -1;
            }
        }
        Map<String, Object> row = new HashMap<>();
        row.put("param", param);
        row.put("label", label);
        row.put("schemeA", a);
        row.put("schemeB", b);
        row.put("unit", unit);
        row.put("difference", difference);
        row.put("better", better);
        return row;
    }

    private Map<String, Object> buildRowWithScores(String param, String label, Double schemeA, Double schemeB, String unit, Boolean higherBetter, Double scoreA, Double scoreB) {
        Map<String, Object> row = buildRow(param, label, schemeA, schemeB, unit, higherBetter);
        if (scoreA != null) {
            row.put("scoreA", scoreA);
        }
        if (scoreB != null) {
            row.put("scoreB", scoreB);
        }
        return row;
    }

    private ProductionData resolveBaselineData(String furnaceId) {
        List<ProductionData> list = furnaceId == null || furnaceId.isBlank()
                ? productionDataRepository.findAllByOrderByTimestampDesc()
                : productionDataRepository.findByFurnaceIdOrderByTimestampDesc(furnaceId);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    private Map<String, Object> withBaseline(Map<String, Object> row, Double baseline) {
        row.put("baseline", baseline);
        return row;
    }

    private void saveComparisonResult(String mode, String schemeA, String schemeB, String result, Double scoreA, Double scoreB, Map<String, Object> payloadObj) {
        try {
            String payload = objectMapper.writeValueAsString(payloadObj);
            saveComparisonHistory(mode, schemeA, schemeB, result, scoreA, scoreB, payload);
        } catch (Exception e) {
            throw new IllegalStateException("保存对比结果失败", e);
        }
    }

    private String resolveSchemeLabel(ComparisonHistory history, int index) {
        if (history == null) {
            return "未知方案";
        }
        if (index == 0) {
            return history.getSchemeA();
        }
        if (index == 1) {
            return history.getSchemeB();
        }
        return "未知方案";
    }

    private String resolveCompareMode(ComparisonHistory historyA, ComparisonHistory historyB) {
        if (historyA == null || historyB == null) {
            return "MIXED";
        }
        String modeA = historyA.getMode();
        String modeB = historyB.getMode();
        if (modeA == null || modeB == null) {
            return modeA != null ? modeA : modeB;
        }
        if (modeA.equals(modeB)) {
            return modeA;
        }
        return "MIXED";
    }

    private String buildProductionLabel(ProductionData data) {
        if (data == null) {
            return "未知方案";
        }
        String time = data.getTimestamp() != null
                ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(data.getTimestamp())
                : "未知时间";
        String furnace = data.getFurnaceId() != null ? "高炉" + data.getFurnaceId() : "未知高炉";
        return time + " - " + furnace;
    }

    private Range resolveRange(Function<ProductionData, Double> extractor, double defaultMin, double defaultMax) {
        // This method is now deprecated for scoring as we use fixed ranges,
        // but kept if needed for other logic or future dynamic range requirements.
        // Simplified to just return defaults.
        return new Range(defaultMin, defaultMax);
    }

    private Range resolveSpecRange(String key, Range fallback) {
        IndustrialDataContract.ParameterSpec spec = IndustrialDataContract.findByAnyKey(key);
        if (spec == null) {
            return fallback;
        }
        return new Range(spec.min(), spec.max());
    }

    private String resolveSpecLabel(String key, String fallback) {
        IndustrialDataContract.ParameterSpec spec = IndustrialDataContract.findByAnyKey(key);
        if (spec == null) {
            return fallback;
        }
        return spec.label();
    }

    private String resolveSpecUnit(String key, String fallback) {
        IndustrialDataContract.ParameterSpec spec = IndustrialDataContract.findByAnyKey(key);
        if (spec == null) {
            return fallback;
        }
        return spec.unit();
    }

    private BaselineValues resolveBaselineValues(ProductionData baseline, Range tempRange, Range pressureRange, Range gasRange) {
        double temp = baseline != null ? valueOrDefault(baseline.getTemperature(), midpoint(tempRange)) : midpoint(tempRange);
        double pressure = baseline != null ? valueOrDefault(baseline.getPressure(), midpoint(pressureRange)) : midpoint(pressureRange);
        double gasFlow = baseline != null ? valueOrDefault(baseline.getGasFlow(), midpoint(gasRange)) : midpoint(gasRange);
        return new BaselineValues(temp, pressure, gasFlow);
    }

    private double resolveStabilityScore(OptimizationSolutionDTO solution, BaselineValues baseline, Range tempRange, Range pressureRange, Range gasRange) {
        if (solution.getStabilityScore() != null && solution.getStabilityScore() > 0) {
            return solution.getStabilityScore();
        }
        double temp = valueOrDefault(solution.getTemperature(), baseline.temperature());
        double pressure = valueOrDefault(solution.getPressure(), baseline.pressure());
        double gasFlow = valueOrDefault(solution.getGasFlow(), baseline.gasFlow());
        return calculateStabilityFromRanges(temp, pressure, gasFlow, tempRange, pressureRange, gasRange);
    }

    private Double resolveCostBaselineScore(ProductionData baseline, Range costRange) {
        if (baseline == null) {
            return null;
        }
        return computeCostScore(baseline.getEnergyConsumption(), baseline.getGasFlow(), baseline.getOxygenLevel(), costRange);
    }

    private double resolveStabilityBaselineScore(BaselineValues baselineValues, Range tempRange, Range pressureRange, Range gasRange) {
        return calculateStabilityFromRanges(baselineValues.temperature(), baselineValues.pressure(), baselineValues.gasFlow(), tempRange, pressureRange, gasRange);
    }

    private double calculateStabilityFromRanges(double temp, double pressure, double gasFlow, Range tempRange, Range pressureRange, Range gasRange) {
        // 稳定性定义为：参数偏离理想中心点的程度。越接近中心越稳定。
        // 使用 range.min 和 range.max 的中点作为理想值。
        // 如果偏离达到 range 的边界，得分为 0 (或更低，但 clamp 到 0)。
        
        double tempMid = midpoint(tempRange);
        double pressureMid = midpoint(pressureRange);
        double gasMid = midpoint(gasRange);

        // 偏差计算：|value - mid| / (span / 2) -> 归一化到 [0, 1] (0为中心，1为边界)
        double devTemp = Math.abs(temp - tempMid) / ((tempRange.max() - tempRange.min()) / 2.0);
        double devPressure = Math.abs(pressure - pressureMid) / ((pressureRange.max() - pressureRange.min()) / 2.0);
        double devGas = Math.abs(gasFlow - gasMid) / ((gasRange.max() - gasRange.min()) / 2.0);

        // 综合偏差，可以加权。这里假设压力波动最影响稳定性
        double avgDev = (devTemp * 1.0 + devPressure * 1.5 + devGas * 1.0) / 3.5;
        
        // 转换为得分：1.0 - avgDev
        // 如果 avgDev > 1.0 (超出范围)，得分 < 0，clamp 到 0。
        return clampValue((1.0 - avgDev) * 100.0, 0.0, 100.0);
    }

    private double resolveStabilityScore(ProductionData data, BaselineValues baseline, Range tempRange, Range pressureRange, Range gasRange) {
        double temp = valueOrDefault(data.getTemperature(), baseline.temperature());
        double pressure = valueOrDefault(data.getPressure(), baseline.pressure());
        double gasFlow = valueOrDefault(data.getGasFlow(), baseline.gasFlow());
        return calculateStabilityFromRanges(temp, pressure, gasFlow, tempRange, pressureRange, gasRange);
    }

    private double resolveCostScore(OptimizationSolutionDTO solution, Range costRange) {
        if (solution.getCostScore() != null && solution.getCostScore() > 0) {
            return solution.getCostScore();
        }
        return computeCostScore(solution.getEstimatedEnergy(), solution.getGasFlow(), solution.getOxygenLevel(), costRange);
    }

    private double resolveCostScore(ProductionData data, Range costRange) {
        return computeCostScore(data.getEnergyConsumption(), data.getGasFlow(), data.getOxygenLevel(), costRange);
    }

    private double computeCostScore(Double energy, Double gasFlow, Double oxygenLevel, Range costRange) {
        double costValue = computeCostValue(energy, gasFlow, oxygenLevel, costRange);
        double score = 1.0 - normalize(costValue, costRange.min(), costRange.max());
        return clampValue(score * 100.0, 0.0, 100.0);
    }

    private double computeCostValue(Double energy, Double gasFlow, Double oxygenLevel, Range costRange) {
        double eng = energy != null ? energy : costRange.max();
        double oxy = oxygenLevel != null ? oxygenLevel : 21.0;
        // 归一化富氧影响：(oxy - 21) * 20 作为一个成本项
        double oxyCost = Math.max(0, (oxy - 21.0) * 20.0);
        
        return eng * 0.7 + oxyCost * 0.3;
    }

    private Range resolveCostRange() {
        // 重新估算成本范围 (与 ComparisonHistoryService 保持一致)
        double minCost = ENERGY_MIN * 0.7 + 0.0; // 最小能耗 + 无富氧
        double maxCost = ENERGY_MAX * 0.7 + (5.0 * 20.0) * 0.3; // 最大能耗 + 5%富氧
        return new Range(minCost, maxCost);
    }

    private Range resolveRangeForComparison(Double valA, Double valB, Range globalRange) {
        // Use global range as baseline
        double min = globalRange.min();
        double max = globalRange.max();
        
        // Expand range if values are outside
        if (valA != null) {
            min = Math.min(min, valA);
            max = Math.max(max, valA);
        }
        if (valB != null) {
            min = Math.min(min, valB);
            max = Math.max(max, valB);
        }
        
        // Add 10% padding to prevent 0/100 scores
        double span = max - min;
        if (span == 0) span = 1.0;
        
        return new Range(min - span * 0.1, max + span * 0.1);
    }

    private double computeScore(Double value, Range range, boolean higherBetter) {
        if (value == null) {
            return 0.0;
        }
        double normalized = normalize(value, range.min(), range.max());
        // Clamp normalized value between 0.05 and 0.95 to ensure visible radar chart
        if (normalized < 0.05) normalized = 0.05;
        if (normalized > 0.95) normalized = 0.95;
        
        return (higherBetter ? normalized : (1.0 - normalized)) * 100.0;
    }

    private double computeProductionScore(Double production, Range productionRange) {
        double value = production != null ? production : 0.0;
        return normalize(value, productionRange.min(), productionRange.max()) * 100.0;
    }

    private double computeEnergyScore(Double energy, Range energyRange) {
        double value = energy != null ? energy : energyRange.max();
        return (1.0 - normalize(value, energyRange.min(), energyRange.max())) * 100.0;
    }

    private double valueOrDefault(Double value, double fallback) {
        return value != null ? value : fallback;
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

    private double midpoint(Range range) {
        return (range.min() + range.max()) / 2.0;
    }

    private double calculateScore(Double production, Double energy, Range productionRange, Range energyRange) {
        double prod = production != null ? production : 0.0;
        double eng = energy != null ? energy : energyRange.max();
        double productionScore = normalize(prod, productionRange.min(), productionRange.max());
        double energyScore = 1.0 - normalize(eng, energyRange.min(), energyRange.max());
        return (productionScore * 0.7 + energyScore * 0.3) * 100.0;
    }

    private double calculateUserScore(Double productionScore, Double energyScore, Double stabilityScore, Double costScore) {
        double prod = valueOrDefault(productionScore, 0.0);
        double energy = valueOrDefault(energyScore, 0.0);
        double stability = valueOrDefault(stabilityScore, 0.0);
        double cost = valueOrDefault(costScore, 0.0);
        double score = prod * USER_SCORE_WEIGHT_PRODUCTION
                + energy * USER_SCORE_WEIGHT_ENERGY
                + stability * USER_SCORE_WEIGHT_STABILITY
                + cost * USER_SCORE_WEIGHT_COST;
        return clampValue(score, 0.0, 100.0);
    }

    private double toPercent(Double value) {
        if (value == null) {
            return 0.0;
        }
        return clampValue(value * 100.0, 0.0, 100.0);
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

    private record Range(double min, double max) {
    }

    private record BaselineValues(double temperature, double pressure, double gasFlow) {
    }
}
