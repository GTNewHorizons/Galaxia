package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.GTRecipeMapId;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeIntentMatcher;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTRecipe;

public final class RecipeInputScreen implements IGuiHolder<GuiData> {

    static final SimpleGuiFactory FACTORY = new SimpleGuiFactory("galaxia_recipe_input", RecipeInputScreen::new);
    private static final int TITLE_H = 12;
    private static final int FOOTER_H = 34;
    private static final int SLOT = 18;

    static volatile @Nullable CelestialAsset.ID pendingAssetId;
    static volatile int pendingModuleIndex = -1;
    static volatile @Nullable ModuleInstance pendingModule;
    static volatile @Nullable GuiScreen pendingReturnScreen;

    private final ItemStackHandler[] itemInputs;
    private final ItemStackHandler[] itemOutputs;
    private final ItemStackHandler[] ghostItemInputs;
    private final ItemStackHandler[] ghostItemOutputs;
    private final FluidTank[] fluidInputs;
    private final FluidTank[] fluidOutputs;
    private final FluidTank[] ghostFluidInputs;
    private final FluidTank[] ghostFluidOutputs;
    private final GTRecipeMapLayout layout;
    private GTRecipe[] allRecipes = new GTRecipe[0];
    private GTRecipeMapId mapId = GTRecipeMapId.INVALID;
    private RecipeIntentMatcher.Result match = new RecipeIntentMatcher.Result(
        RecipeIntentMatcher.Status.NO_INPUT,
        0,
        -1,
        null,
        null);
    private String statusText = "Put items to find a recipe";
    private int statusColor = c(EnumColors.MAP_COLOR_TEXT_MUTED);

    public static void open(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module) {
        pendingAssetId = assetId;
        pendingModuleIndex = moduleIndex;
        pendingModule = module;
        pendingReturnScreen = Minecraft.getMinecraft().currentScreen;
        FACTORY.openClient();
    }

    public RecipeInputScreen() {
        gregtech.api.recipe.RecipeMap<?> map = pendingModule != null && pendingModule.component() instanceof IRecipeModule rm
            ? rm.getRecipeMap()
            : null;
        this.layout = GTRecipeMapLayout.fromRecipeMap(map);
        if (map != null) {
            @SuppressWarnings("unchecked")
            List<GTRecipe> recipes = new ArrayList<>((Collection<GTRecipe>) (Collection<?>) map.getAllRecipes());
            recipes.removeIf(r -> r == null || r.mHidden || r.mFakeRecipe);
            this.allRecipes = recipes.toArray(new GTRecipe[0]);
            GTRecipeMapId id = GTRecipeMapId.fromRecipeMapName(map.unlocalizedName);
            this.mapId = id != null ? id : GTRecipeMapId.INVALID;
        }

        this.itemInputs = itemHandlers(layout.itemInputs().size());
        this.itemOutputs = itemHandlers(layout.itemOutputs().size());
        this.ghostItemInputs = itemHandlers(layout.itemInputs().size());
        this.ghostItemOutputs = itemHandlers(layout.itemOutputs().size());
        this.fluidInputs = fluidTanks(layout.fluidInputs().size());
        this.fluidOutputs = fluidTanks(layout.fluidOutputs().size());
        this.ghostFluidInputs = fluidTanks(layout.fluidInputs().size());
        this.ghostFluidOutputs = fluidTanks(layout.fluidOutputs().size());
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
        int width = Math.max(GTRecipeMapLayout.DEFAULT_WIDTH, layout.width());
        int height = TITLE_H + layout.height() + FOOTER_H;
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_recipe_input", width, height);
        ModuleInstance module = pendingModule;
        if (module == null || !(module.component() instanceof IRecipeModule)) {
            panel.child(new FrameWidget("No recipe module", this).pos(0, 0)
                .size(width, height));
            return panel;
        }

        panel.child(
            new FrameWidget(
                module.kind()
                    .getDisplayName(),
                this).pos(0, 0)
                    .size(width, height));

        addItemSlots(panel, layout.itemInputs(), itemInputs, ghostItemInputs);
        addItemSlots(panel, layout.itemOutputs(), itemOutputs, ghostItemOutputs);
        addFluidSlots(panel, layout.fluidInputs(), fluidInputs, ghostFluidInputs);
        addFluidSlots(panel, layout.fluidOutputs(), fluidOutputs, ghostFluidOutputs);

        int btnY = TITLE_H + layout.height() + 8;
        panel.child(
            btn("Cancel", this::cancel).pos(6, btnY)
                .size(70, 20));
        panel.child(
            btn("Confirm", this::confirm).pos(width - 86, btnY)
                .size(80, 20));
        return panel;
    }

