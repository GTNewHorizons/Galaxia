package com.gtnewhorizons.galaxia.client.gui.station;

final class ModuleConfigModalController {

    enum Kind {
        NONE,
        HAMMER,
        MINER_VOID
    }

    private Kind kind = Kind.NONE;
    private int moduleIndex = -1;
    private int minerVoidPage;

    void openHammer(int moduleIndex) {
        this.kind = Kind.HAMMER;
        this.moduleIndex = moduleIndex;
        this.minerVoidPage = 0;
    }

    void openMinerVoid(int moduleIndex) {
        this.kind = Kind.MINER_VOID;
        this.moduleIndex = moduleIndex;
        this.minerVoidPage = 0;
    }

    void close() {
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
