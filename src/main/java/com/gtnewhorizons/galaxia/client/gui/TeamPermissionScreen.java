package com.gtnewhorizons.galaxia.client.gui;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.compat.teams.TeamRole;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.TeamConfigPacket;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class TeamPermissionScreen implements IGuiHolder<GuiData> {

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory(
        "galaxia_team_permissions",
        TeamPermissionScreen::new);

    private static final int PANEL_W = 300;
    private static final int ROW_H = 22;
    private static final int PAD = 12;
    private static final int HEADER_H = 24;
    private static final int COLOR_PICKER_H = 40;
    private static final int BOTTOM_PAD = 8;

    private static final int[] TEAM_COLORS = { EnumColors.MAP_COLOR_TEAM_ACCENT.getColor(),
        EnumColors.MAP_MACHINE_BLUE.getColor(), };

    public static void open() {
        FACTORY.openClient();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
        return new ModularScreen(Galaxia.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        TeamAction[] actions = TeamAction.values();
        int rowCount = actions.length;
        int contentH = rowCount * (ROW_H + 2);
        int panelH = HEADER_H + COLOR_PICKER_H + contentH + BOTTOM_PAD + 40;

        ModularPanel panel = ModularPanel.defaultPanel("galaxia_team_permissions")
            .size(PANEL_W, Math.min(panelH, 360));

        ParentWidget<?> bg = new ParentWidget<>().pos(0, 0)
            .size(PANEL_W, panelH)
            .background((ctx, x, y, w, h, theme) -> {
                com.cleanroommc.modularui.utils.GlStateManager.color(1f, 1f, 1f, 1f);
                net.minecraft.client.gui.Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_MODAL_BG.getColor());
                net.minecraft.client.gui.Gui
                    .drawRect(x, y, x + w, y + HEADER_H, EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
            });
        panel.child(bg);

        panel.child(
            new TextWidget<>(com.cleanroommc.modularui.api.drawable.IKey.lang("galaxia.gui.team_config.title"))
                .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(PAD, 7));

        int colorPickerY = HEADER_H + 4;
        int swatchSize = 20;
        int swatchGap = 6;

        for (int i = 0; i < TEAM_COLORS.length; i++) {
            int color = TEAM_COLORS[i];
            int sx = PAD + i * (swatchSize + swatchGap);
            int sy = colorPickerY + 14;

            ButtonWidget<?> swatch = new ButtonWidget<>().background((ctx, x, y, w, h, theme) -> {
                int current = GTTeamsCompat.getGalaxiaTeamData()
                    .map(GalaxiaTeamData::getTeamColor)
                    .orElse(TEAM_COLORS[0]);
                Gui.drawRect(x, y, x + w, y + h, color);
                if (current == color) {
                    BorderedRect.drawBorderOnly(x, y, w, h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                } else {
                    BorderedRect.drawBorderOnly(x, y, w, h, EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor());
                }
            })
                .onMousePressed(mb -> {
                    GTTeamsCompat.getGalaxiaTeamData()
                        .ifPresent(d -> d.setTeamColor(color));
                    GALAXIA_NETWORK.sendToServer(TeamConfigPacket.color(color));
                    return true;
                });
            panel.child(
                swatch.pos(sx, sy)
                    .size(swatchSize, swatchSize));

            if (i == 0) {
                TextWidget<?> colorLabelWidget = new TextWidget<>(
                    com.cleanroommc.modularui.api.drawable.IKey.lang("galaxia.gui.team_config.color"))
                        .color(EnumColors.MAP_COLOR_TEXT_SECTION.getColor())
                        .shadow(true)
                        .pos(PAD, colorPickerY);
                panel.child(colorLabelWidget);
            }
        }

        VerticalScrollData scrollData = new VerticalScrollData();
        ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(PAD, HEADER_H + 4 + COLOR_PICKER_H)
            .size(PANEL_W - PAD * 2, Math.max(contentH, 80));
        ParentWidget<?> container = new ParentWidget<>().widthRel(1f)
            .height(contentH);
        scroll.child(container);
        panel.child(scroll);

        for (int i = 0; i < rowCount; i++) {
            TeamAction action = actions[i];
            int y = i * (ROW_H + 2);
            container.child(
                buildRow(action).pos(0, y)
                    .size(PANEL_W - PAD * 2, ROW_H));
        }

        return panel;
    }

    private ParentWidget<?> buildRow(TeamAction action) {
        ParentWidget<?> row = new ParentWidget<>();
        row.background(
            (ctx, x, y, w, h, theme) -> net.minecraft.client.gui.Gui
                .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor()));

        String key = "galaxia.gui.team_config.action." + action.name()
            .toLowerCase();
        row.child(
            new TextWidget<>(com.cleanroommc.modularui.api.drawable.IKey.lang(key))
                .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                .shadow(true)
                .pos(8, (ROW_H - net.minecraft.client.Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT) / 2 + 1));

        TeamRole[] roles = TeamRole.values();
        ButtonWidget<?> cycleBtn = new ButtonWidget<>()
            .background(
                (ctx, x, y, w, h, theme) -> BorderedRect.draw(
                    x,
                    y,
                    w,
                    h,
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()))
            .overlay((ctx, x, y, w, h, theme) -> {
                TeamRole cur = GTTeamsCompat.getGalaxiaTeamData()
                    .map(d -> d.getRequiredRole(action))
                    .orElse(action.getDefaultRole());
                String text = cur.name();
                net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
                int textW = fr.getStringWidth(text);
                fr.drawStringWithShadow(
                    text,
                    x + (w - textW) / 2,
                    y + (h - fr.FONT_HEIGHT) / 2 + 1,
                    EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
            })
            .onMousePressed(mb -> {
                TeamRole cur = GTTeamsCompat.getGalaxiaTeamData()
                    .map(d -> d.getRequiredRole(action))
                    .orElse(action.getDefaultRole());
                TeamRole next = roles[(cur.ordinal() + 1) % roles.length];
                GTTeamsCompat.getGalaxiaTeamData()
                    .ifPresent(d -> d.setRequiredRole(action, next));
                GALAXIA_NETWORK.sendToServer(TeamConfigPacket.permission(action, next));
                return true;
            });
        row.child(
            cycleBtn.right(8)
                .size(72, ROW_H - 4));
        return row;
    }

}
