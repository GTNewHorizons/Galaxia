package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;

public final class ModuleHammer implements ModuleComponent, IParallelModule {

    public final FacilityModuleKind kind;

    private byte parallel = 1;

    private final int maxBatchSize;
    private OrbitalTransferPlanner.RoutePriority routePriority;
    private boolean canFire;

    private HammerVariant variant;
    private AllowShootingConfig config;

    public ModuleHammer(FacilityModuleKind kind, AllowShootingConfig config,
        OrbitalTransferPlanner.RoutePriority routePriority, boolean canFire, HammerVariant variant, int maxBatchSize) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.config = Objects.requireNonNull(config, "config");
        this.routePriority = Objects.requireNonNull(routePriority, "routePriority");
        this.canFire = canFire;
        this.variant = Objects.requireNonNull(variant, "variant");
        this.maxBatchSize = maxBatchSize;
    }

    public static void prepareToFire(ModuleInstance instance, AutomatedFacility outpost) {
        ModuleHammer hammer = (ModuleHammer) instance.component();
        if (!outpost.tryConsumeEnergy(shotEnergyEu(hammer.variant))) return;
        hammer.canFire = true;
    }

    public static int cooldownTicks(HammerVariant variant, ModuleTier tier) {
        return switch (Objects.requireNonNull(variant, "variant")) {
            case BASE -> switch (Objects.requireNonNull(tier, "tier")) {
                case EV -> 60 * 20;
                case IV -> 45 * 20;
                case LuV -> 30 * 20;
                default -> throw invalidTier(variant, tier);
            };
            case BIG -> switch (Objects.requireNonNull(tier, "tier")) {
                case LuV -> 60 * 20;
                case ZPM -> 45 * 20;
                case UV -> 30 * 20;
                default -> throw invalidTier(variant, tier);
            };
        };
    }

    public static long shotEnergyEu(HammerVariant variant) {
        return switch (Objects.requireNonNull(variant, "variant")) {
            case BASE -> 500_000L;
            case BIG -> 8_000_000L;
        };
    }

    public static boolean supportsTier(HammerVariant variant, ModuleTier tier) {
        try {
            cooldownTicks(variant, tier);
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static void requireTier(HammerVariant variant, ModuleTier tier) {
        cooldownTicks(variant, tier);
    }

    public AllowShootingConfig config() {
        return config;
    }

    public void setConfig(AllowShootingConfig newConfig) {
        this.config = Objects.requireNonNull(newConfig, "newConfig");
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

    public void setRoutePriority(OrbitalTransferPlanner.RoutePriority routePriority) {
        this.routePriority = Objects.requireNonNull(routePriority, "routePriority");
    }

    public void setVariant(HammerVariant variant) {
        this.variant = Objects.requireNonNull(variant, "variant");
    }

    private static IllegalStateException invalidTier(HammerVariant variant, ModuleTier tier) {
        return new IllegalStateException("Hammer variant " + variant + " does not support tier " + tier);
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
