package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Objects;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.gui.station.ModuleUpgradeUiModel.Selection;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaWindow;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

final class ModuleConfigModalController {

    static final String WINDOW_ID = "station.config";

    enum Kind {
        NONE,
        HAMMER,
        MODULE_UPGRADE,
        LOGISTICS,
        MINER_BLACKLIST,
        RECIPE_CONFIG,
        DEBUG_DATA_GENERATOR
    }

    private final ParentWidget<?> host;
    private final CelestialAsset.ID assetId;
    private final int x;
    private final int y;
    private final StationTilePickerController tilePickerController;

    private GalaxiaWindow window;
    private boolean closingWindow;
    private Kind kind = Kind.NONE;
    private ModuleInstance.ID moduleId;
    private boolean moduleOperationCancelArmed;
    private boolean hammerUpgradeReserveItems;
    private boolean hammerUpgradeVoidRefund;
    private boolean retargetQueued;
    private ModuleInstance.ID queuedRetargetModuleId;
    private LogisticsConfigAccessMode logisticsAccessMode = LogisticsConfigAccessMode.FULL;
    private Selection moduleUpgradeSelection = Selection.hammer(HammerVariant.BASE, ModuleTier.EV);

    ModuleConfigModalController(ParentWidget<?> host, CelestialAsset.ID assetId, int x, int y) {
        this(host, assetId, x, y, null);
    }

    ModuleConfigModalController(ParentWidget<?> host, CelestialAsset.ID assetId, int x, int y,
        StationTilePickerController tilePickerController) {
        this.host = host;
        this.assetId = assetId;
        this.x = x;
        this.y = y;
        this.tilePickerController = tilePickerController;
    }

    void openHammer(ModuleInstance.ID moduleId) {
        if (StationUiData.module(assetId, moduleId) == null) return;
        if (!prepareOpen(Kind.HAMMER, moduleId)) return;

        HammerConfigWindow widget = new HammerConfigWindow(assetId, this);
        mount(widget);
    }

    void openUpgrade(ModuleInstance.ID moduleId) {
        ModuleInstance module = StationUiData.module(assetId, moduleId);
        if (module == null || !ModuleUpgradeUiModel.supports(module)) return;
        if (!prepareOpen(Kind.MODULE_UPGRADE, module.id)) return;
        this.moduleUpgradeSelection = ModuleUpgradeUiModel.defaultSelection(module);

        ModuleUpgradeWindow widget = new ModuleUpgradeWindow(assetId, this, tilePickerController);
        mount(widget);
    }

    void openLogistics(ModuleInstance.ID moduleId) {
        if (StationUiData.module(assetId, moduleId) == null) return;
        if (!prepareOpen(Kind.LOGISTICS, moduleId)) return;

        LogisticsConfigWindow widget = new LogisticsConfigWindow(assetId, this);
        mount(widget);
    }

    void openStationLogistics() {
        openStationLogistics(LogisticsConfigAccessMode.FULL);
    }

    void openCoreLogistics() {
        openStationLogistics(LogisticsConfigAccessMode.IMPORT_ONLY);
    }

    private void openStationLogistics(LogisticsConfigAccessMode accessMode) {
        if (!prepareOpen(Kind.LOGISTICS, null)) return;
        this.logisticsAccessMode = accessMode == null ? LogisticsConfigAccessMode.FULL : accessMode;

        LogisticsConfigWindow widget = new LogisticsConfigWindow(assetId, this);
        mount(widget);
    }

    void openMinerBlacklist(ModuleInstance.ID moduleId) {
        if (StationUiData.module(assetId, moduleId) == null) return;
        if (!prepareOpen(Kind.MINER_BLACKLIST, moduleId)) return;

        MinerConfigWindow widget = new MinerConfigWindow(assetId, this, tilePickerController);
        mount(widget);
    }

