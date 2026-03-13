package com.blastfurnace.backend.service.trainer;

/**
 * 训练结果类，保存训练过程中的关键指标
 */
public class TrainingResult {
    
    private double trainingLoss;
    private double r2Score;
    private double mae;
    private double rmse;
    private boolean success;
    private String message;
    private Object model;
    private Object preprocessor;
    private String[] features;
    private java.util.List<Double> trueValues;
    private java.util.List<Double> predictedValues;
    private java.util.Map<String, Double> featureImportance;
    
    // 构造方法
    public TrainingResult() {
    }
    
    public TrainingResult(double trainingLoss, double r2Score, double mae, double rmse, boolean success, String message) {
        this.trainingLoss = trainingLoss;
        this.r2Score = r2Score;
        this.mae = mae;
        this.rmse = rmse;
        this.success = success;
        this.message = message;
    }
    
    public TrainingResult(double trainingLoss, double r2Score, boolean success, String message) {
        this.trainingLoss = trainingLoss;
        this.r2Score = r2Score;
        this.mae = 0;
        this.rmse = 0;
        this.success = success;
        this.message = message;
    }
    
    // Getters and Setters
    public double getTrainingLoss() {
        return trainingLoss;
    }
    
    public void setTrainingLoss(double trainingLoss) {
        this.trainingLoss = trainingLoss;
    }
    
    public double getR2Score() {
        return r2Score;
    }
    
    public void setR2Score(double r2Score) {
        this.r2Score = r2Score;
    }
    
    public double getMae() {
        return mae;
    }
    
    public void setMae(double mae) {
        this.mae = mae;
    }
    
    public double getRmse() {
        return rmse;
    }
    
    public void setRmse(double rmse) {
        this.rmse = rmse;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Object getModel() {
        return model;
    }
    
    public void setModel(Object model) {
        this.model = model;
    }
    
    public Object getPreprocessor() {
        return preprocessor;
    }
    
    public void setPreprocessor(Object preprocessor) {
        this.preprocessor = preprocessor;
    }
    
    public String[] getFeatures() {
        return features;
    }
    
    public void setFeatures(String[] features) {
        this.features = features;
    }

    public java.util.List<Double> getTrueValues() {
        return trueValues;
    }

    public void setTrueValues(java.util.List<Double> trueValues) {
        this.trueValues = trueValues;
    }

    public java.util.List<Double> getPredictedValues() {
        return predictedValues;
    }

    public void setPredictedValues(java.util.List<Double> predictedValues) {
        this.predictedValues = predictedValues;
    }

    public java.util.Map<String, Double> getFeatureImportance() {
        return featureImportance;
    }

    public void setFeatureImportance(java.util.Map<String, Double> featureImportance) {
        this.featureImportance = featureImportance;
    }
}
