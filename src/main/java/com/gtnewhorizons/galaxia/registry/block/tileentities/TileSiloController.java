package com.gtnewhorizons.galaxia.registry.block.tileentities;

import com.gtnewhorizons.galaxia.registry.entity.rocket.ModuleData;
import com.gtnewhorizons.galaxia.registry.entity.rocket.RocketEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.List;

public class TileSiloController extends TileEntity {

    public final List<ModuleData> modules = new ArrayList<>();
    private RocketEntity rocket;

    public void addModule(ModuleData data) {
        if (data == null) return;
        modules.add(data);

        RocketEntity r = getOrCreateRocket();
        if (r != null) {
            r.resize(1.0F, getTotalHeight());
        }

        markDirty();
        if (!worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public RocketEntity getOrCreateRocket() {
        if (rocket != null) return rocket;

        // Поиск уже существующей (безопасно)
        for (Object o : worldObj.loadedEntityList) {
            if (o instanceof RocketEntity re &&
                re.getControllerX() == xCoord &&
                re.getControllerY() == yCoord &&
                re.getControllerZ() == zCoord) {
                rocket = re;
                return re;
            }
        }

        // Создаём новую
        System.err.println("[SERVER] Спавним RocketEntity с " + modules.size() + " модулями");
        rocket = new RocketEntity(worldObj, xCoord, yCoord, zCoord);
        worldObj.spawnEntityInWorld(rocket);
        System.err.println("[SERVER] RocketEntity заспавнена! id=" + rocket.getEntityId());
        return rocket;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);                    // сохраняем modules
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 5, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        readFromNBT(pkt.func_148857_g());   // читаем modules
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public void removeRocket() {
        if (rocket != null) {
            rocket.setDead();
            rocket = null;
        }
    }

    @Override
    public void invalidate() { removeRocket(); super.invalidate(); }
    @Override
    public void onChunkUnload() { removeRocket(); super.onChunkUnload(); }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (!worldObj.isRemote && rocket == null) getOrCreateRocket();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        modules.clear();
        NBTTagList list = nbt.getTagList("modules", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            modules.add(ModuleData.readFromNBT(list.getCompoundTagAt(i)));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagList list = new NBTTagList();
        for (ModuleData m : modules) {
            NBTTagCompound tag = new NBTTagCompound();
            m.writeToNBT(tag);
            list.appendTag(tag);
        }
        nbt.setTag("modules", list);
    }

    public int getTotalHeight() {
        int h = 1;
        for (ModuleData m : modules) h += m.type().height;
        return h;
    }
}
