package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.control;
import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.findNamedWidget;
import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.namedTarget;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.Display;

import com.cleanroommc.modularui.ModularUIConfig;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.utils.TreeUtil;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapWidget;
import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeMapId;
import com.gtnewhorizons.galaxia.compat.recipe.NativeRecipeWidget;
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
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientScenario;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.NEIRecipeWidget;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.nei.GTNEIDefaultHandler;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems" })
public final class StationGuiGameTests {

    private static final String STATION_SCREEN = "galaxia_station_management";
    private static final String RECIPE_SCREEN = "galaxia_recipe_input";
    private static final StationTileCoord MACERATOR_TILE = StationTileCoord.of(1, 0);
    private static final StationTileCoord SECOND_MACERATOR_TILE = StationTileCoord.of(-1, 0);

    private StationGuiGameTests() {}

    /** Bug regression: returning from NEI must leave the original module configuration usable. */
    @GameTest(timeoutTicks = 600)
    public static void recipeEditorReturnsToUsableStation(GameTestHelper helper) {
        StationFixture fixture = new StationFixture();
        var sequence = openConfiguration(helper, fixture);

        for (int attempt = 0; attempt < 2; attempt++) {
            boolean useCancelButton = attempt == 0;
            String step = "round " + (attempt + 1) + ": ";
            sequence.step(step + "add recipe")
                .click(control("recipe.add"))
                .awaitClient(step + "editor ready", c -> requireScreen(RECIPE_SCREEN))
                .step(step + "open NEI")
                .click(control("recipe.input.nei"))
                .awaitClient(step + "NEI ready", c -> c.screen(GuiRecipe.class))
                .step(step + "leave NEI")
                .escape()
                .awaitClient(step + "editor restored", c -> requireScreen(RECIPE_SCREEN));
            if (useCancelButton) {
                sequence.step(step + "leave editor")
                    .click(control("recipe.input.cancel"));
            } else {
                sequence.step(step + "leave editor")
                    .escape();
            }
            sequence.awaitClient(step + "station restored", c -> fixture.assertReturnedToStation());
        }

        sequence.step("capture restored configuration")
            .capture("station-after-nei")
            .step("use Add after both return paths")
            .click(control("recipe.add"))
            .awaitClient("editor reopens after both return paths", c -> requireScreen(RECIPE_SCREEN))
            .step("close final editor")
            .escape()
            .awaitClient("final station remains usable", c -> fixture.assertReturnedToStation())
            .succeed();
    }

    /** Integration contract: closing configuration leaves Configure usable for another edit. */
    @GameTest(timeoutTicks = 400)
    public static void recipeConfigurationClosesAndReopens(GameTestHelper helper) {
        StationFixture fixture = new StationFixture();
        openConfiguration(helper, fixture).step("close recipe configuration")
            .click(control("recipe.close"))
            .awaitClient("recipe configuration closed", c -> {
                if (!TreeUtil.flatListByType(requireScreen(STATION_SCREEN).getMainPanel(), DropdownWidget.class)
                    .isEmpty()) {
                    throw new AssertionError("Recipe configuration is still open");
                }
            })
            .step("reopen configuration")
            .click(control("CONFIG"))
            .awaitClient("configuration usable again", c -> requiredWidget("recipe.add"))
            .step("capture reopened configuration")
            .capture("configuration-reopened")
            .succeed();
    }

    /** Integration contract: canceling group creation must not block recipe editing. */
    @GameTest(timeoutTicks = 400)
    public static void cancelGroupCreationLeavesRecipesUsable(GameTestHelper helper) {
        StationFixture fixture = new StationFixture();
        openConfiguration(helper, fixture).step("open groups")
            .click(control("settings.group.selector"))
            .awaitClient("group menu ready", c -> requiredWidget("settings.group.create"))
            .step("create group")
            .click(control("settings.group.create"))
            .awaitClient("group name editor ready", c -> groupNameOverlay())
            .step("capture group name editor")
            .capture("group-name-editor")
            .step("cancel group creation")
            .click(control("settings.group.cancel"))
            .step("add recipe after cancel")
            .click(control("recipe.add"))
            .awaitClient("recipe editor opened", c -> requireScreen(RECIPE_SCREEN))
            .succeed();
    }

