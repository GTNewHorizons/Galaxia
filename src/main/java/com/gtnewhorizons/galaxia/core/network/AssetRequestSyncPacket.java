package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: requests a full sync for a single outpost.
 */
public final class AssetRequestSyncPacket implements IMessage {

    private CelestialAsset.ID assetId;

    public AssetRequestSyncPacket() {}

    public AssetRequestSyncPacket(CelestialAsset.ID assetId) {
        this.assetId = assetId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
    }

    public static final class Handler implements IMessageHandler<AssetRequestSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(AssetRequestSyncPacket packet, MessageContext ctx) {
            if (ctx.side != Side.SERVER) return null;
            AutomatedFacility state = CelestialClient.getByAssetId(packet.assetId) instanceof AutomatedFacility o ? o
                : null;
            if (state == null) {
                CelestialAsset asset = CelestialAssetStore.findAsset(packet.assetId);
                if (asset != null && asset.status() == CelestialAsset.Status.OPERATIONAL) {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                    UUID teamId = TempTeamCompat.getTeam(player);
                    CelestialAssetStore.add(teamId, asset);
                    Galaxia.LOG.info(
                        "[Outpost] Auto-created state for outpost {} (player {})",
                        packet.assetId,
                        player != null ? player.getGameProfile()
                            .getName() : "unknown");
                    state = CelestialClient.getByAssetId(packet.assetId) instanceof AutomatedFacility o ? o : null;
                }
            }
            if (state != null) {
                return AssetSyncPacket.fullSync(state);
            }
            return null;
        }
    }
}
