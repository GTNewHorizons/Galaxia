package com.gtnewhorizons.galaxia.gametests;

import java.awt.Rectangle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import com.cleanroommc.modularui.ModularUIConfig;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.utils.TreeUtil;
import com.cleanroommc.modularui.widget.AbstractScrollWidget;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.CelestialSidebarWidget;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.OrbitalView.OrbitalMapWidget;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapWidget;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientScenario;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Real input and live target resolution shared by full GUI journeys. */
final class GuiTestSupport {

    private GuiTestSupport() {}

    static ClientScenario openMap(GameTestHelper helper) {
        return openMap(ClientTest.scenario(helper), helper, 1);
    }

    static ClientScenario openMap(GameTestHelper helper, int guiScale) {
        return openMap(ClientTest.scenario(helper), helper, guiScale);
    }

    static ClientScenario openMap(ClientScenario scenario, GameTestHelper helper) {
        return openMap(scenario, helper, 1);
    }

    static ClientScenario openMap(ClientScenario scenario, GameTestHelper helper, int guiScale) {
        return scenario.server("equip the galactic map", () -> {
            EntityPlayerMP owner = null;
            for (Object candidate : helper.getWorld().playerEntities) {
                if (candidate instanceof EntityPlayerMP player) {
                    owner = player;
                    break;
                }
            }
            if (owner == null) throw new AssertionError("Integrated client player has not joined");
            EntityPlayerMP player = owner;
            int slot = player.inventory.currentItem;
            ItemStack original = player.inventory.getStackInSlot(slot);
            helper.afterTest(() -> {
                player.inventory.setInventorySlotContents(slot, original);
                player.inventoryContainer.detectAndSendChanges();
            });
            player.inventory.setInventorySlotContents(slot, new ItemStack(GalaxiaItemList.ITEM_GALACTIC_MAP.getItem()));
            player.inventoryContainer.detectAndSendChanges();
        })
            .awaitClient("held map synchronized", c -> {
                ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
                if (held == null || held.getItem() != GalaxiaItemList.ITEM_GALACTIC_MAP.getItem()) {
                    throw new AssertionError("Galactic map has not reached the client");
                }
            })
            .client("prepare GUI settings and diagnostics", c -> {
                Minecraft mc = Minecraft.getMinecraft();
                PauseTrace trace = new PauseTrace();
                MinecraftForge.EVENT_BUS.register(trace);
                c.afterTest(() -> MinecraftForge.EVENT_BUS.unregister(trace));
                int scale = mc.gameSettings.guiScale;
                boolean debug = ModularUIConfig.guiDebugMode;
                c.afterTest(() -> {
                    mc.gameSettings.guiScale = scale;
                    ModularUIConfig.guiDebugMode = debug;
                });
                mc.gameSettings.guiScale = guiScale;
                ModularUIConfig.guiDebugMode = false;
            })
            .useHeldItem()
            .awaitClient("galactic map opened after item use", c -> requireScreen("galactic_orbital_map"));
    }

    public static final class PauseTrace {

        @SubscribeEvent
        public void opened(GuiOpenEvent event) {
            if (!(event.gui instanceof GuiIngameMenu)) return;
            Minecraft mc = Minecraft.getMinecraft();
            Galaxia.LOG.warn(
                "[Horizon gui] Unexpected pause, previous="
                    + (mc.currentScreen == null ? "none"
                        : mc.currentScreen.getClass()
                            .getName())
                    + ", scale="
                    + mc.gameSettings.guiScale
                    + ", active="
                    + Display.isActive()
                    + ", focused="
                    + mc.inGameHasFocus
                    + ", pauseOnLostFocus="
                    + mc.gameSettings.pauseOnLostFocus,
                new Throwable("Pause opening call path"));
        }
    }

    static ClientScenario manageBody(ClientScenario scenario, CelestialObjectKey body) {
        return scenario.withinTicks(200)
            .rightClick(ClientTarget.of("body " + body, c -> bodyTarget(body)))
            .click(control("starmap.context.MANAGE_ASSETS"));
    }

    static ClientScenario openFacility(ClientScenario scenario, GameTestHelper helper, CelestialAsset.ID assetId,
        CelestialObjectKey body) {
        return openFacility(scenario, helper, assetId, body, 1);
    }

    static ClientScenario openFacility(ClientScenario scenario, GameTestHelper helper, CelestialAsset.ID assetId,
        CelestialObjectKey body, int guiScale) {
        return manageBody(openMap(scenario, helper, guiScale), body).click(control("asset.manage." + assetId))
            .awaitClient("station management opened", c -> requireScreen("galaxia_station_management"));
    }

    static ModularScreen requireScreen(String name) {
        ModularScreen screen = ModularScreen.getCurrent();
        if (screen == null || !name.equals(
            screen.getMainPanel()
                .getName())) {
            throw new AssertionError("Expected screen " + name);
        }
        return screen;
    }

    static OrbitalMapWidget map() {
        ModularScreen screen = ModularScreen.getCurrent();
        if (screen == null) return null;
        var maps = TreeUtil.flatListByType(screen.getMainPanel(), OrbitalMapWidget.class);
        if (maps.size() > 1) throw new AssertionError("Ambiguous active orbital map");
        return maps.isEmpty() ? null : maps.getFirst();
    }

    static ClickTarget bodyTarget(CelestialObjectKey key) {
        return bodyTarget(key, 0);
    }

