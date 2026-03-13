package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.ProductionData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@lombok.RequiredArgsConstructor
public class SimulationQualityService {
    private final ThresholdResolverService thresholdResolverService;
    private static final List<String> METRIC_KEYS = List.of(
            "temperature",
            "pressure",
            "windVolume",
            "coalInjection",
            "materialHeight",
            "gasFlow",
            "oxygenLevel",
            "productionRate",
            "energyConsumption",
            "hotMetalTemperature",
            "siliconContent"
    );

    private static final Map<String, Function<ProductionData, Double>> EXTRACTORS = new LinkedHashMap<>();

    static {
        EXTRACTORS.put("temperature", ProductionData::getTemperature);
        EXTRACTORS.put("pressure", ProductionData::getPressure);
        EXTRACTORS.put("windVolume", ProductionData::getWindVolume);
        EXTRACTORS.put("coalInjection", ProductionData::getCoalInjection);
        EXTRACTORS.put("materialHeight", ProductionData::getMaterialHeight);
        EXTRACTORS.put("gasFlow", ProductionData::getGasFlow);
        EXTRACTORS.put("oxygenLevel", ProductionData::getOxygenLevel);
        EXTRACTORS.put("productionRate", ProductionData::getProductionRate);
        EXTRACTORS.put("energyConsumption", ProductionData::getEnergyConsumption);
        EXTRACTORS.put("hotMetalTemperature", ProductionData::getHotMetalTemperature);
        EXTRACTORS.put("siliconContent", ProductionData::getSiliconContent);
    }

    public Map<String, Object> evaluate(List<ProductionData> rows, String furnaceId) {
        int sampleSize = rows == null ? 0 : rows.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sampleSize", sampleSize);
        result.put("furnaceId", furnaceId);
        Map<String, String> thresholdSources = new LinkedHashMap<>();
        Map<String, ThresholdResolverService.ResolvedThreshold> thresholds = new LinkedHashMap<>();
        for (String key : METRIC_KEYS) {
            var rt = thresholdResolverService.resolve(furnaceId, key);
            thresholds.put(key, rt);
            thresholdSources.put(key, rt == null ? "DEFAULT" : rt.source());
        }

        if (sampleSize == 0) {
            result.put("metrics", Map.of());
            result.put("indicators", List.of());
            result.put("thresholdSources", thresholdSources);
            return result;
        }

        int totalCells = sampleSize * METRIC_KEYS.size();
        int missingCount = 0;
        int sentinelCount = 0;
        int warningRows = 0;
        int severeRows = 0;

        for (ProductionData row : rows) {
            boolean warning = false;
            boolean severe = false;
            for (String key : METRIC_KEYS) {
                Double value = getValue(row, key);
                if (value == null) {
                    missingCount++;
                    continue;
                }
                if (isSentinel(value)) {
                    sentinelCount++;
                }
                var rt = thresholds.get(key);
                if (rt != null) {
                    if (value < rt.min() || value > rt.max()) {
                        severe = true;
                    } else if (value < rt.warningMin() || value > rt.warningMax()) {
                        warning = true;
                    }
                } else {
                    if (IndustrialDataContract.isSevere(key, value)) {
                        severe = true;
                    } else if (IndustrialDataContract.isWarning(key, value)) {
                        warning = true;
                    }
                }
            }
            if (severe) {
                severeRows++;
            } else if (warning) {
                warningRows++;
            }
        }

        double missingRatePct = percent(missingCount, totalCells);
        double sentinelRatePct = percent(sentinelCount, totalCells);
        double outOfRangeRatePct = percent(severeRows, sampleSize);
        double warningRatePct = percent(warningRows, sampleSize);
        double corrWindVsProduction = correlation(rows, "windVolume", "productionRate");
        double corrEnergyVsSilicon = correlation(rows, "energyConsumption", "siliconContent");

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("missingRatePct", round(missingRatePct));
        metrics.put("sentinelRatePct", round(sentinelRatePct));
        metrics.put("outOfRangeRatePct", round(outOfRangeRatePct));
        metrics.put("warningRatePct", round(warningRatePct));
        metrics.put("corrWindVsProduction", round(corrWindVsProduction));
        metrics.put("corrEnergyVsSilicon", round(corrEnergyVsSilicon));
        result.put("metrics", metrics);

        List<Map<String, Object>> indicators = new ArrayList<>();
        indicators.add(indicator("missing_rate", "缺失率", round(missingRatePct), "≤ 2%", missingRatePct <= 2, "完整性"));
        indicators.add(indicator("sentinel_rate", "哨兵值占比", round(sentinelRatePct), "≤ 1%", sentinelRatePct <= 1, "完整性"));
        indicators.add(indicator("out_of_range_rate", "硬限制越界率", round(outOfRangeRatePct), "≤ 0.5%", outOfRangeRatePct <= 0.5, "范围性"));
        indicators.add(indicator("warning_rate", "预警触发率", round(warningRatePct), "2% ~ 10%", warningRatePct >= 2 && warningRatePct <= 10, "范围性"));
        indicators.add(indicator("corr_wind_prod", "风量-生产率相关系数", round(corrWindVsProduction), "> 0", corrWindVsProduction > 0, "相关性"));
        indicators.add(indicator("corr_energy_si", "能耗-硅含量相关系数", round(corrEnergyVsSilicon), "> 0", corrEnergyVsSilicon > 0, "相关性"));
        result.put("indicators", indicators);
        result.put("thresholdSources", thresholdSources);
        return result;
    }

    private Map<String, Object> indicator(String key, String name, double value, String target, boolean pass, String category) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("name", name);
        item.put("value", value);
        item.put("target", target);
        item.put("pass", pass);
        item.put("category", category);
        return item;
    }

    private boolean isSentinel(Double value) {
        return value != null && (value == 9999 || value == 9999.0);
    }

    private Double getValue(ProductionData row, String key) {
        Function<ProductionData, Double> extractor = EXTRACTORS.get(key);
        return extractor == null ? null : extractor.apply(row);
    }

    private double percent(int part, int total) {
        if (total <= 0) {
            return 0;
        }
        return part * 100.0 / total;
    }

    private double correlation(List<ProductionData> rows, String xKey, String yKey) {
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (ProductionData row : rows) {
            Double x = getValue(row, xKey);
            Double y = getValue(row, yKey);
            if (x != null && y != null) {
                xs.add(x);
                ys.add(y);
            }
        }
        int n = xs.size();
        if (n < 2) {
            return 0;
        }
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumX2 = 0;
        double sumY2 = 0;
        for (int i = 0; i < n; i++) {
            double x = xs.get(i);
            double y = ys.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }
        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        if (denominator == 0) {
            return 0;
        }
        return numerator / denominator;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