    void openRecipeConfig(ModuleInstance.ID moduleId) {
        ModuleInstance module = StationUiData.module(assetId, moduleId);
        AutomatedFacility facility = StationUiData.facility(assetId);
        if (facility == null || module == null || module.recipe() == null) return;
        if (!prepareOpen(Kind.RECIPE_CONFIG, module.id)) return;

        RecipeBookEditorModel editor = RecipeBookEditorModel.edit(module.id, facility.recipeBook(module));
        RecipeConfigWindow widget = new RecipeConfigWindow(assetId, this, tilePickerController, editor);
        mount(widget);
    }

    void openDebugDataGenerator(ModuleInstance.ID moduleId) {
        ModuleInstance module = StationUiData.module(assetId, moduleId);
        if (module == null || !(module.component() instanceof ModuleDebugDataGenerator)) return;
        if (!prepareOpen(Kind.DEBUG_DATA_GENERATOR, module.id)) return;

        DebugDataGeneratorConfigWindow widget = new DebugDataGeneratorConfigWindow(assetId, this);
        mount(widget);
    }

    private boolean prepareOpen(Kind targetKind, ModuleInstance.ID targetModuleId) {
        if (kind == targetKind && Objects.equals(moduleId, targetModuleId)) {
            close();
            return false;
        }
        close();
        kind = targetKind;
        moduleId = targetModuleId;
        return true;
    }

    /** Opens the configuration as a window over the station, beside the side panel the first time */
    private void mount(ModuleConfigWindow content) {
        WindowLayer layer = WindowLayer.of(host);
        if (layer == null) return;
        window = layer.open(
            WindowSpec.of(WINDOW_ID, IKey.dynamic(content::title))
                .owner(StationManagementScreen.windowId(assetId))
                .anchor((layerWidth, layerHeight, width, height) -> {
                    int[] at = GalaxiaUi.positionIn(host, layer);
                    return new int[] { Math.max(0, Math.min(at[0] + x, layerWidth - width)),
                        Math.max(0, Math.min(at[1] + y, layerHeight - height)) };
                })
                .onUpdate(content::update)
                .onClose(this::windowClosed)
                .body(content::build));
    }

    /** Rebuilds the open window, for a change of what it shows rather than of its values */
    void rebuild() {
        if (window != null) window.rebuildLater();
    }

    private void windowClosed() {
        if (closingWindow) return;
        window = null;
        close();
    }

    void close() {
        if (window != null) {
            GalaxiaWindow open = window;
            window = null;
            closingWindow = true;
            open.close();
            closingWindow = false;
        }
        this.kind = Kind.NONE;
        this.moduleId = null;
        this.hammerUpgradeReserveItems = false;
        this.hammerUpgradeVoidRefund = false;
        this.moduleUpgradeSelection = Selection.hammer(HammerVariant.BASE, ModuleTier.EV);
        this.moduleOperationCancelArmed = false;
        this.logisticsAccessMode = LogisticsConfigAccessMode.FULL;
    }

    boolean isOpen() {
        return kind != Kind.NONE;
    }

    void closeIfTargetMissing() {
        if (kind != Kind.NONE && moduleId != null && StationUiData.module(assetId, moduleId) == null) {
            close();
        }
    }

    void processDeferredActions() {
        if (retargetQueued) {
            ModuleInstance module = queuedRetargetModuleId == null ? null
                : StationUiData.module(assetId, queuedRetargetModuleId);
            retargetQueued = false;
            queuedRetargetModuleId = null;
            retargetTo(module);
        }
        closeIfTargetMissing();
    }

    void requestRetargetTo(ModuleInstance module) {
        retargetQueued = true;
        queuedRetargetModuleId = module == null ? null : module.id;
    }

    void retargetTo(ModuleInstance module) {
        if (kind == Kind.NONE) return;
        if (module == null) {
            close();
            return;
        }
        switch (kind) {
            case HAMMER -> retargetHammer(module);
            case MODULE_UPGRADE -> retargetModuleUpgrade(module);
            case LOGISTICS -> retargetLogistics(module);
            case MINER_BLACKLIST -> retargetMinerBlacklist(module);
            case RECIPE_CONFIG -> retargetRecipeConfig(module);
            case DEBUG_DATA_GENERATOR -> retargetDebugDataGenerator(module);
            case NONE -> {}
        }
    }

