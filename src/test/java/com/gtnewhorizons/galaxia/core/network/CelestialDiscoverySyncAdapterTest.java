package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class CelestialDiscoverySyncAdapterTest {

    @AfterEach
    void clearClientState() {
        CelestialDiscoveryClientState.clear();
    }

    @Test
    void registeredPlanetScanRoundTripsWithoutAsteroidAdapter() {
        UUID teamId = UUID.randomUUID();
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialDiscoveryScanSnapshot expected = new CelestialDiscoveryScanSnapshot(
            teamId,
            mars,
            0.5,
            2L,
            CelestialDiscoveryCapability.PROSPECTING,
            CelestialDiscoveryScanSnapshot.Status.ACTIVE,
            mars,
            CelestialDiscoveryStep.DETECTION,
            40L);
        CelestialDiscoveryScanService scans = new CelestialDiscoveryScanService(scope -> null);
        scans.restore(teamId, List.of(expected));
        CelestialDiscoverySyncAdapter adapter = new CelestialDiscoverySyncAdapter(scans);
        ByteBuf buf = Unpooled.buffer();
        adapter.write(buf, teamId);

        adapter.read(buf)
            .applyClient();

        assertEquals(List.of(expected), CelestialDiscoveryClientState.snapshots());
    }
}
