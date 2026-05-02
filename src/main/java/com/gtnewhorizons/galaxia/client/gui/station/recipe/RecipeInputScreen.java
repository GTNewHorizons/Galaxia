package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.GTRecipeMapId;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTRecipe;

public final class RecipeInputScreen implements IGuiHolder<GuiData> {

    static final SimpleGuiFactory FACTORY = new SimpleGuiFactory("galaxia_recipe_input", RecipeInputScreen::new);
    private static final int W = 200, H = 160, P = 6, SLOT = 18, COLS = 3;

    static volatile @Nullable ModuleInstance pendingModule;
    static volatile @Nullable GuiScreen pendingReturnScreen;

    private final ItemStackHandler[] inputs, outputs;
    private final int maxIn, maxOut;
    private GTRecipe matched;
    private List<GTRecipe> allRecipes;
    private GTRecipeMapId mapId;
    private String statusText = "Put items to find a recipe";
    private int statusColor = c(EnumColors.MAP_COLOR_TEXT_MUTED);

    public static void open(ModuleInstance module) {
        pendingModule = module;
        pendingReturnScreen = Minecraft.getMinecraft().currentScreen;
        FACTORY.openClient();
    }

    public RecipeInputScreen() {
        ModuleInstance m = pendingModule;
        gregtech.api.recipe.RecipeMap<?> map = m != null && m.component() instanceof IRecipeModule rm
            ? rm.getRecipeMap()
            : null;
        int mi = 1, mo = 4;
        if (map != null) {
            try {
                Object be = map.getBackend();
                Object bp = be.getClass()
                    .getMethod("getProperties")
                    .invoke(be);
                mi = bp.getClass()
                    .getField("maxItemInputs")
                    .getInt(bp);
                mo = bp.getClass()
                    .getField("maxItemOutputs")
                    .getInt(bp);
            } catch (Exception ignored) {}
        }
        this.maxIn = Math.max(1, mi);
        this.maxOut = Math.max(1, mo);
        this.inputs = new ItemStackHandler[maxIn];
        this.outputs = new ItemStackHandler[maxOut];
        for (int i = 0; i < maxIn; i++) inputs[i] = new ItemStackHandler(1);
        for (int i = 0; i < maxOut; i++) outputs[i] = new ItemStackHandler(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(GuiData d, ModularPanel p) {
        return new ModularScreen(Galaxia.MODID, p);
    }

    @Override
    public ModularPanel buildUI(GuiData gd, PanelSyncManager sm, UISettings s) {
        s.getRecipeViewerSettings()
            .enable();
        ModularPanel p = ModularPanel.defaultPanel("galaxia_recipe_input", W, H);
        ModuleInstance m = pendingModule;
        if (m == null || !(m.component() instanceof IRecipeModule rm)) {
            p.child(txt("No recipe module", c(EnumColors.MAP_COLOR_TEXT_MUTED), P, P));
            return p;
        }
        gregtech.api.recipe.RecipeMap<?> map = rm.getRecipeMap();
        if (map == null) {
            p.child(txt("No recipe map", c(EnumColors.MAP_COLOR_TEXT_DANGER), P, P + 20));
            return p;
        }
        @SuppressWarnings("unchecked")
        List<GTRecipe> list = new ArrayList<>((Collection<GTRecipe>) (Collection<?>) map.getAllRecipes());
        list.removeIf(r -> r.mHidden || r.mFakeRecipe);
        this.allRecipes = list;
        this.mapId = GTRecipeMapId.fromRecipeMapName(map.unlocalizedName);

        p.child(
            txt(
                m.kind()
                    .getDisplayName(),
                c(EnumColors.MAP_COLOR_TEXT_TITLE),
                P,
                P));

        // Input slots: right-aligned 3-col grid (GT5 style)
        int areaTop = P + 20;
        int rowsIn = (maxIn + COLS - 1) / COLS;
        for (int i = 0; i < maxIn; i++) {
            int row = i / COLS, colInRow = i % COLS;
            int slotsThisRow = Math.min(COLS, maxIn - row * COLS);
            int col = COLS - slotsThisRow + colInRow; // right-aligned
            final int idx = i;
            ModularSlot ms = new ModularSlot(inputs[i], 0).changeListener((stack, amt, cl, init) -> {
                if (init) return;
                if (stack != null && stack.stackSize > 1) {
                    stack.stackSize = 1;
                    inputs[idx].setStackInSlot(0, stack);
                }
                onInputChanged();
            });
            p.child(
                new PhantomItemSlot().slot(ms)
                    .pos(P + col * SLOT, areaTop + row * SLOT)
                    .size(SLOT, SLOT));
        }

        // Output slots: left-aligned 3-col grid, to the right of inputs with gap
        int outX = P + COLS * SLOT + 16;
        for (int i = 0; i < maxOut; i++) {
            int row = i / COLS, col = i % COLS;
            PhantomItemSlot ps = new PhantomItemSlot();
            ps.slot(new ModularSlot(outputs[i], 0))
                .pos(outX + col * SLOT, areaTop + row * SLOT)
                .size(SLOT, SLOT);
            ps.setEnabled(false);
            p.child(ps);
        }

        int rows = Math.max(rowsIn, (maxOut + COLS - 1) / COLS);
        int bottom = areaTop + rows * SLOT + 4;
        p.child(
            new StatusWidget(this).pos(P, bottom)
                .size(W - P * 2, 12));
        int btnY = bottom + 16;
        p.child(
            btn("Cancel", this::cancel).pos(P, btnY)
                .size(70, 20));
        p.child(
            btn("Confirm", this::confirm).pos(W - 80 - P, btnY)
                .size(80, 20));
        return p;
    }

    private void onInputChanged() {
        ItemStack[] in = new ItemStack[maxIn];
        for (int i = 0; i < maxIn; i++) in[i] = inputs[i].getStackInSlot(0);
        List<GTRecipe> matches = new ArrayList<>();
        for (GTRecipe r : allRecipes) if (matchesInputs(r, in)) matches.add(r);
        if (matches.size() == 1) {
            matched = matches.get(0);
            statusText = matched.mDuration + "t " + matched.mEUt + " EU/t";
            statusColor = c(EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED);
            if (matched.mOutputs != null) for (int i = 0; i < maxOut; i++) outputs[i].setStackInSlot(
                0,
                i < matched.mOutputs.length && matched.mOutputs[i] != null ? matched.mOutputs[i].copy() : null);
        } else {
            matched = null;
            for (int i = 0; i < maxOut; i++) outputs[i].setStackInSlot(0, null);
            if (matches.isEmpty()) {
                statusText = hasAnyInput(in) ? "No recipe found" : "Put items to find a recipe";
                statusColor = hasAnyInput(in) ? c(EnumColors.MAP_COLOR_TEXT_DANGER)
                    : c(EnumColors.MAP_COLOR_TEXT_MUTED);
            } else {
                statusText = matches.size() + " matches — be more specific";
                statusColor = c(EnumColors.MAP_COLOR_TEXT_WARNING);
            }
        }
    }

    private void cancel() {
        Minecraft.getMinecraft()
            .displayGuiScreen(pendingReturnScreen);
    }

    private void confirm() {
        if (matched == null) return;
        ModuleInstance m = pendingModule;
        if (m == null || !(m.component() instanceof IRecipeModule rm)) return;
        long hash = com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot
            .computeContentHash(matched.mInputs, matched.mOutputs, matched.mDuration, matched.mEUt);
        var snap = new com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot(
            (byte) (mapId != null ? mapId.ordinal() : 0),
            allRecipes.indexOf(matched),
            hash,
            matched.mInputs,
            matched.mOutputs,
            matched.mDuration,
            matched.mEUt);
        RecipeConfig cfg = rm.getRecipeConfig();
        if (cfg == null) {
            cfg = RecipeConfig.empty();
            rm.setRecipeConfig(cfg);
        }
        cfg.slots()
            .add(new RecipeSlot(snap, true, 0, Integer.MAX_VALUE, (byte) 1, (byte) 1));
        cancel();
    }

    private static boolean hasAnyInput(ItemStack[] in) {
        for (ItemStack s : in) if (s != null) return true;
        return false;
    }

    private static boolean matchesInputs(GTRecipe r, ItemStack[] in) {
        if (r.mInputs == null) return false;
        boolean any = false;
        for (ItemStack s : in) if (s != null) {
            any = true;
            break;
        }
        if (!any) return false;
        for (ItemStack pi : in) {
            if (pi == null) continue;
            boolean found = false;
            for (ItemStack ri : r.mInputs)
                if (ri != null && pi.getItem() == ri.getItem() && pi.getItemDamage() == ri.getItemDamage()) {
                    found = true;
                    break;
                }
            if (!found) return false;
        }
        return true;
    }

    private static final class StatusWidget extends ParentWidget<StatusWidget> {

        private final RecipeInputScreen s;

        StatusWidget(RecipeInputScreen s) {
            this.s = s;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public void drawBackground(ModularGuiContext ctx, WidgetThemeEntry<?> t) {
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(s.statusText, 0, 0, s.statusColor);
        }
    }

    private static TextWidget<?> txt(String t, int c, int x, int y) {
        return new TextWidget<>(IKey.str(t)).color(c)
            .pos(x, y);
    }

    private static ButtonWidget<?> btn(String label, Runnable action) {
        int bc = c(EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT), hc = c(EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED),
            brc = c(EnumColors.MAP_COLOR_BTN_BORDER_ENABLED), tc = c(EnumColors.MAP_COLOR_TEXT_BODY);
        return new ButtonWidget<>().background(drawable((ctx, x, y, w, h) -> BorderedRect.draw(x, y, w, h, bc, brc)))
            .hoverBackground(drawable((ctx, x, y, w, h) -> BorderedRect.draw(x, y, w, h, hc, brc)))
            .overlay(drawable((ctx, x, y, w, h) -> {
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                fr.drawStringWithShadow(
                    label,
                    x + (w - fr.getStringWidth(label)) / 2,
                    y + (h - fr.FONT_HEIGHT) / 2,
                    tc);
            }))
            .onMouseTapped(b -> {
                if (b == 0) action.run();
                return true;
            });
    }

    private static IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, t) -> cmd.draw(ctx, x, y, w, h);
    }

    private static int c(EnumColors color) {
        return color.getColor();
    }
}
