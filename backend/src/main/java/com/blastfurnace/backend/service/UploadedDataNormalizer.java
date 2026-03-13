package com.blastfurnace.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public final class UploadedDataNormalizer {


    public record NormalizedUpload(
            List<Map<String, String>> rows,
            List<String> originalHeaders,
            List<String> normalizedHeaders,
            Map<String, String> headerMapping
    ) {
    }

    public static NormalizedUpload normalize(List<Map<String, String>> originalRows) {
        if (originalRows == null || originalRows.isEmpty()) {
            return new NormalizedUpload(List.of(), List.of(), List.of(), Map.of());
        }

        List<String> originalHeaders = new ArrayList<>(originalRows.get(0).keySet());
        Map<String, String> headerMapping = new LinkedHashMap<>();
        Set<String> normalizedHeaders = new LinkedHashSet<>();

        for (String header : originalHeaders) {
            String canonical = toCanonicalKey(header);
            if (canonical != null) {
                headerMapping.put(header, canonical);
                normalizedHeaders.add(canonical);
            }
        }

        List<Map<String, String>> normalizedRows = new ArrayList<>(originalRows.size());
        for (Map<String, String> row : originalRows) {
            Map<String, String> normalizedRow = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String canonical = toCanonicalKey(entry.getKey());
                if (canonical == null) {
                    continue;
                }
                String value = entry.getValue();
                if (value == null) {
                    continue;
                }
                if (!normalizedRow.containsKey(canonical) || normalizedRow.get(canonical).isBlank()) {
                    normalizedRow.put(canonical, value);
                }
            }
            normalizedRows.add(normalizedRow);
        }

        return new NormalizedUpload(
                normalizedRows,
                List.copyOf(originalHeaders),
                List.copyOf(normalizedHeaders),
                Map.copyOf(headerMapping)
        );
    }

    public static String toCanonicalKey(String header) {
        String normalized = normalizeHeader(header);
        if (normalized.isEmpty()) {
            return null;
        }

        String direct = CANONICAL_NORMALIZED_MAP.get(normalized);
        if (direct != null) {
            return direct;
        }

        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            String canonical = entry.getKey();
            for (String token : entry.getValue()) {
                if (normalized.equals(token)) {
                    return canonical;
                }
            }
        }

        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            String canonical = entry.getKey();
            for (String token : entry.getValue()) {
                if (normalized.contains(token)) {
                    return canonical;
                }
            }
        }
        return null;
    }

    private static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        String s = header.replace("\uFEFF", "").trim();
        if (s.isEmpty()) {
            return "";
        }
        s = s.replaceAll("[\\s\\u00A0]+", "");
        s = s.replaceAll("[\\(（\\[【].*?[\\)）\\]】]", "");
        s = s.replaceAll("[^\\p{IsHan}A-Za-z0-9_%]+", "");
        return s.toLowerCase(Locale.ROOT);
    }

    private static final Map<String, List<String>> ALIASES = buildAliases();
    private static final Map<String, String> CANONICAL_NORMALIZED_MAP = buildCanonicalNormalizedMap();

    private static Map<String, List<String>> buildAliases() {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        map.put("timestamp", tokens("时间戳", "时间", "timestamp", "datetime", "date", "time"));
        map.put("furnaceId", tokens("高炉编号", "高炉id", "炉号", "furnaceid", "furnace_id", "blastfurnaceid"));
        map.put("hotMetalTemperature", tokens("铁水温度", "出铁温度", "hotmetaltemperature", "hotmetaltemp"));
        map.put("temperature", tokens("温度", "炉温", "temperature", "temp"));
        map.put("pressure", tokens("压力", "pressure", "press"));
        map.put("materialHeight", tokens("料面高度", "料位高度", "料面", "料位", "materialheight", "height", "level"));
        map.put("gasFlow", tokens("煤气流量", "煤气", "gasflow"));
        map.put("oxygenLevel", tokens("氧气含量", "氧含量", "oxygenlevel", "oxygen", "o2"));
        map.put("energyConsumption", tokens("能耗", "energyconsumption"));
        map.put("constantSignal", tokens("常量信号", "常量", "constantsignal", "constant"));
        map.put("siliconContent", tokens("铁水含硅量", "硅含量", "si含量", "siliconcontent", "silicon"));
        map.put("status", tokens("状态", "工况状态", "status"));
        map.put("windVolume", tokens("风量", "windvolume"));
        map.put("coalInjection", tokens("喷煤量", "煤粉", "coalinjection"));
        map.put("productionRate", tokens("生产率", "产量", "生产量", "productionrate", "target"));
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildCanonicalNormalizedMap() {
        HashMap<String, String> map = new HashMap<>();
        for (String canonical : ALIASES.keySet()) {
            map.put(normalizeHeader(canonical), canonical);
        }
        return Collections.unmodifiableMap(map);
    }

    private static List<String> tokens(String... values) {
        List<String> tokens = new ArrayList<>(values.length);
        for (String value : values) {
            tokens.add(normalizeHeader(value));
        }
        return List.copyOf(tokens);
    }
}
