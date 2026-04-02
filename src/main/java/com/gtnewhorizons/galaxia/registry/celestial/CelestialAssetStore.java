package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

public final class CelestialAssetStore {

    private static final Map<String, MutableBodyState> STATE_BY_BODY = new LinkedHashMap<>();

    private CelestialAssetStore() {}

    public static synchronized CelestialBodyAssetState getState(String celestialObjectId) {
        MutableBodyState state = STATE_BY_BODY.computeIfAbsent(celestialObjectId, MutableBodyState::new);
        return state.snapshot();
    }

    public static synchronized CelestialBodyAssetState getStateIfPresent(String celestialObjectId) {
        MutableBodyState state = STATE_BY_BODY.get(celestialObjectId);
        if (state == null) {
            return new CelestialBodyAssetState(celestialObjectId, Collections.emptyList());
        }
        return state.snapshot();
    }

    public static synchronized CelestialManagedAsset createAssetInConstruction(String celestialObjectId,
        String displayName, CelestialAssetKind kind, CelestialAssetLocation location) {
        MutableBodyState state = STATE_BY_BODY.computeIfAbsent(celestialObjectId, MutableBodyState::new);
        String assetId = "asset_" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
        List<CelestialAssetRequirement> required = defaultRequirements(kind);
        CelestialManagedAsset asset = new CelestialManagedAsset(
            assetId,
            celestialObjectId,
            displayName,
            kind,
            location,
            CelestialAssetStatus.CONSTRUCTION_SITE,
            required,
            Collections.emptyList());
        state.assets.add(asset);
        return asset;
    }

    public static synchronized CelestialManagedAsset createOperationalAsset(String celestialObjectId,
        String displayName, CelestialAssetKind kind, CelestialAssetLocation location) {
        MutableBodyState state = STATE_BY_BODY.computeIfAbsent(celestialObjectId, MutableBodyState::new);
        CelestialManagedAsset asset = new CelestialManagedAsset(
            "asset_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8),
            celestialObjectId,
            displayName,
            kind,
            location,
            CelestialAssetStatus.OPERATIONAL,
            Collections.emptyList(),
            Collections.emptyList());
        state.assets.add(asset);
        return asset;
    }

