package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStatus;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;

public final class AssetMarkerProvider implements CelestialMarkerProvider {

    @Override
    public List<CelestialMarker> getMarkers(CelestialMarkerContext context) {
        if (context == null || context.assetState() == null || context.assetState().assets().isEmpty()) {
            return new ArrayList<>();
        }

        List<CelestialMarker> markers = new ArrayList<>();
        for (CelestialManagedAsset asset : context.assetState().assets()) {
            ResourceLocation texture = CelestialAssetIcons.get(asset.kind());
            if (texture == null) {
                continue;
            }

            float alpha = getAlpha(asset.status());
            if (alpha <= 0.0f) {
                continue;
            }
            markers.add(new CelestialMarker("asset:" + asset.kind().name().toLowerCase(), texture, alpha));
        }
        return markers;
    }

    private float getAlpha(CelestialAssetStatus status) {
        return switch (status) {
            case OPERATIONAL -> 1.0f;
            case CONSTRUCTION_SITE -> 0.85f;
            case DECONSTRUCTION -> 0.65f;
            case DISABLED -> 0.45f;
            case DESTROYED -> 0.0f;
        };
    }
}
