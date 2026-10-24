package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData;
import com.gtnewhorizons.galaxia.core.config.ConfigRocket;
import com.gtnewhorizons.galaxia.core.state.InventoryKeyState;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.gt5.RocketContent;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.PoweredCandidate;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.WorkBill;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.IRocketControllerTE.TransportEndpoint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityTransportPlatform;

import gregtech.api.util.GTUtility;

/** One concrete vehicle, independent of its physical entity and module holders. */
public final class Rocket {

    private static final double PHYSICAL_MANEUVER_HEIGHT = 64;

    private final UUID id;
    private UUID teamId;
    private VehicleSchedule.Place location;
    private UUID assemblySiloId;
    private final List<BuildSlot> buildSlots;
    private final Map<UUID, Module> modules = new LinkedHashMap<>();
    private final Map<UUID, ModuleWork> moduleWork = new LinkedHashMap<>();
    private Status status = Status.CONSTRUCTING;
    private VehicleSchedule schedule = new VehicleSchedule(List.of(), VehicleSchedule.Mode.RUN_ONCE);
    private long revision;
    @Nullable
    private Service service;
    private long projectionGeneration;
    @Nullable
    private Projection projection;
    @Nullable
    private Execution execution;
    @Nullable
    private DeliveryJob delivery;
    private final Set<UUID> passengers = new HashSet<>();
    private double parkingAngleRadians;
    @Nullable
    private PhysicalPosition physicalPosition;

    private Rocket(UUID id, UUID teamId, CelestialObjectKey bodyKey, UUID assemblySiloId, List<BuildSlot> buildSlots) {
        this.id = id;
        this.teamId = teamId;
        this.location = new VehicleSchedule.Place(
            bodyKey,
            VehicleSchedule.Context.SURFACE,
            new VehicleSchedule.Exact(
                assemblySiloId,
                com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind.SILO));
        this.assemblySiloId = assemblySiloId;
        this.buildSlots = List.copyOf(buildSlots);
    }

    /** One saved world's vehicle identities. Team views are derived from ownership. */
    public static final class Registry {

        private final Map<UUID, Rocket> vehicles = new LinkedHashMap<>();

        public void register(@Nonnull Rocket rocket) {
            for (UUID passenger : rocket.passengers) {
                Rocket previous = passengerVehicle(passenger);
                if (previous != null && previous != rocket)
                    throw new IllegalStateException("Passenger belongs to more than one vehicle");
            }
            Rocket previous = vehicles.putIfAbsent(rocket.id, rocket);
            if (previous != null && previous != rocket) throw new IllegalStateException("Duplicate Rocket identity");
        }

        @Nullable
        public Rocket find(@Nonnull UUID vehicleId) {
            return vehicles.get(vehicleId);
        }

        public List<Rocket> all() {
            return List.copyOf(vehicles.values());
        }

        public List<Rocket> forTeam(@Nonnull UUID teamId) {
            return vehicles.values()
                .stream()
                .filter(rocket -> rocket.teamId.equals(teamId))
                .toList();
        }

        @Nullable
        public Rocket passengerVehicle(@Nonnull UUID playerId) {
            return vehicles.values()
                .stream()
                .filter(vehicle -> vehicle.passengers.contains(playerId))
                .findFirst()
                .orElse(null);
        }

        public boolean board(@Nonnull UUID vehicleId, @Nonnull UUID playerId) {
            Rocket vehicle = find(vehicleId);
            if (vehicle == null || vehicle.status != Status.PHYSICAL || vehicle.isRunning()) return false;
            Rocket previous = passengerVehicle(playerId);
            if (previous != null && previous != vehicle && !previous.disembark(playerId)) return false;
            return vehicle.board(playerId);
        }

        /** A scan never reserves space. Acceptance checks the exact displayed footprint across all teams. */
        public boolean landingSiteAvailable(UUID departingVehicle, VehicleSchedule.Place place) {
            return landingSiteAvailable(departingVehicle, place, false);
        }

        public boolean landingSiteReservedFor(UUID vehicleId, VehicleSchedule.Place place) {
            var rocket = find(vehicleId);
            return rocket != null && rocket.isRunning()
                && rocket.execution.overrideProgram != null
                && rocket.execution.origin == ProgramOrigin.MANUAL_LANDING
                && place.equals(
                    rocket.execution.overrideProgram.visits()
                        .getLast()
                        .place())
                && landingSiteAvailable(vehicleId, place, true);
        }

        private boolean landingSiteAvailable(UUID departingVehicle, VehicleSchedule.Place place, boolean arriving) {
            var site = place.surfaceSite();
            if (site == null) return false;
            var bounds = site.footprint()
                .bounds(site.position());
            for (var other : vehicles.values()) {
                if (other.status == Status.LOST) continue;
                if (!other.id.equals(departingVehicle) && other.isRunning()
                    && other.execution.overrideProgram != null
                    && other.execution.origin == ProgramOrigin.MANUAL_LANDING) {
                    var pending = other.execution.overrideProgram.visits()
                        .getLast()
                        .place();
                    if (pending.body()
                        .equals(place.body())
                        && pending.surfaceSite()
                            .dimension() == site.dimension()
                        && pending.surfaceSite()
                            .footprint()
                            .bounds(
                                pending.surfaceSite()
                                    .position())
                            .intersectsWith(bounds))
                        return false;
                }
                if (!(arriving && other.id.equals(departingVehicle)) && other.allowsProjection()
                    && other.physicalPosition != null
                    && other.projectionPlace()
                        .body()
                        .equals(place.body())
                    && other.physicalPosition.dimension == site.dimension()
                    && other.footprint()
                        .bounds(other.physicalPosition)
                        .intersectsWith(bounds))
                    return false;
            }
            return true;
        }

        public CommandResult acceptManualLanding(UUID vehicleId, UUID actorTeam, long expectedRevision,
            VehicleSchedule.Place place, VehicleSchedule.RouteSettings settings) {
            var rocket = find(vehicleId);
            if (rocket == null || !rocket.teamId.equals(actorTeam)) return CommandResult.FORBIDDEN;
            if (rocket.revision != expectedRevision) return CommandResult.STALE;
            if (!landingSiteAvailable(vehicleId, place)) return CommandResult.BLOCKED;
            return rocket.acceptManualLanding(actorTeam, expectedRevision, place, settings);
        }

        public void mergeTeams(@Nonnull UUID consumedTeam, @Nonnull UUID survivingTeam) {
            for (Rocket rocket : vehicles.values()) {
                if (rocket.teamId.equals(consumedTeam)) rocket.teamId = survivingTeam;
            }
        }

        public void clear() {
            vehicles.clear();
        }

        @Nonnull
        public NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            var encoded = new NBTTagList();
            for (Rocket rocket : vehicles.values()) encoded.appendTag(rocket.serializeNBT());
            out.setTag("vehicles", encoded);
            return out;
        }

