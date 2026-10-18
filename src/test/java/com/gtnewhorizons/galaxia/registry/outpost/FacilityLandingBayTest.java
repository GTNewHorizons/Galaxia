package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/** Product contract: a logical endpoint needs its real completed, enabled and paid landing capability. */
final class FacilityLandingBayTest {

    @BeforeAll
    static void initialize() {
        GalaxiaTestBootstrap.ensureFacilityModules();
        FacilityModuleRegistry.registerLandingBay(Map.of(new ItemStack(Items.iron_ingot), 1L));
    }

    @Test
    void onlyAnEnabledActuallyPoweredBayMakesTheLogicalEndpointAvailable() {
        var facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        var bay = FacilityModuleKind.LANDING_BAY
            .create(StationTileCoord.of(1, 0), FacilityModuleKind.LANDING_BAY.defaultShape(), ModuleTier.NONE);
        bay.updateStatus(Buildable.Status.OPERATIONAL);
        facility.addModule(bay);
        facility.stationLayout()
            .place(bay);
        var endpoint = facility.endpoint(facility.assetId.id());
        assertNotNull(endpoint);
        assertEquals(CelestialAsset.Endpoint.Kind.DOCK, endpoint.kind());
        assertNull(endpoint.physical());
        var resolved = new CelestialAssetStore.EndpointRef(facility, endpoint);
        assertNull(resolved.resources());
        assertEquals(0, facility.availableLandingSlots());
        facility.tick();
        assertEquals(0, facility.availableLandingSlots());
        facility.setEnergyStored(bay.powerDrawEuPerTick());
        facility.tick();
        assertEquals(endpoint.serviceSlots(), facility.availableLandingSlots());
        assertEquals(endpoint.serviceSlots(), resolved.availableServiceSlots());
        assertSame(facility, resolved.resources());
        assertEquals(0, facility.getEnergyStored());
        var id = endpoint.id();
        facility.renameEndpoint(id, "orbital-freight");
        bay.setEnabled(false);
        assertEquals(0, facility.availableLandingSlots());
        assertEquals(
            id,
            facility.endpoint(id)
                .id());
        assertEquals(
            "orbital-freight",
            facility.endpoint(id)
                .routingName());
        assertEquals(
            0,
            facility.endpoint(id)
                .serviceSlots());
        assertNull(resolved.resources());
    }
}
