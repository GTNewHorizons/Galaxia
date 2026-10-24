package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.gtnewhorizons.galaxia.core.state.CelestialObjectKeyState;
import com.gtnewhorizons.galaxia.core.state.InventoryKeyState;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore.EndpointRef;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.EndpointKind;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

/** An immutable vehicle itinerary. Draft edits produce a separate document. */
public record VehicleSchedule(@Nonnull List<Visit> visits, @Nonnull Mode mode, @Nonnull Map<Leg, RouteSettings> routes,
    boolean requireSafeDurability) {

    public VehicleSchedule {
        visits = List.copyOf(visits);
        var visitIds = new HashSet<UUID>();
        var actionIds = new HashSet<UUID>();
        for (Visit visit : visits) {
            if (!visitIds.add(visit.id())) throw new IllegalArgumentException("Duplicate visit identity");
            for (Action action : visit.actions()) {
                if (!actionIds.add(action.id())) throw new IllegalArgumentException("Duplicate action identity");
            }
        }
        var adjacent = new HashMap<Leg, RouteSettings>();
        var unconfigured = new RouteSettings(OptionalDouble.empty(), RoutePriority.PRIORITIZE_DV);
        for (int i = 1; i < visits.size(); i++) {
            var leg = new Leg(
                visits.get(i - 1)
                    .id(),
                visits.get(i)
                    .id());
            adjacent.put(leg, routes.getOrDefault(leg, unconfigured));
        }
        if (mode == Mode.LOOP && visits.size() > 1) {
            var leg = new Leg(
                visits.get(visits.size() - 1)
                    .id(),
                visits.get(0)
                    .id());
            adjacent.put(leg, routes.getOrDefault(leg, unconfigured));
        }
        routes = Map.copyOf(adjacent);
    }

    public VehicleSchedule(List<Visit> visits, Mode mode) {
        this(visits, mode, Map.of(), true);
    }

    public VehicleSchedule(List<Visit> visits, Mode mode, Map<Leg, RouteSettings> routes) {
        this(visits, mode, routes, true);
    }

    public VehicleSchedule withSafeDurability(boolean required) {
        return new VehicleSchedule(visits, mode, routes, required);
    }

    public boolean endsWithoutRecovery() {
        if (mode != Mode.RUN_ONCE || visits.isEmpty()) return false;
        Place end = visits.getLast()
            .place();
        return end.context() == Context.ORBIT && end.target() == null;
    }

    public boolean hasValidRefueling() {
        return visits.stream()
            .allMatch(
                visit -> visit.place()
                    .canRefuel()
                    || visit.actions()
                        .stream()
                        .noneMatch(Refuel.class::isInstance));
    }

    public VehicleSchedule withVisit(Visit replacement) {
        var edited = new ArrayList<>(visits);
        for (int i = 0; i < edited.size(); i++) {
            if (edited.get(i)
                .id()
                .equals(replacement.id())) {
                edited.set(i, replacement);
                return new VehicleSchedule(edited, mode, routes, requireSafeDurability);
            }
        }
        edited.add(replacement);
        return new VehicleSchedule(edited, mode, routes, requireSafeDurability);
    }

    public VehicleSchedule reordered(List<UUID> visitIds) {
        var byId = new HashMap<UUID, Visit>();
        for (Visit visit : visits) byId.put(visit.id(), visit);
        if (visitIds.size() != visits.size() || !new HashSet<>(visitIds).equals(byId.keySet())) {
            throw new IllegalArgumentException("Reordering must contain every visit exactly once");
        }
        return new VehicleSchedule(
            visitIds.stream()
                .map(byId::get)
                .toList(),
            mode,
            routes,
            requireSafeDurability);
    }

    public VehicleSchedule withoutVisit(UUID visitId) {
        return new VehicleSchedule(
            visits.stream()
                .filter(
                    visit -> !visit.id()
                        .equals(visitId))
                .toList(),
            mode,
            routes,
            requireSafeDurability);
    }

    public VehicleSchedule withMode(Mode replacement) {
        return new VehicleSchedule(visits, replacement, routes, requireSafeDurability);
    }

    public VehicleSchedule withRouteSettings(Leg leg, RouteSettings settings) {
        if (!routes.containsKey(leg)) throw new IllegalArgumentException("Route must join adjacent visits");
        var edited = new HashMap<>(routes);
        edited.put(leg, settings);
        return new VehicleSchedule(visits, mode, edited, requireSafeDurability);
    }

    @Nonnull
    public NBTTagCompound serializeNBT() {
        var out = new NBTTagCompound();
        out.setString("mode", mode.name());
        out.setBoolean("safeDurability", requireSafeDurability);
        var encodedVisits = new NBTTagList();
        for (Visit visit : visits) {
            var encoded = visit.place()
                .serializeNBT();
            encoded.setString(
                "id",
                visit.id()
                    .toString());
            encoded.setBoolean("blockDockActions", visit.blockDockActions());
            var actions = new NBTTagList();
            for (Action action : visit.actions()) actions.appendTag(writeAction(action));
            encoded.setTag("actions", actions);
            encodedVisits.appendTag(encoded);
        }
        out.setTag("visits", encodedVisits);
        var encodedRoutes = new NBTTagList();
        for (var entry : routes.entrySet()) {
            var encoded = entry.getKey()
                .serializeNBT();
            encoded.setString(
                "priority",
                entry.getValue()
                    .priority()
                    .name());
            entry.getValue()
                .allowance()
                .ifPresent(value -> encoded.setDouble("allowance", value));
            encodedRoutes.appendTag(encoded);
        }
        out.setTag("routes", encodedRoutes);
        return out;
    }

    @Nonnull
    public static VehicleSchedule deserializeNBT(@Nonnull NBTTagCompound encoded) {
        var in = new NbtReader(encoded, "vehicleSchedule");
        var visits = new ArrayList<Visit>();
        var encodedVisits = in.compounds("visits");
        for (int i = 0; i < encodedVisits.tagCount(); i++) {
            var visit = in.element("visits", i, encodedVisits.getCompoundTagAt(i));
            var actions = new ArrayList<Action>();
            var encodedActions = visit.compounds("actions");
            for (int j = 0; j < encodedActions.tagCount(); j++) {
                actions.add(readAction(visit.element("actions", j, encodedActions.getCompoundTagAt(j))));
            }
            var place = Place.read(visit);
            visits.add(new Visit(UUID.fromString(visit.string("id")), place, actions, visit.bool("blockDockActions")));
        }
        var routes = new HashMap<Leg, RouteSettings>();
        var encodedRoutes = in.compounds("routes");
        for (int i = 0; i < encodedRoutes.tagCount(); i++) {
            var route = in.element("routes", i, encodedRoutes.getCompoundTagAt(i));
            var leg = Leg.deserializeNBT(route.tag());
            var allowance = route.tag()
                .hasKey("allowance") ? OptionalDouble.of(route.doubleValue("allowance")) : OptionalDouble.empty();
            if (routes.put(leg, new RouteSettings(allowance, route.enumValue(RoutePriority.class, "priority")))
                != null) {
                throw new IllegalArgumentException("Duplicate route");
            }
        }
        var schedule = new VehicleSchedule(visits, in.enumValue(Mode.class, "mode"), routes, in.bool("safeDurability"));
        if (!schedule.routes()
            .keySet()
            .equals(routes.keySet())) throw new IllegalArgumentException("Persisted routes must match visit adjacency");
        return schedule;
    }

    private static NBTTagCompound writeTarget(Target target) {
        var out = new NBTTagCompound();
        out.setString(
            "kind",
            target.kind()
                .name());
        if (target instanceof Exact exact) {
            out.setString("type", "exact");
            out.setString(
                "endpoint",
                exact.endpointId()
                    .toString());
        } else if (target instanceof Group group) {
            out.setString("type", "group");
            out.setString("name", group.routingName());
            if (group.stationId() != null) out.setString(
                "station",
                group.stationId()
                    .toString());
        }
        return out;
    }

    private static Target readTarget(NbtReader in) {
        var kind = in.enumValue(Kind.class, "kind");
        return switch (in.string("type")) {
            case "exact" -> new Exact(UUID.fromString(in.string("endpoint")), kind);
            case "group" -> new Group(
                in.nonBlankString("name"),
                kind,
                in.tag()
                    .hasKey("station") ? UUID.fromString(in.string("station")) : null);
            default -> throw new IllegalArgumentException("Unknown schedule target type");
        };
    }

    private static NBTTagCompound writeAction(Action action) {
        var out = new NBTTagCompound();
        out.setString("id", action.id().toString());
        switch (action) {
            case Transfer transfer -> {
                out.setString("type", "transfer");
                out.setTag("resource", InventoryKeyState.encode(transfer.resource()));
                out.setLong("amount", transfer.amount());
                out.setString("direction", transfer.direction().name());
            }
            case Refuel refuel -> {
                out.setString("type", "refuel");
                out.setBoolean("toFull", refuel.toFull());
                out.setTag("targets", InventoryKeyState.encodeAmounts(refuel.targets()));
            }
            case Deploy deploy -> {
                out.setString("type", "deploy");
                out.setTag("cargo", InventoryKeyState.encode(deploy.cargo()));
                out.setLong("amount", deploy.amount());
            }
            case Repair repair -> {
                out.setString("type", "repair");
                out.setDouble("threshold", repair.thresholdPercent());
                if (repair.returnSiloId() != null) out.setString("returnSilo", repair.returnSiloId().toString());
            }
        }
        return out;
    }

    private static Action readAction(NbtReader in) {
        UUID id = UUID.fromString(in.string("id"));
        return switch (in.string("type")) {
            case "transfer" -> new Transfer(
                id,
                InventoryKeyState.decode(
                    in.compound("resource")
                        .tag()),
                in.longValue("amount"),
                in.enumValue(Direction.class, "direction"));
            case "refuel" -> {
                var targets = new HashMap<FluidKey, Long>();
                for (var target : InventoryKeyState.decodeAmounts(in, "targets")
                    .entrySet()) {
                    var resource = target.getKey();
                    if (!(resource instanceof FluidKey fluid))
                        throw new IllegalArgumentException("Propellant must be a fluid");
                    targets.put(fluid, target.getValue());
                }
                yield new Refuel(id, in.bool("toFull"), targets);
            }
            case "deploy" -> {
                var cargo = InventoryKeyState.decode(
                    in.compound("cargo")
                        .tag());
                if (!(cargo instanceof ItemStackWrapper item))
                    throw new IllegalArgumentException("Deploy cargo must be an item");
                yield new Deploy(id, item, in.longValue("amount"));
            }
            case "repair" -> new Repair(
                id,
                in.doubleValue("threshold"),
                in.tag()
                    .hasKey("returnSilo") ? UUID.fromString(in.string("returnSilo")) : null);
            default -> throw new IllegalArgumentException("Unknown schedule action type");
        };
    }

    public record Leg(@Nonnull UUID fromVisit, @Nonnull UUID toVisit) {

        public NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setString("from", fromVisit.toString());
            out.setString("to", toVisit.toString());
            return out;
        }

        public static Leg deserializeNBT(NBTTagCompound encoded) {
            var in = new NbtReader(encoded, "vehicleScheduleLeg");
            return new Leg(UUID.fromString(in.string("from")), UUID.fromString(in.string("to")));
        }
    }

    /** Empty allowance means this route still needs explicit player configuration. */
    public record RouteSettings(@Nonnull OptionalDouble allowance, @Nonnull RoutePriority priority) {

        public RouteSettings {
            if (allowance.isPresent() && (!Double.isFinite(allowance.getAsDouble()) || allowance.getAsDouble() < 0)) {
                throw new IllegalArgumentException("Route allowance must be finite and nonnegative");
            }
        }

        public RouteSettings(double allowance, RoutePriority priority) {
            this(OptionalDouble.of(allowance), priority);
        }
    }

    public enum Mode {
        RUN_ONCE,
        LOOP
    }

    public enum Context {
        SURFACE,
        ORBIT
    }

    public record Place(@Nonnull CelestialObjectKey body, @Nonnull Context context, @Nullable Target target,
        @Nullable SurfaceSite surfaceSite) {

        public Place {
            if (surfaceSite != null && (context != Context.SURFACE || target != null))
                throw new IllegalArgumentException("An unprepared site is a surface position, not an endpoint");
        }

        public Place(CelestialObjectKey body, Context context, @Nullable Target target) {
            this(body, context, target, null);
        }

        public boolean canRefuel() {
            return target != null && target.kind() == Kind.SILO;
        }

        public EndpointKind endpointKind() {
            if (context == Context.SURFACE) return EndpointKind.SURFACE;
            return target == null ? EndpointKind.PARKING : EndpointKind.DOCK;
        }

        public static Place at(@Nonnull EndpointRef endpoint) {
            return new Place(
                endpoint.asset().celestialObjectKey,
                endpoint.asset().location == CelestialAsset.Location.ORBIT ? Context.ORBIT : Context.SURFACE,
                new Exact(
                    endpoint.endpoint()
                        .id(),
                    endpoint.endpoint()
                        .kind()));
        }

        public NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setTag("body", CelestialObjectKeyState.encode(body));
            out.setString("context", context.name());
            if (target != null) out.setTag("target", writeTarget(target));
            if (surfaceSite != null) out.setTag("site", surfaceSite.serializeNBT());
            return out;
        }

        public static Place deserializeNBT(@Nonnull NBTTagCompound encoded) {
            return read(new NbtReader(encoded, "vehiclePlace"));
        }

        private static Place read(NbtReader in) {
            return new Place(
                CelestialObjectKeyState.decode(in.compound("body")),
                in.enumValue(Context.class, "context"),
                in.tag()
                    .hasKey("target") ? readTarget(in.compound("target")) : null,
                in.tag()
                    .hasKey("site") ? SurfaceSite.read(in.compound("site")) : null);
        }
    }

    /** The exact terrain position and footprint shown before a manual mission is accepted. */
    public record SurfaceSite(int dimension, int x, int y, int z, @Nonnull Rocket.Footprint footprint) {

        public SurfaceSite {
            if (y < 1 || y + footprint.height() > 256
                || Math.abs((long) x) >= 30000000
                || Math.abs((long) z) >= 30000000)
                throw new IllegalArgumentException("Landing site is outside the physical world");
        }

        public Rocket.PhysicalPosition position() {
            return new Rocket.PhysicalPosition(dimension, x + .5, y, z + .5, 0);
        }

        public NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setInteger("dimension", dimension);
            out.setInteger("x", x);
            out.setInteger("y", y);
            out.setInteger("z", z);
            out.setTag("footprint", footprint.serializeNBT());
            return out;
        }

        private static SurfaceSite read(NbtReader in) {
            return new SurfaceSite(
                in.integer("dimension"),
                in.integer("x"),
                in.integer("y"),
                in.integer("z"),
                Rocket.Footprint.deserializeNBT(in.compound("footprint")));
        }
    }

    public sealed interface Target {

        @Nonnull
        Kind kind();
    }

    public record Exact(@Nonnull UUID endpointId, @Nonnull Kind kind) implements Target {}

    public record Group(@Nonnull String routingName, @Nonnull Kind kind, @Nullable UUID stationId) implements Target {

        public Group {
            if (routingName.isBlank()) throw new IllegalArgumentException("Routing name must not be blank");
        }
    }

    public record Visit(@Nonnull UUID id, @Nonnull Place place, @Nonnull List<Action> actions,
        boolean blockDockActions) {

        public Visit {
            actions = List.copyOf(actions);
        }

        public Visit(UUID id, Place place, List<Action> actions) {
            this(id, place, actions, false);
        }
    }

    public sealed interface Action {

        @Nonnull
        UUID id();
    }

    public enum Direction {
        LOAD,
        UNLOAD
    }

    public record Transfer(@Nonnull UUID id, @Nonnull InventoryKey resource, long amount, @Nonnull Direction direction)
        implements Action {

        public Transfer {
            if (amount <= 0) throw new IllegalArgumentException("Transfer amount must be positive");
        }
    }

    public record Refuel(@Nonnull UUID id, boolean toFull, @Nonnull Map<FluidKey, Long> targets) implements Action {

        public Refuel(UUID id, Map<FluidKey, Long> targets) {
            this(id, false, targets);
        }

        public static Refuel full(UUID id) {
            return new Refuel(id, true, Map.of());
        }

        public Refuel {
            targets = Map.copyOf(targets);
            if (toFull && !targets.isEmpty()) throw new IllegalArgumentException("Full refueling has no fixed targets");
            if (targets.values()
                .stream()
                .anyMatch(amount -> amount < 0)) {
                throw new IllegalArgumentException("Propellant targets must be nonnegative");
            }
        }
    }

    public record Deploy(@Nonnull UUID id, @Nonnull ItemStackWrapper cargo, long amount) implements Action {

        public Deploy {
            if (amount <= 0) throw new IllegalArgumentException("Deploy amount must be positive");
        }
    }

    /** The threshold selects full repairs. An exact return target also requires handling healthy modules. */
    public record Repair(@Nonnull UUID id, double thresholdPercent, @Nullable UUID returnSiloId) implements Action {

        public Repair(UUID id, double thresholdPercent) {
            this(id, thresholdPercent, null);
        }

        public Repair {
            validatePercent(thresholdPercent);
        }

        public boolean qualifies(double durabilityPercent) {
            validatePercent(durabilityPercent);
            return durabilityPercent <= thresholdPercent;
        }
    }

    private static void validatePercent(double percent) {
        if (!Double.isFinite(percent) || percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Durability percentage must be finite and between 0 and 100");
        }
    }
}
