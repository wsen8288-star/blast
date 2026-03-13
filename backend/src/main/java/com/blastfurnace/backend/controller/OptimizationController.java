package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.dto.EvaluationResultVO;
import com.blastfurnace.backend.dto.EvolutionResult;
import com.blastfurnace.backend.dto.EvolutionaryOptimizationRequest;
import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelEvaluation;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.TrainingLog;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.EvolutionaryOptimizationService;
import com.blastfurnace.backend.service.ModelEvaluationService;
import com.blastfurnace.backend.service.OperationControlService;
import com.blastfurnace.backend.service.ModelTrainingService;
import com.blastfurnace.backend.service.trainer.TrainingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/optimization")
@RequiredArgsConstructor
public class OptimizationController {
    
    private final ModelTrainingService modelTrainingService;
    private final ModelEvaluationService modelEvaluationService;
    private final EvolutionaryOptimizationService evolutionaryOptimizationService;
    private final OperationControlService operationControlService;
    
    @PostMapping("/start")
    public ResponseEntity<?> startOptimization(@RequestBody Object params) {
        try {
            // 处理开始优化逻辑
            System.out.println("开始优化，参数: " + params);
            // 这里可以添加优化任务创建、启动等逻辑
            return ResponseEntity.ok("开始优化成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("开始优化失败: " + e.getMessage());
        }
    }

    @PostMapping("/evolution/start")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:evolution:execute')")
    public Result<EvolutionResult> startEvolutionaryOptimization(@RequestBody EvolutionaryOptimizationRequest request) {
        OperationControlService.Permit permit = null;
        try {
            permit = operationControlService.enterEvolution();
            EvolutionResult result = evolutionaryOptimizationService.startEvolutionaryOptimization(request);
            return Result.success(result, "演化优化任务已提交");
        } catch (Exception e) {
            return Result.error("演化优化失败: " + e.getMessage());
        } finally {
            operationControlService.exit(permit);
        }
    }
    
    @GetMapping("/progress/{taskId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:evolution:execute')")
    public Result<EvolutionResult> getOptimizationProgress(@PathVariable String taskId) {
        try {
            EvolutionResult result = evolutionaryOptimizationService.getOptimizationProgress(taskId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取优化进度失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/result/{taskId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:evolution:execute')")
    public Result<EvolutionResult> getOptimizationResult(@PathVariable String taskId) {
        try {
            EvolutionResult result = evolutionaryOptimizationService.getOptimizationResult(taskId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取优化结果失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/validate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<EvaluationResultVO> validateModel(@RequestBody EvaluationRequest request) {
        try {
            Optional<ModelTraining> training = modelTrainingService.getTrainingStatus(request.getTrainingId());
            if (training.isEmpty()) {
                return Result.error("训练任务不存在");
            }
            String selectedFeatures = training.get().getSelectedFeatures();
            EvaluationResultVO evaluation = modelEvaluationService.evaluate(training.get(), request.getDataSource(), selectedFeatures);
            return Result.success(evaluation, "模型评估成功");
        } catch (Exception e) {
            return Result.error("模型评估失败: " + e.getMessage());
        }
    }

    @GetMapping("/evaluation/history")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<List<ModelEvaluation>> getEvaluationHistory() {
        try {
            List<ModelEvaluation> history = modelEvaluationService.getHistory();
            return Result.success(history);
        } catch (Exception e) {
            return Result.error("获取评估历史失败: " + e.getMessage());
        }
    }

    @GetMapping("/evaluation/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<ModelEvaluation> getEvaluationDetail(@PathVariable Long id) {
        try {
            return modelEvaluationService.getById(id)
                    .map(Result::success)
                    .orElseGet(() -> Result.error("评估记录不存在"));
        } catch (Exception e) {
            return Result.error("获取评估详情失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/evaluation/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('optimize:write')")
    public Result<Void> deleteEvaluation(@PathVariable Long id) {
        try {
            modelEvaluationService.delete(id);
            return Result.success(null, "删除成功");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/evaluation/batch")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('optimize:write')")
    public Result<Void> deleteEvaluationBatch(@RequestBody List<Long> ids) {
        try {
            modelEvaluationService.deleteBatch(ids);
            return Result.success(null, "批量删除成功");
        } catch (Exception e) {
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/save")
    public ResponseEntity<?> saveOptimizationResult(@RequestBody Object data) {
        try {
            // 处理保存优化方案逻辑
            System.out.println("保存优化方案，数据: " + data);
            // 这里可以添加优化方案保存等逻辑
            return ResponseEntity.ok("保存优化方案成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("保存优化方案失败: " + e.getMessage());
        }
    }
    
    // 模型训练相关接口
    @PostMapping("/model/train")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:train:execute')")
    public Result<ModelTraining> startModelTraining(@RequestBody TrainingRequest request) {
        OperationControlService.Permit permit = null;
        try {
            permit = operationControlService.enterTraining();
            if (request == null || request.getTraining() == null || request.getConfig() == null) {
                return Result.error("训练请求参数不完整");
            }
            if (request.getTraining().getModelType() == null || request.getTraining().getModelType().isBlank()) {
                return Result.error("模型类型为空");
            }
            ModelTraining result = modelTrainingService.startTraining(request.getTraining(), request.getConfig());
            return Result.success(result, "模型训练已开始");
        } catch (TrainingException e) {
            return Result.error("模型训练启动失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.error("模型训练启动失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.error("模型训练启动失败: " + e.getMessage());
        } finally {
            operationControlService.exit(permit);
        }
    }
    
    @GetMapping("/model/training/{trainingId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<ModelTraining> getTrainingStatus(@PathVariable Long trainingId) {
        try {
            Optional<ModelTraining> training = modelTrainingService.getTrainingStatus(trainingId);
            return training.map(Result::success).orElseGet(() -> Result.error("训练任务不存在"));
        } catch (Exception e) {
            return Result.error("获取训练状态失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/model/training/{trainingId}/logs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<List<TrainingLog>> getTrainingLogs(@PathVariable Long trainingId) {
        try {
            List<TrainingLog> logs = modelTrainingService.getTrainingLogs(trainingId);
            return Result.success(logs);
        } catch (Exception e) {
            return Result.error("获取训练日志失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/model/config")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('optimize:write')")
    public Result<ModelConfig> saveModelConfig(@RequestBody ModelConfig modelConfig) {
        try {
            ModelConfig savedConfig = modelTrainingService.saveModelConfig(modelConfig);
            return Result.success(savedConfig, "模型配置保存成功");
        } catch (Exception e) {
            return Result.error("模型配置保存失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/model/config/{configId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<ModelConfig> getModelConfig(@PathVariable Long configId) {
        try {
            Optional<ModelConfig> modelConfig = modelTrainingService.getModelConfig(configId);
            return modelConfig.map(Result::success).orElseGet(() -> Result.error("模型配置不存在"));
        } catch (Exception e) {
            return Result.error("获取模型配置失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/model/configs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<List<ModelConfig>> getAllModelConfigs() {
        try {
            List<ModelConfig> configs = modelTrainingService.getAllModelConfigs();
            return Result.success(configs);
        } catch (Exception e) {
            return Result.error("获取模型配置列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/model/training-history")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<List<ModelTraining>> getTrainingHistory() {
        try {
            List<ModelTraining> history = modelTrainingService.getTrainingHistory();
            return Result.success(history);
        } catch (Exception e) {
            return Result.error("获取训练历史失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/model/training/{trainingId}/cancel")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:write')")
    public Result<Boolean> cancelTraining(@PathVariable Long trainingId) {
        try {
            boolean success = modelTrainingService.cancelTraining(trainingId);
            return success ? Result.success(true, "训练任务已取消") : Result.error("取消训练任务失败");
        } catch (Exception e) {
            return Result.error("取消训练任务失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/model/training/{trainingId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('optimize:write')")
    public Result<Boolean> deleteTrainingHistory(@PathVariable Long trainingId) {
        try {
            boolean success = modelTrainingService.deleteTrainingHistory(trainingId);
            return success ? Result.success(true, "训练历史删除成功") : Result.error("训练历史删除失败");
        } catch (Exception e) {
            return Result.error("训练历史删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/model/training/batch")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('optimize:write')")
    public Result<Boolean> deleteTrainingHistoryBatch(@RequestBody List<Long> trainingIds) {
        try {
            boolean success = modelTrainingService.deleteTrainingHistoryBatch(trainingIds);
            return success ? Result.success(true, "批量删除训练历史成功") : Result.error("批量删除训练历史失败");
        } catch (Exception e) {
            return Result.error("批量删除训练历史失败: " + e.getMessage());
        }
    }

    @GetMapping("/model/export/{trainingId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:read')")
    public Result<Map<String, Object>> exportModel(@PathVariable Long trainingId) {
        try {
            Map<String, Object> exportData = modelTrainingService.exportModel(trainingId);
            if (exportData != null) {
                return Result.success(exportData, "模型导出成功");
            } else {
                return Result.error("模型不存在");
            }
        } catch (Exception e) {
            return Result.error("模型导出失败: " + e.getMessage());
        }
    }
    
    // 内部静态类，用于接收训练请求参数
    public static class TrainingRequest {
        private ModelTraining training;
        private ModelConfig config;
        
        // getter and setter
        public ModelTraining getTraining() {
            return training;
        }
        
        public void setTraining(ModelTraining training) {
            this.training = training;
        }
        
        public ModelConfig getConfig() {
            return config;
        }
        
        public void setConfig(ModelConfig config) {
            this.config = config;
        }
    }

    public static class EvaluationRequest {
        private Long trainingId;
        private String dataSource;

        public Long getTrainingId() {
            return trainingId;
        }

        public void setTrainingId(Long trainingId) {
            this.trainingId = trainingId;
        }

        public String getDataSource() {
            return dataSource;
        }

        public void setDataSource(String dataSource) {
            this.dataSource = dataSource;
        }
    }
}
