package com.gtnewhorizons.galaxia.core.network;

import java.io.IOException;
import java.util.ArrayList;
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
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class StarmapActionSyncHandler extends SyncHandler<StarmapActionSyncHandler> {

    public static final String KEY = "starmap_actions";

    private static final int REQUEST_CREATE_ASSET = 1;
    private static final int REQUEST_UPDATE_ASSET = 2;
    private static final int REQUEST_BUILD_MODULE = 3;
    private static final int REQUEST_MODULE_UPDATE = 4;
    private static final int REQUEST_LOGISTICS_CONFIG = 6;
    private static final int REQUEST_FILTER_UPDATE = 7;
    private static final int REQUEST_SATELLITE_MUTATION = 8;

    private static final int RESPONSE_SYNC = 100;
    private static final int RESPONSE_ACTION_FAILED = 101;

    private static StarmapActionSyncHandler activeClientHandler;

    public enum SatelliteMutationOperation {
        ADD,
        SET,
        DELETE_AMOUNT,
        DELETE_ALL
    }

    public StarmapActionSyncHandler() {
        allowC2S();
    }

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
    public static boolean sendRegisterAsset(CelestialObjectKey bodyKey, CelestialAsset asset) {
        AssetCreateRequestPacket packet = switch (asset.kind) {
            case STATION -> AssetCreateRequestPacket
                .createStation(bodyKey, asset.displayName(), ((Station) asset).getController());
            case AUTOMATED_OUTPOST, AUTOMATED_STATION -> AssetCreateRequestPacket
                .createFacility(bodyKey, asset.displayName(), asset.kind, asset.isOperational());
            case SATELLITE -> AssetCreateRequestPacket
                .createSatellite(bodyKey, ((Satellite) asset).satelliteKind(), asset.isOperational());
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
        return sendBuildModules(
            assetId,
            kind,
            shape,
            tier,
            hammerVariant,
            minerFocusTier,
            settingsGroupId,
            0,
            instantBuild,
            coords);
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendBuildModules(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, HammerVariant hammerVariant, MinerFocusTier minerFocusTier, short settingsGroupId,
        int rotation, boolean instantBuild, List<StationTileCoord> coords) {
        return sendBuildModules(
            assetId,
            kind,
            shape,
            tier,
            hammerVariant,
            minerFocusTier,
            settingsGroupId,
            coords == null ? null
                : java.util.Collections.nCopies(coords.size(), ModuleShape.normalizeRotation(rotation)),
            instantBuild,
            coords);
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendBuildModules(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, HammerVariant hammerVariant, MinerFocusTier minerFocusTier, short settingsGroupId,
        List<Integer> rotations, boolean instantBuild, List<StationTileCoord> coords) {
        AssetBuildModulePacket packet = AssetBuildModulePacket.createManyWithSpec(
            assetId,
            kind,
            shape,
            tier,
            hammerVariant,
            minerFocusTier,
            settingsGroupId,
            rotations,
            instantBuild,
            coords);
        Galaxia.GALAXIA_NETWORK.sendToServer(packet);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendCopyModule(CelestialAsset.ID assetId, int sourceModuleIndex,
        ModuleInstance.ID sourceModuleId, boolean instantBuild, List<StationTileCoord> coords) {
        return sendCopyModule(assetId, sourceModuleIndex, sourceModuleId, 0, instantBuild, coords);
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendCopyModule(CelestialAsset.ID assetId, int sourceModuleIndex,
        ModuleInstance.ID sourceModuleId, int rotation, boolean instantBuild, List<StationTileCoord> coords) {
        return sendCopyModule(
            assetId,
            sourceModuleIndex,
            sourceModuleId,
            coords == null ? null
                : java.util.Collections.nCopies(coords.size(), ModuleShape.normalizeRotation(rotation)),
            instantBuild,
            coords);
    }

    @SideOnly(Side.CLIENT)
    public static boolean sendCopyModule(CelestialAsset.ID assetId, int sourceModuleIndex,
        ModuleInstance.ID sourceModuleId, List<Integer> rotations, boolean instantBuild,
        List<StationTileCoord> coords) {
        AssetBuildModulePacket packet = AssetBuildModulePacket
            .copyFromModule(assetId, sourceModuleIndex, sourceModuleId, rotations, instantBuild, coords);
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

    @SideOnly(Side.CLIENT)
    public static boolean sendSatelliteMutation(UUID teamId, CelestialObjectKey bodyKey, SatelliteKind kind,
        SatelliteMutationOperation operation, int amount) {
        StarmapActionSyncHandler handler = activeClientHandler;
        if (handler == null || teamId == null || bodyKey == null || kind == null || operation == null) return false;
        handler.syncToServer(REQUEST_SATELLITE_MUTATION, buf -> {
            PacketUtil.writeId(buf, teamId);
            PacketUtil.writeCelestialObjectKey(buf, bodyKey);
            PacketUtil.writeEnum(buf, kind);
            PacketUtil.writeEnum(buf, operation);
            buf.writeInt(amount);
        });
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

        switch (id) {
            case REQUEST_CREATE_ASSET -> {
                if (!GTTeamsCompat.hasPermission(playerMp, TeamAction.CREATE_ASSET)) {
                    syncFailure("Asset creation denied");
                    return;
                }
                AssetCreateRequestPacket packet = new AssetCreateRequestPacket();
                packet.fromBytes(buf);
                AssetSyncPacket sync;
                try {
                    sync = packet.apply(teamId);
                } catch (IllegalArgumentException ex) {
                    syncFailure(ex.getMessage());
                    return;
                }
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
                AssetSyncPacket sync = packet.apply(teamId, playerMp);
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
                syncPacket(packet.apply(teamId, playerMp));
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
            case REQUEST_SATELLITE_MUTATION -> {
                UUID debugTeamId = PacketUtil.readId(buf);
                CelestialObjectKey bodyKey = PacketUtil.readCelestialObjectKey(buf);
                SatelliteKind kind = PacketUtil.readEnum(buf, SatelliteKind.class);
                SatelliteMutationOperation operation = PacketUtil.readEnum(buf, SatelliteMutationOperation.class);
                int amount = buf.readInt();
                // Creating satellites is still test-only. Destruction uses the normal asset ownership permission.
                if (operation == SatelliteMutationOperation.ADD || operation == SatelliteMutationOperation.SET) {
                    // TODO: Remove this once satellite production is handled by normal gameplay.
                    if (!DebugActionAuthorization.isAuthorized(playerMp)) return;
                } else if (!GTTeamsCompat.hasPermission(playerMp, TeamAction.DESTROY_ASSET)) {
                    return;
                }
                try {
                    for (AssetSyncPacket packet : applySatelliteMutation(
                        debugTeamId,
                        bodyKey,
                        kind,
                        operation,
                        amount)) {
                        Galaxia.GALAXIA_NETWORK.sendTo(packet, playerMp);
                    }
                } catch (IllegalArgumentException ex) {
                    syncFailure(ex.getMessage());
                }
            }
        }
    }

    private void syncPacket(AssetSyncPacket packet) {
        if (packet != null) syncToClient(RESPONSE_SYNC, packet::toBytes);
    }

    private void syncFailure(String message) {
        syncToClient(RESPONSE_ACTION_FAILED, buf -> PacketUtil.writeString(buf, message));
    }

    private static List<AssetSyncPacket> applySatelliteMutation(UUID teamId, CelestialObjectKey bodyKey,
        SatelliteKind kind, SatelliteMutationOperation operation, int amount) {
        return switch (operation) {
            case ADD -> {
                if (amount <= 0) throw new IllegalArgumentException("Satellite ADD amount must be positive: " + amount);
                List<AssetSyncPacket> packets = new ArrayList<>(amount);
                for (int i = 0; i < amount; i++) {
                    // The ADD/SET branch above already rejected anyone who is not a creative operator, so these
                    // satellites skip construction the same way SET's do.
                    AssetSyncPacket packet = AssetCreateRequestPacket.createSatellite(bodyKey, kind, true)
                        .apply(teamId, true);
                    if (packet != null) packets.add(packet);
                }
                yield packets;
            }
            case SET -> syncSatelliteMutations(
                CelestialAssetStore.SERVER.setSatelliteCount(teamId, bodyKey, kind, amount));
            case DELETE_AMOUNT -> syncSatelliteMutations(
                CelestialAssetStore.SERVER.deleteSatelliteAmount(teamId, bodyKey, kind, amount));
            case DELETE_ALL -> syncSatelliteMutations(
                CelestialAssetStore.SERVER.deleteSatellites(teamId, bodyKey, kind));
        };
    }

    private static List<AssetSyncPacket> syncSatelliteMutations(List<CelestialAsset.ID> assetIds) {
        List<AssetSyncPacket> packets = new ArrayList<>(assetIds.size());
        for (CelestialAsset.ID assetId : assetIds) {
            CelestialAsset asset = CelestialAssetStore.SERVER.findAssetInternal(assetId);
            packets.add(asset == null ? AssetSyncPacket.assetRemoved(assetId) : AssetSyncPacket.fullSync(asset));
        }
        return packets;
    }
}
