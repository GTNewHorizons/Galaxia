package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.control;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.TreeUtil;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapWidget;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientScenario;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems" })
public final class ModuleConstructionGuiGameTests {

    private static final String STATION_SCREEN = "galaxia_station_management";
    private static final String PICKER_SCREEN = "galaxia_station_module_picker";
    private static final CelestialObjectKey BODY = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
    private static final List<FacilityModuleKind> KINDS = List
        .of(FacilityModuleKind.HAMMER, FacilityModuleKind.STORAGE, FacilityModuleKind.TANK);
    private static final List<StationTileCoord> TILES = List
        .of(StationTileCoord.of(1, 0), StationTileCoord.of(-1, 0), StationTileCoord.of(0, 1));

    private ModuleConstructionGuiGameTests() {}

    /** Integration contract: the full map-to-build GUI path creates paid modules with the chosen physical options. */
    @GameTest(timeoutTicks = 2400)
    public static void buildHammerStorageAndTankThroughGui(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientScenario scenario = ClientTest.scenario(helper)
            .server("prepare station with cargo storage and exact build materials", () -> fixture.prepare(helper))
            .client(
                "register fixture mirror cleanup",
                c -> c.afterTest(() -> CelestialAssetStore.CLIENT.destroyAssetInternal(fixture.assetId)));
        GuiTestSupport.openFacility(scenario, helper, fixture.assetId, BODY);
        for (int index = 0; index < KINDS.size(); index++) {
            int buildIndex = index;
            FacilityModuleKind kind = KINDS.get(index);
            ModuleTier tier = tier(kind);
            String step = kind.name() + ": ";
            scenario
                .click(
                    ClientTarget.of(
                        "empty station tile " + TILES.get(buildIndex),
                        c -> fixture.emptyTileTarget(TILES.get(buildIndex))))
                .awaitClient(step + "module picker visible", c -> GuiTestSupport.requireScreen(PICKER_SCREEN))
                .click(control("module.kind." + kind.name()))
                .click(control("module.tier." + tier.name()));
            if (kind == FacilityModuleKind.HAMMER) {
                scenario.click(control("module.hammer.BIG"));
            }
            scenario.click(control("module.build"))
                .awaitServer(
                    step + "server accepted paid construction",
                    () -> fixture.assertBuilt(false, buildIndex, false, buildIndex + 1))
                .awaitClient(step + "station map restored", c -> GuiTestSupport.requireScreen(STATION_SCREEN));
        }
        for (int index = 0; index < KINDS.size(); index++) {
            int buildIndex = index;
            String step = KINDS.get(index)
                .name() + ": ";
            scenario.withinTicks(600)
                .awaitServer(
                    step + "normal ticks completed construction",
                    () -> fixture.assertBuilt(false, buildIndex, true, KINDS.size()))
                .withinTicks(150)
                .awaitClient(
                    step + "completed module synchronized",
                    c -> fixture.assertBuilt(true, buildIndex, true, KINDS.size()));
        }
        scenario.server("server consumed all build materials", () -> fixture.assertMaterialsConsumed(false))
            .withinTicks(150)
            .awaitClient("paid inventory synchronized", c -> fixture.assertMaterialsConsumed(true))
            .capture("gui-built-hammer-storage-tank")
            .succeed();
    }

