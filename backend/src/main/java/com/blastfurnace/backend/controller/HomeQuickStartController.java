package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.dto.QuickStartProgressDTO;
import com.blastfurnace.backend.model.CollectionHistory;
import com.blastfurnace.backend.model.ComparisonHistory;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.PreprocessHistory;
import com.blastfurnace.backend.repository.CollectionHistoryRepository;
import com.blastfurnace.backend.repository.ComparisonHistoryRepository;
import com.blastfurnace.backend.repository.ModelTrainingRepository;
import com.blastfurnace.backend.repository.PreprocessHistoryRepository;
import com.blastfurnace.backend.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Optional;

@RestController
@RequestMapping("/api/home/quick-start")
@RequiredArgsConstructor
public class HomeQuickStartController {
    private final CollectionHistoryRepository collectionHistoryRepository;
    private final PreprocessHistoryRepository preprocessHistoryRepository;
    private final ModelTrainingRepository modelTrainingRepository;
    private final ComparisonHistoryRepository comparisonHistoryRepository;

    @GetMapping("/progress")
    public Result<QuickStartProgressDTO> getProgress(@RequestParam(required = false, defaultValue = "24") Integer hours) {
        int windowHours = hours == null ? 24 : Math.max(1, Math.min(hours, 168));
        Date since = new Date(System.currentTimeMillis() - windowHours * 3600_000L);

        Optional<CollectionHistory> c = collectionHistoryRepository.findTopByRunIdIsNotNullAndStartTimeAfterOrderByStartTimeDesc(since);
        Optional<PreprocessHistory> p = preprocessHistoryRepository.findTopByRunIdIsNotNullAndCreatedAtAfterOrderByCreatedAtDesc(since);
        Optional<ModelTraining> t = modelTrainingRepository.findTopByRunIdIsNotNullAndStartTimeAfterOrderByStartTimeDesc(since);
        Optional<ComparisonHistory> o = comparisonHistoryRepository.findTopByRunIdIsNotNullAndCreatedAtAfterOrderByCreatedAtDesc(since);

        Candidate latest = Candidate.empty();
        if (c.isPresent()) latest = latest.pick(c.get().getRunId(), c.get().getStartTime());
        if (p.isPresent()) latest = latest.pick(p.get().getRunId(), p.get().getCreatedAt());
        if (t.isPresent()) latest = latest.pick(t.get().getRunId(), t.get().getStartTime());
        if (o.isPresent()) latest = latest.pick(o.get().getRunId(), o.get().getCreatedAt());

        QuickStartProgressDTO dto = new QuickStartProgressDTO();
        dto.setWindowHours(windowHours);
        dto.setRunId(latest.runId);

        QuickStartProgressDTO.Step collect = new QuickStartProgressDTO.Step();
        QuickStartProgressDTO.Step preprocess = new QuickStartProgressDTO.Step();
        QuickStartProgressDTO.Step train = new QuickStartProgressDTO.Step();
        QuickStartProgressDTO.Step optimize = new QuickStartProgressDTO.Step();

        if (latest.runId == null || latest.runId.isBlank()) {
            collect.setStatus("not_started");
            preprocess.setStatus("not_started");
            train.setStatus("not_started");
            optimize.setStatus("not_started");
            dto.setCurrent(1);
            dto.setUpdatedAt(new Date());
            dto.setCollect(collect);
            dto.setPreprocess(preprocess);
            dto.setTrain(train);
            dto.setOptimize(optimize);
            return Result.success(dto);
        }

        CollectionHistory collectHistory = collectionHistoryRepository.findTopByRunIdOrderByStartTimeDesc(latest.runId).orElse(null);
        PreprocessHistory preprocessHistory = preprocessHistoryRepository.findTopByRunIdOrderByCreatedAtDesc(latest.runId).orElse(null);
        ModelTraining training = modelTrainingRepository.findTopByRunIdOrderByStartTimeDesc(latest.runId).orElse(null);
        ComparisonHistory comparisonHistory = comparisonHistoryRepository.findTopByRunIdOrderByCreatedAtDesc(latest.runId).orElse(null);

        setCollectStep(collect, collectHistory);
        setPreprocessStep(preprocess, preprocessHistory);
        setTrainStep(train, training);
        setOptimizeStep(optimize, comparisonHistory);

        dto.setCollect(collect);
        dto.setPreprocess(preprocess);
        dto.setTrain(train);
        dto.setOptimize(optimize);

        dto.setUpdatedAt(latest.at != null ? latest.at : new Date());
        dto.setCurrent(resolveCurrent(collect.getStatus(), preprocess.getStatus(), train.getStatus(), optimize.getStatus()));
        return Result.success(dto);
    }

    private static void setCollectStep(QuickStartProgressDTO.Step step, CollectionHistory history) {
        if (history == null) {
            step.setStatus("not_started");
            return;
        }
        step.setUpdatedAt(history.getEndTime() != null ? history.getEndTime() : history.getStartTime());
        step.setStatus(normalizeStatus(history.getStatus()));
    }

    private static void setPreprocessStep(QuickStartProgressDTO.Step step, PreprocessHistory history) {
        if (history == null) {
            step.setStatus("not_started");
            return;
        }
        step.setUpdatedAt(history.getCreatedAt());
        step.setStatus(normalizeStatus(history.getStatus()));
    }

    private static void setTrainStep(QuickStartProgressDTO.Step step, ModelTraining training) {
        if (training == null) {
            step.setStatus("not_started");
            return;
        }
        Date at = training.getEndTime() != null ? training.getEndTime() : training.getStartTime();
        step.setUpdatedAt(at);
        step.setStatus(normalizeStatus(training.getStatus()));
    }

    private static void setOptimizeStep(QuickStartProgressDTO.Step step, ComparisonHistory history) {
        if (history == null) {
            step.setStatus("not_started");
            return;
        }
        step.setUpdatedAt(history.getCreatedAt());
        step.setStatus("completed");
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "not_started";
        }
        String s = status.trim().toLowerCase();
        if ("running".equals(s) || "in_progress".equals(s)) return "running";
        if ("completed".equals(s) || "success".equals(s) || "done".equals(s)) return "completed";
        if ("failed".equals(s) || "error".equals(s)) return "failed";
        return s;
    }

    private static int resolveCurrent(String collect, String preprocess, String train, String optimize) {
        if ("completed".equals(optimize)) return 4;
        if ("completed".equals(train)) return 4;
        if ("completed".equals(preprocess)) return 3;
        if ("completed".equals(collect)) return 2;
        return 1;
    }

    private record Candidate(String runId, Date at) {
        static Candidate empty() {
            return new Candidate(null, null);
        }

        Candidate pick(String newRunId, Date newAt) {
            if (newRunId == null || newRunId.isBlank() || newAt == null) {
                return this;
            }
            if (this.at == null || newAt.after(this.at)) {
                return new Candidate(newRunId, newAt);
            }
            return this;
        }
    }
}

