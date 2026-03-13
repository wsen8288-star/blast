package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.StorageDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageDeviceRepository extends JpaRepository<StorageDevice, Long> {
}