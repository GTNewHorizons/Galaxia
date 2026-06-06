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
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
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
    private FacilityModuleKind moduleKind = FacilityModuleKind.POWER;
    private ModuleShape shape = ModuleShape.SINGLE;
    private ModuleTier tier = ModuleTier.HV;
    private HammerVariant hammerVariant;
    private MinerFocusTier minerFocusTier = MinerFocusTier.NONE;
    private short settingsGroupId;
    private int copySourceModuleIndex = -1;
    private ModuleInstance.ID copySourceModuleId;
    private boolean instantBuild;
    private List<StationTileCoord> tileCoords;

    public AssetBuildModulePacket() {}

    public static AssetBuildModulePacket create(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, boolean instantBuild, StationTileCoord tileCoord) {
        return createMany(assetId, kind, shape, tier, instantBuild, tileCoord == null ? null : List.of(tileCoord));
    }

    public static AssetBuildModulePacket createMany(CelestialAsset.ID assetId, FacilityModuleKind kind,
        ModuleShape shape, ModuleTier tier, boolean instantBuild, List<StationTileCoord> tileCoords) {
        requireBuildSpec(kind, shape, tier);
        if (tileCoords != null && tileCoords.size() > MAX_BUILD_TARGETS) {
            throw new IllegalArgumentException("too many module build targets: " + tileCoords.size());
        }
        AssetBuildModulePacket pkt = new AssetBuildModulePacket();
        pkt.assetId = assetId;
        pkt.moduleKind = kind;
        pkt.shape = shape;
        pkt.tier = tier;
        pkt.minerFocusTier = MinerFocusTier.NONE;
        pkt.instantBuild = instantBuild;
        pkt.tileCoords = tileCoords == null ? null : List.copyOf(tileCoords);
        return pkt;
    }

    public static AssetBuildModulePacket createManyWithSpec(CelestialAsset.ID assetId, FacilityModuleKind kind,
        ModuleShape shape, ModuleTier tier, HammerVariant hammerVariant, MinerFocusTier minerFocusTier,
        short settingsGroupId, boolean instantBuild, List<StationTileCoord> tileCoords) {
        AssetBuildModulePacket pkt = createMany(assetId, kind, shape, tier, instantBuild, tileCoords);
        pkt.hammerVariant = hammerVariant;
        pkt.minerFocusTier = minerFocusTier == null ? MinerFocusTier.NONE : minerFocusTier;
        pkt.settingsGroupId = settingsGroupId;
        return pkt;
    }

    public static AssetBuildModulePacket copyFromModule(CelestialAsset.ID assetId, int sourceModuleIndex,
        ModuleInstance.ID sourceModuleId, boolean instantBuild, List<StationTileCoord> tileCoords) {
        if (sourceModuleIndex < 0) {
            throw new IllegalArgumentException("copy module source index must be >= 0");
        }
        if (sourceModuleId == null) {
            throw new IllegalArgumentException("copy module source id must not be null");
        }
        if (tileCoords != null && tileCoords.size() > MAX_BUILD_TARGETS) {
            throw new IllegalArgumentException("too many module build targets: " + tileCoords.size());
        }
        AssetBuildModulePacket pkt = new AssetBuildModulePacket();
        pkt.assetId = assetId;
        pkt.instantBuild = instantBuild;
        pkt.tileCoords = tileCoords == null ? null : List.copyOf(tileCoords);
        pkt.copySourceModuleIndex = sourceModuleIndex;
        pkt.copySourceModuleId = sourceModuleId;
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
        } else {
            buf.writeInt(tileCoords.size());
            for (StationTileCoord coord : tileCoords) {
                PacketUtil.writeStationTileCoord(buf, coord);
            }
        }
        buf.writeBoolean(hammerVariant != null);
        if (hammerVariant != null) {
            PacketUtil.writeEnum(buf, hammerVariant);
        }
        PacketUtil.writeEnum(buf, minerFocusTier == null ? MinerFocusTier.NONE : minerFocusTier);
        buf.writeShort(settingsGroupId & 0xFFFF);
        buf.writeInt(copySourceModuleIndex);
        buf.writeBoolean(copySourceModuleId != null);
        if (copySourceModuleId != null) {
            PacketUtil.writeId(buf, copySourceModuleId);
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
        if (!buf.isReadable()) {
            minerFocusTier = MinerFocusTier.NONE;
            settingsGroupId = 0;
            copySourceModuleIndex = -1;
            copySourceModuleId = null;
            return;
        }
        hammerVariant = buf.readBoolean() ? PacketUtil.readEnum(buf, HammerVariant.class) : null;
        minerFocusTier = PacketUtil.readEnum(buf, MinerFocusTier.class);
        settingsGroupId = (short) buf.readUnsignedShort();
        copySourceModuleIndex = buf.readInt();
        copySourceModuleId = buf.readBoolean() ? PacketUtil.readModuleId(buf) : null;
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
        if (teamId == null || assetId == null) {
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

        ModuleInstance copySource = resolveCopySource(facility);
        if (isCopyBuild() && copySource == null) {
            return null;
        }

        FacilityModuleKind buildKind = copySource == null ? moduleKind : copySource.kind();
        ModuleShape buildShape = copySource == null ? shape : copySource.shape();
        ModuleTier buildTier = copySource == null ? tier : copySource.tier();
        HammerVariant buildHammerVariant = copySource == null ? hammerVariant : hammerVariantFor(copySource);
        MinerFocusTier buildMinerFocusTier = copySource == null ? normalizedMinerFocusTier()
            : minerFocusTierFor(copySource);

        if (buildKind == null || buildShape == null || buildTier == null) {
            return null;
        }
        if (!buildKind.isAllowedOn(asset.kind)) {
            return null;
        }

        if (!buildKind.allowedTiers()
            .contains(buildTier)) {
            return null;
        }
        if (buildShape != buildKind.defaultShape()) {
            return null;
        }
        if (!validatePhysicalSpec(buildKind, buildTier, buildHammerVariant, buildMinerFocusTier)) return null;
        if (!validateSettingsSpec(facility, buildKind, copySource)) return null;

        List<StationTileCoord> anchors = tileCoords;
        if (anchors == null) {
            anchors = List.of(StationTileCoord.CORE);
        }
        if (anchors.isEmpty()) return null;
        if (anchors.stream()
            .anyMatch(coord -> coord == null)) {
            return null;
        }
        if (!validateAllTargets(facility, anchors, buildKind, buildShape)) {
            return null;
        }

        boolean shouldInstantBuild = instantBuild && creativePlayer;
        for (StationTileCoord anchor : anchors) {
            ModuleInstance module = buildKind.create(anchor, buildShape, buildTier);
            if (!applyPhysicalSpec(module, buildTier, buildHammerVariant, buildMinerFocusTier)) return null;
            boolean copyRuntimeSettings = copySource != null && FacilityModuleRegistry.get(buildKind)
                .settingsGroups();
            if (copyRuntimeSettings && !facility.canCopyModuleRuntimeSettings(copySource, module)) return null;
            if (shouldInstantBuild) module.completeConstruction();

            facility.addModule(module);
            if (copyRuntimeSettings) {
                facility.copyModuleRuntimeSettings(copySource, module);
            } else if (settingsGroupId > 0) {
                facility.assignSettingsGroup(module, settingsGroupId);
            }
            facility.layoutCache()
                .applyMutation(MutationKind.PLACE, buildKind, module);

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
        FacilityModuleKind moduleKind, ModuleShape shape) {
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

    private boolean validatePhysicalSpec(FacilityModuleKind kind, ModuleTier targetTier,
        HammerVariant targetHammerVariant, MinerFocusTier targetMinerFocusTier) {
        if (targetHammerVariant != null) {
            if (kind != FacilityModuleKind.HAMMER) return false;
            if (!ModuleHammer.supportsTier(targetHammerVariant, targetTier)) return false;
        }
        MinerFocusTier focusTier = targetMinerFocusTier == null ? MinerFocusTier.NONE : targetMinerFocusTier;
        return focusTier == MinerFocusTier.NONE || kind == FacilityModuleKind.MINER;
    }

    private boolean validateSettingsSpec(AutomatedFacility facility, FacilityModuleKind kind,
        ModuleInstance copySource) {
        if (copySource != null) {
            return true;
        }
        if (settingsGroupId == 0) return true;
        if (!FacilityModuleRegistry.get(kind)
            .settingsGroups()) {
            return false;
        }
        return facility.canJoinSettingsGroup(kind, settingsGroupId);
    }

    private boolean applyPhysicalSpec(ModuleInstance module, ModuleTier targetTier, HammerVariant targetHammerVariant,
        MinerFocusTier targetMinerFocusTier) {
        try {
            if (module.component() instanceof ModuleHammer hammer && targetHammerVariant != null) {
                ModuleHammer.requireTier(targetHammerVariant, targetTier);
                hammer.setVariant(targetHammerVariant);
                module.setTier(targetTier);
            }
            if (module.component() instanceof ModuleMiner miner) {
                miner.setFocus(targetMinerFocusTier == null ? MinerFocusTier.NONE : targetMinerFocusTier, null, 0);
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isCopyBuild() {
        return copySourceModuleIndex >= 0 || copySourceModuleId != null;
    }

    private static void requireBuildSpec(FacilityModuleKind kind, ModuleShape shape, ModuleTier tier) {
        if (kind == null) {
            throw new IllegalArgumentException("module kind must not be null");
        }
        if (shape == null) {
            throw new IllegalArgumentException("module shape must not be null");
        }
        if (tier == null) {
            throw new IllegalArgumentException("module tier must not be null");
        }
    }

    private ModuleInstance resolveCopySource(AutomatedFacility facility) {
        if (!isCopyBuild()) return null;
        if (copySourceModuleIndex < 0 || copySourceModuleId == null) return null;
        List<ModuleInstance> modules = facility.modules();
        if (copySourceModuleIndex >= modules.size()) return null;
        ModuleInstance source = modules.get(copySourceModuleIndex);
        return copySourceModuleId.equals(source.id) ? source : null;
    }

    private MinerFocusTier normalizedMinerFocusTier() {
        return minerFocusTier == null ? MinerFocusTier.NONE : minerFocusTier;
    }

    private static HammerVariant hammerVariantFor(ModuleInstance source) {
        return source.component() instanceof ModuleHammer hammer ? hammer.variant() : null;
    }

    private static MinerFocusTier minerFocusTierFor(ModuleInstance source) {
        return source.component() instanceof ModuleMiner miner ? miner.focusTier() : MinerFocusTier.NONE;
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
