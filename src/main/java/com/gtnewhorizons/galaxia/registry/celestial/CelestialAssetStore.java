package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;

/**
 * Server-authoritative asset store with a separate {@link #CLIENT} mirror instance.
 * <p>
 * Server-side callers keep using the static convenience methods, which delegate to {@link #SERVER}.
 * Client-side code should use {@link #CLIENT} directly or via {@link com.gtnewhorizons.galaxia.client.CelestialClient}.
 * This isolates server and client state in single-player, eliminating the shared-state bug.
 */
public final class CelestialAssetStore {

    // ── Static instances ──

    public static final CelestialAssetStore SERVER = new CelestialAssetStore();
    public static final CelestialAssetStore CLIENT = new CelestialAssetStore();

    // ── Instance fields ──

    private final Map<CelestialAsset.ID, CelestialAsset> byId;

    /** indexes for fast lookups **/
    private final Map<CelestialAsset.ID, UUID> teamById;
    private final Map<UUID, Map<CelestialObjectId, Set<CelestialAsset.ID>>> bodyIndex;
    private final Map<CelestialObjectId, Set<CelestialAsset.ID>> byBody;

    CelestialAssetStore() {
        this.byId = new LinkedHashMap<>();
        this.teamById = new LinkedHashMap<>();
        this.bodyIndex = new LinkedHashMap<>();
        this.byBody = new LinkedHashMap<>();
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

    public static Set<CelestialAsset.ID> getAssetsOnBody(CelestialObjectId objectId) {
        return SERVER.getAssetsOnBodyInternal(objectId);
    }

    public static void removeTeam(UUID teamId) {
        SERVER.removeTeamInternal(teamId);
    }

    public static void transferTeamAssets(UUID fromTeamId, UUID toTeamId) {
        SERVER.transferTeamAssetsInternal(fromTeamId, toTeamId);
    }

    public static List<CelestialAsset> listAssetsInSystem(CelestialObjectId systemId, UUID teamId) {
        return SERVER.listAssetsInSystemInternal(systemId, teamId);
    }
    // ── Instance methods ──

    public void registerAssetInternal(UUID teamId, CelestialAsset asset) {
        byId.put(asset.assetId, asset);
        teamById.put(asset.assetId, teamId);
        bodyIndex.computeIfAbsent(teamId, k -> new LinkedHashMap<>())
            .computeIfAbsent(asset.celestialObjectId, k -> new HashSet<>())
            .add(asset.assetId);
        byBody.computeIfAbsent(asset.celestialObjectId, k -> new HashSet<>()) // ← new
            .add(asset.assetId);
    }

    public UUID getTeamIdInternal(CelestialAsset.ID assetId) {
        return teamById.get(assetId);
    }

    public List<CelestialAsset> getStateInternal(UUID teamId, CelestialObjectId celestialObjectId) {
        Set<CelestialAsset.ID> ids = bodyIndex.getOrDefault(teamId, Map.of())
            .getOrDefault(celestialObjectId, Set.of());
        return resolveIds(ids);
    }

    public Map<CelestialObjectId, Set<CelestialAsset>> getTeamAssetsInternal(UUID teamId) {
        Map<CelestialObjectId, Set<CelestialAsset.ID>> teamIndex = bodyIndex.getOrDefault(teamId, Map.of());
        Map<CelestialObjectId, Set<CelestialAsset>> result = new LinkedHashMap<>();
        teamIndex.forEach((body, ids) -> result.put(body, resolveIdsToSet(ids)));
        return result;
    }

    public CelestialAsset findAssetInternal(CelestialAsset.ID assetId) {
        return byId.get(assetId);
    }

    public List<CelestialAsset> allAssetsInternal() {
        return new ArrayList<>(byId.values());
    }

    public boolean destroyAssetInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.remove(assetId);
        if (asset == null) return false;

        UUID teamId = teamById.remove(assetId);
        if (teamId == null) return false;

        Map<CelestialObjectId, Set<CelestialAsset.ID>> teamIndex = bodyIndex.get(teamId);
        if (teamIndex != null) {
            Set<CelestialAsset.ID> ids = teamIndex.get(asset.celestialObjectId);
            if (ids != null) {
                ids.remove(assetId);
                if (ids.isEmpty()) teamIndex.remove(asset.celestialObjectId);
            }
            if (teamIndex.isEmpty()) bodyIndex.remove(teamId);
        }

        Set<CelestialAsset.ID> bodyIds = byBody.get(asset.celestialObjectId); // ← new
        if (bodyIds != null) {
            bodyIds.remove(assetId);
            if (bodyIds.isEmpty()) byBody.remove(asset.celestialObjectId);
        }
        LogisticStore.removeSignalsFor(assetId);

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
        if (asset == null || asset.status() != Buildable.Status.CONSTRUCTION_SITE) return false;
        return destroyAssetInternal(assetId);
    }