    private static ClientScenario openConfiguration(GameTestHelper helper, StationFixture fixture) {
        var sequence = ClientTest.scenario(helper)
            .server("prepare server station", () -> fixture.prepare(helper))
            .awaitClient("station synchronized", c -> fixture.clientFacility())
            .client("remember client settings", c -> {
                c.afterTest(fixture::cleanup);
                fixture.rememberClientSettings();
            });
        if (fixture.guiScale == 2) sequence.resizeWindow(1281, 721);
        return GuiTestSupport
            .openFacility(
                sequence,
                helper,
                fixture.assetId,
                CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
                fixture.guiScale)
            .awaitClient("station screen ready", c -> fixture.captureStationScreen())
            .step("select macerator")
            .click(ClientTarget.of("macerator tile", c -> fixture.tileTarget(MACERATOR_TILE)))
            .awaitClient("macerator selected", c -> fixture.assertMaceratorSelected())
            .step("open module configuration")
            .click(control("CONFIG"))
            .awaitClient("recipe configuration ready", c -> requiredWidget("recipe.add"));
    }

    /** Integration contract: Save crosses the real command boundary and survives reopening. */
    @GameTest(timeoutTicks = 500)
    public static void savedRecipeToggleSurvivesReopen(GameTestHelper helper) {
        StationFixture fixture = new StationFixture(true);
        openConfiguration(helper, fixture).step("disable recipe")
            .click(control("recipe.toggle.0"))
            .step("save recipe configuration")
            .click(control("recipe.save"))
            .awaitServer("server saved disabled recipe", () -> fixture.assertRecipeEnabled(false, false))
            .awaitClient("saved recipe synchronized", c -> fixture.assertRecipeEnabled(true, false))
            .step("reopen saved configuration")
            .click(control("CONFIG"))
            .awaitClient("saved configuration ready", c -> requiredWidget("recipe.toggle.0"))
            .step("enable saved recipe")
            .click(control("recipe.toggle.0"))
            .step("save enabled recipe")
            .click(control("recipe.save"))
            .awaitServer("server saved reenabled recipe", () -> fixture.assertRecipeEnabled(false, true))
            .succeed();
    }

    /** Bug regression: an actual catalog recipe renders natively and Back leaves the list usable. */
    @GameTest(timeoutTicks = 400)
    public static void nativeRecipeDetailsReturnToList(GameTestHelper helper) {
        StationFixture fixture = new StationFixture(true);
        openConfiguration(helper, fixture).step("open native recipe details")
            .click(control("recipe.details.0"))
            .awaitClient("native recipe widget ready", c -> uniqueWidget(NativeRecipeWidget.class))
            .step("capture native macerator")
            .capture("native-macerator")
            .step("back to recipe list")
            .click(control("recipe.details.back"))
            .awaitClient("native details closed", c -> {
                if (!TreeUtil.flatListByType(requireScreen(STATION_SCREEN).getMainPanel(), NativeRecipeWidget.class)
                    .isEmpty()) throw new AssertionError("Native recipe is still open");
            })
            .step("use Add after details")
            .click(control("recipe.add"))
            .awaitClient("editor opened after details", c -> requireScreen(RECIPE_SCREEN))
            .succeed();
    }

    // The server owns fixture state. UI changes use real commands and synchronization.
    /** Integration contract: a new settings group is server-owned and retains its recipe settings. */
    @GameTest(timeoutTicks = 500)
    public static void createdGroupSurvivesReopen(GameTestHelper helper) {
        StationFixture fixture = new StationFixture(true);
        openConfiguration(helper, fixture).step("open groups")
            .click(control("settings.group.selector"))
            .awaitClient("group menu ready", c -> requiredWidget("settings.group.create"))
            .step("create group")
            .click(control("settings.group.create"))
            .awaitClient("group editor ready", c -> groupNameOverlay())
            .step("save group")
            .click(ClientTarget.of("settings.group.save", c -> fixture.saveGroupTarget()))
            .awaitServer("server created group", () -> fixture.assertGroupCreated(false))
            .awaitClient("group synchronized", c -> fixture.assertGroupCreated(true))
            .step("reopen grouped configuration")
            .click(control("CONFIG"))
            .awaitClient("group name visible", c -> visibleText(fixture.groupName))
            .step("capture saved group")
            .capture("saved-group")
            .step("open grouped recipe details")
            .click(control("recipe.details.0"))
            .awaitClient("group retained native recipe", c -> uniqueWidget(NativeRecipeWidget.class))
            .succeed();
    }

