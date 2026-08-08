package com.gtnewhorizons.galaxia.client.gui.station;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public record StationMapFrame(int widgetWidth, int widgetHeight, int contentLeft, int contentRightPadding,
    int contentVerticalPadding, int panX, int panY) {

    public int tileLocalX(StationTileCoord coord) {
        return tileLocalX(coord.dx());
    }

    public int tileLocalY(StationTileCoord coord) {
        return tileLocalY(coord.dy());
    }

    public int tileLocalX(int dx) {
        return StationMapViewport.tileLeftX(dx, widgetWidth, contentLeft, contentRightPadding, panX);
    }

    public int tileLocalY(int dy) {
        return StationMapViewport.tileTopY(dy, widgetHeight, contentVerticalPadding, panY);
    }

    public int connectorLocalX(StationTileCoord left) {
        return StationMapViewport.connectorLeftX(left, widgetWidth, contentLeft, contentRightPadding, panX);
    }

    public int connectorLocalY(StationTileCoord top) {
        return StationMapViewport.connectorTopY(top, widgetHeight, contentVerticalPadding, panY);
    }
}
