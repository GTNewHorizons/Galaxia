package com.gtnewhorizons.galaxia.registry.dimension;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/**
 * Moves players between Galaxia dimensions without portals
 */
public final class DimensionTeleport {

    private DimensionTeleport() {}

    /**
     * A dimension players can be sent to
     *
     * @param dimension Dimension id
     * @param name      Name shown to players
     * @param orbit     True for the orbit above a surface
     */
    public record Destination(int dimension, String name, boolean orbit) {}

    /** Every Galaxia surface that exists, each followed by its orbit */
    public static List<Destination> destinations() {
        List<Destination> result = new ArrayList<>();
        for (DimensionEnum surface : DimensionEnum.values()) {
            if (!DimensionManager.isDimensionRegistered(surface.getId())) continue;
            result.add(new Destination(surface.getId(), surfaceName(surface), false));
            OrbitDimensions.all()
                .stream()
                .filter(orbit -> orbit.surface() == surface)
                .findFirst()
                .ifPresent(orbit -> result.add(new Destination(orbit.dimensionId(), orbit.displayName(), true)));
        }
        return result;
    }

    private static String surfaceName(DimensionEnum surface) {
        String localized = StatCollector.translateToLocal(surface.getTranslationKey());
        return surface.getTranslationKey()
            .equals(localized)
                ? surface.getName()
                    .replace('_', ' ')
                : localized;
    }

    /**
     * Moves a player to a position in another or the same dimension
     *
     * @param player    The player to move
     * @param dimension Target dimension
     * @param x         Target x
     * @param y         Target y
     * @param z         Target z
     */
    public static void teleport(EntityPlayerMP player, int dimension, double x, double y, double z) {
        player.mountEntity(null);
        player.fallDistance = 0;
        player.motionX = player.motionY = player.motionZ = 0;
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
