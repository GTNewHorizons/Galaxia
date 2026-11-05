package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class AssetStateFramePacket implements IMessage {

    public static final int MAX_MESSAGE_BODY_BYTES = 32_000;
    public static final int MAX_LOGICAL_UPDATE_BYTES = 2 * 1024 * 1024;
    private static final int FRAME_HEADER_BYTES = 27;
    private static final int ASSET_ID_BYTES = 16;
    public static final int MAX_FRAME_PAYLOAD_BYTES = MAX_MESSAGE_BODY_BYTES - FRAME_HEADER_BYTES - ASSET_ID_BYTES;
    public static final int MAX_FRAME_COUNT = Math.ceilDiv(MAX_LOGICAL_UPDATE_BYTES, MAX_FRAME_PAYLOAD_BYTES);

    private CelestialAsset.ID assetId;
    private UUID updateId;
    private int frameIndex;
    private int frameCount;
    private int declaredTotalSize;
    private byte[] payload;

    public AssetStateFramePacket() {}

    AssetStateFramePacket(CelestialAsset.ID assetId, UUID updateId, int frameIndex, int frameCount,
        int declaredTotalSize, byte[] payload) {
        this.assetId = assetId;
        this.updateId = updateId;
        this.frameIndex = frameIndex;
        this.frameCount = frameCount;
        this.declaredTotalSize = declaredTotalSize;
        this.payload = payload == null ? null : payload.clone();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (!isValid()) throw new IllegalStateException("Cannot encode an invalid asset state frame");
        buf.writeBoolean(assetId != null);
        if (assetId != null) PacketUtil.writeId(buf, assetId);
        buf.writeLong(updateId.getMostSignificantBits());
        buf.writeLong(updateId.getLeastSignificantBits());
        buf.writeShort(frameIndex);
        buf.writeShort(frameCount);
        buf.writeInt(declaredTotalSize);
        buf.writeShort(payload == null ? -1 : payload.length);
        if (payload != null) buf.writeBytes(payload);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        payload = null;
        int encodedBodySize = buf.readableBytes();
        if (encodedBodySize < FRAME_HEADER_BYTES || encodedBodySize > MAX_MESSAGE_BODY_BYTES) return;
        try {
            boolean hasAssetId = buf.readBoolean();
            if (hasAssetId && buf.readableBytes() < FRAME_HEADER_BYTES + ASSET_ID_BYTES - 1) return;
            assetId = hasAssetId ? PacketUtil.readAssetId(buf) : null;
            updateId = new UUID(buf.readLong(), buf.readLong());
            frameIndex = buf.readUnsignedShort();
            frameCount = buf.readUnsignedShort();
            declaredTotalSize = buf.readInt();
            int payloadLength = buf.readUnsignedShort();
            if (payloadLength < 1 || payloadLength > MAX_FRAME_PAYLOAD_BYTES || payloadLength != buf.readableBytes())
                return;
            payload = new byte[payloadLength];
            buf.readBytes(payload);
        } catch (RuntimeException ex) {
            payload = null;
        }
    }

    boolean isValid() {
        if (updateId == null || payload == null || assetId != null && assetId.id() == null) return false;
        if (frameCount < 1 || frameCount > MAX_FRAME_COUNT || frameIndex < 0 || frameIndex >= frameCount) return false;
        if (declaredTotalSize < 1 || declaredTotalSize > MAX_LOGICAL_UPDATE_BYTES) return false;
        int expectedFrameCount = Math.ceilDiv(declaredTotalSize, MAX_FRAME_PAYLOAD_BYTES);
        if (frameCount != expectedFrameCount) return false;
        int expectedPayloadLength = frameIndex == frameCount - 1
            ? declaredTotalSize - frameIndex * MAX_FRAME_PAYLOAD_BYTES
            : MAX_FRAME_PAYLOAD_BYTES;
        return payload.length == expectedPayloadLength
            && FRAME_HEADER_BYTES + (assetId == null ? 0 : ASSET_ID_BYTES) + payload.length <= MAX_MESSAGE_BODY_BYTES;
    }

    CelestialAsset.ID assetId() {
        return assetId;
    }

    UUID updateId() {
        return updateId;
    }

    int frameIndex() {
        return frameIndex;
    }

    int frameCount() {
        return frameCount;
    }

    int declaredTotalSize() {
        return declaredTotalSize;
    }

    byte[] payload() {
        return payload == null ? null : payload.clone();
    }

    public static final class Handler implements IMessageHandler<AssetStateFramePacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(AssetStateFramePacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> AssetStateSync.CLIENT.receive(packet));
            return null;
        }
    }
}
