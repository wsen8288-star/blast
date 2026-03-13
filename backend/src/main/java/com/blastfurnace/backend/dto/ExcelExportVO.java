package com.blastfurnace.backend.dto;

import lombok.Data;


@Data
public class ExcelExportVO {
    
    // 对应表头：时间
    private String timestamp;
    
    // 对应表头：温度(°C)
    private Double temperature;
    
    // 对应表头：压力(kPa)
    private Double pressure;
    
    // 对应表头：料面高度(m)
    private Double materialHeight;
    
    // 对应表头：煤气流量
    private Double gasFlow;
    
    // 对应表头：氧气浓度
    private Double oxygenLevel;
    
    // 对应表头：生产速率
    private Double productionRate;
    
    // 对应表头：能耗
    private Double energyConsumption;
    
    // 对应表头：风量
    private Double airFlow;
    
    // 对应表头：煤量
    private Double coalFlow;
    
    // 对应表头：高炉ID
    private String furnaceId;
    
    // 对应表头：铁水温度(°C)
    private Double hotMetalTemperature;
    
    // 对应表头：恒定信号
    private Double constantSignal;
    
    // 对应表头：状态
    private String status;
    
    // 对应表头：操作员
    private String operator;
}
