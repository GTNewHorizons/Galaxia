package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.PhysicalLocation;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialAssetEndpointTest {

    @BeforeAll
    static void initializeRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void stationOwnsIndependentEndpointIdentityAndRenamingNeverChangesItsPhysicalBinding() {
        var station = CelestialAsset
            .create(CelestialObjectKey.registered(CelestialObjectId.MARS), CelestialAsset.Kind.STATION, true);
        var endpoint = new Endpoint(
            UUID.randomUUID(),
            Endpoint.Kind.SILO,
            "assembly",
            new PhysicalLocation(0, new BlockPos(3, 5, 7)),
            null,
            1);

        assertTrue(station.registerEndpoint(endpoint));
        assertFalse(station.registerEndpoint(endpoint));
        assertEquals(endpoint, station.endpoint(endpoint.id()));
        assertThrows(
            UnsupportedOperationException.class,
            () -> station.endpoints()
                .clear());
        var relocated = new Endpoint(
            endpoint.id(),
            endpoint.kind(),
            "other",
            new PhysicalLocation(0, new BlockPos(4, 5, 7)),
            null,
            1);
        assertThrows(IllegalArgumentException.class, () -> station.registerEndpoint(relocated));

        station.renameEndpoint(endpoint.id(), "reassembly");

        assertEquals(
            endpoint.id(),
            station.endpoint(endpoint.id())
                .id());
        assertEquals(
            endpoint.physical(),
            station.endpoint(endpoint.id())
                .physical());
        assertEquals(
            "reassembly",
            station.endpoint(endpoint.id())
                .routingName());
        assertTrue(station.removeEndpoint(endpoint.id()));
        assertFalse(station.removeEndpoint(endpoint.id()));
        assertTrue(
            station.endpoints()
                .isEmpty());
    }

    @Test
    void endpointCapabilityRespectsPhysicalAndLogicalAssetOwnership() {
        var body = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var satellite = CelestialAsset.create(body, CelestialAsset.Kind.SATELLITE, true);
        var physical = new PhysicalLocation(0, new BlockPos(3, 5, 7));
        assertThrows(
            IllegalArgumentException.class,
            () -> satellite
                .registerEndpoint(new Endpoint(satellite.assetId.id(), Endpoint.Kind.DOCK, "invalid", null, null, 1)));
        var station = CelestialAsset.create(body, CelestialAsset.Kind.STATION, true);
        assertThrows(
            IllegalArgumentException.class,
            () -> station.registerEndpoint(
                new Endpoint(station.assetId.id(), Endpoint.Kind.SILO, "invalid", physical, null, 1)));
        var facility = CelestialAsset.create(body, CelestialAsset.Kind.AUTOMATED_OUTPOST, true);
        assertTrue(
            facility.registerEndpoint(
                new Endpoint(facility.assetId.id(), Endpoint.Kind.LANDING_PAD, "logical", null, null, 2)));
        assertThrows(
            IllegalArgumentException.class,
            () -> facility
                .registerEndpoint(new Endpoint(UUID.randomUUID(), Endpoint.Kind.LANDING_PAD, "second", null, null, 1)));
    }
}
