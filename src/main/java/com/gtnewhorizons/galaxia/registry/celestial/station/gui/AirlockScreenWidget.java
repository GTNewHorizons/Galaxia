package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.item.Item;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
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
 * GT-style "screen" that draws the airlock schematic on a black machine screen:
 * the two rooms flanking the central door. The whole drawing is green while the
 * structure is valid and red while it is not.
 */
public class AirlockScreenWidget extends ParentWidget<AirlockScreenWidget> {

    /** Inset of the room/door row from the screen edges. */
    private static final int SCREEN_PAD = 4;
    /** Gap between the two rooms and the central door. */
    private static final int SECTION_GAP = 4;
    private static final int DOOR_WIDTH = 26;
    /** Inner screen frame inset drawn inside the door block. */
    private static final int DOOR_FRAME = 3;
    private static final int DOOR_SEAM_TOP = 6;
    private static final int DOOR_SEAM_WIDTH = 2;
    private static final int DOOR_SEAM_BOTTOM = 12;
    /** Gap between consecutive text/stat rows inside a room. */
    private static final int ROW_GAP = 4;
    private static final int TEXT_ROW_HEIGHT = 12;
    /** Height of the "label" text rows that sit above a bar or badge row. */
    private static final int LABEL_ROW_HEIGHT = 10;
    private static final int PROTECTION_ROW_HEIGHT = 24;
    private static final int BADGE_SIZE = 10;
    private static final int BADGE_ROW_TOP = 12;
    private static final int BADGE_ROW_HEIGHT = 12;
    private static final int BADGE_GAP = 2;
    private static final int OXYGEN_ROW_HEIGHT = 20;
    private static final int BAR_TOP = 10;
    private static final int BAR_HEIGHT = 4;
    private static final int OXYGEN_PCT_TOP = 9;
    /** Right inset of the role text so it never overlaps the controller badge. */
    private static final int ROLE_TEXT_RIGHT = 20;
    private static final int CONTROLLER_BADGE_TOP = 1;
    /** Horizontal inset of the text/bar content from the room's accent border. */
    private static final int ROOM_PAD_X = 6;
    /** Vertical inset of the content from the top/bottom of the room. */
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
    private final BooleanSupplier structureValid;

    public AirlockScreenWidget(RoomSync roomA, RoomSync roomB, BooleanSupplier doorOpen,
        BooleanSupplier structureValid) {
        this.doorOpen = doorOpen;
        this.structureValid = structureValid;

        background(new Rectangle().color(EnumColors.AIRLOCK_SCREEN_BG.getColor()));

        Flow row = Flow.row()
            .sizeRel(1f, 1f)
            .padding(SCREEN_PAD)
            .childPadding(SECTION_GAP)
            .child(room(roomA, "galaxia.gui.airlock_controller.room_a"))
            .child(door())
            .child(room(roomB, "galaxia.gui.airlock_controller.room_b"));
        child(row);
    }

    private IWidget room(RoomSync sync, String nameKey) {
        return new RoomWidget(sync, nameKey).expanded()
            .heightRel(1f);
    }

    private IWidget door() {
        return new DoorWidget().width(DOOR_WIDTH)
            .heightRel(1f);
    }

    private int accent() {
        return structureValid.getAsBoolean() ? EnumColors.MAP_COLOR_SIGNAL_POSITIVE.getColor()
            : EnumColors.MAP_COLOR_SIGNAL_NEGATIVE.getColor();
    }

    /**
     * Per-room values synced through normal per-value sync handlers (one instance per airlock room slot). Every value
     * describes the room itself
     */
    public record RoomSync(BooleanSupplier present, IntSupplier attachments, IntSupplier volume,
        IntSupplier oxygenLevel, IntSupplier protection, IntSupplier role, BooleanSupplier primary) {}

    private class RoomWidget extends ParentWidget<RoomWidget> {

        private final RoomSync room;

        RoomWidget(RoomSync room, String nameKey) {
            this.room = room;

            Rectangle accentBorder = new Rectangle().hollow(1);
            background(
                new Rectangle().color(EnumColors.AIRLOCK_ROOM_FILL.getColor()),
                new DynamicDrawable(() -> accentBorder.color(accent())));

            Flow column = Flow.column()
                .sizeRel(1f, 1f)
                .padding(0, 0, ROOM_PAD_Y, ROOM_PAD_Y)
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
        }

