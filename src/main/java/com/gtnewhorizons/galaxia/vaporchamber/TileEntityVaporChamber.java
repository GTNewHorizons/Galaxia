package com.gtnewhorizons.galaxia.vaporchamber;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import org.apache.commons.lang3.ArrayUtils;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;

public class TileEntityVaporChamber extends TileEntity {

    public static final Vec3[] CHECK_OFFSETS = { Vec3.createVectorHelper(1, 0, 0), Vec3.createVectorHelper(-1, 0, 0),
        Vec3.createVectorHelper(0, 0, 1), Vec3.createVectorHelper(0, 0, -1), Vec3.createVectorHelper(0, 1, 0),
        Vec3.createVectorHelper(0, -1, 0) };

    List<TileEntityVaporChamber> neighbours = new ArrayList<>();
    public Set<BlockPos> notNeighbours = new HashSet<>();

    private int refrigerantAmount = 1000;

    /**
     * Updates the tile entity.
     */
    @Override
    public void updateEntity() {
        if (worldObj.getBlock(xCoord, yCoord - 1, zCoord) == PlanetBlocks.MOON_MAGMA) {
            if (worldObj.getBlockMetadata(xCoord, yCoord - 1, zCoord) == 1) {

            }
        }

        try {
            // worldObj.playerEntities.get(0).addChatMessage(new
            // ChatComponentText(Integer.toString(neighbours.size())));
        } catch (Exception e) {

        }

        markDirty();
    }

    /**
     * Gets the list of neighbours that count as valid neighbours to this one
     *
     * @return The list of valid neighbours
     */
    public List<TileEntityVaporChamber> getNeighbours() {
        return neighbours;
    }

    /**
     * Connects another gantry to this one and updates both
     *
     * @param other The other gantry to connect to
     */
    public void connect(TileEntityVaporChamber other) {
        this.neighbours.add(other);
        other.neighbours.add(this);
        this.updateNeighbourDirs();
        other.updateNeighbourDirs();
        this.markDirty();
        other.markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.markBlockForUpdate(other.xCoord, other.yCoord, other.zCoord);
        }
    }

    /**
     * Update neighbour direction list
     */
    public void updateNeighbourDirs() {
        if (worldObj.isRemote) return;

        // Clear list and go through neighbours
        for (TileEntityVaporChamber neighbour : neighbours) {
            notNeighbours.remove(new BlockPos(neighbour.xCoord, neighbour.yCoord, neighbour.zCoord));
        }

        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        return;
    }

    /**
     * Disconnects the gantry from another one and updates both
     *
     * @param other The other gantry to disconnect from
     */
    public void disconnect(TileEntityVaporChamber other) {
        this.neighbours.remove(other);
        other.neighbours.remove(this);
        this.updateNeighbourDirs();
        other.updateNeighbourDirs();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.markBlockForUpdate(other.xCoord, other.yCoord, other.zCoord);
        }

    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);

        int[] array = new int[] {};
        for (TileEntityVaporChamber neighbour : neighbours) {
            array = ArrayUtils.addAll(array, neighbour.xCoord, neighbour.yCoord, neighbour.zCoord);
        }

        tag.setIntArray("neighbours", array);

        array = new int[] {};
        for (BlockPos pos : notNeighbours) {
            array = ArrayUtils.addAll(array, pos.x, pos.y, pos.z);
        }

        tag.setIntArray("notNeighbours", array);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        if (tag.hasKey("neighbours")) {
            int[] array = {};
            for (int integer : tag.getIntArray("neighbours")) {
                array = ArrayUtils.add(array, integer);
                if (array.length == 3) {
                    if (worldObj.getTileEntity(array[0], array[1], array[2]) instanceof TileEntityVaporChamber teev) {
                        neighbours.add(teev);
                    }
                    array = new int[] {};
                }
            }
        }

        if (tag.hasKey("notNeighbours")) {
            int[] array = {};
            for (int integer : tag.getIntArray("notNeighbours")) {
                array = ArrayUtils.add(array, integer);
                if (array.length == 3) {
                    if (!(worldObj.getTileEntity(array[0], array[1], array[2]) instanceof TileEntityVaporChamber)) {
                        notNeighbours.add(new BlockPos(array[0], array[1], array[2]));
                    }
                    array = new int[] {};
                }
            }
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);

        int[] array = new int[] {};
        for (TileEntityVaporChamber neighbour : neighbours) {
            array = ArrayUtils.addAll(array, neighbour.xCoord, neighbour.yCoord, neighbour.zCoord);
        }

        tag.setIntArray("neighbours", array);

        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();

        int[] array = {};
        for (int integer : tag.getIntArray("neighbours")) {
            array = ArrayUtils.add(array, integer);
            if (array.length == 3) {
                if (worldObj.getTileEntity(array[0], array[1], array[2]) instanceof TileEntityVaporChamber teev) {
                    neighbours.add(teev);
                }
                array = new int[] {};
            }
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
}
