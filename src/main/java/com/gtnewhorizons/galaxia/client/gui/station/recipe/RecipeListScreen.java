package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;

public final class RecipeListScreen implements IGuiHolder<GuiData> {

    private static final int PANEL_WIDTH = 220;
    private static final int HEADER_H = 22;
    private static final int ROW_H = 16;
    private static final int FOOTER_H = 26;
    private static final int PAD = 8;

    static final SimpleGuiFactory FACTORY = new SimpleGuiFactory("galaxia_recipe_list", RecipeListScreen::new);

    private static volatile @Nullable CelestialAsset.ID pendingAssetId;
    private static volatile int pendingModuleIndex = -1;
    private static volatile @Nullable ModuleInstance pendingModule;

    public static void open(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module) {
        if (assetId == null) {
            throw new IllegalArgumentException("RecipeListScreen.open: assetId is null");
        }
        if (module == null) {
            throw new IllegalArgumentException("RecipeListScreen.open: module is null");
        }
        if (!(module.component() instanceof IRecipeModule)) {
            throw new IllegalArgumentException(
                "RecipeListScreen.open: module " + module.kind() + " is not IRecipeModule");
        }
        pendingAssetId = assetId;
        pendingModuleIndex = moduleIndex;
        pendingModule = module;
        resetFactoryHolder();
        FACTORY.openClient();
    }

    private static void resetFactoryHolder() {
        try {
            Field field = SimpleGuiFactory.class.getDeclaredField("guiHolder");
            field.setAccessible(true);
            field.set(FACTORY, null);
        } catch (Exception e) {
            Galaxia.LOG.error("[RecipeListScreen] Failed to reset factory holder", e);
        }
    }

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        CelestialAsset.ID assetId = pendingAssetId;
        int moduleIndex = pendingModuleIndex;
        ModuleInstance module = pendingModule;
        pendingAssetId = null;
        pendingModuleIndex = -1;
        pendingModule = null;

        if (assetId == null || module == null || !(module.component() instanceof IRecipeModule)) {
            return ModularPanel.defaultPanel("galaxia_recipe_list_empty", PANEL_WIDTH, 40)
                .disableThemeBackground(true);
        }

        RecipeConfig config = ((IRecipeModule) module.component()).getRecipeConfig();
        List<RecipeSlot> slots = config != null ? config.slots()
            .toList() : List.of();
        int rows = Math.max(1, Math.min(slots.size(), 16)) + (slots.isEmpty() ? 1 : 0);
        int height = HEADER_H + rows * ROW_H + FOOTER_H;

        ModularPanel panel = ModularPanel.defaultPanel("galaxia_recipe_list", PANEL_WIDTH, height)
            .disableThemeBackground(true);

        panel.child(
            new RecipeListWidget(assetId, moduleIndex, module, config, slots).pos(0, 0)
                .size(PANEL_WIDTH, height));

        return panel;
    }

    private static final class RecipeListWidget extends ParentWidget<RecipeListWidget> {

        private final CelestialAsset.ID assetId;
        private final int moduleIndex;
        private final ModuleInstance module;
        private final @Nullable RecipeConfig config;
        private final List<RecipeSlot> slots;
        private final List<Integer> removeRows = new ArrayList<>();
        private int closeY;

        RecipeListWidget(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module,
            @Nullable RecipeConfig config, List<RecipeSlot> slots) {
            this.assetId = assetId;
            this.moduleIndex = moduleIndex;
            this.module = module;
            this.config = config;
            this.slots = slots;

            listenGuiAction((IGuiAction.MousePressed) button -> {
                if (button != 0) return false;
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                int mx = getContext().getAbsMouseX();
                int my = getContext().getAbsMouseY();
                int rx = mx - getArea().rx;
                int ry = my - getArea().ry;

                // Check remove buttons
                for (int i = 0; i < removeRows.size(); i++) {
                    int rowY = removeRows.get(i);
                    if (ry >= rowY && ry < rowY + ROW_H) {
                        String removeLabel = "[Remove]";
                        int removeX = getArea().width - PAD - fr.getStringWidth(removeLabel);
                        if (rx >= removeX && rx <= removeX + fr.getStringWidth(removeLabel)) {
                            CelestialClient.updateModuleRecipeSlot(
                                assetId,
                                moduleIndex,
                                AssetModuleUpdatePacket.ConfigAction.REMOVE_RECIPE_SLOT,
                                (byte) i,
                                null);
                            return true;
                        }
                    }
                }

                // Check close button
                if (ry >= closeY && ry < closeY + ROW_H) {
                    String closeLabel = "[Close]";
                    int closeW = fr.getStringWidth(closeLabel);
                    int closeX = (getArea().width - closeW) / 2;
                    if (rx >= closeX && rx <= closeX + closeW) {
                        getContext().getScreen()
                            .close();
                        return true;
                    }
                }

                return false;
            });
        }

        @Override
        public boolean canHoverThrough() {
            return false;
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            removeRows.clear();
            int w = getArea().width;
            int h = getArea().height;
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

            BorderedRect.draw(
                0,
                0,
                w,
                h,
                EnumColors.MAP_COLOR_STATION_PANEL_BG.getColor(),
                EnumColors.MAP_COLOR_STATION_PANEL_BORDER.getColor());

            int y = PAD;

            // Header
            String title = module.kind()
                .getDisplayName() + " — Recipes";
            fr.drawStringWithShadow(title, PAD, y, EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
            y += HEADER_H;

            // Recipe list
            if (config == null || slots.isEmpty()) {
                fr.drawStringWithShadow("No recipes configured", PAD, y, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
                y += ROW_H;
            } else {
                for (int i = 0; i < slots.size(); i++) {
                    RecipeSlot slot = slots.get(i);
                    String label = "#" + i
                        + " "
                        + (slot.enabled() ? "[ON]" : "[OFF]")
                        + "  in:"
                        + slot.inputGuard()
                        + "  out:"
                        + slot.outputGuard();
                    int enabledColor = slot.enabled() ? EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor()
                        : EnumColors.MAP_COLOR_TEXT_DANGER.getColor();
                    fr.drawStringWithShadow(label, PAD, y, enabledColor);

                    String removeLabel = "[Remove]";
                    int removeX = w - PAD - fr.getStringWidth(removeLabel);
                    fr.drawStringWithShadow(removeLabel, removeX, y, EnumColors.MAP_COLOR_TEXT_WARNING.getColor());

                    removeRows.add(y);
                    y += ROW_H;
                }
            }

            // Close button
            String closeLabel = "[Close]";
            int closeW = fr.getStringWidth(closeLabel);
            int closeX = (w - closeW) / 2;
            closeY = y + 4;
            fr.drawStringWithShadow(closeLabel, closeX, closeY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }
    }
}
