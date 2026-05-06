package com.gtnewhorizons.galaxia.client.gui.station;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

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
    private int moduleIndex = -1;
    private int minerBlacklistPage;
    private boolean minerSettingsGroupMenuOpen;
    private HammerVariant hammerUpgradeVariant = HammerVariant.BASE;
    private ModuleTier hammerUpgradeTier = ModuleTier.EV;
    private boolean hammerUpgradeReserveItems;

    ModuleConfigModalController(ModularPanel host, CelestialAsset.ID assetId, int x, int y) {
        this.host = host;
        this.assetId = assetId;
        this.x = x;
        this.y = y;
    }

    void openHammer(int moduleIndex) {
        close();
        this.kind = Kind.HAMMER;
        this.moduleIndex = moduleIndex;
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
        close();
        this.kind = Kind.HAMMER_UPGRADE;
        this.moduleIndex = moduleIndex;
        this.hammerUpgradeVariant = variant;
        this.hammerUpgradeTier = ModuleHammer.tierForVariantSwitch(variant, tier);
        this.hammerUpgradeReserveItems = false;

        HammerUpgradeModalWidget widget = new HammerUpgradeModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(HammerUpgradeModalWidget.WIDTH)
            .height(HammerUpgradeModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openLogistics(int moduleIndex) {
        close();
        this.kind = Kind.LOGISTICS;
        this.moduleIndex = moduleIndex;

        LogisticsConfigModalWidget widget = new LogisticsConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(LogisticsConfigModalWidget.WIDTH)
            .height(LogisticsConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openMinerBlacklist(int moduleIndex) {
        close();
        this.kind = Kind.MINER_BLACKLIST;
        this.moduleIndex = moduleIndex;
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
        this.moduleIndex = -1;
        this.minerBlacklistPage = 0;
        this.minerSettingsGroupMenuOpen = false;
        this.hammerUpgradeVariant = HammerVariant.BASE;
        this.hammerUpgradeTier = ModuleTier.EV;
        this.hammerUpgradeReserveItems = false;
    }

    boolean isOpen() {
        return kind != Kind.NONE;
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
        return moduleIndex;
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
}
