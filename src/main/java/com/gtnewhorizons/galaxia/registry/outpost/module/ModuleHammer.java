package com.gtnewhorizons.galaxia.registry.outpost.module;

import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;

public final class ModuleHammer implements ModuleComponent {

    public final FacilityModuleKind kind;
    public final boolean crossPlanetaryCapability;

    private byte parallel = 1;

    private final int maxBatchSize;
    private OrbitalTransferPlanner.RoutePriority routePriority;
    private boolean canFire;

    private boolean planetaryHandling;
    private AllowShootingConfig config;

    public ModuleHammer(FacilityModuleKind kind, AllowShootingConfig config,
        OrbitalTransferPlanner.RoutePriority routePriority, boolean canFire, boolean planetaryHandling,
        boolean crossPlanetaryCapability, int maxBatchSize) {
        this.kind = kind;
        this.config = config;
        this.routePriority = routePriority;
        this.canFire = canFire;
        this.planetaryHandling = planetaryHandling;
        this.crossPlanetaryCapability = crossPlanetaryCapability;
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

    public boolean planetaryHandling() {
        return planetaryHandling;
    }

    public int maxBatchSize() {
        return maxBatchSize;
    }

    public void setRoutePriority(OrbitalTransferPlanner.RoutePriority routePriority) {
        this.routePriority = routePriority;
    }

    public void setPlanetaryHandling(boolean planetaryHandling) {
        this.planetaryHandling = planetaryHandling;
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
