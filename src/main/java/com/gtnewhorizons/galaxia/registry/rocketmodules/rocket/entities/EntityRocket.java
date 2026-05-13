package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class EntityRocket extends Entity {

    public static final double SPAWN_ALTITUDE = 300.0;
    public static final double TERMINAL_FALL_SPEED = -0.5;

    private TileEntitySilo targetSilo;
    private List<Integer> modules = new ArrayList<>();

    private RocketBlueprint blueprint = new RocketBlueprint();
    private boolean launched = false;
    private int destination = -1;
    private int capsuleIndex = 0;

    public EntityRocket(World world) {
        super(world);
        setSize(3f, 10f);
        noClip = true;
    }

    public void setModules(List<Integer> modules) {
        this.modules = modules != null ? new ArrayList<>(modules) : new ArrayList<>();
        // TODO: В будущем конвертировать в blueprint
    }

    public void setTargetSilo(TileEntitySilo silo) {
        this.targetSilo = silo;
    }

    public void turnToLanderAndCache() {
        // TODO: Реализовать логику превращения в лендер (оставляем заглушкой)
        // Сейчас просто оставляем blueprint как есть
    }

    public void initializeSeats() {
        // TODO: Создание EntityRocketSeat для пассажиров
    }

    public void beginLanding(double x, double z) {
        // TODO: Начать процесс посадки
        this.motionY = TERMINAL_FALL_SPEED;
    }

    public List<EntityRocketSeat> getPassengerSeats() {
        // TODO: Вернуть список пассажирских мест
        return new ArrayList<>();
    }

    public void setBlueprint(RocketBlueprint bp) {
        this.blueprint = bp != null ? bp.copy() : new RocketBlueprint();
    }

    public RocketBlueprint getBlueprint() {
        return blueprint;
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

    public boolean interactFirst(EntityPlayer player) {
        // TODO: open rocket GUI / mount a player etc, temporary placeholder
        return true;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote || !launched) return;

        motionY += 0.08;
        moveEntity(0, motionY, 0);

        if (posY > 600) {
            setDead();
        }
    }

    @Override protected void entityInit() {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        blueprint = RocketBlueprint.deserializeNBT(tag.getCompoundTag("blueprint"), RocketPartRegistry.instance());
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
}
