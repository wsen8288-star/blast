package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.AuditLog;
import com.blastfurnace.backend.model.DeploymentStatus;
import com.blastfurnace.backend.model.ModelDeployment;
import com.blastfurnace.backend.model.ModelService;
import com.blastfurnace.backend.repository.AuditLogRepository;
import com.blastfurnace.backend.repository.ModelDeploymentRepository;
import com.blastfurnace.backend.repository.ModelServiceRepository;
import com.blastfurnace.backend.repository.ModelTrainingRepository;
import com.blastfurnace.backend.service.trainer.ModelTrainerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ModelDeploymentServiceTest {

    @Mock
    private ModelTrainingRepository modelTrainingRepository;

    @Mock
    private ModelDeploymentRepository modelDeploymentRepository;

    @Mock
    private ModelServiceRepository modelServiceRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ModelStoreService modelStoreService;

    @Mock
    private ModelTrainerFactory modelTrainerFactory;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ModelDeploymentService modelDeploymentService;

    @Test
    public void cancelDeployment_updatesStatusAndStopsServices() {
        ModelDeployment deployment = new ModelDeployment();
        deployment.setId(1L);
        deployment.setTrainingId(2L);
        deployment.setStatus(DeploymentStatus.RUNNING);
        deployment.setLogs("start");

        ModelService service = new ModelService();
        service.setId(10L);
        service.setDeploymentId(1L);
        service.setStatus("running");

        when(modelDeploymentRepository.findById(1L)).thenReturn(Optional.of(deployment));
        when(modelDeploymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelServiceRepository.findByDeploymentId(1L)).thenReturn(List.of(service));
        when(modelServiceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = modelDeploymentService.cancelDeployment(1L, "alice");

        assertEquals("canceled", result.get("status"));
        assertEquals("部署已取消", result.get("message"));
        assertEquals("stopped", service.getStatus());
        verify(modelStoreService).remove(2L);
        verify(auditLogRepository, atLeastOnce()).save(any(AuditLog.class));
    }

    @Test
    public void checkServiceHealth_marksStoppedWhenModelMissing() throws Exception {
        ModelService service = new ModelService();
        service.setId(3L);
        service.setDeploymentId(1L);
        service.setStatus("running");

        ModelDeployment deployment = new ModelDeployment();
        deployment.setId(1L);
        deployment.setTrainingId(9L);
        deployment.setStatus(DeploymentStatus.RUNNING);

        when(modelServiceRepository.findById(3L)).thenReturn(Optional.of(service));
        when(modelDeploymentRepository.findById(1L)).thenReturn(Optional.of(deployment));
        when(modelStoreService.get(9L)).thenReturn(Optional.empty());
        when(modelServiceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelDeploymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Map<String, Object> result = modelDeploymentService.checkServiceHealth(3L);

        assertEquals("unhealthy", result.get("health"));
        assertEquals("stopped", result.get("status"));
    }
}
