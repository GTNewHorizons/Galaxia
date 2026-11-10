package com.gtnewhorizons.galaxia.client.gui.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;

/**
 * Holds the windows opened over a screen. Clicks pass through the layer itself to what lies below, but not through its
 * windows. Windows remember where they were dragged to while the layer exists, so a reopened screen starts fresh
 */
public final class WindowLayer extends ParentWidget<WindowLayer> {

    /** Offset between windows that open without a place of their own */
    private static final int CASCADE = 16;
    private static final int OFF_LAYER = -10_000;

    private final DoubleSupplier scale;
    private final Map<String, GalaxiaWindow> open = new LinkedHashMap<>();
    private final Map<String, int[]> positions = new HashMap<>();
    private final Map<GalaxiaWindow, Widget<?>> blockers = new HashMap<>();
    private final List<IWidget> pendingRemoval = new ArrayList<>();
    private final List<IWidget> pendingAdd = new ArrayList<>();
    private GalaxiaWindow pendingFront;
    private int cascade;

    /**
     * @param scale How many screen pixels one layer pixel takes, to turn mouse movement into window movement
     */
    public WindowLayer(DoubleSupplier scale) {
        this.scale = scale;
        name("windows");
    }

    /** Opens the window, or brings it to the front when it is already open */
    public GalaxiaWindow open(WindowSpec spec) {
        GalaxiaWindow existing = open.get(spec.id);
        if (existing != null) {
            bringToFront(existing);
            return existing;
        }
        GalaxiaWindow window = new GalaxiaWindow(spec, this);
        window.left(OFF_LAYER)
            .top(OFF_LAYER);
        if (spec.modal) {
            Widget<?> blocker = new Blocker().widthRel(1f)
                .heightRel(1f)
                .background(new Rectangle().color(0x66000000));
            blockers.put(window, blocker);
            pendingAdd.add(blocker);
        }
        open.put(spec.id, window);
        // Put on the layer in the next update: a window opened by a mouse action registers its own listeners, which
        // must not happen while the screen is still going through them
        pendingAdd.add(window);
        return window;
    }

    /** Opens the window when it is closed, closes it when it is open */
    public void toggle(WindowSpec spec) {
        if (isOpen(spec.id)) close(spec.id);
        else open(spec);
    }

    public boolean isOpen(String id) {
        return open.containsKey(id);
    }

    public GalaxiaWindow get(String id) {
        return open.get(id);
    }

    /** Closes the window and every window it owns */
    public boolean close(String id) {
        GalaxiaWindow window = open.remove(id);
        if (window == null) return false;
        for (GalaxiaWindow owned : new ArrayList<>(open.values())) {
            if (id.equals(owned.spec.owner)) close(owned.spec.id);
        }
        // Taken off in the next update, the window may be the one handling the current click
        if (!pendingAdd.remove(window)) pendingRemoval.add(window);
        Widget<?> blocker = blockers.remove(window);
        if (blocker != null && !pendingAdd.remove(blocker)) pendingRemoval.add(blocker);
        if (window.spec.onClose != null) window.spec.onClose.run();
        return true;
    }

    /** Closes the window opened last that can be closed, for Esc */
    public boolean closeTop() {
        List<IWidget> children = getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i) instanceof GalaxiaWindow window && open.containsKey(window.spec.id)
                && window.spec.closable) {
                return close(window.spec.id);
            }
        }
        return false;
    }

    public void closeAll() {
        for (String id : new ArrayList<>(open.keySet())) close(id);
    }

    /** Topmost window under a point of the layer, or null */
    public GalaxiaWindow windowAt(int x, int y) {
        List<IWidget> children = getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i) instanceof GalaxiaWindow window && open.containsKey(window.spec.id)
                && window.getArea()
                    .isInside(x, y)) {
                return window;
            }
        }
        return null;
    }

    public boolean hasModal() {
        return !blockers.isEmpty();
    }

    /** Puts a new window where it was last dragged to, where its spec says, or cascaded from the last one */
    void place(GalaxiaWindow window) {
        int width = window.getArea().width;
        int height = window.getArea().height;
        if (width <= 0 || height <= 0 || !resizer().isWidthCalculated() || !resizer().isHeightCalculated()) return;
        int[] at = positions.get(window.spec.id);
        if (at == null && window.spec.anchor != null) {
            at = window.spec.anchor.place(getArea().width, getArea().height, width, height);
        }
        if (at == null) {
            at = new int[] { getArea().width / 4 + cascade, getArea().height / 6 + cascade };
            cascade = (cascade + CASCADE) % (CASCADE * 8);
        }
        window.placed = true;
        window.moveTo(at[0], at[1]);
    }

    void bringToFront(GalaxiaWindow window) {
        pendingFront = window;
    }

    void remember(GalaxiaWindow window, int x, int y) {
        positions.put(window.spec.id, new int[] { x, y });
    }

    double scale() {
        return Math.max(0.01, scale.getAsDouble());
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        for (IWidget widget : pendingRemoval) remove(widget);
        if (!pendingRemoval.isEmpty()) scheduleResize();
        pendingRemoval.clear();
        if (!pendingAdd.isEmpty()) {
            List<IWidget> added = new ArrayList<>(pendingAdd);
            pendingAdd.clear();
            for (IWidget widget : added) child(widget);
            scheduleResize();
        }
        if (pendingFront != null) {
            List<IWidget> children = getChildren();
            if (children.remove(pendingFront)) {
                Widget<?> blocker = blockers.get(pendingFront);
                if (blocker != null && children.remove(blocker)) children.add(blocker);
                children.add(pendingFront);
            }
            pendingFront = null;
        }
    }

    @Override
    public void onResized() {
        super.onResized();
        // Keep every window reachable when the screen gets smaller
        for (GalaxiaWindow window : open.values()) {
            if (window.placed) window.keepOnLayer();
            else place(window);
        }
    }

    @Override
    public boolean canHover() {
        return false;
    }

    @Override
    public boolean canHoverThrough() {
        return true;
    }

    /** The layer of the screen open now, if it has one */
    public static WindowLayer current() {
        ModularScreen screen = ModularScreen.getCurrent();
        return screen == null ? null : find(screen.getMainPanel());
    }

    /** The layer of the screen a widget is on */
    public static WindowLayer of(IWidget widget) {
        for (IWidget parent = widget; parent != null; parent = parent.hasParent() ? parent.getParent() : null) {
            if (parent instanceof WindowLayer layer) return layer;
        }
        if (!widget.isValid()) return null;
        ModularPanel panel = widget.getScreen()
            .getMainPanel();
        return find(panel);
    }

    private static WindowLayer find(IWidget widget) {
        if (widget instanceof WindowLayer layer) return layer;
        if (!widget.hasChildren()) return null;
        for (IWidget child : widget.getChildren()) {
            WindowLayer found = find(child);
            if (found != null) return found;
        }
        return null;
    }

    /** Dims what lies below a dialog and swallows clicks on it */
    private static final class Blocker extends Widget<Blocker> {

        @Override
        public boolean canHoverThrough() {
            return false;
        }

        @Override
        public boolean canClickThrough() {
            return false;
        }
    }
}
