package com.gtnewhorizons.galaxia.core.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityModuleSettingsSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
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
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

public final class AssetState {

    private AssetState() {}

    public static NBTTagCompound encode(UUID teamId, CelestialAsset asset) {
        if (teamId == null) throw new IllegalStateException("[PERSIST] Asset " + asset.assetId + " has no team");
        NBTTagCompound out = new NBTTagCompound();
        out.setString("team", teamId.toString());
        out.setString("id", asset.assetId.toString());
        out.setTag("body", writeBodyKey(asset.celestialObjectKey));
        out.setString("name", asset.displayName());
        out.setString("kind", asset.kind.name());
        out.setString(
            "status",
            asset.status()
                .name());
        if (asset instanceof Satellite satellite) out.setString(
            "satelliteKind",
            satellite.satelliteKind()
                .name());
        out.setTag("construction", writeConstructionInventory(asset.constructionInventory()));
        if (asset instanceof Station station && station.getController() != null) {
            NBTTagCompound controller = new NBTTagCompound();
            controller.setInteger(
                "x",
                station.getController()
                    .x());
            controller.setInteger(
                "y",
                station.getController()
                    .y());
            controller.setInteger(
                "z",
                station.getController()
                    .z());
            out.setTag("controller", controller);
        }
        out.setTag("logistics", writeLogistics(asset.logisticsConfig.snapshot()));
        if (asset instanceof AutomatedFacility facility) out.setTag("facility", encodeFacility(facility));
        return out;
    }

    public static Decoded decode(NBTTagCompound tag) {
        String path = tag != null && tag.hasKey("id", NBT.TAG_STRING) ? "asset[" + tag.getString("id") + "]" : "asset";
        UUID teamId;
        CelestialAsset.ID assetId;
        try {
            teamId = UUID.fromString(requireString(tag, "team", path));
            assetId = CelestialAsset.ID.from(requireString(tag, "id", path));
        } catch (RuntimeException ex) {
            throw fail(path, "invalid team or asset ID", ex);
        }
        CelestialAsset.Kind kind = requireEnum(CelestialAsset.Kind.class, tag, "kind", path);
        Buildable.Status status = requireEnum(Buildable.Status.class, tag, "status", path);
        SatelliteKind satelliteKind = kind == CelestialAsset.Kind.SATELLITE
            ? requireEnum(SatelliteKind.class, tag, "satelliteKind", path)
            : null;
        if (kind != CelestialAsset.Kind.SATELLITE && tag.hasKey("satelliteKind")) {
            throw fail(path + ".satelliteKind", "present for non-satellite asset");
        }
        CelestialAsset asset;
        try {
            asset = CelestialAsset.create(
                assetId,
                readBodyKey(requireCompound(tag, "body", path), path + ".body"),
                kind,
                status,
                satelliteKind);
            asset.setDisplayName(requireString(tag, "name", path));
            asset.setConstructionInventory(readConstructionInventory(tag, "construction", path));
            asset.logisticsConfig.loadFromSnapshot(readLogistics(tag, "logistics", path));
        } catch (RuntimeException ex) {
            throw fail(path, "invalid asset state", ex);
        }
        if (tag.hasKey("controller")) {
            if (!(asset instanceof Station station)) throw fail(path + ".controller", "present for non-station asset");
            NBTTagCompound controller = requireCompound(tag, "controller", path);
            station.setController(
                new BlockPos(
                    requireInt(controller, "x", path + ".controller"),
                    requireInt(controller, "y", path + ".controller"),
                    requireInt(controller, "z", path + ".controller")));
        }
        if (asset instanceof AutomatedFacility) {
            decodeFacility(asset, requireCompound(tag, "facility", path));
        } else if (tag.hasKey("facility")) {
            throw fail(path + ".facility", "present for non-facility asset");
        }
        return new Decoded(teamId, asset);
    }

    public static void replace(UUID currentTeamId, CelestialAsset current, Decoded replacement) {
        if (current == null || replacement == null || replacement.asset() == null) {
            throw new IllegalArgumentException("Missing asset replacement state");
        }
        CelestialAsset source = replacement.asset();
        if (!Objects.equals(currentTeamId, replacement.teamId())) {
            throw new IllegalArgumentException("Asset team changed during replacement");
        }
        if (!current.assetId.equals(source.assetId) || current.kind != source.kind
            || !current.celestialObjectKey.equals(source.celestialObjectKey)) {
            throw new IllegalArgumentException("Immutable asset identity changed during replacement");
        }
        if (current instanceof Satellite currentSatellite && (!(source instanceof Satellite sourceSatellite)
            || currentSatellite.satelliteKind() != sourceSatellite.satelliteKind())) {
            throw new IllegalArgumentException("Satellite kind changed during replacement");
        }

        current.setDisplayName(source.displayName());
        current.updateStatus(source.status());
        current.setConstructionInventory(source.constructionInventory());
        current.logisticsConfig.loadFromSnapshot(source.logisticsConfig.snapshot());
        if (current instanceof Station station && source instanceof Station replacementStation) {
            station.setController(replacementStation.getController());
        } else if (current instanceof AutomatedFacility facility
            && source instanceof AutomatedFacility replacementFacility) {
                replaceFacility(facility, replacementFacility);
            }
    }

    private static void replaceFacility(AutomatedFacility current, AutomatedFacility replacement) {
        current.clearModules();
        current.clear();
        current.setEnergyStored(replacement.getEnergyStored());
        current.setStationFeatureSalt(replacement.stationFeatureSalt());
        replacement.getBounds(true)
            .forEach((key, bounds) -> current.setBound(key, bounds.low(), bounds.upper()));
        replacement.getBounds(false)
            .forEach((key, bounds) -> current.setBound(key, bounds.low(), bounds.upper()));
        replacement.filtersSnapshot()
            .forEach((items, filters) -> current.setFilters(filters, items));
        for (ModuleInstance module : replacement.modules()) current.addModule(module);
        current.restoreInventory(replacement.itemSnapshot(), replacement.fluidAmounts());
        current.loadUpkeepCredits(replacement.upkeepCredits());
        if (current.stationLayout() != null && replacement.stationLayout() != null) {
            current.stationLayout()
                .loadFromSnapshot(
                    replacement.stationLayout()
                        .snapshot());
        }
        current.restoreModuleSettings(replacement.moduleSettingsSnapshot());
        current.restoreRecipeScheduleStates(replacement.recipeScheduleStates());
    }

