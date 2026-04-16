package com.gtnewhorizons.galaxia.registry.outpost.logistics;

/**
 * Per-module shooting permission configuration.
 *
 * <p>
 * Controls whether a HAMMER or BIG_HAMMER module is allowed to spend EU and dispatch
 * a trajectory-based logistics task. The threshold unit depends on mode:
 * <ul>
 * <li>{@link Mode#WHEN_DV_UNDER} – departure delta-V in orbital velocity units.</li>
 * <li>{@link Mode#WHEN_TOF_UNDER} – time-of-flight in real seconds.</li>
 * </ul>
 */

public record AllowShootingConfig(Mode mode, double threshold) {

    /**
     * Controls when a logistics module is permitted to spend EU to dispatch a trajectory transfer.
     */
    public enum Mode {
        /** Always dispatch regardless of dV or time-of-flight. */
        ALWAYS,
        /** Only dispatch when the computed departure delta-V is below the configured threshold. */
        WHEN_DV_UNDER,
        /** Only dispatch when the estimated time-of-flight (in real seconds) is below the configured threshold. */
        WHEN_TOF_UNDER
    }

    /** Singleton representing "always allow". */
    public static final AllowShootingConfig ALWAYS = new AllowShootingConfig(Mode.ALWAYS, 0.0);

    /**
     * Returns {@code true} if a transfer with the given metrics is permitted.
     *
     * @param departureDv departure delta-V in orbital velocity units
     * @param tofSeconds  time-of-flight in real seconds
     */
    public boolean allows(double departureDv, double tofSeconds) {
        return switch (mode) {
            case ALWAYS -> true;
            case WHEN_DV_UNDER -> departureDv < threshold;
            case WHEN_TOF_UNDER -> tofSeconds < threshold;
        };
    }
}
