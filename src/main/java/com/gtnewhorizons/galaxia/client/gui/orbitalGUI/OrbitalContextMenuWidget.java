package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

record ContextMenuAction(String labelKey, boolean enabled, OrbitalContextMenuWidget.ContextMenuActionType actionType) {}

record ContextMenuLayout(int left, int top, int right, int bottom, int headerHeight, int rowHeight,
    List<ContextMenuAction> actions) {}

public final class OrbitalContextMenuWidget extends ParentWidget<OrbitalContextMenuWidget> {

    private static final int MENU_SIDE_PADDING = 14;
    private static final int MENU_OUTLINE_THICKNESS = 2;
    private static final int HEADER_HEIGHT = 24;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_HOVER_INSET_X = 0;
    private static final int ROW_HOVER_INSET_Y = 0;
    private static final int HEADER_TEXT_X = 14;
    private static final int HEADER_TEXT_Y = 8;
    private static final int ROW_TEXT_X = 14;
    private static final int ROW_TEXT_Y = 6;

    interface Callbacks {

        int getViewportWidth();

        int getViewportHeight();

        void openAssetActions(CelestialObject body);

        boolean canDebugSatellites(CelestialObject body);

        void addSatellite(CelestialObject body, SatelliteKind kind);

        void deleteSatellites(CelestialObject body, SatelliteKind kind);

        void closeContextMenu();
    }

    private final OrbitalView.OrbitalContextMenuState state;
    private final Callbacks callbacks;
    private String lastSignature = "";
    private ParentWidget<?> menuRoot;

    OrbitalContextMenuWidget(OrbitalView.OrbitalContextMenuState state, Callbacks callbacks) {
        this.state = state;
        this.callbacks = callbacks;
        setEnabled(false);
        size(0, 0);
    }

