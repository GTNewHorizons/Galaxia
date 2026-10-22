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
            .awaitAsync(
                "second window displays station",
                c -> map[0].run(w -> GuiTestSupport.requireScreen("galaxia_station_management")))
            .async(
                "choose an empty station tile",
                c -> map[0].click(0, "empty storage tile", w -> station.emptyTileTarget(StationTileCoord.of(-1, 0))))
            .awaitAsync(
                "second window displays module picker",
                c -> map[0].run(w -> GuiTestSupport.requireScreen("galaxia_station_module_picker")))
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
                GuiTestSupport.requireScreen("galaxia_station_management");
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
}
