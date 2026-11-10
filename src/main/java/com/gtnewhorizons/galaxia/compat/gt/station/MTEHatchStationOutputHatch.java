package com.gtnewhorizons.galaxia.compat.gt.station;

import static gregtech.api.enums.Textures.BlockIcons.FLUID_OUT_SIGN;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchMultiInput;
import gregtech.api.util.GTUtility;

/**
 * Station output hatch: an input hatch of its multiblock that fills itself from the shared storage of the station
 * it stands in, like a stocking input hatch does
 * from an ME network. Each tank keeps the fluid set in the matching stock slot, anything else in it goes back to the
 * station
 */
public class MTEHatchStationOutputHatch extends MTEHatchMultiInput {

    public static final int ID = 23056;
    public static final int TIER = 5;
    public static final int TANKS = 16;
    /** Ticks between two refills */
    private static final int RESTOCK_INTERVAL = 20;

    private final StationLink link = new StationLink();
    /** What each tank keeps in stock, single litres that only mark the fluid */
    private final FluidStack[] stock = new FluidStack[TANKS];

    public MTEHatchStationOutputHatch(int id, String name, String nameRegional) {
        super(id, TANKS, name, nameRegional, TIER);
    }

    public MTEHatchStationOutputHatch(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, TANKS, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchStationOutputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return StatCollector.translateToLocal("galaxia.station_hatch.output_hatch.desc")
            .split("\\\\n");
    }

    public FluidStack getStock(int tank) {
        return tank >= 0 && tank < TANKS ? stock[tank] : null;
    }

    /** Sets what a tank keeps in stock, only the kind of fluid counts */
    public void setStock(int tank, FluidStack fluid) {
        if (tank < 0 || tank >= TANKS) return;
        stock[tank] = GTUtility.copyAmount(1, fluid);
        markDirty();
    }

    /** True while the hatch stands inside a station */
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

    /** Sends fluids that are not wanted back to the station and fills every stocked tank up */
    private void restock(IDistributedInventory storage) {
        boolean changed = false;
        for (int tank = 0; tank < TANKS; tank++) {
            FluidStack wanted = stock[tank];
            FluidStack held = getFluid(tank);
            if (held != null && !held.isFluidEqual(wanted)) {
                held.amount -= (int) storage.updateFluids(FluidKey.of(held), held.amount);
                if (held.amount <= 0) setFluid(null, tank);
                changed = true;
                // A full station leaves the fluid where it is
                if (getFluid(tank) != null) continue;
                held = null;
            }
            if (wanted == null) continue;
            int have = held == null ? 0 : held.amount;
            if (have >= getCapacity()) continue;
            long got = storage.updateFluids(FluidKey.of(wanted), -(getCapacity() - have));
            if (got <= 0) continue;
            if (held == null) setFluid(new FluidStack(wanted, (int) got), tank);
            else held.amount += (int) got;
            changed = true;
        }
        if (changed) markDirty();
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        NBTTagCompound stockTag = new NBTTagCompound();
        for (int tank = 0; tank < TANKS; tank++) {
            if (stock[tank] != null) stockTag.setTag("" + tank, stock[tank].writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("stationStock", stockTag);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        NBTTagCompound stockTag = tag.getCompoundTag("stationStock");
        for (int tank = 0; tank < TANKS; tank++) {
            stock[tank] = stockTag.hasKey("" + tank)
                ? FluidStack.loadFluidStackFromNBT(stockTag.getCompoundTag("" + tank))
                : null;
        }
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new StationOutputHatchGui(this).build(guiData, syncManager, uiSettings);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return StationHatchTextures.output(base, FLUID_OUT_SIGN);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return StationHatchTextures.output(base, FLUID_OUT_SIGN);
    }
}
