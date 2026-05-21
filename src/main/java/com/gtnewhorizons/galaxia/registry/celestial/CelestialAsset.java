package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsConfiguration;
import com.gtnewhorizons.galaxia.registry.outpost.Station;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public abstract class CelestialAsset implements Buildable, IDistributedInventory {

    public final ID assetId;
    public final CelestialObjectId celestialObjectId;
    public final CelestialObjectId systemId;
    public final CelestialObjectId planetaryAnchorBodyId;
    public final Kind kind;
    public final Location location;

    private Status status;
    private final Map<ItemStack, Long> requiredResources;
    private Map<ItemStack, Long> constructionInventory;
    private String displayName;

    private int syncRevision;
    private final Set<UUID> syncedPlayerIds = new HashSet<>();
    private boolean dirty = true;

    private final Map<ItemStackWrapper, InventoryBounds> itemBounds = new LinkedHashMap<>();
    private final Map<FluidKey, InventoryBounds> fluidBounds = new LinkedHashMap<>();

    private final List<InventoryBoundDelta> dirtyInventoryBoundDeltas = new ArrayList<>();

    public final LogisticsConfiguration logisticsConfig;

    public static long getItemAmount(CelestialAsset asset, ItemStackWrapper resource) {
        return asset.aggregatedItems()
            .getOrDefault(resource, 0L);
    }

    public static CelestialAsset create(CelestialObjectId celestialObjectId, Kind kind, boolean operational) {
        return create(celestialObjectId, kind, operational ? Status.OPERATIONAL : Status.CONSTRUCTION_SITE);
    }

    public static CelestialAsset create(CelestialObjectId celestialObjectId, Kind kind, Status status) {
        return switch (kind) {
            case STATION -> new Station(ID.create(), celestialObjectId, status);
            case AUTOMATED_STATION, AUTOMATED_OUTPOST -> new AutomatedFacility(
                ID.create(),
                celestialObjectId,
                kind,
                status);
        };
    }

    public static CelestialAsset create(ID id, CelestialObjectId celestialObjectId, Kind kind, Status status) {
        return switch (kind) {
            case STATION -> new Station(id, celestialObjectId, status);
            case AUTOMATED_STATION, AUTOMATED_OUTPOST -> new AutomatedFacility(id, celestialObjectId, kind, status);
        };
    }

    protected CelestialAsset(ID assetId, CelestialObjectId celestialObjectId, Kind kind, Status status,
        Map<ItemStack, Long> constructionInventory) {

        this.assetId = assetId;
        this.status = status;
        this.celestialObjectId = celestialObjectId;
        this.systemId = GalaxiaCelestialAPI.findStar(celestialObjectId)
            .id();
        this.planetaryAnchorBodyId = GalaxiaCelestialAPI.findPlanetaryAnchor(celestialObjectId)
            .id();
        this.displayName = celestialObjectId.displayName() + ":" + kind.getDisplayName();
        this.kind = kind;
        this.location = Location.ofKind(kind);
        this.requiredResources = defaultRequirements(kind);
        this.constructionInventory = constructionInventory == null ? Collections.emptyMap() : constructionInventory;
        this.syncRevision = 0;
        this.logisticsConfig = new LogisticsConfiguration();
    }

    public abstract boolean tryConsumeEnergy(long powerDraw);

    public abstract long getEnergyStored();

    public abstract Stream<ModuleInstance> forEachModule();

    public Map<ItemStack, Long> requiredResources() {
        return requiredResources;
    }

    public Map<ItemStack, Long> constructionInventory() {
        return constructionInventory;
    }

    @Override
    public Map<ItemStack, Long> getRequiredResources() {
        return requiredResources;
    }

    @Override
    public Map<ItemStack, Long> getConstructionInventory() {
        return constructionInventory;
    }

    @Override
    public void clearConsumedResources() {
        constructionInventory.clear();
    }

    public void setConstructionInventory(Map<ItemStack, Long> constructionInventory) {
        this.constructionInventory = constructionInventory;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public void updateStatus(Status status) {
        this.status = status;
        markDirty();
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean hasStoredConstructionResources() {
        for (Long amount : constructionInventory.values()) {
            if (amount > 0) return true;
        }
        return false;
    }

    public boolean hasMiningCapability() {
        return false;
    }

    public boolean hasProductionCapability() {
        return false;
    }

    public WarningPriority warningPriority() {
        return WarningPriority.NONE;
    }

    public int getSyncRevision() {
        return syncRevision;
    }

    public void setSyncRevision(int rev) {
        this.syncRevision = Math.max(this.syncRevision, rev);
    }

    public void bumpSyncRevision() {
        syncRevision++;
    }

    public abstract void tick();

    public static Map<ItemStack, Long> defaultRequirements(CelestialAsset.Kind kind) {
        Map<ItemStack, Long> required = new LinkedHashMap<>();
        switch (kind) {
            case STATION -> {}
            case AUTOMATED_STATION -> {
                required.put(new ItemStack(Blocks.stone), 64L);
                required.put(new ItemStack(Blocks.dirt), 64L);
            }
            case AUTOMATED_OUTPOST -> {
                required.put(new ItemStack(Blocks.stone), 64L);
                required.put(new ItemStack(Blocks.dirt), 64L);
            }
        }
        return required;
    }

    public boolean needsFullSyncFor(UUID playerId) {
        return isDirty() || !syncedPlayerIds.contains(playerId) || !dirtyInventoryBoundDeltas.isEmpty();
    }

    public void markSyncedFor(UUID playerId) {
        syncedPlayerIds.add(playerId);
    }

    public void markDirty() {
        syncedPlayerIds.clear();
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clean() {
        dirty = false;
    }

    public abstract long updateContents(InventoryKey item, int delta, boolean sync);

    /// ----------------------------------------------------------------------------------
    /// Inventory Bounds
    /// ----------------------------------------------------------------------------------

    private <T extends InventoryKey> Map<T, InventoryBounds> getBoundsMap(T key) {
        return key instanceof ItemStackWrapper ? (Map<T, InventoryBounds>) itemBounds
            : (Map<T, InventoryBounds>) fluidBounds;
    }

    public boolean hasLowerBound(InventoryKey key) {
        return getBound(key).hasLow();
    }

    public boolean hasUpperBound(InventoryKey key) {
        return getBound(key).hasUpper();
    }

    public InventoryBounds getBound(InventoryKey key) {
        return getBoundsMap(key).getOrDefault(key, InventoryBounds.invalid());
    }

    public void setBound(InventoryKey key, long low, long upper) {
        getBoundsMap(key).put(key, new InventoryBounds(low, upper));
    }

    public void setBound(InventoryKey key, long amount, boolean low) {
        InventoryBounds bound = getBoundsMap(key).computeIfAbsent(key, k -> InventoryBounds.invalid());
        if (low) {
            bound.setLow(amount);
        } else {
            bound.setUppper(amount);
        }
    }

    public void clearBound(InventoryKey key) {
        getBoundsMap(key).remove(key);
    }

    public void clearBound(InventoryKey key, boolean low) {
        InventoryBounds bound = getBoundsMap(key).remove(key);
        if (low) {
            bound.removeLow();
        } else {
            bound.removeUpper();
        }
        if (!bound.isInvalid()) getBoundsMap(key).put(key, bound);
    }

    private <T extends InventoryKey> long getResourceAmount(T key) {
        return key instanceof ItemStackWrapper ? getItemAmount((ItemStackWrapper) key) : getFluidAmount((FluidKey) key);
    }

    public boolean isBelowUpper(InventoryKey key) {
        return getResourceAmount(key) < getBound(key).upperOrDefault();
    }

    public boolean isAboveLow(InventoryKey key) {
        return getResourceAmount(key) >= getBound(key).lowOrDefault();
    }

    public boolean isAboveLow(InventoryKey key, long amount) {
        return (getResourceAmount(key) - amount) >= getBound(key).lowOrDefault();
    }

    public boolean isInBounds(InventoryKey key) {
        return getBound(key).inBounds(getResourceAmount(key));
    }

    /// ----------------------------------------------------------------------------------
    /// Bound Snapshots & Loads (for persistence)
    /// ----------------------------------------------------------------------------------

    public <T extends InventoryKey> Map<T, InventoryBounds> getBounds(boolean items) {
        return items ? (Map<T, InventoryBounds>) itemBounds : (Map<T, InventoryBounds>) fluidBounds;
    }

    public void markInventoryBoundDelta(BoundKind kind, InventoryKey resource, boolean present, long amount) {
        if (kind == null || resource == null) return;
        dirtyInventoryBoundDeltas.add(new InventoryBoundDelta(kind, resource, present, amount));
        bumpSyncRevision();
        markDirty();
    }

    public record InventoryBoundDelta(BoundKind kind, InventoryKey resource, boolean present, long amount) {

        public String resourceKey() {
            return resource instanceof ItemStackWrapper item ? item.toKey()
                : ((FluidKey) resource).fluid()
                    .getName();
        }
    }

    public List<InventoryBoundDelta> drainDirtyInventoryBoundDeltas() {
        List<InventoryBoundDelta> result = new ArrayList<>(dirtyInventoryBoundDeltas);
        dirtyInventoryBoundDeltas.clear();
        return result;
    }

    /// ----------------------------------------------------------------------------------
    /// Clear all inventory state
    /// ----------------------------------------------------------------------------------

    public void clear() {
        itemBounds.clear();
        fluidBounds.clear();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (CelestialAsset) obj;
        return Objects.equals(this.assetId, that.assetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetId);
    }

    public enum Kind {

        STATION,
        AUTOMATED_STATION, // Not implemented yet
        AUTOMATED_OUTPOST,

        ;

        public String getDisplayName() {
            return StatCollector.translateToLocal(
                "galaxia.outpost.module.kind." + this.name()
                    .toLowerCase());
        }
    }

    public enum Location {

        ORBIT,
        SURFACE,

        ;

        public String getDisplayName() {
            return StatCollector.translateToLocal(
                "galaxia.outpost.module.location." + this.name()
                    .toLowerCase());
        }

        public static Location ofKind(Kind kind) {
            return switch (kind) {
                case STATION, AUTOMATED_STATION -> ORBIT;
                case AUTOMATED_OUTPOST -> SURFACE;
            };
        }
    }

    public record ID(UUID id) implements WithUUID {

        public static ID create() {
            return new ID(UUID.randomUUID());
        }

        public static ID from(String value) {
            if (value == null) return null;
            return new ID(UUID.fromString(value));
        }

        public static ID from(UUID value) {
            return value == null ? null : new ID(value);
        }

        public static ID from(ID id) {
            if (id == null) return null;
            return new ID(id.id());
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }
}
