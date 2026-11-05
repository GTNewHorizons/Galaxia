package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/**
 * Cable of the space elevator, with the climber that rides it. Taken from GTNH Intergalactic, its state reaches the
 * client through the usual description packet instead of Galacticraft's networking
 */
public class TileElevatorCable extends TileEntity {

    public enum ClimberAnimation {
        /** Let the climber sit on its spot */
        NO_ANIMATION,
        /** Animation that drives the climber into orbit, pauses a bit and then drives down */
        DELIVER_ANIMATION,
        /** Animation that spawns the climber in orbit and then drives it down */
        FORMATION_ANIMATION,
    }

    /** Vertical movement speed of the climber */
    private static final float VERTICAL_SPEED = 1.0f;
    /** Rotation speed of the climber */
    private static final float ROTATION_SPEED = 0.5f;
    /** Max height which the climber will drive up to */
    private static final float MAX_HEIGHT = 250.0f;
    /** Time in ticks that determines how long the climber will wait in its up position */
    private static final int STANDBY_TIME_TICKS = 200;
    /** Distance which the climber uses to (de-)accelerate */
    private static final int ACCELERATION_DISTANCE = 30;
    /** Factor with which the climber (de-)accelerates */
    private static final float ACCELERATION_FACTOR = 1.0f / ACCELERATION_DISTANCE;

