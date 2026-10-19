package com.gtnewhorizons.galaxia.client.gui.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;

/**
 * Small building blocks for Galaxia's windows on top of ModularUI2, in the shared theme. Every factory returns a plain
 * MUI2 widget, so they mix freely with hand-made ones
 */
public final class GalaxiaUi {

    /** Inner padding of a window or well */
    public static final int PAD = 4;
    /** Space between rows and between widgets in a row */
    public static final int GAP = 2;
    public static final int BUTTON_H = 14;
    public static final int ICON = 16;

    private GalaxiaUi() {}

    // region Text

    public static TextWidget<?> text(String text) {
        return IKey.str(text)
            .color(GalaxiaGuiStyle.TEXT)
            .asWidget()
            .alignment(Alignment.CenterLeft);
    }

    public static TextWidget<?> text(Supplier<String> text) {
        return IKey.dynamic(text::get)
            .color(GalaxiaGuiStyle.TEXT)
            .asWidget()
            .alignment(Alignment.CenterLeft);
    }

    public static TextWidget<?> dim(String text) {
        return IKey.str(text)
            .color(GalaxiaGuiStyle.TEXT_DIM)
            .asWidget()
            .alignment(Alignment.CenterLeft);
    }

    /** Text and colour both following the game, for statuses */
    public static TextWidget<?> text(Supplier<String> text, IntSupplier color) {
        return IKey.dynamic(text::get)
            .asWidget()
            .color(color)
            .alignment(Alignment.CenterLeft);
    }

    public static TextWidget<?> colored(Supplier<String> text, int color) {
        return IKey.dynamic(text::get)
            .color(color)
            .asWidget()
            .alignment(Alignment.CenterLeft);
    }

    /** One line cut to fit the width, the whole text shown on hover when it was cut */
    public static TextWidget<?> clipped(String text, int maxWidth, int color) {
        String shown = clip(text, maxWidth);
        TextWidget<?> widget = IKey.str(shown)
            .color(color)
            .asWidget()
            .alignment(Alignment.CenterLeft);
        if (!shown.equals(text)) widget.tooltip(t -> t.addLine(text));
        return widget;
    }

    /** Label and value on one line, the label dimmed */
    public static Flow keyValue(String label, Supplier<String> value) {
        return Flow.row()
            .coverChildren()
            .childPadding(4)
            .child(dim(label))
            .child(text(value));
    }

    public static Flow keyValue(String label, String value, int maxWidth) {
        return Flow.row()
            .coverChildren()
            .childPadding(4)
            .child(dim(label))
            .child(clipped(value, maxWidth - width(label) - 4, GalaxiaGuiStyle.TEXT));
    }

    /** Dim heading over a group of rows */
    public static TextWidget<?> section(String text) {
        return dim(text).marginTop(GAP);
    }

    public static int width(String text) {
        FontRenderer font = font();
        return font == null ? text.length() * 6 : font.getStringWidth(text);
    }

    public static String clip(String text, int maxWidth) {
        if (text == null) return "";
        FontRenderer font = font();
        if (font == null || font.getStringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        return font.trimStringToWidth(text, Math.max(0, maxWidth - font.getStringWidth(ellipsis))) + ellipsis;
    }

    private static FontRenderer font() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.fontRenderer;
    }

    /** One line of a text list with its own colour; an empty text leaves a blank line */
    public record Line(String text, int color) {}

    /**
     * Column of text lines that follow the game, read once a tick, for texts collected in one place such as module
     * statuses
     *
     * @param lines    The lines as they stand now
     * @param maxLines Most lines shown, the rest is left out
     * @param width    Width each line is cut to
     */
    public static Flow lines(Supplier<List<Line>> lines, int maxLines, int width) {
        List<List<Line>> current = new java.util.ArrayList<>(List.of(List.of()));
        Flow column = Flow.column()
            .collapseDisabledChild()
            .coverChildren()
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .childPadding(1);
        column.onUpdateListener(w -> current.set(0, lines.get()), true);
        for (int i = 0; i < maxLines; i++) {
            int index = i;
            column.child(
                text(
                    () -> index < current.get(0)
                        .size() ? clip(
                            current.get(0)
                                .get(index)
                                .text(),
                            width) : "",
                    () -> index < current.get(0)
                        .size() ? current.get(0)
                            .get(index)
                            .color() : GalaxiaGuiStyle.TEXT).setEnabledIf(
                                w -> index < current.get(0)
                                    .size()));
        }
        return column;
    }

