package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.OutpostModuleKind;

/**
 * Complete runtime state for a single automated outpost.
 *
 * <p>
 * The outpost is the operational layer on top of a {@code CelestialManagedAsset}.
 * It adds logistics, inventory, and module state that only exist while the outpost is OPERATIONAL.
 */
public final class AutomatedOutpost extends CelestialAsset {

    /**
     * The stellar system id (host star body id) used to bucket this outpost in {@code LogisticsSignalStore}.
     */
    public final CelestialObjectId systemId;

    /**
     * The id of the nearest PLANET/GAS_GIANT ancestor of this outpost's body.
     * Stored at construction time to avoid repeated tree walks in the logistics engine.
     * For planets/gas giants: equals {@code celestialBodyId}.
     * For moons/stations/asteroids: the parent planet id.
     * Falls back to {@code celestialBodyId} if resolution fails.
     */
    public final CelestialObjectId planetaryAnchorBodyId;

    /** Installed modules; ordering is significant for multi-module interactions. */
    private final List<AutomatedOutpostModule> modules;

    /** Virtual item inventory. */
    public final AutomatedOutpostInventory inventory;

    /** Per-resource logistics configuration. */
    public final LogisticsConfiguration logisticsConfig;

    /** Internal energy storage in EU. Max 1,000,000 EU. */
    private long energyStored;

    public static final long MAX_ENERGY = 1_000_000L;
    public static final long PASSIVE_GENERATION = 512L;

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

    /** Returns an unmodifiable view of installed modules. */
    public List<AutomatedOutpostModule> modules() {
        return Collections.unmodifiableList(modules);
    }

    /** Adds a module to this outpost. */
    public void addModule(AutomatedOutpostModule module) {
        modules.add(module);
    }

    /** Removes the module at the given index. */
    public void removeModule(int index) {
        modules.remove(index);
    }

    /** Removes all modules from this outpost. */
    public void clearModules() {
        modules.clear();
    }

    /** Returns {@code true} if at least one module of the given kind is installed. */
    public boolean hasModule(OutpostModuleKind kind) {
        for (AutomatedOutpostModule m : modules) if (m.getKind() == kind) return true;
        return false;
    }

    public boolean hasOperationalModule(OutpostModuleKind kind) {
        for (AutomatedOutpostModule m : modules) {
            if (m.getKind() == kind && m.isOperational()) return true;
        }
        return false;
    }

    /**
     * Returns the first module of the given kind, or {@code null} if not installed.
     */
    public AutomatedOutpostModule firstModule(OutpostModuleKind kind) {
        for (AutomatedOutpostModule m : modules) if (m.getKind() == kind) return m;
        return null;
    }

    public AutomatedOutpostModule firstOperationalModule(OutpostModuleKind kind) {
        for (AutomatedOutpostModule m : modules) {
            if (m.getKind() == kind && m.isOperational()) return m;
        }
        return null;
    }

    public Stream<AutomatedOutpostModule> allOperationalModules() {
        return modules.stream()
            .filter(Buildable::isOperational);
    }

    /** Package-internal mutable list accessor; used by persistence and migration only. */
    List<AutomatedOutpostModule> modulesInternal() {
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

    /**
     * Ticks the outpost logic, including passive power generation.
     */
    public void tick() {
        energyStored = Math.min(MAX_ENERGY, energyStored + PASSIVE_GENERATION);
        for (int i = 0; i < modules.size(); i++) {
            modules.get(i)
                .tick(this);
        }

        LogisticStore.updateSignalsForOutpost(this);
    }
}
