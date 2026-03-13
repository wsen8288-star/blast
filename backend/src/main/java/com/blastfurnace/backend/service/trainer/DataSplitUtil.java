package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class DataSplitUtil {
    private DataSplitUtil() {
    }

    public enum SplitMode {
        AUTO,
        RANDOM,
        TIME
    }

    public record SplitResult(
            List<ProductionData> ordered,
            List<ProductionData> train,
            List<ProductionData> validation,
            SplitMode usedMode,
            double ratio,
            long seed
    ) {
    }

    public static SplitResult split(List<ProductionData> data, ModelTraining training) {
        if (data == null) {
            return new SplitResult(List.of(), List.of(), List.of(), SplitMode.RANDOM, 0.8, 123L);
        }
        double ratio = normalizeRatio(training != null ? training.getSplitRatio() : null);
        long seed = normalizeSeed(training != null ? training.getSplitSeed() : null);
        SplitMode requested = normalizeMode(training != null ? training.getSplitModeUsed() : null);
        if (requested == null) {
            requested = normalizeMode(training != null ? training.getSplitMode() : null);
        }
        if (requested == null) {
            requested = SplitMode.AUTO;
        }

        boolean hasTimestamp = Boolean.TRUE.equals(training != null ? training.getSplitHasTimestamp() : null);
        SplitMode used = resolveUsedMode(requested, hasTimestamp);

        List<ProductionData> ordered = new ArrayList<>(data);
        if (used == SplitMode.TIME) {
            ordered.sort(Comparator.comparing(ProductionData::getTimestamp));
        } else {
            java.util.Collections.shuffle(ordered, new Random(seed));
        }

        if (ordered.size() < 10) {
            return new SplitResult(ordered, ordered, ordered, used, ratio, seed);
        }

        int splitIndex = (int) (ordered.size() * ratio);
        if (splitIndex <= 0 || splitIndex >= ordered.size()) {
            return new SplitResult(ordered, ordered, ordered, used, ratio, seed);
        }
        List<ProductionData> train = ordered.subList(0, splitIndex);
        List<ProductionData> validation = ordered.subList(splitIndex, ordered.size());
        return new SplitResult(ordered, train, validation, used, ratio, seed);
    }

    private static SplitMode resolveUsedMode(SplitMode requested, boolean hasTimestamp) {
        if (requested == SplitMode.TIME) {
            return hasTimestamp ? SplitMode.TIME : SplitMode.RANDOM;
        }
        if (requested == SplitMode.AUTO) {
            return hasTimestamp ? SplitMode.TIME : SplitMode.RANDOM;
        }
        return SplitMode.RANDOM;
    }

    private static double normalizeRatio(Double ratio) {
        if (ratio == null || Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return 0.8;
        }
        double r = Math.max(0.5, Math.min(0.95, ratio));
        return r;
    }

    private static long normalizeSeed(Long seed) {
        if (seed == null) {
            return 123L;
        }
        return seed;
    }

    private static SplitMode normalizeMode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "auto" -> SplitMode.AUTO;
            case "time" -> SplitMode.TIME;
            case "random" -> SplitMode.RANDOM;
            default -> null;
        };
    }
}

