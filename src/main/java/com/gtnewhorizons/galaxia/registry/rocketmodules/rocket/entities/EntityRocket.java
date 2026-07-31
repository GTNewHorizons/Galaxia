package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.CapsulePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.LanderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RiderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import lombok.Getter;
import lombok.Setter;

public class EntityRocket extends Entity implements IEntityAdditionalSpawnData {

    public static final double SPAWN_ALTITUDE = 300.0;
    public static final double TERMINAL_FALL_SPEED = -0.5;
    private static final double MAX_THRUST = 2.0;

    private TileEntitySilo targetSilo;

    private int siloX, siloY, siloZ;
    private boolean hasSilo = false;

    @Getter
    private RocketBlueprint blueprint = new RocketBlueprint();

    @Setter
    @Getter
    private int destination = -1;

    @Setter
    private int capsuleIndex = 0;

    @Getter
    private final List<EntityRocketSeat> passengerSeats = new ArrayList<>();

    public EntityRocket(World world) {
        super(world);
        setSize(3f, 10f);
        noClip = true;
        this.ignoreFrustumCheck = true;
    }

    public boolean isPlayerAboard(EntityPlayer player) {
        if (riddenByEntity == player) return true;
        for (EntityRocketSeat seat : passengerSeats) {
            if (seat.riddenByEntity == player) return true;
        }
        return false;
    }

    public void setBlueprint(RocketBlueprint bp) {
        this.blueprint = bp != null ? bp.copy() : new RocketBlueprint();
    }

    public boolean shouldRender() {
        return isLaunched();
    }

    public boolean isLaunched() {
        return this.dataWatcher.getWatchableObjectByte(16) == 1;
    }

    public void launch() {
        if (worldObj.isRemote) return;
        this.dataWatcher.updateObject(16, (byte) 1);

        if (targetSilo != null) {
            targetSilo.onRocketLaunched();
        }
    }

    public void turnToLanderAndCache() {
        List<RocketPartInstance> toKeep = new ArrayList<>();
        for (RocketPartInstance part : blueprint.getParts()) {
            IRocketPartDef def = part.def();
            if (def instanceof LanderPartDef || def instanceof RiderPartDef || def instanceof CapsulePartDef) {
                toKeep.add(part);
            }
        }
        blueprint.clear();
        for (RocketPartInstance p : toKeep) {
            blueprint.addPart(p);
        }
    }

    public void initializeSeats() {
        passengerSeats.clear();

        int riderCount = 0;
        for (RocketPartInstance part : blueprint.getParts()) {
            if (part.def() instanceof RiderPartDef rider) {
                for (int i = 0; i < rider.riderCapacity(); i++) {
                    EntityRocketSeat seat = new EntityRocketSeat(worldObj, this, riderCount++, 0.0, 1.5 + i * 0.8, 0.0);
                    worldObj.spawnEntityInWorld(seat);
                    passengerSeats.add(seat);
                }
            }
        }
    }

    public void beginLanding(double x, double z) {
        this.motionY = TERMINAL_FALL_SPEED;
        this.motionX = (worldObj.rand.nextDouble() - 0.5) * 0.05;
        this.motionZ = (worldObj.rand.nextDouble() - 0.5) * 0.05;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!worldObj.isRemote && hasSilo && targetSilo == null) {
            TileEntity te = worldObj.getTileEntity(siloX, siloY, siloZ);
            if (te instanceof TileEntitySilo silo) {
                targetSilo = silo;
            }
        }

        if (!isLaunched()) return;

        motionY += 0.08;
        if (motionY > MAX_THRUST) {
            motionY = MAX_THRUST;
        }

        moveEntity(motionX, motionY, motionZ);

        if (worldObj.isRemote) return;

        if (posY < 0 || posY > 1000) {
            setDead();
        }
    }

    @Override
    public boolean interactFirst(EntityPlayer player) {
        if (worldObj.isRemote || passengerSeats.isEmpty() && !hasCapsule()) return false;

        for (EntityRocketSeat seat : passengerSeats) {
            if (seat.riddenByEntity == null) {
                player.mountEntity(seat);
                return true;
            }
        }

        if (hasCapsule() && riddenByEntity == null) {
            player.mountEntity(this);
            return true;
        }
        return false;
    }

    private boolean hasCapsule() {
        return blueprint.getParts()
            .stream()
            .anyMatch(p -> p.def() instanceof CapsulePartDef);
    }

    public void setTargetSilo(TileEntitySilo silo) {
        this.targetSilo = silo;
        if (silo != null) {
            this.siloX = silo.xCoord;
            this.siloY = silo.yCoord;
            this.siloZ = silo.zCoord;
            this.hasSilo = true;
        } else {
            this.hasSilo = false;
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        blueprint = RocketBlueprint.deserializeNBT(
            tag.getCompoundTag("blueprint"),
            com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry.instance());
        destination = tag.getInteger("destination");
        capsuleIndex = tag.getInteger("capsuleIndex");

        siloX = tag.getInteger("siloX");
        siloY = tag.getInteger("siloY");
        siloZ = tag.getInteger("siloZ");
        hasSilo = tag.getBoolean("hasSilo");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setTag("blueprint", blueprint.serializeNBT());
        tag.setInteger("destination", destination);
        tag.setInteger("capsuleIndex", capsuleIndex);

        tag.setInteger("siloX", siloX);
        tag.setInteger("siloY", siloY);
        tag.setInteger("siloZ", siloZ);
        tag.setBoolean("hasSilo", hasSilo);
    }

    @Override
    protected void entityInit() {
        this.dataWatcher.addObject(16, (byte) 0);
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("blueprint", blueprint.serializeNBT());
        tag.setInteger("destination", destination);

        try {
            CompressedStreamTools.write(tag, new ByteBufOutputStream(buffer));
        } catch (IOException _) {}
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        try {
            DataInputStream stream = new DataInputStream(new ByteBufInputStream(buffer));
            NBTTagCompound tag = CompressedStreamTools.read(stream);
            blueprint = RocketBlueprint.deserializeNBT(tag.getCompoundTag("blueprint"), RocketPartRegistry.instance());
            destination = tag.getInteger("destination");
        } catch (IOException _) {}
    }
}