    // endregion

    // region Mouse

    /** A point of the screen in the widget's own coordinates, through every scale and scroll above it */
    public static int[] toLocal(IWidget widget, int screenX, int screenY) {
        var matrix = LocatedWidget.of(widget)
            .getTransformationMatrix();
        return new int[] { matrix.unTransformX(screenX, screenY), matrix.unTransformY(screenX, screenY) };
    }

    /** Where a widget's top left corner lies in another widget's coordinates, through every scale and scroll */
    public static int[] positionIn(IWidget widget, IWidget space) {
        var matrix = LocatedWidget.of(widget)
            .getTransformationMatrix();
        return toLocal(space, matrix.transformX(0, 0), matrix.transformY(0, 0));
    }

    /** Whether a point of the screen lies on the widget */
    public static boolean contains(IWidget widget, int screenX, int screenY) {
        int[] local = toLocal(widget, screenX, screenY);
        return local[0] >= 0 && local[1] >= 0
            && local[0] < widget.getArea().width
            && local[1] < widget.getArea().height;
    }

    // endregion

    // region Containers

    /** Row of widgets; widgets switched off with {@code setEnabledIf} take no space */
    public static Flow row() {
        return Flow.row()
            .collapseDisabledChild()
            .coverChildren()
            .childPadding(GAP)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
    }

    /** Column of rows; rows switched off with {@code setEnabledIf} take no space */
    public static Flow column() {
        return Flow.column()
            .collapseDisabledChild()
            .coverChildren()
            .childPadding(GAP)
            .crossAxisAlignment(Alignment.CrossAxis.START);
    }

    /** Sunken area grouping rows */
    public static Flow well() {
        return column().padding(PAD)
            .background(EnumTextures.GUI_WELL.getImage());
    }

    /** Vertical list as tall as its content up to the given height, then scrolling */
    public static ListWidget<IWidget, ?> scroll(int maxHeight) {
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.scrollDirection(scrollbar())
            .coverChildrenWidth()
            .maxSize(maxHeight)
            .crossAxisAlignment(Alignment.CrossAxis.START);
        return list;
    }

    public static VerticalScrollData scrollbar() {
        return new GalaxiaScrollbar();
    }

    /** Thin rule across the parent, for separating groups */
    public static Widget<?> rule() {
        IDrawable line = (context, x, y, width, height, theme) -> GuiDraw
            .drawRect(x, y, width, 1, GalaxiaGuiStyle.BAR_EDGE);
        return new Widget<>().widthRel(1f)
            .height(1)
            .background(line);
    }

    // endregion

    // region Buttons

    /** Button sized to its text */
    public static ButtonWidget<?> button(String text, Runnable onClick) {
        return button(IKey.str(text), width(text) + 10, onClick);
    }

