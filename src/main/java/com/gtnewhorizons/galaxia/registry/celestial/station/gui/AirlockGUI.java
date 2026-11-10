package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.registry.celestial.station.GalaxiaBehaviors;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileEntityAirlock;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

/**
 * Airlock controller screen: the two rooms the airlock joins on either side of its door, the door's behaviour toggles
 * below and its timings in a settings popup
 */
public class AirlockGUI {

    private static final int WIDTH = 260;
    private static final int HEIGHT = 172;
    private static final int SCREEN_X = 8;
    private static final int SCREEN_Y = 22;
    private static final int SCREEN_WIDTH = WIDTH - 2 * SCREEN_X;
    private static final int SCREEN_HEIGHT = 112;
    private static final int ROOM_WIDTH = 104;
    private static final int DOOR_WIDTH = 24;
    private static final int BUTTON = 18;
    private static final int TOGGLE_GAP = 4;
    private static final int BADGE = 10;
    private static final int SETTINGS_WIDTH = 160;
    private static final int SETTINGS_HEIGHT = 136;

    // spotless:off
    static final int PROTECTION_RADIATION = 1 << 0;
    static final int PROTECTION_PRESSURE  = 1 << 1;
    static final int PROTECTION_SPORES    = 1 << 2;
    static final int PROTECTION_WITHER    = 1 << 3;
    static final int PROTECTION_HEAT      = 1 << 4;
    static final int PROTECTION_COLD      = 1 << 5;
    // spotless:on

    /** What the screen shows about one room, synced value by value */
    record RoomSync(BooleanSupplier present, IntSupplier attachments, IntSupplier volume, IntSupplier oxygenLevel,
        IntSupplier protection, IntSupplier role, BooleanSupplier primary) {}

