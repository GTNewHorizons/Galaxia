package com.gtnewhorizons.galaxia.core.starmap.sync;

import java.io.IOException;
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
            buf -> StarmapActionPayloadCodec
                .write(buf, StarmapActionPayload.createAsset(bodyId, displayName, kind, status)));
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendBuildModule(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, boolean instantBuild, StationTileCoord coord) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(
            REQUEST_ACTION,
            buf -> StarmapActionPayloadCodec
                .write(buf, StarmapActionPayload.buildModule(assetId, kind, shape, tier, instantBuild, coord)));
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendAssetOnly(StarmapAction action, CelestialAsset.ID assetId) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(
            REQUEST_ACTION,
            buf -> StarmapActionPayloadCodec.write(buf, StarmapActionPayload.assetOnly(action, assetId)));
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendRenameAsset(CelestialAsset.ID assetId, String displayName) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(
            REQUEST_ACTION,
            buf -> StarmapActionPayloadCodec.write(buf, StarmapActionPayload.renameAsset(assetId, displayName)));
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
                    .apply(teamId, sanitizePayload(StarmapActionPayloadCodec.read(buf), playerMp));
                syncResult(result);
            }
            case REQUEST_MODULE_UPDATE -> syncPacket(
                AssetModuleUpdatePacket.apply(teamId, readModuleUpdatePacket(buf)));
            case REQUEST_INVENTORY_UPDATE -> syncPacket(
                AssetInventoryUpdatePacket
                    .apply(teamId, playerMp.capabilities.isCreativeMode, readInventoryUpdatePacket(buf)));
            case REQUEST_LOGISTICS_CONFIG -> syncPacket(
                LogisticsConfigUpdatePacket.apply(teamId, readLogisticsConfigPacket(buf)));
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

    private static AssetModuleUpdatePacket readModuleUpdatePacket(PacketBuffer buf) {
        AssetModuleUpdatePacket packet = new AssetModuleUpdatePacket();
        packet.fromBytes(buf);
        return packet;
    }

    private static AssetInventoryUpdatePacket readInventoryUpdatePacket(PacketBuffer buf) {
        AssetInventoryUpdatePacket packet = new AssetInventoryUpdatePacket();
        packet.fromBytes(buf);
        return packet;
    }

    private static LogisticsConfigUpdatePacket readLogisticsConfigPacket(PacketBuffer buf) {
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket();
        packet.fromBytes(buf);
        return packet;
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
}
