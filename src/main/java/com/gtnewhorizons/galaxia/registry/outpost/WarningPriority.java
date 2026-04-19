package com.gtnewhorizons.galaxia.registry.outpost;

public enum WarningPriority {

    NONE,
    IDLE,
    MISSING_INPUT,
    BLOCKED_LOGISTICS,
    NO_POWER;

    public boolean isWarning() {
        return this != NONE;
    }

    public WarningPriority max(WarningPriority other) {
        if (other == null) return this;
        return this.ordinal() >= other.ordinal() ? this : other;
    }
}
