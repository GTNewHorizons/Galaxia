package com.gtnewhorizons.galaxia.preview;

import com.gtnewhorizons.galaxia.client.gui.TeamPermissionScreen;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticChartGui;
import com.gtnewhorizons.galaxia.client.gui.station.ModulePickerScreen;
import com.gtnewhorizons.galaxia.client.gui.station.StationManagementScreen;
import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeInputScreen;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.gui.OxygenCollectorGUI;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.gui.OxygenFillerGUI;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.gui.OxygenPylonGUI;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerTarget;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketEditorUI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import dev.modularui.preview.PreviewCatalog;
import dev.modularui.preview.PreviewEntrypoint;
import dev.modularui.preview.PreviewScenario;
import java.util.List;
import java.util.function.Supplier;

public final class GalaxiaPreviewCatalog implements PreviewCatalog {

    @Override
    public List<PreviewScenario> scenarios() {
        return List.of(
            scenario("starmap/default", "galactic starmap", "starmap", GalacticChartGui.class, StarmapPreviews::overview)
                .tags("default", "interaction")
                .actions("actions/starmap.txt")
                .expectAssets("galaxia:textures/gui/bodyicons/icon_mars.png")
                .timeout(PreviewScenario.TimeoutCategory.EXTENDED),
            scenario("oxygen/collector", "oxygen collector", "oxygen", OxygenCollectorGUI.class,
                OxygenPreviews::collector).tags("default"),
            scenario("oxygen/filler", "oxygen filler", "oxygen", OxygenFillerGUI.class, OxygenPreviews::filler)
                .tags("default")
                .expectAssets("galaxia:textures/gui/oxygen_bar_fill.png"),
            scenario("oxygen/pylon", "oxygen pylon", "oxygen", OxygenPylonGUI.class, OxygenPreviews::pylon)
                .tags("default"),
            scenario("permissions-and-pickers/team-permissions", "team permissions", "permissions-and-pickers",
                TeamPermissionScreen.class, UtilityPreviews::teamPermissions).tags("default", "interaction")
                    .actions("actions/team-permissions.txt"),
            scenario("permissions-and-pickers/item-picker", "item picker", "permissions-and-pickers",
                ItemPickerScreen.class, UtilityPreviews::itemPicker).tags("default", "interaction")
                    .actions("actions/item-picker.txt"),
            scenario("station/controller", "station controller", "station", TileStation.class,
                StationMachinePreviews::controller).tags("default", "interaction")
                    .actions("actions/station-controller.txt"),
            scenario("station/management", "station management", "station", StationManagementScreen.class,
                FacilityPreviews::stationManagement).tags("default", "interaction")
                    .actions("actions/station-management.txt"),
            scenario("station/module-picker", "module picker", "station", ModulePickerScreen.class,
                FacilityPreviews::modulePicker).tags("default", "interaction")
                    .actions("actions/module-picker.txt"),
            scenario("station/hammer-target", "hammer target", "station", TileHammerTarget.class,
                StationMachinePreviews::hammerTarget).tags("default"),
            scenario("station/hammer-cannon", "hammer cannon", "station", TileHammerCannon.class,
                StationMachinePreviews::hammerCannon).tags("default"),
            scenario("recipes/input", "GT recipe input", "recipes", GTRecipeInputScreen.class,
                FacilityPreviews::recipeInput).tags("default", "interaction", "item-rendering")
                    .actions("actions/recipe-input.txt"),
            scenario("rockets/editor-empty", "empty rocket editor", "rockets", RocketEditorUI.class,
                RocketPreviews::emptyRocketEditor).tags("default", "state"),
            scenario("rockets/editor-invalid", "invalid rocket editor", "rockets", RocketEditorUI.class,
                RocketPreviews::invalidRocketEditor).tags("state"),
            scenario("rockets/module-assembler", "module assembler", "rockets", TileEntityModuleAssembler.class,
                RocketPreviews::moduleAssembler).tags("default", "interaction", "item-rendering")
                    .actions("actions/module-assembler.txt"));
    }

    private static PreviewScenario scenario(String id, String description, String family, Class<?> previewedClass,
        Supplier<PreviewEntrypoint> fixture) {
        return PreviewScenario.define(id, description, family, previewedClass, fixture);
    }
}
