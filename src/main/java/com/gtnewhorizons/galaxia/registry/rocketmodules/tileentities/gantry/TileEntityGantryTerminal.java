package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.Vec3;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

import lombok.Getter;

/**
 * Entry and exit point of a gantry network. Each terminal is linked to the multiblock it is part of and its mode
 * decides whether modules may enter, leave or both
 */
public class TileEntityGantryTerminal extends TileEntityGantry {

    private TileEntitySilo connectedSilo;
    public Vec3 siloDir = null;
    private TileEntityModuleAssembler connectedAssembler;
    public Vec3 assemblerDir = null;
    @Getter
    private TerminalMode mode = TerminalMode.BOTH;

    /**
     * Connects a silo provided to this terminal
     *
     * @param silo The silo to connect
     */
    public void connectSilo(TileEntitySilo silo) {
        if (connectedSilo != silo) refreshNetworkRoles();
        connectedSilo = silo;
    }

    /**
     * Connects a module assembler to this terminal
     *
     * @param assembler The assembler to connect
     */
    public void connectAssembler(TileEntityModuleAssembler assembler) {
        if (connectedAssembler != assembler) refreshNetworkRoles();
        connectedAssembler = assembler;
    }

    public TileEntitySilo getSilo() {
        return connectedSilo != null && !connectedSilo.isInvalid() ? connectedSilo : null;
    }

    public TileEntityModuleAssembler getAssembler() {
        return connectedAssembler != null && !connectedAssembler.isInvalid() ? connectedAssembler : null;
    }

    /** Cycles input / output / both and tells the network */
    public void cycleMode() {
        mode = mode.next();
        refreshNetworkRoles();
        sync();
    }

    /** Roles are read live from terminals, routes only depend on rails, so nothing has to be rebuilt */
    private void refreshNetworkRoles() {
        markDirty();
    }

    /**
     * Position of the controller of the linked machine, used to animate modules into it
     *
     * @return x, y, z of the controller, or null when nothing is linked
     */
    public int[] getConsumerPosition() {
        TileEntitySilo silo = getSilo();
        if (silo != null) return new int[] { silo.xCoord, silo.yCoord, silo.zCoord };
        TileEntityModuleAssembler assembler = getAssembler();
        if (assembler != null) return new int[] { assembler.xCoord, assembler.yCoord, assembler.zCoord };
        return null;
    }

    public void sync() {
        markDirty();
        if (!worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /**
     * Passes a module to the linked consumer
     *
     * @param part The module arriving at this terminal
     *
     * @return Boolean : True => the machine took the module
     */
    public boolean passModuleToConsumer(RocketPartInstance part) {
        if (worldObj.isRemote || part == null || !mode.delivers) return false;

        TileEntitySilo silo = getSilo();
        if (silo != null && silo.isStructureValid()) {
            return silo.receiveModulePart(part);
        }
        TileEntityModuleAssembler assembler = getAssembler();
        if (assembler != null && assembler.isStructureValid()) {
            assembler.addPart(part.def());
            return true;
        }
        return false;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("mode", mode.ordinal());
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
        readMode(tag);
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

    private void readMode(NBTTagCompound tag) {
        int ordinal = tag.getInteger("mode");
        TerminalMode[] modes = TerminalMode.values();
        mode = ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : TerminalMode.BOTH;
    }

    @Override
    protected void writeDescription(NBTTagCompound tag) {
        super.writeDescription(tag);
        tag.setInteger("mode", mode.ordinal());
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        super.onDataPacket(net, packet);
        readMode(packet.func_148857_g());
    }
}
