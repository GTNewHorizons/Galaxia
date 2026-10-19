package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

record ContextMenuAction(String labelKey, boolean enabled, OrbitalContextMenuWidget.ContextMenuActionType actionType) {}

public final class OrbitalContextMenuWidget extends ParentWidget<OrbitalContextMenuWidget> {

    private static final int MIN_WIDTH = 100;
    /** Space kept between the menu and the edge of the map */
    private static final int EDGE = 8;

    interface Callbacks {

        void openAssetActions(CelestialObject body);

        boolean canDebugSatellites(CelestialObject body);

        void addSatellite(CelestialObject body, SatelliteKind kind);

        void deleteSatellites(CelestialObject body, SatelliteKind kind);

        void closeContextMenu();
    }

    private final OrbitalView.OrbitalContextMenuState state;
    private final Callbacks callbacks;
    private final StarmapViewContext view;
    private String lastSignature = "";
    private Flow menu;

    OrbitalContextMenuWidget(OrbitalView.OrbitalContextMenuState state, Callbacks callbacks, StarmapViewContext view) {
        this.state = state;
        this.callbacks = callbacks;
        this.view = view;
        setEnabled(false);
        size(0, 0);
    }

    boolean isPointInMenu(int localX, int localY) {
        if (!state.isOpen() || menu == null) return false;
        return localX >= menu.getArea().rx && localX <= menu.getArea().rx + menu.getArea().width
            && localY >= menu.getArea().ry
            && localY <= menu.getArea().ry + menu.getArea().height;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!state.isOpen()) {
            if (isEnabled()) {
                removeAll();
                scheduleResize();
            }
            lastSignature = "";
            setEnabled(false);
            size(0, 0);
            return;
        }
        setEnabled(true);
        size(view.viewportWidth(), view.viewportHeight());
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
        int[] local = GalaxiaUi.toLocal(this, context.getAbsMouseX(), context.getAbsMouseY());
        return !isPointInMenu(local[0], local[1]);
    }

    private String buildSignature() {
        CelestialObject body = state.body();
        if (body == null) return "";
        return body.key() + "|"
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
        menu = null;
        CelestialObject body = state.body();
        if (body == null || body.objectClass() == CelestialObject.Class.GALAXY) return;
        List<ContextMenuAction> actions = buildActions(body, callbacks.canDebugSatellites(body));
        int width = Math.max(MIN_WIDTH, GalaxiaUi.width(body.displayName()));
        for (ContextMenuAction action : actions)
            width = Math.max(width, GalaxiaUi.width(tr(action.labelKey())) + GalaxiaUi.PAD * 2);

        Flow column = GalaxiaUi.column();
        column.name("starmap.context.menu")
            .padding(GalaxiaUi.PAD)
            .background(EnumTextures.GUI_PANEL.getImage());
        column.child(
            GalaxiaUi.clipped(body.displayName(), width, GalaxiaGuiStyle.TEXT_TITLE)
                .height(GalaxiaUi.ICON));
        for (ContextMenuAction action : actions) column.child(actionRow(body, action, width));
        // Opens at the click, pulled back inside the map when it would cross an edge
        Flow placed = column;
        column.left(() -> clamp(state.x(), getArea().width - placed.getArea().width), Unit.Measure.PIXEL)
            .top(() -> clamp(state.y(), getArea().height - placed.getArea().height), Unit.Measure.PIXEL);
        menu = column;
        child(column);
    }

    private static int clamp(int at, int max) {
        return Math.max(EDGE, Math.min(at, max - EDGE));
    }

    private ButtonWidget<?> actionRow(CelestialObject body, ContextMenuAction action, int width) {
        IKey label = IKey.str(tr(action.labelKey()))
            .color(action.enabled() ? GalaxiaGuiStyle.TEXT : GalaxiaGuiStyle.TEXT_DIM)
            .alignment(Alignment.CenterLeft);
        ButtonWidget<?> row = new ButtonWidget<>().size(width, GalaxiaUi.BUTTON_H)
            .background(IDrawable.EMPTY)
            .overlay(
                label.asIcon()
                    .marginLeft(GalaxiaUi.PAD));
        if (!action.enabled()) return row;
        return row.name(
            "starmap.context." + action.actionType()
                .name())
            .hoverBackground(EnumTextures.GUI_BUTTON_HOVERED.getImage())
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return true;
                handleAction(body, action);
                return true;
            });
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

    private static String tr(String key) {
        return net.minecraft.util.StatCollector.translateToLocal(key);
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
