package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;

public final class CelestialAssetIcons {

    private CelestialAssetIcons() {}

    public static ResourceLocation get(CelestialAssetKind kind) {
        return switch (kind) {
            case STATION -> EnumTextures.ICON_STATION.get();
            case AUTOMATED_STATION -> EnumTextures.ICON_STATION_AUTOMATED.get();
            case AUTOMATED_OUTPOST -> EnumTextures.ICON_OUTPOST_AUTOMATED.get();
        };
    }
}
