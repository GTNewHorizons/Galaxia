package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.network.ModularNetwork;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData.Achievement;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.RocketScheduleSyncHandler;
import com.gtnewhorizons.galaxia.core.network.RocketTransitPacket;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket.TransitView;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class GalacticMapWidget extends ParentWidget<GalacticMapWidget> {

    public static final String SCREEN_NAME = "galactic_orbital_map";

    private static final int SIDEBAR_WIDTH = 216;
    private static final int MINIMUM_CANVAS_WIDTH = 1024;
    private static final int MINIMUM_CANVAS_HEIGHT = 576;

    private float interfaceScale = 1f;

    private static final int PANEL_INSET = 10;
    private static final int PANEL_TOP = 30;
    private static final int TRANSIT_WIDTH = 360;
    private static final int TRANSIT_HEIGHT = 76;
    private static final String TRANSIT_TEXT = "galaxia.rocket.transit.";

    private boolean selectingBody;

    private final OrbitalView.OrbitalMapWidget mapWidget;
    private final WindowLayer windows = new WindowLayer(() -> interfaceScale);

    private static final SimpleGuiFactory TRANSIT_FACTORY = GuiFactories
        .createSimple("galaxia:rocket_transit", new IGuiHolder<GuiData>() {

            @Override
            public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
                settings.canInteractWith(GalacticMapWidget::canUseTransitMap);
                if (!canUseTransitMap(data.getPlayer())) return ModularPanel.defaultPanel("galaxia:transit_unavailable")
                    .child(
                        IKey.lang("galaxia.rocket.schedule.no_vehicle")
                            .asWidget());
                return build(syncManager, data.getPlayer());
            }

            @Override
            @SideOnly(Side.CLIENT)
            public ModularScreen createScreen(GuiData data, ModularPanel panel) {
                return GalacticMapWidget.createScreen(panel);
            }
        });

    public static void initTransitFactory() {
        TRANSIT_FACTORY.init();
    }

    @SideOnly(Side.CLIENT)
    public static void openTransitMap() {
        TRANSIT_FACTORY.openClient();
    }

    private static boolean canUseTransitMap(EntityPlayer player) {
        if (GTTeamsCompat.hasAchievement(player, Achievement.STAR_MAP_ACTIVATION)) return true;
        return player.worldObj.isRemote ? RocketTransitPacket.Handler.current() != null
            : Galaxia.proxy.serverRuntime()
                .rockets()
                .passengerVehicle(player.getUniqueID()) != null;
    }

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

    public static ModularPanel build(PanelSyncManager syncManager, EntityPlayer player) {
        return buildPanel(syncManager, contentFor(syncManager, player));
    }

    public static ModularPanel buildBodyPicker(PanelSyncManager syncManager, EntityPlayer player,
        Consumer<CelestialObjectKey> onSelected, ParentWidget<?> choices) {
        GalacticMapWidget content = contentFor(syncManager, player);
        content.selectingBody = true;
        content.mapWidget()
            .selectBodies(onSelected, choices);
        return buildPanel(syncManager, content);
    }

    private static GalacticMapWidget contentFor(PanelSyncManager syncManager, EntityPlayer player) {
        var vehiclesPanel = syncManager.syncedPanel(
            "vehicle_schedule",
            true,
            (subSync, handler) -> RocketScheduleSyncHandler.build(subSync, null));
        CelestialObject galaxyRoot = GalaxiaCelestialAPI.root();
        CelestialObject currentStar = GalaxiaCelestialAPI.findCurrentStar(player.dimension)
            .orElseGet(
                () -> GalaxiaCelestialAPI.getPrimaryStar()
                    .orElse(galaxyRoot));
        return new GalacticMapWidget(galaxyRoot, currentStar, vehiclesPanel);
    }

    private static ModularPanel buildPanel(PanelSyncManager syncManager, GalacticMapWidget content) {
        syncManager.syncValue(StarmapActionSyncHandler.KEY, new StarmapActionSyncHandler());
        return new ModularPanel(SCREEN_NAME) {

            @Override
            public boolean onKeyPressed(char character, int keyCode) {
                if (keyCode == Keyboard.KEY_ESCAPE && !(getContext().getFocusedWidget()
                    .getElement() instanceof TextFieldWidget) && content.windows.closeTop()) return true;
                var transit = RocketTransitPacket.Handler.current();
                if (!content.selectingBody && transit != null
                    && transit.restricted()
                    && (keyCode == Keyboard.KEY_ESCAPE
                        || keyCode == Minecraft.getMinecraft().gameSettings.keyBindInventory.getKeyCode()))
                    return true;
                return content.mapWidget.handleKeyPressed(keyCode) || super.onKeyPressed(character, keyCode);
            }
        }.fullScreenInvisible()
            .child(content);
    }

    private GalacticMapWidget(CelestialObject galaxyRoot, CelestialObject initialLayer, IPanelHandler vehiclesPanel) {
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
            .child(
                GalaxiaUi
                    .button(
                        IKey.lang("galaxia.rocket.schedule.fleet"),
                        GalaxiaUi.width(StatCollector.translateToLocal("galaxia.rocket.schedule.fleet")) + 10,
                        vehiclesPanel::openPanel)
                    .name("starmap.toolbar.vehicles")
                    .setEnabledIf(widget -> !selectingBody))
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
        mapArea.child(
            createTransitStatusWidget(RocketTransitPacket.Handler::current, GalaxiaCelestialAPI::get).right(18)
                .top(PANEL_TOP));
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

    public WindowLayer windows() {
        return windows;
    }

    public OrbitalView.OrbitalMapWidget mapWidget() {
        return mapWidget;
    }

    public static ParentWidget<?> createTransitStatusWidget(Supplier<TransitView> current,
        Function<CelestialObjectKey, Optional<CelestialObject>> bodies) {
        var panel = new ParentWidget<>().name("rocket.transit.panel")
            .size(TRANSIT_WIDTH, TRANSIT_HEIGHT)
            .background(new Rectangle().color(EnumColors.MAP_COLOR_MODAL_BG.getColor()))
            .setEnabledIf(widget -> current.get() != null);
        panel.child(IKey.dynamic(() -> {
            var view = current.get();
            return view == null ? ""
                : StatCollector.translateToLocalFormatted(
                    TRANSIT_TEXT + "vehicle",
                    view.vehicleId()
                        .toString()
                        .substring(0, 8),
                    StatCollector.translateToLocal(TRANSIT_TEXT + (view.journey() == null ? "stop" : "flight")));
        })
            .asWidget()
            .name("rocket.transit.vehicle")
            .pos(8, 8)
            .size(TRANSIT_WIDTH - 16, 10)
            .color(GalaxiaGuiStyle.ACCENT));
        panel.child(IKey.dynamic(() -> {
            var view = current.get();
            if (view == null) return "";
            if (view.journey() == null) return StatCollector.translateToLocalFormatted(
                TRANSIT_TEXT + "location",
                transitBodyName(
                    view.source()
                        .body(),
                    bodies),
                transitContext(
                    view.source()
                        .context()));
            return StatCollector.translateToLocalFormatted(
                TRANSIT_TEXT + "route",
                transitBodyName(
                    view.source()
                        .body(),
                    bodies),
                transitContext(
                    view.source()
                        .context()),
                transitBodyName(
                    view.destination()
                        .body(),
                    bodies),
                transitContext(
                    view.destination()
                        .context()));
        })
            .asWidget()
            .name("rocket.transit.route")
            .pos(8, 25)
            .size(TRANSIT_WIDTH - 16, 10)
            .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor()));
        panel.child(IKey.dynamic(() -> {
            var view = current.get();
            if (view == null || view.journey() == null) return "";
            double total = (double) view.elapsedTicks() + view.remainingTicks();
            long seconds = view.remainingTicks() / 20;
            String remaining = String
                .format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
            return StatCollector.translateToLocalFormatted(
                TRANSIT_TEXT + "progress",
                total <= 0 ? 100 : Math.min(100, (int) (100 * view.elapsedTicks() / total)),
                remaining);
        })
            .asWidget()
            .name("rocket.transit.progress")
            .pos(8, 42)
            .size(TRANSIT_WIDTH - 16, 10)
            .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor()));
        panel.child(IKey.dynamic(() -> {
            var view = current.get();
            if (view == null) return "";
            if (view.problemKey() != null) return StatCollector.translateToLocal(view.problemKey());
            return view.journey() != null && view.journey()
                .geometrySeed() == null
                    ? StatCollector.translateToLocalFormatted(
                        TRANSIT_TEXT + "local",
                        transitBodyName(
                            view.source()
                                .body(),
                            bodies))
                    : "";
        })
            .asWidget()
            .name("rocket.transit.problem")
            .pos(8, 59)
            .size(TRANSIT_WIDTH - 16, 10)
            .color(EnumColors.MAP_COLOR_TEXT_WARNING.getColor()));
        return panel;
    }

    private static String transitBodyName(CelestialObjectKey body,
        Function<CelestialObjectKey, Optional<CelestialObject>> bodies) {
        return bodies.apply(body)
            .orElseThrow()
            .displayName();
    }

    private static String transitContext(VehicleSchedule.Context context) {
        return StatCollector.translateToLocal(
            "galaxia.rocket.schedule.context." + context.name()
                .toLowerCase(Locale.ROOT));
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
