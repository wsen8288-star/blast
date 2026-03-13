package com.blastfurnace.backend.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class OperationControlService {
    private static final int TRAIN_MAX_CONCURRENT = 2;
    private static final long TRAIN_MIN_INTERVAL_MS = 8_000L;
    private static final int DEPLOY_MAX_CONCURRENT = 2;
    private static final long DEPLOY_MIN_INTERVAL_MS = 10_000L;
    private static final int EVOLUTION_MAX_CONCURRENT = 1;
    private static final long EVOLUTION_MIN_INTERVAL_MS = 12_000L;

    private final Map<String, Semaphore> semaphores = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSubmitAt = new ConcurrentHashMap<>();

    public Permit enterTraining() {
        return enter("training", TRAIN_MAX_CONCURRENT, TRAIN_MIN_INTERVAL_MS);
    }

    public Permit enterDeployment() {
        return enter("deployment", DEPLOY_MAX_CONCURRENT, DEPLOY_MIN_INTERVAL_MS);
    }

    public Permit enterEvolution() {
        return enter("evolution", EVOLUTION_MAX_CONCURRENT, EVOLUTION_MIN_INTERVAL_MS);
    }

    public void exit(Permit permit) {
        if (permit != null && permit.semaphore != null) {
            permit.semaphore.release();
        }
    }

    private Permit enter(String operation, int maxConcurrent, long minIntervalMs) {
        String principal = currentPrincipal();
        String throttleKey = operation + ":" + principal;
        String operationLabel = toOperationLabel(operation);
        long now = System.currentTimeMillis();
        Long previous = lastSubmitAt.get(throttleKey);
        if (previous != null && now - previous < minIntervalMs) {
            long waitSeconds = Math.max(1, (minIntervalMs - (now - previous)) / 1000);
            throw new IllegalStateException(operationLabel + "请求过于频繁，请在 " + waitSeconds + " 秒后重试");
        }
        lastSubmitAt.put(throttleKey, now);
        Semaphore semaphore = semaphores.computeIfAbsent(operation, key -> new Semaphore(maxConcurrent, true));
        if (!semaphore.tryAcquire()) {
            throw new IllegalStateException("当前" + operationLabel + "任务过多，请稍后再试");
        }
        return new Permit(semaphore);
    }

    private String toOperationLabel(String operation) {
        if ("training".equals(operation)) {
            return "训练";
        }
        if ("deployment".equals(operation)) {
            return "部署";
        }
        if ("evolution".equals(operation)) {
            return "演化";
        }
        return "操作";
    }

    private String currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "anonymous";
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return "anonymous";
        }
        return name;
    }

    public static class Permit {
        private final Semaphore semaphore;

        public Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }
    }
}
