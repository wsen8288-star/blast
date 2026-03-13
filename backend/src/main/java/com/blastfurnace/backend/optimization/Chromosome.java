package com.blastfurnace.backend.optimization;

import java.util.HashMap;
import java.util.Map;

public class Chromosome {
    private final Map<String, Double> genes;

    public Chromosome(Map<String, Double> genes) {
        this.genes = genes != null ? new HashMap<>(genes) : new HashMap<>();
    }

    public Map<String, Double> getGenes() {
        return genes;
    }

    public Double getGene(String featureName) {
        if (featureName == null) {
            return null;
        }
        return genes.get(featureName);
    }

    public void setGene(String featureName, Double value) {
        if (featureName == null || featureName.isBlank() || value == null) {
            return;
        }
        genes.put(featureName, value);
    }

    public Chromosome copy() {
        return new Chromosome(genes);
    }
}