    private static NBTTagCompound encodeFacility(AutomatedFacility facility) {
        NBTTagCompound out = new NBTTagCompound();
        out.setLong("energy", facility.getEnergyStored());
        out.setLong("featureSalt", facility.stationFeatureSalt());
        out.setTag("itemBounds", writeBounds(facility.getBounds(true)));
        out.setTag("fluidBounds", writeBounds(facility.getBounds(false)));
        Map<Boolean, List<String>> filters = facility.filtersSnapshot();
        out.setTag("itemFilters", writeStrings(filters.getOrDefault(true, List.of())));
        out.setTag("fluidFilters", writeStrings(filters.getOrDefault(false, List.of())));
        writeSettings(out, facility.moduleSettingsSnapshot());

        NBTTagList modules = new NBTTagList();
        for (ModuleInstance module : facility.modules()) modules.appendTag(writeModule(facility, module));
        out.setTag("modules", modules);
        out.setTag("items", writeResources(facility.itemSnapshot()));
        out.setTag("fluids", writeResources(facility.fluidAmounts()));
        out.setTag(
            "upkeepItems",
            writeCredits(
                facility.upkeepCredits()
                    .itemCredits()));
        out.setTag(
            "upkeepFluids",
            writeCredits(
                facility.upkeepCredits()
                    .fluidCredits()));
        out.setTag("anchors", writeAnchors(facility));
        return out;
    }

    private static AutomatedFacility decodeFacility(CelestialAsset asset, NBTTagCompound encoded) {
        String path = "asset[" + asset.assetId + "].facility";
        if (!(asset instanceof AutomatedFacility facility)) throw fail(path, "asset is not an automated facility");
        facility.setEnergyStored(requireLong(encoded, "energy", path));
        facility.setStationFeatureSalt(requireLong(encoded, "featureSalt", path));
        readBounds(facility, requireList(encoded, "itemBounds", NBT.TAG_COMPOUND, path), true, path + ".itemBounds");
        readBounds(facility, requireList(encoded, "fluidBounds", NBT.TAG_COMPOUND, path), false, path + ".fluidBounds");
        facility.setFilters(readStrings(encoded, "itemFilters", path), true);
        facility.setFilters(readStrings(encoded, "fluidFilters", path), false);

        Map<ModuleInstance.ID, RecipeScheduleState> schedules = new LinkedHashMap<>();
        Map<ModuleInstance.ID, ModuleInstance> modules = new LinkedHashMap<>();
        NBTTagList moduleTags = requireList(encoded, "modules", NBT.TAG_COMPOUND, path);
        for (int i = 0; i < moduleTags.tagCount(); i++) {
            String modulePath = path + ".modules[" + i + "]";
            ModuleInstance module = readModule(facility, moduleTags.getCompoundTagAt(i), schedules, modulePath);
            if (modules.put(module.id, module) != null)
                throw fail(modulePath + ".id", "duplicate module ID " + module.id);
            facility.addModule(module);
        }

        facility.restoreInventory(readItems(encoded, "items", path), readFluids(encoded, "fluids", path));
        facility.loadUpkeepCredits(
            new UpkeepSettlement.Credits(
                readItemCredits(encoded, "upkeepItems", path),
                readFluidCredits(encoded, "upkeepFluids", path)));
        restoreAnchors(facility, modules, requireList(encoded, "anchors", NBT.TAG_COMPOUND, path), path + ".anchors");
        facility.restoreModuleSettings(readSettings(encoded, modules, path));
        facility.restoreRecipeScheduleStates(schedules);
        return facility;
    }

    private static NBTTagCompound writeModule(AutomatedFacility facility, ModuleInstance module) {
        NBTTagCompound out = new NBTTagCompound();
        out.setString("id", module.id.toString());
        out.setString(
            "kind",
            module.kind()
                .name());
        out.setString(
            "status",
            module.status()
                .name());
        out.setInteger("ticks", module.ticks());
        out.setString(
            "tier",
            module.tier()
                .name());
        out.setString(
            "priority",
            module.priorityOverride() == null ? ""
                : module.priorityOverride()
                    .name());
        out.setBoolean("enabled", module.enabled());
        out.setString(
            "shape",
            module.shape()
                .name());
        out.setInteger("rotation", module.rotation());
        out.setInteger("parallel", module.component() instanceof IParallelModule parallel ? parallel.getParallel() : 1);
        out.setTag("data", writeModuleData(module));
        out.setTag("construction", writeConstructionInventory(module.getConstructionInventory()));
        ModuleOperationState operation = module.operationOrNull();
        if (operation != null) out.setTag("operation", writeOperation(operation));
        RecipeScheduleState schedule = module.component() instanceof IRecipeModule
            ? facility.recipeScheduleState(module)
            : null;
        if (schedule != null) {
            NBTTagCompound scheduleTag = new NBTTagCompound();
            scheduleTag.setInteger("cursor", schedule.orderCursor() & 0xFF);
            scheduleTag.setInteger("remaining", schedule.orderRemaining() & 0xFF);
            out.setTag("schedule", scheduleTag);
        }
        return out;
    }

    private static ModuleInstance readModule(AutomatedFacility facility, NBTTagCompound tag,
        Map<ModuleInstance.ID, RecipeScheduleState> schedules, String path) {
        ModuleInstance.ID id;
        try {
            id = ModuleInstance.ID.from(requireString(tag, "id", path));
        } catch (RuntimeException ex) {
            throw fail(path + ".id", "invalid module ID", ex);
        }
        FacilityModuleKind kind = requireEnum(FacilityModuleKind.class, tag, "kind", path);
        ModuleShape shape = requireEnum(ModuleShape.class, tag, "shape", path);
        ModuleTier tier = requireEnum(ModuleTier.class, tag, "tier", path);
        if (!kind.allowedTiers()
            .contains(tier)) throw fail(path + ".tier", tier + " is not supported by " + kind);
        ModuleInstance module = FacilityModuleRegistry.create(id, kind, null, shape, tier);
        if (module == null || module.component() == null) throw fail(path, "could not construct module " + kind);

        int rotation = requireInt(tag, "rotation", path);
        if (rotation < 0 || rotation > 3) throw fail(path + ".rotation", "must be between 0 and 3");
        module.setRotation(rotation);
        readModuleData(module, requireCompound(tag, "data", path), path + ".data");
        module.updateStatus(requireEnum(Buildable.Status.class, tag, "status", path));
        int ticks = requireNonNegativeInt(tag, "ticks", path);
        module.setTicks(ticks);
        String priority = requireString(tag, "priority", path);
        module.setPriorityOverride(
            priority.isEmpty() ? null : requireEnum(ModulePriority.class, priority, path + ".priority"));
        module.setEnabled(requireBoolean(tag, "enabled", path));
        int parallel = requireInt(tag, "parallel", path);
        if (parallel < 1) throw fail(path + ".parallel", "must be positive");
        if (module.component() instanceof IParallelModule parallelModule) parallelModule.setParallel((byte) parallel);
        module.clearConsumedResources();
        module.getConstructionInventory()
            .putAll(readConstructionInventory(tag, "construction", path));

        if (tag.hasKey("operation")) {
            module.setOperation(readOperation(kind, id, requireCompound(tag, "operation", path), path + ".operation"));
        }
        if (tag.hasKey("schedule")) {
            if (!(module.component() instanceof IRecipeModule)) {
                throw fail(path + ".schedule", "non-recipe module has schedule state");
            }
            NBTTagCompound schedule = requireCompound(tag, "schedule", path);
            try {
                schedules.put(
                    id,
                    new RecipeScheduleState(
                        checkedByte(requireInt(schedule, "cursor", path + ".schedule"), path + ".schedule.cursor"),
                        checkedByte(
                            requireInt(schedule, "remaining", path + ".schedule"),
                            path + ".schedule.remaining")));
            } catch (IllegalArgumentException ex) {
                throw fail(path + ".schedule", "invalid schedule state", ex);
            }
        }
        return module;
    }

