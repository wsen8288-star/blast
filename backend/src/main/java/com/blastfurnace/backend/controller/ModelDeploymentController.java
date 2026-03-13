package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.dto.DeploymentHistoryDTO;
import com.blastfurnace.backend.dto.DeploymentRequest;
import com.blastfurnace.backend.dto.DeploymentResponse;
import com.blastfurnace.backend.dto.ServiceInfoDTO;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.ModelDeploymentService;
import com.blastfurnace.backend.service.OperationControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/optimization/model/deployment")
@RequiredArgsConstructor
public class ModelDeploymentController {

    private final ModelDeploymentService modelDeploymentService;
    private final OperationControlService operationControlService;

    @PostMapping("/deploy")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:deploy:execute')")
    public Result<DeploymentResponse> deploy(
            @RequestBody DeploymentRequest request
    ) {
        OperationControlService.Permit permit = null;
        try {
            permit = operationControlService.enterDeployment();
            String operator = getOperator();
            DeploymentResponse response = modelDeploymentService.deploy(request, operator);
            return Result.success(response, "部署成功");
        } catch (Exception e) {
            return Result.error("部署失败: " + e.getMessage());
        } finally {
            operationControlService.exit(permit);
        }
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<List<DeploymentHistoryDTO>> getHistory() {
        try {
            return Result.success(modelDeploymentService.getHistory(), "获取部署历史成功");
        } catch (Exception e) {
            return Result.error("获取部署历史失败: " + e.getMessage());
        }
    }

    @GetMapping("/services")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<List<ServiceInfoDTO>> getServices() {
        try {
            return Result.success(modelDeploymentService.getServices(), "获取服务列表成功");
        } catch (Exception e) {
            return Result.error("获取服务列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/service/{id}/stop")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:write')")
    public Result<String> stopService(
            @PathVariable Long id
    ) {
        try {
            String operator = getOperator();
            boolean stopped = modelDeploymentService.stopService(id, operator);
            if (!stopped) {
                return Result.error("服务不存在");
            }
            return Result.successMsg("服务已停止");
        } catch (Exception e) {
            return Result.error("停止服务失败: " + e.getMessage());
        }
    }

    @PostMapping("/service/{id}/start")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:write')")
    public Result<ServiceInfoDTO> startService(
            @PathVariable Long id
    ) {
        try {
            String operator = getOperator();
            return Result.success(modelDeploymentService.startService(id, operator), "服务已启动");
        } catch (Exception e) {
            return Result.error("启动服务失败: " + e.getMessage());
        }
    }

    @PostMapping("/service/{id}/restart")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:write')")
    public Result<ServiceInfoDTO> restartService(
            @PathVariable Long id
    ) {
        try {
            String operator = getOperator();
            return Result.success(modelDeploymentService.restartService(id, operator), "服务已重启");
        } catch (Exception e) {
            return Result.error("重启服务失败: " + e.getMessage());
        }
    }

    @GetMapping("/service/{id}/logs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<List<String>> getServiceLogs(@PathVariable Long id) {
        try {
            return Result.success(modelDeploymentService.getServiceLogs(id), "获取服务日志成功");
        } catch (Exception e) {
            return Result.error("获取服务日志失败: " + e.getMessage());
        }
    }

    @GetMapping("/service/{id}/health")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<Map<String, Object>> getServiceHealth(@PathVariable Long id) {
        try {
            return Result.success(modelDeploymentService.checkServiceHealth(id), "健康检查完成");
        } catch (Exception e) {
            return Result.error("健康检查失败: " + e.getMessage());
        }
    }

    @GetMapping("/service/{id}/config")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<Map<String, Object>> getServiceConfig(@PathVariable Long id) {
        try {
            return Result.success(modelDeploymentService.getServiceConfig(id), "获取服务配置成功");
        } catch (Exception e) {
            return Result.error("获取服务配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/service/{id}/name")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:write')")
    public Result<ServiceInfoDTO> updateServiceName(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        try {
            String operator = getOperator();
            String newName = body.get("name");
            return Result.success(modelDeploymentService.updateServiceName(id, newName, operator), "服务名称已更新");
        } catch (Exception e) {
            return Result.error("更新服务名称失败: " + e.getMessage());
        }
    }

    @PostMapping("/service/{id}/config")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:write')")
    public Result<Map<String, Object>> updateServiceConfig(
            @PathVariable Long id,
            @RequestBody Map<String, Object> config
    ) {
        try {
            String operator = getOperator();
            return Result.success(modelDeploymentService.updateServiceConfig(id, config, operator), "更新服务配置成功");
        } catch (Exception e) {
            return Result.error("更新服务配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:write')")
    public Result<Map<String, Object>> cancelDeployment(
            @PathVariable Long id
    ) {
        try {
            String operator = getOperator();
            return Result.success(modelDeploymentService.cancelDeployment(id, operator), "部署已取消");
        } catch (Exception e) {
            return Result.error("取消部署失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('optimize:deploy:execute')")
    public Result<DeploymentResponse> retryDeployment(
            @PathVariable Long id
    ) {
        OperationControlService.Permit permit = null;
        try {
            permit = operationControlService.enterDeployment();
            String operator = getOperator();
            return Result.success(modelDeploymentService.retryDeployment(id, operator), "部署已重试");
        } catch (Exception e) {
            return Result.error("重试部署失败: " + e.getMessage());
        } finally {
            operationControlService.exit(permit);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('optimize:write')")
    public Result<Void> deleteDeployment(
            @PathVariable Long id
    ) {
        try {
            String operator = getOperator();
            modelDeploymentService.deleteDeploymentHistory(id, operator);
            return Result.success(null, "删除部署历史成功");
        } catch (Exception e) {
            return Result.error("删除部署历史失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('optimize:write')")
    public Result<Void> deleteDeploymentBatch(
            @RequestBody List<Long> ids
    ) {
        try {
            String operator = getOperator();
            modelDeploymentService.deleteDeploymentHistoryBatch(ids, operator);
            return Result.success(null, "批量删除部署历史成功");
        } catch (Exception e) {
            return Result.error("批量删除部署历史失败: " + e.getMessage());
        }
    }

    @PostMapping("/predict/{serviceId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<Map<String, Object>> predict(@PathVariable Long serviceId, @RequestBody Map<String, Object> input) {
        try {
            return Result.success(modelDeploymentService.predict(serviceId, input), "预测成功");
        } catch (Exception e) {
            return Result.error("预测失败: " + e.getMessage());
        }
    }

    @PostMapping("/predict/{serviceId}/explain")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('optimize:read')")
    public Result<Map<String, Object>> explain(@PathVariable Long serviceId, @RequestBody Map<String, Object> input) {
        try {
            return Result.success(modelDeploymentService.explainPrediction(serviceId, input), "解释性分析完成");
        } catch (Exception e) {
            return Result.error("解释性分析失败: " + e.getMessage());
        }
    }

    private String getOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "unknown";
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name;
    }
}
