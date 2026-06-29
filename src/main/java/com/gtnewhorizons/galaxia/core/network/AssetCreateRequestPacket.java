package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeService;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetCreateRequestPacket implements IMessage {

    private CelestialObjectKey celestialObjectId;
    private String displayName;
    private CelestialAsset.Kind kind;
    private boolean operational;
    private SatelliteKind satelliteKind;

    private BlockPos controller;

    public AssetCreateRequestPacket() {}

    public static AssetCreateRequestPacket createFacility(CelestialObjectId celestialObjectId, String displayName,
        CelestialAsset.Kind kind, boolean operational) {
        return createFacility(CelestialObjectKey.registered(celestialObjectId), displayName, kind, operational);
    }

    public static AssetCreateRequestPacket createFacility(CelestialObjectKey celestialObjectId, String displayName,
        CelestialAsset.Kind kind, boolean operational) {
        AssetCreateRequestPacket pkt = new AssetCreateRequestPacket();

        pkt.celestialObjectId = celestialObjectId;
        pkt.displayName = displayName;
        pkt.kind = kind;
        pkt.operational = operational;

        return pkt;
    }

    public static AssetCreateRequestPacket createSatellite(CelestialObjectId celestialObjectId, SatelliteKind kind,
        boolean operational) {
        return createSatellite(CelestialObjectKey.registered(celestialObjectId), kind, operational);
    }

    public static AssetCreateRequestPacket createSatellite(CelestialObjectKey celestialObjectId, SatelliteKind kind,
        boolean operational) {
        AssetCreateRequestPacket pkt = new AssetCreateRequestPacket();

        pkt.celestialObjectId = celestialObjectId;
        pkt.displayName = "";
        pkt.kind = CelestialAsset.Kind.SATELLITE;
        pkt.operational = operational;
        pkt.satelliteKind = kind;

        return pkt;
    }

    public static AssetCreateRequestPacket createStation(CelestialObjectId celestialObjectId, String displayName,
        BlockPos controller) {
        return createStation(CelestialObjectKey.registered(celestialObjectId), displayName, controller);
    }

    public static AssetCreateRequestPacket createStation(CelestialObjectKey celestialObjectId, String displayName,
        BlockPos controller) {
        AssetCreateRequestPacket pkt = new AssetCreateRequestPacket();

        pkt.celestialObjectId = celestialObjectId;
        pkt.displayName = displayName;
        pkt.kind = CelestialAsset.Kind.STATION;
        pkt.operational = true;
        pkt.controller = controller;

        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeCelestialObjectKey(buf, celestialObjectId);
        PacketUtil.writeString(buf, displayName);
        PacketUtil.writeEnum(buf, kind);
        buf.writeBoolean(operational);
        if (kind == CelestialAsset.Kind.STATION) {
            buf.writeInt(controller.x());
            buf.writeInt(controller.y());
            buf.writeInt(controller.z());
        } else if (kind == CelestialAsset.Kind.SATELLITE) {
            PacketUtil.writeEnum(buf, requiredSatelliteKind());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        celestialObjectId = PacketUtil.readCelestialObjectKey(buf);
        displayName = PacketUtil.readString(buf);
        kind = PacketUtil.readEnum(buf, CelestialAsset.Kind.class);
        operational = buf.readBoolean();
        if (kind == CelestialAsset.Kind.STATION) {
            controller = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        } else if (kind == CelestialAsset.Kind.SATELLITE) {
            satelliteKind = PacketUtil.readEnum(buf, SatelliteKind.class);
        }
    }

    public AssetSyncPacket apply(UUID teamId) {
        validateTargetBody(teamId);
        CelestialAsset asset = CelestialAsset.create(celestialObjectId, kind, operational, requiredSatelliteKind());
        asset.setDisplayName(displayName);
        if (kind == CelestialAsset.Kind.STATION) {
            Station station = (Station) asset;
            station.setController(controller);
        }

        CelestialAssetStore.registerAsset(teamId, asset);

        Galaxia.LOG.info("[Outpost] Created asset {} ({}) at {}", asset.assetId, kind, celestialObjectId);

        return AssetSyncPacket.fullSync(asset);
    }

    private void validateTargetBody(UUID teamId) {
        CelestialObject body = CelestialRegistry.get(celestialObjectId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown celestial object for asset creation: " + celestialObjectId));
        if (kind == CelestialAsset.Kind.AUTOMATED_STATION && !body.properties()
            .canCreateStation()) {
            throw new IllegalArgumentException("Cannot create automated station on " + celestialObjectId);
        }
        if (kind == CelestialAsset.Kind.AUTOMATED_OUTPOST && !body.properties()
            .canCreateOutpost()) {
            throw new IllegalArgumentException("Cannot create automated outpost on " + celestialObjectId);
        }
        if (kind == CelestialAsset.Kind.AUTOMATED_OUTPOST && body.id()
            .isMinorBody()
            && !AsteroidFieldKnowledgeService.isDetected(teamId, body.id()
                .minorBodyId())) {
            throw new IllegalArgumentException("Cannot create automated outpost on hidden asteroid " + celestialObjectId);
        }
    }

    private SatelliteKind requiredSatelliteKind() {
        if (kind == CelestialAsset.Kind.SATELLITE && satelliteKind == null) {
            throw new IllegalStateException("satelliteKind is required");
        }
        return satelliteKind;
    }

    public static final class Handler implements IMessageHandler<AssetCreateRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(AssetCreateRequestPacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            if (!GTTeamsCompat.hasPermission(player, TeamAction.CREATE_ASSET)) return null;

            UUID teamId = GTTeamsCompat.getTeam(player);
            return packet.apply(teamId);
        }
    }
}
