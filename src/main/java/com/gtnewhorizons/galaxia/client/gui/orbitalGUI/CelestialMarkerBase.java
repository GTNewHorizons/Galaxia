package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

public class CelestialMarkerBase {

    public record CelestialMarker(String id, ResourceLocation texture, float alpha) {}

    /// This is just duplication of what we have in the store
    @Deprecated
    public static final class CelestialMarkerContext {

        private CelestialObject body;
        private List<CelestialAsset> assetState;

        public CelestialObject body() {
            return body;
        }

        public List<CelestialAsset> assetState() {
            return assetState;
        }

        public CelestialMarkerContext set(CelestialObject body, List<CelestialAsset> assetState) {
            this.body = body;
            this.assetState = assetState;
            return this;
        }
    }

    public interface CelestialMarkerProvider {

        List<CelestialMarker> getMarkers(CelestialMarkerContext context);
    }

    public static final class AssetMarkerProvider implements CelestialMarkerProvider {

        @Override
        public List<CelestialMarker> getMarkers(CelestialMarkerContext context) {
            if (context == null || context.assetState() == null
                || context.assetState()
                    .isEmpty()) {
                return List.of();
            }
            List<CelestialMarker> markers = new ArrayList<>();
            for (CelestialAsset asset : context.assetState()) {
                ResourceLocation texture = CelestialAssetIcons.get(asset.kind);
                if (texture == null) continue;
                // TODO: Put these values in the colors maybe
                float alpha = switch (asset.status()) {
                    case OPERATIONAL -> 1.0f;
                    case CONSTRUCTION_SITE -> 0.85f;
                    case DECONSTRUCTION -> 0.65f;
                    case DISABLED -> 0.45f;
                    case IN_CONSTRUCTION -> 0.0F;
                    case DESTROYED -> 0.0f;
                };
                if (alpha <= 0.0f) continue;
                markers.add(
                    new CelestialMarker(
                        "asset:" + asset.kind.name()
                            .toLowerCase(),
                        texture,
                        alpha));
            }
            return markers;
        }
    }

    public static final class CelestialAssetIcons {

        private CelestialAssetIcons() {}

        public static ResourceLocation get(CelestialAsset.Kind kind) {
            return switch (kind) {
                case STATION -> EnumTextures.ICON_STATION.get();
                case AUTOMATED_STATION -> EnumTextures.ICON_STATION_AUTOMATED.get();
                case AUTOMATED_OUTPOST -> EnumTextures.ICON_OUTPOST_AUTOMATED.get();
            };
        }
    }

    public static final class CelestialMarkerRegistry {

        private static final List<CelestialMarkerProvider> PROVIDERS = new ArrayList<>();
        private static boolean bootstrapped;

        private CelestialMarkerRegistry() {}

        public static synchronized void registerDefaults() {
            if (bootstrapped) return;
            bootstrapped = true;
            register(new AssetMarkerProvider());
        }

        public static synchronized void register(CelestialMarkerProvider provider) {
            if (provider != null) PROVIDERS.add(provider);
        }

        public static synchronized List<CelestialMarker> getMarkers(CelestialMarkerContext context) {
            registerDefaults();
            Map<String, CelestialMarker> markersById = new LinkedHashMap<>();
            for (CelestialMarkerProvider provider : PROVIDERS) {
                for (CelestialMarker marker : provider.getMarkers(context)) {
                    if (marker == null || marker.texture() == null) continue;
                    String markerId = marker.id() != null && !marker.id()
                        .isEmpty() ? marker.id()
                            : marker.texture()
                                .toString();
                    CelestialMarker existing = markersById.get(markerId);
                    if (existing == null || marker.alpha() > existing.alpha()) {
                        markersById.put(markerId, marker);
                    }
                }
            }
            return Collections.unmodifiableList(new ArrayList<>(markersById.values()));
        }
    }
}
