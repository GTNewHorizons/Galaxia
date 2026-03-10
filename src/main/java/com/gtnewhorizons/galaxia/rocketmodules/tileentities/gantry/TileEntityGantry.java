package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public class TileEntityGantry extends TileEntity {

    private final float SPEED = 0.05f;

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

}
