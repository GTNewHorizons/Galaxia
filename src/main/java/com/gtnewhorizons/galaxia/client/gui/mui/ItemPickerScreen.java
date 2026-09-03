package com.gtnewhorizons.galaxia.client.gui.mui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

/**
 * A minimal MUI screen with a single phantom item slot and NEI enabled.
 *
 * <p>
 * Opens via {@link #FACTORY} when the user wants to pick an item for outpost logistics routing.
 * Once the user places an item the stack is stored in {@link #pendingPick}. The caller that opened
 * the picker polls the result on its next update and applies it.
 *
 * <p>
 * Because this screen is opened through {@link SimpleGuiFactory}, the full MUI lifecycle runs
 * (including {@code collectSyncValues}), so {@link PhantomItemSlot} sync handlers are always
 * properly initialised before any interaction occurs.
 */
public final class ItemPickerScreen implements IGuiHolder<GuiData> {

    public enum PickContext {
        OUTPOST_LOGISTICS,
        SIDEBAR_DEBUG
    }

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory("galaxia_item_picker", ItemPickerScreen::new);

    /** Set on the client when the user places an item in the slot; consumed by its opening UI. */
    private static volatile ItemStack pendingPick = null;
    /** Outpost assetId that this pick belongs to; set before opening the screen. */
    private static volatile CelestialAsset.ID pendingForOutpostId = null;
    /** Routing context for the pending pick result. */
    private static volatile PickContext pendingContext = null;
    /** Screen to restore after the picker captured an item. */
    private static volatile GuiScreen pendingReturnScreen = null;

    /**
     * Call before opening the screen so the result can be routed back to the correct outpost
     * even if the starmap screen was closed and reopened in between.
     */
    public static void setPendingForOutpost(CelestialAsset.ID outpostId) {
        clearPendingState();
        pendingReturnScreen = Minecraft.getMinecraft().currentScreen;
        pendingForOutpostId = outpostId;
        pendingContext = PickContext.OUTPOST_LOGISTICS;
    }

    public static void setPendingForSidebarDebug() {
        setPendingForSidebarDebug(Minecraft.getMinecraft().currentScreen);
    }

    public static void setPendingForSidebarDebugWithoutReturnScreen() {
        setPendingForSidebarDebug(null);
    }

    public static void setPendingForSidebarDebug(GuiScreen returnScreen) {
        clearPendingState();
        pendingReturnScreen = returnScreen;
        pendingContext = PickContext.SIDEBAR_DEBUG;
    }

    public static GuiScreen cancelPendingPick() {
        if (pendingPick == null && pendingForOutpostId == null
            && pendingContext == null
            && pendingReturnScreen == null) {
            return null;
        }
        GuiScreen returnScreen = pendingReturnScreen;
        clearPendingState();
        return returnScreen;
    }

    private static void clearPendingState() {
        pendingPick = null;
        pendingForOutpostId = null;
        pendingContext = null;
        pendingReturnScreen = null;
    }

    public static CelestialAsset.ID getPendingForOutpostId() {
        return pendingForOutpostId;
    }

    public static boolean hasPendingPickForOutpost() {
        return pendingPick != null && pendingForOutpostId != null && pendingContext == null;
    }

    public static boolean hasPendingPickForSidebarDebug() {
        return pendingPick != null && pendingForOutpostId == null && pendingContext == null;
    }

    public static boolean hasPendingPicker() {
        return pendingContext != null;
    }

    /** Returns and clears the pending outpost pick and context, or {@code null} if none. */
    public static ItemStack pollPendingPickForOutpost() {
        if (!hasPendingPickForOutpost()) return null;
        ItemStack pick = pendingPick;
        clearPendingState();
        return pick;
    }

    /** Returns and clears the pending sidebar-debug pick and context, or {@code null} if none. */
    public static ItemStack pollPendingPickForSidebarDebug() {
        if (!hasPendingPickForSidebarDebug()) return null;
        ItemStack pick = pendingPick;
        clearPendingState();
        return pick;
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        // Show NEI so the user can drag items from it
        settings.getRecipeViewerSettings()
            .enable();
        ModularPanel panel = new ModularPanel("galaxia_item_picker") {

            @Override
            public boolean onKeyPressed(char typedChar, int keyCode) {
                Minecraft minecraft = Minecraft.getMinecraft();
                boolean closesPicker = keyCode == Keyboard.KEY_ESCAPE
                    || keyCode == minecraft.gameSettings.keyBindInventory.getKeyCode();
                if (!closesPicker || !hasPendingPicker()) {
                    return super.onKeyPressed(typedChar, keyCode);
                }
                minecraft.displayGuiScreen(cancelPendingPick());
                return true;
            }

            @Override
            public void onClose() {
                if (hasPendingPicker()) cancelPendingPick();
                super.onClose();
            }
        }.size(176, 96);

        ItemStackHandler handler = new ItemStackHandler(1);
        ModularSlot slot = new ModularSlot(handler, 0).changeListener((stack, onlyAmountChanged, client, init) -> {
            if (client && !init && stack != null) {
                pendingPick = stack.copy();
                GuiScreen returnScreen = pendingReturnScreen;
                pendingContext = null;
                pendingReturnScreen = null;
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
        panel.child(
            cancelButton().pos(116, 8)
                .size(52, 18));

        return panel;
    }

    private static ButtonWidget<?> cancelButton() {
        return new ButtonWidget<>()
            .background(
                (ctx, x, y, w, h, theme) -> Gui
                    .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor()))
            .hoverBackground(
                (ctx, x, y, w, h, theme) -> Gui
                    .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()))
            .overlay((ctx, x, y, w, h, theme) -> {
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                String text = "Cancel";
                int textW = fr.getStringWidth(text);
                fr.drawStringWithShadow(
                    text,
                    x + (w - textW) / 2,
                    y + (h - fr.FONT_HEIGHT) / 2 + 1,
                    EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
            })
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                Minecraft.getMinecraft()
                    .displayGuiScreen(cancelPendingPick());
                return true;
            })
            .tooltipDynamic(t -> t.addLine("Return without picking an item"));
    }
}
