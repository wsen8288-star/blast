package com.blastfurnace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "production_data")
public class ProductionData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String furnaceId;
    
    @Column(nullable = false)
    private Date timestamp;
    
    // 【重点修改】全部改为 Double 包装类，且允许为 null
    @Column(nullable = true)
    private Double temperature;
    
    @Column(nullable = true)
    private Double pressure;

    @Column(name = "wind_volume", nullable = true)
    private Double windVolume;

    @Column(name = "coal_injection", nullable = true)
    private Double coalInjection;
    
    @Column(name = "material_height", nullable = true)
    private Double materialHeight;
    
    @Column(name = "gas_flow", nullable = true)
    private Double gasFlow;
    
    @Column(name = "oxygen_level", nullable = true)
    private Double oxygenLevel;
    
    @Column(name = "production_rate", nullable = true)
    private Double productionRate;
    
    @Column(name = "energy_consumption", nullable = true)
    private Double energyConsumption;

    // --- 新增字段：铁水温度 ---
    @Column(name = "`hotMetal_Temperature`", nullable = true)
    private Double hotMetalTemperature;

    @Column(name = "constant_signal", nullable = true)
    private Double constantSignal;
    
    // --- 新增字段：硅含量 ---
    @Column(name = "silicon_content", nullable = true)
    private Double siliconContent;
    
    private String status;
    
    private String operator;

    @Column(name = "collection_history_id")
    private Long collectionHistoryId;
}
