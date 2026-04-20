package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

public enum SystemAssetFilter {

    ALL,
    STATIONS,
    OUTPOSTS,
    CONSTRUCTION,
    MINING,
    PRODUCTION,
    WARNINGS;

    public boolean accepts(CelestialAsset asset) {
        return switch (this) {
            case ALL -> true;
            case STATIONS -> asset.kind == CelestialAsset.Kind.STATION
                || asset.kind == CelestialAsset.Kind.AUTOMATED_STATION;
            case OUTPOSTS -> asset.kind == CelestialAsset.Kind.AUTOMATED_OUTPOST;
            case CONSTRUCTION -> asset.isInConstruction();
            case MINING -> asset.hasMiningCapability();
            case PRODUCTION -> asset.hasProductionCapability();
            case WARNINGS -> asset.warningPriority()
                .isWarning();
        };
    }

    public String getDisplayName() {
        return StatCollector.translateToLocal(
            "galaxia.system_asset.filter." + this.name()
                .toLowerCase());
    }
}
