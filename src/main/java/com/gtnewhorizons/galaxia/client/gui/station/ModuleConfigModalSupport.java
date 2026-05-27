package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleConfigModalSupport {

    static final int HEADER_HEIGHT = 24;
    static final int PANEL_PADDING = 8;

    private ModuleConfigModalSupport() {}

    static void drawFrame(String title, int width, int height) {
        drawFrameAt(title, 0, 0, width, height);
    }

    static void drawFrameAt(String title, int x, int y, int width, int height) {
        net.minecraft.client.gui.Gui.drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_MODAL_BG.getColor());
        net.minecraft.client.gui.Gui
            .drawRect(x, y, x + width, y + HEADER_HEIGHT, EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
        BorderedRect.draw(
            x,
            y,
            width,
            height,
            EnumColors.MAP_COLOR_TRANSPARENT.getColor(),
            EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
        drawLine(title, x + PANEL_PADDING, y + PANEL_PADDING, EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
    }

    static ButtonWidget<?> button(String label, Runnable onClick) {
        return button(() -> true, label, onClick);
    }

    static ButtonWidget<?> button(BooleanSupplier enabledSupplier, String label, Runnable onClick) {
        return button(enabledSupplier, () -> label, onClick);
    }

    static ButtonWidget<?> button(BooleanSupplier enabledSupplier, Supplier<String> labelSupplier, Runnable onClick) {
        return new ButtonWidget<>()
            .background(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), false)))
            .hoverBackground(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), true)))
            .overlay(drawable((ctx, x, y, w, h) -> {
                if (!enabledSupplier.getAsBoolean()) return;
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                String label = labelSupplier.get();
                String trimmed = fr.trimStringToWidth(label, w - 4);
                int textW = fr.getStringWidth(trimmed);
                fr.drawStringWithShadow(
                    trimmed,
                    x + (w - textW) / 2,
                    y + (h - fr.FONT_HEIGHT) / 2 + 1,
                    EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
            }))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0 || !enabledSupplier.getAsBoolean()) return false;
                onClick.run();
                return true;
            })
            .setEnabledIf(w -> enabledSupplier.getAsBoolean());
    }

    static ButtonWidget<?> checkbox(BooleanSupplier enabledSupplier, BooleanSupplier checkedSupplier, String tooltip,
        Runnable onClick) {
        return new ButtonWidget<>()
            .background(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), false)))
            .hoverBackground(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), true)))
            .overlay(drawable((ctx, x, y, w, h) -> {
                if (!enabledSupplier.getAsBoolean() || !checkedSupplier.getAsBoolean()) return;
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                int textW = fr.getStringWidth("X");
                fr.drawStringWithShadow(
                    "X",
                    x + (w - textW) / 2,
                    y + (h - fr.FONT_HEIGHT) / 2 + 1,
                    EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
            }))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0 || !enabledSupplier.getAsBoolean()) return false;
                onClick.run();
                return true;
            })
            .tooltipDynamic(t -> { if (enabledSupplier.getAsBoolean()) t.addLine(tooltip); })
            .onUpdateListener(ButtonWidget::markTooltipDirty, true)
            .setEnabledIf(w -> enabledSupplier.getAsBoolean());
    }

    static ButtonWidget<?> button(BooleanSupplier enabledSupplier, String label, String tooltip, Runnable onClick) {
        return button(enabledSupplier, label, onClick)
            .tooltipDynamic(t -> { if (enabledSupplier.getAsBoolean()) t.addLine(tooltip); })
            .onUpdateListener(ButtonWidget::markTooltipDirty, true);
    }

    static ButtonWidget<?> button(BooleanSupplier enabledSupplier, Supplier<String> labelSupplier, String tooltip,
        Runnable onClick) {
        return button(enabledSupplier, labelSupplier, onClick)
            .tooltipDynamic(t -> { if (enabledSupplier.getAsBoolean()) t.addLine(tooltip); })
            .onUpdateListener(ButtonWidget::markTooltipDirty, true);
    }

    static ButtonWidget<?> iconButton(BooleanSupplier enabledSupplier, ItemStack icon, String tooltip,
        Runnable onClick) {
        return new ButtonWidget<>()
            .background(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), false)))
            .hoverBackground(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), true)))
            .overlay(drawable((ctx, x, y, w, h) -> {
                if (!enabledSupplier.getAsBoolean() || icon == null) return;
                renderItemIcon(icon, x + (w - 16) / 2, y + (h - 16) / 2);
            }))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0 || !enabledSupplier.getAsBoolean()) return false;
                onClick.run();
                return true;
            })
            .tooltipDynamic(t -> { if (enabledSupplier.getAsBoolean()) t.addLine(tooltip); })
            .onUpdateListener(ButtonWidget::markTooltipDirty, true)
            .setEnabledIf(w -> enabledSupplier.getAsBoolean());
    }

    static ButtonWidget<?> textureIconButton(BooleanSupplier enabledSupplier, ResourceLocation icon, String tooltip,
        Runnable onClick) {
        return new ButtonWidget<>()
            .background(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), false)))
            .hoverBackground(
                drawable((ctx, x, y, w, h) -> drawButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), true)))
            .overlay(drawable((ctx, x, y, w, h) -> {
                if (!enabledSupplier.getAsBoolean() || icon == null) return;
                renderTextureIcon(icon, x + (w - 12) / 2, y + (h - 12) / 2, 12, 12);
            }))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0 || !enabledSupplier.getAsBoolean()) return false;
                onClick.run();
                return true;
            })
            .tooltipDynamic(t -> { if (enabledSupplier.getAsBoolean()) t.addLine(tooltip); })
            .onUpdateListener(ButtonWidget::markTooltipDirty, true)
            .setEnabledIf(w -> enabledSupplier.getAsBoolean());
    }

    static int drawLine(String text, int x, int y, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(text, x, y, color);
        return y + fr.FONT_HEIGHT + 3;
    }

    static int drawCenteredLine(String text, int centerX, int y, int maxWidth, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String trimmed = fr.trimStringToWidth(text, maxWidth);
        fr.drawStringWithShadow(trimmed, centerX - fr.getStringWidth(trimmed) / 2, y, color);
        return y + fr.FONT_HEIGHT + 3;
    }

    static int drawTrimmedLine(String text, int x, int y, int maxWidth, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(fr.trimStringToWidth(text, maxWidth), x, y, color);
        return y + fr.FONT_HEIGHT + 3;
    }

    static @Nullable AutomatedFacility facility(CelestialAsset.ID assetId) {
        return assetId != null && CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility
            : null;
    }

    static @Nullable CelestialAsset celestialAsset(CelestialAsset.ID assetId) {
        return assetId != null ? CelestialClient.getByAssetId(assetId) : null;
    }

    static @Nullable ModuleInstance module(CelestialAsset.ID assetId, int moduleIndex) {
        AutomatedFacility facility = facility(assetId);
        if (facility == null || moduleIndex < 0
            || moduleIndex >= facility.modules()
                .size()) {
            return null;
        }
        return facility.modules()
            .get(moduleIndex);
    }

    static int moduleIndex(CelestialAsset.ID assetId, ModuleInstance.ID moduleId) {
        AutomatedFacility facility = facility(assetId);
        if (facility == null || moduleId == null) return -1;
        for (int i = 0; i < facility.modules()
            .size(); i++) {
            if (moduleId.equals(
                facility.modules()
                    .get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    static @Nullable ModuleInstance module(CelestialAsset.ID assetId, ModuleInstance.ID moduleId) {
        int moduleIndex = moduleIndex(assetId, moduleId);
        return moduleIndex >= 0 ? module(assetId, moduleIndex) : null;
    }

    static boolean refundBlockedByFullInventory(CelestialAsset.ID assetId, ModuleInstance module) {
        AutomatedFacility facility = facility(assetId);
        return facility != null && module != null
            && module.operationOrNull() != null
            && module.operationOrNull()
                .phase() == ModuleOperationPhase.REFUNDING
            && facility.isItemInventoryFull();
    }

    static String formatEu(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }

    static String moduleTitle(ModuleInstance module, String suffix) {
        StationTileCoord anchor = module.anchor();
        return moduleDisplayName(module) + " (" + (int) anchor.dx() + "," + (int) anchor.dy() + ") " + suffix;
    }

    static IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }

    static void renderItemIcon(ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null || mc.getTextureManager() == null || stack == null) return;
        com.cleanroommc.modularui.utils.GlStateManager.pushMatrix();
        com.cleanroommc.modularui.utils.GlStateManager.translate(x, y, 200f);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderItem renderItem = RenderItem.getInstance();
        float previousZ = renderItem.zLevel;
        renderItem.zLevel = 200f;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        renderItem.zLevel = previousZ;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        com.cleanroommc.modularui.utils.GlStateManager.popMatrix();
    }

    static void renderTextureIcon(ResourceLocation texture, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureManager() == null || texture == null) return;
        mc.getTextureManager()
            .bindTexture(texture);
        com.cleanroommc.modularui.utils.GlStateManager.enableTexture2D();
        com.cleanroommc.modularui.utils.GlStateManager.enableBlend();
        com.cleanroommc.modularui.utils.GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, 0.0, 1.0);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, 1.0, 1.0);
        tessellator.addVertexWithUV(x + width, y, 0.0, 1.0, 0.0);
        tessellator.addVertexWithUV(x, y, 0.0, 0.0, 0.0);
        tessellator.draw();
    }

    private static String moduleDisplayName(ModuleInstance module) {
        if (module.component() instanceof ModuleHammer hammer) {
            return hammer.variant()
                .name() + " Hammer";
        }
        String name = module.kind()
            .name()
            .toLowerCase();
        StringBuilder title = new StringBuilder(name.length());
        boolean nextUpper = true;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '_') {
                title.append(' ');
                nextUpper = true;
                continue;
            }
            title.append(nextUpper ? Character.toUpperCase(ch) : ch);
            nextUpper = false;
        }
        return title.toString();
    }

    private static void drawButtonBackground(int x, int y, int w, int h, boolean enabled, boolean hovered) {
        if (!enabled) return;
        BorderedRect.draw(
            x,
            y,
            w,
            h,
            hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
            EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
    }
}
