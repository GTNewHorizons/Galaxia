package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.stream.Stream;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class Satellite extends CelestialAsset {

    private final SatelliteKind satelliteKind;

    public Satellite(ID assetId, CelestialObjectId celestialObjectId, Status status, SatelliteKind satelliteKind) {
        super(assetId, celestialObjectId, Kind.SATELLITE, status, null);
        this.satelliteKind = satelliteKind;
    }

    public SatelliteKind satelliteKind() {
        return satelliteKind;
    }

    @Override
    public boolean isManageable() {
        return false;
    }

    @Override
    public boolean tryConsumeEnergy(long powerDraw) {
        return false;
    }

    @Override
    public long getEnergyStored() {
        return 0L;
    }

    @Override
    public Stream<ModuleInstance> forEachModule() {
        return Stream.empty();
    }

    @Override
    public void tick() {}

    @Override
    public long updateContents(InventoryKey item, long delta, boolean sync) {
        return 0L;
    }
}
