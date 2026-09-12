package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.item.Item;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.station.GalaxiaBehaviors;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

/**
 * HUD-style "screen" that draws the airlock schematic on a dark monitor: the two room wells flanking the central door
 * node. The bezel and blueprint-blue corner brackets are inherited from {@link ScreenWidget}; the wells and the door
 * are composed out of ordinary mui2 widgets (rooms in a {@link Flow} row, their stat rows as fixed-height children).
 * Only the room badges are item art. While the structure is invalid the schematic is hidden and a single empty error
 * panel is shown instead.
 */
public class AirlockScreenWidget extends ScreenWidget {

    /** Inset of the room/door row from the screen edges (keeps the wells inside the bracket band). */
    private static final int SCREEN_PAD = 6;
    /** Gap between the two rooms and the central door. */
    private static final int SECTION_GAP = 4;
    private static final int DOOR_WIDTH = 26;
    /** Inset of the door node interior from its bezel. */
    private static final int DOOR_INSET = 1;
    /** Center gap between the two door panes while closed. */
    private static final int DOOR_SEAM = 2;
    /** Center gap between the two door panes while open. */
    private static final int DOOR_OPEN_GAP = 8;
    /** Gap between consecutive text/stat rows inside a room. */
    private static final int ROW_GAP = 4;
    private static final int TEXT_ROW_HEIGHT = 12;
    /** Height of the "label" text rows that sit above a bar or badge row. */
    private static final int LABEL_ROW_HEIGHT = 10;
    private static final int PROTECTION_ROW_HEIGHT = 24;
    private static final int BADGE_SIZE = 10;
    private static final int BADGE_GAP = 2;
    private static final int OXYGEN_ROW_HEIGHT = 20;
    private static final int BAR_HEIGHT = 4;
    /** Horizontal inset of the text/bar content from the room's border. */
    private static final int ROOM_PAD_X = 6;
    /** Vertical inset of the content from the bottom of the room. */
    private static final int ROOM_PAD_Y = 4;

    // spotless:off
    public static final int PROTECTION_RADIATION = 1 << 0;
    public static final int PROTECTION_PRESSURE  = 1 << 1;
    public static final int PROTECTION_SPORES    = 1 << 2;
    public static final int PROTECTION_WITHER    = 1 << 3;
    public static final int PROTECTION_HEAT      = 1 << 4;
    public static final int PROTECTION_COLD      = 1 << 5;
    // spotless:on

    private final BooleanSupplier doorOpen;

    public AirlockScreenWidget(RoomSync roomA, RoomSync roomB, BooleanSupplier doorOpen,
        BooleanSupplier structureValid) {
        this.doorOpen = doorOpen;
        // ScreenWidget's constructor supplies the bezel and the corner brackets.

        // Schematic (rooms + door) only while the structure is valid.
        child(
            Flow.row()
                .full()
                .padding(SCREEN_PAD)
                .childPadding(SECTION_GAP)
                .child(room(roomA, "galaxia.gui.airlock_controller.room_a"))
                .child(door())
                .child(room(roomB, "galaxia.gui.airlock_controller.room_b"))
                .setEnabledIf(w -> structureValid.getAsBoolean()));

        // Single empty recessed panel shown while the structure is invalid: nothing else is drawn.
        child(
            new Widget<>().full()
                .margin(SCREEN_PAD)
                .background(
                    new Rectangle().color(EnumColors.AIRLOCK_ROOM_FILL.getColor()),
                    new Rectangle().hollow(1)
                        .color(EnumColors.AIRLOCK_ROOM_BORDER.getColor()))
                .setEnabledIf(w -> !structureValid.getAsBoolean()));
    }

    private IWidget room(RoomSync sync, String nameKey) {
        return new RoomWidget(sync, nameKey).expanded()
            .heightRel(1f);
    }

    private IWidget door() {
        return new DoorWidget().width(DOOR_WIDTH)
            .heightRel(1f);
    }

    /** Blueprint-blue accent used for the oxygen fill and the closed door panes. */
    private int accent() {
        return EnumColors.AIRLOCK_SCREEN_BRACKET.getColor();
    }

    /**
     * Per-room values synced through normal per-value sync handlers (one instance per airlock room slot). Every value
     * describes the room itself.
     */
    public record RoomSync(BooleanSupplier present, IntSupplier attachments, IntSupplier volume,
        IntSupplier oxygenLevel, IntSupplier protection, IntSupplier role, BooleanSupplier primary) {}

    private class RoomWidget extends ParentWidget<RoomWidget> {

        private final RoomSync room;

