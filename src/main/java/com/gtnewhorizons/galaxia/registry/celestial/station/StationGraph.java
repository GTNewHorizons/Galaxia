package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.FluidAttachmentInventory;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.ItemAttachmentInventory;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnergyHandler;
import com.gtnewhorizons.galaxia.registry.interfaces.IGraphListener;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;

public final class StationGraph {

    @Getter
    private final TileStation controller;
    private final Object2ObjectOpenHashMap<BlockPos, TileStationBase<?>> pieces = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<BlockPos, StationAttachmentRegistry.ResolvedAttachment<?>> attachments = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<StationAttachmentRegistry.ResolvedAttachment<?>, IDistributedInventory> resolvedInventories = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<BlockPos, ObjectArrayList<BlockPos>> adjacency = new Object2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<BlockPos> visited = new ObjectOpenHashSet<>();
    private final ObjectArrayList<BlockPos> queue = new ObjectArrayList<>();
    private final List<IGraphListener> listeners = new ObjectArrayList<>();

    public StationGraph(TileStation controller) {
        this.controller = controller;
    }

    public <T extends TileStationBase<?>> Iterable<T> iterateOver(Class<T> clazz) {
        return () -> pieces.values()
            .stream()
            .filter(p -> p != controller && clazz.isInstance(p))
            .map(p -> (T) p)
            .iterator();
    }

    public Stream<Object> getAttachments() {
        return attachments.values()
            .stream()
            .filter(StationGraph::isReady)
            .map(StationAttachmentRegistry.ResolvedAttachment::attachment);
    }

    public <T> Stream<T> getAttachments(Class<T> type) {
        return attachments.values()
            .stream()
            .filter(StationGraph::isReady)
            .filter(ra -> type.isInstance(ra.attachment()))
            .map(ra -> (T) ra.attachment());
    }

    public Stream<StationAttachmentRegistry.ResolvedAttachment<?>> getEnergyAttachments() {
        return attachments.values()
            .stream()
            .filter(ra -> StationAttachmentRegistry.isEnergyHandler(ra.handler()))
            .filter(StationGraph::isReady);
    }

    public Stream<StationAttachmentRegistry.ResolvedAttachment<?>> getFluidStorageAttachments() {
        return attachments.values()
            .stream()
            .filter(ra -> StationAttachmentRegistry.isFluidStorageHandler(ra.handler()))
            .filter(StationGraph::isReady);
    }

    public Stream<StationAttachmentRegistry.ResolvedAttachment<?>> getItemStorageAttachments() {
        return attachments.values()
            .stream()
            .filter(ra -> StationAttachmentRegistry.isItemStorageHandler(ra.handler()))
            .filter(StationGraph::isReady);
    }

    public void tickAttachments() {
        attachments.values()
            .forEach(StationGraph::tick);
    }

    public long drawEnergy(long maxPowerDraw) {
        long remaining = maxPowerDraw;
        for (StationAttachmentRegistry.ResolvedAttachment<?> ra : (Iterable<StationAttachmentRegistry.ResolvedAttachment<?>>) getEnergyAttachments()::iterator) {
            IEnergyHandler h = StationAttachmentRegistry.asEnergyHandler(ra.handler());
            long drawn = h.drawEnergy(ra.attachment(), remaining);
            remaining -= drawn;
            if (remaining <= 0) return maxPowerDraw;
        }
        return maxPowerDraw - remaining;
    }

    public @Nonnull Stream<IDistributedInventory> connectedInventories() {
        return resolvedInventories.values()
            .stream();
    }

    public void registerAttachment(BlockPos parent, BlockPos pos,
        @Nonnull StationAttachmentRegistry.ResolvedAttachment<?> ra) {
        if (!pieces.containsKey(parent)) return;
        if (attachments.containsKey(pos)) return;

        addAdjacency(parent, pos);
        attachments.put(pos, ra);
        if (ra.handler()
            .hasDistributedInventory()) {
            if (StationAttachmentRegistry.isFluidStorageHandler(ra.handler())) {
                resolvedInventories.put(
                    ra,
                    new FluidAttachmentInventory(
                        StationAttachmentRegistry.asFluidStorageHandler(ra.handler()),
                        ra.attachment()));
            } else if (StationAttachmentRegistry.isItemStorageHandler(ra.handler())) {
                resolvedInventories.put(
                    ra,
                    new ItemAttachmentInventory(
                        StationAttachmentRegistry.asItemStorageHandler(ra.handler()),
                        ra.attachment()));
            } else {
                throw new IllegalStateException("[StationGraph] Trying to register unknown inventory handler");
            }
        }
        onAttached(ra, this);
        fireListeners(l -> l.onAttachmentConnected(pos, ra.attachment()));
    }

    public void removeAttachment(BlockPos pos) {
        StationAttachmentRegistry.ResolvedAttachment<?> ra = attachments.remove(pos);
        adjacency.values()
            .forEach(list -> list.remove(pos));

        if (ra.handler()
            .hasDistributedInventory()) {
            resolvedInventories.remove(ra);
        }
        onDetached(ra, this);
        fireListeners(l -> l.onAttachmentDisconnected(pos));
    }

