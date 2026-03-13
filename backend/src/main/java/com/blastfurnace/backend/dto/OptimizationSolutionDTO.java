package com.blastfurnace.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class OptimizationSolutionDTO {
    private Double temperature;
    private Double pressure;
    private Double windVolume;
    private Double coalInjection;
    private Double gasFlow;
    private Double oxygenLevel;
    private Double materialHeight;
    private Map<String, Double> genes;
    private Map<String, Double> deltas;
    private Double predictedProduction;
    private Double estimatedEnergy;
    private Double predictedHotMetalTemperature;
    private Double predictedSiliconContent;
    private Double constraintViolation;
    private Double fitness;
    private Double productionScore;
    private Double energyScore;
    private Double stabilityScore;
    private Double costScore;
    private Double confidence;
    private String explanation;
    private String modelUsed;
}
