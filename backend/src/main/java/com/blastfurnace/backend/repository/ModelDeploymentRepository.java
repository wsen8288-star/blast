package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.ModelDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ModelDeploymentRepository extends JpaRepository<ModelDeployment, Long> {
    List<ModelDeployment> findAllByOrderByDeployTimeDesc();
}
