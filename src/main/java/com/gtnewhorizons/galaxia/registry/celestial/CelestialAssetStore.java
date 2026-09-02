package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

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
    private final Map<UUID, Map<CelestialObjectKey, Set<CelestialAsset.ID>>> bodyIndex;
    private final Map<CelestialObjectKey, Set<CelestialAsset.ID>> byBody;

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

    public static List<CelestialAsset> getState(UUID teamId, CelestialObjectKey celestialObjectKey) {
        return SERVER.getStateInternal(teamId, celestialObjectKey);
    }

    public static Set<CelestialAsset> getTeamAssets(UUID teamId, CelestialObjectKey objectKey) {
        return getTeamAssets(teamId).getOrDefault(objectKey, Set.of());
    }

    public static Map<CelestialObjectKey, Set<CelestialAsset>> getTeamAssets(UUID teamId) {
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

    public static Set<CelestialAsset.ID> getAssetsOnBody(CelestialObjectKey objectKey) {
        return SERVER.getAssetsOnBodyInternal(objectKey);
    }

    public static void removeTeam(UUID teamId) {
        SERVER.removeTeamInternal(teamId);
    }

    public static void transferTeamAssets(UUID fromTeamId, UUID toTeamId) {
        SERVER.transferTeamAssetsInternal(fromTeamId, toTeamId);
    }

    public static List<CelestialAsset> listAssetsInSystem(CelestialObjectKey systemKey, UUID teamId) {
        return SERVER.listAssetsInSystemInternal(systemKey, teamId);
    }
    // ── Instance methods ──

    public void registerAssetInternal(UUID teamId, CelestialAsset asset) {
        byId.put(asset.assetId, asset);
        teamById.put(asset.assetId, teamId);
        bodyIndex.computeIfAbsent(teamId, k -> new LinkedHashMap<>())
            .computeIfAbsent(asset.celestialObjectKey, k -> new HashSet<>())
            .add(asset.assetId);
        byBody.computeIfAbsent(asset.celestialObjectKey, k -> new HashSet<>()) // ← new
            .add(asset.assetId);
        if (this == SERVER) SatelliteNetworkService.refreshAssetEndpoints(teamId, asset);
    }

    public UUID getTeamIdInternal(CelestialAsset.ID assetId) {
        return teamById.get(assetId);
    }

    public List<CelestialAsset> getStateInternal(UUID teamId, CelestialObjectKey celestialObjectKey) {
        Set<CelestialAsset.ID> ids = bodyIndex.getOrDefault(teamId, Map.of())
            .getOrDefault(celestialObjectKey, Set.of());
        return resolveIds(ids);
    }

    public Map<CelestialObjectKey, Set<CelestialAsset>> getTeamAssetsInternal(UUID teamId) {
        Map<CelestialObjectKey, Set<CelestialAsset.ID>> teamIndex = bodyIndex.getOrDefault(teamId, Map.of());
        Map<CelestialObjectKey, Set<CelestialAsset>> result = new LinkedHashMap<>();
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
        if (this == SERVER) SatelliteNetworkService.unregisterAssetEndpoints(assetId);

        UUID teamId = teamById.remove(assetId);
        if (teamId == null) return false;

        Map<CelestialObjectKey, Set<CelestialAsset.ID>> teamIndex = bodyIndex.get(teamId);
        if (teamIndex != null) {
            Set<CelestialAsset.ID> ids = teamIndex.get(asset.celestialObjectKey);
            if (ids != null) {
                ids.remove(assetId);
                if (ids.isEmpty()) teamIndex.remove(asset.celestialObjectKey);
            }
            if (teamIndex.isEmpty()) bodyIndex.remove(teamId);
        }

        Set<CelestialAsset.ID> bodyIds = byBody.get(asset.celestialObjectKey); // ← new
        if (bodyIds != null) {
            bodyIds.remove(assetId);
            if (bodyIds.isEmpty()) byBody.remove(asset.celestialObjectKey);
        }
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
        if (!(asset instanceof AutomatedFacility) || asset.status() != Buildable.Status.CONSTRUCTION_SITE) return false;
        return destroyAssetInternal(assetId);
    }

    public boolean startDeconstructionInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.get(assetId);
        if (!(asset instanceof AutomatedFacility) || asset.status() != Buildable.Status.CONSTRUCTION_SITE) return false;
        asset.updateStatus(CelestialAsset.Status.DECONSTRUCTION);
        return true;
    }

    public boolean completeConstructionInternal(CelestialAsset.ID assetId) {
        CelestialAsset asset = byId.get(assetId);
        if (!(asset instanceof AutomatedFacility) || asset.status() != Buildable.Status.CONSTRUCTION_SITE) return false;
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
        if (!(asset instanceof AutomatedFacility facility) || asset.status() != Buildable.Status.CONSTRUCTION_SITE)
            return false;

        facility.setConstructionInventory(
            mergeIntoConstructionInventory(facility.getConstructionInventory(), stack, amount));

        if (facility.isConstructionSatisfied()) {
            facility.updateStatus(Buildable.Status.OPERATIONAL);
        }
        return true;
    }

    public void clearInternal() {
        byId.clear();
        teamById.clear();
        bodyIndex.clear();
        byBody.clear();
    }

    public boolean isOwnedByInternal(UUID teamId, CelestialAsset.ID id) {
        if (teamId == null) return false;
        UUID owner = teamById.get(id);
        return teamId.equals(owner);
    }

    public void removeTeamInternal(UUID teamId) {
        Map<CelestialObjectKey, Set<CelestialAsset.ID>> teamAssets = bodyIndex.remove(teamId);
        if (teamAssets == null) return;
        if (this == SERVER) SatelliteNetworkService.unregisterTeamEndpoints(teamId);
        for (Map.Entry<CelestialObjectKey, Set<CelestialAsset.ID>> ids : teamAssets.entrySet()) {
            for (CelestialAsset.ID id : ids.getValue()) {
                byId.remove(id);
                teamById.remove(id);
                byBody.get(ids.getKey())
                    .remove(id);
            }
        }
    }

    public Set<CelestialAsset.ID> getAssetsOnBodyInternal(CelestialObjectKey objectKey) {
        return byBody.getOrDefault(objectKey, Set.of());
    }

    public void transferTeamAssetsInternal(UUID fromTeamId, UUID toTeamId) {
        Map<CelestialObjectKey, Set<CelestialAsset.ID>> fromAssets = bodyIndex.remove(fromTeamId);
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
        if (this == SERVER) SatelliteNetworkService.rebuildDataEndpointsFromAssets();
    }

    public int satelliteCount(UUID teamId, CelestialObjectKey bodyKey, SatelliteKind kind) {
        validateSatelliteKey(teamId, bodyKey, kind);
        int count = 0;
        for (CelestialAsset asset : getStateInternal(teamId, bodyKey)) {
            if (asset instanceof Satellite satellite && satellite.satelliteKind() == kind) count++;
        }
        return count;
    }

    public long satelliteBandwidth(UUID teamId, CelestialObjectKey bodyKey) {
        return (long) (satelliteCount(teamId, bodyKey, SatelliteKind.COMMUNICATION)
            * SatelliteKind.COMMUNICATION.effectPerSatellite());
    }

    public double satelliteMiningSpeedBonus(UUID teamId, CelestialObjectKey bodyKey) {
        return satelliteCount(teamId, bodyKey, SatelliteKind.PROSPECTING)
            * SatelliteKind.PROSPECTING.effectPerSatellite();
    }

    public List<CelestialAsset.ID> addSatellites(UUID teamId, CelestialObjectKey bodyKey, SatelliteKind kind,
        int amount) {
        validateSatelliteKey(teamId, bodyKey, kind);
        if (amount < 0) throw new IllegalArgumentException("Satellite amount must be non-negative: " + amount);
        int current = satelliteCount(teamId, bodyKey, kind);
        if (Integer.MAX_VALUE - current < amount) {
            throw new IllegalArgumentException(
                "Satellite count overflow for team " + teamId + ", body " + bodyKey + ", kind " + kind);
        }
        return setSatelliteCount(teamId, bodyKey, kind, current + amount);
    }

    public List<CelestialAsset.ID> setSatelliteCount(UUID teamId, CelestialObjectKey bodyKey, SatelliteKind kind,
        int count) {
        validateSatelliteKey(teamId, bodyKey, kind);
        if (count < 0) throw new IllegalArgumentException("Satellite count must be non-negative: " + count);
        if (count == 0) {
            return deleteSatellites(teamId, bodyKey, kind);
        }
        int current = satelliteCount(teamId, bodyKey, kind);
        List<CelestialAsset.ID> changed = new ArrayList<>();
        for (int i = current; i < count; i++) {
            Satellite satellite = new Satellite(
                CelestialAsset.ID.create(),
                bodyKey,
                Buildable.Status.OPERATIONAL,
                kind);
            registerAssetInternal(teamId, satellite);
            changed.add(satellite.assetId);
        }
        for (int i = current; i > count; i--) {
            CelestialAsset.ID assetId = firstSatelliteId(teamId, bodyKey, kind);
            if (destroyAssetInternal(assetId)) changed.add(assetId);
        }
        return changed;
    }

    public List<CelestialAsset.ID> deleteSatellites(UUID teamId, CelestialObjectKey bodyKey, SatelliteKind kind) {
        validateSatelliteKey(teamId, bodyKey, kind);
        List<CelestialAsset.ID> removed = new ArrayList<>();
        while (satelliteCount(teamId, bodyKey, kind) > 0) {
            CelestialAsset.ID assetId = firstSatelliteId(teamId, bodyKey, kind);
            if (destroyAssetInternal(assetId)) removed.add(assetId);
        }
        return removed;
    }

    public List<CelestialAsset.ID> deleteSatelliteAmount(UUID teamId, CelestialObjectKey bodyKey, SatelliteKind kind,
        int amount) {
        validateSatelliteKey(teamId, bodyKey, kind);
        if (amount < 0) throw new IllegalArgumentException("Satellite amount must be non-negative: " + amount);
        List<CelestialAsset.ID> removed = new ArrayList<>();
        for (int i = 0; i < amount && satelliteCount(teamId, bodyKey, kind) > 0; i++) {
            CelestialAsset.ID assetId = firstSatelliteId(teamId, bodyKey, kind);
            if (destroyAssetInternal(assetId)) removed.add(assetId);
        }
        return removed;
    }

    private CelestialAsset.ID firstSatelliteId(UUID teamId, CelestialObjectKey bodyKey, SatelliteKind kind) {
        for (CelestialAsset asset : getStateInternal(teamId, bodyKey)) {
            if (asset instanceof Satellite satellite && satellite.satelliteKind() == kind) return asset.assetId;
        }
        throw new IllegalStateException(
            "No satellite asset for team " + teamId + ", body " + bodyKey + ", kind " + kind);
    }

    private static void validateSatelliteKey(UUID teamId, CelestialObjectKey bodyKey, SatelliteKind kind) {
        if (teamId == null) throw new IllegalArgumentException("Satellite team id is required");
        if (bodyKey == null) throw new IllegalArgumentException("Satellite body id is required");
        if (kind == null) throw new IllegalArgumentException("Satellite kind is required");
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
     * Returns every team-owned asset whose host body sits in the system rooted at {@code systemKey}.
     * Aggregates by walking descendants of the system root (a star) in the celestial hierarchy.
     * Order: stable DFS by hierarchy. Caller owns the returned list.
     */
    public List<CelestialAsset> listAssetsInSystemInternal(CelestialObjectKey systemKey, UUID teamId) {
        List<CelestialAsset> assets = new ArrayList<>();
        if (systemKey == null || teamId == null) return assets;
        CelestialObject systemRoot = CelestialRegistry.get(systemKey)
            .orElse(null);
        if (systemRoot == null) return assets;
        collectAssetsInSubtree(systemRoot, teamId, assets);
        return assets;
    }

    private void collectAssetsInSubtree(CelestialObject body, UUID teamId, List<CelestialAsset> out) {
        out.addAll(getState(teamId, body.key()));
        for (CelestialObject child : CelestialRegistry.getChildren(body.key())) {
            collectAssetsInSubtree(child, teamId, out);
        }
    }

}
