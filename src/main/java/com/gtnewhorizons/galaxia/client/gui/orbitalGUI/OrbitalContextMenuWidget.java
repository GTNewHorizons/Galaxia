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
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

record ContextMenuAction(String label, boolean enabled, OrbitalContextMenuWidget.ContextMenuActionType actionType) {}

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

        boolean canCreateBaseStation(CelestialObject body);

        boolean canCreateAutomatedStation(CelestialObject body);

        boolean canCreateAutomatedOutpost(CelestialObject body);

        void openAssetManagement(CelestialObject body);

        void createBaseStation(CelestialObject body);

        void triggerAssetCreation(CelestialObject body, CelestialAsset.Kind kind, boolean openManagementFirst);

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
        if (!state.isOpen() || menuRoot == null || !menuRoot.isValid()) return false;
        return localX >= menuRoot.getArea().x - MENU_OUTLINE_THICKNESS
            && localX <= menuRoot.getArea().x + menuRoot.getArea().width + MENU_OUTLINE_THICKNESS
            && localY >= menuRoot.getArea().y - MENU_OUTLINE_THICKNESS
            && localY <= menuRoot.getArea().y + menuRoot.getArea().height + MENU_OUTLINE_THICKNESS;
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
        return true;
    }

    private String buildSignature() {
        CelestialObject body = state.body();
        if (body == null) return "";
        return body.id()
            .getId() + '|'
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
            + callbacks.canCreateBaseStation(body)
            + '|'
            + callbacks.canCreateAutomatedStation(body)
            + '|'
            + callbacks.canCreateAutomatedOutpost(body);
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
                        handleAction(body, action.actionType());
                        return true;
                    }));
            row.child(
                new TextWidget<>(IKey.str(action.label())).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(ROW_TEXT_X, ROW_TEXT_Y));
            return row;
        }

        row.child(
            new TextWidget<>(IKey.str(action.label())).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .shadow(true)
                .pos(ROW_TEXT_X, ROW_TEXT_Y));
        return row;
    }

    private void handleAction(CelestialObject body, ContextMenuActionType actionType) {
        switch (actionType) {
            case MANAGE_ASSETS -> callbacks.openAssetManagement(body);
            case CREATE_STATION -> callbacks.createBaseStation(body);
            case OPEN_AUTOMATED_STATION_CONFIRM -> callbacks
                .triggerAssetCreation(body, CelestialAsset.Kind.AUTOMATED_STATION, true);
            case OPEN_AUTOMATED_OUTPOST_CONFIRM -> callbacks
                .triggerAssetCreation(body, CelestialAsset.Kind.AUTOMATED_OUTPOST, true);
            case MESSAGE -> {}
        }
        callbacks.closeContextMenu();
    }

    private ContextMenuLayout getLayout(CelestialObject body, int menuX, int menuY, int widgetWidth, int widgetHeight) {
        if (body == null || body.objectClass() == CelestialObject.Class.GALAXY) return null;

        List<ContextMenuAction> actions = buildActions(body);
        Minecraft mc = Minecraft.getMinecraft();
        int maxTextWidth = mc.fontRenderer.getStringWidth(body.displayName());
        for (ContextMenuAction action : actions) {
            maxTextWidth = Math.max(maxTextWidth, mc.fontRenderer.getStringWidth(action.label()));
        }

        int width = Math.max(160, maxTextWidth + MENU_SIDE_PADDING * 2);
        int headerHeight = HEADER_HEIGHT;
        int rowHeight = ROW_HEIGHT;
        int height = headerHeight + actions.size() * rowHeight;

        int left = Math.max(8, Math.min(menuX, widgetWidth - width - 8));
        int top = Math.max(8, Math.min(menuY, widgetHeight - height - 8));

        return new ContextMenuLayout(left, top, left + width, top + height, headerHeight, rowHeight, actions);
    }

    private List<ContextMenuAction> buildActions(CelestialObject body) {
        List<ContextMenuAction> actions = new ArrayList<>();
        actions.add(new ContextMenuAction("Manage Assets", true, ContextMenuActionType.MANAGE_ASSETS));
        if (callbacks.canCreateBaseStation(body)) {
            actions.add(new ContextMenuAction("Create Station", true, ContextMenuActionType.CREATE_STATION));
        }
        if (callbacks.canCreateAutomatedStation(body)) {
            actions.add(
                new ContextMenuAction(
                    "Create Automated Station",
                    true,
                    ContextMenuActionType.OPEN_AUTOMATED_STATION_CONFIRM));
        }
        if (callbacks.canCreateAutomatedOutpost(body)) {
            actions.add(
                new ContextMenuAction(
                    "Create Automated Outpost",
                    true,
                    ContextMenuActionType.OPEN_AUTOMATED_OUTPOST_CONFIRM));
        }
        if (actions.size() == 1) {
            actions.add(new ContextMenuAction("No actions available", false, ContextMenuActionType.MESSAGE));
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
        CREATE_STATION,
        OPEN_AUTOMATED_STATION_CONFIRM,
        OPEN_AUTOMATED_OUTPOST_CONFIRM
    }
}
