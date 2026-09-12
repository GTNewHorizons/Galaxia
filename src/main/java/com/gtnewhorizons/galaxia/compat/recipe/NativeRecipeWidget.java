package com.gtnewhorizons.galaxia.compat.recipe;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeHandlerRef;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Embeds an exact native NEI recipe without enabling inventory transfer or crafting controls. */
@SideOnly(Side.CLIENT)
public final class NativeRecipeWidget extends Widget<NativeRecipeWidget> implements Interactable {

    private final RecipeHandlerRef recipe;
    private final NEIRecipeWidget view;
    private final RecipeHost host;
    private final Consumer<PositionedStack> selection;
    private int mouseX;
    private int mouseY;

    public NativeRecipeWidget(RecipeHandlerRef recipe, Consumer<PositionedStack> selection) {
        this.recipe = recipe;
        this.selection = selection;
        this.view = new NEIRecipeWidget(recipe) {

            @Override
            public List<GuiRecipeButton> getRecipeButtons() {
                return List.of();
            }
        };
        view.showAsWidget(true);
        view.update();
        this.host = new RecipeHost();
        size(view.w, view.h);
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        host.position(context);
        withHost(() -> {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();
            try {
                OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GuiContainerManager.enable2DRender();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1, 1, 1, 1);
                view.draw(mouseX, mouseY);
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        });
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        withHost(() -> {
            recipe.handler.onUpdate();
            view.update();
        });
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        if (!isHovering() || context.hasDraggable()) return;
        withHost(() -> {
            ItemStack stack = view.getStackMouseOver(mouseX, mouseY);
            List<String> tooltip = stack == null ? new ArrayList<>()
                : new ArrayList<>(stack.getTooltip(host.mc.thePlayer, host.mc.gameSettings.advancedItemTooltips));
            if (stack != null) tooltip = view.handleItemTooltip(stack, mouseX, mouseY, tooltip);
            tooltip = view.handleTooltip(mouseX, mouseY, tooltip);
            if (!tooltip.isEmpty()) {
                GuiDraw.drawMultilineTip(context.getAbsMouseX(), context.getAbsMouseY(), tooltip);
            }
        });
    }

    @Override
    public @Nonnull Result onMousePressed(int mouseButton) {
        if (mouseButton != 0) return Result.IGNORE;
        host.position(getContext());
        PositionedStack stack = view.getPositionedStackMouseOver(mouseX, mouseY);
        if (stack == null) return Result.IGNORE;
        selection.accept(stack);
        return Result.SUCCESS;
    }

    @Override
    public boolean onMouseScroll(UpOrDown direction, int amount) {
        host.position(getContext());
        return inHost(() -> view.onMouseWheel(direction == UpOrDown.UP ? amount : -amount, mouseX, mouseY));
    }

    private void withHost(Runnable action) {
        inHost(() -> {
            action.run();
            return null;
        });
    }

    private <T> T inHost(Supplier<T> action) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen previous = minecraft.currentScreen;
        minecraft.currentScreen = host;
        try {
            return action.get();
        } finally {
            minecraft.currentScreen = previous;
        }
    }

    /** Supplies the NEI screen contract to handlers while the actual screen stays owned by ModularUI. */
    private final class RecipeHost extends GuiRecipe<IRecipeHandler> {

        RecipeHost() {
            super(Minecraft.getMinecraft().currentScreen);
            this.mc = Minecraft.getMinecraft();
            this.fontRendererObj = mc.fontRenderer;
            currenthandlers.add(recipe.handler);
        }

        void position(ModularGuiContext context) {
            guiLeft = context.transformX(0, 0);
            guiTop = context.transformY(0, 0);
            width = context.getScreenArea().width;
            height = context.getScreenArea().height;
            xSize = view.w;
            ySize = view.h;
            mouseX = context.getMouseX();
            mouseY = context.getMouseY();
        }

        @Override
        public IRecipeHandler getHandler() {
            return recipe.handler;
        }

        @Override
        public String getHandlerName() {
            return view.getHandlerInfo()
                .getHandlerName();
        }

        @Override
        public List<Integer> getRecipeIndices() {
            return List.of(recipe.recipeIndex);
        }

        @Override
        public Point getRecipePosition(int index) {
            return new Point(
                0,
                view.getHandlerInfo()
                    .getYShift());
        }

        @Override
        public boolean isMouseOver(PositionedStack stack, int index) {
            return stack == view.getPositionedStackMouseOver(mouseX, mouseY);
        }

        @Override
        public ArrayList<IRecipeHandler> getCurrentRecipeHandlers() {
            return currenthandlers;
        }
    }
}
