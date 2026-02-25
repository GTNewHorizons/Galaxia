package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.gtnewhorizons.galaxia.rocketmodules.entities.EntityRocket;

public class TileEntitySilo extends TileEntity {

    private EntityRocket entityRocket;

    private final List<Integer> modules = new ArrayList<>();
    public boolean shouldRender = true;

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            if (entityRocket == null || entityRocket.isDead) {
                spawnSeat();
            }
        }
    }

    public void launch() {
        shouldRender = false;
    }

    private void spawnSeat() {
        entityRocket = new EntityRocket(worldObj);
        entityRocket.bindSilo(this);
        entityRocket.setPosition(xCoord + 0.5, yCoord + 1.0, zCoord + 0.5);
        worldObj.spawnEntityInWorld(entityRocket);
    }

    public EntityRocket getEntityRocket() {
        return entityRocket;
    }

    public List<Integer> getModules() {
        return new ArrayList<>(modules);
    }

    public int getNumModules() {
        return modules.size();
    }

    public int getModuleType(int index) {
        return index >= 0 && index < modules.size() ? modules.get(index) : 0;
    }

    public void addModule(int type) {
        modules.add(type);
        markDirty();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (entityRocket != null && !entityRocket.isDead) {
            entityRocket.setDead();
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 512 * 512;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("shouldRender", shouldRender);

        NBTTagList list = new NBTTagList();
        for (int type : modules) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("type", type);
            list.appendTag(entry);
        }
        nbt.setTag("modules", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        shouldRender = nbt.getBoolean("shouldRender");

        modules.clear();
        NBTTagList list = nbt.getTagList("modules", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            modules.add(entry.getInteger("type"));
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