    boolean isPointInMenu(int localX, int localY) {
        if (!state.isOpen()) return false;
        ContextMenuLayout layout = getLayout(state.body(), state.x(), state.y(), getArea().width, getArea().height);
        if (layout == null) return false;
        return localX >= layout.left() - MENU_OUTLINE_THICKNESS && localX <= layout.right() + MENU_OUTLINE_THICKNESS
            && localY >= layout.top() - MENU_OUTLINE_THICKNESS
            && localY <= layout.bottom() + MENU_OUTLINE_THICKNESS;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!state.isOpen()) {
            if (isEnabled()) {
                removeAll();
                scheduleResize();
            }
            menuRoot = null;
            lastSignature = "";
            setEnabled(false);
            size(0, 0);
            return;
        }
        setEnabled(true);
        size(callbacks.getViewportWidth(), callbacks.getViewportHeight());
        String signature = buildSignature();
        if (!signature.equals(lastSignature)) {
            rebuildChildren();
            lastSignature = signature;
        }
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
        if (!state.isOpen()) return;
        super.drawBackground(context, widgetTheme);
    }

    @Override
    public boolean canHoverThrough() {
        if (!state.isOpen()) return true;
        ModularGuiContext context = getContext();
        if (context == null) return true;
        return !isPointInMenu(context.getMouseX() - getArea().x, context.getMouseY() - getArea().y);
    }

    private String buildSignature() {
        CelestialObject body = state.body();
        if (body == null) return "";
        return body.id() + "|"
            + body.displayName()
            + '|'
            + state.x()
            + '|'
            + state.y()
            + '|'
            + getArea().width
            + '|'
            + getArea().height
            + '|'
            + callbacks.canDebugSatellites(body);
    }

    private void rebuildChildren() {
        removeAll();
        menuRoot = null;

        CelestialObject body = state.body();
        ContextMenuLayout layout = getLayout(body, state.x(), state.y(), getArea().width, getArea().height);
        if (layout == null) return;

        ParentWidget<?> root = new ParentWidget<>().pos(layout.left(), layout.top())
            .size(layout.right() - layout.left(), layout.bottom() - layout.top());
        menuRoot = root;

        PassiveBackgroundLayer backgroundLayer = new PassiveBackgroundLayer().pos(0, 0)
            .widthRel(1f)
            .heightRel(1f)
            .background(createMenuBackgroundDrawable());
        root.child(backgroundLayer);
        root.child(
            WidgetOutline
                .create(backgroundLayer, MENU_OUTLINE_THICKNESS, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));
        root.child(
            new TextWidget<>(IKey.str(body.displayName())).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(HEADER_TEXT_X, HEADER_TEXT_Y));

        for (int i = 0; i < layout.actions()
            .size(); i++) {
            ContextMenuAction action = layout.actions()
                .get(i);
            int rowTop = layout.headerHeight() + i * layout.rowHeight();
            root.child(createActionRow(body, action, layout.rowHeight()).pos(0, rowTop));
        }

        child(root);
    }

    private ParentWidget<?> createActionRow(CelestialObject body, ContextMenuAction action, int height) {
        ParentWidget<?> row = new ParentWidget<>().widthRel(1f)
            .height(height);

        if (action.enabled()) {
            row.child(
                new ButtonWidget<>().pos(ROW_HOVER_INSET_X, ROW_HOVER_INSET_Y)
                    .widthRelOffset(1f, -ROW_HOVER_INSET_X * 2)
                    .height(height - ROW_HOVER_INSET_Y * 2)
                    .background(IDrawable.EMPTY)
                    .hoverBackground(
                        drawable(
                            (context, x, y, w, h) -> Gui
                                .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor())))
                    .onMousePressed(mouseButton -> {
                        if (mouseButton != 0) return true;
                        handleAction(body, action);
                        return true;
                    }));
            row.child(
                new TextWidget<>(IKey.lang(action.labelKey())).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(ROW_TEXT_X, ROW_TEXT_Y));
            return row;
        }

        row.child(
            new TextWidget<>(IKey.lang(action.labelKey())).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .shadow(true)
                .pos(ROW_TEXT_X, ROW_TEXT_Y));
        return row;
    }

    private void handleAction(CelestialObject body, ContextMenuAction action) {
        switch (action.actionType()) {
            case MANAGE_ASSETS -> {
                callbacks.openAssetActions(body);
                callbacks.closeContextMenu();
            }
            case ADD_COMMUNICATION_SATELLITE -> callbacks.addSatellite(body, SatelliteKind.COMMUNICATION);
            case DELETE_COMMUNICATION_SATELLITES -> callbacks.deleteSatellites(body, SatelliteKind.COMMUNICATION);
            case ADD_PROSPECTING_SATELLITE -> callbacks.addSatellite(body, SatelliteKind.PROSPECTING);
            case DELETE_PROSPECTING_SATELLITES -> callbacks.deleteSatellites(body, SatelliteKind.PROSPECTING);
            case MESSAGE -> {}
        }
    }

    private ContextMenuLayout getLayout(CelestialObject body, int menuX, int menuY, int widgetWidth, int widgetHeight) {
        if (body == null || body.objectClass() == CelestialObject.Class.GALAXY) return null;

        List<ContextMenuAction> actions = buildActions(body, callbacks.canDebugSatellites(body));
        Minecraft mc = Minecraft.getMinecraft();
        int maxTextWidth = mc.fontRenderer.getStringWidth(body.displayName());
        for (ContextMenuAction action : actions) {
            maxTextWidth = Math.max(maxTextWidth, mc.fontRenderer.getStringWidth(IKey.lang(action.labelKey()).get()));
        }

        int width = Math.max(160, maxTextWidth + MENU_SIDE_PADDING * 2);
        int headerHeight = HEADER_HEIGHT;
        int rowHeight = ROW_HEIGHT;
        int height = headerHeight + actions.size() * rowHeight;

        int left = Math.max(8, Math.min(menuX, widgetWidth - width - 8));
        int top = Math.max(8, Math.min(menuY, widgetHeight - height - 8));

        return new ContextMenuLayout(left, top, left + width, top + height, headerHeight, rowHeight, actions);
    }

    static List<ContextMenuAction> buildActions(CelestialObject body, boolean canDebugSatellites) {
        List<ContextMenuAction> actions = new ArrayList<>();
        actions.add(
            new ContextMenuAction(
                "galaxia.gui.orbital.context_menu.manage_assets",
                true,
                ContextMenuActionType.MANAGE_ASSETS));
        if (canDebugSatellites) {
            actions.add(
                new ContextMenuAction(
                    "galaxia.satellite.action.add_communication",
                    true,
                    ContextMenuActionType.ADD_COMMUNICATION_SATELLITE));
            actions.add(
                new ContextMenuAction(
                    "galaxia.satellite.action.delete_communication",
                    true,
                    ContextMenuActionType.DELETE_COMMUNICATION_SATELLITES));
            actions.add(
                new ContextMenuAction(
                    "galaxia.satellite.action.add_prospecting",
                    true,
                    ContextMenuActionType.ADD_PROSPECTING_SATELLITE));
            actions.add(
                new ContextMenuAction(
                    "galaxia.satellite.action.delete_prospecting",
                    true,
                    ContextMenuActionType.DELETE_PROSPECTING_SATELLITES));
        }
        return actions;
    }

    private static final class PassiveBackgroundLayer extends ParentWidget<PassiveBackgroundLayer> {

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }
    }

    private IDrawable createMenuBackgroundDrawable() {
        return drawable((context, x, y, width, height) -> {
            Gui.drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_MODAL_BG.getColor());
            Gui.drawRect(x, y, x + width, y + HEADER_HEIGHT, EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
        });
    }

    private IDrawable drawable(DrawableCommand drawCommand) {
        return (context, x, y, width, height, widgetTheme) -> drawCommand.draw(context, x, y, width, height);
    }

    public enum ContextMenuActionType {
        MESSAGE,
        MANAGE_ASSETS,
        ADD_COMMUNICATION_SATELLITE,
        DELETE_COMMUNICATION_SATELLITES,
        ADD_PROSPECTING_SATELLITE,
        DELETE_PROSPECTING_SATELLITES
    }
}