    public static ModularPanel build(TileEntityAirlock tile, PosGuiData data, PanelSyncManager syncManager,
        UISettings settings) {
        BooleanSyncValue structureValid = new BooleanSyncValue(tile::isStructureValid, tile::isStructureValid);
        syncManager.syncValue("structureValid", structureValid);
        BooleanSyncValue doorOpen = new BooleanSyncValue(tile::isOpen, tile::isOpen);
        syncManager.syncValue("doorOpen", doorOpen);

        BooleanSyncValue proximityOpening = new BooleanSyncValue(tile::isProximityOpening, tile::setProximityOpening)
            .allowC2S();
        BooleanSyncValue proximityAutoClose = new BooleanSyncValue(
            tile::isProximityAutoClose,
            tile::setProximityAutoClose).allowC2S();
        BooleanSyncValue redstoneControl = new BooleanSyncValue(tile::isRedstoneControl, tile::setRedstoneControl)
            .allowC2S();
        BooleanSyncValue manualClick = new BooleanSyncValue(tile::isManualClick, tile::setManualClick).allowC2S();
        BooleanSyncValue autoSeal = new BooleanSyncValue(tile::isAutoSealOnLeak, tile::setAutoSealOnLeak).allowC2S();
        syncManager.syncValue("proximityOpening", proximityOpening);
        syncManager.syncValue("proximityAutoClose", proximityAutoClose);
        syncManager.syncValue("redstoneControl", redstoneControl);
        syncManager.syncValue("manualClick", manualClick);
        syncManager.syncValue("autoSeal", autoSeal);

        RoomSync roomA = registerRoomSync(tile, syncManager, 0);
        RoomSync roomB = registerRoomSync(tile, syncManager, 1);
        ModularPanel panel = GalaxiaGuiStyle.panel("galaxia:airlock_controller", WIDTH, HEIGHT);
        // The settings open beside the screen instead of over it
        IPanelHandler settingsPanel = syncManager.syncedPanel(
            "settings",
            true,
            (subSync, handler) -> buildSettingsPanel(tile, subSync, handler).relative(panel)
                .leftRel(1)
                .topRel(0));

        // Header: structure lamp, title and the refresh and settings buttons
        panel.child(
            new Widget<>().size(6)
                .pos(SCREEN_X, 10)
                .background(
                    new DynamicDrawable(
                        () -> structureValid.getBoolValue() ? EnumTextures.GUI_LED_ON.getImage()
                            : EnumTextures.GUI_LED_OFF.getImage())));
        panel.child(
            GalaxiaGuiStyle.title("galaxia.gui.airlock_controller.title")
                .pos(SCREEN_X + 10, 8));
        panel.child(
            iconButton(EnumTextures.ICON_CYCLE, "galaxia.gui.airlock_controller.refresh_button.tooltip")
                .name("airlock.refresh")
                .pos(WIDTH - SCREEN_X - 2 * BUTTON - 2, 3)
                .syncHandler(
                    new InteractionSyncHandler()
                        .setOnMousePressed(mouse -> { if (mouse.mouseButton == 0) tile.refreshStructure(); })));
        panel.child(
            iconButton(EnumTextures.ICON_SETTINGS, "galaxia.gui.airlock_controller.settings_button.tooltip")
                .name("airlock.settings")
                .pos(WIDTH - SCREEN_X - BUTTON, 3)
                .onMousePressed(mouse -> {
                    if (mouse != 0) return false;
                    if (settingsPanel.isPanelOpen()) settingsPanel.closePanel();
                    else settingsPanel.openPanel();
                    return true;
                }));

        // The airlock schematic: room, door, room
        ParentWidget<?> screen = new ParentWidget<>().pos(SCREEN_X, SCREEN_Y)
            .size(SCREEN_WIDTH, SCREEN_HEIGHT)
            .background(EnumTextures.GUI_WELL.getImage());
        int inner = SCREEN_HEIGHT - 12;
        screen.child(
            room(roomA, "galaxia.gui.airlock_controller.room_a").pos(6, 6)
                .size(ROOM_WIDTH, inner)
                .setEnabledIf(w -> structureValid.getBoolValue()));
        screen.child(
            door(doorOpen::getBoolValue).pos((SCREEN_WIDTH - DOOR_WIDTH) / 2, 6)
                .size(DOOR_WIDTH, inner)
                .setEnabledIf(w -> structureValid.getBoolValue()));
        screen.child(
            room(roomB, "galaxia.gui.airlock_controller.room_b").pos(SCREEN_WIDTH - 6 - ROOM_WIDTH, 6)
                .size(ROOM_WIDTH, inner)
                .setEnabledIf(w -> structureValid.getBoolValue()));
        screen.child(
            IKey.lang("galaxia.gui.airlock_controller.not_formed")
                .color(GalaxiaGuiStyle.BAD)
                .asWidget()
                .name("airlock.not_formed")
                .size(SCREEN_WIDTH, SCREEN_HEIGHT)
                .textAlign(Alignment.Center)
                .setEnabledIf(w -> !structureValid.getBoolValue()));
        panel.child(screen);

        // Door behaviour toggles
        int togglesWidth = 5 * BUTTON + 4 * TOGGLE_GAP;
        panel.child(
            Flow.row()
                .coverChildren()
                .childPadding(TOGGLE_GAP)
                .pos((WIDTH - togglesWidth) / 2, SCREEN_Y + SCREEN_HEIGHT + 8)
                .child(
                    toggle(
                        proximityOpening,
                        EnumTextures.AIRLOCK_PROXIMITY_OPEN,
                        "galaxia.gui.airlock_controller.toggle.proximity_opening.tooltip"))
                .child(
                    toggle(
                        proximityAutoClose,
                        EnumTextures.AIRLOCK_PROXIMITY_CLOSE,
                        "galaxia.gui.airlock_controller.toggle.proximity_auto_close.tooltip"))
                .child(
                    toggle(
                        redstoneControl,
                        EnumTextures.AIRLOCK_REDSTONE_CONTROL,
                        "galaxia.gui.airlock_controller.toggle.redstone_control.tooltip"))
                .child(
                    toggle(
                        manualClick,
                        EnumTextures.AIRLOCK_MANUAL_CLICK,
                        "galaxia.gui.airlock_controller.toggle.manual_click.tooltip"))
                .child(
                    toggle(
                        autoSeal,
                        EnumTextures.AIRLOCK_AUTO_SEAL,
                        "galaxia.gui.airlock_controller.toggle.auto_seal.tooltip")));
        return panel;
    }

