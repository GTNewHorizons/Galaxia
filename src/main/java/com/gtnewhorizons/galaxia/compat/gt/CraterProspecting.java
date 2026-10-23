package com.gtnewhorizons.galaxia.compat.gt;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.compat.VisualProspectingCompat;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterDeposits;

import cpw.mods.fml.common.Loader;

/** Crater analysis added to the existing GT prospector interactions. */
public final class CraterProspecting {

    private CraterProspecting() {}

    public static void report(World world, EntityPlayer player) {
        if (world.isRemote) return;
        CraterDeposits deposits = CraterDeposits.get(world);
        String id = deposits.find(MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posZ));
        if (id == null) return;
        var snapshot = deposits.inspect(id);
        if (player instanceof EntityPlayerMP serverPlayer && Loader.isModLoaded("visualprospecting")) {
            VisualProspectingCompat.discover(serverPlayer, world, snapshot);
        }
        player.addChatMessage(
            new ChatComponentTranslation(
                "galaxia.crater.scan",
                new ChatComponentTranslation("galaxia.crater.profile." + snapshot.profile().id),
                snapshot.remainingReserve(),
                snapshot.initialReserve(),
                snapshot.completedFracking(),
                snapshot.nextFrackingFluid(),
                new ChatComponentTranslation(
                    snapshot.size()
                        .translationKey()),
                snapshot.diameter()));
    }
}