    public static synchronized boolean cancelConstruction(String assetId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                CelestialManagedAsset asset = state.assets.get(i);
                if (asset.assetId()
                    .equals(assetId) && asset.status() == CelestialAssetStatus.CONSTRUCTION_SITE) {
                    state.assets.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    public static synchronized boolean startDeconstruction(String assetId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                CelestialManagedAsset asset = state.assets.get(i);
                if (!asset.assetId()
                    .equals(assetId) || asset.status() != CelestialAssetStatus.CONSTRUCTION_SITE) {
                    continue;
                }
                state.assets.set(
                    i,
                    new CelestialManagedAsset(
                        asset.assetId(),
                        asset.celestialObjectId(),
                        asset.displayName(),
                        asset.kind(),
                        asset.location(),
                        CelestialAssetStatus.DECONSTRUCTION,
                        asset.requiredResources(),
                        asset.constructionInventory()));
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean completeConstruction(String assetId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                CelestialManagedAsset asset = state.assets.get(i);
                if (!asset.assetId()
                    .equals(assetId) || asset.status() != CelestialAssetStatus.CONSTRUCTION_SITE) {
                    continue;
                }
                state.assets.set(i, toOperationalAsset(asset));
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean addToConstructionInventory(String assetId, ItemStack stack, long amount) {
        if (stack == null || amount <= 0) {
            return false;
        }

        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                CelestialManagedAsset asset = state.assets.get(i);
                if (!asset.assetId()
                    .equals(assetId) || asset.status() != CelestialAssetStatus.CONSTRUCTION_SITE) {
                    continue;
                }

                List<CelestialAssetRequirement> inventory = mergeIntoConstructionInventory(
                    asset.constructionInventory(),
                    stack,
                    amount);
                CelestialManagedAsset updated = new CelestialManagedAsset(
                    asset.assetId(),
                    asset.celestialObjectId(),
                    asset.displayName(),
                    asset.kind(),
                    asset.location(),
                    asset.status(),
                    asset.requiredResources(),
                    inventory);
                state.assets.set(i, isConstructionSatisfied(updated) ? toOperationalAsset(updated) : updated);
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean destroyAsset(String assetId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                if (state.assets.get(i)
                    .assetId()
                    .equals(assetId)) {
                    state.assets.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    public static synchronized boolean renameAsset(String assetId, String displayName) {
        if (displayName == null || displayName.trim()
            .isEmpty()) {
            return false;
        }
        String trimmedName = displayName.trim();
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                CelestialManagedAsset asset = state.assets.get(i);
                if (!asset.assetId()
                    .equals(assetId)) {
                    continue;
                }
                state.assets.set(
                    i,
                    new CelestialManagedAsset(
                        asset.assetId(),
                        asset.celestialObjectId(),
                        trimmedName,
                        asset.kind(),
                        asset.location(),
                        asset.status(),
                        asset.requiredResources(),
                        asset.constructionInventory()));
                return true;
            }
        }
        return false;
    }

    public static synchronized List<CelestialAssetRequirement> previewRequirements(CelestialAssetKind kind) {
        return Collections.unmodifiableList(new ArrayList<>(defaultRequirements(kind)));
    }

    private static CelestialManagedAsset toOperationalAsset(CelestialManagedAsset asset) {
        return new CelestialManagedAsset(
            asset.assetId(),
            asset.celestialObjectId(),
            asset.displayName(),
            asset.kind(),
            asset.location(),
            CelestialAssetStatus.OPERATIONAL,
            asset.requiredResources(),
            asset.constructionInventory());
    }

    private static boolean isConstructionSatisfied(CelestialManagedAsset asset) {
        for (CelestialAssetRequirement required : asset.requiredResources()) {
            long available = 0;
            for (CelestialAssetRequirement stored : asset.constructionInventory()) {
                if (required.matches(stored.stack())) {
                    available += stored.amount();
                }
            }
            if (available < required.amount()) {
                return false;
            }
        }
        return true;
    }

    private static List<CelestialAssetRequirement> mergeIntoConstructionInventory(
        List<CelestialAssetRequirement> constructionInventory, ItemStack stack, long amount) {
        List<CelestialAssetRequirement> merged = new ArrayList<>(constructionInventory);
        for (int i = 0; i < merged.size(); i++) {
            CelestialAssetRequirement entry = merged.get(i);
            if (entry.matches(stack)) {
                merged.set(i, entry.withAmount(entry.amount() + amount));
                return merged;
            }
        }
        merged.add(new CelestialAssetRequirement(stack, amount));
        return merged;
    }

    private static List<CelestialAssetRequirement> defaultRequirements(CelestialAssetKind kind) {
        List<CelestialAssetRequirement> required = new ArrayList<>();
        switch (kind) {
            case STATION -> {}
            case AUTOMATED_STATION -> {
                required.add(new CelestialAssetRequirement(new net.minecraft.item.ItemStack(Blocks.stone), 64L));
                required.add(new CelestialAssetRequirement(new net.minecraft.item.ItemStack(Blocks.dirt), 64L));
            }
            case AUTOMATED_OUTPOST -> {
                required.add(new CelestialAssetRequirement(new net.minecraft.item.ItemStack(Blocks.stone), 64L));
                required.add(new CelestialAssetRequirement(new net.minecraft.item.ItemStack(Blocks.dirt), 64L));
            }
        }
        return required;
    }

    private static final class MutableBodyState {

        private final String celestialObjectId;
        private final List<CelestialManagedAsset> assets = new ArrayList<>();

        private MutableBodyState(String celestialObjectId) {
            this.celestialObjectId = celestialObjectId;
        }

        private CelestialBodyAssetState snapshot() {
            return new CelestialBodyAssetState(celestialObjectId, assets);
        }
    }
}
