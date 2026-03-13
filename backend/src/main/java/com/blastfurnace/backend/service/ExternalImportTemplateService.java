package com.blastfurnace.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ExternalImportTemplateService {
    private final Map<String, Map<String, Object>> templates = new LinkedHashMap<>();

    public ExternalImportTemplateService(ObjectMapper objectMapper) {
        try {
            ClassPathResource resource = new ClassPathResource("external-import-templates.json");
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    List<Map<String, Object>> data = objectMapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
                    for (Map<String, Object> item : data) {
                        String key = String.valueOf(item.getOrDefault("key", "")).trim();
                        if (!key.isEmpty()) {
                            templates.put(key, item);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (templates.isEmpty()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("key", "default");
            fallback.put("label", "默认模板");
            fallback.put("requiredCanonical", List.of("temperature", "pressure", "windVolume", "coalInjection"));
            fallback.put("aliases", Map.of());
            fallback.put("unitConversions", List.of());
            templates.put("default", fallback);
        }
    }

    public List<Map<String, Object>> listTemplates() {
        return new ArrayList<>(templates.values());
    }

    public String normalizeTemplateKey(String key) {
        if (key == null || key.isBlank() || !templates.containsKey(key)) {
            return templates.keySet().iterator().next();
        }
        return key;
    }

    public PreviewResult preview(List<Map<String, String>> originalRows, String templateKey) {
        String finalTemplateKey = normalizeTemplateKey(templateKey);
        Map<String, Object> template = templates.get(finalTemplateKey);
        Map<String, List<String>> aliases = aliasesFromTemplate(template);
        List<Map<String, Object>> unitConversions = unitConversionsFromTemplate(template);
        List<String> requiredCanonical = requiredCanonicalFromTemplate(template);

        List<String> originalHeaders = originalRows.isEmpty() ? List.of() : new ArrayList<>(originalRows.get(0).keySet());
        Map<String, String> headerMapping = new LinkedHashMap<>();
        Map<String, String> headerUnitMap = new HashMap<>();
        List<String> unmatchedHeaders = new ArrayList<>();
        Set<String> matchedCanonicals = new java.util.LinkedHashSet<>();

        for (String header : originalHeaders) {
            String canonical = resolveCanonical(header, aliases);
            if (canonical == null) {
                unmatchedHeaders.add(header);
                continue;
            }
            matchedCanonicals.add(canonical);
            headerMapping.put(header, canonical);
            headerUnitMap.put(header, detectUnit(header));
        }

        List<String> missingRequired = new ArrayList<>();
        for (String req : requiredCanonical) {
            if (!matchedCanonicals.contains(req)) {
                missingRequired.add(req);
            }
        }

        List<Map<String, String>> normalizedRows = new ArrayList<>(originalRows.size());
        int convertedCells = 0;
        for (Map<String, String> row : originalRows) {
            Map<String, String> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String header = entry.getKey();
                String canonical = headerMapping.get(header);
                if (canonical == null) {
                    continue;
                }
                String rawValue = entry.getValue();
                if (rawValue == null || rawValue.isBlank()) {
                    continue;
                }
                String sourceUnit = headerUnitMap.getOrDefault(header, "");
                String converted = convertValue(rawValue, canonical, sourceUnit, unitConversions);
                if (!converted.equals(rawValue.trim())) {
                    convertedCells++;
                }
                if (!normalized.containsKey(canonical) || normalized.get(canonical).isBlank()) {
                    normalized.put(canonical, converted);
                }
            }
            normalizedRows.add(normalized);
        }

        return new PreviewResult(finalTemplateKey, originalHeaders, headerMapping, unmatchedHeaders, missingRequired, normalizedRows, convertedCells);
    }

    public record PreviewResult(
            String templateKey,
            List<String> originalHeaders,
            Map<String, String> headerMapping,
            List<String> unmatchedHeaders,
            List<String> missingRequiredFields,
            List<Map<String, String>> normalizedRows,
            int convertedCellCount
    ) {}

    private Map<String, List<String>> aliasesFromTemplate(Map<String, Object> template) {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        Object raw = template.get("aliases");
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return aliases;
        }
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String canonical = String.valueOf(entry.getKey());
            List<String> tokens = new ArrayList<>();
            if (entry.getValue() instanceof List<?> list) {
                for (Object item : list) {
                    tokens.add(normalizeHeader(String.valueOf(item)));
                }
            }
            aliases.put(canonical, tokens);
        }
        return aliases;
    }

    private List<Map<String, Object>> unitConversionsFromTemplate(Map<String, Object> template) {
        Object raw = template.get("unitConversions");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        copy.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    out.add(copy);
                }
            }
            return out;
        }
        return List.of();
    }

    private List<String> requiredCanonicalFromTemplate(Map<String, Object> template) {
        Object raw = template.get("requiredCanonical");
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                out.add(String.valueOf(item));
            }
            return out;
        }
        return List.of();
    }

    private String resolveCanonical(String header, Map<String, List<String>> aliases) {
        String normalized = normalizeHeader(header);
        for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
            for (String alias : entry.getValue()) {
                if (normalized.equals(alias)) {
                    return entry.getKey();
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
            for (String alias : entry.getValue()) {
                if (normalized.equals(alias) || normalized.contains(alias)) {
                    return entry.getKey();
                }
            }
        }
        return UploadedDataNormalizer.toCanonicalKey(header);
    }

    private String convertValue(String rawValue, String canonical, String sourceUnit, List<Map<String, Object>> unitConversions) {
        Double number = parseNumber(rawValue);
        if (number == null || sourceUnit.isBlank()) {
            return rawValue.trim();
        }
        for (Map<String, Object> c : unitConversions) {
            String cCanonical = String.valueOf(c.getOrDefault("canonical", ""));
            String fromUnit = String.valueOf(c.getOrDefault("fromUnit", "")).toLowerCase(Locale.ROOT);
            if (!canonical.equals(cCanonical) || !sourceUnit.equals(fromUnit)) {
                continue;
            }
            Double factorVal = parseNumber(String.valueOf(c.getOrDefault("factor", "1")));
            Double offsetVal = parseNumber(String.valueOf(c.getOrDefault("offset", "0")));
            double factor = factorVal == null ? 1 : factorVal;
            double offset = offsetVal == null ? 0 : offsetVal;
            double converted = number * factor + offset;
            return String.valueOf(converted);
        }
        return rawValue.trim();
    }

    private Double parseNumber(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String detectUnit(String header) {
        String n = normalizeHeader(header);
        if (n.contains("kpa")) return "kpa";
        if (n.contains("mpa")) return "mpa";
        if (n.contains("pa")) return "pa";
        if (n.contains("kelvin") || n.endsWith("k")) return "k";
        if (n.contains("celsius") || n.contains("℃") || n.endsWith("c")) return "c";
        return "";
    }

    private String normalizeHeader(String header) {
        if (header == null) return "";
        String s = header.replace("\uFEFF", "").trim();
        s = s.replaceAll("[\\s\\u00A0]+", "");
        s = s.replaceAll("[^\\p{IsHan}A-Za-z0-9_%℃]+", "");
        return s.toLowerCase(Locale.ROOT);
    }
}
