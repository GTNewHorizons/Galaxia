package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.Constants.NBT;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public class TileEntityGantry extends TileEntity {

    private final float SPEED = 0.05f;
    private List<int[]> pendingNeighbourCoords = new ArrayList<>();

    List<TileEntityGantry> neighbours = new ArrayList<>();
    private Vec3 sendDirection;
    private Vec3 receiveDirection;
    private Vec3 currentDirection;
    private float progress = 0f;
    private RocketModule containedModule;
    private boolean isReceiving;

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;

        if (!pendingNeighbourCoords.isEmpty()) {
            for (int[] coords : pendingNeighbourCoords) {
                TileEntity te = worldObj.getTileEntity(coords[0], coords[1], coords[2]);
                if (te instanceof TileEntityGantry teg) {
                    neighbours.add(teg);
                }
            }
            pendingNeighbourCoords.clear();
        }

        if (containedModule == null) return;

        progress += SPEED;

        if (progress >= 1.0f) {
            progress = 1.0f;
            tryHandOff();
        }
        markDirty();
    }

    public boolean checkValidGraph() {
        return GantryAPI.terminatesWithTerminals(worldObj, xCoord, yCoord, zCoord);
    }

    public void connect(TileEntityGantry other) {
        this.neighbours.add(other);
        other.neighbours.add(this);
    }

    public void disconnect(TileEntityGantry other) {
        this.neighbours.remove(other);
        other.neighbours.remove(this);

    }

    public void updateTransferDirection() {
        if (isReceiving) {
            currentDirection = receiveDirection;
            return;
        }
        currentDirection = sendDirection;
        return;
    }

    // TODO:
    public void tryHandOff() {

        TileEntityGantry next = getNeighbourGantry(currentDirection);
        // TODO: Handle properly
        if (next == null) return;

        if (next.acceptModule(containedModule, isReceiving)) {
            containedModule = null;
            currentDirection = null;
            progress = 0f;
        }
    }

    public TileEntityGantry getNeighbourGantry(Vec3 dir) {
        if (dir == null) return null;
        int nx = xCoord + (int) dir.xCoord;
        int ny = yCoord + (int) dir.yCoord;
        int nz = zCoord + (int) dir.zCoord;
        TileEntity te = worldObj.getTileEntity(nx, ny, nz);
        return (te instanceof TileEntityGantry ? (TileEntityGantry) te : null);
    }

    public boolean acceptModule(RocketModule module, boolean isReceiving) {
        if (containedModule != null) return false;
        containedModule = module;
        this.isReceiving = isReceiving;
        progress = 0f;
        markDirty();
        return true;

    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList neighbourList = new NBTTagList();
        for (TileEntityGantry neighbour : neighbours) {
            NBTTagCompound neighbourTag = new NBTTagCompound();
            neighbourTag.setInteger("x", neighbour.xCoord);
            neighbourTag.setInteger("y", neighbour.yCoord);
            neighbourTag.setInteger("z", neighbour.zCoord);
            neighbourList.appendTag(neighbourTag);
        }
        tag.setTag("neighbours", neighbourList);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        NBTTagList neighbourList = tag.getTagList("neighbours", NBT.TAG_COMPOUND);
        pendingNeighbourCoords = new ArrayList<>();

        for (int i = 0; i < neighbourList.tagCount(); i++) {
            NBTTagCompound entry = neighbourList.getCompoundTagAt(i);
            pendingNeighbourCoords
                .add(new int[] { entry.getInteger("x"), entry.getInteger("y"), entry.getInteger("z"), });
        }
    }

}
