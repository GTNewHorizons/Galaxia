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
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class StarmapActionSyncHandler extends SyncHandler<StarmapActionSyncHandler> {

    public static final String KEY = "starmap_actions";

    private static final int REQUEST_SATELLITE_MUTATION = 8;

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
            case RESPONSE_ACTION_FAILED -> {
                StationNotificationHelper.showFailure(PacketUtil.readString(buf));
            }
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        EntityPlayer player = getSyncManager().getPlayer();
        if (!(player instanceof EntityPlayerMP playerMp)) return;
        PacketBuffer queued = new PacketBuffer(buf.copy());
        ServerTickTaskQueue.schedule(() -> {
            try {
                handleOnServer(id, queued, playerMp);
            } catch (IOException ex) {
                Galaxia.LOG.warn("Rejected starmap action from {}", playerMp.getCommandSenderName(), ex);
            } finally {
                queued.release();
            }
        });
    }

    private void handleOnServer(int id, PacketBuffer buf, EntityPlayerMP playerMp) throws IOException {
        switch (id) {
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
                    for (CelestialAsset.ID assetId : applySatelliteMutation(
                        debugTeamId,
                        bodyKey,
                        kind,
                        operation,
                        amount)) {
                        if (CelestialAssetStore.SERVER.findAssetInternal(assetId) == null) {
                            AssetStateSync.SERVER.publishRemoved(debugTeamId, assetId);
                        } else {
                            AssetStateSync.SERVER.publishInteractive(assetId);
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    syncFailure(ex.getMessage());
                }
            }
        }
    }

    private void syncFailure(String message) {
        syncToClient(RESPONSE_ACTION_FAILED, buf -> PacketUtil.writeString(buf, message));
    }

    private static List<CelestialAsset.ID> applySatelliteMutation(UUID teamId, CelestialObjectKey bodyKey,
        SatelliteKind kind, SatelliteMutationOperation operation, int amount) {
        return switch (operation) {
            case ADD -> {
                if (amount <= 0) throw new IllegalArgumentException("Satellite ADD amount must be positive: " + amount);
                List<CelestialAsset.ID> assetIds = new ArrayList<>(amount);
                for (int i = 0; i < amount; i++) {
                    // The ADD/SET branch above already rejected anyone who is not a creative operator, so these
                    // satellites skip construction the same way SET's do.
                    CelestialAsset asset = AssetCreateRequestPacket.createSatellite(bodyKey, kind, true)
                        .apply(teamId, true);
                    if (asset != null) assetIds.add(asset.assetId);
                }
                yield assetIds;
            }
            case SET -> CelestialAssetStore.SERVER.setSatelliteCount(teamId, bodyKey, kind, amount);
            case DELETE_AMOUNT -> CelestialAssetStore.SERVER.deleteSatelliteAmount(teamId, bodyKey, kind, amount);
            case DELETE_ALL -> CelestialAssetStore.SERVER.deleteSatellites(teamId, bodyKey, kind);
        };
    }
}
