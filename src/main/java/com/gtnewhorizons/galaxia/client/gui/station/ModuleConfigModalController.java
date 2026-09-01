package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Objects;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

final class ModuleConfigModalController implements StationOverlayCoordinator.Overlay {

    enum Kind {
        NONE,
        HAMMER,
        MODULE_UPGRADE,
        LOGISTICS,
        MINER_BLACKLIST,
        RECIPE_CONFIG,
        DEBUG_DATA_GENERATOR
    }

    private final ModularPanel host;
    private final CelestialAsset.ID assetId;
    private final int x;
    private final int y;
    private final StationTilePickerController tilePickerController;
    private final StationOverlayCoordinator overlayCoordinator;

    private ParentWidget<?> modal;
    private Kind kind = Kind.NONE;
    private ModuleInstance.ID moduleId;
    private int minerBlacklistPage;
    private boolean moduleOperationCancelArmed;
    private boolean hammerUpgradeReserveItems;
    private boolean hammerUpgradeVoidRefund;
    private boolean retargetQueued;
    private ModuleInstance.ID queuedRetargetModuleId;
    private LogisticsConfigAccessMode logisticsAccessMode = LogisticsConfigAccessMode.FULL;
    private ModuleUpgradeSelection moduleUpgradeSelection = ModuleUpgradeSelection
        .hammer(HammerVariant.BASE, ModuleTier.EV);

    ModuleConfigModalController(ModularPanel host, CelestialAsset.ID assetId, int x, int y) {
        this(host, assetId, x, y, null, new StationOverlayCoordinator());
    }

    ModuleConfigModalController(ModularPanel host, CelestialAsset.ID assetId, int x, int y,
        StationTilePickerController tilePickerController, StationOverlayCoordinator overlayCoordinator) {
        this.host = host;
        this.assetId = assetId;
        this.x = x;
        this.y = y;
        this.tilePickerController = tilePickerController;
        this.overlayCoordinator = overlayCoordinator;
        overlayCoordinator.register(this);
    }

