package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;

import cpw.mods.fml.common.Optional;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IMachineBlockUpdateable;
import gregtech.api.util.GTUtility;
import lombok.Getter;
import lombok.Setter;

@Optional.Interface(iface = "gregtech.api.interfaces.tileentity.IMachineBlockUpdateable", modid = "gregtech_nh")
public abstract class GalaxiaMultiblockBase<T extends GalaxiaMultiblockBase<T>> extends TileEntity
    implements ISurvivalConstructable, IMachineBlockUpdateable, IAlignment {

    /** Front faces a horizontal direction, no rotation or mirroring */
    public static final IAlignmentLimits HORIZONTAL = (direction, rotation,
        flip) -> direction.offsetY == 0 && rotation.isNotRotated() && flip.isNotFlipped();
    /** Like {@link #HORIZONTAL}, but the structure may be mirrored left to right */
    public static final IAlignmentLimits HORIZONTAL_MIRRORABLE = (direction, rotation,
        flip) -> direction.offsetY == 0 && rotation.isNotRotated() && !flip.isVerticallyFliped();
    /** Front faces any of the six directions, no rotation or mirroring */
    public static final IAlignmentLimits ANY_DIRECTION = (direction, rotation, flip) -> rotation.isNotRotated()
        && flip.isNotFlipped();

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
            if (!GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded()) this.updated = true;

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
        // The structure is always checked with currentFacing, so the front texture has to follow it
        placedFacing = currentFacing.getDirection();
        mCheckTimer = 0;
    }

    public void setFacing(ForgeDirection dir) {
        if (dir == null || dir == ForgeDirection.UNKNOWN) return;
        toolSetDirection(dir);
    }

    // IAlignment

    @Override
    public ExtendedFacing getExtendedFacing() {
        return currentFacing;
    }

    /**
     * Applies a new orientation and schedules a structure check. Tools should go through
     * {@link #toolSetExtendedFacing(ExtendedFacing)} so the limits are respected
     *
     * @param facing The new orientation
     */
    @Override
    public void setExtendedFacing(ExtendedFacing facing) {
        if (facing == null || facing == currentFacing) return;
        currentFacing = facing;
        placedFacing = facing.getDirection();
        onFacingChanged();
        markStructureDirty();
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return HORIZONTAL;
    }

    /** Called after the orientation changed, before the structure is re-checked */
    protected void onFacingChanged() {}

    /**
     * Faces the controller towards whoever placed it, falling back to a horizontal direction when the vertical one is
     * not allowed
     *
     * @param placer The entity that placed the controller
     */
    public void onPlacedBy(EntityLivingBase placer) {
        if (!toolSetDirection(PlacementHelper.placeInEveryDirection(placer))) {
            toolSetDirection(PlacementHelper.placeOnlyCardinal(placer));
        }
    }

    /**
     * GTNH-style wrenching: hitting a side turns the front towards it, hitting the front rotates the structure and
     * sneaking on the front mirrors it
     *
     * @param player The player holding the wrench
     * @param side   The side that was hit, already adjusted for the wrench grid
     *
     * @return Boolean : True => the orientation changed
     */
    public boolean onWrench(EntityPlayer player, ForgeDirection side) {
        boolean changed;
        if (side == currentFacing.getDirection()) {
            changed = player.isSneaking() ? toolSetFlip(null) : toolSetRotation(null);
        } else {
            changed = toolSetDirection(side);
        }

        if (changed) {
            player.addChatComponentMessage(
                new ChatComponentTranslation("galaxia.chat.multiblock.facing", currentFacing.getLocalizedName()));
        }
        return changed;
    }

    /**
     * Shared wrench handling for every multiblock controller block, call it first in
     * {@link Block#onBlockActivated}
     *
     * @return Boolean : True => the click was a wrench on a controller and has been consumed
     */
    public static boolean handleWrench(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!isWrench(player.getHeldItem())) return false;
        if (!(world.getTileEntity(x, y, z) instanceof GalaxiaMultiblockBase<?>multiblock)) return false;
        if (world.isRemote) return true;

        ForgeDirection clicked = ForgeDirection.getOrientation(side);
        ForgeDirection target = GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded()
            ? GTUtility.determineWrenchingSide(clicked, hitX, hitY, hitZ)
            : clicked;
        if (multiblock.onWrench(player, target)) {
            world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "random.anvil_use", 0.4F, 1.4F);
        }
        return true;
    }

    public static boolean isWrench(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem()
            .getToolClasses(stack)
            .contains("wrench")) return true;
        return GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded()
            && GTUtility.isStackInList(stack, GregTechAPI.sWrenchList);
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
        if (this.worldObj != null && this.worldObj.isRemote) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }
}
