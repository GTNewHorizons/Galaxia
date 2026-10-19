package com.gtnewhorizons.galaxia.compat.gt.station;

import static gregtech.api.enums.Textures.BlockIcons.ITEM_IN_SIGN;

import net.minecraft.item.ItemStack;
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
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

/**
 * Station input bus: an output bus of its multiblock that hands everything put into it over to the shared storage
 * of the station it stands in
 */
public class MTEHatchStationInputBus extends MTEHatchOutputBus {

    public static final int ID = 23053;
    public static final int TIER = 3;
    /** Ticks between two hand-overs */
    private static final int TRANSFER_INTERVAL = 20;

    private final StationLink link = new StationLink();

    public MTEHatchStationInputBus(int id, String name, String nameRegional) {
        super(id, name, nameRegional, TIER);
    }

    public MTEHatchStationInputBus(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchStationInputBus(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return StatCollector.translateToLocal("galaxia.station_hatch.input_bus.desc")
            .split("\\n");
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        super.onPostTick(base, tick);
        if (!base.isServerSide() || tick % TRANSFER_INTERVAL != 0) return;
        IDistributedInventory storage = link.storage(base);
        if (storage == null) return;
        boolean changed = false;
        for (int slot = 0; slot < mInventory.length; slot++) {
            ItemStack stack = mInventory[slot];
            if (stack == null || stack.stackSize <= 0) continue;
            long stored = storage.updateItems(ItemStackWrapper.of(stack), stack.stackSize);
            if (stored <= 0) continue;
            stack.stackSize -= (int) stored;
            if (stack.stackSize <= 0) mInventory[slot] = null;
            changed = true;
        }
        if (changed) markDirty();
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new StationInputBusGui(this).build(data, syncManager, uiSettings);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return StationHatchTextures.input(base, getBaseMetaTileEntity().getColorization(), ITEM_IN_SIGN);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return StationHatchTextures.input(base, getBaseMetaTileEntity().getColorization(), ITEM_IN_SIGN);
    }
}
