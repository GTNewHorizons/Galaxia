package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;

public class EntityRocket extends Entity {

    private RocketBlueprint blueprint;
    private boolean ascending = true;
    private int launchTicks = 0;

    public EntityRocket(World world) {
        super(world);
        setSize(3f, 1f);
        noClip = true;
    }

    public void setBlueprint(RocketBlueprint bp) {
        this.blueprint = bp;
    }

    @Override
    protected void entityInit() {}

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) return;
        if (ascending) {
            launchTicks++;
            if (launchTicks > 40) {
                motionY += 0.05;
                moveEntity(0, motionY, 0);
            }
            if (posY > 500) {
                ascending = false;
                setDead();
            }
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {}

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {}
}
