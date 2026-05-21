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

/**
 * Server-authoritative asset store with a separate {@link #CLIENT} mirror instance.
 * <p>
 * Server-side callers keep using the static convenience methods, which delegate to {@link #SERVER}.
 * Client-side code should use {@link #CLIENT} directly or via {@link com.gtnewhorizons.galaxia.client.CelestialClient}.
 * This isolates server and client state in single-player, eliminating the shared-state bug.
 */
public final class CelestialAssetStore {

    // ── Static instances ──

    /** Server-authoritative instance. Static convenience methods delegate here. */
    public static final CelestialAssetStore SERVER = new CelestialAssetStore();

    /** Client-side mirror, populated by sync packets. Isolated from SERVER. */
    public static final CelestialAssetStore CLIENT = new CelestialAssetStore();

    // ── Instance fields ──

    private final Map<UUID, Map<CelestialObjectId, Set<CelestialAsset>>> stateByBody;
    private final Map<CelestialAsset.ID, UUID> teamById;
    private final Map<CelestialAsset.ID, CelestialAsset> byId;

    /** Package-private for testing; external code uses {@link #SERVER} or {@link #CLIENT}. */
    CelestialAssetStore() {
        this.stateByBody = new LinkedHashMap<>();
        this.teamById = new LinkedHashMap<>();
        this.byId = new LinkedHashMap<>();
    }

    // ── Static convenience wrappers (delegate to SERVER) ──

    public static void registerAsset(UUID teamId, CelestialAsset asset) {
        SERVER.registerAssetInternal(teamId, asset);
    }

    public static UUID getTeamId(CelestialAsset.ID assetId) {
        return SERVER.getTeamIdInternal(assetId);
    }

    public static List<CelestialAsset> getState(UUID teamId, CelestialObjectId celestialObjectId) {
        return SERVER.getStateInternal(teamId, celestialObjectId);
    }

    public static Set<CelestialAsset> getTeamAssets(UUID teamId, CelestialObjectId objectId) {
        return getTeamAssets(teamId).getOrDefault(objectId, Set.of());
    }

    public static Map<CelestialObjectId, Set<CelestialAsset>> getTeamAssets(UUID teamId) {
        return SERVER.getTeamAssetsInternal(teamId);
    }

    public static CelestialAsset findAsset(CelestialAsset.ID assetId) {
        return SERVER.findAssetInternal(assetId);
    }

    public static List<CelestialAsset> allAssets() {
        return SERVER.allAssetsInternal();
    }

    public static boolean disableAsset(CelestialAsset.ID assetId) {
        return SERVER.disableAssetInternal(assetId);
    }

    public static boolean enableAsset(CelestialAsset.ID assetId) {
        return SERVER.enableAssetInternal(assetId);
    }

    public static boolean destroyAsset(CelestialAsset.ID assetId) {
        return SERVER.destroyAssetInternal(assetId);
    }

    public static boolean cancelConstruction(CelestialAsset.ID assetId) {
        return SERVER.cancelConstructionInternal(assetId);
    }

    public static boolean startDeconstruction(CelestialAsset.ID assetId) {
        return SERVER.startDeconstructionInternal(assetId);
    }

    public static boolean completeConstruction(CelestialAsset.ID assetId) {
        return SERVER.completeConstructionInternal(assetId);
    }

    public static boolean renameAsset(CelestialAsset.ID assetId, String displayName) {
        return SERVER.renameAssetInternal(assetId, displayName);
    }

    public static boolean addToConstructionInventory(CelestialAsset.ID assetId, ItemStack stack, long amount) {
        return SERVER.addToConstructionInventoryInternal(assetId, stack, amount);
    }

    public static void clear() {
        SERVER.clearInternal();
    }

    public static boolean isOwnedBy(UUID teamId, CelestialAsset.ID id) {
        return SERVER.isOwnedByInternal(teamId, id);
    }

    // ── Instance methods ──

    public void registerAssetInternal(UUID teamId, CelestialAsset asset) {
        Map<CelestialObjectId, Set<CelestialAsset>> byBody = stateByBody
            .computeIfAbsent(teamId, k -> new LinkedHashMap<>());

        Set<CelestialAsset> celestialAssets = byBody.computeIfAbsent(asset.celestialObjectId, k -> new HashSet<>());

        celestialAssets.add(asset);
        teamById.put(asset.assetId, teamId);
        byId.put(asset.assetId, asset);
    }

