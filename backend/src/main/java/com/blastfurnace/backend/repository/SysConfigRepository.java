package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.SysConfig;
import com.blastfurnace.backend.model.SysConfigGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysConfigRepository extends JpaRepository<SysConfig, Long> {
    Optional<SysConfig> findByConfigKey(String configKey);
    List<SysConfig> findByConfigGroupOrderByConfigKeyAsc(SysConfigGroup configGroup);
    boolean existsByConfigKey(String configKey);
}
