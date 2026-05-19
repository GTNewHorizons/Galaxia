package com.gtnewhorizons.galaxia.core.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import io.netty.buffer.ByteBuf;

/** Shared serialization helpers for outpost network packets. */
public final class PacketUtil {

    private PacketUtil() {}

    // ── String helpers ─────────────────────────────────────────────────────

    static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ── ID helpers ──────────────────────────────────────────────────

    static <T extends WithUUID> void writeId(ByteBuf buf, T with) {
        UUID uuid = with.id();
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    static UUID readId(ByteBuf buf) {
        long mostSig = buf.readLong();
        long leastSig = buf.readLong();
        return new UUID(mostSig, leastSig);
    }

    static ModuleInstance.ID readModuleId(ByteBuf buf) {
        return new ModuleInstance.ID(readId(buf));
    }

    static CelestialAsset.ID readAssetId(ByteBuf buf) {
        return new CelestialAsset.ID(readId(buf));
    }

    public static LogisticsDelivery.ID readDeliveryId(ByteBuf buf) {
        return new LogisticsDelivery.ID(readId(buf));
    }

    // ── Station tile helpers ───────────────────────────────────────────────

    static void writeStationTileCoord(ByteBuf buf, StationTileCoord coord) {
        buf.writeByte(coord.dx());
        buf.writeByte(coord.dy());
    }

    static StationTileCoord readStationTileCoord(ByteBuf buf) {
        byte dx = buf.readByte();
        byte dy = buf.readByte();
        return new StationTileCoord(dx, dy);
    }

    // ── Enum helpers ───────────────────────────────────────────────────────

    /** Convert an enum value to its unsigned byte ordinal. */
    public static <T extends Enum<T>> byte enumOrdinal(T value) {
        return (byte) Objects.requireNonNull(value, "value")
            .ordinal();
    }

    /**
     * Convert an unsigned byte ordinal to its enum value, or {@code null} if the ordinal
     * is out of range. Delegates to {@link #enumFromOrdinal(int, Class)}.
     */
    public static <T extends Enum<T>> T enumFromByte(int b, Class<T> enumClass) {
        return enumFromOrdinal(Byte.toUnsignedInt((byte) b), enumClass);
    }

    /** Shared ordinal → enum lookup. Returns {@code null} when ordinal is out of range. */
    @SuppressWarnings("unchecked")
    private static <T extends Enum<T>> T enumFromOrdinal(int ordinal, Class<T> enumClass) {
        T[] values = enumClass.getEnumConstants();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        return null;
    }

    static <T extends Enum<T>> void writeEnum(ByteBuf buf, T enumValue) {
        buf.writeByte(enumOrdinal(enumValue));
    }

    /**
     * Read an enum value from a {@link ByteBuf}. Malformed ordinals crash immediately.
     */
    static <T extends Enum<T>> T readEnum(ByteBuf buf, Class<T> enumClass) {
        int ordinal = buf.readUnsignedByte();
        T value = enumFromOrdinal(ordinal, enumClass);
        if (value != null) return value;
        throw new IllegalStateException(
            "[PacketUtil] Unknown enum ordinal " + ordinal + " for " + enumClass.getSimpleName());
    }

    // ── ItemStack helpers ──────────────────────────────────────────────

    static void writeInventoryKey(ByteBuf buf, InventoryKey key) {
        buf.writeBoolean(key.isItem());
        writeString(buf, key.toKey());
    }

    static InventoryKey readInventoryKey(ByteBuf buf) {
        final boolean item = buf.readBoolean();
        if (item) {
            return ItemStackWrapper.fromKey(readString(buf));
        } else {
            return FluidKey.fromName(readString(buf));
        }
    }

    /**
     * @param keys Must all be of the same type
     */
    static void writeInventoryKeys(ByteBuf buf, List<InventoryKey> keys) {
        if (keys.isEmpty()) return;
        buf.writeBoolean(
            keys.getFirst()
                .isItem());
        buf.writeInt(keys.size());
        for (InventoryKey key : keys) {
            writeString(buf, key.toKey());
        }
    }

    static List<InventoryKey> readInventoryKeys(ByteBuf buf) {
        final boolean item = buf.readBoolean();
        final int size = buf.readInt();
        final List<InventoryKey> ret = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (item) {
                ret.add(ItemStackWrapper.fromKey(readString(buf)));
            } else {
                ret.add(FluidKey.fromName(readString(buf)));
            }
        }

        return ret;
    }
}