    public boolean startDeconstructionInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.get(assetId);
        if (asset == null || asset.status() != Buildable.Status.CONSTRUCTION_SITE) return false;
        asset.updateStatus(CelestialAsset.Status.DECONSTRUCTION);
        return true;
    }

    public boolean completeConstructionInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.get(assetId);
        if (asset == null || asset.status() != Buildable.Status.CONSTRUCTION_SITE) return false;
        asset.completeConstruction();
        return true;
    }

    public boolean renameAssetInternal(CelestialAsset.ID assetId, String displayName) {
        if (displayName == null || displayName.isBlank()) return false;
        CelestialAsset asset = byId.get(assetId);
        if (asset == null) return false;
        asset.setDisplayName(displayName.trim());
        return true;
    }

    public boolean addToConstructionInventoryInternal(CelestialAsset.ID assetId, ItemStack stack, long amount) {
        if (stack == null || amount <= 0) return false;
        CelestialAsset asset = byId.get(assetId);
        if (asset == null || asset.status() != Buildable.Status.CONSTRUCTION_SITE) return false;

        asset.setConstructionInventory(mergeIntoConstructionInventory(asset.constructionInventory(), stack, amount));

        if (asset.isConstructionSatisfied()) {
            asset.updateStatus(Buildable.Status.OPERATIONAL);
        }
        return true;
    }

    public void clearInternal() {
        byId.clear();
        teamById.clear();
    }

    public boolean isOwnedByInternal(UUID teamId, CelestialAsset.ID id) {
        if (teamId == null) return false;
        UUID owner = teamById.get(id);
        return teamId.equals(owner);
    }

    public void removeTeamInternal(UUID teamId) {
        Map<CelestialObjectId, Set<CelestialAsset.ID>> teamAssets = bodyIndex.remove(teamId);
        if (teamAssets == null) return;
        for (Map.Entry<CelestialObjectId, Set<CelestialAsset.ID>> ids : teamAssets.entrySet()) {
            for (CelestialAsset.ID id : ids.getValue()) {
                byId.remove(id);
                teamById.remove(id);
                byBody.get(ids.getKey())
                    .remove(id);
            }
        }
    }

    public Set<CelestialAsset.ID> getAssetsOnBodyInternal(CelestialObjectId objectId) {
        return byBody.getOrDefault(objectId, Set.of());
    }

    public void transferTeamAssetsInternal(UUID fromTeamId, UUID toTeamId) {
        Map<CelestialObjectId, Set<CelestialAsset.ID>> fromAssets = bodyIndex.remove(fromTeamId);
        if (fromAssets == null || fromAssets.isEmpty()) return;

        for (Set<CelestialAsset.ID> ids : fromAssets.values()) {
            for (CelestialAsset.ID id : ids) {
                teamById.put(id, toTeamId);
            }
        }

        bodyIndex.merge(toTeamId, fromAssets, (existing, incoming) -> {
            for (var entry : incoming.entrySet()) {
                existing.merge(entry.getKey(), entry.getValue(), (existingSet, incomingSet) -> {
                    existingSet.addAll(incomingSet);
                    return existingSet;
                });
            }
            return existing;
        });
    }

    private List<CelestialAsset> resolveIds(Set<CelestialAsset.ID> ids) {
        List<CelestialAsset> result = new ArrayList<>(ids.size());
        for (CelestialAsset.ID id : ids) {
            CelestialAsset a = byId.get(id);
            if (a != null) result.add(a);
        }
        return result;
    }

    private Set<CelestialAsset> resolveIdsToSet(Set<CelestialAsset.ID> ids) {
        Set<CelestialAsset> result = new HashSet<>(ids.size());
        for (CelestialAsset.ID id : ids) {
            CelestialAsset a = byId.get(id);
            if (a != null) result.add(a);
        }
        return result;
    }

    private static Map<ItemStack, Long> mergeIntoConstructionInventory(Map<ItemStack, Long> constructionInventory,
        ItemStack stack, long amount) {
        Map<ItemStack, Long> merged = new LinkedHashMap<>(constructionInventory);
        merged.merge(stack, amount, Long::sum);
        return merged;
    }

    /**
     * Returns every team-owned asset whose host body sits in the system rooted at {@code systemId}.
     * Aggregates by walking descendants of the system root (a star) in the celestial hierarchy.
     * Order: stable DFS by hierarchy. Caller owns the returned list.
     */
    public List<CelestialAsset> listAssetsInSystemInternal(CelestialObjectId systemId, UUID teamId) {
        List<CelestialAsset> assets = new ArrayList<>();
        if (systemId == null || teamId == null) return assets;
        CelestialObject systemRoot = CelestialRegistry.findById(systemId)
            .orElse(null);
        if (systemRoot == null) return assets;
        collectAssetsInSubtree(systemRoot, teamId, assets);
        return assets;
    }

    private void collectAssetsInSubtree(CelestialObject body, UUID teamId, List<CelestialAsset> out) {
        out.addAll(getState(teamId, body.id()));
        for (CelestialObject child : GalaxiaCelestialAPI.getChildren(body)) {
            collectAssetsInSubtree(child, teamId, out);
        }
    }

}
