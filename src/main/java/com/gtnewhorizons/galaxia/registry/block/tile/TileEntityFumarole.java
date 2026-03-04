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

    private int activeTicksThisCycle = -1;

    private AxisAlignedBB jetColliderHeavy;
    private AxisAlignedBB jetColliderSteady;
    private AxisAlignedBB jetColliderWide;

    // These variables are generated lazily and cached. I did not use NBT because I want to minimize the impact
    // of these TileEntities on the save file.
    private int cycleOffset = -1;
    private int ventType = -1;
    private AxisAlignedBB jetCollider;

    public boolean isJetting() {
        if (cycleOffset < 0) initOffset();
        long worldTime = worldObj.getTotalWorldTime() + cycleOffset;
        long cyclePos = worldTime % CYCLE_TICKS;
        return cyclePos < activeTicksThisCycle;
    }

    // Hash so that offset is seemingly random by coordinate but consistent
    private void initOffset() {
        int h = xCoord * 1664525 + yCoord * 1013904223 + zCoord * 22695477;
        h ^= (h >>> 16);
        h *= 0x45d9f3b;
        h ^= (h >>> 16);
        cycleOffset = Math.abs(h) % CYCLE_TICKS;
        ventType = Math.abs(h) % 3;
        activeTicksThisCycle = 60 + (Math.abs(h) % 41);
    }

    @Override
    public void updateEntity() {
        if (!isJetting()) {
            jetCollider = null;
            return;
        }

        ForgeDirection facing = ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord, yCoord, zCoord));

        // Particle stuff is client only
        if (worldObj.isRemote) {
            long t = (worldObj.getTotalWorldTime() + cycleOffset) % CYCLE_TICKS;
            // for wideConeExplosion only first tick
            if (ventType == 2) {
                if (t == 0) spawnParticles(facing);
            } else {
                if (t % 6 == 0) spawnParticles(facing);
            }
            return;
        }

        // Collision stuff is server only
        long t = (worldObj.getTotalWorldTime() + cycleOffset) % CYCLE_TICKS;

        switch (ventType) {
            case 2:
                if (t == 0) applyFireDamage(9.0f);
                break;
            case 1:
                if (t % 5 == 0) applyFireDamage(2.5f);
                break;
            case 0:
                if (t % 5 == 0) applyFireDamage(1.5f);
                break;
        }
    }

    private void applyFireDamage(float amount) {
        List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, getJetCollision());
        for (EntityPlayer player : players) {
            player.attackEntityFrom(DamageSource.inFire, amount);
        }
    }

    private void spawnParticles(ForgeDirection dir) {
        double cx = xCoord + 0.5 + dir.offsetX * 0.01;
        double cy = yCoord + 0.5 + dir.offsetY * 0.01;
        double cz = zCoord + 0.5 + dir.offsetZ * 0.01;

        double hole = 0.24;
        double ox = dir.offsetX == 0 ? (worldObj.rand.nextDouble() - 0.5) * hole : 0;
        double oy = dir.offsetY == 0 ? (worldObj.rand.nextDouble() - 0.5) * hole : 0;
        double oz = dir.offsetZ == 0 ? (worldObj.rand.nextDouble() - 0.5) * hole : 0;

        switch (ventType) {
            case 0 -> heavySmokeBurst(dir, cx, cy, cz, ox, oy, oz);
            case 1 -> steadyFlamePillar(dir, cx, cy, cz, ox, oy, oz);
            case 2 -> wideConeExplosion(dir, cx, cy, cz, ox, oy, oz);
        }
    }

    private void heavySmokeBurst(ForgeDirection dir, double cx, double cy, double cz, double ox, double oy, double oz) {
        double baseSpeed = 0.26 + worldObj.rand.nextDouble() * 0.19;
        double vx = dir.offsetX * baseSpeed;
        double vy = dir.offsetY * baseSpeed;
        double vz = dir.offsetZ * baseSpeed;

        int count = 11 + worldObj.rand.nextInt(8);
        double gravity = -0.028 - worldObj.rand.nextDouble() * 0.015;

        for (int i = 0; i < count; i++) {
            double spread = i * 0.011;
            worldObj.spawnParticle(
                "largesmoke",
                cx + ox,
                cy + oy,
                cz + oz,
                vx + (worldObj.rand.nextDouble() - 0.5) * spread,
                vy + (worldObj.rand.nextDouble() - 0.5) * spread + gravity,
                vz + (worldObj.rand.nextDouble() - 0.5) * spread);

            if (worldObj.rand.nextInt(3) == 0) {
                worldObj.spawnParticle("flame", cx + ox, cy + oy, cz + oz, vx * 0.65, vy * 0.65, vz * 0.65);
            }
        }
    }

    private void steadyFlamePillar(ForgeDirection dir, double cx, double cy, double cz, double ox, double oy,
        double oz) {
        double speed = 0.085 + worldObj.rand.nextDouble() * 0.065;
        double vx = dir.offsetX * speed;
        double vy = dir.offsetY * speed;
        double vz = dir.offsetZ * speed;

        int count = 4 + worldObj.rand.nextInt(4);
        for (int i = 0; i < count; i++) {
            worldObj.spawnParticle(
                "flame",
                cx + ox,
                cy + oy,
                cz + oz,
                vx + (worldObj.rand.nextDouble() - 0.5) * 0.055,
                vy + (worldObj.rand.nextDouble() - 0.5) * 0.055,
                vz + (worldObj.rand.nextDouble() - 0.5) * 0.055);
        }
    }

    private void wideConeExplosion(ForgeDirection dir, double cx, double cy, double cz, double ox, double oy,
        double oz) {
        int count = 16 + worldObj.rand.nextInt(11);
        double baseSpeed = 0.29 + worldObj.rand.nextDouble() * 0.24;

        for (int i = 0; i < count; i++) {
            double angle = worldObj.rand.nextDouble() * Math.PI * 2;
            double speed = baseSpeed + worldObj.rand.nextDouble() * 0.13;

            double vx = dir.offsetX * speed + Math.cos(angle) * 0.22;
            double vy = dir.offsetY * speed + (worldObj.rand.nextDouble() - 0.35) * 0.28;
            double vz = dir.offsetZ * speed + Math.sin(angle) * 0.22;

            worldObj.spawnParticle("flame", cx + ox, cy + oy, cz + oz, vx, vy, vz);

            if (i % 3 == 0) {
                worldObj.spawnParticle("largesmoke", cx + ox, cy + oy, cz + oz, vx * 0.55, vy * 0.55, vz * 0.55);
            }
        }
    }

    public @NotNull AxisAlignedBB getJetCollision() {
        if (ventType == -1) initOffset();

        switch (ventType) {
            case 0 -> {
                if (jetColliderHeavy == null) {
                    jetColliderHeavy = createDirectionalBox(3.0, 1.0, 1.0);
                }
                return jetColliderHeavy;
            }
            case 1 -> {
                if (jetColliderSteady == null) {
                    jetColliderSteady = createDirectionalBox(1.5, 0.8, 0.8);
                }
                return jetColliderSteady;
            }
            case 2 -> {
                if (jetColliderWide == null) {
                    jetColliderWide = createDirectionalBox(2.0, 2.0, 2.0);
                }
                return jetColliderWide;
            }
            default -> {
                if (jetCollider == null) {
                    jetCollider = createDirectionalBox(3.0, 1.0, 1.0);
                }
                return jetCollider;
            }
        }
    }

    private AxisAlignedBB createDirectionalBox(double length, double width, double height) {
        ForgeDirection dir = ForgeDirection.getOrientation(getBlockMetadata());

        double minX = xCoord + 0.5 - width / 2.0;
        double maxX = xCoord + 0.5 + width / 2.0;
        double minY = yCoord + 0.5 - height / 2.0;
        double maxY = yCoord + 0.5 + height / 2.0;
        double minZ = zCoord + 0.5 - width / 2.0;
        double maxZ = zCoord + 0.5 + width / 2.0;

        if (dir.offsetX > 0) {
            minX = xCoord + 1;
            maxX = xCoord + 1 + length;
        } else if (dir.offsetX < 0) {
            maxX = xCoord;
            minX = xCoord - length;
        } else if (dir.offsetY > 0) {
            minY = yCoord + 1;
            maxY = yCoord + 1 + length;
        } else if (dir.offsetY < 0) {
            maxY = yCoord;
            minY = yCoord - length;
        } else if (dir.offsetZ > 0) {
            minZ = zCoord + 1;
            maxZ = zCoord + 1 + length;
        } else if (dir.offsetZ < 0) {
            maxZ = zCoord;
            minZ = zCoord - length;
        }

        return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
