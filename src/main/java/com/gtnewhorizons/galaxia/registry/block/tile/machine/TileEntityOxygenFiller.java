package com.gtnewhorizons.galaxia.registry.block.tile.machine;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidTank;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.IItemHandler;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.gui.OxygenFillerGUI;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;

public class TileEntityOxygenFiller extends TileEntityGalaxiaMachine implements IInventory {

    public static final int SLOT_COUNT = 6;

    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];
    private final IItemHandler itemHandler = new InvWrapper(this);

    protected FluidTank oxygenTank;

    public TileEntityOxygenFiller() {
        this.oxygenTank = new FluidTank(getMaxOxygenBuffer());
    }

    @Override
    public FluidTank getOxygenTank() {
        return oxygenTank;
    }

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    public double getMaxEnergyBuffer() {
        return ConfigMachines.filler.maxEnergyBuffer;
    }

    @Override
    public int getMaxOxygenBuffer() {
        return ConfigMachines.filler.maxOxygenBuffer;
    }

    @Override
    protected double getEuPerOperation() {
        return ConfigMachines.filler.euPerOperation;
    }

    @Override
    protected int getWorkIntervalTicks() {
        return ConfigMachines.filler.ticksPerOperation;
    }

    @Override
    protected void doWork() {
        if (getStoredOxygen() <= 0) return;

        int oxygenPerCycle = ConfigMachines.filler.oxygenPerOperation;
        int remaining = Math.min(oxygenPerCycle, getStoredOxygen());

        for (int i = 0; i < SLOT_COUNT && remaining > 0; i++) {
            ItemStack stack = slots[i];
            if (stack == null || !(stack.getItem() instanceof ItemOxygenTank tankItem)) continue;

            int current = tankItem.getCurrentOxygen(stack);
            int max = tankItem.getMaxOxygen();
            int space = max - current;
            if (space <= 0) continue;

            int fill = Math.min(remaining, space);
            tankItem.fillTank(stack, fill);
            remaining -= fill;
            active = true;
        }

        drainOxygen(oxygenPerCycle - remaining, true);
    }

    @Override
    protected void writeMachineNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots[i] != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setByte("slot", (byte) i);
                slots[i].writeToNBT(entry);
                list.appendTag(entry);
            }
        }
        tag.setTag("fillerSlots", list);
    }

    @Override
    protected void readMachineNBT(NBTTagCompound tag) {
        NBTTagList list = tag.getTagList("fillerSlots", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getByte("slot") & 0xFF;
            if (slot < SLOT_COUNT) {
                slots[slot] = ItemStack.loadItemStackFromNBT(entry);
            }
        }
    }

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slots[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slots[slot] == null) return null;
        if (slots[slot].stackSize <= amount) {
            ItemStack stack = slots[slot];
            slots[slot] = null;
            markDirty();
            return stack;
        }
        ItemStack split = slots[slot].splitStack(amount);
        markDirty();
        return split;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = slots[slot];
        slots[slot] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        slots[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "galaxia.gui.oxygen_filler.title";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
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
        return stack != null && stack.getItem() instanceof ItemOxygenTank;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return OxygenFillerGUI.build(this, guiData, syncManager);
    }
}
