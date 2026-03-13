package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ProductionData;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.regression.RandomForest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 随机森林训练器 - 使用Smile库实现
 */
public class RandomForestTrainer implements ModelTrainer {
    
    @Override
    public TrainingResult train(List<ProductionData> trainingData, ModelTraining modelTraining, 
                               ModelConfig modelConfig, String[] selectedFeatures) {
        try {
            java.util.Arrays.sort(selectedFeatures);
            if (trainingData == null || trainingData.isEmpty()) {
                return new TrainingResult(0, 0, false, "训练数据为空");
            }
            
            System.out.println("========== 开始Smile随机森林训练 ==========");
            System.out.println("训练数据量: " + trainingData.size());
            String targetVariable = getTargetVariable(modelTraining);

            int droppedTargets = 0;
            List<ProductionData> filteredTrainingData = new ArrayList<>(trainingData.size());
            for (ProductionData pd : trainingData) {
                Double y = getTargetValue(pd, targetVariable);
                if (y == null || Double.isNaN(y) || Double.isInfinite(y)) {
                    droppedTargets++;
                    continue;
                }
                filteredTrainingData.add(pd);
            }
            if (filteredTrainingData.isEmpty()) {
                return new TrainingResult(0, 0, false, "目标值缺失，无法训练: " + targetVariable);
            }
            
            // 计算输入特征数量
            int inputSize = 0;
            for (String feature : selectedFeatures) {
                if (!targetVariable.equals(feature)) {
                    inputSize++;
                }
            }
            
            if (inputSize == 0) {
                return new TrainingResult(0, 0, false, "没有选择有效的输入特征");
            }
            
            // 构建特征名称数组
            String[] featureNames = new String[inputSize + 1];
            int idx = 0;
            for (String feature : selectedFeatures) {
                if (!targetVariable.equals(feature)) {
                    featureNames[idx++] = feature;
                }
            }
            featureNames[inputSize] = "target";
            
            DataSplitUtil.SplitResult split = DataSplitUtil.split(filteredTrainingData, modelTraining);
            List<ProductionData> trainList = split.train();
            List<ProductionData> evalList = split.validation();
            
            double[][] trainData = buildDataArray(trainList, selectedFeatures, inputSize, targetVariable);
            DataFrame trainDf = buildDataFrame(trainData, featureNames);
            
            // 配置随机森林参数 - 优先从modelConfig获取，如果没有则使用算法默认值
            int numTrees = modelConfig.getTreeCount() != null ? modelConfig.getTreeCount() : 100; // 默认100棵树
            int maxDepth = modelConfig.getMaxDepth() != null ? modelConfig.getMaxDepth() : 20; // 默认最大深度20
            int mtry = modelConfig.getFeatureCount() != null ? modelConfig.getFeatureCount() : Math.max(1, (int) Math.sqrt(inputSize)); // 默认mtry为特征数的平方根
            int nodeSize = modelConfig.getNodeSize() != null ? modelConfig.getNodeSize() : 5;
            double subsample = modelConfig.getSubsample() != null ? modelConfig.getSubsample() : 1.0;

            if (numTrees < 1 || numTrees > 5000) {
                throw new TrainingException("随机森林的树数量不合法: " + numTrees);
            }
            if (maxDepth < 1 || maxDepth > 100) {
                throw new TrainingException("随机森林的最大深度不合法: " + maxDepth);
            }
            if (mtry < 1 || mtry > inputSize) {
                throw new TrainingException("随机森林的特征采样数不合法: " + mtry);
            }
            if (nodeSize < 1 || nodeSize > 100000) {
                throw new TrainingException("随机森林的叶子最小样本数不合法: " + nodeSize);
            }
            if (Double.isNaN(subsample) || Double.isInfinite(subsample) || subsample <= 0 || subsample > 1.0) {
                throw new TrainingException("随机森林的采样率不合法: " + subsample);
            }
            
            System.out.println("随机森林参数 - 树数量: " + numTrees + ", 最大深度: " + maxDepth + ", mtry: " + mtry);
            
            if (Thread.currentThread().isInterrupted()) {
                throw new TrainingException("训练已取消", new InterruptedException("训练已取消"));
            }
            Formula formula = Formula.lhs("target");
            RandomForest model;
            TrainingRuntimeGuards.acquireTreeModelPermit();
            try {
                model = RandomForest.fit(formula, trainDf, numTrees, mtry, maxDepth, 100, nodeSize, subsample);
            } finally {
                TrainingRuntimeGuards.releaseTreeModelPermit();
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new TrainingException("训练已取消", new InterruptedException("训练已取消"));
            }
            
            double[][] evalData = buildDataArray(evalList, selectedFeatures, inputSize, targetVariable);
            DataFrame evalDf = buildDataFrame(evalData, featureNames);
            double[] actuals = new double[evalList.size()];
            double[] predictions = new double[evalList.size()];
            for (int i = 0; i < evalList.size(); i++) {
                actuals[i] = evalData[i][inputSize];
                predictions[i] = model.predict(evalDf.get(i));
            }
            
            // 计算指标
            double[] metrics = calculateMetrics(actuals, predictions);
            double loss = metrics[0];
            double mae = metrics[1];
            double rmse = metrics[2];
            double rSquared = metrics[3];
            double r2Score = rSquared;
            
            System.out.println("训练完成 - R²: " + (r2Score * 100) + "%, MAE: " + mae + ", RMSE: " + rmse);
            
            String message = "随机森林训练成功";
            if (droppedTargets > 0) {
                message = message + " (丢弃目标缺失样本: " + droppedTargets + ")";
            }
            TrainingResult result = new TrainingResult(loss, r2Score, mae, rmse, true, message);
            result.setModel(model);
            result.setFeatures(selectedFeatures);
            return result;
            
        } catch (TrainingException e) {
            throw e;
        } catch (Exception e) {
            throw new TrainingException("随机森林训练失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public TrainingResult evaluate(List<ProductionData> evaluationData, ModelTraining modelTraining, 
                                  ModelConfig modelConfig, String[] selectedFeatures, Object model, Object preprocessor) {
        java.util.Arrays.sort(selectedFeatures);
        if (evaluationData == null || evaluationData.isEmpty()) {
            return new TrainingResult(0, 0, false, "评估数据为空");
        }
        if (!(model instanceof RandomForest rfModel)) {
            return new TrainingResult(0, 0, false, "模型未就绪");
        }
        String targetVariable = getTargetVariable(modelTraining);

        int droppedTargets = 0;
        List<ProductionData> filteredEvaluationData = new ArrayList<>(evaluationData.size());
        for (ProductionData pd : evaluationData) {
            Double y = getTargetValue(pd, targetVariable);
            if (y == null || Double.isNaN(y) || Double.isInfinite(y)) {
                droppedTargets++;
                continue;
            }
            filteredEvaluationData.add(pd);
        }
        if (filteredEvaluationData.isEmpty()) {
            return new TrainingResult(0, 0, false, "目标值缺失，无法评估: " + targetVariable);
        }

        int inputSize = 0;
        for (String feature : selectedFeatures) {
            if (!targetVariable.equals(feature)) {
                inputSize++;
            }
        }
        if (inputSize == 0) {
            return new TrainingResult(0, 0, false, "没有选择有效的输入特征");
        }
        String[] featureNames = new String[inputSize + 1];
        int idx = 0;
        for (String feature : selectedFeatures) {
            if (!targetVariable.equals(feature)) {
                featureNames[idx++] = feature;
            }
        }
        featureNames[inputSize] = "target";
        
        double[][] evalData = buildDataArray(filteredEvaluationData, selectedFeatures, inputSize, targetVariable);
        DataFrame evalDf = buildDataFrame(evalData, featureNames);
        double[] actuals = new double[filteredEvaluationData.size()];
        double[] predictions = new double[filteredEvaluationData.size()];
        for (int i = 0; i < filteredEvaluationData.size(); i++) {
            actuals[i] = evalData[i][inputSize];
            predictions[i] = rfModel.predict(evalDf.get(i));
        }
        
        double[] metrics = calculateMetrics(actuals, predictions);
        double loss = metrics[0];
        double mae = metrics[1];
        double rmse = metrics[2];
        double rSquared = metrics[3];
        double r2Score = rSquared;
        
        String message = "随机森林评估完成";
        if (droppedTargets > 0) {
            message = message + " (丢弃目标缺失样本: " + droppedTargets + ")";
        }
        TrainingResult result = new TrainingResult(loss, r2Score, mae, rmse, true, message);
        result.setModel(rfModel);
        result.setFeatures(selectedFeatures);

        int n = Math.min(actuals.length, predictions.length);
        List<Double> trueValues = new ArrayList<>(n);
        List<Double> predictedValues = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // 调试代码

            trueValues.add(actuals[i]);
            predictedValues.add(predictions[i]);
        }
        result.setTrueValues(trueValues);
        result.setPredictedValues(predictedValues);

        Map<String, Double> fiMap = new HashMap<>();
        try {
            double[] importance = rfModel.importance();
            int impIdx = 0;
            for (String feature : selectedFeatures) {
                if (!targetVariable.equals(feature) && impIdx < importance.length) {
                    fiMap.put(feature, importance[impIdx]);
                    impIdx++;
                }
            }
        } catch (Throwable ignored) {
        }
        result.setFeatureImportance(fiMap);

        return result;
    }
    
    private double[] calculateMetrics(double[] actual, double[] predicted) {
        int n = actual.length;
        double sumSE = 0, sumAE = 0, sumActual = 0;
        
        for (int i = 0; i < n; i++) {
            sumSE += Math.pow(actual[i] - predicted[i], 2);
            sumAE += Math.abs(actual[i] - predicted[i]);
            sumActual += actual[i];
        }
        
        double mse = sumSE / n;
        double mae = sumAE / n;
        double rmse = Math.sqrt(mse);
        
        double mean = sumActual / n;
        double sst = 0;
        for (int i = 0; i < n; i++) {
            sst += Math.pow(actual[i] - mean, 2);
        }
        double rSquared = sst > 0 ? 1 - (sumSE / sst) : 1.0;
        
        return new double[]{mse, mae, rmse, rSquared};
    }
    
    private double[][] buildDataArray(List<ProductionData> dataList, String[] selectedFeatures, int inputSize, String targetVariable) {
        double[][] data = new double[dataList.size()][inputSize + 1];
        for (int i = 0; i < dataList.size(); i++) {
            ProductionData pd = dataList.get(i);
            int col = 0;
            for (String feature : selectedFeatures) {
                if (!targetVariable.equals(feature)) {
                    data[i][col++] = getFeatureValue(pd, feature);
                }
            }
            Double y = getTargetValue(pd, targetVariable);
            if (y == null || Double.isNaN(y) || Double.isInfinite(y)) {
                throw new IllegalArgumentException("目标值缺失: " + targetVariable);
            }
            data[i][inputSize] = y;
        }
        return data;
    }
    
    private DataFrame buildDataFrame(double[][] data, String[] featureNames) {
        DoubleVector[] vectors = new DoubleVector[featureNames.length];
        for (int j = 0; j < featureNames.length; j++) {
            double[] colData = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                colData[i] = data[i][j];
            }
            vectors[j] = DoubleVector.of(featureNames[j], colData);
        }
        return DataFrame.of(vectors);
    }
    
