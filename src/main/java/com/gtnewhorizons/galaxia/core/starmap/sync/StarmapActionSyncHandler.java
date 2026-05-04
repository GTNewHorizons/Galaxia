package com.gtnewhorizons.galaxia.core.starmap.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.network.AssetInventoryUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetSyncPacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsConfigUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class StarmapActionSyncHandler extends SyncHandler {

    public static final String KEY = "starmap_actions";

    private static final int REQUEST_ACTION = 1;
    private static final int RESPONSE_SYNC = 2;
    private static final int REQUEST_MODULE_UPDATE = 3;
    private static final int REQUEST_INVENTORY_UPDATE = 4;
    private static final int REQUEST_LOGISTICS_CONFIG = 5;

    private static StarmapActionSyncHandler activeClientHandler;

    @Override
    public void init(String key, PanelSyncManager syncManager) {
        super.init(key, syncManager);
        if (syncManager.isClient()) {
            activeClientHandler = this;
        }
    }

    @Override
    public void dispose() {
        if (this == activeClientHandler) {
            activeClientHandler = null;
        }
        super.dispose();
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendCreateAsset(CelestialObjectId bodyId, String displayName, CelestialAsset.Kind kind,
        Buildable.Status status) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(
            REQUEST_ACTION,
            buf -> writePayload(buf, StarmapActionPayload.createAsset(bodyId, displayName, kind, status)));
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendBuildModule(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, boolean instantBuild, StationTileCoord coord) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(
            REQUEST_ACTION,
            buf -> writePayload(
                buf,
                StarmapActionPayload.buildModule(assetId, kind, shape, tier, instantBuild, coord)));
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendAssetOnly(StarmapAction action, CelestialAsset.ID assetId) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(REQUEST_ACTION, buf -> writePayload(buf, StarmapActionPayload.assetOnly(action, assetId)));
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendRenameAsset(CelestialAsset.ID assetId, String displayName) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(
            REQUEST_ACTION,
            buf -> writePayload(buf, StarmapActionPayload.renameAsset(assetId, displayName)));
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendModuleUpdate(AssetModuleUpdatePacket packet) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(REQUEST_MODULE_UPDATE, packet::toBytes);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendInventoryUpdate(AssetInventoryUpdatePacket packet) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(REQUEST_INVENTORY_UPDATE, packet::toBytes);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendLogisticsConfig(LogisticsConfigUpdatePacket packet) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(REQUEST_LOGISTICS_CONFIG, packet::toBytes);
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id != RESPONSE_SYNC) return;
        AssetSyncPacket packet = new AssetSyncPacket();
        packet.fromBytes(buf);
        AssetSyncPacket.Handler.handleClientSync(packet);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        EntityPlayer player = getSyncManager().getPlayer();
        if (!(player instanceof EntityPlayerMP playerMp)) return;
        UUID teamId = TempTeamCompat.getTeam(playerMp);
        switch (id) {
            case REQUEST_ACTION -> {
                StarmapActionResult result = StarmapServerActions
                    .apply(teamId, sanitizePayload(readPayload(buf), playerMp));
                syncResult(result);
            }
            case REQUEST_MODULE_UPDATE -> syncPacket(
                AssetModuleUpdatePacket.apply(teamId, readPacket(buf, AssetModuleUpdatePacket::new)));
            case REQUEST_INVENTORY_UPDATE -> syncPacket(
                AssetInventoryUpdatePacket.apply(
                    teamId,
                    playerMp.capabilities.isCreativeMode,
                    readPacket(buf, AssetInventoryUpdatePacket::new)));
            case REQUEST_LOGISTICS_CONFIG -> syncPacket(
                LogisticsConfigUpdatePacket.apply(teamId, readPacket(buf, LogisticsConfigUpdatePacket::new)));
            default -> {}
        }
    }

    private void syncResult(StarmapActionResult result) {
        if (result.applied()) {
            syncPacket(result.syncPacket());
        }
    }

    private void syncPacket(AssetSyncPacket packet) {
        if (packet != null) syncToClient(RESPONSE_SYNC, packet::toBytes);
    }

    private static <T> T readPacket(PacketBuffer buf, PacketFactory<T> factory) {
        T packet = factory.create();
        if (packet instanceof AssetModuleUpdatePacket moduleUpdate) {
            moduleUpdate.fromBytes(buf);
        } else if (packet instanceof AssetInventoryUpdatePacket inventoryUpdate) {
            inventoryUpdate.fromBytes(buf);
        } else if (packet instanceof LogisticsConfigUpdatePacket logisticsConfig) {
            logisticsConfig.fromBytes(buf);
        }
        return packet;
    }

    private interface PacketFactory<T> {

        T create();
    }

    private static StarmapActionPayload sanitizePayload(StarmapActionPayload payload, EntityPlayerMP player) {
        if (payload.action() != StarmapAction.BUILD_MODULE || !payload.instantBuild()
            || player.capabilities.isCreativeMode) {
            return payload;
        }
        return StarmapActionPayload.buildModule(
            payload.assetId(),
            payload.moduleKind(),
            payload.moduleShape(),
            payload.moduleTier(),
            false,
            payload.tileCoord());
    }

    private static void writePayload(PacketBuffer buf, StarmapActionPayload payload) {
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

    private static StarmapActionPayload readPayload(PacketBuffer buf) {
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
