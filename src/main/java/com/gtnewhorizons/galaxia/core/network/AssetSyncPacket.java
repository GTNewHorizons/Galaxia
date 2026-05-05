package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlotList;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class AssetSyncPacket implements IMessage {

    public static final byte FULL_SYNC = 0;
    public static final byte MODULE_ADDED = 1;
    public static final byte MODULE_REMOVED = 2;
    public static final byte MODULE_UPDATED = 3;
    public static final byte INVENTORY_UPDATE = 4;
    public static final byte LOGISTICS_CONFIG_UPDATED = 6;
    public static final byte LOGISTICS_CONFIG_REMOVED = 7;
    public static final byte LAYOUT_TILE_UPDATED = 8;
    public static final byte LAYOUT_TILE_REMOVED = 9;
    public static final byte ASSET_REMOVED = 10;
    public static final byte MINER_VOID_CONFIG_UPDATED = 11;

    private CelestialAsset.ID assetId;
    private byte syncType;

    private int syncRevision;

    private UUID teamId;
    private CelestialObjectId celestialBodyId;
    private CelestialObjectId systemId;
    private CelestialObjectId planetaryAnchorBodyId;
    private Buildable.Status assetStatus;
    private CelestialAsset.Kind assetKind;
    private String displayName;
    private long energyStored;

    private List<AssetSyncPacket> fullSyncDeltas;

    private int moduleIndex;
    private ModuleInstance.ID moduleId;
    private ModuleInstance moduleData;

    private String resourceKey;
    private long inventoryDelta;
    private LogisticsResourceConfig logConfig;
    private int minerVoidChancePercent;

    private StationTileCoord tileCoord;
    private StationTileState tileState;
    private ModuleInstance.ID tileModuleId;

    public AssetSyncPacket() {}

    public static AssetSyncPacket fullSync(AutomatedFacility state) {
        AssetSyncPacket pkt = fullSync((CelestialAsset) state);
        pkt.systemId = state.systemId;
        pkt.planetaryAnchorBodyId = state.planetaryAnchorBodyId;
        pkt.energyStored = state.getEnergyStored();

        List<ModuleInstance> modules = state.modules();
        for (int i = 0; i < modules.size(); i++) {
            pkt.fullSyncDeltas.add(moduleAdded(state.assetId, i, modules.get(i)));
        }

        for (Map.Entry<ItemStackWrapper, Long> e : state.inventory.snapshot()
            .entrySet()) {
            pkt.fullSyncDeltas.add(
                inventoryUpdate(
                    state.assetId,
                    e.getKey()
                        .toKey(),
                    e.getValue()));
        }

        for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> e : state.logisticsConfig.snapshot()
            .entrySet()) {
            LogisticsResourceConfig cfg = e.getValue();
            pkt.fullSyncDeltas.add(
                logisticsConfigUpdated(
                    state.assetId,
                    e.getKey()
                        .toKey(),
                    cfg.minReserve(),
                    cfg.orderSize(),
                    cfg.isImportEnabled(),
                    cfg.isSupplyEnabled()));
        }

        for (Map.Entry<String, Integer> e : state.minerVoidChances()
            .entrySet()) {
            pkt.fullSyncDeltas.add(minerVoidConfigUpdated(state.assetId, e.getKey(), e.getValue()));
        }

        StationLayout layout = state.stationLayout();
        if (layout != null) {
            for (Map.Entry<StationTileCoord, PlacedTile> e : layout.snapshot()
                .entrySet()) {
                pkt.fullSyncDeltas.add(layoutTileUpdated(state.assetId, e.getKey(), e.getValue()));
            }
        }

        return pkt;
    }

    public static AssetSyncPacket fullSync(CelestialAsset state) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = state.assetId;
        pkt.syncType = FULL_SYNC;
        pkt.syncRevision = state.getSyncRevision();
        pkt.syncRevision = state.getSyncRevision();

        pkt.teamId = CelestialAssetStore.getTeamId(state.assetId);
        pkt.celestialBodyId = state.celestialObjectId;
        pkt.assetStatus = state.status();
        pkt.assetKind = state.kind;
        pkt.displayName = state.displayName();
        pkt.systemId = CelestialObjectId.INVALID;
        pkt.planetaryAnchorBodyId = CelestialObjectId.INVALID;
        pkt.energyStored = 0L;

        pkt.fullSyncDeltas = new ArrayList<>();
        return pkt;
    }

    public static AssetSyncPacket assetRemoved(CelestialAsset.ID assetId) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = ASSET_REMOVED;
        return pkt;
    }

    public static AssetSyncPacket moduleAdded(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MODULE_ADDED;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleData = module;
        return pkt;
    }

    public static AssetSyncPacket moduleRemoved(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MODULE_REMOVED;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        return pkt;
    }

    public static AssetSyncPacket moduleUpdated(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MODULE_UPDATED;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleData = module;
        return pkt;
    }

    public static AssetSyncPacket inventoryUpdate(CelestialAsset.ID assetId, String resourceKey, long delta) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = INVENTORY_UPDATE;
        pkt.resourceKey = resourceKey;
        pkt.inventoryDelta = delta;
        return pkt;
    }

    public static AssetSyncPacket logisticsConfigUpdated(CelestialAsset.ID assetId, String resourceKey, int minReserve,
        int orderSize, boolean importEnabled, boolean supplyEnabled) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LOGISTICS_CONFIG_UPDATED;
        pkt.resourceKey = resourceKey;
        pkt.logConfig = new LogisticsResourceConfig(minReserve, orderSize, importEnabled, supplyEnabled);
        return pkt;
    }

    public static AssetSyncPacket logisticsConfigRemoved(CelestialAsset.ID assetId, String resourceKey) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LOGISTICS_CONFIG_REMOVED;
        pkt.resourceKey = resourceKey;
        return pkt;
    }

    public static AssetSyncPacket layoutTileUpdated(CelestialAsset.ID assetId, StationTileCoord coord,
        PlacedTile tile) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LAYOUT_TILE_UPDATED;
        pkt.tileCoord = coord;
        pkt.tileState = tile.state();
        pkt.tileModuleId = tile.module() == null ? null : tile.module().id;
        return pkt;
    }

    public static AssetSyncPacket layoutTileRemoved(CelestialAsset.ID assetId, StationTileCoord coord) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LAYOUT_TILE_REMOVED;
        pkt.tileCoord = coord;
        return pkt;
    }

    public static AssetSyncPacket minerVoidConfigUpdated(CelestialAsset.ID assetId, String oreKey, int percent) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MINER_VOID_CONFIG_UPDATED;
        pkt.resourceKey = Objects.requireNonNull(oreKey, "oreKey");
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("miner void chance percent out of range: " + percent);
        }
        pkt.minerVoidChancePercent = percent;
        return pkt;
    }

    /**
     * Decides what to sync for the given facility and player. Returns a list of packets
     * (full sync or individual deltas) and updates the facility's dirty/sync state.
     */
    public static List<AssetSyncPacket> figureOutWhatToSend(AutomatedFacility facility, UUID playerId) {
        List<AssetSyncPacket> packets = new ArrayList<>();
        if (facility.needsFullSyncFor(playerId)) {
            packets.add(fullSync(facility));
            facility.markSyncedFor(playerId);
            facility.drainDirtyModules();
            facility.drainRemovedIds();
            facility.drainDirtyMinerVoidChances();
            return packets;
        }
        if (!facility.isDirty()) {
            return packets;
        }
        for (ModuleInstance.ID id : facility.drainRemovedIds()) {
            packets.add(
                moduleRemoved(facility.assetId, facility.moduleIndex(id), id)
                    .withSyncRevision(facility.getSyncRevision()));
        }
        for (ModuleInstance m : facility.drainDirtyModules()) {
            int idx = facility.moduleIndex(m.id);
            packets.add(moduleAdded(facility.assetId, idx, m).withSyncRevision(facility.getSyncRevision()));
        }
        for (Map.Entry<String, Integer> e : facility.drainDirtyMinerVoidChances()
            .entrySet()) {
            packets.add(
                minerVoidConfigUpdated(facility.assetId, e.getKey(), e.getValue())
                    .withSyncRevision(facility.getSyncRevision()));
        }
        return packets;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        buf.writeByte(syncType);
        buf.writeInt(syncRevision);
        buf.writeInt(syncRevision);

        switch (syncType) {
            case FULL_SYNC -> {
                buf.writeLong(teamId.getMostSignificantBits());
                buf.writeLong(teamId.getLeastSignificantBits());
                PacketUtil.writeEnum(buf, celestialBodyId);
                PacketUtil.writeEnum(buf, systemId);
                PacketUtil.writeEnum(buf, planetaryAnchorBodyId);
                PacketUtil.writeEnum(buf, assetStatus);
                PacketUtil.writeEnum(buf, assetKind);
                PacketUtil.writeString(buf, displayName == null ? "" : displayName);
                buf.writeLong(energyStored);

                buf.writeInt(fullSyncDeltas.size());
                for (AssetSyncPacket d : fullSyncDeltas) {
                    buf.writeByte(d.syncType);
                    d.writeDelta(buf);
                }
            }
            default -> writeDelta(buf);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        syncType = buf.readByte();
        syncRevision = buf.readInt();
        syncRevision = buf.readInt();

        switch (syncType) {
            case FULL_SYNC -> {
                teamId = new UUID(buf.readLong(), buf.readLong());
                celestialBodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
                systemId = PacketUtil.readEnum(buf, CelestialObjectId.class);
                planetaryAnchorBodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
                assetStatus = PacketUtil.readEnum(buf, Buildable.Status.class);
                assetKind = PacketUtil.readEnum(buf, CelestialAsset.Kind.class);
                displayName = PacketUtil.readString(buf);
                energyStored = buf.readLong();

                int count = buf.readInt();
                fullSyncDeltas = new ArrayList<>(count);

                for (int i = 0; i < count; i++) {
                    AssetSyncPacket d = new AssetSyncPacket();
                    d.assetId = assetId;
                    d.syncType = buf.readByte();
                    d.readDelta(buf);
                    fullSyncDeltas.add(d);
                }
            }
            default -> readDelta(buf);
        }
    }

    private void writeDelta(ByteBuf buf) {
        switch (syncType) {
            case MODULE_ADDED, MODULE_UPDATED -> {
                buf.writeInt(moduleIndex);
                writeModule(buf, moduleData);
            }
            case MODULE_REMOVED -> {
                buf.writeInt(moduleIndex);
                PacketUtil.writeId(buf, moduleId);
            }
            case INVENTORY_UPDATE -> {
                PacketUtil.writeString(buf, resourceKey);
                buf.writeLong(inventoryDelta);
            }
            case LOGISTICS_CONFIG_UPDATED -> {
                PacketUtil.writeString(buf, resourceKey);
                writeLogisticsConfig(buf, logConfig);
            }
            case LOGISTICS_CONFIG_REMOVED -> PacketUtil.writeString(buf, resourceKey);
            case LAYOUT_TILE_UPDATED -> {
                PacketUtil.writeStationTileCoord(buf, tileCoord);
                PacketUtil.writeEnum(buf, tileState);
                boolean hasModule = tileModuleId != null;
                buf.writeBoolean(hasModule);
                if (hasModule) PacketUtil.writeId(buf, tileModuleId);
            }
            case LAYOUT_TILE_REMOVED -> PacketUtil.writeStationTileCoord(buf, tileCoord);
            case MINER_VOID_CONFIG_UPDATED -> {
                PacketUtil.writeString(buf, resourceKey);
                buf.writeByte(minerVoidChancePercent);
            }
        }
    }

    private void readDelta(ByteBuf buf) {
        switch (syncType) {
            case MODULE_ADDED, MODULE_UPDATED -> {
                moduleIndex = buf.readInt();
                moduleData = readModule(buf);
            }
            case MODULE_REMOVED -> {
                moduleIndex = buf.readInt();
                moduleId = PacketUtil.readModuleId(buf);
            }
            case INVENTORY_UPDATE -> {
                resourceKey = PacketUtil.readString(buf);
                inventoryDelta = buf.readLong();
            }
            case LOGISTICS_CONFIG_UPDATED -> {
                resourceKey = PacketUtil.readString(buf);
                logConfig = readLogisticsConfig(buf);
            }
            case LOGISTICS_CONFIG_REMOVED -> resourceKey = PacketUtil.readString(buf);
            case LAYOUT_TILE_UPDATED -> {
                tileCoord = PacketUtil.readStationTileCoord(buf);
                tileState = PacketUtil.readEnum(buf, StationTileState.class);
                tileModuleId = buf.readBoolean() ? PacketUtil.readModuleId(buf) : null;
            }
            case LAYOUT_TILE_REMOVED -> tileCoord = PacketUtil.readStationTileCoord(buf);
            case MINER_VOID_CONFIG_UPDATED -> {
                resourceKey = PacketUtil.readString(buf);
                minerVoidChancePercent = buf.readUnsignedByte();
                if (minerVoidChancePercent > 100) {
                    throw new IllegalArgumentException(
                        "miner void chance percent out of range: " + minerVoidChancePercent);
                }
            }
        }
    }

    private static void writeModule(ByteBuf buf, ModuleInstance module) {
        PacketUtil.writeId(buf, module.id);
        PacketUtil.writeEnum(buf, module.kind());
        PacketUtil.writeEnum(buf, module.status());
        PacketUtil.writeEnum(buf, module.tier());
        PacketUtil.writeEnum(buf, module.shape());
        PacketUtil.writeEnum(buf, module.priorityOverride());
        buf.writeBoolean(module.enabled());
        buf.writeShort(module.groupId());
        buf.writeByte(module.component() instanceof IParallelModule pm ? pm.getParallel() : 1);

        StationTileCoord anchor = module.anchorOrNull();
        buf.writeBoolean(anchor != null);
        if (anchor != null) PacketUtil.writeStationTileCoord(buf, anchor);

        switch (module.kind()) {
            case MINER -> {}
            case HAMMER -> {
                ModuleHammer h = (ModuleHammer) module.component();
                PacketUtil.writeEnum(
                    buf,
                    h.config()
                        .mode());
                buf.writeDouble(
                    h.config()
                        .threshold());
                PacketUtil.writeEnum(buf, h.routePriority());
                PacketUtil.writeEnum(buf, h.variant());
            }
            case POWER -> {}
            case STORAGE, TANK, BATTERY -> {}
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> writeRecipeConfig(
                buf,
                module);
            default -> {}
        }
    }

    private static ModuleInstance readModule(ByteBuf buf) {
        ModuleInstance.ID id = PacketUtil.readModuleId(buf);
        FacilityModuleKind kind = PacketUtil.readEnum(buf, FacilityModuleKind.class);
        Buildable.Status status = PacketUtil.readEnum(buf, Buildable.Status.class);
        ModuleTier tier = PacketUtil.readEnum(buf, ModuleTier.class);
        ModuleShape shape = PacketUtil.readEnum(buf, ModuleShape.class);
        ModulePriority modulePriority = PacketUtil.readEnum(buf, ModulePriority.class);
        boolean enabled = buf.readBoolean();
        short groupId = buf.readShort();
        byte parallel = buf.readByte();
        StationTileCoord anchor = buf.readBoolean() ? PacketUtil.readStationTileCoord(buf) : null;

        ModuleInstance module = FacilityModuleRegistry.create(id, kind, anchor, shape, tier);
        module.setPriorityOverride(modulePriority);
        module.setEnabled(enabled);
        module.setGroupId(groupId);

        switch (kind) {
            case MINER -> {}
            case HAMMER -> {
                AllowShootingConfig cfg = new AllowShootingConfig(
                    PacketUtil.readEnum(buf, AllowShootingConfig.Mode.class),
                    buf.readDouble());
                OrbitalTransferPlanner.RoutePriority routePriority = PacketUtil
                    .readEnum(buf, OrbitalTransferPlanner.RoutePriority.class);
                HammerVariant variant = PacketUtil.readEnum(buf, HammerVariant.class);
                ModuleHammer.requireTier(variant, tier);
                module.setComponent(new ModuleHammer(kind, cfg, routePriority, false, variant, 64));
            }
            case POWER -> {}
            case STORAGE, TANK, BATTERY -> {}
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> readRecipeConfig(
                buf,
                module);
            default -> {}
        }

        if (module.component() instanceof IParallelModule pm) {
            pm.setParallel(parallel);
        }
        module.updateStatus(status);
        return module;
    }

    private static void writeLogisticsConfig(ByteBuf buf, LogisticsResourceConfig cfg) {
        buf.writeInt(cfg.minReserve());
        buf.writeInt(cfg.orderSize());
        buf.writeBoolean(cfg.isImportEnabled());
        buf.writeBoolean(cfg.isSupplyEnabled());
    }

    private static LogisticsResourceConfig readLogisticsConfig(ByteBuf buf) {
        return new LogisticsResourceConfig(buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean());
    }

    private static void writeRecipeConfig(ByteBuf buf, ModuleInstance module) {
        if (!(module.component() instanceof IRecipeModule recipeModule)) {
            buf.writeBoolean(false);
            return;
        }
        RecipeConfig config = recipeModule.getRecipeConfig();
        if (config == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        buf.writeByte(
            config.mode()
                .ordinal());
        buf.writeByte(
            config.notDoablePolicy()
                .ordinal());
        buf.writeByte(config.orderCursor());
        buf.writeByte(config.orderRemaining());

        List<RecipeSlot> slots = config.slots()
            .toList();
        buf.writeByte(slots.size());
        for (RecipeSlot slot : slots) {
            RecipeSnapshot snap = slot.recipe();
            buf.writeByte(snap.recipeMapOrdinal());
            buf.writeInt(snap.recipeIndex());
            buf.writeLong(snap.contentHash());
            buf.writeBoolean(slot.enabled());
            buf.writeInt(slot.inputGuard());
            buf.writeInt(slot.outputGuard());
            buf.writeByte(slot.priority());
            buf.writeByte(slot.orderSize());
        }
    }

    private static void readRecipeConfig(ByteBuf buf, ModuleInstance module) {
        if (!buf.readBoolean()) return;
        int modeOrd = Byte.toUnsignedInt(buf.readByte());
        int policyOrd = Byte.toUnsignedInt(buf.readByte());
        byte orderCursor = buf.readByte();
        byte orderRemaining = buf.readByte();

        RecipeSchedulerMode[] modes = RecipeSchedulerMode.values();
        if (modeOrd >= modes.length) return;
        RecipeSchedulerMode mode = modes[modeOrd];

        NotDoablePolicy[] policies = NotDoablePolicy.values();
        if (policyOrd >= policies.length) return;
        NotDoablePolicy policy = policies[policyOrd];

        int slotCount = Byte.toUnsignedInt(buf.readByte());
        if (slotCount < 0 || slotCount > RecipeSlotList.MAX_RECIPE_SLOTS) return;

        RecipeConfig config = new RecipeConfig(new RecipeSlotList(), mode, policy, orderCursor, orderRemaining);

        for (int i = 0; i < slotCount; i++) {
            byte mapOrdinal = buf.readByte();
            int recipeIndex = buf.readInt();
            long contentHash = buf.readLong();
            boolean enabled = buf.readBoolean();
            int inputGuard = buf.readInt();
            int outputGuard = buf.readInt();
            byte priority = buf.readByte();
            byte orderSize = buf.readByte();

            RecipeSnapshot ref = RecipeSnapshot.unresolved(mapOrdinal, recipeIndex, contentHash);
            RecipeSlot slot = new RecipeSlot(ref, enabled, inputGuard, outputGuard, priority, orderSize);
            config.slots()
                .add(slot);
        }

        if (module.component() instanceof IRecipeModule recipeModule) {
            recipeModule.setRecipeConfig(config);
        }
    }

    public AssetSyncPacket withSyncRevision(int rev) {
        this.syncRevision = rev;
        return this;
    }

    // ── Test-support: package-private accessors ──

    byte syncType() {
        return syncType;
    }

    int moduleIndex() {
        return moduleIndex;
    }

    ModuleInstance.ID moduleId() {
        return moduleId;
    }

    ModuleInstance moduleData() {
        return moduleData;
    }

    StationTileCoord tileCoord() {
        return tileCoord;
    }

    StationTileState tileState() {
        return tileState;
    }

    ModuleInstance.ID tileModuleId() {
        return tileModuleId;
    }

    List<AssetSyncPacket> fullSyncDeltas() {
        return fullSyncDeltas;
    }

    int syncRevision() {
        return syncRevision;
    }

    /**
     * Package-private test helper: applies a decoded delta packet to a facility.
     * Mirrors the logic in {@link Handler#handleDelta}.
     */
    static void applyDeltaToFacility(AutomatedFacility state, AssetSyncPacket packet) {
        switch (packet.syncType) {
            case MODULE_ADDED -> {
                if (packet.moduleIndex < state.modules()
                    .size()) {
                    state.modulesInternal()
                        .set(packet.moduleIndex, packet.moduleData);
                } else {
                    state.addModule(packet.moduleData);
                }
                // Place layout tiles for the module
                StationLayout layout = state.stationLayout();
                ModuleInstance module = packet.moduleData;
                if (layout != null && module.anchorOrNull() != null) {
                    layout.place(module);
                }
            }
            case MODULE_REMOVED -> {
                state.removeModule(packet.moduleId);
                StationLayout layout = state.stationLayout();
                if (layout != null) layout.removeTileForModule(packet.moduleId);
            }
            case MODULE_UPDATED -> {
                if (packet.moduleIndex < state.modules()
                    .size()) {
                    state.modulesInternal()
                        .set(packet.moduleIndex, packet.moduleData);
                }
            }
            case INVENTORY_UPDATE -> {
                ItemStackWrapper r = ItemStackWrapper.fromKey(packet.resourceKey);
                if (r != null) {
                    if (packet.inventoryDelta > 0) {
                        state.inventory.setAmount(r, state.inventory.getAmount(r) + packet.inventoryDelta);
                    } else {
                        state.inventory
                            .setAmount(r, Math.max(0, state.inventory.getAmount(r) - Math.abs(packet.inventoryDelta)));
                    }
                }
            }
            case LOGISTICS_CONFIG_UPDATED -> {
                ItemStackWrapper r = ItemStackWrapper.fromKey(packet.resourceKey);
                if (r != null) state.logisticsConfig.set(r, packet.logConfig);
            }
            case LOGISTICS_CONFIG_REMOVED -> {
                ItemStackWrapper r = ItemStackWrapper.fromKey(packet.resourceKey);
                if (r != null) state.logisticsConfig.reset(r);
            }
            case LAYOUT_TILE_UPDATED -> {
                ModuleInstance module = Handler.findModuleById(state, packet.tileModuleId);
                StationLayout layout = state.stationLayout();
                if (layout != null) layout.place(packet.tileCoord, new PlacedTile(module, packet.tileState));
            }
            case LAYOUT_TILE_REMOVED -> {
                StationLayout layout = state.stationLayout();
                if (layout != null) layout.remove(packet.tileCoord);
            }
            case MINER_VOID_CONFIG_UPDATED -> state
                .setMinerVoidChancePercent(packet.resourceKey, packet.minerVoidChancePercent);
        }
    }

    public static final class Handler implements IMessageHandler<AssetSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(AssetSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> handleClientSync(packet));
            return null;
        }

        @SideOnly(Side.CLIENT)
        public static void handleClientSync(AssetSyncPacket packet) {
            switch (packet.syncType) {
                case ASSET_REMOVED -> CelestialAssetStore.CLIENT.destroyAssetInternal(packet.assetId);
                case FULL_SYNC -> handleFull(packet);
                default -> {
                    if (CelestialAssetStore.CLIENT
                        .findAssetInternal(packet.assetId) instanceof AutomatedFacility state) {
                        handleDelta(state, packet);
                        state.setSyncRevision(Math.max(state.getSyncRevision(), packet.syncRevision));
                    }
                }
            }
        }

        private static void handleFull(AssetSyncPacket packet) {
            CelestialAsset asset = CelestialAssetStore.CLIENT.findAssetInternal(packet.assetId);
            if (asset == null) {
                asset = CelestialAsset
                    .create(packet.assetId, packet.celestialBodyId, packet.assetKind, packet.assetStatus);
                CelestialAssetStore.CLIENT.addInternal(packet.teamId, asset);
            }
            asset.updateStatus(packet.assetStatus);
            asset.setDisplayName(packet.displayName);
            asset.setSyncRevision(packet.syncRevision);
            if (!(asset instanceof AutomatedFacility state)) return;

            state.setEnergyStored(packet.energyStored);

            state.clearModules();
            state.setMinerVoidChances(java.util.Collections.emptyMap());
            state.inventory.clear();
            state.logisticsConfig.clear();
            StationLayout layout = state.stationLayout();
            if (layout != null) layout.loadFromSnapshot(java.util.Collections.emptyMap());

            for (AssetSyncPacket d : packet.fullSyncDeltas) {
                handleDelta(state, d);
            }

            state.setSyncRevision(packet.syncRevision);
        }

        private static void handleDelta(AutomatedFacility state, AssetSyncPacket packet) {
            switch (packet.syncType) {
                case MODULE_ADDED -> {
                    if (packet.moduleIndex < state.modules()
                        .size()) {
                        state.modulesInternal()
                            .set(packet.moduleIndex, packet.moduleData);
                    } else {
                        state.addModule(packet.moduleData);
                    }
                    // Place layout tiles for the module on the client mirror
                    StationLayout layout = state.stationLayout();
                    ModuleInstance module = packet.moduleData;
                    if (layout != null && module.anchorOrNull() != null) {
                        layout.place(module);
                    }
                }
                case MODULE_REMOVED -> {
                    state.removeModule(packet.moduleId);
                    StationLayout layout = state.stationLayout();
                    if (layout != null) layout.removeTileForModule(packet.moduleId);
                }
                case MODULE_UPDATED -> {
                    if (packet.moduleIndex < state.modules()
                        .size()) {
                        state.modulesInternal()
                            .set(packet.moduleIndex, packet.moduleData);
                    }
                }
                case INVENTORY_UPDATE -> {
                    ItemStackWrapper r = ItemStackWrapper.fromKey(packet.resourceKey);
                    if (r != null) {
                        if (packet.inventoryDelta > 0) {
                            state.inventory.setAmount(r, state.inventory.getAmount(r) + packet.inventoryDelta);
                        } else {
                            state.inventory.setAmount(
                                r,
                                Math.max(0, state.inventory.getAmount(r) - Math.abs(packet.inventoryDelta)));
                        }
                    }
                }
                case LOGISTICS_CONFIG_UPDATED -> {
                    ItemStackWrapper r = ItemStackWrapper.fromKey(packet.resourceKey);
                    if (r != null) state.logisticsConfig.set(r, packet.logConfig);
                }
                case LOGISTICS_CONFIG_REMOVED -> {
                    ItemStackWrapper r = ItemStackWrapper.fromKey(packet.resourceKey);
                    if (r != null) state.logisticsConfig.reset(r);
                }
                case LAYOUT_TILE_UPDATED -> {
                    ModuleInstance module = findModuleById(state, packet.tileModuleId);
                    StationLayout layout = state.stationLayout();
                    if (layout != null) layout.place(packet.tileCoord, new PlacedTile(module, packet.tileState));
                }
                case LAYOUT_TILE_REMOVED -> {
                    StationLayout layout = state.stationLayout();
                    if (layout != null) layout.remove(packet.tileCoord);
                }
                case MINER_VOID_CONFIG_UPDATED -> state
                    .setMinerVoidChancePercent(packet.resourceKey, packet.minerVoidChancePercent);
            }
        }

        static ModuleInstance findModuleById(AutomatedFacility state, ModuleInstance.ID id) {
            if (id == null) return null;
            for (ModuleInstance m : state.modules()) {
                if (m.id.equals(id)) return m;
            }
            return null;
        }
    }
}
