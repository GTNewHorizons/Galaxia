package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketGuiStyle;

/**
 * Where a space elevator ends in orbit. Everything the elevator below sends up lands in its buffer, from where pipes
 * and the station take it. While an elevator is connected the cable is drawn hanging down from the dock
 */
public class TileElevatorDock extends TileEntity implements IInventory, IFluidHandler, IGuiHolder<PosGuiData> {

    public static final int SLOTS = 27;
    public static final int TANKS = 4;
    public static final int TANK_CAPACITY = 64_000;
    /** Ticks an elevator counts as connected after its last delivery cycle */
    private static final int LINK_TIMEOUT = 100;

    private final ItemStack[] inventory = new ItemStack[SLOTS];
    private final FluidTank[] tanks = new FluidTank[TANKS];
    private long lastLinkTime = Long.MIN_VALUE / 2;
    private boolean linked;

    public TileElevatorDock() {
        for (int i = 0; i < TANKS; i++) tanks[i] = new FluidTank(TANK_CAPACITY);
    }

    /** Called by the elevator below on each delivery cycle */
    public void markLinked() {
        lastLinkTime = worldObj.getTotalWorldTime();
        if (!linked) {
            linked = true;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public boolean isLinked() {
        return linked;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote || !linked) return;
        if (worldObj.getTotalWorldTime() - lastLinkTime > LINK_TIMEOUT) {
            linked = false;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /**
     * Puts items into the buffer, merging with matching stacks first
     *
     * @param stack Items to insert, not changed
     *
     * @return Number of items that did not fit
     */
    public int insertItem(ItemStack stack) {
        int left = stack.stackSize;
        for (int i = 0; i < SLOTS && left > 0; i++) {
            ItemStack slot = inventory[i];
            if (slot == null || !slot.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(slot, stack)) continue;
            int moved = Math.min(left, Math.min(slot.getMaxStackSize(), getInventoryStackLimit()) - slot.stackSize);
            if (moved <= 0) continue;
            slot.stackSize += moved;
            left -= moved;
        }
        for (int i = 0; i < SLOTS && left > 0; i++) {
            if (inventory[i] != null) continue;
            ItemStack placed = stack.copy();
            placed.stackSize = Math.min(left, stack.getMaxStackSize());
            inventory[i] = placed;
            left -= placed.stackSize;
        }
        if (left != stack.stackSize) markDirty();
        return left;
    }

    // region Inventory

    @Override
    public int getSizeInventory() {
        return SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = inventory[slot];
        if (stack == null) return null;
        if (stack.stackSize <= amount) {
            inventory[slot] = null;
            markDirty();
            return stack;
        }
        markDirty();
        return stack.splitStack(amount);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        inventory[slot] = stack;
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "tile.elevator_dock.name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }

    // endregion

    // region Fluids

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        // Top up a tank holding the same fluid before starting an empty one
        for (FluidTank tank : tanks) {
            if (tank.getFluid() != null && tank.getFluid()
                .isFluidEqual(resource)) {
                int filled = tank.fill(resource, doFill);
                if (filled > 0) {
                    if (doFill) markDirty();
                    return filled;
                }
            }
        }
        for (FluidTank tank : tanks) {
            if (tank.getFluidAmount() == 0) {
                int filled = tank.fill(resource, doFill);
                if (doFill && filled > 0) markDirty();
                return filled;
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null) return null;
        for (FluidTank tank : tanks) {
            if (tank.getFluid() != null && tank.getFluid()
                .isFluidEqual(resource)) {
                FluidStack drained = tank.drain(resource.amount, doDrain);
                if (doDrain && drained != null) markDirty();
                return drained;
            }
        }
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        for (FluidTank tank : tanks) {
            if (tank.getFluidAmount() > 0) {
                FluidStack drained = tank.drain(maxDrain, doDrain);
                if (doDrain && drained != null) markDirty();
                return drained;
            }
        }
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        FluidTankInfo[] info = new FluidTankInfo[TANKS];
        for (int i = 0; i < TANKS; i++) info[i] = tanks[i].getInfo();
        return info;
    }

    public FluidTank getTank(int index) {
        return tanks[index];
    }

    // endregion

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = RocketGuiStyle.panel("galaxia:elevator_dock", 176, 196);
        panel.child(
            RocketGuiStyle.title("tile.elevator_dock.name")
                .pos(8, 7));
        panel
            .child(
                RocketGuiStyle
                    .label(
                        () -> net.minecraft.util.StatCollector.translateToLocal(
                            linked ? "galaxia.elevator_dock.linked" : "galaxia.elevator_dock.unlinked"),
                        () -> linked ? RocketGuiStyle.GOOD : RocketGuiStyle.TEXT_DIM,
                        0.6f)
                    .pos(8, 18));
        InvWrapper items = new InvWrapper(this);
        for (int i = 0; i < SLOTS; i++) {
            panel.child(
                new ItemSlot().slot(new ModularSlot(items, i))
                    .background(EnumTextures.ROCKET_SLOT.getImage())
                    .pos(7 + (i % 9) * 18, 28 + (i / 9) * 18));
        }
        for (int i = 0; i < TANKS; i++) {
            panel.child(
                new FluidSlot().syncHandler(new FluidSlotSyncHandler(tanks[i]).canFillSlot(false))
                    .size(36, 16)
                    .pos(7 + i * 41, 86));
        }
        panel.child(
            RocketGuiStyle.playerInventory()
                .pos(7, 112));
        return panel;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList items = new NBTTagList();
        for (int i = 0; i < SLOTS; i++) {
            if (inventory[i] == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte("slot", (byte) i);
            inventory[i].writeToNBT(entry);
            items.appendTag(entry);
        }
        tag.setTag("items", items);
        NBTTagList fluids = new NBTTagList();
        for (FluidTank tank : tanks) fluids.appendTag(tank.writeToNBT(new NBTTagCompound()));
        tag.setTag("tanks", fluids);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        NBTTagList items = tag.getTagList("items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            int slot = entry.getByte("slot");
            if (slot >= 0 && slot < SLOTS) inventory[slot] = ItemStack.loadItemStackFromNBT(entry);
        }
        NBTTagList fluids = tag.getTagList("tanks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < Math.min(TANKS, fluids.tagCount()); i++) tanks[i].readFromNBT(fluids.getCompoundTagAt(i));
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("linked", linked);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        linked = packet.func_148857_g()
            .getBoolean("linked");
    }
}
