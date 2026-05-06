package com.gtnewhorizons.galaxia.registry.outpost.module;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;

public final class ModuleHammer implements ModuleComponent, IParallelModule {

    private static final ModuleTier[] BASE_TIERS = { ModuleTier.EV, ModuleTier.IV, ModuleTier.LuV };
    private static final ModuleTier[] BIG_TIERS = { ModuleTier.LuV, ModuleTier.ZPM, ModuleTier.UV };

    public final FacilityModuleKind kind;

    private byte parallel = 1;

    private final int maxBatchSize;
    private OrbitalTransferPlanner.RoutePriority routePriority;
    private boolean canFire;

    private HammerVariant variant;
    private AllowShootingConfig config;

    public ModuleHammer(@Nonnull FacilityModuleKind kind, @Nonnull AllowShootingConfig config,
        @Nonnull OrbitalTransferPlanner.RoutePriority routePriority, boolean canFire, @Nonnull HammerVariant variant,
        int maxBatchSize) {
        this.kind = kind;
        this.config = config;
        this.routePriority = routePriority;
        this.canFire = canFire;
        this.variant = variant;
        this.maxBatchSize = maxBatchSize;
    }

    public static void prepareToFire(ModuleInstance instance, AutomatedFacility outpost) {
        ModuleHammer hammer = (ModuleHammer) instance.component();
        if (!outpost.tryConsumeEnergy(shotEnergyEu(hammer.variant))) return;
        hammer.canFire = true;
    }

    public static int cooldownTicks(@Nonnull HammerVariant variant, @Nonnull ModuleTier tier) {
        return switch (variant) {
            case BASE -> switch (tier) {
                    case EV -> 60 * 20;
                    case IV -> 45 * 20;
                    case LuV -> 30 * 20;
                    default -> throw invalidTier(variant, tier);
                };
            case BIG -> switch (tier) {
                    case LuV -> 60 * 20;
                    case ZPM -> 45 * 20;
                    case UV -> 30 * 20;
                    default -> throw invalidTier(variant, tier);
                };
        };
    }

    public static long shotEnergyEu(@Nonnull HammerVariant variant) {
        return switch (variant) {
            case BASE -> 500_000L;
            case BIG -> 8_000_000L;
        };
    }

    public static int chargeTicks(@Nonnull HammerVariant variant, @Nonnull ModuleTier tier) {
        return Math.max(1, cooldownTicks(variant, tier) - 20);
    }

    public static long chargeRateEuPerTick(@Nonnull HammerVariant variant, @Nonnull ModuleTier tier) {
        long energy = shotEnergyEu(variant);
        int chargeTicks = chargeTicks(variant, tier);
        return Math.ceilDiv(energy, chargeTicks);
    }

    public static ModuleTier nextTier(@Nonnull HammerVariant variant, @Nonnull ModuleTier current) {
        ModuleTier[] values = tiersFor(variant);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
        }
        throw invalidTier(variant, current);
    }

    public static ModuleTier tierForVariantSwitch(@Nonnull HammerVariant targetVariant,
        @Nonnull ModuleTier currentTier) {
        return supportsTier(targetVariant, currentTier) ? currentTier : tiersFor(targetVariant)[0];
    }

    public static boolean supportsTier(@Nonnull HammerVariant variant, @Nonnull ModuleTier tier) {
        try {
            cooldownTicks(variant, tier);
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static void requireTier(@Nonnull HammerVariant variant, @Nonnull ModuleTier tier) {
        cooldownTicks(variant, tier);
    }

    public AllowShootingConfig config() {
        return config;
    }

    public void setConfig(@Nonnull AllowShootingConfig newConfig) {
        this.config = newConfig;
    }

    public OrbitalTransferPlanner.RoutePriority routePriority() {
        return routePriority;
    }

    public boolean canFire() {
        return canFire;
    }

    public void fire() {
        canFire = false;
    }

    public HammerVariant variant() {
        return variant;
    }

    public int maxBatchSize() {
        return maxBatchSize;
    }

    public void setRoutePriority(@Nonnull OrbitalTransferPlanner.RoutePriority routePriority) {
        this.routePriority = routePriority;
    }

    public void setVariant(@Nonnull HammerVariant variant) {
        this.variant = variant;
    }

    private static IllegalStateException invalidTier(HammerVariant variant, ModuleTier tier) {
        return new IllegalStateException("Hammer variant " + variant + " does not support tier " + tier);
    }

    private static ModuleTier[] tiersFor(HammerVariant variant) {
        return switch (variant) {
            case BASE -> BASE_TIERS;
            case BIG -> BIG_TIERS;
        };
    }

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }
}
