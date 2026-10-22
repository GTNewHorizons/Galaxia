package com.gtnewhorizons.galaxia.gametests;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizons.galaxia.compat.ae2.QuantumLinkRules;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkCalculator;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkState;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.api.IAppEngApi;
import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridNotification;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "appliedenergistics2" })
public final class QuantumBandwidthGameTests {

    private QuantumBandwidthGameTests() {}

    /** Real AE2 connections share satellite capacity and release their one-Mbps allocations. */
    @GameTest(timeoutTicks = 100)
    public static void quantumBridgesShareBandwidthAndReleaseIt(GameTestHelper helper) throws FailedConnection {
        Fixture fixture = new Fixture();
        fixture.prepare(helper);
        IGridConnection first = fixture.connect(0);
        fixture.assertUsage(1000);
        if (!QuantumLinkRules.isConnectionAllowed(first) || !QuantumLinkRules.isConnectionAllowed(first))
            throw new AssertionError("A reserved connection failed validation");
        fixture.assertUsage(1000);

        long capacity = SatelliteNetworkCalculator.widestPath(Fixture.EARTH, Fixture.MARS, fixture.network())
            .capacityKbps();
        if (!SatelliteNetworkService
            .reserveBandwidth(fixture.team, fixture.competitor, Fixture.EARTH, Fixture.MARS, capacity - 1000))
            throw new AssertionError("Could not reserve the remaining shared capacity");
        fixture.expectUnavailable(AEApi.instance(), 1);
        SatelliteNetworkService.releaseBandwidth(fixture.team, fixture.competitor);
        fixture.network();
        IGridConnection second = fixture.connect(1);
        fixture.assertUsage(2000);

        FailedConnection rejected = new FailedConnection("AE2 rejected the fixture connection");
        IAppEngApi rejectingApi = (IAppEngApi) Proxy.newProxyInstance(
            IAppEngApi.class.getClassLoader(),
            new Class<?>[] { IAppEngApi.class },
            (proxy, method, args) -> { throw rejected; });
        if (fixture.expectUnavailable(rejectingApi, 2) != rejected
            || fixture.expectUnavailable(rejectingApi, 2) != rejected)
            throw new AssertionError("Available bandwidth did not reach the rejecting AE2 backend");
        fixture.assertUsage(2000);
        if (!SatelliteNetworkService
            .reserveBandwidth(fixture.team, fixture.competitor, Fixture.EARTH, Fixture.MARS, capacity - 2000))
            throw new AssertionError("Failed AE2 connection creation leaked a bandwidth reservation");
        SatelliteNetworkService.releaseBandwidth(fixture.team, fixture.competitor);

        fixture.close(first);
        fixture.assertUsage(1000);
        fixture.setRelays(Fixture.MARS, 1);
        fixture.network();
        if (QuantumLinkRules.isConnectionAllowed(second))
            throw new AssertionError("A surviving relay kept a bridge above its available bandwidth");
        fixture.close(second);
        fixture.assertUsage(0);
        fixture.setRelays(Fixture.MARS, Fixture.RELAY_COUNT);
        fixture.network();
        IGridConnection restored = fixture.connect(1);
        fixture.assertUsage(1000);
        fixture.close(restored);
        fixture.assertUsage(0);
        helper.succeed();
    }

