package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.state.NbtReader;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

public class EntityRocketSeat extends Entity implements IEntityAdditionalSpawnData {

    private Entity rocket;
    private int seatIndex;

    private double offsetX, offsetY, offsetZ;

    private int clientParentId = -1;
    private UUID parentId;
    private UUID vehicleId;
    private long projectionGeneration;

    public EntityRocketSeat(World world) {
        super(world);

        this.setSize(0f, 0f);
        this.noClip = true;
    }

    public EntityRocketSeat(World world, EntityRocket rocket, int seatIndex, double offsetX, double offsetY,
        double offsetZ) {
        this(world);
        this.rocket = rocket;
        this.parentId = rocket.getUniqueID();
        this.vehicleId = rocket.vehicleId();
        this.projectionGeneration = rocket.projectionGeneration();
        this.seatIndex = seatIndex;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    @Override
    protected void entityInit() {

    }

    public EntityRocket getRocket() {
        if (rocket instanceof EntityRocket castRocket && !rocket.isDead) {
            return castRocket;
        }
        if (worldObj.isRemote && clientParentId >= 0) {
            rocket = worldObj.getEntityByID(clientParentId);
            return rocket instanceof EntityRocket found && !found.isDead ? found : null;
        }
        if (!worldObj.isRemote && parentId != null) {
            for (Entity candidate : worldObj.loadedEntityList) {
                if (candidate instanceof EntityRocket found && !found.isDead && parentId.equals(found.getUniqueID())) {
                    rocket = found;
                    return found;
                }
            }
        }
        return null;
    }

    public int seatIndex() {
        return seatIndex;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!worldObj.isRemote) {
            var vehicle = vehicleId == null ? null
                : Galaxia.proxy.serverRuntime()
                    .rockets()
                    .find(vehicleId);
            if (vehicle == null || parentId == null || !vehicle.ownsProjection(parentId, projectionGeneration)) {
                this.setDead();
                return;
            }
        }
        EntityRocket parent = getRocket();
        if (parent != null) {

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
        var in = new NbtReader(tag, "rocketSeat");
        parentId = UUID.fromString(in.string("parent"));
        vehicleId = UUID.fromString(in.string("vehicle"));
        projectionGeneration = in.longValue("generation");
        if (projectionGeneration <= 0) throw new IllegalStateException("Invalid Rocket seat generation");
        this.seatIndex = tag.getInteger("SeatIndex");
        this.offsetX = tag.getDouble("OffsetX");
        this.offsetY = tag.getDouble("OffsetY");
        this.offsetZ = tag.getDouble("OffsetZ");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        if (parentId == null || vehicleId == null)
            throw new IllegalStateException("Unbound Rocket seat cannot be saved");
        tag.setString("parent", parentId.toString());
        tag.setString("vehicle", vehicleId.toString());
        tag.setLong("generation", projectionGeneration);
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
