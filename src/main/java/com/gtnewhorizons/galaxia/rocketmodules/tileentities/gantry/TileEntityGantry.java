package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.Constants.NBT;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.utility.TransitModule;

public class TileEntityGantry extends TileEntity {

    private final float SPEED = 0.1f;
    private final Deque<TransitModule> queue = new ArrayDeque<>();
    private final static int DISPATCH_INTERVAL = 20;
    private int dispatchCooldown = 0;
    private List<int[]> pendingNeighbourCoords = new ArrayList<>();

    List<TileEntityGantry> neighbours = new ArrayList<>();
    private Vec3 currentDirection;
    private float progress = 0f;
    private TransitModule containedTransitModule;

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

        if (!queue.isEmpty()) {
            if (dispatchCooldown > 0) {
                dispatchCooldown--;
            } else if (containedTransitModule == null) {
                TransitModule entry = queue.poll();
                containedTransitModule = entry;
                currentDirection = GantryAPI.getDirectionTo(this, entry.destination());
                progress = 0f;
                dispatchCooldown = DISPATCH_INTERVAL;
                markDirty();

            }
        }

        if (containedTransitModule == null) return;

        progress += SPEED;

        if (progress >= 1.0f) {
            progress = 1.0f;
            tryHandOff();
        }
        markDirty();
    }

    public Vec3 getDirection() {
        return currentDirection;
    }

    public void setDirection(Vec3 dir) {
        currentDirection = dir;
    }

    public void clearModule() {
        progress = 0f;
        containedTransitModule = null;
        currentDirection = null;
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

    public RocketModule getModule() {
        if (containedTransitModule == null) return null;
        return containedTransitModule.module();
    }

    // TODO:
    public void tryHandOff() {

        TileEntityGantry next = getNeighbourGantry(currentDirection);
        if (next == null || next == this) {
            if (this instanceof TileEntityGantryTerminal teg) {
                teg.passModuleToConsumer();
            }
            clearModule();
            return;
        }

        if (next.acceptModule(containedTransitModule)) {
            clearModule();
            if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
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

    public void enqueueModule(TransitModule transit) {
        queue.addLast(transit);
        if (dispatchCooldown <= 0) dispatchCooldown = DISPATCH_INTERVAL;
        markDirty();
    }

    public boolean acceptModule(TransitModule transit) {
        if (worldObj.isRemote) return false;
        if (transit == null) {
            return false;
        }
        enqueueModule(transit);
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
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