    private static ButtonWidget<?> iconButton(EnumTextures icon, String tooltipKey) {
        return GalaxiaGuiStyle.button()
            .size(BUTTON)
            .overlay(icon.getImage())
            .tooltipBuilder(t -> t.addLine(IKey.lang(tooltipKey)));
    }

    /** Behaviour toggle, its button lit while the behaviour is on */
    private static ButtonWidget<?> toggle(BooleanSyncValue sync, EnumTextures icon, String tooltipKey) {
        return new ButtonWidget<>().size(BUTTON)
            .background(
                new DynamicDrawable(
                    () -> sync.getBoolValue() ? EnumTextures.GUI_BUTTON_SELECTED.getImage()
                        : EnumTextures.GUI_BUTTON.getImage()))
            .hoverBackground(EnumTextures.GUI_BUTTON_HOVERED.getImage())
            .overlay(icon.getImage())
            .tooltipBuilder(
                t -> t.addLine(IKey.lang(tooltipKey))
                    .addLine(
                        IKey.dynamic(
                            () -> StatCollector.translateToLocal(
                                sync.getBoolValue() ? "galaxia.gui.airlock_controller.on"
                                    : "galaxia.gui.airlock_controller.off"))))
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                sync.setBoolValue(!sync.getBoolValue(), true, true);
                return true;
            });
    }

    /** One room the airlock joins, a raised card on the schematic */
    private static ParentWidget<?> room(RoomSync room, String nameKey) {
        BooleanSupplier present = room.present();
        Flow column = Flow.column()
            .full()
            .padding(5)
            .childPadding(3)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .collapseDisabledChild(true);
        column.child(
            Flow.row()
                .fullWidth()
                .height(10)
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .child(GalaxiaGuiStyle.title(nameKey))
                .child(
                    new Widget<>().size(6)
                        .background(
                            new DynamicDrawable(
                                () -> room.primary()
                                    .getAsBoolean() ? EnumTextures.GUI_LED_ON.getImage()
                                        : EnumTextures.GUI_LED_OFF.getImage()))
                        .tooltipBuilder(
                            t -> t.addLine(
                                IKey.dynamic(
                                    () -> StatCollector.translateToLocal(
                                        room.primary()
                                            .getAsBoolean() ? "galaxia.gui.role.main" : "galaxia.gui.role.secondary"))))
                        .setEnabledIf(w -> present.getAsBoolean())));
        column.child(
            line(
                () -> StatCollector.translateToLocalFormatted(
                    "galaxia.gui.airlock_controller.room.role",
                    StatCollector.translateToLocal(
                        GalaxiaBehaviors.byId(
                            room.role()
                                .getAsInt())
                            .get()
                            .getUnlocalizedName())),
                GalaxiaGuiStyle.TEXT).setEnabledIf(w -> present.getAsBoolean()));
        column.child(
            line(
                () -> StatCollector.translateToLocal("galaxia.gui.airlock_controller.no_room"),
                GalaxiaGuiStyle.TEXT_DIM).setEnabledIf(w -> !present.getAsBoolean()));
        column.child(
            line(
                () -> StatCollector.translateToLocal(
                    room.protection()
                        .getAsInt() == 0 ? "galaxia.gui.airlock_controller.room.protection.none"
                            : "galaxia.gui.airlock_controller.room.protection"),
                GalaxiaGuiStyle.TEXT).setEnabledIf(w -> present.getAsBoolean()));
        column.child(
            badges(room).setEnabledIf(
                w -> present.getAsBoolean() && room.protection()
                    .getAsInt() != 0));
        column.child(
            line(
                () -> StatCollector.translateToLocalFormatted(
                    "galaxia.gui.airlock_controller.room.attachments",
                    room.attachments()
                        .getAsInt()),
                GalaxiaGuiStyle.TEXT_DIM).setEnabledIf(w -> present.getAsBoolean()));
        column.child(
            line(
                () -> StatCollector.translateToLocalFormatted(
                    "galaxia.gui.airlock_controller.room.volume",
                    room.volume()
                        .getAsInt()),
                GalaxiaGuiStyle.TEXT_DIM).setEnabledIf(w -> present.getAsBoolean()));
        column.child(
            line(
                () -> StatCollector.translateToLocalFormatted(
                    "galaxia.gui.airlock_controller.room.oxygen",
                    room.oxygenLevel()
                        .getAsInt()),
                GalaxiaGuiStyle.TEXT).setEnabledIf(w -> present.getAsBoolean()));
        column.child(
            GalaxiaGuiStyle.bar(
                () -> room.oxygenLevel()
                    .getAsInt() / 100.0,
                () -> GalaxiaGuiStyle.ACCENT)
                .fullWidth()
                .height(6)
                .setEnabledIf(w -> present.getAsBoolean()));
        return new ParentWidget<>().background(EnumTextures.GUI_BUTTON.getImage())
            .child(column);
    }

    private static Widget<?> line(java.util.function.Supplier<String> text, int color) {
        return IKey.dynamic(text::get)
            .color(color)
            .asWidget()
            .fullWidth()
            .height(9)
            .textAlign(Alignment.CenterLeft);
    }

    private static Flow badges(RoomSync room) {
        return Flow.row()
            .coverChildren()
            .childPadding(2)
            .collapseDisabledChild(true)
            .child(
                badge(
                    room,
                    PROTECTION_RADIATION,
                    GalaxiaItemList.RADIATION_PROTECTION.getItem(),
                    "galaxia.gui.airlock_controller.room.protection.radiation"))
            .child(
                badge(
                    room,
                    PROTECTION_PRESSURE,
                    GalaxiaItemList.PRESSURE_PROTECTION_HIGH.getItem(),
                    "galaxia.gui.airlock_controller.room.protection.pressure"))
            .child(
                badge(
                    room,
                    PROTECTION_SPORES,
                    GalaxiaItemList.SPORE_FILTER.getItem(),
                    "galaxia.gui.airlock_controller.room.protection.spores"))
            .child(
                badge(
                    room,
                    PROTECTION_WITHER,
                    GalaxiaItemList.WITHER_PROTECTION.getItem(),
                    "galaxia.gui.airlock_controller.room.protection.wither"))
            .child(
                badge(
                    room,
                    PROTECTION_HEAT,
                    GalaxiaItemList.THERMAL_PROTECTION_HOT.getItem(),
                    "galaxia.gui.airlock_controller.room.protection.heat"))
            .child(
                badge(
                    room,
                    PROTECTION_COLD,
                    GalaxiaItemList.THERMAL_PROTECTION_COLD.getItem(),
                    "galaxia.gui.airlock_controller.room.protection.cold"));
    }

    private static Widget<?> badge(RoomSync room, int bit, Item item, String tooltipKey) {
        return new Widget<>().size(BADGE)
            .background(new ItemDrawable(item))
            .tooltipBuilder(t -> t.addLine(IKey.lang(tooltipKey)))
            .setEnabledIf(
                w -> (room.protection()
                    .getAsInt() & bit) != 0);
    }

    /** The door between the rooms: two panes that slide apart while it is open */
    private static ParentWidget<?> door(BooleanSupplier open) {
        ParentWidget<?> door = new ParentWidget<>().background(EnumTextures.GUI_WELL.getImage());
        door.child(pane(open).left(2));
        door.child(pane(open).right(2));
        return door;
    }

    private static Widget<?> pane(BooleanSupplier open) {
        return new Widget<>().top(2)
            .bottom(2)
            .width(() -> open.getAsBoolean() ? 4 : (DOOR_WIDTH - 4 - 2) / 2, Unit.Measure.PIXEL)
            .background(EnumTextures.GUI_BUTTON_SELECTED.getImage());
    }

    /** Registers the values the screen shows about the room in one slot of the airlock */
    private static RoomSync registerRoomSync(TileEntityAirlock tile, PanelSyncManager syncManager, int index) {
        BooleanSyncValue present = new BooleanSyncValue(
            () -> roomAt(tile, index) != null,
            () -> roomAt(tile, index) != null);
        syncManager.syncValue("room" + index + ".present", present);
        IntSyncValue attachments = new IntSyncValue(() -> attachments(tile, index), () -> attachments(tile, index));
        syncManager.syncValue("room" + index + ".attachments", attachments);
        IntSyncValue volume = new IntSyncValue(() -> volume(tile, index), () -> volume(tile, index));
        syncManager.syncValue("room" + index + ".volume", volume);
        IntSyncValue oxygen = new IntSyncValue(() -> oxygenLevel(tile, index), () -> oxygenLevel(tile, index));
        syncManager.syncValue("room" + index + ".oxygenLevel", oxygen);
        IntSyncValue protection = new IntSyncValue(
            () -> protectionFlags(tile, index),
            () -> protectionFlags(tile, index));
        syncManager.syncValue("room" + index + ".protection", protection);
        IntSyncValue role = new IntSyncValue(() -> roleIdx(tile, index), () -> roleIdx(tile, index));
        syncManager.syncValue("room" + index + ".role", role);
        BooleanSyncValue primary = new BooleanSyncValue(() -> isPrimary(tile, index), () -> isPrimary(tile, index));
        syncManager.syncValue("room" + index + ".primary", primary);
        return new RoomSync(
            present::getBoolValue,
            attachments::getIntValue,
            volume::getIntValue,
            oxygen::getIntValue,
            protection::getIntValue,
            role::getIntValue,
            primary::getBoolValue);
    }

    private static TileStation roomAt(TileEntityAirlock tile, int index) {
        if (tile.getWorldObj() == null) return null;
        List<BlockPos> controllers = tile.getStationControllers();
        if (index >= controllers.size()) return null;
        TileEntity te = controllers.get(index)
            .getTE(tile.getWorldObj());
        return te instanceof TileStation station ? station : null;
    }

    private static int oxygenLevel(TileEntityAirlock tile, int index) {
        TileStation room = roomAt(tile, index);
        return room != null ? (int) Math.round(room.getOxygenLevel()) : 0;
    }

    private static int volume(TileEntityAirlock tile, int index) {
        TileStation room = roomAt(tile, index);
        return room != null ? room.getVolume() : 0;
    }

    private static int attachments(TileEntityAirlock tile, int index) {
        TileStation room = roomAt(tile, index);
        return room != null ? room.getAttachments()
            .size() : 0;
    }

    private static int protectionFlags(TileEntityAirlock tile, int index) {
        TileStation room = roomAt(tile, index);
        if (room == null) return 0;
        int flags = 0;
        if (room.isSealed()) flags |= PROTECTION_RADIATION | PROTECTION_PRESSURE;
        if (room.hasAirPurifier()) flags |= PROTECTION_SPORES;
        if (room.hasWitherBlocker()) flags |= PROTECTION_WITHER;
        if (room.getHeatingModifier() > 0) flags |= PROTECTION_HEAT;
        if (room.getCoolingModifier() > 0) flags |= PROTECTION_COLD;
        return flags;
    }

    private static int roleIdx(TileEntityAirlock tile, int index) {
        TileStation room = roomAt(tile, index);
        return room != null ? GalaxiaBehaviors.of(room.getBehavior())
            .getId() : GalaxiaBehaviors.ROOM.getId();
    }

    private static boolean isPrimary(TileEntityAirlock tile, int index) {
        TileStation room = roomAt(tile, index);
        return room != null && room.isPrimary();
    }

    /** Timings of the airlock, in a popup beside the screen */
    private static ModularPanel buildSettingsPanel(TileEntityAirlock tile, PanelSyncManager syncManager,
        IPanelHandler handler) {
        IntSyncValue checkInterval = new IntSyncValue(tile::getCheckInterval, tile::setCheckInterval).allowC2S();
        IntSyncValue closeDelay = new IntSyncValue(tile::getCloseDelay, tile::setCloseDelay).allowC2S();
        IntSyncValue proximityRange = new IntSyncValue(tile::getProximityRange, tile::setProximityRange).allowC2S();
        syncManager.syncValue("checkInterval", checkInterval);
        syncManager.syncValue("closeDelay", closeDelay);
        syncManager.syncValue("proximityRange", proximityRange);

        ModularPanel panel = GalaxiaGuiStyle.panel("galaxia:airlock_settings", SETTINGS_WIDTH, SETTINGS_HEIGHT);
        panel.child(
            GalaxiaGuiStyle.title("galaxia.gui.airlock_controller.settings.title")
                .pos(8, 8));
        panel.child(
            GalaxiaGuiStyle.textButton(IKey.str("x"))
                .size(12)
                .pos(SETTINGS_WIDTH - 18, 6)
                .tooltipBuilder(t -> t.addLine(IKey.lang("galaxia.gui.airlock_controller.settings.close.tooltip")))
                .onMousePressed(mouse -> {
                    handler.closePanel();
                    return true;
                }));
        numberSetting(
            panel,
            24,
            "galaxia.gui.airlock_controller.settings.check_interval",
            checkInterval,
            TileEntityAirlock.MIN_CHECK_INTERVAL,
            TileEntityAirlock.MAX_CHECK_INTERVAL);
        numberSetting(
            panel,
            54,
            "galaxia.gui.airlock_controller.settings.close_delay",
            closeDelay,
            TileEntityAirlock.MIN_CLOSE_DELAY,
            TileEntityAirlock.MAX_CLOSE_DELAY);
        numberSetting(
            panel,
            84,
            "galaxia.gui.airlock_controller.settings.proximity_range",
            proximityRange,
            TileEntityAirlock.MIN_PROXIMITY_RANGE,
            TileEntityAirlock.MAX_PROXIMITY_RANGE);
        panel.child(
            GalaxiaGuiStyle.textButton(IKey.lang("galaxia.gui.airlock_controller.settings.reset"))
                .name("airlock.settings.reset")
                .size(64, 16)
                .pos((SETTINGS_WIDTH - 64) / 2, SETTINGS_HEIGHT - 24)
                .tooltipBuilder(t -> t.addLine(IKey.lang("galaxia.gui.airlock_controller.settings.reset.tooltip")))
                .onMousePressed(mouse -> {
                    if (mouse != 0) return false;
                    checkInterval.setValue(TileEntityAirlock.DEFAULT_CHECK_INTERVAL, true, true);
                    closeDelay.setValue(TileEntityAirlock.DEFAULT_CLOSE_DELAY, true, true);
                    proximityRange.setValue(TileEntityAirlock.DEFAULT_PROXIMITY_RANGE, true, true);
                    return true;
                }));
        return panel;
    }

    private static void numberSetting(ModularPanel panel, int y, String labelKey, IntSyncValue value, int min,
        int max) {
        panel.child(
            IKey.lang(labelKey)
                .color(GalaxiaGuiStyle.TEXT_DIM)
                .asWidget()
                .pos(8, y));
        panel.child(
            new TextFieldWidget().value(value)
                .size(SETTINGS_WIDTH - 16, 14)
                .pos(8, y + 11)
                .numbersInt(min, max)
                .formatAsInteger(true)
                .setTextAlignment(Alignment.Center)
                .setTextColor(GalaxiaGuiStyle.TEXT)
                .background(EnumTextures.GUI_WELL.getImage())
                .tooltipBuilder(t -> t.addLine(IKey.lang("galaxia.gui.airlock_controller.range", min, max))));
    }
}