    private double getFeatureValue(ProductionData data, String feature) {
        return switch (feature) {
            case "temperature" -> data.getTemperature() != null ? data.getTemperature() : 0;
            case "pressure" -> data.getPressure() != null ? data.getPressure() : 0;
            case "windVolume" -> data.getWindVolume() != null ? data.getWindVolume() : 0;
            case "coalInjection" -> data.getCoalInjection() != null ? data.getCoalInjection() : 0;
            case "materialHeight" -> data.getMaterialHeight() != null ? data.getMaterialHeight() : 0;
            case "gasFlow" -> data.getGasFlow() != null ? data.getGasFlow() : 0;
            case "oxygenLevel" -> data.getOxygenLevel() != null ? data.getOxygenLevel() : 0;
            case "energyConsumption" -> data.getEnergyConsumption() != null ? data.getEnergyConsumption() : 0;
            case "hotMetalTemperature" -> data.getHotMetalTemperature() != null ? data.getHotMetalTemperature() : 0;
            case "constantSignal" -> data.getConstantSignal() != null ? data.getConstantSignal() : 0;
            case "siliconContent" -> data.getSiliconContent() != null ? data.getSiliconContent() : 0;
            default -> throw new IllegalArgumentException("未知的特征名: " + feature);
        };
    }
    
