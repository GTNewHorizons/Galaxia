package com.gtnewhorizons.galaxia.registry.outpost;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public class AutomatedStation extends CelestialAsset {

    public AutomatedStation(ID assetId, CelestialObjectId celestialObjectId, Status status) {
        super(assetId, celestialObjectId, Kind.AUTOMATED_STATION, status, null);
    }
}
