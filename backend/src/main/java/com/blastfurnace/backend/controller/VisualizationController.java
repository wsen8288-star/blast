package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.dto.ExcelExportVO;
import com.blastfurnace.backend.dto.ReportGenerationDTO;
import com.blastfurnace.backend.dto.ReportGenerationResponseDTO;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.ReportGenerationService;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.blastfurnace.backend.model.ReportHistory; // 新增
import com.blastfurnace.backend.repository.ReportHistoryRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/visualization")
public class VisualizationController {
    private static final Logger log = LoggerFactory.getLogger(VisualizationController.class);
    
    @Autowired
    private ProductionDataRepository productionDataRepository;
    
    @Autowired
    private ReportHistoryRepository reportHistoryRepository;

    @Autowired
    private ReportGenerationService reportGenerationService;

    @GetMapping("/single")
    public ResponseEntity<?> getSingleMetricData(@RequestParam(required = false) String furnaceId,
                                                @RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate,
                                                @RequestParam(required = false) String metric) {
        try {
            // 处理获取单指标趋势数据逻辑
            System.out.println("获取单指标趋势数据，参数: furnaceId=" + furnaceId + ", startDate=" + startDate + ", endDate=" + endDate + ", metric=" + metric);
            
            // 解析日期字符串为Date对象
            Date start = null;
            Date end = null;
            try {
                if (startDate != null) {
                    // 尝试解析多种日期格式
                    start = parseDate(startDate);
                    System.out.println("解析开始日期成功: " + start);
                }
                if (endDate != null) {
                    // 尝试解析多种日期格式
                    end = parseDate(endDate);
                    System.out.println("解析结束日期成功: " + end);
                }
            } catch (Exception e) {
                System.err.println("日期解析失败: " + e.getMessage());
            }
            
            // 根据furnaceId和日期范围查询数据
            List<ProductionData> dataList;
            if (furnaceId != null && start != null && end != null) {
                dataList = productionDataRepository.findByFurnaceIdAndTimestampBetween(furnaceId, start, end);
            } else if (furnaceId != null) {
                dataList = productionDataRepository.findByFurnaceId(furnaceId);
            } else if (start != null && end != null) {
                dataList = productionDataRepository.findByTimestampBetween(start, end);
            } else {
                dataList = productionDataRepository.findAll();
            }
            
            // 转换数据格式，只返回需要的指标
            List<Map<String, Object>> result = dataList.stream()
                .map(data -> {
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("timestamp", data.getTimestamp());
                    
                    // 根据指标类型返回对应的数据
                    switch (metric != null ? metric : "temperature") {
                        case "temperature":
                            item.put("value", data.getTemperature());
                            break;
                        case "pressure":
                            item.put("value", data.getPressure());
                            break;
                        case "materialHeight":
                        case "materialLevel":
                            item.put("value", data.getMaterialHeight());
                            break;
                        case "gasFlow":
                        case "gasComposition":
                            item.put("value", data.getGasFlow());
                            break;
                        case "oxygenLevel":
                            item.put("value", data.getOxygenLevel());
                            break;
                        case "productionRate":
                            item.put("value", data.getProductionRate());
                            break;
                        case "energyConsumption":
                            item.put("value", data.getEnergyConsumption());
                            break;
                        
                        case "hotMetalTemperature":
                            item.put("value", data.getHotMetalTemperature());
                            break;
                        case "constantSignal":
                            item.put("value", data.getConstantSignal());
                            break;
                        default:
                            item.put("value", data.getTemperature());
                    }
                    
                    return item;
                })
                .collect(Collectors.toList());
            
            // 返回与前端期望格式匹配的响应
            return ResponseEntity.ok(Map.of("code", 200, "data", result, "message", "获取单指标趋势数据成功"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("code", 500, "message", "获取单指标趋势数据失败: " + e.getMessage()));
        }
    }
    
    @GetMapping("/multi")
    public ResponseEntity<?> getMultiMetricData(@RequestParam(required = false) String furnaceId,
                                                @RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate) {
        try {
            // 处理获取多指标关联数据逻辑
            System.out.println("获取多指标关联数据，参数: furnaceId=" + furnaceId + ", startDate=" + startDate + ", endDate=" + endDate);
            
            List<ProductionData> dataList;
            if (furnaceId != null) {
                dataList = productionDataRepository.findByFurnaceId(furnaceId);
            } else {
                dataList = productionDataRepository.findAll();
            }
            
            // 返回与前端期望格式匹配的响应
            return ResponseEntity.ok(Map.of("code", 200, "data", dataList, "message", "获取多指标关联数据成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 500, "message", "获取多指标关联数据失败: " + e.getMessage()));
        }
    }
    
    @GetMapping("/comparison")
    public ResponseEntity<?> getComparisonData(@RequestParam(required = false) String furnaceId1,
                                              @RequestParam(required = false) String furnaceId2,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate) {
        try {
            // 处理获取工况对比数据逻辑
            System.out.println("获取工况对比数据，参数: furnaceId1=" + furnaceId1 + ", furnaceId2=" + furnaceId2 + ", startDate=" + startDate + ", endDate=" + endDate);
            
            // 获取两个炉号的数据进行对比
            List<ProductionData> dataList1 = furnaceId1 != null ? productionDataRepository.findByFurnaceId(furnaceId1) : productionDataRepository.findAll();
            List<ProductionData> dataList2 = furnaceId2 != null ? productionDataRepository.findByFurnaceId(furnaceId2) : productionDataRepository.findAll();
            
            // 构建对比结果
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("furnace1", dataList1);
            result.put("furnace2", dataList2);
            
            // 返回与前端期望格式匹配的响应
            return ResponseEntity.ok(Map.of("code", 200, "data", result, "message", "获取工况对比数据成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 500, "message", "获取工况对比数据失败: " + e.getMessage()));
        }
    }
    
    @GetMapping("/furnaces")
    public ResponseEntity<?> getFurnaceList() {
        try {
            // 处理获取高炉列表逻辑
            System.out.println("获取高炉列表");
            
            // 返回高炉ID列表 (String List)，与前端预期一致
            List<String> furnaceList = List.of("BF-001", "BF-002", "BF-003");
            
            // 返回与前端期望格式匹配的响应
            return ResponseEntity.ok(Map.of("code", 200, "data", furnaceList, "message", "获取高炉列表成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 500, "message", "获取高炉列表失败: " + e.getMessage()));
        }
    }
    
@PostMapping("/report/generate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasAuthority('visualization:report:generate')")
    public Result<ReportGenerationResponseDTO> generateReport(@RequestBody ReportGenerationDTO request) {
        try {
            ReportGenerationResponseDTO response = reportGenerationService.generateReport(request);
            log.info("报表生成成功: {}", response.getFileName());
            return Result.success(response, "报表生成成功");

        } catch (Exception e) {
            log.error("生成报表失败", e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    // --- [新增辅助方法] 样式装饰 ---
    private void decorateHeader(Workbook wb, Cell cell) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        cell.setCellStyle(style);
    }

    // --- [新增辅助方法] 指标名转换 ---
    private String getMetricName(String metric) {
        switch (metric) {
            case "temperature": return "温度(°C)";
            case "pressure": return "压力(kPa)";
            case "materialHeight": return "料面高度(m)";
            case "gasFlow": return "煤气流量";
            case "oxygenLevel": return "氧气浓度";
            case "productionRate": return "生产速率";
            case "energyConsumption": return "能耗";
            case "airFlow": return "风量";
            case "coalFlow": return "煤量";
            case "hotMetalTemperature": return "铁水温度(°C)";
            case "constantSignal": return "恒定信号";
            default: return metric;
        }
    }

    // --- [新增辅助方法] 动态取值 ---
    private Double getMetricValue(ExcelExportVO vo, String metric) {
        switch (metric) {
            case "temperature": return vo.getTemperature();
            case "pressure": return vo.getPressure();
            case "materialHeight": return vo.getMaterialHeight();
            case "gasFlow": return vo.getGasFlow();
            case "oxygenLevel": return vo.getOxygenLevel();
            case "productionRate": return vo.getProductionRate();
            case "energyConsumption": return vo.getEnergyConsumption();
            case "airFlow": return vo.getAirFlow();
            case "coalFlow": return vo.getCoalFlow();
            case "hotMetalTemperature": return vo.getHotMetalTemperature();
            case "constantSignal": return vo.getConstantSignal();
            default: return 0.0;
        }
    }
    // 辅助方法：根据指标名称获取 Excel 列索引 (与 headers 数组对应)
    private int getColumnIndexByMetric(String metric) {
        switch (metric) {
            case "temperature": return 1;
            case "pressure": return 2;
            case "materialHeight": return 3;
            case "gasFlow": return 4;
            case "oxygenLevel": return 5;
            case "productionRate": return 6;
            case "energyConsumption": return 7;
            case "airFlow": return 8;
            case "coalFlow": return 9;
            default: return 1; // 默认温度
        }
    }

    // 辅助方法：处理 null 值
    private double val(Double d) {
        return d == null ? 0.0 : d;
    }
    /**
     * 将ProductionData转换为ExcelExportVO，为所有15个字段赋值
     */
    private ExcelExportVO convertToVO(ProductionData data, SimpleDateFormat sdf) {
        ExcelExportVO vo = new ExcelExportVO();
        
        // 时间字段格式化
        vo.setTimestamp(sdf.format(data.getTimestamp()));
        
        // 基础数值字段
        vo.setTemperature(data.getTemperature());
        vo.setPressure(data.getPressure());
        vo.setMaterialHeight(data.getMaterialHeight());
        vo.setGasFlow(data.getGasFlow());
        vo.setOxygenLevel(data.getOxygenLevel());
        vo.setProductionRate(data.getProductionRate());
        vo.setEnergyConsumption(data.getEnergyConsumption());
        vo.setHotMetalTemperature(data.getHotMetalTemperature());
        vo.setConstantSignal(data.getConstantSignal());
        
        // 衍生字段处理：airFlow取值于gasFlow，coalFlow取值于productionRate
        vo.setAirFlow(data.getGasFlow());
        vo.setCoalFlow(data.getProductionRate());
        
        // 字符串字段
        vo.setFurnaceId(data.getFurnaceId());
        vo.setStatus(data.getStatus());
        vo.setOperator(data.getOperator());
        
        return vo;
    }

    /**
     * 计算聚合数据，数值字段计算平均值（保留2位小数），字符串字段取首个
     */
    private ExcelExportVO calculateAverageVO(String timeLabel, List<ProductionData> list) {
        ExcelExportVO vo = new ExcelExportVO();
        vo.setTimestamp(timeLabel);
        
        // 辅助方法：计算平均值并保留2位小数
        vo.setTemperature(avg(list, d -> d.getTemperature()));
        vo.setPressure(avg(list, d -> d.getPressure()));
        vo.setMaterialHeight(avg(list, d -> d.getMaterialHeight()));
        vo.setGasFlow(avg(list, d -> d.getGasFlow()));
        vo.setOxygenLevel(avg(list, d -> d.getOxygenLevel()));
        vo.setProductionRate(avg(list, d -> d.getProductionRate()));
        vo.setEnergyConsumption(avg(list, d -> d.getEnergyConsumption()));
        vo.setHotMetalTemperature(avg(list, d -> d.getHotMetalTemperature()));
        vo.setConstantSignal(avg(list, d -> d.getConstantSignal()));
        
        // 衍生字段计算平均值
        vo.setAirFlow(avg(list, d -> d.getGasFlow()));
        vo.setCoalFlow(avg(list, d -> d.getProductionRate()));
        
        // 字符串字段取列表第一条数据的对应值
        if (!list.isEmpty()) {
            ProductionData firstData = list.get(0);
            vo.setFurnaceId(firstData.getFurnaceId());
            vo.setStatus(firstData.getStatus());
            vo.setOperator(firstData.getOperator());
        }
        
        return vo;
    }

    // 辅助方法：计算平均值并保留2位小数
    private Double avg(List<ProductionData> list, java.util.function.ToDoubleFunction<ProductionData> mapper) {
        if (list == null || list.isEmpty()) return null;
        
        OptionalDouble average = list.stream()
                .filter(d -> d != null)
                .mapToDouble(mapper)
                .average();
                
        if (average.isPresent()) {
            double val = average.getAsDouble();
            return Math.round(val * 100.0) / 100.0;
        } else {
            return null;
        }
    }
    
    // 辅助方法：解析多种日期格式
    private Date parseDate(String dateStr) throws Exception {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        // 常见的日期格式
        String[] patterns = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSX", // ISO 8601 with milliseconds and timezone
            "yyyy-MM-dd'T'HH:mm:ssX",     // ISO 8601 with timezone
            "yyyy-MM-dd HH:mm:ss",        // Standard SQL
            "yyyy-MM-dd",                 // Date only
            "yyyy/MM/dd HH:mm:ss",        // Slash separator
            "yyyy/MM/dd"                  // Slash date only
        };
        
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                return sdf.parse(dateStr);
            } catch (Exception e) {
                // Continue to next pattern
            }
        }
        
        // Try Instant.parse as a fallback for strict ISO strings
        try {
            return Date.from(Instant.parse(dateStr));
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析日期格式: " + dateStr);
        }
    }
    // --- [修改点 3] 修复 calculateDistribution 复用取值逻辑 ---
    private Map<String, Double> calculateDistribution(List<ExcelExportVO> dataList, String metric) {
        Map<String, Double> result = new LinkedHashMap<>();
        try {
            List<Double> values = new ArrayList<>();
            for (ExcelExportVO vo : dataList) {
                // 使用新的辅助方法获取值，不再需要写一遍 switch-case
                Double val = getMetricValue(vo, metric); 
                if (val != null) values.add(val);
            }

            if (values.isEmpty()) return result;

            double min = values.stream().mapToDouble(v -> v).min().orElse(0);
            double max = values.stream().mapToDouble(v -> v).max().orElse(100);
            
            // 避免最大最小值相等导致除以0
            if (Math.abs(max - min) < 1e-6) {
                max = min + 10.0;
            }
            
            double step = (max - min) / 10.0;
            // 避免步长过小
            if (step < 1e-6) step = 1.0;
            
            // 格式化 step 避免浮点数精度问题
            step = Math.round(step * 100.0) / 100.0;

            // 初始化所有区间
            for (int i = 0; i < 10; i++) {
                double start = min + i * step;
                double end = start + step;
                // 使用 %.1f 格式化
                String key = String.format("%.1f-%.1f", start, end);
                result.put(key, 0.0);
            }

            // 填充数据
            for (Double v : values) {
                int index = (int) ((v - min) / step);
                if (index >= 10) index = 9; // 处理最大值边界
                if (index < 0) index = 0;   // 处理小于最小值（理论上不应发生）
                
                double start = min + index * step;
                double end = start + step;
                String key = String.format("%.1f-%.1f", start, end);
                
                // 累加计数
                result.put(key, result.getOrDefault(key, 0.0) + 1.0);
            }
        } catch (Exception e) {
            log.error("分布计算失败", e);
        }
        return result;
    }
}
