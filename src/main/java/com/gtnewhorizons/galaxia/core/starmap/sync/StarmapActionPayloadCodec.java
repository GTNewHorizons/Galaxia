package com.gtnewhorizons.galaxia.core.starmap.sync;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.network.PacketBuffer;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StarmapActionPayloadCodec {

    private StarmapActionPayloadCodec() {}

    static void write(PacketBuffer buf, StarmapActionPayload payload) {
        writeEnum(buf, payload.action());
        switch (payload.action()) {
            case CREATE_ASSET -> {
                writeEnum(buf, payload.bodyId());
                writeString(buf, payload.displayName());
                writeEnum(buf, payload.assetKind());
                writeEnum(buf, payload.assetStatus());
            }
            case BUILD_MODULE -> {
                writeAssetId(buf, payload.assetId());
                writeEnum(buf, payload.moduleKind());
                writeEnum(buf, payload.moduleShape());
                writeEnum(buf, payload.moduleTier());
                buf.writeBoolean(payload.instantBuild());
                buf.writeBoolean(payload.tileCoord() != null);
                if (payload.tileCoord() != null) writeStationTileCoord(buf, payload.tileCoord());
            }
            case DESTROY_ASSET, CANCEL_CONSTRUCTION, START_DECONSTRUCTION -> writeAssetId(buf, payload.assetId());
            case RENAME_ASSET -> {
                writeAssetId(buf, payload.assetId());
                writeString(buf, payload.displayName());
            }
            default -> {}
        }
    }

    static StarmapActionPayload read(PacketBuffer buf) {
        StarmapAction action = readEnum(buf, StarmapAction.class);
        return switch (action) {
            case CREATE_ASSET -> StarmapActionPayload.createAsset(
                readEnum(buf, CelestialObjectId.class),
                readString(buf),
                readEnum(buf, CelestialAsset.Kind.class),
                readEnum(buf, Buildable.Status.class));
            case BUILD_MODULE -> StarmapActionPayload.buildModule(
                readAssetId(buf),
                readEnum(buf, FacilityModuleKind.class),
                readEnum(buf, ModuleShape.class),
                readEnum(buf, ModuleTier.class),
                buf.readBoolean(),
                buf.readBoolean() ? readStationTileCoord(buf) : null);
            case DESTROY_ASSET, CANCEL_CONSTRUCTION, START_DECONSTRUCTION -> StarmapActionPayload
                .assetOnly(action, readAssetId(buf));
            case RENAME_ASSET -> StarmapActionPayload.renameAsset(readAssetId(buf), readString(buf));
            default -> throw new IllegalArgumentException("Unsupported starmap action: " + action);
        };
    }

    private static void writeAssetId(PacketBuffer buf, CelestialAsset.ID id) {
        UUID uuid = id.id();
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static CelestialAsset.ID readAssetId(PacketBuffer buf) {
        return new CelestialAsset.ID(new UUID(buf.readLong(), buf.readLong()));
    }

    private static void writeStationTileCoord(PacketBuffer buf, StationTileCoord coord) {
        buf.writeByte(coord.dx());
        buf.writeByte(coord.dy());
    }

    private static StationTileCoord readStationTileCoord(PacketBuffer buf) {
        return new StationTileCoord(buf.readByte(), buf.readByte());
    }

    private static <T extends Enum<T>> void writeEnum(PacketBuffer buf, T value) {
        buf.writeByte(value.ordinal());
    }

    private static <T extends Enum<T>> T readEnum(PacketBuffer buf, Class<T> enumClass) {
        T[] values = enumClass.getEnumConstants();
        int ordinal = buf.readUnsignedByte();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid ordinal " + ordinal + " for " + enumClass.getSimpleName());
        }
        return values[ordinal];
    }

    private static void writeString(PacketBuffer buf, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(PacketBuffer buf) {
        int length = buf.readUnsignedShort();
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
