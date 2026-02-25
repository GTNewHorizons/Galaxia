package com.gtnewhorizons.galaxia.rocketmodules.entities;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

public class EntityRocket extends Entity {

    private TileEntitySilo silo;
    private final List<Integer> modules = new ArrayList<>();

    public EntityRocket(World world) {
        super(world);
        this.noClip = true;
        this.preventEntitySpawning = true;
        this.setSize(3.0F, 1.0F);
    }

    public void bindSilo(TileEntitySilo silo) {
        this.silo = silo;
    }

    public void launch() {
        dataWatcher.updateObject(10, (byte) 1);

        modules.clear();
        modules.addAll(silo.getModules());

        StringBuilder sb = new StringBuilder();
        for (int t : modules) {
            if (sb.length() > 0) sb.append(",");
            sb.append(t);
        }
        dataWatcher.updateObject(11, sb.toString());

        silo.launch();
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(10, (byte) 0);
        dataWatcher.addObject(11, "");
    }

    public boolean shouldRender() {
        return dataWatcher.getWatchableObjectByte(10) == 1;
    }

    public List<Integer> getModuleTypes() {
        if (worldObj.isRemote) {
            String ser = dataWatcher.getWatchableObjectString(11);
            if (ser == null || ser.isEmpty()) return new ArrayList<>();
            String[] parts = ser.split(",");
            List<Integer> list = new ArrayList<>(parts.length);
            for (String p : parts) {
                try {
                    list.add(Integer.parseInt(p.trim()));
                } catch (Exception ignored) {}
            }
            return list;
        }
        return new ArrayList<>(modules);
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

        if (worldObj.isRemote) {
            List<Integer> types = getModuleTypes();
            float newH = 1.0F + types.size() * 2.5F;
            if (Math.abs(this.height - newH) > 0.1F) {
                this.setSize(3.0F, newH);
            }
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (int type : modules) {
            NBTTagCompound e = new NBTTagCompound();
            e.setInteger("type", type);
            list.appendTag(e);
        }
        tag.setTag("modules", list);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        modules.clear();
        NBTTagList list = tag.getTagList("modules", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            modules.add(
                list.getCompoundTagAt(i)
                    .getInteger("type"));
        }
    }
}
