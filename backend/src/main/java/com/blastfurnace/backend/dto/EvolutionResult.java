package com.blastfurnace.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class EvolutionResult {
    private String runId;
    private String status;
    private String message;
    private Integer progress;
    private Long startedAt;
    private Long finishedAt;
    private List<OptimizationSolutionDTO> topSolutions;
    private List<Double> maxFitnessHistory;
    private List<Double> avgFitnessHistory;
    private List<OptimizationSolutionDTO> bestSolutionsHistory;
    private List<String> searchFeatures;
    private Map<String, com.blastfurnace.backend.optimization.ParameterRanges.Range> ranges;
    private Map<String, Double> baselineGenes;

    public EvolutionResult(
            List<OptimizationSolutionDTO> topSolutions,
            List<Double> maxFitnessHistory,
            List<Double> avgFitnessHistory,
            List<OptimizationSolutionDTO> bestSolutionsHistory
    ) {
        this.topSolutions = topSolutions;
        this.maxFitnessHistory = maxFitnessHistory;
        this.avgFitnessHistory = avgFitnessHistory;
        this.bestSolutionsHistory = bestSolutionsHistory;
    }
}
