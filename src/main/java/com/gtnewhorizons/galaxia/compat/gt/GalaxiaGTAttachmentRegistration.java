package com.gtnewhorizons.galaxia.compat.gt;

import com.gtnewhorizons.galaxia.compat.gt.crater.MTECraterExtractor;
import com.gtnewhorizons.galaxia.compat.gt.elevator.MTEGalaxiaSpaceElevator;
import com.gtnewhorizons.galaxia.compat.gt.handlers.GTHatchEnergySourceHandler;
import com.gtnewhorizons.galaxia.compat.gt.handlers.MTELapotronicSuperCapacitorHandler;
import com.gtnewhorizons.galaxia.compat.gt.handlers.MTETankTFFTHandler;
import com.gtnewhorizons.galaxia.compat.gt.handlers.MTEYottaFluidTankHandler;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationInputBus;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationInputHatch;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationOutputBus;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationOutputHatch;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;

import goodgenerator.blocks.tileEntity.MTEYottaFluidTank;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
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
        // Energy hatches in a wall and dynamo hatches inside hand their energy to the station
        GTHatchEnergySourceHandler hatchSource = new GTHatchEnergySourceHandler();
        StationAttachmentRegistry.register(MTEHatchEnergy.class, hatchSource);
        StationAttachmentRegistry.register(MTEHatchDynamo.class, hatchSource);

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
        new MTECraterExtractor();
        mteHatchStationMaintenance = new MTEHatchStationMaintenance(
            MTEHatchStationMaintenance.ID,
            "hatch.station_controller",
            "Station Controller Hatch",
            1,
            false);
        new MTEGalaxiaSpaceElevator(
            MTEGalaxiaSpaceElevator.ID,
            "multimachine.galaxia.space_elevator",
            "Galaxia Space Elevator");
        mteHatchAutoStationMaintenance = new MTEHatchStationMaintenance(
            MTEHatchStationMaintenance.ID + 1,
            "hatch.station_controller.auto",
            "Station Controller Auto Hatch",
            1,
            true);

        // Hatches that reach the station's shared storage without pipes
        new MTEHatchStationInputBus(MTEHatchStationInputBus.ID, "hatch.station.input_bus", "Station Input Bus");
        new MTEHatchStationOutputBus(MTEHatchStationOutputBus.ID, "hatch.station.output_bus", "Station Output Bus");
        new MTEHatchStationInputHatch(MTEHatchStationInputHatch.ID, "hatch.station.input_hatch", "Station Input Hatch");
        new MTEHatchStationOutputHatch(
            MTEHatchStationOutputHatch.ID,
            "hatch.station.output_hatch",
            "Station Output Hatch");
    }
}
