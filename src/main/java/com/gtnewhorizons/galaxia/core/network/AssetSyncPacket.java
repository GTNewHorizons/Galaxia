package com.gtnewhorizons.galaxia.core.network;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;

final class AssetSyncPacket {

    static final byte STATE = 0;
    static final byte ASSET_REMOVED = 1;
    static final byte CLEAR = 2;

    private byte syncType;
    private long publishedRevision;
    private CelestialAsset.ID assetId;
    private byte[] statePayload;

    public AssetSyncPacket() {}

    static AssetSyncPacket state(UUID teamId, CelestialAsset asset) {
        AssetSyncPacket packet = new AssetSyncPacket();
        packet.syncType = STATE;
        packet.assetId = asset.assetId;
        packet.statePayload = encodeState(AssetState.encode(teamId, asset));
        return packet;
    }

    static AssetSyncPacket assetRemoved(CelestialAsset.ID assetId, long revision) {
        AssetSyncPacket packet = new AssetSyncPacket();
        packet.syncType = ASSET_REMOVED;
        packet.assetId = assetId;
        packet.publishedRevision = revision;
        return packet;
    }

    static AssetSyncPacket clear() {
        AssetSyncPacket packet = new AssetSyncPacket();
        packet.syncType = CLEAR;
        return packet;
    }

    AssetSyncPacket withPublishedRevision(long revision) {
        AssetSyncPacket packet = new AssetSyncPacket();
        packet.syncType = syncType;
        packet.assetId = assetId;
        packet.statePayload = statePayload;
        packet.publishedRevision = revision;
        return packet;
    }

    boolean hasSameState(AssetSyncPacket other) {
        return other != null && Arrays.equals(statePayload, other.statePayload);
    }

    public void toBytes(ByteBuf buf) {
        validate();
        buf.writeByte(syncType);
        buf.writeLong(publishedRevision);
        if (syncType != CLEAR) PacketUtil.writeId(buf, assetId);
        if (syncType == STATE) buf.writeBytes(statePayload);
    }

    public void fromBytes(ByteBuf buf) {
        syncType = buf.readByte();
        publishedRevision = buf.readLong();
        if (syncType != CLEAR) assetId = PacketUtil.readAssetId(buf);
        if (syncType == STATE) {
            statePayload = new byte[buf.readableBytes()];
            buf.readBytes(statePayload);
        }
        validate();
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing asset update bytes");
    }

    private void validate() {
        switch (syncType) {
            case STATE -> {
                if (publishedRevision < 1L || assetId == null
                    || statePayload == null
                    || statePayload.length < 1
                    || statePayload.length > AssetStateFramePacket.MAX_LOGICAL_UPDATE_BYTES) {
                    throw new IllegalArgumentException("Invalid asset state publication");
                }
            }
            case ASSET_REMOVED -> {
                if (publishedRevision < 1L || assetId == null) {
                    throw new IllegalArgumentException("Invalid asset removal publication");
                }
            }
            case CLEAR -> {
                if (publishedRevision != 0L || assetId != null || statePayload != null) {
                    throw new IllegalArgumentException("Invalid asset clear publication");
                }
            }
            default -> throw new IllegalArgumentException("Unknown asset update type " + syncType);
        }
    }

    private static byte[] encodeState(NBTTagCompound state) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            CompressedStreamTools.write(state, new ByteBufOutputStream(buffer));
            if (buffer.readableBytes() > AssetStateFramePacket.MAX_LOGICAL_UPDATE_BYTES) {
                throw new IllegalArgumentException("Asset state exceeds the logical update limit");
            }
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.readBytes(encoded);
            return encoded;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not encode asset state", ex);
        } finally {
            buffer.release();
        }
    }

    private static NBTTagCompound decodeState(byte[] encoded) {
        if (encoded == null || encoded.length < 1 || encoded.length > AssetStateFramePacket.MAX_LOGICAL_UPDATE_BYTES) {
            throw new IllegalArgumentException("Invalid asset state size");
        }
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
            DataInputStream input = new DataInputStream(bytes)) {
            NBTTagCompound state = CompressedStreamTools
                .func_152456_a(input, new NBTSizeTracker(AssetStateFramePacket.MAX_LOGICAL_UPDATE_BYTES));
            if (bytes.available() != 0) throw new IllegalArgumentException("Trailing canonical asset state bytes");
            return state;
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("Invalid canonical asset state", ex);
        }
    }

    byte syncType() {
        return syncType;
    }

    CelestialAsset.ID assetId() {
        return assetId;
    }

    long publishedRevision() {
        return publishedRevision;
    }

    NBTTagCompound state() {
        return decodeState(statePayload);
    }
}