    /** Integration contract: group members share recipe edits, while detached modules retain independent recipes. */
    @GameTest(timeoutTicks = 1000)
    public static void groupedMaceratorsShareRecipeChangesThroughGui(GameTestHelper helper) {
        StationFixture fixture = new StationFixture(true, 1, true);
        openConfiguration(helper, fixture).click(control("settings.group.selector"))
            .click(control("settings.group.create"))
            .awaitClient("group editor ready", c -> groupNameOverlay())
            .click(ClientTarget.of("save shared group", c -> fixture.saveGroupTarget()))
            .awaitServer("first module owns the new group", () -> fixture.assertGroupCreated(false))
            .awaitClient("new group synchronized", c -> fixture.assertGroupCreated(true))
            .click(ClientTarget.of("second macerator", c -> fixture.tileTarget(SECOND_MACERATOR_TILE)))
            .click(control("CONFIG"))
            .awaitClient("second module configuration ready", c -> requiredWidget("recipe.add"))
            .click(control("settings.group.selector"))
            .click(ClientTarget.of("existing shared group", c -> textTarget(fixture.groupName)))
            .awaitServer("both modules joined the same group", () -> fixture.assertSharedRecipes(false, true))
            .awaitClient("shared recipes synchronized", c -> fixture.assertSharedRecipes(true, true))
            .click(control("CONFIG"))
            .awaitClient("joined module shows the inherited recipe", c -> requiredWidget("recipe.toggle.0"))
            .click(control("recipe.toggle.0"))
            .click(control("recipe.save"))
            .awaitServer(
                "edit through second module updated both members",
                () -> fixture.assertSharedRecipes(false, false))
            .awaitClient("both disabled recipes synchronized", c -> fixture.assertSharedRecipes(true, false))
            .click(ClientTarget.of("first macerator", c -> fixture.tileTarget(MACERATOR_TILE)))
            .click(control("CONFIG"))
            .awaitClient("first module configuration reopened", c -> requiredWidget("recipe.toggle.0"))
            .click(control("recipe.toggle.0"))
            .click(control("recipe.save"))
            .awaitServer(
                "edit through first module reenabled both members",
                () -> fixture.assertSharedRecipes(false, true))
            .awaitClient("reenabled shared recipes synchronized", c -> fixture.assertSharedRecipes(true, true))
            .capture("shared-macerator-recipes")
            .click(ClientTarget.of("detach second macerator", c -> fixture.tileTarget(SECOND_MACERATOR_TILE)))
            .click(control("CONFIG"))
            .click(control("settings.group.selector"))
            .click(ClientTarget.of("leave settings group", c -> textTarget("No Group")))
            .awaitServer("detached module kept its recipe", () -> fixture.assertDetachedRecipes(false, true, false))
            .awaitClient("detachment synchronized", c -> fixture.assertDetachedRecipes(true, true, false))
            .click(control("CONFIG"))
            .click(control("recipe.toggle.0"))
            .click(control("recipe.save"))
            .awaitServer(
                "private edit left the group recipe enabled",
                () -> fixture.assertDetachedRecipes(false, false, false))
            .awaitClient(
                "independent recipe state synchronized",
                c -> fixture.assertDetachedRecipes(true, false, false))
            .click(ClientTarget.of("detach last group member", c -> fixture.tileTarget(MACERATOR_TILE)))
            .click(control("CONFIG"))
            .click(control("settings.group.selector"))
            .click(ClientTarget.of("leave empty group", c -> textTarget("No Group")))
            .awaitServer(
                "empty group removed without losing recipes",
                () -> fixture.assertDetachedRecipes(false, false, true))
            .awaitClient(
                "group removal and private recipes synchronized",
                c -> fixture.assertDetachedRecipes(true, false, true))
            .succeed();
    }

