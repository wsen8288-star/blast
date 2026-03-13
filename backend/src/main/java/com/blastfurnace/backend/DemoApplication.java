package com.blastfurnace.backend;

import com.blastfurnace.backend.service.StorageDeviceService;
import com.blastfurnace.backend.service.StorageConfigService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.boot.CommandLineRunner;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
    
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("collection-task-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }
    
    @Bean
    public CommandLineRunner initStorageDevices(StorageDeviceService storageDeviceService, StorageConfigService storageConfigService) {
        return args -> {
            storageDeviceService.initStorageDevices();
            storageConfigService.initStorageConfig();
        };
    }
}
