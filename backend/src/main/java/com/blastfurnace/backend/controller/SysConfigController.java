package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.annotation.LogAction;
import com.blastfurnace.backend.model.SysConfig;
import com.blastfurnace.backend.model.SysConfigGroup;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.repository.SysConfigRepository;
import com.blastfurnace.backend.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigRepository sysConfigRepository;
    private final SysConfigService sysConfigService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:config:read')")
    public Result<List<SysConfig>> list(@RequestParam String group) {
        try {
            SysConfigGroup g = SysConfigGroup.valueOf(group);
            return Result.success(sysConfigService.listByGroup(g));
        } catch (IllegalArgumentException e) {
            return Result.error("configGroup无效");
        } catch (Exception e) {
            return Result.error("获取系统配置失败: " + e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:config:write')")
    @LogAction(module = "系统管理", value = "创建系统配置")
    public Result<SysConfig> create(@RequestBody SysConfig config) {
        try {
            if (config == null) {
                return Result.error("配置不能为空");
            }
            if (config.getConfigKey() == null || config.getConfigKey().isBlank()) {
                return Result.error("configKey不能为空");
            }
            String key = config.getConfigKey().trim();
            if (sysConfigRepository.existsByConfigKey(key)) {
                return Result.error("configKey已存在");
            }
            config.setId(null);
            config.setConfigKey(key);
            SysConfig saved = sysConfigService.save(config);
            return Result.success(saved, "创建配置成功");
        } catch (Exception e) {
            return Result.error("创建配置失败: " + e.getMessage());
        }
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:config:write')")
    @LogAction(module = "系统管理", value = "批量更新系统配置")
    public Result<List<SysConfig>> batchUpdate(@RequestBody BatchUpdateRequest request) {
        try {
            if (request == null || request.group() == null) {
                return Result.error("configGroup不能为空");
            }
            List<SysConfigService.BatchItem> items = (request.items() == null ? List.<SysConfigItem>of() : request.items())
                    .stream()
                    .map(item -> item == null
                            ? null
                            : new SysConfigService.BatchItem(
                                    item.configKey(),
                                    item.configValue(),
                                    item.configName(),
                                    item.description()
                            ))
                    .toList();
            List<SysConfig> saved = sysConfigService.batchUpsertByGroup(request.group(), items);
            return Result.success(saved, "批量更新成功");
        } catch (Exception e) {
            return Result.error("批量更新失败: " + e.getMessage());
        }
    }

    public record SysConfigItem(String configKey, String configValue, String configName, String description) {}

    public record BatchUpdateRequest(SysConfigGroup group, List<SysConfigItem> items) {}
}
