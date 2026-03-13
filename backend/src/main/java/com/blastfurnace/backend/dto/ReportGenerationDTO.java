package com.blastfurnace.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReportGenerationDTO {
    private String furnaceId;
    private String startDate;      // 前端传 "yyyy-MM-dd HH:mm:ss"
    private String endDate;        // 前端传 "yyyy-MM-dd HH:mm:ss"
    private String reportType;     // "daily", "weekly", etc.
    private String format;         // "excel", "csv"
    private List<String> metrics;  // ["temperature", "pressure"]
    private String storagePath;    // 存储路径
    
    // --- 新增增强字段 ---
    private String timeGrain;      // "raw"(原始), "1h"(小时均), "1d"(日均)
    private String customFileName; // 自定义文件名前缀
    private Boolean overwrite;     // true=覆盖, false=重命名
    private Boolean includeCharts; // true=在Excel中多生成一个统计Sheet
    private Boolean includeTrendChart; // true=包含参数趋势图
    private Boolean includeDistributionChart; // true=包含参数分布图
}
