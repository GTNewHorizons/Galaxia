package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketGuiStyle;

/**
 * Rooms of a station drawn from above at their controller positions, airlock connections as lines between them.
 * Clicking a room selects it for the detail panel
 */
public class StationGraphWidget extends Widget<StationGraphWidget> implements Interactable {

    private static final int NODE = 10;
    private static final int MARGIN = 12;

    private final StationTerminalUI.View view;

    StationGraphWidget(StationTerminalUI.View view) {
        this.view = view;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
        super.draw(context, widgetTheme);
        if (view.rooms.isEmpty()) return;
        int[][] at = layout();

        // Airlock connections first, rooms on top
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2f);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        t.setColorOpaque_I(RocketGuiStyle.ACCENT & 0xFFFFFF);
        for (int i = 0; i + 1 < view.links.length; i += 2) {
            int a = view.links[i], b = view.links[i + 1];
            if (a >= at.length || b >= at.length) continue;
            t.addVertex(at[a][0], at[a][1], 0);
            t.addVertex(at[b][0], at[b][1], 0);
        }
        t.draw();
        GL11.glLineWidth(1f);
        GlStateManager.enableTexture2D();

        int hovered = hoveredRoom(at);
        for (int i = 0; i < at.length; i++) {
            StationTerminalUI.Room room = view.rooms.get(i);
            int x = at[i][0] - NODE / 2, y = at[i][1] - NODE / 2;
            int frame = i == view.selected ? RocketGuiStyle.TEXT_TITLE
                : i == hovered ? RocketGuiStyle.TEXT : RocketGuiStyle.BAR_EDGE;
            Gui.drawRect(x - 1, y - 1, x + NODE + 1, y + NODE + 1, frame);
            Gui.drawRect(x, y, x + NODE, y + NODE, RocketGuiStyle.BAR_BACK);
            Gui.drawRect(x + 2, y + 2, x + NODE - 2, y + NODE - 2, room.color());
            // The main room carries a dot in the middle
            if (room.main()) Gui.drawRect(x + 4, y + 4, x + NODE - 4, y + NODE - 4, RocketGuiStyle.TEXT_TITLE);
        }
    }

    /** Rooms on the map */
    public int roomCount() {
        return view.rooms.size();
    }

    /** Airlock connections on the map */
    public int linkCount() {
        return view.links.length / 2;
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (mouseButton != 0 || view.rooms.isEmpty()) return Result.IGNORE;
        int index = hoveredRoom(layout());
        if (index < 0) return Result.IGNORE;
        view.selected = index;
        return Result.SUCCESS;
    }

    /**
     * Screen positions of the rooms: the station seen from above, x to the right and z down, scaled to fit
     */
    private int[][] layout() {
        int count = view.rooms.size();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (StationTerminalUI.Room room : view.rooms) {
            minX = Math.min(minX, room.x());
            maxX = Math.max(maxX, room.x());
            minZ = Math.min(minZ, room.z());
            maxZ = Math.max(maxZ, room.z());
        }
        int width = getArea().width - MARGIN * 2, height = getArea().height - MARGIN * 2;
        double spanX = Math.max(1, maxX - minX), spanZ = Math.max(1, maxZ - minZ);
        double scale = Math.min(width / spanX, height / spanZ);
        // Centre the layout, a single room sits in the middle
        double offsetX = MARGIN + (width - (maxX - minX) * scale) / 2;
        double offsetY = MARGIN + (height - (maxZ - minZ) * scale) / 2;
        int[][] at = new int[count][2];
        for (int i = 0; i < count; i++) {
            StationTerminalUI.Room room = view.rooms.get(i);
            at[i][0] = (int) Math.round(offsetX + (room.x() - minX) * scale);
            at[i][1] = (int) Math.round(offsetY + (room.z() - minZ) * scale);
        }
        return at;
    }

    private int hoveredRoom(int[][] at) {
        int mouseX = getContext().getMouseX() - getArea().x;
        int mouseY = getContext().getMouseY() - getArea().y;
        for (int i = 0; i < at.length; i++) {
            if (Math.abs(mouseX - at[i][0]) <= NODE / 2 + 1 && Math.abs(mouseY - at[i][1]) <= NODE / 2 + 1) return i;
        }
        return -1;
    }
}
