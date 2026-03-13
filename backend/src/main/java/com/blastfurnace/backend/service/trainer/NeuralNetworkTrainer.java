package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ProductionData;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.IUpdater;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * 神经网络训练器 - 使用Deeplearning4j实现
 * 支持所有激活函数：Sigmoid, ReLU, Tanh, Leaky ReLU
 */
public class NeuralNetworkTrainer implements ModelTrainer {
    
    @Override
    public TrainingResult train(List<ProductionData> trainingData, ModelTraining modelTraining,
                               ModelConfig modelConfig, String[] selectedFeatures,
                               Consumer<ModelTraining> progressCallback) {
        try {
            java.util.Arrays.sort(selectedFeatures);
            if (trainingData == null || trainingData.isEmpty()) {
                return new TrainingResult(0, 0, false, "训练数据为空");
            }
            
            System.out.println("========== 开始DL4J神经网络训练 ==========");
            System.out.println("训练数据量: " + trainingData.size());
            String targetVariable = getTargetVariable(modelTraining);

            int droppedTargets = 0;
            List<ProductionData> filteredTrainingData = new java.util.ArrayList<>(trainingData.size());
            for (ProductionData data : trainingData) {
                Double y = getTargetValue(data, targetVariable);
                if (y == null || Double.isNaN(y) || Double.isInfinite(y)) {
                    droppedTargets++;
                    continue;
                }
                filteredTrainingData.add(data);
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
            List<ProductionData> shuffled = split.ordered();
            
            org.deeplearning4j.nn.multilayer.MultiLayerNetwork model;
            DataSet trainData;
            DataSet testData;
            
            trainData = buildDataSet(split.train(), inputFeatureNames, targetVariable);
            testData = buildDataSet(split.validation(), inputFeatureNames, targetVariable);
            System.out.println("数据集划分完成 - 训练集: " + trainData.numExamples() + ", 验证集: " + testData.numExamples());
            
            NormalizerStandardize normalizer = new NormalizerStandardize();
            normalizer.fitLabel(true);
            normalizer.fit(trainData);
            normalizer.transform(trainData);
            normalizer.transform(testData);
            
            String activationName = modelConfig.getActivationFunction();
            Activation activation = getActivation(activationName);
            System.out.println("使用激活函数: " + activationName + " -> " + activation);
            
            WeightInit weightInit = getWeightInit(activationName);
            System.out.println("使用权重初始化: " + weightInit);
            
            LossFunctions.LossFunction lossFunction = getLossFunction(modelConfig.getLossFunction());

            double learningRate = modelTraining.getLearningRate() != null ? modelTraining.getLearningRate() : 0.001;
            if (Double.isNaN(learningRate) || Double.isInfinite(learningRate) || learningRate <= 0 || learningRate > 1.0) {
                throw new TrainingException("学习率不合法: " + learningRate);
            }
            String optimizerName = modelConfig.getOptimizer();
            IUpdater updater = getUpdater(optimizerName, learningRate);
            Double dropoutRateBoxed = modelConfig.getDropoutRate();
            double dropoutRate = dropoutRateBoxed != null ? dropoutRateBoxed : 0.0;
            dropoutRate = Math.max(0.0, Math.min(0.95, dropoutRate));

            Integer numLayersObj = modelConfig.getHiddenLayers();
            if (numLayersObj == null || numLayersObj < 1 || numLayersObj > 50) {
                throw new TrainingException("隐藏层数量不合法: " + numLayersObj);
            }
            int numLayers = numLayersObj;
            String neuronsPerLayerValue = modelConfig.getNeuronsPerLayer();
            if (neuronsPerLayerValue == null || neuronsPerLayerValue.isBlank()) {
                throw new TrainingException("每层神经元数为空");
            }
            String[] neuronsStr = neuronsPerLayerValue.split(",");
            if (neuronsStr.length != numLayers) {
                throw new TrainingException("每层神经元数数量与隐藏层数量不一致");
            }
            int[] hiddenSizes = new int[numLayers];
            for (int i = 0; i < numLayers; i++) {
                String token = neuronsStr[i] == null ? "" : neuronsStr[i].trim();
                int n;
                try {
                    n = Integer.parseInt(token);
                } catch (NumberFormatException e) {
                    throw new TrainingException("每层神经元数必须为正整数");
                }
                if (n < 1 || n > 100000) {
                    throw new TrainingException("每层神经元数不合法: " + n);
                }
                hiddenSizes[i] = n;
            }
            
            NeuralNetConfiguration.ListBuilder builder = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .weightInit(weightInit)
                    .updater(updater)
                    .list();
            
            int prevSize = inputSize;
            for (int i = 0; i < hiddenSizes.length; i++) {
                var layerBuilder = new DenseLayer.Builder()
                        .nIn(prevSize)
                        .nOut(hiddenSizes[i])
                        .activation(activation);
                if (dropoutRate > 0) {
                    layerBuilder.dropOut(dropoutRate);
                }
                builder.layer(i, layerBuilder.build());
                prevSize = hiddenSizes[i];
            }
            
            builder.layer(hiddenSizes.length, new OutputLayer.Builder(lossFunction)
                    .nIn(prevSize)
                    .nOut(1)
                    .activation(Activation.IDENTITY)
                    .build());
            
            MultiLayerConfiguration conf = builder.build();
            model = new MultiLayerNetwork(conf);
            model.init();
            
            Integer epochsObj = modelTraining.getEpochs();
            int epochs = epochsObj != null ? epochsObj : 0;
            if (epochs < 1 || epochs > 200000) {
                throw new TrainingException("训练轮数不合法: " + epochs);
            }
            Integer batchSizeObj = modelTraining.getBatchSize();
            int batchSize = batchSizeObj != null ? batchSizeObj : 0;
            if (batchSize < 1 || batchSize > 100000) {
                throw new TrainingException("批次大小不合法: " + batchSize);
            }
            if (batchSize > trainData.numExamples()) {
                batchSize = trainData.numExamples();
            }
            
            System.out.println(
                    "开始训练 - 轮数: " + epochs
                            + ", 批次大小: " + batchSize
                            + ", 学习率: " + learningRate
                            + ", 优化器: " + (optimizerName == null ? "adam" : optimizerName)
                            + ", Dropout: " + dropoutRate
            );
            
            boolean earlyStoppingEnabled = shuffled.size() >= 10
                    && trainData != null
                    && testData != null
                    && trainData.numExamples() > 0
                    && testData.numExamples() > 0
                    && epochs >= 2;
            int minEpochs = Math.min(epochs, 20);
            int patience = Math.min(epochs, 20);
            double bestValLoss = Double.POSITIVE_INFINITY;
            int bestEpoch = 0;
            INDArray bestParams = null;
            int epochsWithoutImprovement = 0;

            for (int i = 0; i < epochs; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new TrainingException("训练已取消", new InterruptedException("训练已取消"));
                }
                if (batchSize >= trainData.numExamples()) {
                    model.fit(trainData);
                } else {
                    for (int j = 0; j < trainData.numExamples(); j += batchSize) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new TrainingException("训练已取消", new InterruptedException("训练已取消"));
                        }
                        int end = Math.min(j + batchSize, trainData.numExamples());
                        DataSet batch;
                        try {
                            batch = (DataSet) trainData.getRange(j, end);
                        } catch (Exception e) {
                            throw new TrainingException("神经网络训练批次切片失败", e);
                        }
                        try {
                            model.fit(batch);
                        } catch (Exception e) {
                            throw new TrainingException("神经网络训练批次失败", e);
                        }
                        if (Thread.currentThread().isInterrupted()) {
                            throw new TrainingException("训练已取消", new InterruptedException("训练已取消"));
                        }
                    }
                }
                if (i == 0 || i == epochs - 1 || i % 10 == 0) {
                    int progress = (int) ((i + 1) * 100.0 / epochs);
                    modelTraining.setCurrentEpoch(i + 1);
                    modelTraining.setProgress(progress);
                    modelTraining.setTrainingLoss(model.score());
                    if (progressCallback != null) {
                        progressCallback.accept(modelTraining);
                    }
                }

                if (earlyStoppingEnabled) {
                    double valLoss = meanSquaredError(testData.getLabels(), model.output(testData.getFeatures()));
                    if (valLoss + 1e-12 < bestValLoss) {
                        bestValLoss = valLoss;
                        bestEpoch = i + 1;
                        bestParams = model.params().dup();
                        epochsWithoutImprovement = 0;
                    } else if (i + 1 >= minEpochs) {
                        epochsWithoutImprovement++;
                        if (epochsWithoutImprovement >= patience) {
                            break;
                        }
                    }
                }
            }
            if (bestParams != null) {
                model.setParams(bestParams);
            }
            
