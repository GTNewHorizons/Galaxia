package com.gtnewhorizons.galaxia.client.gui.mui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

/**
 * A minimal MUI screen with a single phantom item slot and NEI enabled.
 *
 * <p>
 * Opens via {@link #FACTORY} when the user wants to pick an item for outpost logistics routing.
 * Once the user places an item the stack is stored in {@link #pendingPick}. The caller polls
 * {@link #pollPendingPick()} on the next tick (in {@code StarmapAssetActionsWidget.onUpdate})
 * and adds it to the logistics configuration.
 *
 * <p>
 * Because this screen is opened through {@link SimpleGuiFactory}, the full MUI lifecycle runs
 * (including {@code collectSyncValues}), so {@link PhantomItemSlot} sync handlers are always
 * properly initialised before any interaction occurs.
 */
public final class ItemPickerScreen implements IGuiHolder<GuiData> {

    public enum PickContext {
        OUTPOST_LOGISTICS,
        MINER_BLACKLIST,
        SIDEBAR_DEBUG
    }

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory("galaxia_item_picker", ItemPickerScreen::new);

    /** Set on the client when the user places an item in the slot; consumed by the map widget. */
    private static volatile ItemStack pendingPick = null;
    /** Outpost assetId that this pick belongs to; set before opening the screen. */
    private static volatile CelestialAsset.ID pendingForOutpostId = null;
    /** Module index for module-scoped picks such as miner blacklist configuration. */
    private static volatile int pendingModuleIndex = -1;
    /** Routing context for the pending pick result. */
    private static volatile PickContext pendingContext = null;
    /** Screen to restore after the picker captured an item. */
    private static volatile GuiScreen pendingReturnScreen = null;

    /**
     * Call before opening the screen so the result can be routed back to the correct outpost
     * even if the starmap screen was closed and reopened in between.
     */
    public static void setPendingForOutpost(CelestialAsset.ID outpostId) {
        pendingReturnScreen = Minecraft.getMinecraft().currentScreen;
        pendingForOutpostId = outpostId;
        pendingModuleIndex = -1;
        pendingContext = PickContext.OUTPOST_LOGISTICS;
    }

    public static void setPendingForMinerBlacklist(CelestialAsset.ID outpostId, int moduleIndex) {
        pendingReturnScreen = Minecraft.getMinecraft().currentScreen;
        pendingForOutpostId = outpostId;
        pendingModuleIndex = moduleIndex;
        pendingContext = PickContext.MINER_BLACKLIST;
    }

    public static void setPendingForSidebarDebug() {
        pendingReturnScreen = Minecraft.getMinecraft().currentScreen;
        pendingForOutpostId = null;
        pendingModuleIndex = -1;
        pendingContext = PickContext.SIDEBAR_DEBUG;
    }

    public static CelestialAsset.ID getPendingForOutpostId() {
        return pendingForOutpostId;
    }

    public static boolean hasPendingPickForOutpost() {
        return pendingPick != null && pendingForOutpostId != null && pendingContext == PickContext.OUTPOST_LOGISTICS;
    }

    public static boolean hasPendingPickForMinerBlacklist() {
        return pendingPick != null && pendingForOutpostId != null && pendingContext == PickContext.MINER_BLACKLIST;
    }

    public static boolean hasPendingPickForSidebarDebug() {
        return pendingPick != null && pendingContext == PickContext.SIDEBAR_DEBUG;
    }

    public static int getPendingModuleIndex() {
        return pendingModuleIndex;
    }

    /** Returns and clears the pending outpost pick and context, or {@code null} if none. */
    public static ItemStack pollPendingPickForOutpost() {
        if (!hasPendingPickForOutpost()) return null;
        ItemStack pick = pendingPick;
        pendingPick = null;
        pendingForOutpostId = null;
        pendingModuleIndex = -1;
        pendingContext = null;
        pendingReturnScreen = null;
        return pick;
    }

    public static ItemStack pollPendingPickForMinerBlacklist() {
        if (!hasPendingPickForMinerBlacklist()) return null;
        ItemStack pick = pendingPick;
        pendingPick = null;
        pendingForOutpostId = null;
        pendingModuleIndex = -1;
        pendingContext = null;
        pendingReturnScreen = null;
        return pick;
    }

    /** Returns and clears the pending sidebar-debug pick and context, or {@code null} if none. */
    public static ItemStack pollPendingPickForSidebarDebug() {
        if (!hasPendingPickForSidebarDebug()) return null;
        ItemStack pick = pendingPick;
        pendingPick = null;
        pendingForOutpostId = null;
        pendingModuleIndex = -1;
        pendingContext = null;
        pendingReturnScreen = null;
        return pick;
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        // Show NEI so the user can drag items from it
        settings.getRecipeViewerSettings()
            .enable();
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_item_picker", 176, 96);

        ItemStackHandler handler = new ItemStackHandler(1);
        ModularSlot slot = new ModularSlot(handler, 0).changeListener((stack, onlyAmountChanged, client, init) -> {
            if (client && !init && stack != null) {
                pendingPick = stack.copy();
                GuiScreen returnScreen = pendingReturnScreen;
                Minecraft.getMinecraft()
                    .displayGuiScreen(returnScreen);
            }
        });

        panel.child(
            IKey.str("Pick item")
                .asWidget()
                .pos(8, 8)
                .size(100, 12));
        panel.child(
            IKey.str("Drag item from NEI")
                .asWidget()
                .pos(8, 24)
                .size(160, 12));
        panel.child(
            IKey.str("into the ghost slot")
                .asWidget()
                .pos(8, 38)
                .size(160, 12));
        panel.child(
            new PhantomItemSlot().slot(slot)
                .pos(78, 56)
                .size(20, 20));

        return panel;
    }
}
