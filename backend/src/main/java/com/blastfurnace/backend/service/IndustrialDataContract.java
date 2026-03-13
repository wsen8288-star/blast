package com.blastfurnace.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IndustrialDataContract {
    private IndustrialDataContract() {
    }

    public record ParameterSpec(
            String key,
            String label,
            String unit,
            double min,
            double max,
            double warningMin,
            double warningMax
    ) {
    }

    private static final Map<String, ParameterSpec> CANONICAL_SPECS = buildCanonicalSpecs();
    private static final Map<String, ParameterSpec> RANGE_LOOKUP = buildRangeLookup();

    public static Map<String, ParameterSpec> getCanonicalSpecs() {
        return CANONICAL_SPECS;
    }

    public static List<ParameterSpec> orderedSpecs() {
        return List.copyOf(CANONICAL_SPECS.values());
    }

    public static ParameterSpec findByAnyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String canonical = UploadedDataNormalizer.toCanonicalKey(key);
        if (canonical != null) {
            ParameterSpec spec = CANONICAL_SPECS.get(canonical);
            if (spec != null) {
                return spec;
            }
        }
        return RANGE_LOOKUP.get(normalizeToken(key));
    }

    public static boolean isWarning(String key, Double value) {
        ParameterSpec spec = findByAnyKey(key);
        if (spec == null || value == null) {
            return false;
        }
        return value < spec.warningMin || value > spec.warningMax;
    }

    public static boolean isSevere(String key, Double value) {
        ParameterSpec spec = findByAnyKey(key);
        if (spec == null || value == null) {
            return false;
        }
        return value < spec.min || value > spec.max;
    }

    public static double clamp(String key, double value) {
        ParameterSpec spec = findByAnyKey(key);
        if (spec == null) {
            return value;
        }
        return Math.max(spec.min, Math.min(spec.max, value));
    }

    public static Map<String, ParameterSpec> buildRangeLookup() {
        LinkedHashMap<String, ParameterSpec> lookup = new LinkedHashMap<>();
        for (ParameterSpec spec : CANONICAL_SPECS.values()) {
            putAlias(lookup, spec, spec.key);
        }

        ParameterSpec temperature = CANONICAL_SPECS.get("temperature");
        putAliases(lookup, temperature, "温度", "炉温", "furnaceTemperature");

        ParameterSpec pressure = CANONICAL_SPECS.get("pressure");
        putAliases(lookup, pressure, "压力", "风压", "windPressure");

        ParameterSpec windVolume = CANONICAL_SPECS.get("windVolume");
        putAliases(lookup, windVolume, "风量", "wind_volume");

        ParameterSpec coalInjection = CANONICAL_SPECS.get("coalInjection");
        putAliases(lookup, coalInjection, "喷煤量", "coal_injection");

        ParameterSpec materialHeight = CANONICAL_SPECS.get("materialHeight");
        putAliases(lookup, materialHeight, "料面高度", "料位高度", "level");

        ParameterSpec gasFlow = CANONICAL_SPECS.get("gasFlow");
        putAliases(lookup, gasFlow, "煤气流量", "gas_flow");

        ParameterSpec oxygenLevel = CANONICAL_SPECS.get("oxygenLevel");
        putAliases(lookup, oxygenLevel, "氧气含量", "oxygenContent");

        ParameterSpec productionRate = CANONICAL_SPECS.get("productionRate");
        putAliases(lookup, productionRate, "生产率", "产量", "target");

        ParameterSpec energy = CANONICAL_SPECS.get("energyConsumption");
        putAliases(lookup, energy, "能耗", "energy_consumption");

        ParameterSpec hotMetalTemp = CANONICAL_SPECS.get("hotMetalTemperature");
        putAliases(lookup, hotMetalTemp, "铁水温度");

        ParameterSpec silicon = CANONICAL_SPECS.get("siliconContent");
        putAliases(lookup, silicon, "硅含量", "铁水含硅量");

        return Map.copyOf(lookup);
    }

    private static Map<String, ParameterSpec> buildCanonicalSpecs() {
        List<ParameterSpec> list = new ArrayList<>();
        list.add(spec("temperature", "温度", "℃", 1100, 1400, 1150, 1380));
        list.add(spec("pressure", "压力", "kPa", 100, 300, 120, 280));
        list.add(spec("windVolume", "风量", "m³/h", 3000, 6000, 3300, 5700));
        list.add(spec("coalInjection", "喷煤量", "kg/t", 100, 220, 110, 210));
        list.add(spec("materialHeight", "料面高度", "m", 2, 6, 2.3, 5.7));
        list.add(spec("gasFlow", "煤气流量", "m³/h", 2000, 5000, 2300, 4700));
        list.add(spec("oxygenLevel", "氧气含量", "%", 18, 25, 18.5, 24.5));
        list.add(spec("productionRate", "生产率", "t/h", 20, 80, 25, 75));
        list.add(spec("energyConsumption", "能耗", "kgce/t", 800, 2000, 900, 1800));
        list.add(spec("hotMetalTemperature", "铁水温度", "℃", 1420, 1560, 1440, 1540));
        list.add(spec("siliconContent", "硅含量", "%", 0.1, 1.0, 0.2, 0.8));

        LinkedHashMap<String, ParameterSpec> map = new LinkedHashMap<>();
        for (ParameterSpec s : list) {
            map.put(s.key, s);
        }
        return Map.copyOf(map);
    }

    private static ParameterSpec spec(
            String key,
            String label,
            String unit,
            double min,
            double max,
            double warningMin,
            double warningMax
    ) {
        return new ParameterSpec(key, label, unit, min, max, warningMin, warningMax);
    }

    private static void putAliases(Map<String, ParameterSpec> map, ParameterSpec spec, String... aliases) {
        if (spec == null || aliases == null) {
            return;
        }
        for (String alias : aliases) {
            putAlias(map, spec, alias);
        }
    }

    private static void putAlias(Map<String, ParameterSpec> map, ParameterSpec spec, String alias) {
        if (spec == null || alias == null || alias.isBlank()) {
            return;
        }
        map.put(normalizeToken(alias), spec);
    }

    private static String normalizeToken(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