    private static NBTTagCompound writeModuleData(ModuleInstance module) {
        NBTTagCompound out = new NBTTagCompound();
        if (module.component() instanceof ModuleHammer hammer) {
            out.setString(
                "allowMode",
                hammer.config()
                    .mode()
                    .name());
            out.setDouble(
                "allowThreshold",
                hammer.config()
                    .threshold());
            out.setString(
                "routePriority",
                hammer.routePriority()
                    .name());
            out.setString(
                "variant",
                hammer.variant()
                    .name());
            out.setLong("energy", hammer.energyStored());
            out.setInteger("shotCooldown", hammer.shotCooldownTicks());
            out.setInteger("probeCooldown", hammer.routeProbeCooldownTicks());
        } else if (module.component() instanceof ModuleMiner miner) {
            out.setString(
                "focusTier",
                miner.focusTier()
                    .name());
            if (miner.focusOreKeyOrNull() != null) out.setString("focusOre", miner.focusOreKeyOrNull());
            out.setInteger("focusProgress", miner.focusAlignmentProgress());
        } else if (module.component() instanceof ModuleDebugDataGenerator generator) {
            ModuleDebugDataGenerator.Config config = generator.config();
            out.setString(
                "mode",
                config.mode()
                    .name());
            out.setBoolean("enabled", config.enabled());
            out.setString(
                "dataType",
                config.dataType()
                    .name());
            out.setLong("amountKb", config.amountKb());
            out.setInteger("duration", config.durationTicks());
            writeOptionalBodyKey(out, "origin", config.originBodyKey());
            out.setInteger("progress", generator.jobProgressTicks());
            out.setLong("consumedDeciKb", generator.consumedDeciKb());
            writeOptionalBodyKey(out, "counterpart", generator.detectedCounterpartBodyKey());
        }
        return out;
    }

    private static void readModuleData(ModuleInstance module, NBTTagCompound data, String path) {
        switch (module.kind()) {
            case HAMMER -> {
                AllowShootingConfig.Mode mode = requireEnum(AllowShootingConfig.Mode.class, data, "allowMode", path);
                double threshold = requireDouble(data, "allowThreshold", path);
                OrbitalTransferPlanner.RoutePriority priority = requireEnum(
                    OrbitalTransferPlanner.RoutePriority.class,
                    data,
                    "routePriority",
                    path);
                HammerVariant variant = requireEnum(HammerVariant.class, data, "variant", path);
                long energy = requireLong(data, "energy", path);
                int shotCooldown = requireNonNegativeInt(data, "shotCooldown", path);
                int probeCooldown = requireNonNegativeInt(data, "probeCooldown", path);
                if (energy < 0L) throw fail(path + ".energy", "must be non-negative");
                try {
                    ModuleHammer.requireTier(variant, module.tier());
                    ModuleHammer hammer = new ModuleHammer(
                        module.kind(),
                        new AllowShootingConfig(mode, threshold),
                        priority,
                        variant,
                        64,
                        energy);
                    hammer.setDispatchCooldowns(shotCooldown, probeCooldown);
                    module.setComponent(hammer);
                } catch (RuntimeException ex) {
                    throw fail(path, "invalid hammer state", ex);
                }
            }
            case MINER -> {
                if (!(module.component() instanceof ModuleMiner miner)) throw fail(path, "invalid miner component");
                MinerFocusTier tier = requireEnum(MinerFocusTier.class, data, "focusTier", path);
                String ore = data.hasKey("focusOre") ? requireString(data, "focusOre", path) : null;
                int progress = requireNonNegativeInt(data, "focusProgress", path);
                try {
                    miner.setFocus(tier, ore, progress);
                } catch (RuntimeException ex) {
                    throw fail(path, "invalid miner focus", ex);
                }
            }
            case DEBUG_DATA_GENERATOR -> readDebugGenerator(module, data, path);
            case POWER, GEOTHERMAL_GENERATOR, STORAGE, TANK, BATTERY, MAINTENANCE_BAY, MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> {
                if (!data.hasNoTags()) throw fail(path, "unexpected data for " + module.kind());
            }
        }
    }

    private static void readDebugGenerator(ModuleInstance module, NBTTagCompound data, String path) {
        if (!(module.component() instanceof ModuleDebugDataGenerator generator)) {
            throw fail(path, "invalid debug data generator component");
        }
        ModuleDebugDataGenerator.Mode mode = requireEnum(ModuleDebugDataGenerator.Mode.class, data, "mode", path);
        boolean enabled = requireBoolean(data, "enabled", path);
        SatelliteDataType dataType = requireEnum(SatelliteDataType.class, data, "dataType", path);
        long amount = requireLong(data, "amountKb", path);
        int duration = requireInt(data, "duration", path);
        int progress = requireInt(data, "progress", path);
        long consumed = requireLong(data, "consumedDeciKb", path);
        if (amount < 0L || amount > ModuleDebugDataGenerator.MAX_AMOUNT_KB) {
            throw fail(path + ".amountKb", "out of range");
        }
        if (duration < 1) throw fail(path + ".duration", "must be positive");
        if (progress < 0) throw fail(path + ".progress", "must be non-negative");
        if (consumed < 0L) throw fail(path + ".consumedDeciKb", "must be non-negative");
        generator.restore(
            new ModuleDebugDataGenerator.Config(
                mode,
                enabled,
                dataType,
                amount,
                duration,
                readOptionalBodyKey(data, "origin", path)),
            progress,
            consumed,
            readOptionalBodyKey(data, "counterpart", path));
    }

