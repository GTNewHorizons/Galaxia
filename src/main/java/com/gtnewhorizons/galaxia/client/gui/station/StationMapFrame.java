package com.gtnewhorizons.galaxia.client.gui.station;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

record StationMapFrame(int widgetWidth, int widgetHeight, int contentLeft, int contentRightPadding,
    int contentVerticalPadding, int panX, int panY) {

    int tileLocalX(StationTileCoord coord) {
        return tileLocalX(coord.dx());
    }

    int tileLocalY(StationTileCoord coord) {
        return tileLocalY(coord.dy());
    }

    int tileLocalX(int dx) {
        return StationMapViewport.tileLeftX(dx, widgetWidth, contentLeft, contentRightPadding, panX);
    }

    int tileLocalY(int dy) {
        return StationMapViewport.tileTopY(dy, widgetHeight, contentVerticalPadding, panY);
    }
}
