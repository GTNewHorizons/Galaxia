package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePanelAction;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

/**
 * Left column of the station window: the station at a glance, the selected tile and what can be done with it, and
 * below the details of the selected module
 */
final class StationSidePanel {

    private static final int STATUS_LINES = 10;
    private static final int DETAIL_LINES = 16;
    private static final int BAR_HEIGHT = 8;

    private final @Nullable CelestialAsset.ID assetId;
    private final @Nullable StationMapWidget map;
    private final @Nullable StationTilePickerController tilePickerController;
    private final @Nullable ModuleConfigModalController configController;
    private final int width;
    private @Nullable StationTileCoord armedDestroySelection;
    private @Nullable HammerDispatchStatus.Status hammerDispatchStatus;
    private boolean destroyMultipleMode;

    StationSidePanel(@Nullable CelestialAsset.ID assetId, @Nullable StationMapWidget map,
        @Nullable StationTilePickerController tilePickerController,
        @Nullable ModuleConfigModalController configController, int width) {
        this.assetId = assetId;
        this.map = map;
        this.tilePickerController = tilePickerController;
        this.configController = configController;
        this.width = width;
    }

    /** The station and the selected tile with its actions */
    /**
     * @param inventoryButton The button opening the station inventory
     * @param copyModule      Starts placing copies of a module, or null where modules can't be copied
     */
    Flow overview(IWidget inventoryButton, @Nullable Consumer<ModuleInstance.ID> copyModule) {
        int textWidth = width - GalaxiaUi.PAD * 2;
        GalaxiaUi.Column column = GalaxiaUi.columnOf(panel());
        column.add(
            GalaxiaUi.dim(tr("galaxia.gui.station.panel.none"))
                .setEnabledIf(w -> facility() == null));
        GalaxiaUi.Column station = GalaxiaUi.columnOf(GalaxiaUi.column());
        station
            .text(
                () -> map == null ? ""
                    : StatCollector.translateToLocalFormatted(
                        "galaxia.gui.station.panel.layer",
                        map.visionLayer()
                            .getDisplayName()))
            .text(
                () -> facility() == null ? ""
                    : StatCollector.translateToLocalFormatted(
                        "galaxia.gui.station.panel.modules",
                        facility().modules()
                            .size()))
            .text(
                () -> facility() == null ? ""
                    : StatCollector.translateToLocalFormatted("galaxia.gui.station.panel.tiles", tileCount()))
            .text(
                () -> facility() == null ? ""
                    : StatCollector.translateToLocalFormatted(
                        "galaxia.gui.station.panel.energy",
                        StationUiData.formatEu(facility().getEnergyStored()),
                        StationUiData.formatEu(facility().energyCapacity())));
        station.flow()
            .setEnabledIf(w -> facility() != null);
        column.add(station.flow());
        column.add(inventoryButton);
        column.rule();
        column.add(
            GalaxiaUi.dim(tr("galaxia.gui.station.panel.no_tile"))
                .setEnabledIf(w -> facility() != null && selection() == null));
        column.add(
            GalaxiaUi.lines(this::statusLines, STATUS_LINES, textWidth)
                .setEnabledIf(w -> selection() != null));
        column.add(
            GalaxiaGuiStyle.bar(this::hammerCharge, () -> GalaxiaGuiStyle.GOOD)
                .size(textWidth, BAR_HEIGHT)
                .setEnabledIf(w -> selectedHammer() != null));

        int half = (textWidth - GalaxiaUi.GAP) / 2;
        Flow actions = GalaxiaUi.row();
        for (ModulePanelAction action : ModulePanelAction.values()) {
            actions.child(
                GalaxiaUi.button(action.label(), () -> true, () -> runModuleAction(action))
                    .name(action.name())
                    .width(half)
                    .setEnabledIf(w -> isAvailable(action)));
        }
        actions.setEnabledIf(w -> {
            for (ModulePanelAction action : ModulePanelAction.values()) if (isAvailable(action)) return true;
            return false;
        });
        column.add(actions);
        // Copying and destroying work on the selected module; Multiple joins them once Destroy is armed
        Flow destroy = GalaxiaUi.row()
            .child(
                GalaxiaUi.button(
                    tr("galaxia.gui.station.copy_module"),
                    () -> copyModule != null && canDestroySelected(),
                    () -> {
                        ModuleInstance module = selectedModule();
                        if (copyModule != null && module != null) copyModule.accept(module.id);
                    })
                    .width(half)
                    .setEnabledIf(w -> copyModule != null))
            .child(
                GalaxiaUi.button(this::destroyLabel, half, this::canDestroySelected, this::destroyPressed)
                    .name("station.destroy"));
        destroy.setEnabledIf(w -> canDestroySelected());
        column.add(destroy);
        column.add(
            GalaxiaUi
                .toggle(
                    () -> tr("galaxia.gui.station.panel.multiple"),
                    half,
                    () -> destroyMultipleMode,
                    () -> destroyMultipleMode = !destroyMultipleMode)
                .tooltip(t -> t.addLine(tr("galaxia.gui.station.panel.multiple.tooltip")))
                .setEnabledIf(w -> armedDestroySelection != null)
                .marginLeft(half + GalaxiaUi.GAP));

        Flow flow = column.flow();
        flow.onUpdateListener(w -> update(), true);
        flow.setEnabledIf(w -> !isPickerActive());
        return flow;
    }

