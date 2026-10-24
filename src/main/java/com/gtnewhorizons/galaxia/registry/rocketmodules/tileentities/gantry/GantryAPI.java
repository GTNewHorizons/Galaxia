package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketModule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;

import gregtech.api.util.GTUtility;

/** Connected machine admission and physical trips whose authoritative state belongs to the Rocket. */
public final class GantryAPI {

    public static final Vec3[] CHECK_OFFSETS = java.util.Arrays.stream(ForgeDirection.VALID_DIRECTIONS)
        .map(side -> Vec3.createVectorHelper(side.offsetX, side.offsetY, side.offsetZ))
        .toArray(Vec3[]::new);

    private GantryAPI() {}

    /** Explicit native destruction, never chunk unload or structure disformation. */
    public static void dropModules(Rocket.Registry registry, Rocket.Holder holder, TileEntity tile) {
        if (tile.getWorldObj().isRemote) return;
        for (Rocket rocket : registry.all()) {
            for (var drop : rocket.dropModules(holder)) {
                GTUtility.dropItemToBlockPos(
                    tile.getWorldObj(),
                    tile.xCoord,
                    tile.yCoord,
                    tile.zCoord,
                    ItemRocketModule.dropped(drop));
            }
        }
    }

    public record AcceptedWork(UUID workId, UUID controllerId, UUID terminalId) {}

    public enum DispatchResult {

        ACCEPTED,
        ALREADY_ACTIVE,
        BLOCKED_ROUTE,
        BUSY_ROUTE,
        RECEIVER_UNAVAILABLE,
        WRONG_SOURCE;

        public boolean accepted() {
            return this == ACCEPTED || this == ALREADY_ACTIVE;
        }
    }

    /** Selects a connected machine only after it reserves this exact work's supplied bill. */
    @Nullable
    public static AcceptedWork requestWork(TileEntityGantryTerminal origin, UUID teamId,
        TileEntityModuleAssembler.WorkRequest request) {
        List<TileEntityGantryTerminal> candidates = findEndpointTerminals(origin).stream()
            .filter(terminal -> terminal.getAssembler() != null)
            .filter(
                terminal -> terminal.getMode().accepts
                    && (request.operation() != TileEntityModuleAssembler.Operation.REPAIR
                        || terminal.getMode().delivers))
            .sorted(
                Comparator.comparingInt((TileEntityGantryTerminal terminal) -> terminal.getAssembler().xCoord)
                    .thenComparingInt(terminal -> terminal.getAssembler().yCoord)
                    .thenComparingInt(terminal -> terminal.getAssembler().zCoord))
            .toList();
        for (TileEntityGantryTerminal terminal : candidates) {
            TileEntityModuleAssembler assembler = terminal.getAssembler();
            var previous = assembler.acceptedWork();
            if (previous != null && previous.workId()
                .equals(request.workId())) {
                return assembler.tryAccept(teamId, request)
                    .accepted() ? new AcceptedWork(request.workId(), assembler.controllerId(), terminal.terminalId())
                        : null;
            }
        }
        for (TileEntityGantryTerminal terminal : candidates) {
            TileEntityModuleAssembler assembler = terminal.getAssembler();
            if (assembler.tryAccept(teamId, request)
                .accepted()) {
                return new AcceptedWork(request.workId(), assembler.controllerId(), terminal.terminalId());
            }
        }
        return null;
    }

