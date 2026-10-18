package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.CommonProxy;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.IRocketControllerTE;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/** Integration contracts for concrete custody, durable movement and bounded physical-trip claims. */
final class GantryTransportTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
        TileEntity.addMapping(TileEntityGantry.class, "test.galaxia.transport_node");
        TileEntity.addMapping(Terminal.class, "test.galaxia.transport_terminal");
    }

    @Test
    void firstClientProjectionAfterHandoffUsesThisModulesRouteInsteadOfPreviousTraffic() {
        var runtime = CelestialServerRuntime.create();
        var previousProxy = Galaxia.proxy;
        Galaxia.proxy = new CommonProxy() {

            @Override
            public CelestialServerRuntime serverRuntime() {
                return runtime;
            }
        };
        try {
            var route = new Route();
            var registry = runtime.rockets();
            Rocket reverse = vehicle(route.right, registry);
            assertEquals(
                GantryAPI.DispatchResult.ACCEPTED,
                dispatch(registry, reverse, UUID.randomUUID(), route.right, route.left));
            route.tick(registry, 40);
            assertEquals(
                holder(route.left),
                reverse.modules()
                    .getFirst()
                    .holder());

            Rocket forward = vehicle(route.left, registry);
            assertEquals(
                GantryAPI.DispatchResult.ACCEPTED,
                dispatch(registry, forward, UUID.randomUUID(), route.left, route.right));
            for (int i = 0; i < 30 && GantryAPI.heldModule(registry, route.left.nodeId()) != null; i++) {
                GantryAPI.tickNode(registry, route.left);
            }
            assertEquals(
                new Rocket.Holder(Rocket.HolderKind.GANTRY, route.middle.nodeId()),
                forward.modules()
                    .getFirst()
                    .holder());
            var client = new TileEntityGantry();
            client.onDataPacket(null, (S35PacketUpdateTileEntity) route.middle.getDescriptionPacket());
            assertNotNull(client.getDirection());
            assertEquals(1, client.getDirection().xCoord);
            assertEquals(0, client.getDirection().yCoord);
            assertEquals(0, client.getDirection().zCoord);
        } finally {
            Galaxia.proxy = previousProxy;
        }
    }

    @Test
    void followingModulesShareACorridorAndKeepTheirCustodyAfterReload() {
        var route = new Route();
        var registry = new Rocket.Registry();
        Rocket leader = vehicle(route.left, registry);
        Rocket follower = vehicle(route.left, registry);
        UUID leaderId = leader.modules()
            .getFirst()
            .id();
        UUID followerId = follower.modules()
            .getFirst()
            .id();
        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, leader, UUID.randomUUID(), route.left, route.right));
        for (int i = 0; i < 30 && GantryAPI.heldModule(registry, route.left.nodeId()) != null; i++) {
            GantryAPI.tickNode(registry, route.left);
        }
        assertEquals(
            new Rocket.Holder(Rocket.HolderKind.GANTRY, route.middle.nodeId()),
            leader.module(leaderId)
                .holder());
        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, follower, UUID.randomUUID(), route.left, route.right));
        GantryAPI.tickNode(registry, route.middle);
        GantryAPI.tickNode(registry, route.left);
        assertNotNull(
            leader.module(leaderId)
                .gantryTransport());
        assertNotNull(
            follower.module(followerId)
                .gantryTransport());

        var nodes = new TileEntityGantry[] { route.left, route.middle, route.right };
        var restoredRoute = new Route();
        var restoredNodes = new TileEntityGantry[] { restoredRoute.left, restoredRoute.middle, restoredRoute.right };
        for (int i = 0; i < nodes.length; i++) {
            var tag = new NBTTagCompound();
            nodes[i].writeToNBT(tag);
            restoredNodes[i].readFromNBT(tag);
        }
        var saved = registry.serializeNBT();
        registry.restoreNBT(saved, definitions());
        restoredRoute.left.connect(restoredRoute.middle);
        restoredRoute.middle.connect(restoredRoute.right);
        restoredRoute.tick(registry, 80);
        var restoredLeader = registry.find(leader.id())
            .module(leaderId);
        var restoredFollower = registry.find(follower.id())
            .module(followerId);
        assertEquals(holder(restoredRoute.right), restoredLeader.holder());
        assertEquals(holder(restoredRoute.right), restoredFollower.holder());
        assertNull(restoredLeader.gantryTransport());
        assertNull(restoredFollower.gantryTransport());
        assertEquals(43, restoredLeader.durabilityPercent());
        assertEquals(43, restoredFollower.durabilityPercent());
    }

    @Test
    void branchedDeliveriesAndReturnsShareTheTrunkWithoutDeadlock() {
        var route = new Route();
        var entry = new TileEntityGantry();
        entry.xCoord = 1;
        route.middle.xCoord = 2;
        route.right.xCoord = 3;
        route.left.disconnect(route.middle);
        route.left.connect(entry);
        entry.connect(route.middle);
        var branch = new Terminal();
        branch.xCoord = 2;
        branch.zCoord = 1;
        route.middle.connect(branch);
        var registry = new Rocket.Registry();
        Rocket first = vehicle(route.left, registry);
        Rocket second = vehicle(route.left, registry);
        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, first, UUID.randomUUID(), route.left, route.right));
        for (int i = 0; i < 30 && GantryAPI.heldModule(registry, route.left.nodeId()) != null; i++) {
            GantryAPI.tickNode(registry, route.left);
        }
        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, second, UUID.randomUUID(), route.left, branch));
        var nodes = new TileEntityGantry[] { route.left, entry, route.middle, route.right, branch };
        tick(registry, 80, nodes);
        assertEquals(
            holder(route.right),
            first.modules()
                .getFirst()
                .holder());
        assertEquals(
            holder(branch),
            second.modules()
                .getFirst()
                .holder());

        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, first, UUID.randomUUID(), route.right, route.left));
        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, second, UUID.randomUUID(), branch, route.left));
        tick(registry, 80, nodes);
        assertEquals(
            holder(route.left),
            first.modules()
                .getFirst()
                .holder());
        assertEquals(
            holder(route.left),
            second.modules()
                .getFirst()
                .holder());
        assertNull(
            first.modules()
                .getFirst()
                .gantryTransport());
        assertNull(
            second.modules()
                .getFirst()
                .gantryTransport());
    }

    @Test
    void independentBranchesCanQueueAtTheSameReceiverTerminal() {
        var route = new Route();
        var branch = new Terminal();
        branch.zCoord = 1;
        branch.connect(route.left);
        var registry = new Rocket.Registry();
        Rocket first = vehicle(route.right, registry);
        Rocket second = vehicle(branch, registry);
        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, first, UUID.randomUUID(), route.right, route.left));
        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, second, UUID.randomUUID(), branch, route.left));
        tick(registry, 80, route.left, route.middle, route.right, branch);
        assertEquals(
            holder(route.left),
            first.modules()
                .getFirst()
                .holder());
        assertEquals(
            holder(route.left),
            second.modules()
                .getFirst()
                .holder());
    }

    @Test
    void passedTrackIsReleasedWhileThePreviousReceiverIsBlocked() {
        var route = new Route();
        var branch = new Terminal();
        branch.xCoord = 1;
        branch.zCoord = 1;
        route.middle.connect(branch);
        var registry = new Rocket.Registry();
        Rocket first = vehicle(route.left, registry);
        Rocket second = vehicle(route.left, registry);
        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, first, UUID.randomUUID(), route.left, route.right));
        route.right.accepting = false;
        route.tick(registry, 40);
        assertEquals(
            new Rocket.Holder(Rocket.HolderKind.GANTRY, route.right.nodeId()),
            first.modules()
                .getFirst()
                .holder());
        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, second, UUID.randomUUID(), route.left, branch));
        tick(registry, 80, route.left, route.middle, route.right, branch);
        assertEquals(
            holder(branch),
            second.modules()
                .getFirst()
                .holder());
        assertNotNull(
            first.modules()
                .getFirst()
                .gantryTransport());
        route.right.accepting = true;
        route.tick(registry, 10);
        assertEquals(
            holder(route.right),
            first.modules()
                .getFirst()
                .holder());
    }

    @Test
    void overlappingCounterflowWaitsAtSourceAndEachExactModuleReachesItsOwnReceiver() {
        var route = new Route();
        var registry = new Rocket.Registry();
        Rocket first = vehicle(route.left, registry);
        Rocket second = vehicle(route.right, registry);
        var firstModule = first.modules()
            .getFirst();
        var secondModule = second.modules()
            .getFirst();
        UUID firstWork = UUID.randomUUID();
        UUID secondWork = UUID.randomUUID();

        assertEquals(GantryAPI.DispatchResult.ACCEPTED, dispatch(registry, first, firstWork, route.left, route.right));
        assertEquals(
            GantryAPI.DispatchResult.ALREADY_ACTIVE,
            dispatch(registry, first, firstWork, route.left, route.right));
        assertEquals(
            GantryAPI.DispatchResult.BUSY_ROUTE,
            dispatch(registry, second, secondWork, route.right, route.left));
        assertEquals(holder(route.right), secondModule.holder());
        assertNull(secondModule.gantryTransport());
        route.tick(registry, 40);
        assertEquals(holder(route.right), firstModule.holder());
        assertNull(firstModule.gantryTransport());
        assertEquals(
            GantryAPI.DispatchResult.ACCEPTED,
            dispatch(registry, second, secondWork, route.right, route.left));
        route.tick(registry, 40);
        assertEquals(holder(route.left), secondModule.holder());
        assertEquals(43, firstModule.durabilityPercent());
        assertEquals(43, secondModule.durabilityPercent());
        assertEquals(first.id(), firstModule.vehicleId());
        assertEquals(second.id(), secondModule.vehicleId());
    }

    @Test
    void rejectedFinalHandoffRetainsCustodyAndReloadedTripRetriesTheExactReceiver() {
        var route = new Route();
        var registry = new Rocket.Registry();
        Rocket rocket = vehicle(route.left, registry);
        UUID work = UUID.randomUUID();
        dispatch(registry, rocket, work, route.left, route.right);
        route.right.accepting = false;
        route.tick(registry, 40);
        var module = rocket.modules()
            .getFirst();
        assertEquals(new Rocket.Holder(Rocket.HolderKind.GANTRY, route.right.nodeId()), module.holder());
        assertNotNull(module.pendingTransfer());
        assertEquals(
            1,
            module.gantryTransport()
                .progress());
        var saved = rocket.serializeNBT();
        Rocket restored = Rocket.deserializeNBT(saved, definitions());
        registry.clear();
        registry.register(restored);
        route.right.accepting = true;
        GantryAPI.tickNode(registry, route.right);
        assertEquals(
            holder(route.right),
            restored.modules()
                .getFirst()
                .holder());
        assertNull(
            restored.modules()
                .getFirst()
                .gantryTransport());
        assertTrue(restored.finishGantryTransport(module.id(), work));
    }

    @Test
    void separateNodeAndManifestReloadPreservesPendingHopAndPartialProgress() {
        var route = new Route();
        var registry = new Rocket.Registry();
        Rocket rocket = vehicle(route.left, registry);
        dispatch(registry, rocket, UUID.randomUUID(), route.left, route.right);
        for (int i = 0; i < 4; i++) GantryAPI.tickNode(registry, route.left);
        var original = rocket.modules()
            .getFirst();
        double progress = original.gantryTransport()
            .progress();
        assertTrue(progress > 0 && progress < 1);
        var pending = original.pendingTransfer();
        assertNotNull(pending);
        var leftTag = new NBTTagCompound();
        var middleTag = new NBTTagCompound();
        var rightTag = new NBTTagCompound();
        route.left.writeToNBT(leftTag);
        route.middle.writeToNBT(middleTag);
        route.right.writeToNBT(rightTag);
        var restoredRoute = new Route();
        restoredRoute.left.readFromNBT(leftTag);
        restoredRoute.middle.readFromNBT(middleTag);
        restoredRoute.right.readFromNBT(rightTag);
        registry.clear();
        Rocket restored = Rocket.deserializeNBT(rocket.serializeNBT(), definitions());
        registry.register(restored);
        restoredRoute.left.connect(restoredRoute.middle);
        restoredRoute.middle.connect(restoredRoute.right);
        var module = restored.modules()
            .getFirst();
        assertEquals(
            progress,
            module.gantryTransport()
                .progress());
        assertEquals(pending, module.pendingTransfer());
        assertEquals(module, GantryAPI.heldModule(registry, restoredRoute.left.nodeId()));
        restoredRoute.tick(registry, 40);
        assertEquals(holder(restoredRoute.right), module.holder());
        assertNull(module.gantryTransport());
    }

    @Test
    void disconnectedRouteKeepsModuleAndSamePositionReplacementCannotReceiveIt() {
        var route = new Route();
        var registry = new Rocket.Registry();
        Rocket rocket = vehicle(route.left, registry);
        dispatch(registry, rocket, UUID.randomUUID(), route.left, route.right);
        for (int i = 0; i < 11; i++) GantryAPI.tickNode(registry, route.left);
        var module = rocket.modules()
            .getFirst();
        assertEquals(new Rocket.Holder(Rocket.HolderKind.GANTRY, route.middle.nodeId()), module.holder());
        route.middle.disconnect(route.right);
        var replacement = new Terminal();
        replacement.xCoord = route.right.xCoord;
        route.middle.connect(replacement);
        for (int i = 0; i < 30; i++) GantryAPI.tickNode(registry, route.middle);
        assertEquals(new Rocket.Holder(Rocket.HolderKind.GANTRY, route.middle.nodeId()), module.holder());
        assertFalse(
            module.holder()
                .equals(holder(replacement)));
        route.middle.disconnect(replacement);
        route.middle.connect(route.right);
        route.tick(registry, 40);
        assertEquals(holder(route.right), module.holder());
    }

    private static void tick(Rocket.Registry registry, int ticks, TileEntityGantry... nodes) {
        for (int i = 0; i < ticks; i++) {
            for (TileEntityGantry node : nodes) GantryAPI.tickNode(registry, node);
        }
    }

    private static GantryAPI.DispatchResult dispatch(Rocket.Registry registry, Rocket rocket, UUID work,
        Terminal source, Terminal destination) {
        return GantryAPI.dispatch(
            registry,
            rocket,
            rocket.modules()
                .getFirst()
                .id(),
            work,
            source,
            destination,
            holder(destination));
    }

    private static Rocket.Holder holder(Terminal terminal) {
        return new Rocket.Holder(
            Rocket.HolderKind.ASSEMBLER,
            terminal.controllerIdentity()
                .id());
    }

    private static RocketPartRegistry definitions() {
        var registry = new RocketPartRegistry();
        registry.register(definition(), RocketPartCategory.STRUCTURAL);
        return registry;
    }

    private static RocketPartDef definition() {
        return RocketPartDef.structural(1, "test_frame", 1, 1, 1, null);
    }

    private static Rocket vehicle(Terminal source, Rocket.Registry registry) {
        var design = new RocketBlueprint();
        design.addPart(new RocketPartInstance(definition(), 0, 0, 0, false));
        var rocket = Rocket
            .construct(TEAM, CelestialObjectKey.registered(CelestialObjectId.OVERWORLD), UUID.randomUUID(), design);
        rocket.acceptProduced(
            Rocket.Module.produced(
                rocket.id(),
                rocket.buildSlots()
                    .getFirst(),
                43,
                holder(source)));
        registry.register(rocket);
        return rocket;
    }

    private static final class Route {

        private final Terminal left = new Terminal();
        private final TileEntityGantry middle = new TileEntityGantry();
        private final Terminal right = new Terminal();

        private Route() {
            middle.xCoord = 1;
            right.xCoord = 2;
            left.connect(middle);
            middle.connect(right);
        }

        private void tick(Rocket.Registry registry, int ticks) {
            for (int i = 0; i < ticks; i++) {
                GantryAPI.tickNode(registry, left);
                GantryAPI.tickNode(registry, middle);
                GantryAPI.tickNode(registry, right);
            }
        }
    }

    /** Fake physical receiver boundary. All routing, manifest changes and serialization use production code. */
    private static final class Terminal extends TileEntityGantryTerminal {

        private final IRocketControllerTE.MachineIdentity controller = new IRocketControllerTE.MachineIdentity();
        private boolean accepting = true;

        private Terminal() {
            controller.claim(TEAM);
        }

        @Override
        public int dimension() {
            return 0;
        }

        @Override
        public IRocketControllerTE.MachineIdentity controllerIdentity() {
            return controller;
        }

        @Override
        public boolean canReceive(Rocket rocket, Rocket.Module module, UUID workId, Rocket.Holder receiver) {
            return accepting && holder(this).equals(receiver);
        }

        @Override
        public boolean acceptDelivery(Rocket rocket, Rocket.Module module, UUID workId, Rocket.Holder receiver) {
            return canReceive(rocket, module, workId, receiver);
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            super.writeToNBT(tag);
            controller.writeToNBT(tag);
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            super.readFromNBT(tag);
            controller.readFromNBT(tag);
        }
    }
}
