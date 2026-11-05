package com.gtnewhorizons.galaxia.client.gui.ui;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;

/**
 * A window on a {@link WindowLayer}: an opaque frame sized to its content, with a title bar it is dragged by. The
 * title bar shows two grooves as a hint, and a close button on the right. A body taller than the layer allows scrolls
 */
public final class GalaxiaWindow extends Flow {

    /** Smallest part of the window kept on the layer when it is dragged off an edge */
    private static final int KEEP_VISIBLE = 40;
    private static final int GRIP_MIN = 24;

    final WindowSpec spec;
    private final WindowLayer layer;
    private final Flow content;
    boolean placed;
    /** Where the window was put, which its area only shows after the next layout */
    private int x = Integer.MIN_VALUE, y = Integer.MIN_VALUE;
    private boolean rebuildPending;
    private int lastWidth, lastHeight;
    private int lastShape;

    GalaxiaWindow(WindowSpec spec, WindowLayer layer) {
        super(GuiAxis.Y);
        this.spec = spec;
        this.layer = layer;
        name(spec.id);
        padding(GalaxiaUi.PAD);
        childPadding(GalaxiaUi.GAP);
        crossAxisAlignment(Alignment.CrossAxis.START);
        coverChildrenHeight();
        coverChildrenWidth(minWidth(spec));

        child(new TitleBar());
        content = GalaxiaUi.column();
        ListWidget<IWidget, ?> body = GalaxiaUi.scroll(0)
            .maxSize(() -> maxBodyHeight());
        body.child(content);
        child(body);
        fill();
    }

    public WindowSpec spec() {
        return spec;
    }

    public String id() {
        return spec.id;
    }

    /** Builds the body again from the spec, for content that changed shape */
    public void rebuild() {
        content.removeAll();
        fill();
        if (isValid()) scheduleResize();
    }

    /** Builds the body again on the next update, for clicks inside the body that change its shape */
    public void rebuildLater() {
        rebuildPending = true;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (spec.onUpdate != null) spec.onUpdate.run();
        // Rows shown or hidden and text that changed length need the window laid out again to fit
        int shape = shapeOf(content);
        if (shape != lastShape) {
            lastShape = shape;
            scheduleResize();
        }
        if (!rebuildPending) return;
        rebuildPending = false;
        rebuild();
    }

    public void close() {
        layer.close(spec.id);
    }

    /** What decides the size of the body: which widgets are shown and the length of every text */
    private static int shapeOf(IWidget widget) {
        int shape = widget.isEnabled() ? 1 : 0;
        if (widget instanceof TextWidget<?>text && text.getKey() != null) {
            shape = 31 * shape + GalaxiaUi.width(
                text.getKey()
                    .get());
        }
        if (widget.hasChildren()) {
            for (IWidget child : widget.getChildren()) shape = 31 * shape + shapeOf(child);
        }
        return shape;
    }

    private void fill() {
        spec.body.accept(new GalaxiaUi.Column(content));
    }

    private int maxBodyHeight() {
        int layerHeight = layer.getArea().height;
        int limit = spec.maxHeight > 0 ? spec.maxHeight : layerHeight * 3 / 4;
        // Never taller than the layer can show below the title bar
        return Math
            .max(GalaxiaUi.ICON, Math.min(limit, layerHeight - GalaxiaUi.ICON - GalaxiaUi.PAD * 3 - GalaxiaUi.GAP));
    }

    private static int minWidth(WindowSpec spec) {
        int width = GalaxiaUi.width(spec.title.get()) + GalaxiaUi.GAP * 2 + GRIP_MIN;
        width += spec.headerButtons.size() * (GalaxiaUi.ICON + GalaxiaUi.GAP);
        if (spec.closable) width += GalaxiaUi.ICON + GalaxiaUi.GAP;
        return width + GalaxiaUi.PAD * 2;
    }

    /** Moves the window, keeping enough of it on the layer to grab it again */
    void moveTo(int x, int y) {
        int layerWidth = layer.getArea().width;
        int layerHeight = layer.getArea().height;
        int width = getArea().width;
        x = Math.max(KEEP_VISIBLE - width, Math.min(layerWidth - KEEP_VISIBLE, x));
        y = Math.max(0, Math.min(layerHeight - GalaxiaUi.ICON - GalaxiaUi.PAD * 2, y));
        if (x == this.x && y == this.y) return;
        this.x = x;
        this.y = y;
        left(x).top(y);
        scheduleResize();
    }

