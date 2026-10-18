package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.PhysicalLocation;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.IRocketControllerTE.EndpointBinding;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.IRocketControllerTE.MachineIdentity;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class NativeTransportEndpointTest {

    private static final UUID TEAM = UUID.randomUUID();
    private static final CelestialObjectKey BODY = CelestialObjectKey.registered(CelestialObjectId.MARS);
    private static final PhysicalLocation LOCATION = new PhysicalLocation(7, new BlockPos(10, 20, 30));

    @BeforeAll
    static void initialize() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    /** Product contract: missing loaded membership never invents a standalone site. */
    @Test
    void unprovenStationMembershipWaitsForAnExplicitStandaloneChoice() {
        var store = new CelestialAssetStore();
        var machine = machine();
        var binding = new EndpointBinding();
        assertFalse(binding.formed(store, machine, TEAM, BODY, Endpoint.Kind.SILO, LOCATION, null));
        assertTrue(
            store.allAssetsInternal()
                .isEmpty());
        binding.selectStandalone();
        assertTrue(binding.formed(store, machine, TEAM, BODY, Endpoint.Kind.SILO, LOCATION, null));
        var site = store.findAssetInternal(binding.assetId());
        assertEquals(machine.id(), site.assetId.id());
        assertEquals(CelestialAsset.Kind.TRANSPORT_SITE, site.kind);
        assertEquals(
            machine.id(),
            site.endpoint(machine.id())
                .id());
        assertEquals(
            LOCATION,
            site.endpoint(machine.id())
                .physical());
        assertEquals(
            site,
            store.findEndpointInternal(machine.id())
                .asset());
    }

    /** Integration contract: native save/reload and reform retain exact identity and settings. */
    @Test
    void reformAndReloadKeepIdentityAndSelectedReturnSilo() {
        var store = new CelestialAssetStore();
        var machine = machine();
        var binding = new EndpointBinding();
        binding.selectStandalone();
        binding.formed(store, machine, TEAM, BODY, Endpoint.Kind.LANDING_PAD, LOCATION, null);
        var site = store.findAssetInternal(binding.assetId());
        var returnSilo = UUID.randomUUID();
        site.renameEndpoint(machine.id(), "service-pad");
        site.setEndpointServiceSilo(machine.id(), returnSilo);
        binding.unavailable(store);
        assertEquals(CelestialAsset.Status.DISABLED, site.status());

        var saved = new NBTTagCompound();
        machine.writeToNBT(saved);
        binding.writeToNBT(saved);
        var restoredMachine = new MachineIdentity();
        var restored = new EndpointBinding();
        restoredMachine.readFromNBT(saved);
        restored.readFromNBT(saved);
        assertTrue(restored.formed(store, restoredMachine, TEAM, BODY, Endpoint.Kind.LANDING_PAD, LOCATION, null));
        assertEquals(machine.id(), restoredMachine.id());
        assertEquals(binding.assetId(), restored.assetId());
        assertEquals(CelestialAsset.Status.OPERATIONAL, site.status());
        assertEquals(
            "service-pad",
            site.endpoint(machine.id())
                .routingName());
        assertEquals(
            returnSilo,
            site.endpoint(machine.id())
                .serviceSiloId());
        assertEquals(
            LOCATION,
            site.endpoint(machine.id())
                .physical());
        assertFalse(restored.formed(store, restoredMachine, TEAM, BODY, Endpoint.Kind.LANDING_PAD, LOCATION, null));
    }

    /** Product contract: a subordinate controller cannot disable or destroy its Station. */
    @Test
    void stationBindingOnlyChangesTheSubordinateEndpoint() {
        var store = new CelestialAssetStore();
        var station = CelestialAsset.create(BODY, CelestialAsset.Kind.STATION, true);
        store.registerAssetInternal(TEAM, station);
        var machine = machine();
        var binding = new EndpointBinding();
        assertTrue(binding.formed(store, machine, TEAM, BODY, Endpoint.Kind.SILO, LOCATION, station.assetId));
        assertEquals(station.assetId, binding.assetId());
        assertNotEquals(station.assetId.id(), machine.id());
        binding.unavailable(store);
        assertEquals(CelestialAsset.Status.OPERATIONAL, station.status());
        assertNotNull(station.endpoint(machine.id()));
        assertTrue(binding.destroyed(store, machine));
        assertSame(station, store.findAssetInternal(station.assetId));
        assertEquals(CelestialAsset.Status.OPERATIONAL, station.status());
        assertNull(station.endpoint(machine.id()));
        assertFalse(binding.formed(store, machine, TEAM, BODY, Endpoint.Kind.SILO, LOCATION, station.assetId));
    }

    /** Product contract: permanent replacement never revives or redirects an old exact endpoint. */
    @Test
    void permanentDestructionRequiresANewMachineIdentity() {
        var store = new CelestialAssetStore();
        var machine = machine();
        var binding = new EndpointBinding();
        binding.selectStandalone();
        binding.formed(store, machine, TEAM, BODY, Endpoint.Kind.LANDING_PAD, LOCATION, null);
        var oldId = binding.assetId();
        assertTrue(binding.destroyed(store, machine));
        assertNull(store.findEndpointInternal(machine.id()));
        assertFalse(binding.formed(store, machine, TEAM, BODY, Endpoint.Kind.LANDING_PAD, LOCATION, null));
        assertNull(store.findAssetInternal(oldId));
        var replacement = machine();
        var newBinding = new EndpointBinding();
        newBinding.selectStandalone();
        assertTrue(newBinding.formed(store, replacement, TEAM, BODY, Endpoint.Kind.LANDING_PAD, LOCATION, null));
        assertNotEquals(oldId, newBinding.assetId());
    }

    /** Product contract: saved physical identity cannot be rebound to a new position or owner. */
    @Test
    void exactBindingRejectsDifferentPhysicalLocationOrTeam() {
        var store = new CelestialAssetStore();
        var machine = machine();
        var binding = new EndpointBinding();
        binding.selectStandalone();
        binding.formed(store, machine, TEAM, BODY, Endpoint.Kind.SILO, LOCATION, null);
        assertThrows(
            IllegalStateException.class,
            () -> binding.formed(
                store,
                machine,
                TEAM,
                BODY,
                Endpoint.Kind.SILO,
                new PhysicalLocation(7, new BlockPos(11, 20, 30)),
                null));
        assertFalse(binding.formed(store, machine, UUID.randomUUID(), BODY, Endpoint.Kind.SILO, LOCATION, null));
        assertThrows(IllegalStateException.class, binding::selectStandalone);
    }

    private static MachineIdentity machine() {
        var machine = new MachineIdentity();
        machine.claim(TEAM);
        return machine;
    }
}