    public UUID getTeamIdInternal(CelestialAsset.ID assetId) {
        return teamById.get(assetId);
    }

    public List<CelestialAsset> getStateInternal(UUID teamId, CelestialObjectId celestialObjectId) {
        Set<CelestialAsset> celestialAssets = stateByBody.getOrDefault(teamId, Collections.emptyMap())
            .getOrDefault(celestialObjectId, Collections.emptySet());
        return new ArrayList<>(celestialAssets);
    }

    public Map<CelestialObjectId, Set<CelestialAsset>> getTeamAssetsInternal(UUID teamId) {
        return stateByBody.getOrDefault(teamId, new LinkedHashMap<>());
    }

    public CelestialAsset findAssetInternal(CelestialAsset.ID assetId) {
        return byId.get(assetId);
    }

    public List<CelestialAsset> allAssetsInternal() {
        List<CelestialAsset> all = new ArrayList<>();
        for (Map<CelestialObjectId, Set<CelestialAsset>> teamAsset : stateByBody.values()) {
            for (Set<CelestialAsset> assets : teamAsset.values()) {
                all.addAll(assets);
            }
        }
        return all;
    }

    public boolean destroyAssetInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.get(assetId);
        if (asset == null) return false;

        UUID id = teamById.get(assetId);
        if (id == null) return false;

        Map<CelestialObjectId, Set<CelestialAsset>> map = stateByBody.get(id);
        if (map == null) {
            return false;
        }

        Set<CelestialAsset> list = map.get(asset.celestialObjectId);
        if (list == null) return false;

        list.remove(asset);
        byId.remove(assetId);
        teamById.remove(assetId);

        return true;
    }

    public boolean disableAssetInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.get(assetId);
        if (asset == null || asset.status() != Buildable.Status.OPERATIONAL) return false;
        asset.updateStatus(Buildable.Status.DISABLED);
        return true;
    }

    public boolean enableAssetInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.get(assetId);
        if (asset == null || asset.status() != Buildable.Status.DISABLED) return false;
        asset.updateStatus(Buildable.Status.OPERATIONAL);
        return true;
    }

    public boolean cancelConstructionInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.get(assetId);
        if (asset == null || asset.status() != Buildable.Status.CONSTRUCTION_SITE) {
            return false;
        }
        return destroyAssetInternal(assetId);
    }

    public boolean startDeconstructionInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.get(assetId);
        if (asset == null || asset.status() != Buildable.Status.CONSTRUCTION_SITE) {
            return false;
        }
        asset.updateStatus(CelestialAsset.Status.DECONSTRUCTION);
        return true;
    }

    public boolean completeConstructionInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.get(assetId);
        if (asset == null || asset.status() != Buildable.Status.CONSTRUCTION_SITE) {
            return false;
        }
        asset.completeConstruction();
        return true;
    }

    public boolean renameAssetInternal(CelestialAsset.ID assetId, String displayName) {
        if (displayName == null || displayName.trim()
            .isEmpty()) {
            return false;
        }

        CelestialAsset asset = byId.get(assetId);
        if (asset == null) return false;

        asset.setDisplayName(displayName.trim());
        return true;
    }

    public boolean addToConstructionInventoryInternal(CelestialAsset.ID assetId, ItemStack stack, long amount) {
        if (stack == null || amount <= 0) return false;

        CelestialAsset asset = byId.get(assetId);
        if (asset == null || asset.status() != Buildable.Status.CONSTRUCTION_SITE) {
            return false;
        }

        Map<ItemStack, Long> inventory = mergeIntoConstructionInventory(asset.constructionInventory(), stack, amount);

        asset.setConstructionInventory(inventory);

        if (asset.isConstructionSatisfied()) {
            asset.updateStatus(Buildable.Status.OPERATIONAL);
        }

        return true;
    }

    public void clearInternal() {
        stateByBody.clear();
        byId.clear();
        teamById.clear();
    }

    public boolean isOwnedByInternal(UUID teamId, CelestialAsset.ID id) {
        UUID owner = teamById.get(id);
        if (owner == null) return false;

        return owner.equals(teamId);
    }

    private static Map<ItemStack, Long> mergeIntoConstructionInventory(Map<ItemStack, Long> constructionInventory,
        ItemStack stack, long amount) {
        Map<ItemStack, Long> merged = new LinkedHashMap<>(constructionInventory);
        merged.merge(stack, amount, Long::sum);
        return merged;
    }
}