    /** Reserves one physical trip, then confirms custody at its first Gantry node. */
    public static DispatchResult dispatch(Rocket.Registry registry, Rocket rocket, UUID moduleId, UUID workId,
        TileEntityGantry source, TileEntityGantryTerminal destination, Rocket.Holder receiver) {
        if (rocket.status() == Rocket.Status.INCOMPLETE) return DispatchResult.WRONG_SOURCE;
        Rocket.Module module = rocket.module(moduleId);
        var sourceTerminal = source instanceof TileEntityGantryTerminal terminal ? terminal : null;
        var controller = sourceTerminal == null ? null : sourceTerminal.controllerIdentity();
        boolean heldHere = module.holder()
            .equals(new Rocket.Holder(Rocket.HolderKind.GANTRY, source.nodeId()));
        if (!heldHere && sourceTerminal != null && !sourceTerminal.getMode().accepts)
            return DispatchResult.RECEIVER_UNAVAILABLE;
        var targetController = destination.controllerIdentity();
        if ((!heldHere && (controller == null || !controller.belongsTo(rocket.teamId()))) || targetController == null
            || !targetController.belongsTo(rocket.teamId())
            || source.dimension() != destination.dimension()) {
            return DispatchResult.WRONG_SOURCE;
        }
        var target = new Rocket.TerminalTarget(
            destination.nodeId(),
            targetController.id(),
            destination.dimension(),
            destination.xCoord,
            destination.yCoord,
            destination.zCoord,
            receiver);
        if (module.gantryTransport() != null) {
            var trip = module.gantryTransport();
            if (!trip.workId()
                .equals(workId)
                || !trip.target()
                    .equals(target))
                return DispatchResult.WRONG_SOURCE;
            if (trip.routeIndex() >= 0) return DispatchResult.ALREADY_ACTIVE;
            return sourceTerminal != null && enterRoute(registry, rocket, module, sourceTerminal)
                ? DispatchResult.ACCEPTED
                : DispatchResult.BUSY_ROUTE;
        }
        if (!heldHere && (controller == null || !module.holder()
            .id()
            .equals(controller.id()))) {
            return DispatchResult.WRONG_SOURCE;
        }
        if (!destination.getMode().delivers || !destination.canReceive(rocket, module, workId, receiver))
            return DispatchResult.RECEIVER_UNAVAILABLE;
        List<TileEntityGantry> route = path(source, destination);
        if (route == null) return DispatchResult.BLOCKED_ROUTE;
        List<UUID> reserved = route.stream()
            .map(TileEntityGantry::nodeId)
            .toList();
        if (occupied(registry, source.nodeId(), module) || !routeAvailable(registry, reserved, module))
            return DispatchResult.BUSY_ROUTE;
        if (!rocket.beginGantryTransport(moduleId, workId, module.holder(), target, reserved))
            return DispatchResult.WRONG_SOURCE;
        if (heldHere) {
            source.syncProjection();
            return DispatchResult.ACCEPTED;
        }
        return enterRoute(registry, rocket, module, sourceTerminal) ? DispatchResult.ACCEPTED
            : DispatchResult.BUSY_ROUTE;
    }

    private static boolean enterRoute(Rocket.Registry registry, Rocket rocket, Rocket.Module module,
        TileEntityGantryTerminal source) {
        var trip = module.gantryTransport();
        if (!trip.reservedRoute()
            .getFirst()
            .equals(source.nodeId()) || occupied(registry, source.nodeId(), module)) {
            return false;
        }
        var receiver = new Rocket.Holder(Rocket.HolderKind.GANTRY, source.nodeId());
        var transfer = rocket.beginTransfer(module.id(), trip.workId(), module.holder(), receiver);
        if (rocket.confirmTransfer(transfer, receiver) == Rocket.TransferResult.REJECTED) return false;
        TileEntityModuleAssembler assembler = source.getAssembler();
        if (assembler != null) {
            var output = assembler.completedOutput();
            if (output != null && output.vehicleId()
                .equals(rocket.id())
                && output.moduleId()
                    .equals(module.id())) {
                assembler.acknowledgeOutput(output.workId());
            }
        }
        source.syncProjection();
        return true;
    }

    /** Same server step used by native Gantry ticks and deterministic transport integration tests. */
    public static void tickNode(Rocket.Registry registry, TileEntityGantry node) {
        Rocket.Module module = heldModule(registry, node.nodeId());
        if (module == null || module.gantryTransport() == null) return;
        Rocket rocket = registry.find(module.vehicleId());
        if (rocket.status() == Rocket.Status.INCOMPLETE) return;
        var trip = module.gantryTransport();
        int index = trip.routeIndex();
        if (index < 0 || index >= trip.reservedRoute()
            .size()
            || !trip.reservedRoute()
                .get(index)
                .equals(node.nodeId()))
            return;
        if (index + 1 < trip.reservedRoute()
            .size()) {
            UUID nextId = trip.reservedRoute()
                .get(index + 1);
            TileEntityGantry next = node.getNeighbours()
                .stream()
                .filter(
                    candidate -> candidate.nodeId()
                        .equals(nextId) && adjacent(node, candidate))
                .findFirst()
                .orElse(null);
            if (next == null || occupied(registry, nextId, module)) return;
            node.projectDirection(next);
            var receiver = new Rocket.Holder(Rocket.HolderKind.GANTRY, nextId);
            var transfer = rocket.beginTransfer(module.id(), trip.workId(), module.holder(), receiver);
            if (!rocket.advanceGantryProgress(module.id(), TileEntityGantry.MOVEMENT_PER_TICK)) {
                node.syncProjection();
                return;
            }
            if (rocket.confirmTransfer(transfer, receiver) != Rocket.TransferResult.REJECTED) {
                node.syncProjection();
                next.syncProjection();
            }
            return;
        }
        if (!(node instanceof TileEntityGantryTerminal terminal) || !terminal.matchesTarget(trip.target())) return;
        node.projectDirectionToController();
        var transfer = rocket.beginTransfer(
            module.id(),
            trip.workId(),
            module.holder(),
            trip.target()
                .receiver());
        if (!rocket.advanceGantryProgress(module.id(), TileEntityGantry.MOVEMENT_PER_TICK)) {
            node.syncProjection();
            return;
        }
        if (!terminal.acceptDelivery(
            rocket,
            module,
            trip.workId(),
            trip.target()
                .receiver()))
            return;
        if (rocket.confirmTransfer(
            transfer,
            trip.target()
                .receiver())
            != Rocket.TransferResult.REJECTED) {
            rocket.finishGantryTransport(module.id(), trip.workId());
            node.syncProjection();
        }
    }

