package com.gtnewhorizons.galaxia.preview;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.station.ModulePickerScreen;
import com.gtnewhorizons.galaxia.client.gui.station.StationManagementScreen;
import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeInputScreen;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import dev.modularui.preview.PreviewEntrypoint;
import gregtech.api.recipe.RecipeMaps;

final class FacilityPreviews {

    private FacilityPreviews() {}

    static PreviewEntrypoint stationManagement() {
        return PreviewEntrypoint.of(StationManagementScreen.class, context -> {
            FacilityState state = createFacility();
            PreviewSupport.setStaticField(StationManagementScreen.class, "pendingAssetId", state.facility().assetId);
            PreviewSupport.setStaticField(StationManagementScreen.class, "pendingCreativeBuildMode", false);
            return new StationManagementScreen()
                .buildUI(PreviewSupport.guiData(), PreviewSupport.sync(context), PreviewSupport.settings());
        });
    }

    static PreviewEntrypoint modulePicker() {
        return PreviewEntrypoint.of(ModulePickerScreen.class, context -> {
            FacilityState state = createFacility();
            PreviewSupport.setStaticField(ModulePickerScreen.class, "pendingAssetId", state.facility().assetId);
            PreviewSupport.setStaticField(ModulePickerScreen.class, "pendingCoord", StationTileCoord.of(3, 0));
            PreviewSupport.setStaticField(ModulePickerScreen.class, "pendingInstantBuild", false);
            PreviewSupport.setStaticField(ModulePickerScreen.class, "pendingMultipleBuild", false);
            PreviewSupport.setStaticField(ModulePickerScreen.class, "pendingSelectedKind", null);
            PreviewSupport.setStaticField(ModulePickerScreen.class, "pendingSettingsGroupId", (short) 0);
            return new ModulePickerScreen()
                .buildUI(PreviewSupport.guiData(), PreviewSupport.sync(context), PreviewSupport.settings());
        });
    }

    static PreviewEntrypoint recipeInput() {
        return PreviewEntrypoint.of(GTRecipeInputScreen.class, context -> {
            FacilityState state = createFacility();
            PreviewSupport.setStaticField(GTRecipeInputScreen.class, "pendingAssetId", state.facility().assetId);
            PreviewSupport.setStaticField(GTRecipeInputScreen.class, "pendingModuleIndex", 0);
            PreviewSupport.setStaticField(GTRecipeInputScreen.class, "pendingModule", state.module());
            GTRecipeInputScreen screen = new GTRecipeInputScreen();
            return screen.buildUI(PreviewSupport.guiData(), PreviewSupport.sync(context), PreviewSupport.settings());
        });
    }

    private static FacilityState createFacility() {
        PreviewSupport.initializeStarmap();
        PreviewSupport.initializeFacilityModules();
        initializeRecipeMaps();
        CelestialClient.clear();
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = FacilityModuleKind.MACERATOR
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        facility.stationLayout().place(module);
        facility.addModule(module);
        CelestialClient.add(facility);
        return new FacilityState(facility, module);
    }

    private static void initializeRecipeMaps() {
        RecipeMaps.maceratorRecipes.getAllRecipes();
    }

    private record FacilityState(AutomatedFacility facility, ModuleInstance module) {}
}
