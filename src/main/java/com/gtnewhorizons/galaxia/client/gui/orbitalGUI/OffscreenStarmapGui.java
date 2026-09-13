package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.sbancuz.offscreen.integration.mui2.OffscreenSimpleFactory;

/**
 * Server-synced galactic starmap shown in an Offscreen window.
 * <p>
 * This is an {@link OffscreenSimpleFactory} so the keybind can open it without holding anything. The builder runs on
 * both sides (same panel as the {@code ItemGalacticMap} flow), while the Offscreen mod owns the synced session, the
 * divert mixins, and the window lifecycle ({@code openOffscreen}/{@code toggleOffscreen}).
 * <p>
 * This class must stay free of client-only references: the factory is registered on both sides.
 */
public final class OffscreenStarmapGui {

    private OffscreenStarmapGui() {}

    public static final OffscreenSimpleFactory FACTORY = new OffscreenSimpleFactory(
        "galaxia_offscreen_starmap",
        Galaxia.MODID,
        OffscreenStarmapGui::build);

    private static ModularPanel build(final GuiData data, final PanelSyncManager syncManager,
        final UISettings settings) {
        return GalacticMapWidget.build(syncManager, data.getPlayer());
    }
}
