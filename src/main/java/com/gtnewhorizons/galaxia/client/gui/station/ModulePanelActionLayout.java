package com.gtnewhorizons.galaxia.client.gui.station;

final class ModulePanelActionLayout {

    static final int COLUMNS = 2;

    private ModulePanelActionLayout() {}

    static Cell cellForIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        return new Cell(index / COLUMNS, index % COLUMNS);
    }

    record Cell(int row, int column) {}
}
