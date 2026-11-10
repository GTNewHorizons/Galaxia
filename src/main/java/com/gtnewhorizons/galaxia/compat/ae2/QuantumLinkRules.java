package com.gtnewhorizons.galaxia.compat.ae2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.api.GalaxiaSatelliteAPI;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

import appeng.api.IAppEngApi;
import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.core.worlddata.WorldData;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Quantum bridges may not reach across space for free, otherwise nobody would build interplanetary logistics.
 * <p>
 * A network keeps working inside its own dimension. A bridge between two dimensions only links when the owner's team
 * has a communication satellite around the body at both ends. Links between different bodies also reserve one Mbps
 * of shared satellite bandwidth for their lifetime. The Overworld, its orbit and every dimension that is
 * not part of the Galaxia system count as home and link among themselves without relays, but a link from home to
 * another body needs a relay around the Overworld as well
 */
public final class QuantumLinkRules {

    private static final CelestialObjectKey HOME = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
    /** Ticks between checks of existing links, so lost relays cut them and new relays connect them */
    private static final int RECHECK_INTERVAL = 100;
    private static final long REQUIRED_BANDWIDTH_KBPS = 1000;

    private static final Map<IQuantumLink, Boolean> BRIDGES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<IGridConnection, Reservation> CONNECTIONS = new IdentityHashMap<>();
    private static int ticks;

    private QuantumLinkRules() {}

    /** Remembers a bridge so its link is checked again later */
    public static void track(IQuantumLink bridge) {
        if (bridge.galaxia$isAlive()) BRIDGES.put(bridge, Boolean.TRUE);
        else BRIDGES.remove(bridge);
    }

    public static IGridConnection createConnection(IAppEngApi api, IGridNode a, IGridNode b) throws FailedConnection {
        if (!isLinkAllowed(a, b)) throw new FailedConnection("No relay satellites between the bridges");
        if (!isInterplanetary(a, b)) return api.createGridConnection(a, b);
        UUID team = teamOf(a, b);
        UUID reservation = UUID.randomUUID();
        if (!SatelliteNetworkService.reserveBandwidth(
            team,
            reservation,
            bodyOf(a.getWorld().provider.dimensionId),
            bodyOf(b.getWorld().provider.dimensionId),
            REQUIRED_BANDWIDTH_KBPS))
            throw new FailedConnection("Insufficient satellite bandwidth for a one-Mbps quantum bridge");
        boolean connected = false;
        try {
            IGridConnection connection = api.createGridConnection(a, b);
            CONNECTIONS.put(connection, new Reservation(team, reservation));
            connected = true;
            return connection;
        } finally {
            if (!connected) SatelliteNetworkService.releaseBandwidth(team, reservation);
        }
    }

    public static boolean isConnectionAllowed(IGridConnection connection) {
        if (!isLinkAllowed(connection.a(), connection.b())) return false;
        if (!isInterplanetary(connection.a(), connection.b())) return true;
        Reservation reservation = CONNECTIONS.get(connection);
        return reservation != null && reservation.teamId()
            .equals(teamOf(connection.a(), connection.b()))
            && SatelliteNetworkService.hasReservation(reservation.teamId(), reservation.id());
    }

    /** Destroys the AE2 connection and releases its satellite allocation. */
    public static void destroyConnection(IGridConnection connection) {
        Reservation reservation = CONNECTIONS.remove(connection);
        if (reservation != null) SatelliteNetworkService.releaseBandwidth(reservation.teamId(), reservation.id());
        connection.destroy();
    }

    private static boolean isInterplanetary(IGridNode a, IGridNode b) {
        World worldA = a.getWorld(), worldB = b.getWorld();
        return worldA != null && worldB != null
            && !bodyOf(worldA.provider.dimensionId).equals(bodyOf(worldB.provider.dimensionId));
    }

    /**
     * Whether two bridge ends may be linked
     *
     * @param a One end
     * @param b The other end
     *
     * @return Boolean : True => the link may exist
     */
    public static boolean isLinkAllowed(IGridNode a, IGridNode b) {
        World worldA = a.getWorld(), worldB = b.getWorld();
        if (worldA == null || worldB == null) return true;
        int dimA = worldA.provider.dimensionId, dimB = worldB.provider.dimensionId;
        if (dimA == dimB) return true;

        CelestialObjectKey bodyA = bodyOf(dimA), bodyB = bodyOf(dimB);
        if (bodyA.equals(HOME) && bodyB.equals(HOME)) return true;

        UUID team = teamOf(a, b);
        if (team == null) return false;
        return hasRelay(team, bodyA) && hasRelay(team, bodyB);
    }

    /** Body a dimension belongs to, dimensions outside the Galaxia system belong to the Overworld */
    private static CelestialObjectKey bodyOf(int dimension) {
        Optional<CelestialObject> body = GalaxiaCelestialAPI.findByDimension(dimension);
        return body.map(CelestialObject::key)
            .orElse(HOME);
    }

    private static boolean hasRelay(UUID team, CelestialObjectKey body) {
        return GalaxiaSatelliteAPI.count(team, body, SatelliteKind.COMMUNICATION) > 0;
    }

    private static UUID teamOf(IGridNode a, IGridNode b) {
        UUID team = teamOf(a);
        return team == null ? teamOf(b) : team;
    }

    private record Reservation(UUID teamId, UUID id) {}

    /** Team of whoever placed the bridge block */
    private static UUID teamOf(IGridNode node) {
        int playerId = node.getPlayerID();
        if (playerId < 0) return null;
        if (!(WorldData.instance()
            .playerData() instanceof IPlayerMappingAccess access)) return null;
        UUID player = access.galaxia$getPlayerMapping()
            .get(playerId)
            .orNull();
        return GTTeamsCompat.getTeam(player);
    }

    /**
     * Checks every known bridge now and then
     */
    public static final class Ticker {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || ++ticks < RECHECK_INTERVAL) return;
            ticks = 0;
            List<IQuantumLink> bridges;
            synchronized (BRIDGES) {
                bridges = new ArrayList<>(BRIDGES.keySet());
            }
            for (IQuantumLink bridge : bridges) {
                if (bridge.galaxia$isAlive()) bridge.galaxia$recheckLink();
                else BRIDGES.remove(bridge);
            }
        }
    }
}