    /** What the selected module does and holds; hidden for the core and empty tiles */
    Flow details() {
        Flow panel = panel();
        panel.child(GalaxiaUi.lines(this::detailLines, DETAIL_LINES, width - GalaxiaUi.PAD * 2));
        panel.setEnabledIf(w -> !isPickerActive() && !detailLines().isEmpty());
        return panel;
    }

    private List<GalaxiaUi.Line> detailLines() {
        AutomatedFacility facility = facility();
        ModuleInstance module = selectedModule();
        if (facility == null || module == null || facility.stationLayout() == null) return List.of();
        return ModuleDetailTextRegistry
            .collect(new ModuleDetailTextRegistry.Context(facility, facility.stationLayout(), module, assetId))
            .lines();
    }

    private Flow panel() {
        return Flow.column()
            .collapseDisabledChild()
            .crossAxisAlignment(com.cleanroommc.modularui.utils.Alignment.CrossAxis.START)
            .childPadding(GalaxiaUi.GAP)
            .width(width)
            .coverChildrenHeight()
            .padding(GalaxiaUi.PAD)
            .background(EnumTextures.GUI_PANEL.getImage());
    }

    private void update() {
        StationTileCoord selected = selection();
        if (armedDestroySelection != null && !armedDestroySelection.equals(selected)) {
            armedDestroySelection = null;
            destroyMultipleMode = false;
        }
        ModuleInstance module = selectedModule();
        hammerDispatchStatus = module != null && module.component() instanceof ModuleHammer
            ? CelestialClient.hammerDispatchStatus(assetId, module.id)
            : null;
    }

    private List<GalaxiaUi.Line> statusLines() {
        AutomatedFacility facility = facility();
        StationTileCoord selected = selection();
        if (facility == null || selected == null || facility.stationLayout() == null) return List.of();
        PlacedTile tile = facility.stationLayout()
            .get(selected);
        ModuleInstance module = tile == null ? null : tile.module();
        return ModuleStatusTextRegistry
            .collect(new ModuleStatusTextRegistry.Context(facility, selected, tile, module, hammerDispatchStatus))
            .lines();
    }

    private double hammerCharge() {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return 0;
        return hammer.energyStored() / (double) Math.max(1L, hammer.energyCapacity());
    }

    private int tileCount() {
        StationLayout layout = facility() == null ? null : facility().stationLayout();
        return layout == null ? 0 : layout.size();
    }

    // region Destroy

    private String destroyLabel() {
        return tr(
            armedDestroySelection != null && armedDestroySelection.equals(selection())
                ? "galaxia.gui.station.panel.confirm"
                : "galaxia.gui.station.panel.destroy");
    }

    /** First press arms, the second destroys the module or, with Multiple on, starts choosing more of them */
    private void destroyPressed() {
        if (!canDestroySelected()) return;
        StationTileCoord selected = selection();
        if (!selected.equals(armedDestroySelection)) {
            armedDestroySelection = selected;
            destroyMultipleMode = false;
            return;
        }
        if (!(destroyMultipleMode && startDestroyPicker())) destroySelected();
        armedDestroySelection = null;
        destroyMultipleMode = false;
    }

    private boolean canDestroySelected() {
        return !isPickerActive() && selectedModule() != null;
    }

    private void destroySelected() {
        ModuleInstance module = selectedModule();
        if (assetId == null || module == null) return;
        CelestialClient.requestModuleDeconstruction(assetId, module.id);
    }

    private boolean startDestroyPicker() {
        if (assetId == null || tilePickerController == null) return false;
        AutomatedFacility facility = facility();
        if (facility == null || facility.stationLayout() == null) return false;
        tilePickerController.start(
            tr("galaxia.gui.station.panel.destroy_title"),
            tr("galaxia.gui.station.panel.destroy"),
            coord -> moduleAt(facility, coord) != null,
            coord -> canonicalDestroyTarget(facility, coord),
            this::destroyTargets);
        tilePickerController.setVisualStyle(StationTilePickerController.VisualStyle.DECONSTRUCT);
        return true;
    }

