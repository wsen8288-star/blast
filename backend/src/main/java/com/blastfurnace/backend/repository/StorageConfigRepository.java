package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.StorageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageConfigRepository extends JpaRepository<StorageConfig, Long> {
    StorageConfig findByIsDefaultTrue();
}
