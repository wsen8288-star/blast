package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.AnomalyRecord;
import com.blastfurnace.backend.model.AnomalyStatus;
import com.blastfurnace.backend.model.AnomalyThreshold;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.repository.AnomalyRecordRepository;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import com.blastfurnace.backend.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {
    private static final List<String> DEFAULT_MONITOR_PARAMS = List.of(
            "temperature",
            "pressure",
            "materialHeight",
            "gasFlow",
            "oxygenLevel",
            "productionRate",
            "energyConsumption",
            "hotMetalTemperature",
            "siliconContent"
    );
    private static final int MIN_STAT_SAMPLE_SIZE = 10;
    private static final int SINGLE_POINT_HISTORY_SIZE = 200;

    private final AnomalyRecordRepository anomalyRecordRepository;
    private final ProductionDataRepository productionDataRepository;
    private final com.blastfurnace.backend.repository.AnomalyThresholdRepository anomalyThresholdRepository;
    private final UserRepository userRepository;
    private final WarningBroadcastService warningBroadcastService;
    private final ThresholdResolverService thresholdResolverService;
    private final SysConfigService sysConfigService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask;
    private ScheduledConfig scheduledConfig;
    private volatile LocalDateTime lastScheduleRunTime;
    private volatile String lastBatchId;

    @Transactional
    public Map<String, Object> detectAnomalies(String furnaceId,
                                               List<String> params,
                                               String algorithm,
                                               String detectionMode,
                                               LocalDateTime startTime,
                                               LocalDateTime endTime,
                                               Integer batchSize) {
        String mode = normalizeMode(detectionMode);
        List<String> effectiveParams = (params == null || params.isEmpty())
                ? DEFAULT_MONITOR_PARAMS
                : params;
        if ("scheduled".equals(mode)) {
            int interval = 10;
            Map<String, Object> started = startScheduledDetection(furnaceId, effectiveParams, algorithm, interval, batchSize);
            started.put("mode", "scheduled");
            return started;
        }
        List<ProductionData> sourceData = loadDetectionData(mode, furnaceId, startTime, endTime, batchSize);
        return runDetectionBatch(furnaceId, effectiveParams, algorithm, mode, sourceData);
    }

    @Transactional
    public Map<String, Object> detectCollectedPoint(ProductionData currentData,
                                                    List<String> params,
                                                    String algorithm) {
        if (currentData == null || currentData.getId() == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("batchId", UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            empty.put("mode", "realtime");
            empty.put("created", 0);
            empty.put("processedRows", 0);
            empty.put("sampleSize", 0);
            empty.put("runAt", LocalDateTime.now());
            return empty;
        }

        String furnaceId = currentData.getFurnaceId();
        List<String> effectiveParams = (params == null || params.isEmpty()) ? DEFAULT_MONITOR_PARAMS : params;
        List<ProductionData> history = (furnaceId == null || furnaceId.isBlank())
                ? productionDataRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, SINGLE_POINT_HISTORY_SIZE))
                : productionDataRepository.findByFurnaceIdOrderByTimestampDesc(furnaceId, PageRequest.of(0, SINGLE_POINT_HISTORY_SIZE));
        if (history.stream().noneMatch(item -> item != null && Objects.equals(item.getId(), currentData.getId()))) {
            history = new ArrayList<>(history);
            history.add(0, currentData);
        }

        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime detectionTime = LocalDateTime.now();
        Map<String, ThresholdConfig> thresholdCache = loadThresholdConfigCache(furnaceId, effectiveParams);
        Set<String> existingPairs = loadExistingPairs(furnaceId, effectiveParams, List.of(currentData));
        List<AnomalyRecord> createdRecords = new ArrayList<>();
        int created = 0;

        for (String param : effectiveParams) {
            String pairKey = buildPairKey(currentData.getId(), param);
            if (existingPairs.contains(pairKey)) {
                continue;
            }
            created += checkParameter(
                    furnaceId,
                    param,
                    currentData,
                    history,
                    detectionTime,
                    algorithm,
                    thresholdCache.get(param),
                    createdRecords
            );
        }

        if (!createdRecords.isEmpty()) {
            broadcastAfterCommit(createdRecords);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("batchId", batchId);
        summary.put("mode", "realtime");
        summary.put("created", created);
        summary.put("processedRows", 1);
        summary.put("sampleSize", history.size());
        summary.put("runAt", LocalDateTime.now());
        return summary;
    }

    public synchronized Map<String, Object> startScheduledDetection(String furnaceId,
                                                                    List<String> params,
                                                                    String algorithm,
                                                                    Integer intervalSeconds,
                                                                    Integer batchSize) {
        stopScheduledDetection();
        int interval = Math.max(5, Math.min(intervalSeconds == null ? 10 : intervalSeconds, 3600));
        int size = Math.max(10, Math.min(batchSize == null ? 50 : batchSize, 500));
        scheduledConfig = new ScheduledConfig(furnaceId, params, algorithm, interval, size);
        scheduledTask = scheduler.scheduleAtFixedRate(this::runScheduledTask, 0, interval, TimeUnit.SECONDS);
        Map<String, Object> result = new HashMap<>();
        result.put("running", true);
        result.put("intervalSeconds", interval);
        result.put("batchSize", size);
        return result;
    }

    public synchronized Map<String, Object> stopScheduledDetection() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        scheduledConfig = null;
        Map<String, Object> result = new HashMap<>();
        result.put("running", false);
        result.put("lastRunTime", lastScheduleRunTime);
        result.put("lastBatchId", lastBatchId);
        return result;
    }

    public Map<String, Object> getScheduleStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("running", scheduledTask != null && !scheduledTask.isCancelled());
        result.put("lastRunTime", lastScheduleRunTime);
        result.put("lastBatchId", lastBatchId);
        result.put("furnaceId", scheduledConfig == null ? null : scheduledConfig.furnaceId());
        result.put("intervalSeconds", scheduledConfig == null ? null : scheduledConfig.intervalSeconds());
        return result;
    }

    private void runScheduledTask() {
        try {
            ScheduledConfig config = scheduledConfig;
            if (config == null) {
                return;
            }
            List<ProductionData> data = loadDetectionData("realtime", config.furnaceId(), null, null, config.batchSize());
            Map<String, Object> summary = runDetectionBatch(config.furnaceId(), config.params(), config.algorithm(), "scheduled", data);
            lastScheduleRunTime = LocalDateTime.now();
            lastBatchId = String.valueOf(summary.get("batchId"));
        } catch (Exception e) {
            log.error("Scheduled anomaly detection failed", e);
        }
    }

    private List<ProductionData> loadDetectionData(String mode,
                                                   String furnaceId,
                                                   LocalDateTime startTime,
                                                   LocalDateTime endTime,
                                                   Integer batchSize) {
        int size = Math.max(10, Math.min(batchSize == null ? 50 : batchSize, 1000));
        if ("batch".equals(mode)) {
            LocalDateTime start = startTime == null ? LocalDateTime.now().minusDays(1) : startTime;
            LocalDateTime end = endTime == null ? LocalDateTime.now() : endTime;
            Date startDate = toDate(start);
            Date endDate = toDate(end);
            List<ProductionData> raw = (furnaceId == null || furnaceId.isBlank())
                    ? productionDataRepository.findByTimestampBetween(startDate, endDate)
                    : productionDataRepository.findByFurnaceIdAndTimestampBetween(furnaceId, startDate, endDate);
            return raw.stream()
                    .sorted((a, b) -> {
                        Date ta = a == null ? null : a.getTimestamp();
                        Date tb = b == null ? null : b.getTimestamp();
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return tb.compareTo(ta);
                    })
                    .limit(size)
                    .collect(Collectors.toList());
        }
        if (furnaceId == null || furnaceId.isBlank()) {
            return productionDataRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, size));
        }
        return productionDataRepository.findByFurnaceIdOrderByTimestampDesc(furnaceId, PageRequest.of(0, size));
    }

    @Transactional
    protected Map<String, Object> runDetectionBatch(String furnaceId,
                                                    List<String> params,
                                                    String algorithm,
                                                    String mode,
                                                    List<ProductionData> sourceData) {
        List<ProductionData> recentData = sourceData == null ? List.of() : sourceData;
        List<String> effectiveParams = (params == null ? List.<String>of() : params).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int created = 0;
        int processedRows = 0;
        if (recentData.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("batchId", batchId);
            empty.put("mode", mode);
            empty.put("created", 0);
            empty.put("processedRows", 0);
            empty.put("sampleSize", 0);
            empty.put("runAt", LocalDateTime.now());
            return empty;
        }
        if (effectiveParams.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("batchId", batchId);
            empty.put("mode", mode);
            empty.put("created", 0);
            empty.put("processedRows", recentData.size());
            empty.put("sampleSize", recentData.size());
            empty.put("runAt", LocalDateTime.now());
            return empty;
        }
        Map<String, List<ProductionData>> dataByFurnace = recentData.stream()
                .collect(Collectors.groupingBy(d -> {
                    String currentFurnaceId = d == null ? null : d.getFurnaceId();
                    if (currentFurnaceId == null || currentFurnaceId.isBlank()) {
                        return "GLOBAL";
                    }
                    return currentFurnaceId;
                }));
        Map<String, Map<String, ThresholdConfig>> thresholdCacheByFurnace = new HashMap<>();
        Map<String, Set<String>> existingPairsByFurnace = new HashMap<>();
        List<AnomalyRecord> createdRecords = new ArrayList<>();
        LocalDateTime detectionTime = LocalDateTime.now();
        for (ProductionData data : recentData) {
            processedRows++;
            Long relatedDataId = data.getId();
            if (relatedDataId == null) {
                continue;
            }
            String currentFurnaceId = (furnaceId == null || furnaceId.isBlank())
                    ? Optional.ofNullable(data.getFurnaceId()).filter(s -> !s.isBlank()).orElse("GLOBAL")
                    : furnaceId;
            Map<String, ThresholdConfig> thresholdCache = thresholdCacheByFurnace.computeIfAbsent(
                    currentFurnaceId,
                    key -> loadThresholdConfigCache(key, effectiveParams)
            );
            Set<String> existingPairs = existingPairsByFurnace.computeIfAbsent(
                    currentFurnaceId,
                    key -> loadExistingPairs(key, effectiveParams, dataByFurnace.getOrDefault(key, List.of()))
            );
            for (String param : effectiveParams) {
                String pairKey = buildPairKey(relatedDataId, param);
                if (existingPairs.contains(pairKey)) {
                    continue;
                }
                int before = createdRecords.size();
                created += checkParameter(
                        currentFurnaceId,
                        param,
                        data,
                        recentData,
                        detectionTime,
                        algorithm,
                        thresholdCache.get(param),
                        createdRecords
                );
                if (createdRecords.size() > before) {
                    existingPairs.add(pairKey);
                }
            }
        }
        broadcastAfterCommit(createdRecords);
        Map<String, Object> summary = new HashMap<>();
        summary.put("batchId", batchId);
        summary.put("mode", mode);
        summary.put("created", created);
        summary.put("processedRows", processedRows);
        summary.put("sampleSize", recentData.size());
        summary.put("runAt", LocalDateTime.now());
        return summary;
    }

    private int checkParameter(String furnaceId,
                               String param,
                               ProductionData current,
                               List<ProductionData> history,
                               LocalDateTime detectionTime,
                               String algorithm,
                               ThresholdConfig thresholdConfig,
                               List<AnomalyRecord> createdRecords) {
        Double value = getParamValue(current, param);
        if (value == null) return 0;
        boolean useThreshold = algorithm == null || "ALL".equalsIgnoreCase(algorithm) || "THRESHOLD".equalsIgnoreCase(algorithm);
        boolean useZScore = algorithm == null || "ALL".equalsIgnoreCase(algorithm) || "Z_SCORE".equalsIgnoreCase(algorithm);
        boolean useIQR = algorithm == null || "ALL".equalsIgnoreCase(algorithm) || "IQR".equalsIgnoreCase(algorithm);

        if (useThreshold) {
            ThresholdConfig config = thresholdConfig != null ? thresholdConfig : getThresholdConfig(param, furnaceId);
            if (config != null && (value > config.max || value < config.min)) {
                String level = deriveThresholdLevel(value, config);
                createAnomalyRecord(
                        furnaceId,
                        param,
                        value,
                        config.getRangeString(),
                        level,
                        "Value out of range (Threshold)",
                        detectionTime,
                        current.getId(),
                        createdRecords
                );
                if (algorithm == null || "ALL".equalsIgnoreCase(algorithm) || "THRESHOLD".equalsIgnoreCase(algorithm)) {
                    return 1;
                }
            }
        }

        int created = 0;
        List<Double> values = history.stream()
                .filter(d -> {
                    if (d == null) return false;
                    if (current.getId() != null && current.getId().equals(d.getId())) return false;
                    String currentFurnaceId = furnaceId == null ? "" : furnaceId;
                    String itemFurnaceId = d.getFurnaceId() == null ? "" : d.getFurnaceId();
                    return currentFurnaceId.equals(itemFurnaceId);
                })
                .map(d -> getParamValue(d, param))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (useZScore && values.size() >= MIN_STAT_SAMPLE_SIZE) {
            double mean = calculateMean(values);
            double stdDev = calculateStdDev(values, mean);
            if (stdDev > 0) {
                double zScore = Math.abs((value - mean) / stdDev);
                if (zScore > 3.0) {
                    createAnomalyRecord(
                            furnaceId,
                            param,
                            value,
                            "Z-Score < 3.0",
                            "提示",
                            String.format("Statistical anomaly (Z-Score: %.2f)", zScore),
                            detectionTime,
                            current.getId(),
                            createdRecords
                    );
                    created++;
                }
            }
        }

        if (useIQR && values.size() >= MIN_STAT_SAMPLE_SIZE) {
            values.sort(Double::compareTo);
            double q1 = getPercentile(values, 25);
            double q3 = getPercentile(values, 75);
            double iqr = q3 - q1;
            double lowerBound = q1 - 1.5 * iqr;
            double upperBound = q3 + 1.5 * iqr;
            if (value < lowerBound || value > upperBound) {
                createAnomalyRecord(
                        furnaceId,
                        param,
                        value,
                        String.format("[%.2f, %.2f]", lowerBound, upperBound),
                        "提示",
                        "Statistical anomaly (IQR Outlier)",
                        detectionTime,
                        current.getId(),
                        createdRecords
                );
                created++;
            }
        }
        return created;
    }

    private double getPercentile(List<Double> sortedValues, double percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(sortedValues.size() - 1, index)));
    }

    private void createAnomalyRecord(String furnaceId,
                                     String param,
                                     Double value,
                                     String range,
                                     String level,
                                     String desc,
                                     LocalDateTime time,
                                     Long relatedDataId,
                                     List<AnomalyRecord> createdRecords) {
        AnomalyRecord record = new AnomalyRecord();
        record.setFurnaceId(furnaceId);
        record.setDetectionTime(time);
        record.setParameterName(param);
        record.setActualValue(value);
        record.setExpectedRange(range);
        record.setLevel(level);
        record.setStatus(AnomalyStatus.PENDING.getCode());
        record.setDescription(desc);
        record.setRelatedDataId(relatedDataId);
        AnomalyRecord saved = anomalyRecordRepository.save(record);
        createdRecords.add(saved);
        log.info("Anomaly detected: {} - {} = {}", furnaceId, param, value);
    }

    private String buildPairKey(Long relatedDataId, String param) {
        return relatedDataId + ":" + param;
    }

    private Set<String> loadExistingPairs(String furnaceId, List<String> params, List<ProductionData> sourceData) {
        List<Long> relatedDataIds = sourceData.stream()
                .map(ProductionData::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (relatedDataIds.isEmpty() || params.isEmpty()) {
            return new java.util.HashSet<>();
        }
        return anomalyRecordRepository.findExistingPairs(furnaceId, relatedDataIds, params).stream()
                .map(row -> buildPairKey((Long) row[0], String.valueOf(row[1])))
                .collect(Collectors.toSet());
    }

    private Map<String, ThresholdConfig> loadThresholdConfigCache(String furnaceId, List<String> params) {
        if (params.isEmpty()) {
            return Map.of();
        }
        Map<String, ThresholdConfig> cache = new HashMap<>();
        for (String param : params) {
            var resolved = thresholdResolverService.resolve(furnaceId, param);
            if (resolved != null) {
                cache.put(param, new ThresholdConfig(
                        resolved.min(),
                        resolved.max(),
                        resolved.tipOffsetPct(),
                        resolved.warningOffsetPct(),
                        resolved.severeOffsetPct()
                ));
            }
        }
        return cache;
    }

    private void broadcastAfterCommit(List<AnomalyRecord> createdRecords) {
        if (createdRecords == null || createdRecords.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    createdRecords.forEach(warningBroadcastService::broadcastNewWarning);
                }
            });
            return;
        }
        createdRecords.forEach(warningBroadcastService::broadcastNewWarning);
    }

    private String deriveThresholdLevel(Double value, ThresholdConfig config) {
        if (value == null || config == null) return "提示";
        double width = config.max - config.min;
        if (width <= 0) return "警告";
        double diff = value > config.max ? (value - config.max) : (config.min - value);
        double ratio = diff / width;
        double severe = Math.max(0.0, config.severeOffsetPct / 100.0);
        double warning = Math.max(0.0, config.warningOffsetPct / 100.0);
        double tip = Math.max(0.0, config.tipOffsetPct / 100.0);
        if (ratio >= severe) return "严重";
        if (ratio >= warning) return "警告";
        if (ratio >= tip) return "提示";
        return "提示";
    }

    private double defaultPct(Double value, double defaultValue) {
        if (value == null) return defaultValue;
        if (Double.isNaN(value) || Double.isInfinite(value)) return defaultValue;
        return value;
    }

    private Double getParamValue(ProductionData data, String param) {
        switch (param) {
            case "temperature": return data.getTemperature();
            case "pressure": return data.getPressure();
            case "windVolume": return data.getWindVolume();
            case "coalInjection": return data.getCoalInjection();
            case "materialHeight": return data.getMaterialHeight();
            case "gasFlow": return data.getGasFlow();
            case "oxygenLevel": return data.getOxygenLevel();
            case "productionRate": return data.getProductionRate();
            case "energyConsumption": return data.getEnergyConsumption();
            case "hotMetalTemperature": return data.getHotMetalTemperature();
            case "siliconContent": return data.getSiliconContent();
            default: return null;
        }
    }

    private static class ThresholdConfig {
        double min;
        double max;
        double tipOffsetPct;
        double warningOffsetPct;
        double severeOffsetPct;
        ThresholdConfig(double min, double max, double tipOffsetPct, double warningOffsetPct, double severeOffsetPct) {
            this.min = min;
            this.max = max;
            this.tipOffsetPct = tipOffsetPct;
            this.warningOffsetPct = warningOffsetPct;
            this.severeOffsetPct = severeOffsetPct;
        }
        String getRangeString() { return min + "-" + max; }
    }

    private ThresholdConfig getThresholdConfig(String param, String furnaceId) {
        var resolved = thresholdResolverService.resolve(furnaceId, param);
        if (resolved == null) {
            return null;
        }
        return new ThresholdConfig(
                resolved.min(),
                resolved.max(),
                resolved.tipOffsetPct(),
                resolved.warningOffsetPct(),
                resolved.severeOffsetPct()
        );
    }

    private double calculateMean(List<Double> data) {
        return data.stream().mapToDouble(val -> val).average().orElse(0.0);
    }

    private double calculateStdDev(List<Double> data, double mean) {
        double temp = 0;
        for (double a : data) {
            temp += (a - mean) * (a - mean);
        }
        return Math.sqrt(temp / data.size());
    }

    public Page<AnomalyRecord> getAnomalies(String furnaceId, List<Integer> statuses, String level, String parameterName, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        Specification<AnomalyRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (furnaceId != null && !furnaceId.isEmpty()) predicates.add(cb.equal(root.get("furnaceId"), furnaceId));
            if (statuses != null && !statuses.isEmpty()) predicates.add(root.get("status").in(statuses));
            if (level != null && !level.isEmpty()) predicates.add(cb.equal(root.get("level"), level));
            if (parameterName != null && !parameterName.isEmpty()) predicates.add(cb.equal(root.get("parameterName"), parameterName));
            if (startTime != null) predicates.add(cb.greaterThanOrEqualTo(root.get("detectionTime"), startTime));
            if (endTime != null) predicates.add(cb.lessThanOrEqualTo(root.get("detectionTime"), endTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return anomalyRecordRepository.findAll(spec, pageable);
    }

    public Map<String, Long> getStatistics(String furnaceId) {
        LocalDateTime startOfDay = LocalDateTime.now(resolveSystemZoneId()).withHour(0).withMinute(0).withSecond(0);
        long currentPending = anomalyRecordRepository.count((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (furnaceId != null && !furnaceId.isEmpty()) predicates.add(cb.equal(root.get("furnaceId"), furnaceId));
            predicates.add(root.get("status").in(Arrays.asList(
                    AnomalyStatus.PENDING.getCode(),
                    AnomalyStatus.PROCESSING.getCode()
            )));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        long todayCount = anomalyRecordRepository.count((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (furnaceId != null && !furnaceId.isEmpty()) predicates.add(cb.equal(root.get("furnaceId"), furnaceId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("detectionTime"), startOfDay));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        long processedToday = anomalyRecordRepository.count((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (furnaceId != null && !furnaceId.isEmpty()) predicates.add(cb.equal(root.get("furnaceId"), furnaceId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("detectionTime"), startOfDay));
            predicates.add(root.get("status").in(Arrays.asList(
                    AnomalyStatus.RESOLVED.getCode(),
                    AnomalyStatus.CLOSED.getCode()
            )));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        long processingRate = todayCount > 0 ? (processedToday * 100 / todayCount) : 100;
        return Map.of("currentAnomalyCount", currentPending, "todayAnomalyCount", todayCount, "processingRate", processingRate);
    }

    @Transactional
    public AnomalyRecord handleAnomaly(Long id, Long handlerUser, String handlerContent, Integer status) {
        AnomalyRecord record = anomalyRecordRepository.findById(id).orElseThrow(() -> new RuntimeException("Anomaly record not found"));
        AnomalyStatus current = AnomalyStatus.fromRaw(record.getStatus());
        AnomalyStatus target = status == null ? AnomalyStatus.CLOSED : AnomalyStatus.fromRaw(status);
        if (target == null) {
            throw new RuntimeException("无效状态");
        }
        if (handlerUser != null) {
            String name = userRepository.findById(handlerUser)
                    .map(u -> u.getUsername() == null ? null : u.getUsername().trim())
                    .filter(s -> !s.isBlank())
                    .orElse(null);
            if (name != null) {
                record.setHandler(name);
            }
        }
        if (current == target) {
            if (handlerUser != null) record.setHandlerUser(handlerUser);
            if (handlerContent != null && !handlerContent.isBlank()) record.setHandlerContent(handlerContent);
            if (target == AnomalyStatus.RESOLVED || target == AnomalyStatus.CLOSED) record.setHandleTime(LocalDateTime.now());
            return anomalyRecordRepository.save(record);
        }
        if (!canTransit(current, target)) {
            throw new RuntimeException("状态流转不允许: " + current + " -> " + target);
        }
        record.setStatus(target.getCode());
        record.setHandlerUser(handlerUser);
        record.setHandlerContent(handlerContent);
        if (target == AnomalyStatus.RESOLVED || target == AnomalyStatus.CLOSED) {
            record.setHandleTime(LocalDateTime.now());
        }
        return anomalyRecordRepository.save(record);
    }

    private boolean canTransit(AnomalyStatus from, AnomalyStatus to) {
        if (from == null) return to == AnomalyStatus.PENDING || to == AnomalyStatus.PROCESSING || to == AnomalyStatus.RESOLVED || to == AnomalyStatus.CLOSED;
        if (from == to) return true;
        return switch (from) {
            case PENDING -> to == AnomalyStatus.PROCESSING || to == AnomalyStatus.RESOLVED || to == AnomalyStatus.CLOSED;
            case PROCESSING -> to == AnomalyStatus.RESOLVED || to == AnomalyStatus.CLOSED;
            case RESOLVED -> to == AnomalyStatus.CLOSED;
            case CLOSED -> false;
        };
    }

    public Map<String, Object> getChartData() {
        List<Object[]> distribution = anomalyRecordRepository.countByParameterName();
        List<Map<String, Object>> pieData = new ArrayList<>();
        for (Object[] row : distribution) {
            pieData.add(Map.of("name", row[0], "value", row[1]));
        }
        LocalDateTime sevenDaysAgo = LocalDateTime.now(resolveSystemZoneId()).minusDays(6).withHour(0).withMinute(0).withSecond(0);
        List<Object[]> trend = anomalyRecordRepository.countByDate(sevenDaysAgo);
        List<String> dates = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        Map<String, Long> trendMap = new HashMap<>();
        for (Object[] row : trend) {
            trendMap.put(row[0].toString(), (Long) row[1]);
        }
        for (int i = 0; i < 7; i++) {
            LocalDateTime d = sevenDaysAgo.plusDays(i);
            String dateStr = d.toLocalDate().toString();
            dates.add(dateStr);
            counts.add(trendMap.getOrDefault(dateStr, 0L));
        }
        return Map.of(
                "distribution", pieData,
                "trend", Map.of("dates", dates, "counts", counts)
        );
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(resolveSystemZoneId()).toInstant());
    }

    private ZoneId resolveSystemZoneId() {
        String zone = sysConfigService.getString("system_timezone", "Asia/Shanghai");
        try {
            return ZoneId.of(zone);
        } catch (Exception ignored) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "realtime";
        }
        String m = mode.trim().toLowerCase();
        if ("batch".equals(m) || "history".equals(m)) return "batch";
        if ("scheduled".equals(m)) return "scheduled";
        return "realtime";
    }

    @PreDestroy
    public void shutdownScheduler() {
        stopScheduledDetection();
        scheduler.shutdownNow();
    }

    private record ScheduledConfig(String furnaceId,
                                   List<String> params,
                                   String algorithm,
                                   int intervalSeconds,
                                   int batchSize) {
    }
}
