package com.gtnewhorizons.galaxia.compat.recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.integration.nei.INEIRecipeTransfer;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.fluid.FluidInteractions;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

public final class GTRecipeInputScreen implements IGuiHolder<GuiData> {

    static final SimpleGuiFactory FACTORY = new SimpleGuiFactory("galaxia_recipe_input", GTRecipeInputScreen::new);
    private static final int TITLE_TAB_PADDING = 3;
    private static final int TITLE_TEXT_PADDING = 2;
    private static final int BODY_Y = 14;
    private static final int RECIPE_Y = BODY_Y;
    private static final int FOOTER_H = 52;
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
    private final String title;
    private final @Nullable gregtech.api.recipe.RecipeMap<?> recipeMap;
    private final String[] neiTransferIds;
    private GTRecipe[] allRecipes = new GTRecipe[0];
    private GTRecipeMapId mapId = GTRecipeMapId.INVALID;
    private RecipeIntentMatcher.Result match = new RecipeIntentMatcher.Result(
        RecipeIntentMatcher.Status.NO_INPUT,
        0,
        -1,
        null,
        null);
    private String statusText = "Put items to find a recipe";
    private @Nullable String statusDetailText;
    private int statusColor = c(EnumColors.MAP_COLOR_TEXT_MUTED);

    public static void open(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module) {
        pendingAssetId = assetId;
        pendingModuleIndex = moduleIndex;
        pendingModule = module;
        pendingReturnScreen = Minecraft.getMinecraft().currentScreen;
        resetFactoryHolder();
        FACTORY.openClient();
    }

    private static void resetFactoryHolder() {
        try {
            Field field = SimpleGuiFactory.class.getDeclaredField("guiHolder");
            field.setAccessible(true);
            field.set(FACTORY, null);
        } catch (ReflectiveOperationException ignored) {}
    }

