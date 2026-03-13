package com.blastfurnace.backend.service.trainer;

import java.util.concurrent.Semaphore;

final class TrainingRuntimeGuards {
    private static final Semaphore TREE_MODEL_TRAINING_PERMIT = new Semaphore(1, true);

    private TrainingRuntimeGuards() {
    }

    static void acquireTreeModelPermit() {
        try {
            TREE_MODEL_TRAINING_PERMIT.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TrainingException("训练已取消", e);
        }
    }

    static void releaseTreeModelPermit() {
        TREE_MODEL_TRAINING_PERMIT.release();
    }
}
