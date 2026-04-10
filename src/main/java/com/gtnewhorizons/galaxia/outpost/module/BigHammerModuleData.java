package com.gtnewhorizons.galaxia.outpost.module;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

/**
 * Static configuration data for a {@link com.gtnewhorizons.galaxia.outpost.OutpostModuleKind#BIG_HAMMER} module.
 *
 * <p>
 * BIG_HAMMER operates within the same stellar system as the host outpost without
 * range restrictions or batch-size limits.
 *
 * <p>
 * Configuration fields:
 * <ul>
 * <li>{@code planetaryTransferHandling} – when {@code true} the module also handles
 * PLANETARY-scope signals (same planet + moons), duplicating HAMMER coverage.</li>
 * <li>{@code allowShooting} – shooting permission; {@code null} in old saves
 * is treated as {@link AllowShootingConfig#ALWAYS} via {@link #effectiveShooting()}.</li>
 * <li>Cooldown: 20 ticks (1 second) between operations.</li>
 * </ul>
 */
@Desugar
public record BigHammerModuleData(boolean planetaryTransferHandling, AllowShootingConfig allowShooting,
    OrbitalTransferPlanner.RoutePriority routePriority) implements OutpostModuleData {

    /** Creates a default instance: no planetary-transfer duplication, always allow. */
    public static BigHammerModuleData getDefault() {
        return new BigHammerModuleData(
            false,
            AllowShootingConfig.ALWAYS,
            OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF);
    }

    /** Returns the effective shooting config, defaulting to ALWAYS for pre-migration saves. */
    public AllowShootingConfig effectiveShooting() {
        return allowShooting != null ? allowShooting : AllowShootingConfig.ALWAYS;
    }

    public OrbitalTransferPlanner.RoutePriority effectiveRoutePriority() {
        return routePriority != null ? routePriority : OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
    }

    /** Ticks between successive BIG_HAMMER dispatches (1 s at 20 TPS). */
    public static final int COOLDOWN_TICKS = 20;
}
