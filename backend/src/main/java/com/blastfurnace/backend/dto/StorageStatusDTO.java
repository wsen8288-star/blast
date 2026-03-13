package com.blastfurnace.backend.dto;

import com.blastfurnace.backend.model.StorageDevice;
import lombok.Data;
import java.util.List;

@Data
public class StorageStatusDTO {
    private int totalStorage;
    private int usedStorage;
    private int remainingStorage;
    private int usedStoragePercentage;
    private int fileCount;
    private String lastBackupTime;
    private String backupStatus;
    private List<StorageDevice> storageDevices;
}
