package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.Comparator;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

public enum SystemAssetSort {

    BY_BODY,
    BY_KIND,
    BY_NAME,
    BY_WARNINGS_FIRST;

    public Comparator<CelestialAsset> comparator() {
        return switch (this) {
            case BY_BODY -> Comparator.comparing((CelestialAsset a) -> a.celestialObjectId.name())
                .thenComparing(CelestialAsset::displayName);
            case BY_KIND -> Comparator.comparing((CelestialAsset a) -> a.kind.ordinal())
                .thenComparing(CelestialAsset::displayName);
            case BY_NAME -> Comparator.comparing(CelestialAsset::displayName);
            case BY_WARNINGS_FIRST -> Comparator.comparingInt((CelestialAsset a) -> -a.warningPriority().priority)
                .thenComparing((CelestialAsset a) -> a.isInConstruction() ? 0 : 1)
                .thenComparing(CelestialAsset::displayName);
        };
    }

    public String getDisplayName() {
        return StatCollector.translateToLocal(
            "galaxia.system_asset.sort." + this.name()
                .toLowerCase());
    }
}
