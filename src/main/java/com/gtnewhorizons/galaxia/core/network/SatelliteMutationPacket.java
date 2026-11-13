package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Satellite commands use the gameplay channel independently of any open GUI or container. */
public final class SatelliteMutationPacket implements IMessage {

    public enum SatelliteMutationOperation {
        ADD,
        SET,
        DELETE_AMOUNT,
        DELETE_ALL
    }

    private UUID teamId;
    private CelestialObjectKey bodyKey;
    private SatelliteKind kind;
    private SatelliteMutationOperation operation;
    private int amount;

    public SatelliteMutationPacket() {}

    public SatelliteMutationPacket(UUID teamId, CelestialObjectKey bodyKey, SatelliteKind kind,
        SatelliteMutationOperation operation, int amount) {
        this.teamId = teamId;
        this.bodyKey = bodyKey;
        this.kind = kind;
        this.operation = operation;
        this.amount = amount;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, teamId);
        PacketUtil.writeCelestialObjectKey(buf, bodyKey);
        PacketUtil.writeEnum(buf, kind);
        PacketUtil.writeEnum(buf, operation);
        buf.writeInt(amount);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        teamId = PacketUtil.readId(buf);
        bodyKey = PacketUtil.readCelestialObjectKey(buf);
        kind = PacketUtil.readEnum(buf, SatelliteKind.class);
        operation = PacketUtil.readEnum(buf, SatelliteMutationOperation.class);
        amount = buf.readInt();
    }

    public static final class Handler implements IMessageHandler<SatelliteMutationPacket, IMessage> {

        @Override
        public IMessage onMessage(SatelliteMutationPacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerTickTaskQueue.schedule(() -> {
                // ADD/SET remain creative-operator tools. Destruction uses asset ownership permissions.
                if (packet.operation == SatelliteMutationOperation.ADD
                    || packet.operation == SatelliteMutationOperation.SET) {
                    if (!DebugActionAuthorization.isAuthorized(player)) return;
                } else if (!GTTeamsCompat.hasPermission(packet.teamId, player, TeamAction.DESTROY_ASSET)) {
                    return;
                }
                try {
                    for (CelestialAsset.ID assetId : packet.apply()) {
                        if (CelestialAssetStore.SERVER.findAssetInternal(assetId) == null) {
                            AssetStateSync.SERVER.publishRemoved(packet.teamId, assetId);
                        } else {
                            AssetStateSync.SERVER.publishInteractive(assetId);
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    Galaxia.GALAXIA_NETWORK.sendTo(StarmapActionStatusPacket.rejected(ex.getMessage()), player);
                }
            });
            return null;
        }
    }

    public List<CelestialAsset.ID> apply() {
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
