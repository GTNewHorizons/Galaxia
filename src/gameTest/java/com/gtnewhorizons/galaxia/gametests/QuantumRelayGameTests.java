package com.gtnewhorizons.galaxia.gametests;

import java.lang.reflect.Proxy;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.compat.ae2.IPlayerMappingAccess;
import com.gtnewhorizons.galaxia.compat.ae2.IQuantumLink;
import com.gtnewhorizons.galaxia.compat.ae2.QuantumLinkRules;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.networking.IGridNode;
import appeng.api.util.WorldCoord;
import appeng.core.worlddata.WorldData;
import appeng.me.cluster.implementations.QuantumCluster;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "appliedenergistics2" })
public final class QuantumRelayGameTests {

    private QuantumRelayGameTests() {}

    /**
     * Product contract: quantum bridges link freely around the Overworld, but reaching another body takes the owner's
     * team a relay satellite around both ends.
     */
    @GameTest(timeoutTicks = 100)
    public static void quantumBridgesNeedRelaysAcrossSpace(GameTestHelper helper) {
        if (!(new QuantumCluster(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 0)) instanceof IQuantumLink)) {
            throw new AssertionError("Quantum bridge mixin is not applied");
        }
        if (!(WorldData.instance()
            .playerData() instanceof IPlayerMappingAccess)) {
            throw new AssertionError("AE2 player mapping accessor is not applied");
        }

        MinecraftServer server = MinecraftServer.getServer();
        EntityPlayerMP player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
        UUID team = GTTeamsCompat.getTeam(player);
        if (team == null) throw new AssertionError("Test player has no team");
        int owner = AEApi.instance()
            .registries()
            .players()
            .getID(player);

        World overworld = server.worldServerForDimension(0);
        World orbit = server.worldServerForDimension(OrbitTestSupport.overworldOrbit());
        World mars = server.worldServerForDimension(DimensionEnum.MARS.getId());
        IGridNode home = node(overworld, owner), above = node(orbit, owner), far = node(mars, owner);

        CelestialObjectKey earthKey = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        CelestialObjectKey marsKey = CelestialObjectKey.registered(CelestialObjectId.MARS);
        int earthRelays = relays(team, earthKey), marsRelays = relays(team, marsKey);
        helper.afterTest(() -> {
            setRelays(team, earthKey, earthRelays);
            setRelays(team, marsKey, marsRelays);
        });

        setRelays(team, earthKey, 0);
        setRelays(team, marsKey, 0);
        if (!QuantumLinkRules.isLinkAllowed(home, above)) {
            throw new AssertionError("Overworld and its orbit should link without relays");
        }
        if (QuantumLinkRules.isLinkAllowed(home, far)) throw new AssertionError("Mars linked without any relay");

        setRelays(team, marsKey, 1);
        if (QuantumLinkRules.isLinkAllowed(home, far)) throw new AssertionError("Mars linked without an Earth relay");

        setRelays(team, earthKey, 1);
        if (!QuantumLinkRules.isLinkAllowed(home, far)) throw new AssertionError("Relays at both ends did not link");
        if (!QuantumLinkRules.isLinkAllowed(far, above)) throw new AssertionError("Mars and Earth orbit did not link");
        helper.succeed();
    }

    private static int relays(UUID team, CelestialObjectKey body) {
        return CelestialAssetStore.SERVER.satelliteCount(team, body, SatelliteKind.COMMUNICATION);
    }

    private static void setRelays(UUID team, CelestialObjectKey body, int count) {
        CelestialAssetStore.SERVER.setSatelliteCount(team, body, SatelliteKind.COMMUNICATION, count);
    }

    /** A grid node standing in one world, owned by an AE player id */
    private static IGridNode node(World world, int owner) {
        return (IGridNode) Proxy.newProxyInstance(
            IGridNode.class.getClassLoader(),
            new Class<?>[] { IGridNode.class },
            (proxy, method, args) -> switch (method.getName()) {
            case "getWorld" -> world;
            case "getPlayerID" -> owner;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "node in " + world.provider.dimensionId;
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
            });
    }
}