    static ClickTarget bodyTarget(CelestialObjectKey key, int mouseButton) {
        OrbitalMapWidget map = map();
        if (map == null) return null;
        Rectangle local = map.visibleBodyBounds(key);
        if (local == null) return null;
        var matrix = LocatedWidget.of(map)
            .getTransformationMatrix();
        int left = matrix.transformX(local.x, local.y);
        int top = matrix.transformY(local.x, local.y);
        Rectangle absolute = new Rectangle(
            left,
            top,
            matrix.transformX(local.x + local.width, local.y + local.height) - left,
            matrix.transformY(local.x + local.width, local.y + local.height) - top);
        return new ClickTarget(
            key,
            absolute,
            () -> map.isValid() && map.areAncestorsEnabled()
                && map.getPanel()
                    .getTopHovering() == map
                && key.equals(
                    map.interactableBodyAt(
                        matrix.unTransformX(
                            map.getContext()
                                .getAbsMouseX(),
                            map.getContext()
                                .getAbsMouseY()),
                        matrix.unTransformY(
                            map.getContext()
                                .getAbsMouseX(),
                            map.getContext()
                                .getAbsMouseY()),
                        mouseButton)));
    }

    static ClickTarget tileTarget(StationMapWidget map, StationTileCoord coordinate) {
        Rectangle local = map.tileBounds(coordinate);
        var matrix = LocatedWidget.of(map)
            .getTransformationMatrix();
        int x = matrix.transformX(local.x, local.y);
        int y = matrix.transformY(local.x, local.y);
        Rectangle bounds = new Rectangle(
            x,
            y,
            matrix.transformX(local.x + local.width, local.y + local.height) - x,
            matrix.transformY(local.x + local.width, local.y + local.height) - y);
        return new ClickTarget(
            coordinate,
            bounds,
            () -> map.isValid() && map.areAncestorsEnabled()
                && map.getPanel()
                    .isBelowMouse(map)
                && coordinate.equals(
                    map.interactableTileAt(
                        matrix.unTransformX(
                            map.getContext()
                                .getAbsMouseX(),
                            map.getContext()
                                .getAbsMouseY()),
                        matrix.unTransformY(
                            map.getContext()
                                .getAbsMouseX(),
                            map.getContext()
                                .getAbsMouseY()))));
    }

    static ClickTarget contextMenuDismissTarget() {
        OrbitalMapWidget map = map();
        IWidget menuWidget = findNamedWidget("starmap.context.menu");
        if (map == null || menuWidget == null) return null;
        Rectangle menu = widgetBounds(menuWidget);
        menu.grow(2, 2);
        Rectangle area = widgetBounds(map);
        for (int quarter : new int[] { 1, 3 }) {
            Rectangle point = new Rectangle(area.x + area.width * quarter / 4, area.y + area.height - 16, 1, 1);
            if (!menu.intersects(point)) {
                return new ClickTarget(
                    "map.dismissContext",
                    point,
                    () -> map.isValid() && map.getPanel()
                        .isBelowMouse(map));
            }
        }
        return null;
    }

    static ClickTarget sidebarDebugTarget() {
        ModularScreen screen = ModularScreen.getCurrent();
        if (screen == null) return null;
        var sidebars = TreeUtil.flatListByType(screen.getMainPanel(), CelestialSidebarWidget.class);
        if (sidebars.size() != 1) return null;
        CelestialSidebarWidget sidebar = sidebars.getFirst();
        Rectangle bounds = sidebar.creativeButtonBounds();
        if (bounds == null || bounds.isEmpty()) return null;
        var matrix = LocatedWidget.of(sidebar)
            .getTransformationMatrix();
        bounds.translate(matrix.transformX(0, 0), matrix.transformY(0, 0));
        return new ClickTarget(
            "sidebar.debug",
            bounds,
            () -> sidebar.isValid() && sidebar.areAncestorsEnabled()
                && sidebar.getPanel()
                    .isBelowMouse(sidebar));
    }

    static IWidget findNamedWidget(String name) {
        ModularScreen screen = ModularScreen.getCurrent();
        if (screen == null) return null;
        IWidget match = null;
        for (var panel : screen.getPanelManager()
            .getOpenPanels()) {
            for (IWidget widget : TreeUtil
                .flatListBFS(panel, w -> name.equals(w.getName()) && w.isValid() && w.areAncestorsEnabled())) {
                if (match != null) throw new AssertionError("Ambiguous active control: " + name);
                match = widget;
            }
        }
        return match;
    }

    static ClientTarget control(String name) {
        return ClientTarget.of(name, c -> namedTarget(name));
    }

    static ClickTarget namedTarget(String name) {
        IWidget widget = findNamedWidget(name);
        if (widget == null) return null;
        Rectangle bounds = widgetBounds(widget);
        for (IWidget parent = widget.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof AbstractScrollWidget<?, ?>) bounds = bounds.intersection(widgetBounds(parent));
            if (parent == widget.getPanel()) break;
        }
        if (bounds.isEmpty()) return null;
        return new ClickTarget(
            widget,
            bounds,
            () -> widget.isValid() && widget.areAncestorsEnabled()
                && widget.getPanel()
                    .isBelowMouse(widget));
    }

    static void resizeWindow(int width, int height) {
        try {
            Display.setDisplayMode(new DisplayMode(width, height));
        } catch (LWJGLException failure) {
            throw new AssertionError("Could not resize the test window", failure);
        }
    }

    static Rectangle widgetBounds(IWidget widget) {
        var matrix = LocatedWidget.of(widget)
            .getTransformationMatrix();
        int localX = widget instanceof AbstractScrollWidget<?, ?>scroll ? scroll.getScrollX() : 0;
        int localY = widget instanceof AbstractScrollWidget<?, ?>scroll ? scroll.getScrollY() : 0;
        int x = matrix.transformX(localX, localY);
        int y = matrix.transformY(localX, localY);
        return new Rectangle(
            x,
            y,
            matrix.transformX(localX + widget.getArea().width, localY + widget.getArea().height) - x,
            matrix.transformY(localX + widget.getArea().width, localY + widget.getArea().height) - y);
    }
}
