package com.gtnewhorizons.galaxia.client.gui.station;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

final class ModuleConfigModalController {

    enum Kind {
        NONE,
        HAMMER,
        MINER_VOID
    }

    private final ModularPanel host;
    private final CelestialAsset.ID assetId;
    private final int x;
    private final int y;

    private ParentWidget<?> modal;
    private Kind kind = Kind.NONE;
    private int moduleIndex = -1;
    private int minerVoidPage;

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
        this.minerVoidPage = 0;

        HammerConfigModalWidget widget = new HammerConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(HammerConfigModalWidget.WIDTH)
            .height(HammerConfigModalWidget.HEIGHT);
        this.modal = widget;
        host.child(widget);
    }

    void openMinerVoid(int moduleIndex) {
        close();
        this.kind = Kind.MINER_VOID;
        this.moduleIndex = moduleIndex;
        this.minerVoidPage = 0;

        MinerVoidConfigModalWidget widget = new MinerVoidConfigModalWidget(assetId, this);
        widget.left(x)
            .top(y)
            .width(MinerVoidConfigModalWidget.WIDTH)
            .height(MinerVoidConfigModalWidget.HEIGHT);
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
        this.minerVoidPage = 0;
    }

    boolean isOpen() {
        return kind != Kind.NONE;
    }

    boolean isHammerOpen() {
        return kind == Kind.HAMMER;
    }

    boolean isMinerVoidOpen() {
        return kind == Kind.MINER_VOID;
    }

    int moduleIndex() {
        return moduleIndex;
    }

    int minerVoidPage() {
        return minerVoidPage;
    }

    void setMinerVoidPage(int minerVoidPage) {
        this.minerVoidPage = Math.max(0, minerVoidPage);
    }
}
