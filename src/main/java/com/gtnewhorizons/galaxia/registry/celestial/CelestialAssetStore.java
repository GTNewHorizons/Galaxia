package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;

public final class CelestialAssetStore {

    private static final Map<UUID, Map<CelestialObjectId, Set<CelestialAsset>>> STATE_BY_BODY = new LinkedHashMap<>();
    private static final Map<CelestialAsset.ID, UUID> TEAM_BY_ID = new LinkedHashMap<>();
    private static final Map<CelestialAsset.ID, CelestialAsset> BY_ID = new LinkedHashMap<>();

    private CelestialAssetStore() {}

    public static void add(UUID teamId, CelestialAsset asset) {
        Map<CelestialObjectId, Set<CelestialAsset>> byBody = STATE_BY_BODY
            .computeIfAbsent(teamId, k -> new LinkedHashMap<>());

        Set<CelestialAsset> celestialAssets = byBody.computeIfAbsent(asset.celestialObjectId, k -> new HashSet<>());

        celestialAssets.add(asset);
        TEAM_BY_ID.put(asset.assetId, teamId);
        BY_ID.put(asset.assetId, asset);
    }

    public static UUID getTeamId(CelestialAsset.ID assetId) {
        return TEAM_BY_ID.get(assetId);
    }

    public static List<CelestialAsset> getState(UUID teamId, CelestialObjectId celestialObjectId) {
        Set<CelestialAsset> celestialAssets = STATE_BY_BODY.getOrDefault(teamId, Collections.emptyMap())
            .getOrDefault(celestialObjectId, Collections.emptySet());
        return new ArrayList<>(celestialAssets);
    }

    public static Map<CelestialObjectId, Set<CelestialAsset>> getTeamAssets(UUID teamId) {
        return STATE_BY_BODY.getOrDefault(teamId, new LinkedHashMap<>());
    }

    public static CelestialAsset findAsset(CelestialAsset.ID assetId) {
        return BY_ID.get(assetId);
    }

    public static List<CelestialAsset> allAssets() {
        List<CelestialAsset> all = new ArrayList<>();
        for (Map<CelestialObjectId, Set<CelestialAsset>> teamAsset : STATE_BY_BODY.values()) {
            for (Set<CelestialAsset> assets : teamAsset.values()) {
                all.addAll(assets);
            }
        }
        return all;
    }

    public static CelestialAsset createAssetInConstruction(UUID teamId, CelestialObjectId celestialObjectId,
        String displayName, CelestialAsset.Kind kind) {

        CelestialAsset asset = CelestialAsset.create(celestialObjectId, kind, Buildable.Status.CONSTRUCTION_SITE);
        asset.setDisplayName(displayName);

        add(teamId, asset);
        return asset;
    }

    public static CelestialAsset createOperationalAsset(UUID teamId, CelestialObjectId celestialObjectId,
        String displayName, CelestialAsset.Kind kind) {

        CelestialAsset asset = CelestialAsset.create(celestialObjectId, kind, Buildable.Status.OPERATIONAL);
        asset.setDisplayName(displayName);

        add(teamId, asset);
        return asset;
    }

    public static boolean destroyAsset(CelestialAsset.ID assetId) {
        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null) return false;

        UUID id = TEAM_BY_ID.get(assetId);
        if (id == null) return false;

        Map<CelestialObjectId, Set<CelestialAsset>> map = STATE_BY_BODY.get(id);
        if (map == null) {
            return false;
        }

        Set<CelestialAsset> list = map.get(asset.celestialObjectId);
        if (list == null) return false;

        list.remove(asset);
        BY_ID.remove(assetId);
        TEAM_BY_ID.remove(assetId);

        return true;
    }

    public static boolean cancelConstruction(CelestialAsset.ID assetId) {
        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
            return false;
        }
        return destroyAsset(assetId);
    }

    public static boolean startDeconstruction(CelestialAsset.ID assetId) {
        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
            return false;
        }
        asset.updateStatus(CelestialAsset.Status.DECONSTRUCTION);
        return true;
    }

    public static boolean completeConstruction(CelestialAsset.ID assetId) {
        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
            return false;
        }
        asset.completeConstruction();
        return true;
    }

    public static boolean renameAsset(CelestialAsset.ID assetId, String displayName) {
        if (displayName == null || displayName.trim()
            .isEmpty()) {
            return false;
        }

        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null) return false;

        asset.setDisplayName(displayName.trim());
        return true;
    }

    public static boolean addToConstructionInventory(CelestialAsset.ID assetId, ItemStack stack, long amount) {
        if (stack == null || amount <= 0) return false;

        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
            return false;
        }

        Map<ItemStack, Long> inventory = mergeIntoConstructionInventory(asset.constructionInventory(), stack, amount);

        asset.setConstructionInventory(inventory);

        if (asset.isConstructionSatisfied()) {
            asset.updateStatus(CelestialAsset.Status.OPERATIONAL);
        }

        return true;
    }

    public static void clear() {
        STATE_BY_BODY.clear();
        BY_ID.clear();
    }

    private static Map<ItemStack, Long> mergeIntoConstructionInventory(Map<ItemStack, Long> constructionInventory,
        ItemStack stack, long amount) {
        Map<ItemStack, Long> merged = new LinkedHashMap<>(constructionInventory);
        merged.merge(stack, amount, Long::sum);
        return merged;
    }

    public static boolean isOwnedBy(UUID teamId, CelestialAsset.ID id) {
        UUID owner = TEAM_BY_ID.get(id);
        if (owner == null) return false;

        return owner.equals(teamId);
    }
}
