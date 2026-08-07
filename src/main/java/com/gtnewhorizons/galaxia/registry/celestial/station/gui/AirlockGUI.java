package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import java.util.List;

import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.station.GalaxiaBehaviors;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileEntityAirlock;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.gui.AirlockScreenWidget.RoomSync;

public class AirlockGUI {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 212;
    /** Left/right inset of the content columns within the panel. */
    private static final int PANEL_PAD = 8;
    /** Inset of the header chrome and the gap between it and the content below. */
    private static final int HEADER_INSET = 4;
    private static final int HEADER_HEIGHT = 18;
    private static final int HEADER_TITLE_X = 6;
    private static final int HEADER_TITLE_Y = 3;
    private static final int SCREEN_HEIGHT = 140;
    private static final int TOGGLE_ROW_HEIGHT = 18;
    /** Gap between the toggle buttons in the behavior row. */
    private static final int TOGGLE_GAP = 4;
    /** Side of every square icon button (toggles, settings, refresh, close). */
    private static final int SQUARE_BUTTON_SIZE = 18;
    private static final int SETTINGS_BUTTON_RIGHT = 22;
    private static final int REFRESH_BUTTON_RIGHT = 2;
    private static final int SETTINGS_WIDTH = 120;
    private static final int SETTINGS_HEIGHT = 130;
    private static final int SETTINGS_PAD = 8;
    /** Gap between the setting rows. */
    private static final int SETTINGS_GAP = 4;
    private static final int FIELD_WIDTH = 60;
    private static final int FIELD_HEIGHT = 16;
    private static final int RESET_WIDTH = 64;
    private static final int RESET_HEIGHT = 16;

    public static ModularPanel build(TileEntityAirlock tile, PosGuiData data, PanelSyncManager syncManager,
        UISettings settings) {

        // S2C-only: structure validity drives the empty/error state.
        BooleanSyncValue structureValidSync = new BooleanSyncValue(tile::isStructureValid, tile::isStructureValid);
        syncManager.syncValue("structureValid", structureValidSync);

        // S2C-only: door state drawn on the screen's central door panel.
        BooleanSyncValue doorOpenSync = new BooleanSyncValue(tile::isOpen, tile::isOpen);
        syncManager.syncValue("doorOpen", doorOpenSync);

        // C2S behavior toggles.
        BooleanSyncValue proximityOpeningSync = new BooleanSyncValue(
            tile::isProximityOpening,
            tile::setProximityOpening).allowC2S();
        BooleanSyncValue proximityAutoCloseSync = new BooleanSyncValue(
            tile::isProximityAutoClose,
            tile::setProximityAutoClose).allowC2S();
        BooleanSyncValue redstoneControlSync = new BooleanSyncValue(tile::isRedstoneControl, tile::setRedstoneControl)
            .allowC2S();
        BooleanSyncValue manualClickSync = new BooleanSyncValue(tile::isManualClick, tile::setManualClick).allowC2S();
        BooleanSyncValue autoSealSync = new BooleanSyncValue(tile::isAutoSealOnLeak, tile::setAutoSealOnLeak)
            .allowC2S();
        syncManager.syncValue("proximityOpening", proximityOpeningSync);
        syncManager.syncValue("proximityAutoClose", proximityAutoCloseSync);
        syncManager.syncValue("redstoneControl", redstoneControlSync);
        syncManager.syncValue("manualClick", manualClickSync);
        syncManager.syncValue("autoSeal", autoSealSync);

        // Per-room sync values (S2C only), one slot per possible station controller. Each slot resolves the
        // TileStation at that index from the airlock's station controller list.
        RoomSync roomA = registerRoomSync(tile, syncManager, 0);
        RoomSync roomB = registerRoomSync(tile, syncManager, 1);
        // Settings side panel, opened via the header button.
        IPanelHandler settingsHandler = syncManager.syncedPanel(
            "settings",
            true,
            (subSyncManager, subSyncHandler) -> buildSettingsPanel(tile, subSyncManager, subSyncHandler));

        ModularPanel panel = ModularPanel.defaultPanel("galaxia:airlock_controller", PANEL_WIDTH, PANEL_HEIGHT);

        // GT-style title strip across the top of the panel: dark chrome bar with a
        // green/red status underline. Holds the title and the settings/refresh buttons.
        panel.child(headerBar(structureValidSync, settingsHandler, tile));

        int screenTop = HEADER_INSET + HEADER_HEIGHT + HEADER_INSET;

        // Screen area: GT-style black screen drawing the airlock schematic. The
        // drawing is green while the structure is valid and red while it is not.
        panel.child(
            new AirlockScreenWidget(roomA, roomB, doorOpenSync::getBoolValue, structureValidSync::getBoolValue)
                .pos(PANEL_PAD, screenTop)
                .size(PANEL_WIDTH - 2 * PANEL_PAD, SCREEN_HEIGHT));

        // Behavior toggles.
        panel.child(
            Flow.row()
                .pos(PANEL_PAD, screenTop + SCREEN_HEIGHT + PANEL_PAD)
                .size(PANEL_WIDTH - 2 * PANEL_PAD, TOGGLE_ROW_HEIGHT)
                .childPadding(TOGGLE_GAP)
                .child(
                    squareToggle(
                        proximityOpeningSync,
                        "galaxia.gui.airlock_controller.toggle.proximity_opening.tooltip",
                        GuiTextures.MOVE_TO))
                .child(
                    squareToggle(
                        proximityAutoCloseSync,
                        "galaxia.gui.airlock_controller.toggle.proximity_auto_close.tooltip",
                        GuiTextures.STOP))
                .child(
                    squareToggle(
                        redstoneControlSync,
                        "galaxia.gui.airlock_controller.toggle.redstone_control.tooltip",
                        GuiTextures.WRENCH))
                .child(
                    squareToggle(
                        manualClickSync,
                        "galaxia.gui.airlock_controller.toggle.manual_click.tooltip",
                        GuiTextures.MAIN_HANDLE))
                .child(
                    squareToggle(
                        autoSealSync,
                        "galaxia.gui.airlock_controller.toggle.auto_seal.tooltip",
                        GuiTextures.LOCKED)));

        return panel;
    }