    private static NBTTagCompound writeOperation(ModuleOperationState operation) {
        NBTTagCompound out = new NBTTagCompound();
        ModuleOperationPlan plan = operation.plan();
        IModuleOperation spec = plan.spec();
        if (spec instanceof ModuleDeconstructionOperation) {
            out.setString("type", "DECONSTRUCTION");
        } else if (spec instanceof HammerModuleOperation hammer) {
            out.setString("type", "HAMMER");
            out.setString(
                "targetTier",
                hammer.targetTier()
                    .name());
            out.setString("variant", hammer.targetVariantKey());
        } else if (spec instanceof MinerFocusOperation miner) {
            out.setString("type", "MINER_FOCUS");
            out.setString(
                "targetTier",
                miner.targetTier()
                    .name());
            out.setString("focusTier", miner.targetFocusTierKey());
            if (miner.targetFocusOreKey() != null) out.setString("focusOre", miner.targetFocusOreKey());
        } else if (spec instanceof ModuleTierOperation tier) {
            out.setString("type", "MODULE_TIER");
            out.setString(
                "targetTier",
                tier.targetTier()
                    .name());
        } else {
            throw new IllegalStateException("[PERSIST] Unsupported module operation " + spec);
        }
        out.setString(
            "phase",
            operation.phase()
                .name());
        out.setInteger("buildTicks", plan.buildTicks());
        out.setInteger("refundPercent", plan.completionRefundPercent());
        out.setBoolean("reserveItems", plan.reserveItems());
        out.setBoolean("voidRefund", plan.voidCompletionRefund());
        out.setInteger("elapsed", operation.elapsedBuildTicks());
        out.setTag("materialCost", writeResources(plan.materialCost()));
        out.setTag("completionRefund", writeResources(plan.completionRefundCost()));
        out.setTag("deposited", writeStringAmounts(operation.depositedResources()));
        out.setTag("refundBuffer", writeStringAmounts(operation.refundBuffer()));
        return out;
    }

    private static ModuleOperationState readOperation(FacilityModuleKind moduleKind, ModuleInstance.ID moduleId,
        NBTTagCompound tag, String path) {
        String type = requireString(tag, "type", path);
        ModuleOperationPhase phase = requireEnum(ModuleOperationPhase.class, tag, "phase", path);
        int buildTicks = requireInt(tag, "buildTicks", path);
        IModuleOperation spec;
        switch (type) {
            case "DECONSTRUCTION" -> {
                if (buildTicks != 0) throw fail(path + ".buildTicks", "must be zero for deconstruction");
                spec = new ModuleDeconstructionOperation();
            }
            case "HAMMER" -> {
                if (moduleKind != FacilityModuleKind.HAMMER) throw fail(path, "hammer operation on " + moduleKind);
                String variant = requireString(tag, "variant", path);
                if (variant.isBlank()) throw fail(path + ".variant", "must not be blank");
                spec = new HammerModuleOperation(requireEnum(ModuleTier.class, tag, "targetTier", path), variant);
            }
            case "MINER_FOCUS" -> {
                if (moduleKind != FacilityModuleKind.MINER) throw fail(path, "miner focus operation on " + moduleKind);
                String focusTier = requireString(tag, "focusTier", path);
                if (focusTier.isBlank()) throw fail(path + ".focusTier", "must not be blank");
                spec = new MinerFocusOperation(
                    requireEnum(ModuleTier.class, tag, "targetTier", path),
                    focusTier,
                    tag.hasKey("focusOre") ? requireString(tag, "focusOre", path) : null);
            }
            case "MODULE_TIER" -> spec = new ModuleTierOperation(
                requireEnum(ModuleTier.class, tag, "targetTier", path));
            default -> throw fail(path + ".type", "unknown operation type " + type);
        }
        if (!(spec instanceof ModuleDeconstructionOperation) && buildTicks <= 0) {
            throw fail(path + ".buildTicks", "must be positive");
        }
        try {
            ModuleOperationPlan plan = new ModuleOperationPlan(
                spec,
                buildTicks,
                readItems(tag, "materialCost", path),
                readItems(tag, "completionRefund", path),
                requireInt(tag, "refundPercent", path),
                requireBoolean(tag, "reserveItems", path),
                requireBoolean(tag, "voidRefund", path));
            return ModuleOperationState.restore(
                plan,
                phase,
                requireInt(tag, "elapsed", path),
                readStringAmounts(tag, "deposited", path),
                readStringAmounts(tag, "refundBuffer", path));
        } catch (RuntimeException ex) {
            throw fail(path, "invalid operation for module " + moduleId, ex);
        }
    }