        private ParentWidget<?> protectionRow() {
            return new ParentWidget<>().left(0)
                .right(0)
                .height(PROTECTION_ROW_HEIGHT)
                .child(
                    textRow(
                        IKey.lang("galaxia.gui.airlock_controller.room.protection"),
                        EnumColors.MAP_COLOR_TEXT_BODY::getColor).top(0)
                            .height(LABEL_ROW_HEIGHT))
                .child(
                    Flow.row()
                        .left(ROOM_PAD_X)
                        .right(ROOM_PAD_X)
                        .top(BADGE_ROW_TOP)
                        .height(BADGE_ROW_HEIGHT)
                        .childPadding(BADGE_GAP)
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
                                "galaxia.gui.airlock_controller.room.protection.cold")))
                .child(
                    textRow(
                        IKey.lang("galaxia.gui.airlock_controller.room.protection.none"),
                        EnumColors.MAP_COLOR_TEXT_MUTED::getColor).top(BADGE_ROW_TOP)
                            .height(BADGE_ROW_HEIGHT)
                            .setEnabledIf(
                                w -> room.protection()
                                    .getAsInt() == 0))
                .setEnabledIf(
                    w -> room.present()
                        .getAsBoolean());
        }

        private Widget<?> badge(int bit, Item item, String tooltipKey) {
            return new Widget<>().size(BADGE_SIZE)
                .background(new ItemDrawable(item))
                .tooltipBuilder(t -> t.addLine(IKey.lang(tooltipKey)))
                .setEnabledIf(
                    w -> (room.protection()
                        .getAsInt() & bit) != 0);
        }

        private ParentWidget<?> oxygenRow() {
            return new ParentWidget<>().left(0)
                .right(0)
                .height(OXYGEN_ROW_HEIGHT)
                .child(
                    textRow(
                        IKey.lang("galaxia.gui.airlock_controller.room.oxygen"),
                        EnumColors.MAP_COLOR_TEXT_BODY::getColor).top(0)
                            .height(LABEL_ROW_HEIGHT))
                .child(
                    new Widget<>().left(ROOM_PAD_X)
                        .right(ROOM_PAD_X)
                        .top(BAR_TOP)
                        .height(BAR_HEIGHT)
                        .background(new Rectangle().color(EnumColors.AIRLOCK_ROOM_BAR_BG.getColor())))
                .child(
                    new Widget<>().left(ROOM_PAD_X)
                        .top(BAR_TOP)
                        .height(BAR_HEIGHT)
                        .width(
                            () -> (double) Math.clamp(
                                room.oxygenLevel()
                                    .getAsInt() / 100f,
                                0,
                                1),
                            Unit.Measure.RELATIVE)
                        .background(new DynamicDrawable(() -> new Rectangle().color(accent()))))
                .child(
                    textRow(
                        IKey.dynamic(
                            () -> room.oxygenLevel()
                                .getAsInt() + "%"),
                        EnumColors.MAP_COLOR_TEXT_TITLE::getColor).top(OXYGEN_PCT_TOP)
                            .height(LABEL_ROW_HEIGHT)
                            .textAlign(Alignment.TopCenter))
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

        private ParentWidget<?> roleRow() {
            return new ParentWidget<>().left(0)
                .right(0)
                .height(TEXT_ROW_HEIGHT)
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
                                        .getUnlocalizedName())))).left(ROOM_PAD_X)
                                            .right(ROLE_TEXT_RIGHT)
                                            .height(TEXT_ROW_HEIGHT)
                                            .textAlign(Alignment.TopLeft)
                                            .color(EnumColors.MAP_COLOR_TEXT_BODY::getColor))
                .child(
                    controllerBadge().right(ROOM_PAD_X)
                        .top(CONTROLLER_BADGE_TOP))
                .setEnabledIf(
                    w -> room.present()
                        .getAsBoolean());
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

        private TextWidget<?> textRow(IKey key, IntSupplier color) {
            return new TextWidget<>(key).left(ROOM_PAD_X)
                .right(ROOM_PAD_X)
                .height(TEXT_ROW_HEIGHT)
                .textAlign(Alignment.TopLeft)
                .color(color);
        }
    }

    private class DoorWidget extends Widget<DoorWidget> {

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            int w = getArea().width;
            int h = getArea().height;
            boolean closed = !doorOpen.getAsBoolean();
            int accent = accent();
            int signalBad = EnumColors.MAP_COLOR_SIGNAL_NEGATIVE.getColor();
            GuiDraw.drawRect(0, 0, w, h, closed ? accent : signalBad);
            GuiDraw.drawRect(
                DOOR_FRAME,
                DOOR_FRAME,
                w - 2 * DOOR_FRAME,
                h - 2 * DOOR_FRAME,
                EnumColors.AIRLOCK_SCREEN_BG.getColor());
            GuiDraw.drawRect(
                (float) (w - DOOR_SEAM_WIDTH) / 2,
                DOOR_SEAM_TOP,
                DOOR_SEAM_WIDTH,
                h - DOOR_SEAM_BOTTOM,
                closed ? accent : signalBad);
        }
    }
}
