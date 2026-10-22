package com.gtnewhorizons.galaxia.registry.celestial.station.devices;

import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

/**
 * Keeps the withering air out of the room it stands in, wearing down anti-wither cartridges
 */
public class TileWitherBlocker extends TileStationCartridgeDevice {

    @Override
    protected String blockName() {
        return "wither_blocker";
    }

    @Override
    protected GalaxiaItemList cartridge() {
        return GalaxiaItemList.WITHER_CARTRIDGE;
    }

    @Override
    protected String missingCartridgeKey() {
        return "galaxia.gui.station_device.no_wither_cartridge";
    }
}
