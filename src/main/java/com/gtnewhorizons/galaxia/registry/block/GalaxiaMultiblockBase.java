package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;

import cpw.mods.fml.common.Optional;
import gregtech.api.interfaces.tileentity.IMachineBlockUpdateable;
import lombok.Getter;
import lombok.Setter;

@Optional.Interface(iface = "gregtech.api.interfaces.tileentity.IMachineBlockUpdateable", modid = "gregtech")
public abstract class GalaxiaMultiblockBase<T extends GalaxiaMultiblockBase<T>> extends TileEntity
    implements ISurvivalConstructable, IMachineBlockUpdateable {

    @Setter
    @Getter
    protected ForgeDirection placedFacing = ForgeDirection.NORTH;
    @Getter
    protected ExtendedFacing currentFacing = ExtendedFacing.DEFAULT;
    protected int mCheckTimer = 0;
    protected boolean updated = true;

    @Getter
    protected boolean structureValid = false;
    protected boolean isChunkUnloading = false;
    protected boolean reloadHappened = false;

    public abstract IStructureDefinition<T> getStructureDefinition();

    protected abstract int getControllerOffsetX();

    protected abstract int getControllerOffsetY();

    protected abstract int getControllerOffsetZ();

    protected boolean needsFormationOnReload() {
        return true;
    }

    public abstract Block getControllerBlock();

    public void markStructureDirty() {
        updated = true;
        mCheckTimer = 0;
    }

    // does practically nothing but must be implemented
    @Override
    public String[] getStructureDescription(ItemStack trigger) {
        return new String[0];
    }

    @SuppressWarnings("unchecked")
    protected boolean checkStructure() {
        if (worldObj == null || worldObj.isRemote) return structureValid;

        return getStructureDefinition().check(
            (T) this,
            "main",
            worldObj,
            currentFacing,
            xCoord,
            yCoord,
            zCoord,
            getControllerOffsetX(),
            getControllerOffsetY(),
            getControllerOffsetZ(),
            false);
    }

    protected final boolean checkPiece(String piece, int horizontalOffset, int verticalOffset, int depthOffset) {
        return getStructureDefinition().check(
            (T) this,
            piece,
            worldObj,
            currentFacing,
            xCoord,
            yCoord,
            zCoord,
            horizontalOffset,
            verticalOffset,
            depthOffset,
            false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        if (worldObj == null) return;
        if (!hintsOnly && worldObj.isRemote) return;

        getStructureDefinition().buildOrHints(
            (T) this,
            trigger,
            "main",
            worldObj,
            currentFacing,
            xCoord,
            yCoord,
            zCoord,
            getControllerOffsetX(),
            getControllerOffsetY(),
            getControllerOffsetZ(),
            hintsOnly);
    }

    @SuppressWarnings("unchecked")
    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (worldObj == null || worldObj.isRemote) return -1;
        if (structureValid) return -1;
        return getStructureDefinition().survivalBuild(
            (T) this,
            trigger,
            "main",
            worldObj,
            currentFacing,
            xCoord,
            yCoord,
            zCoord,
            getControllerOffsetX(),
            getControllerOffsetY(),
            getControllerOffsetZ(),
            elementBudget,
            env,
            false);
    }

    protected void onStructureFormed() {}

    protected void onStructureDisformed() {}

    protected void onStructureChecked() {}

    protected void reset() {
        structureValid = false;
        updated = true;
        onStructureDisformed();
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj == null || worldObj.isRemote) return;

        if (mCheckTimer <= 0) {
            // Just in case, but a proper checking with onMachineBlockUpdate would be better
            if (!GalaxiaAPI.isGregTechLoaded()) this.updated = true;

            if (this.updated) {
                this.updated = false;

                final boolean valid = checkStructure();
                if (valid != structureValid) {
                    structureValid = valid;
                    if (valid) onStructureFormed();
                    else onStructureDisformed();

                    markDirty();
                    worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                }

                onStructureChecked();
            }
            mCheckTimer = 100;
        } else {
            mCheckTimer--;
        }
    }

    @Override
    public void onMachineBlockUpdate() {
        this.updated = true;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 512 * 512;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("structureValid", structureValid);
        nbt.setInteger("facing", currentFacing.getIndex());
        nbt.setInteger("placedFacing", placedFacing.ordinal());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        structureValid = nbt.getBoolean("structureValid");
        if (!reloadHappened && needsFormationOnReload()) {
            structureValid = false;
        }
        reloadHappened = true;
        if (nbt.hasKey("facing")) {
            currentFacing = ExtendedFacing.byIndex(nbt.getInteger("facing"));
        }
        if (nbt.hasKey("placedFacing")) {
            placedFacing = ForgeDirection.values()[nbt.getInteger("placedFacing")];
        }
        mCheckTimer = 0;
    }

    public void setFacing(ForgeDirection dir) {
        if (dir == null) return;

        switch (dir) {

            case NORTH:
                this.currentFacing = ExtendedFacing.NORTH_NORMAL_NONE;
                break;

            case SOUTH:
                this.currentFacing = ExtendedFacing.SOUTH_NORMAL_NONE;
                break;

            case EAST:
                this.currentFacing = ExtendedFacing.EAST_NORMAL_NONE;
                break;

            case WEST:
                this.currentFacing = ExtendedFacing.WEST_NORMAL_NONE;
                break;

            case UP:
                this.currentFacing = ExtendedFacing.UP_NORMAL_NONE;
                break;

            case DOWN:
                this.currentFacing = ExtendedFacing.DOWN_NORMAL_NONE;
                break;

            default:
                this.currentFacing = ExtendedFacing.DEFAULT;
                break;
        }

        markDirty();

        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        isChunkUnloading = true;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