    @Override
    public String getModelType() {
        return "random_forest";
    }

    @Override
    public byte[] serializeModel(Object model) throws Exception {
        if (model == null) {
            return null;
        }
        try {
            byte[] smileBytes = serializeWithSmile(model);
            if (smileBytes != null) {
                return smileBytes;
            }
        } catch (Exception ignored) {
        }
        return ModelTrainer.super.serializeModel(model);
    }

    @Override
    public Object deserializeModel(byte[] modelBytes) throws Exception {
        if (modelBytes == null || modelBytes.length == 0) {
            return null;
        }
        try {
            Object model = deserializeWithSmile(modelBytes, RandomForest.class);
            if (model != null) {
                return model;
            }
        } catch (Exception ignored) {
        }
        return ModelTrainer.super.deserializeModel(modelBytes);
    }

    private byte[] serializeWithSmile(Object model) throws Exception {
        Class<?> writeClass = Class.forName("smile.io.Write");
        java.lang.reflect.Method writeMethod = null;
        for (var method : writeClass.getMethods()) {
            if (!"object".equals(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (java.io.OutputStream.class.isAssignableFrom(paramTypes[1])) {
                writeMethod = method;
                break;
            }
        }
        if (writeMethod == null) {
            return null;
        }
        try (var bos = new ByteArrayOutputStream()) {
            writeMethod.invoke(null, model, bos);
            return bos.toByteArray();
        }
    }

    private Object deserializeWithSmile(byte[] modelBytes, Class<?> expectedClass) throws Exception {
        Class<?> readClass = Class.forName("smile.io.Read");
        java.lang.reflect.Method readMethod = null;
        boolean withClass = false;
        for (var method : readClass.getMethods()) {
            if (!"object".equals(method.getName())) {
                continue;
            }
            if (method.getParameterCount() == 1 && java.io.InputStream.class.isAssignableFrom(method.getParameterTypes()[0])) {
                readMethod = method;
                break;
            }
            if (method.getParameterCount() == 2
                    && java.io.InputStream.class.isAssignableFrom(method.getParameterTypes()[0])
                    && Class.class.isAssignableFrom(method.getParameterTypes()[1])) {
                readMethod = method;
                withClass = true;
                break;
            }
        }
        if (readMethod == null) {
            return null;
        }
        try (var bis = new ByteArrayInputStream(modelBytes)) {
            return withClass ? readMethod.invoke(null, bis, expectedClass) : readMethod.invoke(null, bis);
        }
    }
}
