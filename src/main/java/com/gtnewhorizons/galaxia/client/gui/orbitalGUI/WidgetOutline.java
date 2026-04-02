package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;

public final class WidgetOutline {

    private WidgetOutline() {}

    public static ParentWidget<?> create(ParentWidget<?> target, int thickness, int color) {
        return create(target, thickness, color, color, color, color);
    }

    public static ParentWidget<?> create(ParentWidget<?> target, int thickness, int topColor, int rightColor,
        int bottomColor, int leftColor) {
        return new OutlineOverlayWidget(target, thickness, topColor, rightColor, bottomColor, leftColor);
    }

    private static void drawSolid(GuiContext context, int x, int y, int width, int height, int color) {
        Gui.drawRect(x, y, x + width, y + height, color);
    }

    private static final class OutlineOverlayWidget extends ParentWidget<OutlineOverlayWidget> {

        private final ParentWidget<?> target;
        private final int thickness;

        private OutlineOverlayWidget(ParentWidget<?> target, int thickness, int topColor, int rightColor,
            int bottomColor, int leftColor) {
            this.target = target;
            this.thickness = thickness;
            child(
                new BorderEdgeWidget(topColor).left(0)
                    .top(0)
                    .widthRel(1f)
                    .height(thickness));
            child(
                new BorderEdgeWidget(leftColor).left(0)
                    .top(0)
                    .width(thickness)
                    .heightRel(1f));
            child(
                new BorderEdgeWidget(rightColor).right(0)
                    .top(0)
                    .width(thickness)
                    .heightRel(1f));
            child(
                new BorderEdgeWidget(bottomColor).left(0)
                    .bottom(0)
                    .widthRel(1f)
                    .height(thickness));
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (target == null || !target.isValid()) {
                setEnabled(false);
                return;
            }
            setEnabled(true);
            left(target.getArea().rx - thickness);
            top(target.getArea().ry - thickness);
            size(target.getArea().width + thickness * 2, target.getArea().height + thickness * 2);
        }

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }
    }

    private static final class BorderEdgeWidget extends ParentWidget<BorderEdgeWidget> {

        private final int color;

        private BorderEdgeWidget(int color) {
            this.color = color;
        }

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            drawSolid(context, 0, 0, getArea().width, getArea().height, color);
        }
    }
}