    private static void writeSettings(NBTTagCompound out, FacilityModuleSettingsSnapshot snapshot) {
        NBTTagList groups = new NBTTagList();
        snapshot.groups()
            .values()
            .stream()
            .sorted(
                Comparator.comparingInt(
                    group -> group.id()
                        .value()))
            .forEach(group -> {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger(
                    "id",
                    group.id()
                        .value());
                tag.setString(
                    "kind",
                    group.kind()
                        .name());
                tag.setString("name", group.displayName());
                tag.setTag("settings", writeSettings(group.settings()));
                groups.appendTag(tag);
            });
        out.setTag("settingsGroups", groups);

        NBTTagList privateSettings = new NBTTagList();
        snapshot.privateSettings()
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(ModuleInstance.ID::toString)))
            .forEach(entry -> {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString(
                    "module",
                    entry.getKey()
                        .toString());
                tag.setTag("settings", writeSettings(entry.getValue()));
                privateSettings.appendTag(tag);
            });
        out.setTag("privateSettings", privateSettings);

        NBTTagList membership = new NBTTagList();
        snapshot.membership()
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(ModuleInstance.ID::toString)))
            .forEach(entry -> {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString(
                    "module",
                    entry.getKey()
                        .toString());
                tag.setInteger(
                    "group",
                    entry.getValue()
                        .value());
                membership.appendTag(tag);
            });
        out.setTag("settingsMembership", membership);
    }

    private static FacilityModuleSettingsSnapshot readSettings(NBTTagCompound encoded,
        Map<ModuleInstance.ID, ModuleInstance> modules, String path) {
        Map<SettingsGroup.ID, SettingsGroup> groups = new LinkedHashMap<>();
        NBTTagList groupTags = requireList(encoded, "settingsGroups", NBT.TAG_COMPOUND, path);
        for (int i = 0; i < groupTags.tagCount(); i++) {
            String itemPath = path + ".settingsGroups[" + i + "]";
            NBTTagCompound tag = groupTags.getCompoundTagAt(i);
            SettingsGroup.ID id;
            try {
                id = new SettingsGroup.ID(requireInt(tag, "id", itemPath));
            } catch (RuntimeException ex) {
                throw fail(itemPath + ".id", "invalid settings group ID", ex);
            }
            FacilityModuleKind kind = requireEnum(FacilityModuleKind.class, tag, "kind", itemPath);
            SettingsGroup group = new SettingsGroup(
                id,
                kind,
                requireString(tag, "name", itemPath),
                readSettings(kind, requireCompound(tag, "settings", itemPath), itemPath + ".settings"));
            if (groups.put(id, group) != null) throw fail(itemPath + ".id", "duplicate settings group " + id);
        }

        Map<ModuleInstance.ID, ModuleSettings> privateSettings = new LinkedHashMap<>();
        NBTTagList privateTags = requireList(encoded, "privateSettings", NBT.TAG_COMPOUND, path);
        for (int i = 0; i < privateTags.tagCount(); i++) {
            String itemPath = path + ".privateSettings[" + i + "]";
            NBTTagCompound tag = privateTags.getCompoundTagAt(i);
            ModuleInstance.ID id = requireModuleId(tag, "module", itemPath);
            ModuleInstance module = modules.get(id);
            if (module == null) throw fail(itemPath + ".module", "references missing module " + id);
            ModuleSettings settings = readSettings(
                module.kind(),
                requireCompound(tag, "settings", itemPath),
                itemPath + ".settings");
            if (privateSettings.put(id, settings) != null) throw fail(itemPath + ".module", "duplicate owner " + id);
        }

        Map<ModuleInstance.ID, SettingsGroup.ID> membership = new LinkedHashMap<>();
        NBTTagList membershipTags = requireList(encoded, "settingsMembership", NBT.TAG_COMPOUND, path);
        for (int i = 0; i < membershipTags.tagCount(); i++) {
            String itemPath = path + ".settingsMembership[" + i + "]";
            NBTTagCompound tag = membershipTags.getCompoundTagAt(i);
            ModuleInstance.ID id = requireModuleId(tag, "module", itemPath);
            SettingsGroup.ID groupId = new SettingsGroup.ID(requireInt(tag, "group", itemPath));
            if (!modules.containsKey(id)) throw fail(itemPath + ".module", "references missing module " + id);
            if (membership.put(id, groupId) != null) throw fail(itemPath + ".module", "duplicate membership " + id);
        }
        try {
            return new FacilityModuleSettingsSnapshot(privateSettings, groups, membership);
        } catch (RuntimeException ex) {
            throw fail(path + ".settings", "invalid settings relationships", ex);
        }
    }

    private static NBTTagCompound writeSettings(ModuleSettings settings) {
        NBTTagCompound out = new NBTTagCompound();
        if (settings instanceof MinerSettings miner) {
            out.setString("type", "MINER");
            out.setTag("blacklist", writeStrings(miner.blacklistedOreKeys()));
        } else if (settings instanceof RecipeModuleSettings recipes) {
            out.setString("type", "RECIPE");
            out.setTag("book", writeRecipeBook(recipes.book()));
        } else {
            throw new IllegalStateException("[PERSIST] Unsupported module settings " + settings);
        }
        return out;
    }

    private static ModuleSettings readSettings(FacilityModuleKind kind, NBTTagCompound tag, String path) {
        String type = requireString(tag, "type", path);
        try {
            if (kind == FacilityModuleKind.MINER && "MINER".equals(type)) {
                return new MinerSettings(new LinkedHashSet<>(readStrings(tag, "blacklist", path)));
            }
            if (FacilityModuleRegistry.get(kind)
                .settingsGroups() && "RECIPE".equals(type)) {
                return new RecipeModuleSettings(readRecipeBook(requireCompound(tag, "book", path), path + ".book"));
            }
        } catch (RuntimeException ex) {
            throw fail(path, "invalid settings for " + kind, ex);
        }
        throw fail(path + ".type", type + " does not match " + kind);
    }

    private static NBTTagCompound writeRecipeBook(RecipeBook book) {
        NBTTagCompound out = new NBTTagCompound();
        out.setString(
            "mode",
            book.mode()
                .name());
        out.setString(
            "notDoablePolicy",
            book.notDoablePolicy()
                .name());
        NBTTagList recipes = new NBTTagList();
        for (SavedRecipe saved : book.recipes()) {
            NBTTagCompound tag = new NBTTagCompound();
            RecipeSnapshot recipe = saved.recipe();
            tag.setInteger("map", recipe.recipeMapOrdinal() & 0xFF);
            tag.setInteger("index", recipe.recipeIndex());
            tag.setLong("hash", recipe.contentHash());
            writeItemArray(tag, "inputs", recipe.inputs());
            writeItemArray(tag, "outputs", recipe.outputs());
            writeFluidArray(tag, "fluidInputs", recipe.fluidInputs());
            writeFluidArray(tag, "fluidOutputs", recipe.fluidOutputs());
            writeIntArray(tag, "outputChances", recipe.outputChances());
            writeIntArray(tag, "fluidOutputChances", recipe.fluidOutputChances());
            tag.setInteger("duration", recipe.duration());
            tag.setInteger("eut", recipe.eut());
            tag.setBoolean("enabled", saved.enabled());
            tag.setLong("requestAmount", saved.requestAmount());
            tag.setInteger("priority", saved.priority() & 0xFF);
            tag.setInteger("orderSize", saved.orderSize() & 0xFF);
            tag.setString("displayName", saved.displayName());
            recipes.appendTag(tag);
        }
        out.setTag("recipes", recipes);
        return out;
    }

    private static RecipeBook readRecipeBook(NBTTagCompound tag, String path) {
        RecipeSchedulerMode mode = requireEnum(RecipeSchedulerMode.class, tag, "mode", path);
        NotDoablePolicy policy = requireEnum(NotDoablePolicy.class, tag, "notDoablePolicy", path);
        NBTTagList recipeTags = requireList(tag, "recipes", NBT.TAG_COMPOUND, path);
        if (recipeTags.tagCount() > RecipeBook.MAX_RECIPES) throw fail(path + ".recipes", "too many recipes");
        List<SavedRecipe> recipes = new ArrayList<>(recipeTags.tagCount());
        for (int i = 0; i < recipeTags.tagCount(); i++) {
            String recipePath = path + ".recipes[" + i + "]";
            NBTTagCompound recipe = recipeTags.getCompoundTagAt(i);
            int map = requireInt(recipe, "map", recipePath);
            int priority = requireInt(recipe, "priority", recipePath);
            int orderSize = requireInt(recipe, "orderSize", recipePath);
            if (map < 1 || map > 255) throw fail(recipePath + ".map", "must be between 1 and 255");
            if (priority < 0 || priority > Byte.MAX_VALUE) throw fail(recipePath + ".priority", "out of range");
            if (orderSize < 1 || orderSize > Byte.MAX_VALUE) throw fail(recipePath + ".orderSize", "out of range");
            try {
                RecipeSnapshot snapshot = new RecipeSnapshot(
                    (byte) map,
                    requireInt(recipe, "index", recipePath),
                    requireLong(recipe, "hash", recipePath),
                    readItemArray(recipe, "inputs", recipePath),
                    readItemArray(recipe, "outputs", recipePath),
                    readFluidArray(recipe, "fluidInputs", recipePath),
                    readFluidArray(recipe, "fluidOutputs", recipePath),
                    readIntArray(recipe, "outputChances", recipePath),
                    readIntArray(recipe, "fluidOutputChances", recipePath),
                    requireInt(recipe, "duration", recipePath),
                    requireInt(recipe, "eut", recipePath));
                recipes.add(
                    new SavedRecipe(
                        snapshot,
                        requireBoolean(recipe, "enabled", recipePath),
                        requireLong(recipe, "requestAmount", recipePath),
                        (byte) priority,
                        (byte) orderSize,
                        requireString(recipe, "displayName", recipePath)));
            } catch (RuntimeException ex) {
                throw fail(recipePath, "invalid recipe", ex);
            }
        }
        try {
            return new RecipeBook(recipes, mode, policy);
        } catch (RuntimeException ex) {
            throw fail(path, "invalid recipe book", ex);
        }
    }

    private static NBTTagList writeBounds(Map<? extends InventoryKey, InventoryBounds> bounds) {
        NBTTagList out = new NBTTagList();
        for (Map.Entry<? extends InventoryKey, InventoryBounds> entry : bounds.entrySet()) {
            NBTTagCompound tag = writeResource(entry.getKey());
            tag.setLong(
                "low",
                entry.getValue()
                    .low());
            tag.setLong(
                "upper",
                entry.getValue()
                    .upper());
            out.appendTag(tag);
        }
        return out;
    }

    private static void readBounds(AutomatedFacility facility, NBTTagList tags, boolean items, String path) {
        Set<InventoryKey> seen = new LinkedHashSet<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            String itemPath = path + "[" + i + "]";
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            InventoryKey key = items ? readItemKey(tag, itemPath) : readFluidKey(tag, itemPath);
            if (!seen.add(key)) throw fail(itemPath, "duplicate resource " + key);
            try {
                facility.setBound(key, requireLong(tag, "low", itemPath), requireLong(tag, "upper", itemPath));
            } catch (RuntimeException ex) {
                throw fail(itemPath, "invalid inventory bound", ex);
            }
        }
    }

    private static NBTTagList writeAnchors(AutomatedFacility facility) {
        NBTTagList out = new NBTTagList();
        StationLayout layout = facility.stationLayout();
        if (layout == null) return out;
        for (ModuleInstance module : facility.modules()) {
            StationTileCoord anchor = module.anchorOrNull();
            if (anchor == null) throw new IllegalStateException("[PERSIST] Module " + module.id + " has no anchor");
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("module", module.id.toString());
            tag.setInteger("x", anchor.dx());
            tag.setInteger("y", anchor.dy());
            out.appendTag(tag);
        }
        return out;
    }

    private static void restoreAnchors(AutomatedFacility facility, Map<ModuleInstance.ID, ModuleInstance> modules,
        NBTTagList tags, String path) {
        StationLayout layout = facility.stationLayout();
        if (layout == null) {
            if (tags.tagCount() != 0) throw fail(path, "facility has no layout but anchors are present");
            return;
        }
        Map<StationTileCoord, PlacedTile> anchors = new LinkedHashMap<>();
        Set<ModuleInstance.ID> anchoredModules = new LinkedHashSet<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            String anchorPath = path + "[" + i + "]";
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            ModuleInstance.ID id = requireModuleId(tag, "module", anchorPath);
            ModuleInstance module = modules.get(id);
            if (module == null) throw fail(anchorPath + ".module", "references missing module " + id);
            if (!anchoredModules.add(id)) throw fail(anchorPath + ".module", "duplicate anchor for " + id);
            int x = requireInt(tag, "x", anchorPath);
            int y = requireInt(tag, "y", anchorPath);
            if (x < StationTileCoord.MIN || x > StationTileCoord.MAX
                || y < StationTileCoord.MIN
                || y > StationTileCoord.MAX) {
                throw fail(anchorPath, "coordinate is out of range: " + x + "," + y);
            }
            StationTileCoord coord = StationTileCoord.of(x, y);
            module.initAnchor(coord);
            PlacedTile tile = new PlacedTile(module, StationTileState.fromModuleStatus(module.status()));
            if (anchors.put(coord, tile) != null) throw fail(anchorPath, "duplicate anchor coordinate " + coord);
        }
        if (!anchoredModules.equals(modules.keySet())) throw fail(path, "every module must have exactly one anchor");
        try {
            layout.loadFromSnapshot(anchors);
            for (ModuleInstance module : modules.values()) layout.place(module);
        } catch (RuntimeException ex) {
            throw fail(path, "invalid or overlapping module footprints", ex);
        }
    }

    private static NBTTagList writeLogistics(Map<InventoryKey, LogisticsResourceConfig> configs) {
        NBTTagList out = new NBTTagList();
        configs.forEach((key, config) -> {
            NBTTagCompound tag = writeResource(key);
            tag.setInteger("minReserve", config.minReserve());
            tag.setInteger("orderSize", config.orderSize());
            tag.setBoolean("import", config.isImportEnabled());
            tag.setBoolean("supply", config.isSupplyEnabled());
            out.appendTag(tag);
        });
        return out;
    }

    private static Map<InventoryKey, LogisticsResourceConfig> readLogistics(NBTTagCompound source, String key,
        String path) {
        NBTTagList tags = requireList(source, key, NBT.TAG_COMPOUND, path);
        Map<InventoryKey, LogisticsResourceConfig> out = new LinkedHashMap<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            String itemPath = path + "." + key + "[" + i + "]";
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            String type = requireString(tag, "type", itemPath);
            InventoryKey resource = switch (type) {
                case "item" -> readItemKey(tag, itemPath);
                case "fluid" -> readFluidKey(tag, itemPath);
                default -> throw fail(itemPath + ".type", "unknown resource type " + type);
            };
            LogisticsResourceConfig config = new LogisticsResourceConfig(
                requireInt(tag, "minReserve", itemPath),
                requireInt(tag, "orderSize", itemPath),
                requireBoolean(tag, "import", itemPath),
                requireBoolean(tag, "supply", itemPath));
            if (out.put(resource, config) != null) throw fail(itemPath, "duplicate logistics resource " + resource);
        }
        return out;
    }

    private static NBTTagList writeConstructionInventory(Map<ItemStack, Long> inventory) {
        Map<ItemStackWrapper, Long> items = new LinkedHashMap<>();
        for (Map.Entry<ItemStack, Long> entry : inventory.entrySet()) {
            ItemStackWrapper key = ItemStackWrapper.of(entry.getKey());
            if (key == null) throw new IllegalStateException("[PERSIST] Construction inventory contains invalid item");
            if (items.put(key, entry.getValue()) != null) {
                throw new IllegalStateException("[PERSIST] Construction inventory contains duplicate item " + key);
            }
        }
        return writeResources(items);
    }

    private static Map<ItemStack, Long> readConstructionInventory(NBTTagCompound source, String key, String path) {
        Map<ItemStack, Long> out = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : readItems(source, key, path).entrySet()) {
            out.put(
                entry.getKey()
                    .toStack(1),
                entry.getValue());
        }
        return out;
    }

    private static NBTTagList writeResources(Map<? extends InventoryKey, Long> resources) {
        NBTTagList out = new NBTTagList();
        for (Map.Entry<? extends InventoryKey, Long> entry : resources.entrySet()) {
            out.appendTag(writeResource(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    private static Map<ItemStackWrapper, Long> readItems(NBTTagCompound source, String key, String path) {
        NBTTagList list = requireList(source, key, NBT.TAG_COMPOUND, path);
        Map<ItemStackWrapper, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            String itemPath = path + "." + key + "[" + i + "]";
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStackWrapper item = readItemKey(tag, itemPath);
            long amount = requirePositiveLong(tag, "amount", itemPath);
            if (out.put(item, amount) != null) throw fail(itemPath, "duplicate item " + item);
        }
        return out;
    }

    private static Map<FluidKey, Long> readFluids(NBTTagCompound source, String key, String path) {
        NBTTagList list = requireList(source, key, NBT.TAG_COMPOUND, path);
        Map<FluidKey, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            String itemPath = path + "." + key + "[" + i + "]";
            NBTTagCompound tag = list.getCompoundTagAt(i);
            FluidKey fluid = readFluidKey(tag, itemPath);
            long amount = requirePositiveLong(tag, "amount", itemPath);
            if (out.put(fluid, amount) != null) throw fail(itemPath, "duplicate fluid key " + fluid);
        }
        return out;
    }

    private static NBTTagCompound writeResource(InventoryKey key, long amount) {
        NBTTagCompound out = writeResource(key);
        out.setLong("amount", amount);
        return out;
    }

    private static NBTTagCompound writeResource(InventoryKey key) {
        NBTTagCompound out = new NBTTagCompound();
        NBTTagCompound stack = new NBTTagCompound();
        if (key instanceof ItemStackWrapper item) {
            out.setString("type", "item");
            item.toStack(1)
                .writeToNBT(stack);
        } else if (key instanceof FluidKey fluid) {
            out.setString("type", "fluid");
            fluid.toStack(1)
                .writeToNBT(stack);
        } else {
            throw new IllegalStateException("[PERSIST] Unsupported inventory resource " + key);
        }
        out.setTag("stack", stack);
        return out;
    }

    private static ItemStackWrapper readItemKey(NBTTagCompound tag, String path) {
        if (!"item".equals(requireString(tag, "type", path))) throw fail(path + ".type", "expected item");
        ItemStack stack = ItemStack.loadItemStackFromNBT(requireCompound(tag, "stack", path));
        ItemStackWrapper item = ItemStackWrapper.of(stack);
        if (item == null) throw fail(path + ".stack", "unknown or malformed item");
        return item;
    }

    private static FluidKey readFluidKey(NBTTagCompound tag, String path) {
        if (!"fluid".equals(requireString(tag, "type", path))) throw fail(path + ".type", "expected fluid");
        FluidStack stack = FluidStack.loadFluidStackFromNBT(requireCompound(tag, "stack", path));
        if (stack == null || stack.getFluid() == null) throw fail(path + ".stack", "unknown or malformed fluid");
        return FluidKey.of(stack);
    }

    private static NBTTagList writeCredits(Map<? extends InventoryKey, UpkeepAmount> credits) {
        NBTTagList out = new NBTTagList();
        credits.forEach(
            (key, value) -> { if (!value.isZero()) out.appendTag(writeResource(key, value.microUnitsPerMinute())); });
        return out;
    }

    private static Map<ItemStackWrapper, UpkeepAmount> readItemCredits(NBTTagCompound source, String key, String path) {
        Map<ItemStackWrapper, UpkeepAmount> out = new LinkedHashMap<>();
        readItems(source, key, path).forEach((item, amount) -> out.put(item, UpkeepAmount.ofMicroUnits(amount)));
        return out;
    }

    private static Map<FluidKey, UpkeepAmount> readFluidCredits(NBTTagCompound source, String key, String path) {
        Map<FluidKey, UpkeepAmount> out = new LinkedHashMap<>();
        readFluids(source, key, path).forEach((fluid, amount) -> out.put(fluid, UpkeepAmount.ofMicroUnits(amount)));
        return out;
    }

    private static NBTTagList writeStringAmounts(Map<String, Long> amounts) {
        NBTTagList out = new NBTTagList();
        amounts.forEach((key, amount) -> {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("key", key);
            tag.setLong("amount", amount);
            out.appendTag(tag);
        });
        return out;
    }

    private static Map<String, Long> readStringAmounts(NBTTagCompound source, String key, String path) {
        NBTTagList tags = requireList(source, key, NBT.TAG_COMPOUND, path);
        Map<String, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            String itemPath = path + "." + key + "[" + i + "]";
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            String itemKey = requireString(tag, "key", itemPath);
            if (itemKey.isBlank()) throw fail(itemPath + ".key", "must not be blank");
            long amount = requirePositiveLong(tag, "amount", itemPath);
            if (out.put(itemKey, amount) != null) throw fail(itemPath + ".key", "duplicate key " + itemKey);
        }
        return out;
    }

    private static void writeItemArray(NBTTagCompound target, String key, ItemStack[] stacks) {
        if (stacks == null) return;
        NBTTagList out = new NBTTagList();
        for (ItemStack stack : stacks) {
            ItemStackWrapper item = ItemStackWrapper.of(stack);
            if (item == null || stack.stackSize <= 0) throw new IllegalStateException("[PERSIST] Invalid recipe item");
            out.appendTag(writeResource(item, stack.stackSize));
        }
        target.setTag(key, out);
    }

    private static ItemStack[] readItemArray(NBTTagCompound source, String key, String path) {
        if (!source.hasKey(key)) return null;
        NBTTagList tags = requireList(source, key, NBT.TAG_COMPOUND, path);
        ItemStack[] out = new ItemStack[tags.tagCount()];
        for (int i = 0; i < tags.tagCount(); i++) {
            String itemPath = path + "." + key + "[" + i + "]";
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            long amount = requirePositiveLong(tag, "amount", itemPath);
            if (amount > Integer.MAX_VALUE) throw fail(itemPath + ".amount", "is too large");
            out[i] = readItemKey(tag, itemPath).toStack(amount);
        }
        return out;
    }

    private static void writeFluidArray(NBTTagCompound target, String key, FluidStack[] stacks) {
        if (stacks == null) return;
        NBTTagList out = new NBTTagList();
        for (FluidStack stack : stacks) {
            if (stack == null || stack.getFluid() == null || stack.amount <= 0) {
                throw new IllegalStateException("[PERSIST] Invalid recipe fluid");
            }
            out.appendTag(writeResource(FluidKey.of(stack), stack.amount));
        }
        target.setTag(key, out);
    }

    private static FluidStack[] readFluidArray(NBTTagCompound source, String key, String path) {
        if (!source.hasKey(key)) return null;
        NBTTagList tags = requireList(source, key, NBT.TAG_COMPOUND, path);
        FluidStack[] out = new FluidStack[tags.tagCount()];
        for (int i = 0; i < tags.tagCount(); i++) {
            String itemPath = path + "." + key + "[" + i + "]";
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            long amount = requirePositiveLong(tag, "amount", itemPath);
            if (amount > Integer.MAX_VALUE) throw fail(itemPath + ".amount", "is too large");
            out[i] = readFluidKey(tag, itemPath).toStack((int) amount);
        }
        return out;
    }

    private static void writeIntArray(NBTTagCompound target, String key, int[] values) {
        if (values != null) target.setIntArray(key, values);
    }

    private static int[] readIntArray(NBTTagCompound source, String key, String path) {
        if (!source.hasKey(key)) return null;
        requireType(source, key, NBT.TAG_INT_ARRAY, path);
        return source.getIntArray(key);
    }

    private static NBTTagList writeStrings(Iterable<String> strings) {
        NBTTagList out = new NBTTagList();
        for (String value : strings) {
            if (value == null || value.isBlank()) throw new IllegalStateException("[PERSIST] Blank persisted string");
            out.appendTag(new NBTTagString(value));
        }
        return out;
    }

    private static List<String> readStrings(NBTTagCompound source, String key, String path) {
        NBTTagList tags = requireList(source, key, NBT.TAG_STRING, path);
        List<String> out = new ArrayList<>(tags.tagCount());
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            String value = tags.getStringTagAt(i);
            if (value == null || value.isBlank()) throw fail(path + "." + key + "[" + i + "]", "blank string");
            if (!seen.add(value)) throw fail(path + "." + key + "[" + i + "]", "duplicate string " + value);
            out.add(value);
        }
        return out;
    }

    private static void writeOptionalBodyKey(NBTTagCompound target, String key, CelestialObjectKey bodyKey) {
        if (bodyKey != null) target.setTag(key, writeBodyKey(bodyKey));
    }

    private static CelestialObjectKey readOptionalBodyKey(NBTTagCompound source, String key, String path) {
        if (!source.hasKey(key)) return null;
        return readBodyKey(requireCompound(source, key, path), path + "." + key);
    }

    static NBTTagCompound writeBodyKey(CelestialObjectKey key) {
        NBTTagCompound out = new NBTTagCompound();
        if (key.isRegistered()) {
            out.setString("type", "registered");
            out.setString(
                "body",
                key.registeredBodyId()
                    .name());
        } else {
            out.setString("type", "minor");
            out.setString(
                "parent",
                key.minorBodyId()
                    .parentBodyId()
                    .name());
            out.setInteger(
                "index",
                key.minorBodyId()
                    .index());
        }
        return out;
    }

    static CelestialObjectKey readBodyKey(NBTTagCompound tag, String path) {
        String type = requireString(tag, "type", path);
        if ("registered".equals(type)) {
            return CelestialObjectKey.registered(requireEnum(CelestialObjectId.class, tag, "body", path));
        }
        if ("minor".equals(type)) {
            CelestialObjectId parent = requireEnum(CelestialObjectId.class, tag, "parent", path);
            int index = requireInt(tag, "index", path);
            try {
                return CelestialObjectKey.minorBody(new MinorCelestialBodyId(parent, index));
            } catch (RuntimeException ex) {
                throw fail(path, "invalid minor celestial body key", ex);
            }
        }
        throw fail(path + ".type", "unknown celestial object key type " + type);
    }

    private static ModuleInstance.ID requireModuleId(NBTTagCompound source, String key, String path) {
        try {
            return ModuleInstance.ID.from(requireString(source, key, path));
        } catch (RuntimeException ex) {
            throw fail(path + "." + key, "invalid module ID", ex);
        }
    }

    private static byte checkedByte(int value, String path) {
        if (value < 0 || value > Byte.MAX_VALUE) throw fail(path, "must be between 0 and " + Byte.MAX_VALUE);
        return (byte) value;
    }

    private static long requirePositiveLong(NBTTagCompound source, String key, String path) {
        long value = requireLong(source, key, path);
        if (value <= 0L) throw fail(path + "." + key, "must be positive");
        return value;
    }

    private static int requireNonNegativeInt(NBTTagCompound source, String key, String path) {
        int value = requireInt(source, key, path);
        if (value < 0) throw fail(path + "." + key, "must be non-negative");
        return value;
    }

    private static NBTTagCompound requireCompound(NBTTagCompound source, String key, String path) {
        requireType(source, key, NBT.TAG_COMPOUND, path);
        return source.getCompoundTag(key);
    }

    private static NBTTagList requireList(NBTTagCompound source, String key, int elementType, String path) {
        requireType(source, key, NBT.TAG_LIST, path);
        NBTTagList list = source.getTagList(key, elementType);
        if (list.tagCount() > 0 && list.func_150303_d() != elementType) {
            throw fail(path + "." + key, "has the wrong list element type");
        }
        return list;
    }

    private static String requireString(NBTTagCompound source, String key, String path) {
        requireType(source, key, NBT.TAG_STRING, path);
        return source.getString(key);
    }

    private static int requireInt(NBTTagCompound source, String key, String path) {
        requireType(source, key, NBT.TAG_INT, path);
        return source.getInteger(key);
    }

    private static long requireLong(NBTTagCompound source, String key, String path) {
        requireType(source, key, NBT.TAG_LONG, path);
        return source.getLong(key);
    }

    private static double requireDouble(NBTTagCompound source, String key, String path) {
        requireType(source, key, NBT.TAG_DOUBLE, path);
        return source.getDouble(key);
    }

    private static boolean requireBoolean(NBTTagCompound source, String key, String path) {
        requireType(source, key, NBT.TAG_BYTE, path);
        return source.getBoolean(key);
    }

    private static <T extends Enum<T>> T requireEnum(Class<T> type, NBTTagCompound source, String key, String path) {
        return requireEnum(type, requireString(source, key, path), path + "." + key);
    }

    private static <T extends Enum<T>> T requireEnum(Class<T> type, String name, String path) {
        try {
            return Enum.valueOf(type, name);
        } catch (RuntimeException ex) {
            throw fail(path, "invalid " + type.getSimpleName() + " value " + name, ex);
        }
    }

    private static void requireType(NBTTagCompound source, String key, int type, String path) {
        if (source == null || !source.hasKey(key, type)) throw fail(path + "." + key, "missing or has wrong type");
    }

    private static IllegalStateException fail(String path, String message) {
        return new IllegalStateException("[PERSIST] " + path + ": " + message);
    }

    private static IllegalStateException fail(String path, String message, Throwable cause) {
        return new IllegalStateException("[PERSIST] " + path + ": " + message, cause);
    }

    public record Decoded(UUID teamId, CelestialAsset asset) {}
}