    private static <T> boolean isReady(StationAttachmentRegistry.ResolvedAttachment<T> ra) {
        return ra.handler()
            .isReady(ra.attachment());
    }

    private static <T> void tick(StationAttachmentRegistry.ResolvedAttachment<T> ra) {
        ra.handler()
            .tick(ra.attachment());
    }

    private static <T> void onAttached(StationAttachmentRegistry.ResolvedAttachment<T> ra, StationGraph graph) {
        ra.handler()
            .onAttached(ra.attachment(), graph);
    }

    private static <T> void onDetached(StationAttachmentRegistry.ResolvedAttachment<T> ra, StationGraph graph) {
        ra.handler()
            .onDetached(ra.attachment(), graph);
    }

    public void connectPiece(BlockPos pos) {
        if (controller.getWorldObj() == null || pieces.containsKey(pos)) return;
        if (!(pos.getTE(controller.getWorldObj()) instanceof TileStationBase<?>newPiece)) return;

        pieces.put(pos, newPiece);
        for (BlockPos airlockPos : newPiece.airlocks) {
            if (!(airlockPos.getTE(controller.getWorldObj()) instanceof TileEntityAirlock airlock)) continue;

            for (BlockPos other : airlock.getStationControllers()) {
                if (!pieces.containsKey(other) || other.equals(pos)) continue;

                listeners.add(newPiece);
                addAdjacency(pos, other);
                addAdjacency(other, pos);
                fireListeners(l -> l.onPieceConnected(pieces.get(other), newPiece, controller.here));
            }
        }
    }

    public void disconnectPiece(BlockPos pos) {
        if (!pieces.containsKey(pos)) return;

        ObjectArrayList<BlockPos> adj = adjacency.getOrDefault(pos, new ObjectArrayList<>());
        long pieceNeighborCount = adj.stream()
            .filter(pieces::containsKey)
            .count();

        if (pieceNeighborCount > 1) {
            rebuild();
            return;
        }

        TileStationBase<?> piece = pieces.remove(pos);
        // Remove children attachments
        adj.stream()
            .filter(attachments::containsKey)
            .toList()
            .forEach(this::removeAttachment);
        // Cleanup neighbor pointers
        adj.forEach(
            neighbor -> {
                if (adjacency.containsKey(neighbor)) adjacency.get(neighbor)
                    .remove(pos);
            });

        adjacency.remove(pos);
        if (piece != null) fireListeners(l -> l.onPieceDisconnected(piece, null));
    }

    public void rebuild() {
        var oldPieces = new Object2ObjectOpenHashMap<>(pieces);

        // Detach all current attachments before clearing
        attachments.forEach((pos, ra) -> {
            onDetached(ra, this);
            fireListeners(l -> l.onAttachmentDisconnected(pos));
        });

        clearData();
        BlockPos start = controller.here;
        if (start == null || controller.getWorldObj() == null) return;

        pieces.put(start, controller);
        queue.add(start);
        visited.add(start);

        for (int head = 0; head < queue.size(); head++) {
            BlockPos current = queue.get(head);
            TileStationBase<?> piece = pieces.get(current);
            if (piece == null) continue;

            for (BlockPos airlockPos : piece.airlocks) {
                if (!(airlockPos.getTE(controller.getWorldObj()) instanceof TileEntityAirlock airlock)) continue;

                for (BlockPos other : airlock.getStationControllers()) {
                    if (other.equals(current) || !visited.add(other)) continue;
                    if (!(other.getTE(controller.getWorldObj()) instanceof TileStationBase<?>neighbor)) continue;

                    listeners.add(neighbor);
                    pieces.put(other, neighbor);
                    queue.add(other);
                    addAdjacency(current, other);
                    addAdjacency(other, current);
                    fireListeners(l -> l.onPieceConnected(piece, neighbor, controller.here));
                }
            }
        }

        // Notify for pieces that were lost in rebuild
        oldPieces.forEach((pos, piece) -> {
            if (!pieces.containsKey(pos) && !pos.equals(start)) {
                fireListeners(l -> l.onPieceDisconnected(piece, null));
            }
        });

        fireListeners(l -> l.onGraphRebuilt(controller));
        visited.clear();
        queue.clear();
    }

    public void destroy() {
        pieces.values()
            .stream()
            .filter(p -> p != null && p != controller)
            .forEach(p -> fireListeners(l -> l.onPieceDisconnected(p, null)));

        attachments.forEach((pos, ra) -> {
            onDetached(ra, this);
            fireListeners(l -> l.onAttachmentDisconnected(pos));
        });

        clearData();
        listeners.clear();
    }

    private void addAdjacency(BlockPos from, BlockPos to) {
        adjacency.computeIfAbsent(from, k -> new ObjectArrayList<>())
            .add(to);
    }

    private void clearData() {
        pieces.clear();
        attachments.clear();
        adjacency.clear();
        visited.clear();
        queue.clear();
        resolvedInventories.clear();
    }

    private void fireListeners(Consumer<IGraphListener> action) {
        listeners.forEach(action);
    }

    public boolean isEmpty() {
        return pieces.size() <= 1;
    }

    public void addListener(IGraphListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(IGraphListener listener) {
        listeners.remove(listener);
    }
}
