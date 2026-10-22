package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.UUID;
import java.util.stream.Stream;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class Satellite extends CelestialAsset implements SatelliteDataEndpointRegistry.Endpoint {

    private final SatelliteKind satelliteKind;

    public Satellite(ID assetId, CelestialObjectKey celestialObjectKey, Status status, SatelliteKind satelliteKind) {
        super(assetId, celestialObjectKey, Kind.SATELLITE, status);
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
    public UUID id() {
        return assetId.id();
    }

    @Override
    public CelestialObjectKey bodyKey() {
        return celestialObjectKey;
    }

    @Override
    public boolean produces() {
        return satelliteKind == SatelliteKind.PROSPECTING && status() == Status.OPERATIONAL;
    }

    @Override
    public boolean consumes() {
        return produces();
    }

    @Override
    public SatelliteDataKey producedKey() {
        return SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, celestialObjectKey);
    }

    @Override
    public SatelliteDataKey demandKey() {
        return SatelliteDataKey.origin(SatelliteDataType.PROCESSED_PROSPECTING, celestialObjectKey);
    }

    @Override
    public long amountDeciKb() {
        return 1L;
    }

    @Override
    public SatelliteDataTransferPlanner.Demand demand() {
        // Reception uses the object's bounded flow pool, independently of this satellite's own generation rate.
        UUID team = CelestialAssetStore.getTeamId(assetId);
        long capacity = Math.max(
            100L,
            SatelliteBandwidthFormatter.kilobits(
                SatelliteNetworkService.current(team)
                    .capacityKbps(celestialObjectKey)));
        return new SatelliteDataTransferPlanner.Demand(id(), bodyKey(), demandKey(), capacity);
    }

    @Override
    public long accept(SatelliteDataKey key, long amount) {
        if (!consumes() || amount <= 0L || !demandKey().matchesProduced(key)) return 0L;
        long accepted = Math.min(amount, demand().deciKb());
        SatelliteNetworkService
            .acceptProspectingFlow(CelestialAssetStore.getTeamId(assetId), celestialObjectKey, accepted);
        return accepted;
    }

}
