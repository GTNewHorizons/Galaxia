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

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsConfiguration;
import com.gtnewhorizons.galaxia.registry.outpost.Station;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;

public abstract class CelestialAsset implements Buildable, IDistributedInventory {

    public enum Kind {

        STATION, // Not Implemented yet
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
    private final Map<Integer, List<ItemStack>> filters = new Int2ObjectArrayMap<>();

    public final LogisticsConfiguration logisticsConfig;

    public static long getItemAmount(CelestialAsset asset, ItemStackWrapper resource) {
        return asset.aggregatedItemAmounts()
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
        return isDirty() || !syncedPlayerIds.contains(playerId);
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

    public List<ItemStack> getFiltersFor(int i) {
        return filters.getOrDefault(i, List.of());
    }

    public void setFilters(int slot, List<ItemStack> filterList) {
        if (filterList == null || filterList.isEmpty()) {
            filters.remove(slot);
        } else {
            filters.put(slot, new ArrayList<>(filterList));
        }
        markDirty();
    }

    public void addFilter(int slot, ItemStack filter) {
        if (filter == null) return;
        filters.computeIfAbsent(slot, k -> new ArrayList<>())
            .add(filter.copy());
        markDirty();
    }

    public void removeFilter(int slot, ItemStack filter) {
        if (filter == null) return;
        List<ItemStack> list = filters.get(slot);
        if (list == null) return;
        list.removeIf(
            f -> f != null && f.getItem() == filter.getItem()
                && (!f.getHasSubtypes() || f.getItemDamage() == filter.getItemDamage())
                && (!f.hasTagCompound() || ItemStack.areItemStackTagsEqual(f, filter)));
        if (list.isEmpty()) {
            filters.remove(slot);
        }
        markDirty();
    }

    public void clearFilters(int slot) {
        filters.remove(slot);
        markDirty();
    }

    public Map<Integer, List<ItemStack>> filtersSnapshot() {
        return Collections.unmodifiableMap(filters);
    }

    public abstract boolean tryConsumeEnergy(long powerDraw);

    public abstract long getEnergyStored();

    public abstract Stream<ModuleInstance> forEachModule();

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