    /** Bug regression: the highest BIG HAMMER tier must remain selectable and usable in the upgrade GUI. */
    @GameTest(timeoutTicks = 2400)
    public static void builtBigHammerCanUpgradeToUvThroughGui(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientScenario scenario = ClientTest.scenario(helper)
            .server("prepare station with build and UV upgrade materials", () -> fixture.prepare(helper, true))
            .client(
                "register fixture mirror cleanup",
                c -> c.afterTest(() -> CelestialAssetStore.CLIENT.destroyAssetInternal(fixture.assetId)));
        GuiTestSupport.openFacility(scenario, helper, fixture.assetId, BODY)
            .click(ClientTarget.of("empty HAMMER tile", c -> fixture.emptyTileTarget(TILES.getFirst())))
            .awaitClient("module picker visible", c -> GuiTestSupport.requireScreen(PICKER_SCREEN))
            .click(control("module.kind.HAMMER"))
            .click(control("module.hammer.BIG"))
            .click(control("module.tier.UV"))
            .click(control("module.tier.LuV"))
            .click(control("module.build"))
            .withinTicks(700)
            .awaitServerAccelerated("BIG LuV HAMMER completed", 10, () -> fixture.assertBuilt(false, 0, true, 1))
            .withinTicks(150)
            .awaitClient("built HAMMER synchronized", c -> fixture.assertBuilt(true, 0, true, 1))
            .server(
                "remember the built module identity",
                () -> fixture.upgradeModuleId = fixture.facility(false)
                    .stationLayout()
                    .moduleAt(TILES.getFirst()).id)
            .click(ClientTarget.of("built HAMMER tile", c -> fixture.tileTarget(TILES.getFirst())))
            .click(control("UPGRADE"))
            .click(control("module.upgrade.hammer.variant.BASE"))
            .click(control("module.upgrade.hammer.tier.EV"))
            .click(control("module.upgrade.hammer.variant.BIG"))
            .click(control("module.upgrade.hammer.tier.UV"))
            .capture("big-hammer-uv-upgrade-selected")
            .click(control("module.upgrade.confirm"))
            .awaitServer("GUI submitted the UV upgrade", () -> fixture.assertHammerUpgrade(false, false))
            .withinTicks(700)
            .awaitServerAccelerated(
                "simulation ticks completed UV upgrade",
                10,
                () -> fixture.assertHammerUpgrade(false, true))
            .withinTicks(150)
            .awaitClient("UV HAMMER synchronized", c -> fixture.assertHammerUpgrade(true, true))
            .click(control("UPGRADE"))
            .awaitClient("UV remains selected when reopening", c -> {
                if (GuiTestSupport.findNamedWidget("module.upgrade.hammer.tier.UV") == null)
                    throw new AssertionError("UV is missing after reopening the upgraded HAMMER");
                fixture.assertHammerUpgrade(true, true);
            })
            .capture("big-hammer-uv-upgrade-complete")
            .succeed();
    }

    /**
     * Integration contract: abandoning a build draft spends nothing and cannot leak its options into the next build.
     */
    @GameTest(timeoutTicks = 1400)
    public static void abandonedBuildDraftDoesNotAffectNextModule(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientScenario scenario = ClientTest.scenario(helper)
            .server("prepare station and build materials", () -> fixture.prepare(helper))
            .client(
                "register fixture mirror cleanup",
                c -> c.afterTest(() -> CelestialAssetStore.CLIENT.destroyAssetInternal(fixture.assetId)));
        GuiTestSupport.openFacility(scenario, helper, fixture.assetId, BODY)
            .click(ClientTarget.of("abandoned build tile", c -> fixture.emptyTileTarget(TILES.getFirst())))
            .awaitClient("module picker visible", c -> GuiTestSupport.requireScreen(PICKER_SCREEN))
            .click(control("module.kind.HAMMER"))
            .click(control("module.tier.LuV"))
            .click(control("module.hammer.BIG"))
            .step("leave the selected HAMMER without building")
            .escape()
            .awaitClient("picker closed to gameplay", c -> {
                if (Minecraft.getMinecraft().currentScreen != null)
                    throw new AssertionError("Escape did not close the module picker");
            })
            .server("abandoned draft left server unchanged", () -> fixture.assertDraftOutcome(false, false))
            .client("abandoned draft left client unchanged", c -> fixture.assertDraftOutcome(true, false));
        GuiTestSupport.openFacility(scenario, helper, fixture.assetId, BODY)
            .click(ClientTarget.of("different build tile", c -> fixture.emptyTileTarget(TILES.get(1))))
            .awaitClient("fresh module chooser visible", c -> GuiTestSupport.requireScreen(PICKER_SCREEN))
            .click(control("module.kind.STORAGE"))
            .click(control("module.tier.HV"))
            .click(control("module.build"))
            .awaitServer("only the replacement storage was ordered", () -> fixture.assertDraftOutcome(false, true))
            .awaitClient("replacement order synchronized", c -> {
                GuiTestSupport.requireScreen(STATION_SCREEN);
                fixture.assertDraftOutcome(true, true);
            })
            .withinTicks(600)
            .awaitServerAccelerated("replacement storage completed through simulation ticks", 10, () -> {
                fixture.assertDraftOutcome(false, true);
                if (!fixture.facility(false)
                    .stationLayout()
                    .moduleAt(TILES.get(1))
                    .isOperational()) throw new AssertionError("Replacement storage has not completed construction");
            })
            .withinTicks(150)
            .awaitClient("completed storage synchronized", c -> {
                fixture.assertDraftOutcome(true, true);
                if (!fixture.facility(true)
                    .stationLayout()
                    .moduleAt(TILES.get(1))
                    .isOperational()) throw new AssertionError("Completed replacement storage has not synchronized");
            })
            .capture("replacement-build-after-abandoned-draft")
            .succeed();
    }

