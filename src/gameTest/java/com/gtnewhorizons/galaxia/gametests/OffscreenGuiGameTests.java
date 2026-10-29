package com.gtnewhorizons.galaxia.gametests;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.InventoryBasic;

import com.cleanroommc.modularui.ModularUIConfig;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticMapWidget;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.OffscreenStarmapGui;
import com.gtnewhorizons.galaxia.client.gui.station.ModulePickerScreen;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.api.client.ClientWindow;
import com.sbancuz.offscreen.api.OffscreenAPI;
import com.sbancuz.offscreen.window.Window;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems", "offscreen" })
public final class OffscreenGuiGameTests {

    private OffscreenGuiGameTests() {}

    /** A paid build in the second window must leave the first window's real server container open. */
    @GameTest(timeoutTicks = 1800)
    public static void buildModuleWhileChestRemainsOpen(GameTestHelper helper) {
        ModuleConstructionGuiGameTests.Fixture station = new ModuleConstructionGuiGameTests.Fixture();
        CelestialObjectKey body = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        ClientWindow[] map = new ClientWindow[1];
        EntityPlayerMP[] player = new EntityPlayerMP[1];
        Container[] chest = new Container[1];
        ClientTest.scenario(helper)
            .server("prepare station and paid build materials", () -> {
                station.prepare(helper);
                player[0] = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
                helper.afterTest(() -> player[0].closeScreen());
            })
            .client("open the optional client-only map", c -> {
                Minecraft mc = Minecraft.getMinecraft();
                int originalScale = mc.gameSettings.guiScale;
                boolean originalDebug = ModularUIConfig.guiDebugMode;
                c.afterTest(() -> {
                    mc.gameSettings.guiScale = originalScale;
                    ModularUIConfig.guiDebugMode = originalDebug;
                    CelestialAssetStore.CLIENT.destroyAssetInternal(station.assetId);
                });
                mc.gameSettings.guiScale = 1;
                ModularUIConfig.guiDebugMode = false;
                Window opened = OffscreenStarmapGui.toggle();
                if (opened == null) throw new AssertionError("Offscreen map did not create a native window");
                c.afterTest(() -> OffscreenAPI.close(opened));
                map[0] = c.offscreenWindow(opened.getId());
            })
            .withinTicks(300)
            .awaitAsync("map and station synchronized", c -> map[0].run(w -> {
                GuiTestSupport.requireScreen(GalacticMapWidget.SCREEN_NAME);
                station.facility(true);
            }))
            .async("size map independently of the game window", c -> map[0].resize(1280, 800))
            .async(
                "position camera preparation over Sol",
                c -> map[0].scroll(
                    120,
                    "Sol",
                    w -> GuiTestSupport.bodyTarget(CelestialObjectKey.registered(CelestialObjectId.SOL))))
            .async("frame the inner Solar System", c -> map[0].run(w -> GuiTestSupport.zoomInnerSolarSystem()))
            .server("open a real server-owned chest container", () -> {
                player[0].displayGUIChest(new InventoryBasic("Offscreen coexistence", true, 9));
                chest[0] = player[0].openContainer;
                if (!(chest[0] instanceof ContainerChest)) throw new AssertionError("Server did not open a chest");
            })
            .awaitClient(
                "main window displays the server chest",
                c -> c.mainWindow()
                    .screen(GuiChest.class))
            .async(
                "capture main chest",
                c -> c.mainWindow()
                    .capture("main-chest"))
            .async("capture independent map", c -> map[0].capture("offscreen-map"))
            .async(
                "select body in the second window",
                c -> map[0].click(1, "Overworld", w -> GuiTestSupport.bodyTarget(body, 1)))
            .async("capture body context menu", c -> map[0].capture("offscreen-body-menu"))
            .async(
                "manage body assets",
                c -> map[0].click(0, "manage assets", w -> GuiTestSupport.namedTarget("starmap.context.MANAGE_ASSETS")))
            .async(
                "open station in the second window",
                c -> map[0].click(0, "station", w -> GuiTestSupport.namedTarget("asset.manage." + station.assetId)))
            .awaitAsync("second window displays station", c -> map[0].run(w -> GuiTestSupport.requireStation()))
            .async(
                "choose an empty station tile",
                c -> map[0].click(0, "empty storage tile", w -> station.emptyTileTarget(StationTileCoord.of(-1, 0))))
            .awaitAsync(
                "second window displays module picker",
                c -> map[0].run(w -> GuiTestSupport.requireWindow(ModulePickerScreen.WINDOW_ID)))
            .async(
                "choose storage",
                c -> map[0].click(0, "STORAGE", w -> GuiTestSupport.namedTarget("module.kind.STORAGE")))
            .async("choose HV tier", c -> map[0].click(0, "HV", w -> GuiTestSupport.namedTarget("module.tier.HV")))
            .async(
                "order paid construction",
                c -> map[0].click(0, "build", w -> GuiTestSupport.namedTarget("module.build")))
            .awaitServer("server accepted only the selected paid build and kept chest open", () -> {
                station.assertBuilt(false, 1, false, 1);
                station.assertDraftOutcome(false, true);
                if (player[0].openContainer != chest[0])
                    throw new AssertionError("Offscreen action replaced the server chest container");
            })
            .awaitAsync("build and payment synchronized in second window", c -> map[0].run(w -> {
                GuiTestSupport.requireStation();
                station.assertDraftOutcome(true, true);
            }))
            .awaitClient(
                "main chest survived station navigation",
                c -> c.mainWindow()
                    .screen(GuiChest.class))
            .async("resize second window with station open", c -> map[0].resize(1440, 900))
            .async("capture built station", c -> map[0].capture("offscreen-station-built"))
            .async(
                "capture preserved main chest",
                c -> c.mainWindow()
                    .capture("main-chest-after-build"))
            .async("close second window", c -> map[0].close())
            .client("closing second window leaves main GUI intact", c -> {
                if (map[0].isOpen()) throw new AssertionError("Offscreen window remained open");
                c.mainWindow()
                    .screen(GuiChest.class);
            })
            .server("closing second window leaves server container intact", () -> {
                if (player[0].openContainer != chest[0])
                    throw new AssertionError("Closing Offscreen closed the chest container");
                station.assertDraftOutcome(false, true);
            })
            .succeed();
    }

