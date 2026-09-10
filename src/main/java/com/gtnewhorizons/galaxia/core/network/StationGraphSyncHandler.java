package com.gtnewhorizons.galaxia.core.network;

import java.io.IOException;
import java.math.BigInteger;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnergyHandler;
import com.gtnewhorizons.galaxia.registry.interfaces.IFluidStorageHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lombok.Setter;

public final class StationGraphSyncHandler extends SyncHandler<StationGraphSyncHandler> {

    public static final String KEY = "station_graph_sync";
    private static final int OP_FULL_SYNC = 1;

    private static volatile StationGraphSyncHandler activeClientHandler;
    private volatile EnergySnapshot snapshot = new EnergySnapshot(0, 0, 0, 0, 0, 0);

    private EnergySnapshot lastSent;
    private int syncTicker;

    @Setter
    private TileStation station;

    public record EnergySnapshot(int attachmentCount, long totalStored, long totalCapacity, long fluidStored,
        long fluidCapacity, int fluidAttachmentCount) {}

    @Override
    public void init(String key, PanelSyncManager syncManager) {
        super.init(key, syncManager);
        if (syncManager.isClient()) activeClientHandler = this;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (getSyncManager() == null || getSyncManager().isClient() || station == null) return;
        if (init) {
            forceDirty();
            triggerFullSync();
        } else if (++syncTicker % 20 == 0) {
            triggerFullSync();
        }
    }

    @Override
    public void dispose() {
        if (this == activeClientHandler) activeClientHandler = null;
        if (station != null) {
            station.clearActiveGraphSyncHandler(this);
            station = null;
        }
        super.dispose();
    }

    public void forceDirty() {
        lastSent = null;
    }

    public void triggerFullSync() {
        if (getSyncManager() == null || getSyncManager().isClient() || station == null) return;

        StationGraph graph = station.getGraph();
        long stored = 0, capacity = 0;
        int count = 0;
        long fluidStored = 0, fluidCapacity = 0;
        int fluidCount = 0;
        if (graph != null) {
            for (StationAttachmentRegistry.ResolvedAttachment<?> ra : (Iterable<StationAttachmentRegistry.ResolvedAttachment<?>>) graph
                .getEnergyAttachments()::iterator) {
                stored = saturatedAdd(stored, energyStored(ra));
                capacity = saturatedAdd(capacity, energyCapacity(ra));
                count++;
            }
            for (StationAttachmentRegistry.ResolvedAttachment<?> ra : (Iterable<StationAttachmentRegistry.ResolvedAttachment<?>>) graph
                .getFluidStorageAttachments()::iterator) {
                fluidStored = saturatedAdd(fluidStored, fluidStoredValue(ra));
                fluidCapacity = saturatedAdd(fluidCapacity, fluidCapacityValue(ra));
                fluidCount++;
            }
        }

        if (lastSent != null && count == lastSent.attachmentCount()
            && stored == lastSent.totalStored()
            && capacity == lastSent.totalCapacity()
            && fluidStored == lastSent.fluidStored()
            && fluidCapacity == lastSent.fluidCapacity()
            && fluidCount == lastSent.fluidAttachmentCount()) return;

        EnergySnapshot publication = new EnergySnapshot(
            count,
            stored,
            capacity,
            fluidStored,
            fluidCapacity,
            fluidCount);
        lastSent = publication;
        syncToClient(OP_FULL_SYNC, buf -> {
            buf.writeInt(publication.attachmentCount());
            buf.writeLong(publication.totalStored());
            buf.writeLong(publication.totalCapacity());
            buf.writeLong(publication.fluidStored());
            buf.writeLong(publication.fluidCapacity());
            buf.writeInt(publication.fluidAttachmentCount());
        });
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {}

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id != OP_FULL_SYNC) return;
        int count = buf.readInt();
        long stored = buf.readLong();
        long capacity = buf.readLong();
        long fluidStored = buf.readLong();
        long fluidCapacity = buf.readLong();
        int fluidCount = buf.readInt();
        snapshot = new EnergySnapshot(count, stored, capacity, fluidStored, fluidCapacity, fluidCount);
    }

    @SideOnly(Side.CLIENT)
    public static EnergySnapshot getSnapshot() {
        StationGraphSyncHandler h = activeClientHandler;
        return h != null ? h.snapshot : new EnergySnapshot(0, 0, 0, 0, 0, 0);
    }

    private static long saturatedAdd(long accumulator, BigInteger value) {
        return BigInteger.valueOf(accumulator)
            .add(value)
            .min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }

    private static <T> BigInteger energyStored(StationAttachmentRegistry.ResolvedAttachment<T> ra) {
        return ((IEnergyHandler<T>) ra.handler()).getEnergyStored(ra.attachment());
    }

    private static <T> BigInteger energyCapacity(StationAttachmentRegistry.ResolvedAttachment<T> ra) {
        return ((IEnergyHandler<T>) ra.handler()).getEnergyCapacity(ra.attachment());
    }

    private static <T> BigInteger fluidStoredValue(StationAttachmentRegistry.ResolvedAttachment<T> ra) {
        return BigInteger.valueOf(((IFluidStorageHandler<T>) ra.handler()).getFluidStored(ra.attachment()));
    }

    private static <T> BigInteger fluidCapacityValue(StationAttachmentRegistry.ResolvedAttachment<T> ra) {
        return BigInteger.valueOf(((IFluidStorageHandler<T>) ra.handler()).getFluidCapacity(ra.attachment()));
    }
}