    private void destroyTargets(List<StationTileCoord> targets) {
        if (assetId == null || targets == null || targets.isEmpty()) return;
        AutomatedFacility facility = facility();
        if (facility == null) return;
        Set<ModuleInstance.ID> moduleIds = new HashSet<>();
        for (StationTileCoord target : targets) {
            ModuleInstance module = moduleAt(facility, target);
            if (module != null) moduleIds.add(module.id);
        }
        List<ModuleInstance> modules = facility.modules();
        for (int i = modules.size() - 1; i >= 0; i--) {
            ModuleInstance module = modules.get(i);
            if (moduleIds.contains(module.id)) CelestialClient.requestModuleDeconstruction(assetId, module.id);
        }
    }

    private static @Nullable ModuleInstance moduleAt(AutomatedFacility facility, StationTileCoord coord) {
        if (facility == null || coord == null) return null;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return null;
        PlacedTile tile = layout.get(coord);
        if (tile == null || tile.isCore() || tile.module() == null) return null;
        return facility.moduleById(tile.module().id);
    }

    private static @Nullable StationTileCoord canonicalDestroyTarget(AutomatedFacility facility,
        StationTileCoord coord) {
        if (facility == null || coord == null) return null;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return null;
        PlacedTile tile = layout.get(coord);
        if (tile == null || tile.isCore() || tile.module() == null) return null;
        ModuleInstance.ID moduleId = tile.module().id;
        StationTileCoord canonical = null;
        for (StationTileCoord candidate : layout.snapshot()
            .keySet()) {
            PlacedTile candidateTile = layout.get(candidate);
            if (candidateTile == null || candidateTile.module() == null || !candidateTile.module().id.equals(moduleId))
                continue;
            if (canonical == null || candidate.dx() < canonical.dx()
                || candidate.dx() == canonical.dx() && candidate.dy() < canonical.dy()) {
                canonical = candidate;
            }
        }
        return canonical;
    }

    // endregion

    // region Module actions

    private boolean isAvailable(ModulePanelAction action) {
        if (isPickerActive() || configController == null) return false;
        if (isCoreSelected()) return action == ModulePanelAction.CONFIG;
        ModuleInstance module = selectedModule();
        if (module == null) return false;
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(module.kind());
        return definition != null && definition.panelActions()
            .contains(action);
    }

    private void runModuleAction(ModulePanelAction action) {
        if (configController == null || !isAvailable(action)) return;
        if (isCoreSelected()) {
            if (action == ModulePanelAction.CONFIG) configController.openCoreLogistics();
            return;
        }
        ModuleInstance module = selectedModule();
        if (module == null) return;
        switch (action) {
            case CONFIG -> openModuleConfig(module);
            case UPGRADE -> {
                if (ModuleUpgradeUiModel.supports(module)) configController.openUpgrade(module.id);
            }
        }
    }

    private void openModuleConfig(ModuleInstance module) {
        if (module.component() instanceof ModuleMiner) {
            configController.openMinerBlacklist(module.id);
        } else if (module.component() instanceof ModuleHammer) {
            configController.openHammer(module.id);
        } else if (module.recipe() != null) {
            configController.openRecipeConfig(module.id);
        } else if (module.component() instanceof ModuleDebugDataGenerator) {
            configController.openDebugDataGenerator(module.id);
        }
    }

    // endregion

    private @Nullable AutomatedFacility facility() {
        return StationUiData.facility(assetId);
    }

    private @Nullable StationTileCoord selection() {
        return map == null ? null : map.selection();
    }

    private @Nullable ModuleInstance selectedModule() {
        AutomatedFacility facility = facility();
        StationTileCoord selected = selection();
        if (facility == null || selected == null || facility.stationLayout() == null) return null;
        ModuleInstance module = facility.stationLayout()
            .moduleAt(selected);
        return module == null ? null : facility.moduleById(module.id);
    }

    private @Nullable ModuleHammer selectedHammer() {
        ModuleInstance module = selectedModule();
        return module != null && module.component() instanceof ModuleHammer hammer ? hammer : null;
    }

    private boolean isCoreSelected() {
        AutomatedFacility facility = facility();
        StationTileCoord selected = selection();
        if (facility == null || selected == null || facility.stationLayout() == null) return false;
        PlacedTile tile = facility.stationLayout()
            .get(selected);
        return tile != null && tile.isCore();
    }

    private boolean isPickerActive() {
        return tilePickerController != null && tilePickerController.isActive();
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }
}