            INDArray predictions = model.output(testData.getFeatures());
            
            DataSet predSet = new DataSet(testData.getFeatures().dup(), predictions);
            DataSet actualSet = new DataSet(testData.getFeatures().dup(), testData.getLabels().dup());
            
            normalizer.revert(predSet);
            normalizer.revert(actualSet);
            
            double[] metrics = calculateMetrics(actualSet.getLabels(), predSet.getLabels());
            double mse = metrics[0];
            double mae = metrics[1];
            double rmse = metrics[2];
            double rSquared = metrics[3];
            double r2Score = rSquared;
            double trainLoss = model.score();
            
            System.out.println("训练完成 - R²: " + rSquared + " (Accuracy: " + (r2Score * 100) + "%), MAE: " + mae + ", RMSE: " + rmse);
            
            String message = "神经网络训练成功 (激活函数: " + modelConfig.getActivationFunction() + ")";
            if (droppedTargets > 0) {
                message = message + " (丢弃目标缺失样本: " + droppedTargets + ")";
            }
            if (earlyStoppingEnabled && bestEpoch > 0) {
                message = message + " (早停: bestEpoch=" + bestEpoch + ", valLoss=" + bestValLoss + ")";
            }
            message = message + " (mse=" + mse + ")";
            TrainingResult result = new TrainingResult(trainLoss, r2Score, mae, rmse, true, message);
            Map<String, Object> packedModel = new HashMap<>();
            packedModel.put("model", model);
            packedModel.put("normalizer", normalizer);
            result.setModel(packedModel);
            result.setPreprocessor(normalizer);
            result.setFeatures(selectedFeatures);
            return result;
            
        } catch (TrainingException e) {
            throw e;
        } catch (Exception e) {
            throw new TrainingException("神经网络训练失败: " + e.getMessage(), e);
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
        MultiLayerNetwork nnModel = null;
        NormalizerStandardize normalizer = null;
        if (model instanceof Map<?,?> m) {
            Object mModel = m.get("model");
            Object mNorm = m.get("normalizer");
            if (mModel instanceof MultiLayerNetwork) {
                nnModel = (MultiLayerNetwork) mModel;
            }
            if (mNorm instanceof NormalizerStandardize) {
                normalizer = (NormalizerStandardize) mNorm;
            }
            if (nnModel == null && preprocessor instanceof NormalizerStandardize) {
                normalizer = (NormalizerStandardize) preprocessor;
            }
        } else if (model instanceof MultiLayerNetwork) {
            nnModel = (MultiLayerNetwork) model;
            if (preprocessor instanceof NormalizerStandardize) {
                normalizer = (NormalizerStandardize) preprocessor;
            }
        }
        if (nnModel == null) {
            return new TrainingResult(0, 0, false, "模型未就绪");
        }
        if (normalizer == null) {
            return new TrainingResult(0, 0, false, "归一化器缺失");
        }
        String targetVariable = getTargetVariable(modelTraining);

        int droppedTargets = 0;
        List<ProductionData> filteredEvaluationData = new java.util.ArrayList<>(evaluationData.size());
        for (ProductionData data : evaluationData) {
            Double y = getTargetValue(data, targetVariable);
            if (y == null || Double.isNaN(y) || Double.isInfinite(y)) {
                droppedTargets++;
                continue;
            }
            filteredEvaluationData.add(data);
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

        double[][] features = new double[filteredEvaluationData.size()][inputSize];
        double[][] labels = new double[filteredEvaluationData.size()][1];
        for (int i = 0; i < filteredEvaluationData.size(); i++) {
            ProductionData data = filteredEvaluationData.get(i);
            int featureIndex = 0;
            for (String feature : inputFeatureNames) {
                features[i][featureIndex] = getFeatureValue(data, feature);
                featureIndex++;
            }
            Double y = getTargetValue(data, targetVariable);
            labels[i][0] = y;
        }
        
        INDArray inputArray = Nd4j.create(features);
        INDArray outputArray = Nd4j.create(labels);
        DataSet evalData = new DataSet(inputArray, outputArray);
        
        normalizer.transform(evalData);
        INDArray predictions = nnModel.output(evalData.getFeatures());
        
        DataSet predSet = new DataSet(evalData.getFeatures().dup(), predictions);
        DataSet actualSet = new DataSet(evalData.getFeatures().dup(), evalData.getLabels().dup());
        try {
            java.lang.reflect.Method revertLabels = normalizer.getClass().getMethod("revertLabels", org.nd4j.linalg.api.ndarray.INDArray.class);
            revertLabels.invoke(normalizer, predSet.getLabels());
            revertLabels.invoke(normalizer, actualSet.getLabels());
        } catch (Throwable t) {
            normalizer.revert(predSet);
            normalizer.revert(actualSet);
        }
        
        double[] metrics = calculateMetrics(actualSet.getLabels(), predSet.getLabels());
        double loss = metrics[0];
        double mae = metrics[1];
        double rmse = metrics[2];
        double rSquared = metrics[3];
        double r2Score = rSquared;
        
        String message = "神经网络评估完成";
        if (droppedTargets > 0) {
            message = message + " (丢弃目标缺失样本: " + droppedTargets + ")";
        }
        TrainingResult result = new TrainingResult(loss, r2Score, mae, rmse, true, message);
        result.setModel(nnModel);
        result.setPreprocessor(normalizer);
        result.setFeatures(selectedFeatures);

        // 填充详细评估数据用于前端绘图
        List<Double> trueValues = new java.util.ArrayList<>();
        List<Double> predictedValues = new java.util.ArrayList<>();
        INDArray actualLabels = actualSet.getLabels();
        INDArray predictedLabels = predSet.getLabels();
        for (int i = 0; i < actualLabels.length(); i++) {
            trueValues.add(actualLabels.getDouble(i, 0));
            predictedValues.add(predictedLabels.getDouble(i, 0));
        }
        result.setTrueValues(trueValues);
        result.setPredictedValues(predictedValues);

        java.util.Map<String, Double> featureImportance = new java.util.LinkedHashMap<>();
        double baselineR2 = rSquared;
        double[][] baseFeatures = features;
        double[][] baseLabels = labels;
        for (int featureIndex = 0; featureIndex < inputSize; featureIndex++) {
            double[][] permutedFeatures = new double[baseFeatures.length][inputSize];
            for (int i = 0; i < baseFeatures.length; i++) {
                System.arraycopy(baseFeatures[i], 0, permutedFeatures[i], 0, inputSize);
            }
            double[] columnValues = new double[baseFeatures.length];
            for (int i = 0; i < baseFeatures.length; i++) {
                columnValues[i] = permutedFeatures[i][featureIndex];
            }
            java.util.Random random = new java.util.Random(123 + featureIndex);
            for (int i = columnValues.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                double temp = columnValues[i];
                columnValues[i] = columnValues[j];
                columnValues[j] = temp;
            }
            for (int i = 0; i < baseFeatures.length; i++) {
                permutedFeatures[i][featureIndex] = columnValues[i];
            }

            DataSet permutedData = new DataSet(Nd4j.create(permutedFeatures), Nd4j.create(baseLabels));
            normalizer.transform(permutedData);
            INDArray permutedPredictions = nnModel.output(permutedData.getFeatures());
            DataSet permutedPredSet = new DataSet(permutedData.getFeatures().dup(), permutedPredictions);
            DataSet permutedActualSet = new DataSet(permutedData.getFeatures().dup(), permutedData.getLabels().dup());
            try {
                java.lang.reflect.Method revertLabels = normalizer.getClass().getMethod("revertLabels", org.nd4j.linalg.api.ndarray.INDArray.class);
                revertLabels.invoke(normalizer, permutedPredSet.getLabels());
                revertLabels.invoke(normalizer, permutedActualSet.getLabels());
            } catch (Throwable t) {
                normalizer.revert(permutedPredSet);
                normalizer.revert(permutedActualSet);
            }
            double[] permMetrics = calculateMetrics(permutedActualSet.getLabels(), permutedPredSet.getLabels());
            double permR2 = permMetrics[3];
            double importance = Math.max(0, baselineR2 - permR2);
            featureImportance.put(inputFeatureNames[featureIndex], importance);
        }
        result.setFeatureImportance(featureImportance);

        return result;
    }
    
    private double[] calculateMetrics(INDArray actual, INDArray predicted) {
        int n = (int) actual.length();
        double sumSE = 0, sumAE = 0, sumActual = 0;
        
        for (int i = 0; i < n; i++) {
            double a = actual.getDouble(i, 0);
            double p = predicted.getDouble(i, 0);
            sumSE += Math.pow(a - p, 2);
            sumAE += Math.abs(a - p);
            sumActual += a;
        }
        
        double mse = sumSE / n;
        double mae = sumAE / n;
        double rmse = Math.sqrt(mse);
        
        double mean = sumActual / n;
        double sst = 0;
        for (int i = 0; i < n; i++) {
            sst += Math.pow(actual.getDouble(i, 0) - mean, 2);
        }
        // 防止除以0，并确保 R² 在合理范围内
        double rSquared = sst > 1e-10 ? 1 - (sumSE / sst) : 0.0;
        
        return new double[]{mse, mae, rmse, rSquared};
    }

    private DataSet buildDataSet(List<ProductionData> dataList, String[] inputFeatureNames, String targetVariable) {
        int inputSize = inputFeatureNames.length;
        double[][] features = new double[dataList.size()][inputSize];
        double[][] labels = new double[dataList.size()][1];
        for (int i = 0; i < dataList.size(); i++) {
            ProductionData data = dataList.get(i);
            for (int j = 0; j < inputSize; j++) {
                features[i][j] = getFeatureValue(data, inputFeatureNames[j]);
            }
            Double y = getTargetValue(data, targetVariable);
            if (y == null || Double.isNaN(y) || Double.isInfinite(y)) {
                throw new IllegalArgumentException("目标值缺失: " + targetVariable);
            }
            labels[i][0] = y;
        }
        return new DataSet(Nd4j.create(features), Nd4j.create(labels));
    }

    private double meanSquaredError(INDArray labels, INDArray predictions) {
        INDArray diff = predictions.sub(labels);
        INDArray sq = diff.mul(diff);
        return sq.meanNumber().doubleValue();
    }
    
    private Activation getActivation(String name) {
        if (name == null) return Activation.SIGMOID;
        return switch (name.toLowerCase()) {
            case "relu" -> Activation.RELU;
            case "tanh" -> Activation.TANH;
            case "leaky_relu" -> Activation.LEAKYRELU;
            case "sigmoid" -> Activation.SIGMOID;
            default -> Activation.SIGMOID;
        };
    }

    private WeightInit getWeightInit(String activationName) {
        if (activationName == null) return WeightInit.XAVIER;
        return switch (activationName.toLowerCase()) {
            case "relu", "leaky_relu" -> WeightInit.RELU;
            case "tanh", "sigmoid" -> WeightInit.XAVIER;
            default -> WeightInit.XAVIER;
        };
    }
    
    private LossFunctions.LossFunction getLossFunction(String name) {
        if (name == null) return LossFunctions.LossFunction.MSE;
        switch (name.toLowerCase()) {
            case "mae": return LossFunctions.LossFunction.L1;
            default: return LossFunctions.LossFunction.MSE;
        }
    }

    private IUpdater getUpdater(String optimizer, double learningRate) {
        String opt = optimizer == null ? "" : optimizer.trim().toLowerCase();
        return switch (opt) {
            case "sgd" -> new Sgd(learningRate);
            case "nesterovs", "nesterov" -> new Nesterovs(learningRate, 0.9);
            case "rmsprop", "rms_prop" -> new RmsProp(learningRate);
            case "adam", "" -> new Adam(learningRate);
            default -> new Adam(learningRate);
        };
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
        return "neural_network";
    }

    @Override
    public byte[] serializeModel(Object model) throws Exception {
        if (model == null) {
            return null;
        }
        MultiLayerNetwork nnModel = null;
        NormalizerStandardize normalizer = null;
        if (model instanceof Map<?,?> m) {
            Object mModel = m.get("model");
            Object mNorm = m.get("normalizer");
            if (mModel instanceof MultiLayerNetwork) {
                nnModel = (MultiLayerNetwork) mModel;
            }
            if (mNorm instanceof NormalizerStandardize) {
                normalizer = (NormalizerStandardize) mNorm;
            }
        } else if (model instanceof MultiLayerNetwork) {
            nnModel = (MultiLayerNetwork) model;
        }
        if (nnModel == null) {
            return ModelTrainer.super.serializeModel(model);
        }
        try (var bos = new ByteArrayOutputStream(); var oos = new ObjectOutputStream(bos)) {
            byte[] netBytes;
            try (var nbos = new ByteArrayOutputStream()) {
                ModelSerializer.writeModel(nnModel, nbos, true);
                netBytes = nbos.toByteArray();
            }
            oos.writeObject(netBytes);
            try {
                oos.writeObject(normalizer);
            } catch (Exception e) {
                oos.writeObject(null);
            }
            oos.flush();
            return bos.toByteArray();
        }
    }

    @Override
    public Object deserializeModel(byte[] modelBytes) throws Exception {
        if (modelBytes == null || modelBytes.length == 0) {
            return null;
        }
        MultiLayerNetwork nnModel = null;
        NormalizerStandardize normalizer = null;
        try (var bis = new ByteArrayInputStream(modelBytes); var ois = new ObjectInputStream(bis)) {
            Object first = ois.readObject();
            if (first instanceof byte[] netBytes) {
                try (var nbis = new ByteArrayInputStream(netBytes)) {
                    nnModel = ModelSerializer.restoreMultiLayerNetwork(nbis, true);
                }
            } else if (first instanceof MultiLayerNetwork) {
                nnModel = (MultiLayerNetwork) first;
            } else {
                try (var mbis = new ByteArrayInputStream(modelBytes)) {
                    nnModel = ModelSerializer.restoreMultiLayerNetwork(mbis, true);
                }
            }
            try {
                Object n = ois.readObject();
                if (n instanceof NormalizerStandardize) {
                    normalizer = (NormalizerStandardize) n;
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            try (var mbis = new ByteArrayInputStream(modelBytes)) {
                nnModel = ModelSerializer.restoreMultiLayerNetwork(mbis, true);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("model", nnModel);
        result.put("normalizer", normalizer);
        return result;
    }

    @Override
    public byte[] serializePreprocessor(Object preprocessor) throws Exception {
        if (preprocessor == null) {
            return null;
        }
        if (!(preprocessor instanceof NormalizerStandardize normalizer)) {
            return ModelTrainer.super.serializePreprocessor(preprocessor);
        }
        try (var bos = new ByteArrayOutputStream()) {
            Class<?> serializerClass = Class.forName("org.nd4j.linalg.dataset.api.preprocessor.serializer.NormalizerSerializer");
            Object serializer = serializerClass.getMethod("getDefault").invoke(null);
            java.lang.reflect.Method writeMethod = null;
            for (var method : serializerClass.getMethods()) {
                if (!"write".equals(method.getName()) || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] paramTypes = method.getParameterTypes();
                if (java.io.OutputStream.class.isAssignableFrom(paramTypes[1]) && paramTypes[0].isInstance(normalizer)) {
                    writeMethod = method;
                    break;
                }
            }
            if (writeMethod == null) {
                throw new NoSuchMethodException("NormalizerSerializer.write");
            }
            writeMethod.invoke(serializer, normalizer, bos);
            return bos.toByteArray();
        } catch (ReflectiveOperationException ignored) {
            return ModelTrainer.super.serializePreprocessor(preprocessor);
        }
    }

    @Override
    public Object deserializePreprocessor(byte[] preprocessorBytes) throws Exception {
        if (preprocessorBytes == null || preprocessorBytes.length == 0) {
            return null;
        }
        try (var bis = new ByteArrayInputStream(preprocessorBytes)) {
            Class<?> serializerClass = Class.forName("org.nd4j.linalg.dataset.api.preprocessor.serializer.NormalizerSerializer");
            Object serializer = serializerClass.getMethod("getDefault").invoke(null);
            java.lang.reflect.Method restoreMethod = null;
            for (var method : serializerClass.getMethods()) {
                if (!"restore".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?>[] paramTypes = method.getParameterTypes();
                if (java.io.InputStream.class.isAssignableFrom(paramTypes[0])) {
                    restoreMethod = method;
                    break;
                }
            }
            if (restoreMethod == null) {
                throw new NoSuchMethodException("NormalizerSerializer.restore");
            }
            return restoreMethod.invoke(serializer, bis);
        } catch (ReflectiveOperationException ignored) {
            return ModelTrainer.super.deserializePreprocessor(preprocessorBytes);
        }
    }
}