    /**
     * Registers the S2C per-value sync handlers for one room slot and returns a {@link RoomSync} bundle of their
     * client-side suppliers. Every value describes the room at that slot itself; none aggregates over the whole
     * station.
     */
    private static RoomSync registerRoomSync(TileEntityAirlock tile, PanelSyncManager syncManager, int index) {
        BooleanSyncValue presentSync = new BooleanSyncValue(
            () -> roomAt(tile, index) != null,
            () -> roomAt(tile, index) != null);
        syncManager.syncValue("room" + index + ".present", presentSync);

        IntSyncValue attachmentsSync = new IntSyncValue(() -> attachments(tile, index), () -> attachments(tile, index));
        syncManager.syncValue("room" + index + ".attachments", attachmentsSync);

        IntSyncValue volumeSync = new IntSyncValue(() -> volume(tile, index), () -> volume(tile, index));
        syncManager.syncValue("room" + index + ".volume", volumeSync);

        IntSyncValue oxygenLevelSync = new IntSyncValue(() -> oxygenLevel(tile, index), () -> oxygenLevel(tile, index));
        syncManager.syncValue("room" + index + ".oxygenLevel", oxygenLevelSync);

        IntSyncValue protectionSync = new IntSyncValue(
            () -> protectionFlags(tile, index),
            () -> protectionFlags(tile, index));
        syncManager.syncValue("room" + index + ".protection", protectionSync);

        IntSyncValue roleSync = new IntSyncValue(() -> roleIdx(tile, index), () -> roleIdx(tile, index));
        syncManager.syncValue("room" + index + ".role", roleSync);

        BooleanSyncValue primarySync = new BooleanSyncValue(() -> isPrimary(tile, index), () -> isPrimary(tile, index));
        syncManager.syncValue("room" + index + ".primary", primarySync);

        return new RoomSync(
            presentSync::getBoolValue,
            attachmentsSync::getIntValue,
            volumeSync::getIntValue,
            oxygenLevelSync::getIntValue,
            protectionSync::getIntValue,
            roleSync::getIntValue,
            primarySync::getBoolValue);
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
        if (room.isSealed()) {
            flags |= AirlockScreenWidget.PROTECTION_RADIATION | AirlockScreenWidget.PROTECTION_PRESSURE;
        }
        if (room.hasAirPurifier()) {
            flags |= AirlockScreenWidget.PROTECTION_SPORES;
        }
        if (room.hasWitherBlocker()) {
            flags |= AirlockScreenWidget.PROTECTION_WITHER;
        }
        if (room.getHeatingModifier() > 0) {
            flags |= AirlockScreenWidget.PROTECTION_HEAT;
        }
        if (room.getCoolingModifier() > 0) {
            flags |= AirlockScreenWidget.PROTECTION_COLD;
        }
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

    private static ModularPanel buildSettingsPanel(TileEntityAirlock tile, PanelSyncManager syncManager,
        IPanelHandler settingsHandler) {
        IntSyncValue checkIntervalSync = new IntSyncValue(tile::getCheckInterval, tile::setCheckInterval).allowC2S();
        IntSyncValue closeDelaySync = new IntSyncValue(tile::getCloseDelay, tile::setCloseDelay).allowC2S();
        IntSyncValue proximityRangeSync = new IntSyncValue(tile::getProximityRange, tile::setProximityRange).allowC2S();
        syncManager.syncValue("checkInterval", checkIntervalSync);
        syncManager.syncValue("closeDelay", closeDelaySync);
        syncManager.syncValue("proximityRange", proximityRangeSync);

        return ModularPanel.defaultPanel("galaxia:airlock_settings", SETTINGS_WIDTH, SETTINGS_HEIGHT)
            .child(
                Flow.column()
                    .pos(SETTINGS_PAD, SETTINGS_PAD)
                    .childPadding(SETTINGS_GAP)
                    .child(
                        Flow.row()
                            .sizeRel(1f, 0f)
                            .child(
                                IKey.lang("galaxia.gui.airlock_controller.settings.title")
                                    .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                                    .asWidget()
                                    .expanded())
                            .child(closeButton(settingsHandler)))
                    .child(
                        numberField(
                            "galaxia.gui.airlock_controller.settings.check_interval",
                            checkIntervalSync,
                            TileEntityAirlock.MIN_CHECK_INTERVAL,
                            TileEntityAirlock.MAX_CHECK_INTERVAL))
                    .child(
                        numberField(
                            "galaxia.gui.airlock_controller.settings.close_delay",
                            closeDelaySync,
                            TileEntityAirlock.MIN_CLOSE_DELAY,
                            TileEntityAirlock.MAX_CLOSE_DELAY))
                    .child(
                        numberField(
                            "galaxia.gui.airlock_controller.settings.proximity_range",
                            proximityRangeSync,
                            TileEntityAirlock.MIN_PROXIMITY_RANGE,
                            TileEntityAirlock.MAX_PROXIMITY_RANGE))
                    .child(resetButton(checkIntervalSync, closeDelaySync, proximityRangeSync)));
    }

    private static ButtonWidget<?> closeButton(IPanelHandler settingsHandler) {
        return new ButtonWidget<>().size(SQUARE_BUTTON_SIZE, SQUARE_BUTTON_SIZE)
            .background(GuiTextures.BUTTON_CLEAN)
            .overlay(GuiTextures.CLOSE)
            .tooltipBuilder(t -> t.addLine(IKey.lang("galaxia.gui.airlock_controller.settings.close.tooltip")))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                settingsHandler.closePanel();
                return true;
            });
    }

    private static ButtonWidget<?> resetButton(IntSyncValue checkIntervalSync, IntSyncValue closeDelaySync,
        IntSyncValue proximityRangeSync) {
        return new ButtonWidget<>().size(RESET_WIDTH, RESET_HEIGHT)
            .overlay(IKey.lang("galaxia.gui.airlock_controller.settings.reset"))
            .tooltipBuilder(t -> t.addLine(IKey.lang("galaxia.gui.airlock_controller.settings.reset.tooltip")))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                checkIntervalSync.setValue(TileEntityAirlock.DEFAULT_CHECK_INTERVAL, true, true);
                closeDelaySync.setValue(TileEntityAirlock.DEFAULT_CLOSE_DELAY, true, true);
                proximityRangeSync.setValue(TileEntityAirlock.DEFAULT_PROXIMITY_RANGE, true, true);
                return true;
            });
    }

    /**
     * GT parallels-style numeric text field: typeable, centered, clamped to [min, max]. Hover tooltip states the range.
     */
    private static TextFieldWidget numberField(String labelKey, IntSyncValue value, int min, int max) {
        return new TextFieldWidget().size(FIELD_WIDTH, FIELD_HEIGHT)
            .value(value)
            .numbersInt(min, max)
            .formatAsInteger(true)
            .setTextAlignment(Alignment.Center)
            .tooltipBuilder(
                t -> t.addLine(IKey.lang(labelKey))
                    .addLine(IKey.lang("galaxia.gui.airlock_controller.range", min, max)));
    }

    /**
     * Small square GT-style toggle button. Coloured green while enabled, grey while disabled; clicking flips the synced
     * value. Hover tooltip explains what the toggle does.
     */
    private static ButtonWidget<?> squareToggle(BooleanSyncValue sync, String tooltipKey, UITexture icon) {
        return new ButtonWidget<>().size(SQUARE_BUTTON_SIZE, SQUARE_BUTTON_SIZE)
            .background(
                new DynamicDrawable(
                    () -> GuiTextures.BUTTON_CLEAN.withColorOverride(
                        sync.getBoolValue() ? EnumColors.AIRLOCK_TOGGLE_ON.getColor()
                            : EnumColors.AIRLOCK_TOGGLE_OFF.getColor())))
            .overlay(new DynamicDrawable(() -> icon))
            .tooltipBuilder(t -> t.addLine(IKey.lang(tooltipKey)))
            .onMousePressed((IGuiAction.MousePressed) mouseButton -> {
                if (mouseButton != 0) return false;
                sync.setBoolValue(!sync.getBoolValue(), true, true);
                return true;
            });
    }

    private static ButtonWidget<?> settingsButton(IPanelHandler settingsHandler) {
        return new ButtonWidget<>().size(SQUARE_BUTTON_SIZE, SQUARE_BUTTON_SIZE)
            .background(GuiTextures.BUTTON_CLEAN)
            .overlay(GuiTextures.GEAR)
            .tooltipBuilder(t -> t.addLine(IKey.lang("galaxia.gui.airlock_controller.settings_button.tooltip")))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                settingsHandler.openPanel();
                return true;
            });
    }

    /**
     * Re-opens the airlock controller GUI through the server, rebuilding the panel from scratch. This re-resolves the
     * per-room sync values and behavior metrics, so a room that became linked after the GUI was opened shows up.
     */
    private static ButtonWidget<?> refreshButton(TileEntityAirlock tile) {
        return new ButtonWidget<>().size(SQUARE_BUTTON_SIZE, SQUARE_BUTTON_SIZE)
            .background(GuiTextures.BUTTON_CLEAN)
            .overlay(GuiTextures.REFRESH)
            .tooltipBuilder(t -> t.addLine(IKey.lang("galaxia.gui.airlock_controller.refresh_button.tooltip")))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                GuiFactories.tileEntity()
                    .openClient(tile.xCoord, tile.yCoord, tile.zCoord);
                return true;
            });
    }

    /**
     * GT-style title strip: a dark chrome bar across the top of the panel with a subtle top highlight and a
     * green/red status underline (structure validity). The title sits on the left, the settings and refresh buttons
     * on the right.
     */
    private static IWidget headerBar(BooleanSyncValue structureValidSync, IPanelHandler settingsHandler,
        TileEntityAirlock tile) {
        return new ParentWidget<>().left(HEADER_INSET)
            .right(HEADER_INSET)
            .top(HEADER_INSET)
            .height(HEADER_HEIGHT)
            .background(
                new Rectangle().verticalGradient(
                    EnumColors.AIRLOCK_HEADER_GRADIENT_TOP.getColor(),
                    EnumColors.AIRLOCK_HEADER_GRADIENT_BOTTOM.getColor()))
            .child(
                new Widget<>().left(0)
                    .right(0)
                    .top(0)
                    .height(1)
                    .background(new Rectangle().color(EnumColors.AIRLOCK_HEADER_HIGHLIGHT.getColor())))
            .child(
                new Widget<>().left(0)
                    .right(0)
                    .bottom(0)
                    .height(1)
                    .background(
                        new DynamicDrawable(
                            () -> new Rectangle().color(
                                structureValidSync.getBoolValue() ? EnumColors.MAP_COLOR_SIGNAL_POSITIVE.getColor()
                                    : EnumColors.MAP_COLOR_SIGNAL_NEGATIVE.getColor()))))
            .child(
                IKey.lang("galaxia.gui.airlock_controller.title")
                    .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .asWidget()
                    .pos(HEADER_TITLE_X, HEADER_TITLE_Y))
            .child(
                settingsButton(settingsHandler).right(SETTINGS_BUTTON_RIGHT)
                    .top(0))
            .child(
                refreshButton(tile).right(REFRESH_BUTTON_RIGHT)
                    .top(0));
    }

}
