package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.CollectionSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectionSettingsRepository extends JpaRepository<CollectionSettings, Long> {
    CollectionSettings findByIsDefaultTrue();
}