    private void addItemSlots(ModularPanel panel, List<GTRecipeMapLayout.Slot> slots, ItemStackHandler[] hard,
        ItemStackHandler[] ghost) {
        for (GTRecipeMapLayout.Slot slot : slots) {
            int index = slot.index();
            panel.child(
                new PhantomItemSlot().slot(new ModularSlot(hard[index], 0).changeListener((stack, amount, client,
                    init) -> {
                        if (init) return;
                        if (stack != null && stack.stackSize > 1) {
                            stack.stackSize = 1;
                            hard[index].setStackInSlot(0, stack);
                        }
                        onInputChanged();
                    }))
                    .pos(slot.x(), TITLE_H + slot.y())
                    .size(SLOT, SLOT));
            PhantomItemSlot ghostSlot = new PhantomItemSlot();
            ghostSlot.slot(new ModularSlot(ghost[index], 0));
            ghostSlot.pos(slot.x(), TITLE_H + slot.y())
                .size(SLOT, SLOT);
            ghostSlot.setEnabled(false);
            panel.child(ghostSlot);
        }
    }

    private void addFluidSlots(ModularPanel panel, List<GTRecipeMapLayout.Slot> slots, FluidTank[] hard,
        FluidTank[] ghost) {
        for (GTRecipeMapLayout.Slot slot : slots) {
            int index = slot.index();
            panel.child(
                new FluidSlot().syncHandler(new IntentFluidSlotSyncHandler(hard[index]))
                    .pos(slot.x(), TITLE_H + slot.y())
                    .size(SLOT, SLOT));
            FluidSlot ghostSlot = new FluidSlot()
                .syncHandler(new FluidSlotSyncHandler(ghost[index]).canFillSlot(false)
                    .canDrainSlot(false))
                .pos(slot.x(), TITLE_H + slot.y())
                .size(SLOT, SLOT);
            ghostSlot.setEnabled(false);
            panel.child(ghostSlot);
        }
    }

    private void onInputChanged() {
        match = RecipeIntentMatcher.match(
            mapId,
            allRecipes,
            itemStacks(itemInputs),
            itemStacks(itemOutputs),
            fluidStacks(fluidInputs),
            fluidStacks(fluidOutputs));
        clearGhosts();
        switch (match.status()) {
            case NO_INPUT -> {
                statusText = "Put items to find a recipe";
                statusColor = c(EnumColors.MAP_COLOR_TEXT_MUTED);
            }
            case NO_MATCH -> {
                statusText = "No recipe found";
                statusColor = c(EnumColors.MAP_COLOR_TEXT_DANGER);
            }
            case MULTIPLE_MATCHES -> {
                statusText = match.matchCount() + " matches - need more items";
                statusColor = c(EnumColors.MAP_COLOR_TEXT_WARNING);
            }
            case SINGLE_MATCH -> {
                RecipeSnapshot snapshot = match.snapshot();
                statusText = snapshot.duration() + "t " + snapshot.eut() + " EU/t";
                statusColor = c(EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED);
                applyItemGhosts(snapshot.inputs(), itemInputs, ghostItemInputs);
                applyItemGhosts(snapshot.outputs(), itemOutputs, ghostItemOutputs);
                applyFluidGhosts(snapshot.fluidInputs(), fluidInputs, ghostFluidInputs);
                applyFluidGhosts(snapshot.fluidOutputs(), fluidOutputs, ghostFluidOutputs);
            }
        }
    }

    private void cancel() {
        Minecraft.getMinecraft()
            .displayGuiScreen(pendingReturnScreen);
    }

