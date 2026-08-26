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
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
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
    private List<ModulePlacement> targets = List.of();

    public AssetBuildModulePacket() {}

    public static AssetBuildModulePacket create(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, boolean instantBuild, ModulePlacement target) {
        if (target == null) {
            throw new IllegalArgumentException("module build target must not be null");
        }
        return createMany(assetId, kind, shape, tier, instantBuild, List.of(target));
    }

    public static AssetBuildModulePacket createMany(CelestialAsset.ID assetId, FacilityModuleKind kind,
        ModuleShape shape, ModuleTier tier, boolean instantBuild, List<ModulePlacement> targets) {
        requireBuildSpec(kind, shape, tier);
        requireTargets(targets);
        AssetBuildModulePacket pkt = new AssetBuildModulePacket();
        pkt.assetId = assetId;
        pkt.moduleKind = kind;
        pkt.shape = shape;
        pkt.tier = tier;
        pkt.minerFocusTier = MinerFocusTier.NONE;
        pkt.instantBuild = instantBuild;
        pkt.targets = List.copyOf(targets);
        return pkt;
    }

    public static AssetBuildModulePacket createManyWithSpec(CelestialAsset.ID assetId, FacilityModuleKind kind,
        ModuleShape shape, ModuleTier tier, HammerVariant hammerVariant, MinerFocusTier minerFocusTier,
        short settingsGroupId, boolean instantBuild, List<ModulePlacement> targets) {
        AssetBuildModulePacket pkt = createMany(assetId, kind, shape, tier, instantBuild, targets);
        pkt.hammerVariant = hammerVariant;
        pkt.minerFocusTier = minerFocusTier == null ? MinerFocusTier.NONE : minerFocusTier;
        pkt.settingsGroupId = settingsGroupId;
        return pkt;
    }

    public static AssetBuildModulePacket copyFromModule(CelestialAsset.ID assetId, int sourceModuleIndex,
        ModuleInstance.ID sourceModuleId, boolean instantBuild, List<ModulePlacement> targets) {
        if (sourceModuleIndex < 0) {
            throw new IllegalArgumentException("copy module source index must be >= 0");
        }
        if (sourceModuleId == null) {
            throw new IllegalArgumentException("copy module source id must not be null");
        }
        requireTargets(targets);
        AssetBuildModulePacket pkt = new AssetBuildModulePacket();
        pkt.assetId = assetId;
        pkt.instantBuild = instantBuild;
        pkt.targets = List.copyOf(targets);
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
        buf.writeInt(targets.size());
        for (ModulePlacement target : targets) {
            PacketUtil.writeStationTileCoord(buf, target.anchor());
            buf.writeByte(target.rotation());
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
        if (targetCount <= 0 || targetCount > MAX_BUILD_TARGETS) {
            throw new IllegalArgumentException("invalid module build target count: " + targetCount);
        }
        List<ModulePlacement> decodedTargets = new ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) {
            decodedTargets.add(new ModulePlacement(PacketUtil.readStationTileCoord(buf), buf.readByte()));
        }
        targets = List.copyOf(decodedTargets);
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
            if (player == null) return null;
            ServerTickTaskQueue.schedule(() -> {
                if (!GTTeamsCompat.hasPermission(player, TeamAction.BUILD_MODULE)) return;
                UUID teamId = GTTeamsCompat.getTeam(player);
                if (message.apply(teamId, player)) AssetStateSync.SERVER.publishInteractive(message.assetId);
            });
            return null;
        }
    }

    public boolean apply(UUID teamId, EntityPlayerMP player) {
        if (player == null) return false;
        return apply(teamId, DebugActionAuthorization.isAuthorized(player));
    }

    public boolean apply(UUID teamId, boolean debugActionAuthorized) {
        if (teamId == null || assetId == null) {
            return false;
        }

        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null) return false;

        if (!CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            return false;
        }

        if (!(asset instanceof AutomatedFacility facility)) {
            return false;
        }

        ModuleInstance copySource = resolveCopySource(facility);
        if (isCopyBuild() && copySource == null) {
            return false;
        }

        FacilityModuleKind buildKind = copySource == null ? moduleKind : copySource.kind();
        ModuleShape buildShape = copySource == null ? shape : copySource.shape();
        ModuleTier buildTier = copySource == null ? tier : copySource.tier();
        HammerVariant buildHammerVariant = copySource == null ? hammerVariant : hammerVariantFor(copySource);
        MinerFocusTier buildMinerFocusTier = copySource == null ? normalizedMinerFocusTier()
            : minerFocusTierFor(copySource);

        if (buildKind == null || buildShape == null || buildTier == null) {
            return false;
        }
        if (buildKind.isDebugOnly() && !debugActionAuthorized) {
            return false;
        }
        if (!buildKind.isAllowedOn(asset.kind)) {
            return false;
        }

        if (!buildKind.allowedTiers()
            .contains(buildTier)) {
            return false;
        }
        if (buildShape != buildKind.defaultShape()) {
            return false;
        }
        if (!validatePhysicalSpec(buildKind, buildTier, buildHammerVariant, buildMinerFocusTier)) return false;
        if (!validateSettingsSpec(facility, buildKind, copySource)) return false;

        if (targets.isEmpty() || !validateAllTargets(facility, buildKind, buildShape)) {
            return false;
        }

        boolean shouldInstantBuild = instantBuild && debugActionAuthorized;
        for (ModulePlacement target : targets) {
            StationTileCoord anchor = target.anchor();
            int buildRotation = target.rotation();
            ModuleInstance module = buildKind.create(anchor, buildShape, buildTier);
            module.setRotation(buildRotation);
            if (!applyPhysicalSpec(module, buildTier, buildHammerVariant, buildMinerFocusTier)) return false;
            boolean copyRuntimeSettings = copySource != null && FacilityModuleRegistry.get(buildKind)
                .settingsGroups();
            if (copyRuntimeSettings && !facility.canCopyModuleRuntimeSettings(copySource, module)) return false;
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
                for (StationTileCoord coord : module.tiles()) {
                    facility.stationLayout()
                        .place(coord, new PlacedTile(module, initialState));
                }
            }
        }

        return true;
    }

    CelestialAsset.ID assetId() {
        return assetId;
    }

    private boolean validateAllTargets(AutomatedFacility facility, FacilityModuleKind moduleKind, ModuleShape shape) {
        if (targets.size() == 1 && StationTileCoord.CORE.equals(
            targets.get(0)
                .anchor())
            && !facility.hasStationLayout()) {
            return true;
        }
        if (!facility.hasStationLayout()) return false;
        PlanetaryFeatureKey requiredAnchorFeature = moduleKind.requiredAnchorFeature();
        Set<StationTileCoord> plannedTiles = new HashSet<>();
        Set<StationTileCoord> originalTiles = facility.stationLayout()
            .snapshot()
            .keySet();
        for (ModulePlacement target : targets) {
            StationTileCoord anchor = target.anchor();
            int rotation = target.rotation();
            if (!shape.fitsAt(anchor, rotation)) return false;
            if (requiredAnchorFeature != null && !facility.planetaryFeaturesAt(anchor)
                .contains(requiredAnchorFeature)) {
                return false;
            }
            StationTileCoord[] footprint = shape.tiles(anchor, rotation);
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

    private static void requireTargets(List<ModulePlacement> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("module build targets must not be empty");
        }
        if (targets.size() > MAX_BUILD_TARGETS) {
            throw new IllegalArgumentException("too many module build targets: " + targets.size());
        }
        if (targets.stream()
            .anyMatch(target -> target == null || target.anchor() == null)) {
            throw new IllegalArgumentException("module build targets must not contain null targets or anchors");
        }
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
