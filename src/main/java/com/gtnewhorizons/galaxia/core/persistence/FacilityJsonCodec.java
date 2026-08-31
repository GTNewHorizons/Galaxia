package com.gtnewhorizons.galaxia.core.persistence;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.gtnewhorizons.galaxia.core.network.PacketUtil;
import com.gtnewhorizons.galaxia.core.persistence.CelestialObjectKeyJsonCodec.CelestialObjectKeyJson;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityModuleSettingsSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.MinerFocusOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleDeconstructionOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduleState;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.RecipeModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepAmount;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;

final class FacilityJsonCodec {

    private static final Logger LOG = LogManager.getLogger(FacilityPersistenceManager.class);
    private static final Gson PURE_GSON = new GsonBuilder().create();
    private static final Set<String> RECIPE_FIELDS = Set.of(
        "recipeMapOrdinal",
        "recipeIndex",
        "contentHash",
        "inputs",
        "outputs",
        "fluidInputs",
        "fluidOutputs",
        "outputChances",
        "fluidOutputChances",
        "duration",
        "eut",
        "enabled",
        "requestAmount",
        "priority",
        "orderSize",
        "displayName");

    private FacilityJsonCodec() {}

    static FacilityStateJson encode(AutomatedFacility state) {
        FacilityStateJson out = new FacilityStateJson();
        out.energyStored = state.getEnergyStored();
        out.stationFeatureSalt = state.stationFeatureSalt();
        out.itemBounds = encodeBounds(state.getBounds(true));
        out.fluidBounds = encodeBounds(state.getBounds(false));
        out.filters = new LinkedHashMap<>(state.filtersSnapshot());
        FacilityModuleSettingsSnapshot settingsSnapshot = state.moduleSettingsSnapshot();
        out.settingsGroups = new ArrayList<>();
        settingsSnapshot.groups()
            .values()
            .stream()
            .sorted(
                Comparator.comparingInt(
                    group -> group.id()
                        .value()))
            .forEach(group -> out.settingsGroups.add(encodeSettingsGroup(group)));
        out.privateModuleSettings = new LinkedHashMap<>();
        out.settingsMembership = new LinkedHashMap<>();
        settingsSnapshot.membership()
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(ModuleInstance.ID::toString)))
            .forEach(
                entry -> out.settingsMembership.put(
                    entry.getKey()
                        .toString(),
                    entry.getValue()
                        .value()));
        settingsSnapshot.privateSettings()
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(ModuleInstance.ID::toString)))
            .forEach(
                entry -> out.privateModuleSettings.put(
                    entry.getKey()
                        .toString(),
                    encodeSettingsGroupSettings(entry.getValue())));
        out.modules = new ArrayList<>();
        int moduleCount = 0;
        for (ModuleInstance m : state.modules()) {
            moduleCount++;
            ModuleJson mj = new ModuleJson();
            mj.moduleId = m.id.toString();
            mj.kind = m.kind()
                .name();
            mj.status = m.status()
                .name();
            mj.constructionProgress = 0f;
            mj.cooldownTicks = m.cooldownTicks();
            mj.tier = PacketUtil.enumOrdinal(m.tier());
            mj.priorityOverride = PacketUtil.enumOrdinal(m.priorityOverride());
            mj.enabled = m.enabled();
            mj.shape = PacketUtil.enumOrdinal(m.shape());
            mj.rotation = (byte) m.rotation();
            mj.parallel = m.component() instanceof IParallelModule pm ? pm.getParallel() : 1;
            mj.moduleOperation = encodeModuleOperation(m.kind(), m.operationOrNull());
            if (m.component() instanceof IRecipeModule) {
                RecipeScheduleState scheduleState = state.recipeScheduleState(m);
                if (scheduleState != null) {
                    mj.recipeSchedule = new RecipeScheduleJson();
                    mj.recipeSchedule.orderCursor = scheduleState.orderCursor();
                    mj.recipeSchedule.orderRemaining = scheduleState.orderRemaining();
                }
            }
            JsonObject moduleData = new JsonObject();
            if (m.component() instanceof ModuleHammer hammer) {
                moduleData.add("config", PURE_GSON.toJsonTree(hammer.config()));
                moduleData.add("routePriority", PURE_GSON.toJsonTree(hammer.routePriority()));
                moduleData.addProperty(
                    "variant",
                    hammer.variant()
                        .name());
                moduleData.addProperty("energyStored", hammer.energyStored());
                moduleData.addProperty("shotCooldownTicks", hammer.shotCooldownTicks());
                moduleData.addProperty("routeProbeCooldownTicks", hammer.routeProbeCooldownTicks());
            } else if (m.component() instanceof ModuleMiner miner) {
                moduleData.addProperty(
                    "focusTier",
                    miner.focusTier()
                        .name());
                String focusOreKey = miner.focusOreKeyOrNull();
                moduleData
                    .add("focusOreKey", focusOreKey == null ? JsonNull.INSTANCE : PURE_GSON.toJsonTree(focusOreKey));
                moduleData.addProperty("focusAlignmentProgress", miner.focusAlignmentProgress());
            } else if (m.component() instanceof ModuleDebugDataGenerator debugGenerator) {
                ModuleDebugDataGenerator.Config config = debugGenerator.config();
                moduleData.addProperty(
                    "mode",
                    config.mode()
                        .name());
                moduleData.addProperty("enabled", config.enabled());
                moduleData.addProperty(
                    "dataType",
                    config.dataType()
                        .name());
                moduleData.addProperty("amountKb", config.amountKb());
                moduleData.addProperty("durationTicks", config.durationTicks());
                CelestialObjectKey originBodyKey = config.originBodyKey();
                moduleData.add(
                    "originBodyKey",
                    originBodyKey == null ? JsonNull.INSTANCE
                        : PURE_GSON.toJsonTree(CelestialObjectKeyJsonCodec.encode(originBodyKey)));
                moduleData.addProperty("jobProgressTicks", debugGenerator.jobProgressTicks());
                moduleData.addProperty("consumedDeciKb", debugGenerator.consumedDeciKb());
                CelestialObjectKey detectedCounterpartBodyKey = debugGenerator.detectedCounterpartBodyKey();
                moduleData.add(
                    "detectedCounterpartBodyKey",
                    detectedCounterpartBodyKey == null ? JsonNull.INSTANCE
                        : PURE_GSON.toJsonTree(CelestialObjectKeyJsonCodec.encode(detectedCounterpartBodyKey)));
            }
            mj.data = moduleData;
            mj.consumedResources = new LinkedHashMap<>();
            for (Map.Entry<ItemStack, Long> e : m.getConstructionInventory()
                .entrySet()) {
                mj.consumedResources.put(
                    ItemStackWrapper.of(e.getKey())
                        .toKey(),
                    e.getValue());
            }
            out.modules.add(mj);
        }
        LOG.info("[PERSIST] SAVE ENCODE: facility {} has {} module(s) in state", state.assetId, moduleCount);

        out.buffer = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> e : state.itemSnapshot()
            .entrySet()) {
            out.buffer.put(
                e.getKey()
                    .toKey(),
                e.getValue());
        }
        out.fluidBuffer = new LinkedHashMap<>(state.fluidSnapshot());
        out.upkeepItemCredits = encodeItemUpkeepAmountMap(
            state.upkeepCredits()
                .itemCredits());
        out.upkeepFluidCredits = encodeFluidUpkeepAmountMap(
            state.upkeepCredits()
                .fluidCredits());
        out.layoutTiles = new ArrayList<>();
        StationLayout layout = state.stationLayout();
        int anchorCount = 0;
        if (layout != null) {
            for (Map.Entry<StationTileCoord, PlacedTile> entry : layout.snapshot()
                .entrySet()) {
                StationTileCoord coord = entry.getKey();
                // Save only anchor tiles — children are reconstructed on load
                if (!layout.isAnchorAt(coord)) continue;
                anchorCount++;
                StationTileJson tileJson = new StationTileJson();
                tileJson.dx = coord.dx();
                tileJson.dy = coord.dy();
                tileJson.state = entry.getValue()
                    .state()
                    .name();
                ModuleInstance module = entry.getValue()
                    .module();
                tileJson.moduleId = module == null ? null : module.id.toString();
                out.layoutTiles.add(tileJson);
            }
            LOG.info(
                "[PERSIST] SAVE ENCODE: facility {} layout has {} anchor tile(s) out of {} total tiles",
                state.assetId,
                anchorCount,
                layout.size());
        } else {
            LOG.info("[PERSIST] SAVE ENCODE: facility {} has no layout", state.assetId);
        }
        return out;
    }

    static AutomatedFacility decode(CelestialAsset asset, FacilityStateJson json) {
        if (asset == null || json == null || asset.systemKey == null) return null;
        if (!(asset instanceof AutomatedFacility state)) return null;
        state.setEnergyStored(json.energyStored);
        state.setStationFeatureSalt(json.stationFeatureSalt);
        decodeBounds(state, json.itemBounds, true);
        decodeBounds(state, json.fluidBounds, false);
        decodeFilters(state, json.filters);
        Map<ModuleInstance.ID, RecipeScheduleState> recipeScheduleStates = new LinkedHashMap<>();
        int moduleDecodedCount = 0;
        if (json.modules != null) {
            for (ModuleJson mj : json.modules) {
                String rawKind = mj.kind;
                FacilityModuleKind kind = safeValueOf(FacilityModuleKind.class, rawKind);
                if (kind == null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module " + mj.moduleId + " has unknown kind: '" + rawKind + "'");
                }
                ModuleInstance.ID moduleId = ModuleInstance.ID.from(mj.moduleId);
                if (moduleId == null && mj.moduleId != null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module from JSON has malformed ID: '" + mj.moduleId + "' of kind " + rawKind);
                }
                if (moduleId == null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module of kind " + rawKind + " has null/missing moduleId");
                }
                ModuleShape shape = PacketUtil.enumFromByte(mj.shape, ModuleShape.class);
                if (shape == null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module " + moduleId + " has invalid shape ordinal: " + mj.shape);
                }
                ModuleTier tier = PacketUtil.enumFromByte(mj.tier, ModuleTier.class);
                if (tier == null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module " + moduleId + " has invalid tier ordinal: " + mj.tier);
                }
                if (!kind.allowedTiers()
                    .contains(tier)) {
                    throw new IllegalStateException(
                        "[PERSIST] Module " + moduleId + " kind=" + kind + " has unsupported tier: " + tier);
                }
                ModuleInstance module = FacilityModuleRegistry.create(moduleId, kind, null, shape, tier);
                if (module == null || module.component() == null) {
                    throw new IllegalStateException(
                        "[PERSIST] Failed to create module " + kind + " (id=" + moduleId + "): component is null");
                }
                module.setRotation(mj.rotation);
                LOG.info(
                    "[PERSIST] LOAD DECODE: module {} kind={} shape={} tier={} status={} anchor=({},{})",
                    module.id,
                    kind,
                    shape,
                    tier,
                    mj.status,
                    (module.anchorOrNull() != null ? (int) module.anchorOrNull()
                        .dx() : ModuleInstance.NULL_ANCHOR_LOG_VALUE),
                    (module.anchorOrNull() != null ? (int) module.anchorOrNull()
                        .dy() : ModuleInstance.NULL_ANCHOR_LOG_VALUE));
                JsonObject data = mj.data != null ? mj.data.getAsJsonObject() : null;
                switch (kind) {
                    case HAMMER -> {
                        JsonObject hammerData = Objects.requireNonNull(data, "[PERSIST] Hammer module missing data");
                        AllowShootingConfig config = Objects.requireNonNull(
                            PURE_GSON.fromJson(hammerData.get("config"), AllowShootingConfig.class),
                            "[PERSIST] Hammer module missing config");
                        OrbitalTransferPlanner.RoutePriority routePriority = Objects.requireNonNull(
                            PURE_GSON
                                .fromJson(hammerData.get("routePriority"), OrbitalTransferPlanner.RoutePriority.class),
                            "[PERSIST] Hammer module missing routePriority");
                        HammerVariant variant = Objects.requireNonNull(
                            PURE_GSON.fromJson(hammerData.get("variant"), HammerVariant.class),
                            "[PERSIST] Hammer module missing variant");
                        long energyStored = Objects
                            .requireNonNull(
                                hammerData.get("energyStored"),
                                "[PERSIST] Hammer module missing energyStored")
                            .getAsLong();
                        ModuleHammer.requireTier(variant, tier);
                        ModuleHammer hammer = new ModuleHammer(kind, config, routePriority, variant, 64, energyStored);
                        hammer.setDispatchCooldowns(
                            optionalInt(hammerData, "shotCooldownTicks", 0),
                            optionalInt(hammerData, "routeProbeCooldownTicks", 0));
                        module.setComponent(hammer);
                    }
                    case MINER -> {
                        if (!(module.component() instanceof ModuleMiner miner)) {
                            throw new IllegalStateException(
                                "[PERSIST] Miner module " + moduleId + " has non-miner data");
                        }
                        decodeMinerSettings(module, miner, data);
                    }
                    case DEBUG_DATA_GENERATOR -> {
                        if (!(module.component() instanceof ModuleDebugDataGenerator debugGenerator)) {
                            throw new IllegalStateException(
                                "[PERSIST] Debug data generator module " + moduleId + " has invalid component");
                        }
                        JsonObject generatorData = Objects
                            .requireNonNull(data, "[PERSIST] Debug data generator module missing data");
                        ModuleDebugDataGenerator.Mode mode = Objects.requireNonNull(
                            PURE_GSON.fromJson(generatorData.get("mode"), ModuleDebugDataGenerator.Mode.class),
                            "[PERSIST] Debug data generator missing mode");
                        SatelliteDataType dataType = Objects.requireNonNull(
                            PURE_GSON.fromJson(generatorData.get("dataType"), SatelliteDataType.class),
                            "[PERSIST] Debug data generator missing dataType");
                        CelestialObjectKey originBodyKey = null;
                        JsonElement originElement = generatorData.get("originBodyKey");
                        if (originElement != null && !originElement.isJsonNull()) {
                            originBodyKey = CelestialObjectKeyJsonCodec
                                .decode(PURE_GSON.fromJson(originElement, CelestialObjectKeyJson.class));
                        }
                        CelestialObjectKey detectedCounterpartBodyKey = null;
                        JsonElement detectedElement = generatorData.get("detectedCounterpartBodyKey");
                        if (detectedElement != null && !detectedElement.isJsonNull()) {
                            detectedCounterpartBodyKey = CelestialObjectKeyJsonCodec
                                .decode(PURE_GSON.fromJson(detectedElement, CelestialObjectKeyJson.class));
                        }
                        debugGenerator.restore(
                            new ModuleDebugDataGenerator.Config(
                                mode,
                                requireBoolean(generatorData, "enabled", moduleId),
                                dataType,
                                requireLong(generatorData, "amountKb", moduleId),
                                requireInt(generatorData, "durationTicks", moduleId),
                                originBodyKey),
                            requireInt(generatorData, "jobProgressTicks", moduleId),
                            requireLong(generatorData, "consumedDeciKb", moduleId),
                            detectedCounterpartBodyKey);
                    }
                    case POWER, GEOTHERMAL_GENERATOR -> {}
                    case STORAGE, TANK, BATTERY, MAINTENANCE_BAY -> {}
                    case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> {}
                }

                Buildable.Status moduleStatus = Objects.requireNonNull(
                    safeValueOf(Buildable.Status.class, mj.status),
                    "[PERSIST] Module " + moduleId + " has invalid status: " + mj.status);
                module.updateStatus(moduleStatus);
                module.setTicks(mj.cooldownTicks);
                module.setPriorityOverride(PacketUtil.enumFromByte(mj.priorityOverride, ModulePriority.class));
                module.setEnabled(mj.enabled);
                if (module.component() instanceof IParallelModule pm) {
                    pm.setParallel(mj.parallel);
                }
                module.clearConsumedResources();
                if (mj.consumedResources != null) {
                    for (Map.Entry<String, Long> e : mj.consumedResources.entrySet()) {
                        ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                        if (key != null) {
                            module.getConstructionInventory()
                                .put(key.toStack(e.getValue()), e.getValue());
                        }
                    }
                }
                module.setOperation(decodeModuleOperation(mj.moduleOperation, module.id));
                if (mj.recipeSchedule != null) {
                    if (!(module.component() instanceof IRecipeModule)) {
                        throw new IllegalStateException(
                            "[PERSIST] Non-recipe module " + module.id + " has recipe schedule state");
                    }
                    recipeScheduleStates.put(module.id, decodeRecipeScheduleState(module.id, mj.recipeSchedule));
                }
                state.addModule(module);
                moduleDecodedCount++;
            }
        }
        LOG.info("[PERSIST] LOAD DECODE: finished decoding modules: {} decoded", moduleDecodedCount);

        state.loadFromSnapshot(decodeItemBuffer(json.buffer, state.assetId));
        try {
            state.loadFluidSnapshot(json.fluidBuffer);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "[PERSIST] Facility " + state.assetId + " has invalid fluid buffer: " + ex.getMessage(),
                ex);
        }
        state.loadUpkeepCredits(
            new UpkeepSettlement.Credits(
                decodeItemUpkeepAmountMap(json.upkeepItemCredits),
                decodeFluidUpkeepAmountMap(json.upkeepFluidCredits)));

        StationLayout layout = state.stationLayout();
        int tilesLoaded = 0;
        int tilesSkipped = 0;
        if (layout != null && json.layoutTiles != null && !json.layoutTiles.isEmpty()) {
            Map<ModuleInstance.ID, ModuleInstance> modulesById = new LinkedHashMap<>();
            for (ModuleInstance m : state.modules()) {
                modulesById.put(m.id, m);
            }
            Map<StationTileCoord, PlacedTile> layoutSnapshot = new LinkedHashMap<>();
            for (StationTileJson tj : json.layoutTiles) {
                if (tj == null) continue;
                StationTileState tileState = safeValueOf(StationTileState.class, tj.state);
                if (tileState == null) continue;
                if (tj.dx < StationTileCoord.MIN || tj.dx > StationTileCoord.MAX
                    || tj.dy < StationTileCoord.MIN
                    || tj.dy > StationTileCoord.MAX) {
                    LOG.warn(
                        "[PERSIST] LOAD LAYOUT: skipping tile out of range: ({}, {}) state={}",
                        tj.dx,
                        tj.dy,
                        tj.state);
                    tilesSkipped++;
                    continue;
                }
                StationTileCoord coord = StationTileCoord.of(tj.dx, tj.dy);
                ModuleInstance module = tj.moduleId == null ? null
                    : modulesById.get(ModuleInstance.ID.from(tj.moduleId));
                if (tj.moduleId != null && module == null) {
                    LOG.info(
                        "[PERSIST] LOAD LAYOUT: skipping orphan tile ({},{}) for missing module {}",
                        (int) tj.dx,
                        (int) tj.dy,
                        tj.moduleId);
                    tilesSkipped++;
                    continue;
                }
                if (module != null) {
                    module.initAnchor(coord);
                }
                layoutSnapshot.put(coord, new PlacedTile(module, tileState));
                tilesLoaded++;
            }
            LOG.info(
                "[PERSIST] LOAD LAYOUT: {} tiles loaded, {} skipped (orphans/out-of-range)",
                tilesLoaded,
                tilesSkipped);
            layout.loadFromSnapshot(layoutSnapshot);
            // Fallback: find anchors for modules whose initAnchor wasn't called during tile loading.
            // Modules may have null anchors if the layout tile's moduleId lookup failed
            // (e.g. UUID format mismatch between JSON and deserialized module).
            int fallbackAnchors = 0;
            for (ModuleInstance m : state.modules()) {
                if (m.anchorOrNull() != null) continue;
                for (Map.Entry<StationTileCoord, PlacedTile> entry : layout.snapshot()
                    .entrySet()) {
                    PlacedTile tile = entry.getValue();
                    if (tile.module() != null && tile.module().id.equals(m.id)) {
                        StationTileCoord coord = entry.getKey();
                        m.initAnchor(coord);
                        LOG.info(
                            "[PERSIST] LOAD LAYOUT: fallback initAnchor for {} id={} at ({},{})",
                            m.kind(),
                            m.id,
                            (int) coord.dx(),
                            (int) coord.dy());
                        fallbackAnchors++;
                        break;
                    }
                }
            }
            if (fallbackAnchors > 0) {
                LOG.warn(
                    "[PERSIST] LOAD LAYOUT: {} module(s) required fallback anchor initialization",
                    fallbackAnchors);
            }
            // Expand each module's full footprint — place() populates child tiles
            int expandedCount = 0;
            for (ModuleInstance m : state.modules()) {
                if (m.anchorOrNull() != null) {
                    layout.place(m);
                    expandedCount++;
                }
            }
            LOG.info(
                "[PERSIST] LOAD LAYOUT: expanded {} module(s) with anchor, layout now has {} tile(s)",
                expandedCount,
                layout.size());
        } else {
            LOG.info(
                "[PERSIST] LOAD LAYOUT: no layout tiles in JSON or no layout (tiles={})",
                json.layoutTiles != null ? json.layoutTiles.size() : 0);
        }

        state.restoreModuleSettings(decodeModuleSettings(state, json));
        state.restoreRecipeScheduleStates(recipeScheduleStates);

        LOG.info(
            "[PERSIST] LOAD DECODE END: facility {} has {} module(s), layout has {} tile(s)",
            state.assetId,
            state.modules()
                .size(),
            layout != null ? layout.size() : 0);
        return state;
    }

    private static Map<String, Long> encodeItemUpkeepAmountMap(Map<ItemStackWrapper, UpkeepAmount> amounts) {
        Map<String, Long> encoded = new LinkedHashMap<>();
        if (amounts == null) return encoded;
        for (Map.Entry<ItemStackWrapper, UpkeepAmount> entry : amounts.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                || entry.getValue()
                    .isZero())
                continue;
            encoded.put(
                entry.getKey()
                    .toKey(),
                entry.getValue()
                    .microUnitsPerMinute());
        }
        return encoded;
    }

    private static Map<ItemStackWrapper, UpkeepAmount> decodeItemUpkeepAmountMap(Map<String, Long> encoded) {
        Map<ItemStackWrapper, UpkeepAmount> decoded = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) return decoded;
        for (Map.Entry<String, Long> entry : encoded.entrySet()) {
            ItemStackWrapper key = ItemStackWrapper.fromKey(entry.getKey());
            if (key != null && entry.getValue() > 0L) decoded.put(key, UpkeepAmount.ofMicroUnits(entry.getValue()));
        }
        return decoded;
    }

    private static Map<String, Long> encodeFluidUpkeepAmountMap(Map<FluidKey, UpkeepAmount> amounts) {
        Map<String, Long> encoded = new LinkedHashMap<>();
        if (amounts == null) return encoded;
        for (Map.Entry<FluidKey, UpkeepAmount> entry : amounts.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                || entry.getValue()
                    .isZero())
                continue;
            encoded.put(
                entry.getKey()
                    .toKey(),
                entry.getValue()
                    .microUnitsPerMinute());
        }
        return encoded;
    }

    private static Map<FluidKey, UpkeepAmount> decodeFluidUpkeepAmountMap(Map<String, Long> encoded) {
        Map<FluidKey, UpkeepAmount> decoded = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) return decoded;
        for (Map.Entry<String, Long> entry : encoded.entrySet()) {
            FluidKey key = FluidKey.fromName(entry.getKey());
            if (key != null && entry.getValue() > 0L) decoded.put(key, UpkeepAmount.ofMicroUnits(entry.getValue()));
        }
        return decoded;
    }

    private static Map<ItemStackWrapper, Long> decodeItemBuffer(Map<String, Long> encoded, CelestialAsset.ID assetId) {
        if (encoded == null) {
            throw new IllegalStateException("[PERSIST] Facility " + assetId + " is missing item buffer");
        }
        Map<ItemStackWrapper, Long> decoded = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : encoded.entrySet()) {
            String encodedKey = entry.getKey();
            Long amount = entry.getValue();
            if (encodedKey == null || encodedKey.isBlank()) {
                throw new IllegalStateException("[PERSIST] Facility " + assetId + " has a null or blank item key");
            }
            if (amount == null || amount <= 0L) {
                throw new IllegalStateException(
                    "[PERSIST] Facility " + assetId + " has invalid item amount for " + encodedKey + ": " + amount);
            }
            ItemStackWrapper key = ItemStackWrapper.fromKey(encodedKey);
            if (key == null) {
                throw new IllegalStateException(
                    "[PERSIST] Facility " + assetId + " has unresolvable item key " + encodedKey);
            }
            if (decoded.put(key, amount) != null) {
                throw new IllegalStateException(
                    "[PERSIST] Facility " + assetId + " has duplicate semantic item key " + encodedKey);
            }
        }
        return decoded;
    }

    private static void decodeFilters(AutomatedFacility state, Map<Boolean, List<String>> encoded) {
        if (encoded == null) {
            throw new IllegalStateException("[PERSIST] Facility " + state.assetId + " is missing filters");
        }
        if (encoded.containsKey(null)) {
            throw new IllegalStateException("[PERSIST] Facility " + state.assetId + " has a null filter side");
        }
        try {
            state.setFilters(encoded.getOrDefault(true, List.of()), true);
            state.setFilters(encoded.getOrDefault(false, List.of()), false);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "[PERSIST] Facility " + state.assetId + " has invalid filters: " + ex.getMessage(),
                ex);
        }
    }

    static final class FacilityStateJson {

        long energyStored;
        long stationFeatureSalt;
        Map<String, BoundJson> itemBounds;
        Map<String, BoundJson> fluidBounds;
        Map<Boolean, List<String>> filters;
        List<SettingsGroupJson> settingsGroups;
        Map<String, JsonObject> privateModuleSettings;
        Map<String, Integer> settingsMembership;
        List<ModuleJson> modules;
        Map<String, Long> buffer;
        Map<String, Long> fluidBuffer;
        Map<String, Long> upkeepItemCredits;
        Map<String, Long> upkeepFluidCredits;
        List<StationTileJson> layoutTiles;
    }

    private static Map<String, BoundJson> encodeBounds(Map<? extends InventoryKey, InventoryBounds> bounds) {
        Map<String, BoundJson> encoded = new LinkedHashMap<>();
        for (Map.Entry<? extends InventoryKey, InventoryBounds> entry : bounds.entrySet()) {
            encoded.put(
                entry.getKey()
                    .toKey(),
                new BoundJson(
                    entry.getValue()
                        .low(),
                    entry.getValue()
                        .upper()));
        }
        return encoded;
    }

    private static void decodeBounds(AutomatedFacility state, Map<String, BoundJson> encoded, boolean items) {
        if (encoded == null) return;
        for (Map.Entry<String, BoundJson> entry : encoded.entrySet()) {
            InventoryKey key = items ? ItemStackWrapper.fromKey(entry.getKey()) : FluidKey.fromName(entry.getKey());
            BoundJson bound = entry.getValue();
            if (key != null && bound != null && bound.low != null && bound.upper != null) {
                state.setBound(key, bound.low, bound.upper);
            }
        }
    }

    static final class BoundJson {

        Long low;
        Long upper;

        BoundJson() {}

        BoundJson(long low, long upper) {
            this.low = low;
            this.upper = upper;
        }
    }

    static final class StationTileJson {

        int dx;
        int dy;
        String state;
        String moduleId;
    }

    static final class SettingsGroupJson {

        int id;
        String kind;
        String displayName;
        JsonObject data;
    }

    static final class ModuleJson {

        String moduleId;
        String kind;
        String status;
        float constructionProgress;
        int cooldownTicks;
        byte tier;
        byte priorityOverride;
        boolean enabled;
        byte shape;
        byte rotation;
        byte parallel;
        RecipeScheduleJson recipeSchedule;
        JsonElement data;
        Map<String, Long> consumedResources;
        ModuleOperationJson moduleOperation;
    }

    static final class RecipeScheduleJson {

        byte orderCursor;
        byte orderRemaining;
    }

    private static RecipeScheduleState decodeRecipeScheduleState(ModuleInstance.ID moduleId,
        RecipeScheduleJson encoded) {
        try {
            return new RecipeScheduleState(encoded.orderCursor, encoded.orderRemaining);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("[PERSIST] Module " + moduleId + " has invalid recipe schedule state", ex);
        }
    }

    static final class ModuleOperationJson {

        String specType;
        String phase;
        String targetModuleKind;
        String targetTier;
        String targetVariantKey;
        String targetFocusTierKey;
        String targetFocusOreKey;
        int buildTicks;
        int completionRefundPercent;
        boolean reserveItems;
        boolean voidCompletionRefund;
        int elapsedBuildTicks;
        Map<String, Long> completionRefundCost;
        Map<String, Long> depositedResources;
        Map<String, Long> refundBuffer;
    }

    private static void writeRecipeSnapshot(JsonObject slotObj, RecipeSnapshot snapshot) {
        slotObj.addProperty("duration", snapshot.duration());
        slotObj.addProperty("eut", snapshot.eut());
        writeItemStacks(slotObj, "inputs", snapshot.inputs());
        writeItemStacks(slotObj, "outputs", snapshot.outputs());
        writeIntArray(slotObj, "outputChances", snapshot.outputChances());
        writeFluidStacks(slotObj, "fluidInputs", snapshot.fluidInputs());
        writeFluidStacks(slotObj, "fluidOutputs", snapshot.fluidOutputs());
        writeIntArray(slotObj, "fluidOutputChances", snapshot.fluidOutputChances());
    }

    private static RecipeSnapshot readRecipeSnapshot(JsonObject slotObj, byte recipeMapOrdinal, int recipeIndex,
        long contentHash) {
        return new RecipeSnapshot(
            recipeMapOrdinal,
            recipeIndex,
            contentHash,
            readItemStacks(slotObj, "inputs"),
            readItemStacks(slotObj, "outputs"),
            readFluidStacks(slotObj, "fluidInputs"),
            readFluidStacks(slotObj, "fluidOutputs"),
            readIntArray(slotObj, "outputChances"),
            readIntArray(slotObj, "fluidOutputChances"),
            requireRecipeInt(slotObj, "duration"),
            requireRecipeInt(slotObj, "eut"));
    }

    private static void writeItemStacks(JsonObject target, String key, ItemStack[] stacks) {
        if (stacks == null) {
            target.add(key, JsonNull.INSTANCE);
            return;
        }
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (ItemStack stack : stacks) {
            ItemStackWrapper wrapper = ItemStackWrapper.of(stack);
            if (wrapper == null) {
                throw new IllegalStateException("[PERSIST] Recipe " + key + " contains an invalid item stack");
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("key", wrapper.toKey());
            obj.addProperty("amount", stack.stackSize);
            writeRecipeTag(obj, stack.getTagCompound());
            array.add(obj);
        }
        target.add(key, array);
    }

    private static ItemStack[] readItemStacks(JsonObject source, String key) {
        JsonElement encoded = source.get(key);
        if (encoded == null) throw new IllegalStateException("Missing recipe field " + key);
        if (encoded.isJsonNull()) return null;
        if (!encoded.isJsonArray()) throw new IllegalStateException("Recipe field " + key + " must be an array");
        com.google.gson.JsonArray array = encoded.getAsJsonArray();
        ItemStack[] stacks = new ItemStack[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element == null || !element.isJsonObject()) {
                throw new IllegalStateException("Recipe field " + key + " has an invalid item at index " + i);
            }
            JsonObject obj = element.getAsJsonObject();
            if (obj.entrySet()
                .size() != 3 || !obj.has("key")
                || !obj.has("amount")
                || !obj.has("tag")) {
                throw new IllegalStateException("Recipe field " + key + " has a malformed item at index " + i);
            }
            ItemStackWrapper wrapper = ItemStackWrapper.fromKey(requireRecipeString(obj, "key"));
            if (wrapper == null) {
                throw new IllegalStateException("Recipe field " + key + " has an unknown item at index " + i);
            }
            int amount = requireRecipeInt(obj, "amount");
            if (amount <= 0) {
                throw new IllegalStateException("Recipe field " + key + " has a non-positive amount at index " + i);
            }
            stacks[i] = wrapper.toStack(amount);
            stacks[i].setTagCompound(readRecipeTag(obj, "tag"));
        }
        return stacks;
    }

    private static int optionalInt(JsonObject source, String key, int fallback) {
        return source != null && source.has(key) ? source.get(key)
            .getAsInt() : fallback;
    }

    private static int requireInt(JsonObject source, String key, ModuleInstance.ID moduleId) {
        JsonElement element = Objects
            .requireNonNull(source.get(key), "[PERSIST] Module " + moduleId + " missing required int field " + key);
        return element.getAsInt();
    }

    private static long requireLong(JsonObject source, String key, ModuleInstance.ID moduleId) {
        JsonElement element = Objects
            .requireNonNull(source.get(key), "[PERSIST] Module " + moduleId + " missing required long field " + key);
        return element.getAsLong();
    }

    private static boolean requireBoolean(JsonObject source, String key, ModuleInstance.ID moduleId) {
        JsonElement element = Objects
            .requireNonNull(source.get(key), "[PERSIST] Module " + moduleId + " missing required boolean field " + key);
        return element.getAsBoolean();
    }

    private static void writeIntArray(JsonObject target, String key, int[] values) {
        if (values == null) {
            target.add(key, JsonNull.INSTANCE);
            return;
        }
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (int value : values) {
            array.add(new com.google.gson.JsonPrimitive(value));
        }
        target.add(key, array);
    }

    private static int[] readIntArray(JsonObject source, String key) {
        JsonElement encoded = source.get(key);
        if (encoded == null) throw new IllegalStateException("Missing recipe field " + key);
        if (encoded.isJsonNull()) return null;
        if (!encoded.isJsonArray()) throw new IllegalStateException("Recipe field " + key + " must be an array");
        com.google.gson.JsonArray array = encoded.getAsJsonArray();
        int[] values = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element == null || element.isJsonNull()) {
                throw new IllegalStateException("Recipe field " + key + " has a null value at index " + i);
            }
            values[i] = requireRecipeIntValue(element, key);
        }
        return values;
    }

    private static void writeFluidStacks(JsonObject target, String key, FluidStack[] stacks) {
        if (stacks == null) {
            target.add(key, JsonNull.INSTANCE);
            return;
        }
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (FluidStack stack : stacks) {
            String fluidName = fluidName(stack);
            if (fluidName == null) {
                throw new IllegalStateException("[PERSIST] Recipe " + key + " contains an invalid fluid stack");
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("fluid", fluidName);
            obj.addProperty("amount", stack.amount);
            writeRecipeTag(obj, stack.tag);
            array.add(obj);
        }
        target.add(key, array);
    }

    private static FluidStack[] readFluidStacks(JsonObject source, String key) {
        JsonElement encoded = source.get(key);
        if (encoded == null) throw new IllegalStateException("Missing recipe field " + key);
        if (encoded.isJsonNull()) return null;
        if (!encoded.isJsonArray()) throw new IllegalStateException("Recipe field " + key + " must be an array");
        com.google.gson.JsonArray array = encoded.getAsJsonArray();
        FluidStack[] stacks = new FluidStack[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element == null || !element.isJsonObject()) {
                throw new IllegalStateException("Recipe field " + key + " has an invalid fluid at index " + i);
            }
            JsonObject obj = element.getAsJsonObject();
            if (obj.entrySet()
                .size() != 3 || !obj.has("fluid")
                || !obj.has("amount")
                || !obj.has("tag")) {
                throw new IllegalStateException("Recipe field " + key + " has a malformed fluid at index " + i);
            }
            Fluid fluid = FluidRegistry.getFluid(requireRecipeString(obj, "fluid"));
            if (fluid == null) {
                throw new IllegalStateException("Recipe field " + key + " has an unknown fluid at index " + i);
            }
            int amount = requireRecipeInt(obj, "amount");
            if (amount <= 0) {
                throw new IllegalStateException("Recipe field " + key + " has a non-positive amount at index " + i);
            }
            stacks[i] = new FluidStack(fluid, amount);
            stacks[i].tag = readRecipeTag(obj, "tag");
        }
        return stacks;
    }

    private static String fluidName(FluidStack stack) {
        if (stack == null) return null;
        Fluid fluid = stack.getFluid();
        return fluid != null ? fluid.getName() : null;
    }

    private static void writeRecipeTag(JsonObject target, NBTTagCompound tag) {
        if (tag == null) {
            target.add("tag", JsonNull.INSTANCE);
        } else {
            target.addProperty("tag", tag.toString());
        }
    }

    private static NBTTagCompound readRecipeTag(JsonObject source, String key) {
        JsonElement encoded = source.get(key);
        if (encoded == null) throw new IllegalStateException("Missing recipe field " + key);
        if (encoded.isJsonNull()) return null;
        if (!encoded.isJsonPrimitive() || !encoded.getAsJsonPrimitive()
            .isString()) {
            throw new IllegalStateException("Recipe field " + key + " must be an NBT string or null");
        }
        try {
            NBTBase parsed = JsonToNBT.func_150315_a(encoded.getAsString());
            if (parsed instanceof NBTTagCompound compound) return compound;
            throw new IllegalStateException("Recipe field " + key + " must contain a compound NBT tag");
        } catch (NBTException ex) {
            throw new IllegalStateException("Recipe field " + key + " has an invalid NBT tag", ex);
        }
    }

    private static ModuleOperationJson encodeModuleOperation(FacilityModuleKind moduleKind,
        ModuleOperationState operation) {
        if (operation == null) return null;
        ModuleOperationJson json = new ModuleOperationJson();
        ModuleOperationPlan plan = operation.plan();
        json.phase = operation.phase()
            .name();
        if (plan.spec() instanceof ModuleDeconstructionOperation) {
            json.specType = "DECONSTRUCTION";
        } else if (plan.spec() instanceof HammerModuleOperation hammerSpec) {
            json.specType = "HAMMER";
            json.targetModuleKind = FacilityModuleKind.HAMMER.name();
            json.targetTier = hammerSpec.targetTier()
                .name();
            json.targetVariantKey = hammerSpec.targetVariantKey();
        } else if (plan.spec() instanceof MinerFocusOperation minerSpec) {
            json.specType = "MINER_FOCUS";
            json.targetModuleKind = FacilityModuleKind.MINER.name();
            json.targetTier = minerSpec.targetTier()
                .name();
            json.targetFocusTierKey = minerSpec.targetFocusTierKey();
            json.targetFocusOreKey = minerSpec.targetFocusOreKey();
        } else if (plan.spec() instanceof ModuleTierOperation tierSpec) {
            json.specType = "MODULE_TIER";
            json.targetModuleKind = moduleKind.name();
            json.targetTier = tierSpec.targetTier()
                .name();
        }
        json.buildTicks = plan.buildTicks();
        json.completionRefundPercent = plan.completionRefundPercent();
        json.completionRefundCost = encodeOperationCost(plan.completionRefundCost());
        json.reserveItems = plan.reserveItems();
        json.voidCompletionRefund = plan.voidCompletionRefund();
        json.elapsedBuildTicks = operation.elapsedBuildTicks();
        json.depositedResources = new LinkedHashMap<>(operation.depositedResources());
        json.refundBuffer = new LinkedHashMap<>(operation.refundBuffer());
        return json;
    }

    private static ModuleOperationState decodeModuleOperation(ModuleOperationJson json, ModuleInstance.ID moduleId) {
        if (json == null) return null;
        ModuleOperationPhase phase = requireEnum(
            ModuleOperationPhase.class,
            json.phase,
            "[PERSIST] Module " + moduleId + " has invalid operation phase: " + json.phase);
        FacilityModuleKind regKind = json.targetModuleKind != null
            ? requireOptionalEnum(
                FacilityModuleKind.class,
                json.targetModuleKind,
                "[PERSIST] Module " + moduleId + " has invalid target kind: " + json.targetModuleKind)
            : null;
        boolean deconstruction = "DECONSTRUCTION".equals(json.specType);
        ModuleTier targetTier = deconstruction ? null
            : requireEnum(
                ModuleTier.class,
                json.targetTier,
                "[PERSIST] Module " + moduleId + " has invalid target tier: " + json.targetTier);
        if ((!deconstruction && json.buildTicks <= 0) || (deconstruction && json.buildTicks != 0)) {
            throw new IllegalStateException(
                "[PERSIST] Module " + moduleId + " operation has invalid buildTicks: " + json.buildTicks);
        }
        IModuleOperation spec;
        if (deconstruction) {
            spec = new ModuleDeconstructionOperation();
        } else if ("HAMMER".equals(json.specType)) {
            spec = new HammerModuleOperation(targetTier, json.targetVariantKey);
        } else if ("MINER_FOCUS".equals(json.specType)) {
            spec = new MinerFocusOperation(targetTier, json.targetFocusTierKey, json.targetFocusOreKey);
        } else if ("MODULE_TIER".equals(json.specType)) {
            if (regKind == null) {
                throw new IllegalStateException(
                    "[PERSIST] Module " + moduleId + " tier operation is missing target kind");
            }
            spec = new ModuleTierOperation(targetTier);
        } else {
            throw new IllegalStateException(
                "[PERSIST] Module " + moduleId + " has unknown spec type: " + json.specType);
        }
        Map<ItemStackWrapper, Long> cost = !deconstruction && regKind != null ? FacilityModuleRegistry.operationCost(
            FacilityModuleRegistry.get(regKind)
                .getTierData(targetTier)
                .constructionCost())
            : Map.of();
        ModuleOperationPlan plan = new ModuleOperationPlan(
            spec,
            json.buildTicks,
            cost,
            requireOperationCost(json.completionRefundCost, "completionRefundCost", moduleId),
            json.completionRefundPercent,
            json.reserveItems,
            json.voidCompletionRefund);
        return ModuleOperationState.restore(
            plan,
            phase,
            json.elapsedBuildTicks,
            requireOperationAmounts(json.depositedResources, "depositedResources", moduleId),
            requireOperationAmounts(json.refundBuffer, "refundBuffer", moduleId));
    }

    private static Map<String, Long> encodeOperationCost(Map<ItemStackWrapper, Long> cost) {
        Map<String, Long> encoded = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : cost.entrySet()) {
            encoded.merge(
                entry.getKey()
                    .toKey(),
                entry.getValue(),
                Long::sum);
        }
        return encoded;
    }

    private static Map<ItemStackWrapper, Long> requireOperationCost(Map<String, Long> amounts, String fieldName,
        ModuleInstance.ID moduleId) {
        if (amounts == null) {
            throw new IllegalStateException("[PERSIST] Module " + moduleId + " operation missing " + fieldName);
        }
        Map<ItemStackWrapper, Long> cost = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : amounts.entrySet()) {
            ItemStackWrapper item = ItemStackWrapper.fromKey(entry.getKey());
            if (item == null) {
                throw new IllegalStateException(
                    "[PERSIST] Module " + moduleId
                        + " operation "
                        + fieldName
                        + " contains unresolvable item: "
                        + entry.getKey());
            }
            cost.merge(item, entry.getValue(), Long::sum);
        }
        return cost;
    }

    private static Map<String, Long> requireOperationAmounts(Map<String, Long> amounts, String fieldName,
        ModuleInstance.ID moduleId) {
        if (amounts == null) {
            throw new IllegalStateException("[PERSIST] Module " + moduleId + " operation missing " + fieldName);
        }
        return amounts;
    }

    private static <T extends Enum<T>> T requireEnum(Class<T> cls, String name, String message) {
        T value = safeValueOf(cls, name);
        if (value == null) throw new IllegalStateException(message);
        return value;
    }

    private static <T extends Enum<T>> T requireOptionalEnum(Class<T> cls, String name, String message) {
        if (name == null) return null;
        T value = safeValueOf(cls, name);
        if (value == null) throw new IllegalStateException(message);
        return value;
    }

    private static SettingsGroupJson encodeSettingsGroup(SettingsGroup group) {
        SettingsGroupJson json = new SettingsGroupJson();
        json.id = group.id()
            .value();
        json.kind = group.kind()
            .name();
        json.displayName = group.displayName();
        json.data = encodeSettingsGroupSettings(group.settings());
        return json;
    }

    private static FacilityModuleSettingsSnapshot decodeModuleSettings(AutomatedFacility state,
        FacilityStateJson json) {
        List<SettingsGroupJson> encodedGroups = Objects
            .requireNonNull(json.settingsGroups, "[PERSIST] Facility missing settingsGroups");
        Map<String, JsonObject> encodedPrivate = Objects
            .requireNonNull(json.privateModuleSettings, "[PERSIST] Facility missing privateModuleSettings");
        Map<String, Integer> encodedMembership = Objects
            .requireNonNull(json.settingsMembership, "[PERSIST] Facility missing settingsMembership");
        return new FacilityModuleSettingsSnapshot(
            decodePrivateModuleSettings(state, encodedPrivate),
            decodeSettingsGroups(encodedGroups),
            decodeSettingsMembership(encodedMembership));
    }

    private static Map<SettingsGroup.ID, SettingsGroup> decodeSettingsGroups(List<SettingsGroupJson> encodedGroups) {
        Map<SettingsGroup.ID, SettingsGroup> groups = new LinkedHashMap<>();
        for (SettingsGroupJson groupJson : encodedGroups) {
            SettingsGroup.ID id = new SettingsGroup.ID(groupJson.id);
            FacilityModuleKind kind = Objects.requireNonNull(
                safeValueOf(FacilityModuleKind.class, groupJson.kind),
                "[PERSIST] Settings group " + id + " has invalid kind: " + groupJson.kind);
            SettingsGroup group = new SettingsGroup(
                id,
                kind,
                groupJson.displayName,
                decodeSettings(kind, groupJson.data, "settings group " + id));
            if (groups.put(id, group) != null) {
                throw new IllegalStateException("[PERSIST] Duplicate settings group " + id);
            }
        }
        return groups;
    }

    private static Map<ModuleInstance.ID, ModuleSettings> decodePrivateModuleSettings(AutomatedFacility state,
        Map<String, JsonObject> encodedPrivate) {
        Map<ModuleInstance.ID, ModuleSettings> privateSettings = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : encodedPrivate.entrySet()) {
            ModuleInstance.ID moduleId = ModuleInstance.ID.from(entry.getKey());
            if (moduleId == null) throw new IllegalStateException("[PERSIST] Invalid private settings module ID");
            int moduleIndex = state.moduleIndex(moduleId);
            if (moduleIndex < 0) {
                throw new IllegalStateException("[PERSIST] Private settings reference missing module " + moduleId);
            }
            FacilityModuleKind kind = state.modules()
                .get(moduleIndex)
                .kind();
            if (privateSettings
                .put(moduleId, decodeSettings(kind, entry.getValue(), "private settings for " + moduleId)) != null) {
                throw new IllegalStateException("[PERSIST] Duplicate private settings for " + moduleId);
            }
        }
        return privateSettings;
    }

    private static Map<ModuleInstance.ID, SettingsGroup.ID> decodeSettingsMembership(
        Map<String, Integer> encodedMembership) {
        Map<ModuleInstance.ID, SettingsGroup.ID> membership = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : encodedMembership.entrySet()) {
            ModuleInstance.ID moduleId = ModuleInstance.ID.from(entry.getKey());
            if (moduleId == null || entry.getValue() == null) {
                throw new IllegalStateException("[PERSIST] Invalid module settings membership: " + entry);
            }
            if (membership.put(moduleId, new SettingsGroup.ID(entry.getValue())) != null) {
                throw new IllegalStateException("[PERSIST] Duplicate module settings membership for " + moduleId);
            }
        }
        return membership;
    }

    private static JsonObject encodeSettingsGroupSettings(ModuleSettings settings) {
        JsonObject data = new JsonObject();
        if (settings instanceof MinerSettings minerSettings) {
            data.add("minerSettings", PURE_GSON.toJsonTree(minerSettings));
            return data;
        }
        if (settings instanceof RecipeModuleSettings recipeSettings) {
            JsonObject recipeData = new JsonObject();
            recipeData.add("recipeBook", encodeRecipeBook(recipeSettings.book()));
            data.add("recipeSettings", recipeData);
            return data;
        }
        throw new IllegalStateException("[PERSIST] Unsupported settings group payload " + settings);
    }

    private static ModuleSettings decodeSettings(FacilityModuleKind kind, JsonObject encoded, String context) {
        JsonObject data = Objects.requireNonNull(encoded, "[PERSIST] " + context + " missing data");
        if (kind == FacilityModuleKind.MINER) {
            if (data.entrySet()
                .size() != 1 || !data.has("minerSettings")) {
                throw new IllegalStateException("[PERSIST] " + context + " has malformed miner data");
            }
            JsonObject settingsData = data.getAsJsonObject("minerSettings");
            JsonElement keysElement = Objects.requireNonNull(
                settingsData.get("blacklistedOreKeys"),
                "[PERSIST] " + context + " missing blacklistedOreKeys");
            Type keySetType = new TypeToken<Set<String>>() {}.getType();
            Set<String> keys = Objects.requireNonNull(
                PURE_GSON.fromJson(keysElement, keySetType),
                "[PERSIST] " + context + " has null blacklistedOreKeys");
            return new MinerSettings(keys);
        }
        if (FacilityModuleRegistry.get(kind)
            .settingsGroups()) {
            if (data.entrySet()
                .size() != 1 || !data.has("recipeSettings")) {
                throw new IllegalStateException("[PERSIST] " + context + " has malformed recipe data");
            }
            JsonObject recipeData = data.getAsJsonObject("recipeSettings");
            if (recipeData.entrySet()
                .size() != 1 || !recipeData.has("recipeBook")) {
                throw new IllegalStateException("[PERSIST] " + context + " has malformed recipe book data");
            }
            try {
                return new RecipeModuleSettings(decodeRecipeBook(recipeData.get("recipeBook"), context));
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new IllegalStateException("[PERSIST] " + context + " has invalid recipe book", ex);
            }
        }
        throw new IllegalStateException("[PERSIST] Unsupported settings group kind " + kind);
    }

    private static JsonObject encodeRecipeBook(RecipeBook book) {
        JsonObject data = new JsonObject();
        data.addProperty(
            "mode",
            book.mode()
                .name());
        data.addProperty(
            "notDoablePolicy",
            book.notDoablePolicy()
                .name());
        com.google.gson.JsonArray recipes = new com.google.gson.JsonArray();
        for (SavedRecipe recipe : book.recipes()) {
            JsonObject recipeData = new JsonObject();
            recipeData.addProperty(
                "recipeMapOrdinal",
                recipe.recipe()
                    .recipeMapOrdinal() & 0xFF);
            recipeData.addProperty(
                "recipeIndex",
                recipe.recipe()
                    .recipeIndex());
            recipeData.addProperty(
                "contentHash",
                recipe.recipe()
                    .contentHash());
            writeRecipeSnapshot(recipeData, recipe.recipe());
            recipeData.addProperty("enabled", recipe.enabled());
            recipeData.addProperty("requestAmount", recipe.requestAmount());
            recipeData.addProperty("priority", recipe.priority() & 0xFF);
            recipeData.addProperty("orderSize", recipe.orderSize() & 0xFF);
            recipeData.addProperty("displayName", recipe.displayName());
            recipes.add(recipeData);
        }
        data.add("recipes", recipes);
        return data;
    }

    private static void decodeMinerSettings(ModuleInstance module, ModuleMiner miner, JsonObject data) {
        JsonObject minerData = Objects.requireNonNull(data, "[PERSIST] Miner module " + module.id + " missing data");
        if (minerData.entrySet()
            .size() != 3 || !minerData.has("focusTier")
            || !minerData.has("focusOreKey")
            || !minerData.has("focusAlignmentProgress")) {
            throw new IllegalStateException("[PERSIST] Miner module " + module.id + " has malformed settings data");
        }
        decodeMinerFocus(module, miner, minerData);
    }

    private static void decodeMinerFocus(ModuleInstance module, ModuleMiner miner, JsonObject minerData) {
        MinerFocusTier focusTier = requireEnum(
            MinerFocusTier.class,
            minerData.get("focusTier")
                .getAsString(),
            "[PERSIST] Miner module " + module.id + " has invalid focus tier");
        JsonElement focusOreElement = minerData.get("focusOreKey");
        String focusOreKey = focusOreElement == null || focusOreElement.isJsonNull() ? null
            : focusOreElement.getAsString();
        int focusAlignmentProgress = minerData.get("focusAlignmentProgress")
            .getAsInt();
        miner.setFocus(focusTier, focusOreKey, focusAlignmentProgress);
    }

    private static RecipeBook decodeRecipeBook(JsonElement encoded, String context) {
        if (encoded == null || encoded.isJsonNull() || !encoded.isJsonObject()) {
            throw new IllegalStateException("[PERSIST] " + context + " missing recipe book");
        }
        JsonObject data = encoded.getAsJsonObject();
        if (data.entrySet()
            .size() != 3 || !data.has("mode")
            || !data.has("notDoablePolicy")
            || !data.has("recipes")
            || !data.get("recipes")
                .isJsonArray()) {
            throw new IllegalStateException("[PERSIST] " + context + " has malformed recipe book");
        }
        RecipeSchedulerMode mode = requireEnum(
            RecipeSchedulerMode.class,
            data.get("mode")
                .getAsString(),
            "[PERSIST] " + context + " has invalid recipe mode");
        NotDoablePolicy policy = requireEnum(
            NotDoablePolicy.class,
            data.get("notDoablePolicy")
                .getAsString(),
            "[PERSIST] " + context + " has invalid not-doable policy");
        com.google.gson.JsonArray recipesData = data.getAsJsonArray("recipes");
        if (recipesData.size() > RecipeBook.MAX_RECIPES) {
            throw new IllegalStateException("[PERSIST] " + context + " exceeds the recipe book size limit");
        }
        List<SavedRecipe> recipes = new ArrayList<>(recipesData.size());
        for (int i = 0; i < recipesData.size(); i++) {
            JsonElement recipeElement = recipesData.get(i);
            if (recipeElement == null || recipeElement.isJsonNull() || !recipeElement.isJsonObject()) {
                throw new IllegalStateException("[PERSIST] " + context + " has malformed recipe at index " + i);
            }
            JsonObject recipeData = recipeElement.getAsJsonObject();
            try {
                if (recipeData.entrySet()
                    .size() != RECIPE_FIELDS.size()
                    || !recipeData.entrySet()
                        .stream()
                        .allMatch(entry -> RECIPE_FIELDS.contains(entry.getKey()))) {
                    throw new IllegalArgumentException("recipe fields do not match the persistence contract");
                }
                int mapOrdinal = requireRecipeInt(recipeData, "recipeMapOrdinal");
                int priority = requireRecipeInt(recipeData, "priority");
                int orderSize = requireRecipeInt(recipeData, "orderSize");
                if (mapOrdinal < 1 || mapOrdinal > 255) {
                    throw new IllegalArgumentException("recipeMapOrdinal must be between 1 and 255");
                }
                if (priority < 0 || priority > Byte.MAX_VALUE) {
                    throw new IllegalArgumentException("priority must be between 0 and " + Byte.MAX_VALUE);
                }
                if (orderSize < 1 || orderSize > Byte.MAX_VALUE) {
                    throw new IllegalArgumentException("orderSize must be between 1 and " + Byte.MAX_VALUE);
                }
                RecipeSnapshot snapshot = readRecipeSnapshot(
                    recipeData,
                    (byte) mapOrdinal,
                    requireRecipeInt(recipeData, "recipeIndex"),
                    requireRecipeLong(recipeData, "contentHash"));
                recipes.add(
                    new SavedRecipe(
                        snapshot,
                        requireRecipeBoolean(recipeData, "enabled"),
                        requireRecipeLong(recipeData, "requestAmount"),
                        (byte) priority,
                        (byte) orderSize,
                        requireRecipeString(recipeData, "displayName")));
            } catch (RuntimeException ex) {
                throw new IllegalStateException("[PERSIST] " + context + " has invalid recipe at index " + i, ex);
            }
        }
        try {
            return new RecipeBook(recipes, mode, policy);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("[PERSIST] " + context + " has invalid recipe book", ex);
        }
    }

    private static int requireRecipeInt(JsonObject data, String key) {
        return requireRecipeIntValue(requireRecipeField(data, key), key);
    }

    private static int requireRecipeIntValue(JsonElement value, String key) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
            .isNumber()) {
            throw new IllegalStateException("Recipe field " + key + " must be an integer");
        }
        try {
            return Integer.parseInt(value.getAsString());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Recipe field " + key + " must be an integer", ex);
        }
    }

    private static long requireRecipeLong(JsonObject data, String key) {
        JsonElement value = requireRecipeField(data, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
            .isNumber()) {
            throw new IllegalStateException("Recipe field " + key + " must be an integer");
        }
        try {
            return Long.parseLong(value.getAsString());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Recipe field " + key + " must be an integer", ex);
        }
    }

    private static boolean requireRecipeBoolean(JsonObject data, String key) {
        JsonElement value = requireRecipeField(data, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
            .isBoolean()) {
            throw new IllegalStateException("Recipe field " + key + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static String requireRecipeString(JsonObject data, String key) {
        JsonElement value = requireRecipeField(data, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
            .isString()) {
            throw new IllegalStateException("Recipe field " + key + " must be a string");
        }
        return value.getAsString();
    }

    private static JsonElement requireRecipeField(JsonObject data, String key) {
        JsonElement value = data.get(key);
        if (value == null || value.isJsonNull()) {
            throw new IllegalStateException("Missing recipe field " + key);
        }
        return value;
    }

    private static <T extends Enum<T>> T safeValueOf(Class<T> cls, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(cls, name);
        } catch (IllegalArgumentException e) {
            LOG.warn("[Logistics] Unknown enum value {} for {}", name, cls.getSimpleName());
            return null;
        }
    }
}
