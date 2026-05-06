package com.gtnewhorizons.galaxia.client.gui.station;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

final class ModuleConfigModalController {

    enum Kind {
        NONE,
        HAMMER,
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

        HammerConfigModalWidget widget = new HammerConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(HammerConfigModalWidget.WIDTH)
            .height(HammerConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openMinerBlacklist(int moduleIndex) {
        close();
        this.kind = Kind.MINER_BLACKLIST;
        this.moduleIndex = moduleIndex;
        this.minerBlacklistPage = 0;

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
    }

    boolean isOpen() {
        return kind != Kind.NONE;
    }

    boolean isHammerOpen() {
        return kind == Kind.HAMMER;
    }

    boolean isMinerBlacklistOpen() {
        return kind == Kind.MINER_BLACKLIST;
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
}