    void openHammer(ModuleInstance.ID moduleId) {
        if (ModuleConfigModalSupport.module(assetId, moduleId) == null) return;
        if (closeIfSame(Kind.HAMMER, moduleId)) return;
        overlayCoordinator.closeOthers(this);
        close();
        this.kind = Kind.HAMMER;
        this.moduleId = moduleId;
        this.minerBlacklistPage = 0;
        this.moduleOperationCancelArmed = false;

        HammerConfigModalWidget widget = new HammerConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(HammerConfigModalWidget.WIDTH)
            .height(HammerConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openUpgrade(ModuleInstance.ID moduleId) {
        ModuleInstance module = ModuleConfigModalSupport.module(assetId, moduleId);
        if (module == null || !ModuleUpgradeUiModel.supports(module)) return;
        if (closeIfSame(Kind.MODULE_UPGRADE, module.id)) return;
        overlayCoordinator.closeOthers(this);
        close();
        this.kind = Kind.MODULE_UPGRADE;
        this.moduleId = module.id;
        this.moduleUpgradeSelection = ModuleUpgradeUiModel.defaultSelection(module);
        this.hammerUpgradeReserveItems = false;
        this.hammerUpgradeVoidRefund = false;
        this.moduleOperationCancelArmed = false;

        ModuleUpgradeModalWidget widget = new ModuleUpgradeModalWidget(assetId, this, tilePickerController);
        widget.left(x)
            .top(y)
            .width(ModuleUpgradeModalWidget.WIDTH)
            .height(ModuleUpgradeModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openLogistics(ModuleInstance.ID moduleId) {
        if (ModuleConfigModalSupport.module(assetId, moduleId) == null) return;
        if (closeIfSame(Kind.LOGISTICS, moduleId)) return;
        overlayCoordinator.closeOthers(this);
        close();
        this.kind = Kind.LOGISTICS;
        this.moduleId = moduleId;
        this.logisticsAccessMode = LogisticsConfigAccessMode.FULL;

        LogisticsConfigModalWidget widget = new LogisticsConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(LogisticsConfigModalWidget.WIDTH)
            .height(LogisticsConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openStationLogistics() {
        openStationLogistics(LogisticsConfigAccessMode.FULL);
    }

    void openCoreLogistics() {
        openStationLogistics(LogisticsConfigAccessMode.IMPORT_ONLY);
    }

    private void openStationLogistics(LogisticsConfigAccessMode accessMode) {
        if (closeIfSame(Kind.LOGISTICS, null)) return;
        overlayCoordinator.closeOthers(this);
        close();
        this.kind = Kind.LOGISTICS;
        this.moduleId = null;
        this.logisticsAccessMode = accessMode == null ? LogisticsConfigAccessMode.FULL : accessMode;

        LogisticsConfigModalWidget widget = new LogisticsConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(LogisticsConfigModalWidget.WIDTH)
            .height(LogisticsConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openMinerBlacklist(ModuleInstance.ID moduleId) {
        if (ModuleConfigModalSupport.module(assetId, moduleId) == null) return;
        if (closeIfSame(Kind.MINER_BLACKLIST, moduleId)) return;
        overlayCoordinator.closeOthers(this);
        close();
        this.kind = Kind.MINER_BLACKLIST;
        this.moduleId = moduleId;
        this.minerBlacklistPage = 0;
        this.moduleOperationCancelArmed = false;

        MinerBlacklistConfigModalWidget widget = new MinerBlacklistConfigModalWidget(
            assetId,
            this,
            tilePickerController);
        widget.left(x)
            .top(y)
            .width(MinerBlacklistConfigModalWidget.WIDTH)
            .height(MinerBlacklistConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openRecipeConfig(ModuleInstance.ID moduleId) {
        ModuleInstance module = ModuleConfigModalSupport.module(assetId, moduleId);
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null || module == null || module.recipe() == null) return;
        if (closeIfSame(Kind.RECIPE_CONFIG, module.id)) return;
        overlayCoordinator.closeOthers(this);
        close();
        this.kind = Kind.RECIPE_CONFIG;
        this.moduleId = module.id;

        RecipeBookEditorModel editor = RecipeBookEditorModel
            .edit(facility.recipeBookOwner(module), facility.recipeBook(module));
        RecipeConfigModalWidget widget = new RecipeConfigModalWidget(assetId, this, tilePickerController, editor);
        widget.left(x)
            .top(y)
            .width(RecipeConfigModalWidget.WIDTH)
            .height(RecipeConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openDebugDataGenerator(ModuleInstance.ID moduleId) {
        ModuleInstance module = ModuleConfigModalSupport.module(assetId, moduleId);
        if (module == null || !(module.component() instanceof ModuleDebugDataGenerator)) return;
        if (closeIfSame(Kind.DEBUG_DATA_GENERATOR, module.id)) return;
        overlayCoordinator.closeOthers(this);
        close();
        this.kind = Kind.DEBUG_DATA_GENERATOR;
        this.moduleId = module.id;

        DebugDataGeneratorConfigModalWidget widget = new DebugDataGeneratorConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(DebugDataGeneratorConfigModalWidget.WIDTH)
            .height(DebugDataGeneratorConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    private boolean closeIfSame(Kind targetKind, ModuleInstance.ID targetModuleId) {
        if (kind == targetKind && Objects.equals(moduleId, targetModuleId)) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        if (modal != null) {
            host.remove(modal);
            modal = null;
        }
        this.kind = Kind.NONE;
        this.moduleId = null;
        this.minerBlacklistPage = 0;
        this.hammerUpgradeReserveItems = false;
        this.hammerUpgradeVoidRefund = false;
        this.moduleUpgradeSelection = ModuleUpgradeSelection.hammer(HammerVariant.BASE, ModuleTier.EV);
        this.moduleOperationCancelArmed = false;
        this.logisticsAccessMode = LogisticsConfigAccessMode.FULL;
    }

    @Override
    public boolean isOpen() {
        return kind != Kind.NONE;
    }

    @Override
    public boolean containsMouse(int mouseX, int mouseY) {
        if (modal == null) return false;
        return mouseX >= modal.getArea().rx && mouseX < modal.getArea().rx + modal.getArea().width
            && mouseY >= modal.getArea().ry
            && mouseY < modal.getArea().ry + modal.getArea().height;
    }

    void closeIfTargetMissing() {
        if (kind != Kind.NONE && moduleId != null && ModuleConfigModalSupport.module(assetId, moduleId) == null) {
            close();
        }
    }

    @Override
    public void processDeferredActions() {
        if (retargetQueued) {
            ModuleInstance module = queuedRetargetModuleId == null ? null
                : ModuleConfigModalSupport.module(assetId, queuedRetargetModuleId);
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

    int minerBlacklistPage() {
        return minerBlacklistPage;
    }

    void setMinerBlacklistPage(int minerBlacklistPage) {
        this.minerBlacklistPage = Math.max(0, minerBlacklistPage);
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

    ModuleUpgradeSelection moduleUpgradeSelection() {
        return moduleUpgradeSelection;
    }

    void selectModuleUpgradeOption(String groupId, String optionId) {
        ModuleInstance module = ModuleConfigModalSupport.module(assetId, moduleId);
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
    }

    private void retargetMinerBlacklist(ModuleInstance module) {
        if (!(module.component() instanceof ModuleMiner)) {
            close();
            return;
        }
        moduleId = module.id;
        minerBlacklistPage = 0;
        moduleOperationCancelArmed = false;
    }

    private void retargetRecipeConfig(ModuleInstance module) {
        if (module.recipe() == null) {
            close();
            return;
        }
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
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
