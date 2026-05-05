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
        hammer.canFire = true;
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

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }
}