    /** Flag if the cable should be rendered */
    private boolean shouldRender = false;
    /** Current height of the climber */
    private float currentHeight = MAX_HEIGHT;
    /** Current rotation of the climber */
    private float currentRotation = 0;
    /** Flag if the climber is moving */
    private boolean isMoving = false;
    /** Flag if the climber is rotating */
    private boolean isRotating = false;
    /** Flag if the climber is currently moving up or down */
    private boolean isMovingDirectionUp = true;
    /** Flag if the climber is currently moving clockwise or anti-clockwise */
    private boolean isRotationDirectionClockwise = true;
    /** Current time that the climber is waiting for to move vertical again */
    private int currentWaitTime = 0;
    /** Current animation of the climber */
    private ClimberAnimation animation = ClimberAnimation.NO_ANIMATION;

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 20 * super.getMaxRenderDistanceSquared();
    }

    public float getClimberHeight() {
        return currentHeight;
    }

    public float getClimberRotation() {
        return currentRotation;
    }

    /**
     * Shows or hides the cable. Showing it lets the climber come down from orbit
     *
     * @param shouldRender True if it should be rendered, else false
     */
    public void setShouldRender(boolean shouldRender) {
        if (this.shouldRender == shouldRender) return;
        if (shouldRender) {
            startAnimation(ClimberAnimation.FORMATION_ANIMATION);
        } else {
            currentRotation = 0f;
            currentHeight = MAX_HEIGHT;
        }
        this.shouldRender = shouldRender;
        sync();
    }

    public boolean shouldRender() {
        return shouldRender;
    }

    @Override
    public void updateEntity() {
        switch (animation) {
            case DELIVER_ANIMATION -> animateDelivery();
            case FORMATION_ANIMATION -> animateFormation();
            default -> {}
        }
    }

    /** Starts a climber animation, on the client as well */
    public void startAnimation(ClimberAnimation animation) {
        this.animation = animation;
        isMoving = true;
        isRotating = true;
        sync();
    }

    public ClimberAnimation getAnimation() {
        return animation;
    }

    public void stopAnimation() {
        animation = ClimberAnimation.NO_ANIMATION;
        isRotating = false;
        isMoving = false;
    }

    private void sync() {
        markDirty();
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        writeState(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        readState(compound);
    }

    private void writeState(NBTTagCompound compound) {
        compound.setFloat("height", currentHeight);
        compound.setFloat("rotation", currentRotation);
        compound.setBoolean("verticalDirection", isMovingDirectionUp);
        compound.setBoolean("rotationDirection", isRotationDirectionClockwise);
        compound.setBoolean("rotationMovement", isRotating);
        compound.setBoolean("verticalMovement", isMoving);
        compound.setInteger("waitTime", currentWaitTime);
        compound.setBoolean("shouldRender", shouldRender);
        compound.setInteger("animation", animation.ordinal());
    }

    private void readState(NBTTagCompound compound) {
        currentHeight = compound.getFloat("height");
        currentRotation = compound.getFloat("rotation");
        isMoving = compound.getBoolean("verticalMovement");
        isRotating = compound.getBoolean("rotationMovement");
        isMovingDirectionUp = compound.getBoolean("verticalDirection");
        isRotationDirectionClockwise = compound.getBoolean("rotationDirection");
        currentWaitTime = compound.getInteger("waitTime");
        shouldRender = compound.getBoolean("shouldRender");
        int ordinal = compound.getInteger("animation");
        animation = ordinal >= 0 && ordinal < ClimberAnimation.values().length ? ClimberAnimation.values()[ordinal]
            : ClimberAnimation.NO_ANIMATION;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeState(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        boolean rendered = shouldRender;
        readState(packet.func_148857_g());
        // The block renderer draws the plain cable block only while the full cable is hidden
        if (rendered != shouldRender)
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
    }

    private void animateFormation() {
        if (isRotating) {
            if (isRotationDirectionClockwise) {
                currentRotation = (currentRotation + ROTATION_SPEED) % 360f;
            } else {
                currentRotation = (currentRotation - ROTATION_SPEED) % 360f;
            }
        }
        if (isMoving) {
            if (currentHeight <= 0.0f) {
                isMovingDirectionUp = true;
                stopAnimation();
            } else {
                if (currentHeight < ACCELERATION_DISTANCE) {
                    currentHeight -= VERTICAL_SPEED * ACCELERATION_FACTOR * Math.max(currentHeight, 1);
                } else {
                    currentHeight -= VERTICAL_SPEED;
                }
            }
        }
    }

    private void animateDelivery() {
        if (isRotating) {
            if (isRotationDirectionClockwise) {
                currentRotation = (currentRotation + ROTATION_SPEED) % 360f;
            } else {
                currentRotation = (currentRotation - ROTATION_SPEED) % 360f;
            }
        }
        if (isMoving && currentWaitTime <= 0) {
            if (isMovingDirectionUp) {
                if (currentHeight >= MAX_HEIGHT) {
                    isMovingDirectionUp = false;
                    currentWaitTime = STANDBY_TIME_TICKS;
                } else {
                    if (currentHeight <= ACCELERATION_DISTANCE) {
                        currentHeight += VERTICAL_SPEED * ACCELERATION_FACTOR * Math.max(currentHeight, 1);
                    } else if (currentHeight >= MAX_HEIGHT - ACCELERATION_DISTANCE) {
                        currentHeight += VERTICAL_SPEED * ACCELERATION_FACTOR * Math.max(MAX_HEIGHT - currentHeight, 1);
                    } else {
                        currentHeight += VERTICAL_SPEED;
                    }
                }
            } else {
                if (currentHeight <= 0.0f) {
                    isMovingDirectionUp = true;
                    stopAnimation();
                } else {
                    if (currentHeight < ACCELERATION_DISTANCE) {
                        currentHeight -= VERTICAL_SPEED * ACCELERATION_FACTOR * Math.max(currentHeight, 1);
                    } else if (currentHeight >= MAX_HEIGHT - ACCELERATION_DISTANCE) {
                        currentHeight -= VERTICAL_SPEED * ACCELERATION_FACTOR * Math.max(MAX_HEIGHT - currentHeight, 1);
                    } else {
                        currentHeight -= VERTICAL_SPEED;
                    }
                }
            }
        } else if (currentWaitTime > 0) {
            currentWaitTime--;
        }
    }
}
