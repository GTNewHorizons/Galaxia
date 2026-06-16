package com.gtnewhorizons.galaxia.compat.gt;

import com.gtnewhorizons.galaxia.compat.gt.handlers.MTELapotronicSuperCapacitorHandler;
import com.gtnewhorizons.galaxia.compat.gt.handlers.MTETankTFFTHandler;
import com.gtnewhorizons.galaxia.compat.gt.handlers.MTEYottaFluidTankHandler;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;

import goodgenerator.blocks.tileEntity.MTEYottaFluidTank;
import gregtech.api.enums.GTValues;
import kekztech.common.tileentities.MTELapotronicSuperCapacitor;
import kekztech.common.tileentities.MTETankTFFT;

public final class GalaxiaGTAttachmentRegistration {

    private GalaxiaGTAttachmentRegistration() {}

    public static MTEHatchStationMaintenance mteHatchStationMaintenance;
    public static MTEHatchStationMaintenance mteHatchAutoStationMaintenance;

    public static void init() {
        registerHatches();
        StationAttachmentRegistry.register(MTELapotronicSuperCapacitor.class, new MTELapotronicSuperCapacitorHandler());
        StationAttachmentRegistry.register(MTETankTFFT.class, new MTETankTFFTHandler());
        StationAttachmentRegistry.register(MTEYottaFluidTank.class, new MTEYottaFluidTankHandler());

        int[] amps = new int[] { 4, 16, 64 };
        for (int tier = 0; tier < GTValues.V.length; tier++) {
            int id = MTEStationPlug.ID + tier;
            new MTEStationPlug(id, "station_plug_" + tier, "Station Plug " + tier, tier);
            for (int i = 0; i < amps.length; i++) {
                id = MTEStationPlugMulti.ID + tier * 10 + i;
                new MTEStationPlugMulti(
                    id,
                    "station_plug_multi_" + amps[i] + "a_t" + tier,
                    "Station Plug " + amps[i] + "A (Tier " + tier + ")",
                    tier,
                    amps[i]);
            }
        }
    }

    private static void registerHatches() {
        mteHatchStationMaintenance = new MTEHatchStationMaintenance(
            MTEHatchStationMaintenance.ID,
            "hatch.station_controller",
            "Station Controller Hatch",
            1,
            false);
        mteHatchAutoStationMaintenance = new MTEHatchStationMaintenance(
            MTEHatchStationMaintenance.ID + 1,
            "hatch.station_controller",
            "Station Controller Auto Hatch",
            1,
            true);
    }
}
