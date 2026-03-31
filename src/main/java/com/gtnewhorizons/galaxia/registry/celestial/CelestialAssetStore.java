package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CelestialAssetStore {

    private static final Map<String, MutableBodyState> STATE_BY_BODY = new LinkedHashMap<>();

    private CelestialAssetStore() {}

    public static synchronized CelestialBodyAssetState getState(String celestialObjectId) {
        MutableBodyState state = STATE_BY_BODY.computeIfAbsent(celestialObjectId, MutableBodyState::new);
        return state.snapshot();
    }

    public static synchronized CelestialConstructionSite createConstructionSite(String celestialObjectId, String displayName,
        CelestialAssetKind kind, CelestialAssetLocation location) {
        MutableBodyState state = STATE_BY_BODY.computeIfAbsent(celestialObjectId, MutableBodyState::new);
        String siteId = "site_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Map<String, Long> required = defaultRequirements(kind);
        CelestialConstructionSite site = new CelestialConstructionSite(
            siteId,
            celestialObjectId,
            displayName,
            kind,
            location,
            ConstructionSiteStatus.PLANNED,
            required,
            new LinkedHashMap<>());
        state.constructionSites.add(site);
        return site;
    }

    public static synchronized boolean cancelConstruction(String siteId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.constructionSites.size(); i++) {
                if (state.constructionSites.get(i).siteId().equals(siteId)) {
                    state.constructionSites.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    public static synchronized boolean completeConstruction(String siteId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.constructionSites.size(); i++) {
                CelestialConstructionSite site = state.constructionSites.get(i);
                if (!site.siteId().equals(siteId)) {
                    continue;
                }
                CelestialManagedAsset asset = new CelestialManagedAsset(
                    "asset_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                    site.celestialObjectId(),
                    site.displayName(),
                    site.kind(),
                    site.location());
                state.assets.add(asset);
                state.constructionSites.remove(i);
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean destroyAsset(String assetId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                if (state.assets.get(i).assetId().equals(assetId)) {
                    state.assets.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<String, Long> defaultRequirements(CelestialAssetKind kind) {
        Map<String, Long> required = new LinkedHashMap<>();
        switch (kind) {
            case STATION -> {
                required.put("Steel", 256L);
                required.put("Circuits", 48L);
                required.put("Motors", 32L);
            }
            case OUTPOST -> {
                required.put("Steel", 96L);
                required.put("Pipes", 32L);
                required.put("Control Units", 12L);
            }
        }
        return required;
    }

    private static final class MutableBodyState {

        private final String celestialObjectId;
        private final List<CelestialManagedAsset> assets = new ArrayList<>();
        private final List<CelestialConstructionSite> constructionSites = new ArrayList<>();

        private MutableBodyState(String celestialObjectId) {
            this.celestialObjectId = celestialObjectId;
        }

        private CelestialBodyAssetState snapshot() {
            return new CelestialBodyAssetState(celestialObjectId, assets, constructionSites);
        }
    }
}
