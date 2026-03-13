package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.AnomalyThreshold;
import com.blastfurnace.backend.repository.AnomalyThresholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ThresholdResolverService {
    private final AnomalyThresholdRepository anomalyThresholdRepository;

    public ResolvedThreshold resolve(String furnaceId, String param) {
        for (String candidate : resolveParamCandidates(param)) {
            Optional<AnomalyThreshold> specific = (furnaceId == null || furnaceId.isBlank())
                    ? Optional.empty()
                    : anomalyThresholdRepository.findFirstByFurnaceIdAndParameterNameOrderByUpdateTimeDesc(furnaceId, candidate);
            if (specific.isPresent()) {
                var cfg = specific.get();
                return fromConfig(cfg, "FURNACE");
            }
            Optional<AnomalyThreshold> global = anomalyThresholdRepository.findFirstByFurnaceIdAndParameterNameOrderByUpdateTimeDesc("GLOBAL", candidate);
            if (global.isPresent()) {
                var cfg = global.get();
                return fromConfig(cfg, "GLOBAL");
            }
        }
        DefaultConfig def = defaults(param);
        if (def == null) return null;
        return compute(def.min, def.max, 0.0, 10.0, 20.0, "DEFAULT");
    }

    public Map<String, ResolvedThreshold> resolveAll(String furnaceId) {
        Map<String, ResolvedThreshold> resolved = new LinkedHashMap<>();
        for (IndustrialDataContract.ParameterSpec spec : IndustrialDataContract.orderedSpecs()) {
            if (spec == null || spec.key() == null) {
                continue;
            }
            ResolvedThreshold threshold = resolve(furnaceId, spec.key());
            if (threshold != null) {
                resolved.put(spec.key(), threshold);
            }
        }
        return resolved;
    }

    private ResolvedThreshold fromConfig(AnomalyThreshold cfg, String source) {
        double tip = pctOrDefault(cfg.getTipOffsetPct(), 0.0);
        double warn = pctOrDefault(cfg.getWarningOffsetPct(), 10.0);
        double severe = pctOrDefault(cfg.getSevereOffsetPct(), 20.0);
        return compute(cfg.getMinVal(), cfg.getMaxVal(), tip, warn, severe, source);
    }

    private double pctOrDefault(Double v, double def) {
        if (v == null) return def;
        if (Double.isNaN(v) || Double.isInfinite(v)) return def;
        return v;
    }

    private ResolvedThreshold compute(Double min, Double max, double tipPct, double warnPct, double severePct, String source) {
        if (min == null || max == null) return null;
        double lo = min;
        double hi = max;
        double width = hi - lo;
        double warningMin = lo;
        double warningMax = hi;
        if (width > 0) {
            double delta = Math.max(0.0, warnPct) / 100.0 * width;
            warningMin = lo + delta;
            warningMax = hi - delta;
            if (warningMin > warningMax) {
                warningMin = lo;
                warningMax = hi;
            }
        }
        return new ResolvedThreshold(lo, hi, warningMin, warningMax, tipPct, warnPct, severePct, source);
    }

    private DefaultConfig defaults(String param) {
        IndustrialDataContract.ParameterSpec spec = IndustrialDataContract.findByAnyKey(param);
        if (spec == null) {
            return null;
        }
        return new DefaultConfig(spec.min(), spec.max());
    }

    private Set<String> resolveParamCandidates(String param) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (param != null && !param.isBlank()) {
            String raw = param.trim();
            candidates.add(raw);
            String canonical = UploadedDataNormalizer.toCanonicalKey(raw);
            if (canonical != null && !canonical.isBlank()) {
                candidates.add(canonical);
                candidates.add(toSnakeCase(canonical));
            }
            IndustrialDataContract.ParameterSpec spec = IndustrialDataContract.findByAnyKey(raw);
            if (spec != null) {
                candidates.add(spec.key());
                candidates.add(toSnakeCase(spec.key()));
                if (spec.label() != null && !spec.label().isBlank()) {
                    candidates.add(spec.label());
                }
            }
        }
        return candidates;
    }

    private String toSnakeCase(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String snake = input.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
        return snake;
    }

    public record ResolvedThreshold(
            double min,
            double max,
            double warningMin,
            double warningMax,
            double tipOffsetPct,
            double warningOffsetPct,
            double severeOffsetPct,
            String source
    ) {}

    private record DefaultConfig(double min, double max) {}
}