    @Nullable
    public static Rocket.Module heldModule(Rocket.Registry registry, UUID nodeId) {
        Rocket.Module held = null;
        var holder = new Rocket.Holder(Rocket.HolderKind.GANTRY, nodeId);
        for (Rocket rocket : registry.all()) {
            for (Rocket.Module module : rocket.modules()) {
                if (!module.holder()
                    .equals(holder)) continue;
                if (held != null) throw new IllegalStateException("Multiple modules occupy one Gantry node");
                held = module;
            }
        }
        return held;
    }

    private static boolean occupied(Rocket.Registry registry, UUID nodeId, Rocket.Module own) {
        for (Rocket rocket : registry.all()) {
            for (Rocket.Module module : rocket.modules()) {
                if (module == own) continue;
                if (module.holder()
                    .kind() == Rocket.HolderKind.GANTRY && module.holder()
                        .id()
                        .equals(nodeId))
                    return true;
                var pending = module.pendingTransfer();
                if (pending != null && pending.destination()
                    .kind() == Rocket.HolderKind.GANTRY
                    && pending.destination()
                        .id()
                        .equals(nodeId))
                    return true;
            }
        }
        return false;
    }

    /** Admit following traffic, but keep incompatible directions outside their shared corridor. */
    private static boolean routeAvailable(Rocket.Registry registry, List<UUID> route, Rocket.Module own) {
        for (Rocket rocket : registry.all()) {
            for (Rocket.Module module : rocket.modules()) {
                if (module == own || module.gantryTransport() == null) continue;
                var trip = module.gantryTransport();
                var remaining = trip.reservedRoute()
                    .subList(
                        Math.max(0, trip.routeIndex()),
                        trip.reservedRoute()
                            .size());
                if (!compatibleRoutes(route, remaining)) return false;
            }
        }
        return true;
    }

    private static boolean compatibleRoutes(List<UUID> route, List<UUID> remaining) {
        int shared = 0;
        int previousRouteIndex = -1;
        int previousRemainingIndex = -1;
        for (int i = 0; i < route.size(); i++) {
            int other = remaining.indexOf(route.get(i));
            if (other < 0) continue;
            if (shared > 0 && (i != previousRouteIndex + 1 || other != previousRemainingIndex + 1)) return false;
            previousRouteIndex = i;
            previousRemainingIndex = other;
            shared++;
        }
        // A common receiver can queue arrivals even when the incoming branches share no track.
        return shared != 1 || route.getLast()
            .equals(remaining.getLast());
    }

    public static boolean terminatesWithTerminals(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityGantry start)) return false;
        List<TileEntityGantry> graph = connectedNodes(start);
        return graph != null && graph.stream()
            .filter(TileEntityGantryTerminal.class::isInstance)
            .count() >= 2
            && graph.stream()
                .noneMatch(
                    node -> node.getNeighbours()
                        .size() <= 1 && !(node instanceof TileEntityGantryTerminal));
    }

    public static List<TileEntityGantryTerminal> findEndpointTerminals(TileEntityGantryTerminal start) {
        List<TileEntityGantry> nodes = connectedNodes(start);
        return nodes == null ? List.of()
            : nodes.stream()
                .filter(TileEntityGantryTerminal.class::isInstance)
                .map(TileEntityGantryTerminal.class::cast)
                .toList();
    }

    @Nullable
    public static TileEntityGantry findNode(TileEntityGantry start, UUID nodeId) {
        return start.getNetwork()
            .findNode(nodeId);
    }

    @Nullable
    public static TileEntityGantryTerminal findControllerTerminal(TileEntityGantryTerminal start, UUID controllerId) {
        return findEndpointTerminals(start).stream()
            .filter(terminal -> {
                var identity = terminal.controllerIdentity();
                return identity != null && identity.id()
                    .equals(controllerId);
            })
            .findFirst()
            .orElse(null);
    }

    @Nullable
    private static List<TileEntityGantry> connectedNodes(TileEntityGantry start) {
        return start.getNetwork()
            .getNodes();
    }

    @Nullable
    private static List<TileEntityGantry> path(TileEntityGantry start, TileEntityGantryTerminal end) {
        return start.getNetwork()
            .route(start, end);
    }

    private static boolean adjacent(TileEntityGantry first, TileEntityGantry second) {
        int x = second.xCoord - first.xCoord;
        int y = second.yCoord - first.yCoord;
        int z = second.zCoord - first.zCoord;
        return Math.abs(x) + Math.abs(y) + Math.abs(z) == 1;
    }

    public static boolean isTerminal(TileEntityGantry gantry) {
        return gantry instanceof TileEntityGantryTerminal;
    }
}
