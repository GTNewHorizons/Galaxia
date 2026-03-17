package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.Vec3;

import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

public class TileEntityGantryTerminal extends TileEntityGantry {

    private TileEntitySilo connectedSilo;
    public Vec3 siloDir = null;
    private TileEntityModuleAssembler connectedAssembler;
    public Vec3 assemblerDir = null;

    /**
     * Connects a silo provided to this terminal
     *
     * @param silo The silo to connect
     */
    public void connectSilo(TileEntitySilo silo) {
        connectedSilo = silo;
    }

    /**
     * Connects a module assembler to this terminal
     *
     * @param assembler The assembler to connect
     */
    public void connectAssembler(TileEntityModuleAssembler assembler) {
        connectedAssembler = assembler;
    }

    public TileEntitySilo getSilo() {
        return connectedSilo;
    }

    public TileEntityModuleAssembler getAssembler() {
        return connectedAssembler;
    }

    public void sync() {
        markDirty();
        if (!worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /**
     * Passes a module to a linked consumer if it exists
     */
    public void passModuleToConsumer() {
        if (worldObj.isRemote) return;

        if (connectedSilo != null && connectedSilo.receiveModule(getModule().getId())) {
            clearModule();
            return;
        } else if (connectedAssembler != null) {
            connectedAssembler.addModule(getModule().getId());
            connectedAssembler.sync();
            clearModule();
            return;
        }
        return;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (connectedAssembler != null) {
            NBTTagCompound assemblerTag = new NBTTagCompound();
            assemblerTag.setInteger("x", connectedAssembler.xCoord);
            assemblerTag.setInteger("y", connectedAssembler.yCoord);
            assemblerTag.setInteger("z", connectedAssembler.zCoord);
            tag.setTag("assembler", assemblerTag);
        }
        if (connectedSilo != null) {
            NBTTagCompound siloTag = new NBTTagCompound();
            siloTag.setInteger("x", connectedSilo.xCoord);
            siloTag.setInteger("y", connectedSilo.yCoord);
            siloTag.setInteger("z", connectedSilo.zCoord);
            tag.setTag("silo", siloTag);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("assembler")) {
            NBTTagCompound assemblerTag = tag.getCompoundTag("assembler");
            assemblerDir = Vec3.createVectorHelper(
                assemblerTag.getDouble("x"),
                assemblerTag.getDouble("y"),
                assemblerTag.getDouble("z"));
        }
        if (tag.hasKey("silo")) {
            NBTTagCompound siloTag = tag.getCompoundTag("silo");
            siloDir = Vec3.createVectorHelper(siloTag.getDouble("x"), siloTag.getDouble("y"), siloTag.getDouble("z"));
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        super.getDescriptionPacket();
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);

        if (siloDir != null) {
            tag.setFloat("siloX", (float) siloDir.xCoord);
            tag.setFloat("siloY", (float) siloDir.yCoord);
            tag.setFloat("siloZ", (float) siloDir.zCoord);
        }

        if (assemblerDir != null) {
            tag.setFloat("assemblerX", (float) assemblerDir.xCoord);
            tag.setFloat("assemblerY", (float) assemblerDir.yCoord);
            tag.setFloat("assemblerZ", (float) assemblerDir.zCoord);
        }

        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

}
