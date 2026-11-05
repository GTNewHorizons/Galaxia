package com.gtnewhorizons.galaxia.client.gui;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.compat.teams.TeamRole;
import com.gtnewhorizons.galaxia.core.network.TeamConfigPacket;

/** Window over the galactic map with the role each team action needs */
public final class TeamPermissionScreen {

    public static final String WINDOW_ID = "starmap.team";

    private static final int LABEL_WIDTH = 120;
    private static final int ROLE_WIDTH = 64;

    private TeamPermissionScreen() {}

    public static void open() {
        WindowLayer layer = WindowLayer.current();
        if (layer != null) open(layer);
    }

    public static void open(WindowLayer layer) {
        layer.toggle(
            WindowSpec.of(WINDOW_ID, "galaxia.gui.team_config.title")
                .centered()
                .body(TeamPermissionScreen::body));
    }

    private static void body(GalaxiaUi.Column column) {
        column.section(StatCollector.translateToLocal("galaxia.gui.team_config.roles"));
        for (TeamAction action : TeamAction.values()) {
            String label = StatCollector.translateToLocal(
                "galaxia.gui.team_config.action." + action.name()
                    .toLowerCase());
            column.row(
                GalaxiaUi.clipped(label, LABEL_WIDTH, GalaxiaGuiStyle.TEXT)
                    .width(LABEL_WIDTH)
                    .alignment(Alignment.CenterLeft),
                roleButton(action));
        }
    }

    /** Role the action needs; only the owner can change it, everyone else sees it dimmed */
    private static ButtonWidget<?> roleButton(TeamAction action) {
        ButtonWidget<?> button = GalaxiaUi.button(IKey.str(""), ROLE_WIDTH, () -> {
            if (!isOwner()) return;
            TeamRole[] roles = TeamRole.values();
            TeamRole next = roles[(requiredRole(action).ordinal() + 1) % roles.length];
            GTTeamsCompat.getGalaxiaTeamData()
                .ifPresent(data -> data.setRequiredRole(action, next));
            GALAXIA_NETWORK.sendToServer(TeamConfigPacket.permission(action, next));
        });
        IDrawable editable = IKey.dynamic(() -> requiredRole(action).name())
            .color(GalaxiaGuiStyle.TEXT)
            .shadow(true);
        IDrawable fixed = IKey.dynamic(() -> requiredRole(action).name())
            .color(GalaxiaGuiStyle.TEXT_DIM)
            .shadow(true);
        button.overlay(new DynamicDrawable(() -> isOwner() ? editable : fixed));
        return button;
    }

    private static TeamRole requiredRole(TeamAction action) {
        return GTTeamsCompat.getGalaxiaTeamData()
            .map(data -> data.getRequiredRole(action))
            .orElse(action.getDefaultRole());
    }

    private static boolean isOwner() {
        return GTTeamsCompat.isOwner(Minecraft.getMinecraft().thePlayer);
    }
}
