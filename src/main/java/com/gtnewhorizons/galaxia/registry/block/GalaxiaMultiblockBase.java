package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

public abstract class GalaxiaMultiblockBase<T extends GalaxiaMultiblockBase<T>> extends TileEntity
    implements ISurvivalConstructable {

    protected boolean structureValid = false;
    private int mCheckTimer = 0;

    public abstract IStructureDefinition<T> getStructureDefinition();

    protected abstract int getControllerOffsetX();

    protected abstract int getControllerOffsetY();

    protected abstract int getControllerOffsetZ();

    public abstract Block getControllerBlock();

    public boolean isStructureValid() {
        return structureValid;
    }

    public void markStructureDirty() {
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

        boolean valid = getStructureDefinition().check(
            (T) this,
            "main",
            worldObj,
            ExtendedFacing.DEFAULT,
            xCoord,
            yCoord,
            zCoord,
            getControllerOffsetX(),
            getControllerOffsetY(),
            getControllerOffsetZ(),
            false);

        if (valid != structureValid) {
            structureValid = valid;
            if (valid) onStructureFormed();
            else onStructureDisformed();
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        return valid;
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
            ExtendedFacing.DEFAULT,
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
            ExtendedFacing.DEFAULT,
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

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj.isRemote) return;

        if (mCheckTimer <= 0) {
            checkStructure();
            mCheckTimer = 100;
        } else {
            mCheckTimer--;
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

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("structureValid", structureValid);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        structureValid = nbt.getBoolean("structureValid");
        mCheckTimer = 0;
    }
}
