package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.control;

import java.awt.Point;
import java.awt.Rectangle;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.CelestialSidebarWidget;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaWindow;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientScenario;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;

/** Windows of the galactic map: moving them, closing them and opening the full lists behind truncated ones */
@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems" })
public final class StarmapWindowGameTests {

    private static final CelestialObjectKey BODY = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
    private static final String ASSETS = "starmap.panel.assets";
    private static final String SIGNALS = "starmap.panel.signals";
    private static final int DRAG_X = 60;
    private static final int DRAG_Y = 40;

    private StarmapWindowGameTests() {}

    /** A window follows its title bar, ignores drags on its controls and keeps its place while the map is open */
    @GameTest(timeoutTicks = 900)
    public static void windowMovesOnlyByTitleBar(GameTestHelper helper) {
        Rectangle[] before = new Rectangle[1];
        Point[] start = new Point[1];
        GuiTestSupport.openMap(helper)
            .step("open Assets")
            .click(control("starmap.toolbar.assets"))
            .awaitClient("Assets window placed", c -> before[0] = placed(ASSETS))
            .step("drag the Assets title bar")
            .drag(ClientTarget.of("Assets title bar", c -> {
                var target = GuiTestSupport.namedTarget(ASSETS + ".title");
                if (target != null)
                    start[0] = center(GuiTestSupport.widgetBounds(GuiTestSupport.requireWindow(ASSETS + ".title")));
                return target;
            }))
            .to(c -> new Point(start[0].x + DRAG_X, start[0].y + DRAG_Y))
            .overFrames(8)
            .awaitClient("window followed the title bar", c -> {
                Rectangle after = placed(ASSETS);
                if (after.x - before[0].x < DRAG_X / 2 || after.y - before[0].y < DRAG_Y / 2) throw new AssertionError(
                    "Window did not follow its title bar: before=" + before[0] + ", after=" + after);
                before[0] = after;
            })
            .step("drag from a control inside the window")
            .drag(control("starmap.assets.filter"))
            .to(c -> {
                Point from = center(GuiTestSupport.widgetBounds(GuiTestSupport.requireWindow("starmap.assets.filter")));
                return new Point(from.x + DRAG_X, from.y + DRAG_Y);
            })
            .overFrames(8)
            .client("window stayed under a control drag", c -> {
                Rectangle after = placed(ASSETS);
                if (!after.getLocation()
                    .equals(before[0].getLocation()))
                    throw new AssertionError(
                        "Dragging a control moved its window: before=" + before[0] + ", after=" + after);
            })
            .step("close and reopen Assets")
            .click(control("starmap.toolbar.assets"))
            .awaitClient("Assets closed", c -> requireClosed(ASSETS))
            .click(control("starmap.toolbar.assets"))
            .awaitClient("Assets reopened where it was left", c -> {
                Rectangle after = placed(ASSETS);
                if (!after.getLocation()
                    .equals(before[0].getLocation()))
                    throw new AssertionError("Window lost its place: before=" + before[0] + ", after=" + after);
            })
            .capture("starmap-window-moved")
            .succeed();
    }

    /** Esc closes the top window first and the map only when no window can be closed */
    @GameTest(timeoutTicks = 900)
    public static void escapeClosesTopWindowBeforeMap(GameTestHelper helper) {
        GuiTestSupport.openMap(helper)
            .step("open Assets then Signals")
            .click(control("starmap.toolbar.assets"))
            .click(control("starmap.toolbar.signals"))
            .awaitClient("both windows open", c -> {
                placed(ASSETS);
                placed(SIGNALS);
            })
            .escape()
            .awaitClient("Esc closed Signals only", c -> {
                requireClosed(SIGNALS);
                placed(ASSETS);
                GuiTestSupport.requireScreen("galactic_orbital_map");
            })
            .escape()
            .awaitClient("Esc closed Assets only", c -> {
                requireClosed(ASSETS);
                GuiTestSupport.requireScreen("galactic_orbital_map");
            })
            .escape()
            .awaitClient(
                "Esc closed the map",
                c -> {
                    if (Minecraft.getMinecraft().currentScreen != null)
                        throw new AssertionError("Map stayed open with no window left");
                })
            .succeed();
    }

    /** Assets and Signals open side by side instead of on top of each other */
    @GameTest(timeoutTicks = 600)
    public static void assetsAndSignalsDoNotOverlap(GameTestHelper helper) {
        GuiTestSupport.openMap(helper)
            .click(control("starmap.toolbar.assets"))
            .click(control("starmap.toolbar.signals"))
            .awaitClient("windows side by side", c -> {
                Rectangle assets = placed(ASSETS);
                Rectangle signals = placed(SIGNALS);
                if (assets.intersects(signals))
                    throw new AssertionError("Assets " + assets + " overlaps Signals " + signals);
            })
            .capture("starmap-assets-and-signals")
            .succeed();
    }

