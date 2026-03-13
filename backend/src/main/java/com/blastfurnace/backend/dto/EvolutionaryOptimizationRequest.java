package com.blastfurnace.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvolutionaryOptimizationRequest {
    private String runId;
    private String mode;
    private Integer generations;
    private Integer populationSize;
    private Long serviceId;
    private String furnaceId;
    private Long baselineDataId;
}
