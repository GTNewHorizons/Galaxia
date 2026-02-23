package com.gtnewhorizons.galaxia.block.tileentities;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.gtnewhorizons.galaxia.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.modules.ModuleType;
import com.gtnewhorizons.galaxia.modules.ModuleTypes;

public class TileEntityModuleController extends TileEntity {

    private String moduleId;
    private final List<int[]> shellPositions = new ArrayList<>();

    public void setModule(String id) {
        this.moduleId = id;
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
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

    public ModuleType getType() {
        return ModuleTypes.byId(moduleId);
    }

    public void buildStructure() {
        ModuleType type = getType();
        if (type == null || worldObj == null) return;

        shellPositions.clear();

        int ix = type.getInternalSizeX(), iy = type.getInternalSizeY(), iz = type.getInternalSizeZ();
        int wt = type.getWallThickness();
        int fullX = ix + 2 * wt;
        int fullY = iy + 2 * wt;
        int fullZ = iz + 2 * wt;

        for (int dx = 0; dx < fullX; dx++) {
            for (int dy = 0; dy < fullY; dy++) {
                for (int dz = 0; dz < fullZ; dz++) {
                    boolean isInternal = dx >= wt && dx < wt + ix
                        && dy >= wt
                        && dy < wt + iy
                        && dz >= wt
                        && dz < wt + iz;
                    if (isInternal) continue;

                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    int wx = xCoord + dx;
                    int wy = yCoord + dy;
                    int wz = zCoord + dz;

                    worldObj.setBlock(wx, wy, wz, GalaxiaBlocksEnum.MODULE_SHELL.get());
                    shellPositions.add(new int[] { wx, wy, wz });
                }
            }
        }
        markDirty();
    }

    public void destroyStructure() {
        for (int[] p : shellPositions) {
            if (worldObj.getBlock(p[0], p[1], p[2]) == GalaxiaBlocksEnum.MODULE_SHELL.get()) {
                worldObj.setBlockToAir(p[0], p[1], p[2]);
            }
        }
        shellPositions.clear();
        markDirty();
    }

    @Override
    public void invalidate() {
        destroyStructure();
        super.invalidate();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (moduleId != null) nbt.setString("moduleId", moduleId);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        moduleId = nbt.hasKey("moduleId") ? nbt.getString("moduleId") : null;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }
}