        public void restoreNBT(@Nonnull NBTTagCompound encoded, @Nonnull RocketPartRegistry definitions) {
            var in = new NbtReader(encoded, "rockets");
            var entries = in.compounds("vehicles");
            var restored = new LinkedHashMap<UUID, Rocket>();
            var passengers = new HashSet<UUID>();
            for (int i = 0; i < entries.tagCount(); i++) {
                Rocket rocket = Rocket.deserializeNBT(entries.getCompoundTagAt(i), definitions);
                for (UUID passenger : rocket.passengers) {
                    if (!passengers.add(passenger))
                        throw new IllegalStateException("Passenger belongs to more than one vehicle");
                }
                if (restored.put(rocket.id, rocket) != null)
                    throw new IllegalStateException("Duplicate Rocket identity");
            }
            vehicles.clear();
            vehicles.putAll(restored);
        }
    }

    @Nonnull
    public static Rocket construct(@Nonnull UUID teamId, @Nonnull CelestialObjectKey bodyKey,
        @Nonnull UUID assemblySiloId, @Nonnull RocketBlueprint design) {
        if (design.isEmpty()) throw new IllegalArgumentException("Rocket construction requires a non-empty design");
        List<BuildSlot> slots = new ArrayList<>();
        for (RocketPartInstance part : design.getParts()) {
            slots.add(new BuildSlot(UUID.randomUUID(), UUID.randomUUID(), part));
        }
        return new Rocket(UUID.randomUUID(), teamId, bodyKey, assemblySiloId, slots);
    }

    public static Rocket construct(@Nonnull UUID teamId, @Nonnull CelestialAssetStore.EndpointRef endpoint,
        @Nonnull RocketBlueprint design) {
        if (endpoint.endpoint()
            .kind() != CelestialAsset.Endpoint.Kind.SILO
            || !teamId.equals(CelestialAssetStore.getTeamId(endpoint.asset().assetId)))
            throw new IllegalArgumentException("Construction requires an owned Silo endpoint");
        Rocket rocket = construct(
            teamId,
            endpoint.asset().celestialObjectKey,
            endpoint.endpoint()
                .id(),
            design);
        rocket.location = VehicleSchedule.Place.at(endpoint);
        return rocket;
    }

    public UUID id() {
        return id;
    }

    public UUID teamId() {
        return teamId;
    }

    public CelestialObjectKey bodyKey() {
        return location.body();
    }

    public VehicleSchedule.Place location() {
        return location;
    }

    @Nullable
    public UUID currentEndpointId() {
        return status != Status.IN_FLIGHT && location.target() instanceof VehicleSchedule.Exact exact
            ? exact.endpointId()
            : null;
    }

    @Nullable
    public UUID reservedArrivalEndpointId() {
        return execution == null || execution.journey == null ? null : execution.arrivalEndpointId;
    }

    public boolean isRunning() {
        return execution != null && !execution.completed;
    }

    public Set<UUID> passengers() {
        return Set.copyOf(passengers);
    }

    public boolean restrictsPassengers() {
        if (status == Status.IN_FLIGHT || status == Status.DISASSEMBLED || status == Status.INCOMPLETE) return true;
        UUID endpointId = currentEndpointId();
        var endpoint = endpointId == null ? null : CelestialAssetStore.findEndpoint(endpointId);
        if (endpoint != null) return endpoint.endpoint()
            .physical() == null;
        if (location.context() == VehicleSchedule.Context.ORBIT) return true;
        var dimension = GalaxiaCelestialAPI.get(bodyKey())
            .map(body -> body.dimensionEnum())
            .orElse(null);
        return dimension == null || physicalPosition == null || physicalPosition.dimension != dimension.getId();
    }

    public TransitView transitView() {
        var schedule = program();
        PoweredCandidate journey = execution == null ? null : execution.journey;
        long elapsed = 0;
        long total = 0;
        if (journey != null) {
            for (int i = 0; i < journey.obligations()
                .size(); i++) {
                var phase = journey.obligations()
                    .get(i);
                long ticks = Math.max(
                    1,
                    (long) Math
                        .ceil((phase.endEpochOsu() - phase.startEpochOsu()) / OrbitalTransferPlanner.OSU_PER_TICK));
                total += ticks;
                if (i < execution.phaseIndex) elapsed += ticks;
            }
            elapsed += execution.phaseTicks;
        }
        var destination = journey == null ? location
            : schedule.visits()
                .get(execution.destinationIndex)
                .place();
        return new TransitView(
            id,
            location,
            destination,
            journey,
            elapsed,
            Math.max(0, total - elapsed),
            restrictsPassengers(),
            execution == null || execution.problem == null ? null : execution.problem.key);
    }

    public record TransitView(@Nonnull UUID vehicleId, @Nonnull VehicleSchedule.Place source,
        @Nonnull VehicleSchedule.Place destination, @Nullable PoweredCandidate journey, long elapsedTicks,
        long remainingTicks, boolean restricted, @Nullable String problemKey) {

        public TransitView {
            if (elapsedTicks < 0 || remainingTicks < 0) throw new IllegalArgumentException("Invalid journey clock");
        }

        @Nullable
        public OrbitalMechanics.ManeuverObligation currentPhase() {
            if (journey == null) return null;
            long elapsed = elapsedTicks;
            for (var phase : journey.obligations()) {
                long ticks = durationTicks(phase);
                if (elapsed < ticks) return phase;
                elapsed -= ticks;
            }
            return null;
        }

        public double phaseProgress() {
            if (journey == null) return 0;
            long elapsed = elapsedTicks;
            for (var phase : journey.obligations()) {
                long ticks = durationTicks(phase);
                if (elapsed < ticks) return (double) elapsed / ticks;
                elapsed -= ticks;
            }
            return 1;
        }

        private static long durationTicks(OrbitalMechanics.ManeuverObligation phase) {
            return Math.max(
                1,
                (long) Math.ceil((phase.endEpochOsu() - phase.startEpochOsu()) / OrbitalTransferPlanner.OSU_PER_TICK));
        }

        public NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("vehicle", vehicleId.toString());
            out.setTag("source", source.serializeNBT());
            out.setTag("destination", destination.serializeNBT());
            if (journey != null) out.setTag("journey", journey.serializeNBT());
            out.setLong("elapsed", elapsedTicks);
            out.setLong("remaining", remainingTicks);
            out.setBoolean("restricted", restricted);
            if (problemKey != null) out.setString("problem", problemKey);
            return out;
        }

        public static TransitView deserializeNBT(NBTTagCompound encoded) {
            var in = new NbtReader(encoded, "rocketTransit");
            return new TransitView(
                UUID.fromString(in.string("vehicle")),
                VehicleSchedule.Place.deserializeNBT(
                    in.compound("source")
                        .tag()),
                VehicleSchedule.Place.deserializeNBT(
                    in.compound("destination")
                        .tag()),
                in.tag()
                    .hasKey("journey")
                        ? PoweredCandidate.deserializeNBT(
                            in.compound("journey")
                                .tag())
                        : null,
                in.longValue("elapsed"),
                in.longValue("remaining"),
                in.bool("restricted"),
                in.tag()
                    .hasKey("problem") ? in.string("problem") : null);
        }
    }

    public boolean board(@Nonnull UUID playerId) {
        if (status != Status.PHYSICAL || isRunning()) return false;
        passengers.add(playerId);
        return true;
    }

    public boolean disembark(@Nonnull UUID playerId) {
        return status == Status.PHYSICAL && !restrictsPassengers() && passengers.remove(playerId);
    }

    public UUID assemblySiloId() {
        return assemblySiloId;
    }

    public Status status() {
        return status;
    }

    @Nullable
    public Projection projection() {
        return projection;
    }

    /** A newly spawned visual entity supersedes old unloaded representations, never the vehicle itself. */
    @Nullable
    public Projection claimProjection(@Nonnull UUID entityId) {
        if (!allowsProjection()) return null;
        if (projection != null && projection.entityId.equals(entityId)) return projection;
        projection = new Projection(entityId, ++projectionGeneration);
        return projection;
    }

    public boolean ownsProjection(@Nonnull UUID entityId, long generation) {
        return allowsProjection() && projection != null
            && projection.entityId.equals(entityId)
            && projection.generation == generation;
    }

    @Nullable
    public Projection claimProjection(@Nonnull UUID entityId, @Nonnull PhysicalPosition position) {
        Projection claimed = claimProjection(entityId);
        if (claimed != null) physicalPosition = position;
        return claimed;
    }

    public boolean allowsProjection() {
        if (status == Status.PHYSICAL) {
            if (location.context() == VehicleSchedule.Context.SURFACE) return true;
            var endpoint = currentEndpointId() == null ? null : CelestialAssetStore.findEndpoint(currentEndpointId());
            return endpoint != null && endpoint.endpoint()
                .physical() != null;
        }
        return physicalManeuver() != null;
    }

    @Nullable
    public OrbitalMechanics.ManeuverObligation physicalManeuver() {
        if (status != Status.IN_FLIGHT || execution == null
            || execution.journey == null
            || execution.phaseIndex >= execution.journey.obligations()
                .size())
            return null;
        var phase = execution.journey.obligations()
            .get(execution.phaseIndex);
        return phase.kind() == OrbitalMechanics.ManeuverKind.ASCENT
            || phase.kind() == OrbitalMechanics.ManeuverKind.LANDING ? phase : null;
    }

    public double physicalManeuverProgress() {
        var phase = physicalManeuver();
        if (phase == null) return 1;
        double ticks = Math
            .max(1, Math.ceil((phase.endEpochOsu() - phase.startEpochOsu()) / OrbitalTransferPlanner.OSU_PER_TICK));
        return Math.clamp(execution.phaseTicks / ticks, 0, 1);
    }

    /** Current visual position, derived from the accepted maneuver clock and its fixed ground anchor. */
    @Nullable
    public PhysicalPosition projectionPosition() {
        if (physicalPosition == null) return null;
        var phase = physicalManeuver();
        if (phase == null) return physicalPosition;
        double fraction = physicalManeuverProgress();
        if (phase.kind() == OrbitalMechanics.ManeuverKind.LANDING) fraction = 1 - fraction;
        return new PhysicalPosition(
            physicalPosition.dimension(),
            physicalPosition.x(),
            physicalPosition.y() + PHYSICAL_MANEUVER_HEIGHT * fraction,
            physicalPosition.z(),
            physicalPosition.yaw());
    }

    @Nullable
    public PhysicalPosition physicalPosition() {
        return physicalPosition;
    }

    @Nullable
    public PhysicalPosition passengerAnchor() {
        return execution != null && execution.journey != null ? execution.sourcePosition : physicalPosition;
    }

    public VehicleSchedule.Place projectionPlace() {
        var schedule = program();
        var phase = physicalManeuver();
        if (phase != null && phase.kind() == OrbitalMechanics.ManeuverKind.LANDING) {
            var destination = schedule.visits()
                .get(execution.destinationIndex)
                .place();
            return execution.arrivalEndpointId == null ? destination
                : new VehicleSchedule.Place(
                    destination.body(),
                    destination.context(),
                    new VehicleSchedule.Exact(
                        execution.arrivalEndpointId,
                        destination.target()
                            .kind()));
        }
        return location;
    }

    /** Physical clearance includes both authored module extents and the native entity's minimum volume. */
    public Footprint footprint() {
        var bounds = builtBlueprint().bounds();
        int span = Math.max(3, Math.max(bounds.width(), bounds.depth()));
        return new Footprint(span, Math.max(10, bounds.height()), span);
    }

    public record Footprint(int width, int height, int depth) {

        public Footprint {
            if (width <= 0 || height <= 0 || depth <= 0)
                throw new IllegalArgumentException("A vehicle footprint must have positive dimensions");
        }

        public AxisAlignedBB bounds(PhysicalPosition position) {
            double angle = Math.toRadians(position.yaw);
            double halfX = (Math.abs(Math.cos(angle)) * width + Math.abs(Math.sin(angle)) * depth) * .5;
            double halfZ = (Math.abs(Math.sin(angle)) * width + Math.abs(Math.cos(angle)) * depth) * .5;
            return AxisAlignedBB.getBoundingBox(
                position.x - halfX,
                position.y,
                position.z - halfZ,
                position.x + halfX,
                position.y + height,
                position.z + halfZ);
        }

        public NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setInteger("width", width);
            out.setInteger("height", height);
            out.setInteger("depth", depth);
            return out;
        }

        public static Footprint deserializeNBT(NbtReader in) {
            return new Footprint(in.integer("width"), in.integer("height"), in.integer("depth"));
        }
    }

    public record PhysicalPosition(int dimension, double x, double y, double z, float yaw) {

        public PhysicalPosition {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw))
                throw new IllegalArgumentException("Invalid physical vehicle position");
        }

        private NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setInteger("dimension", dimension);
            out.setDouble("x", x);
            out.setDouble("y", y);
            out.setDouble("z", z);
            out.setDouble("yaw", yaw);
            return out;
        }

        private static PhysicalPosition deserializeNBT(NbtReader in) {
            return new PhysicalPosition(
                in.integer("dimension"),
                in.doubleValue("x"),
                in.doubleValue("y"),
                in.doubleValue("z"),
                (float) in.doubleValue("yaw"));
        }
    }

    public boolean retireProjection(@Nonnull UUID entityId, long generation) {
        if (!ownsProjection(entityId, generation)) return false;
        projection = null;
        return true;
    }

    @Nullable
    public Service service() {
        return service;
    }

    /** Accepts one landing occurrence. Its threshold is never evaluated again after admission. */
    public ServiceResult beginService(@Nonnull UUID occurrenceId, @Nonnull VehicleSchedule.Repair action,
        @Nonnull UUID padId, @Nonnull UUID returnSiloId) {
        if (action.returnSiloId() != null && !action.returnSiloId()
            .equals(returnSiloId)) return ServiceResult.CONFLICT;
        if (service != null && service.id.equals(occurrenceId)) {
            if (!service.actionId.equals(action.id()) || !service.padId.equals(padId)
                || !service.returnSiloId.equals(returnSiloId)) return ServiceResult.CONFLICT;
            return service.reassembled ? ServiceResult.COMPLETED : ServiceResult.ALREADY_ACTIVE;
        }
        var installed = new Holder(HolderKind.INSTALLED, id);
        if (status != Status.PHYSICAL || (service != null && !service.reassembled)
            || modules.size() != buildSlots.size()
            || modules.values()
                .stream()
                .anyMatch(
                    module -> !module.holder.equals(installed) || module.pendingTransfer != null
                        || module.gantryTransport != null)) {
            return ServiceResult.CONFLICT;
        }
        var repairs = new LinkedHashMap<UUID, UUID>();
        var returns = new LinkedHashMap<UUID, UUID>();
        for (Module module : modules.values()) {
            if (action.qualifies(module.durabilityPercent)) repairs.put(module.id, UUID.randomUUID());
            returns.put(module.id, UUID.randomUUID());
        }
        if (repairs.isEmpty() && action.returnSiloId() == null) return ServiceResult.SKIPPED;
        var pad = new Holder(HolderKind.PAD_BUFFER, padId);
        for (Module module : modules.values()) {
            var disassembly = beginTransfer(module.id, occurrenceId, installed, pad);
            confirmTransfer(disassembly, pad);
        }
        service = new Service(occurrenceId, action.id(), padId, returnSiloId, repairs, returns, Set.of(), false);
        location = new VehicleSchedule.Place(
            bodyKey(),
            location.context(),
            new VehicleSchedule.Exact(
                padId,
                com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind.LANDING_PAD));
        projection = null;
        moduleWork.clear();
        status = Status.DISASSEMBLED;
        revision++;
        return ServiceResult.ACCEPTED;
    }

    @Nullable
    public ModuleWork workFor(@Nonnull UUID moduleId) {
        return moduleWork.get(moduleId);
    }

    public boolean reserveWork(@Nonnull ModuleWork work) {
        BuildSlot slot = buildSlots.stream()
            .filter(candidate -> candidate.moduleId.equals(work.moduleId))
            .findFirst()
            .orElse(null);
        if (slot == null) return false;
        UUID expectedWork = status == Status.CONSTRUCTING
            || (modules.size() < buildSlots.size() && !modules.containsKey(work.moduleId)) ? slot.requestId
                : service != null && !service.reassembled ? service.repairs.get(work.moduleId) : null;
        if (!work.workId.equals(expectedWork) || work.machine.receiver.kind != HolderKind.ASSEMBLER
            || !work.machine.controllerId.equals(work.machine.receiver.id)) return false;
        ModuleWork previous = moduleWork.putIfAbsent(work.moduleId, work);
        return previous == null || previous.equals(work);
    }

    /** Destruction releases only unfinished manufacture assigned to that concrete machine. */
    public void workOwnerDestroyed(@Nonnull UUID controllerId) {
        if (moduleWork.entrySet()
            .removeIf(
                entry -> !modules.containsKey(entry.getKey())
                    && entry.getValue().machine.controllerId.equals(controllerId)))
            revision++;
    }

    /** A recovered partial build resumes only after every existing concrete module reaches its chosen Silo. */
    public boolean resumeConstruction(@Nonnull UUID siloId, @Nonnull VehicleSchedule.Place assemblyPlace) {
        if (modules.size() >= buildSlots.size() || service == null
            || service.reassembled
            || !service.returnSiloId.equals(siloId)
            || status != Status.DISASSEMBLED
            || !isSiloPlace(assemblyPlace, siloId)
            || modules.values()
                .stream()
                .anyMatch(
                    module -> !module.holder.equals(new Holder(HolderKind.SILO_BUFFER, siloId))
                        || module.pendingTransfer != null
                        || module.gantryTransport != null))
            return false;
        service = null;
        assemblySiloId = siloId;
        location = assemblyPlace;
        status = Status.CONSTRUCTING;
        revision++;
        return true;
    }

    /** Native Assembler callers invoke this only for their retained, fully paid completed output. */
    public boolean completeRepair(@Nonnull UUID moduleId, @Nonnull UUID workId, @Nonnull UUID assemblerId) {
        if (service == null || !workId.equals(service.repairs.get(moduleId))) return false;
        if (service.repaired.contains(moduleId)) return true;
        Module module = modules.get(moduleId);
        ModuleWork work = moduleWork.get(moduleId);
        if (status != Status.DISASSEMBLED || module == null
            || work == null
            || !work.workId.equals(workId)
            || !work.machine.controllerId.equals(assemblerId)
            || !module.holder.equals(new Holder(HolderKind.ASSEMBLER, assemblerId))
            || module.pendingTransfer != null
            || module.gantryTransport != null) return false;
        module.durabilityPercent = 100;
        var repaired = new HashSet<>(service.repaired);
        repaired.add(moduleId);
        service = new Service(
            service.id,
            service.actionId,
            service.padId,
            service.returnSiloId,
            service.repairs,
            service.returns,
            repaired,
            false);
        revision++;
        return true;
    }

    public boolean assemble(@Nonnull UUID siloId) {
        return assemble(
            siloId,
            new VehicleSchedule.Place(
                bodyKey(),
                VehicleSchedule.Context.SURFACE,
                new VehicleSchedule.Exact(siloId, CelestialAsset.Endpoint.Kind.SILO)));
    }

    private boolean isSiloPlace(VehicleSchedule.Place place, UUID siloId) {
        return place.body()
            .equals(bodyKey()) && place.target() instanceof VehicleSchedule.Exact exact
            && exact.endpointId()
                .equals(siloId)
            && exact.kind() == CelestialAsset.Endpoint.Kind.SILO;
    }

    public boolean assemble(@Nonnull UUID siloId, @Nonnull VehicleSchedule.Place assemblyPlace) {
        UUID expectedSilo = service != null && !service.reassembled ? service.returnSiloId : assemblySiloId;
        if (!expectedSilo.equals(siloId) || !isSiloPlace(assemblyPlace, siloId)
            || modules.size() != buildSlots.size()
            || (service != null && !service.reassembled && !service.repaired.equals(service.repairs.keySet())))
            return false;
        var receiving = new Holder(HolderKind.SILO_BUFFER, siloId);
        if (modules.values()
            .stream()
            .anyMatch(
                module -> !module.holder.equals(receiving) || module.pendingTransfer != null
                    || module.gantryTransport != null))
            return false;
        var installed = new Holder(HolderKind.INSTALLED, id);
        for (Module module : modules.values()) {
            var installation = beginTransfer(module.id, UUID.randomUUID(), receiving, installed);
            confirmTransfer(installation, installed);
        }
        moduleWork.clear();
        assemblySiloId = siloId;
        location = assemblyPlace;
        if (service != null && !service.reassembled) {
            service = new Service(
                service.id,
                service.actionId,
                service.padId,
                service.returnSiloId,
                service.repairs,
                service.returns,
                service.repaired,
                true);
        }
        status = Status.PHYSICAL;
        revision++;
        return true;
    }

    /** Explicit native recovery chooses a new exact Silo without moving any concrete module. */
    public boolean rebindServiceReturn(@Nonnull UUID expectedServiceId, @Nonnull UUID padId, @Nonnull UUID newSiloId) {
        if (service == null || service.reassembled
            || !service.id.equals(expectedServiceId)
            || !service.padId.equals(padId)
            || (status != Status.DISASSEMBLED && status != Status.INCOMPLETE)) return false;
        if (service.returnSiloId.equals(newSiloId)) return true;
        if (modules.values()
            .stream()
            .anyMatch(module -> module.holder.kind == HolderKind.SILO_BUFFER)) return false;
        var previousReceiver = new Holder(HolderKind.SILO_BUFFER, service.returnSiloId);
        for (Module module : modules.values()) {
            if (module.gantryTransport != null && module.gantryTransport.target.receiver.equals(previousReceiver)) {
                module.pendingTransfer = null;
                module.gantryTransport = null;
            }
        }
        service = new Service(
            service.id,
            service.actionId,
            service.padId,
            newSiloId,
            service.repairs,
            service.returns,
            service.repaired,
            false);
        revision++;
        return true;
    }

    public RocketBlueprint builtBlueprint() {
        var built = new RocketBlueprint();
        if (status == Status.PHYSICAL || status == Status.IN_FLIGHT) {
            for (BuildSlot slot : buildSlots) built.addPart(slot.part);
        }
        return built;
    }

    public List<BuildSlot> buildSlots() {
        return buildSlots;
    }

    public List<Module> modules() {
        return List.copyOf(modules.values());
    }

    public Map<InventoryKey, Long> cargoContents() {
        var contents = new LinkedHashMap<InventoryKey, Long>();
        for (Module module : modules.values()) {
            for (ItemStack stack : module.cargoContents()) {
                contents.merge(ItemStackWrapper.of(stack), (long) stack.stackSize, Long::sum);
            }
            FluidStack fluid = module.freight.getFluid();
            if (fluid != null) contents.merge(FluidKey.of(fluid), (long) fluid.amount, Long::sum);
        }
        return Map.copyOf(contents);
    }

    public Map<FluidKey, Long> propellantAmounts() {
        var contents = new LinkedHashMap<FluidKey, Long>();
        for (Module module : modules.values()) {
            module.propellant.forEach((key, tank) -> contents.merge(key, (long) tank.getFluidAmount(), Long::sum));
        }
        return Map.copyOf(contents);
    }

    public long cargoAmount(@Nonnull InventoryKey resource) {
        return cargoContents().getOrDefault(resource, 0L);
    }

    public static boolean acceptsItemCargo(@Nonnull ItemStack stack) {
        FluidStack contained = GTUtility.getFluidForFilledItem(stack.copy(), true);
        return contained == null || contained.amount <= 0;
    }

    public long insertCargo(@Nonnull InventoryKey resource, long amount, boolean simulate) {
        return transferCargo(resource, amount, simulate, VehicleSchedule.Direction.LOAD);
    }

    public long extractCargo(@Nonnull InventoryKey resource, long amount, boolean simulate) {
        return transferCargo(resource, amount, simulate, VehicleSchedule.Direction.UNLOAD);
    }

    private long transferCargo(InventoryKey resource, long amount, boolean simulate,
        VehicleSchedule.Direction direction) {
        if (amount < 0) throw new IllegalArgumentException("Cargo transfer amount must be nonnegative");
        if (status != Status.PHYSICAL) return 0;
        long moved = 0;
        for (Module module : modules.values()) {
            if (moved == amount) break;
            if (!module.storesAccessible()) continue;
            int requested = (int) Math.min(amount - moved, Integer.MAX_VALUE);
            if (resource instanceof ItemStackWrapper item) {
                if (direction == VehicleSchedule.Direction.LOAD) {
                    ItemStack remainder = module.insertCargo(item.toStack(requested), simulate);
                    moved += requested - (remainder == null ? 0 : remainder.stackSize);
                } else {
                    for (int slot = 0; slot < module.cargo.getSlots() && moved < amount; slot++) {
                        ItemStack present = module.cargo.getStackInSlot(slot);
                        if (present == null || !item.equals(ItemStackWrapper.of(present))) continue;
                        ItemStack extracted = module.cargo
                            .extractItem(slot, (int) Math.min(amount - moved, Integer.MAX_VALUE), simulate);
                        if (extracted != null) moved += extracted.stackSize;
                    }
                }
            } else if (resource instanceof FluidKey fluid) {
                if (direction == VehicleSchedule.Direction.LOAD) {
                    moved += module.fillFreight(fluid.toStack(requested), simulate);
                } else {
                    FluidStack present = module.freight.getFluid();
                    if (present == null || !fluid.equals(FluidKey.of(present))) continue;
                    FluidStack extracted = module.freight.drain(requested, !simulate);
                    if (extracted != null) moved += extracted.amount;
                }
            }
        }
        return moved;
    }

    public long refuel(@Nonnull FluidKey resource, long amount, boolean simulate) {
        if (amount < 0) throw new IllegalArgumentException("Refuel amount must be nonnegative");
        if (status != Status.PHYSICAL) return 0;
        long moved = 0;
        for (Module module : modules.values()) {
            if (moved == amount) break;
            moved += module
                .fillPropellant(resource.toStack((int) Math.min(amount - moved, Integer.MAX_VALUE)), simulate);
        }
        return moved;
    }

    @Nullable
    public Propulsion propulsion() {
        if (status == Status.CONSTRUCTING || status == Status.DISASSEMBLED || modules.size() != buildSlots.size())
            return null;
        RocketPartDef.Engine engine = null;
        double thrust = 0;
        double structuralMass = 0;
        var amounts = new LinkedHashMap<FluidKey, Long>();
        var installed = new Holder(HolderKind.INSTALLED, id);
        for (Module module : modules.values()) {
            if (!module.holder.equals(installed) || module.pendingTransfer != null || module.gantryTransport != null)
                return null;
            structuralMass += module.part.def()
                .dryMassKg();
            var candidate = module.part.def()
                .engine();
            if (candidate != null && module.durabilityPercent > 0) {
                if (engine == null) engine = candidate;
                if (Double.compare(engine.exhaustVelocityMps(), candidate.exhaustVelocityMps()) != 0
                    || !engine.propellants()
                        .equals(candidate.propellants()))
                    return null;
                thrust += candidate.thrustNewtons();
            }
            module.propellant.forEach((key, tank) -> amounts.merge(key, (long) tank.getFluidAmount(), Long::sum));
        }
        if (engine == null || engine.propellants()
            .isEmpty() || !Double.isFinite(thrust) || !Double.isFinite(structuralMass)) return null;
        double propellantMass = 0;
        for (var entry : amounts.entrySet()) {
            if (entry.getValue() == 0) continue;
            var properties = engine.propellants()
                .get(entry.getKey());
            if (properties == null) return null;
            propellantMass += entry.getValue() * properties.kilogramsPerUnit();
        }
        double batches = Double.POSITIVE_INFINITY;
        double massPerBatch = 0;
        for (var entry : engine.propellants()
            .entrySet()) {
            batches = Math.min(
                batches,
                amounts.getOrDefault(entry.getKey(), 0L) / entry.getValue()
                    .volumeParts());
            massPerBatch += entry.getValue()
                .volumeParts()
                * entry.getValue()
                    .kilogramsPerUnit();
        }
        double usable = batches * massPerBatch;
        double wetMass = structuralMass + propellantMass;
        if (!Double.isFinite(usable) || !Double.isFinite(wetMass) || wetMass - usable <= 0) return null;
        var unitsPerKilogram = new LinkedHashMap<FluidKey, Double>();
        for (var entry : engine.propellants()
            .entrySet()) {
            unitsPerKilogram.put(
                entry.getKey(),
                entry.getValue()
                    .volumeParts() / massPerBatch);
        }
        return new Propulsion(structuralMass, wetMass, usable, engine.exhaustVelocityMps(), thrust, unitsPerKilogram);
    }

    /** Actual mass and mixture-limited supplies. Freight contents add no mass under the cargo contract. */
    public record Propulsion(double structuralMassKg, double wetMassKg, double usablePropellantKg,
        double exhaustVelocityMps, double thrustNewtons, @Nonnull Map<FluidKey, Double> unitsPerKilogram) {

        public Propulsion {
            unitsPerKilogram = Map.copyOf(unitsPerKilogram);
        }

        public double inertMassKg() {
            return wetMassKg - usablePropellantKg;
        }

        public double usableDeltaVMps() {
            return localVehicle().usableDeltaVMps();
        }

        public OrbitalMechanics.LocalVehicle localVehicle() {
            return new OrbitalMechanics.LocalVehicle(
                inertMassKg(),
                usablePropellantKg,
                exhaustVelocityMps,
                thrustNewtons);
        }
    }

    /** A simple delivery order produces trips through the ordinary vehicle executor. */
    public record DeliveryPlan(@Nonnull VehicleSchedule.Place home, @Nonnull VehicleSchedule.Place destination,
        @Nonnull Map<InventoryKey, Long> requested) {

        public DeliveryPlan {
            requested = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(requested));
            if (!isSilo(home) || !isSilo(destination)
                || home.equals(destination)
                || requested.isEmpty()
                || requested.entrySet()
                    .stream()
                    .anyMatch(
                        entry -> !(entry.getKey() instanceof ItemStackWrapper) || entry.getValue() == null
                            || entry.getValue() <= 0))
                throw new IllegalArgumentException("A delivery needs distinct silos and positive item quantities");
        }

        private static boolean isSilo(VehicleSchedule.Place place) {
            return place.target() instanceof VehicleSchedule.Exact exact
                && exact.kind() == CelestialAsset.Endpoint.Kind.SILO;
        }

        public NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setTag("home", home.serializeNBT());
            out.setTag("destination", destination.serializeNBT());
            out.setTag("requested", InventoryKeyState.encodeAmounts(requested));
            return out;
        }

        public static DeliveryPlan deserializeNBT(NBTTagCompound encoded) {
            var in = new NbtReader(encoded, "deliveryPlan");
            return new DeliveryPlan(
                VehicleSchedule.Place.deserializeNBT(
                    in.compound("home")
                        .tag()),
                VehicleSchedule.Place.deserializeNBT(
                    in.compound("destination")
                        .tag()),
                InventoryKeyState.decodeAmounts(in, "requested"));
        }
    }

    public record DeliveryView(@Nonnull DeliveryPlan plan, @Nonnull Map<InventoryKey, Long> delivered,
        boolean enabled) {

        public DeliveryView {
            delivered = Map.copyOf(delivered);
            if (!delivered.keySet()
                .equals(
                    plan.requested()
                        .keySet())
                || delivered.entrySet()
                    .stream()
                    .anyMatch(
                        entry -> entry.getValue() < 0 || entry.getValue() > plan.requested()
                            .get(entry.getKey())))
                throw new IllegalArgumentException("Delivery receipts must fit their order");
        }

        public NBTTagCompound serializeNBT() {
            var out = plan.serializeNBT();
            out.setTag("delivered", InventoryKeyState.encodeAmounts(delivered));
            out.setBoolean("enabled", enabled);
            return out;
        }

        public static DeliveryView deserializeNBT(NBTTagCompound encoded) {
            var in = new NbtReader(encoded, "deliveryView");
            return new DeliveryView(
                DeliveryPlan.deserializeNBT(encoded),
                InventoryKeyState.decodeAmounts(in, "delivered"),
                in.bool("enabled"));
        }
    }

    private static final class DeliveryJob {

        private final DeliveryPlan plan;
        private final Map<InventoryKey, Long> delivered = new LinkedHashMap<>();
        private boolean enabled;
        @Nullable
        private String waitingProblemKey;
        private int retryTicks;

        private DeliveryJob(DeliveryView view) {
            plan = view.plan();
            delivered.putAll(view.delivered());
            enabled = view.enabled();
        }

        private boolean complete() {
            return delivered.entrySet()
                .stream()
                .allMatch(
                    entry -> entry.getValue()
                        .equals(
                            plan.requested()
                                .get(entry.getKey())));
        }

        private NBTTagCompound serializeNBT() {
            return new DeliveryView(plan, delivered, enabled).serializeNBT();
        }

        private static DeliveryJob deserializeNBT(NBTTagCompound encoded) {
            return new DeliveryJob(DeliveryView.deserializeNBT(encoded));
        }
    }

    @Nullable
    public DeliveryView deliveryView() {
        return delivery == null ? null : new DeliveryView(delivery.plan, delivery.delivered, delivery.enabled);
    }

    public CommandResult saveDelivery(UUID actorTeam, long expectedRevision, DeliveryPlan plan) {
        if (!teamId.equals(actorTeam)) return CommandResult.FORBIDDEN;
        if (revision != expectedRevision) return CommandResult.STALE;
        if (plan.requested()
            .keySet()
            .stream()
            .anyMatch(key -> !acceptsItemCargo(((ItemStackWrapper) key).toStack(1)))) return CommandResult.BLOCKED;
        if (status != Status.PHYSICAL || isRunning() || !matchesCurrentPlace(plan.home())) return CommandResult.BLOCKED;
        for (var place : List.of(plan.home(), plan.destination())) {
            var endpoint = CelestialAssetStore.findEndpoint(((VehicleSchedule.Exact) place.target()).endpointId());
            if (endpoint == null || !matchesEndpoint(place, endpoint)
                || !teamId.equals(CelestialAssetStore.getTeamId(endpoint.asset().assetId)))
                return CommandResult.BLOCKED;
        }
        if (delivery == null || !delivery.plan.equals(plan)) {
            var delivered = new LinkedHashMap<InventoryKey, Long>();
            plan.requested()
                .keySet()
                .forEach(key -> delivered.put(key, 0L));
            delivery = new DeliveryJob(new DeliveryView(plan, delivered, true));
        } else delivery.enabled = true;
        revision++;
        return CommandResult.APPLIED;
    }

    /** Stops producing new trips. A trip already underway still brings the same vehicle home. */
    public CommandResult stopDelivery(UUID actorTeam, long expectedRevision) {
        if (!teamId.equals(actorTeam)) return CommandResult.FORBIDDEN;
        if (revision != expectedRevision) return CommandResult.STALE;
        if (delivery == null) return CommandResult.BLOCKED;
        delivery.enabled = false;
        delivery.waitingProblemKey = null;
        if (execution != null && execution.origin == ProgramOrigin.DELIVERY
            && status == Status.PHYSICAL
            && execution.journey == null
            && matchesCurrentPlace(delivery.plan.home())) execution.completed = true;
        revision++;
        return CommandResult.APPLIED;
    }

    public CommandResult clearDelivery(UUID actorTeam, long expectedRevision) {
        if (!teamId.equals(actorTeam)) return CommandResult.FORBIDDEN;
        if (revision != expectedRevision) return CommandResult.STALE;
        if (delivery == null || delivery.enabled || isRunning()) return CommandResult.BLOCKED;
        delivery = null;
        if (execution != null && execution.origin == ProgramOrigin.DELIVERY) execution = null;
        revision++;
        return CommandResult.APPLIED;
    }

    private void beginDeliveryTrip() {
        if (status != Status.PHYSICAL || !matchesCurrentPlace(delivery.plan.home())) return;
        if (delivery.complete()) {
            delivery.waitingProblemKey = null;
            delivery.enabled = false;
            revision++;
            return;
        }
        if (delivery.retryTicks > 0) {
            delivery.retryTicks--;
            return;
        }
        delivery.retryTicks = 20;
        var homePad = deliveryPadFor(delivery.plan.home());
        if (homePad == null) {
            delivery.waitingProblemKey = "galaxia.vehicle.simple.waiting_home_pad";
            return;
        }
        var destinationPad = deliveryPadFor(delivery.plan.destination());
        if (destinationPad == null) {
            delivery.waitingProblemKey = "galaxia.vehicle.simple.waiting_destination_pad";
            return;
        }
        delivery.waitingProblemKey = Problem.ACTION_RESOURCES.key;
        var resources = currentResources();
        if (resources == null) return;
        var packing = new ArrayList<com.cleanroommc.modularui.utils.item.ItemStackHandler>();
        for (var module : modules.values()) {
            var copy = new com.cleanroommc.modularui.utils.item.ItemStackHandler(module.cargo.getSlots());
            for (int slot = 0; slot < module.cargo.getSlots(); slot++) {
                var stack = module.cargo.getStackInSlot(slot);
                copy.setStackInSlot(slot, stack == null ? null : stack.copy());
            }
            packing.add(copy);
        }
        var loads = new ArrayList<VehicleSchedule.Action>();
        var unloads = new ArrayList<VehicleSchedule.Action>();
        for (var request : delivery.plan.requested()
            .entrySet()) {
            long remaining = request.getValue() - delivery.delivered.get(request.getKey());
            if (remaining == 0) continue;
            var item = (ItemStackWrapper) request.getKey();
            long aboard = Math.min(remaining, cargoAmount(item));
            long offered = Math.min(remaining - aboard, resources.updateContents(item, -(remaining - aboard), true));
            offered = Math.min(Integer.MAX_VALUE, Math.min(offered, insertCargo(item, offered, true)));
            var rest = offered == 0 ? null : item.toStack((int) Math.min(offered, Integer.MAX_VALUE));
            for (var inventory : packing) {
                for (int slot = 0; rest != null && slot < inventory.getSlots(); slot++)
                    rest = inventory.insertItem(slot, rest, false);
            }
            long loaded = offered - (rest == null ? 0 : rest.stackSize);
            if (loaded > 0) loads
                .add(new VehicleSchedule.Transfer(UUID.randomUUID(), item, loaded, VehicleSchedule.Direction.LOAD));
            if (loaded + aboard > 0) unloads.add(
                new VehicleSchedule.Transfer(
                    UUID.randomUUID(),
                    item,
                    loaded + aboard,
                    VehicleSchedule.Direction.UNLOAD));
        }
        if (unloads.isEmpty()) return;
        loads.add(VehicleSchedule.Refuel.full(UUID.randomUUID()));
        var home = new VehicleSchedule.Visit(UUID.randomUUID(), delivery.plan.home(), loads);
        double threshold = ConfigRocket.wear.automaticRepairThresholdPercent;
        var serviceDestination = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            destinationPad,
            List.of(
                new VehicleSchedule.Repair(
                    UUID.randomUUID(),
                    threshold,
                    ((VehicleSchedule.Exact) delivery.plan.destination()
                        .target()).endpointId())));
        var destination = new VehicleSchedule.Visit(UUID.randomUUID(), delivery.plan.destination(), unloads);
        var serviceHome = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            homePad,
            List.of(
                new VehicleSchedule.Repair(
                    UUID.randomUUID(),
                    threshold,
                    ((VehicleSchedule.Exact) delivery.plan.home()
                        .target()).endpointId())));
        var returned = new VehicleSchedule.Visit(UUID.randomUUID(), delivery.plan.home(), List.of());
        var trip = new VehicleSchedule(
            List.of(home, serviceDestination, destination, serviceHome, returned),
            VehicleSchedule.Mode.RUN_ONCE).withSafeDurability(schedule.requireSafeDurability());
        for (var leg : trip.routes()
            .keySet())
            trip = trip.withRouteSettings(
                leg,
                new VehicleSchedule.RouteSettings(0, OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV));
        delivery.retryTicks = 0;
        delivery.waitingProblemKey = null;
        execution = new Execution();
        execution.origin = ProgramOrigin.DELIVERY;
        execution.overrideProgram = trip;
        revision++;
    }

    @Nullable
    private VehicleSchedule.Place deliveryPadFor(VehicleSchedule.Place silo) {
        UUID siloId = ((VehicleSchedule.Exact) silo.target()).endpointId();
        return CelestialAssetStore.getTeamAssets(teamId, silo.body())
            .stream()
            .filter(asset -> asset.status() == CelestialAsset.Status.OPERATIONAL)
            .flatMap(
                asset -> asset.endpoints()
                    .values()
                    .stream()
                    .filter(
                        endpoint -> endpoint.kind() == CelestialAsset.Endpoint.Kind.LANDING_PAD
                            && endpoint.physical() != null
                            && endpoint.serviceSlots() > 0
                            && siloId.equals(endpoint.serviceSiloId()))
                    .map(endpoint -> new CelestialAssetStore.EndpointRef(asset, endpoint)))
            .filter(
                endpoint -> VehicleSchedule.Place.at(endpoint)
                    .context() == silo.context())
            .sorted(
                java.util.Comparator.comparing(
                    endpoint -> endpoint.endpoint()
                        .id()))
            .findFirst()
            .map(VehicleSchedule.Place::at)
            .orElse(null);
    }

    public VehicleSchedule schedule() {
        return schedule;
    }

    private VehicleSchedule program() {
        return execution != null && execution.overrideProgram != null ? execution.overrideProgram : schedule;
    }

    public ScheduleView scheduleView() {
        int locked = lockedVisitCount();
        UUID activeVisit = locked == 0 ? null
            : schedule.visits()
                .get(locked - 1)
                .id();
        var engine = propulsion();
        return new ScheduleView(
            id,
            revision,
            schedule,
            status,
            location,
            activeVisit,
            new HashSet<>(
                schedule.visits()
                    .subList(0, locked)
                    .stream()
                    .map(VehicleSchedule.Visit::id)
                    .toList()),
            delivery != null && delivery.waitingProblemKey != null ? delivery.waitingProblemKey
                : execution != null && execution.problem != null ? execution.problem.key
                    : program().endsWithoutRecovery() ? "galaxia.rocket.schedule.no_recovery" : null,
            engine == null ? null : engine.localVehicle(),
            propellantAmounts(),
            cargoContents(),
            modules.values()
                .stream()
                .map(this::moduleView)
                .toList(),
            buildSlots.size(),
            service != null && !service.reassembled ? service.returnSiloId : null,
            execution == null ? null : execution.launchWarning,
            execution == null || execution.completed || execution.origin != ProgramOrigin.MANUAL_LANDING ? null
                : execution.overrideProgram,
            deliveryView(),
            isRunning());
    }

    private ModuleView moduleView(Module module) {
        boolean selected = service != null && !service.reassembled && service.repairs.containsKey(module.id);
        boolean repaired = selected && service.repaired.contains(module.id);
        String work = module.gantryTransport != null ? "transport"
            : selected && !repaired ? moduleWork.containsKey(module.id) ? "repair_admitted" : "repair_waiting"
                : service != null && !service.reassembled ? "return" : null;
        return new ModuleView(
            module.id,
            module.part.def()
                .nameKey(),
            module.durabilityPercent,
            module.holder.kind,
            selected,
            repaired,
            work == null ? null : "galaxia.rocket.schedule.work." + work);
    }

    private int lockedVisitCount() {
        if (execution != null && execution.overrideProgram != null) return 0;
        if (isRunning()) return Math
            .max(execution.visitIndex, execution.journey == null ? execution.visitIndex : execution.destinationIndex)
            + 1;
        if (service != null && !service.reassembled) {
            for (int i = 0; i < schedule.visits()
                .size(); i++) {
                if (schedule.visits()
                    .get(i)
                    .actions()
                    .stream()
                    .anyMatch(
                        action -> action.id()
                            .equals(service.actionId)))
                    return i + 1;
            }
        }
        return 0;
    }

    public long revision() {
        return revision;
    }

    public CommandResult saveSchedule(@Nonnull UUID actorTeam, long expectedRevision,
        @Nonnull VehicleSchedule replacement) {
        if (!teamId.equals(actorTeam)) return CommandResult.FORBIDDEN;
        if (revision != expectedRevision) return CommandResult.STALE;
        if (delivery != null) return CommandResult.BLOCKED;
        if (!hasPreparedSurfaceDestinations(replacement) || !replacement.hasValidRefueling())
            return CommandResult.BLOCKED;
        if (!passengers.isEmpty() && replacement.endsWithoutRecovery()) return CommandResult.BLOCKED;
        int locked = lockedVisitCount();
        if (replacement.visits()
            .size() < locked
            || !replacement.visits()
                .subList(0, locked)
                .equals(
                    schedule.visits()
                        .subList(0, locked)))
            return CommandResult.BLOCKED;
        for (int i = 1; i < locked; i++) {
            var leg = new VehicleSchedule.Leg(
                schedule.visits()
                    .get(i - 1)
                    .id(),
                schedule.visits()
                    .get(i)
                    .id());
            if (!replacement.routes()
                .get(leg)
                .equals(
                    schedule.routes()
                        .get(leg)))
                return CommandResult.BLOCKED;
        }
        if (execution != null && execution.overrideProgram != null && execution.completed) execution = null;
        if (execution != null && execution.overrideProgram == null) {
            execution.acknowledgedRisks.removeIf(leg -> !sameLeg(schedule, replacement, leg));
            execution.launchWarning = null;
        }
        schedule = replacement;
        revision++;
        return CommandResult.APPLIED;
    }

    /** A manual command compiles into the same executor without changing the saved automated itinerary. */
    private CommandResult acceptManualLanding(UUID actorTeam, long expectedRevision, VehicleSchedule.Place destination,
        VehicleSchedule.RouteSettings settings) {
        if (!teamId.equals(actorTeam)) return CommandResult.FORBIDDEN;
        if (revision != expectedRevision) return CommandResult.STALE;
        if (delivery != null || status != Status.PHYSICAL
            || destination.surfaceSite() == null
            || settings.allowance()
                .isEmpty()
            || !destination.surfaceSite()
                .footprint()
                .equals(footprint())
            || (isRunning() && (execution.overrideProgram == null || execution.journey != null)))
            return CommandResult.BLOCKED;
        var source = new VehicleSchedule.Visit(UUID.randomUUID(), location, List.of());
        var target = new VehicleSchedule.Visit(UUID.randomUUID(), destination, List.of());
        var manual = new VehicleSchedule(List.of(source, target), VehicleSchedule.Mode.RUN_ONCE)
            .withSafeDurability(schedule.requireSafeDurability())
            .withRouteSettings(new VehicleSchedule.Leg(source.id(), target.id()), settings);
        execution = new Execution();
        execution.overrideProgram = manual;
        revision++;
        return CommandResult.APPLIED;
    }

    public CommandResult cancelManualLanding(UUID actorTeam, long expectedRevision) {
        if (!teamId.equals(actorTeam)) return CommandResult.FORBIDDEN;
        if (revision != expectedRevision) return CommandResult.STALE;
        if (execution == null || execution.origin != ProgramOrigin.MANUAL_LANDING
            || execution.overrideProgram == null
            || execution.completed
            || execution.journey != null
            || status != Status.PHYSICAL) return CommandResult.BLOCKED;
        execution = null;
        revision++;
        return CommandResult.APPLIED;
    }

    /** Native terrain is rechecked at the composition boundary before any arrival maneuver progresses. */
    @Nullable
    public VehicleSchedule.Place pendingSurfaceLanding() {
        if (execution == null || execution.origin != ProgramOrigin.MANUAL_LANDING
            || execution.overrideProgram == null
            || execution.journey == null
            || status != Status.IN_FLIGHT) return null;
        if (execution.phaseIndex < execution.journey.obligations()
            .size()) {
            var kind = execution.journey.obligations()
                .get(execution.phaseIndex)
                .kind();
            if (kind != OrbitalMechanics.ManeuverKind.CAPTURE && kind != OrbitalMechanics.ManeuverKind.LANDING)
                return null;
        }
        return execution.overrideProgram.visits()
            .getLast()
            .place();
    }

    public void blockSurfaceLanding() {
        if (pendingSurfaceLanding() != null) waitFor(Problem.LANDING_SITE_BLOCKED);
    }

    /** Starts at the actual current place. Authorization of the player belongs to the server runtime. */
    public CommandResult start(@Nonnull UUID actorTeam, long expectedRevision) {
        if (!teamId.equals(actorTeam)) return CommandResult.FORBIDDEN;
        if (revision != expectedRevision) return CommandResult.STALE;
        if (delivery != null) return CommandResult.BLOCKED;
        if (execution != null && execution.reviewPaused && status == Status.PHYSICAL) {
            execution.reviewPaused = false;
            clearProblem();
            revision++;
            return CommandResult.APPLIED;
        }
        if (status != Status.PHYSICAL || isRunning()
            || !schedule.hasValidRefueling()
            || !hasPreparedSurfaceDestinations(schedule)
            || schedule.visits()
                .isEmpty()
            || (!passengers.isEmpty() && schedule.endsWithoutRecovery())
            || schedule.routes()
                .values()
                .stream()
                .anyMatch(
                    route -> route.allowance()
                        .isEmpty())
            || !matchesCurrentPlace(
                schedule.visits()
                    .getFirst()
                    .place()))
            return CommandResult.BLOCKED;
        execution = new Execution();
        revision++;
        return CommandResult.APPLIED;
    }

    private static boolean hasPreparedSurfaceDestinations(VehicleSchedule itinerary) {
        for (int i = itinerary.mode() == VehicleSchedule.Mode.RUN_ONCE ? 1 : 0; i < itinerary.visits()
            .size(); i++) {
            var place = itinerary.visits()
                .get(i)
                .place();
            if (place.context() == VehicleSchedule.Context.SURFACE && place.target() == null) return false;
        }
        return true;
    }

    /** Reviews only an uncommitted departure. No fuel or active journey changes in this command. */
    public CommandResult reviewLaunchRisk(@Nonnull UUID actorTeam, long expectedRevision, boolean proceed) {
        if (!teamId.equals(actorTeam)) return CommandResult.FORBIDDEN;
        if (revision != expectedRevision) return CommandResult.STALE;
        if (execution == null || execution.launchWarning == null
            || execution.journey != null
            || status != Status.PHYSICAL) return CommandResult.BLOCKED;
        if (proceed) execution.acknowledgedRisks.add(execution.launchWarning.leg);
        execution.reviewPaused = !proceed;
        execution.launchWarning = null;
        clearProblem();
        if (!proceed) waitFor(Problem.REVIEW_PAUSED);
        revision++;
        return CommandResult.APPLIED;
    }

    private static boolean sameLeg(VehicleSchedule previous, VehicleSchedule replacement, VehicleSchedule.Leg leg) {
        if (!java.util.Objects.equals(
            previous.routes()
                .get(leg),
            replacement.routes()
                .get(leg)))
            return false;
        for (UUID id : List.of(leg.fromVisit(), leg.toVisit())) {
            var before = previous.visits()
                .stream()
                .filter(
                    visit -> visit.id()
                        .equals(id))
                .findFirst();
            var after = replacement.visits()
                .stream()
                .filter(
                    visit -> visit.id()
                        .equals(id))
                .findFirst();
            if (before.isEmpty() || !before.equals(after)) return false;
        }
        return true;
    }

    private boolean matchesCurrentPlace(VehicleSchedule.Place requested) {
        if (!location.body()
            .equals(requested.body()) || location.context() != requested.context()) return false;
        if (requested.surfaceSite() != null) return requested.surfaceSite()
            .equals(location.surfaceSite());
        if (requested.target() == null) return true;
        if (requested.target() instanceof VehicleSchedule.Exact && requested.target()
            .equals(location.target())) return true;
        UUID endpointId = currentEndpointId();
        if (endpointId == null) return false;
        var ref = CelestialAssetStore.findEndpoint(endpointId);
        return ref != null && matchesEndpoint(requested, ref);
    }

    /** Advances only active server ticks. Chosen geometry, paid phases and action receipts survive world saves. */
    public void tick(@Nonnull Registry registry, double orbitalEpoch) {
        if (delivery != null && delivery.enabled && !isRunning()) beginDeliveryTrip();
        var schedule = program();
        if (!isRunning() || status == Status.LOST) return;
        if (execution.reviewPaused) return;
        if (execution.launchWarning != null) {
            if (!passengers.isEmpty()) return;
            execution.launchWarning = null;
            clearProblem();
        }
        if (status == Status.INCOMPLETE) {
            waitFor(Problem.INCOMPLETE);
            return;
        }
        if (execution.retryTicks > 0) {
            execution.retryTicks--;
            return;
        }
        if (execution.journey != null) {
            tickJourney(registry);
            return;
        }
        var visit = schedule.visits()
            .get(execution.visitIndex);
        if (status == Status.DISASSEMBLED) {
            waitFor(Problem.SERVICE);
            return;
        }
        var resources = currentResources();
        var touched = new HashSet<InventoryKey>();
        for (var action : visit.actions()) {
            if (execution.finished.contains(action.id())) continue;
            if (action instanceof VehicleSchedule.Transfer transfer) {
                if (!touched.add(transfer.resource())) continue;
                if (resources == null) continue;
                long remaining = transfer.amount() - execution.progress.getOrDefault(action.id(), 0L);
                boolean loading = transfer.direction() == VehicleSchedule.Direction.LOAD;
                long moved = loading ? insertCargo(transfer.resource(), remaining, true)
                    : extractCargo(transfer.resource(), remaining, true);
                moved = Math.min(moved, resources.updateContents(transfer.resource(), loading ? -moved : moved, true));
                if (moved > 0) {
                    long external = resources.updateContents(transfer.resource(), loading ? -moved : moved, false);
                    long internal = loading ? insertCargo(transfer.resource(), external, false)
                        : extractCargo(transfer.resource(), external, false);
                    if (internal != external)
                        throw new IllegalStateException("Rocket transfer admission changed within one server step");
                    resources.markDirty();
                    execution.progress.merge(action.id(), internal, Long::sum);
                    if (!loading && delivery != null
                        && execution.origin == ProgramOrigin.DELIVERY
                        && visit.place()
                            .equals(delivery.plan.destination())) {
                        delivery.delivered.computeIfPresent(
                            transfer.resource(),
                            (key, count) -> count + Math.min(
                                internal,
                                delivery.plan.requested()
                                    .get(key) - count));
                    }
                }
                if (execution.progress.getOrDefault(action.id(), 0L) == transfer.amount())
                    execution.finished.add(action.id());
            } else if (action instanceof VehicleSchedule.Refuel refuel) {
                if (!location.canRefuel()) {
                    waitFor(Problem.REFUEL_SILO);
                    return;
                }
                var targets = refuelTargets(refuel);
                if (resources != null) for (var target : targets.entrySet()) {
                    long missing = Math
                        .max(0, target.getValue() - propellantAmounts().getOrDefault(target.getKey(), 0L));
                    long admitted = refuel(target.getKey(), missing, true);
                    admitted = Math.min(admitted, resources.updateContents(target.getKey(), -admitted, true));
                    if (admitted > 0) {
                        long removed = resources.updateContents(target.getKey(), -admitted, false);
                        if (refuel(target.getKey(), removed, false) != removed)
                            throw new IllegalStateException("Refuel admission changed within one server step");
                        resources.markDirty();
                    }
                }
                if (targets.entrySet()
                    .stream()
                    .allMatch(target -> propellantAmounts().getOrDefault(target.getKey(), 0L) >= target.getValue())) {
                    execution.finished.add(action.id());
                }
            } else if (action instanceof VehicleSchedule.Deploy deploy) {
                if (!isOpeningPayload(deploy) || location.context() != VehicleSchedule.Context.ORBIT
                    || !bodyKey().equals(
                        CelestialObjectKey
                            .registered(com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId.OVERWORLD)))
                    continue;
                if (extractCargo(deploy.cargo(), deploy.amount(), true) != deploy.amount()) continue;
                extractCargo(deploy.cargo(), deploy.amount(), false);
                GTTeamsCompat.awardAchievement(teamId, GalaxiaTeamData.Achievement.STAR_MAP_ACTIVATION);
                execution.finished.add(action.id());
            } else if (action instanceof VehicleSchedule.Repair repair) {
                if (visit.actions()
                    .stream()
                    .anyMatch(
                        other -> other != action && !(other instanceof VehicleSchedule.Repair)
                            && !execution.finished.contains(other.id())))
                    continue;
                if (service != null && service.id.equals(execution.occurrenceId) && service.reassembled) {
                    execution.finished.add(action.id());
                    continue;
                }
                if (repair.returnSiloId() == null && (service == null || !service.id.equals(execution.occurrenceId))
                    && modules.values()
                        .stream()
                        .noneMatch(module -> repair.qualifies(module.durabilityPercent))) {
                    execution.finished.add(action.id());
                    continue;
                }
                UUID endpointId = currentEndpointId();
                var endpoint = endpointId == null ? null : CelestialAssetStore.findEndpoint(endpointId);
                var controller = endpoint == null ? null : TransportEndpoint.loaded(endpoint);
                if (!(controller instanceof TileEntityTransportPlatform pad)) continue;
                var result = pad.beginService(registry, this, execution.occurrenceId, repair);
                if (result == ServiceResult.SKIPPED || result == ServiceResult.COMPLETED)
                    execution.finished.add(action.id());
            }
        }
        if (visit.actions()
            .stream()
            .anyMatch(action -> !execution.finished.contains(action.id()))) {
            waitFor(
                status == Status.DISASSEMBLED || visit.actions()
                    .stream()
                    .anyMatch(
                        action -> action instanceof VehicleSchedule.Repair && !execution.finished.contains(action.id()))
                            ? Problem.SERVICE
                            : Problem.ACTION_RESOURCES);
            return;
        }
        clearProblem();
        int next = execution.visitIndex + 1;
        if (next == schedule.visits()
            .size()) {
            if (schedule.mode() == VehicleSchedule.Mode.RUN_ONCE) {
                execution.completed = true;
                if (schedule.endsWithoutRecovery()) {
                    if (!passengers.isEmpty())
                        throw new IllegalStateException("A crewed mission cannot deliberately lose its Rocket");
                    status = Status.LOST;
                    projection = null;
                    var lost = new Holder(HolderKind.LOST, id);
                    for (Module module : modules.values()) {
                        var handoff = beginTransfer(module.id, execution.occurrenceId, module.holder, lost);
                        confirmTransfer(handoff, lost);
                    }
                }
                revision++;
                return;
            }
            next = 0;
        }
        depart(next, orbitalEpoch);
    }

    private static boolean isOpeningPayload(VehicleSchedule.Deploy deploy) {
        return deploy.cargo()
            .equals(ItemStackWrapper.of(new ItemStack(RocketContent.PRIMITIVE_TELESCOPE)));
    }

    private Map<FluidKey, Long> refuelTargets(VehicleSchedule.Refuel action) {
        if (!action.toFull()) return action.targets();
        var targets = new LinkedHashMap<FluidKey, Long>();
        for (Module module : modules.values())
            module.propellant.forEach((key, tank) -> targets.merge(key, (long) tank.getCapacity(), Long::sum));
        return targets;
    }

    @Nullable
    private com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory currentResources() {
        UUID endpointId = currentEndpointId();
        var endpoint = endpointId == null ? null : CelestialAssetStore.findEndpoint(endpointId);
        return endpoint == null || !teamId.equals(CelestialAssetStore.getTeamId(endpoint.asset().assetId)) ? null
            : endpoint.resources();
    }

    private void depart(int next, double epoch) {
        var schedule = program();
        var destination = schedule.visits()
            .get(next);
        if (matchesCurrentPlace(destination.place())) {
            activateVisit(next);
            return;
        }
        Propulsion engine = propulsion();
        if (engine == null) {
            waitFor(Problem.PROPULSION);
            return;
        }
        if (execution.origin != ProgramOrigin.DELIVERY) {
            double remaining = remainingAllowance();
            if (!Double.isFinite(remaining) || engine.usableDeltaVMps() + 1e-7 < remaining) {
                waitFor(Problem.FUEL_BUDGET);
                return;
            }
        }
        var sourceBody = GalaxiaCelestialAPI.get(bodyKey())
            .orElse(null);
        var targetBody = GalaxiaCelestialAPI.get(
            destination.place()
                .body())
            .orElse(null);
        if (sourceBody == null || targetBody == null) {
            waitFor(Problem.UNAVAILABLE_BODY);
            return;
        }
        var config = ConfigRocket.flight;
        var model = config.costModel();
        var plan = OrbitalTransferPlanner.computePoweredCandidates(
            GalaxiaCelestialAPI.root(),
            sourceBody,
            targetBody,
            epoch,
            location.endpointKind(),
            parkingAngleRadians,
            destination.place()
                .endpointKind(),
            1,
            engine.localVehicle(),
            model);
        if (execution.origin == ProgramOrigin.DELIVERY && !configureDeliveryRoute(next, plan, engine, model)) return;
        if (execution.origin == ProgramOrigin.DELIVERY && engine.usableDeltaVMps() + 1e-7 < remainingAllowance()) {
            waitFor(Problem.FUEL_BUDGET);
            return;
        }
        var settings = program().routes()
            .get(
                new VehicleSchedule.Leg(
                    schedule.visits()
                        .get(execution.visitIndex)
                        .id(),
                    destination.id()));
        if (settings == null || settings.allowance()
            .isEmpty()) {
            waitFor(Problem.FUEL_BUDGET);
            return;
        }
        var supplied = plan.candidates()
            .stream()
            .filter(
                candidate -> candidate.status() == OrbitalMechanics.LocalStatus.FEASIBLE
                    && candidate.totalDvMps() <= settings.allowance()
                        .getAsDouble()
                    && candidate.gameSeconds() <= config.maximumTransferSeconds
                    && billsFor(candidate) != null)
            .toList();
        var eligible = new OrbitalTransferPlanner.PoweredPlan(
            plan.status(),
            supplied.stream()
                .filter(
                    candidate -> !schedule.requireSafeDurability() || coversMaintenanceInterval(candidate, next, model))
                .toList());
        PoweredCandidate selected = OrbitalTransferPlanner.selectPoweredCandidate(
            eligible,
            settings.allowance()
                .getAsDouble(),
            settings.priority());
        if (selected == null) {
            waitFor(!supplied.isEmpty() && schedule.requireSafeDurability() ? Problem.DURABILITY : Problem.NO_ROUTE);
            return;
        }
        var leg = new VehicleSchedule.Leg(
            schedule.visits()
                .get(execution.visitIndex)
                .id(),
            destination.id());
        if (!passengers.isEmpty() && !execution.acknowledgedRisks.contains(leg)) {
            LaunchWarning warning = returnRisk(selected, destination.place(), leg, engine, model);
            if (warning != null) {
                if (!warning.equals(execution.launchWarning)) {
                    execution.launchWarning = warning;
                    revision++;
                }
                waitFor(Problem.CONFIRMATION_REQUIRED);
                return;
            }
        }
        execution.launchWarning = null;
        UUID sourceEndpointId = currentEndpointId();
        var sourceEndpoint = sourceEndpointId == null ? null : CelestialAssetStore.findEndpoint(sourceEndpointId);
        if (sourceEndpoint != null && sourceEndpoint.availableServiceSlots() == 0) {
            waitFor(Problem.ENDPOINT_UNAVAILABLE);
            return;
        }
        execution.journey = selected;
        execution.wear = wearFor(selected);
        execution.bills = billsFor(selected);
        execution.destinationIndex = next;
        execution.sourcePosition = physicalPosition;
        execution.phaseIndex = 0;
        execution.phaseTicks = 0;
        execution.phasePaid = false;
        execution.arrivalEndpointId = null;
        status = Status.IN_FLIGHT;
        if (!allowsProjection()) projection = null;
        var sourceController = sourceEndpoint == null ? null : TransportEndpoint.loaded(sourceEndpoint);
        if (sourceController instanceof TileEntitySilo silo && !silo.releaseOccupancy(this)) {
            throw new IllegalStateException("Departing Rocket lost its exact Silo occupancy");
        }
        clearProblem();
        revision++;
    }

    /** Derives flight limits while treating service handoffs as ground transport. */
    private boolean configureDeliveryRoute(int next, OrbitalTransferPlanner.PoweredPlan candidates, Propulsion engine,
        OrbitalMechanics.CostModel model) {
        if (next != 1 && next != 3) {
            waitFor(Problem.SERVICE);
            return false;
        }
        var selected = candidates.candidates()
            .stream()
            .filter(
                candidate -> candidate.status() == OrbitalMechanics.LocalStatus.FEASIBLE
                    && candidate.gameSeconds() <= ConfigRocket.flight.maximumTransferSeconds
                    && billsFor(candidate) != null)
            .min(java.util.Comparator.comparingDouble(PoweredCandidate::totalDvMps))
            .orElse(null);
        var trip = execution.overrideProgram;
        var source = trip.visits()
            .get(execution.visitIndex);
        if (selected == null) {
            if (next == 3 && location.canRefuel() && destinationHasPropellant(location, engine)) {
                var full = VehicleSchedule.Refuel.full(UUID.randomUUID());
                boolean missing = refuelTargets(full).entrySet()
                    .stream()
                    .anyMatch(target -> propellantAmounts().getOrDefault(target.getKey(), 0L) < target.getValue());
                if (missing && source.actions()
                    .stream()
                    .noneMatch(VehicleSchedule.Refuel.class::isInstance)) {
                    var actions = new ArrayList<>(source.actions());
                    actions.add(full);
                    execution.overrideProgram = trip
                        .withVisit(new VehicleSchedule.Visit(source.id(), source.place(), actions));
                    waitFor(Problem.ACTION_RESOURCES);
                    return false;
                }
            }
            waitFor(Problem.NO_ROUTE);
            return false;
        }
        var destination = trip.visits()
            .get(next);
        trip = trip.withRouteSettings(
            new VehicleSchedule.Leg(source.id(), destination.id()),
            new VehicleSchedule.RouteSettings(
                selected.totalDvMps(),
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV));
        if (next == 1) {
            var reverse = returnAssessment(selected, destination.place(), engine, model);
            boolean refueling = reverse.candidate() == null;
            if (refueling && !destinationHasPropellant(delivery.plan.destination(), engine)) {
                waitFor(Problem.FUEL_BUDGET);
                return false;
            }
            var receiving = trip.visits()
                .get(2);
            var actions = new ArrayList<VehicleSchedule.Action>();
            for (var action : receiving.actions()) if (!(action instanceof VehicleSchedule.Refuel)) actions.add(action);
            if (refueling) actions.add(VehicleSchedule.Refuel.full(UUID.randomUUID()));
            trip = trip.withVisit(new VehicleSchedule.Visit(receiving.id(), receiving.place(), actions));
            var returnPad = trip.visits()
                .get(3);
            trip = trip.withRouteSettings(
                new VehicleSchedule.Leg(receiving.id(), returnPad.id()),
                new VehicleSchedule.RouteSettings(
                    refueling ? engine.usableDeltaVMps()
                        : reverse.candidate()
                            .totalDvMps(),
                    OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV));
        }
        execution.overrideProgram = trip;
        return true;
    }

    private boolean destinationHasPropellant(VehicleSchedule.Place destination, Propulsion engine) {
        var endpoint = CelestialAssetStore.findEndpoint(((VehicleSchedule.Exact) destination.target()).endpointId());
        if (endpoint == null || !matchesEndpoint(destination, endpoint)
            || !teamId.equals(CelestialAssetStore.getTeamId(endpoint.asset().assetId))) return false;
        var resources = endpoint.resources();
        return resources != null && engine.unitsPerKilogram()
            .keySet()
            .stream()
            .allMatch(key -> resources.updateContents(key, -1, true) > 0);
    }

    private record ReturnAssessment(double remainingDeltaVMps, boolean dataAvailable,
        @Nullable PoweredCandidate candidate) {}

    @Nullable
    private LaunchWarning returnRisk(PoweredCandidate outbound, VehicleSchedule.Place destination,
        VehicleSchedule.Leg leg, Propulsion engine, OrbitalMechanics.CostModel model) {
        var assessment = returnAssessment(outbound, destination, engine, model);
        return assessment.candidate() != null ? null
            : new LaunchWarning(
                leg,
                location,
                destination,
                outbound.totalDvMps(),
                assessment.remainingDeltaVMps(),
                assessment.dataAvailable());
    }

    private ReturnAssessment returnAssessment(PoweredCandidate outbound, VehicleSchedule.Place destination,
        Propulsion engine, OrbitalMechanics.CostModel model) {
        if (outbound.obligations()
            .isEmpty()) return new ReturnAssessment(engine.usableDeltaVMps(), true, outbound);
        var remaining = new LinkedHashMap<>(propellantAmounts());
        for (var bill : billsFor(outbound))
            bill.forEach((key, amount) -> remaining.compute(key, (ignored, value) -> value - amount));
        double usableKg = engine.unitsPerKilogram.entrySet()
            .stream()
            .mapToDouble(entry -> remaining.getOrDefault(entry.getKey(), 0L) / entry.getValue())
            .min()
            .orElse(0);
        var wear = wearFor(outbound);
        double returnThrust = modules.values()
            .stream()
            .filter(
                module -> module.part.def()
                    .engine() != null && module.durabilityPercent > wear.get(module.id))
            .mapToDouble(
                module -> module.part.def()
                    .engine()
                    .thrustNewtons())
            .sum();
        double remainingDv = engine.exhaustVelocityMps * Math.log1p(usableKg / engine.inertMassKg());
        if (returnThrust <= 0) return new ReturnAssessment(remainingDv, true, null);
        var vehicle = new OrbitalMechanics.LocalVehicle(
            engine.inertMassKg(),
            usableKg,
            engine.exhaustVelocityMps,
            returnThrust);
        var source = GalaxiaCelestialAPI.get(destination.body())
            .orElse(null);
        var target = GalaxiaCelestialAPI.get(location.body())
            .orElse(null);
        if (source == null || target == null) return new ReturnAssessment(remainingDv, false, null);
        double epoch = outbound.obligations()
            .getLast()
            .endEpochOsu();
        var reverse = OrbitalTransferPlanner.computePoweredCandidates(
            GalaxiaCelestialAPI.root(),
            source,
            target,
            epoch,
            destination.endpointKind(),
            outbound.finalParkingAngleRadians(),
            location.endpointKind(),
            1,
            vehicle,
            model);
        var candidate = reverse.candidates()
            .stream()
            .filter(
                route -> route.status() == OrbitalMechanics.LocalStatus.FEASIBLE
                    && route.gameSeconds() <= ConfigRocket.flight.maximumTransferSeconds
                    && billsFor(route, remaining) != null)
            .min(java.util.Comparator.comparingDouble(PoweredCandidate::totalDvMps))
            .orElse(null);
        boolean dataAvailable = reverse.status() != OrbitalMechanics.LocalStatus.UNAVAILABLE_DATA
            && reverse.status() != OrbitalMechanics.LocalStatus.NOT_FOUND;
        return new ReturnAssessment(remainingDv, dataAvailable, candidate);
    }

    private double remainingAllowance() {
        var schedule = program();
        double total = 0;
        int from = execution.visitIndex;
        for (int count = 0; count < schedule.visits()
            .size(); count++) {
            int to = from + 1;
            if (to == schedule.visits()
                .size()) {
                if (schedule.mode() == VehicleSchedule.Mode.RUN_ONCE) break;
                to = 0;
            }
            var settings = schedule.routes()
                .get(
                    new VehicleSchedule.Leg(
                        schedule.visits()
                            .get(from)
                            .id(),
                        schedule.visits()
                            .get(to)
                            .id()));
            if (settings == null || settings.allowance()
                .isEmpty()) return Double.NaN;
            total += settings.allowance()
                .getAsDouble();
            if (schedule.visits()
                .get(to)
                .actions()
                .stream()
                .anyMatch(
                    action -> action instanceof VehicleSchedule.Refuel refuel && (refuel.toFull() || refuel.targets()
                        .values()
                        .stream()
                        .anyMatch(amount -> amount > 0))))
                break;
            from = to;
        }
        return total;
    }

    @Nullable
    private List<Map<FluidKey, Long>> billsFor(PoweredCandidate candidate) {
        return billsFor(candidate, propellantAmounts());
    }

    @Nullable
    private List<Map<FluidKey, Long>> billsFor(PoweredCandidate candidate, Map<FluidKey, Long> supplies) {
        var available = new LinkedHashMap<>(supplies);
        var bills = new ArrayList<Map<FluidKey, Long>>();
        for (var phase : candidate.obligations()) {
            Map<FluidKey, Long> bill = fuelBill(phase.propellantKg());
            if (bill == null) return null;
            for (var debit : bill.entrySet()) {
                long left = available.getOrDefault(debit.getKey(), 0L) - debit.getValue();
                if (left < 0) return null;
                available.put(debit.getKey(), left);
            }
            bills.add(bill);
        }
        return List.copyOf(bills);
    }

    /** Charges whole mixture batches so rounding cannot consume one companion independently. */
    @Nullable
    private Map<FluidKey, Long> fuelBill(double kilograms) {
        if (kilograms == 0) return Map.of();
        var engine = modules.values()
            .stream()
            .map(
                module -> module.part.def()
                    .engine())
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);
        if (engine == null) return null;
        int scale = engine.propellants()
            .values()
            .stream()
            .mapToInt(
                value -> Math.max(
                    0,
                    BigDecimal.valueOf(value.volumeParts())
                        .stripTrailingZeros()
                        .scale()))
            .max()
            .orElse(0);
        var ratios = new LinkedHashMap<FluidKey, BigInteger>();
        BigInteger divisor = BigInteger.ZERO;
        for (var entry : engine.propellants()
            .entrySet()) {
            BigInteger integer = BigDecimal.valueOf(
                entry.getValue()
                    .volumeParts())
                .movePointRight(scale)
                .toBigIntegerExact();
            ratios.put(entry.getKey(), integer);
            divisor = divisor.gcd(integer);
        }
        double massPerBatch = 0;
        var units = new LinkedHashMap<FluidKey, Long>();
        try {
            for (var entry : ratios.entrySet()) {
                long amount = entry.getValue()
                    .divide(divisor)
                    .longValueExact();
                units.put(entry.getKey(), amount);
                massPerBatch += amount * engine.propellants()
                    .get(entry.getKey())
                    .kilogramsPerUnit();
            }
            double requiredBatches = Math.ceil(kilograms / massPerBatch);
            if (!Double.isFinite(requiredBatches) || requiredBatches >= Long.MAX_VALUE) return null;
            long batches = (long) requiredBatches;
            for (var entry : units.entrySet()) entry.setValue(Math.multiplyExact(entry.getValue(), batches));
        } catch (ArithmeticException invalidMixture) {
            return null;
        }
        return Map.copyOf(units);
    }

    private boolean debitFuel(Map<FluidKey, Long> bill) {
        var amounts = propellantAmounts();
        if (bill.entrySet()
            .stream()
            .anyMatch(entry -> amounts.getOrDefault(entry.getKey(), 0L) < entry.getValue())) return false;
        for (var entry : bill.entrySet()) {
            long remaining = entry.getValue();
            for (Module module : modules.values()) {
                FluidTank tank = module.propellant.get(entry.getKey());
                if (tank == null || remaining == 0) continue;
                var drained = tank.drain((int) Math.min(remaining, Integer.MAX_VALUE), true);
                if (drained != null) remaining -= drained.amount;
            }
            if (remaining != 0) throw new IllegalStateException("Accepted maneuver bill lost its propellant");
        }
        return true;
    }

    private void tickJourney(Registry registry) {
        var schedule = program();
        var journey = execution.journey;
        var destination = schedule.visits()
            .get(execution.destinationIndex)
            .place();
        if (execution.phaseIndex < journey.obligations()
            .size()) {
            var phase = journey.obligations()
                .get(execution.phaseIndex);
            boolean arrivalManeuver = phase.kind() == OrbitalMechanics.ManeuverKind.CAPTURE
                || phase.kind() == OrbitalMechanics.ManeuverKind.LANDING
                || phase.kind() == OrbitalMechanics.ManeuverKind.DOCKING;
            if (arrivalManeuver && destination.target() != null && !reserveArrival(registry, destination)) {
                waitFor(Problem.ENDPOINT_UNAVAILABLE);
                return;
            }
            if (!execution.phasePaid) {
                if (!debitFuel(execution.bills.get(execution.phaseIndex))) {
                    waitFor(Problem.PROPULSION);
                    return;
                }
                execution.phasePaid = true;
                revision++;
            }
            clearProblem();
            long ticks = Math.max(
                1,
                (long) Math.ceil((phase.endEpochOsu() - phase.startEpochOsu()) / OrbitalTransferPlanner.OSU_PER_TICK));
            if (++execution.phaseTicks < ticks) return;
            execution.phaseIndex++;
            if (phase.kind() == OrbitalMechanics.ManeuverKind.ASCENT
                || phase.kind() == OrbitalMechanics.ManeuverKind.LANDING) projection = null;
            execution.phaseTicks = 0;
            execution.phasePaid = false;
            revision++;
            return;
        }
        if (destination.target() != null && !reserveArrival(registry, destination)) {
            waitFor(Problem.ENDPOINT_UNAVAILABLE);
            return;
        }
        location = destination.target() == null ? destination
            : new VehicleSchedule.Place(
                destination.body(),
                destination.context(),
                new VehicleSchedule.Exact(
                    execution.arrivalEndpointId,
                    destination.target()
                        .kind()));
        if (destination.surfaceSite() != null) physicalPosition = destination.surfaceSite()
            .position();
        parkingAngleRadians = journey.finalParkingAngleRadians();
        execution.wear.forEach((moduleId, wear) -> {
            Module module = module(moduleId);
            module.durabilityPercent = Math.max(0, module.durabilityPercent - wear);
        });
        status = Status.PHYSICAL;
        var arrivedEndpoint = currentEndpointId() == null ? null
            : CelestialAssetStore.findEndpoint(currentEndpointId());
        var arrivalController = arrivedEndpoint == null ? null : TransportEndpoint.loaded(arrivedEndpoint);
        if (arrivalController != null && !arrivalController.acceptArrival(registry, this)) {
            throw new IllegalStateException("Committed arrival lost its exact transport endpoint");
        }
        projection = null;
        activateVisit(execution.destinationIndex);
    }

    private void activateVisit(int index) {
        execution.visitIndex = index;
        execution.destinationIndex = index;
        execution.occurrenceId = UUID.randomUUID();
        execution.finished.clear();
        execution.progress.clear();
        execution.journey = null;
        execution.bills = List.of();
        execution.wear = Map.of();
        execution.phaseIndex = 0;
        execution.phaseTicks = 0;
        execution.phasePaid = false;
        execution.arrivalEndpointId = null;
        execution.launchWarning = null;
        clearProblem();
        revision++;
    }

    private boolean reserveArrival(Registry registry, VehicleSchedule.Place destination) {
        if (execution.arrivalEndpointId != null) {
            var chosen = CelestialAssetStore.findEndpoint(execution.arrivalEndpointId);
            return chosen != null && matchesEndpoint(destination, chosen) && endpointHasSpace(registry, chosen);
        }
        for (var asset : CelestialAssetStore.getTeamAssets(teamId, destination.body())) {
            for (var endpoint : asset.endpoints()
                .values()
                .stream()
                .sorted(java.util.Comparator.comparing(CelestialAsset.Endpoint::id))
                .toList()) {
                var ref = new CelestialAssetStore.EndpointRef(asset, endpoint);
                if (matchesEndpoint(destination, ref) && endpointHasSpace(registry, ref)) {
                    execution.arrivalEndpointId = endpoint.id();
                    revision++;
                    return true;
                }
            }
        }
        return false;
    }

    private Map<UUID, Double> wearFor(PoweredCandidate journey) {
        double beltDistance = journey.beltDistanceMeters()
            .values()
            .stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        var wear = new LinkedHashMap<UUID, Double>();
        for (Module module : modules.values()) {
            wear.put(
                module.id,
                module.part.def()
                    .wearPercent(journey.traveledDistanceMeters(), beltDistance));
        }
        return Map.copyOf(wear);
    }

    private boolean coversMaintenanceInterval(PoweredCandidate first, int destinationIndex,
        OrbitalMechanics.CostModel model) {
        var schedule = program();
        var wear = new LinkedHashMap<>(wearFor(first));
        var awaitingService = new HashSet<>(modules.keySet());
        var maximumCondition = new LinkedHashMap<UUID, Double>();
        for (Module module : modules.values())
            maximumCondition.put(module.id, module.durabilityPercent - wear.get(module.id));
        int current = destinationIndex;
        var currentPlace = schedule.visits()
            .get(current)
            .place();
        for (int count = 0; count < schedule.visits()
            .size(); count++) {
            for (UUID moduleId : awaitingService) {
                if (module(moduleId).durabilityPercent - wear.get(moduleId)
                    < ConfigRocket.flight.minimumDurabilityReservePercent) return false;
            }
            for (var action : schedule.visits()
                .get(current)
                .actions()) {
                if (action instanceof VehicleSchedule.Repair repair && currentPlace.target() != null
                    && currentPlace.target()
                        .kind() == CelestialAsset.Endpoint.Kind.LANDING_PAD) {
                    awaitingService.removeIf(moduleId -> repair.qualifies(Math.max(0, maximumCondition.get(moduleId))));
                    if (repair.returnSiloId() != null) {
                        var receiving = CelestialAssetStore.findEndpoint(repair.returnSiloId());
                        if (receiving == null || receiving.endpoint()
                            .kind() != CelestialAsset.Endpoint.Kind.SILO
                            || !teamId.equals(CelestialAssetStore.getTeamId(receiving.asset().assetId))) return false;
                        currentPlace = VehicleSchedule.Place.at(receiving);
                    }
                }
            }
            if (awaitingService.isEmpty()) return true;
            int next = current + 1;
            if (next == schedule.visits()
                .size()) {
                if (schedule.mode() == VehicleSchedule.Mode.RUN_ONCE) return true;
                next = 0;
            }
            if (next == destinationIndex) return true;
            var nextPlace = schedule.visits()
                .get(next)
                .place();
            if (currentPlace.equals(nextPlace)) {
                current = next;
                continue;
            }
            var source = GalaxiaCelestialAPI.get(currentPlace.body())
                .orElse(null);
            var destination = GalaxiaCelestialAPI.get(nextPlace.body())
                .orElse(null);
            var route = schedule.routes()
                .get(
                    new VehicleSchedule.Leg(
                        schedule.visits()
                            .get(current)
                            .id(),
                        schedule.visits()
                            .get(next)
                            .id()));
            if (source == null || destination == null
                || route == null
                || route.allowance()
                    .isEmpty())
                return false;
            var bound = OrbitalTransferPlanner.maximumTravelExposure(
                GalaxiaCelestialAPI.root(),
                source,
                destination,
                currentPlace.endpointKind(),
                nextPlace.endpointKind(),
                route.allowance()
                    .getAsDouble(),
                model);
            if (bound == null) return false;
            for (UUID moduleId : awaitingService) wear.merge(
                moduleId,
                module(moduleId).part.def()
                    .wearPercent(bound.distanceMeters(), bound.beltDistanceMeters()),
                Double::sum);
            current = next;
            currentPlace = nextPlace;
        }
        return true;
    }

    private boolean matchesEndpoint(VehicleSchedule.Place place, CelestialAssetStore.EndpointRef ref) {
        if (place.target() == null || !place.body()
            .equals(ref.asset().celestialObjectKey)
            || !teamId.equals(CelestialAssetStore.getTeamId(ref.asset().assetId))
            || place.target()
                .kind()
                != ref.endpoint()
                    .kind())
            return false;
        if (place.target() instanceof VehicleSchedule.Exact exact) return exact.endpointId()
            .equals(
                ref.endpoint()
                    .id());
        var group = (VehicleSchedule.Group) place.target();
        return group.routingName()
            .equals(
                ref.endpoint()
                    .routingName())
            && (group.stationId() == null || group.stationId()
                .equals(ref.asset().assetId.id()));
    }

    private boolean endpointHasSpace(Registry registry, CelestialAssetStore.EndpointRef ref) {
        int capacity = ref.availableServiceSlots();
        if (capacity <= 0) return false;
        var controller = TransportEndpoint.loaded(ref);
        if (controller != null && !controller.reserveArrival(registry, this)) return false;
        long occupied = registry.all()
            .stream()
            .filter(
                other -> other != this && other.status != Status.LOST
                    && (ref.endpoint()
                        .id()
                        .equals(other.currentEndpointId())
                        || ref.endpoint()
                            .id()
                            .equals(other.reservedArrivalEndpointId())
                        || (other.service != null && !other.service.reassembled
                            && ref.endpoint()
                                .id()
                                .equals(other.service.returnSiloId))))
            .count();
        return occupied < capacity;
    }

    private void waitFor(Problem problem) {
        if (execution.problem != problem) {
            execution.problem = problem;
            revision++;
        }
        execution.retryTicks = 20;
    }

    private void clearProblem() {
        if (execution.problem != null) {
            execution.problem = null;
            revision++;
        }
        execution.retryTicks = 0;
    }

    public enum Problem {

        ACTION_RESOURCES("action_resources"),
        REFUEL_SILO("refuel_silo"),
        SERVICE("service"),
        PROPULSION("propulsion"),
        FUEL_BUDGET("fuel_budget"),
        UNAVAILABLE_BODY("unavailable_body"),
        NO_ROUTE("no_route"),
        ENDPOINT_UNAVAILABLE("endpoint_unavailable"),
        INCOMPLETE("incomplete"),
        DURABILITY("durability"),
        CONFIRMATION_REQUIRED("confirmation_required"),
        REVIEW_PAUSED("review_paused"),
        LANDING_SITE_BLOCKED("landing_site_blocked");

        private final String key;

        Problem(String name) {
            key = "galaxia.rocket.schedule.wait." + name;
        }
    }

    /** Vehicle-local execution state. It owns no copied modules, inventory or endpoint registry. */
    private enum ProgramOrigin {
        MANUAL_LANDING,
        DELIVERY
    }

    private static final class Execution {

        private ProgramOrigin origin = ProgramOrigin.MANUAL_LANDING;

        @Nullable
        private VehicleSchedule overrideProgram;

        private int visitIndex;
        private int destinationIndex;
        private UUID occurrenceId = UUID.randomUUID();
        private boolean completed;
        private final Set<UUID> finished = new HashSet<>();
        private final Map<UUID, Long> progress = new LinkedHashMap<>();
        @Nullable
        private PoweredCandidate journey;
        private List<Map<FluidKey, Long>> bills = List.of();
        private Map<UUID, Double> wear = Map.of();
        private int phaseIndex;
        private long phaseTicks;
        private boolean phasePaid;
        @Nullable
        private UUID arrivalEndpointId;
        @Nullable
        private Problem problem;
        private int retryTicks;
        @Nullable
        private PhysicalPosition sourcePosition;
        @Nullable
        private LaunchWarning launchWarning;
        private final Set<VehicleSchedule.Leg> acknowledgedRisks = new HashSet<>();
        private boolean reviewPaused;

        private NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            if (overrideProgram != null) out.setTag("manualProgram", overrideProgram.serializeNBT());
            if (overrideProgram != null) out.setString("programOrigin", origin.name());
            out.setInteger("visit", visitIndex);
            out.setInteger("destination", destinationIndex);
            out.setString("occurrence", occurrenceId.toString());
            out.setBoolean("completed", completed);
            out.setInteger("phase", phaseIndex);
            out.setLong("phaseTicks", phaseTicks);
            out.setBoolean("paid", phasePaid);
            out.setInteger("retry", retryTicks);
            out.setBoolean("reviewPaused", reviewPaused);
            if (launchWarning != null) out.setTag("launchWarning", launchWarning.serializeNBT());
            var acknowledgements = new NBTTagList();
            for (var leg : acknowledgedRisks) {
                acknowledgements.appendTag(leg.serializeNBT());
            }
            out.setTag("acknowledgedRisks", acknowledgements);
            if (problem != null) out.setString("problem", problem.name());
            if (arrivalEndpointId != null) out.setString("arrival", arrivalEndpointId.toString());
            if (journey != null) out.setTag("journey", journey.serializeNBT());
            if (sourcePosition != null) out.setTag("sourcePosition", sourcePosition.serializeNBT());
            var actions = new NBTTagList();
            var ids = new HashSet<>(progress.keySet());
            ids.addAll(finished);
            for (UUID id : ids) {
                var action = new NBTTagCompound();
                action.setString("id", id.toString());
                action.setLong("progress", progress.getOrDefault(id, 0L));
                action.setBoolean("finished", finished.contains(id));
                actions.appendTag(action);
            }
            out.setTag("actions", actions);
            var payments = new NBTTagList();
            for (var bill : bills) {
                var payment = new NBTTagCompound();
                var resources = new NBTTagList();
                bill.forEach((key, amount) -> {
                    var resource = InventoryKeyState.encode(key);
                    resource.setLong("amount", amount);
                    resources.appendTag(resource);
                });
                payment.setTag("resources", resources);
                payments.appendTag(payment);
            }
            out.setTag("bills", payments);
            var damage = new NBTTagList();
            wear.forEach((moduleId, percent) -> {
                var module = new NBTTagCompound();
                module.setString("module", moduleId.toString());
                module.setDouble("wear", percent);
                damage.appendTag(module);
            });
            out.setTag("wear", damage);
            return out;
        }

        private static Execution deserializeNBT(NbtReader in, VehicleSchedule schedule) {
            var state = new Execution();
            if (in.tag()
                .hasKey("manualProgram")) {
                state.overrideProgram = VehicleSchedule.deserializeNBT(
                    in.compound("manualProgram")
                        .tag());
                if (in.tag()
                    .hasKey("programOrigin")) state.origin = in.enumValue(ProgramOrigin.class, "programOrigin");
                if (state.origin == ProgramOrigin.MANUAL_LANDING
                    && (state.overrideProgram.mode() != VehicleSchedule.Mode.RUN_ONCE || state.overrideProgram.visits()
                        .size() != 2
                        || state.overrideProgram.visits()
                            .getLast()
                            .place()
                            .surfaceSite() == null
                        || state.overrideProgram.visits()
                            .stream()
                            .anyMatch(
                                visit -> !visit.actions()
                                    .isEmpty())))
                    throw new IllegalStateException("Invalid accepted manual mission");
                schedule = state.overrideProgram;
            }
            state.visitIndex = in.integer(
                "visit",
                0,
                schedule.visits()
                    .size() - 1);
            state.destinationIndex = in.integer(
                "destination",
                0,
                schedule.visits()
                    .size() - 1);
            state.occurrenceId = UUID.fromString(in.string("occurrence"));
            state.completed = in.bool("completed");
            state.phaseIndex = in.integer("phase");
            state.phaseTicks = in.longValue("phaseTicks");
            state.phasePaid = in.bool("paid");
            state.retryTicks = in.integer("retry", 0, 20);
            state.reviewPaused = in.bool("reviewPaused");
            if (in.tag()
                .hasKey("launchWarning"))
                state.launchWarning = LaunchWarning.deserializeNBT(in.compound("launchWarning"));
            var acknowledgements = in.compounds("acknowledgedRisks");
            for (int i = 0; i < acknowledgements.tagCount(); i++) {
                var acknowledged = in.element("acknowledgedRisks", i, acknowledgements.getCompoundTagAt(i));
                var leg = VehicleSchedule.Leg.deserializeNBT(acknowledged.tag());
                if (!schedule.routes()
                    .containsKey(leg) || !state.acknowledgedRisks.add(leg))
                    throw new IllegalStateException("Invalid launch acknowledgement");
            }
            if (in.tag()
                .hasKey("problem")) state.problem = in.enumValue(Problem.class, "problem");
            if (in.tag()
                .hasKey("arrival")) state.arrivalEndpointId = UUID.fromString(in.string("arrival"));
            if (in.tag()
                .hasKey("journey"))
                state.journey = PoweredCandidate.deserializeNBT(
                    in.compound("journey")
                        .tag());
            if (in.tag()
                .hasKey("sourcePosition"))
                state.sourcePosition = PhysicalPosition.deserializeNBT(in.compound("sourcePosition"));
            var actions = in.compounds("actions");
            var validActions = schedule.visits()
                .get(state.visitIndex)
                .actions()
                .stream()
                .map(VehicleSchedule.Action::id)
                .collect(java.util.stream.Collectors.toSet());
            for (int i = 0; i < actions.tagCount(); i++) {
                var action = in.element("actions", i, actions.getCompoundTagAt(i));
                UUID id = UUID.fromString(action.string("id"));
                long progress = action.longValue("progress");
                if (!validActions.contains(id) || progress < 0 || state.progress.put(id, progress) != null)
                    throw new IllegalStateException("Invalid mission action receipt");
                if (action.bool("finished")) state.finished.add(id);
            }
            var payments = in.compounds("bills");
            var bills = new ArrayList<Map<FluidKey, Long>>();
            for (int i = 0; i < payments.tagCount(); i++) {
                var payment = in.element("bills", i, payments.getCompoundTagAt(i));
                var bill = new LinkedHashMap<FluidKey, Long>();
                var resources = payment.compounds("resources");
                for (int j = 0; j < resources.tagCount(); j++) {
                    var resource = payment.element("resources", j, resources.getCompoundTagAt(j));
                    var key = InventoryKeyState.decode(resource.tag());
                    long amount = resource.longValue("amount");
                    if (!(key instanceof FluidKey fluid) || amount <= 0 || bill.put(fluid, amount) != null)
                        throw new IllegalStateException("Invalid maneuver payment");
                }
                bills.add(Map.copyOf(bill));
            }
            state.bills = List.copyOf(bills);
            var encodedWear = in.compounds("wear");
            var wear = new LinkedHashMap<UUID, Double>();
            for (int i = 0; i < encodedWear.tagCount(); i++) {
                var module = in.element("wear", i, encodedWear.getCompoundTagAt(i));
                double percent = module.doubleValue("wear");
                if (!Double.isFinite(percent) || percent < 0
                    || wear.put(UUID.fromString(module.string("module")), percent) != null)
                    throw new IllegalStateException("Invalid journey wear");
            }
            state.wear = Map.copyOf(wear);
            int phases = state.journey == null ? 0
                : state.journey.obligations()
                    .size();
            if (state.phaseIndex < 0 || state.phaseIndex > phases
                || state.phaseTicks < 0
                || state.bills.size() != phases
                || (state.phasePaid && state.phaseIndex == phases))
                throw new IllegalStateException("Invalid mission maneuver cursor");
            return state;
        }
    }

    @Nonnull
    public Module module(@Nonnull UUID moduleId) {
        Module module = modules.get(moduleId);
        if (module == null) throw new IllegalArgumentException("Module does not belong to this Rocket");
        return module;
    }

    /** Destruction replaces custody with a one-use item claim. Contents remain on the same concrete module. */
    public List<ModuleDrop> dropModules(@Nonnull Holder destroyedHolder) {
        var drops = new ArrayList<ModuleDrop>();
        UUID endpoint = currentEndpointId();
        boolean installedHere = destroyedHolder.id.equals(endpoint)
            && (destroyedHolder.kind == HolderKind.PAD_BUFFER || destroyedHolder.kind == HolderKind.SILO_BUFFER);
        for (Module module : modules.values()) {
            if (!module.holder.equals(destroyedHolder)
                && !(installedHere && module.holder.equals(new Holder(HolderKind.INSTALLED, id)))) continue;
            module.pendingTransfer = null;
            module.gantryTransport = null;
            UUID token = UUID.randomUUID();
            var destination = new Holder(HolderKind.DROPPED, token);
            var handoff = beginTransfer(module.id, token, module.holder, destination);
            if (confirmTransfer(handoff, destination) != TransferResult.ACCEPTED)
                throw new IllegalStateException("Destroyed holder lost its module");
            moduleWork.remove(module.id);
            drops.add(new ModuleDrop(id, module.id, token));
        }
        if (!drops.isEmpty()) {
            status = Status.INCOMPLETE;
            projection = null;
            if (execution != null) execution.launchWarning = null;
            if (execution != null) waitFor(Problem.INCOMPLETE);
            revision++;
        }
        return List.copyOf(drops);
    }

    /**
     * Explicit player recovery into a real Pad and selected Silo. Duplicate item claims cannot adopt a module twice.
     */
    public boolean recoverDrop(@Nonnull UUID actorTeam, @Nonnull ModuleDrop drop,
        @Nonnull VehicleSchedule.Place padPlace, @Nonnull UUID receivingSiloId) {
        if (!teamId.equals(actorTeam) || !id.equals(drop.vehicleId)
            || !bodyKey().equals(padPlace.body())
            || !(padPlace.target() instanceof VehicleSchedule.Exact exact)
            || exact.kind() != CelestialAsset.Endpoint.Kind.LANDING_PAD) return false;
        UUID padId = exact.endpointId();
        Module module = modules.get(drop.moduleId);
        if (module == null || !module.holder.equals(new Holder(HolderKind.DROPPED, drop.token))) return false;
        var pad = new Holder(HolderKind.PAD_BUFFER, padId);
        boolean activeService = service != null && !service.reassembled;
        boolean sameService = activeService && service.padId.equals(padId)
            && service.returnSiloId.equals(receivingSiloId);
        if (!sameService && modules.values()
            .stream()
            .anyMatch(other -> other.holder.kind != HolderKind.DROPPED && !other.holder.equals(pad))) return false;
        if (!sameService) {
            var returns = new LinkedHashMap<UUID, UUID>();
            for (Module other : modules.values()) returns.put(other.id, UUID.randomUUID());
            service = new Service(
                activeService ? service.id : UUID.randomUUID(),
                activeService ? service.actionId : UUID.randomUUID(),
                padId,
                receivingSiloId,
                activeService ? service.repairs : Map.of(),
                returns,
                activeService ? service.repaired : Set.of(),
                false);
            moduleWork.keySet()
                .removeIf(modules::containsKey);
        }
        var transfer = beginTransfer(module.id, drop.token, module.holder, pad);
        if (confirmTransfer(transfer, pad) != TransferResult.ACCEPTED) return false;
        location = padPlace;
        if (modules.values()
            .stream()
            .noneMatch(other -> other.holder.kind == HolderKind.DROPPED)) status = Status.DISASSEMBLED;
        revision++;
        return true;
    }

    public record ModuleDrop(@Nonnull UUID vehicleId, @Nonnull UUID moduleId, @Nonnull UUID token) {

        public NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("vehicle", vehicleId.toString());
            out.setString("module", moduleId.toString());
            out.setString("token", token.toString());
            return out;
        }

        public static ModuleDrop deserializeNBT(NBTTagCompound encoded) {
            var in = new NbtReader(encoded, "droppedRocketModule");
            return new ModuleDrop(
                UUID.fromString(in.string("vehicle")),
                UUID.fromString(in.string("module")),
                UUID.fromString(in.string("token")));
        }
    }

    /** Adopts only the exact paid output reserved by this vehicle's construction order. */
    public boolean acceptProduced(@Nonnull Module module) {
        if (!id.equals(module.vehicleId)) return false;
        BuildSlot slot = buildSlots.stream()
            .filter(candidate -> candidate.requestId.equals(module.productionRequestId))
            .findFirst()
            .orElse(null);
        if (slot == null || !slot.moduleId.equals(module.id) || !slot.part.equals(module.part)) return false;
        Module previous = modules.get(module.id);
        if (previous != null) {
            return previous.productionRequestId.equals(module.productionRequestId);
        }
        modules.put(module.id, module);
        return true;
    }

    @Nonnull
    public Transfer beginTransfer(@Nonnull UUID moduleId, @Nonnull UUID workId, @Nonnull Holder source,
        @Nonnull Holder destination) {
        Module module = module(moduleId);
        if (!module.holder.equals(source)) {
            throw new IllegalStateException("Transfer source does not hold this module");
        }
        if (module.pendingTransfer != null) {
            Transfer pending = module.pendingTransfer;
            if (pending.workId.equals(workId) && pending.source.equals(source)
                && pending.destination.equals(destination)) return pending;
            throw new IllegalStateException("Module already has an accepted transfer");
        }
        if (module.gantryTransport != null) {
            var transport = module.gantryTransport;
            if (!transport.workId.equals(workId))
                throw new IllegalStateException("Transfer does not belong to the reserved trip");
            int next = transport.routeIndex + 1;
            Holder expected = next < transport.reservedRoute.size()
                ? new Holder(HolderKind.GANTRY, transport.reservedRoute.get(next))
                : transport.target.receiver;
            if (!destination.equals(expected))
                throw new IllegalStateException("Transfer skips the reserved Gantry route");
        }
        module.pendingTransfer = new Transfer(
            UUID.randomUUID(),
            id,
            moduleId,
            workId,
            module.transferSequence + 1,
            source,
            destination);
        return module.pendingTransfer;
    }

    public TransferResult confirmTransfer(@Nonnull Transfer transfer, @Nonnull Holder receiver) {
        if (!id.equals(transfer.vehicleId) || !receiver.equals(transfer.destination)) return TransferResult.REJECTED;
        Module module = modules.get(transfer.moduleId);
        if (module == null) return TransferResult.REJECTED;
        if (transfer.equals(module.lastAcceptedTransfer)) return TransferResult.ALREADY_ACCEPTED;
        if (!transfer.equals(module.pendingTransfer) || !module.holder.equals(transfer.source)) {
            return TransferResult.REJECTED;
        }
        module.holder = receiver;
        module.transferSequence = transfer.sequence;
        module.lastAcceptedTransfer = transfer;
        module.pendingTransfer = null;
        if (module.gantryTransport != null) {
            var transport = module.gantryTransport;
            module.gantryTransport = new GantryTransport(
                transport.workId,
                transport.target,
                transport.reservedRoute,
                receiver.kind == HolderKind.GANTRY ? transport.routeIndex + 1 : transport.routeIndex,
                0);
        }
        return TransferResult.ACCEPTED;
    }

    public boolean beginGantryTransport(@Nonnull UUID moduleId, @Nonnull UUID workId, @Nonnull Holder source,
        @Nonnull TerminalTarget target, @Nonnull List<UUID> reservedRoute) {
        Module module = module(moduleId);
        if (module.gantryTransport != null) {
            var existing = module.gantryTransport;
            return existing.workId.equals(workId) && existing.target.equals(target)
                && existing.reservedRoute.equals(reservedRoute);
        }
        if (!module.holder.equals(source) || module.pendingTransfer != null) return false;
        int startIndex = source.kind == HolderKind.GANTRY ? 0 : -1;
        var trip = new GantryTransport(workId, target, reservedRoute, startIndex, 0);
        if (source.kind == HolderKind.GANTRY && !source.id.equals(trip.reservedRoute.getFirst())) return false;
        if (!target.terminalId.equals(trip.reservedRoute.getLast())) return false;
        module.gantryTransport = trip;
        return true;
    }

    public boolean advanceGantryProgress(@Nonnull UUID moduleId, double delta) {
        if (!Double.isFinite(delta) || delta < 0)
            throw new IllegalArgumentException("Transport progress must be nonnegative and finite");
        Module module = module(moduleId);
        var transport = module.gantryTransport;
        if (transport == null || module.pendingTransfer == null || module.holder.kind != HolderKind.GANTRY)
            return false;
        double progress = Math.min(1, transport.progress + delta);
        module.gantryTransport = new GantryTransport(
            transport.workId,
            transport.target,
            transport.reservedRoute,
            transport.routeIndex,
            progress);
        return progress == 1;
    }

    public boolean finishGantryTransport(@Nonnull UUID moduleId, @Nonnull UUID workId) {
        Module module = module(moduleId);
        var transport = module.gantryTransport;
        if (transport == null) {
            return module.lastAcceptedTransfer != null && module.lastAcceptedTransfer.workId.equals(workId)
                && module.lastAcceptedTransfer.destination.equals(module.holder);
        }
        if (!transport.workId.equals(workId) || !transport.target.receiver.equals(module.holder)
            || module.pendingTransfer != null) return false;
        module.gantryTransport = null;
        return true;
    }

    @Nonnull
    public NBTTagCompound serializeNBT() {
        var out = new NBTTagCompound();
        out.setString("id", id.toString());
        out.setString("team", teamId.toString());
        out.setString("assemblySilo", assemblySiloId.toString());
        out.setTag("location", location.serializeNBT());
        out.setTag("schedule", schedule.serializeNBT());
        out.setLong("revision", revision);
        out.setString("status", status.name());
        out.setLong("projectionGeneration", projectionGeneration);
        if (projection != null) out.setString("entity", projection.entityId.toString());
        if (service != null) out.setTag("service", service.serializeNBT());
        if (execution != null) out.setTag("execution", execution.serializeNBT());
        if (delivery != null) out.setTag("delivery", delivery.serializeNBT());
        if (physicalPosition != null) out.setTag("physicalPosition", physicalPosition.serializeNBT());
        out.setDouble("parkingAngle", parkingAngleRadians);
        var crew = new NBTTagList();
        for (UUID passenger : passengers) crew.appendTag(new NBTTagString(passenger.toString()));
        out.setTag("passengers", crew);
        var work = new NBTTagList();
        for (ModuleWork claim : moduleWork.values()) work.appendTag(claim.serializeNBT());
        out.setTag("work", work);
        var slots = new NBTTagList();
        for (BuildSlot slot : buildSlots) {
            var encoded = new NBTTagCompound();
            encoded.setString("request", slot.requestId.toString());
            encoded.setString("module", slot.moduleId.toString());
            encoded.setTag("part", slot.part.serialize());
            slots.appendTag(encoded);
        }
        out.setTag("buildSlots", slots);
        var concrete = new NBTTagList();
        for (Module module : modules.values()) {
            var encoded = new NBTTagCompound();
            encoded.setString("id", module.id.toString());
            encoded.setDouble("durability", module.durabilityPercent);
            encoded.setTag("holder", module.holder.serializeNBT());
            encoded.setLong("sequence", module.transferSequence);
            encoded.setTag("cargo", module.cargo.serializeNBT());
            encoded.setTag("freight", module.freight.writeToNBT(new NBTTagCompound()));
            var propellant = new NBTTagList();
            module.propellant.forEach((key, tank) -> {
                var compartment = InventoryKeyState.encode(key);
                compartment.setTag("tank", tank.writeToNBT(new NBTTagCompound()));
                propellant.appendTag(compartment);
            });
            encoded.setTag("propellant", propellant);
            if (module.gantryTransport != null) encoded.setTag("transport", module.gantryTransport.serializeNBT());
            if (module.pendingTransfer != null) encoded.setTag("pending", module.pendingTransfer.serializeNBT());
            if (module.lastAcceptedTransfer != null)
                encoded.setTag("accepted", module.lastAcceptedTransfer.serializeNBT());
            concrete.appendTag(encoded);
        }
        out.setTag("modules", concrete);
        return out;
    }

    @Nonnull
    public static Rocket deserializeNBT(@Nonnull NBTTagCompound encoded, @Nonnull RocketPartRegistry definitions) {
        var in = new NbtReader(encoded, "rocket");
        var slots = new ArrayList<BuildSlot>();
        var requests = new HashSet<UUID>();
        var reserved = new LinkedHashMap<UUID, BuildSlot>();
        var encodedSlots = in.compounds("buildSlots");
        for (int i = 0; i < encodedSlots.tagCount(); i++) {
            var slot = in.element("buildSlots", i, encodedSlots.getCompoundTagAt(i));
            var part = RocketPartInstance.deserialize(
                slot.compound("part")
                    .tag(),
                definitions);
            if (part == null) throw new IllegalStateException("Unknown Rocket module definition");
            var decoded = new BuildSlot(
                UUID.fromString(slot.string("request")),
                UUID.fromString(slot.string("module")),
                part);
            if (!requests.add(decoded.requestId) || reserved.put(decoded.moduleId, decoded) != null) {
                throw new IllegalStateException("Duplicate Rocket construction identity");
            }
            slots.add(decoded);
        }
        if (slots.isEmpty()) throw new IllegalStateException("Rocket construction requires a non-empty design");
        var location = VehicleSchedule.Place.deserializeNBT(
            in.compound("location")
                .tag());
        var rocket = new Rocket(
            UUID.fromString(in.string("id")),
            UUID.fromString(in.string("team")),
            location.body(),
            UUID.fromString(in.string("assemblySilo")),
            slots);
        rocket.location = location;
        if (in.tag()
            .hasKey("physicalPosition"))
            rocket.physicalPosition = PhysicalPosition.deserializeNBT(in.compound("physicalPosition"));
        rocket.parkingAngleRadians = in.doubleValue("parkingAngle");
        if (!Double.isFinite(rocket.parkingAngleRadians)) throw new IllegalStateException("Invalid parking angle");
        var crew = in.strings("passengers");
        for (int i = 0; i < crew.tagCount(); i++) {
            if (!rocket.passengers.add(UUID.fromString(crew.getStringTagAt(i))))
                throw new IllegalStateException("Duplicate passenger");
        }
        rocket.schedule = VehicleSchedule.deserializeNBT(
            in.compound("schedule")
                .tag());
        rocket.revision = in.longValue("revision");
        if (rocket.revision < 0) throw new IllegalStateException("Negative Rocket revision");
        rocket.status = in.enumValue(Status.class, "status");
        if (in.tag()
            .hasKey("execution"))
            rocket.execution = Execution.deserializeNBT(in.compound("execution"), rocket.schedule);
        if (in.tag()
            .hasKey("delivery"))
            rocket.delivery = DeliveryJob.deserializeNBT(
                in.compound("delivery")
                    .tag());
        rocket.projectionGeneration = in.longValue("projectionGeneration");
        if (rocket.projectionGeneration < 0) throw new IllegalStateException("Negative Rocket projection generation");
        if (in.tag()
            .hasKey("entity")) {
            rocket.projection = new Projection(UUID.fromString(in.string("entity")), rocket.projectionGeneration);
            if (!rocket.allowsProjection())
                throw new IllegalStateException("Nonphysical Rocket has an entity projection");
        }
        if (in.tag()
            .hasKey("service")) rocket.service = Service.deserializeNBT(in.compound("service"));
        var encodedModules = in.compounds("modules");
        for (int i = 0; i < encodedModules.tagCount(); i++) {
            var module = in.element("modules", i, encodedModules.getCompoundTagAt(i));
            UUID moduleId = UUID.fromString(module.string("id"));
            BuildSlot slot = reserved.get(moduleId);
            if (slot == null || rocket.modules.containsKey(moduleId)) {
                throw new IllegalStateException("Unreserved or duplicate Rocket module");
            }
            var restored = Module.produced(
                rocket.id,
                slot,
                module.doubleValue("durability"),
                Holder.deserializeNBT(module.compound("holder")));
            restored.transferSequence = module.longValue("sequence");
            if (module.tag()
                .hasKey("pending")) restored.pendingTransfer = Transfer.deserializeNBT(module.compound("pending"));
            if (module.tag()
                .hasKey("accepted"))
                restored.lastAcceptedTransfer = Transfer.deserializeNBT(module.compound("accepted"));
            restored.validateTransfers();
            restored.readStores(module);
            if (module.tag()
                .hasKey("transport")) {
                restored.gantryTransport = GantryTransport.deserializeNBT(module.compound("transport"));
                restored.validateTransport();
            }
            rocket.modules.put(moduleId, restored);
        }
        var claims = in.compounds("work");
        for (int i = 0; i < claims.tagCount(); i++) {
            var claim = ModuleWork.deserializeNBT(in.element("work", i, claims.getCompoundTagAt(i)));
            if (rocket.workFor(claim.moduleId) != null || !rocket.reserveWork(claim))
                throw new IllegalStateException("Invalid or duplicate Rocket module work");
        }
        if (rocket.service != null && (!rocket.service.returns.keySet()
            .equals(rocket.modules.keySet())
            || (!rocket.service.reassembled && rocket.status != Status.DISASSEMBLED
                && rocket.status != Status.INCOMPLETE))) {
            throw new IllegalStateException("Service does not match the vehicle lifecycle or assigned modules");
        }
        return rocket;
    }

    /** Reserved output identity does not itself constitute a manufactured module. */
    public record BuildSlot(@Nonnull UUID requestId, @Nonnull UUID moduleId, @Nonnull RocketPartInstance part) {}

    public enum Status {
        CONSTRUCTING,
        PHYSICAL,
        DISASSEMBLED,
        IN_FLIGHT,
        INCOMPLETE,
        LOST
    }

    public record Projection(@Nonnull UUID entityId, long generation) {

        public Projection {
            if (generation <= 0) throw new IllegalArgumentException("Projection generation must be positive");
        }
    }

    public enum ServiceResult {
        ACCEPTED,
        SKIPPED,
        ALREADY_ACTIVE,
        COMPLETED,
        CONFLICT
    }

    /** Frozen work identities and completion for one physical turnaround. */
    public record Service(@Nonnull UUID id, @Nonnull UUID actionId, @Nonnull UUID padId, @Nonnull UUID returnSiloId,
        @Nonnull Map<UUID, UUID> repairs, @Nonnull Map<UUID, UUID> returns, @Nonnull Set<UUID> repaired,
        boolean reassembled) {

        public Service {
            repairs = Map.copyOf(repairs);
            returns = Map.copyOf(returns);
            repaired = Set.copyOf(repaired);
            if (!returns.keySet()
                .containsAll(repairs.keySet())
                || !repairs.keySet()
                    .containsAll(repaired))
                throw new IllegalArgumentException("Invalid service module assignment");
        }

        private NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("id", id.toString());
            out.setString("action", actionId.toString());
            out.setString("pad", padId.toString());
            out.setString("silo", returnSiloId.toString());
            out.setBoolean("reassembled", reassembled);
            var modules = new NBTTagList();
            returns.forEach((moduleId, workId) -> {
                var entry = new NBTTagCompound();
                entry.setString("module", moduleId.toString());
                entry.setString("return", workId.toString());
                if (repairs.containsKey(moduleId)) entry.setString(
                    "repair",
                    repairs.get(moduleId)
                        .toString());
                entry.setBoolean("repaired", repaired.contains(moduleId));
                modules.appendTag(entry);
            });
            out.setTag("modules", modules);
            return out;
        }

        private static Service deserializeNBT(NbtReader in) {
            var repairs = new LinkedHashMap<UUID, UUID>();
            var returns = new LinkedHashMap<UUID, UUID>();
            var repaired = new HashSet<UUID>();
            var entries = in.compounds("modules");
            for (int i = 0; i < entries.tagCount(); i++) {
                var entry = in.element("modules", i, entries.getCompoundTagAt(i));
                UUID moduleId = UUID.fromString(entry.string("module"));
                if (returns.put(moduleId, UUID.fromString(entry.string("return"))) != null) {
                    throw new IllegalStateException("Duplicate service module");
                }
                if (entry.tag()
                    .hasKey("repair")) repairs.put(moduleId, UUID.fromString(entry.string("repair")));
                if (entry.bool("repaired")) repaired.add(moduleId);
            }
            return new Service(
                UUID.fromString(in.string("id")),
                UUID.fromString(in.string("action")),
                UUID.fromString(in.string("pad")),
                UUID.fromString(in.string("silo")),
                repairs,
                returns,
                repaired,
                in.bool("reassembled"));
        }
    }

    public record ModuleWork(@Nonnull UUID workId, @Nonnull UUID moduleId, @Nonnull TerminalTarget machine,
        @Nonnull WorkBill bill) {

        private NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("work", workId.toString());
            out.setString("module", moduleId.toString());
            out.setTag("machine", machine.serializeNBT());
            out.setTag("bill", bill.serializeNBT());
            return out;
        }

        private static ModuleWork deserializeNBT(NbtReader in) {
            return new ModuleWork(
                UUID.fromString(in.string("work")),
                UUID.fromString(in.string("module")),
                TerminalTarget.deserializeNBT(in.compound("machine")),
                WorkBill.deserializeNBT(
                    in.compound("bill")
                        .tag()));
        }
    }

    public enum HolderKind {
        ASSEMBLER,
        PAD_BUFFER,
        GANTRY,
        SILO_BUFFER,
        INSTALLED,
        DROPPED,
        LOST
    }

    public record Holder(@Nonnull HolderKind kind, @Nonnull UUID id) {

        private NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("kind", kind.name());
            out.setString("id", id.toString());
            return out;
        }

        private static Holder deserializeNBT(NbtReader in) {
            return new Holder(in.enumValue(HolderKind.class, "kind"), UUID.fromString(in.string("id")));
        }
    }

    public record TerminalTarget(@Nonnull UUID terminalId, @Nonnull UUID controllerId, int dimension, int x, int y,
        int z, @Nonnull Holder receiver) {

        private NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("terminal", terminalId.toString());
            out.setString("controller", controllerId.toString());
            out.setInteger("dimension", dimension);
            out.setInteger("x", x);
            out.setInteger("y", y);
            out.setInteger("z", z);
            out.setTag("receiver", receiver.serializeNBT());
            return out;
        }

        private static TerminalTarget deserializeNBT(NbtReader in) {
            return new TerminalTarget(
                UUID.fromString(in.string("terminal")),
                UUID.fromString(in.string("controller")),
                in.integer("dimension"),
                in.integer("x"),
                in.integer("y"),
                in.integer("z"),
                Holder.deserializeNBT(in.compound("receiver")));
        }
    }

    public record GantryTransport(@Nonnull UUID workId, @Nonnull TerminalTarget target,
        @Nonnull List<UUID> reservedRoute, int routeIndex, double progress) {

        public GantryTransport {
            reservedRoute = List.copyOf(reservedRoute);
            if (reservedRoute.isEmpty() || new HashSet<>(reservedRoute).size() != reservedRoute.size()
                || routeIndex < -1
                || routeIndex >= reservedRoute.size()
                || !Double.isFinite(progress)
                || progress < 0
                || progress > 1) {
                throw new IllegalArgumentException("Invalid reserved Gantry trip");
            }
        }

        private NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("work", workId.toString());
            out.setTag("target", target.serializeNBT());
            var route = new NBTTagList();
            for (UUID node : reservedRoute) route.appendTag(new NBTTagString(node.toString()));
            out.setTag("route", route);
            out.setInteger("index", routeIndex);
            out.setDouble("progress", progress);
            return out;
        }

        private static GantryTransport deserializeNBT(NbtReader in) {
            var route = new ArrayList<UUID>();
            var nodes = in.strings("route");
            for (int i = 0; i < nodes.tagCount(); i++) route.add(UUID.fromString(nodes.getStringTagAt(i)));
            return new GantryTransport(
                UUID.fromString(in.string("work")),
                TerminalTarget.deserializeNBT(in.compound("target")),
                route,
                in.integer("index"),
                in.doubleValue("progress"));
        }
    }

    public enum TransferResult {
        ACCEPTED,
        ALREADY_ACCEPTED,
        REJECTED
    }

    public enum CommandResult {
        APPLIED,
        FORBIDDEN,
        STALE,
        BLOCKED
    }

    /** Only the schedule and client-visible execution state are synchronized to its editor. */
    public record ScheduleView(@Nonnull UUID vehicleId, long revision, @Nonnull VehicleSchedule schedule,
        @Nonnull Status status, @Nonnull VehicleSchedule.Place place, @Nullable UUID activeVisitId,
        @Nonnull Set<UUID> lockedVisitIds, @Nullable String problemKey,
        @Nullable OrbitalMechanics.LocalVehicle propulsion, @Nonnull Map<FluidKey, Long> propellant,
        @Nonnull Map<InventoryKey, Long> cargo, @Nonnull List<ModuleView> modules, int requiredModules,
        @Nullable UUID returnSiloId, @Nullable LaunchWarning launchWarning, @Nullable VehicleSchedule manualMission,
        @Nullable DeliveryView delivery, boolean running) {

        public ScheduleView {
            lockedVisitIds = Set.copyOf(lockedVisitIds);
            propellant = Map.copyOf(propellant);
            cargo = Map.copyOf(cargo);
            modules = List.copyOf(modules);
            if (revision < 0) throw new IllegalArgumentException("Schedule revision must be nonnegative");
            if (requiredModules < modules.size() || propellant.values()
                .stream()
                .anyMatch(amount -> amount < 0)
                || cargo.values()
                    .stream()
                    .anyMatch(amount -> amount < 0))
                throw new IllegalArgumentException("Invalid vehicle summary");
        }

        public NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("vehicle", vehicleId.toString());
            out.setLong("revision", revision);
            out.setTag("schedule", schedule.serializeNBT());
            out.setString("status", status.name());
            out.setTag("place", place.serializeNBT());
            if (activeVisitId != null) out.setString("activeVisit", activeVisitId.toString());
            if (problemKey != null) out.setString("problem", problemKey);
            var locked = new NBTTagList();
            lockedVisitIds.forEach(visit -> locked.appendTag(new NBTTagString(visit.toString())));
            out.setTag("locked", locked);
            if (propulsion != null) {
                var engine = new NBTTagCompound();
                engine.setDouble("inertMassKg", propulsion.inertMassKg());
                engine.setDouble("usablePropellantKg", propulsion.usablePropellantKg());
                engine.setDouble("exhaustVelocityMps", propulsion.exhaustVelocityMps());
                engine.setDouble("thrustNewtons", propulsion.thrustNewtons());
                out.setTag("propulsion", engine);
            }
            out.setTag("propellant", InventoryKeyState.encodeAmounts(propellant));
            out.setTag("cargo", InventoryKeyState.encodeAmounts(cargo));
            var parts = new NBTTagList();
            modules.forEach(module -> parts.appendTag(module.serializeNBT()));
            out.setTag("modules", parts);
            out.setInteger("requiredModules", requiredModules);
            if (returnSiloId != null) out.setString("returnSilo", returnSiloId.toString());
            if (launchWarning != null) out.setTag("launchWarning", launchWarning.serializeNBT());
            if (manualMission != null) out.setTag("manualMission", manualMission.serializeNBT());
            if (delivery != null) out.setTag("delivery", delivery.serializeNBT());
            out.setBoolean("running", running);
            return out;
        }

        public static ScheduleView deserializeNBT(@Nonnull NBTTagCompound encoded) {
            var in = new NbtReader(encoded, "rocketScheduleView");
            var locked = new HashSet<UUID>();
            var entries = in.strings("locked");
            for (int i = 0; i < entries.tagCount(); i++) locked.add(UUID.fromString(entries.getStringTagAt(i)));
            OrbitalMechanics.LocalVehicle propulsion = null;
            if (in.tag()
                .hasKey("propulsion")) {
                var engine = in.compound("propulsion");
                propulsion = new OrbitalMechanics.LocalVehicle(
                    engine.doubleValue("inertMassKg"),
                    engine.doubleValue("usablePropellantKg"),
                    engine.doubleValue("exhaustVelocityMps"),
                    engine.doubleValue("thrustNewtons"));
            }
            var propellant = new LinkedHashMap<FluidKey, Long>();
            InventoryKeyState.decodeAmounts(in, "propellant")
                .forEach((key, amount) -> {
                    if (!(key instanceof FluidKey fluid))
                        throw new IllegalArgumentException("Propellant must be fluid");
                    propellant.put(fluid, amount);
                });
            var modules = new ArrayList<ModuleView>();
            var parts = in.compounds("modules");
            for (int i = 0; i < parts.tagCount(); i++)
                modules.add(ModuleView.deserializeNBT(in.element("modules", i, parts.getCompoundTagAt(i))));
            return new ScheduleView(
                UUID.fromString(in.string("vehicle")),
                in.longValue("revision"),
                VehicleSchedule.deserializeNBT(
                    in.compound("schedule")
                        .tag()),
                in.enumValue(Status.class, "status"),
                VehicleSchedule.Place.deserializeNBT(
                    in.compound("place")
                        .tag()),
                in.tag()
                    .hasKey("activeVisit") ? UUID.fromString(in.string("activeVisit")) : null,
                locked,
                in.tag()
                    .hasKey("problem") ? in.string("problem") : null,
                propulsion,
                propellant,
                InventoryKeyState.decodeAmounts(in, "cargo"),
                modules,
                in.integer("requiredModules"),
                in.tag()
                    .hasKey("returnSilo") ? UUID.fromString(in.string("returnSilo")) : null,
                in.tag()
                    .hasKey("launchWarning") ? LaunchWarning.deserializeNBT(in.compound("launchWarning")) : null,
                in.tag()
                    .hasKey("manualMission")
                        ? VehicleSchedule.deserializeNBT(
                            in.compound("manualMission")
                                .tag())
                        : null,
                in.tag()
                    .hasKey("delivery")
                        ? DeliveryView.deserializeNBT(
                            in.compound("delivery")
                                .tag())
                        : null,
                in.bool("running"));
        }
    }

    /** A return assessment for one uncommitted crewed departure, derived from actual remaining onboard supplies. */
    public record LaunchWarning(@Nonnull VehicleSchedule.Leg leg, @Nonnull VehicleSchedule.Place source,
        @Nonnull VehicleSchedule.Place destination, double outboundDeltaVMps, double remainingDeltaVMps,
        boolean returnDataAvailable) {

        public LaunchWarning {
            if (!Double.isFinite(outboundDeltaVMps) || outboundDeltaVMps < 0
                || !Double.isFinite(remainingDeltaVMps)
                || remainingDeltaVMps < 0) throw new IllegalArgumentException("Invalid launch review");
        }

        private NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setTag("leg", leg.serializeNBT());
            out.setTag("source", source.serializeNBT());
            out.setTag("destination", destination.serializeNBT());
            out.setDouble("outboundDeltaV", outboundDeltaVMps);
            out.setDouble("remainingDeltaV", remainingDeltaVMps);
            out.setBoolean("returnDataAvailable", returnDataAvailable);
            return out;
        }

        private static LaunchWarning deserializeNBT(NbtReader in) {
            return new LaunchWarning(
                VehicleSchedule.Leg.deserializeNBT(
                    in.compound("leg")
                        .tag()),
                VehicleSchedule.Place.deserializeNBT(
                    in.compound("source")
                        .tag()),
                VehicleSchedule.Place.deserializeNBT(
                    in.compound("destination")
                        .tag()),
                in.doubleValue("outboundDeltaV"),
                in.doubleValue("remainingDeltaV"),
                in.bool("returnDataAvailable"));
        }
    }

    /** Client-needed condition and handling facts, without another module payload. */
    public record ModuleView(@Nonnull UUID id, @Nonnull String nameKey, double durabilityPercent,
        @Nonnull HolderKind holder, boolean repairSelected, boolean repairCompleted, @Nullable String workKey) {

        public ModuleView {
            if (!Double.isFinite(durabilityPercent) || durabilityPercent < 0 || durabilityPercent > 100) {
                throw new IllegalArgumentException("Invalid displayed module durability");
            }
        }

        private NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("id", id.toString());
            out.setString("name", nameKey);
            out.setDouble("durability", durabilityPercent);
            out.setString("holder", holder.name());
            out.setBoolean("repairSelected", repairSelected);
            out.setBoolean("repairCompleted", repairCompleted);
            if (workKey != null) out.setString("work", workKey);
            return out;
        }

        private static ModuleView deserializeNBT(NbtReader in) {
            return new ModuleView(
                UUID.fromString(in.string("id")),
                in.nonBlankString("name"),
                in.doubleValue("durability"),
                in.enumValue(HolderKind.class, "holder"),
                in.bool("repairSelected"),
                in.bool("repairCompleted"),
                in.tag()
                    .hasKey("work") ? in.nonBlankString("work") : null);
        }
    }

    public record Transfer(@Nonnull UUID id, @Nonnull UUID vehicleId, @Nonnull UUID moduleId, @Nonnull UUID workId,
        long sequence, @Nonnull Holder source, @Nonnull Holder destination) {

        private NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("id", id.toString());
            out.setString("vehicle", vehicleId.toString());
            out.setString("module", moduleId.toString());
            out.setString("work", workId.toString());
            out.setLong("sequence", sequence);
            out.setTag("source", source.serializeNBT());
            out.setTag("destination", destination.serializeNBT());
            return out;
        }

        private static Transfer deserializeNBT(NbtReader in) {
            return new Transfer(
                UUID.fromString(in.string("id")),
                UUID.fromString(in.string("vehicle")),
                UUID.fromString(in.string("module")),
                UUID.fromString(in.string("work")),
                in.longValue("sequence"),
                Holder.deserializeNBT(in.compound("source")),
                Holder.deserializeNBT(in.compound("destination")));
        }
    }

    /** Concrete state remains vehicle-owned while physical custody changes. */
    public static final class Module {

        private final UUID id;
        private final UUID vehicleId;
        private final UUID productionRequestId;
        private final RocketPartInstance part;
        private double durabilityPercent;
        private final ItemStackHandler cargo;
        private final FluidTank freight;
        private final Map<FluidKey, FluidTank> propellant = new LinkedHashMap<>();
        private Holder holder;
        private long transferSequence;
        @Nullable
        private Transfer pendingTransfer;
        @Nullable
        private Transfer lastAcceptedTransfer;
        @Nullable
        private GantryTransport gantryTransport;

        private Module(UUID id, UUID vehicleId, UUID productionRequestId, RocketPartInstance part,
            double durabilityPercent, Holder holder) {
            if (!Double.isFinite(durabilityPercent) || durabilityPercent < 0 || durabilityPercent > 100) {
                throw new IllegalArgumentException("Module durability must be between zero and one hundred percent");
            }
            this.id = id;
            this.vehicleId = vehicleId;
            this.productionRequestId = productionRequestId;
            this.part = part;
            this.durabilityPercent = durabilityPercent;
            this.holder = holder;
            var storage = part.def()
                .storage();
            cargo = new ItemStackHandler(storage.itemSlots());
            freight = new FluidTank(Math.toIntExact(storage.freightFluidCapacity()));
            storage.propellantCapacities()
                .forEach((key, capacity) -> propellant.put(key, new FluidTank(Math.toIntExact(capacity))));
        }

        @Nonnull
        public static Module produced(@Nonnull UUID vehicleId, @Nonnull BuildSlot slot, double durabilityPercent,
            @Nonnull Holder holder) {
            return new Module(slot.moduleId, vehicleId, slot.requestId, slot.part, durabilityPercent, holder);
        }

        public UUID id() {
            return id;
        }

        public UUID vehicleId() {
            return vehicleId;
        }

        public UUID productionRequestId() {
            return productionRequestId;
        }

        public RocketPartInstance part() {
            return part;
        }

        public double durabilityPercent() {
            return durabilityPercent;
        }

        public Holder holder() {
            return holder;
        }

        @Nullable
        public Transfer pendingTransfer() {
            return pendingTransfer;
        }

        @Nullable
        public GantryTransport gantryTransport() {
            return gantryTransport;
        }

        /** Returns independent stacks for presentation, including when the sealed module is in service. */
        public List<ItemStack> cargoContents() {
            var contents = new ArrayList<ItemStack>();
            for (int i = 0; i < cargo.getSlots(); i++) {
                ItemStack stack = cargo.getStackInSlot(i);
                if (stack != null) contents.add(stack.copy());
            }
            return List.copyOf(contents);
        }

        @Nullable
        public FluidStack freightContents() {
            return freight.getFluid() == null ? null
                : freight.getFluid()
                    .copy();
        }

        public Map<FluidKey, Integer> propellantContents() {
            var contents = new LinkedHashMap<FluidKey, Integer>();
            propellant.forEach((key, tank) -> contents.put(key, tank.getFluidAmount()));
            return Map.copyOf(contents);
        }

        @Nullable
        public ItemStack insertCargo(@Nonnull ItemStack input, boolean simulate) {
            ItemStack remainder = input.copy();
            if (!storesAccessible()) return remainder;
            if (!acceptsItemCargo(remainder)) return remainder;
            for (int i = 0; i < cargo.getSlots() && remainder != null; i++) {
                remainder = cargo.insertItem(i, remainder, simulate);
            }
            return remainder == null ? null : remainder.copy();
        }

        public int fillFreight(@Nonnull FluidStack input, boolean simulate) {
            return storesAccessible() ? freight.fill(input.copy(), !simulate) : 0;
        }

        public int fillPropellant(@Nonnull FluidStack input, boolean simulate) {
            if (!storesAccessible()) return 0;
            FluidTank tank = propellant.get(FluidKey.of(input));
            return tank == null ? 0 : tank.fill(input.copy(), !simulate);
        }

        private boolean storesAccessible() {
            return holder.kind == HolderKind.INSTALLED && pendingTransfer == null;
        }

        private void readStores(NbtReader in) {
            var encodedCargo = in.compound("cargo");
            if (encodedCargo.integer("Size") != cargo.getSlots()) {
                throw new IllegalStateException("Module cargo capacity does not match its definition");
            }
            var items = encodedCargo.compounds("Items");
            cargo.deserializeNBT(encodedCargo.tag());
            int restoredItems = 0;
            for (int i = 0; i < cargo.getSlots(); i++) {
                ItemStack stack = cargo.getStackInSlot(i);
                if (stack == null) continue;
                if (stack.stackSize <= 0
                    || stack.stackSize > Math.min(cargo.getSlotLimit(i), stack.getMaxStackSize())) {
                    throw new IllegalStateException("Module cargo stack exceeds its slot capacity");
                }
                restoredItems++;
            }
            if (restoredItems != items.tagCount())
                throw new IllegalStateException("Module cargo contains an invalid item or slot");
            readTank(freight, in.compound("freight"));
            var compartments = in.compounds("propellant");
            var restored = new HashSet<FluidKey>();
            for (int i = 0; i < compartments.tagCount(); i++) {
                var compartment = in.element("propellant", i, compartments.getCompoundTagAt(i));
                var key = InventoryKeyState.decode(compartment.tag());
                if (!(key instanceof FluidKey fluid) || !restored.add(fluid) || !propellant.containsKey(fluid)) {
                    throw new IllegalStateException("Module propellant compartment does not match its definition");
                }
                FluidTank tank = propellant.get(fluid);
                readTank(tank, compartment.compound("tank"));
                if (tank.getFluid() != null && !FluidKey.of(tank.getFluid())
                    .equals(fluid)) {
                    throw new IllegalStateException("Module propellant compartment contains the wrong fluid");
                }
            }
            if (!restored.equals(propellant.keySet()))
                throw new IllegalStateException("Module propellant compartment is missing");
        }

        private static void readTank(FluidTank tank, NbtReader in) {
            tank.readFromNBT(in.tag());
            if ((!in.tag()
                .hasKey("Empty") && tank.getFluid() == null) || tank.getFluidAmount() < 0
                || tank.getFluidAmount() > tank.getCapacity()) {
                throw new IllegalStateException("Module tank contains invalid fluid or amount");
            }
        }

        private void validateTransfers() {
            if (transferSequence < 0) throw new IllegalStateException("Negative module transfer sequence");
            if (pendingTransfer != null && (!owns(pendingTransfer) || pendingTransfer.sequence != transferSequence + 1
                || !pendingTransfer.source.equals(holder))) {
                throw new IllegalStateException("Pending transfer does not match module custody");
            }
            if (lastAcceptedTransfer != null
                && (!owns(lastAcceptedTransfer) || lastAcceptedTransfer.sequence != transferSequence
                    || !lastAcceptedTransfer.destination.equals(holder))) {
                throw new IllegalStateException("Accepted transfer does not match module custody");
            }
            if ((lastAcceptedTransfer == null) != (transferSequence == 0)) {
                throw new IllegalStateException("Module transfer receipt is missing or unexpected");
            }
        }

        private boolean owns(Transfer transfer) {
            return vehicleId.equals(transfer.vehicleId) && id.equals(transfer.moduleId);
        }

        private void validateTransport() {
            var trip = gantryTransport;
            if (!trip.target.terminalId.equals(trip.reservedRoute.getLast())
                || (holder.kind == HolderKind.GANTRY
                    && (trip.routeIndex < 0 || !holder.id.equals(trip.reservedRoute.get(trip.routeIndex))))
                || (pendingTransfer != null && !pendingTransfer.workId.equals(trip.workId))) {
                throw new IllegalStateException("Gantry trip does not match module custody");
            }
        }
    }
}
