package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBootableMultiblock;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IGraphListener;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationAttachment;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public final class StationGraph {

    private final TileStationController controller;
    private final Object2ObjectOpenHashMap<BlockPos, TileStationBase<?>> pieces = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<BlockPos, IStationAttachment<?>> attachments = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<BlockPos, ObjectArrayList<BlockPos>> adjacency = new Object2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<BlockPos> visited = new ObjectOpenHashSet<>();
    private final ObjectArrayList<BlockPos> queue = new ObjectArrayList<>();
    private final List<IGraphListener> listeners = new ObjectArrayList<>();

    public StationGraph(TileStationController controller) {
        this.controller = controller;
    }

    @SuppressWarnings("unchecked")
    public <T extends TileStationBase<?>> Iterable<T> iterateOver(Class<T> clazz) {
        return () -> pieces.values()
            .stream()
            .filter(p -> p != controller && clazz.isInstance(p))
            .map(p -> (T) p)
            .iterator();
    }

    public Stream<? extends IStationAttachment<?>> getAttachments() {
        return attachments.values()
            .stream()
            .filter(IStationAttachment::isReady);
    }

    public <T extends GalaxiaBootableMultiblock<T> & IStationAttachment<T>> Stream<T> getAttachments(Class<T> type) {
        return attachments.values()
            .stream()
            .filter(IStationAttachment::isReady)
            .filter(type::isInstance)
            .map(type::cast);
    }

    public @Nonnull Stream<IDistributedInventory> connectedInventories() {
        return attachments.keySet()
            .stream()
            .map(pos -> pos.getTE(controller.getWorldObj()))
            .filter(te -> te instanceof IDistributedInventory)
            .filter(te -> !(te instanceof GalaxiaMultiblockBase<?>base) || base.isStructureValid())
            .map(te -> (IDistributedInventory) te);
    }

    public void registerAttachment(BlockPos parent, BlockPos pos, IStationAttachment<?> attachment) {
        if (!pieces.containsKey(parent)) return;
        if (attachments.containsKey(pos)) return;

        addAdjacency(parent, pos);
        attachments.put(pos, attachment);
        attachment.onAttached(this);
        fireListeners(l -> l.onAttachmentConnected(pos, attachment));
    }

    public void removeAttachment(BlockPos pos) {
        IStationAttachment<?> attachment = attachments.remove(pos);
        adjacency.values()
            .forEach(list -> list.remove(pos));

        if (attachment != null) {
            attachment.onDetached(this);
            fireListeners(l -> l.onAttachmentDisconnected(pos));
        }
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
        attachments.forEach((pos, attachment) -> {
            attachment.onDetached(this);
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

        attachments.forEach((pos, attachment) -> {
            attachment.onDetached(this);
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
    }

    private void fireListeners(Consumer<IGraphListener> action) {
        listeners.forEach(action);
    }

    public boolean isEmpty() {
        return pieces.size() <= 1;
    }

    public TileStationController getController() {
        return controller;
    }

    public void addListener(IGraphListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(IGraphListener listener) {
        listeners.remove(listener);
    }
}
