package com.gtnewhorizons.galaxia.compat.recipe;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.WidgetOutline;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTRecipe;

public final class GTRecipePickerScreen implements IGuiHolder<GuiData> {

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory(
        "galaxia_recipe_picker",
        GTRecipePickerScreen::new);

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 250;
    private static final int HEADER_HEIGHT = 24;
    private static final int CONTENT_TOP = HEADER_HEIGHT + 14;
    private static final int PANEL_PADDING = 8;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 2;
    private static final int MAX_VISIBLE_ROWS = 7;
    private static final int ICON_SIZE = 16;
    private static final int TEXT_BASELINE_OFFSET = 1;
    private static final int OUT_ICON_OFFSET = 12;

    static volatile @Nullable CelestialAsset.ID pendingAssetId;
    static volatile @Nullable StationTileCoord pendingCoord;
    static volatile @Nullable RecipeSnapshot pendingSelection;

    public static void open(CelestialAsset.ID assetId, StationTileCoord coord) {
        pendingAssetId = assetId;
        pendingCoord = coord;
        pendingSelection = null;
        FACTORY.openClient();
    }

    public static void clearPending() {
        pendingAssetId = null;
        pendingCoord = null;
        pendingSelection = null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
        return new ModularScreen(Galaxia.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        syncManager.syncValue(StarmapActionSyncHandler.KEY, new StarmapActionSyncHandler());
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_recipe_picker", PANEL_WIDTH, PANEL_HEIGHT);
        ParentWidget<?> backgroundLayer = new PassiveBackgroundLayer().pos(0, 0)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .background(drawable((ctx, x, y, w, h) -> {
                net.minecraft.client.gui.Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_MODAL_BG.getColor());
                net.minecraft.client.gui.Gui
                    .drawRect(x, y, x + w, y + HEADER_HEIGHT, EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
            }));
        panel.child(backgroundLayer);
        panel.child(WidgetOutline.create(backgroundLayer, 3, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));

        String title = "Select Recipe";
        AutomatedFacility facility = resolveFacility();
        if (facility != null && pendingCoord != null) {
            ModuleInstance module = facility.stationLayout()
                .moduleAt(pendingCoord);
            if (module != null) {
                title = "Select Recipe \u2014 " + module.kind()
                    .getDisplayName();
            }
        }
        panel.child(
            new TextWidget<>(IKey.str(title)).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(PANEL_PADDING, PANEL_PADDING));

        if (facility == null || pendingCoord == null) {
            panel.child(
                new TextWidget<>(IKey.str("No station selected")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .shadow(true)
                    .pos(PANEL_PADDING, CONTENT_TOP));
            return panel;
        }

        ModuleInstance module = facility.stationLayout()
            .moduleAt(pendingCoord);
        if (module == null || !(module.component() instanceof IRecipeModule recipeModule)) {
            panel.child(
                new TextWidget<>(IKey.str("No recipe module selected"))
                    .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .shadow(true)
                    .pos(PANEL_PADDING, CONTENT_TOP));
            return panel;
        }

        GTRecipeMapId mapId = GTRecipeMapId.fromRecipeMapName(recipeModule.getRecipeMapName());
        if (mapId == null || mapId == GTRecipeMapId.INVALID) {
            panel.child(
                new TextWidget<>(IKey.str("Unknown recipe map")).color(EnumColors.MAP_COLOR_TEXT_DANGER.getColor())
                    .shadow(true)
                    .pos(PANEL_PADDING, CONTENT_TOP));
            return panel;
        }

        GTRecipe[] allRecipes = GTRecipeMapId.getRecipes(mapId);
        if (allRecipes == null) {
            panel.child(
                new TextWidget<>(IKey.str("No available recipes")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .shadow(true)
                    .pos(PANEL_PADDING, CONTENT_TOP));
            return panel;
        }

        List<RecipeEntry> entries = new ArrayList<>();
        int index = 0;
        for (GTRecipe recipe : allRecipes) {
            if (recipe == null || recipe.mHidden || recipe.mFakeRecipe) {
                index++;
                continue;
            }
            entries.add(new RecipeEntry(index, recipe));
            index++;
        }

        if (entries.isEmpty()) {
            panel.child(
                new TextWidget<>(IKey.str("No available recipes")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .shadow(true)
                    .pos(PANEL_PADDING, CONTENT_TOP));
            return panel;
        }

        int contentY = HEADER_HEIGHT + PANEL_PADDING;
        int contentWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        int scrollHeight = Math.min(entries.size(), MAX_VISIBLE_ROWS) * (ROW_HEIGHT + ROW_GAP);

        ScrollWidget<?> scroll = new ScrollWidget<>().pos(PANEL_PADDING, contentY)
            .size(contentWidth, scrollHeight);

        int rowY = 0;
        for (RecipeEntry entry : entries) {
            int rowIndex = entry.index;
            GTRecipe recipe = entry.recipe;
            GTRecipeMapId finalMapId = mapId;
            scroll.child(
                createRecipeRow(entry, finalMapId).pos(0, rowY)
                    .size(contentWidth, ROW_HEIGHT));
            rowY += ROW_HEIGHT + ROW_GAP;
        }
        panel.child(scroll);

        return panel;
    }

    private ButtonWidget<?> createRecipeRow(RecipeEntry entry, GTRecipeMapId mapId) {
        return new ButtonWidget<>()
            .background(
                drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor())))
            .hoverBackground(
                drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor())))
            .overlay(drawable((ctx, x, y, w, h) -> drawRecipeRow(entry, x, y, w, h)))
            .onMouseTapped(mouseButton -> {
                if (mouseButton != 0) return false;
                RecipeSnapshot snapshot = new RecipeSnapshot(
                    (byte) mapId.ordinal(),
                    entry.index,
                    RecipeSnapshot.computeContentHash(
                        entry.recipe.mInputs,
                        entry.recipe.mOutputs,
                        entry.recipe.mFluidInputs,
                        entry.recipe.mFluidOutputs,
                        entry.recipe.mOutputChances,
                        entry.recipe.mFluidOutputChances,
                        entry.recipe.mDuration,
                        entry.recipe.mEUt),
                    entry.recipe.mInputs,
                    entry.recipe.mOutputs,
                    entry.recipe.mFluidInputs,
                    entry.recipe.mFluidOutputs,
                    entry.recipe.mOutputChances,
                    entry.recipe.mFluidOutputChances,
                    entry.recipe.mDuration,
                    entry.recipe.mEUt);
                pendingSelection = snapshot;
                Minecraft.getMinecraft()
                    .displayGuiScreen(null);
                return true;
            });
    }

