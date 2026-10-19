package com.gtnewhorizons.galaxia.compat.ae2;

import java.util.ArrayList;
import java.util.Collections;
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

import appeng.api.networking.IGridNode;
import appeng.core.worlddata.WorldData;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Quantum bridges may not reach across space for free, otherwise nobody would build interplanetary logistics.
 * <p>
 * A network keeps working inside its own dimension. A bridge between two dimensions only links when the owner's team
 * has a communication satellite around the body at both ends. The Overworld, its orbit and every dimension that is
 * not part of the Galaxia system count as home and link among themselves without relays, but a link from home to
 * another body needs a relay around the Overworld as well
 */
public final class QuantumLinkRules {

    private static final CelestialObjectKey HOME = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
    /** Ticks between checks of existing links, so lost relays cut them and new relays connect them */
    private static final int RECHECK_INTERVAL = 100;

    private static final Map<IQuantumLink, Boolean> BRIDGES = Collections.synchronizedMap(new WeakHashMap<>());
    private static int ticks;

    private QuantumLinkRules() {}

    /** Remembers a bridge so its link is checked again later */
    public static void track(IQuantumLink bridge) {
        BRIDGES.put(bridge, Boolean.TRUE);
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

        UUID team = teamOf(a);
        if (team == null) team = teamOf(b);
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
