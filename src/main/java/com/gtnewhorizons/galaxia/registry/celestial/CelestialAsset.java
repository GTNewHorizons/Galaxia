package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
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
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

public abstract class CelestialAsset implements Buildable, IDistributedInventory {

    public final ID assetId;
    public final CelestialObjectKey celestialObjectKey;
    public final CelestialObjectKey systemKey;
    public final CelestialObjectKey planetaryAnchorBodyKey;
    public final Kind kind;
    public final Location location;

    private Status status;
    private final Map<ItemStack, Long> requiredResources;
    private Map<ItemStack, Long> constructionInventory;
    private String displayName;

    private int stateRevision;
    private boolean dirty = true;

    private final Map<ItemStackWrapper, InventoryBounds> itemBounds = new LinkedHashMap<>();
    private final Map<FluidKey, InventoryBounds> fluidBounds = new LinkedHashMap<>();

    public final LogisticsConfiguration logisticsConfig;

    public static long getItemAmount(CelestialAsset asset, ItemStackWrapper resource) {
        return asset.aggregatedItems()
            .getOrDefault(resource, 0L);
    }

    public static CelestialAsset create(CelestialObjectKey celestialObjectKey, Kind kind, boolean operational) {
        return create(celestialObjectKey, kind, operational ? Status.OPERATIONAL : Status.CONSTRUCTION_SITE);
    }

    public static CelestialAsset create(CelestialObjectKey celestialObjectKey, Kind kind, boolean operational,
        SatelliteKind satelliteKind) {
        return create(
            celestialObjectKey,
            kind,
            operational ? Status.OPERATIONAL : Status.CONSTRUCTION_SITE,
            satelliteKind);
    }

    public static CelestialAsset create(CelestialObjectKey celestialObjectKey, Kind kind, Status status) {
        return create(celestialObjectKey, kind, status, SatelliteKind.COMMUNICATION);
    }

    public static CelestialAsset create(CelestialObjectKey celestialObjectKey, Kind kind, Status status,
        SatelliteKind satelliteKind) {
        return switch (kind) {
            case STATION -> new Station(ID.create(), celestialObjectKey, status);
            case AUTOMATED_STATION, AUTOMATED_OUTPOST -> new AutomatedFacility(
                ID.create(),
                celestialObjectKey,
                kind,
                status);
            case SATELLITE -> {
                if (satelliteKind == null) throw new IllegalArgumentException("satelliteKind is required");
                yield new Satellite(ID.create(), celestialObjectKey, status, satelliteKind);
            }
        };
    }

    public static CelestialAsset create(ID id, CelestialObjectKey celestialObjectKey, Kind kind, Status status) {
        return create(id, celestialObjectKey, kind, status, SatelliteKind.COMMUNICATION);
    }

    public static CelestialAsset create(ID id, CelestialObjectKey celestialObjectKey, Kind kind, Status status,
        SatelliteKind satelliteKind) {
        return switch (kind) {
            case STATION -> new Station(id, celestialObjectKey, status);
            case AUTOMATED_STATION, AUTOMATED_OUTPOST -> new AutomatedFacility(id, celestialObjectKey, kind, status);
            case SATELLITE -> {
                if (satelliteKind == null) throw new IllegalArgumentException("satelliteKind is required");
                yield new Satellite(id, celestialObjectKey, status, satelliteKind);
            }
        };
    }

    protected CelestialAsset(ID assetId, CelestialObjectKey celestialObjectKey, Kind kind, Status status,
        Map<ItemStack, Long> constructionInventory) {

        this.assetId = assetId;
        this.status = status;
        this.celestialObjectKey = celestialObjectKey;
        this.systemKey = resolveStar(celestialObjectKey).key();
        this.planetaryAnchorBodyKey = resolvePlanetaryAnchor(celestialObjectKey).key();
        this.displayName = displayName(celestialObjectKey) + ":" + kind.getDisplayName();
        this.kind = kind;
        this.location = Location.ofKind(kind);
        this.requiredResources = defaultRequirements(kind);
        this.constructionInventory = constructionInventory == null ? Collections.emptyMap() : constructionInventory;
        this.stateRevision = 0;
        this.logisticsConfig = new LogisticsConfiguration();
    }

    protected CelestialAsset(ID assetId, CelestialObjectId celestialObjectId, Kind kind, Status status,
        Map<ItemStack, Long> constructionInventory) {

        this(assetId, CelestialObjectKey.registered(celestialObjectId), kind, status, constructionInventory);
    }

    private static CelestialObject resolveStar(CelestialObjectKey key) {
        CelestialObject star = GalaxiaCelestialAPI.findStar(key);
        if (star != null) return star;
        if (key != null && key.isMinorBody()) {
            star = GalaxiaCelestialAPI.findStar(
                CelestialObjectKey.registered(
                    key.minorBodyId()
                        .parentBodyId()));
        }
        if (star != null) return star;
        throw new IllegalStateException("Cannot resolve asset system for celestial object: " + key);
    }

    private static CelestialObject resolvePlanetaryAnchor(CelestialObjectKey key) {
        CelestialObject anchor = GalaxiaCelestialAPI.findPlanetaryAnchor(key);
        if (anchor != null) return anchor;
        if (key != null && key.isMinorBody()) {
            anchor = GalaxiaCelestialAPI.findPlanetaryAnchor(
                CelestialObjectKey.registered(
                    key.minorBodyId()
                        .parentBodyId()));
        }
        if (anchor != null) return anchor;
        throw new IllegalStateException("Cannot resolve asset planetary anchor for celestial object: " + key);
    }

    private static String displayName(CelestialObjectKey key) {
        if (key == null) return "";
        if (key.isRegistered()) return key.registeredBodyId()
            .displayName();
        return key.minorBodyId()
            .parentBodyId()
            .displayName() + " "
            + (key.minorBodyId()
                .index() + 1);
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
        if (this.status == status) return;
        this.status = status;
        markStateChanged();
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        if (Objects.equals(this.displayName, displayName)) return;
        this.displayName = displayName;
        markStateChanged();
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

    public int getStateRevision() {
        return stateRevision;
    }

    public void setStateRevision(int revision) {
        this.stateRevision = Math.max(this.stateRevision, revision);
    }

    public void bumpStateRevision() {
        stateRevision++;
        dirty = true;
    }

    protected void markStateChanged() {
        bumpStateRevision();
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
            case SATELLITE -> {}
        }
        return required;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clean() {
        dirty = false;
    }

    public abstract long updateContents(InventoryKey item, long delta, boolean sync);

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

    public boolean trySetBound(InventoryKey key, long amount, boolean low) {
        InventoryBounds current = getBound(key);
        long nextLow = low ? amount : current.low();
        long nextUpper = low ? current.upper() : amount;
        boolean hasNextLow = low || current.hasLow();
        boolean hasNextUpper = !low || current.hasUpper();
        if (hasNextLow && hasNextUpper && nextLow > nextUpper) return false;
        getBoundsMap(key).put(key, new InventoryBounds(nextLow, nextUpper));
        return true;
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
        markStateChanged();
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
        SATELLITE,

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
                case STATION, AUTOMATED_STATION, SATELLITE -> ORBIT;
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
