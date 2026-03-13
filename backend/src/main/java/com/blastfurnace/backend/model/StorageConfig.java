package com.blastfurnace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "storage_config")
public class StorageConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "main_storage_path", nullable = false)
    private String mainStoragePath;
    
    @Column(name = "backup_storage_path", nullable = false)
    private String backupStoragePath;
    
    @Column(name = "storage_format", nullable = false)
    private String storageFormat;
    
    @Column(name = "auto_backup", nullable = false)
    private Boolean autoBackup;
    
    @Column(name = "backup_frequency", nullable = false)
    private String backupFrequency;
    
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;
}
