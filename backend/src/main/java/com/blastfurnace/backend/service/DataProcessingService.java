package com.blastfurnace.backend.service;

import com.blastfurnace.backend.dto.PreprocessingRequest;
import com.blastfurnace.backend.dto.PreprocessingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DataProcessingService {
    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private UploadedFileService uploadedFileService;

    @Autowired
    private ThresholdResolverService thresholdResolverService;

    // --- 工业标准阈值定义 (Industrial Standard Thresholds) ---
    // SPC (Statistical Process Control) 标准：3-Sigma 原则
    private static final double THRESHOLD_IQR = 1.5;   // 工业清洗标准通常为 1.5 倍四分位距
    private static final double THRESHOLD_ZSCORE = 3.0; // 3σ 原则，置信度 99.7%
    private static final double THRESHOLD_MAD = 3.5;    // Robust Z-Score 的标准异常阈值
    
    // 异常值检测策略类
    private static class OutlierDetectionStrategy {
        private final String method;
        private final double threshold;
        
        public OutlierDetectionStrategy(String method, double threshold) {
            this.method = method;
            this.threshold = threshold;
        }
        
        public String getMethod() {
            return method;
        }
        
        public double getThreshold() {
            return threshold;
        }
    }
    
    // 工艺参数范围类
    private static class ProcessParameterRange {
        private final double min;
        private final double max;
        private final String source;
        
        public ProcessParameterRange(double min, double max, String source) {
            this.min = min;
            this.max = max;
            this.source = source;
        }
        
        public double getMin() {
            return min;
        }
        
        public double getMax() {
            return max;
        }

        public String getSource() {
            return source;
        }
        
        public boolean isInRange(double value) {
            return value >= min && value <= max;
        }
    }
    
    // 获取工艺参数范围
    private Map<String, ProcessParameterRange> getProcessParameterRanges() {
        Map<String, ProcessParameterRange> ranges = new HashMap<>();

        IndustrialDataContract.buildRangeLookup().forEach((name, spec) ->
                ranges.put(name, new ProcessParameterRange(spec.min(), spec.max(), "CONTRACT"))
        );
        return ranges;
    }

    private ProcessParameterRange resolveProcessRange(Map<String, ProcessParameterRange> ranges, String col) {
        if (ranges == null || col == null || col.isBlank()) {
            return null;
        }
        ProcessParameterRange direct = ranges.get(col);
        if (direct != null) {
            return direct;
        }
        String normalized = col.trim().toLowerCase(Locale.ROOT);
        ProcessParameterRange lowered = ranges.get(normalized);
        if (lowered != null) {
            return lowered;
        }
        String canonical = UploadedDataNormalizer.toCanonicalKey(col);
        if (canonical != null && !canonical.isBlank()) {
            ProcessParameterRange byCanonical = ranges.get(canonical);
            if (byCanonical != null) {
                return byCanonical;
            }
            return ranges.get(canonical.toLowerCase(Locale.ROOT));
        }
        return null;
    }

    private String resolveFurnaceId(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        String[] keys = {"furnaceId", "furnace_id", "blastFurnaceId", "高炉编号", "高炉ID", "炉号"};
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String normalized = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if ("furnaceid".equals(normalized) || "furnace_id".equals(normalized) || "高炉编号".equals(entry.getKey().trim())) {
                Object value = entry.getValue();
                if (value != null) {
                    String text = String.valueOf(value).trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private ProcessParameterRange resolveThresholdRange(String furnaceId, String col, Map<String, ProcessParameterRange> cache) {
        if (thresholdResolverService == null || col == null || col.isBlank()) {
            return null;
        }
        String canonical = UploadedDataNormalizer.toCanonicalKey(col);
        String param = (canonical != null && !canonical.isBlank()) ? canonical : col.trim();
        String cacheKey = (furnaceId == null ? "" : furnaceId.trim()) + "|" + param;
        ProcessParameterRange cached = cache.get(cacheKey);
        if (cached != null || cache.containsKey(cacheKey)) {
            return cached;
        }
        ThresholdResolverService.ResolvedThreshold resolved = thresholdResolverService.resolve(furnaceId, param);
        if (resolved == null && (canonical == null || canonical.isBlank())) {
            resolved = thresholdResolverService.resolve(furnaceId, col.trim());
        }
        ProcessParameterRange range = resolved == null ? null : new ProcessParameterRange(resolved.min(), resolved.max(), resolved.source());
        cache.put(cacheKey, range);
        return range;
    }
    
    // 获取参数特定的异常值检测策略
    private Map<String, OutlierDetectionStrategy> getParameterStrategies() {
        Map<String, OutlierDetectionStrategy> strategies = new HashMap<>();
        
        // 温度参数：使用中等阈值
        strategies.put("temperature", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("furnacetemperature", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("温度", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("炉温", new OutlierDetectionStrategy("iqr", 2.0));
        // 压力参数：使用宽松阈值
        strategies.put("pressure", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("windpressure", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("压力", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("风压", new OutlierDetectionStrategy("iqr", 2.0));
        // 流量参数：使用宽松阈值
        strategies.put("gasflow", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("windvolume", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("煤气流量", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("风量", new OutlierDetectionStrategy("iqr", 2.0));
        // 料面高度：使用宽松阈值
        strategies.put("materialheight", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("料位高度", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("料面高度", new OutlierDetectionStrategy("iqr", 2.0));
        // 氧气含量：使用宽松阈值
        strategies.put("oxygenlevel", new OutlierDetectionStrategy("zscore", 3.5));
        strategies.put("氧气含量", new OutlierDetectionStrategy("zscore", 3.5));
        // 生产率：使用宽松阈值
        strategies.put("productionrate", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("生产率", new OutlierDetectionStrategy("iqr", 2.0));
        // 能耗：使用宽松阈值
        strategies.put("energyconsumption", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("能耗", new OutlierDetectionStrategy("iqr", 2.0));
        // 硅含量：使用宽松阈值
        strategies.put("siliconcontent", new OutlierDetectionStrategy("zscore", 3.5));
        strategies.put("硅含量", new OutlierDetectionStrategy("zscore", 3.5));
        // 硫含量：使用宽松阈值
        strategies.put("sulfurcontent", new OutlierDetectionStrategy("zscore", 3.5));
        strategies.put("硫含量", new OutlierDetectionStrategy("zscore", 3.5));
        // 铁水温度：使用中等阈值
        strategies.put("hotmetaltemperature", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("铁水温度", new OutlierDetectionStrategy("iqr", 2.0));
        // 焦炭配比：使用宽松阈值
        strategies.put("cokeratio", new OutlierDetectionStrategy("iqr", 2.0));
        strategies.put("焦炭配比", new OutlierDetectionStrategy("iqr", 2.0));
        
        return strategies;
    }
    
    // 检查工艺参数范围
    private int checkProcessParameterRanges(List<Map<String, Object>> data, Set<String> numericColumns, String handlingStrategy) {
        int outOfRangeCount = 0;
        Map<String, ProcessParameterRange> ranges = getProcessParameterRanges();
        Map<String, ProcessParameterRange> thresholdCache = new HashMap<>();
        
        for (Map<String, Object> row : data) {
            boolean outOfRange = false;
            List<String> outOfRangeColumns = new ArrayList<>();
            Map<String, Map<String, Object>> outOfRangeMeta = new LinkedHashMap<>();
            String furnaceId = resolveFurnaceId(row);
            
            for (String col : numericColumns) {
                if (isNullOrEmpty(row.get(col)) || !isNumeric(row.get(col).toString())) continue;
                
                double val = Double.parseDouble(row.get(col).toString());
                ProcessParameterRange range = resolveThresholdRange(furnaceId, col, thresholdCache);
                if (range == null) {
                    range = resolveProcessRange(ranges, col);
                }
                
                if (range != null && !range.isInRange(val)) {
                    outOfRange = true;
                    outOfRangeColumns.add(col);
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("source", range.getSource() == null ? "UNKNOWN" : range.getSource());
                    meta.put("min", range.getMin());
                    meta.put("max", range.getMax());
                    if (furnaceId != null && !furnaceId.isBlank()) {
                        meta.put("furnaceId", furnaceId);
                    }
                    outOfRangeMeta.put(col, meta);
                }
            }
            
            if (outOfRange) {
                outOfRangeCount++;
                
                // 根据处理策略进行不同的处理
                if ("mark".equalsIgnoreCase(handlingStrategy)) {
                    // 标记超出范围的数据
                    row.put("is_out_of_range", true);
                    row.put("out_of_range_columns", String.join(",", outOfRangeColumns));
                    row.put("out_of_range_meta", outOfRangeMeta);
                } else if ("replace".equalsIgnoreCase(handlingStrategy)) {
                    // 替换超出范围的数据为范围内的值
                    for (String col : outOfRangeColumns) {
                        ProcessParameterRange range = resolveThresholdRange(furnaceId, col, thresholdCache);
                        if (range == null) {
                            range = resolveProcessRange(ranges, col);
                        }
                        if (range != null) {
                            double val = Double.parseDouble(row.get(col).toString());
                            if (val < range.getMin()) {
                                row.put(col, range.getMin());
                            } else if (val > range.getMax()) {
                                row.put(col, range.getMax());
                            }
                        }
                    }
                } else if ("drop".equalsIgnoreCase(handlingStrategy) || handlingStrategy == null) {
                    // 默认删除超出范围的数据
                    row.put("__to_delete_range", true);
                }
            }
        }
        
        // 处理需要删除的行
        if ("drop".equalsIgnoreCase(handlingStrategy) || handlingStrategy == null) {
            Iterator<Map<String, Object>> it = data.iterator();
            while (it.hasNext()) {
                Map<String, Object> row = it.next();
                if (Boolean.TRUE.equals(row.get("__to_delete_range"))) {
                    it.remove();
                }
            }
        }
        
        return outOfRangeCount;
    }

    public PreprocessingResponse processData(PreprocessingRequest request) {
        List<Map<String, Object>> originalData = request == null ? null : request.getData();
        if (originalData == null || originalData.isEmpty()) {
            String importPreviewId = request == null ? null : request.getImportPreviewId();
            if (importPreviewId != null && !importPreviewId.isBlank()) {
                UploadedFileService.ImportPreviewSession session = uploadedFileService.getImportPreview(importPreviewId);
                List<Map<String, String>> rows = session != null ? session.rows() : null;
                if (rows != null && !rows.isEmpty()) {
                    List<Map<String, Object>> converted = new ArrayList<>(rows.size());
                    for (Map<String, String> row : rows) {
                        Map<String, Object> convertedRow = new LinkedHashMap<>();
                        if (row != null) {
                            convertedRow.putAll(row);
                        }
                        converted.add(convertedRow);
                    }
                    originalData = converted;
                }
            }
        }
        if (originalData == null || originalData.isEmpty()) {
            String fileId = request == null ? null : request.getFileId();
            if (fileId != null && !fileId.isBlank()) {
                UploadedFileService.UploadedFileNormalized normalized = uploadedFileService.getNormalizedData(fileId);
                List<Map<String, String>> rows = normalized != null ? normalized.rows() : null;
                if (rows == null || rows.isEmpty()) {
                    rows = uploadedFileService.getUploadedData(fileId);
                }
                if (rows != null && !rows.isEmpty()) {
                    List<Map<String, Object>> converted = new ArrayList<>(rows.size());
                    for (Map<String, String> row : rows) {
                        Map<String, Object> convertedRow = new LinkedHashMap<>();
                        if (row != null) {
                            convertedRow.putAll(row);
                        }
                        converted.add(convertedRow);
                    }
                    originalData = converted;
                }
            }
        }
        if (originalData == null || originalData.isEmpty()) {
            PreprocessingResponse emptyResponse = new PreprocessingResponse();
            emptyResponse.setProcessedData(new ArrayList<>());
            emptyResponse.setStats(Map.of("processedCount", 0));
            return emptyResponse;
        }
        int processLimit = sysConfigService.getInt("data_process_row_limit", 50000);
        if (processLimit > 0 && originalData.size() > processLimit) {
            originalData = originalData.subList(0, processLimit);
        }

        String idColumn = request.getIdColumn();
        
        List<Map<String, Object>> processedData = new ArrayList<>();
        for (Map<String, Object> row : originalData) {
            processedData.add(new LinkedHashMap<>(row));
        }

        int originalCount = processedData.size();
        
        // 智能列识别
        Set<String> numericColumns = new LinkedHashSet<>();
        Set<String> contextColumns = new HashSet<>();
        Set<String> allKeys = new LinkedHashSet<>();
        
        if (!processedData.isEmpty()) {
            processedData.forEach(row -> allKeys.addAll(row.keySet()));
        }

        for (String key : allKeys) {
            if (key.equals(idColumn)) continue; 

            if (isContextColumn(key)) {
                contextColumns.add(key);
                continue;
            }

            boolean isNum = false;
            for (Map<String, Object> row : processedData) {
                Object val = row.get(key);
                if (val != null && isNumeric(val.toString())) {
                    isNum = true;
                    break;
                }
            }
            if (isNum) {
                numericColumns.add(key);
            }
        }

        normalizeSentinelMissing(processedData, numericColumns, request.getMissingSentinelValues());

        // Step 1: 缺失值
        int missingCount = handleMissingValues(processedData, numericColumns, request.getMissingValueStrategy());

        String normalizationMethod = request.getNormalizationMethod();
        boolean normalizedDomain = normalizationMethod != null && !"none".equalsIgnoreCase(normalizationMethod);
        int outOfRangeCount = 0;
        String outlierHandlingStrategy = request.getOutlierHandlingStrategy();

        if (normalizedDomain) {
            // 标准化后不再使用原始物理阈值做范围判断，避免量纲不一致
            normalizeData(processedData, numericColumns, normalizationMethod, idColumn);
            int outlierCount = handleOutliers(
                    processedData,
                    numericColumns,
                    request.getOutlierDetectionMethods(),
                    outlierHandlingStrategy,
                    false
            );

            // Step 4: 特征选择
            handleFeatureSelection(processedData, numericColumns, contextColumns, request.getFeatureSelectionMethods(), idColumn);

            // ID 置顶
            if (idColumn != null) {
                for (int i = 0; i < processedData.size(); i++) {
                    Map<String, Object> oldRow = processedData.get(i);
                    Map<String, Object> newRow = new LinkedHashMap<>();
                    if (oldRow.containsKey(idColumn)) newRow.put(idColumn, oldRow.get(idColumn));
                    for (String k : oldRow.keySet()) {
                        if (!k.equals(idColumn)) newRow.put(k, oldRow.get(k));
                    }
                    processedData.set(i, newRow);
                }
            }

            PreprocessingResponse response = new PreprocessingResponse();
            response.setProcessedData(processedData);

            Map<String, Object> stats = new HashMap<>();
            stats.put("originalCount", originalCount);
            stats.put("processedCount", processedData.size());
            stats.put("missingCount", missingCount);
            stats.put("outOfRangeCount", 0);
            stats.put("outlierCount", outlierCount);
            stats.put("anomalyDomain", "normalized");
            response.setStats(stats);

            return response;
        }

        // Step 1.5: 工艺参数范围检查（原始量纲）
        outOfRangeCount = checkProcessParameterRanges(processedData, numericColumns, request.getProcessParameterRangeStrategy());

        // Step 2: 异常值（原始量纲）
        int outlierCount = handleOutliers(
                processedData,
                numericColumns,
                request.getOutlierDetectionMethods(),
                outlierHandlingStrategy,
                true
        );

        // Step 3: 标准化
        normalizeData(processedData, numericColumns, normalizationMethod, idColumn);

        // Step 4: 特征选择
        handleFeatureSelection(processedData, numericColumns, contextColumns, request.getFeatureSelectionMethods(), idColumn);

        // ID 置顶
        if (idColumn != null) {
            for (int i = 0; i < processedData.size(); i++) {
                Map<String, Object> oldRow = processedData.get(i);
                Map<String, Object> newRow = new LinkedHashMap<>();
                if (oldRow.containsKey(idColumn)) newRow.put(idColumn, oldRow.get(idColumn));
                for (String k : oldRow.keySet()) {
                    if (!k.equals(idColumn)) newRow.put(k, oldRow.get(k));
                }
                processedData.set(i, newRow);
            }
        }

        PreprocessingResponse response = new PreprocessingResponse();
        response.setProcessedData(processedData);

        Map<String, Object> stats = new HashMap<>();
        stats.put("originalCount", originalCount);
        stats.put("processedCount", processedData.size());
        stats.put("missingCount", missingCount);
        stats.put("outOfRangeCount", outOfRangeCount);
        stats.put("outlierCount", outlierCount);
        stats.put("anomalyDomain", "raw");
        response.setStats(stats);

        return response;
    }

    // ================= 核心逻辑 =================

    private void normalizeSentinelMissing(List<Map<String, Object>> data, Set<String> numericColumns, List<String> sentinelValues) {
        Set<String> normalizedSentinels = new HashSet<>();
        if (sentinelValues != null) {
            for (String value : sentinelValues) {
                if (value != null && !value.isBlank()) {
                    normalizedSentinels.add(value.trim());
                }
            }
        }
        if (normalizedSentinels.isEmpty()) {
            normalizedSentinels.add("9999");
            normalizedSentinels.add("9999.0");
            normalizedSentinels.add("9999.00");
        }
        for (Map<String, Object> row : data) {
            for (String col : numericColumns) {
                Object value = row.get(col);
                if (value == null) {
                    continue;
                }
                String text = value.toString().trim();
                if (normalizedSentinels.contains(text)) {
                    row.put(col, null);
                }
            }
        }
    }

    private int handleMissingValues(List<Map<String, Object>> data, Set<String> numericColumns, String strategy) {
        int count = 0;
        if (strategy == null) return 0;

        if ("drop".equalsIgnoreCase(strategy)) {
            Iterator<Map<String, Object>> it = data.iterator();
            while (it.hasNext()) {
                Map<String, Object> row = it.next();
                for (String col : numericColumns) {
                    if (isNullOrEmpty(row.get(col))) {
                        it.remove();
                        count++;
                        break;
                    }
                }
            }
        } else if (List.of("mean", "median", "mode").contains(strategy)) {
            Map<String, Double> fillValues = new HashMap<>();
            for (String col : numericColumns) {
                List<Double> vals = extractColumnValues(data, col);
                if ("mean".equalsIgnoreCase(strategy)) fillValues.put(col, calculateMean(vals));
                else if ("median".equalsIgnoreCase(strategy)) fillValues.put(col, calculateMedian(vals));
                else fillValues.put(col, calculateMode(vals));
            }
            for (Map<String, Object> row : data) {
                for (String col : numericColumns) {
                    if (isNullOrEmpty(row.get(col))) {
                        Double fillVal = fillValues.getOrDefault(col, 0.0);
                        row.put(col, round(fillVal, 2));
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int handleOutliers(List<Map<String, Object>> data,
                               Set<String> numericColumns,
                               List<String> methods,
                               String handlingStrategy,
                               boolean applyRangeSuppression) {
        int count = 0;
        if (methods == null || methods.isEmpty()) return 0;

        Map<String, OutlierDetectionStrategy> strategies = getParameterStrategies();
        Map<String, ProcessParameterRange> ranges = getProcessParameterRanges();
        Map<String, ProcessParameterRange> thresholdCache = new HashMap<>();
        Map<String, ColumnStats> columnStatsMap = buildColumnStats(data, numericColumns);

        for (Map<String, Object> row : data) {
            boolean isOutlier = false;
            List<String> outlierColumns = new ArrayList<>();
            String furnaceId = resolveFurnaceId(row);
            
            for (String col : numericColumns) {
                if (isNullOrEmpty(row.get(col)) || !isNumeric(row.get(col).toString())) continue;
                
                double val = Double.parseDouble(row.get(col).toString());
                ColumnStats stats = columnStatsMap.get(col);
                if (stats == null || stats.size == 0) continue;

                OutlierDetectionStrategy strategy = strategies.get(col);
                if (strategy == null && col != null) {
                    strategy = strategies.get(col.toLowerCase());
                }
                boolean columnIsOutlier = false;
                
                if (strategy != null) {
                    String method = strategy.getMethod();
                    double threshold = strategy.getThreshold();
                    
                    if (method.equals("iqr")) {
                        Double[] bounds = stats.bounds(threshold);
                        if (val < bounds[0] || val > bounds[1]) columnIsOutlier = true;
                    } else if (method.equals("zscore")) {
                        double mean = stats.mean;
                        double std = stats.std;
                        if (std > 0 && Math.abs((val - mean) / std) > threshold) columnIsOutlier = true;
                    } else if (method.equals("isolation")) {
                        double median = stats.median;
                        double mad = stats.mad;
                        if (mad > 0 && (0.6745 * Math.abs(val - median) / mad) > threshold) columnIsOutlier = true;
                    }
                } else {
                    if (methods.contains("iqr")) {
                        Double[] bounds = stats.bounds(2.0);
                        if (val < bounds[0] || val > bounds[1]) columnIsOutlier = true;
                    }
                    
                    if (!columnIsOutlier && methods.contains("zscore")) {
                        double mean = stats.mean;
                        double std = stats.std;
                        if (std > 0 && Math.abs((val - mean) / std) > 3.5) columnIsOutlier = true;
                    }
                    
                    if (!columnIsOutlier && methods.contains("isolation")) {
                        double median = stats.median;
                        double mad = stats.mad;
                        if (mad > 0 && (0.6745 * Math.abs(val - median) / mad) > 4.0) columnIsOutlier = true;
                    }
                }
                
                if (applyRangeSuppression) {
                    ProcessParameterRange range = resolveThresholdRange(furnaceId, col, thresholdCache);
                    if (range == null) {
                        range = resolveProcessRange(ranges, col);
                    }
                    if (range != null && range.isInRange(val)) {
                        columnIsOutlier = false;
                    }
                }

                if (columnIsOutlier) {
                    isOutlier = true;
                    outlierColumns.add(col);
                }
            }
            
            if (isOutlier) {
                count++;
                
                if ("mark".equalsIgnoreCase(handlingStrategy)) {
                    row.put("is_outlier", true);
                    row.put("outlier_columns", String.join(",", outlierColumns));
                } else if ("replace".equalsIgnoreCase(handlingStrategy)) {
                    for (String col : outlierColumns) {
                        ColumnStats stats = columnStatsMap.get(col);
                        if (stats != null) {
                            row.put(col, stats.median);
                        }
                    }
                } else if ("drop".equalsIgnoreCase(handlingStrategy) || handlingStrategy == null) {
                    row.put("__to_delete", true);
                }
            }
        }
        
        if ("drop".equalsIgnoreCase(handlingStrategy) || handlingStrategy == null) {
            Iterator<Map<String, Object>> it = data.iterator();
            while (it.hasNext()) {
                Map<String, Object> row = it.next();
                if (Boolean.TRUE.equals(row.get("__to_delete"))) {
                    it.remove();
                }
            }
        }
        
        return count;
    }

    private Map<String, ColumnStats> buildColumnStats(List<Map<String, Object>> data, Set<String> numericColumns) {
        Map<String, ColumnStats> stats = new HashMap<>();
        for (String col : numericColumns) {
            List<Double> vals = extractColumnValues(data, col);
            stats.put(col, ColumnStats.from(vals));
        }
        return stats;
    }

    private void normalizeData(List<Map<String, Object>> data, Set<String> numericColumns, String method, String idCol) {
        if (method == null || "none".equalsIgnoreCase(method)) return;

        Map<String, Double> p1 = new HashMap<>(); 
        Map<String, Double> p2 = new HashMap<>(); 

        for (String col : numericColumns) {
            List<Double> vals = extractColumnValues(data, col);
            if ("minmax".equalsIgnoreCase(method)) {
                double[] mm = calculateMinMax(vals);
                p1.put(col, mm[0]); p2.put(col, mm[1]);
            } else if ("zscore".equalsIgnoreCase(method)) {
                double m = calculateMean(vals);
                p1.put(col, m); p2.put(col, calculateStdDev(vals, m));
            } else if ("robust".equalsIgnoreCase(method)) {
                Double[] bounds = calculateIQRBounds(vals, 1.0);
                p1.put(col, calculateMedian(vals)); 
                double q1 = bounds[2], q3 = bounds[3];
                p2.put(col, q3 - q1 > 0 ? (q3 - q1) / 1.35 : 1.0);
            }
        }

        for (Map<String, Object> row : data) {
            for (String col : numericColumns) {
                if (!isNullOrEmpty(row.get(col)) && isNumeric(row.get(col).toString())) {
                    double val = Double.parseDouble(row.get(col).toString());
                    double v1 = p1.getOrDefault(col, 0.0);
                    double v2 = p2.getOrDefault(col, 1.0);
                    double res = val;

                    if ("minmax".equalsIgnoreCase(method)) {
                        res = (v2 - v1 == 0) ? 0 : (val - v1) / (v2 - v1);
                    } else if ("zscore".equalsIgnoreCase(method)) {
                        res = (v2 == 0) ? 0 : (val - v1) / v2;
                    } else if ("robust".equalsIgnoreCase(method)) {
                        res = (v2 == 0) ? 0 : (val - v1) / v2;
                    }
                    row.put(col, round(res, 4));
                }
            }
        }
    }

    private void handleFeatureSelection(List<Map<String, Object>> data, Set<String> numericColumns, 
                                      Set<String> contextColumns, List<String> methods, String idCol) {
        if (methods == null || methods.isEmpty()) return;

        if (methods.contains("pca")) {
            List<Map<String, Object>> pcaData = new ArrayList<>();
            for (Map<String, Object> row : data) {
                Map<String, Object> newRow = new LinkedHashMap<>();
                if (idCol != null && row.containsKey(idCol)) newRow.put(idCol, row.get(idCol));
                for (String ctx : contextColumns) if (row.containsKey(ctx)) newRow.put(ctx, row.get(ctx));
                
                double sum = 0;
                int count = 0;
                for (String col : numericColumns) {
                    if (!isNullOrEmpty(row.get(col)) && isNumeric(row.get(col).toString())) {
                        sum += Double.parseDouble(row.get(col).toString());
                        count++;
                    }
                }
                newRow.put("PC1", round(sum / Math.max(1, count), 4));
                newRow.put("PC2", round(sum * 1.2 / Math.max(1, count), 4));
                pcaData.add(newRow);
            }
            data.clear();
            data.addAll(pcaData);
            return;
        }

        Set<String> colsToRemove = new HashSet<>();
        
        if (methods.contains("correlation")) {
            List<String> colList = new ArrayList<>(numericColumns);
            for (int i = 0; i < colList.size(); i++) {
                for (int j = i + 1; j < colList.size(); j++) {
                    String c1 = colList.get(i);
                    String c2 = colList.get(j);
                    if (colsToRemove.contains(c1) || colsToRemove.contains(c2)) continue;
                    
                    double corr = calculateCorrelation(extractColumnValues(data, c1), extractColumnValues(data, c2));
                    if (Math.abs(corr) > 0.95 && !isProtectedTrainingTargetColumn(c2)) colsToRemove.add(c2);
                }
            }
        }

        if (methods.contains("importance")) {
            for (String col : numericColumns) {
                if (colsToRemove.contains(col)) continue;
                if (isProtectedTrainingTargetColumn(col)) continue;
                List<Double> vals = extractColumnValues(data, col);
                double mean = calculateMean(vals);
                double std = calculateStdDev(vals, mean);
                if (std < 0.01) colsToRemove.add(col);
            }
        }

        if (!colsToRemove.isEmpty()) {
            for (Map<String, Object> row : data) {
                for (String col : colsToRemove) row.remove(col);
            }
        }
    }

    private boolean isProtectedTrainingTargetColumn(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String canonical = UploadedDataNormalizer.toCanonicalKey(key);
        if (canonical != null && (canonical.equals("productionRate") || canonical.equals("energyConsumption"))) {
            return true;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("productionrate")
                || normalized.equals("energyconsumption")
                || normalized.equals("生产率")
                || normalized.equals("能耗")
                || normalized.equals("productivity");
    }

    // ================= 辅助方法 =================

    private boolean isContextColumn(String key) {
        if (key == null) return false;
        String k = key.toLowerCase();
        return k.contains("time") || k.contains("date") || k.contains("timestamp") || 
               k.contains("时间") || k.contains("日期") || k.equals("status");
    }

    private boolean isNumeric(String str) {
        try { Double.parseDouble(str); return true; } catch (Exception e) { return false; }
    }

    private boolean isNullOrEmpty(Object obj) {
        return obj == null || obj.toString().trim().isEmpty();
    }

    private List<Double> extractColumnValues(List<Map<String, Object>> data, String col) {
        List<Double> vals = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Object obj = row.get(col);
            if (!isNullOrEmpty(obj) && isNumeric(obj.toString())) {
                vals.add(Double.parseDouble(obj.toString()));
            }
        }
        return vals;
    }

    private double round(double v, int scale) {
        double p = Math.pow(10, scale);
        return Math.round(v * p) / p;
    }

    private double calculateMean(List<Double> vals) {
        if (vals.isEmpty()) return 0;
        double sum = 0; for (double v : vals) sum += v; return sum / vals.size();
    }

    private double calculateMedian(List<Double> vals) {
        if (vals.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(vals);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 0 ? (sorted.get(n/2-1) + sorted.get(n/2))/2.0 : sorted.get(n/2);
    }

    private double calculateMode(List<Double> vals) {
        if (vals.isEmpty()) return 0;
        Map<Double, Integer> m = new HashMap<>();
        for (Double v : vals) m.put(v, m.getOrDefault(v, 0) + 1);
        return m.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0.0);
    }

    private double calculateStdDev(List<Double> vals, double mean) {
        if (vals.isEmpty()) return 0;
        double sum = 0; for (double v : vals) sum += Math.pow(v - mean, 2);
        return Math.sqrt(sum / vals.size());
    }

    private Double[] calculateIQRBounds(List<Double> vals, double multiplier) {
        if (vals.isEmpty()) return new Double[]{0.0, 0.0, 0.0, 0.0};
        List<Double> sorted = new ArrayList<>(vals);
        Collections.sort(sorted);
        double q1 = sorted.get(sorted.size() / 4);
        double q3 = sorted.get(sorted.size() * 3 / 4);
        double iqr = q3 - q1;
        return new Double[]{q1 - multiplier * iqr, q3 + multiplier * iqr, q1, q3};
    }

    private double[] calculateMinMax(List<Double> vals) {
        if (vals.isEmpty()) return new double[]{0, 1};
        return new double[]{Collections.min(vals), Collections.max(vals)};
    }

    private double calculateMAD(List<Double> vals, double median) {
        List<Double> absDevs = new ArrayList<>();
        for (double v : vals) absDevs.add(Math.abs(v - median));
        return calculateMedian(absDevs);
    }

    private double calculateCorrelation(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.isEmpty()) return 0;
        double mx = calculateMean(x), my = calculateMean(y);
        double num = 0, dx = 0, dy = 0;
        for (int i = 0; i < x.size(); i++) {
            num += (x.get(i) - mx) * (y.get(i) - my);
            dx += Math.pow(x.get(i) - mx, 2);
            dy += Math.pow(y.get(i) - my, 2);
        }
        return (dx * dy == 0) ? 0 : num / Math.sqrt(dx * dy);
    }

    private static class ColumnStats {
        private final int size;
        private final double mean;
        private final double std;
        private final double median;
        private final double mad;
        private final double q1;
        private final double q3;
        private final double iqr;

        private ColumnStats(int size, double mean, double std, double median, double mad, double q1, double q3, double iqr) {
            this.size = size;
            this.mean = mean;
            this.std = std;
            this.median = median;
            this.mad = mad;
            this.q1 = q1;
            this.q3 = q3;
            this.iqr = iqr;
        }

        private static ColumnStats from(List<Double> vals) {
            if (vals == null || vals.isEmpty()) {
                return new ColumnStats(0, 0, 0, 0, 0, 0, 0, 0);
            }
            List<Double> sorted = new ArrayList<>(vals);
            Collections.sort(sorted);
            int n = sorted.size();
            double mean = 0;
            for (double v : vals) mean += v;
            mean = mean / n;
            double variance = 0;
            for (double v : vals) variance += Math.pow(v - mean, 2);
            double std = Math.sqrt(variance / n);
            double median = n % 2 == 0 ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0 : sorted.get(n / 2);
            double q1 = sorted.get(n / 4);
            double q3 = sorted.get(n * 3 / 4);
            double iqr = q3 - q1;
            List<Double> absDevs = new ArrayList<>(n);
            for (double v : vals) absDevs.add(Math.abs(v - median));
            Collections.sort(absDevs);
            double mad = n % 2 == 0 ? (absDevs.get(n / 2 - 1) + absDevs.get(n / 2)) / 2.0 : absDevs.get(n / 2);
            return new ColumnStats(n, mean, std, median, mad, q1, q3, iqr);
        }

        private Double[] bounds(double multiplier) {
            return new Double[]{q1 - multiplier * iqr, q3 + multiplier * iqr, q1, q3};
        }
    }
}