    boolean isHammerOpen() {
        return kind == Kind.HAMMER;
    }

    boolean isModuleUpgradeOpen() {
        return kind == Kind.MODULE_UPGRADE;
    }

    boolean isMinerBlacklistOpen() {
        return kind == Kind.MINER_BLACKLIST;
    }

    boolean isLogisticsOpen() {
        return kind == Kind.LOGISTICS;
    }

    boolean isRecipeConfigOpen() {
        return kind == Kind.RECIPE_CONFIG;
    }

    boolean isDebugDataGeneratorOpen() {
        return kind == Kind.DEBUG_DATA_GENERATOR;
    }

    ModuleInstance.ID moduleId() {
        return moduleId;
    }

    LogisticsConfigAccessMode logisticsAccessMode() {
        return logisticsAccessMode;
    }

    boolean isModuleOperationCancelArmed() {
        return moduleOperationCancelArmed;
    }

    void armModuleOperationCancel() {
        moduleOperationCancelArmed = true;
    }

    void clearModuleOperationCancel() {
        moduleOperationCancelArmed = false;
    }

    boolean hammerUpgradeReserveItems() {
        return hammerUpgradeReserveItems;
    }

    void toggleHammerUpgradeReserveItems() {
        hammerUpgradeReserveItems = !hammerUpgradeReserveItems;
    }

    boolean hammerUpgradeVoidRefund() {
        return hammerUpgradeVoidRefund;
    }

    void toggleHammerUpgradeVoidRefund() {
        hammerUpgradeVoidRefund = !hammerUpgradeVoidRefund;
    }

    Selection moduleUpgradeSelection() {
        return moduleUpgradeSelection;
    }

    void selectModuleUpgradeOption(String groupId, String optionId) {
        ModuleInstance module = StationUiData.module(assetId, moduleId);
        if (module == null) return;
        moduleUpgradeSelection = ModuleUpgradeUiModel.selectOption(module, moduleUpgradeSelection, groupId, optionId);
    }

    private void retargetHammer(ModuleInstance module) {
        if (!(module.component() instanceof ModuleHammer)) {
            close();
            return;
        }
        moduleId = module.id;
        moduleOperationCancelArmed = false;
    }

    private void retargetLogistics(ModuleInstance module) {
        if (logisticsAccessMode == LogisticsConfigAccessMode.IMPORT_ONLY) {
            close();
            return;
        }
        moduleId = module.id;
        moduleOperationCancelArmed = false;
    }

    private void retargetModuleUpgrade(ModuleInstance module) {
        if (!ModuleUpgradeUiModel.supports(module)) {
            close();
            return;
        }
        boolean sameModule = module.id.equals(moduleId);
        moduleId = module.id;
        if (sameModule) {
            moduleUpgradeSelection = ModuleUpgradeUiModel.normalize(module, moduleUpgradeSelection);
            return;
        }
        moduleUpgradeSelection = ModuleUpgradeUiModel.defaultSelection(module);
        hammerUpgradeReserveItems = false;
        hammerUpgradeVoidRefund = false;
        moduleOperationCancelArmed = false;
        // Another module can offer other choices
        rebuild();
    }

    private void retargetMinerBlacklist(ModuleInstance module) {
        if (!(module.component() instanceof ModuleMiner)) {
            close();
            return;
        }
        moduleId = module.id;
        moduleOperationCancelArmed = false;
    }

    private void retargetRecipeConfig(ModuleInstance module) {
        if (module.recipe() == null) {
            close();
            return;
        }
        AutomatedFacility facility = StationUiData.facility(assetId);
        close();
        if (facility != null && facility.moduleById(module.id) != null) openRecipeConfig(module.id);
    }

    private void retargetDebugDataGenerator(ModuleInstance module) {
        if (!(module.component() instanceof ModuleDebugDataGenerator)) {
            close();
            return;
        }
        moduleId = module.id;
    }

}
