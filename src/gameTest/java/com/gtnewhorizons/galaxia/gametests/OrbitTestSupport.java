package com.gtnewhorizons.galaxia.gametests;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;

/**
 * Stations can only be built in orbit, so tests that need one take the player up into the Overworld orbit and bring
 * them back afterwards
 */
final class OrbitTestSupport {

    private OrbitTestSupport() {}

    /** Dimension id of the orbit above the Overworld */
    static int overworldOrbit() {
        return OrbitDimensions.all()
            .stream()
            .filter(orbit -> orbit.surface() == DimensionEnum.OVERWORLD)
            .findFirst()
            .orElseThrow(() -> new AssertionError("The Overworld has no orbit"))
            .dimensionId();
    }

    /**
     * Moves the player into the Overworld orbit for the rest of the test
     *
     * @param helper The running test
     * @param player The test player
     * @param x      Arrival x
     * @param y      Arrival y
     * @param z      Arrival z
     *
     * @return The orbit world
     */
    static WorldServer enterOrbit(GameTestHelper helper, EntityPlayerMP player, double x, double y, double z) {
        int home = player.dimension;
        double oldX = player.posX, oldY = player.posY, oldZ = player.posZ;
        int orbit = overworldOrbit();
        moveTo(player, orbit, x, y, z);
        helper.afterTest(() -> moveTo(player, home, oldX, oldY, oldZ));
        return MinecraftServer.getServer()
            .worldServerForDimension(orbit);
    }

    static void moveTo(EntityPlayerMP player, int dimension, double x, double y, double z) {
        player.mountEntity(null);
        if (player.dimension == dimension) {
            player.playerNetServerHandler.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer target = server.worldServerForDimension(dimension);
        server.getConfigurationManager()
            .transferPlayerToDimension(player, dimension, new Teleporter(target) {

                @Override
                public void placeInPortal(Entity entity, double px, double py, double pz, float yaw) {
                    entity.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
                }

                @Override
                public boolean makePortal(Entity entity) {
                    return true;
                }
            });
    }
}
