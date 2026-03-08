package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;

import com.gtnewhorizons.galaxia.rocketmodules.link.ILinkable;

public class TileEntityMonorailPole extends TileEntity implements ILinkable {

    private ChunkCoordinates prevPos = null;
    private ChunkCoordinates nextPos = null;

    @Override
    public String getLinkableName() {
        return "Monorail Pole";
    }

    @Override
    public boolean canBeSlave() {
        return true;
    }

    @Override
    public boolean canBeMaster() {
        return prevPos != null;
    }

    @Override
    public void setMasterPos(ChunkCoordinates pos) {
        this.prevPos = pos;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public ChunkCoordinates getMasterPos() {
        return prevPos;
    }

    @Override
    public void onSlaveLinked(TileEntity slave, EntityPlayer player) {
        this.nextPos = new ChunkCoordinates(slave.xCoord, slave.yCoord, slave.zCoord);
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void onSlaveUnlinked(TileEntity slave) {
        this.nextPos = null;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public ChunkCoordinates getPrevPos() {
        return prevPos;
    }

    public ChunkCoordinates getNextPos() {
        return nextPos;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("prevX")) {
            prevPos = new ChunkCoordinates(tag.getInteger("prevX"), tag.getInteger("prevY"), tag.getInteger("prevZ"));
        } else {
            prevPos = null;
        }
        if (tag.hasKey("nextX")) {
            nextPos = new ChunkCoordinates(tag.getInteger("nextX"), tag.getInteger("nextY"), tag.getInteger("nextZ"));
        } else {
            nextPos = null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (prevPos != null) {
            tag.setInteger("prevX", prevPos.posX);
            tag.setInteger("prevY", prevPos.posY);
            tag.setInteger("prevZ", prevPos.posZ);
        }
        if (nextPos != null) {
            tag.setInteger("nextX", nextPos.posX);
            tag.setInteger("nextY", nextPos.posY);
            tag.setInteger("nextZ", nextPos.posZ);
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }
}