    /** Pulls the window back onto the layer after the layer got smaller */
    void keepOnLayer() {
        moveTo(x, y);
    }

    /** Whether the window stands where it was put, so its area can be read */
    public boolean isPlaced() {
        return placed && getArea().rx == x && getArea().ry == y;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    @Override
    public void onResized() {
        super.onResized();
        // The size is known only now, so a new window takes its place here
        if (!placed) {
            layer.place(this);
        } else if (getArea().width != lastWidth || getArea().height != lastHeight) {
            // Content that grew must not push the window past the edge of the layer
            moveTo(
                Math.max(0, Math.min(x, layer.getArea().width - getArea().width)),
                Math.max(0, Math.min(y, layer.getArea().height - getArea().height)));
        }
        lastWidth = getArea().width;
        lastHeight = getArea().height;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        // Painted here and not as the background: the backgrounds of a layer's children are drawn before a window
        // that has just moved is laid out again, which would leave its panel at the old place for a frame
        EnumTextures.GUI_PANEL.getImage()
            .draw(0, 0, getArea().width, getArea().height);
        super.draw(context, widgetTheme);
    }

    // A parent without a background is skipped by hovering, which would let clicks through to the map
    @Override
    public boolean canHover() {
        return true;
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public boolean canClickThrough() {
        return false;
    }

    /** Title, drag grooves, header buttons and the close button; pressing the title or the grooves drags */
    private final class TitleBar extends Flow implements Interactable {

        private boolean dragging;
        private int pressMouseX, pressMouseY, pressX, pressY;

        TitleBar() {
            super(GuiAxis.X);
            name(spec.id + ".title");
            widthRel(1f).height(GalaxiaUi.ICON);
            childPadding(GalaxiaUi.GAP * 2);
            crossAxisAlignment(Alignment.CrossAxis.CENTER);
            // The title and the grooves only draw, so pressing them reaches the bar and starts a drag
            child(
                new Decoration().width(() -> GalaxiaUi.width(spec.title.get()), Unit.Measure.PIXEL)
                    .height(GalaxiaUi.ICON)
                    .overlay(
                        spec.title.color(GalaxiaGuiStyle.TEXT_TITLE)
                            .shadow(true)));
            child(
                new Decoration().expanded()
                    .height(6)
                    .background(EnumTextures.GUI_TITLEBAR_GRIP.getImage()));
            for (IWidget button : spec.headerButtons) child(button);
            if (spec.closable) {
                child(
                    GalaxiaUi.iconButton(EnumTextures.ICON_CLOSE.getImage(), null, GalaxiaWindow.this::close)
                        .name(spec.id + ".close")
                        .tooltip(t -> t.addLine(IKey.lang("galaxia.gui.window.close"))));
            }
        }

        @Override
        public boolean canHover() {
            return true;
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            if (mouseButton != 0) return Result.IGNORE;
            dragging = true;
            pressMouseX = getContext().getAbsMouseX();
            pressMouseY = getContext().getAbsMouseY();
            pressX = x;
            pressY = y;
            layer.bringToFront(GalaxiaWindow.this);
            return Result.SUCCESS;
        }

        @Override
        public void onInit() {
            super.onInit();
            // Released anywhere on the screen, also outside the bar after a fast drag
            listenGuiAction((IGuiAction.MouseReleased) button -> {
                if (dragging && button == 0) finishDrag();
                return false;
            });
        }

        // Followed every frame while dragging rather than on drag events, so the window keeps up with the cursor
        // whatever delivers the mouse movement
        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            super.draw(context, widgetTheme);
            if (!dragging) return;
            double scale = layer.scale();
            moveTo(
                pressX + (int) Math.round((context.getAbsMouseX() - pressMouseX) / scale),
                pressY + (int) Math.round((context.getAbsMouseY() - pressMouseY) / scale));
        }

        @Override
        public boolean onMouseRelease(int mouseButton) {
            if (!dragging) return false;
            finishDrag();
            return true;
        }

        private void finishDrag() {
            dragging = false;
            layer.remember(GalaxiaWindow.this, x, y);
        }
    }

    /** Drawing only, never hovered */
    private static final class Decoration extends Widget<Decoration> {

        @Override
        public boolean canHover() {
            return false;
        }
    }
}
