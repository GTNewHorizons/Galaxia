package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.CommonProxy;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketModule;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.ManeuverKind;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers.BlockModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers.BlockSilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.BlockGantry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import sun.misc.Unsafe;

/** Native integration contracts. Only formation and the empty server World are harness boundaries. */
final class LandingPadServiceTest {

    private static final UUID TEAM = UUID.randomUUID();
    private static final CelestialObjectKey BODY = CelestialObjectKey.registered(CelestialObjectId.MARS);
    private final List<CelestialAsset.ID> registered = new ArrayList<>();
    private static final int DIMENSION = 41777;
    private static FluidKey FUEL;
    private static FluidKey OXIDIZER;
    private WorldServer previousWorld;
    private boolean worldInstalled;
    private CommonProxy previousProxy;
    private boolean proxyInstalled;
    private Item previousModuleItem;

    @BeforeAll
    static void initialize() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        FUEL = new FluidKey(FluidRegistry.WATER, null);
        OXIDIZER = new FluidKey(FluidRegistry.LAVA, null);
        TileEntity.addMapping(FormedSilo.class, "test.galaxia.service_silo");
        TileEntity.addMapping(FormedPad.class, "test.galaxia.service_platform");
        TileEntity.addMapping(FormedAssembler.class, "test.galaxia.service_assembler");
    }

    @AfterEach
    void removeAssets() throws Exception {
        registered.forEach(CelestialAssetStore.SERVER::destroyAssetInternal);
        if (proxyInstalled) {
            Galaxia.proxy = previousProxy;
            Field item = GalaxiaItemList.class.getDeclaredField("itemInstance");
            item.setAccessible(true);
            item.set(GalaxiaItemList.RECOVERED_ROCKET_MODULE, previousModuleItem);
        }
        if (worldInstalled) {
            if (previousWorld == null) loadedWorlds().remove(DIMENSION);
            else loadedWorlds().put(DIMENSION, previousWorld);
        }
    }

    /** Integration contract: a real orbital Dock uses Station resources and permits a physical projection. */
    @Test
    void dockIsStationOwnedAndLoadsActualStationCargoAfterPaidArrival() throws Exception {
        var fixture = new Fixture(false);
        var dock = new FormedPad(CelestialAsset.Endpoint.Kind.DOCK);
        assertEquals(CelestialAsset.Endpoint.Kind.DOCK, dock.endpointKind());
        dock.machineIdentity()
            .claim(TEAM);
        dock.endpointBinding()
            .selectStandalone();
        var dockPosition = new BlockPos(4, 0, 0);
        var physical = new CelestialAsset.PhysicalLocation(DIMENSION, dockPosition);
        assertFalse(
            dock.endpointBinding()
                .formed(
                    CelestialAssetStore.SERVER,
                    dock.machineIdentity(),
                    TEAM,
                    BODY,
                    dock.endpointKind(),
                    physical,
                    null));
        dock.endpointBinding()
            .selectStation();
        assertFalse(
            dock.endpointBinding()
                .formed(
                    CelestialAssetStore.SERVER,
                    dock.machineIdentity(),
                    TEAM,
                    BODY,
                    dock.endpointKind(),
                    physical,
                    null));
        var station = new BoundStation(CelestialAsset.ID.create(), BODY);
        var stationPosition = new BlockPos(5, 0, 0);
        station.setController(stationPosition);
        var controller = new FormedStation(station, stationPosition);
        station.controller = controller;
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, station);
        registered.add(station.assetId);
        assertTrue(
            dock.endpointBinding()
                .formed(
                    CelestialAssetStore.SERVER,
                    dock.machineIdentity(),
                    TEAM,
                    BODY,
                    dock.endpointKind(),
                    physical,
                    station.assetId));
        var world = (LoadedEndpointWorld) loadedWorlds().get(DIMENSION);
        world.tiles.put(dockPosition, dock);
        world.tiles.put(stationPosition, controller);
        controller.setWorldObj(world);
        var port = new TileEntityRocketLoadingPort();
        port.xCoord = 6;
        port.setWorldObj(world);
        var portPosition = new BlockPos(6, 0, 0);
        world.tiles.put(portPosition, port);
        controller.getGraph()
            .connectPiece(stationPosition);
        controller.getGraph()
            .registerAttachment(
                stationPosition,
                portPosition,
                new StationAttachmentRegistry.ResolvedAttachment<>(port, TileEntityRocketLoadingPort.HANDLER));
        var ref = CelestialAssetStore.findEndpoint(
            dock.machineIdentity()
                .id());
        assertEquals(CelestialAsset.Location.ORBIT, ref.asset().location);
        assertEquals(1, ref.availableServiceSlots());
        assertSame(station, ref.resources());
        var cargo = ItemStackWrapper.of(new ItemStack(Items.stick));
        assertEquals(7, port.updateContents(cargo, 7, false));
        assertEquals(5000, port.updateContents(FUEL, 5000, false));
        assertEquals(5000, port.updateContents(OXIDIZER, 5000, false));
        var definition = fixture.repairable()
            .withStorage(new RocketPartDef.Storage(9, 0, Map.of()));
        var rocket = fixture.builtRocket(definition, 90);
        assertEquals(1000, rocket.refuel(FUEL, 1000, false));
        assertEquals(2000, rocket.refuel(OXIDIZER, 2000, false));
        var ground = new VehicleSchedule.Visit(UUID.randomUUID(), rocket.location(), List.of());
        var arrival = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            new VehicleSchedule.Place(
                BODY,
                VehicleSchedule.Context.ORBIT,
                new VehicleSchedule.Exact(
                    dock.machineIdentity()
                        .id(),
                    dock.endpointKind())),
            List.of(new VehicleSchedule.Transfer(UUID.randomUUID(), cargo, 7, VehicleSchedule.Direction.LOAD)));
        var schedule = new VehicleSchedule(List.of(ground, arrival), VehicleSchedule.Mode.RUN_ONCE)
            .withSafeDurability(false);
        schedule = schedule.withRouteSettings(
            schedule.routes()
                .keySet()
                .iterator()
                .next(),
            new VehicleSchedule.RouteSettings(14000, RoutePriority.PRIORITIZE_DV));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(TEAM, rocket.revision(), schedule));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(TEAM, rocket.revision()));
        for (int tick = 0; tick < 2000 && rocket.isRunning(); tick++) rocket.tick(fixture.registry, tick);
        assertFalse(
            rocket.isRunning(),
            () -> "Dock flight did not complete: " + rocket.scheduleView()
                .problemKey());
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(
            dock.machineIdentity()
                .id(),
            rocket.currentEndpointId());
        assertTrue(dock.acceptArrival(fixture.registry, rocket));
        assertTrue(rocket.allowsProjection());
        assertEquals(7, rocket.cargoAmount(cargo));
        assertEquals(0, port.getItemAmount(cargo));
        assertTrue(
            rocket.propellantAmounts()
                .get(FUEL) < 1000);
        assertTrue(
            rocket.propellantAmounts()
                .get(OXIDIZER) < 2000);
        assertEquals(5000, port.getFluidAmount(FUEL));
        assertEquals(5000, port.getFluidAmount(OXIDIZER));
        var saved = new NBTTagCompound();
        dock.writeToNBT(saved);
        var restored = new FormedPad();
        restored.readFromNBT(saved);
        assertEquals(CelestialAsset.Endpoint.Kind.DOCK, restored.endpointKind());
        assertEquals(
            dock.machineIdentity()
                .id(),
            restored.machineIdentity()
                .id());
        assertEquals(
            dock.endpointBinding()
                .assetId(),
            restored.endpointBinding()
                .assetId());
        world.tiles.remove(dockPosition);
        assertEquals(0, ref.availableServiceSlots());
        assertNull(ref.resources());
        assertEquals(CelestialAsset.Status.OPERATIONAL, station.status());
    }

    /** Integration contract: replacing a destroyed receiving Silo preserves actual moving custody and frozen work. */
    @Test
    void explicitServiceRebindRejectsUnloadThenRoutesFromTheExactHeldNodeToNewSilo() throws Exception {
        var fixture = new Fixture(true);
        var rocket = fixture.rocket(40);
        assertEquals(
            Rocket.ServiceResult.ACCEPTED,
            fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repair()));
        fixture.tick(1);
        var moving = rocket.modules()
            .getLast();
        assertEquals(
            Rocket.HolderKind.GANTRY,
            moving.holder()
                .kind());
        var heldNode = moving.holder();
        var service = rocket.service();
        var oldSilo = fixture.silo;
        oldSilo.unload();
        fixture.silo = new FormedSilo();
        register(fixture.silo, 2);
        fixture.siloTerminal.connectSilo(fixture.silo);
        var world = (LoadedEndpointWorld) loadedWorlds().get(DIMENSION);
        world.tiles.put(new BlockPos(2, 0, 0), fixture.silo);
        fixture.pad.setWorldObj(world);
        assertTrue(
            fixture.pad.selectServiceSilo(
                fixture.silo.machineIdentity()
                    .id()));
        assertFalse(
            fixture.pad.rebindServiceReturn(
                fixture.registry,
                rocket.id(),
                service.id(),
                fixture.silo.machineIdentity()
                    .id()));
        assertEquals(service, rocket.service());
        assertEquals(heldNode, moving.holder());
        oldSilo.endpointBinding()
            .destroyed(CelestialAssetStore.SERVER, oldSilo.machineIdentity());
        assertTrue(
            fixture.pad.rebindServiceReturn(
                fixture.registry,
                rocket.id(),
                service.id(),
                fixture.silo.machineIdentity()
                    .id()));
        assertEquals(heldNode, moving.holder());
        assertNull(moving.gantryTransport());
        assertEquals(
            service.id(),
            rocket.service()
                .id());
        assertEquals(
            service.repairs(),
            rocket.service()
                .repairs());
        assertEquals(
            service.returns(),
            rocket.service()
                .returns());
        fixture.tick(60);
        assertEquals(
            new Rocket.Holder(
                Rocket.HolderKind.SILO_BUFFER,
                fixture.silo.machineIdentity()
                    .id()),
            moving.holder());
        fixture.assembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot));
        fixture.assembler.injectEnergyUnits(ForgeDirection.NORTH, 10, 3);
        fixture.tick(200);
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertTrue(
            rocket.service()
                .reassembled());
        assertEquals(
            fixture.silo.machineIdentity()
                .id(),
            rocket.assemblySiloId());
        assertEquals(
            100,
            rocket.modules()
                .getFirst()
                .durabilityPercent());
    }

    /** Integration contract: explicit destruction drops each concrete module once, then recovery resumes its return. */
    @Test
    void siloBreakDropsOnceAndRecoveredManifestReturnsThroughTheRealGantry() throws Exception {
        var fixture = new Fixture(true);
        var rocket = fixture.builtRocket(fixture.repairable(), 40);
        var originalModules = rocket.modules()
            .stream()
            .map(Rocket.Module::id)
            .toList();
        var world = installNativeDrops(fixture);
        var runtime = Galaxia.proxy.serverRuntime();
        fixture.silo.setWorldObj(world);
        var oldSiloSave = new NBTTagCompound();
        fixture.silo.writeToNBT(oldSiloSave);
        var block = new BlockSilo();
        block.breakBlock(world, 2, 0, 0, block, 0);
        assertEquals(originalModules.size(), world.drops.size());
        assertEquals(Rocket.Status.INCOMPLETE, rocket.status());
        assertTrue(
            rocket.modules()
                .stream()
                .allMatch(
                    module -> module.holder()
                        .kind() == Rocket.HolderKind.DROPPED));
        assertNull(
            CelestialAssetStore.findEndpoint(
                fixture.silo.machineIdentity()
                    .id()));
        var definitions = new RocketPartRegistry();
        definitions.register(
            rocket.modules()
                .getFirst()
                .part()
                .def(),
            RocketPartCategory.STRUCTURAL);
        definitions.register(
            rocket.modules()
                .getLast()
                .part()
                .def(),
            RocketPartCategory.LIQUID_ENGINES);
        fixture.registry.restoreNBT(
            runtime.rockets()
                .serializeNBT(),
            definitions);
        rocket = fixture.registry.find(rocket.id());
        runtime.rockets()
            .clear();
        runtime.rockets()
            .register(rocket);
        var retiredController = new FormedSilo();
        retiredController.readFromNBT(oldSiloSave);
        retiredController.setWorldObj(world);
        world.tiles.put(new BlockPos(2, 0, 0), retiredController);
        block.breakBlock(world, 2, 0, 0, block, 0);
        assertEquals(originalModules.size(), world.drops.size());
        fixture.silo = new FormedSilo();
        register(fixture.silo, 2);
        world.tiles.put(new BlockPos(2, 0, 0), fixture.silo);
        fixture.siloTerminal.connectSilo(fixture.silo);
        for (int i = 0; i < world.drops.size(); i++) {
            var claim = Rocket.ModuleDrop.deserializeNBT(
                world.drops.get(i)
                    .getEntityItem()
                    .getTagCompound());
            assertTrue(
                rocket.recoverDrop(
                    TEAM,
                    claim,
                    fixture.padPlace(),
                    fixture.silo.machineIdentity()
                        .id()));
            assertFalse(
                rocket.recoverDrop(
                    TEAM,
                    claim,
                    fixture.padPlace(),
                    fixture.silo.machineIdentity()
                        .id()));
            if (i == 0) {
                fixture.tick(30);
                assertEquals(
                    Rocket.HolderKind.PAD_BUFFER,
                    rocket.module(claim.moduleId())
                        .holder()
                        .kind());
            }
        }
        assertTrue(
            rocket.service()
                .repairs()
                .isEmpty());
        fixture.tick(200);
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(
            originalModules,
            rocket.modules()
                .stream()
                .map(Rocket.Module::id)
                .toList());
        assertEquals(
            40,
            rocket.modules()
                .getFirst()
                .durabilityPercent());
        assertTrue(
            rocket.service()
                .reassembled());
    }

    /** Integration contract: a fully paid manufacture output becomes concrete before its machine is destroyed. */
    @Test
    void assemblerBreakDropsPaidManufactureBeforeTheSiloHasCollectedIt() throws Exception {
        var fixture = new Fixture(true);
        var bill = new RocketPartDef.WorkBill(Map.of(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 1L), 10, 3);
        var definition = fixture.repairable()
            .withManufactureBill(bill);
        var design = new RocketBlueprint();
        design.addPart(new RocketPartInstance(definition, 0, 0, 0, false));
        design.addPart(new RocketPartInstance(definition, 1, 0, 0, false));
        var rocket = Rocket.construct(
            TEAM,
            BODY,
            fixture.silo.machineIdentity()
                .id(),
            design);
        fixture.registry.register(rocket);
        var slot = rocket.buildSlots()
            .getFirst();
        var request = TileEntityModuleAssembler.WorkRequest.fromBill(
            slot.requestId(),
            rocket.id(),
            slot.moduleId(),
            definition.id(),
            TileEntityModuleAssembler.Operation.MANUFACTURE,
            bill);
        var target = new Rocket.TerminalTarget(
            fixture.assemblerTerminal.nodeId(),
            fixture.assembler.controllerId(),
            DIMENSION,
            fixture.assemblerTerminal.xCoord,
            fixture.assemblerTerminal.yCoord,
            fixture.assemblerTerminal.zCoord,
            new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, fixture.assembler.controllerId()));
        assertTrue(rocket.reserveWork(new Rocket.ModuleWork(slot.requestId(), slot.moduleId(), target, bill)));
        fixture.assembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot));
        assertTrue(
            fixture.assembler.tryAccept(TEAM, request)
                .accepted());
        fixture.assembler.injectEnergyUnits(ForgeDirection.NORTH, 10, 3);
        for (int tick = 0; tick < request.workTicks(); tick++) fixture.assembler.tickWork(fixture.registry);
        assertNotNull(fixture.assembler.completedOutput());
        assertTrue(
            rocket.modules()
                .isEmpty());
        var world = installNativeDrops(fixture);
        fixture.assembler.xCoord = 1;
        fixture.assembler.setWorldObj(world);
        world.tiles.put(new BlockPos(1, 0, 0), fixture.assembler);
        var block = new BlockModuleAssembler() {

            @Override
            public void blockUpdate(World world, int x, int y, int z, int meta) {}
        };
        block.breakBlock(world, 1, 0, 0, block, 0);
        assertEquals(1, world.drops.size());
        assertEquals(Rocket.Status.INCOMPLETE, rocket.status());
        var module = rocket.module(slot.moduleId());
        assertEquals(slot.requestId(), module.productionRequestId());
        assertEquals(100, module.durabilityPercent());
        assertEquals(
            Rocket.HolderKind.DROPPED,
            module.holder()
                .kind());
        assertNull(fixture.assembler.acceptedWork());
        var claim = Rocket.ModuleDrop.deserializeNBT(
            world.drops.getFirst()
                .getEntityItem()
                .getTagCompound());
        assertTrue(
            rocket.recoverDrop(
                TEAM,
                claim,
                fixture.padPlace(),
                fixture.silo.machineIdentity()
                    .id()));
        fixture.tick(200);
        assertEquals(Rocket.Status.CONSTRUCTING, rocket.status());
        assertEquals(
            1,
            rocket.modules()
                .size());
        assertEquals(
            new Rocket.Holder(
                Rocket.HolderKind.SILO_BUFFER,
                fixture.silo.machineIdentity()
                    .id()),
            module.holder());
    }

    /** Integration contract: paid repair output survives destruction as the same repaired module claim. */
    @Test
    void assemblerBreakCommitsPaidRepairBeforeDroppingAndClearsItsNativeJob() throws Exception {
        var fixture = new Fixture(true);
        var rocket = fixture.rocket(40);
        var worn = rocket.modules()
            .getFirst();
        fixture.assembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot));
        assertEquals(
            Rocket.ServiceResult.ACCEPTED,
            fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repair()));
        for (int tick = 0; tick < 100 && worn.holder()
            .kind() != Rocket.HolderKind.ASSEMBLER; tick++) fixture.tick(1);
        assertEquals(
            Rocket.HolderKind.ASSEMBLER,
            worn.holder()
                .kind());
        var paidWork = fixture.assembler.acceptedWork();
        fixture.assembler.injectEnergyUnits(ForgeDirection.NORTH, paidWork.euPerTick(), paidWork.workTicks());
        for (int tick = 0; tick < paidWork.workTicks(); tick++) fixture.assembler.tickWork(fixture.registry);
        assertNotNull(fixture.assembler.completedOutput());
        var world = installNativeDrops(fixture);
        fixture.assembler.xCoord = 1;
        fixture.assembler.setWorldObj(world);
        world.tiles.put(new BlockPos(1, 0, 0), fixture.assembler);
        var block = new BlockModuleAssembler() {

            @Override
            public void blockUpdate(World world, int x, int y, int z, int meta) {}
        };
        block.breakBlock(world, 1, 0, 0, block, 0);
        assertEquals(1, world.drops.size());
        assertEquals(Rocket.Status.INCOMPLETE, rocket.status());
        assertEquals(100, worn.durabilityPercent());
        assertTrue(
            rocket.service()
                .repaired()
                .contains(worn.id()));
        assertNull(fixture.assembler.acceptedWork());
        assertNull(fixture.assembler.completedOutput());
        assertFalse(fixture.assembler.isStructureValid());
        block.breakBlock(world, 1, 0, 0, block, 0);
        assertEquals(1, world.drops.size());
        var claim = Rocket.ModuleDrop.deserializeNBT(
            world.drops.getFirst()
                .getEntityItem()
                .getTagCompound());
        assertTrue(
            rocket.recoverDrop(
                TEAM,
                claim,
                fixture.padPlace(),
                fixture.silo.machineIdentity()
                    .id()));
        fixture.tick(200);
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(100, worn.durabilityPercent());
    }

    /** Integration contract: destroying a moving node drops its own custody and releases physical links. */
    @Test
    void gantryBreakDropsItsExactModuleAndStopsOtherServiceMovement() throws Exception {
        var fixture = new Fixture(true);
        var rocket = fixture.rocket(40);
        var worn = rocket.modules()
            .getFirst();
        fixture.assembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot));
        assertEquals(
            Rocket.ServiceResult.ACCEPTED,
            fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repair()));
        fixture.tick(1);
        assertEquals(new Rocket.Holder(Rocket.HolderKind.GANTRY, fixture.padTerminal.nodeId()), worn.holder());
        var other = rocket.modules()
            .getLast();
        var otherHolder = other.holder();
        var world = installNativeDrops(fixture);
        for (var terminal : List.of(fixture.padTerminal, fixture.assemblerTerminal, fixture.siloTerminal)) {
            terminal.setWorldObj(world);
            world.tiles.put(new BlockPos(terminal.xCoord, terminal.yCoord, terminal.zCoord), terminal);
        }
        var block = new BlockGantry() {

            @Override
            public void blockUpdate(World world, int x, int y, int z, int meta) {}
        };
        block.breakBlock(world, 0, 0, 0, block, 0);
        assertEquals(1, world.drops.size());
        assertFalse(
            fixture.assemblerTerminal.getNeighbours()
                .contains(fixture.padTerminal));
        assertEquals(Rocket.Status.INCOMPLETE, rocket.status());
        assertEquals(
            Rocket.HolderKind.DROPPED,
            worn.holder()
                .kind());
        assertNull(worn.gantryTransport());
        assertNull(worn.pendingTransfer());
        fixture.tick(40);
        assertEquals(otherHolder, other.holder());
        var claim = Rocket.ModuleDrop.deserializeNBT(
            world.drops.getFirst()
                .getEntityItem()
                .getTagCompound());
        assertEquals(worn.id(), claim.moduleId());
        assertFalse(
            rocket.recoverDrop(
                UUID.randomUUID(),
                claim,
                fixture.padPlace(),
                fixture.silo.machineIdentity()
                    .id()));
    }

    /** Product contract: a prepared Pad cannot disassemble a Rocket before its physical landing completes. */
    @Test
    void serviceRequiresTouchdownAtThisPad() {
        var fixture = new Fixture(true);
        var rocket = fixture.builtRocket(fixture.repairable(), 40);
        var holder = rocket.modules()
            .getFirst()
            .holder();
        assertEquals(
            Rocket.ServiceResult.CONFLICT,
            fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repair()));
        assertEquals(
            holder,
            rocket.modules()
                .getFirst()
                .holder());
        assertNull(rocket.service());
        assertEquals(1000, rocket.refuel(FUEL, 1000, false));
        assertEquals(2000, rocket.refuel(OXIDIZER, 2000, false));
        var departure = new VehicleSchedule.Visit(UUID.randomUUID(), rocket.location(), List.of());
        var destination = new VehicleSchedule.Visit(UUID.randomUUID(), fixture.padPlace(), List.of());
        var schedule = new VehicleSchedule(List.of(departure, destination), VehicleSchedule.Mode.RUN_ONCE)
            .withSafeDurability(false);
        for (var leg : schedule.routes()
            .keySet())
            schedule = schedule
                .withRouteSettings(leg, new VehicleSchedule.RouteSettings(14000, RoutePriority.PRIORITIZE_DV));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(TEAM, rocket.revision(), schedule));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(TEAM, rocket.revision()));
        boolean landingObserved = false;
        for (int tick = 0; tick < 2000 && rocket.isRunning(); tick++) {
            var phase = rocket.physicalManeuver();
            if (phase != null && phase.kind() == ManeuverKind.LANDING) {
                landingObserved = true;
                assertEquals(Rocket.Status.IN_FLIGHT, rocket.status());
                assertEquals(
                    Rocket.ServiceResult.CONFLICT,
                    fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repair()));
                assertNull(rocket.service());
                assertTrue(
                    rocket.modules()
                        .stream()
                        .allMatch(
                            module -> module.holder()
                                .equals(holder)));
                assertTrue(
                    rocket.modules()
                        .stream()
                        .allMatch(module -> module.gantryTransport() == null));
            }
            rocket.tick(fixture.registry, tick);
        }
        assertTrue(landingObserved, "The same-body flight must include physical landing");
        assertFalse(rocket.isRunning());
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(
            Rocket.ServiceResult.ACCEPTED,
            fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repair()));
    }

    @Test
    void allSkippedNeedsNoReturnSiloAndKeepsEveryModuleInstalled() {
        var fixture = new Fixture(false);
        var rocket = fixture.rocket(90);
        var original = rocket.modules()
            .getFirst()
            .holder();
        assertEquals(
            Rocket.ServiceResult.SKIPPED,
            fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repair()));
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(
            original,
            rocket.modules()
                .getFirst()
                .holder());
        assertNull(rocket.service());
        assertNull(fixture.silo.vehicleId());
    }

    @Test
    void missingMaterialsWaitOnPadThenActualPaidRepairAndExactSiloReassemblyComplete() throws Exception {
        var fixture = new Fixture(true);
        var rocket = fixture.rocket(40, 90);
        var schedule = rocket.schedule();
        var ids = rocket.modules()
            .stream()
            .map(Rocket.Module::id)
            .toList();
        double arrivalWear = rocket.module(ids.getFirst())
            .durabilityPercent();
        double skippedWear = rocket.module(ids.get(1))
            .durabilityPercent();
        assertEquals(
            Rocket.ServiceResult.ACCEPTED,
            fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repair()));
        assertEquals(rocket.id(), fixture.silo.vehicleId());
        fixture.tick(20);
        var worn = rocket.module(ids.getFirst());
        assertNull(rocket.workFor(worn.id()));
        assertEquals(
            new Rocket.Holder(
                Rocket.HolderKind.PAD_BUFFER,
                fixture.pad.machineIdentity()
                    .id()),
            worn.holder());
        assertEquals(arrivalWear, worn.durabilityPercent());

        fixture.assembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot));
        fixture.tick(60);
        assertEquals(arrivalWear, worn.durabilityPercent());
        assertTrue(
            rocket.service()
                .repaired()
                .isEmpty());
        assertNotNull(fixture.assembler.acceptedWork());
        assertEquals(0, fixture.assembler.storedEU());
        fixture.assembler.injectEnergyUnits(ForgeDirection.NORTH, 10, 3);
        fixture.tick(200);

        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertTrue(
            rocket.service()
                .reassembled());
        assertEquals(
            fixture.silo.machineIdentity()
                .id(),
            rocket.assemblySiloId());
        assertEquals(
            ids,
            rocket.modules()
                .stream()
                .map(Rocket.Module::id)
                .toList());
        assertEquals(100, worn.durabilityPercent());
        assertEquals(
            skippedWear,
            rocket.module(ids.get(1))
                .durabilityPercent());
        assertEquals(schedule, rocket.schedule());
        assertNull(fixture.assembler.completedOutput());
        assertEquals(0, fixture.assembler.storedEU());
    }

    @Test
    void selectedSiloWaitsForItsCurrentVehicleAndNeverAdoptsAnotherOrder() {
        var fixture = new Fixture(true);
        var first = fixture.rocket(40);
        assertEquals(
            Rocket.ServiceResult.ACCEPTED,
            fixture.pad.beginService(fixture.registry, first, UUID.randomUUID(), repair()));
        var second = fixture.builtRocket(fixture.repairable(), 40);
        assertFalse(fixture.silo.adoptServiceReturn(second));
        assertEquals(
            Rocket.ServiceResult.CONFLICT,
            fixture.pad.beginService(fixture.registry, second, UUID.randomUUID(), repair()));
        assertEquals(first.id(), fixture.silo.vehicleId());
        assertEquals(Rocket.Status.PHYSICAL, second.status());
        assertNull(second.service());
    }

    /**
     * Integration contract: skip an unavailable nearest Assembler, repair only worn modules concurrently and return one
     * intact vehicle.
     */
    @Test
    void unavailableNearestAssemblerIsSkippedWhileHealthyModulesReturnDirectlyAndTwoRepairsRunConcurrently()
        throws Exception {
        var fixture = new Fixture(true);
        var unavailableAssembler = new FormedAssembler();
        var unavailableTerminal = new Terminal();
        unavailableTerminal.xCoord = 1;
        unavailableTerminal.zCoord = 1;
        unavailableTerminal.connectAssembler(unavailableAssembler);
        var secondAssembler = new FormedAssembler();
        var secondTerminal = new Terminal();
        fixture.padTerminal.disconnect(fixture.assemblerTerminal);
        fixture.assemblerTerminal.disconnect(fixture.siloTerminal);
        fixture.assemblerTerminal.xCoord = 4;
        secondTerminal.xCoord = 3;
        secondTerminal.zCoord = 1;
        secondTerminal.connectAssembler(secondAssembler);
        fixture.siloTerminal.xCoord = 3;
        fixture.siloTerminal.zCoord = -1;
        var firstTrack = new TileEntityGantry();
        firstTrack.xCoord = 1;
        var secondTrack = new TileEntityGantry();
        secondTrack.xCoord = 2;
        var junction = new TileEntityGantry();
        junction.xCoord = 3;
        fixture.padTerminal.connect(firstTrack);
        firstTrack.connect(unavailableTerminal);
        firstTrack.connect(secondTrack);
        secondTrack.connect(junction);
        junction.connect(fixture.assemblerTerminal);
        junction.connect(secondTerminal);
        junction.connect(fixture.siloTerminal);
        var nodes = List.of(
            fixture.padTerminal,
            firstTrack,
            unavailableTerminal,
            secondTrack,
            junction,
            fixture.assemblerTerminal,
            secondTerminal,
            fixture.siloTerminal);
        var rocket = fixture.rocket(20, 30, 95);
        var modules = rocket.modules()
            .stream()
            .map(Rocket.Module::id)
            .toList();
        var repairAction = repair();
        var healthy = new HashMap<UUID, Double>();
        for (var module : rocket.modules()) {
            if (!repairAction.qualifies(module.durabilityPercent()))
                healthy.put(module.id(), module.durabilityPercent());
        }
        var directReturns = new HashSet<UUID>();
        fixture.assembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 2));
        secondAssembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 2));
        fixture.assembler.injectEnergyUnits(ForgeDirection.NORTH, 10, 3);
        secondAssembler.injectEnergyUnits(ForgeDirection.NORTH, 10, 3);
        unavailableAssembler.attachServerWorld();
        fixture.assembler.attachServerWorld();
        secondAssembler.attachServerWorld();
        assertEquals(
            Rocket.ServiceResult.ACCEPTED,
            fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repairAction));
        boolean concurrentOutbound = false;
        boolean concurrentReturn = false;
        for (int tick = 0; tick < 600 && !rocket.service()
            .reassembled(); tick++) {
            fixture.pad.tickServices(fixture.registry);
            unavailableAssembler.tickWork(fixture.registry);
            fixture.assembler.tickWork(fixture.registry);
            secondAssembler.tickWork(fixture.registry);
            for (var node : nodes) GantryAPI.tickNode(fixture.registry, node);
            long outbound = rocket.modules()
                .stream()
                .filter(
                    module -> module.gantryTransport() != null && module.gantryTransport()
                        .target()
                        .receiver()
                        .kind() == Rocket.HolderKind.ASSEMBLER)
                .count();
            long returning = rocket.modules()
                .stream()
                .filter(
                    module -> module.gantryTransport() != null && rocket.service()
                        .repaired()
                        .contains(module.id())
                        && module.gantryTransport()
                            .target()
                            .receiver()
                            .kind() == Rocket.HolderKind.SILO_BUFFER)
                .count();
            concurrentOutbound |= outbound >= 2;
            concurrentReturn |= returning >= 2;
            assertNull(unavailableAssembler.acceptedWork(), "The nearest Assembler cannot accept an unfunded repair");
            assertNull(unavailableAssembler.completedOutput());
            for (var moduleId : healthy.keySet()) {
                var module = rocket.module(moduleId);
                assertNull(rocket.workFor(moduleId), "Healthy modules must never reserve Assembler work");
                assertNotEquals(
                    Rocket.HolderKind.ASSEMBLER,
                    module.holder()
                        .kind());
                if (module.gantryTransport() != null) {
                    assertEquals(
                        Rocket.HolderKind.SILO_BUFFER,
                        module.gantryTransport()
                            .target()
                            .receiver()
                            .kind());
                    directReturns.add(moduleId);
                }
            }
            fixture.silo.tickServiceReturn(rocket);
        }
        assertTrue(concurrentOutbound, "Pad must send the second module before the first arrives");
        assertTrue(concurrentReturn, "Both repaired modules must be able to return concurrently");
        assertEquals(
            2,
            rocket.service()
                .repairs()
                .size());
        assertEquals(healthy.keySet(), directReturns);
        healthy.forEach(
            (moduleId, durability) -> assertEquals(
                durability,
                rocket.module(moduleId)
                    .durabilityPercent()));
        assertTrue(
            rocket.service()
                .reassembled());
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(
            modules,
            rocket.modules()
                .stream()
                .map(Rocket.Module::id)
                .toList());
        assertTrue(
            rocket.service()
                .repairs()
                .keySet()
                .stream()
                .allMatch(
                    module -> rocket.module(module)
                        .durabilityPercent() == 100));
        assertEquals(1, fixture.assembler.getStackInSlot(0).stackSize);
        assertEquals(1, secondAssembler.getStackInSlot(0).stackSize);
        assertEquals(rocket.id(), fixture.silo.vehicleId());
    }

    @Test
    void missingRepairBillDoesNotManufactureFreeWork() throws Exception {
        var fixture = new Fixture(true);
        var noRecipe = RocketPartDef.structural(2, "unrepairable", 1, 1, 1, null);
        var rocket = fixture.rocket(noRecipe, 40);
        double arrivalWear = rocket.modules()
            .getFirst()
            .durabilityPercent();
        assertEquals(
            Rocket.ServiceResult.ACCEPTED,
            fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repair()));
        fixture.assembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot));
        fixture.assembler.injectEnergyUnits(ForgeDirection.NORTH, 10, 10);
        fixture.tick(40);
        assertNull(
            rocket.workFor(
                rocket.modules()
                    .getFirst()
                    .id()));
        assertNull(fixture.assembler.acceptedWork());
        assertEquals(
            arrivalWear,
            rocket.modules()
                .getFirst()
                .durabilityPercent());
        assertEquals(
            Rocket.HolderKind.PAD_BUFFER,
            rocket.modules()
                .getFirst()
                .holder()
                .kind());
        assertEquals(1, fixture.assembler.getStackInSlot(0).stackSize);
    }

    @Test
    void unavailableSiloAndReloadRetainTheWholeAcceptedOrderAndPaidOutput() throws Exception {
        var fixture = new Fixture(true);
        var rocket = fixture.rocket(40, 90);
        UUID vehicleId = rocket.id();
        UUID returnSilo = fixture.silo.machineIdentity()
            .id();
        fixture.assembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot));
        assertEquals(
            Rocket.ServiceResult.ACCEPTED,
            fixture.pad.beginService(fixture.registry, rocket, UUID.randomUUID(), repair()));
        fixture.tick(60);
        fixture.silo.unload();
        fixture.assembler.injectEnergyUnits(ForgeDirection.NORTH, 10, 3);
        fixture.tick(30);
        assertFalse(
            rocket.service()
                .reassembled());
        assertNotNull(fixture.assembler.completedOutput());
        var service = rocket.service();
        var definitions = new RocketPartRegistry();
        definitions.register(
            rocket.modules()
                .getFirst()
                .part()
                .def(),
            RocketPartCategory.STRUCTURAL);
        definitions.register(
            rocket.modules()
                .getLast()
                .part()
                .def(),
            RocketPartCategory.LIQUID_ENGINES);
        var savedSilo = new NBTTagCompound();
        fixture.silo.writeToNBT(savedSilo);
        fixture.registry.restoreNBT(fixture.registry.serializeNBT(), definitions);
        fixture.silo = new FormedSilo();
        fixture.silo.readFromNBT(savedSilo);
        ((LoadedEndpointWorld) loadedWorlds().get(DIMENSION)).tiles.put(new BlockPos(2, 0, 0), fixture.silo);
        fixture.siloTerminal.connectSilo(fixture.silo);
        rocket = fixture.registry.find(vehicleId);
        assertEquals(service, rocket.service());
        assertEquals(vehicleId, fixture.silo.vehicleId());
        fixture.tick(20);
        assertEquals(Rocket.Status.DISASSEMBLED, rocket.status());
        assertNotNull(fixture.assembler.completedOutput());
        fixture.silo.reform();
        fixture.tick(200);
        assertEquals(Rocket.Status.PHYSICAL, rocket.status());
        assertEquals(returnSilo, rocket.assemblySiloId());
        assertEquals(vehicleId, rocket.id());
        assertTrue(
            rocket.service()
                .reassembled());
        assertNull(fixture.assembler.completedOutput());
        assertEquals(0, fixture.assembler.storedEU());
    }

    /** Bug regression: ordinary return flights restore the Silo's usable vehicle slot. */
    @Test
    void ordinaryReturnFlightBindsTheReceivingSiloAndPersistsItsVehicle() throws Exception {
        var fixture = new Fixture(false);
        var rocket = fixture.rocket(100);
        rocket.refuel(FUEL, 1000, false);
        rocket.refuel(OXIDIZER, 2000, false);
        var departure = new VehicleSchedule.Visit(UUID.randomUUID(), rocket.location(), List.of());
        var arrival = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            VehicleSchedule.Place.at(
                CelestialAssetStore.findEndpoint(
                    fixture.silo.machineIdentity()
                        .id())),
            List.of());
        var schedule = new VehicleSchedule(List.of(departure, arrival), VehicleSchedule.Mode.RUN_ONCE)
            .withSafeDurability(false);
        for (var leg : schedule.routes()
            .keySet())
            schedule = schedule
                .withRouteSettings(leg, new VehicleSchedule.RouteSettings(14000, RoutePriority.PRIORITIZE_DV));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(TEAM, rocket.revision(), schedule));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(TEAM, rocket.revision()));
        for (int tick = 0; tick < 2000 && rocket.isRunning(); tick++) rocket.tick(fixture.registry, tick);
        assertFalse(
            rocket.isRunning(),
            () -> rocket.scheduleView()
                .problemKey());
        assertEquals(
            fixture.silo.machineIdentity()
                .id(),
            rocket.currentEndpointId());
        assertEquals(rocket.id(), fixture.silo.vehicleId());
        var saved = new NBTTagCompound();
        fixture.silo.writeToNBT(saved);
        var restored = new FormedSilo();
        restored.readFromNBT(saved);
        assertEquals(rocket.id(), restored.vehicleId());
    }

    /** Product contract: a landing facility cannot be used as a propellant refueling stop. */
    @Test
    void refuelingActionsRequireASilo() throws Exception {
        var fixture = new Fixture(false);
        var rocket = fixture.builtRocket(fixture.repairable(), 100);
        var origin = new VehicleSchedule.Visit(UUID.randomUUID(), rocket.location(), List.of());
        var destination = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            fixture.padPlace(),
            List.of(new VehicleSchedule.Refuel(UUID.randomUUID(), Map.of(FUEL, 1000L))));
        var schedule = new VehicleSchedule(List.of(origin, destination), VehicleSchedule.Mode.RUN_ONCE);
        assertEquals(Rocket.CommandResult.BLOCKED, rocket.saveSchedule(TEAM, rocket.revision(), schedule));
        assertTrue(
            rocket.schedule()
                .visits()
                .isEmpty());
    }

    /** Integration contract: full refill waits for actual Silo supplies and resumes after a save. */
    @Test
    void fullRefillUsesInstalledCapacityAndSurvivesASupplyShortageAndReload() throws Exception {
        var fixture = new Fixture(false);
        var world = (LoadedEndpointWorld) loadedWorlds().get(DIMENSION);
        var port = new TileEntityRocketLoadingPort();
        port.xCoord = 6;
        port.setWorldObj(world);
        world.tiles.put(new BlockPos(6, 0, 0), port);
        fixture.silo.testPort = port;
        var definition = fixture.repairable()
            .withStorage(new RocketPartDef.Storage(9, 0, Map.of()));
        var rocket = fixture.builtRocket(definition, 100);
        var cargo = ItemStackWrapper.of(new ItemStack(Items.stick));
        assertEquals(7, rocket.insertCargo(cargo, 7, false));
        assertEquals(100, rocket.refuel(FUEL, 100, false));
        assertEquals(500, port.updateContents(FUEL, 500, false));
        assertEquals(2000, port.updateContents(OXIDIZER, 2000, false));
        var visit = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            rocket.location(),
            List.of(VehicleSchedule.Refuel.full(UUID.randomUUID())));
        var schedule = new VehicleSchedule(List.of(visit), VehicleSchedule.Mode.RUN_ONCE);
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(TEAM, rocket.revision(), schedule));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(TEAM, rocket.revision()));
        rocket.tick(fixture.registry, 0);
        assertTrue(rocket.isRunning(), "Full refill must wait for missing fuel");
        assertEquals(Map.of(FUEL, 600L, OXIDIZER, 2000L), rocket.propellantAmounts());
        assertEquals(0, port.getFluidAmount(FUEL));
        var definitions = new RocketPartRegistry();
        definitions.register(definition, RocketPartCategory.STRUCTURAL);
        definitions.register(fixture.flightEngine(), RocketPartCategory.LIQUID_ENGINES);
        UUID vehicleId = rocket.id();
        fixture.registry.restoreNBT(fixture.registry.serializeNBT(), definitions);
        rocket = fixture.registry.find(vehicleId);
        assertNotNull(rocket);
        assertEquals(schedule, rocket.schedule());
        assertEquals(600, port.updateContents(FUEL, 600, false));
        for (int tick = 1; tick < 100 && rocket.isRunning(); tick++) rocket.tick(fixture.registry, tick);
        assertFalse(rocket.isRunning());
        assertEquals(Map.of(FUEL, 1000L, OXIDIZER, 2000L), rocket.propellantAmounts());
        assertEquals(200, port.getFluidAmount(FUEL));
        assertEquals(0, port.getFluidAmount(OXIDIZER));
        assertEquals(7, rocket.cargoAmount(cargo));
    }

    /** Product contract: a full vehicle does not need external resources to satisfy a full-refill action. */
    @Test
    void alreadyFullRefillCompletesWithoutASupplyPort() throws Exception {
        var fixture = new Fixture(false);
        var rocket = fixture.builtRocket(fixture.repairable(), 100);
        assertEquals(1000, rocket.refuel(FUEL, 1000, false));
        assertEquals(2000, rocket.refuel(OXIDIZER, 2000, false));
        var before = rocket.propellantAmounts();
        var visit = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            rocket.location(),
            List.of(VehicleSchedule.Refuel.full(UUID.randomUUID())));
        var schedule = new VehicleSchedule(List.of(visit), VehicleSchedule.Mode.RUN_ONCE);
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(TEAM, rocket.revision(), schedule));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.start(TEAM, rocket.revision()));
        rocket.tick(fixture.registry, 0);
        assertFalse(rocket.isRunning());
        assertEquals(before, rocket.propellantAmounts());
    }

    private static VehicleSchedule.Repair repair() {
        return new VehicleSchedule.Repair(UUID.randomUUID(), 75);
    }

    /**
     * Integration contract: competing vehicles repeat paid flight and service without mixing concrete modules or
     * stores.
     */
    @Test
    void twoVehiclesRepeatTwoServiceCyclesAcrossReloadThroughOnePaidAssembler() throws Exception {
        var fixture = new Fixture(true);
        var otherPad = new FormedPad();
        var otherSilo = new FormedSilo();
        register(otherSilo, 3);
        register(otherPad, 4);
        var otherSiloTerminal = new Terminal();
        otherSiloTerminal.xCoord = 3;
        otherSiloTerminal.connectSilo(otherSilo);
        otherSilo.setGantryTerminal(otherSiloTerminal);
        var otherPadTerminal = new Terminal();
        otherPadTerminal.xCoord = 4;
        otherPadTerminal.connectPlatform(otherPad);
        otherPad.setGantryTerminal(otherPadTerminal);
        fixture.siloTerminal.connect(otherSiloTerminal);
        otherSiloTerminal.connect(otherPadTerminal);
        var world = (LoadedEndpointWorld) loadedWorlds().get(DIMENSION);
        world.tiles.put(new BlockPos(3, 0, 0), otherSilo);
        world.tiles.put(new BlockPos(4, 0, 0), otherPad);
        CelestialAssetStore.findEndpoint(
            otherPad.machineIdentity()
                .id())
            .asset()
            .setEndpointServiceSilo(
                otherPad.machineIdentity()
                    .id(),
                otherSilo.machineIdentity()
                    .id());

        var cargoPart = fixture.repairable()
            .withStorage(new RocketPartDef.Storage(9, 0, Map.of()));
        var engine = fixture.flightEngine()
            .withEngine(
                new RocketPartDef.Engine(
                    1000000,
                    1000000,
                    fixture.flightEngine()
                        .engine()
                        .propellants()))
            .withRepairBill(cargoPart.repairBill());
        var definitions = new RocketPartRegistry();
        definitions.register(cargoPart, RocketPartCategory.STRUCTURAL);
        definitions.register(engine, RocketPartCategory.LIQUID_ENGINES);
        var first = fixture.builtRocket(cargoPart, fixture.silo, engine, 40);
        var second = fixture.builtRocket(cargoPart, otherSilo, engine, 40);
        var vehicleIds = List.of(first.id(), second.id());
        var receivingSilos = Map.of(first.id(), fixture.silo, second.id(), otherSilo);
        var receivingPads = Map.of(first.id(), fixture.pad, second.id(), otherPad);
        var moduleIds = new HashMap<UUID, List<UUID>>();
        var schedules = new HashMap<UUID, VehicleSchedule>();
        var cargos = new HashMap<UUID, ItemStackWrapper>();
        var completed = new HashMap<UUID, Integer>();
        var observedServices = new HashMap<UUID, Rocket.Service>();
        var sealedFuel = new HashMap<UUID, Map<FluidKey, Long>>();
        var completedServices = new HashSet<UUID>();
        for (var rocket : fixture.registry.all()) {
            moduleIds.put(
                rocket.id(),
                rocket.modules()
                    .stream()
                    .map(Rocket.Module::id)
                    .toList());
            completed.put(rocket.id(), 0);
            var stack = new ItemStack(Items.stick);
            var tag = new NBTTagCompound();
            tag.setString(
                "shipment",
                rocket.id()
                    .toString());
            stack.setTagCompound(tag);
            var cargo = ItemStackWrapper.of(stack);
            cargos.put(rocket.id(), cargo);
            assertEquals(7, rocket.insertCargo(cargo, 7, false));
            assertEquals(1000, rocket.refuel(FUEL, 1000, false));
            assertEquals(2000, rocket.refuel(OXIDIZER, 2000, false));
            var silo = new VehicleSchedule.Visit(UUID.randomUUID(), rocket.location(), List.of());
            var orbit = new VehicleSchedule.Visit(
                UUID.randomUUID(),
                new VehicleSchedule.Place(BODY, VehicleSchedule.Context.ORBIT, null),
                List.of());
            var pad = new VehicleSchedule.Visit(
                UUID.randomUUID(),
                VehicleSchedule.Place.at(
                    CelestialAssetStore.findEndpoint(
                        receivingPads.get(rocket.id())
                            .machineIdentity()
                            .id())),
                List.of(new VehicleSchedule.Repair(UUID.randomUUID(), 100)));
            var schedule = new VehicleSchedule(List.of(silo, orbit, pad), VehicleSchedule.Mode.LOOP)
                .withSafeDurability(false);
            for (var leg : schedule.routes()
                .keySet())
                schedule = schedule
                    .withRouteSettings(leg, new VehicleSchedule.RouteSettings(14000, RoutePriority.PRIORITIZE_DV));
            schedules.put(rocket.id(), schedule);
            assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(TEAM, rocket.revision(), schedule));
            assertEquals(Rocket.CommandResult.APPLIED, rocket.start(TEAM, rocket.revision()));
        }
        fixture.assembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 8));
        fixture.assembler.injectEnergyUnits(ForgeDirection.NORTH, 10, 24);
        boolean reloaded = false;
        for (int tick = 0; tick < 12000 && completed.values()
            .stream()
            .anyMatch(count -> count < 2); tick++) {
            for (var rocket : fixture.registry.all())
                if (completed.get(rocket.id()) < 2) rocket.tick(fixture.registry, tick);
            for (var rocket : fixture.registry.all()) {
                var service = rocket.service();
                if (service != null && !observedServices.containsKey(service.id())) {
                    assertEquals(
                        moduleIds.get(rocket.id())
                            .size(),
                        service.repairs()
                            .size());
                    observedServices.put(service.id(), service);
                    sealedFuel.put(service.id(), rocket.propellantAmounts());
                }
            }
            otherPad.tickServices(fixture.registry);
            fixture.tick(1);
            GantryAPI.tickNode(fixture.registry, otherSiloTerminal);
            GantryAPI.tickNode(fixture.registry, otherPadTerminal);
            for (var rocket : fixture.registry.all()) otherSilo.tickServiceReturn(rocket);
            if (!reloaded && fixture.registry.all()
                .stream()
                .allMatch(rocket -> rocket.status() == Rocket.Status.DISASSEMBLED)
                && fixture.assembler.acceptedWork() != null) {
                var accepted = fixture.assembler.acceptedWork();
                var machine = new NBTTagCompound();
                fixture.assembler.writeToNBT(machine);
                fixture.registry.restoreNBT(fixture.registry.serializeNBT(), definitions);
                fixture.assembler.readFromNBT(machine);
                fixture.assembler.reform();
                assertEquals(accepted, fixture.assembler.acceptedWork());
                for (var rocket : fixture.registry.all()) assertEquals(
                    observedServices.get(
                        rocket.service()
                            .id()),
                    rocket.service());
                reloaded = true;
            }
            for (var rocket : fixture.registry.all()) {
                assertEquals(
                    moduleIds.get(rocket.id()),
                    rocket.modules()
                        .stream()
                        .map(Rocket.Module::id)
                        .toList());
                assertTrue(
                    rocket.modules()
                        .stream()
                        .allMatch(
                            module -> module.vehicleId()
                                .equals(rocket.id())));
                assertEquals(Map.of(cargos.get(rocket.id()), 7L), rocket.cargoContents());
                var service = rocket.service();
                if (service == null || !service.reassembled() || !completedServices.add(service.id())) continue;
                assertEquals(sealedFuel.get(service.id()), rocket.propellantAmounts());
                assertEquals(
                    receivingSilos.get(rocket.id())
                        .machineIdentity()
                        .id(),
                    rocket.currentEndpointId());
                assertTrue(
                    rocket.modules()
                        .stream()
                        .allMatch(module -> module.durabilityPercent() == 100));
                assertEquals(schedules.get(rocket.id()), rocket.schedule());
                var nextVisit = schedules.get(rocket.id())
                    .visits()
                    .getFirst()
                    .id();
                for (int wait = 0; wait < 100 && !nextVisit.equals(
                    rocket.scheduleView()
                        .activeVisitId()); wait++) {
                    rocket.tick(fixture.registry, tick);
                    assertEquals(Rocket.Status.PHYSICAL, rocket.status());
                }
                assertEquals(
                    nextVisit,
                    rocket.scheduleView()
                        .activeVisitId());
                assertEquals(sealedFuel.get(service.id()), rocket.propellantAmounts());
                completed.merge(rocket.id(), 1, Integer::sum);
            }
        }
        assertTrue(reloaded, "Both accepted orders must contend for the same paid machine across reload");
        assertEquals(
            Map.of(first.id(), 2, second.id(), 2),
            completed,
            () -> "Machine " + fixture.assembler.acceptedWork()
                + ", EU "
                + fixture.assembler.storedEU()
                + ", vehicles "
                + fixture.registry.all()
                    .stream()
                    .map(
                        rocket -> rocket.id() + " "
                            + rocket.scheduleView()
                                .problemKey()
                            + " "
                            + rocket.service()
                            + " "
                            + rocket.modules()
                                .stream()
                                .map(module -> module.id() + " " + module.holder() + " " + module.gantryTransport())
                                .toList())
                    .toList());
        assertEquals(4, completedServices.size());
        assertNull(fixture.assembler.getStackInSlot(0));
        assertEquals(0, fixture.assembler.storedEU());
        assertNull(fixture.assembler.completedOutput());
        assertEquals(
            vehicleIds,
            fixture.registry.all()
                .stream()
                .map(Rocket::id)
                .toList());
    }

    @Test
    void simpleDeliveriesKeepTheSameRocketAndCreditOnlyCommittedUnloadsAcrossReload() throws Exception {
        var fixture = new Fixture(false);
        var cargo = RocketPartDef.structural(92, "delivery_cargo", 1, 1, 100, null)
            .withStorage(new RocketPartDef.Storage(1, 0, Map.of()));
        var rocket = fixture.builtRocket(cargo, 100);
        var vehicleId = rocket.id();
        var moduleIds = rocket.modules()
            .stream()
            .map(Rocket.Module::id)
            .toList();
        assertTrue(fixture.silo.acceptArrival(fixture.registry, rocket));
        fixture.silo.testPort = new TileEntityRocketLoadingPort();
        var target = fixture.deliveryDestination();
        target.testPort = new TileEntityRocketLoadingPort();
        var iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        var gold = ItemStackWrapper.of(new ItemStack(Items.gold_ingot));
        assertEquals(150, fixture.silo.testPort.updateContents(iron, 150, false));
        assertEquals(10000, fixture.silo.testPort.updateContents(FUEL, 10000, false));
        assertEquals(20000, fixture.silo.testPort.updateContents(OXIDIZER, 20000, false));
        assertEquals(576, target.testPort.updateContents(gold, 576, false));
        var home = rocket.location();
        var destination = VehicleSchedule.Place.at(
            CelestialAssetStore.findEndpoint(
                target.machineIdentity()
                    .id()));
        var order = new Rocket.DeliveryPlan(home, destination, Map.of(iron, 150L));
        var advanced = rocket.schedule();
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveDelivery(TEAM, rocket.revision(), order));
        assertEquals(Rocket.CommandResult.STALE, rocket.saveDelivery(TEAM, rocket.revision() - 1, order));
        assertEquals(Rocket.CommandResult.FORBIDDEN, rocket.stopDelivery(UUID.randomUUID(), rocket.revision()));
        for (int tick = 0; tick < 5000 && !target.machineIdentity()
            .id()
            .equals(rocket.currentEndpointId()); tick++) fixture.deliveryTick(rocket, tick);
        assertEquals(
            target.machineIdentity()
                .id(),
            rocket.currentEndpointId());
        assertEquals(
            0,
            rocket.deliveryView()
                .delivered()
                .get(iron));
        assertTrue(rocket.isRunning());
        assertEquals(Rocket.CommandResult.APPLIED, rocket.stopDelivery(TEAM, rocket.revision()));
        assertEquals(Rocket.CommandResult.BLOCKED, rocket.clearDelivery(TEAM, rocket.revision()));
        var definitions = new RocketPartRegistry();
        definitions.register(cargo, RocketPartCategory.STRUCTURAL);
        definitions.register(fixture.flightEngine(), RocketPartCategory.LIQUID_ENGINES);
        fixture.registry.restoreNBT(fixture.registry.serializeNBT(), definitions);
        rocket = fixture.registry.find(vehicleId);
        assertEquals(
            0,
            rocket.deliveryView()
                .delivered()
                .get(iron));
        assertEquals(576, target.testPort.updateContents(gold, -576, false));
        for (int tick = 0; tick < 5000 && rocket.isRunning(); tick++) fixture.deliveryTick(rocket, tick);
        assertFalse(rocket.isRunning());
        assertEquals(home, rocket.location());
        assertEquals(
            64,
            rocket.deliveryView()
                .delivered()
                .get(iron));
        assertEquals(64, target.testPort.updateContents(iron, -Long.MAX_VALUE, true));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveDelivery(TEAM, rocket.revision(), order));
        for (int tick = 0; tick < 15000 && rocket.deliveryView()
            .enabled(); tick++) fixture.deliveryTick(rocket, tick);
        assertFalse(
            rocket.deliveryView()
                .enabled());
        assertFalse(rocket.isRunning());
        assertEquals(home, rocket.location());
        assertEquals(
            150,
            rocket.deliveryView()
                .delivered()
                .get(iron));
        assertEquals(150, target.testPort.updateContents(iron, -Long.MAX_VALUE, true));
        assertEquals(0, rocket.cargoAmount(iron));
        assertEquals(
            moduleIds,
            rocket.modules()
                .stream()
                .map(Rocket.Module::id)
                .toList());
        assertEquals(advanced, rocket.schedule());
        assertEquals(Rocket.CommandResult.APPLIED, rocket.clearDelivery(TEAM, rocket.revision()));
        assertNull(rocket.deliveryView());
        assertEquals(advanced, rocket.schedule());
    }

    @Test
    void simpleDeliveryPacksDifferentRequestsTogetherWithoutOverbookingOneCargoSlot() throws Exception {
        var fixture = new Fixture(false);
        var cargo = RocketPartDef.structural(92, "delivery_cargo", 1, 1, 100, null)
            .withStorage(new RocketPartDef.Storage(1, 0, Map.of()));
        var rocket = fixture.builtRocket(cargo, 100);
        assertTrue(fixture.silo.acceptArrival(fixture.registry, rocket));
        fixture.silo.testPort = new TileEntityRocketLoadingPort();
        var target = fixture.deliveryDestination();
        target.testPort = new TileEntityRocketLoadingPort();
        var iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        var gold = ItemStackWrapper.of(new ItemStack(Items.gold_ingot));
        fixture.silo.testPort.updateContents(iron, 65, false);
        fixture.silo.testPort.updateContents(gold, 65, false);
        fixture.silo.testPort.updateContents(FUEL, 10000, false);
        fixture.silo.testPort.updateContents(OXIDIZER, 20000, false);
        var destination = VehicleSchedule.Place.at(
            CelestialAssetStore.findEndpoint(
                target.machineIdentity()
                    .id()));
        var order = new Rocket.DeliveryPlan(rocket.location(), destination, Map.of(iron, 65L, gold, 65L));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveDelivery(TEAM, rocket.revision(), order));
        for (int tick = 0; tick < 20000 && rocket.deliveryView()
            .enabled(); tick++) fixture.deliveryTick(rocket, tick);
        assertFalse(
            rocket.deliveryView()
                .enabled());
        assertEquals(order.home(), rocket.location());
        assertEquals(
            order.requested(),
            rocket.deliveryView()
                .delivered());
        assertEquals(65, target.testPort.updateContents(iron, -Long.MAX_VALUE, true));
        assertEquals(65, target.testPort.updateContents(gold, -Long.MAX_VALUE, true));
        assertTrue(
            rocket.cargoContents()
                .isEmpty());
    }

    @Test
    void stoppingBeforeDepartureKeepsLoadedCargoAndReleasesTheSavedAdvancedRoute() throws Exception {
        var fixture = new Fixture(false);
        var cargo = RocketPartDef.structural(92, "delivery_cargo", 1, 1, 100, null)
            .withStorage(new RocketPartDef.Storage(1, 0, Map.of()));
        var rocket = fixture.builtRocket(cargo, 100);
        assertTrue(fixture.silo.acceptArrival(fixture.registry, rocket));
        fixture.silo.testPort = new TileEntityRocketLoadingPort();
        var target = fixture.deliveryDestination();
        var iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        fixture.silo.testPort.updateContents(iron, 1, false);
        var destination = VehicleSchedule.Place.at(
            CelestialAssetStore.findEndpoint(
                target.machineIdentity()
                    .id()));
        assertEquals(
            Rocket.CommandResult.APPLIED,
            rocket.saveDelivery(
                TEAM,
                rocket.revision(),
                new Rocket.DeliveryPlan(rocket.location(), destination, Map.of(iron, 1L))));
        fixture.deliveryTick(rocket, 0);
        assertTrue(rocket.isRunning());
        assertEquals(1, rocket.cargoAmount(iron));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.stopDelivery(TEAM, rocket.revision()));
        assertFalse(rocket.isRunning());
        assertEquals(Rocket.CommandResult.APPLIED, rocket.clearDelivery(TEAM, rocket.revision()));
        assertEquals(1, rocket.cargoAmount(iron));
        assertNull(rocket.deliveryView());
    }

    @Test
    void simpleDeliveryCanDepartWithOnlyPresenceOfDestinationFuelThenWaitsForTheFullRefill() throws Exception {
        var fixture = new Fixture(false);
        var cargo = RocketPartDef.structural(92, "delivery_cargo", 1, 1, 100, null)
            .withStorage(new RocketPartDef.Storage(1, 0, Map.of()));
        var engine = fixture.flightEngine()
            .withEngine(
                new RocketPartDef.Engine(
                    1000000,
                    3500,
                    Map.of(FUEL, new RocketPartDef.Propellant(1, 1), OXIDIZER, new RocketPartDef.Propellant(2, 1))));
        var rocket = fixture.builtRocket(cargo, fixture.silo, engine, 100);
        assertTrue(fixture.silo.acceptArrival(fixture.registry, rocket));
        fixture.silo.testPort = new TileEntityRocketLoadingPort();
        var target = fixture.deliveryDestination();
        target.testPort = new TileEntityRocketLoadingPort();
        var iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        fixture.silo.testPort.updateContents(iron, 1, false);
        fixture.silo.testPort.updateContents(FUEL, 10000, false);
        fixture.silo.testPort.updateContents(OXIDIZER, 20000, false);
        target.testPort.updateContents(FUEL, 1, false);
        target.testPort.updateContents(OXIDIZER, 1, false);
        var destination = VehicleSchedule.Place.at(
            CelestialAssetStore.findEndpoint(
                target.machineIdentity()
                    .id()));
        var order = new Rocket.DeliveryPlan(rocket.location(), destination, Map.of(iron, 1L));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveDelivery(TEAM, rocket.revision(), order));
        for (int tick = 0; tick < 8000 && rocket.deliveryView()
            .delivered()
            .get(iron) == 0; tick++) fixture.deliveryTick(rocket, tick);
        assertEquals(destination, rocket.location());
        assertEquals(
            1,
            rocket.deliveryView()
                .delivered()
                .get(iron));
        assertTrue(rocket.isRunning());
        assertEquals(0, target.testPort.updateContents(FUEL, -Long.MAX_VALUE, true));
        assertEquals(0, target.testPort.updateContents(OXIDIZER, -Long.MAX_VALUE, true));
        target.testPort.updateContents(FUEL, 1000, false);
        target.testPort.updateContents(OXIDIZER, 2000, false);
        for (int tick = 0; tick < 8000 && rocket.deliveryView()
            .enabled(); tick++) fixture.deliveryTick(rocket, tick);
        assertFalse(rocket.isRunning());
        assertFalse(
            rocket.deliveryView()
                .enabled());
        assertEquals(order.home(), rocket.location());
    }

    @Test
    void simpleDeliveryRejectsFilledContainersAtTheAuthoritativeCommand() throws Exception {
        var fixture = new Fixture(false);
        var cargo = RocketPartDef.structural(92, "delivery_cargo", 1, 1, 100, null)
            .withStorage(new RocketPartDef.Storage(1, 0, Map.of()));
        var rocket = fixture.builtRocket(cargo, 100);
        assertTrue(fixture.silo.acceptArrival(fixture.registry, rocket));
        var target = fixture.deliveryDestination();
        var destination = VehicleSchedule.Place.at(
            CelestialAssetStore.findEndpoint(
                target.machineIdentity()
                    .id()));
        var bucket = ItemStackWrapper.of(new ItemStack(Items.water_bucket));
        var order = new Rocket.DeliveryPlan(rocket.location(), destination, Map.of(bucket, 1L));
        assertEquals(Rocket.CommandResult.BLOCKED, rocket.saveDelivery(TEAM, rocket.revision(), order));
        assertNull(rocket.deliveryView());
        assertFalse(rocket.isRunning());
        assertTrue(
            rocket.cargoContents()
                .isEmpty());
    }

    @Test
    void simpleModeUsesBothPadsAndRepairsOnlyTheWornModuleBeforeReturningHome() throws Exception {
        var fixture = new Fixture(false);
        var iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        var bill = new RocketPartDef.WorkBill(Map.of(iron, 1L), 10, 3);
        var cargo = RocketPartDef.structural(92, "delivery_cargo", 1, 1, 100, null)
            .withStorage(new RocketPartDef.Storage(1, 0, Map.of()))
            .withRepairBill(bill);
        var rocket = fixture.builtRocket(cargo, 40);
        var moduleId = rocket.modules()
            .getFirst()
            .id();
        assertTrue(fixture.silo.acceptArrival(fixture.registry, rocket));
        fixture.silo.testPort = new TileEntityRocketLoadingPort();
        fixture.silo.testPort.updateContents(iron, 1, false);
        fixture.silo.testPort.updateContents(FUEL, 10000, false);
        fixture.silo.testPort.updateContents(OXIDIZER, 20000, false);
        fixture.assembler.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 2));
        var target = fixture.deliveryDestination();
        var destination = VehicleSchedule.Place.at(
            CelestialAssetStore.findEndpoint(
                target.machineIdentity()
                    .id()));
        var order = new Rocket.DeliveryPlan(rocket.location(), destination, Map.of(iron, 1L));
        assertEquals(Rocket.CommandResult.APPLIED, rocket.saveDelivery(TEAM, rocket.revision(), order));
        boolean destinationService = false;
        boolean homeService = false;
        for (int tick = 0; tick < 12000 && rocket.deliveryView()
            .enabled(); tick++) {
            fixture.deliveryTick(rocket, tick);
            var service = rocket.service();
            if (service != null) {
                if (service.padId()
                    .equals(
                        fixture.deliveryPad.machineIdentity()
                            .id())
                    && !service.repairs()
                        .isEmpty())
                    destinationService = true;
                if (service.padId()
                    .equals(
                        fixture.pad.machineIdentity()
                            .id()))
                    homeService = true;
            }
        }
        assertFalse(
            rocket.deliveryView()
                .enabled(),
            () -> "problem=" + rocket.scheduleView()
                .problemKey()
                + " status="
                + rocket.status()
                + " location="
                + rocket.location()
                + " service="
                + rocket.service()
                + " modules="
                + rocket.modules()
                    .stream()
                    .map(
                        module -> module.id() + ":"
                            + module.holder()
                            + ":work="
                            + rocket.workFor(module.id())
                            + ":trip="
                            + module.gantryTransport())
                    .toList());
        assertTrue(destinationService, "The destination pad must admit the worn module to paid repair");
        assertTrue(homeService, "Healthy modules must still return to the home silo through gantry");
        assertTrue(
            rocket.module(moduleId)
                .durabilityPercent() > 99);
        assertEquals(1, fixture.assembler.getStackInSlot(0).stackSize);
        assertEquals(order.home(), rocket.location());
        assertEquals(1, target.testPort.updateContents(iron, -Long.MAX_VALUE, true));
    }

    @Test
    void simpleModeWaitsForBothPadBindingsWithoutLoadingCargoOrLaunching() throws Exception {
        var fixture = new Fixture(false);
        var cargo = RocketPartDef.structural(92, "delivery_cargo", 1, 1, 100, null)
            .withStorage(new RocketPartDef.Storage(1, 0, Map.of()));
        var rocket = fixture.builtRocket(cargo, 100);
        assertTrue(fixture.silo.acceptArrival(fixture.registry, rocket));
        fixture.silo.testPort = new TileEntityRocketLoadingPort();
        var iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        fixture.silo.testPort.updateContents(iron, 1, false);
        var target = fixture.deliveryDestination();
        var homePad = CelestialAssetStore.findEndpoint(
            fixture.pad.machineIdentity()
                .id());
        var destinationPad = CelestialAssetStore.findEndpoint(
            fixture.deliveryPad.machineIdentity()
                .id());
        homePad.asset()
            .setEndpointServiceSilo(
                homePad.endpoint()
                    .id(),
                null);
        destinationPad.asset()
            .setEndpointServiceSilo(
                destinationPad.endpoint()
                    .id(),
                null);
        var destination = VehicleSchedule.Place.at(
            CelestialAssetStore.findEndpoint(
                target.machineIdentity()
                    .id()));
        assertEquals(
            Rocket.CommandResult.APPLIED,
            rocket.saveDelivery(
                TEAM,
                rocket.revision(),
                new Rocket.DeliveryPlan(rocket.location(), destination, Map.of(iron, 1L))));
        fixture.deliveryTick(rocket, 0);
        assertFalse(rocket.isRunning());
        assertEquals(
            "galaxia.vehicle.simple.waiting_home_pad",
            rocket.scheduleView()
                .problemKey());
        homePad.asset()
            .setEndpointServiceSilo(
                homePad.endpoint()
                    .id(),
                fixture.silo.machineIdentity()
                    .id());
        for (int tick = 1; tick < 30; tick++) fixture.deliveryTick(rocket, tick);
        assertFalse(rocket.isRunning());
        assertEquals(
            "galaxia.vehicle.simple.waiting_destination_pad",
            rocket.scheduleView()
                .problemKey());
        assertEquals(0, rocket.cargoAmount(iron));
        assertEquals(1, fixture.silo.testPort.updateContents(iron, -Long.MAX_VALUE, true));
        destinationPad.asset()
            .setEndpointServiceSilo(
                destinationPad.endpoint()
                    .id(),
                target.machineIdentity()
                    .id());
        for (int tick = 30; tick < 60; tick++) fixture.deliveryTick(rocket, tick);
        assertTrue(rocket.isRunning());
        assertEquals(1, rocket.cargoAmount(iron));
    }

    private final class Fixture {

        private final FormedPad pad = new FormedPad();
        private FormedSilo silo = new FormedSilo();
        private final FormedAssembler assembler = new FormedAssembler();
        private final Terminal padTerminal = new Terminal();
        private final Terminal assemblerTerminal = new Terminal();
        private final Terminal siloTerminal = new Terminal();
        private final Rocket.Registry registry = new Rocket.Registry();
        private final List<TileEntityGantry> deliveryNodes = new ArrayList<>();
        private FormedPad deliveryPad;
        private FormedSilo deliverySilo;

        private FormedSilo deliveryDestination() throws ReflectiveOperationException {
            if (deliverySilo != null) return deliverySilo;
            deliverySilo = new FormedSilo();
            deliveryPad = new FormedPad();
            register(deliverySilo, 6);
            register(deliveryPad, 4);
            var world = (LoadedEndpointWorld) loadedWorlds().get(DIMENSION);
            world.tiles.put(new BlockPos(6, 0, 0), deliverySilo);
            world.tiles.put(new BlockPos(4, 0, 0), deliveryPad);
            var bridge = new Terminal();
            bridge.xCoord = 3;
            var arrival = new Terminal();
            arrival.xCoord = 4;
            arrival.connectPlatform(deliveryPad);
            deliveryPad.setGantryTerminal(arrival);
            var rail = new Terminal();
            rail.xCoord = 5;
            var receiver = new Terminal();
            receiver.xCoord = 6;
            receiver.connectSilo(deliverySilo);
            deliverySilo.setGantryTerminal(receiver);
            siloTerminal.connect(bridge);
            bridge.connect(arrival);
            arrival.connect(rail);
            rail.connect(receiver);
            deliveryNodes.addAll(List.of(bridge, arrival, rail, receiver));
            CelestialAssetStore.findEndpoint(
                pad.machineIdentity()
                    .id())
                .asset()
                .setEndpointServiceSilo(
                    pad.machineIdentity()
                        .id(),
                    silo.machineIdentity()
                        .id());
            CelestialAssetStore.findEndpoint(
                deliveryPad.machineIdentity()
                    .id())
                .asset()
                .setEndpointServiceSilo(
                    deliveryPad.machineIdentity()
                        .id(),
                    deliverySilo.machineIdentity()
                        .id());
            deliverySilo.testPort = new TileEntityRocketLoadingPort();
            return deliverySilo;
        }

        private void deliveryTick(Rocket rocket, double epoch) throws Exception {
            assembler.injectEnergyUnits(ForgeDirection.SOUTH, 10, 1);
            rocket.tick(registry, epoch);
            tick(1);
        }

        private Fixture(boolean configureSilo) {
            register(pad, 0);
            register(silo, 2);
            padTerminal.connectPlatform(pad);
            pad.setGantryTerminal(padTerminal);
            assemblerTerminal.xCoord = 1;
            assemblerTerminal.connectAssembler(assembler);
            siloTerminal.xCoord = 2;
            siloTerminal.connectSilo(silo);
            silo.setGantryTerminal(siloTerminal);
            padTerminal.connect(assemblerTerminal);
            assemblerTerminal.connect(siloTerminal);
            try {
                var world = (LoadedEndpointWorld) unsafe().allocateInstance(LoadedEndpointWorld.class);
                world.tiles = new HashMap<>(Map.of(new BlockPos(0, 0, 0), pad, new BlockPos(2, 0, 0), silo));
                previousWorld = loadedWorlds().put(DIMENSION, world);
                worldInstalled = true;
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            if (configureSilo) CelestialAssetStore.findEndpoint(
                pad.machineIdentity()
                    .id())
                .asset()
                .setEndpointServiceSilo(
                    pad.machineIdentity()
                        .id(),
                    silo.machineIdentity()
                        .id());
        }

        private VehicleSchedule.Place padPlace() {
            var ref = CelestialAssetStore.findEndpoint(
                pad.machineIdentity()
                    .id());
            return VehicleSchedule.Place.at(ref);
        }

        private RocketPartDef repairable() {
            var price = new RocketPartDef.WorkBill(
                Map.of(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 1L),
                10,
                3);
            return RocketPartDef.structural(1, "repairable", 1, 1, 1, null)
                .withRepairBill(price);
        }

        private Rocket rocket(double... wear) {
            return rocket(repairable(), wear);
        }

        private Rocket builtRocket(RocketPartDef definition, double... wear) {
            return builtRocket(definition, silo, flightEngine(), wear);
        }

        private RocketPartDef flightEngine() {
            return new RocketPartDef(
                91,
                "service_flight_engine",
                1,
                1,
                100,
                null,
                RocketPartDef.Kind.ENGINE,
                0,
                0,
                new RocketPartDef.Storage(0, 0, Map.of(FUEL, 1000L, OXIDIZER, 2000L)),
                new RocketPartDef.Engine(
                    1000000,
                    10000,
                    Map.of(FUEL, new RocketPartDef.Propellant(1, 1), OXIDIZER, new RocketPartDef.Propellant(2, 1))),
                null,
                null);
        }

        private Rocket builtRocket(RocketPartDef definition, FormedSilo origin, RocketPartDef engine, double... wear) {
            var design = new RocketBlueprint();
            for (int i = 0; i < wear.length; i++) design.addPart(new RocketPartInstance(definition, i, 0, 0, false));
            design.addPart(new RocketPartInstance(engine, wear.length, 0, 0, false));
            var endpoint = CelestialAssetStore.findEndpoint(
                origin.machineIdentity()
                    .id());
            var rocket = Rocket.construct(TEAM, endpoint, design);
            for (int i = 0; i < wear.length; i++) rocket.acceptProduced(
                Rocket.Module.produced(
                    rocket.id(),
                    rocket.buildSlots()
                        .get(i),
                    wear[i],
                    new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId())));
            rocket.acceptProduced(
                Rocket.Module.produced(
                    rocket.id(),
                    rocket.buildSlots()
                        .getLast(),
                    100,
                    new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId())));
            assertTrue(rocket.assemble(rocket.assemblySiloId()));
            registry.register(rocket);
            return rocket;
        }

        private Rocket rocket(RocketPartDef definition, double... wear) {
            var rocket = builtRocket(definition, wear);
            assertEquals(1000, rocket.refuel(FUEL, 1000, false));
            assertEquals(2000, rocket.refuel(OXIDIZER, 2000, false));
            var ground = new VehicleSchedule.Visit(UUID.randomUUID(), rocket.location(), List.of());
            var orbit = new VehicleSchedule.Visit(
                UUID.randomUUID(),
                new VehicleSchedule.Place(BODY, VehicleSchedule.Context.ORBIT, null),
                List.of());
            var arrival = new VehicleSchedule.Visit(
                UUID.randomUUID(),
                new VehicleSchedule.Place(
                    BODY,
                    VehicleSchedule.Context.SURFACE,
                    new VehicleSchedule.Exact(
                        pad.machineIdentity()
                            .id(),
                        pad.endpointKind())),
                List.of());
            var schedule = new VehicleSchedule(List.of(ground, orbit, arrival), VehicleSchedule.Mode.RUN_ONCE)
                .withSafeDurability(false);
            for (var leg : schedule.routes()
                .keySet())
                schedule = schedule
                    .withRouteSettings(leg, new VehicleSchedule.RouteSettings(14000, RoutePriority.PRIORITIZE_DV));
            assertEquals(Rocket.CommandResult.APPLIED, rocket.saveSchedule(TEAM, rocket.revision(), schedule));
            assertEquals(Rocket.CommandResult.APPLIED, rocket.start(TEAM, rocket.revision()));
            for (int tick = 0; tick < 2000 && rocket.isRunning(); tick++) rocket.tick(registry, tick);
            assertFalse(
                rocket.isRunning(),
                () -> "Flight did not arrive: " + rocket.scheduleView()
                    .problemKey());
            assertTrue(pad.acceptArrival(registry, rocket));
            return rocket;
        }

        private void tick(int count) throws Exception {
            assembler.attachServerWorld();
            for (int i = 0; i < count; i++) {
                pad.tickServices(registry);
                if (deliveryPad != null) deliveryPad.tickServices(registry);
                assembler.tickWork(registry);
                GantryAPI.tickNode(registry, padTerminal);
                GantryAPI.tickNode(registry, assemblerTerminal);
                GantryAPI.tickNode(registry, siloTerminal);
                for (var node : deliveryNodes) GantryAPI.tickNode(registry, node);
                for (var rocket : registry.all()) {
                    silo.tickServiceReturn(rocket);
                    if (deliverySilo != null) deliverySilo.tickServiceReturn(rocket);
                }
            }
        }
    }

    private void register(IRocketControllerTE.TransportEndpoint controller, int x) {
        controller.machineIdentity()
            .claim(TEAM);
        controller.endpointBinding()
            .selectStandalone();
        assertTrue(
            controller.endpointBinding()
                .formed(
                    CelestialAssetStore.SERVER,
                    controller.machineIdentity(),
                    TEAM,
                    BODY,
                    controller.endpointKind(),
                    new CelestialAsset.PhysicalLocation(DIMENSION, new BlockPos(x, 0, 0)),
                    null));
        registered.add(
            controller.endpointBinding()
                .assetId());
    }

    private static final class FormedPad extends TileEntityTransportPlatform {

        private FormedPad() {
            this(CelestialAsset.Endpoint.Kind.LANDING_PAD);
        }

        private FormedPad(CelestialAsset.Endpoint.Kind kind) {
            super(kind);
            structureValid = true;
        }

        @Override
        public void markDirty() {}
    }

    private static final class FormedSilo extends TileEntitySilo {

        private TileEntityRocketLoadingPort testPort;

        /** Harness exception: attachment capture is supplied without reconstructing the multiblock. */
        @Override
        public com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory loadingInventory() {
            return testPort == null ? super.loadingInventory() : testPort;
        }

        private FormedSilo() {
            structureValid = true;
        }

        private void unload() {
            structureValid = false;
            endpointBinding().unavailable(CelestialAssetStore.SERVER);
        }

        private void reform() {
            structureValid = true;
            endpointBinding().formed(
                CelestialAssetStore.SERVER,
                machineIdentity(),
                TEAM,
                BODY,
                endpointKind(),
                new CelestialAsset.PhysicalLocation(DIMENSION, new BlockPos(2, 0, 0)),
                null);
        }

        @Override
        public void markDirty() {}
    }

    private static final class FormedAssembler extends TileEntityModuleAssembler {

        private FormedAssembler() {
            super(new SupplyLimits(2, 1, 1000, 1000, 100));
            machineIdentity().claim(TEAM);
            structureValid = true;
        }

        /** Harness exception: this tick only needs World.isRemote and no chunk simulation. */
        private void attachServerWorld() throws Exception {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            worldObj = (World) ((Unsafe) field.get(null)).allocateInstance(WorldServer.class);
        }

        private void reform() {
            structureValid = true;
        }

        @Override
        protected boolean checkStructure() {
            return true;
        }

        @Override
        public void markDirty() {}
    }

    /** Harness exception: normal runtime and item registration are provided without starting FML or chunks. */
    private LoadedEndpointWorld installNativeDrops(Fixture fixture) throws Exception {
        var runtime = CelestialServerRuntime.create();
        fixture.registry.all()
            .forEach(runtime.rockets()::register);
        previousProxy = Galaxia.proxy;
        proxyInstalled = true;
        Galaxia.proxy = new CommonProxy() {

            @Override
            public CelestialServerRuntime serverRuntime() {
                return runtime;
            }
        };
        Field item = GalaxiaItemList.class.getDeclaredField("itemInstance");
        item.setAccessible(true);
        previousModuleItem = (Item) item.get(GalaxiaItemList.RECOVERED_ROCKET_MODULE);
        item.set(GalaxiaItemList.RECOVERED_ROCKET_MODULE, new ItemRocketModule());
        var world = (LoadedEndpointWorld) loadedWorlds().get(DIMENSION);
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(world, new WorldProviderSurface());
        world.provider.dimensionId = DIMENSION;
        world.drops = new ArrayList<>();
        return world;
    }

    /** Harness exception: isolates MinecraftServer's loaded-controller lookup, keeping the real Station inventory. */
    private static final class BoundStation extends Station {

        private FormedStation controller;

        private BoundStation(CelestialAsset.ID id, CelestialObjectKey body) {
            super(id, body, CelestialAsset.Status.OPERATIONAL);
        }

        @Override
        public TileStation getTileController() {
            return controller;
        }
    }

    /** Harness exception: formation supplies the real Station graph without structure/chunk generation. */
    private static final class FormedStation extends TileStation {

        private final Station station;

        private FormedStation(Station station, BlockPos position) {
            this.station = station;
            here = position;
            graph = new StationGraph(this);
            structureValid = true;
        }

        @Override
        public Station getBackingStation() {
            return station;
        }
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, WorldServer> loadedWorlds() throws ReflectiveOperationException {
        Field field = DimensionManager.class.getDeclaredField("worlds");
        field.setAccessible(true);
        return (Map<Integer, WorldServer>) field.get(null);
    }

    /** Harness exception: only real native tile lookup is modeled, without Minecraft chunk/server startup. */
    private static final class LoadedEndpointWorld extends WorldServer {

        private Map<BlockPos, TileEntity> tiles;
        private List<EntityItem> drops;

        private LoadedEndpointWorld() {
            super(null, null, "native_endpoint_test", DIMENSION, null, null);
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return tiles.containsKey(new BlockPos(x, y, z));
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return tiles.get(new BlockPos(x, y, z));
        }

        @Override
        public void removeTileEntity(int x, int y, int z) {
            tiles.remove(new BlockPos(x, y, z));
        }

        @Override
        public void markBlockForUpdate(int x, int y, int z) {}

        @Override
        public net.minecraft.block.Block getBlock(int x, int y, int z) {
            return net.minecraft.init.Blocks.air;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return 0;
        }

        @Override
        public void markTileEntityChunkModified(int x, int y, int z, TileEntity tile) {}

        @Override
        public boolean spawnEntityInWorld(Entity entity) {
            if (entity instanceof EntityItem item) drops.add(item);
            return true;
        }
    }

    private static final class Terminal extends TileEntityGantryTerminal {

        @Override
        public int dimension() {
            return DIMENSION;
        }
    }
}
