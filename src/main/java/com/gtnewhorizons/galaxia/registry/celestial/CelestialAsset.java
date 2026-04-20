package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedStation;
import com.gtnewhorizons.galaxia.registry.outpost.Station;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;

public abstract class CelestialAsset implements Buildable {

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
    public final Kind kind;
    public final Location location;

    private Status status;
    private final Map<ItemStack, Long> requiredResources;
    private Map<ItemStack, Long> constructionInventory;
    private String displayName;

    private int syncRevision;

    public static CelestialAsset create(CelestialObjectId celestialObjectId, Kind kind, Status status) {
        return switch (kind) {
            case STATION -> new Station(ID.create(), celestialObjectId, status);
            case AUTOMATED_STATION -> new AutomatedStation(ID.create(), celestialObjectId, status);
            case AUTOMATED_OUTPOST -> new AutomatedOutpost(ID.create(), celestialObjectId, status);
        };
    }

    public static CelestialAsset create(ID id, CelestialObjectId celestialObjectId, Kind kind, Status status) {
        return switch (kind) {
            case STATION -> new Station(id, celestialObjectId, status);
            case AUTOMATED_STATION -> new AutomatedStation(id, celestialObjectId, status);
            case AUTOMATED_OUTPOST -> new AutomatedOutpost(id, celestialObjectId, status);
        };
    }

    protected CelestialAsset(ID assetId, CelestialObjectId celestialObjectId, Kind kind, Status status,
        Map<ItemStack, Long> constructionInventory) {

        this.assetId = assetId;
        this.status = status;
        this.celestialObjectId = celestialObjectId;
        this.displayName = celestialObjectId.displayName() + ":" + kind.getDisplayName();
        this.kind = kind;
        this.location = Location.ofKind(kind);
        this.requiredResources = defaultRequirements(kind);
        this.constructionInventory = constructionInventory == null ? Collections.emptyMap() : constructionInventory;
        this.syncRevision = 0;
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

    public void bumpSyncRevision() {
        syncRevision++;
    }

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
