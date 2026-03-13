package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.SysConfig;
import com.blastfurnace.backend.model.SysConfigGroup;
import com.blastfurnace.backend.repository.SysConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigRepository sysConfigRepository;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadCache() {
        initDefaultConfigs();
        refreshAll();
    }

    private void initDefaultConfigs() {
        ensureDefaultConfig(
                "system_name",
                "高炉生产参数优化系统",
                "系统名称",
                SysConfigGroup.SYSTEM_CONFIG,
                "用于页面展示的系统名称"
        );
        ensureDefaultConfig(
                "system_default_furnace_id",
                "BF-001",
                "默认高炉编号",
                SysConfigGroup.SYSTEM_CONFIG,
                "未指定高炉时使用的默认值"
        );
        ensureDefaultConfig(
                "system_timezone",
                "Asia/Shanghai",
                "系统时区",
                SysConfigGroup.SYSTEM_CONFIG,
                "用于时间展示与统计对齐"
        );
        ensureDefaultConfig(
                "system_ui_refresh_seconds",
                "10",
                "前端默认刷新间隔(秒)",
                SysConfigGroup.SYSTEM_CONFIG,
                "用于前端轮询/刷新默认值"
        );
        ensureDefaultConfig(
                "monitor_strict_live_enable",
                "true",
                "实时监控严格模式",
                SysConfigGroup.SYSTEM_CONFIG,
                "开启后无实时数据不回退模拟数据"
        );
        ensureDefaultConfig(
                "monitor_live_fail_threshold",
                "3",
                "实时链路失败阈值",
                SysConfigGroup.SYSTEM_CONFIG,
                "连续失败达到阈值后进入冻结与回退决策"
        );
        ensureDefaultConfig(
                "monitor_anomaly_sync_delay_ms",
                "300",
                "异常事件补拉延迟(毫秒)",
                SysConfigGroup.SYSTEM_CONFIG,
                "收到异常事件后延迟补拉异常列表的毫秒数"
        );

        ensureDefaultConfig(
                "evo_max_iterations",
                "30",
                "进化计算最大代数",
                SysConfigGroup.ALGO_CONFIG,
                "限制进化算法的最高迭代次数"
        );
        ensureDefaultConfig(
                "evo_max_population",
                "40",
                "最大种群规模上限",
                SysConfigGroup.ALGO_CONFIG,
                "限制种群规模上限"
        );
        ensureDefaultConfig(
                "evo_weight_balanced_production",
                "0.6",
                "平衡模式产量权重",
                SysConfigGroup.ALGO_CONFIG,
                "演化计算平衡模式下产量评分权重"
        );
        ensureDefaultConfig(
                "evo_weight_balanced_energy",
                "0.4",
                "平衡模式能耗权重",
                SysConfigGroup.ALGO_CONFIG,
                "演化计算平衡模式下能耗评分权重"
        );
        ensureDefaultConfig(
                "evo_weight_high_yield_production",
                "0.9",
                "高产模式产量权重",
                SysConfigGroup.ALGO_CONFIG,
                "演化计算高产模式下产量评分权重"
        );
        ensureDefaultConfig(
                "evo_weight_high_yield_energy",
                "0.1",
                "高产模式能耗权重",
                SysConfigGroup.ALGO_CONFIG,
                "演化计算高产模式下能耗评分权重"
        );
        ensureDefaultConfig(
                "evo_weight_low_energy_production",
                "0.2",
                "低耗模式产量权重",
                SysConfigGroup.ALGO_CONFIG,
                "演化计算低耗模式下产量评分权重"
        );
        ensureDefaultConfig(
                "evo_weight_low_energy_energy",
                "0.8",
                "低耗模式能耗权重",
                SysConfigGroup.ALGO_CONFIG,
                "演化计算低耗模式下能耗评分权重"
        );
        ensureDefaultConfig(
                "evo_cost_energy_weight",
                "0.7",
                "成本函数能耗权重",
                SysConfigGroup.ALGO_CONFIG,
                "演化成本函数中能耗项权重"
        );
        ensureDefaultConfig(
                "evo_cost_oxygen_weight",
                "0.3",
                "成本函数富氧权重",
                SysConfigGroup.ALGO_CONFIG,
                "演化成本函数中富氧项权重"
        );
        ensureDefaultConfig(
                "evo_cost_oxygen_base",
                "21.0",
                "富氧基准",
                SysConfigGroup.ALGO_CONFIG,
                "成本函数中富氧成本起算基准"
        );
        ensureDefaultConfig(
                "evo_cost_oxygen_factor",
                "20.0",
                "富氧成本系数",
                SysConfigGroup.ALGO_CONFIG,
                "每1单位富氧增量对应的成本系数"
        );
        ensureDefaultConfig(
                "evo_cost_oxygen_max_delta",
                "5.0",
                "富氧最大增量",
                SysConfigGroup.ALGO_CONFIG,
                "成本范围估算使用的富氧最大增量"
        );
        ensureDefaultConfig(
                "data_retention_days",
                "90",
                "生产数据保留天数",
                SysConfigGroup.STORAGE_CONFIG,
                "超过此天数的热数据将被定时清理(0为不清理)"
        );
        ensureDefaultConfig(
                "system_log_retention_days",
                "30",
                "系统日志保留天数",
                SysConfigGroup.STORAGE_CONFIG,
                "超过此天数的系统日志将被定时清理(0为不清理)"
        );
        ensureDefaultConfig(
                "warning_retention_days",
                "30",
                "预警数据保留天数",
                SysConfigGroup.STORAGE_CONFIG,
                "超过此天数的已处理预警数据将被定时清理(最少1天)"
        );
        ensureDefaultConfig(
                "alarm_push_interval",
                "0",
                "告警推送间隔(秒)",
                SysConfigGroup.ALARM_CONFIG,
                "相同告警在规定时间内不重复推送"
        );
        ensureDefaultConfig(
                "alarm_global_enable",
                "true",
                "开启全局系统告警",
                SysConfigGroup.ALARM_CONFIG,
                "关闭后将停止WebSocket异常推送"
        );
    }

    private void ensureDefaultConfig(
            String key,
            String defaultValue,
            String name,
            SysConfigGroup group,
            String description
    ) {
        if (key == null || key.isBlank()) {
            return;
        }
        String k = key.trim();
        if (sysConfigRepository.existsByConfigKey(k)) {
            return;
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(k);
        config.setConfigValue(defaultValue);
        config.setConfigName(name);
        config.setConfigGroup(group);
        config.setDescription(description);
        sysConfigRepository.save(config);
    }

    public void refreshAll() {
        cache.clear();
        for (SysConfig config : sysConfigRepository.findAll()) {
            if (config.getConfigKey() == null) continue;
            cache.put(config.getConfigKey(), config.getConfigValue());
        }
    }

    public Map<String, String> snapshot() {
        return Collections.unmodifiableMap(cache);
    }

    public Optional<SysConfig> findEntityByKey(String key) {
        return sysConfigRepository.findByConfigKey(key);
    }

    public List<SysConfig> listByGroup(SysConfigGroup group) {
        return sysConfigRepository.findByConfigGroupOrderByConfigKeyAsc(group);
    }

    public SysConfig save(SysConfig config) {
        SysConfig saved = sysConfigRepository.save(config);
        if (saved.getConfigKey() != null) {
            cache.put(saved.getConfigKey(), saved.getConfigValue());
        }
        return saved;
    }

    @Transactional
    public List<SysConfig> batchUpsertByGroup(SysConfigGroup group, List<BatchItem> items) {
        if (group == null) {
            throw new IllegalArgumentException("configGroup不能为空");
        }
        List<SysConfig> saved = new java.util.ArrayList<>();
        List<BatchItem> source = items == null ? List.of() : items;
        for (BatchItem item : source) {
            if (item == null || item.configKey() == null || item.configKey().isBlank()) {
                continue;
            }
            String key = item.configKey().trim();
            SysConfig config = sysConfigRepository.findByConfigKey(key).orElseGet(SysConfig::new);
            config.setConfigKey(key);
            config.setConfigGroup(group);
            if (item.configName() != null) {
                config.setConfigName(item.configName());
            }
            if (item.description() != null) {
                config.setDescription(item.description());
            }
            if (item.configValue() != null) {
                config.setConfigValue(item.configValue());
            }
            saved.add(sysConfigRepository.save(config));
        }
        refreshAll();
        return saved;
    }

    public String getString(String key, String defaultValue) {
        String value = cache.get(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public int getInt(String key, int defaultValue) {
        String value = cache.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String value = cache.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = cache.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String v = value.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v) || "on".equals(v)) {
            return true;
        }
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "n".equals(v) || "off".equals(v)) {
            return false;
        }
        return defaultValue;
    }

    public record BatchItem(String configKey, String configValue, String configName, String description) {}
}
