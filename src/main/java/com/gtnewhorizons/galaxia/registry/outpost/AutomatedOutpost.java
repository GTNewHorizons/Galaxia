package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.OutpostModuleKind;

public final class AutomatedOutpost extends CelestialAsset {

    public final CelestialObjectId systemId;

    public final CelestialObjectId planetaryAnchorBodyId;

    private final List<ModuleInstance> modules;

    public final AutomatedOutpostInventory inventory;

    public final LogisticsConfiguration logisticsConfig;

    private long energyStored;

    public static final long MAX_ENERGY = 1_000_000L;

    public AutomatedOutpost(CelestialAsset.ID assetId, CelestialObjectId celestialBodyId, Status status) {
        super(assetId, celestialBodyId, Kind.AUTOMATED_OUTPOST, status, null);
        this.systemId = GalaxiaCelestialAPI.findStar(celestialBodyId)
            .id();
        this.planetaryAnchorBodyId = GalaxiaCelestialAPI.findPlanetaryAnchor(celestialBodyId)
            .id();
        this.modules = new ArrayList<>();
        this.inventory = new AutomatedOutpostInventory();
        this.logisticsConfig = new LogisticsConfiguration();
        this.energyStored = 0;
    }

    public List<ModuleInstance> modules() {
        return Collections.unmodifiableList(modules);
    }

    public void addModule(ModuleInstance module) {
        if (modules.contains(module)) return;
        modules.add(module);
    }

    public void removeModule(int index) {
        modules.remove(index);
    }

    public void clearModules() {
        modules.clear();
    }

    public Stream<ModuleInstance> allOperationalModules() {
        return modules.stream()
            .filter(ModuleInstance::isOperational);
    }

    public List<ModuleInstance> modulesInternal() {
        return modules;
    }

    public long getEnergyStored() {
        return energyStored;
    }

    public void setEnergyStored(long energyStored) {
        this.energyStored = Math.min(MAX_ENERGY, Math.max(0, energyStored));
    }

    public void addEnergy(long delta) {
        setEnergyStored(energyStored + delta);
    }

    public boolean tryConsumeEnergy(long amount) {
        if (amount <= 0) return true;
        if (energyStored < amount) return false;
        energyStored -= amount;
        return true;
    }

    @Override
    public boolean hasMiningCapability() {
        for (ModuleInstance m : modules) {
            if (m.kind() == OutpostModuleKind.MINER && m.isOperational()) return true;
        }
        return false;
    }

    @Override
    public boolean hasProductionCapability() {
        for (ModuleInstance m : modules) {
            OutpostModuleKind k = m.kind();
            if ((k == OutpostModuleKind.HAMMER || k == OutpostModuleKind.BIG_HAMMER) && m.isOperational()) return true;
        }
        return false;
    }

    @Override
    public WarningPriority warningPriority() {
        if (!isOperational()) return WarningPriority.NONE;
        if (energyStored <= 0L) return WarningPriority.NO_POWER;
        for (ModuleInstance m : modules) {
            if (m.isOperational()) return WarningPriority.NONE;
        }
        return WarningPriority.IDLE;
    }

    public void tick() {
        for (ModuleInstance module : modules) {
            module.tick(this);
        }

        LogisticStore.updateSignalsForOutpost(this);
    }
}
