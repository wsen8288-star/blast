package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.AnomalyThreshold;
import com.blastfurnace.backend.repository.AnomalyThresholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnomalyConfigService {

    private final AnomalyThresholdRepository anomalyThresholdRepository;

    public List<AnomalyThreshold> getThresholds(String furnaceId) {
        if (furnaceId == null || furnaceId.isEmpty()) {
            return anomalyThresholdRepository.findAll();
        }
        return anomalyThresholdRepository.findByFurnaceId(furnaceId);
    }

    @Transactional
    public void saveThreshold(AnomalyThreshold threshold) {
        String parameterName = normalizeParameterName(threshold.getParameterName());
        threshold.setParameterName(parameterName);
        Optional<AnomalyThreshold> existing = anomalyThresholdRepository.findFirstByFurnaceIdAndParameterNameOrderByUpdateTimeDesc(
                threshold.getFurnaceId(), parameterName);
        
        if (existing.isPresent()) {
            AnomalyThreshold target = existing.get();
            target.setMinVal(threshold.getMinVal());
            target.setMaxVal(threshold.getMaxVal());
            target.setTipOffsetPct(threshold.getTipOffsetPct());
            target.setWarningOffsetPct(threshold.getWarningOffsetPct());
            target.setSevereOffsetPct(threshold.getSevereOffsetPct());
            target.setUpdateTime(LocalDateTime.now());
            anomalyThresholdRepository.save(target);
        } else {
            threshold.setUpdateTime(LocalDateTime.now());
            anomalyThresholdRepository.save(threshold);
        }
    }

    private String normalizeParameterName(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String canonical = UploadedDataNormalizer.toCanonicalKey(input);
        if (canonical != null && !canonical.isBlank()) {
            return canonical;
        }
        IndustrialDataContract.ParameterSpec spec = IndustrialDataContract.findByAnyKey(input);
        if (spec != null && spec.key() != null && !spec.key().isBlank()) {
            return spec.key();
        }
        return input.trim();
    }
    
    @Transactional
    public void deleteThreshold(Long id) {
        anomalyThresholdRepository.deleteById(id);
    }
}
