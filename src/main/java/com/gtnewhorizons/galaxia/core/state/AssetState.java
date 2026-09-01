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

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
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
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduleState;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
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
        NbtReader in = NbtReader.persistence(tag, path);
        UUID teamId;
        CelestialAsset.ID assetId;
        try {
            teamId = UUID.fromString(in.string("team"));
            assetId = CelestialAsset.ID.from(in.string("id"));
        } catch (RuntimeException ex) {
            throw fail(path, "invalid team or asset ID", ex);
        }
        CelestialAsset.Kind kind = in.enumValue(CelestialAsset.Kind.class, "kind");
        Buildable.Status status = in.enumValue(Buildable.Status.class, "status");
        SatelliteKind satelliteKind = kind == CelestialAsset.Kind.SATELLITE
            ? in.enumValue(SatelliteKind.class, "satelliteKind")
            : null;
        if (kind != CelestialAsset.Kind.SATELLITE && tag.hasKey("satelliteKind")) {
            throw fail(path + ".satelliteKind", "present for non-satellite asset");
        }
        CelestialAsset asset;
        try {
            asset = CelestialAsset.create(assetId, readBodyKey(in.compound("body")), kind, status, satelliteKind);
            asset.setDisplayName(in.string("name"));
            asset.setConstructionInventory(readConstructionInventory(in, "construction"));
            asset.logisticsConfig.loadFromSnapshot(readLogistics(in, "logistics"));
        } catch (RuntimeException ex) {
            throw fail(path, "invalid asset state", ex);
        }
        if (tag.hasKey("controller")) {
            if (!(asset instanceof Station station)) throw fail(path + ".controller", "present for non-station asset");
            NbtReader controller = in.compound("controller");
            station
                .setController(new BlockPos(controller.integer("x"), controller.integer("y"), controller.integer("z")));
        }
        if (asset instanceof AutomatedFacility) {
            decodeFacility(asset, in.compound("facility"));
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
        current.restoreBounds(replacement.boundsSnapshot());
        replacement.filtersSnapshot()
            .forEach((items, filters) -> current.setFilters(filters, items));
        current.restoreModulesAndSettings(replacement.modules(), replacement.settingsGroups());
        current.restoreInventory(replacement.inventorySnapshot());
        current.loadUpkeepCredits(replacement.upkeepCredits());
        if (current.stationLayout() != null && replacement.stationLayout() != null) {
            current.stationLayout()
                .loadFromSnapshot(
                    replacement.stationLayout()
                        .snapshot());
        }
        current.restoreRecipeScheduleStates(replacement.recipeScheduleStates());
    }

    private static NBTTagCompound encodeFacility(AutomatedFacility facility) {
        NBTTagCompound out = new NBTTagCompound();
        out.setLong("energy", facility.getEnergyStored());
        out.setLong("featureSalt", facility.stationFeatureSalt());
        out.setTag("bounds", writeBounds(facility.boundsSnapshot()));
        Map<Boolean, List<String>> filters = facility.filtersSnapshot();
        out.setTag("itemFilters", writeStrings(filters.getOrDefault(true, List.of())));
        out.setTag("fluidFilters", writeStrings(filters.getOrDefault(false, List.of())));
        writeSettingsGroups(out, facility.settingsGroups());

        NBTTagList modules = new NBTTagList();
        for (ModuleInstance module : facility.modules()) modules.appendTag(writeModule(facility, module));
        out.setTag("modules", modules);
        out.setTag("inventory", writeResources(facility.inventorySnapshot()));
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

    private static AutomatedFacility decodeFacility(CelestialAsset asset, NbtReader in) {
        String path = in.path();
        if (!(asset instanceof AutomatedFacility facility)) throw fail(path, "asset is not an automated facility");
        facility.setEnergyStored(in.longValue("energy"));
        facility.setStationFeatureSalt(in.longValue("featureSalt"));
        facility.restoreBounds(readBounds(in, "bounds"));
        facility.setFilters(readStrings(in, "itemFilters"), true);
        facility.setFilters(readStrings(in, "fluidFilters"), false);

        Map<ModuleInstance.ID, RecipeScheduleState> schedules = new LinkedHashMap<>();
        Map<ModuleInstance.ID, ModuleInstance> modules = new LinkedHashMap<>();
        List<ModuleInstance> restoredModules = new ArrayList<>();
        NBTTagList moduleTags = in.compounds("modules");
        for (int i = 0; i < moduleTags.tagCount(); i++) {
            NbtReader moduleIn = in.element("modules", i, moduleTags.getCompoundTagAt(i));
            ModuleInstance module = readModule(facility, moduleIn, schedules);
            if (modules.put(module.id, module) != null)
                throw fail(moduleIn.path() + ".id", "duplicate module ID " + module.id);
            restoredModules.add(module);
        }
        facility.restoreModulesAndSettings(restoredModules, readSettingsGroups(in));

        facility.restoreInventory(readResources(in, "inventory", InventoryKey.class));
        facility.loadUpkeepCredits(
            new UpkeepSettlement.Credits(readItemCredits(in, "upkeepItems"), readFluidCredits(in, "upkeepFluids")));
        restoreAnchors(facility, modules, in, "anchors");
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
        if (FacilityModuleRegistry.get(module.kind())
            .settingsGroups()) {
            out.setTag("settingsBinding", writeSettingsBinding(module));
        } else if (module.settingsBinding() != null) {
            throw new IllegalStateException("[PERSIST] Unsupported module has settings binding " + module.id);
        }
        return out;
    }

    private static ModuleInstance readModule(AutomatedFacility facility, NbtReader in,
        Map<ModuleInstance.ID, RecipeScheduleState> schedules) {
        NBTTagCompound tag = in.tag();
        String path = in.path();
        ModuleInstance.ID id;
        try {
            id = ModuleInstance.ID.from(in.string("id"));
        } catch (RuntimeException ex) {
            throw fail(path + ".id", "invalid module ID", ex);
        }
        FacilityModuleKind kind = in.enumValue(FacilityModuleKind.class, "kind");
        ModuleShape shape = in.enumValue(ModuleShape.class, "shape");
        ModuleTier tier = in.enumValue(ModuleTier.class, "tier");
        ModuleInstance module;
        try {
            module = FacilityModuleRegistry.create(id, kind, null, shape, tier);
        } catch (IllegalArgumentException ex) {
            throw fail(path + ".tier", ex.getMessage(), ex);
        }

        module.setRotation(in.integer("rotation", 0, 3));
        readModuleData(module, in.compound("data"));
        module.updateStatus(in.enumValue(Buildable.Status.class, "status"));
        module.setTicks(in.integer("ticks", 0, Integer.MAX_VALUE));
        String priority = in.string("priority");
        module.setPriorityOverride(priority.isEmpty() ? null : in.enumValue(ModulePriority.class, "priority"));
        module.setEnabled(in.bool("enabled"));
        int parallel = in.integer("parallel", 1, Byte.MAX_VALUE);
        if (module.component() instanceof IParallelModule parallelModule) parallelModule.setParallel((byte) parallel);
        module.clearConsumedResources();
        module.getConstructionInventory()
            .putAll(readConstructionInventory(in, "construction"));

        if (tag.hasKey("operation")) {
            module.setOperation(readOperation(kind, id, in.compound("operation")));
        }
        if (tag.hasKey("schedule")) {
            if (!(module.component() instanceof IRecipeModule)) {
                throw fail(path + ".schedule", "non-recipe module has schedule state");
            }
            NbtReader schedule = in.compound("schedule");
            schedules.put(
                id,
                new RecipeScheduleState(
                    (byte) schedule.integer("cursor", 0, RecipeBook.MAX_RECIPES - 1),
                    (byte) schedule.integer("remaining", 0, Byte.MAX_VALUE)));
        }
        if (FacilityModuleRegistry.get(kind)
            .settingsGroups()) {
            module.setSettingsBinding(readSettingsBinding(kind, in.compound("settingsBinding")));
        } else if (tag.hasKey("settingsBinding")) {
            throw fail(path + ".settingsBinding", "module kind does not support settings");
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

    private static void readModuleData(ModuleInstance module, NbtReader in) {
        NBTTagCompound data = in.tag();
        String path = in.path();
        if (module.component() instanceof ModuleHammer) {
            AllowShootingConfig.Mode mode = in.enumValue(AllowShootingConfig.Mode.class, "allowMode");
            double threshold = in.doubleValue("allowThreshold");
            OrbitalTransferPlanner.RoutePriority priority = in
                .enumValue(OrbitalTransferPlanner.RoutePriority.class, "routePriority");
            HammerVariant variant = in.enumValue(HammerVariant.class, "variant");
            long energy = in.longValue("energy");
            int shotCooldown = in.integer("shotCooldown", 0, Integer.MAX_VALUE);
            int probeCooldown = in.integer("probeCooldown", 0, Integer.MAX_VALUE);
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
        } else if (module.component() instanceof ModuleMiner miner) {
            MinerFocusTier tier = in.enumValue(MinerFocusTier.class, "focusTier");
            String ore = data.hasKey("focusOre") ? in.string("focusOre") : null;
            int progress = in.integer("focusProgress", 0, Integer.MAX_VALUE);
            try {
                miner.setFocus(tier, ore, progress);
            } catch (RuntimeException ex) {
                throw fail(path, "invalid miner focus", ex);
            }
        } else if (module.component() instanceof ModuleDebugDataGenerator generator) {
            readDebugGenerator(generator, in);
        } else if (!data.hasNoTags()) {
            throw fail(path, "unexpected data for " + module.kind());
        }
    }

    private static void readDebugGenerator(ModuleDebugDataGenerator generator, NbtReader in) {
        String path = in.path();
        ModuleDebugDataGenerator.Mode mode = in.enumValue(ModuleDebugDataGenerator.Mode.class, "mode");
        boolean enabled = in.bool("enabled");
        SatelliteDataType dataType = in.enumValue(SatelliteDataType.class, "dataType");
        long amount = in.longValue("amountKb");
        int duration = in.integer("duration", 1, Integer.MAX_VALUE);
        int progress = in.integer("progress", 0, Integer.MAX_VALUE);
        long consumed = in.longValue("consumedDeciKb");
        if (amount < 0L || amount > ModuleDebugDataGenerator.MAX_AMOUNT_KB) {
            throw fail(path + ".amountKb", "out of range");
        }
        if (consumed < 0L) throw fail(path + ".consumedDeciKb", "must be non-negative");
        generator.restore(
            new ModuleDebugDataGenerator.Config(
                mode,
                enabled,
                dataType,
                amount,
                duration,
                readOptionalBodyKey(in, "origin")),
            progress,
            consumed,
            readOptionalBodyKey(in, "counterpart"));
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
        NbtReader in) {
        NBTTagCompound tag = in.tag();
        String path = in.path();
        String type = in.string("type");
        ModuleOperationPhase phase = in.enumValue(ModuleOperationPhase.class, "phase");
        int buildTicks = in.integer("buildTicks");
        IModuleOperation spec;
        switch (type) {
            case "DECONSTRUCTION" -> spec = new ModuleDeconstructionOperation();
            case "HAMMER" -> {
                if (moduleKind != FacilityModuleKind.HAMMER) throw fail(path, "hammer operation on " + moduleKind);
                spec = new HammerModuleOperation(
                    in.enumValue(ModuleTier.class, "targetTier"),
                    in.nonBlankString("variant"));
            }
            case "MINER_FOCUS" -> {
                if (moduleKind != FacilityModuleKind.MINER) throw fail(path, "miner focus operation on " + moduleKind);
                spec = new MinerFocusOperation(
                    in.enumValue(ModuleTier.class, "targetTier"),
                    in.nonBlankString("focusTier"),
                    tag.hasKey("focusOre") ? in.string("focusOre") : null);
            }
            case "MODULE_TIER" -> spec = new ModuleTierOperation(in.enumValue(ModuleTier.class, "targetTier"));
            default -> throw fail(path + ".type", "unknown operation type " + type);
        }
        try {
            ModuleOperationPlan plan = new ModuleOperationPlan(
                spec,
                buildTicks,
                readResources(in, "materialCost", ItemStackWrapper.class),
                readResources(in, "completionRefund", ItemStackWrapper.class),
                in.integer("refundPercent"),
                in.bool("reserveItems"),
                in.bool("voidRefund"));
            return ModuleOperationState.restore(
                plan,
                phase,
                in.integer("elapsed"),
                readStringAmounts(in, "deposited"),
                readStringAmounts(in, "refundBuffer"));
        } catch (RuntimeException ex) {
            throw fail(path, "invalid operation for module " + moduleId, ex);
        }
    }

    private static void writeSettingsGroups(NBTTagCompound out, List<SettingsGroup> settingsGroups) {
        NBTTagList groups = new NBTTagList();
        settingsGroups.stream()
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
                tag.setTag("settings", ModuleSettingsState.encode(group.settings()));
                groups.appendTag(tag);
            });
        out.setTag("settingsGroups", groups);
    }

    private static List<SettingsGroup> readSettingsGroups(NbtReader in) {
        Map<SettingsGroup.ID, SettingsGroup> groups = new LinkedHashMap<>();
        NBTTagList groupTags = in.compounds("settingsGroups");
        for (int i = 0; i < groupTags.tagCount(); i++) {
            NbtReader groupIn = in.element("settingsGroups", i, groupTags.getCompoundTagAt(i));
            String itemPath = groupIn.path();
            SettingsGroup.ID id;
            try {
                id = new SettingsGroup.ID(groupIn.integer("id"));
            } catch (RuntimeException ex) {
                throw fail(itemPath + ".id", "invalid settings group ID", ex);
            }
            FacilityModuleKind kind = groupIn.enumValue(FacilityModuleKind.class, "kind");
            SettingsGroup group;
            try {
                group = new SettingsGroup(
                    id,
                    kind,
                    groupIn.string("name"),
                    ModuleSettingsState.decode(kind, groupIn.compound("settings")));
            } catch (RuntimeException ex) {
                throw fail(itemPath, "invalid settings group", ex);
            }
            if (groups.put(id, group) != null) throw fail(itemPath + ".id", "duplicate settings group " + id);
        }
        return List.copyOf(groups.values());
    }

    private static NBTTagCompound writeSettingsBinding(ModuleInstance module) {
        ModuleInstance.SettingsBinding binding = module.settingsBinding();
        if (binding == null) throw new IllegalStateException("[PERSIST] Module has no settings binding " + module.id);
        NBTTagCompound out = new NBTTagCompound();
        if (binding instanceof ModuleInstance.SettingsBinding.Private privateBinding) {
            out.setBoolean("shared", false);
            out.setTag("settings", ModuleSettingsState.encode(privateBinding.settings()));
        } else {
            out.setBoolean("shared", true);
            out.setInteger(
                "group",
                ((ModuleInstance.SettingsBinding.Shared) binding).groupId()
                    .value());
        }
        return out;
    }

    private static ModuleInstance.SettingsBinding readSettingsBinding(FacilityModuleKind kind, NbtReader in) {
        String path = in.path();
        try {
            if (in.bool("shared")) {
                return new ModuleInstance.SettingsBinding.Shared(new SettingsGroup.ID(in.integer("group")));
            }
            return new ModuleInstance.SettingsBinding.Private(
                ModuleSettingsState.decode(kind, in.compound("settings")));
        } catch (RuntimeException ex) {
            throw fail(path, "invalid module settings binding", ex);
        }
    }

    private static NBTTagList writeBounds(Map<? extends InventoryKey, InventoryBounds> bounds) {
        NBTTagList out = new NBTTagList();
        for (Map.Entry<? extends InventoryKey, InventoryBounds> entry : bounds.entrySet()) {
            NBTTagCompound tag = InventoryKeyState.encode(entry.getKey());
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

    private static Map<InventoryKey, InventoryBounds> readBounds(NbtReader in, String key) {
        NBTTagList tags = in.compounds(key);
        Map<InventoryKey, InventoryBounds> bounds = new LinkedHashMap<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            NbtReader itemIn = in.element(key, i, tags.getCompoundTagAt(i));
            String itemPath = itemIn.path();
            InventoryKey resource = readResource(itemIn);
            try {
                InventoryBounds previous = bounds
                    .put(resource, new InventoryBounds(itemIn.longValue("low"), itemIn.longValue("upper")));
                if (previous != null) throw fail(itemPath, "duplicate resource " + resource);
            } catch (RuntimeException ex) {
                throw fail(itemPath, "invalid inventory bound", ex);
            }
        }
        return bounds;
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
        NbtReader in, String key) {
        NBTTagList tags = in.compounds(key);
        String path = in.path() + "." + key;
        StationLayout layout = facility.stationLayout();
        if (layout == null) {
            if (tags.tagCount() != 0) throw fail(path, "facility has no layout but anchors are present");
            return;
        }
        Set<ModuleInstance.ID> anchoredModules = new LinkedHashSet<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            NbtReader anchorIn = in.element(key, i, tags.getCompoundTagAt(i));
            String anchorPath = anchorIn.path();
            ModuleInstance.ID id = requireModuleId(anchorIn, "module");
            ModuleInstance module = modules.get(id);
            if (module == null) throw fail(anchorPath + ".module", "references missing module " + id);
            if (!anchoredModules.add(id)) throw fail(anchorPath + ".module", "duplicate anchor for " + id);
            StationTileCoord coord;
            try {
                coord = StationTileCoord.of(anchorIn.integer("x"), anchorIn.integer("y"));
            } catch (IllegalArgumentException ex) {
                throw fail(anchorPath, "coordinate is out of range", ex);
            }
            module.initAnchor(coord);
            try {
                layout.place(module);
            } catch (RuntimeException ex) {
                throw fail(anchorPath, "invalid or overlapping module footprint", ex);
            }
        }
        if (!anchoredModules.equals(modules.keySet())) throw fail(path, "every module must have exactly one anchor");
    }

    private static NBTTagList writeLogistics(Map<InventoryKey, LogisticsResourceConfig> configs) {
        NBTTagList out = new NBTTagList();
        configs.forEach((key, config) -> {
            NBTTagCompound tag = InventoryKeyState.encode(key);
            tag.setInteger("minReserve", config.minReserve());
            tag.setInteger("orderSize", config.orderSize());
            tag.setBoolean("import", config.isImportEnabled());
            tag.setBoolean("supply", config.isSupplyEnabled());
            out.appendTag(tag);
        });
        return out;
    }

    private static Map<InventoryKey, LogisticsResourceConfig> readLogistics(NbtReader in, String key) {
        NBTTagList tags = in.compounds(key);
        Map<InventoryKey, LogisticsResourceConfig> out = new LinkedHashMap<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            NbtReader itemIn = in.element(key, i, tags.getCompoundTagAt(i));
            String itemPath = itemIn.path();
            InventoryKey resource = readResource(itemIn);
            LogisticsResourceConfig config = new LogisticsResourceConfig(
                itemIn.integer("minReserve"),
                itemIn.integer("orderSize"),
                itemIn.bool("import"),
                itemIn.bool("supply"));
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

    private static Map<ItemStack, Long> readConstructionInventory(NbtReader in, String key) {
        Map<ItemStack, Long> out = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : readResources(in, key, ItemStackWrapper.class).entrySet()) {
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

    private static <K extends InventoryKey> Map<K, Long> readResources(NbtReader in, String key, Class<K> type) {
        NBTTagList list = in.compounds(key);
        Map<K, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NbtReader resourceIn = in.element(key, i, list.getCompoundTagAt(i));
            String resourcePath = resourceIn.path();
            InventoryKey resource = readResource(resourceIn);
            if (!type.isInstance(resource)) throw fail(resourcePath, "unexpected resource type");
            long amount = requirePositiveLong(resourceIn, "amount");
            if (out.put(type.cast(resource), amount) != null) {
                throw fail(resourcePath, "duplicate resource " + resource);
            }
        }
        return out;
    }

    private static NBTTagCompound writeResource(InventoryKey key, long amount) {
        NBTTagCompound out = InventoryKeyState.encode(key);
        out.setLong("amount", amount);
        return out;
    }

    private static InventoryKey readResource(NbtReader in) {
        try {
            return InventoryKeyState.decode(in);
        } catch (RuntimeException ex) {
            throw fail(in.path(), "invalid inventory resource", ex);
        }
    }

    private static NBTTagList writeCredits(Map<? extends InventoryKey, UpkeepAmount> credits) {
        NBTTagList out = new NBTTagList();
        credits.forEach(
            (key, value) -> { if (!value.isZero()) out.appendTag(writeResource(key, value.microUnitsPerMinute())); });
        return out;
    }

    private static Map<ItemStackWrapper, UpkeepAmount> readItemCredits(NbtReader in, String key) {
        Map<ItemStackWrapper, UpkeepAmount> out = new LinkedHashMap<>();
        readResources(in, key, ItemStackWrapper.class)
            .forEach((item, amount) -> out.put(item, UpkeepAmount.ofMicroUnits(amount)));
        return out;
    }

    private static Map<FluidKey, UpkeepAmount> readFluidCredits(NbtReader in, String key) {
        Map<FluidKey, UpkeepAmount> out = new LinkedHashMap<>();
        readResources(in, key, FluidKey.class)
            .forEach((fluid, amount) -> out.put(fluid, UpkeepAmount.ofMicroUnits(amount)));
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

    private static Map<String, Long> readStringAmounts(NbtReader in, String key) {
        NBTTagList tags = in.compounds(key);
        Map<String, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            NbtReader itemIn = in.element(key, i, tags.getCompoundTagAt(i));
            String itemPath = itemIn.path();
            String itemKey = itemIn.string("key");
            if (itemKey.isBlank()) throw fail(itemPath + ".key", "must not be blank");
            long amount = requirePositiveLong(itemIn, "amount");
            if (out.put(itemKey, amount) != null) throw fail(itemPath + ".key", "duplicate key " + itemKey);
        }
        return out;
    }

    private static NBTTagList writeStrings(Iterable<String> strings) {
        NBTTagList out = new NBTTagList();
        for (String value : strings) {
            if (value == null || value.isBlank()) throw new IllegalStateException("[PERSIST] Blank persisted string");
            out.appendTag(new NBTTagString(value));
        }
        return out;
    }

    private static List<String> readStrings(NbtReader in, String key) {
        NBTTagList tags = in.strings(key);
        List<String> out = new ArrayList<>(tags.tagCount());
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            String value = tags.getStringTagAt(i);
            String itemPath = in.path() + "." + key + "[" + i + "]";
            if (value == null || value.isBlank()) throw fail(itemPath, "blank string");
            if (!seen.add(value)) throw fail(itemPath, "duplicate string " + value);
            out.add(value);
        }
        return out;
    }

    private static void writeOptionalBodyKey(NBTTagCompound target, String key, CelestialObjectKey bodyKey) {
        if (bodyKey != null) target.setTag(key, writeBodyKey(bodyKey));
    }

    private static CelestialObjectKey readOptionalBodyKey(NbtReader in, String key) {
        if (!in.tag()
            .hasKey(key)) return null;
        return readBodyKey(in.compound(key));
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

    static CelestialObjectKey readBodyKey(NbtReader in) {
        String path = in.path();
        String type = in.string("type");
        if ("registered".equals(type)) {
            return CelestialObjectKey.registered(in.enumValue(CelestialObjectId.class, "body"));
        }
        if ("minor".equals(type)) {
            CelestialObjectId parent = in.enumValue(CelestialObjectId.class, "parent");
            int index = in.integer("index");
            try {
                return CelestialObjectKey.minorBody(new MinorCelestialBodyId(parent, index));
            } catch (RuntimeException ex) {
                throw fail(path, "invalid minor celestial body key", ex);
            }
        }
        throw fail(path + ".type", "unknown celestial object key type " + type);
    }

    private static ModuleInstance.ID requireModuleId(NbtReader in, String key) {
        try {
            return ModuleInstance.ID.from(in.string(key));
        } catch (RuntimeException ex) {
            throw fail(in.path() + "." + key, "invalid module ID", ex);
        }
    }

    private static long requirePositiveLong(NbtReader in, String key) {
        long value = in.longValue(key);
        if (value <= 0L) throw fail(in.path() + "." + key, "must be positive");
        return value;
    }

    private static IllegalStateException fail(String path, String message) {
        return new IllegalStateException("[PERSIST] " + path + ": " + message);
    }

    private static IllegalStateException fail(String path, String message, Throwable cause) {
        return new IllegalStateException("[PERSIST] " + path + ": " + message, cause);
    }

    public record Decoded(UUID teamId, CelestialAsset asset) {}
}
