package com.gtnewhorizons.galaxia.core.network;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;

final class AssetSyncPacket {

    private static final String ASSET_STATE_KEY = "assetState";
    private static final String HAMMER_STATUSES_KEY = "hammerDispatchStatuses";
    private static final String MODULE_ID_KEY = "moduleId";
    private static final String CODE_KEY = "code";
    private static final String REQUIRED_ENERGY_KEY = "requiredEnergy";
    private static final String STORED_ENERGY_KEY = "storedEnergy";
    private static final String SEND_AMOUNT_KEY = "sendAmount";
    private static final String ORDER_SIZE_KEY = "orderSize";

    static final byte STATE = 0;
    static final byte ASSET_REMOVED = 1;
    static final byte CLEAR = 2;

    private byte syncType;
    private long publishedRevision;
    private CelestialAsset.ID assetId;
    private byte[] statePayload;

    public AssetSyncPacket() {}

    static AssetSyncPacket state(UUID teamId, CelestialAsset asset,
        Map<ModuleInstance.ID, HammerDispatchStatus.Status> hammerStatuses) {
        AssetSyncPacket packet = new AssetSyncPacket();
        packet.syncType = STATE;
        packet.assetId = asset.assetId;
        packet.statePayload = encodeState(encodeStatePayload(teamId, asset, hammerStatuses));
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
        if (syncType == STATE) buf.writeBytes(statePayload);
    }

    public void fromBytes(ByteBuf buf, CelestialAsset.ID framedAssetId) {
        syncType = buf.readByte();
        publishedRevision = buf.readLong();
        assetId = framedAssetId;
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
                if (statePayload == null || statePayload.length < 1) {
                    throw new IllegalArgumentException("Invalid asset state publication");
                }
            }
            case ASSET_REMOVED, CLEAR -> {
                if (statePayload != null) throw new IllegalArgumentException("Invalid asset publication payload");
            }
            default -> throw new IllegalArgumentException("Unknown asset update type " + syncType);
        }
        boolean clear = syncType == CLEAR;
        boolean missingAssetId = assetId == null;
        boolean zeroRevision = publishedRevision == 0L;
        if (clear != missingAssetId || clear != zeroRevision || publishedRevision < 0L) {
            throw new IllegalArgumentException("Invalid asset publication identity or revision");
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

    StatePayload state() {
        NBTTagCompound encoded = decodeState(statePayload);
        if (!encoded.hasKey(ASSET_STATE_KEY, NBT.TAG_COMPOUND) || !encoded.hasKey(HAMMER_STATUSES_KEY, NBT.TAG_LIST)) {
            throw new IllegalArgumentException("Invalid asset state envelope");
        }
        return new StatePayload(
            encoded.getCompoundTag(ASSET_STATE_KEY),
            decodeHammerStatuses(encoded.getTagList(HAMMER_STATUSES_KEY, NBT.TAG_COMPOUND)));
    }

    private static NBTTagCompound encodeStatePayload(UUID teamId, CelestialAsset asset,
        Map<ModuleInstance.ID, HammerDispatchStatus.Status> hammerStatuses) {
        if (hammerStatuses == null) throw new IllegalArgumentException("Missing Hammer dispatch statuses");
        NBTTagCompound encoded = new NBTTagCompound();
        encoded.setTag(ASSET_STATE_KEY, AssetState.encode(teamId, asset));
        NBTTagList statuses = new NBTTagList();
        List<Map.Entry<ModuleInstance.ID, HammerDispatchStatus.Status>> entries = new ArrayList<>(
            hammerStatuses.entrySet());
        entries.sort(
            Comparator.comparing(
                entry -> entry.getKey()
                    .toString()));
        for (Map.Entry<ModuleInstance.ID, HammerDispatchStatus.Status> entry : entries) {
            ModuleInstance.ID moduleId = entry.getKey();
            HammerDispatchStatus.Status status = entry.getValue();
            NBTTagCompound statusTag = new NBTTagCompound();
            statusTag.setString(MODULE_ID_KEY, moduleId.toString());
            statusTag.setString(
                CODE_KEY,
                status.code()
                    .name());
            statusTag.setLong(REQUIRED_ENERGY_KEY, status.requiredEnergy());
            statusTag.setLong(STORED_ENERGY_KEY, status.storedEnergy());
            statusTag.setLong(SEND_AMOUNT_KEY, status.sendAmount());
            statusTag.setInteger(ORDER_SIZE_KEY, status.orderSize());
            statuses.appendTag(statusTag);
        }
        encoded.setTag(HAMMER_STATUSES_KEY, statuses);
        return encoded;
    }

    private static Map<ModuleInstance.ID, HammerDispatchStatus.Status> decodeHammerStatuses(NBTTagList statuses) {
        Map<ModuleInstance.ID, HammerDispatchStatus.Status> decoded = new LinkedHashMap<>();
        for (int i = 0; i < statuses.tagCount(); i++) {
            NBTTagCompound statusTag = statuses.getCompoundTagAt(i);
            ModuleInstance.ID moduleId = ModuleInstance.ID.from(statusTag.getString(MODULE_ID_KEY));
            HammerDispatchStatus.Status previous = decoded.put(
                moduleId,
                new HammerDispatchStatus.Status(
                    HammerDispatchStatus.Code.valueOf(statusTag.getString(CODE_KEY)),
                    statusTag.getLong(REQUIRED_ENERGY_KEY),
                    statusTag.getLong(STORED_ENERGY_KEY),
                    statusTag.getLong(SEND_AMOUNT_KEY),
                    statusTag.getInteger(ORDER_SIZE_KEY)));
            if (previous != null) throw new IllegalArgumentException("Duplicate Hammer dispatch status");
        }
        return Map.copyOf(decoded);
    }

    record StatePayload(NBTTagCompound assetState,
        Map<ModuleInstance.ID, HammerDispatchStatus.Status> hammerStatuses) {}
}
