package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsConfiguration;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

public class CelestialAsset implements Buildable {

    public final ID assetId;
    public final CelestialObjectKey celestialObjectKey;
    public final CelestialObjectKey systemKey;
    public final CelestialObjectKey planetaryAnchorBodyKey;
    public final Kind kind;
    public final Location location;

    private Status status;
    private final Map<ItemStack, Long> requiredResources;
    private String displayName;

    private boolean dirty = true;

    public final LogisticsConfiguration logisticsConfig;
    private final Map<UUID, Endpoint> endpoints = new LinkedHashMap<>();

    @Nonnull
    public Map<UUID, Endpoint> endpoints() {
        return Map.copyOf(endpoints);
    }

    @Nullable
    public Endpoint endpoint(@Nonnull UUID id) {
        return endpoints.get(id);
    }

    public boolean registerEndpoint(@Nonnull Endpoint endpoint) {
        validateEndpoint(endpoint);
        Endpoint existing = endpoints.get(endpoint.id());
        if (existing != null) {
            if (!existing.equals(endpoint))
                throw new IllegalArgumentException("An endpoint identity cannot be rebound");
            return false;
        }
        endpoints.put(endpoint.id(), endpoint);
        markDirty();
        return true;
    }

    public boolean removeEndpoint(@Nonnull UUID id) {
        if (endpoints.remove(id) == null) return false;
        markDirty();
        return true;
    }

    public void renameEndpoint(@Nonnull UUID id, @Nonnull String routingName) {
        Endpoint current = endpoints.get(id);
        if (current == null) throw new IllegalArgumentException("Unknown endpoint identity");
        Endpoint renamed = new Endpoint(
            id,
            current.kind(),
            routingName,
            current.physical(),
            current.serviceSiloId(),
            current.serviceSlots());
        if (current.equals(renamed)) return;
        endpoints.put(id, renamed);
        markDirty();
    }

    public void restoreEndpoints(@Nonnull Collection<Endpoint> restored) {
        var replacement = new LinkedHashMap<UUID, Endpoint>();
        for (Endpoint endpoint : restored) {
            validateEndpoint(endpoint);
            if (replacement.put(endpoint.id(), endpoint) != null)
                throw new IllegalArgumentException("Duplicate endpoint identity");
        }
        if (endpoints.equals(replacement)) return;
        endpoints.clear();
        endpoints.putAll(replacement);
        markDirty();
    }

    public void setEndpointServiceSilo(@Nonnull UUID id, @Nullable UUID siloId) {
        Endpoint current = endpoints.get(id);
        if (current == null || current.kind() != Endpoint.Kind.LANDING_PAD) {
            throw new IllegalArgumentException("Only a Landing Pad can select a service Silo");
        }
        Endpoint updated = new Endpoint(
            id,
            current.kind(),
            current.routingName(),
            current.physical(),
            siloId,
            current.serviceSlots());
        if (current.equals(updated)) return;
        endpoints.put(id, updated);
        markDirty();
    }

    public void setEndpointServiceSlots(@Nonnull UUID id, int serviceSlots) {
        Endpoint current = endpoints.get(id);
        if (current == null) throw new IllegalArgumentException("Unknown endpoint identity");
        var updated = new Endpoint(
            id,
            current.kind(),
            current.routingName(),
            current.physical(),
            current.serviceSiloId(),
            serviceSlots);
        if (updated.equals(current)) return;
        endpoints.put(id, updated);
        markDirty();
    }

    private void validateEndpoint(Endpoint endpoint) {
        if (kind == Kind.SATELLITE) throw new IllegalArgumentException("Satellites do not offer transport endpoints");
        if (kind == Kind.STATION && (endpoint.id()
            .equals(assetId.id()) || endpoint.physical() == null)) {
            throw new IllegalArgumentException("A Station endpoint needs its own physical controller identity");
        }
        if ((kind == Kind.AUTOMATED_STATION || kind == Kind.AUTOMATED_OUTPOST) && (!endpoint.id()
            .equals(assetId.id()) || endpoint.physical() != null)) {
            throw new IllegalArgumentException(
                "An Automated Facility offers one logical endpoint using its asset identity");
        }
        if (kind == Kind.TRANSPORT_SITE && (!endpoint.id()
            .equals(assetId.id()) || endpoint.physical() == null)) {
            throw new IllegalArgumentException("A standalone transport site uses its physical controller identity");
        }
    }

