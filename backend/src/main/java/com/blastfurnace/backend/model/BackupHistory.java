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
@Table(name = "backup_history")
public class BackupHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "backup_time", nullable = false)
    private Date backupTime;
    
    @Column(name = "backup_size", nullable = false)
    private String backupSize;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "backup_type", nullable = false)
    private String backupType;
    
    @Column(name = "backup_path", nullable = false)
    private String backupPath;

    @Column(name = "restore_source_backup_id")
    private Long restoreSourceBackupId;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;
}
