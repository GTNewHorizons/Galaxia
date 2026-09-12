package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.control;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems" })
public final class StarmapGameTests {

    private static final CelestialObjectKey BODY = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);

    private StarmapGameTests() {}

    /** Integration contract: text entry owns keys while focused, and screen shortcuts execute once elsewhere. */
    @GameTest(timeoutTicks = 600)
    public static void typingSearchDoesNotTriggerMapShortcuts(GameTestHelper helper) {
        boolean[] debug = new boolean[1];
        GuiTestSupport.openMap(helper)
            .client(
                "remember debug state",
                c -> debug[0] = GuiTestSupport.map()
                    .isDebugOverlayEnabled())
            .step("focus map search")
            .click(control("starmap.sidebar.search"))
            .step("type b into search")
            .key(Keyboard.KEY_B, 'b')
            .client("text entry does not toggle debug", c -> {
                var search = (TextFieldWidget) GuiTestSupport.findNamedWidget("starmap.sidebar.search");
                if (search == null || !"b".equals(search.getText()))
                    throw new AssertionError("The focused search field did not receive the typed character");
                if (GuiTestSupport.map()
                    .isDebugOverlayEnabled() != debug[0])
                    throw new AssertionError("Typing into search also toggled map debug mode");
            })
            .step("leave the search field")
            .click(control("starmap.toolbar.transfers"))
            .step("use the map debug shortcut")
            .key(Keyboard.KEY_B, 'b')
            .client("shortcut toggles once outside text input", c -> {
                if (GuiTestSupport.map()
                    .isDebugOverlayEnabled() == debug[0])
                    throw new AssertionError("The map shortcut did not toggle debug mode exactly once");
            })
            .succeed();
    }

    /** Integration contract: only gestures started on the orbital scene may pan its camera. */
    @GameTest(timeoutTicks = 1200)
    public static void draggingControlsDoesNotPanUnderlyingMap(GameTestHelper helper) {
        CelestialObjectKey[] initialSelection = new CelestialObjectKey[1];
        Rectangle[] before = new Rectangle[1];
        Point[] dragStart = new Point[1];
        var sequence = GuiTestSupport.openMap(helper)
            .step("pause orbital motion through keyboard")
            .key(Keyboard.KEY_SPACE, ' ')
            .client(
                "remember map selection",
                c -> {
                    initialSelection[0] = GuiTestSupport.map()
                        .getFocusedBody()
                        .key();
                })
            .step("open Assets")
            .click(control("starmap.toolbar.assets"));
        for (String target : List.of("starmap.toolbar.transfers", "starmap.assets.filter")) {
            sequence.client("record planet position before dragging " + target, c -> {
                before[0] = GuiTestSupport.map()
                    .visibleBodyBounds(BODY);
                if (before[0] == null) throw new AssertionError("Reference planet is not visible");
            })
                .step("drag from " + target)
                .drag(control(target))
                .to(c -> {
                    var map = GuiTestSupport.map();
                    Rectangle bounds = map.visibleBodyBounds(BODY);
                    if (bounds == null) throw new AssertionError("Drag destination is not visible");
                    var matrix = LocatedWidget.of(map)
                        .getTransformationMatrix();
                    int x = bounds.x + bounds.width / 2;
                    int y = bounds.y + bounds.height / 2;
                    return new Point(matrix.transformX(x, y), matrix.transformY(x, y));
                })
                .overFrames(8)
                .client("control drag retains map position", c -> {
                    Rectangle after = GuiTestSupport.map()
                        .visibleBodyBounds(BODY);
                    if (after == null || !before[0].equals(after)) throw new AssertionError(
                        "Dragging " + target
                            + " also moved the orbital scene: before="
                            + before[0]
                            + ", after="
                            + after);
                    if (!initialSelection[0].equals(
                        GuiTestSupport.map()
                            .getFocusedBody()
                            .key()))
                        throw new AssertionError("Releasing a control drag selected the planet underneath");
                });
        }
        sequence.step("close Assets")
            .click(control("starmap.toolbar.assets"))
            .client("record planet before map drag", c -> {
                before[0] = GuiTestSupport.map()
                    .visibleBodyBounds(BODY);
                if (before[0] == null) throw new AssertionError("Reference planet is not visible");
            })
            .step("drag empty orbital space")
            .drag(ClientTarget.of("empty orbital space", c -> {
                var map = GuiTestSupport.map();
                int x = map.getArea().width / 2;
                int y = map.getArea().height * 3 / 4;
                if (map.interactableBodyAt(x, y, 0) != null) return null;
                var matrix = LocatedWidget.of(map)
                    .getTransformationMatrix();
                dragStart[0] = new Point(matrix.transformX(x, y), matrix.transformY(x, y));
                return new ClickTarget(
                    map,
                    new Rectangle(dragStart[0].x, dragStart[0].y, 1, 1),
                    () -> map.isValid() && map.getPanel()
                        .getTopHovering() == map);
            }))
            .to(c -> new Point(dragStart[0].x + 80, dragStart[0].y + 45))
            .overFrames(8)
            .client("orbital scene still accepts dragging", c -> {
                Rectangle after = GuiTestSupport.map()
                    .visibleBodyBounds(BODY);
                if (after == null || before[0].getLocation()
                    .distance(after.getLocation()) < 10)
                    throw new AssertionError("Dragging empty orbital space did not pan the map");
            })
            .succeed();
    }

    /** Bug regression: scrolling map controls must not also zoom the orbital scene underneath them. */
    @GameTest(timeoutTicks = 900)
    public static void scrollingControlsDoesNotZoomUnderlyingMap(GameTestHelper helper) {
        double[] zoom = new double[1];
        var sequence = GuiTestSupport.openMap(helper);
        for (String target : List.of("starmap.toolbar.assets", "starmap.assets.filter")) {
            sequence.client(
                "record zoom before scrolling " + target,
                c -> zoom[0] = GuiTestSupport.map()
                    .getDisplayZoomMultiplier())
                .step("scroll " + target)
                .scroll(control(target), 120)
                .client("controls retain map zoom", c -> {
                    if (Double.compare(
                        zoom[0],
                        GuiTestSupport.map()
                            .getDisplayZoomMultiplier())
                        != 0) throw new AssertionError("Scrolling " + target + " also zoomed the underlying map");
                })
                .step("toggle assets panel")
                .click(control("starmap.toolbar.assets"));
        }
        sequence.awaitClient("assets panel closed", c -> {
            if (GuiTestSupport.map()
                .isAssetsPanelOpen()) throw new AssertionError("Assets panel is still open");
            zoom[0] = GuiTestSupport.map()
                .getDisplayZoomMultiplier();
        })
            .step("scroll orbital scene")
            .scroll(ClientTarget.of("Overworld", c -> GuiTestSupport.bodyTarget(BODY)), 120)
            .client("scene still accepts zoom input", c -> {
                if (GuiTestSupport.map()
                    .getDisplayZoomMultiplier() <= zoom[0])
                    throw new AssertionError("Scrolling the orbital scene did not zoom in");
            })
            .succeed();
    }

    /** Bug regression: resizing an open map preserves selection, open panels and usable scaled controls. */
    @GameTest(timeoutTicks = 1200)
    public static void mapRetainsSelectionAndPanelsAcrossWindowResize(GameTestHelper helper) {
        var sequence = GuiTestSupport.openMap(helper, 5)
            .step("select Overworld before resize")
            .click(ClientTarget.of("Overworld", c -> GuiTestSupport.bodyTarget(BODY)))
            .awaitClient("selection ready before resize", c -> {
                var selected = GuiTestSupport.map()
                    .getFocusedBody();
                if (selected == null || !BODY.equals(selected.key())) throw new AssertionError("Selection unavailable");
            })
            .step("open signals before resize")
            .click(control("starmap.toolbar.signals"));
        for (int[] dimensions : List.of(new int[] { 2400, 1280 }, new int[] { 1281, 721 })) {
            sequence.step("resize open map to " + dimensions[0] + "x" + dimensions[1])
                .withinTicks(150)
                .resizeWindow(dimensions[0], dimensions[1])
                .withinTicks(150)
                .awaitClient("selection and panels survive resize", c -> {
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc.displayWidth != dimensions[0] || mc.displayHeight != dimensions[1])
                        throw new AssertionError("Client framebuffer is " + mc.displayWidth + "x" + mc.displayHeight);
                    var map = GuiTestSupport.map();
                    if (map == null || map.getFocusedBody() == null
                        || !BODY.equals(
                            map.getFocusedBody()
                                .key()))
                        throw new AssertionError("Resizing discarded the selected body");
                    if (!map.isSignalsOpen()) throw new AssertionError("Resizing closed the signals panel");
                    if (mc.gameSettings.guiScale != 5) throw new AssertionError("Map changed the global GUI scale");
                    var details = GuiTestSupport.widgetBounds(map.createPinnedInfoWidget());
                    var canvas = GuiTestSupport.widgetBounds(map);
                    if (details.isEmpty() || !canvas.contains(details) || details.x < canvas.getCenterX())
                        throw new AssertionError("Body details must remain visible on the right of the map");
                })
                .step("close signals after resize")
                .click(control("starmap.toolbar.signals"))
                .awaitClient(
                    "resized toolbar remains usable",
                    c -> {
                        if (GuiTestSupport.map()
                            .isSignalsOpen()) throw new AssertionError("Resized toolbar click failed");
                    })
                .step("reopen signals after resize")
                .click(control("starmap.toolbar.signals"));
        }
        sequence.succeed();
    }

    /** Bug regression: large global GUI scale leaves map controls and orbital selection usable. */
    @GameTest(timeoutTicks = 600)
    public static void mapRemainsUsableAtGuiScaleFour(GameTestHelper helper) {
        verifyLargeGuiScale(helper, 4);
    }

    /** Bug regression: the map fits independently without rewriting the player's global scale setting. */
    @GameTest(timeoutTicks = 600)
    public static void mapRemainsUsableAtGuiScaleFive(GameTestHelper helper) {
        verifyLargeGuiScale(helper, 5);
    }

    private static void verifyLargeGuiScale(GameTestHelper helper, int scale) {
        GuiTestSupport.openMap(helper, scale)
            .awaitClient("full toolbar reachable", c -> {
                for (String control : List.of("signals", "transfers", "satelliteLinks", "assets", "clickMode")) {
                    if (GuiTestSupport.namedTarget("starmap.toolbar." + control) == null)
                        throw new AssertionError("Toolbar control unavailable at GUI scale " + scale + ": " + control);
                }
                if (Minecraft.getMinecraft().gameSettings.guiScale != scale)
                    throw new AssertionError("Map changed the global GUI scale");
            })
            .awaitClient("NEI controls do not cover map controls", c -> {
                if (!NEIClientConfig.isEnabled() || NEIClientConfig.isHidden())
                    throw new AssertionError("NEI must be globally visible to verify screen-specific exclusion");
                var nei = new LayoutManager();
                for (var control : List.of(LayoutManager.options, LayoutManager.bookmarksButton)) {
                    if (nei.getWidgetUnderMouse(control.x + control.w / 2, control.y + control.h / 2) == control)
                        throw new AssertionError("NEI control remains interactive over the star map");
                }
            })
            .step("open signals")
            .click(control("starmap.toolbar.signals"))
            .awaitClient(
                "signals panel opened",
                c -> {
                    if (!GuiTestSupport.map()
                        .isSignalsOpen()) throw new AssertionError("Signals panel did not open");
                })
            .step("close signals")
            .click(control("starmap.toolbar.signals"))
            .awaitClient(
                "signals panel closed",
                c -> {
                    if (GuiTestSupport.map()
                        .isSignalsOpen()) throw new AssertionError("Signals panel did not close");
                })
            .step("select scaled orbital body")
            .click(ClientTarget.of("Overworld", c -> GuiTestSupport.bodyTarget(BODY)))
            .awaitClient("scaled input selects correct body", c -> {
                var selected = GuiTestSupport.map()
                    .getFocusedBody();
                if (selected == null || !BODY.equals(selected.key()))
                    throw new AssertionError("Scaled input selected the wrong body");
                if (Minecraft.getMinecraft().gameSettings.guiScale != scale)
                    throw new AssertionError("Map interactions changed the global GUI scale");
            })
            .step("capture independent map scale")
            .capture("map-global-scale-" + scale)
            .succeed();
    }

    /** Bug regression: dismissing a context menu must not select the body underneath the same click. */
    @GameTest(timeoutTicks = 600)
    public static void selectsBodyFromLiveOrbitalFrame(GameTestHelper helper) {
        GuiTestSupport.openMap(helper)
            .step("open Mars context menu")
            .rightClick(
                ClientTarget.of(
                    "Mars context",
                    c -> GuiTestSupport.bodyTarget(CelestialObjectKey.registered(CelestialObjectId.MARS), 1)))
            .awaitClient("context menu open before dismissal", c -> {
                if (GuiTestSupport.findNamedWidget("starmap.context.menu") == null)
                    throw new AssertionError("Context menu is not open");
                if (BODY.equals(
                    GuiTestSupport.map()
                        .getFocusedBody()
                        .key()))
                    throw new AssertionError("Overworld is already selected before dismissal");
            })
            .step("dismiss context menu over Overworld without selecting it")
            .click(// Right-button targeting permits a body outside the open menu. The actual input is a left click.
                ClientTarget.of("dismiss over Overworld", c -> GuiTestSupport.bodyTarget(BODY, 1)))
            .client("dismissal leaves selection unchanged", c -> {
                if (GuiTestSupport.findNamedWidget("starmap.context.menu") != null)
                    throw new AssertionError("Context menu remains open after dismissal");
                if (BODY.equals(
                    GuiTestSupport.map()
                        .getFocusedBody()
                        .key()))
                    throw new AssertionError("Menu dismissal selected the body underneath it");
            })
            .withinTicks(200)
            .step("select Overworld from current rendered bounds")
            .click(ClientTarget.of("orbital body Overworld", c -> GuiTestSupport.bodyTarget(BODY)))
            .awaitClient("Overworld selected by real input", c -> {
                var map = GuiTestSupport.map();
                if (map == null || map.getFocusedBody() == null
                    || !BODY.equals(
                        map.getFocusedBody()
                            .key())) {
                    throw new AssertionError("Overworld was not selected");
                }
            })
            .step("capture selected orbital body")
            .capture("starmap-selected-overworld")
            .succeed();
    }

    /** Integration contract: GUI confirmation creates a station site and cancellation removes it on both peers. */
    @GameTest(timeoutTicks = 800)
    public static void createsAndCancelsStationThroughGui(GameTestHelper helper) {
        AssetFixture fixture = new AssetFixture();
        var sequence = ClientTest.scenario(helper)
            .server("remember existing assets", () -> fixture.prepare(helper, false));
        GuiTestSupport.manageBody(GuiTestSupport.openMap(sequence, helper), BODY)
            .step("choose automated station")
            .click(control("asset.create.AUTOMATED_STATION"))
            .step("cancel station confirmation")
            .click(control("asset.cancel"))
            .step("choose automated station again")
            .click(control("asset.create.AUTOMATED_STATION"))
            .step("confirm station construction")
            .click(control("asset.confirm"))
            .awaitServer("exactly one station site created", () -> fixture.assertCreated(false, null))
            .awaitClient("station site synchronized", c -> fixture.assertCreated(true, null))
            .step("capture GUI-created station site")
            .capture("gui-created-station")
            .step("cancel empty station construction through its row")
            .click(
                ClientTarget.of(
                    "station construction action",
                    c -> GuiTestSupport.namedTarget("asset.constructionAction." + fixture.created.getFirst())))
            .awaitServer("station construction removed on server", () -> fixture.assertRemoved(false))
            .awaitClient("station removal synchronized and row removed", c -> {
                fixture.assertRemoved(true);
                if (GuiTestSupport.findNamedWidget("asset.constructionAction." + fixture.created.getFirst()) != null)
                    throw new AssertionError("Cancelled station row remains visible");
                GuiTestSupport.requireScreen("galactic_orbital_map");
            })
            .succeed();
    }

    /** Integration contract: GUI-created satellites require confirmation to delete and preserve the other kind. */
    @GameTest(timeoutTicks = 800)
    public static void createsAndDeletesSatellitesThroughGui(GameTestHelper helper) {
        AssetFixture fixture = new AssetFixture();
        var sequence = ClientTest.scenario(helper)
            .server("prepare creative player prerequisites", () -> {
                fixture.prepare(helper, true);
                fixture.assertSatellites(false, Set.of());
            });
        GuiTestSupport.openMap(sequence, helper)
            .awaitClient("creative controls available", c -> {
                if (GuiTestSupport.sidebarDebugTarget() == null)
                    throw new AssertionError("Creative button is not available");
                if (GuiTestSupport.map()
                    .isCreativeBuildModeEnabled()) throw new AssertionError("Expected debug build mode off");
                c.afterTest(() -> {
                    var map = GuiTestSupport.map();
                    if (map != null && map.isCreativeBuildModeEnabled()) map.toggleCreativeBuildMode();
                });
            })
            .step("enable creative build through Debug button")
            .click(ClientTarget.of("sidebar.debug", c -> GuiTestSupport.sidebarDebugTarget()))
            .awaitClient("creative build enabled", c -> {
                if (!GuiTestSupport.map()
                    .isCreativeBuildModeEnabled()) throw new AssertionError("Debug click did not enable building");
            })
            .withinTicks(200)
            .step("open satellite context menu")
            .rightClick(ClientTarget.of("body context", c -> GuiTestSupport.bodyTarget(BODY)));
        for (SatelliteKind kind : SatelliteKind.values()) {
            String control = "starmap.context.ADD_" + kind.name() + "_SATELLITE";
            sequence.step("add " + kind + " satellite through menu")
                .click(control(control))
                .awaitServer(kind + " satellite created", () -> fixture.assertCreated(false, kind))
                .awaitClient(kind + " satellite synchronized", c -> fixture.assertCreated(true, kind));
        }
        sequence.step("disable creative build through Debug button")
            .click(ClientTarget.of("sidebar.debug", c -> GuiTestSupport.sidebarDebugTarget()))
            .awaitClient(
                "creative build disabled",
                c -> {
                    if (GuiTestSupport.map()
                        .isCreativeBuildModeEnabled()) throw new AssertionError("Debug mode remains enabled");
                })
            .step("capture GUI-created satellites")
            .capture("gui-created-satellites");
        GuiTestSupport.manageBody(sequence, BODY)
            .step("open Satellites tab")
            .click(control("asset.tab.SATELLITES"));
        Set<SatelliteKind> remaining = EnumSet.allOf(SatelliteKind.class);
        for (SatelliteKind kind : SatelliteKind.values()) {
            Set<SatelliteKind> before = Set.copyOf(remaining);
            remaining.remove(kind);
            Set<SatelliteKind> after = Set.copyOf(remaining);
            int amount = remaining.isEmpty() ? 0 : 1;
            String control = "asset.satellite.delete." + kind + "." + amount;
            sequence.step("arm " + kind + " deletion")
                .click(control(control))
                .server("arming leaves server satellites intact", () -> fixture.assertSatellites(false, before))
                .awaitClient("deletion awaits confirmation", c -> {
                    fixture.assertSatellites(true, before);
                    if (GuiTestSupport.namedTarget(control + ".confirm") == null)
                        throw new AssertionError("Satellite deletion confirmation is not available");
                })
                .step("confirm " + kind + " deletion")
                .click(control(control + ".confirm"))
                .awaitServer("only requested satellite kind removed", () -> fixture.assertSatellites(false, after))
                .awaitClient("satellite removal synchronized", c -> {
                    fixture.assertSatellites(true, after);
                    if (GuiTestSupport.findNamedWidget(control) != null
                        || GuiTestSupport.findNamedWidget(control + ".confirm") != null)
                        throw new AssertionError("Deleted satellite row remains visible");
                });
        }
        sequence.step("capture emptied satellite manager")
            .capture("gui-deleted-satellites")
            .succeed();
    }

    private static final class AssetFixture {

        private final Set<CelestialAsset.ID> previous = new HashSet<>();
        private final List<CelestialAsset.ID> created = new ArrayList<>();
        private UUID team;

        void prepare(GameTestHelper helper, boolean creative) {
            EntityPlayerMP player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            team = GTTeamsCompat.getTeamData(player)
                .orElseThrow()
                .getTeamId();
            for (CelestialAsset asset : CelestialAssetStore.SERVER.assetsViewInternal()) previous.add(asset.assetId);
            boolean originalCreative = player.capabilities.isCreativeMode;
            helper.afterTest(() -> {
                for (CelestialAsset asset : List.copyOf(CelestialAssetStore.SERVER.getStateInternal(team, BODY))) {
                    if (!previous.contains(asset.assetId)) AssetStateSync.SERVER.destroyAsset(asset.assetId);
                }
                player.capabilities.isCreativeMode = originalCreative;
                player.sendPlayerAbilities();
            });
            player.capabilities.isCreativeMode = creative;
            player.sendPlayerAbilities();
        }

        void assertSatellites(boolean client, Set<SatelliteKind> expected) {
            var store = client ? CelestialAssetStore.CLIENT : CelestialAssetStore.SERVER;
            int count = 0;
            for (CelestialAsset asset : store.getStateInternal(team, BODY)) {
                if (!(asset instanceof Satellite satellite)) continue;
                if (!expected.contains(satellite.satelliteKind()) || !created.contains(asset.assetId))
                    throw new AssertionError("Unexpected satellite in test orbit: " + satellite.satelliteKind());
                count++;
            }
            if (count != expected.size()) throw new AssertionError("Unexpected satellite count: " + count);
            for (SatelliteKind kind : expected) assertCreated(client, kind);
        }

        void assertRemoved(boolean client) {
            var store = client ? CelestialAssetStore.CLIENT : CelestialAssetStore.SERVER;
            for (CelestialAsset asset : store.getStateInternal(team, BODY)) {
                if (created.contains(asset.assetId)) throw new AssertionError("Cancelled asset remains present");
            }
        }

        void assertCreated(boolean client, SatelliteKind kind) {
            var store = client ? CelestialAssetStore.CLIENT : CelestialAssetStore.SERVER;
            List<CelestialAsset> matches = new ArrayList<>();
            for (CelestialAsset asset : store.getStateInternal(team, BODY)) {
                if (previous.contains(asset.assetId)) continue;
                if (kind == null ? asset.kind == CelestialAsset.Kind.AUTOMATED_STATION
                    : asset instanceof Satellite satellite && satellite.satelliteKind() == kind) matches.add(asset);
            }
            if (matches.size() != 1) throw new AssertionError("Expected one GUI-created asset, got " + matches.size());
            CelestialAsset asset = matches.getFirst();
            Buildable.Status expected = kind == null ? Buildable.Status.CONSTRUCTION_SITE
                : Buildable.Status.OPERATIONAL;
            if (asset.status() != expected)
                throw new AssertionError("Unexpected GUI-created asset status: " + asset.status());
            if (!client && !created.contains(asset.assetId)) created.add(asset.assetId);
            if (client && !created.contains(asset.assetId))
                throw new AssertionError("Client asset differs from server");
        }
    }
}
