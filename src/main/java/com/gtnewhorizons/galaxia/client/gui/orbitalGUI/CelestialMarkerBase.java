package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

public final class CelestialMarkerBase {

    private CelestialMarkerBase() {}

    static float assetMarkerAlpha(CelestialAsset asset) {
        if (asset == null) return 0.0f;
        return switch (asset.status()) {
            case OPERATIONAL -> 1.0f;
            case CONSTRUCTION_SITE -> 0.85f;
            case DECONSTRUCTION -> 0.65f;
            case DISABLED -> 0.45f;
            case IN_CONSTRUCTION -> 0.0F;
            case DESTROYED -> 0.0f;
        };
    }

    public static final class CelestialAssetIcons {

        private CelestialAssetIcons() {}

        public static ResourceLocation get(CelestialAsset.Kind kind) {
            return switch (kind) {
                case STATION -> EnumTextures.ICON_STATION.get();
                case AUTOMATED_STATION -> EnumTextures.ICON_STATION_AUTOMATED.get();
                case AUTOMATED_OUTPOST -> EnumTextures.ICON_OUTPOST_AUTOMATED.get();
                case SATELLITE -> EnumTextures.ICON_SATELLITE.get();
            };
        }
    }
}
