package com.gtnewhorizons.galaxia.rocketmodules.entities;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry.ModuleInfo;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

public class EntityRocket extends Entity {

    private TileEntitySilo silo;
    private final List<Integer> modules = new ArrayList<>();
    private int capsuleIndex = -1;

    public EntityRocket(World world) {
        super(world);
        this.noClip = true;
        this.preventEntitySpawning = true;
        this.setSize(3.0F, 1.0F);
    }

    public void bindSilo(TileEntitySilo silo) {
        this.silo = silo;
    }

    public void setCapsuleIndex(int index) {
        this.capsuleIndex = index;
        dataWatcher.updateObject(12, index);
    }

    public int getCapsuleIndex() {
        return worldObj.isRemote ? dataWatcher.getWatchableObjectInt(12) : capsuleIndex;
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
        dataWatcher.addObject(10, (byte) 0); // launched
        dataWatcher.addObject(11, ""); // modules
        dataWatcher.addObject(12, -1); // capsuleIndex
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

    private double getTotalHeight() {
        double h = 0.0;
        for (int t : modules) {
            ModuleInfo info = ModuleRegistry.getModule(t);
            h += info != null ? info.height() : 2.0;
        }
        return h;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!worldObj.isRemote) {
            if (riddenByEntity == null) this.setDead();
        }

        if (dataWatcher.getWatchableObjectByte(10) == 1) {
            this.motionY += 0.01D;
            this.moveEntity(this.motionX, this.motionY, this.motionZ);
        }

        double totalH = getTotalHeight();
        float newH = (float) (totalH + 0.5);
        if (Math.abs(this.height - newH) > 0.05F) {
            this.setSize(3.0F, newH);
        }
    }

    @Override
    public double getMountedYOffset() {
        int cIdx = getCapsuleIndex();
        if (cIdx < 0) return this.height * 0.75D;

        List<Integer> types = getModuleTypes();
        double offset = 0.0;
        for (int i = 0; i < cIdx && i < types.size(); i++) {
            ModuleInfo info = ModuleRegistry.getModule(types.get(i));
            offset += info != null ? info.height() : 2.0;
        }

        ModuleInfo capInfo = ModuleRegistry.getModule(types.get(cIdx));
        double capH = capInfo != null ? capInfo.height() : 2.5;
        return offset + capH * 0.4D;
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
        tag.setInteger("capsuleIndex", capsuleIndex);
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
        capsuleIndex = tag.getInteger("capsuleIndex");
    }
}
