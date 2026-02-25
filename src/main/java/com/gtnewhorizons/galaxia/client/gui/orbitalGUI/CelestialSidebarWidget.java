package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.utility.EnumColors;

public class CelestialSidebarWidget extends ParentWidget<CelestialSidebarWidget> {

    private final OrbitalCelestialBody root;
    private final OrbitalMapWidget map;

    private String searchQuery = "";
    private final Set<OrbitalCelestialBody> expanded = new HashSet<>();
    private double scrollOffset = 0;
    private TextFieldWidget searchField;

    private static final int LINE_HEIGHT = 26;
    private static final int ARROW_ZONE = 42;

    public CelestialSidebarWidget(OrbitalCelestialBody root, OrbitalMapWidget map) {
        this.root = root;
        this.map = map;
        expanded.add(root);
    }

    @Override
    public void onInit() {
        super.onInit();

        searchField = new TextFieldWidget().left(14)
            .top(28)
            .right(8)
            .height(16)
            .setMaxLength(64)
            .setTextColor(EnumColors.MapSidebarSearchInput.getColor())
            .hintText(StatCollector.translateToLocal("galaxia.gui.orbital.search.placeholder"))
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
            .setFocusOnGuiOpen(true);
        child(searchField);

        listenGuiAction((IGuiAction.MouseScroll) (dir, amt) -> {
            scrollOffset += dir.isUp() ? -35 : 35;
            scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
            return true;
        });

        listenGuiAction((IGuiAction.MousePressed) button -> {
            int mouseX = getContext().getMouseX();
            int mouseY = getContext().getMouseY();
            return handleClick(mouseX, mouseY, button);
        });
    }

    private boolean handleClick(int mx, int my, int button) {
        if (button != 0) return false;

        int localY = my - getArea().ry - 52;

        if (localY < 0) return false;

        int index = (int) ((localY + scrollOffset) / LINE_HEIGHT);
        List<VisibleEntry> visible = getVisibleEntries();
        if (index < 0 || index >= visible.size()) return false;

        VisibleEntry entry = visible.get(index);
        int localX = mx - getArea().rx;

        if (entry.hasChildren && localX < ARROW_ZONE + entry.depth * 24) {
            if (expanded.contains(entry.body)) expanded.remove(entry.body);
            else expanded.add(entry.body);
            return true;
        }

        map.focusOn(entry.body);
        return true;
    }

    @Desugar
    private record VisibleEntry(OrbitalCelestialBody body, int depth, boolean hasChildren) {}

    private List<VisibleEntry> getVisibleEntries() {
        List<VisibleEntry> list = new ArrayList<>();
        collect(root, 0, list);
        return list;
    }

    private void collect(OrbitalCelestialBody body, int depth, List<VisibleEntry> list) {
        boolean matches = searchQuery.isEmpty() || body.name()
            .toLowerCase()
            .contains(searchQuery);
        if (matches || searchQuery.isEmpty()) {
            list.add(
                new VisibleEntry(
                    body,
                    depth,
                    !body.children()
                        .isEmpty()));
        }
        if (expanded.contains(body) || !searchQuery.isEmpty()) {
            for (OrbitalCelestialBody child : body.children()) {
                collect(child, depth + 1, list);
            }
        }
    }

    private double getMaxScroll() {
        return Math.max(0, getVisibleEntries().size() * LINE_HEIGHT - getArea().height + 80);
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
        super.drawBackground(context, widgetTheme);

        String newQuery = searchField == null ? ""
            : searchField.getText()
                .toLowerCase();
        if (!newQuery.equals(searchQuery)) {
            searchQuery = newQuery;
            scrollOffset = 0;
        }

        Gui.drawRect(0, 0, getArea().width, getArea().height, EnumColors.MapSidebarBackground.getColor());

        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocal("galaxia.gui.orbital.search"),
            18,
            16,
            EnumColors.MapSidebaSearchLabel.getColor());

        List<VisibleEntry> visible = getVisibleEntries();
        int y = 56 - (int) scrollOffset;

        int mouseLocalY = getContext().getMouseY() - getArea().ry - 52;
        int hovered = (mouseLocalY >= 0) ? (int) ((mouseLocalY + scrollOffset) / LINE_HEIGHT) : -1;

        for (int i = 0; i < visible.size(); i++) {
            VisibleEntry e = visible.get(i);
            int sy = y + i * LINE_HEIGHT;
            if (sy < 50 || sy > getArea().height - 10) continue;

            int iconX = 10 + e.depth * 24;
            int textX = 22 + e.depth * 24;
            String text = e.body.name();

            int color = (i == hovered) ? EnumColors.MapSidebarListHovered.getColor()
                : EnumColors.MapSidebarListNormal.getColor();

            if (e.hasChildren) {
                IDrawable play = IDrawable.of(GuiTextures.PLAY);
                if (expanded.contains(e.body)) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(iconX + 4f, sy + 10f, 0f);
                    GL11.glRotatef(90f, 0f, 0f, 1f);
                    GL11.glTranslatef(-4f, -4f, 0f);
                    play.draw(context, 0, 0, 8, 8, widgetTheme.getTheme());
                    GL11.glPopMatrix();
                } else {
                    play.draw(context, iconX, sy + 6, 8, 8, widgetTheme.getTheme());
                }
            }

            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(text, textX, sy + 6, color);
        }
    }
}
