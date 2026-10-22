package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

/** Ores of the body the miner works on: which one it focuses on and which ones it voids after mining */
final class MinerConfigWindow implements ModuleConfigWindow {

    private static final int NAME_WIDTH = 150;
    private static final int FOCUS_WIDTH = 40;
    private static final int LIST_HEIGHT = 6 * (GalaxiaUi.ICON + GalaxiaUi.GAP);

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final StationTilePickerController tilePickerController;
    private final SettingsGroupSelector settingsGroupSelector;

    MinerConfigWindow(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        StationTilePickerController tilePickerController) {
        this.assetId = assetId;
        this.controller = controller;
        this.tilePickerController = tilePickerController;
        this.settingsGroupSelector = new SettingsGroupSelector(assetId, controller, FacilityModuleKind.MINER);
    }

    @Override
    public String title() {
        ModuleInstance module = selectedModule();
        return module == null ? StatCollector.translateToLocal("galaxia.gui.station.miner.title")
            : StationUiData.moduleTitle(module, "galaxia.gui.station.config.purpose.configuration");
    }

    @Override
    public void update() {
        settingsGroupSelector.update();
    }

    @Override
    public void build(GalaxiaUi.Column column) {
        column.add(
            GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.station.miner.none"))
                .setEnabledIf(w -> selectedMiner() == null));
        column.add(settingsGroupSelector.row());

        AutomatedFacility facility = StationUiData.facility(assetId);
        List<MinerBlacklistOptions.Entry> options = facility == null ? List.of()
            : MinerBlacklistOptions.forFacility(facility);
        if (options.isEmpty()) {
            column.dim(StatCollector.translateToLocal("galaxia.gui.station.miner.no_ores"));
        } else {
            column.add(
                GalaxiaUi.row()
                    .child(
                        new Widget<>().size(GalaxiaUi.ICON + GalaxiaUi.GAP + NAME_WIDTH, 1)
                            .marginTop(GalaxiaUi.GAP))
                    .child(
                        GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.station.miner.focus"))
                            .width(FOCUS_WIDTH)
                            .setEnabledIf(w -> MinerFocusUiModel.canShowOreFocus(selectedModule())))
                    .child(GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.station.miner.blacklist"))));
            ListWidget<IWidget, ?> list = GalaxiaUi.scroll(LIST_HEIGHT);
            Flow rows = GalaxiaUi.column();
            for (MinerBlacklistOptions.Entry option : options) rows.child(oreRow(option));
            list.child(rows);
            column.add(list);
        }
        column.add(
            GalaxiaUi.button(
                StatCollector.translateToLocal("galaxia.gui.station.copy_settings"),
                this::canCopySettings,
                this::startCopySettingsPicker));
    }

    private Flow oreRow(MinerBlacklistOptions.Entry option) {
        return GalaxiaUi.row()
            .child(GalaxiaUi.item(option.displayStack()))
            .child(
                GalaxiaUi
                    .text(
                        () -> GalaxiaUi.clip(option.displayName(), NAME_WIDTH),
                        () -> MinerFocusUiModel.isFocusedOre(selectedModule(), option.key()) ? GalaxiaGuiStyle.WARN
                            : GalaxiaGuiStyle.TEXT)
                    .width(NAME_WIDTH))
            .child(
                GalaxiaUi.button(
                    () -> StatCollector.translateToLocal(
                        MinerFocusUiModel.isFocusedOre(selectedModule(), option.key())
                            ? "galaxia.gui.station.miner.focus.clear"
                            : "galaxia.gui.station.miner.focus.set"),
                    FOCUS_WIDTH,
                    () -> canUseControls() && MinerFocusUiModel.canSetOre(selectedModule(), option.key()),
                    () -> toggleFocusOre(option)))
            .child(
                GalaxiaUi
                    .toggle(
                        () -> isBlacklisted(option) ? "X" : "",
                        GalaxiaUi.BUTTON_H,
                        () -> isBlacklisted(option),
                        () -> { if (canUseControls()) setBlacklisted(option.key(), !isBlacklisted(option)); })
                    .tooltip(
                        t -> t.addLine(StatCollector.translateToLocal("galaxia.gui.station.miner.blacklist.tooltip"))));
    }

    private boolean isBlacklisted(MinerBlacklistOptions.Entry option) {
        ModuleInstance module = selectedModule();
        AutomatedFacility facility = StationUiData.facility(assetId);
        return module != null && facility != null && facility.isMinerOreBlacklisted(module, option.key());
    }

    private void setBlacklisted(String oreKey, boolean blacklisted) {
        if (selectedMiner() == null) return;
        settingsGroupSelector.closeMenu();
        CelestialClient.setMinerOreBlacklisted(assetId, controller.moduleId(), oreKey, blacklisted);
    }

    private void toggleFocusOre(MinerBlacklistOptions.Entry option) {
        ModuleInstance module = selectedModule();
        if (selectedMiner() == null) return;
        settingsGroupSelector.closeMenu();
        String targetOreKey = MinerFocusUiModel.oreTargetForClick(module, option.key());
        CelestialClient.setMinerFocusOre(assetId, controller.moduleId(), targetOreKey);
    }

    private boolean canUseControls() {
        return selectedMiner() != null && !settingsGroupSelector.isBlockingModuleControls();
    }

    private boolean canCopySettings() {
        AutomatedFacility facility = StationUiData.facility(assetId);
        return tilePickerController != null && canUseControls() && facility != null && facility.stationLayout() != null;
    }

    private void startCopySettingsPicker() {
        AutomatedFacility facility = StationUiData.facility(assetId);
        ModuleInstance source = selectedModule();
        if (facility == null || source == null || tilePickerController == null) return;
        controller.close();
        tilePickerController.start(
            StatCollector.translateToLocal("galaxia.gui.station.miner.copy_title"),
            StatCollector.translateToLocal("galaxia.gui.station.copy"),
            coord -> {
                ModuleInstance target = facility.stationLayout()
                    .moduleAt(coord);
                return target != null && facility.canCopyModuleRuntimeSettings(source, target);
            },
            coord -> StationTilePickerController.normalizeModuleTarget(facility, coord),
            targets -> CelestialClient
                .copyModuleSettings(assetId, source.id, StationTilePickerController.moduleIds(facility, targets)));
    }

    private ModuleMiner selectedMiner() {
        ModuleInstance module = selectedModule();
        return module != null && module.component() instanceof ModuleMiner miner ? miner : null;
    }

    private ModuleInstance selectedModule() {
        return StationUiData.module(assetId, controller.moduleId());
    }

}
