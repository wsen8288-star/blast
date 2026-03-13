package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.ProductionData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public final class UploadedProductionDataMapper {
    private UploadedProductionDataMapper() {
    }

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

    public static List<ProductionData> toProductionDataList(
            List<Map<String, String>> uploadedRows,
            String selectedFeaturesStr,
            String targetVariableRaw
    ) {
        if (uploadedRows == null || uploadedRows.isEmpty()) {
            return Collections.emptyList();
        }

        String targetVariable = normalizeTargetVariableOrThrow(targetVariableRaw);
        Set<String> selectedKeys = normalizeSelectedKeysOrThrow(selectedFeaturesStr, targetVariable);
        boolean timestampSelected = isTimestampSelected(selectedFeaturesStr) || hasUsableTimestamp(uploadedRows);

        List<ProductionData> productionDataList = new ArrayList<>(uploadedRows.size());
        int targetPresentCount = 0;
        for (Map<String, String> rowMap : uploadedRows) {
            ProductionData data = new ProductionData();
            data.setFurnaceId("uploaded_file");
            data.setTimestamp(resolveTimestamp(rowMap, timestampSelected));

            for (String key : selectedKeys) {
                BiConsumer<ProductionData, Double> setter = FEATURE_SETTER_MAP.get(key);
                if (setter == null) {
                    continue;
                }
                Double value = parseDouble(rowMap.get(key));
                setter.accept(data, value);
            }

            Double targetValue = parseDouble(firstNonBlank(rowMap.get(targetVariable)));
            BiConsumer<ProductionData, Double> targetSetter = FEATURE_SETTER_MAP.get(targetVariable);
            if (targetSetter != null) {
                targetSetter.accept(data, targetValue);
            }
            if (targetValue != null) {
                targetPresentCount++;
            }

            data.setStatus(rowMap != null ? rowMap.getOrDefault("status", "正常") : "正常");
            data.setOperator("系统");
            productionDataList.add(data);
        }

        if (!productionDataList.isEmpty() && targetPresentCount == 0) {
            throw new IllegalArgumentException("上传文件缺少目标列(" + targetVariable + ")，无法训练/评估");
        }

        return productionDataList;
    }

    public static boolean hasUsableTimestamp(List<Map<String, String>> uploadedRows) {
        if (uploadedRows == null || uploadedRows.isEmpty()) {
            return false;
        }
        int total = 0;
        int ok = 0;
        for (Map<String, String> row : uploadedRows) {
            if (row == null) {
                continue;
            }
            if (!row.containsKey("timestamp")) {
                continue;
            }
            String s = row.get("timestamp");
            if (s == null || s.isBlank()) {
                continue;
            }
            total++;
            if (tryParseTimestamp(s) != null) {
                ok++;
            }
        }
        if (total == 0) {
            return false;
        }
        return ok * 1.0 / total >= 0.8;
    }

    private static String normalizeTargetVariableOrThrow(String targetVariableRaw) {
        String targetVariable = targetVariableRaw == null ? "" : targetVariableRaw.trim();
        if (targetVariable.isEmpty()) {
            return "productionRate";
        }
        String canonical = UploadedDataNormalizer.toCanonicalKey(targetVariable);
        if (canonical == null || !FEATURE_SETTER_MAP.containsKey(canonical)) {
            throw new IllegalArgumentException("未知预测目标: " + targetVariableRaw);
        }
        return canonical;
    }

    private static Set<String> normalizeSelectedKeysOrThrow(String selectedFeaturesStr, String targetVariable) {
        String s = selectedFeaturesStr == null ? "" : selectedFeaturesStr.trim();
        if (s.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> selectedKeys = new LinkedHashSet<>();
        for (String token : s.split(",")) {
            String raw = token == null ? "" : token.trim();
            if (raw.isEmpty()) {
                continue;
            }
            String canonical = UploadedDataNormalizer.toCanonicalKey(raw);
            if (canonical == null) {
                throw new IllegalArgumentException("您勾选了不支持的训练特征: " + raw);
            }
            if ("timestamp".equals(canonical)) {
                continue;
            }
            if (targetVariable.equals(canonical)) {
                continue;
            }
            if (!FEATURE_SETTER_MAP.containsKey(canonical)) {
                throw new IllegalArgumentException("您勾选了不支持的训练特征: " + raw);
            }
            selectedKeys.add(canonical);
        }
        return selectedKeys;
    }

    private static boolean isTimestampSelected(String selectedFeaturesStr) {
        if (selectedFeaturesStr == null || selectedFeaturesStr.isBlank()) {
            return false;
        }
        return Arrays.stream(selectedFeaturesStr.split(","))
                .map(token -> token == null ? "" : token.trim())
                .filter(token -> !token.isEmpty())
                .map(UploadedDataNormalizer::toCanonicalKey)
                .anyMatch("timestamp"::equals);
    }

    private static Date resolveTimestamp(Map<String, String> rowMap, boolean timestampSelected) {
        if (!timestampSelected) {
            return new Date();
        }
        String timestampStr = rowMap != null ? firstNonBlank(rowMap.get("timestamp")) : "";
        Date parsed = tryParseTimestamp(timestampStr);
        return parsed != null ? parsed : new Date();
    }

    public static Date tryParseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isBlank()) {
            return null;
        }
        String s = timestampStr.trim();
        String[] patterns = new String[]{
                "yyyy/M/d HH:mm",
                "yyyy/M/d HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy/M/d"
        };
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern);
                sdf.setLenient(false);
                return sdf.parse(s);
            } catch (Exception ignored) {
            }
        }
        return null;
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

    private static Double parseDouble(String value) {
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