    /** Bug regression: configuration controls remain reachable at doubled GUI scale with odd framebuffer dimensions. */
    @GameTest(timeoutTicks = 400)
    public static void configurationFitsDoubledGuiScale(GameTestHelper helper) {
        StationFixture fixture = new StationFixture(false, 2);
        openConfiguration(helper, fixture).step("capture doubled configuration")
            .capture("configuration-scale-2")
            .step("close visible configuration")
            .click(control("recipe.close"))
            .awaitClient("scaled configuration closed", c -> {
                if (!TreeUtil.flatListByType(requireScreen(STATION_SCREEN).getMainPanel(), DropdownWidget.class)
                    .isEmpty()) throw new AssertionError("Scaled configuration is still open");
            })
            .succeed();
    }

    /** Bug regression: the NEI selection is added, explicitly saved, and reopened as the same native recipe. */
    @GameTest(timeoutTicks = 700)
    public static void neiRecipeTransferSurvivesSave(GameTestHelper helper) {
        StationFixture fixture = new StationFixture();
        NeiButtons buttons = new NeiButtons();
        openConfiguration(helper, fixture).client("observe NEI controls", c -> {
            MinecraftForge.EVENT_BUS.register(buttons);
            c.afterTest(() -> MinecraftForge.EVENT_BUS.unregister(buttons));
        })
            .step("add recipe")
            .click(control("recipe.add"))
            .awaitClient("recipe editor ready", c -> requireScreen(RECIPE_SCREEN))
            .step("open NEI catalog")
            .click(control("recipe.input.nei"))
            .withinTicks(200)
            .awaitClient("NEI transfer control ready", c -> buttons.transferPoint())
            .async("transfer displayed recipe", client -> client.shiftClick(0, c -> buttons.transferPoint(), c -> true))
            .awaitClient("transferred recipe editor ready", c -> requireScreen(RECIPE_SCREEN))
            .step("capture transferred recipe")
            .capture("nei-transferred-recipe")
            .step("add selected recipe to draft")
            .click(control("recipe.input.add"))
            .awaitClient("station draft restored", c -> fixture.assertReturnedToStation())
            .step("save imported recipe")
            .click(control("recipe.save"))
            .awaitServer("server saved NEI selection", () -> fixture.assertRecipeMatches(false, buttons.selectedRecipe))
            .awaitClient(
                "saved NEI selection synchronized",
                c -> fixture.assertRecipeMatches(true, buttons.selectedRecipe))
            .step("reopen imported recipe")
            .click(control("CONFIG"))
            .awaitClient("imported recipe available", c -> requiredWidget("recipe.details.0"))
            .step("open imported native details")
            .click(control("recipe.details.0"))
            .awaitClient("imported native view ready", c -> uniqueWidget(NativeRecipeWidget.class))
            .step("capture imported native recipe")
            .capture("saved-nei-recipe")
            .succeed();
    }

    /** Product contract: Close discards the recipe draft, while Save commits it. */
    @GameTest(timeoutTicks = 500)
    public static void closingRecipeDraftDiscardsChanges(GameTestHelper helper) {
        StationFixture fixture = new StationFixture(true);
        openConfiguration(helper, fixture).step("disable draft recipe")
            .click(control("recipe.toggle.0"))
            .step("close without saving")
            .click(control("recipe.close"))
            .server("server recipe unchanged", () -> fixture.assertRecipeEnabled(false, true))
            .step("reopen discarded draft")
            .click(control("CONFIG"))
            .awaitClient("original recipe restored", c -> requiredWidget("recipe.toggle.0"))
            .step("disable original recipe")
            .click(control("recipe.toggle.0"))
            .step("save current draft")
            .click(control("recipe.save"))
            .awaitServer("only saved change reaches server", () -> fixture.assertRecipeEnabled(false, false))
            .succeed();
    }

