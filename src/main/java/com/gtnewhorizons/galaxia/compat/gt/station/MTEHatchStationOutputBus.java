package com.gtnewhorizons.galaxia.compat.gt.station;

import static gregtech.api.enums.Textures.BlockIcons.ITEM_OUT_SIGN;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.util.GTUtility;

/**
 * Station output bus: an input bus of its multiblock that stocks itself from the shared storage of the station it
 * stands in, like a stocking input bus does
 * from an ME network. Each slot keeps a full stack of the item set in the matching stock slot, anything else in it
 * goes back to the station
 */
public class MTEHatchStationOutputBus extends MTEHatchInputBus {

    public static final int ID = 23054;
    public static final int TIER = 3;
    /** Ticks between two restocks */
    private static final int RESTOCK_INTERVAL = 20;

    private final StationLink link = new StationLink();
    /** What each slot keeps in stock, single items that only mark the kind */
    private final ItemStack[] stock = new ItemStack[getSlots(TIER)];

    public MTEHatchStationOutputBus(int id, String name, String nameRegional) {
        super(id, name, nameRegional, TIER, getSlots(TIER) + 1, null);
        disableSort = true;
    }

    public MTEHatchStationOutputBus(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
        disableSort = true;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchStationOutputBus(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return StatCollector.translateToLocal("galaxia.station_hatch.output_bus.desc")
            .split("\\\\n");
    }

    public int getStockSlots() {
        return stock.length;
    }

    public ItemStack getStock(int slot) {
        return slot >= 0 && slot < stock.length ? stock[slot] : null;
    }

    /** Sets what a slot keeps in stock, only the kind of the item counts */
    public void setStock(int slot, ItemStack item) {
        if (slot < 0 || slot >= stock.length) return;
        stock[slot] = GTUtility.copyAmount(1, item);
        markDirty();
    }

    /** True while the bus stands inside a station */
    public boolean isLinked() {
        return link.storage(getBaseMetaTileEntity()) != null;
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        super.onPostTick(base, tick);
        if (!base.isServerSide() || tick % RESTOCK_INTERVAL != 0) return;
        IDistributedInventory storage = link.storage(base);
        if (storage != null) restock(storage);
    }

    /** Sends unwanted items back to the station and tops every stocked slot up to a full stack */
    private void restock(IDistributedInventory storage) {
        boolean changed = false;
        for (int slot = 0; slot < stock.length; slot++) {
            ItemStackWrapper wanted = ItemStackWrapper.of(stock[slot]);
            ItemStack held = mInventory[slot];
            if (held != null && !ItemStackWrapper.of(held)
                .equals(wanted)) {
                held.stackSize -= (int) storage.updateItems(ItemStackWrapper.of(held), held.stackSize);
                if (held.stackSize <= 0) mInventory[slot] = null;
                changed = true;
                // A full station leaves the item where it is
                if (mInventory[slot] != null) continue;
                held = null;
            }
            if (wanted == null) continue;
            int full = Math.min(stock[slot].getMaxStackSize(), getInventoryStackLimit());
            int have = held == null ? 0 : held.stackSize;
            if (have >= full) continue;
            long got = storage.updateItems(wanted, -(full - have));
            if (got <= 0) continue;
            if (held == null) mInventory[slot] = wanted.toStack(got);
            else held.stackSize += (int) got;
            changed = true;
        }
        if (changed) markDirty();
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        NBTTagCompound stockTag = new NBTTagCompound();
        for (int slot = 0; slot < stock.length; slot++) {
            if (stock[slot] != null) stockTag.setTag("" + slot, stock[slot].writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("stationStock", stockTag);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        // Slots stay where the stock slots put them
        disableSort = true;
        NBTTagCompound stockTag = tag.getCompoundTag("stationStock");
        for (int slot = 0; slot < stock.length; slot++) {
            stock[slot] = stockTag.hasKey("" + slot)
                ? ItemStack.loadItemStackFromNBT(stockTag.getCompoundTag("" + slot))
                : null;
        }
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new StationOutputBusGui(this).build(data, syncManager, uiSettings);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return StationHatchTextures.output(base, ITEM_OUT_SIGN);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return StationHatchTextures.output(base, ITEM_OUT_SIGN);
    }
}
