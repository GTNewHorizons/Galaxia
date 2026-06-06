package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

public class EntityRocketSeat extends Entity implements IEntityAdditionalSpawnData {

    private Entity rocket;
    private int seatIndex;

    private double offsetX, offsetY, offsetZ;

    private int clientParentId = -1;

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

    public EntityRocket getRocket() {
        if (rocket instanceof EntityRocket castRocket) {
            return castRocket;
        }
        return null;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (worldObj.isRemote && rocket == null && clientParentId != -1) {
            Entity parent = worldObj.getEntityByID(clientParentId);
            if (parent != null) {
                this.rocket = parent;
            }
        }

        if (!worldObj.isRemote) {
            if (rocket == null || rocket.isDead) {
                this.setDead();
                return;
            }
        }

        if (rocket != null) {

            this.motionX = rocket.motionX;
            this.motionY = rocket.motionY;
            this.motionZ = rocket.motionZ;

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

    @Override
    public void writeSpawnData(ByteBuf buf) {
        // Send parent ID and offsets
        buf.writeInt(rocket != null ? rocket.getEntityId() : -1);
        buf.writeInt(seatIndex);
        buf.writeDouble(offsetX);
        buf.writeDouble(offsetY);
        buf.writeDouble(offsetZ);
    }

    @Override
    public void readSpawnData(ByteBuf buf) {
        this.clientParentId = buf.readInt();
        this.seatIndex = buf.readInt();
        this.offsetX = buf.readDouble();
        this.offsetY = buf.readDouble();
        this.offsetZ = buf.readDouble();
    }
}
