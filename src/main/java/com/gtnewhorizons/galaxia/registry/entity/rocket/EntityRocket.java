package com.gtnewhorizons.galaxia.registry.entity.rocket;

import com.gtnewhorizons.galaxia.registry.block.tileentities.TileEntitySilo;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class EntityRocket extends Entity {

    TileEntitySilo silo;

    public boolean launched;

    public EntityRocket(World world) {
        super(world);
        this.noClip = true;
        this.preventEntitySpawning = true;
        this.setSize(1F, 1F);
    }

    public void bindSilo(TileEntitySilo silo) {
        this.silo = silo;
    }

    public void launch() {
        dataWatcher.updateObject(10, (byte) 1);
        dataWatcher.updateObject(11, silo.mods);
        silo.launch();
    }

    @Override
    protected void entityInit() {
        //launched
        dataWatcher.addObject(10, (byte) 0);
        //mods
        dataWatcher.addObject(11, 0);
    }

    public boolean shouldRender() {
        return dataWatcher.getWatchableObjectByte(10) == 1;
    }

    public int getMods() {
        return dataWatcher.getWatchableObjectInt(11);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!worldObj.isRemote) {

            if (riddenByEntity == null) {
                this.setDead();
            }
        }
        if (dataWatcher.getWatchableObjectByte(10) == 1) {
            this.motionY += 0.01D;
            this.moveEntity(this.motionX, this.motionY, this.motionZ);
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        launched = tag.getBoolean("launched");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setBoolean("launched", launched);
    }
}