    /** The ore grid of a body shows its first ores and opens every ore in a window of its own */
    @GameTest(timeoutTicks = 900)
    public static void oreGridOpensEveryOre(GameTestHelper helper) {
        GuiTestSupport.openMap(helper)
            .withinTicks(200)
            .step("select Overworld")
            .click(ClientTarget.of("orbital body Overworld", c -> GuiTestSupport.bodyTarget(BODY)))
            .awaitClient("details show the ore grid", c -> GuiTestSupport.requireWindow("starmap.panel.details"))
            .click(control("starmap.details.ores.showAll"))
            .awaitClient("every ore opened", c -> placed("starmap.details.ores.all"))
            .capture("starmap-every-ore")
            .click(control("starmap.details.ores.showAll"))
            .awaitClient("button closes the window again", c -> requireClosed("starmap.details.ores.all"))
            .succeed();
    }

    /** The transfer planner opens from the sidebar debug tools as a window of its own */
    @GameTest(timeoutTicks = 600)
    public static void transferPlannerOpensFromSidebar(GameTestHelper helper) {
        withDebugTools(GuiTestSupport.openMap(helper)).click(control("starmap.sidebar.simulator"))
            .awaitClient("planner open", c -> placed("transfer.simulator"))
            .capture("starmap-transfer-planner")
            .click(control("transfer.simulator.close"))
            .awaitClient("planner closed", c -> requireClosed("transfer.simulator"))
            .succeed();
    }

    /** The supply debug window closes only through its title bar and keeps its one action readable */
    @GameTest(timeoutTicks = 600)
    public static void supplyDebugWindowHasOneAction(GameTestHelper helper) {
        withDebugTools(GuiTestSupport.openMap(helper)).click(control("starmap.sidebar.supply"))
            .awaitClient("supply window open", c -> placed(CelestialSidebarWidget.SUPPLY_DEBUG_WINDOW))
            .capture("starmap-supply-debug")
            .click(control(CelestialSidebarWidget.SUPPLY_DEBUG_WINDOW + ".close"))
            .awaitClient("supply window closed", c -> requireClosed(CelestialSidebarWidget.SUPPLY_DEBUG_WINDOW))
            .succeed();
    }

    /** A body that takes neither station nor outpost greys both buttons out and ignores clicks on them */
    @GameTest(timeoutTicks = 600)
    public static void unavailableAssetKindsCannotBeCreated(GameTestHelper helper) {
        CelestialObjectKey sol = CelestialObjectKey.registered(CelestialObjectId.SOL);
        GuiTestSupport.manageBody(GuiTestSupport.openMap(helper), sol)
            .awaitClient("asset manager open", c -> placed("asset.manage"))
            .capture("starmap-unavailable-assets")
            .click(control("asset.create.AUTOMATED_STATION"))
            .click(control("asset.create.AUTOMATED_OUTPOST"))
            .client("no creation dialog opened", c -> requireClosed("asset.dialog"))
            .succeed();
    }

    /** Turns on the sidebar debug tools from a known state and turns them off again after the test */
    private static ClientScenario withDebugTools(ClientScenario scenario) {
        return scenario.client("debug tools start off", c -> {
            setDebugTools(false);
            c.afterTest(() -> setDebugTools(false));
        })
            .click(control("starmap.sidebar.debug"));
    }

    private static void setDebugTools(boolean on) {
        var map = GuiTestSupport.map();
        if (map != null && map.isCreativeBuildModeEnabled() != on) map.toggleCreativeBuildMode();
    }

    /** Screen bounds of an open window once it is on the layer */
    private static Rectangle placed(String id) {
        IWidget window = GuiTestSupport.requireWindow(id);
        if (!((GalaxiaWindow) window).isPlaced()) throw new AssertionError("Window " + id + " is not placed yet");
        Rectangle bounds = GuiTestSupport.widgetBounds(window);
        if (bounds.x < -1000 || bounds.y < -1000) throw new AssertionError("Window " + id + " is not placed yet");
        return bounds;
    }

    private static void requireClosed(String id) {
        if (GuiTestSupport.findNamedWidget(id) != null) throw new AssertionError("Window " + id + " is still open");
    }

    private static Point center(Rectangle bounds) {
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }
}
