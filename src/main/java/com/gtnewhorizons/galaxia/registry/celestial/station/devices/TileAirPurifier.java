package com.gtnewhorizons.galaxia.registry.celestial.station.devices;

import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

/**
 * Filters spores out of the room it stands in, wearing down air filters
 */
public class TileAirPurifier extends TileStationCartridgeDevice {

    @Override
    protected String blockName() {
        return "air_purifier";
    }

    @Override
    protected GalaxiaItemList cartridge() {
        return GalaxiaItemList.AIR_FILTER;
    }

    @Override
    protected String missingCartridgeKey() {
        return "galaxia.gui.station_device.no_filter";
    }
}
