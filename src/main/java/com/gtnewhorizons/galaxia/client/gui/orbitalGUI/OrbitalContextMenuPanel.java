package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;

final class OrbitalContextMenuPanel {

    interface Callbacks {

        boolean canCreateBaseStation(OrbitalCelestialBody body);

        boolean canCreateAutomatedStation(OrbitalCelestialBody body);

        boolean canCreateAutomatedOutpost(OrbitalCelestialBody body);

        void openAssetManagement(OrbitalCelestialBody body);

        void createBaseStation(OrbitalCelestialBody body);

        void triggerAssetCreation(OrbitalCelestialBody body, CelestialAssetKind kind, boolean openManagementFirst);
    }

    private final Callbacks callbacks;

    OrbitalContextMenuPanel(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    void draw(OrbitalCelestialBody body, int menuX, int menuY, int widgetWidth, int widgetHeight, int mouseX,
        int mouseY) {
        ContextMenuLayout layout = getLayout(body, menuX, menuY, widgetWidth, widgetHeight);
        if (layout == null) {
            return;
        }

        Gui.drawRect(layout.left, layout.top, layout.right, layout.bottom, 0xFF111925);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + layout.headerHeight, 0xFF23324B);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + 2, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.bottom - 2, layout.right, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.top, layout.left + 2, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.right - 2, layout.top, layout.right, layout.bottom, 0xFF59BFD9);

        Minecraft mc = Minecraft.getMinecraft();
        mc.fontRenderer.drawStringWithShadow(body.displayName(), layout.left + 10, layout.top + 7, 0xFFFFFFFF);
        for (int i = 0; i < layout.actions.size(); i++) {
            ContextMenuAction action = layout.actions.get(i);
            int rowTop = layout.top + layout.headerHeight + i * layout.rowHeight;
            int rowBottom = rowTop + layout.rowHeight;
            boolean hovered = mouseX >= layout.left && mouseX <= layout.right && mouseY >= rowTop && mouseY < rowBottom;
            if (hovered && action.enabled) {
                Gui.drawRect(layout.left + 4, rowTop, layout.right - 4, rowBottom - 1, 0xFF375575);
            }
            int color = action.enabled ? 0xFFD9E0FF : 0xFF6F7A89;
            mc.fontRenderer.drawStringWithShadow(action.label, layout.left + 10, rowTop + 5, color);
        }
    }

    boolean handleClick(OrbitalCelestialBody body, int menuX, int menuY, int widgetWidth, int widgetHeight,
        int localMouseX, int localMouseY) {
        ContextMenuLayout layout = getLayout(body, menuX, menuY, widgetWidth, widgetHeight);
        if (layout == null) {
            return false;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            return false;
        }
        if (localMouseY < layout.top + layout.headerHeight) {
            return true;
        }

        int rowIndex = (localMouseY - layout.top - layout.headerHeight) / layout.rowHeight;
        if (rowIndex < 0 || rowIndex >= layout.actions.size()) {
            return true;
        }

        ContextMenuAction action = layout.actions.get(rowIndex);
        if (!action.enabled) {
            return true;
        }

        switch (action.actionType) {
            case MANAGE_ASSETS -> callbacks.openAssetManagement(body);
            case CREATE_STATION -> callbacks.createBaseStation(body);
            case OPEN_AUTOMATED_STATION_CONFIRM ->
                callbacks.triggerAssetCreation(body, CelestialAssetKind.AUTOMATED_STATION, true);
            case OPEN_AUTOMATED_OUTPOST_CONFIRM ->
                callbacks.triggerAssetCreation(body, CelestialAssetKind.AUTOMATED_OUTPOST, true);
            case MESSAGE -> {
            }
        }
        return true;
    }

    boolean isWithin(OrbitalCelestialBody body, int menuX, int menuY, int widgetWidth, int widgetHeight, int localMouseX,
        int localMouseY) {
        ContextMenuLayout layout = getLayout(body, menuX, menuY, widgetWidth, widgetHeight);
        return layout != null && localMouseX >= layout.left && localMouseX <= layout.right && localMouseY >= layout.top
            && localMouseY <= layout.bottom;
    }

    private ContextMenuLayout getLayout(OrbitalCelestialBody body, int menuX, int menuY, int widgetWidth,
        int widgetHeight) {
        if (body == null || body.objectClass() == CelestialObjectClass.GALAXY) {
            return null;
        }

        List<ContextMenuAction> actions = buildActions(body);
        Minecraft mc = Minecraft.getMinecraft();
        int maxTextWidth = mc.fontRenderer.getStringWidth(body.displayName());
        for (ContextMenuAction action : actions) {
            maxTextWidth = Math.max(maxTextWidth, mc.fontRenderer.getStringWidth(action.label));
        }

        int width = Math.max(150, maxTextWidth + 20);
        int headerHeight = 22;
        int rowHeight = 18;
        int height = headerHeight + actions.size() * rowHeight;
        int left = clamp(menuX, 8, Math.max(8, widgetWidth - width - 8));
        int top = clamp(menuY, 8, Math.max(8, widgetHeight - height - 8));
        return new ContextMenuLayout(left, top, left + width, top + height, headerHeight, rowHeight, actions);
    }

    private List<ContextMenuAction> buildActions(OrbitalCelestialBody body) {
        List<ContextMenuAction> actions = new ArrayList<>();
        actions.add(new ContextMenuAction("Manage Assets", true, ContextMenuActionType.MANAGE_ASSETS));
        if (callbacks.canCreateBaseStation(body)) {
            actions.add(new ContextMenuAction("Create Station", true, ContextMenuActionType.CREATE_STATION));
        }
        if (callbacks.canCreateAutomatedStation(body)) {
            actions.add(
                new ContextMenuAction("Create Automated Station", true, ContextMenuActionType.OPEN_AUTOMATED_STATION_CONFIRM));
        }
        if (callbacks.canCreateAutomatedOutpost(body)) {
            actions.add(
                new ContextMenuAction("Create Automated Outpost", true, ContextMenuActionType.OPEN_AUTOMATED_OUTPOST_CONFIRM));
        }
        if (actions.size() == 1) {
            actions.add(new ContextMenuAction("No actions available", false, ContextMenuActionType.MESSAGE));
        }
        return actions;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum ContextMenuActionType {
        MESSAGE,
        MANAGE_ASSETS,
        CREATE_STATION,
        OPEN_AUTOMATED_STATION_CONFIRM,
        OPEN_AUTOMATED_OUTPOST_CONFIRM
    }

    private static final class ContextMenuAction {

        final String label;
        final boolean enabled;
        final ContextMenuActionType actionType;

        ContextMenuAction(String label, boolean enabled, ContextMenuActionType actionType) {
            this.label = label;
            this.enabled = enabled;
            this.actionType = actionType;
        }
    }

    private static final class ContextMenuLayout {

        final int left;
        final int top;
        final int right;
        final int bottom;
        final int headerHeight;
        final int rowHeight;
        final List<ContextMenuAction> actions;

        ContextMenuLayout(int left, int top, int right, int bottom, int headerHeight, int rowHeight,
            List<ContextMenuAction> actions) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.headerHeight = headerHeight;
            this.rowHeight = rowHeight;
            this.actions = actions;
        }
    }
}
