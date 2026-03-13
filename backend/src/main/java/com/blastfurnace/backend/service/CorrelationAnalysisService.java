package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CorrelationAnalysisService {

    private final ProductionDataRepository productionDataRepository;

    /**
     * 计算相关性矩阵
     */
    public Map<String, Object> calculateCorrelationAnalysis(
            String furnaceId,
            Date startTime,
            Date endTime,
            List<String> parameters,
            String method,
            Integer maxLag,
            Integer minOverlap) {
        List<ProductionData> dataList = productionDataRepository.findByFurnaceIdAndTimestampBetween(
                furnaceId, startTime, endTime);
        String algo = normalizeMethod(method);
        Map<String, Map<String, Double>> matrix = calculateCorrelationMatrix(dataList, parameters, algo);
        int lagWindow = normalizeLagWindow(maxLag);
        int overlap = normalizeMinOverlap(minOverlap);
        List<Map<String, Object>> lagPairs = calculateLagPairs(dataList, parameters, algo, lagWindow, overlap);
        Map<String, Object> meta = new HashMap<>();
        meta.put("method", algo);
        meta.put("sampleSize", dataList.size());
        meta.put("maxLag", lagWindow);
        meta.put("minOverlap", overlap);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        meta.put("timeRange", Map.of(
                "start", LocalDateTime.ofInstant(startTime.toInstant(), ZoneId.systemDefault()).format(formatter),
                "end", LocalDateTime.ofInstant(endTime.toInstant(), ZoneId.systemDefault()).format(formatter)
        ));
        meta.put("note", "相关不等于因果，建议结合工艺机理与业务经验解读。");
        Map<String, Object> response = new HashMap<>();
        response.put("matrix", matrix);
        response.put("meta", meta);
        response.put("lagPairs", lagPairs);
        return response;
    }

    public Map<String, Map<String, Double>> calculateCorrelationMatrix(
            String furnaceId,
            Date startTime,
            Date endTime,
            List<String> parameters) {
        List<ProductionData> dataList = productionDataRepository.findByFurnaceIdAndTimestampBetween(
                furnaceId, startTime, endTime);
        return calculateCorrelationMatrix(dataList, parameters, "pearson");
    }

    private Map<String, Map<String, Double>> calculateCorrelationMatrix(
            List<ProductionData> dataList,
            List<String> parameters,
            String method) {
        return parameters.stream().collect(Collectors.toMap(
                param1 -> param1,
                param1 -> parameters.stream().collect(Collectors.toMap(
                        param2 -> param2,
                        param2 -> calculateCorrelation(dataList, param1, param2, method)
                ))
        ));
    }

    private double calculateCorrelation(
            List<ProductionData> dataList,
            String param1Name,
            String param2Name,
            String method) {
        List<Double> param1Values = extractParameterValues(dataList, param1Name);
        List<Double> param2Values = extractParameterValues(dataList, param2Name);
        return calculateCorrelation(param1Values, param2Values, method);
    }

    private double calculateCorrelation(
            List<Double> param1Values,
            List<Double> param2Values,
            String method) {
        List<Double> validX = new ArrayList<>();
        List<Double> validY = new ArrayList<>();
        int size = Math.min(param1Values.size(), param2Values.size());
        for (int i = 0; i < size; i++) {
            Double xVal = param1Values.get(i);
            Double yVal = param2Values.get(i);
            if (xVal != null
                    && yVal != null
                    && !xVal.isNaN()
                    && !yVal.isNaN()
                    && xVal != 9999.0
                    && yVal != 9999.0) {
                validX.add(xVal);
                validY.add(yVal);
            }
        }
        if (validX.size() < 2) {
            return 0.0;
        }
        if ("spearman".equals(method)) {
            double[] rankX = rank(validX);
            double[] rankY = rank(validY);
            return calculatePearsonCorrelation(rankX, rankY, rankX.length);
        }
        double[] x = toArray(validX);
        double[] y = toArray(validY);
        return calculatePearsonCorrelation(x, y, x.length);
    }

    private double calculatePearsonCorrelation(double[] x, double[] y, int validCount) {
        // 计算均值
        double meanX = calculateMean(x, validCount);
        double meanY = calculateMean(y, validCount);

        // 计算协方差和标准差
        double covariance = 0.0;
        double varianceX = 0.0;
        double varianceY = 0.0;

        for (int i = 0; i < validCount; i++) {
            double diffX = x[i] - meanX;
            double diffY = y[i] - meanY;
            covariance += diffX * diffY;
            varianceX += diffX * diffX;
            varianceY += diffY * diffY;
        }

        // 计算相关系数
        double stdDevX = Math.sqrt(varianceX);
        double stdDevY = Math.sqrt(varianceY);

        if (stdDevX == 0 || stdDevY == 0) {
            return 0.0;
        }

        double r = covariance / (stdDevX * stdDevY);
        if (Double.isNaN(r) || Double.isInfinite(r)) {
            return 0.0;
        }
        if (r > 1) {
            return 1.0;
        }
        if (r < -1) {
            return -1.0;
        }
        return r;
    }

    private List<Map<String, Object>> calculateLagPairs(
            List<ProductionData> dataList,
            List<String> parameters,
            String method,
            int maxLag,
            int minOverlap) {
        if (maxLag <= 0 || parameters == null || parameters.size() < 2) {
            return Collections.emptyList();
        }
        Map<String, List<Double>> cache = new HashMap<>();
        for (String parameter : parameters) {
            cache.put(parameter, extractParameterValues(dataList, parameter));
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            for (int j = i + 1; j < parameters.size(); j++) {
                String param1 = parameters.get(i);
                String param2 = parameters.get(j);
                List<Double> xValues = cache.getOrDefault(param1, Collections.emptyList());
                List<Double> yValues = cache.getOrDefault(param2, Collections.emptyList());
                Map<String, Object> pair = findBestLag(param1, param2, xValues, yValues, method, maxLag, minOverlap);
                results.add(pair);
            }
        }
        return results;
    }

    private Map<String, Object> findBestLag(
            String param1,
            String param2,
            List<Double> xValues,
            List<Double> yValues,
            String method,
            int maxLag,
            int minOverlap) {
        double bestCorr = 0.0;
        int bestLag = 0;
        int bestSamples = 0;
        List<Map<String, Object>> profile = new ArrayList<>();
        for (int lag = -maxLag; lag <= maxLag; lag++) {
            PairedSeries paired = pairByLag(xValues, yValues, lag);
            int validCount = paired.x().size();
            if (validCount < minOverlap || validCount < 2) {
                continue;
            }
            double corr = calculateCorrelation(paired.x(), paired.y(), method);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("lag", lag);
            point.put("correlation", corr);
            point.put("samples", validCount);
            profile.add(point);
            if (Math.abs(corr) > Math.abs(bestCorr)
                    || (Math.abs(corr) == Math.abs(bestCorr) && Math.abs(lag) < Math.abs(bestLag))) {
                bestCorr = corr;
                bestLag = lag;
                bestSamples = validCount;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("param1", param1);
        result.put("param2", param2);
        result.put("bestLag", bestLag);
        result.put("bestCorrelation", bestCorr);
        result.put("samples", bestSamples);
        result.put("profile", profile);
        return result;
    }

    private PairedSeries pairByLag(List<Double> xValues, List<Double> yValues, int lag) {
        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        int xSize = xValues.size();
        int ySize = yValues.size();
        for (int i = 0; i < xSize; i++) {
            int j = i + lag;
            if (j < 0 || j >= ySize) {
                continue;
            }
            Double xVal = xValues.get(i);
            Double yVal = yValues.get(j);
            if (xVal != null && yVal != null && !xVal.isNaN() && !yVal.isNaN()) {
                x.add(xVal);
                y.add(yVal);
            }
        }
        return new PairedSeries(x, y);
    }

    private int normalizeLagWindow(Integer maxLag) {
        if (maxLag == null) {
            return 0;
        }
        int normalized = Math.max(0, maxLag);
        return Math.min(normalized, 120);
    }

    private int normalizeMinOverlap(Integer minOverlap) {
        if (minOverlap == null) {
            return 20;
        }
        int normalized = Math.max(2, minOverlap);
        return Math.min(normalized, 2000);
    }

    /**
     * 提取参数值列表
     */
    private List<Double> extractParameterValues(List<ProductionData> dataList, String paramName) {
        return dataList.stream().map(data -> {
            return switch (paramName) {
                case "temperature" -> data.getTemperature();
                case "pressure" -> data.getPressure();
                case "windVolume" -> data.getWindVolume();
                case "coalInjection" -> data.getCoalInjection();
                case "materialHeight" -> data.getMaterialHeight();
                case "gasFlow" -> data.getGasFlow();
                case "oxygenLevel" -> data.getOxygenLevel();
                case "productionRate" -> data.getProductionRate();
                case "energyConsumption" -> data.getEnergyConsumption();
                case "hotMetalTemperature" -> data.getHotMetalTemperature();
                case "constantSignal" -> data.getConstantSignal();
                case "siliconContent" -> data.getSiliconContent();
                default -> null;
            };
        }).collect(Collectors.toList());
    }

    /**
     * 计算均值
     */
    private double calculateMean(double[] values, int count) {
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            sum += values[i];
        }
        return sum / count;
    }

    private double[] toArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private double[] rank(List<Double> values) {
        List<Integer> index = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            index.add(i);
        }
        index.sort((a, b) -> Double.compare(values.get(a), values.get(b)));
        double[] ranks = new double[values.size()];
        int i = 0;
        while (i < index.size()) {
            int j = i;
            while (j + 1 < index.size() && Objects.equals(values.get(index.get(j)), values.get(index.get(j + 1)))) {
                j++;
            }
            double avgRank = (i + j + 2) / 2.0;
            for (int k = i; k <= j; k++) {
                ranks[index.get(k)] = avgRank;
            }
            i = j + 1;
        }
        return ranks;
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "pearson";
        }
        String m = method.trim().toLowerCase();
        if ("spearman".equals(m)) {
            return "spearman";
        }
        return "pearson";
    }

    private record PairedSeries(List<Double> x, List<Double> y) {
    }
}
