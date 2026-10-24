package com.gtnewhorizons.galaxia.latemixin.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.gtnewhorizons.galaxia.compat.gt.station.StationEnergyExchange;
import com.gtnewhorizons.galaxia.compat.gt.station.StationLink;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;

/**
 * Energy hatches inside a station draw from its energy stores
 */
@Mixin(value = MTEHatchEnergy.class, remap = false)
public abstract class MTEHatchEnergyStationMixin extends MTEHatch {

    @Unique
    private final StationLink galaxia$station = new StationLink();

    private MTEHatchEnergyStationMixin() {
        super(null, 0, 0, (String[]) null, null);
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        super.onPostTick(base, tick);
        StationEnergyExchange.feed((MTEHatchEnergy) (Object) this, galaxia$station);
    }
}
