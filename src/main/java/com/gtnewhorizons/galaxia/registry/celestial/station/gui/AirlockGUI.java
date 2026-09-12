package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import java.util.List;

import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
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
    /** Height of the schematic screen between the header and the toggle row. */
    private static final int SCREEN_HEIGHT = 140;
    /** Height of the behavior toggle row under the screen. */
    private static final int TOGGLE_ROW_HEIGHT = 18;
    /** Gap between the toggle buttons in the behavior row. */
    private static final int TOGGLE_GAP = 4;
    /** Side of the square icon buttons in the header and settings panel. */
    private static final int SETTINGS_WIDTH = 160;
    private static final int SETTINGS_HEIGHT = 176;
    private static final int RESET_WIDTH = 64;
    private static final int RESET_HEIGHT = 18;

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

        return StationPanel.defaultPanel("galaxia:airlock_controller", PANEL_WIDTH, PANEL_HEIGHT)
            .child(
                Flow.col()
                    .full()
                    .padding(PANEL_PAD)
                    .child(
                        new StationHeaderWidget(
                            IKey.lang("galaxia.gui.airlock_controller.title")
                                .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor()),
                            new DynamicDrawable(
                                () -> new Rectangle().color(
                                    structureValidSync.getBoolValue() ? EnumColors.MAP_COLOR_SIGNAL_POSITIVE.getColor()
                                        : EnumColors.MAP_COLOR_SIGNAL_NEGATIVE.getColor()))).fullWidth()
                                            .button(StationButtonWidget.refreshButton(tile))
                                            .button(StationButtonWidget.settingsButton(settingsHandler)))
                    .child(
                        new AirlockScreenWidget(
                            roomA,
                            roomB,
                            doorOpenSync::getBoolValue,
                            structureValidSync::getBoolValue).fullWidth()
                                .height(SCREEN_HEIGHT))
                    .child(
                        Flow.row()
                            // .size(PANEL_WIDTH - 2 * PANEL_PAD, TOGGLE_ROW_HEIGHT)
                            .padding(PANEL_PAD)
                            .coverChildren()
                            .childPadding(TOGGLE_GAP)
                            .child(
                                StationButtonWidget.toggleButton(
                                    proximityOpeningSync,
                                    "galaxia.gui.airlock_controller.toggle.proximity_opening.tooltip",
                                    GuiTextures.MOVE_TO))
                            .child(
                                StationButtonWidget.toggleButton(
                                    proximityAutoCloseSync,
                                    "galaxia.gui.airlock_controller.toggle.proximity_auto_close.tooltip",
                                    GuiTextures.STOP))
                            .child(
                                StationButtonWidget.toggleButton(
                                    redstoneControlSync,
                                    "galaxia.gui.airlock_controller.toggle.redstone_control.tooltip",
                                    GuiTextures.WRENCH))
                            .child(
                                StationButtonWidget.toggleButton(
                                    manualClickSync,
                                    "galaxia.gui.airlock_controller.toggle.manual_click.tooltip",
                                    GuiTextures.MAIN_HANDLE))
                            .child(
                                StationButtonWidget.toggleButton(
                                    autoSealSync,
                                    "galaxia.gui.airlock_controller.toggle.auto_seal.tooltip",
                                    GuiTextures.LOCKED))));
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

        return StationPanel.defaultPanel("galaxia:airlock_settings", SETTINGS_WIDTH, SETTINGS_HEIGHT)
            .child(
                new StationSettingsWidget(
                    IKey.lang("galaxia.gui.airlock_controller.settings.title")
                        .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor()),
                    settingsHandler).numberSetting(
                        IKey.lang("galaxia.gui.airlock_controller.settings.check_interval")
                            .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor()),
                        checkIntervalSync,
                        TileEntityAirlock.MIN_CHECK_INTERVAL,
                        TileEntityAirlock.MAX_CHECK_INTERVAL)

                        .numberSetting(
                            IKey.lang("galaxia.gui.airlock_controller.settings.close_delay")
                                .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor()),
                            closeDelaySync,
                            TileEntityAirlock.MIN_CLOSE_DELAY,
                            TileEntityAirlock.MAX_CLOSE_DELAY)
                        .numberSetting(
                            IKey.lang("galaxia.gui.airlock_controller.settings.proximity_range")
                                .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor()),
                            proximityRangeSync,
                            TileEntityAirlock.MIN_PROXIMITY_RANGE,
                            TileEntityAirlock.MAX_PROXIMITY_RANGE)
                        .footer(resetButton(checkIntervalSync, closeDelaySync, proximityRangeSync).marginTop(4)));
    }

    private static ButtonWidget<?> resetButton(IntSyncValue checkIntervalSync, IntSyncValue closeDelaySync,
        IntSyncValue proximityRangeSync) {
        return new ButtonWidget<>().size(RESET_WIDTH, RESET_HEIGHT)
            .background(
                new Rectangle().color(EnumColors.AIRLOCK_PANEL_EDGE.getColor()),
                new Rectangle().hollow(1)
                    .color(EnumColors.AIRLOCK_PANEL_BORDER.getColor()))
            .hoverBackground(new Rectangle().color(EnumColors.STATION_TOGGLE_ON.getColor()))
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
}
