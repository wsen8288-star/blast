package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import smile.math.kernel.GaussianKernel;
import smile.regression.GaussianProcessRegression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class GaussianProcessRegressionTrainer implements ModelTrainer {

    @Override
    public TrainingResult train(List<ProductionData> trainingData, ModelTraining modelTraining,
                                ModelConfig modelConfig, String[] selectedFeatures,
                                Consumer<ModelTraining> progressCallback) {
        try {
            java.util.Arrays.sort(selectedFeatures);
            if (trainingData == null || trainingData.isEmpty()) {
                return new TrainingResult(0, 0, false, "训练数据为空");
            }

            System.out.println("========== 开始Smile高斯过程回归训练 ==========");
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

            int inputSize = 0;
            for (String feature : selectedFeatures) {
                if (!targetVariable.equals(feature)) {
                    inputSize++;
                }
            }

            if (inputSize == 0) {
                return new TrainingResult(0, 0, false, "没有选择有效的输入特征");
            }

            String[] inputFeatureNames = new String[inputSize];
            int inputIndex = 0;
            for (String feature : selectedFeatures) {
                if (!targetVariable.equals(feature)) {
                    inputFeatureNames[inputIndex++] = feature;
                }
            }

            DataSplitUtil.SplitResult split = DataSplitUtil.split(filteredTrainingData, modelTraining);
            List<ProductionData> trainList = split.train();
            List<ProductionData> testList = split.validation();
            int trainSize = trainList.size();
            int testSize = testList.size();

            double[][] trainX = new double[trainSize][inputSize];
            double[] trainY = new double[trainSize];
            double[][] testX = new double[testSize][inputSize];
            double[] testY = new double[testSize];

            for (int i = 0; i < trainSize; i++) {
                ProductionData pd = trainList.get(i);
                int col = 0;
                for (String feature : inputFeatureNames) {
                    trainX[i][col++] = getFeatureValue(pd, feature);
                }
                Double y = getTargetValue(pd, targetVariable);
                if (y == null || Double.isNaN(y) || Double.isInfinite(y)) {
                    throw new TrainingException("目标值缺失: " + targetVariable);
                }
                trainY[i] = y;
            }

            for (int i = 0; i < testSize; i++) {
                ProductionData pd = testList.get(i);
                int col = 0;
                for (String feature : inputFeatureNames) {
                    testX[i][col++] = getFeatureValue(pd, feature);
                }
                Double y = getTargetValue(pd, targetVariable);
                if (y == null || Double.isNaN(y) || Double.isInfinite(y)) {
                    throw new TrainingException("目标值缺失: " + targetVariable);
                }
                testY[i] = y;
            }

            double[] xMean = new double[inputSize];
            double[] xStd = new double[inputSize];
            for (int j = 0; j < inputSize; j++) {
                double sum = 0, sumSq = 0;
                for (int i = 0; i < trainSize; i++) {
                    sum += trainX[i][j];
                    sumSq += trainX[i][j] * trainX[i][j];
                }
                xMean[j] = sum / trainSize;
                xStd[j] = Math.sqrt(sumSq / trainSize - xMean[j] * xMean[j]);
                if (xStd[j] < 1e-10) {
                    System.err.println("特征 [" + inputFeatureNames[j] + "] 方差为0，已忽略其标准化影响");
                    xStd[j] = 1;
                }
            }

            double yMean = 0;
            double ySumSq = 0;
            for (double v : trainY) {
                yMean += v;
                ySumSq += v * v;
            }
            yMean /= trainSize;
            double yStd = Math.sqrt(ySumSq / trainSize - yMean * yMean);
            if (yStd < 1e-10) yStd = 1;

            double[][] trainXnorm = new double[trainSize][inputSize];
            for (int i = 0; i < trainSize; i++) {
                for (int j = 0; j < inputSize; j++) {
                    trainXnorm[i][j] = (trainX[i][j] - xMean[j]) / xStd[j];
                }
            }

            double[] trainYnorm = new double[trainSize];
            for (int i = 0; i < trainSize; i++) {
                trainYnorm[i] = (trainY[i] - yMean) / yStd;
            }

            double[][] testXnorm = new double[testSize][inputSize];
            for (int i = 0; i < testSize; i++) {
                for (int j = 0; j < inputSize; j++) {
                    testXnorm[i][j] = (testX[i][j] - xMean[j]) / xStd[j];
                }
            }

            Integer epochsObj = modelTraining.getEpochs();
            if (epochsObj != null && epochsObj < 1) {
                throw new TrainingException("迭代次数不合法: " + epochsObj);
            }
            int epochs = epochsObj != null ? epochsObj : 1;
            Integer batchSizeObj = modelTraining.getBatchSize();
            if (batchSizeObj != null && batchSizeObj < 1) {
                throw new TrainingException("批次大小不合法: " + batchSizeObj);
            }
            int batchSize = batchSizeObj != null ? batchSizeObj : trainSize;
            int effectiveBatchSize = Math.min(batchSize, trainSize);

            double lengthScale = modelConfig.getGprLengthScale() != null ? modelConfig.getGprLengthScale() : 1.0;
            lengthScale = Math.max(0.1, Math.min(lengthScale, 10.0));

            double noise = 0.1;
            if (modelConfig.getGprNoiseVariance() != null) {
                noise = modelConfig.getGprNoiseVariance();
            }
            if (Double.isNaN(noise) || Double.isInfinite(noise) || noise <= 0 || noise > 10.0) {
                throw new TrainingException("噪声水平不合法: " + noise);
            }

            System.out.println("高斯核参数 - lengthScale: " + lengthScale + ", noise: " + noise);
            System.out.println("训练配置 - 迭代次数: " + epochs + ", 批次大小: " + effectiveBatchSize);

            GaussianKernel kernel = new GaussianKernel(lengthScale);

            double[][] evalX = testSize > 0 ? testXnorm : trainXnorm;
            double[] evalY = testSize > 0 ? testY : trainY;

            List<GaussianProcessRegression<double[]>> models = new ArrayList<>();
            List<Integer> trainIndices = new ArrayList<>(trainSize);
            for (int i = 0; i < trainSize; i++) {
                trainIndices.add(i);
            }
            java.util.Collections.shuffle(trainIndices, new java.util.Random(split.seed()));
            int cursor = 0;
            for (int t = 0; t < epochs; t++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new TrainingException("训练已取消", new InterruptedException("训练已取消"));
                }
                double[][] batchX = new double[effectiveBatchSize][inputSize];
                double[] batchY = new double[effectiveBatchSize];
                for (int i = 0; i < effectiveBatchSize; i++) {
                    int idx = trainIndices.get((cursor + i) % trainSize);
                    batchX[i] = trainXnorm[idx];
                    batchY[i] = trainYnorm[idx];
                }
                cursor = (cursor + effectiveBatchSize) % trainSize;

                GaussianProcessRegression<double[]> model = GaussianProcessRegression.fit(batchX, batchY, kernel, noise);
                if (Thread.currentThread().isInterrupted()) {
                    throw new TrainingException("训练已取消", new InterruptedException("训练已取消"));
                }
                models.add(model);
                if (t == 0 || t == epochs - 1 || t % 5 == 0) {
                    int progress = (int) ((t + 1) * 100.0 / epochs);
                    modelTraining.setCurrentEpoch(t + 1);
                    modelTraining.setProgress(progress);
                    if (progressCallback != null) {
                        progressCallback.accept(modelTraining);
                    }
                }
            }

            double[] predictions = predict(models, evalX, yStd, yMean);

            double[] metrics = calculateMetrics(evalY, predictions);
            double loss = metrics[0];
            double mae = metrics[1];
            double rmse = metrics[2];
            double rSquared = metrics[3];
            double r2Score = rSquared;

            System.out.println("训练完成 - R²: " + (r2Score * 100) + "%, MAE: " + mae + ", RMSE: " + rmse);

            GprPreprocessor preprocessor = new GprPreprocessor(xMean, xStd, yMean, yStd, inputSize);
            Map<String, Object> packedModel = new HashMap<>();
            packedModel.put("model", models);
            packedModel.put("preprocessor", preprocessor);
            String message = "高斯过程回归训练成功";
            if (droppedTargets > 0) {
                message = message + " (丢弃目标缺失样本: " + droppedTargets + ")";
            }
            TrainingResult result = new TrainingResult(loss, r2Score, mae, rmse, true, message);
            result.setModel(packedModel);
            result.setPreprocessor(preprocessor);
            result.setFeatures(selectedFeatures);
            int m = Math.min(evalY.length, predictions.length);
            List<Double> trueValues = new ArrayList<>(m);
            List<Double> predictedValues = new ArrayList<>(m);
            for (int i = 0; i < m; i++) {
                trueValues.add(evalY[i]);
                predictedValues.add(predictions[i]);
            }
            result.setTrueValues(trueValues);
            result.setPredictedValues(predictedValues);
            return result;

        } catch (TrainingException e) {
            throw e;
        } catch (Exception e) {
            throw new TrainingException("高斯过程回归训练失败: " + e.getMessage(), e);
        }
    }

    @Override
    public TrainingResult train(List<ProductionData> trainingData, ModelTraining modelTraining,
                                ModelConfig modelConfig, String[] selectedFeatures) {
        return train(trainingData, modelTraining, modelConfig, selectedFeatures, null);
    }

    @Override
    public TrainingResult evaluate(List<ProductionData> evaluationData, ModelTraining modelTraining,
                                   ModelConfig modelConfig, String[] selectedFeatures, Object model, Object preprocessor) {
        java.util.Arrays.sort(selectedFeatures);
        if (evaluationData == null || evaluationData.isEmpty()) {
            return new TrainingResult(0, 0, false, "评估数据为空");
        }
        if (!(model instanceof Map<?, ?> packed)) {
            return new TrainingResult(0, 0, false, "模型未就绪");
        }
        Object modelObj = packed.get("model");
        Object prepObj = packed.get("preprocessor");
        if (!(modelObj instanceof List<?> models)) {
            return new TrainingResult(0, 0, false, "模型未就绪");
        }
        if (!(prepObj instanceof GprPreprocessor p)) {
            return new TrainingResult(0, 0, false, "预处理器未就绪");
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

        String[] inputFeatureNames = new String[inputSize];
        int inputIndex = 0;
        for (String feature : selectedFeatures) {
            if (!targetVariable.equals(feature)) {
                inputFeatureNames[inputIndex++] = feature;
            }
        }

        int n = filteredEvaluationData.size();
        double[][] evalX = new double[n][inputSize];
        double[] evalY = new double[n];
        for (int i = 0; i < n; i++) {
            ProductionData pd = filteredEvaluationData.get(i);
            int col = 0;
            for (String feature : inputFeatureNames) {
                evalX[i][col++] = getFeatureValue(pd, feature);
            }
            Double y = getTargetValue(pd, targetVariable);
            evalY[i] = y;
        }

        double[][] evalXnorm = new double[n][inputSize];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < inputSize; j++) {
                evalXnorm[i][j] = (evalX[i][j] - p.xMean[j]) / p.xStd[j];
            }
        }

        @SuppressWarnings("unchecked")
        List<GaussianProcessRegression<double[]>> castedModels = (List<GaussianProcessRegression<double[]>>) models;
        double[] predictions = predict(castedModels, evalXnorm, p.yStd, p.yMean);

        double[] metrics = calculateMetrics(evalY, predictions);
        double loss = metrics[0];
        double mae = metrics[1];
        double rmse = metrics[2];
        double rSquared = metrics[3];
        double r2Score = rSquared;

        String message = "高斯过程回归评估成功";
        if (droppedTargets > 0) {
            message = message + " (丢弃目标缺失样本: " + droppedTargets + ")";
        }
        TrainingResult result = new TrainingResult(loss, r2Score, mae, rmse, true, message);
        result.setModel(model);
        result.setPreprocessor(preprocessor);
        result.setFeatures(selectedFeatures);
        int m = Math.min(evalY.length, predictions.length);
        List<Double> trueValues = new ArrayList<>(m);
        List<Double> predictedValues = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            trueValues.add(evalY[i]);
            predictedValues.add(predictions[i]);
        }
        result.setTrueValues(trueValues);
        result.setPredictedValues(predictedValues);
        return result;
    }

    private double[] calculateMetrics(double[] actual, double[] predicted) {
        int n = actual.length;
        double sumSE = 0;
        double sumAE = 0;
        double sumSq = 0;
        double mean = 0;
        for (double v : actual) {
            mean += v;
        }
        mean /= n;
        for (int i = 0; i < n; i++) {
            double error = actual[i] - predicted[i];
            sumSE += error * error;
            sumAE += Math.abs(error);
            sumSq += error * error;
        }
        double mse = sumSE / n;
        double mae = sumAE / n;
        double rmse = Math.sqrt(sumSq / n);

        double sst = 0;
        for (int i = 0; i < n; i++) {
            sst += Math.pow(actual[i] - mean, 2);
        }
        double rSquared = sst > 0 ? 1 - (sumSE / sst) : 1.0;

        return new double[]{mse, mae, rmse, rSquared};
    }

    private double[] predict(List<GaussianProcessRegression<double[]>> models, double[][] features, double yStd, double yMean) {
        double[] predictions = new double[features.length];
        for (GaussianProcessRegression<double[]> gpr : models) {
            for (int i = 0; i < features.length; i++) {
                double predNorm = gpr.predict(features[i]);
                predictions[i] += predNorm * yStd + yMean;
            }
        }
        int modelCount = models.size();
        if (modelCount > 0) {
            for (int i = 0; i < predictions.length; i++) {
                predictions[i] /= modelCount;
            }
        }
        return predictions;
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

    private static class GprPreprocessor implements Serializable {
        private static final long serialVersionUID = 1L;
        private final double[] xMean;
        private final double[] xStd;
        private final double yMean;
        private final double yStd;
        private final int inputSize;

        private GprPreprocessor(double[] xMean, double[] xStd, double yMean, double yStd, int inputSize) {
            this.xMean = xMean;
            this.xStd = xStd;
            this.yMean = yMean;
            this.yStd = yStd;
            this.inputSize = inputSize;
        }
    }

    @Override
    public String getModelType() {
        return "gpr";
    }

    @Override
    public byte[] serializeModel(Object model) throws Exception {
        if (model == null) {
            return null;
        }
        List<GaussianProcessRegression<double[]>> models = null;
        GprPreprocessor preprocessor = null;
        if (model instanceof Map<?, ?> map) {
            Object mModel = map.get("model");
            Object mPreprocessor = map.get("preprocessor");
            if (mModel instanceof List) {
                @SuppressWarnings("unchecked")
                List<GaussianProcessRegression<double[]>> casted = (List<GaussianProcessRegression<double[]>>) mModel;
                models = casted;
            }
            if (mPreprocessor instanceof GprPreprocessor) {
                preprocessor = (GprPreprocessor) mPreprocessor;
            }
        } else if (model instanceof List) {
            @SuppressWarnings("unchecked")
            List<GaussianProcessRegression<double[]>> casted = (List<GaussianProcessRegression<double[]>>) model;
            models = casted;
        }
        if (models == null) {
            return ModelTrainer.super.serializeModel(model);
        }
        try (var bos = new ByteArrayOutputStream(); var oos = new ObjectOutputStream(bos)) {
            oos.writeObject(models);
            oos.writeObject(preprocessor);
            oos.flush();
            return bos.toByteArray();
        }
    }

    @Override
    public Object deserializeModel(byte[] modelBytes) throws Exception {
        if (modelBytes == null || modelBytes.length == 0) {
            return null;
        }
        try (var bis = new ByteArrayInputStream(modelBytes); var ois = new ObjectInputStream(bis)) {
            Object models = ois.readObject();
            Object preprocessor = ois.readObject();
            Map<String, Object> packedModel = new HashMap<>();
            packedModel.put("model", models);
            packedModel.put("preprocessor", preprocessor);
            return packedModel;
        }
    }
}
