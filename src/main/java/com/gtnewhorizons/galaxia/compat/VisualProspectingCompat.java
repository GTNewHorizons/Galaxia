package com.gtnewhorizons.galaxia.compat;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterDeposits;
import com.sinthoras.visualprospecting.VisualProspecting_API;
import com.sinthoras.visualprospecting.database.CraterPosition;

import cpw.mods.fml.common.Optional;

/** Publishes crater discoveries and successful extraction updates through VP's existing map and team cache. */
public final class VisualProspectingCompat {

    private VisualProspectingCompat() {}

    @Optional.Method(modid = "visualprospecting")
    public static void discover(EntityPlayerMP player, World world, CraterDeposits.Snapshot crater) {
        VisualProspecting_API.LogicalServer.discoverCrater(player, position(world, crater));
    }

    @Optional.Method(modid = "visualprospecting")
    public static void update(World world, CraterDeposits.Snapshot crater) {
        VisualProspecting_API.LogicalServer.updateCrater(position(world, crater));
    }

    @Optional.Method(modid = "visualprospecting")
    private static CraterPosition position(World world, CraterDeposits.Snapshot crater) {
        return new CraterPosition(
            world.provider.dimensionId,
            crater.id(),
            crater.x(),
            crater.z(),
            crater.diameter(),
            "galaxia.crater.profile." + crater.profile().id,
            crater.size()
                .translationKey(),
            crater.initialReserve(),
            crater.remainingReserve(),
            crater.completedFracking(),
            crater.nextFrackingFluid());
    }
}