    /** Bug regression: selecting the core safely closes module configuration during the live widget update loop. */
    @GameTest(timeoutTicks = 500)
    public static void selectingCoreClosesModuleConfiguration(GameTestHelper helper) {
        StationFixture fixture = new StationFixture(true);
        openConfiguration(helper, fixture).step("select station core with configuration open")
            .click(ClientTarget.of("station core", c -> fixture.tileTarget(StationTileCoord.of(0, 0))))
            .awaitClient("module configuration closed after retarget", c -> {
                if (!StationTileCoord.of(0, 0)
                    .equals(uniqueWidget(StationMapWidget.class).selection())) {
                    throw new AssertionError("Station core was not selected");
                }
                if (!TreeUtil.flatListByType(requireScreen(STATION_SCREEN).getMainPanel(), DropdownWidget.class)
                    .isEmpty()) throw new AssertionError("Module configuration remains open for the core");
            })
            .step("select macerator again")
            .click(ClientTarget.of("macerator tile", c -> fixture.tileTarget(MACERATOR_TILE)))
            .awaitClient("macerator selected again", c -> fixture.assertMaceratorSelected())
            .step("configure after retarget")
            .click(control("CONFIG"))
            .awaitClient("configuration remains usable", c -> requiredWidget("recipe.details.0"))
            .succeed();
    }

    /** Bug regression: a window resize must not turn a pending Close click into an unintended Save. */
    @GameTest(timeoutTicks = 600)
    public static void resizingDuringCloseDoesNotSaveDraft(GameTestHelper helper) {
        StationFixture fixture = new StationFixture(true, 2);
        openConfiguration(helper, fixture).step("modify recipe draft")
            .click(control("recipe.toggle.0"))
            .withinTicks(150)
            .step("close while the window changes size")
            .click(ClientTarget.of("recipe.close during resize", fixture::resizeWhileResolvingClose))
            .awaitClient("resized configuration closed", c -> {
                if (findNamedWidget("recipe.close") != null) throw new AssertionError("Configuration is still open");
                if (Display.getWidth() != 1600 || Display.getHeight() != 900) {
                    throw new AssertionError("Window resize did not take effect");
                }
            })
            .step("reopen after resize")
            .click(control("CONFIG"))
            .awaitClient("reopened recipe available", c -> requiredWidget("recipe.toggle.0"))
            .step("disable original recipe after discarded draft")
            .click(control("recipe.toggle.0"))
            .step("save intentional change")
            .click(control("recipe.save"))
            .awaitServer(
                "only the intentional save changed the recipe",
                () -> fixture.assertRecipeEnabled(false, false))
            .step("capture resized station")
            .capture("station-after-resize")
            .succeed();
    }

    /** Observes NEI's public button event. Clicks still go through the real GuiScreen input path. */
    public static final class NeiButtons {

        private final List<GuiRecipeButton.UpdateRecipeButtonsEvent.Post> pages = new ArrayList<>();
        private RecipeSnapshot selectedRecipe;

        @SubscribeEvent
        public void collect(GuiRecipeButton.UpdateRecipeButtonsEvent.Post event) {
            pages.add(event);
        }

        Point transferPoint() {
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            for (var page : pages) {
                if (page.gui != screen) continue;
                NEIRecipeWidget widget = page.recipeWidget;
                for (GuiRecipeButton button : page.buttonList) {
                    if (!(button instanceof GuiOverlayButton) || !button.enabled || !button.visible) continue;
                    int x = widget.x + button.xPosition + button.width / 2;
                    int y = widget.y + button.yPosition + button.height / 2;
                    if (x < 0 || y < 0 || x >= screen.width || y >= screen.height) continue;
                    if (!(button.handlerRef.handler instanceof GTNEIDefaultHandler handler)) continue;
                    var recipe = ((GTNEIDefaultHandler.CachedDefaultRecipe) handler.arecipes
                        .get(button.handlerRef.recipeIndex)).mRecipe;
                    var catalog = GTRecipeMapId.getRecipes(GTRecipeMapId.MACERATOR);
                    for (int i = 0; i < catalog.length; i++) {
                        if (catalog[i] == recipe) {
                            selectedRecipe = GTRecipeMapId.MACERATOR.snapshot(i, recipe);
                            return new Point(x, y);
                        }
                    }
                }
            }
            throw new AssertionError("No visible transfer button for a registered macerator recipe");
        }
    }

    private static final class StationFixture {

        private final CelestialAsset.ID assetId = CelestialAsset.ID.create();
        private final boolean withRecipe;
        private final int guiScale;
        private final boolean secondMacerator;
        private GuiScreen stationScreen;
        private int originalGuiScale;
        private boolean originalGuiDebug;
        private boolean initialized;
        private String groupName;
        private CompletableFuture<Void> windowResize;

        StationFixture() {
            this(false);
        }

