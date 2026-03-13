package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.PreprocessHistory;
import com.blastfurnace.backend.repository.PreprocessHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreprocessHistoryService {
    private final PreprocessHistoryRepository preprocessHistoryRepository;

    public void recordSuccess(String runId, int recordCount) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        PreprocessHistory history = new PreprocessHistory();
        history.setRunId(runId.trim());
        history.setStatus("completed");
        history.setRecordCount(recordCount);
        preprocessHistoryRepository.save(history);
    }

    public void recordFailure(String runId, String message) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        PreprocessHistory history = new PreprocessHistory();
        history.setRunId(runId.trim());
        history.setStatus("failed");
        history.setMessage(message);
        preprocessHistoryRepository.save(history);
    }
}
