package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.utility.EnumColors;

public class CelestialSidebarWidget extends Widget {

    private final OrbitalCelestialBody root;
    private final OrbitalMapWidget map;

    private String searchQuery = "";
    private final Set<OrbitalCelestialBody> expanded = new HashSet<>();
    private double scrollOffset = 0;

    private static final int LINE_HEIGHT = 26;
    private static final int ARROW_ZONE = 42;

    private boolean searchFocused = true;
    private long lastKeyPressTime = 0;

    public CelestialSidebarWidget(OrbitalCelestialBody root, OrbitalMapWidget map) {
        this.root = root;
        this.map = map;
        expanded.add(root);
    }

    @Override
    public void onInit() {
        super.onInit();

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

        listenGuiAction((IGuiAction.KeyPressed) this::handleKey);
    }

    private boolean handleClick(int mx, int my, int button) {
        if (button != 0) return false;

        int localY = my - getArea().ry - 52;

        if (localY < 0) {
            searchFocused = true;
            return true;
        }

        searchFocused = false;

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

    private boolean handleKey(char typedChar, int keyCode) {
        if (!searchFocused) return false;

        long now = System.currentTimeMillis();
        if (now - lastKeyPressTime < 30) {
            lastKeyPressTime = now;
            return true;
        }
        lastKeyPressTime = now;

        if (keyCode == 14) {
            if (!searchQuery.isEmpty()) searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            scrollOffset = 0;
            return true;
        }
        if (typedChar >= 32 && typedChar <= 126) {
            searchQuery += Character.toLowerCase(typedChar);
            scrollOffset = 0;
            return true;
        }
        return false;
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

        Gui.drawRect(0, 0, getArea().width, getArea().height, EnumColors.SidebarBackground.getColor());

        Gui.drawRect(8, 8, getArea().width - 8, 44, EnumColors.SearchBarBackground.getColor());
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocal("galaxia.gui.orbital.search"),
            18,
            16,
            EnumColors.SearchLabelColor.getColor());

        String placeholder = StatCollector.translateToLocal("galaxia.gui.orbital.search.placeholder");
        String disp = searchQuery.isEmpty() ? placeholder : searchQuery + "█";
        Minecraft.getMinecraft().fontRenderer
            .drawStringWithShadow(disp, 18, 30, EnumColors.SearchInputColor.getColor());

        List<VisibleEntry> visible = getVisibleEntries();
        int y = 56 - (int) scrollOffset;

        int mouseLocalY = getContext().getMouseY() - getArea().ry - 52;
        int hovered = (mouseLocalY >= 0) ? (int) ((mouseLocalY + scrollOffset) / LINE_HEIGHT) : -1;

        for (int i = 0; i < visible.size(); i++) {
            VisibleEntry e = visible.get(i);
            int sy = y + i * LINE_HEIGHT;
            if (sy < 50 || sy > getArea().height - 10) continue;

            int indent = 18 + e.depth * 24;
            String prefix = e.hasChildren ? (expanded.contains(e.body) ? "▼ " : "▶ ") : "  ";
            String text = prefix + e.body.name();

            int color = (i == hovered) ? EnumColors.SidebarListHovered.getColor()
                : EnumColors.SidebarListNormal.getColor();

            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(text, indent, sy + 6, color);
        }
    }
}
