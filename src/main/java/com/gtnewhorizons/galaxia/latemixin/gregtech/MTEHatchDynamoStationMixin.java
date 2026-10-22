package com.gtnewhorizons.galaxia.latemixin.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.gtnewhorizons.galaxia.compat.gt.station.StationEnergyExchange;
import com.gtnewhorizons.galaxia.compat.gt.station.StationLink;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;

/**
 * Dynamo hatches inside a station charge its energy stores
 */
@Mixin(value = MTEHatchDynamo.class, remap = false)
public abstract class MTEHatchDynamoStationMixin extends MTEHatch {

    @Unique
    private final StationLink galaxia$station = new StationLink();

    private MTEHatchDynamoStationMixin() {
        super(null, 0, 0, (String[]) null, null);
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        super.onPostTick(base, tick);
        StationEnergyExchange.collect((MTEHatchDynamo) (Object) this, galaxia$station);
    }
}
