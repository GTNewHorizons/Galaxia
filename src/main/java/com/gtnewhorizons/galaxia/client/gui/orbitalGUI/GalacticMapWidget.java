package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.network.ModularNetwork;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class GalacticMapWidget extends ParentWidget<GalacticMapWidget> {

    public static final String SCREEN_NAME = "galactic_orbital_map";

    private static final int SIDEBAR_WIDTH = 216;
    private static final int MINIMUM_CANVAS_WIDTH = 1024;
    private static final int MINIMUM_CANVAS_HEIGHT = 576;

    private float interfaceScale = 1f;
    private final IntSupplier viewportWidth;
    private final IntSupplier viewportHeight;

    private static final int PANEL_INSET = 10;
    private static final int PANEL_TOP = 30;

    private final OrbitalView.OrbitalMapWidget mapWidget;
    private final WindowLayer windows = new WindowLayer(() -> interfaceScale);

    public static void prepareOpen(EntityPlayerMP player) {
        ModularNetwork.SERVER.closeAll(player);
    }

    @SideOnly(Side.CLIENT)
    public static void prepareClientOpen() {
        // A map replaces the current UI. Let native close events dispose its panels and sync state first.
        Minecraft mc = Minecraft.getMinecraft();
        // The server replaced its old containers. Echoing close-all would also close the new one.
        ModularNetwork.CLIENT.closeAll(mc.thePlayer, false);
        mc.displayGuiScreen(null);
    }

    @SideOnly(Side.CLIENT)
    public static ModularScreen createScreen(ModularPanel mainPanel) {
        prepareClientOpen();
        return new ModularScreen(Galaxia.MODID, mainPanel);
    }

    public static ModularPanel build(EntityPlayer player) {
        return build(player, () -> Minecraft.getMinecraft().displayWidth, () -> Minecraft.getMinecraft().displayHeight);
    }

    public static ModularPanel build(EntityPlayer player, IntSupplier viewportWidth, IntSupplier viewportHeight) {
        return buildPanel(contentFor(player, viewportWidth, viewportHeight));
    }

    public static ModularPanel buildBodyPicker(EntityPlayer player, Consumer<CelestialObjectKey> onSelected,
        ParentWidget<?> choices) {
        GalacticMapWidget content = contentFor(
            player,
            () -> Minecraft.getMinecraft().displayWidth,
            () -> Minecraft.getMinecraft().displayHeight);
        content.mapWidget()
            .selectBodies(onSelected, choices);
        return buildPanel(content);
    }

    private static GalacticMapWidget contentFor(EntityPlayer player, IntSupplier viewportWidth,
        IntSupplier viewportHeight) {
        CelestialObject galaxyRoot = GalaxiaCelestialAPI.root();
        CelestialObject currentStar = GalaxiaCelestialAPI.findCurrentStar(player.dimension)
            .orElseGet(
                () -> GalaxiaCelestialAPI.getPrimaryStar()
                    .orElse(galaxyRoot));
        return new GalacticMapWidget(galaxyRoot, currentStar, viewportWidth, viewportHeight);
    }

    private static ModularPanel buildPanel(GalacticMapWidget content) {
        return new ModularPanel(SCREEN_NAME) {

            @Override
            public boolean onKeyPressed(char character, int keyCode) {
                if (keyCode == Keyboard.KEY_ESCAPE && !(getContext().getFocusedWidget()
                    .getElement() instanceof TextFieldWidget) && content.windows.closeTop()) return true;
                return content.mapWidget.handleKeyPressed(keyCode) || super.onKeyPressed(character, keyCode);
            }
        }.fullScreenInvisible()
            .child(content);
    }

    public GalacticMapWidget(CelestialObject galaxyRoot, CelestialObject initialLayer) {
        this(
            galaxyRoot,
            initialLayer,
            () -> Minecraft.getMinecraft().displayWidth,
            () -> Minecraft.getMinecraft().displayHeight);
    }

    private GalacticMapWidget(CelestialObject galaxyRoot, CelestialObject initialLayer, IntSupplier viewportWidth,
        IntSupplier viewportHeight) {
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        name("starmap.screen");
        // Shown in the rename dialog of the asset manager
        TextFieldWidget renameField = GalaxiaUi
            .textField(StatCollector.translateToLocal("galaxia.gui.orbital.asset.name"), 180)
            .setMaxLength(48)
            .setFocusOnGuiOpen(false);
        ParentWidget<?> mapArea = new ParentWidget<>().left(SIDEBAR_WIDTH)
            .top(0)
            .widthRelOffset(1f, -SIDEBAR_WIDTH)
            .heightRel(1f);

        OrbitalMapPreferences preferences = OrbitalMapPreferences.current();
        String worldPreferenceKey = OrbitalMapPreferences.currentWorldKey();
        String playerPreferenceKey = OrbitalMapPreferences.currentPlayerKey();
        this.mapWidget = new OrbitalView.OrbitalMapWidget(galaxyRoot).withInitialLayer(initialLayer)
            .attachRenameField(renameField);
        this.mapWidget.setClickMode(preferences.clickMode(worldPreferenceKey, playerPreferenceKey));
        Flow toolbar = GalaxiaUi.row()
            .name("starmap.toolbar")
            .pos(PANEL_INSET, PANEL_INSET)
            .child(
                toolbarToggle(
                    "signals",
                    "galaxia.gui.orbital.toolbar.signals",
                    mapWidget::isSignalsOpen,
                    mapWidget::toggleSignals))
            .child(
                toolbarToggle(
                    "transfers",
                    "galaxia.gui.orbital.toolbar.transfers",
                    () -> !mapWidget.areTransfersHidden(),
                    mapWidget::toggleTransfersHidden))
            .child(
                toolbarToggle(
                    "satelliteLinks",
                    "galaxia.gui.orbital.toolbar.satellites",
                    () -> !mapWidget.isSatelliteNetworkHidden(),
                    mapWidget::toggleSatelliteNetworkHidden))
            .child(
                toolbarToggle(
                    "assets",
                    "galaxia.gui.orbital.toolbar.assets",
                    mapWidget::isAssetsPanelOpen,
                    mapWidget::toggleAssetsPanel))
            .child(GalaxiaUi.button(IKey.dynamic(this::clickModeLabel), clickModeWidth(), () -> {
                mapWidget.toggleClickMode();
                preferences.setClickMode(worldPreferenceKey, playerPreferenceKey, mapWidget.getClickMode());
            })
                .name("starmap.toolbar.clickMode")
                .tooltip(t -> t.addLine(StatCollector.translateToLocal("galaxia.gui.orbital.click_mode.tooltip"))));

        mapArea.child(
            mapWidget.pos(0, 0)
                .sizeRel(1f));
        mapArea.child(mapWidget.createPinnedInfoWidget());
        mapArea.child(mapWidget.createTransferSimulatorWidget());
        mapArea.child(mapWidget.createAssetActionsWidget());
        mapArea.child(toolbar);
        mapArea.child(
            mapWidget.createSignalsWidget()
                .pos(PANEL_INSET, PANEL_TOP));
        mapArea.child(
            mapWidget.createAssetsPanelWidget()
                .pos(PANEL_INSET, PANEL_TOP));
        mapArea.child(
            mapWidget.createContextMenuWidget()
                .pos(0, 0)
                .sizeRel(1f));
        child(mapArea);
        // After the map, so bodies and orbits drawn past the map's edge stay under the translucent sidebar
        child(
            new CelestialSidebarWidget(galaxyRoot, initialLayer, mapWidget).left(0)
                .top(0)
                .width(SIDEBAR_WIDTH)
                .heightRel(1f));
        child(
            windows.pos(0, 0)
                .sizeRel(1f));
    }

    @Override
    public void beforeResize(boolean onOpen) {
        super.beforeResize(onOpen);
        Minecraft mc = Minecraft.getMinecraft();
        int pixelWidth = viewportWidth.getAsInt();
        int pixelHeight = viewportHeight.getAsInt();
        int minecraftScale = new ScaledResolution(mc, pixelWidth, pixelHeight).getScaleFactor();
        int mapScale = Math.max(1, Math.min(pixelWidth / MINIMUM_CANVAS_WIDTH, pixelHeight / MINIMUM_CANVAS_HEIGHT));
        interfaceScale = (float) mapScale / minecraftScale;
        var screen = getScreen().getScreenArea();
        int width = (int) Math.ceil(screen.width / interfaceScale);
        int height = (int) Math.ceil(screen.height / interfaceScale);
        if (getArea().width != width || getArea().height != height) size(width, height);
    }

    @Override
    public void transform(IViewportStack stack) {
        super.transform(stack);
        stack.scale(interfaceScale, interfaceScale);
    }

    public WindowLayer windows() {
        return windows;
    }

    public OrbitalView.OrbitalMapWidget mapWidget() {
        return mapWidget;
    }

    private String clickModeLabel() {
        String key = mapWidget.getClickMode() == OrbitalMapClickMode.FOLLOW ? "galaxia.gui.orbital.click_mode.follow"
            : "galaxia.gui.orbital.click_mode.hierarchy";
        return StatCollector.translateToLocal(key);
    }

    /** Toolbar button that stays lit while what it shows is on */
    private static ButtonWidget<?> toolbarToggle(String name, String langKey, BooleanSupplier on, Runnable onClick) {
        String label = StatCollector.translateToLocal(langKey);
        return GalaxiaUi.toggle(() -> label, GalaxiaUi.width(label) + 10, on, onClick)
            .name("starmap.toolbar." + name);
    }

    private static int clickModeWidth() {
        return Math.max(
            GalaxiaUi.width(StatCollector.translateToLocal("galaxia.gui.orbital.click_mode.follow")),
            GalaxiaUi.width(StatCollector.translateToLocal("galaxia.gui.orbital.click_mode.hierarchy"))) + 10;
    }
}