    public GTRecipeInputScreen() {
        String mapName = pendingModule != null && pendingModule.component() instanceof IRecipeModule rm
            ? rm.getRecipeMapName()
            : null;
        GTRecipeMapId id = GTRecipeMapId.fromRecipeMapName(mapName);
        this.mapId = id != null ? id : GTRecipeMapId.INVALID;
        gregtech.api.recipe.RecipeMap<?> map = GTRecipeMapId.findRecipeMap(this.mapId);
        this.recipeMap = map;
        this.layout = GTRecipeMapLayout.fromRecipeMap(map);
        this.title = titleFor(map, pendingModule);
        this.neiTransferIds = neiTransferIds(map, pendingModule);
        if (map != null) {
            List<GTRecipe> recipes = new ArrayList<>(map.getAllRecipes());
            recipes.removeIf(r -> r == null || r.mHidden || r.mFakeRecipe);
            this.allRecipes = recipes.toArray(new GTRecipe[0]);
        }

        this.itemInputs = itemHandlers(
            layout.itemInputs()
                .size());
        this.itemOutputs = itemHandlers(
            layout.itemOutputs()
                .size());
        this.ghostItemInputs = itemHandlers(
            layout.itemInputs()
                .size());
        this.ghostItemOutputs = itemHandlers(
            layout.itemOutputs()
                .size());
        this.fluidInputs = fluidTanks(
            layout.fluidInputs()
                .size());
        this.fluidOutputs = fluidTanks(
            layout.fluidOutputs()
                .size());
        this.ghostFluidInputs = fluidTanks(
            layout.fluidInputs()
                .size());
        this.ghostFluidOutputs = fluidTanks(
            layout.fluidOutputs()
                .size());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(GuiData d, ModularPanel p) {
        return new ModularScreen(Galaxia.MODID, p);
    }

    @Override
    public ModularPanel buildUI(GuiData gd, PanelSyncManager sm, UISettings s) {
        sm.syncValue(StarmapActionSyncHandler.KEY, new StarmapActionSyncHandler());
        s.getRecipeViewerSettings()
            .enable();
        s.customContainer(() -> new RecipeInputNeiContainer(this));
        int width = Math.max(GTRecipeMapLayout.DEFAULT_WIDTH, layout.width());
        int height = BODY_Y + layout.height() + FOOTER_H;
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_recipe_input", width, height)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true);
        ModuleInstance module = pendingModule;
        if (module == null || !(module.component() instanceof IRecipeModule)) {
            addFrame(panel, "No recipe module", width, height);
            return panel;
        }

        addFrame(panel, title, width, height);

        addProgress(panel);
        addItemSlots(panel, layout.itemInputs(), itemInputs, ghostItemInputs);
        addItemSlots(panel, layout.itemOutputs(), itemOutputs, ghostItemOutputs);
        addFluidSlots(panel, layout.fluidInputs(), fluidInputs, ghostFluidInputs);
        addFluidSlots(panel, layout.fluidOutputs(), fluidOutputs, ghostFluidOutputs);

        int btnY = height - 26;
        panel.child(
            btn("Cancel", this::cancel).pos(6, btnY)
                .size(58, 20));
        panel.child(
            btn("NEI", this::openNeiRecipeMap).pos(68, btnY)
                .size(40, 20));
        panel.child(
            btn("Confirm", this::confirm).pos(width - 64, btnY)
                .size(58, 20));
        return panel;
    }

    private void addFrame(ModularPanel panel, String title, int width, int height) {
        panel.child(
            new BodyBackgroundWidget(this).pos(0, BODY_Y)
                .size(width, height - BODY_Y));
        panel.child(
            new TitleTabWidget(title).pos(0, 0)
                .size(titleTabWidth(title, width), BODY_Y));
        panel.child(
            new StatusTextWidget(this).pos(0, height - 52)
                .size(width, 24));
    }

    private void addItemSlots(ModularPanel panel, List<GTRecipeMapLayout.Slot> slots, ItemStackHandler[] hard,
        ItemStackHandler[] ghost) {
        for (GTRecipeMapLayout.Slot slot : slots) {
            int index = slot.index();
            panel.child(
                new PhantomItemSlot()
                    .slot(new ModularSlot(hard[index], 0).changeListener((stack, amount, client, init) -> {
                        if (init) return;
                        if (isFluidContainer(stack)) {
                            hard[index].setStackInSlot(0, null);
                            onInputChanged();
                            return;
                        }
                        if (stack != null && stack.stackSize > 1) {
                            stack.stackSize = 1;
                            hard[index].setStackInSlot(0, stack);
                        }
                        onInputChanged();
                    }))
                    .background(GTGuiTextures.SLOT_ITEM_STANDARD, slot.overlay())
                    .pos(slot.x(), RECIPE_Y + slot.y())
                    .size(SLOT, SLOT));
            GhostItemWidget ghostSlot = new GhostItemWidget(ghost[index]);
            ghostSlot.pos(slot.x(), RECIPE_Y + slot.y())
                .size(SLOT, SLOT);
            panel.child(ghostSlot);
        }
    }

    private void addFluidSlots(ModularPanel panel, List<GTRecipeMapLayout.Slot> slots, FluidTank[] hard,
        FluidTank[] ghost) {
        for (GTRecipeMapLayout.Slot slot : slots) {
            int index = slot.index();
            panel.child(
                new FluidSlot().syncHandler(new IntentFluidSlotSyncHandler(hard[index]))
                    .background(GTGuiTextures.SLOT_FLUID_STANDARD, slot.overlay())
                    .pos(slot.x(), RECIPE_Y + slot.y())
                    .size(SLOT, SLOT));
            GhostFluidWidget ghostSlot = new GhostFluidWidget(ghost[index]);
            ghostSlot.pos(slot.x(), RECIPE_Y + slot.y())
                .size(SLOT, SLOT);
            panel.child(ghostSlot);
        }
    }

    private void addProgress(ModularPanel panel) {
        GTRecipeMapLayout.Progress progress = layout.progress();
        if (!progress.enabled() || progress.texture() == null) return;
        panel.child(
            new ProgressWidget().texture(progress.texture(), progress.imageSize())
                .direction(progress.direction())
                .value(new DoubleValue.Dynamic(() -> (System.currentTimeMillis() % 1000L) / 1000.0, null))
                .pos(progress.x(), RECIPE_Y + progress.y())
                .size(progress.width(), progress.height()));
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
                statusDetailText = null;
                statusColor = c(EnumColors.MAP_COLOR_TEXT_MUTED);
            }
            case NO_MATCH -> {
                statusText = "No recipe found";
                statusDetailText = null;
                statusColor = c(EnumColors.MAP_COLOR_TEXT_DANGER);
            }
            case MULTIPLE_MATCHES -> {
                statusText = match.matchCount() + " matches - need more items";
                statusDetailText = null;
                statusColor = c(EnumColors.MAP_COLOR_TEXT_WARNING);
            }
            case SINGLE_MATCH -> {
                RecipeSnapshot snapshot = match.snapshot();
                statusText = neiUsage(snapshot.eut());
                statusDetailText = neiDuration(snapshot.duration());
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
        if (cfg != null) slotIndex = cfg.savedRecipes()
            .size();
        if (slotIndex < 0
            || slotIndex >= com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList.MAX_SAVED_RECIPES) return;
        SavedRecipe slot = new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1);
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
        for (FluidTank tank : ghostFluidInputs) tank.drain(Integer.MAX_VALUE, true);
        for (FluidTank tank : ghostFluidOutputs) tank.drain(Integer.MAX_VALUE, true);
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

    private void applyFluidGhosts(FluidStack[] recipeStacks, FluidTank[] hard, FluidTank[] ghost) {
        boolean[] consumed = consumeHardFluids(recipeStacks, hard);
        for (int i = 0; i < ghost.length; i++) {
            FluidStack recipe = recipeStacks != null && i < recipeStacks.length ? recipeStacks[i] : null;
            boolean alreadyProvided = i < consumed.length && consumed[i];
            if (recipe != null && hard[i].getFluid() == null && !alreadyProvided) {
                ghost[i].fill(copyFluid(recipe), true);
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

    private static boolean isFluidContainer(@Nullable ItemStack stack) {
        if (stack == null) return false;
        ItemStack one = stack.copy();
        one.stackSize = 1;
        FluidStack fluid = FluidInteractions.getFluidForItem(one);
        return fluid != null && fluid.amount > 0;
    }

    private static boolean itemMatches(ItemStack hard, ItemStack recipeStack) {
        return hard != null && recipeStack != null
            && hard.getItem() == recipeStack.getItem()
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
        return new ButtonWidget<>().overlay(drawable((ctx, x, y, w, h) -> {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            fr.drawString(label, x + (w - fr.getStringWidth(label)) / 2, y + (h - fr.FONT_HEIGHT) / 2 + 1, 0xFF1E2530);
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

    private static String neiUsage(int eut) {
        long voltage = Math.abs((long) eut);
        return StatCollector.translateToLocalFormatted(
            "GT5U.nei.display.usage",
            Long.toString(voltage),
            GTUtility.getTierNameWithParentheses(voltage));
    }

    private static String neiDuration(int duration) {
        return StatCollector.translateToLocalFormatted("GT5U.nei.display.duration.ticks", Integer.toString(duration));
    }

    private static int titleTabWidth(String title, int maxWidth) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        return Math.min(maxWidth, fr.getStringWidth(title) + TITLE_TAB_PADDING * 2 + TITLE_TEXT_PADDING * 2);
    }

    private static String[] neiTransferIds(@Nullable gregtech.api.recipe.RecipeMap<?> map,
        @Nullable ModuleInstance module) {
        if (map == null) return new String[0];
        String name = map.unlocalizedName;
        if (name == null || name.isEmpty()) return new String[0];

        if (module != null && module.component() instanceof IRecipeModule recipeModule) {
            List<String> extra = recipeModule.getAdditionalNeiTransferIdents();
            if (!extra.isEmpty()) {
                String[] all = new String[1 + extra.size()];
                all[0] = name;
                for (int i = 0; i < extra.size(); i++) all[i + 1] = extra.get(i);
                return all;
            }
        }
        return new String[] { name };
    }

    private static String titleFor(@Nullable gregtech.api.recipe.RecipeMap<?> map, @Nullable ModuleInstance module) {
        if (map != null) {
            String localized = StatCollector.translateToLocal(map.unlocalizedName);
            if (!localized.equals(map.unlocalizedName)) return localized;
        }
        return module != null ? module.kind()
            .getDisplayName() : "Recipe";
    }

    private void openNeiRecipeMap() {
        if (neiTransferIds.length > 0) {
            GuiCraftingRecipe.openRecipeGui(neiTransferIds[0]);
        }
    }

    private void applyNeiRecipe(IRecipeHandler recipe, int recipeIndex) {
        for (ItemStackHandler handler : itemInputs) handler.setStackInSlot(0, null);
        for (ItemStackHandler handler : itemOutputs) handler.setStackInSlot(0, null);
        for (FluidTank tank : fluidInputs) tank.drain(Integer.MAX_VALUE, true);
        for (FluidTank tank : fluidOutputs) tank.drain(Integer.MAX_VALUE, true);

        List<PositionedStack> inputs = recipe.getIngredientStacks(recipeIndex);
        List<PositionedStack> outputs = recipe.getOtherStacks(recipeIndex);
        Galaxia.LOG.info(
            "[RecipeInput] NEI transfer: recipeIndex={} inputs={} outputs={}",
            recipeIndex,
            inputs != null ? inputs.size() : 0,
            outputs != null ? outputs.size() : 0);

        boolean inputFilled = fillByPosition(inputs, layout.itemInputs(), itemInputs);
        boolean outputFilled = fillByPosition(outputs, layout.itemOutputs(), itemOutputs);

        if (!inputFilled && !outputFilled) {
            Galaxia.LOG.warn("[RecipeInput] NEI transfer: no item slots matched by position, trying sequential");
            fillSequential(inputs, itemInputs);
            fillSequential(outputs, itemOutputs);
        }

        Galaxia.LOG.info(
            "[RecipeInput] fluid fill: stacksIn={} stacksOut={} slotCount={} tankCount={}",
            inputs != null ? inputs.size() : -1,
            outputs != null ? outputs.size() : -1,
            layout.fluidInputs()
                .size(),
            fluidInputs.length);
        fillFluidsByPosition(inputs, layout.fluidInputs(), fluidInputs);
        fillFluidsByPosition(outputs, layout.fluidOutputs(), fluidOutputs);
        fillFluidsSequential(inputs, fluidInputs);
        fillFluidsSequential(outputs, fluidOutputs);

        onInputChanged();
    }

    private static void fillFluidsByPosition(@Nullable List<PositionedStack> stacks, List<GTRecipeMapLayout.Slot> slots,
        FluidTank[] tanks) {
        if (stacks == null || stacks.isEmpty() || slots.isEmpty() || tanks.length == 0) return;
        boolean[] used = new boolean[Math.min(slots.size(), tanks.length)];
        for (PositionedStack positioned : stacks) {
            ItemStack stack = firstItem(positioned);
            if (stack == null) {
                Galaxia.LOG.info("[RecipeInput] fillFluids: firstItem returned null (positioned is null)");
                continue;
            }
            FluidStack fluid = FluidInteractions.getFluidForItem(stack);
            if (fluid == null) {
                Galaxia.LOG.info(
                    "[RecipeInput] fillFluids: getFluidForItem returned null for item={}",
                    stack.getDisplayName());
                continue;
            }
            int slotIndex = findSlotFor(positioned, slots, used);
            if (slotIndex < 0 || slotIndex >= tanks.length) {
                Galaxia.LOG.info(
                    "[RecipeInput] fillFluids: findSlotFor returned {} for fluid {} at ({},{}) (slots={}, tanks={})",
                    slotIndex,
                    fluid.getFluid()
                        .getName(),
                    positioned.relx,
                    positioned.rely,
                    slots.size(),
                    tanks.length);
                continue;
            }
            used[slotIndex] = true;
            int filled = tanks[slotIndex].fill(fluid.copy(), true);
            Galaxia.LOG.info(
                "[RecipeInput] fillFluids: filled {} mB of {} into tank {}",
                filled,
                fluid.getFluid()
                    .getName(),
                slotIndex);
        }
    }

    /** Fills fluid tanks sequentially, skipping already-filled tanks. */
    private static void fillFluidsSequential(@Nullable List<PositionedStack> stacks, FluidTank[] tanks) {
        if (stacks == null || tanks.length == 0) return;
        int t = 0;
        for (PositionedStack positioned : stacks) {
            ItemStack stack = firstItem(positioned);
            if (stack == null) continue;
            FluidStack fluid = FluidInteractions.getFluidForItem(stack);
            if (fluid == null) continue;
            while (t < tanks.length && tanks[t].getFluidAmount() > 0) t++;
            if (t >= tanks.length) break;
            tanks[t].fill(fluid.copy(), true);
            t++;
        }
    }

    /** Returns true if at least one slot was filled by position matching. */
    private static boolean fillByPosition(@Nullable List<PositionedStack> stacks, List<GTRecipeMapLayout.Slot> slots,
        ItemStackHandler[] handlers) {
        if (stacks == null || stacks.isEmpty() || slots.isEmpty() || handlers.length == 0) return false;
        boolean[] used = new boolean[Math.min(slots.size(), handlers.length)];
        boolean anyFilled = false;
        for (PositionedStack positioned : stacks) {
            ItemStack stack = firstItem(positioned);
            if (stack == null || isFluidContainer(stack)) continue;
            int slotIndex = findSlotFor(positioned, slots, used);
            if (slotIndex < 0 || slotIndex >= handlers.length) continue;
            used[slotIndex] = true;
            stack.stackSize = 1;
            handlers[slotIndex].setStackInSlot(0, stack);
            anyFilled = true;
        }
        return anyFilled;
    }

    /** Fills handlers sequentially, skipping already-filled slots. */
    private static void fillSequential(@Nullable List<PositionedStack> stacks, ItemStackHandler[] handlers) {
        if (stacks == null || handlers.length == 0) return;
        int h = 0;
        for (PositionedStack positioned : stacks) {
            ItemStack stack = firstItem(positioned);
            if (stack == null || isFluidContainer(stack)) continue;
            while (h < handlers.length && handlers[h].getStackInSlot(0) != null) h++;
            if (h >= handlers.length) break;
            stack.stackSize = 1;
            handlers[h].setStackInSlot(0, stack);
            h++;
        }
    }

    private static int findSlotFor(PositionedStack positioned, List<GTRecipeMapLayout.Slot> slots, boolean[] used) {
        int x = positioned.relx - 1;
        int y = positioned.rely - 1;
        for (int i = 0; i < slots.size() && i < used.length; i++) {
            if (used[i]) continue;
            GTRecipeMapLayout.Slot slot = slots.get(i);
            if (Math.abs(slot.x() - x) <= 15 && Math.abs(slot.y() - y) <= 15) return i;
            if (Math.abs(slot.x() - x) <= 15 && Math.abs(RECIPE_Y + slot.y() - y) <= 15) return i;
        }
        Galaxia.LOG.info(
            "[RecipeInput] findSlotFor: no match for ({},{}) — slots: {}",
            x,
            y,
            slots.stream()
                .map(s -> "(" + s.x() + "," + s.y() + ")")
                .reduce((a, b) -> a + " " + b)
                .orElse("none"));
        return -1;
    }

    private static @Nullable ItemStack firstItem(PositionedStack positioned) {
        if (positioned == null) {
            Galaxia.LOG.info("[RecipeInput] firstItem: positioned is null");
            return null;
        }
        if (positioned.items == null) {
            Galaxia.LOG.info(
                "[RecipeInput] firstItem: positioned.items is null for pos ({},{})",
                positioned.relx,
                positioned.rely);
            return null;
        }
        if (positioned.items.length == 0) {
            Galaxia.LOG.info(
                "[RecipeInput] firstItem: positioned.items is empty for pos ({},{})",
                positioned.relx,
                positioned.rely);
            return null;
        }
        for (ItemStack stack : positioned.items) {
            if (stack == null) {
                Galaxia.LOG.info("[RecipeInput] firstItem: stack is null in items array");
                continue;
            }
            if (stack.getItem() == null) {
                Galaxia.LOG.info("[RecipeInput] firstItem: stack.getItem() is null for stack={}", stack);
                continue;
            }
            return stack.copy();
        }
        Galaxia.LOG.info(
            "[RecipeInput] firstItem: no valid stack in {} items for pos ({},{})",
            positioned.items.length,
            positioned.relx,
            positioned.rely);
        return null;
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

    private static final class GhostItemWidget extends ParentWidget<GhostItemWidget> {

        private final ItemStackHandler handler;

        GhostItemWidget(ItemStackHandler handler) {
            this.handler = handler;
            disableHoverThemeBackground(true);
        }

        @Override
        public boolean canHoverThrough() {
            return handler.getStackInSlot(0) == null;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            ItemStack stack = handler.getStackInSlot(0);
            if (stack == null) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.fontRenderer == null || mc.getTextureManager() == null) return;
            RenderItem renderItem = RenderItem.getInstance();
            float prevZ = renderItem.zLevel;
            renderItem.zLevel = 200f;
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, 0.55f);
            try {
                RenderHelper.enableGUIStandardItemLighting();
                renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 1, 1);
                RenderHelper.disableStandardItemLighting();
            } finally {
                GL11.glPopAttrib();
                GL11.glColor4f(1f, 1f, 1f, 1f);
                renderItem.zLevel = prevZ;
            }
        }
    }

    private static final class GhostFluidWidget extends ParentWidget<GhostFluidWidget> {

        private final FluidTank tank;

        GhostFluidWidget(FluidTank tank) {
            this.tank = tank;
            background(IDrawable.NONE);
            disableHoverThemeBackground(true);
        }

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public boolean canClickThrough() {
            return true;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            FluidStack fluid = tank.getFluid();
            if (fluid == null) return;
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, 0.55f);
            try {
                com.cleanroommc.modularui.drawable.GuiDraw
                    .drawFluidTexture(fluid, 1, 1, 16, 16, context.getCurrentDrawingZ());
            } finally {
                GL11.glPopAttrib();
                GL11.glColor4f(1f, 1f, 1f, 1f);
            }
        }
    }

    private static final class RecipeInputNeiContainer extends ModularContainer
        implements INEIRecipeTransfer<GuiContainer> {

        private final GTRecipeInputScreen screen;

        RecipeInputNeiContainer(GTRecipeInputScreen screen) {
            this.screen = screen;
        }

        @Override
        public String[] getIdents() {
            return screen.neiTransferIds;
        }

        @Override
        public int transferRecipe(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
            screen.applyNeiRecipe(recipe, recipeIndex);
            return 0;
        }

        @Override
        public List<GuiOverlayButton.ItemOverlayState> presenceOverlay(GuiContainer gui, IRecipeHandler recipe,
            int recipeIndex) {
            List<PositionedStack> ingredients = recipe.getIngredientStacks(recipeIndex);
            if (ingredients == null) {
                throw new IllegalArgumentException(
                    "RecipeInputNeiContainer: ingredients list is null for recipe index " + recipeIndex);
            }
            List<GuiOverlayButton.ItemOverlayState> result = new ArrayList<>(ingredients.size());
            for (PositionedStack stack : ingredients) {
                result.add(new GuiOverlayButton.ItemOverlayState(stack, true));
            }
            return result;
        }

        @Override
        public ArrayList<PositionedStack> positionStacks(GuiContainer gui, ArrayList<PositionedStack> stacks) {
            return stacks;
        }
    }

    private static final class BodyBackgroundWidget extends ParentWidget<BodyBackgroundWidget> {

        private final GTRecipeInputScreen screen;

        BodyBackgroundWidget(GTRecipeInputScreen screen) {
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
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            GTGuiTextures.BACKGROUND_STANDARD.draw(ctx, 0, 0, w, h, theme.getTheme());
            if (screen.layout.progress()
                .enabled()) {
                GTRecipeMapLayout.Progress progress = screen.layout.progress();
                if (progress.texture() == null) {
                    int x = progress.x();
                    int y = progress.y();
                    BorderedRect.draw(x, y, progress.width(), progress.height(), 0xFF788398, 0xFFECF0FF);
                    fr.drawString(">", x + progress.width() / 2 - 2, y + 5, 0xFFE6EAF6);
                }
            }
        }
    }

    private static final class TitleTabWidget extends ParentWidget<TitleTabWidget> {

        private final String title;

        TitleTabWidget(String title) {
            this.title = title;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public void drawBackground(ModularGuiContext ctx, WidgetThemeEntry<?> theme) {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            GTGuiTextures.BACKGROUND_TITLE_STANDARD
                .draw(ctx, 0, 0, getArea().width, getArea().height, theme.getTheme());
            fr.drawString(title, TITLE_TAB_PADDING + TITLE_TEXT_PADDING, BODY_Y - fr.FONT_HEIGHT, 0xFF303642);
        }
    }

    private static final class StatusTextWidget extends ParentWidget<StatusTextWidget> {

        private final GTRecipeInputScreen screen;

        StatusTextWidget(GTRecipeInputScreen screen) {
            this.screen = screen;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public void drawBackground(ModularGuiContext ctx, WidgetThemeEntry<?> theme) {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            int statusY = screen.statusDetailText != null ? 0 : 12;
            fr.drawStringWithShadow(screen.statusText, 6, statusY, screen.statusColor);
            if (screen.statusDetailText != null) {
                fr.drawStringWithShadow(screen.statusDetailText, 6, statusY + 10, screen.statusColor);
            }
        }
    }
}
