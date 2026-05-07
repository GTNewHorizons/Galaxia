package com.gtnewhorizons.galaxia.core.network;

import java.io.IOException;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
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

    private static final int REQUEST_CREATE_ASSET = 1;
    private static final int REQUEST_UPDATE_ASSET = 2;
    private static final int REQUEST_BUILD_MODULE = 3;
    private static final int REQUEST_MODULE_UPDATE = 4;
    private static final int REQUEST_INVENTORY_UPDATE = 5;
    private static final int REQUEST_LOGISTICS_CONFIG = 6;

    private static final int RESPONSE_SYNC = 100;

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
        AssetCreatePacket packet = AssetCreatePacket.create(bodyId, displayName, kind, status);
        handler.syncToServer(REQUEST_CREATE_ASSET, packet::toBytes);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendBuildModule(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, boolean instantBuild, StationTileCoord coord) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        AssetBuildModulePacket packet = AssetBuildModulePacket.create(assetId, kind, shape, tier, instantBuild, coord);
        handler.syncToServer(REQUEST_BUILD_MODULE, packet::toBytes);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendUpdateAsset(AssetUpdatePacket packet) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || !handler.isValid()) return false;
        handler.syncToServer(REQUEST_UPDATE_ASSET, packet::toBytes);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendDestroyAsset(CelestialAsset.ID assetId) {
        return sendUpdateAsset(AssetUpdatePacket.create(assetId, AssetUpdatePacket.Action.DESTROY_ASSET));
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendRenameAsset(CelestialAsset.ID assetId, String displayName) {
        return sendUpdateAsset(AssetUpdatePacket.rename(assetId, displayName));
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendCancelConstruction(CelestialAsset.ID assetId) {
        return sendUpdateAsset(AssetUpdatePacket.create(assetId, AssetUpdatePacket.Action.CANCEL_CONSTRUCTION));
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendStartDeconstruction(CelestialAsset.ID assetId) {
        return sendUpdateAsset(AssetUpdatePacket.create(assetId, AssetUpdatePacket.Action.START_DECONSTRUCTION));
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendRequestFullSync(CelestialAsset.ID assetId) {
        return sendUpdateAsset(AssetUpdatePacket.create(assetId, AssetUpdatePacket.Action.REQUEST_FULL_SYNC));
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
        boolean creative = playerMp.capabilities.isCreativeMode;

        switch (id) {
            case REQUEST_CREATE_ASSET -> {
                AssetCreatePacket packet = new AssetCreatePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId));
            }
            case REQUEST_UPDATE_ASSET -> {
                AssetUpdatePacket packet = new AssetUpdatePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId));
            }
            case REQUEST_BUILD_MODULE -> {
                AssetBuildModulePacket packet = new AssetBuildModulePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId, creative));
            }
            case REQUEST_MODULE_UPDATE -> {
                AssetModuleUpdatePacket packet = new AssetModuleUpdatePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId, creative));
            }
            case REQUEST_INVENTORY_UPDATE -> {
                AssetInventoryUpdatePacket packet = new AssetInventoryUpdatePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId, creative));
            }
            case REQUEST_LOGISTICS_CONFIG -> {
                LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId));
            }
        }
    }

    private void syncPacket(AssetSyncPacket packet) {
        if (packet != null) syncToClient(RESPONSE_SYNC, packet::toBytes);
    }
}
