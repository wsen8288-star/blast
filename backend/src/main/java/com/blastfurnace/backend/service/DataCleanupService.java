package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class DataCleanupService {

    @Autowired
    private ProductionDataRepository productionDataRepository;

    @Autowired
    private SysConfigService sysConfigService;

    public void cleanupAndInitializeData() {
        int retentionDays = sysConfigService.getInt("data_retention_days", 0);
        if (retentionDays > 0) {
            long cutoffMs = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L;
            long deleted = productionDataRepository.deleteByTimestampBefore(new Date(cutoffMs));
            System.out.println("已清理过期生产数据，保留天数=" + retentionDays + "，删除条数=" + deleted);
        } else {
            productionDataRepository.deleteAll();
            System.out.println("已清理所有生产数据");
        }
    }
}
