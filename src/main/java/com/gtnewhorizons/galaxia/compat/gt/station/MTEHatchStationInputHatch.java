package com.gtnewhorizons.galaxia.compat.gt.station;

import static gregtech.api.enums.Textures.BlockIcons.FLUID_IN_SIGN;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;

/**
 * Station input hatch: an output hatch of its multiblock that hands the fluid put into it over to the shared
 * storage of the station it stands in
 */
public class MTEHatchStationInputHatch extends MTEHatchOutput {

    public static final int ID = 23055;
    public static final int TIER = 3;
    /** Ticks between two hand-overs */
    private static final int TRANSFER_INTERVAL = 20;

    private final StationLink link = new StationLink();

    public MTEHatchStationInputHatch(int id, String name, String nameRegional) {
        super(id, name, nameRegional, TIER);
    }

    public MTEHatchStationInputHatch(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchStationInputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return StatCollector.translateToLocal("galaxia.station_hatch.input_hatch.desc")
            .split("\\n");
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        super.onPostTick(base, tick);
        if (!base.isServerSide() || tick % TRANSFER_INTERVAL != 0 || mFluid == null || mFluid.amount <= 0) return;
        IDistributedInventory storage = link.storage(base);
        if (storage == null) return;
        long stored = storage.updateFluids(FluidKey.of(mFluid), mFluid.amount);
        if (stored <= 0) return;
        mFluid.amount -= (int) stored;
        if (mFluid.amount <= 0) mFluid = null;
        markDirty();
    }

    /** True while the hatch stands inside a station */
    public boolean isLinked() {
        return link.storage(getBaseMetaTileEntity()) != null;
    }

    // The output modes and fluid lock of a GT output hatch do not apply, every fluid goes into the station

    @Override
    public boolean outputsSteam() {
        return true;
    }

    @Override
    public boolean outputsLiquids() {
        return true;
    }

    @Override
    public boolean isFluidLocked() {
        return false;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {}

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new StationInputHatchGui(this).build(data, syncManager, uiSettings);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return StationHatchTextures.input(base, getBaseMetaTileEntity().getColorization(), FLUID_IN_SIGN);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return StationHatchTextures.input(base, getBaseMetaTileEntity().getColorization(), FLUID_IN_SIGN);
    }
}
