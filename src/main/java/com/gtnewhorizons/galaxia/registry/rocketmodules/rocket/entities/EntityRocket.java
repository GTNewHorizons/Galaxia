package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class EntityRocket extends Entity {

    private RocketBlueprint blueprint = new RocketBlueprint();
    private boolean launched = false;

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

    public void launch() {
        launched = true;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote || !launched) return;

        motionY += 0.08;
        moveEntity(0, motionY, 0);

        if (posY > 600) setDead();
    }

    @Override protected void entityInit() {}
    @Override protected void readEntityFromNBT(NBTTagCompound tag) {
        blueprint = RocketBlueprint.deserializeNBT(tag.getCompoundTag("blueprint"), RocketPartRegistry.instance());
    }
    @Override protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setTag("blueprint", blueprint.serializeNBT());
    }
}