    private static ModuleTier tier(FacilityModuleKind kind) {
        return kind == FacilityModuleKind.HAMMER ? ModuleTier.LuV : ModuleTier.HV;
    }

    private static final class Fixture {

        private final CelestialAsset.ID assetId = CelestialAsset.ID.create();
        private final Map<ItemStackWrapper, Long> materials = new LinkedHashMap<>();
        private ModuleInstance.ID upgradeModuleId;
        private int prerequisiteModules;

        void prepare(GameTestHelper helper) {
            prepare(helper, false);
        }

        void prepare(GameTestHelper helper, boolean includeHammerUpgrade) {
            EntityPlayerMP player = null;
            for (Object candidate : helper.getWorld().playerEntities) {
                if (candidate instanceof EntityPlayerMP actual) {
                    player = actual;
                    break;
                }
            }
            if (player == null) throw new AssertionError("Integrated player has not joined");
            if (includeHammerUpgrade) {
                EntityPlayerMP testPlayer = player;
                boolean originalCreative = player.capabilities.isCreativeMode;
                helper.afterTest(() -> {
                    testPlayer.capabilities.isCreativeMode = originalCreative;
                    testPlayer.sendPlayerAbilities();
                });
                player.capabilities.isCreativeMode = false;
                player.sendPlayerAbilities();
            }
            UUID teamId = GTTeamsCompat.getTeamData(player)
                .orElseThrow()
                .getTeamId();
            helper.afterTest(() -> AssetStateSync.SERVER.destroyAsset(assetId));
            AutomatedFacility facility = new AutomatedFacility(
                assetId,
                BODY,
                CelestialAsset.Kind.AUTOMATED_STATION,
                Buildable.Status.OPERATIONAL);
            facility.setDisplayName("GUI module construction " + assetId);
            FacilityCommand.Result storage = facility.applyCommand(
                new FacilityCommand.BuildModules(
                    assetId,
                    FacilityModuleKind.STORAGE,
                    FacilityModuleKind.STORAGE.defaultShape(),
                    new IModuleComponent.BuildPhysicalSpec.Tier(includeHammerUpgrade ? ModuleTier.IV : ModuleTier.HV),
                    null,
                    true,
                    includeHammerUpgrade
                        ? List.of(
                            ModulePlacement.at(StationTileCoord.of(0, -1)),
                            ModulePlacement.at(StationTileCoord.of(0, -2)))
                        : List.of(ModulePlacement.at(StationTileCoord.of(0, -1)))),
                new FacilityCommand.Authority(true, true));
            if (storage.status() != FacilityCommand.Status.CHANGED)
                throw new AssertionError("Could not seed prerequisite cargo storage");
            prerequisiteModules = facility.modules()
                .size();
            for (FacilityModuleKind kind : KINDS) {
                FacilityModuleRegistry.operationCost(
                    FacilityModuleRegistry.get(kind)
                        .getTierData(tier(kind))
                        .constructionCost())
                    .forEach((resource, amount) -> materials.merge(resource, amount, Long::sum));
            }
            if (includeHammerUpgrade) {
                FacilityModuleRegistry.operationCost(
                    FacilityModuleRegistry.get(FacilityModuleKind.HAMMER)
                        .getTierData(ModuleTier.UV)
                        .constructionCost())
                    .forEach((resource, amount) -> materials.merge(resource, amount, Long::sum));
            }
            materials.forEach(
                (resource, amount) -> {
                    if (facility.insert(resource, amount) != amount)
                        throw new AssertionError("Fixture materials exceed capacity");
                });
            CelestialAssetStore.SERVER.registerAssetInternal(teamId, facility);
            AssetStateSync.SERVER.publishFullTo(player.getUniqueID(), assetId);
        }

        AutomatedFacility facility(boolean client) {
            var asset = (client ? CelestialAssetStore.CLIENT : CelestialAssetStore.SERVER).findAssetInternal(assetId);
            if (!(asset instanceof AutomatedFacility facility))
                throw new AssertionError("Fixture station is unavailable");
            return facility;
        }