        RoomWidget(RoomSync room, String nameKey) {
            this.room = room;

            background(
                new Rectangle().color(EnumColors.AIRLOCK_ROOM_FILL.getColor()),
                new Rectangle().hollow(1)
                    .color(EnumColors.AIRLOCK_ROOM_BORDER.getColor()));

            // Stat rows flow down the well; rows that hide (no room / protection state) collapse so the rest stay put.
            Flow column = Flow.column()
                .full()
                .padding(ROOM_PAD_X, ROOM_PAD_Y)
                .childPadding(ROW_GAP)
                .collapseDisabledChild(true);

            column.child(textRow(IKey.lang(nameKey), EnumColors.MAP_COLOR_TEXT_TITLE::getColor));
            column.child(roleRow());
            column.child(
                textRow(IKey.lang("galaxia.gui.airlock_controller.no_room"), EnumColors.MAP_COLOR_TEXT_MUTED::getColor)
                    .setEnabledIf(
                        w -> !room.present()
                            .getAsBoolean()));
            column.child(protectionRow());
            column.child(attachmentsRow());
            column.child(volumeRow());
            column.child(oxygenRow());
            child(column);

            // Machined top hairline on the well, matching the panel header highlight.
            child(
                new Widget<>().fullWidth()
                    .height(1)
                    .background(new Rectangle().color(EnumColors.STATION_HEADER_HIGHLIGHT.getColor())));
        }

        private IWidget roleRow() {
            return Flow.row()
                .fullWidth()
                .height(TEXT_ROW_HEIGHT)
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .child(
                    new TextWidget<>(
                        IKey.comp(
                            IKey.lang("galaxia.gui.airlock_controller.room.role"),
                            IKey.str(": "),
                            IKey.dynamic(
                                () -> StatCollector.translateToLocal(
                                    GalaxiaBehaviors.byId(
                                        room.role()
                                            .getAsInt())
                                        .get()
                                        .getUnlocalizedName())))).height(TEXT_ROW_HEIGHT)
                                            .expanded()
                                            .textAlign(Alignment.CenterLeft)
                                            .color(EnumColors.MAP_COLOR_TEXT_BODY::getColor))
                .child(controllerBadge())
                .setEnabledIf(
                    w -> room.present()
                        .getAsBoolean());
        }

        private IWidget protectionRow() {
            return Flow.column()
                .fullWidth()
                .height(PROTECTION_ROW_HEIGHT)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .childPadding(ROW_GAP)
                .collapseDisabledChild(true)
                .child(
                    textRow(
                        IKey.lang("galaxia.gui.airlock_controller.room.protection"),
                        EnumColors.MAP_COLOR_TEXT_BODY::getColor).height(LABEL_ROW_HEIGHT))
                .child(
                    badgeRow().setEnabledIf(
                        w -> room.protection()
                            .getAsInt() != 0))
                .child(
                    textRow(
                        IKey.lang("galaxia.gui.airlock_controller.room.protection.none"),
                        EnumColors.MAP_COLOR_TEXT_MUTED::getColor).height(LABEL_ROW_HEIGHT)
                            .setEnabledIf(
                                w -> room.protection()
                                    .getAsInt() == 0))
                .setEnabledIf(
                    w -> room.present()
                        .getAsBoolean());
        }

        private Flow badgeRow() {
            return Flow.row()
                .coverChildren()
                .childPadding(BADGE_GAP)
                .height(TEXT_ROW_HEIGHT)
                .collapseDisabledChild(true)
                .child(
                    badge(
                        PROTECTION_RADIATION,
                        GalaxiaItemList.RADIATION_PROTECTION.getItem(),
                        "galaxia.gui.airlock_controller.room.protection.radiation"))
                .child(
                    badge(
                        PROTECTION_PRESSURE,
                        GalaxiaItemList.PRESSURE_PROTECTION_HIGH.getItem(),
                        "galaxia.gui.airlock_controller.room.protection.pressure"))
                .child(
                    badge(
                        PROTECTION_SPORES,
                        GalaxiaItemList.SPORE_FILTER.getItem(),
                        "galaxia.gui.airlock_controller.room.protection.spores"))
                .child(
                    badge(
                        PROTECTION_WITHER,
                        GalaxiaItemList.WITHER_PROTECTION.getItem(),
                        "galaxia.gui.airlock_controller.room.protection.wither"))
                .child(
                    badge(
                        PROTECTION_HEAT,
                        GalaxiaItemList.THERMAL_PROTECTION_HOT.getItem(),
                        "galaxia.gui.airlock_controller.room.protection.heat"))
                .child(
                    badge(
                        PROTECTION_COLD,
                        GalaxiaItemList.THERMAL_PROTECTION_COLD.getItem(),
                        "galaxia.gui.airlock_controller.room.protection.cold"));
        }

        private Widget<?> badge(int bit, Item item, String tooltipKey) {
            return new Widget<>().size(BADGE_SIZE)
                .background(new ItemDrawable(item))
                .tooltipBuilder(t -> t.addLine(IKey.lang(tooltipKey)))
                .setEnabledIf(
                    w -> (room.protection()
                        .getAsInt() & bit) != 0);
        }

