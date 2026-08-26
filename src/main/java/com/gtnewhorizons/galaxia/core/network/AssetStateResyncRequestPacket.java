package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetStateResyncRequestPacket implements IMessage {

    private CelestialAsset.ID assetId;

    public AssetStateResyncRequestPacket() {}

    AssetStateResyncRequestPacket(CelestialAsset.ID assetId) {
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

    public static final class Handler implements IMessageHandler<AssetStateResyncRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(AssetStateResyncRequestPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player != null && message.assetId != null) {
                ServerTickTaskQueue
                    .schedule(() -> AssetStateSync.SERVER.publishFullTo(player.getUniqueID(), message.assetId));
            }
            return null;
        }
    }
}