        StationFixture(boolean withRecipe) {
            this(withRecipe, 1);
        }

        StationFixture(boolean withRecipe, int guiScale) {
            this(withRecipe, guiScale, false);
        }

        StationFixture(boolean withRecipe, int guiScale, boolean secondMacerator) {
            this.withRecipe = withRecipe;
            this.guiScale = guiScale;
            this.secondMacerator = secondMacerator;
        }

        void prepare(GameTestHelper helper) {
            EntityPlayerMP player = null;
            for (Object candidate : helper.getWorld().playerEntities) {
                if (candidate instanceof EntityPlayerMP actual) {
                    player = actual;
                    break;
                }
            }
            if (player == null) throw new AssertionError("Integrated client player has not joined");
            UUID teamId = GTTeamsCompat.getTeamData(player)
                .orElseThrow()
                .getTeamId();
            helper.afterTest(() -> AssetStateSync.SERVER.destroyAsset(assetId));
            AutomatedFacility facility = new AutomatedFacility(
                assetId,
                CelestialObjectId.OVERWORLD,
                CelestialAsset.Kind.AUTOMATED_STATION,
                Buildable.Status.OPERATIONAL);
            facility.applyCommand(
                new FacilityCommand.BuildModules(
                    assetId,
                    FacilityModuleKind.MACERATOR,
                    FacilityModuleKind.MACERATOR.defaultShape(),
                    new IModuleComponent.BuildPhysicalSpec.Tier(ModuleTier.HV),
                    null,
                    true,
                    secondMacerator
                        ? List.of(ModulePlacement.at(MACERATOR_TILE), ModulePlacement.at(SECOND_MACERATOR_TILE))
                        : List.of(ModulePlacement.at(MACERATOR_TILE))),
                new FacilityCommand.Authority(true, true));
            if (facility.modules()
                .size() != (secondMacerator ? 2 : 1)) throw new AssertionError("Could not prepare a macerator fixture");
            if (withRecipe) {
                var recipes = GTRecipeMapId.getRecipes(GTRecipeMapId.MACERATOR);
                if (recipes == null) throw new AssertionError("Macerator catalog is unavailable");
                SavedRecipe selected = null;
                for (int index = 0; index < recipes.length; index++) {
                    var recipe = recipes[index];
                    if (!recipe.mHidden && !recipe.mFakeRecipe
                        && recipe.mInputs.length > 0
                        && recipe.mInputs[0] != null
                        && recipe.mInputs[0].getItem() == Item.getItemFromBlock(Blocks.cobblestone)) {
                        selected = new SavedRecipe(
                            GTRecipeMapId.MACERATOR.snapshot(index, recipe),
                            true,
                            0,
                            (byte) 1,
                            (byte) 1);
                        break;
                    }
                }
                if (selected == null) throw new AssertionError("Registered cobblestone macerator recipe is missing");
                RecipeBook defaults = RecipeBook.empty();
                facility.applyCommand(
                    new FacilityCommand.ReplaceRecipeBook(
                        assetId,
                        facility.modules()
                            .getFirst().id,
                        new RecipeBook(List.of(selected), defaults.mode(), defaults.notDoablePolicy())),
                    new FacilityCommand.Authority(true, true));
            }
            CelestialAssetStore.SERVER.registerAssetInternal(teamId, facility);
            AssetStateSync.SERVER.publishFullTo(player.getUniqueID(), assetId);
        }

        AutomatedFacility clientFacility() {
            if (CelestialAssetStore.CLIENT.findAssetInternal(assetId) instanceof AutomatedFacility facility)
                return facility;
            throw new AssertionError("Server station has not reached the client");
        }

        void assertRecipeEnabled(boolean client, boolean enabled) {
            AutomatedFacility facility = client ? clientFacility()
                : (AutomatedFacility) CelestialAssetStore.SERVER.findAssetInternal(assetId);
            RecipeBook book = facility.recipeBook(
                facility.modules()
                    .getFirst());
            if (book.recipes()
                .size() != 1
                || book.recipes()
                    .getFirst()
                    .enabled() != enabled) {
                throw new AssertionError(
                    "Expected one recipe with enabled=" + enabled
                        + ", count="
                        + book.recipes()
                            .size());
            }
        }