    /** A real bridge cannot keep charging its previous owner's team after an ordinary membership change. */
    @GameTest(timeoutTicks = 100)
    public static void connectionRequiresCurrentTeamBandwidth(GameTestHelper helper) throws FailedConnection {
        Fixture fixture = new Fixture();
        fixture.prepare(helper);
        var oldTeam = TeamManager.getTeamById(fixture.team);
        UUID owner = UUID.randomUUID();
        UUID nextOwner = UUID.randomUUID();
        oldTeam.addMember(owner);
        var nextTeam = TeamManager.createTeam("Quantum bandwidth fixture", nextOwner);
        UUID next = nextTeam.getTeamId();
        helper.afterTest(() -> {
            nextTeam.removeMember(owner);
            for (CelestialObjectKey body : List.of(Fixture.EARTH, Fixture.MARS))
                CelestialAssetStore.SERVER.setSatelliteCount(next, body, SatelliteKind.COMMUNICATION, 0)
                    .forEach(id -> AssetStateSync.SERVER.publishRemoved(next, id));
            TeamManager.mergeTeams(oldTeam, nextTeam);
            oldTeam.removeMember(owner);
            oldTeam.removeMember(nextOwner);
        });
        int ownerId = AEApi.instance()
            .registries()
            .players()
            .getID(new GameProfile(owner, "QaQuantumOwner"));
        for (IGridNode[] pair : fixture.nodes) for (IGridNode node : pair) node.setPlayerID(ownerId);
        IGridConnection original = fixture.connect(0);
        fixture.assertUsage(1000);
        oldTeam.removeMember(owner);
        nextTeam.addMember(owner);
        CelestialAssetStore.SERVER.setSatelliteCount(next, Fixture.EARTH, SatelliteKind.COMMUNICATION, 1);
        CelestialAssetStore.SERVER.setSatelliteCount(next, Fixture.MARS, SatelliteKind.COMMUNICATION, 1);
        SatelliteNetworkService.rebuild(next, 0);
        if (!next.equals(GTTeamsCompat.getTeam(owner)))
            throw new AssertionError("The real team registry did not change the bridge owner's team");
        if (!QuantumLinkRules.isLinkAllowed(fixture.nodes[0][0], fixture.nodes[0][1]))
            throw new AssertionError("The new team's real relays are unavailable");
        if (QuantumLinkRules.isConnectionAllowed(original))
            throw new AssertionError("Quantum bridge kept using its previous team's bandwidth");
        fixture.close(original);
        fixture.assertUsage(0);
        fixture.expectUnavailable(AEApi.instance(), 0);
        CelestialAssetStore.SERVER
            .setSatelliteCount(next, Fixture.EARTH, SatelliteKind.COMMUNICATION, Fixture.RELAY_COUNT);
        CelestialAssetStore.SERVER
            .setSatelliteCount(next, Fixture.MARS, SatelliteKind.COMMUNICATION, Fixture.RELAY_COUNT);
        SatelliteNetworkService.rebuild(next, 0);
        IGridConnection recreated = fixture.connect(0);
        if (!QuantumLinkRules.isConnectionAllowed(recreated))
            throw new AssertionError("The new team's available bandwidth did not restore the connection");
        SatelliteNetworkState current = SatelliteNetworkService.rebuild(next, 0);
        if (current.usedKbps(Fixture.EARTH) != 1000L * current.links()
            .get(0)
            .quality()
            .airtimeCost()) throw new AssertionError("Recreated quantum bridge did not charge its current team");
        fixture.assertUsage(0);
        fixture.close(recreated);
        if (SatelliteNetworkService.rebuild(next, 0)
            .usedKbps(Fixture.EARTH) != 0) throw new AssertionError("Current team's reservation was not released");
        helper.succeed();
    }

    private static final class Fixture {

        static final CelestialObjectKey EARTH = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        static final CelestialObjectKey MARS = CelestialObjectKey.registered(CelestialObjectId.MARS);
        static final int RELAY_COUNT = 300 * SatelliteNetworkState.Quality.DEEP_SPACE.airtimeCost();
        final UUID competitor = UUID.randomUUID();
        final List<IGridConnection> connections = new ArrayList<>();
        final IGridNode[][] nodes = new IGridNode[3][2];
        UUID team;

        void prepare(GameTestHelper helper) {
            EntityPlayerMP player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            team = GTTeamsCompat.getTeam(player);
            if (team == null) throw new AssertionError("Test player has no team");
            int owner = AEApi.instance()
                .registries()
                .players()
                .getID(player);
            int earthRelays = relays(EARTH), marsRelays = relays(MARS);
            helper.afterTest(() -> {
                for (IGridConnection connection : List.copyOf(connections)) close(connection);
                for (IGridNode[] pair : nodes) for (IGridNode node : pair) if (node != null) node.destroy();
                SatelliteNetworkService.releaseBandwidth(team, competitor);
                setRelays(EARTH, earthRelays);
                setRelays(MARS, marsRelays);
                network();
            });
            World[] worlds = { MinecraftServer.getServer()
                .worldServerForDimension(0),
                MinecraftServer.getServer()
                    .worldServerForDimension(DimensionEnum.MARS.getId()) };
            for (int pair = 0; pair < nodes.length; pair++) {
                for (int side = 0; side < worlds.length; side++) {
                    GridBlock block = new GridBlock(worlds[side], pair * 8, owner);
                    nodes[pair][side] = block.node;
                }
            }
            setRelays(EARTH, RELAY_COUNT);
            setRelays(MARS, RELAY_COUNT);
            if (SatelliteNetworkCalculator.widestPath(EARTH, MARS, network())
                .capacityKbps() <= 2000) throw new AssertionError("Fixture needs capacity for two independent bridges");
        }

