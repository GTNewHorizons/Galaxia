package com.gtnewhorizons.galaxia.outpost.module;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

/**
 * Static configuration data for a {@link com.gtnewhorizons.galaxia.outpost.OutpostModuleKind#HAMMER} module.
 *
 * <p>
 * Operational constants (not stored here – they are hardcoded in the engine):
 * <ul>
 * <li>EU cost: 100 EU × departure-dV × items transferred.</li>
 * <li>Max batch size: 64 items per {@code LogisticsTask}.</li>
 * <li>Cooldown: 20 ticks (1 second) between operations.</li>
 * </ul>
 *
 * <p>
 * Runtime mutable state (cooldownTicks, energyBuffer) is tracked in
 * {@link com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule} to avoid
 * record-copy overhead on every server tick.
 *
 * <p>
 * {@code allowShooting} may be {@code null} in saves created before this field
 * was introduced; use {@link #effectiveShooting()} which defaults to
 * {@link AllowShootingConfig#ALWAYS}.
 */
@Desugar
public record HammerModuleData(AllowShootingConfig allowShooting, OrbitalTransferPlanner.RoutePriority routePriority)
    implements OutpostModuleData {

    /** Creates a default instance (ALWAYS allow shooting). */
    public static HammerModuleData getDefault() {
        return new HammerModuleData(AllowShootingConfig.ALWAYS, OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF);
    }

    /** Returns the effective config, defaulting to ALWAYS for pre-migration saves. */
    public AllowShootingConfig effectiveShooting() {
        return allowShooting != null ? allowShooting : AllowShootingConfig.ALWAYS;
    }

    public OrbitalTransferPlanner.RoutePriority effectiveRoutePriority() {
        return routePriority != null ? routePriority : OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
    }

    /** Maximum number of items in a single logistics task. Larger orders are split. */
    public static final int MAX_BATCH_SIZE = 64;

    /** Ticks between successive HAMMER dispatches (1 s at 20 TPS). */
    public static final int COOLDOWN_TICKS = 20;
}
