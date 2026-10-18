package com.gtnewhorizons.galaxia.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.network.AssetCreateRequestPacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.PhysicalLocation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable.Status;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AssetEndpointStateTest {

    @BeforeAll
    static void initializeRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void physicalSiteAndSubordinateEndpointMetadataRoundTripThroughCommonAssetState() {
        var team = UUID.randomUUID();
        var body = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var site = CelestialAsset
            .create(CelestialAsset.ID.create(), body, CelestialAsset.Kind.TRANSPORT_SITE, Status.OPERATIONAL);
        var endpoint = new Endpoint(
            site.assetId.id(),
            Endpoint.Kind.LANDING_PAD,
            "arrival",
            new PhysicalLocation(-1, new BlockPos(7, 9, 11)),
            UUID.randomUUID(),
            1);
        site.registerEndpoint(endpoint);

        var restored = AssetState.decode(AssetState.encode(team, site));

        assertEquals(team, restored.teamId());
        assertEquals(site.assetId, restored.asset().assetId);
        assertEquals(
            site.endpoints(),
            restored.asset()
                .endpoints());
        assertFalse(
            restored.asset()
                .tryConsumeEnergy(1));
        site.renameEndpoint(endpoint.id(), "changed");
        AssetState.replace(team, site, restored);
        assertEquals(
            "arrival",
            site.endpoint(endpoint.id())
                .routingName());

        var station = CelestialAsset.create(body, CelestialAsset.Kind.STATION, true);
        station.registerEndpoint(
            new Endpoint(UUID.randomUUID(), Endpoint.Kind.SILO, "assembly", endpoint.physical(), null, 1));
        station.registerEndpoint(
            new Endpoint(
                UUID.randomUUID(),
                Endpoint.Kind.DOCK,
                "orbital",
                new PhysicalLocation(-1, new BlockPos(8, 9, 11)),
                null,
                2));
        assertEquals(
            station.endpoints(),
            AssetState.decode(AssetState.encode(team, station))
                .asset()
                .endpoints());
        var facility = CelestialAsset.create(body, CelestialAsset.Kind.AUTOMATED_OUTPOST, true);
        facility.registerEndpoint(
            new Endpoint(facility.assetId.id(), Endpoint.Kind.LANDING_PAD, "facility", null, null, 0));
        assertEquals(
            facility.endpoints(),
            AssetState.decode(AssetState.encode(team, facility))
                .asset()
                .endpoints());
    }

    @Test
    void physicalSiteCannotBeCreatedByRemoteAssetRequestsOrRandomIdentityFactory() {
        var body = CelestialObjectKey.registered(CelestialObjectId.MARS);
        assertThrows(
            IllegalArgumentException.class,
            () -> CelestialAsset.create(body, CelestialAsset.Kind.TRANSPORT_SITE, true));
        var request = AssetCreateRequestPacket
            .createFacility(body, "unformed", CelestialAsset.Kind.TRANSPORT_SITE, true);
        assertThrows(IllegalArgumentException.class, () -> request.apply(UUID.randomUUID(), true));
    }

    @Test
    void commonEndpointStateIsRequiredByTheNewAssetFormat() {
        var body = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var station = CelestialAsset.create(body, CelestialAsset.Kind.STATION, true);
        var encoded = AssetState.encode(UUID.randomUUID(), station);
        encoded.removeTag("endpoints");

        assertThrows(IllegalStateException.class, () -> AssetState.decode(encoded));
    }
}