    private void confirm() {
        RecipeSnapshot snapshot = match.snapshot();
        CelestialAsset.ID assetId = pendingAssetId;
        ModuleInstance module = pendingModule;
        if (snapshot == null || assetId == null || module == null || !(module.component() instanceof IRecipeModule rm))
            return;
        int slotIndex = 0;
        RecipeConfig cfg = rm.getRecipeConfig();
        if (cfg != null) slotIndex = cfg.slots()
            .size();
        if (slotIndex < 0 || slotIndex >= com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlotList.MAX_RECIPE_SLOTS)
            return;
        RecipeSlot slot = new RecipeSlot(snapshot, true, 0, Integer.MAX_VALUE, (byte) 1, (byte) 1);
        CelestialClient.updateModuleRecipeSlot(
            assetId,
            pendingModuleIndex,
            AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT,
            (byte) slotIndex,
            slot);
        cancel();
    }

    private void clearGhosts() {
        for (ItemStackHandler handler : ghostItemInputs) handler.setStackInSlot(0, null);
        for (ItemStackHandler handler : ghostItemOutputs) handler.setStackInSlot(0, null);
        for (FluidTank tank : ghostFluidInputs) tank.setFluid(null);
        for (FluidTank tank : ghostFluidOutputs) tank.setFluid(null);
    }

    private static void applyItemGhosts(ItemStack[] recipeStacks, ItemStackHandler[] hard, ItemStackHandler[] ghost) {
        boolean[] consumed = consumeHardItems(recipeStacks, hard);
        for (int i = 0; i < ghost.length; i++) {
            ItemStack recipe = recipeStacks != null && i < recipeStacks.length ? recipeStacks[i] : null;
            boolean alreadyProvided = i < consumed.length && consumed[i];
            if (recipe != null && hard[i].getStackInSlot(0) == null && !alreadyProvided) {
                ItemStack copy = recipe.copy();
                copy.stackSize = 1;
                ghost[i].setStackInSlot(0, copy);
            }
        }
    }

    private static boolean[] consumeHardItems(ItemStack[] recipeStacks, ItemStackHandler[] hard) {
        boolean[] consumed = recipeStacks != null ? new boolean[recipeStacks.length] : new boolean[0];
        for (ItemStackHandler handler : hard) {
            ItemStack hardStack = handler.getStackInSlot(0);
            if (hardStack == null || recipeStacks == null) continue;
            for (int i = 0; i < recipeStacks.length; i++) {
                if (!consumed[i] && itemMatches(hardStack, recipeStacks[i])) {
                    consumed[i] = true;
                    break;
                }
            }
        }
        return consumed;
    }

    private static void applyFluidGhosts(FluidStack[] recipeStacks, FluidTank[] hard, FluidTank[] ghost) {
        boolean[] consumed = consumeHardFluids(recipeStacks, hard);
        for (int i = 0; i < ghost.length; i++) {
            FluidStack recipe = recipeStacks != null && i < recipeStacks.length ? recipeStacks[i] : null;
            boolean alreadyProvided = i < consumed.length && consumed[i];
            if (recipe != null && hard[i].getFluid() == null && !alreadyProvided) {
                ghost[i].setFluid(copyFluid(recipe));
            }
        }
    }

    private static boolean[] consumeHardFluids(FluidStack[] recipeStacks, FluidTank[] hard) {
        boolean[] consumed = recipeStacks != null ? new boolean[recipeStacks.length] : new boolean[0];
        for (FluidTank tank : hard) {
            FluidStack hardStack = tank.getFluid();
            if (hardStack == null || recipeStacks == null) continue;
            for (int i = 0; i < recipeStacks.length; i++) {
                if (!consumed[i] && fluidMatches(hardStack, recipeStacks[i])) {
                    consumed[i] = true;
                    break;
                }
            }
        }
        return consumed;
    }

    private static ItemStack[] itemStacks(ItemStackHandler[] handlers) {
        ItemStack[] stacks = new ItemStack[handlers.length];
        for (int i = 0; i < handlers.length; i++) stacks[i] = handlers[i].getStackInSlot(0);
        return stacks;
    }

    private static FluidStack[] fluidStacks(FluidTank[] tanks) {
        FluidStack[] stacks = new FluidStack[tanks.length];
        for (int i = 0; i < tanks.length; i++) stacks[i] = tanks[i].getFluid();
        return stacks;
    }