        IGridConnection connect(int pair) throws FailedConnection {
            IGridConnection connection = QuantumLinkRules
                .createConnection(AEApi.instance(), nodes[pair][0], nodes[pair][1]);
            connections.add(connection);
            if (nodes[pair][0].getGrid() != nodes[pair][1].getGrid())
                throw new AssertionError("The real AE2 connection did not join the endpoint grids");
            return connection;
        }

        FailedConnection expectUnavailable(IAppEngApi api, int pair) {
            try {
                IGridConnection unexpected = QuantumLinkRules.createConnection(api, nodes[pair][0], nodes[pair][1]);
                connections.add(unexpected);
                throw new AssertionError("An unavailable connection was created");
            } catch (FailedConnection expected) {
                return expected;
            }
        }

        void close(IGridConnection connection) {
            QuantumLinkRules.destroyConnection(connection);
            connections.remove(connection);
        }

        void assertUsage(long expected) {
            SatelliteNetworkState state = network();
            if (expected > 0L) expected *= state.links()
                .get(0)
                .quality()
                .airtimeCost();
            if (state.usedKbps(EARTH) != expected || state.usedKbps(MARS) != expected) throw new AssertionError(
                "Expected " + expected
                    + " kbps per endpoint, got "
                    + state.usedKbps(EARTH)
                    + "/"
                    + state.usedKbps(MARS));
        }

        SatelliteNetworkState network() {
            return SatelliteNetworkService.rebuild(team, 0);
        }

        int relays(CelestialObjectKey body) {
            return CelestialAssetStore.SERVER.satelliteCount(team, body, SatelliteKind.COMMUNICATION);
        }

        void setRelays(CelestialObjectKey body, int count) {
            boolean removing = count < relays(body);
            var changed = CelestialAssetStore.SERVER.setSatelliteCount(team, body, SatelliteKind.COMMUNICATION, count);
            if (removing) changed.forEach(id -> AssetStateSync.SERVER.publishRemoved(team, id));
        }
    }

    /** A minimal host at the public AE2 grid API, backed by a real AE2 node. */
    private static final class GridBlock implements IGridBlock, IGridHost {

        final DimensionalCoord location;
        final IGridNode node;

        GridBlock(World world, int x, int owner) {
            location = new DimensionalCoord(world, x, 200, 0);
            node = AEApi.instance()
                .createGridNode(this);
            node.setPlayerID(owner);
            node.updateState();
        }

        @Override
        public double getIdlePowerUsage() {
            return 0;
        }

        @Override
        public EnumSet<GridFlags> getFlags() {
            return EnumSet.noneOf(GridFlags.class);
        }

        @Override
        public boolean isWorldAccessible() {
            return false;
        }

        @Override
        public DimensionalCoord getLocation() {
            return location;
        }

        @Override
        public AEColor getGridColor() {
            return AEColor.Transparent;
        }

        @Override
        public void onGridNotification(GridNotification notification) {}

        @Override
        public void setNetworkStatus(IGrid grid, int channelsInUse) {}

        @Override
        public EnumSet<ForgeDirection> getConnectableSides() {
            return EnumSet.noneOf(ForgeDirection.class);
        }

        @Override
        public IGridHost getMachine() {
            return this;
        }

        @Override
        public void gridChanged() {}

        @Override
        public ItemStack getMachineRepresentation() {
            return null;
        }

        @Override
        public IGridNode getGridNode(ForgeDirection dir) {
            return node;
        }

        @Override
        public AECableType getCableConnectionType(ForgeDirection dir) {
            return AECableType.NONE;
        }

        @Override
        public void securityBreak() {
            throw new AssertionError("The fixture's own AE2 nodes failed security");
        }
    }
}
