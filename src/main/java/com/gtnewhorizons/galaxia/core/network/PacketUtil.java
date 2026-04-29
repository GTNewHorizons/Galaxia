package com.gtnewhorizons.galaxia.core.network;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
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

    static <T extends Enum<T>> void writeEnum(ByteBuf buf, T enumValue) {
        buf.writeByte(enumValue.ordinal());
    }

    @SuppressWarnings("unchecked")
    static <T extends Enum<T>> T readEnum(ByteBuf buf, Class<T> enumClass) {
        int ordinal = buf.readUnsignedByte();
        T[] values = enumClass.getEnumConstants();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        Galaxia.LOG.warn(
            "[PacketUtil] Unknown enum ordinal {} for {}, falling back to {}",
            ordinal,
            enumClass.getSimpleName(),
            values[0]);
        return values[0];
    }

    public static <T extends Enum<T>> byte enumOrdinal(T value) {
        return (byte) value.ordinal();
    }

    public static <T extends Enum<T>> T enumFromByte(int b, Class<T> enumClass) {
        int ordinal = Byte.toUnsignedInt((byte) b);
        T[] values = enumClass.getEnumConstants();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        Galaxia.LOG.warn(
            "[PacketUtil] Unknown enum ordinal {} for {}, falling back to {}",
            ordinal,
            enumClass.getSimpleName(),
            values[0]);
        return values[0];
    }

}
