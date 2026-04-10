package com.gtnewhorizons.galaxia.outpost.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.api.celestial.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStatus;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: requests a full sync for a single outpost.
 */
public final class OutpostRequestSyncPacket implements IMessage {

    private String assetId;

    public OutpostRequestSyncPacket() {}

    public OutpostRequestSyncPacket(String assetId) {
        this.assetId = assetId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = readString(buf);
    }

    public static final class Handler implements IMessageHandler<OutpostRequestSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostRequestSyncPacket packet, MessageContext ctx) {
            if (ctx.side != Side.SERVER) return null;
            AutomatedOutpostState state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) {
                // Lazily create state the first time a client opens the management UI
                CelestialManagedAsset asset = CelestialAssetStore.findAsset(packet.assetId);
                if (asset != null && asset.status() == CelestialAssetStatus.OPERATIONAL) {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                    UUID teamId = player != null ? player.getUniqueID() : new UUID(0L, 0L);
                    String bodyId = asset.celestialObjectId();
                    String systemId = resolveSystemId(bodyId);
                    state = new AutomatedOutpostState(asset.assetId(), teamId, bodyId, systemId);
                    OutpostDataStore.get()
                        .put(state);
                    Galaxia.LOG.info(
                        "[Outpost] Auto-created state for outpost {} (player {})",
                        packet.assetId,
                        player != null ? player.getGameProfile()
                            .getName() : "unknown");
                }
            }
            if (state != null) {
                return new OutpostFullSyncPacket(state);
            }
            return null;
        }
    }

    /**
     * Resolves the stellar system id (host star body id) for a given celestial body id.
     * Falls back to {@code bodyId} itself if the tree or host star cannot be found.
     */
    static String resolveSystemId(String bodyId) {
        OrbitalCelestialBody root = GalaxiaCelestialAPI.getPrimaryRoot();
        if (root == null || bodyId == null) return bodyId;
        OrbitalCelestialBody body = OrbitalTransferPlanner.findBodyById(root, bodyId);
        if (body == null) return bodyId;
        OrbitalCelestialBody star = OrbitalTransferPlanner.findHostStar(root, body);
        return star != null ? star.id() : bodyId;
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