    public static ButtonWidget<?> button(IKey text, int width, Runnable onClick) {
        return GalaxiaGuiStyle.textButton(text)
            .size(width, BUTTON_H)
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                onClick.run();
                return true;
            });
    }

    /** Button that turns dark with grey text and ignores clicks while it can't be used */
    public static ButtonWidget<?> button(String text, BooleanSupplier enabled, Runnable onClick) {
        return button(() -> text, width(text) + 10, enabled, onClick);
    }

    /** Button whose label follows the game, dark with grey text while it can't be used */
    public static ButtonWidget<?> button(Supplier<String> text, int width, BooleanSupplier enabled, Runnable onClick) {
        IDrawable on = IKey.dynamic(() -> clip(text.get(), width - 4))
            .color(GalaxiaGuiStyle.TEXT)
            .shadow(true);
        IDrawable off = IKey.dynamic(() -> clip(text.get(), width - 4))
            .color(GalaxiaGuiStyle.TEXT_DIM)
            .shadow(true);
        return new ButtonWidget<>().size(width, BUTTON_H)
            .background(
                new DynamicDrawable(
                    () -> (enabled.getAsBoolean() ? EnumTextures.GUI_BUTTON : EnumTextures.GUI_BUTTON_DISABLED)
                        .getImage()))
            .hoverBackground(
                new DynamicDrawable(
                    () -> (enabled.getAsBoolean() ? EnumTextures.GUI_BUTTON_HOVERED : EnumTextures.GUI_BUTTON_DISABLED)
                        .getImage()))
            .overlay(new DynamicDrawable(() -> enabled.getAsBoolean() ? on : off))
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                if (enabled.getAsBoolean()) onClick.run();
                return true;
            });
    }

    /** Button showing whether something is on, highlighted while it is */
    public static ButtonWidget<?> toggle(Supplier<String> text, int width, BooleanSupplier on, Runnable onClick) {
        return new ButtonWidget<>().size(width, BUTTON_H)
            .background(
                new DynamicDrawable(
                    () -> (on.getAsBoolean() ? EnumTextures.GUI_BUTTON_SELECTED : EnumTextures.GUI_BUTTON).getImage()))
            .hoverBackground(EnumTextures.GUI_BUTTON_HOVERED.getImage())
            .overlay(
                IKey.dynamic(text::get)
                    .color(GalaxiaGuiStyle.TEXT)
                    .shadow(true))
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                onClick.run();
                return true;
            });
    }

    /**
     * One option of a choice: highlighted while chosen, dark with grey text while it can't be chosen
     *
     * @param text     Label of the option
     * @param width    Width of the button
     * @param selected Whether the option is the chosen one
     * @param enabled  Whether the option can be chosen now
     * @param onPick   What choosing it does
     */
    public static ButtonWidget<?> choice(Supplier<String> text, int width, BooleanSupplier selected,
        BooleanSupplier enabled, Runnable onPick) {
        IDrawable on = IKey.dynamic(() -> clip(text.get(), width - 4))
            .color(GalaxiaGuiStyle.TEXT)
            .shadow(true);
        IDrawable off = IKey.dynamic(() -> clip(text.get(), width - 4))
            .color(GalaxiaGuiStyle.TEXT_DIM)
            .shadow(true);
        return new ButtonWidget<>().size(width, BUTTON_H)
            .background(
                new DynamicDrawable(
                    () -> (selected.getAsBoolean() ? EnumTextures.GUI_BUTTON_SELECTED
                        : enabled.getAsBoolean() ? EnumTextures.GUI_BUTTON : EnumTextures.GUI_BUTTON_DISABLED)
                            .getImage()))
            .hoverBackground(
                new DynamicDrawable(
                    () -> (selected.getAsBoolean() ? EnumTextures.GUI_BUTTON_SELECTED
                        : enabled.getAsBoolean() ? EnumTextures.GUI_BUTTON_HOVERED : EnumTextures.GUI_BUTTON_DISABLED)
                            .getImage()))
            .overlay(new DynamicDrawable(() -> enabled.getAsBoolean() || selected.getAsBoolean() ? on : off))
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                if (enabled.getAsBoolean()) onPick.run();
                return true;
            });
    }

    /**
     * Flat button for rows of a list: nothing behind it until hovered. Give it its content with {@code child} or
     * {@code overlay}
     */
    public static ButtonWidget<?> flatButton(int width, int height, Runnable onClick) {
        return new ButtonWidget<>().size(width, height)
            .background(IDrawable.EMPTY)
            .hoverBackground(EnumTextures.GUI_BUTTON_HOVERED.getImage())
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                onClick.run();
                return true;
            });
    }

    /**
     * Raised card to pick from a grid, holding several lines given with {@code child}; its content shows through
     * clicks to the card
     */
    public static ButtonWidget<?> card(int width, int height, Runnable onClick) {
        return GalaxiaGuiStyle.button()
            .size(width, height)
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                onClick.run();
                return true;
            });
    }

    /** Square button with an icon and a tooltip */
    public static ButtonWidget<?> iconButton(UITexture icon, String tooltip, Runnable onClick) {
        ButtonWidget<?> button = GalaxiaGuiStyle.button()
            .size(ICON)
            .overlay(icon)
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                onClick.run();
                return true;
            });
        if (tooltip != null) button.tooltip(t -> t.addLine(tooltip));
        return button;
    }

    /** Square icon button, dark and ignoring clicks while it can't be used */
    public static ButtonWidget<?> iconButton(IDrawable icon, Supplier<String> tooltip, BooleanSupplier enabled,
        Runnable onClick) {
        return new ButtonWidget<>().size(ICON)
            .background(
                new DynamicDrawable(
                    () -> (enabled.getAsBoolean() ? EnumTextures.GUI_BUTTON : EnumTextures.GUI_BUTTON_DISABLED)
                        .getImage()))
            .hoverBackground(
                new DynamicDrawable(
                    () -> (enabled.getAsBoolean() ? EnumTextures.GUI_BUTTON_HOVERED : EnumTextures.GUI_BUTTON_DISABLED)
                        .getImage()))
            .overlay(icon)
            .tooltipDynamic(t -> t.addLine(tooltip.get()))
            .onUpdateListener(ButtonWidget::markTooltipDirty, true)
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                if (enabled.getAsBoolean()) onClick.run();
                return true;
            });
    }

    /**
     * Back on the left and the decision on the right, the way dialogs are laid out across the mod
     *
     * @param back       Label of the button that leaves without changes
     * @param onBack     What leaving does
     * @param confirm    Label of the button that applies the decision
     * @param canConfirm Whether the decision can be made yet
     * @param onConfirm  What applying does
     */
    public static Flow footer(String back, Runnable onBack, String confirm, BooleanSupplier canConfirm,
        Runnable onConfirm) {
        // Sized by its buttons, so a narrow window grows to fit them instead of laying them over each other
        return row().childPadding(GAP * 2)
            .marginTop(GAP)
            .child(button(back, onBack))
            .child(confirm(confirm, canConfirm, onConfirm));
    }

    /** The decision of a window that its title bar closes, greyed out until it can be made */
    public static ButtonWidget<?> confirm(String label, BooleanSupplier canConfirm, Runnable onConfirm) {
        ButtonWidget<?> button = button(label, () -> { if (canConfirm.getAsBoolean()) onConfirm.run(); });
        IDrawable ready = IKey.str(label)
            .color(GalaxiaGuiStyle.TEXT)
            .shadow(true);
        IDrawable waiting = IKey.str(label)
            .color(GalaxiaGuiStyle.TEXT_DIM)
            .shadow(true);
        return button.overlay(new DynamicDrawable(() -> canConfirm.getAsBoolean() ? ready : waiting));
    }

    // endregion

    // region Inputs

    public static TextFieldWidget textField(String hint, int width) {
        TextFieldWidget field = new TextFieldWidget().size(width, BUTTON_H)
            .background(EnumTextures.GUI_WELL.getImage())
            .setTextColor(GalaxiaGuiStyle.TEXT);
        if (hint != null) field.hintText(hint)
            .hintColor(GalaxiaGuiStyle.TEXT_DIM);
        return field;
    }

    // endregion

    // region Items

    /** Item icon with its name on hover */
    public static Widget<?> item(ItemStack stack) {
        ItemStack shown = stack.copy();
        return new ItemDrawable(shown).asWidget()
            .size(ICON)
            .tooltip(t -> t.addLine(shown.getDisplayName()));
    }

    /**
     * Grid of item icons that shows only the first few, with a button opening a window with all of them
     *
     * @param id       Name of the grid, the button is named {@code id + ".showAll"}
     * @param stacks   Items to show
     * @param columns  Icons per row
     * @param maxShown Icons shown before the button takes the last place
     * @param title    Title of the window with every icon
     */
    public static Flow itemGrid(String id, List<ItemStack> stacks, int columns, int maxShown, String title) {
        // One frame around every icon, so they don't hang on the bare panel
        Flow grid = well().childPadding(GAP);
        boolean truncated = stacks.size() > maxShown;
        int shown = truncated ? maxShown - 1 : stacks.size();
        Flow line = null;
        for (int i = 0; i < shown; i++) {
            if (i % columns == 0) {
                line = row();
                grid.child(line);
            }
            line.child(item(stacks.get(i)));
        }
        if (truncated) {
            if (shown % columns == 0) {
                line = row();
                grid.child(line);
            }
            // Takes the place of one more icon, so the grid keeps its shape
            ButtonWidget<?> more = button(IKey.str("..."), ICON, () -> {}).name(id + ".showAll")
                .height(ICON)
                .tooltip(
                    t -> t.addLine(
                        StatCollector.translateToLocalFormatted("galaxia.gui.common.show_all", stacks.size())));
            more.onMousePressed(mouse -> {
                if (mouse != 0) return false;
                WindowLayer layer = WindowLayer.of(more);
                if (layer != null) layer.toggle(
                    WindowSpec.of(id + ".all", IKey.str(title))
                        .body(
                            column -> column.add(itemGrid(id + ".all", stacks, columns * 2, Integer.MAX_VALUE, title)))
                        .anchor((lw, lh, w, h) -> beside(more, lw, lh, w, h))
                        .maxHeight(lh(layer) * 2 / 3));
                return true;
            });
            line.child(more);
        }
        return grid;
    }

    /** Next to the widget on the right, or left of its window when the right has no room */
    private static int[] beside(IWidget widget, int layerWidth, int layerHeight, int width, int height) {
        int x = widget.getArea().x + ICON + PAD;
        if (x + width > layerWidth) {
            IWidget window = widget;
            while (window != null && !(window instanceof GalaxiaWindow)) window = window.getParent();
            int left = window != null ? window.getArea().x : widget.getArea().x;
            x = Math.max(0, left - width - PAD);
        }
        int y = Math.max(0, Math.min(widget.getArea().y - height / 2, layerHeight - height));
        return new int[] { x, y };
    }

    private static int lh(WindowLayer layer) {
        return Math.max(120, layer.getArea().height);
    }

    // endregion

    /** A column the window body is assembled in, one call per row */
    public static final class Column {

        private final Flow flow;

        Column(Flow flow) {
            this.flow = flow;
        }

        public Column add(IWidget widget) {
            flow.child(widget);
            return this;
        }

        public Column text(String text) {
            return add(GalaxiaUi.text(text));
        }

        public Column text(Supplier<String> text) {
            return add(GalaxiaUi.text(text));
        }

        public Column dim(String text) {
            return add(GalaxiaUi.dim(text));
        }

        public Column keyValue(String label, Supplier<String> value) {
            return add(GalaxiaUi.keyValue(label, value));
        }

        public Column keyValue(String label, String value, int maxWidth) {
            return add(GalaxiaUi.keyValue(label, value, maxWidth));
        }

        public Column section(String text) {
            return add(GalaxiaUi.section(text));
        }

        public Column row(IWidget... widgets) {
            Flow row = GalaxiaUi.row();
            for (IWidget widget : widgets) row.child(widget);
            return add(row);
        }

        public Column rule() {
            return add(GalaxiaUi.rule());
        }

        public Column each(Consumer<Column> part) {
            part.accept(this);
            return this;
        }

        public Flow flow() {
            return flow;
        }
    }

    /** Wraps any parent so window code can address it like a column */
    public static Column columnOf(Flow flow) {
        return new Column(flow);
    }
}