        void assertRecipeMatches(boolean client, RecipeSnapshot expected) {
            AutomatedFacility facility = client ? clientFacility()
                : (AutomatedFacility) CelestialAssetStore.SERVER.findAssetInternal(assetId);
            RecipeBook book = facility.recipeBook(
                facility.modules()
                    .getFirst());
            if (expected == null || book.recipes()
                .size() != 1
                || !expected.equals(
                    book.recipes()
                        .getFirst()
                        .recipe())) {
                throw new AssertionError("Saved recipe does not match the selected NEI catalog entry");
            }
        }

        void rememberClientSettings() {
            Minecraft mc = Minecraft.getMinecraft();
            originalGuiScale = mc.gameSettings.guiScale;
            originalGuiDebug = ModularUIConfig.guiDebugMode;
            initialized = true;
        }

        void captureStationScreen() {
            requireScreen(STATION_SCREEN);
            Minecraft mc = Minecraft.getMinecraft();
            stationScreen = mc.currentScreen;
        }

        void cleanup() {
            CelestialAssetStore.CLIENT.destroyAssetInternal(assetId);
            if (initialized) {
                Minecraft.getMinecraft().gameSettings.guiScale = originalGuiScale;
                ModularUIConfig.guiDebugMode = originalGuiDebug;
            }
        }

        void assertReturnedToStation() {
            requireScreen(STATION_SCREEN);
            if (Minecraft.getMinecraft().currentScreen != stationScreen) {
                throw new AssertionError("Recipe editor did not return to its original station screen");
            }
            requiredWidget("recipe.add");
        }

        ClickTarget tileTarget(StationTileCoord coordinate) {
            StationMapWidget map = uniqueWidget(StationMapWidget.class);
            return GuiTestSupport.tileTarget(map, coordinate);
        }

        void assertMaceratorSelected() {
            if (!MACERATOR_TILE.equals(uniqueWidget(StationMapWidget.class).selection())) {
                StationMapWidget map = uniqueWidget(StationMapWidget.class);
                throw new AssertionError(
                    "Macerator tile was not selected by the click, actual=" + map.selection()
                        + ", cursor="
                        + map.getContext()
                            .getAbsMouseX()
                        + ","
                        + map.getContext()
                            .getAbsMouseY());
            }
        }

        void assertGroupCreated(boolean client) {
            AutomatedFacility facility = client ? clientFacility()
                : (AutomatedFacility) CelestialAssetStore.SERVER.findAssetInternal(assetId);
            if (facility.settingsGroups()
                .size() != 1) throw new AssertionError("Expected one saved settings group");
            var group = facility.settingsGroups()
                .iterator()
                .next();
            if (!groupName.equals(group.displayName()) || facility.settingsGroupMembers(group.id())
                .size() != 1) {
                throw new AssertionError("Saved group must contain the macerator and its chosen name");
            }
            assertRecipeEnabled(client, true);
        }

        void assertSharedRecipes(boolean client, boolean enabled) {
            AutomatedFacility facility = client ? clientFacility()
                : (AutomatedFacility) CelestialAssetStore.SERVER.findAssetInternal(assetId);
            if (facility.settingsGroups()
                .size() != 1) throw new AssertionError("Expected one shared settings group");
            var group = facility.settingsGroups()
                .iterator()
                .next();
            if (!groupName.equals(group.displayName()) || facility.settingsGroupMembers(group.id())
                .size() != 2) throw new AssertionError("Both macerators must belong to the created group");
            RecipeSnapshot sharedRecipe = null;
            for (StationTileCoord tile : List.of(MACERATOR_TILE, SECOND_MACERATOR_TILE)) {
                var module = facility.stationLayout()
                    .moduleAt(tile);
                if (module == null || !facility.settingsGroupMembers(group.id())
                    .contains(module)) throw new AssertionError("Expected group member at " + tile);
                RecipeBook book = facility.recipeBook(module);
                if (book.recipes()
                    .size() != 1
                    || book.recipes()
                        .getFirst()
                        .enabled() != enabled)
                    throw new AssertionError(
                        "Shared recipe state differs at " + tile + ", expected enabled=" + enabled);
                RecipeSnapshot recipe = book.recipes()
                    .getFirst()
                    .recipe();
                if (sharedRecipe != null && !sharedRecipe.equals(recipe))
                    throw new AssertionError("Group members resolved different recipes");
                sharedRecipe = recipe;
            }
        }

