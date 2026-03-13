package com.blastfurnace.backend.model;

public enum AnomalyStatus {
    PENDING(0),
    PROCESSING(1),
    RESOLVED(2),
    CLOSED(3);

    private final int code;

    AnomalyStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static AnomalyStatus fromRaw(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number number) {
            int v = number.intValue();
            for (AnomalyStatus s : values()) {
                if (s.code == v) return s;
            }
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) return null;
        for (AnomalyStatus s : values()) {
            if (s.name().equalsIgnoreCase(text)) return s;
        }
        if ("COMPLETED".equalsIgnoreCase(text)) return RESOLVED;
        if ("2".equals(text)) return RESOLVED;
        if ("3".equals(text)) return CLOSED;
        return null;
    }
}