        private IWidget oxygenRow() {
            return Flow.column()
                .expanded()
                .childPadding(2)
                .child(
                    Flow.row()
                        .fullWidth()
                        .height(LABEL_ROW_HEIGHT)
                        .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                        .child(
                            IKey.lang("galaxia.gui.airlock_controller.room.oxygen")
                                .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                                .asWidget())
                        .child(
                            IKey.dynamic(
                                () -> room.oxygenLevel()
                                    .getAsInt() + "%")
                                .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                                .asWidget()))
                .child(
                    new ParentWidget<>().fullWidth()
                        .height(BAR_HEIGHT)
                        .background(new Rectangle().color(EnumColors.AIRLOCK_ROOM_BAR_BG.getColor()))
                        .child(
                            new Widget<>().heightRel(1f)
                                .width(
                                    () -> Math.clamp(
                                        room.oxygenLevel()
                                            .getAsInt() / 100f,
                                        0,
                                        1),
                                    Unit.Measure.RELATIVE)
                                .background(new DynamicDrawable(() -> new Rectangle().color(accent())))))
                .setEnabledIf(
                    w -> room.present()
                        .getAsBoolean());
        }

        private TextWidget<?> volumeRow() {
            return textRow(
                IKey.comp(
                    IKey.lang("galaxia.gui.airlock_controller.room.volume"),
                    IKey.str(": "),
                    IKey.dynamic(
                        () -> String.valueOf(
                            room.volume()
                                .getAsInt()))),
                EnumColors.MAP_COLOR_TEXT_MUTED::getColor).setEnabledIf(
                    w -> room.present()
                        .getAsBoolean());
        }

        private TextWidget<?> attachmentsRow() {
            return textRow(
                IKey.comp(
                    IKey.lang("galaxia.gui.airlock_controller.room.attachments"),
                    IKey.str(": "),
                    IKey.dynamic(
                        () -> String.valueOf(
                            room.attachments()
                                .getAsInt()))),
                EnumColors.MAP_COLOR_TEXT_MUTED::getColor).setEnabledIf(
                    w -> room.present()
                        .getAsBoolean());
        }

        private TextWidget<?> textRow(IKey key, IntSupplier color) {
            return new TextWidget<>(key).fullWidth()
                .height(TEXT_ROW_HEIGHT)
                .textAlign(Alignment.CenterLeft)
                .color(color);
        }

        /**
         * Placeholder badge for the controller role: the station controller block icon, tinted gold for the primary
         * controller and silver for secondaries. Swap for a dedicated texture when one exists.
         */
        private Widget<?> controllerBadge() {
            return new Widget<>().size(BADGE_SIZE)
                .background(
                    new DynamicDrawable(
                        () -> new Rectangle().color(
                            room.primary()
                                .getAsBoolean() ? EnumColors.AIRLOCK_BADGE_PRIMARY.getColor()
                                    : EnumColors.AIRLOCK_BADGE_SECONDARY.getColor())));
        }
    }

    /**
     * Door node: a recessed well holding two sliding panes around a center seam. The panes are anchored to their side
     * of the well and sized through a {@link Unit} supplier, so the open/closed state stays standard mui2 geometry. The
     * open panes drop to the empty-bar tint so the state reads at a glance.
     */
    private class DoorWidget extends ParentWidget<DoorWidget> {

        DoorWidget() {
            background(
                new Rectangle().color(EnumColors.AIRLOCK_ROOM_FILL.getColor()),
                new Rectangle().hollow(1)
                    .color(EnumColors.AIRLOCK_ROOM_BORDER.getColor()));
            child(pane().left(DOOR_INSET));
            child(pane().right(DOOR_INSET));
        }

        /** One sliding door pane, anchored to its side of the well; the width shrinks as the door opens. */
        private Widget<?> pane() {
            return new Widget<>().top(DOOR_INSET)
                .bottom(DOOR_INSET)
                .width(() -> paneSize(), Unit.Measure.PIXEL)
                .background(
                    new DynamicDrawable(
                        () -> new Rectangle()
                            .color(doorOpen.getAsBoolean() ? EnumColors.AIRLOCK_ROOM_BAR_BG.getColor() : accent())));
        }

        /** Interior width of the well, excluding the bezel on both sides. */
        private int innerWidth() {
            return DOOR_WIDTH - 2 * DOOR_INSET;
        }

        /** Center seam between the two panes: wider while the door is open. */
        private int gap() {
            return doorOpen.getAsBoolean() ? DOOR_OPEN_GAP : DOOR_SEAM;
        }

        /** Width of both door panes: the interior minus the seam, halved, symmetric around the center. */
        private int paneSize() {
            return Math.max(0, (innerWidth() - gap()) / 2);
        }
    }
}