    @GameTest(timeoutTicks = 1200)
    public static void assetManagerRefreshesWithoutReopening(GameTestHelper helper) {
        ModuleConstructionGuiGameTests.Fixture station = new ModuleConstructionGuiGameTests.Fixture();
        CelestialObjectKey body = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        ClientWindow[] map = new ClientWindow[1];
        CelestialAsset.ID[] created = new CelestialAsset.ID[1];
        java.util.UUID[] team = new java.util.UUID[1];
        ClientTest.scenario(helper)
            .server("prepare a station for live list changes", () -> {
                station.prepare(helper);
                team[0] = CelestialAssetStore.getTeamId(station.assetId);
                helper.afterTest(() -> { if (created[0] != null) AssetStateSync.SERVER.destroyAsset(created[0]); });
            })
            .client("open the independent map", c -> {
                Minecraft mc = Minecraft.getMinecraft();
                int scale = mc.gameSettings.guiScale;
                c.afterTest(() -> mc.gameSettings.guiScale = scale);
                mc.gameSettings.guiScale = 1;
                Window opened = OffscreenStarmapGui.toggle();
                if (opened == null) throw new AssertionError("Offscreen map did not open");
                c.afterTest(() -> OffscreenAPI.close(opened));
                map[0] = c.offscreenWindow(opened.getId());
            })
            .withinTicks(300)
            .awaitAsync("map has synchronized station data", c -> map[0].run(w -> {
                GuiTestSupport.requireScreen(GalacticMapWidget.SCREEN_NAME);
                station.facility(true);
            }))
            .async("prepare the map viewport", c -> map[0].resize(1280, 800))
            .async(
                "position the camera over Sol",
                c -> map[0].scroll(
                    120,
                    "Sol",
                    w -> GuiTestSupport.bodyTarget(CelestialObjectKey.registered(CelestialObjectId.SOL))))
            .async("frame the inner Solar System", c -> map[0].run(w -> GuiTestSupport.zoomInnerSolarSystem()))
            .async("select Overworld", c -> map[0].click(1, "Overworld", w -> GuiTestSupport.bodyTarget(body, 1)))
            .async(
                "open the live asset list",
                c -> map[0].click(0, "manage assets", w -> GuiTestSupport.namedTarget("starmap.context.MANAGE_ASSETS")))
            .awaitAsync("station row is visible", c -> map[0].run(w -> {
                if (GuiTestSupport.namedTarget("asset.manage." + station.assetId) == null)
                    throw new AssertionError("Station row is not visible");
            }))
            .async(
                "request station destruction",
                c -> map[0]
                    .click(0, "destroy station", w -> GuiTestSupport.namedTarget("asset.destroy." + station.assetId)))
            .async(
                "arm station destruction",
                c -> map[0].click(0, "arm destruction", w -> GuiTestSupport.namedTarget("asset.confirm")))
            .async(
                "confirm station destruction",
                c -> map[0].click(0, "confirm destruction", w -> GuiTestSupport.namedTarget("asset.confirm")))
            .awaitClient("removal reached the client store", c -> {
                if (CelestialAssetStore.CLIENT.findAssetInternal(station.assetId) != null)
                    throw new AssertionError("Client still has the removed station");
            })
            .awaitAsync("open list removes the row", c -> map[0].run(w -> {
                if (GuiTestSupport.findNamedWidget("asset.manage." + station.assetId) != null)
                    throw new AssertionError("Removed station is still visible in the open list");
            }))
            .async(
                "request a new station",
                c -> map[0]
                    .click(0, "create station", w -> GuiTestSupport.namedTarget("asset.create.AUTOMATED_STATION")))
            .async(
                "confirm station creation",
                c -> map[0].click(0, "confirm creation", w -> GuiTestSupport.namedTarget("asset.confirm")))
            .awaitServer("server accepted station creation", () -> {
                var assets = CelestialAssetStore.getState(team[0], body);
                if (assets.isEmpty()) throw new AssertionError("Server has not created the new station");
                created[0] = assets.getFirst().assetId;
            })
            .awaitClient("creation reached the client store", c -> {
                if (CelestialAssetStore.CLIENT.findAssetInternal(created[0]) == null)
                    throw new AssertionError("New station has not reached the client store");
            })
            .awaitAsync("open list adds the construction row", c -> map[0].run(w -> {
                if (GuiTestSupport.namedTarget("asset.constructionAction." + created[0]) == null)
                    throw new AssertionError("New construction row is not visible");
            }))
            .succeed();
    }
}
