package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

public class TileEntityFumarole extends TileEntity {

    // How frequently the vent activates
    private static final int CYCLE_TICKS = 200;
    // For how long the vent activates
    private static final int ACTIVE_TICKS = 80;
    // Size of collision box for damage;
    private static final int JET_HEIGHT = 3;

    // These variables are generated lazily and cached. I did not use NBT because I want to minimize the impact
    // of these TileEntities on the save file.
    private int cycleOffset = -1;
    private AxisAlignedBB jetCollider;

    public boolean isJetting() {
        if (cycleOffset < 0) initOffset();
        return (worldObj.getTotalWorldTime() + cycleOffset) % CYCLE_TICKS < ACTIVE_TICKS;
    }

    // Hash so that offset is seemingly random by coordinate but consistent
    private void initOffset() {
        int h = xCoord * 1664525 + yCoord * 1013904223 + zCoord * 22695477;
        h ^= (h >>> 16);
        h *= 0x45d9f3b;
        h ^= (h >>> 16);
        cycleOffset = Math.abs(h) % CYCLE_TICKS;
    }

    @Override
    public void updateEntity() {

        if (!isJetting()) return;

        ForgeDirection facing = ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord, yCoord, zCoord));

        // Particle stuff is client only
        if (worldObj.isRemote) {
            double primarySpeed = 0.15 + worldObj.rand.nextDouble() * 0.15;
            double vx = facing.offsetX * primarySpeed;
            double vy = facing.offsetY * primarySpeed;
            double vz = facing.offsetZ * primarySpeed;

            double spread = 0.08;
            vx += (worldObj.rand.nextDouble() - 0.5) * spread;
            vy += (worldObj.rand.nextDouble() - 0.5) * spread;
            vz += (worldObj.rand.nextDouble() - 0.5) * spread;

            double ox = facing.offsetX == 0 ? worldObj.rand.nextDouble() : 0.5;
            double oy = facing.offsetY == 0 ? worldObj.rand.nextDouble() : 0.5;
            double oz = facing.offsetZ == 0 ? worldObj.rand.nextDouble() : 0.5;

            worldObj.spawnParticle("smoke", xCoord + ox, yCoord + oy, zCoord + oz, vx, vy, vz);
            worldObj.spawnParticle("flame", xCoord + ox, yCoord + oy, zCoord + oz, vx, vy, vz);
            return;
        }

        // Collision stuff is server only
        long t = (worldObj.getTotalWorldTime() + cycleOffset) % CYCLE_TICKS;
        if (t % 5 != 0) return;

        List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, getJetCollision());
        // TODO: This should be heat buildup or statuses instead of damage
        for (EntityPlayer player : players) {
            player.attackEntityFrom(DamageSource.inFire, 2.0f);
        }
    }

    // This is AI slop and totally illegible, feel free to rewrite if you want to have a miserable time.
    // TLDR makes a 3 block collision box extending from the facing side.
    public @NotNull AxisAlignedBB getJetCollision() {
        if (jetCollider == null) {
            ForgeDirection facing = ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord, yCoord, zCoord));
            double jx1, jx2, jy1, jy2, jz1, jz2;

            if (facing.offsetX != 0) {
                double near = xCoord + Math.max(0, facing.offsetX);
                jx1 = near + Math.min(0, facing.offsetX * JET_HEIGHT);
                jx2 = near + Math.max(0, facing.offsetX * JET_HEIGHT);
                jy1 = yCoord;
                jy2 = yCoord + 1;
                jz1 = zCoord;
                jz2 = zCoord + 1;
            } else if (facing.offsetY != 0) {
                double near = yCoord + Math.max(0, facing.offsetY);
                jx1 = xCoord;
                jx2 = xCoord + 1;
                jy1 = near + Math.min(0, facing.offsetY * JET_HEIGHT);
                jy2 = near + Math.max(0, facing.offsetY * JET_HEIGHT);
                jz1 = zCoord;
                jz2 = zCoord + 1;
            } else if (facing.offsetZ != 0) {
                double near = zCoord + Math.max(0, facing.offsetZ);
                jx1 = xCoord;
                jx2 = xCoord + 1;
                jy1 = yCoord;
                jy2 = yCoord + 1;
                jz1 = near + Math.min(0, facing.offsetZ * JET_HEIGHT);
                jz2 = near + Math.max(0, facing.offsetZ * JET_HEIGHT);
            } else {
                jx1 = xCoord;
                jx2 = xCoord + 1;
                jy1 = yCoord + 1;
                jy2 = yCoord + 1 + JET_HEIGHT;
                jz1 = zCoord;
                jz2 = zCoord + 1;
            }

            jetCollider = AxisAlignedBB.getBoundingBox(jx1, jy1, jz1, jx2, jy2, jz2);
        }
        return jetCollider;
    }
}