    public record PhysicalLocation(int dimension, @Nonnull BlockPos controller) {}

    public record Endpoint(@Nonnull UUID id, @Nonnull Kind kind, @Nonnull String routingName,
        @Nullable PhysicalLocation physical, @Nullable UUID serviceSiloId, int serviceSlots) {

        public Endpoint {
            if (routingName.isBlank() || serviceSlots < 0) throw new IllegalArgumentException(
                "Endpoint name must not be blank and service slots must be nonnegative");
        }

        public enum Kind {
            SILO,
            LANDING_PAD,
            DOCK
        }
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
            case TRANSPORT_SITE -> throw new IllegalArgumentException(
                "Transport sites require a native controller identity");
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
            case TRANSPORT_SITE -> new CelestialAsset(id, celestialObjectKey, kind, status);
        };
    }

    protected CelestialAsset(ID assetId, CelestialObjectKey celestialObjectKey, Kind kind, Status status) {

        this.assetId = assetId;
        this.status = status;
        this.celestialObjectKey = celestialObjectKey;
        CelestialObject star = GalaxiaCelestialAPI.findStar(celestialObjectKey);
        if (star == null) throw new IllegalStateException("Cannot resolve asset system for " + celestialObjectKey);
        this.systemKey = star.key();
        CelestialObject planetaryAnchor = GalaxiaCelestialAPI.findPlanetaryAnchor(celestialObjectKey);
        if (planetaryAnchor == null)
            throw new IllegalStateException("Cannot resolve asset planetary anchor for " + celestialObjectKey);
        this.planetaryAnchorBodyKey = planetaryAnchor.key();
        this.displayName = defaultName(celestialObjectKey, kind);
        this.kind = kind;
        this.location = Location.ofKind(kind);
        this.requiredResources = defaultRequirements(kind);
        this.logisticsConfig = new LogisticsConfiguration();
    }

    protected CelestialAsset(ID assetId, CelestialObjectId celestialObjectId, Kind kind, Status status) {

        this(assetId, CelestialObjectKey.registered(celestialObjectId), kind, status);
    }

    /** The short name a new asset gets: the body it is at and what it is, like "Mars Outpost" */
    public static String defaultName(CelestialObjectKey body, Kind kind) {
        return StatCollector.translateToLocalFormatted(
            "galaxia.asset.default_name." + kind.name()
                .toLowerCase(java.util.Locale.ROOT),
            displayName(body));
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

    public boolean tryConsumeEnergy(long powerDraw) {
        return false;
    }

    public long getEnergyStored() {
        return 0;
    }

    public Stream<ModuleInstance> forEachModule() {
        return Stream.empty();
    }

    public Map<ItemStack, Long> requiredResources() {
        return requiredResources;
    }

    @Override
    public Map<ItemStack, Long> getRequiredResources() {
        return requiredResources;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public void updateStatus(Status status) {
        if (this.status == status) return;
        this.status = status;
        markDirty();
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        if (Objects.equals(this.displayName, displayName)) return;
        this.displayName = displayName;
        markDirty();
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

    public void tick() {}

    public static Map<ItemStack, Long> defaultRequirements(CelestialAsset.Kind kind) {
        Map<ItemStack, Long> required = new LinkedHashMap<>();
        switch (kind) {
            case STATION, TRANSPORT_SITE -> {}
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
        AUTOMATED_STATION,
        AUTOMATED_OUTPOST,
        SATELLITE,
        TRANSPORT_SITE,

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
                case AUTOMATED_OUTPOST, TRANSPORT_SITE -> SURFACE;
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
