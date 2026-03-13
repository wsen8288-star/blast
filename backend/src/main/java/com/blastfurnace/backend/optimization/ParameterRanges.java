package com.blastfurnace.backend.optimization;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ParameterRanges {
    private final Map<String, Range> ranges;

    public ParameterRanges(Map<String, Range> ranges) {
        this.ranges = Collections.unmodifiableMap(ranges != null ? Map.copyOf(ranges) : Map.of());
    }

    public Map<String, Range> getRanges() {
        return ranges;
    }

    public Range getRange(String featureName) {
        if (featureName == null) {
            return null;
        }
        return ranges.get(featureName);
    }

    public List<String> keys() {
        return ranges.keySet().stream().filter(Objects::nonNull).sorted().toList();
    }

    public record Range(double min, double max) {
    }
}
