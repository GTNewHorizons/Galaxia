package com.gtnewhorizons.galaxia.registry.celestial.station.devices;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.items.special.ItemStationCartridge;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

/**
 * A station machine that wears down a cartridge while it works and takes a fresh one from the station's storage once
 * the old one is used up
 */
public abstract class TileStationCartridgeDevice extends TileStationDevice implements IInventory {

    public static final long ENERGY_USAGE = 1024;

    private ItemStack filter;
    private int ticksSinceWear;

    /** The cartridge the machine wears down */
    protected abstract GalaxiaItemList cartridge();

    /** Lang key shown while the machine has no cartridge */
    protected abstract String missingCartridgeKey();

    @Override
    public long getEnergyUsage() {
        return ENERGY_USAGE;
    }

    private boolean isFilter(ItemStack stack) {
        return stack != null && stack.getItem() == cartridge().getItem();
    }

    @Override
    protected @Nullable String missingInput() {
        return isFilter(filter) ? null : missingCartridgeKey();
    }

    @Override
    protected void consumeInput() {
        if (++ticksSinceWear < ItemStationCartridge.TICKS_PER_WEAR) return;
        ticksSinceWear = 0;
        if (ItemStationCartridge.wear(filter)) filter = null;
        markDirty();
    }

    @Override
    protected void supply(Station storage) {
        if (filter != null) return;
        ItemStackWrapper fresh = ItemStackWrapper.of(new ItemStack(cartridge().getItem()));
        if (storage.updateItems(fresh, -1) > 0) {
            filter = fresh.toStack(1);
            markDirty();
        }
    }

    @Override
    protected void addDeviceWidgets(ModularPanel panel, PanelSyncManager syncManager) {
        panel.child(
            new ItemSlot().slot(new ModularSlot(new InvWrapper(this), 0).filter(this::isFilter))
                .background(EnumTextures.GUI_SLOT.getImage())
                .pos(8, CONTENT_Y - 1));
        panel.child(
            GalaxiaGuiStyle
                .label(() -> StatCollector.translateToLocal(cartridge().getNameKey()), () -> GalaxiaGuiStyle.TEXT, 1f)
                .pos(30, CONTENT_Y + 4));
    }

    // region Inventory

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return filter;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (filter == null || amount <= 0) return null;
        ItemStack taken = filter;
        filter = null;
        markDirty();
        return taken;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        filter = stack;
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "tile." + blockName() + ".name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
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
        return isFilter(stack);
    }

    // endregion

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (filter != null) tag.setTag("filter", filter.writeToNBT(new NBTTagCompound()));
        tag.setInteger("ticksSinceWear", ticksSinceWear);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        filter = tag.hasKey("filter") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("filter")) : null;
        ticksSinceWear = tag.getInteger("ticksSinceWear");
    }
}
