package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;

import io.netty.buffer.ByteBuf;

public final class AssetCreatePacket {

    private CelestialObjectId celestialObjectId;
    private String displayName;
    private CelestialAsset.Kind kind;
    private Buildable.Status status;

    public AssetCreatePacket() {}

    public static AssetCreatePacket create(CelestialObjectId celestialObjectId, String displayName,
        CelestialAsset.Kind kind, Buildable.Status status) {
        AssetCreatePacket pkt = new AssetCreatePacket();
        pkt.celestialObjectId = celestialObjectId;
        pkt.displayName = displayName;
        pkt.kind = kind;
        pkt.status = status;
        return pkt;
    }

    public void toBytes(ByteBuf buf) {
        PacketUtil.writeString(buf, celestialObjectId.toString());
        PacketUtil.writeString(buf, displayName == null ? "" : displayName);
        PacketUtil.writeEnum(buf, kind);
        PacketUtil.writeEnum(buf, status);
    }

    public void fromBytes(ByteBuf buf) {
        celestialObjectId = CelestialObjectId.fromString(PacketUtil.readString(buf));
        displayName = PacketUtil.readString(buf);
        kind = PacketUtil.readEnum(buf, CelestialAsset.Kind.class);
        status = PacketUtil.readEnum(buf, Buildable.Status.class);
    }

    public AssetSyncPacket apply(UUID teamId) {
        if (teamId == null || celestialObjectId == null || kind == null || status == null) {
            return null;
        }

        CelestialAsset asset = status == Buildable.Status.OPERATIONAL
            ? CelestialAssetStore.createOperationalAsset(teamId, celestialObjectId, displayName, kind)
            : CelestialAssetStore.createAssetInConstruction(teamId, celestialObjectId, displayName, kind);

        if (asset == null) {
            return null;
        }

        return AssetSyncPacket.fullSync(asset);
    }
}
