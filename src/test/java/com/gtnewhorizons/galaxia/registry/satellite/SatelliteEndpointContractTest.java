package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

class SatelliteEndpointContractTest {

    @Test
    void arbitraryConsumerReceivesTheActualOriginForAnyOriginDemand() {
        UUID team = UUID.randomUUID();
        var origin = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var location = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        var produced = SatelliteDataKey.origin(SatelliteDataType.RESEARCH, origin);
        var store = new SatelliteDataBufferStore();
        store.finishProduction(location, produced, 10L);
        SatelliteDataKey[] received = { null };
        var sink = new SatelliteDataEndpointRegistry.Endpoint() {

            public UUID id() {
                return team;
            }

            public CelestialObjectKey bodyKey() {
                return location;
            }

            public boolean consumes() {
                return true;
            }

            public SatelliteDataKey demandKey() {
                return SatelliteDataKey.any(SatelliteDataType.RESEARCH);
            }

            public long amountDeciKb() {
                return 10L;
            }

            public long accept(SatelliteDataKey key, long amount) {
                received[0] = key;
                return amount;
            }
        };

        SatelliteDataJobService.tickEndpointsUsage(team, List.of(sink), store, SatelliteNetworkState.empty(team, 0));

        assertEquals(produced, received[0]);
        assertEquals(0L, store.pendingDeciKb(location, produced));
    }
}
