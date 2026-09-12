package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

public final class GalacticMapWidget extends ParentWidget<GalacticMapWidget> {

    public static final String SCREEN_NAME = "galactic_orbital_map";

    private static final int SIDEBAR_WIDTH = 216;
    private static final int MINIMUM_CANVAS_WIDTH = 1024;
    private static final int MINIMUM_CANVAS_HEIGHT = 576;

    private static final IDrawable TOP_BUTTON_BORDER = new Rectangle().hollow(1)
        .color(EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());

    private float interfaceScale = 1f;

    private static final int PANEL_INSET = 10;
    private static final int PANEL_TOP = 30;
    private static final int DETAILS_RIGHT_INSET = 18;
    private static final int TOP_BUTTON_GAP = 6;
    private static final int SIGNALS_W = 66;
    private static final int TOP_BUTTON_H = 16;
    private static final int TRANSFERS_W = 92;
    private static final int SATELLITE_LINKS_W = 98;
    private static final int ASSETS_W = 66;
    private static final int HIERARCHY_TOGGLE_W = 74;

    private final OrbitalView.OrbitalMapWidget mapWidget;

    public static ModularPanel build(PanelSyncManager syncManager, EntityPlayer player) {
        syncManager.syncValue(StarmapActionSyncHandler.KEY, new StarmapActionSyncHandler());
        CelestialObject galaxyRoot = GalaxiaCelestialAPI.root();
        CelestialObject currentStar = GalaxiaCelestialAPI.findCurrentStar(player.dimension)
            .orElseGet(
                () -> GalaxiaCelestialAPI.getPrimaryStar()
                    .orElse(galaxyRoot));
        GalacticMapWidget content = new GalacticMapWidget(galaxyRoot, currentStar);
        return new ModularPanel(SCREEN_NAME) {

            @Override
            public boolean onKeyPressed(char character, int keyCode) {
                return content.mapWidget.handleKeyPressed(keyCode) || super.onKeyPressed(character, keyCode);
            }
        }.fullScreenInvisible()
            .child(content);
    }

    public GalacticMapWidget(CelestialObject galaxyRoot, CelestialObject initialLayer) {
        name("starmap.screen");
        TextFieldWidget renameField = new TextFieldWidget().left(SIDEBAR_WIDTH)
            .top(-1000)
            .width(180)
            .height(22)
            .setMaxLength(48)
            .setTextColor(EnumColors.MapSidebarSearchInput.getColor())
            .hintText(StatCollector.translateToLocal("galaxia.gui.orbital.asset.name"))
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
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
        Flow toolbar = Flow.row()
            .name("starmap.toolbar")
            .pos(PANEL_INSET, PANEL_INSET)
            .height(TOP_BUTTON_H)
            .coverChildrenWidth()
            .childPadding(TOP_BUTTON_GAP)
            .child(
                createTopBarButton(
                    "signals",
                    SIGNALS_W,
                    () -> toolbarText(mapWidget.isSignalsOpen() ? "signals.open" : "signals.closed"),
                    mapWidget::toggleSignals))
            .child(
                createTopBarButton(
                    "transfers",
                    TRANSFERS_W,
                    () -> toolbarText(mapWidget.areTransfersHidden() ? "transfers.show" : "transfers.hide"),
                    mapWidget::toggleTransfersHidden))
            .child(
                createTopBarButton(
                    "satelliteLinks",
                    SATELLITE_LINKS_W,
                    () -> toolbarText(mapWidget.isSatelliteNetworkHidden() ? "satellites.show" : "satellites.hide"),
                    mapWidget::toggleSatelliteNetworkHidden))
            .child(
                createTopBarButton(
                    "assets",
                    ASSETS_W,
                    () -> toolbarText(mapWidget.isAssetsPanelOpen() ? "assets.open" : "assets.closed"),
                    mapWidget::toggleAssetsPanel))
            .child(createTopBarButton("clickMode", HIERARCHY_TOGGLE_W, this::clickModeLabel, () -> {
                mapWidget.toggleClickMode();
                preferences.setClickMode(worldPreferenceKey, playerPreferenceKey, mapWidget.getClickMode());
            }).tooltipDynamic(t -> t.addLine(StatCollector.translateToLocal("galaxia.gui.orbital.click_mode.tooltip")))
                .onUpdateListener(ButtonWidget::markTooltipDirty, true));

        mapArea.child(
            mapWidget.pos(0, 0)
                .sizeRel(1f));
        var details = mapWidget.createPinnedInfoWidget();
        mapArea.child(
            details.name("starmap.panel.details")
                .right(DETAILS_RIGHT_INSET)
                .top(
                    () -> Math.max(PANEL_TOP, (mapArea.getArea().height - details.getArea().height) / 2),
                    Unit.Measure.PIXEL));
        mapArea.child(mapWidget.createTransferTooltipWidget());
        mapArea.child(mapWidget.createTransferSimulatorWidget());
        mapArea.child(mapWidget.createAssetActionsWidget());
        mapArea.child(toolbar);
        mapArea.child(
            mapWidget.createSignalsWidget()
                .name("starmap.panel.signals")
                .pos(PANEL_INSET, PANEL_TOP));
        mapArea.child(
            mapWidget.createAssetsPanelWidget()
                .name("starmap.panel.assets")
                .pos(PANEL_INSET, PANEL_TOP));
        mapArea.child(
            mapWidget.createContextMenuWidget()
                .pos(0, 0)
                .sizeRel(1f));
        child(
            new CelestialSidebarWidget(galaxyRoot, initialLayer, mapWidget).left(0)
                .top(0)
                .width(SIDEBAR_WIDTH)
                .heightRel(1f));
        child(mapArea);
        child(renameField);
    }

    @Override
    public void beforeResize(boolean onOpen) {
        super.beforeResize(onOpen);
        Minecraft mc = Minecraft.getMinecraft();
        int minecraftScale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        int mapScale = Math
            .max(1, Math.min(mc.displayWidth / MINIMUM_CANVAS_WIDTH, mc.displayHeight / MINIMUM_CANVAS_HEIGHT));
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

    public OrbitalView.OrbitalMapWidget mapWidget() {
        return mapWidget;
    }

    private static String toolbarText(String key) {
        return StatCollector.translateToLocal("galaxia.gui.orbital.toolbar." + key);
    }

    private String clickModeLabel() {
        String key = mapWidget.getClickMode() == OrbitalMapClickMode.FOLLOW ? "galaxia.gui.orbital.click_mode.follow"
            : "galaxia.gui.orbital.click_mode.hierarchy";
        return StatCollector.translateToLocal(key);
    }

    private ButtonWidget<?> createTopBarButton(String name, int width, Supplier<String> labelSupplier,
        Runnable onClick) {
        return new ButtonWidget<>().name("starmap.toolbar." + name)
            .size(width, TOP_BUTTON_H)
            .background(new Rectangle().color(EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor()), TOP_BUTTON_BORDER)
            .hoverBackground(
                new Rectangle().color(EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()),
                TOP_BUTTON_BORDER)
            .overlay(IKey.dynamic(labelSupplier::get))
            .onMousePressed(mb -> {
                onClick.run();
                return true;
            });
    }

}
