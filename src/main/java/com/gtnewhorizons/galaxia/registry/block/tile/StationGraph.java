package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IGraphListener;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public final class StationGraph {

    private final TileStationController controller;

    private final Object2ObjectOpenHashMap<BlockPos, TileStationBase<?>> pieces = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<BlockPos, ObjectArrayList<BlockPos>> adjacency = new Object2ObjectOpenHashMap<>();

    private final ObjectOpenHashSet<BlockPos> visited = new ObjectOpenHashSet<>();
    private final ObjectArrayList<BlockPos> queue = new ObjectArrayList<>();

    private final List<IGraphListener> listeners = new ObjectArrayList<>();

    public StationGraph(TileStationController controller) {
        this.controller = controller;
    }

    @SuppressWarnings("unchecked")
    public <T extends TileStationBase<?>> Iterable<T> iterateOver(Class<T> clazz) {
        return () -> new Iterator<T>() {

            private final ObjectIterator<Object2ObjectMap.Entry<BlockPos, TileStationBase<?>>> it = pieces
                .object2ObjectEntrySet()
                .fastIterator();
            private T next;

            private void advance() {
                while (next == null) {
                    if (!it.hasNext()) return;
                    TileStationBase<?> piece = it.next()
                        .getValue();
                    if (piece != controller && clazz.isInstance(piece)) {
                        next = (T) piece;
                    }
                }
            }

            @Override
            public boolean hasNext() {
                advance();
                return next != null;
            }

            @Override
            public T next() {
                advance();
                T result = next;
                if (result == null) throw new NoSuchElementException();
                next = null;
                return result;
            }
        };
    }

    public Iterable<IDistributedInventory> connectedInventories() {
        return () -> {
            Iterator<TileStationDock> dockIt = iterateOver(TileStationDock.class).iterator();
            return new Iterator<>() {

                private List<BlockPos> targets = new ObjectArrayList<>();
                private int targetIndex;
                private IDistributedInventory next;

                private void advance() {
                    while (next == null) {
                        if (targetIndex < targets.size()) {
                            BlockPos pos = targets.get(targetIndex++);
                            TileEntity te = pos.getTE(controller.getWorldObj());
                            if (te instanceof IDistributedInventory inv) {
                                next = inv;
                                return;
                            }
                        } else if (dockIt.hasNext()) {
                            targets = dockIt.next()
                                .getHammerTargets();
                            targetIndex = 0;
                        } else {
                            return;
                        }
                    }
                }

                @Override
                public boolean hasNext() {
                    advance();
                    return next != null;
                }

                @Override
                public IDistributedInventory next() {
                    advance();
                    IDistributedInventory result = next;
                    if (result == null) throw new NoSuchElementException();
                    next = null;
                    return result;
                }
            };
        };
    }

    public void rebuild() {
        adjacency.clear();
        pieces.clear();
        visited.clear();
        queue.clear();

        BlockPos start = controller.here;
        if (start == null || controller.getWorldObj() == null) return;

        pieces.put(start, controller);
        queue.add(start);
        visited.add(start);

        int head = 0;
        while (head < queue.size()) {
            BlockPos current = queue.get(head++);
            TileStationBase<?> piece = pieces.get(current);
            if (piece == null) continue;

            for (BlockPos airlockPos : piece.airlocks) {
                TileEntityAirlock airlock = resolveAirlock(airlockPos);
                if (airlock == null) continue;

                for (BlockPos other : airlock.getStationControllers()) {
                    if (other.equals(current)) continue;
                    if (!visited.add(other)) continue;

                    TileStationBase<?> neighbor = resolvePiece(other);
                    if (neighbor != null) {
                        pieces.put(other, neighbor);
                    }

                    queue.add(other);
                    adjacency.computeIfAbsent(current, k -> new ObjectArrayList<>())
                        .add(other);
                    adjacency.computeIfAbsent(other, k -> new ObjectArrayList<>())
                        .add(current);
                }
            }
        }

        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i)
                .onGraphRebuilt(controller);
        }
    }

    public void destroy() {
        adjacency.clear();
        pieces.clear();
        visited.clear();
        queue.clear();
        listeners.clear();
    }

    public boolean isEmpty() {
        return pieces.size() <= 1;
    }

    public TileStationController getController() {
        return controller;
    }

    public void addListener(IGraphListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(IGraphListener listener) {
        listeners.remove(listener);
    }

    private TileEntityAirlock resolveAirlock(BlockPos pos) {
        TileEntity te = pos.getTE(controller.getWorldObj());
        return te instanceof TileEntityAirlock lock ? lock : null;
    }

    private TileStationBase<?> resolvePiece(BlockPos pos) {
        TileEntity te = pos.getTE(controller.getWorldObj());
        return te instanceof TileStationBase<?>base ? base : null;
    }
}
