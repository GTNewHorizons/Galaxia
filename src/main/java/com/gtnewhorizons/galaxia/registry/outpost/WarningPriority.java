package com.gtnewhorizons.galaxia.registry.outpost;

public enum WarningPriority {

    NONE(0),
    IDLE(1),
    MISSING_INPUT(2),
    BLOCKED_LOGISTICS(3),
    NO_POWER(4);

    public final int priority;

    WarningPriority(int priority) {
        this.priority = priority;
    }

    public boolean isWarning() {
        return this != NONE;
    }

    public WarningPriority max(WarningPriority other) {
        if (other == null) return this;
        return this.priority >= other.priority ? this : other;
    }
}