        ClickTarget emptyTileTarget(StationTileCoord tile) {
            if (facility(true).stationLayout()
                .isOccupied(tile)) throw new AssertionError("Build tile is already occupied");
            return tileTarget(tile);
        }

        ClickTarget tileTarget(StationTileCoord tile) {
            var screen = GuiTestSupport.requireScreen(STATION_SCREEN);
            List<StationMapWidget> maps = TreeUtil
                .flatListByType(screen.getMainPanel(), StationMapWidget.class, IWidget::areAncestorsEnabled);
            if (maps.size() != 1) throw new AssertionError("Expected one visible station map");
            StationMapWidget map = maps.getFirst();
            if (!assetId.equals(map.assetId())) throw new AssertionError("GUI opened a different facility");
            return GuiTestSupport.tileTarget(map, tile);
        }

        void assertHammerUpgrade(boolean client, boolean complete) {
            ModuleInstance module = facility(client).stationLayout()
                .moduleAt(TILES.getFirst());
            if (module == null || !module.id.equals(upgradeModuleId)
                || !(module.component() instanceof ModuleHammer hammer)
                || hammer.variant() != HammerVariant.BIG)
                throw new AssertionError("Upgrade must preserve the built BIG HAMMER and its location");
            if (complete) {
                if (module.tier() != ModuleTier.UV || !module.isOperational())
                    throw new AssertionError("BIG HAMMER has not completed its UV upgrade");
            } else if (module.operationOrNull() == null || !(module.operationOrNull()
                .plan()
                .spec() instanceof IModuleOperation.Hammer target)
                || target.targetVariant() != HammerVariant.BIG
                || target.targetTier() != ModuleTier.UV) {
                    throw new AssertionError("Upgrade GUI did not submit the selected BIG UV target");
                }
        }

        void assertBuilt(boolean client, int index, boolean complete, int builtCount) {
            AutomatedFacility facility = facility(client);
            ModuleInstance module = facility.stationLayout()
                .moduleAt(TILES.get(index));
            FacilityModuleKind kind = KINDS.get(index);
            if (facility.modules()
                .size() != builtCount + prerequisiteModules || module == null
                || module.kind() != kind
                || module.tier() != tier(kind))
                throw new AssertionError("GUI build must retain chosen kind, tier and tile for " + kind);
            if (kind == FacilityModuleKind.HAMMER
                && (!(module.component() instanceof ModuleHammer hammer) || hammer.variant() != HammerVariant.BIG))
                throw new AssertionError("GUI build did not retain the BIG HAMMER choice");
            if (complete && !module.isOperational())
                throw new AssertionError("Module is still under construction: " + kind);
            if (!complete && module.operationOrNull() == null)
                throw new AssertionError("GUI Build must create a paid timed construction operation");
        }

        void assertDraftOutcome(boolean client, boolean replacementBuilt) {
            AutomatedFacility facility = facility(client);
            if (facility.stationLayout()
                .isOccupied(TILES.getFirst()))
                throw new AssertionError("Abandoned HAMMER draft occupied its original tile");
            if (facility.modules()
                .size() != (replacementBuilt ? 2 : 1))
                throw new AssertionError("Abandoned draft changed the module count");
            ModuleInstance replacement = facility.stationLayout()
                .moduleAt(TILES.get(1));
            if (replacementBuilt) {
                if (replacement == null || replacement.kind() != FacilityModuleKind.STORAGE
                    || replacement.tier() != ModuleTier.HV)
                    throw new AssertionError("Replacement build reused the abandoned kind, tier or tile");
            } else if (replacement != null) {
                throw new AssertionError("Unconfirmed draft created a module");
            }
            Map<ItemStackWrapper, Long> spent = replacementBuilt ? FacilityModuleRegistry.operationCost(
                FacilityModuleRegistry.get(FacilityModuleKind.STORAGE)
                    .getTierData(ModuleTier.HV)
                    .constructionCost())
                : Map.of();
            materials.forEach((resource, amount) -> {
                if (facility.itemAmount(resource) != amount - spent.getOrDefault(resource, 0L))
                    throw new AssertionError("Inventory must charge only the confirmed replacement build: " + resource);
            });
        }

        void assertMaterialsConsumed(boolean client) {
            AutomatedFacility facility = facility(client);
            materials.forEach(
                (resource, amount) -> {
                    if (facility.itemAmount(resource) != 0)
                        throw new AssertionError("GUI construction left unspent materials");
                });
        }
    }
}
