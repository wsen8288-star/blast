package com.blastfurnace.backend.repository;

import com.blastfurnace.backend.model.ModelService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ModelServiceRepository extends JpaRepository<ModelService, Long> {
    List<ModelService> findAllByOrderByIdDesc();
    List<ModelService> findByDeploymentIdIn(List<Long> deploymentIds);
    List<ModelService> findByDeploymentId(Long deploymentId);
    List<ModelService> findByEnvironmentAndStatus(String environment, String status);
    List<ModelService> findByStatus(String status);
}