    private static ItemStackHandler[] itemHandlers(int count) {
        ItemStackHandler[] handlers = new ItemStackHandler[count];
        for (int i = 0; i < count; i++) handlers[i] = new ItemStackHandler(1);
        return handlers;
    }

    private static FluidTank[] fluidTanks(int count) {
        FluidTank[] tanks = new FluidTank[count];
        for (int i = 0; i < count; i++) tanks[i] = new FluidTank(Integer.MAX_VALUE);
        return tanks;
    }

    private static boolean itemMatches(ItemStack hard, ItemStack recipeStack) {
        return hard != null && recipeStack != null && hard.getItem() == recipeStack.getItem()
            && hard.getItemDamage() == recipeStack.getItemDamage();
    }

    private static boolean fluidMatches(FluidStack hard, FluidStack recipeStack) {
        String hardName = fluidName(hard);
        return hardName != null && hardName.equals(fluidName(recipeStack));
    }

    private static FluidStack copyFluid(FluidStack stack) {
        if (stack == null) return null;
        try {
            return stack.copy();
        } catch (RuntimeException e) {
            Fluid fluid = fluidType(stack);
            return fluid != null ? new FluidStack(fluid, stack.amount) : null;
        }
    }

    private static String fluidName(FluidStack stack) {
        Fluid fluid = fluidType(stack);
        return fluid != null ? fluid.getName() : null;
    }

    private static Fluid fluidType(FluidStack stack) {
        if (stack == null) return null;
        try {
            return stack.getFluid();
        } catch (RuntimeException ignored) {
            try {
                Field field = FluidStack.class.getDeclaredField("fluid");
                field.setAccessible(true);
                return (Fluid) field.get(stack);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }

    private static ButtonWidget<?> btn(String label, Runnable action) {
        int bc = c(EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT), hc = c(EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED),
            brc = c(EnumColors.MAP_COLOR_BTN_BORDER_ENABLED), tc = c(EnumColors.MAP_COLOR_TEXT_BODY);
        return new ButtonWidget<>().background(drawable((ctx, x, y, w, h) -> BorderedRect.draw(x, y, w, h, bc, brc)))
            .hoverBackground(drawable((ctx, x, y, w, h) -> BorderedRect.draw(x, y, w, h, hc, brc)))
            .overlay(drawable((ctx, x, y, w, h) -> {
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                fr.drawStringWithShadow(label, x + (w - fr.getStringWidth(label)) / 2, y + (h - fr.FONT_HEIGHT) / 2, tc);
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

    private final class IntentFluidSlotSyncHandler extends FluidSlotSyncHandler {

        IntentFluidSlotSyncHandler(FluidTank fluidTank) {
            super(fluidTank);
            phantom(true);
            controlsAmount(false);
        }

        @Override
        public void notifyUpdate() {
            super.notifyUpdate();
            onInputChanged();
        }

        @Override
        public void setValue(@Nullable FluidStack value, boolean setSource, boolean sync) {
            super.setValue(value, setSource, sync);
            onInputChanged();
        }
    }

    private static final class FrameWidget extends ParentWidget<FrameWidget> {

        private final String title;
        private final RecipeInputScreen screen;

        FrameWidget(String title, RecipeInputScreen screen) {
            this.title = title;
            this.screen = screen;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public void drawBackground(ModularGuiContext ctx, WidgetThemeEntry<?> theme) {
            int w = getArea().width;
            int h = getArea().height;
            BorderedRect.draw(0, 0, w, h, 0xFFB7C0D3, 0xFF2F3646);
            BorderedRect.draw(0, 0, w, TITLE_H, 0xFF9FAAC2, 0xFF2F3646);
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            fr.drawString(title, 6, 3, 0xFF303642);
            if (screen.layout.progress().enabled()) {
                GTRecipeMapLayout.Progress progress = screen.layout.progress();
                int x = progress.x();
                int y = TITLE_H + progress.y();
                BorderedRect.draw(x, y, progress.width(), progress.height(), 0xFF788398, 0xFFECF0FF);
                fr.drawString(">", x + progress.width() / 2 - 2, y + 5, 0xFFE6EAF6);
            }
            fr.drawStringWithShadow(screen.statusText, 6, TITLE_H + screen.layout.height() - 10, screen.statusColor);
        }
    }
}
