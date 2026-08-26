package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AssetFilterUpdatePacketTest {

    private static final UUID OWNER_TEAM = UUID.randomUUID();
    private static final UUID OTHER_TEAM = UUID.randomUUID();

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @AfterEach
    void cleanStoresAfter() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void applyRejectsFilterMutationFromAnotherTeam() {
        AutomatedFacility facility = addFacilityToServer();
        AssetFilterUpdatePacket packet = AssetFilterUpdatePacket.addFilter(facility.assetId, true, "item.diamond");

        boolean accepted = packet.apply(OTHER_TEAM);

        assertFalse(accepted);
        assertEquals(
            List.of(),
            facility.getItemFilter()
                .serialize());
        assertEquals(0, facility.getStateRevision());
    }

    private static AutomatedFacility addFacilityToServer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(OWNER_TEAM, facility);
        return facility;
    }
}
