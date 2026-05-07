package com.gtnewhorizons.galaxia.client.gui.station;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

final class ModuleConfigModalController {

    enum Kind {
        NONE,
        HAMMER,
        HAMMER_UPGRADE,
        LOGISTICS,
        MINER_BLACKLIST
    }

    private final ModularPanel host;
    private final CelestialAsset.ID assetId;
    private final int x;
    private final int y;

    private ParentWidget<?> modal;
    private Kind kind = Kind.NONE;
    private ModuleInstance.ID moduleId;
    private int minerBlacklistPage;
    private boolean minerSettingsGroupMenuOpen;
    private HammerVariant hammerUpgradeVariant = HammerVariant.BASE;
    private ModuleTier hammerUpgradeTier = ModuleTier.EV;
    private boolean hammerUpgradeReserveItems;
    private boolean hammerUpgradeVoidRefund;

    ModuleConfigModalController(ModularPanel host, CelestialAsset.ID assetId, int x, int y) {
        this.host = host;
        this.assetId = assetId;
        this.x = x;
        this.y = y;
    }

    void openHammer(int moduleIndex) {
        ModuleInstance.ID targetModuleId = resolveModuleId(moduleIndex);
        if (targetModuleId == null) return;
        close();
        this.kind = Kind.HAMMER;
        this.moduleId = targetModuleId;
        this.minerBlacklistPage = 0;
        this.minerSettingsGroupMenuOpen = false;

        HammerConfigModalWidget widget = new HammerConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(HammerConfigModalWidget.WIDTH)
            .height(HammerConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openHammerUpgrade(int moduleIndex, HammerVariant variant, ModuleTier tier) {
        ModuleInstance.ID targetModuleId = resolveModuleId(moduleIndex);
        if (targetModuleId == null) return;
        close();
        this.kind = Kind.HAMMER_UPGRADE;
        this.moduleId = targetModuleId;
        this.hammerUpgradeVariant = variant;
        this.hammerUpgradeTier = ModuleHammer.tierForVariantSwitch(variant, tier);
        this.hammerUpgradeReserveItems = false;
        this.hammerUpgradeVoidRefund = false;

        HammerUpgradeModalWidget widget = new HammerUpgradeModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(HammerUpgradeModalWidget.WIDTH)
            .height(HammerUpgradeModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openLogistics(int moduleIndex) {
        ModuleInstance.ID targetModuleId = resolveModuleId(moduleIndex);
        if (targetModuleId == null) return;
        close();
        this.kind = Kind.LOGISTICS;
        this.moduleId = targetModuleId;

        LogisticsConfigModalWidget widget = new LogisticsConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(LogisticsConfigModalWidget.WIDTH)
            .height(LogisticsConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openMinerBlacklist(int moduleIndex) {
        ModuleInstance.ID targetModuleId = resolveModuleId(moduleIndex);
        if (targetModuleId == null) return;
        close();
        this.kind = Kind.MINER_BLACKLIST;
        this.moduleId = targetModuleId;
        this.minerBlacklistPage = 0;
        this.minerSettingsGroupMenuOpen = false;

        MinerBlacklistConfigModalWidget widget = new MinerBlacklistConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(MinerBlacklistConfigModalWidget.WIDTH)
            .height(MinerBlacklistConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void close() {
        if (modal != null) {
            host.remove(modal);
            modal = null;
        }
        this.kind = Kind.NONE;
        this.moduleId = null;
        this.minerBlacklistPage = 0;
        this.minerSettingsGroupMenuOpen = false;
        this.hammerUpgradeVariant = HammerVariant.BASE;
        this.hammerUpgradeTier = ModuleTier.EV;
        this.hammerUpgradeReserveItems = false;
        this.hammerUpgradeVoidRefund = false;
    }

    boolean isOpen() {
        return kind != Kind.NONE;
    }

    boolean containsMouse(int mouseX, int mouseY) {
        if (modal == null) return false;
        return mouseX >= modal.getArea().rx && mouseX < modal.getArea().rx + modal.getArea().width
            && mouseY >= modal.getArea().ry
            && mouseY < modal.getArea().ry + modal.getArea().height;
    }

    void closeIfTargetMissing() {
        if (kind != Kind.NONE && moduleId != null && moduleIndex() < 0) {
            close();
        }
    }

    void retargetTo(ModuleInstance module) {
        if (kind == Kind.NONE) return;
        if (module == null) {
            close();
            return;
        }
        switch (kind) {
            case HAMMER -> retargetHammer(module);
            case HAMMER_UPGRADE -> retargetHammerUpgrade(module);
            case LOGISTICS -> retargetLogistics(module);
            case MINER_BLACKLIST -> retargetMinerBlacklist(module);
            case NONE -> {}
        }
    }

    boolean isHammerOpen() {
        return kind == Kind.HAMMER;
    }

    boolean isHammerUpgradeOpen() {
        return kind == Kind.HAMMER_UPGRADE;
    }

    boolean isMinerBlacklistOpen() {
        return kind == Kind.MINER_BLACKLIST;
    }

    boolean isLogisticsOpen() {
        return kind == Kind.LOGISTICS;
    }

    int moduleIndex() {
        if (moduleId == null) return -1;
        return ModuleConfigModalSupport.moduleIndex(assetId, moduleId);
    }

    ModuleInstance.ID moduleId() {
        return moduleId;
    }

    int minerBlacklistPage() {
        return minerBlacklistPage;
    }

    void setMinerBlacklistPage(int minerBlacklistPage) {
        this.minerBlacklistPage = Math.max(0, minerBlacklistPage);
    }

    boolean isMinerSettingsGroupMenuOpen() {
        return minerSettingsGroupMenuOpen;
    }

    void toggleMinerSettingsGroupMenu() {
        minerSettingsGroupMenuOpen = !minerSettingsGroupMenuOpen;
    }

    void closeMinerSettingsGroupMenu() {
        minerSettingsGroupMenuOpen = false;
    }

    HammerVariant hammerUpgradeVariant() {
        return hammerUpgradeVariant;
    }

    void setHammerUpgradeVariant(HammerVariant hammerUpgradeVariant) {
        this.hammerUpgradeVariant = hammerUpgradeVariant;
        this.hammerUpgradeTier = ModuleHammer.tierForVariantSwitch(hammerUpgradeVariant, hammerUpgradeTier);
    }

    ModuleTier hammerUpgradeTier() {
        return hammerUpgradeTier;
    }

    void setHammerUpgradeTier(ModuleTier hammerUpgradeTier) {
        ModuleHammer.requireTier(hammerUpgradeVariant, hammerUpgradeTier);
        this.hammerUpgradeTier = hammerUpgradeTier;
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

    private ModuleInstance.ID resolveModuleId(int moduleIndex) {
        ModuleInstance module = ModuleConfigModalSupport.module(assetId, moduleIndex);
        return module == null ? null : module.id;
    }

    private void retargetHammer(ModuleInstance module) {
        if (!(module.component() instanceof ModuleHammer)) {
            close();
            return;
        }
        moduleId = module.id;
    }

    private void retargetHammerUpgrade(ModuleInstance module) {
        if (!(module.component() instanceof ModuleHammer hammer)) {
            close();
            return;
        }
        moduleId = module.id;
        hammerUpgradeVariant = hammer.variant();
        hammerUpgradeTier = ModuleHammer.tierForVariantSwitch(hammer.variant(), module.tier());
        hammerUpgradeReserveItems = false;
        hammerUpgradeVoidRefund = false;
    }

    private void retargetLogistics(ModuleInstance module) {
        moduleId = module.id;
    }

    private void retargetMinerBlacklist(ModuleInstance module) {
        if (!(module.component() instanceof ModuleMiner)) {
            close();
            return;
        }
        moduleId = module.id;
        minerBlacklistPage = 0;
        minerSettingsGroupMenuOpen = false;
    }
}
