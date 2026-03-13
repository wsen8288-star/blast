package com.blastfurnace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "storage_devices")
public class StorageDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String type;
    
    @Column(nullable = false)
    private String status;
    
    @Column(nullable = false)
    private int capacity;
    
    @Column(nullable = false)
    private int used;
    
    @Column(nullable = false)
    private int remaining;
    
    @Column(nullable = false)
    private int usagePercentage;

    @PrePersist
    @PreUpdate
    public void normalizeDerivedFields() {
        int cap = Math.max(0, capacity);
        int usedVal = Math.max(0, used);
        if (usedVal > cap) usedVal = cap;
        used = usedVal;
        remaining = cap - usedVal;
        usagePercentage = cap > 0 ? (int) Math.round((usedVal * 100.0) / cap) : 0;
    }

    public int getRemaining() {
        int cap = Math.max(0, capacity);
        int usedVal = Math.max(0, used);
        if (usedVal > cap) usedVal = cap;
        return cap - usedVal;
    }

    public int getUsagePercentage() {
        int cap = Math.max(0, capacity);
        int usedVal = Math.max(0, used);
        if (usedVal > cap) usedVal = cap;
        return cap > 0 ? (int) Math.round((usedVal * 100.0) / cap) : 0;
    }
}
