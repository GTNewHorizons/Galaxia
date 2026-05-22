package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetBuildModulePacket implements IMessage {

    private static final int MAX_BUILD_TARGETS = 256;

    private CelestialAsset.ID assetId;
    private FacilityModuleKind moduleKind;
    private ModuleShape shape;
    private ModuleTier tier;
    private boolean instantBuild;
    private List<StationTileCoord> tileCoords;

    public AssetBuildModulePacket() {}

    public static AssetBuildModulePacket create(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, boolean instantBuild, StationTileCoord tileCoord) {
        return createMany(assetId, kind, shape, tier, instantBuild, tileCoord == null ? null : List.of(tileCoord));
    }

    public static AssetBuildModulePacket createMany(CelestialAsset.ID assetId, FacilityModuleKind kind,
        ModuleShape shape, ModuleTier tier, boolean instantBuild, List<StationTileCoord> tileCoords) {
        if (tileCoords != null && tileCoords.size() > MAX_BUILD_TARGETS) {
            throw new IllegalArgumentException("too many module build targets: " + tileCoords.size());
        }
        AssetBuildModulePacket pkt = new AssetBuildModulePacket();
        pkt.assetId = assetId;
        pkt.moduleKind = kind;
        pkt.shape = shape;
        pkt.tier = tier;
        pkt.instantBuild = instantBuild;
        pkt.tileCoords = tileCoords == null ? null : List.copyOf(tileCoords);
        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeEnum(buf, moduleKind);
        PacketUtil.writeEnum(buf, shape);
        PacketUtil.writeEnum(buf, tier);
        buf.writeBoolean(instantBuild);
        if (tileCoords == null) {
            buf.writeInt(-1);
            return;
        }
        buf.writeInt(tileCoords.size());
        for (StationTileCoord coord : tileCoords) {
            PacketUtil.writeStationTileCoord(buf, coord);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleKind = PacketUtil.readEnum(buf, FacilityModuleKind.class);
        shape = PacketUtil.readEnum(buf, ModuleShape.class);
        tier = PacketUtil.readEnum(buf, ModuleTier.class);
        instantBuild = buf.readBoolean();
        int targetCount = buf.readInt();
        if (targetCount < 0) {
            tileCoords = null;
        } else {
            if (targetCount > MAX_BUILD_TARGETS) {
                throw new IllegalArgumentException("too many module build targets: " + targetCount);
            }
            tileCoords = new ArrayList<>(targetCount);
            for (int i = 0; i < targetCount; i++) {
                tileCoords.add(PacketUtil.readStationTileCoord(buf));
            }
        }
    }

    public static class Handler implements IMessageHandler<AssetBuildModulePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetBuildModulePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (!GTTeamsCompat.hasPermission(player, TeamAction.BUILD_MODULE)) return null;
            UUID teamId = GTTeamsCompat.getTeam(player);
            boolean creative = player.capabilities.isCreativeMode;
            return message.apply(teamId, creative);
        }
    }

    public AssetSyncPacket apply(UUID teamId, boolean creativePlayer) {
        if (teamId == null || assetId == null || moduleKind == null || shape == null || tier == null) {
            return null;
        }

        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null) return null;

        if (!CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            return null;
        }

        if (!(asset instanceof AutomatedFacility facility)) {
            return null;
        }

        if (!moduleKind.isAllowedOn(asset.kind)) {
            return null;
        }

        if (!moduleKind.allowedTiers()
            .contains(tier)) {
            return null;
        }
        if (shape != moduleKind.defaultShape()) {
            return null;
        }

        List<StationTileCoord> anchors = tileCoords;
        if (anchors == null) {
            anchors = List.of(StationTileCoord.CORE);
        }
        if (anchors.isEmpty()) return null;
        if (anchors.stream()
            .anyMatch(coord -> coord == null)) {
            return null;
        }
        if (!validateAllTargets(facility, anchors, moduleKind)) {
            return null;
        }

        boolean shouldInstantBuild = instantBuild && creativePlayer;
        for (StationTileCoord anchor : anchors) {
            ModuleInstance module = moduleKind.create(anchor, shape, tier);
            if (shouldInstantBuild) module.completeConstruction();

            facility.addModule(module);
            facility.layoutCache()
                .applyMutation(MutationKind.PLACE, moduleKind, module);

            if (facility.hasStationLayout() && module.anchorOrNull() != null) {
                StationTileState initialState = StationTileState.fromModuleStatus(module.status());
                for (StationTileCoord coord : module.shape()
                    .tiles(module.anchor())) {
                    facility.stationLayout()
                        .place(coord, new PlacedTile(module, initialState));
                }
            }
        }

        return AssetSyncPacket.fullSync(facility);
    }

    private boolean validateAllTargets(AutomatedFacility facility, List<StationTileCoord> anchors,
        FacilityModuleKind moduleKind) {
        if (anchors.size() == 1 && StationTileCoord.CORE.equals(anchors.get(0)) && !facility.hasStationLayout()) {
            return true;
        }
        if (!facility.hasStationLayout()) return false;
        PlanetaryFeatureKey requiredAnchorFeature = moduleKind.requiredAnchorFeature();
        Set<StationTileCoord> plannedTiles = new HashSet<>();
        Set<StationTileCoord> originalTiles = facility.stationLayout()
            .snapshot()
            .keySet();
        for (StationTileCoord anchor : anchors) {
            if (!shape.fitsAt(anchor)) return false;
            if (requiredAnchorFeature != null && !facility.planetaryFeaturesAt(anchor)
                .contains(requiredAnchorFeature)) {
                return false;
            }
            StationTileCoord[] footprint = shape.tiles(anchor);
            boolean hasAdjacent = false;
            for (StationTileCoord coord : footprint) {
                if (originalTiles.contains(coord) || plannedTiles.contains(coord)) return false;
                if (!hasAdjacent && hasKnownOccupiedNeighbour(originalTiles, plannedTiles, coord)) hasAdjacent = true;
            }
            if (!hasAdjacent) return false;
            for (StationTileCoord coord : footprint) {
                plannedTiles.add(coord);
            }
        }
        return true;
    }

    private static boolean hasKnownOccupiedNeighbour(Set<StationTileCoord> originalTiles,
        Set<StationTileCoord> plannedTiles, StationTileCoord coord) {
        return containsKnown(originalTiles, plannedTiles, coord.dx() - 1, coord.dy())
            || containsKnown(originalTiles, plannedTiles, coord.dx() + 1, coord.dy())
            || containsKnown(originalTiles, plannedTiles, coord.dx(), coord.dy() - 1)
            || containsKnown(originalTiles, plannedTiles, coord.dx(), coord.dy() + 1);
    }

    private static boolean containsKnown(Set<StationTileCoord> originalTiles, Set<StationTileCoord> plannedTiles,
        int dx, int dy) {
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX) return false;
        if (dy < StationTileCoord.MIN || dy > StationTileCoord.MAX) return false;
        StationTileCoord coord = StationTileCoord.of(dx, dy);
        return originalTiles.contains(coord) || plannedTiles.contains(coord);
    }
}
