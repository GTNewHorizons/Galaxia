package com.gtnewhorizons.galaxia.core.network;

import java.io.IOException;
import java.math.BigInteger;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileEntityAirlock;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnergyHandler;
import com.gtnewhorizons.galaxia.registry.interfaces.IFluidStorageHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lombok.Setter;

// TODO: Remove this completely, for now it stays since it's used in the rooms GUI
public final class StationGraphSyncHandler extends SyncHandler<StationGraphSyncHandler> {

    public static final String KEY = "station_graph_sync";
    private static final int OP_FULL_SYNC = 1;

    private volatile EnergySnapshot snapshot = new EnergySnapshot(0, 0, 0, 0, 0, 0);
    private volatile RoomSnapshot[] roomSnapshots = new RoomSnapshot[0];

    private int lastSentCount = -1;
    private long lastSentStored = -1;
    private long lastSentCapacity = -1;
    private long lastSentFluidStored = -1;
    private long lastSentFluidCapacity = -1;
    private int lastSentFluidCount = -1;
    private RoomSnapshot[] lastSentRooms = new RoomSnapshot[0];
    private int syncTicker;

    @Setter
    private TileStation station;

    @Setter
    private TileEntityAirlock airlock;

    public record EnergySnapshot(int attachmentCount, long totalStored, long totalCapacity, long fluidStored,
        long fluidCapacity, int fluidAttachmentCount) {}

    public record RoomSnapshot(boolean sealed, boolean oxygenated, int oxygenLevel, int volume, long powerStored,
        long powerCapacity) {}

    @Override
    public void init(String key, PanelSyncManager syncManager) {
        super.init(key, syncManager);
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (getSyncManager() == null || getSyncManager().isClient() || (station == null && airlock == null)) return;
        if (init) {
            forceDirty();
            triggerFullSync();
        } else if (++syncTicker % 20 == 0) {
            triggerFullSync();
        }
    }

    @Override
    public void dispose() {
        if (station != null) {
            station.clearActiveGraphSyncHandler(this);
            station = null;
        }
        airlock = null;
        super.dispose();
    }

    public void forceDirty() {
        lastSentCount = -1;
        lastSentStored = -1;
        lastSentCapacity = -1;
        lastSentFluidStored = -1;
        lastSentFluidCapacity = -1;
        lastSentFluidCount = -1;
        lastSentRooms = new RoomSnapshot[0];
    }

    public void triggerFullSync() {
        if (getSyncManager() == null || getSyncManager().isClient()) return;

        StationGraph graph = station != null ? station.getGraph() : null;
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

        RoomSnapshot[] rooms = airlock != null ? collectRooms() : new RoomSnapshot[0];

        if (count == lastSentCount && stored == lastSentStored
            && capacity == lastSentCapacity
            && fluidStored == lastSentFluidStored
            && fluidCapacity == lastSentFluidCapacity
            && fluidCount == lastSentFluidCount
            && roomsEqual(rooms, lastSentRooms)) return;

        lastSentCount = count;
        lastSentStored = stored;
        lastSentCapacity = capacity;
        lastSentFluidStored = fluidStored;
        lastSentFluidCapacity = fluidCapacity;
        lastSentFluidCount = fluidCount;
        lastSentRooms = rooms;

        final int fCount = count;
        final long fStored = stored;
        final long fCapacity = capacity;
        final long fFluidStored = fluidStored;
        final long fFluidCapacity = fluidCapacity;
        final int fFluidCount = fluidCount;
        syncToClient(OP_FULL_SYNC, buf -> {
            buf.writeInt(fCount);
            buf.writeLong(fStored);
            buf.writeLong(fCapacity);
            buf.writeLong(fFluidStored);
            buf.writeLong(fFluidCapacity);
            buf.writeInt(fFluidCount);
            buf.writeInt(rooms.length);
            for (RoomSnapshot room : rooms) {
                buf.writeBoolean(room.sealed());
                buf.writeBoolean(room.oxygenated());
                buf.writeInt(room.oxygenLevel());
                buf.writeInt(room.volume());
                buf.writeLong(room.powerStored());
                buf.writeLong(room.powerCapacity());
            }
        });
    }

    private RoomSnapshot[] collectRooms() {
        TileEntityAirlock lock = airlock;
        if (lock == null || lock.getWorldObj() == null) return new RoomSnapshot[0];
        return lock.getStationControllers()
            .stream()
            .map(pos -> pos.getTE(lock.getWorldObj()))
            .filter(te -> te instanceof TileStation)
            .map(te -> {
                TileStation room = (TileStation) te;
                long powerStored = 0, powerCapacity = 0;
                StationGraph roomGraph = room.getGraph();
                if (roomGraph != null) {
                    for (StationAttachmentRegistry.ResolvedAttachment<?> ra : (Iterable<StationAttachmentRegistry.ResolvedAttachment<?>>) roomGraph
                        .getEnergyAttachments()::iterator) {
                        powerStored = saturatedAdd(powerStored, energyStored(ra));
                        powerCapacity = saturatedAdd(powerCapacity, energyCapacity(ra));
                    }
                }
                return new RoomSnapshot(
                    room.isSealed(),
                    room.isOxygenated(),
                    (int) Math.round(room.getOxygenLevel()),
                    room.getVolume(),
                    powerStored,
                    powerCapacity);
            })
            .toArray(RoomSnapshot[]::new);
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

        int roomCount = buf.readInt();
        RoomSnapshot[] rooms = new RoomSnapshot[roomCount];
        for (int i = 0; i < roomCount; i++) {
            rooms[i] = new RoomSnapshot(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readLong(),
                buf.readLong());
        }
        roomSnapshots = rooms;
    }

    @SideOnly(Side.CLIENT)
    public EnergySnapshot getSnapshot() {
        return snapshot;
    }

    @SideOnly(Side.CLIENT)
    public RoomSnapshot[] getRoomSnapshots() {
        return roomSnapshots;
    }

    private static boolean roomsEqual(RoomSnapshot[] a, RoomSnapshot[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) return false;
        }
        return true;
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
