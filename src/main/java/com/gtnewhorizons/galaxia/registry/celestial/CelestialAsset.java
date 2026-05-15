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
import com.gtnewhorizons.galaxia.registry.interfaces.FluidKey;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.ResourceFilter;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsConfiguration;
import com.gtnewhorizons.galaxia.registry.outpost.Station;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

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
    private final Map<Integer, ResourceFilter<ItemStackWrapper>> itemFilters = new Int2ObjectArrayMap<>();
    private final Map<Integer, ResourceFilter<FluidKey>> fluidFilters = new Int2ObjectArrayMap<>();

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

    @Override
    public ResourceFilter<ItemStackWrapper> getItemFilter(int idx) {
        ResourceFilter<ItemStackWrapper> f = itemFilters.get(idx);
        return f != null ? f : ResourceFilter.forItems();
    }

    public void addFilter(int slot, ItemStack stack) {
        ItemStackWrapper key = ItemStackWrapper.of(stack);
        if (key == null) return;
        itemFilters.computeIfAbsent(slot, s -> ResourceFilter.forItems())
            .addIdentity(key);
        markDirty();
    }

    public void removeFilter(int slot, ItemStack stack) {
        ItemStackWrapper key = ItemStackWrapper.of(stack);
        if (key == null) return;
        ResourceFilter<ItemStackWrapper> f = itemFilters.get(slot);
        if (f != null) {
            f.remove(key);
            if (f.isEmpty()) itemFilters.remove(slot);
        }
        markDirty();
    }

    public void setFilters(int slot, List<ItemStack> stacks) {
        ResourceFilter<ItemStackWrapper> f = ResourceFilter.forItems();
        for (ItemStack stack : stacks) {
            ItemStackWrapper key = ItemStackWrapper.of(stack);
            if (key != null) f.addIdentity(key);
        }
        if (f.isEmpty()) {
            itemFilters.remove(slot);
        } else {
            itemFilters.put(slot, f);
        }
        markDirty();
    }

    public void setFilters(int slot, List<String> serializedKeys, boolean fromSerialized) {
        ResourceFilter<ItemStackWrapper> f = ResourceFilter.forItems();
        f.setAll(serializedKeys);
        if (f.isEmpty()) {
            itemFilters.remove(slot);
        } else {
            itemFilters.put(slot, f);
        }
        markDirty();
    }

    public void clearFilters(int slot) {
        itemFilters.remove(slot);
        markDirty();
    }

    public List<ItemStack> getFiltersFor(int slot) {
        ResourceFilter<ItemStackWrapper> f = itemFilters.get(slot);
        if (f == null) return List.of();
        return f.identities()
            .stream()
            .map(w -> w.toStack(1))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public Map<Integer, List<String>> filtersSnapshot() {
        Map<Integer, List<String>> result = new Int2ObjectArrayMap<>();
        for (Map.Entry<Integer, ResourceFilter<ItemStackWrapper>> e : itemFilters.entrySet()) {
            result.put(
                e.getKey(),
                e.getValue()
                    .serialize());
        }
        return result;
    }

    @Override
    public ResourceFilter<FluidKey> getFluidFilter(int idx) {
        ResourceFilter<FluidKey> f = fluidFilters.get(idx);
        return f != null ? f : ResourceFilter.forFluids();
    }

    public void addFluidFilter(int slot, FluidKey key) {
        if (key == null) return;
        fluidFilters.computeIfAbsent(slot, s -> ResourceFilter.forFluids())
            .addIdentity(key);
        markDirty();
    }

    public void removeFluidFilter(int slot, FluidKey key) {
        if (key == null) return;
        ResourceFilter<FluidKey> f = fluidFilters.get(slot);
        if (f != null) {
            f.remove(key);
            if (f.isEmpty()) fluidFilters.remove(slot);
        }
        markDirty();
    }

    public void setFluidFilters(int slot, List<FluidKey> keys) {
        ResourceFilter<FluidKey> f = ResourceFilter.forFluids();
        for (FluidKey key : keys) {
            if (key != null) f.addIdentity(key);
        }
        if (f.isEmpty()) {
            fluidFilters.remove(slot);
        } else {
            fluidFilters.put(slot, f);
        }
        markDirty();
    }

    public void clearFluidFilters(int slot) {
        fluidFilters.remove(slot);
        markDirty();
    }

    public List<FluidKey> getFluidFiltersFor(int slot) {
        ResourceFilter<FluidKey> f = fluidFilters.get(slot);
        return f != null ? f.identities() : List.of();
    }

    public Map<Integer, List<String>> fluidFiltersSnapshot() {
        Map<Integer, List<String>> result = new Int2ObjectArrayMap<>();
        for (Map.Entry<Integer, ResourceFilter<FluidKey>> e : fluidFilters.entrySet()) {
            result.put(
                e.getKey(),
                e.getValue()
                    .serialize());
        }
        return result;
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
