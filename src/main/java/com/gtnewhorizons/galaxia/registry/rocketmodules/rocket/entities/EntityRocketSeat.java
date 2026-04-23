package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public class EntityRocketSeat extends Entity {

    private Entity rocket;
    private int seatIndex;

    private double offsetX, offsetY, offsetZ;

    public EntityRocketSeat(World world) {
        super(world);

        this.setSize(0f, 0f);
        this.noClip = true;
    }

    public EntityRocketSeat(World world, Entity rocket, int seatIndex, double offsetX, double offsetY, double offsetZ) {
        this(world);
        this.rocket = rocket;
        this.seatIndex = seatIndex;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    @Override
    protected void entityInit() {

    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!worldObj.isRemote) {
            if (rocket == null || rocket.isDead) {
                this.setDead();
                return;
            }
        }

        if (rocket != null) {
            double yaw = Math.toRadians(rocket.rotationYaw);

            double rotatedX = offsetX * Math.cos(yaw) - offsetZ * Math.sin(yaw);
            double rotatedZ = offsetX * Math.sin(yaw) + offsetZ * Math.cos(yaw);

            this.setPosition(rocket.posX + rotatedX, rocket.posY + offsetY, rocket.posZ + rotatedZ);
        }
    }

    @Override
    public double getMountedYOffset() {
        return 0;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        this.seatIndex = tag.getInteger("SeatIndex");
        this.offsetX = tag.getDouble("OffsetX");
        this.offsetY = tag.getDouble("OffsetY");
        this.offsetZ = tag.getDouble("OffsetZ");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setInteger("SeatIndex", this.seatIndex);
        tag.setDouble("OffsetX", this.offsetX);
        tag.setDouble("OffsetY", this.offsetY);
        tag.setDouble("OffsetZ", this.offsetZ);
    }
}
