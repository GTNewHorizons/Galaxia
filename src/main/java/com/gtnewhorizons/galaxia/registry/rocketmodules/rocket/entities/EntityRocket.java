package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.CapsulePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.LanderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RiderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class EntityRocket extends Entity {

    public static final double SPAWN_ALTITUDE = 300.0;
    public static final double TERMINAL_FALL_SPEED = -0.5;

    private TileEntitySilo targetSilo;
    private RocketBlueprint blueprint = new RocketBlueprint();
    private boolean launched = false;
    private int destination = -1;
    private int capsuleIndex = 0;

    private final List<EntityRocketSeat> passengerSeats = new ArrayList<>();

    public EntityRocket(World world) {
        super(world);
        setSize(3f, 10f);
        noClip = true;
    }

    public void setBlueprint(RocketBlueprint bp) {
        this.blueprint = bp != null ? bp.copy() : new RocketBlueprint();
    }

    public RocketBlueprint getBlueprint() {
        return blueprint;
    }

    public void setTargetSilo(TileEntitySilo silo) {
        this.targetSilo = silo;
    }

    public void setDestination(int destination) {
        this.destination = destination;
    }

    public int getDestination() {
        return destination;
    }

    public void setCapsuleIndex(int index) {
        this.capsuleIndex = index;
    }

    public boolean shouldRender() {
        return !launched;
    }

    public void launch() {
        launched = true;
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

    public List<EntityRocketSeat> getPassengerSeats() {
        return passengerSeats;
    }

    public void beginLanding(double x, double z) {
        this.motionY = TERMINAL_FALL_SPEED;
        this.motionX = (worldObj.rand.nextDouble() - 0.5) * 0.05;
        this.motionZ = (worldObj.rand.nextDouble() - 0.5) * 0.05;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote || !launched) return;

        motionY += 0.08;
        moveEntity(motionX, motionY, motionZ);

        if (posY < 0) {
            setDead();
        }
    }

    @Override
    public boolean interactFirst(EntityPlayer player) {
        if (!worldObj.isRemote && !passengerSeats.isEmpty()) {
            player.mountEntity(this);
            return true;
        }
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        blueprint = RocketBlueprint.deserializeNBT(
            tag.getCompoundTag("blueprint"),
            com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry.instance());
        destination = tag.getInteger("destination");
        capsuleIndex = tag.getInteger("capsuleIndex");
        launched = tag.getBoolean("launched");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setTag("blueprint", blueprint.serializeNBT());
        tag.setInteger("destination", destination);
        tag.setInteger("capsuleIndex", capsuleIndex);
        tag.setBoolean("launched", launched);
    }

    @Override
    protected void entityInit() {}
}
