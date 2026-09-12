package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;

public final class WidgetOutline {

    private WidgetOutline() {}

    public static ParentWidget<?> create(ParentWidget<?> target, int thickness, int color) {
        return new OutlineOverlayWidget(target, thickness, color);
    }

    private static final class OutlineOverlayWidget extends ParentWidget<OutlineOverlayWidget> {

        private final ParentWidget<?> target;
        private final int thickness;

        private OutlineOverlayWidget(ParentWidget<?> target, int thickness, int color) {
            this.target = target;
            this.thickness = thickness;
            background(
                new Rectangle().hollow(thickness)
                    .color(color));
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            syncToTarget();
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            syncToTarget();
            if (!isEnabled()) return;
            super.drawBackground(context, widgetTheme);
        }

        private void syncToTarget() {
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

}
