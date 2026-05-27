package com.gtnewhorizons.galaxia.core.network;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.gtnewhorizons.galaxia.client.gui.station.StationNotificationHelper;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class StarmapActionSyncHandler extends SyncHandler<StarmapActionSyncHandler> {

    public static final String KEY = "starmap_actions";

    private static final int REQUEST_CREATE_ASSET = 1;
    private static final int REQUEST_UPDATE_ASSET = 2;
    private static final int REQUEST_BUILD_MODULE = 3;
    private static final int REQUEST_MODULE_UPDATE = 4;
    private static final int REQUEST_INVENTORY_UPDATE = 5;
    private static final int REQUEST_LOGISTICS_CONFIG = 6;
    private static final int REQUEST_FILTER_UPDATE = 7;

    private static final int RESPONSE_SYNC = 100;
    private static final int RESPONSE_ACTION_FAILED = 101;

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
    public static boolean sendRegisterAsset(CelestialObjectId bodyId, CelestialAsset asset) {
        AssetCreateRequestPacket packet = switch (asset.kind) {
            case STATION -> AssetCreateRequestPacket
                .createStation(bodyId, asset.displayName(), ((Station) asset).getController());
            case AUTOMATED_OUTPOST, AUTOMATED_STATION -> AssetCreateRequestPacket
                .createFacility(bodyId, asset.displayName(), asset.kind, asset.isOperational());
        };
        Galaxia.GALAXIA_NETWORK.sendToServer(packet);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendBuildModule(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, boolean instantBuild, StationTileCoord coord) {
        return sendBuildModules(assetId, kind, shape, tier, instantBuild, coord == null ? null : List.of(coord));
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendBuildModules(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, boolean instantBuild, List<StationTileCoord> coords) {
        return sendBuildModules(assetId, kind, shape, tier, null, MinerFocusTier.NONE, (short) 0, instantBuild, coords);
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendBuildModules(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, HammerVariant hammerVariant, MinerFocusTier minerFocusTier, short settingsGroupId,
        boolean instantBuild, List<StationTileCoord> coords) {
        AssetBuildModulePacket packet = AssetBuildModulePacket.createManyWithSpec(
            assetId,
            kind,
            shape,
            tier,
            hammerVariant,
            minerFocusTier,
            settingsGroupId,
            instantBuild,
            coords);
        Galaxia.GALAXIA_NETWORK.sendToServer(packet);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendCopyModule(CelestialAsset.ID assetId, int sourceModuleIndex,
        ModuleInstance.ID sourceModuleId, boolean instantBuild, List<StationTileCoord> coords) {
        AssetBuildModulePacket packet = AssetBuildModulePacket
            .copyFromModule(assetId, sourceModuleIndex, sourceModuleId, instantBuild, coords);
        Galaxia.GALAXIA_NETWORK.sendToServer(packet);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendUpdateAsset(AssetUpdatePacket packet) {
        Galaxia.GALAXIA_NETWORK.sendToServer(packet);
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
        Galaxia.GALAXIA_NETWORK.sendToServer(packet);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendInventoryUpdate(AssetInventoryUpdatePacket packet) {
        Galaxia.GALAXIA_NETWORK.sendToServer(packet);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendLogisticsConfig(LogisticsConfigUpdatePacket packet) {
        Galaxia.GALAXIA_NETWORK.sendToServer(packet);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendFilterUpdate(AssetFilterUpdatePacket packet) {
        Galaxia.GALAXIA_NETWORK.sendToServer(packet);
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        switch (id) {
            case RESPONSE_SYNC -> {
                AssetSyncPacket packet = new AssetSyncPacket();
                packet.fromBytes(buf);
                AssetSyncPacket.Handler.handleClientSync(packet);
            }
            case RESPONSE_ACTION_FAILED -> {
                StationNotificationHelper.showFailure(PacketUtil.readString(buf));
            }
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        EntityPlayer player = getSyncManager().getPlayer();
        if (!(player instanceof EntityPlayerMP playerMp)) return;
        UUID teamId = GTTeamsCompat.getTeam(playerMp);
        boolean creative = playerMp.capabilities.isCreativeMode;

        switch (id) {
            case REQUEST_CREATE_ASSET -> {
                if (!GTTeamsCompat.hasPermission(playerMp, TeamAction.CREATE_ASSET)) {
                    syncFailure("Asset creation denied");
                    return;
                }
                AssetCreateRequestPacket packet = new AssetCreateRequestPacket();
                packet.fromBytes(buf);
                AssetSyncPacket sync = packet.apply(teamId);
                if (sync == null) {
                    syncFailure("Asset creation failed");
                } else {
                    syncPacket(sync);
                }
            }
            case REQUEST_UPDATE_ASSET -> {
                AssetUpdatePacket packet = new AssetUpdatePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId, playerMp));
            }
            case REQUEST_BUILD_MODULE -> {
                if (!GTTeamsCompat.hasPermission(playerMp, TeamAction.BUILD_MODULE)) return;
                AssetBuildModulePacket packet = new AssetBuildModulePacket();
                packet.fromBytes(buf);
                AssetSyncPacket sync = packet.apply(teamId, creative);
                if (sync == null) {
                    syncFailure("Module build failed");
                } else {
                    syncPacket(sync);
                }
            }
            case REQUEST_MODULE_UPDATE -> {
                if (!GTTeamsCompat.hasPermission(playerMp, TeamAction.MODIFY_MODULE)) return;
                AssetModuleUpdatePacket packet = new AssetModuleUpdatePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId, creative));
            }
            case REQUEST_INVENTORY_UPDATE -> {
                if (!GTTeamsCompat.hasPermission(playerMp, TeamAction.MANAGE_INVENTORY)) return;
                AssetInventoryUpdatePacket packet = new AssetInventoryUpdatePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId, creative));
            }
            case REQUEST_LOGISTICS_CONFIG -> {
                if (!GTTeamsCompat.hasPermission(playerMp, TeamAction.CONFIGURE_LOGISTICS)) return;
                LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId));
            }
            case REQUEST_FILTER_UPDATE -> {
                if (!GTTeamsCompat.hasPermission(playerMp, TeamAction.CONFIGURE_LOGISTICS)) return;
                AssetFilterUpdatePacket packet = new AssetFilterUpdatePacket();
                packet.fromBytes(buf);
                syncPacket(packet.apply(teamId));
            }
        }
    }

    private void syncPacket(AssetSyncPacket packet) {
        if (packet != null) syncToClient(RESPONSE_SYNC, packet::toBytes);
    }

    private void syncFailure(String message) {
        syncToClient(RESPONSE_ACTION_FAILED, buf -> PacketUtil.writeString(buf, message));
    }
}