    private static void drawRecipeRow(RecipeEntry entry, int x, int y, int width, int height) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        GTRecipe recipe = entry.recipe;

        int iconX = x + 4;
        int iconY = y + (height - ICON_SIZE) / 2;
        if (recipe.mInputs != null) {
            int shown = 0;
            for (ItemStack stack : recipe.mInputs) {
                if (stack == null) continue;
                if (shown >= 3) break;
                renderItemIcon(stack, iconX, iconY);
                iconX += ICON_SIZE + 1;
                shown++;
            }
        }

        int arrowX = iconX + 2;
        fr.drawStringWithShadow(
            "\u2192",
            arrowX,
            y + (height - fr.FONT_HEIGHT) / 2 + TEXT_BASELINE_OFFSET,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());

        int outIconX = arrowX + OUT_ICON_OFFSET;
        if (recipe.mOutputs != null) {
            int shown = 0;
            for (ItemStack stack : recipe.mOutputs) {
                if (stack == null) continue;
                if (shown >= 3) break;
                renderItemIcon(stack, outIconX, iconY);
                outIconX += ICON_SIZE + 1;
                shown++;
            }
        }

        String displayName = "Unknown";
        if (recipe.mOutputs != null) {
            for (ItemStack stack : recipe.mOutputs) {
                if (stack != null && stack.getItem() != null) {
                    displayName = stack.getDisplayName();
                    break;
                }
            }
        }
        int nameX = outIconX + 6;
        int maxNameWidth = width - (nameX - x) - 8;
        String trimmed = fr.trimStringToWidth(displayName, maxNameWidth);
        fr.drawStringWithShadow(
            trimmed,
            nameX,
            y + (height - fr.FONT_HEIGHT) / 2 + TEXT_BASELINE_OFFSET,
            EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
    }

    private static void renderItemIcon(ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null || mc.getTextureManager() == null) return;
        com.cleanroommc.modularui.utils.GlStateManager.pushMatrix();
        com.cleanroommc.modularui.utils.GlStateManager.translate(x, y, 200f);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderItem ri = RenderItem.getInstance();
        float prevZ = ri.zLevel;
        ri.zLevel = 200f;
        net.minecraft.client.renderer.OpenGlHelper
            .setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240f, 240f);
        ri.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        ri.zLevel = prevZ;
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        com.cleanroommc.modularui.utils.GlStateManager.popMatrix();
    }

    private static @Nullable AutomatedFacility resolveFacility() {
        CelestialAsset.ID assetId = pendingAssetId;
        if (assetId == null) return null;
        return CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility : null;
    }

    private IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }

    private static final class PassiveBackgroundLayer extends ParentWidget<PassiveBackgroundLayer> {

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }
    }

    private static final class RecipeEntry {

        final int index;
        final GTRecipe recipe;

        RecipeEntry(int index, GTRecipe recipe) {
            this.index = index;
            this.recipe = recipe;
        }
    }
}