        void assertDetachedRecipes(boolean client, boolean detachedEnabled, boolean groupRemoved) {
            AutomatedFacility facility = client ? clientFacility()
                : (AutomatedFacility) CelestialAssetStore.SERVER.findAssetInternal(assetId);
            var first = facility.stationLayout()
                .moduleAt(MACERATOR_TILE);
            var second = facility.stationLayout()
                .moduleAt(SECOND_MACERATOR_TILE);
            if (first == null || second == null
                || facility.modules()
                    .size() != 2)
                throw new AssertionError("Leaving a group must retain both macerators");
            if (facility.settingsGroups()
                .size() != (groupRemoved ? 0 : 1))
                throw new AssertionError("Group must exist exactly while it still has a member");
            if (!groupRemoved) {
                var group = facility.settingsGroups()
                    .iterator()
                    .next();
                var members = facility.settingsGroupMembers(group.id());
                if (members.size() != 1 || !members.contains(first))
                    throw new AssertionError("Only the first macerator should remain in the group");
            }
            assertRecipeEnabled(client, true);
            RecipeBook detached = facility.recipeBook(second);
            if (detached.recipes()
                .size() != 1
                || detached.recipes()
                    .getFirst()
                    .enabled() != detachedEnabled
                || !detached.recipes()
                    .getFirst()
                    .recipe()
                    .equals(
                        facility.recipeBook(first)
                            .recipes()
                            .getFirst()
                            .recipe()))
                throw new AssertionError("Detached module must retain the same recipe with independent enabled state");
        }

        ClickTarget saveGroupTarget() {
            IWidget field = findNamedWidget("settings.group.name");
            if (!(field instanceof TextFieldWidget nameField)) return null;
            groupName = nameField.getText();
            return namedTarget("settings.group.save");
        }

        ClickTarget resizeWhileResolvingClose(ClientTest client) {
            if (windowResize != null) {
                if (!windowResize.isDone()) return null;
                windowResize.join();
                return namedTarget("recipe.close");
            }
            ClickTarget target = namedTarget("recipe.close");
            if (target != null) windowResize = client.resizeWindow(1600, 900);
            return target;
        }

    }

    private static ModularScreen requireScreen(String name) {
        ModularScreen screen = ModularScreen.getCurrent();
        if (screen == null || !name.equals(screen.getName())) {
            throw new AssertionError("Expected " + name + ", got " + screen);
        }
        return screen;
    }

    private static <T extends IWidget> T uniqueWidget(Class<T> type) {
        List<T> widgets = TreeUtil
            .flatListByType(requireScreen(STATION_SCREEN).getMainPanel(), type, IWidget::areAncestorsEnabled);
        if (widgets.size() != 1) {
            throw new AssertionError("Expected one visible " + type.getSimpleName() + ", found " + widgets.size());
        }
        return widgets.getFirst();
    }

    private static ClickTarget textTarget(String text) {
        IWidget option = visibleText(text);
        return new ClickTarget(
            option,
            GuiTestSupport.widgetBounds(option),
            () -> option.isValid() && option.areAncestorsEnabled()
                && option.getPanel()
                    .isBelowMouse(option));
    }

    private static IWidget visibleText(String text) {
        List<IWidget> matches = new ArrayList<>();
        for (var panel : requireScreen(STATION_SCREEN).getPanelManager()
            .getOpenPanels()) {
            matches.addAll(
                TreeUtil.flatListBFS(
                    panel,
                    widget -> widget instanceof TextWidget<?>label && widget.areAncestorsEnabled()
                        && text.equals(
                            label.getKey()
                                .get())));
        }
        if (matches.size() != 1)
            throw new AssertionError("Expected one visible label '" + text + "', found " + matches.size());
        return matches.getFirst();
    }

    private static IWidget groupNameOverlay() {
        return requiredWidget("settings.group.name").getParent();
    }

    private static IWidget requiredWidget(String name) {
        IWidget widget = findNamedWidget(name);
        if (widget == null) throw new AssertionError("Active control not found: " + name);
        return widget;
    }

}
