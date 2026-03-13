package com.blastfurnace.backend.dto;

import com.blastfurnace.backend.model.ModelEvaluation;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class EvaluationResultVO {
    private ModelEvaluation evaluation;
    private List<Double> trueValues;
    private List<Double> predictedValues;
    private Map<String, Double> featureImportance;
}
