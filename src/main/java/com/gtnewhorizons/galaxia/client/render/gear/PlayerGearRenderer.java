package com.gtnewhorizons.galaxia.client.render.gear;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenMask;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemReactionControlSystem;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemThermalProtection;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the space gear worn in Galaxia's bauble slots onto the player model: oxygen tanks and the RCS pack on the
 * back, the oxygen mask on the face and the thermal suit as a thin overlay over body, arms and legs.
 * <p>
 * Works for every rendered player. Baubles-Expanded already syncs bauble slots to all clients tracking a player
 * (on every slot change and when tracking starts), so the bauble inventory read here is valid for other players too.
 * <p>
 * The OBJ models are authored in pixels / 16 in the frame left after a biped part's {@code postRender} followed by a
 * 180 degree turn around Z: +Y up, +Z towards the player's back, +X towards the player's right.
 */
@SideOnly(Side.CLIENT)
public class PlayerGearRenderer {

    private static final float PX = 0.0625F;

    // Placement of the back gear, in model pixels
    private static final float BODY_BACK = 2.3F;
    private static final float ARMOUR_OFFSET = 1.0F;
    private static final float RCS_CENTRE_Y = -5.0F;
    private static final float RCS_DEPTH = 2.25F;
    private static final float TANK_TOP_Y = -2.5F;
    private static final float TANK_X = 2.05F;
    private static final float TANK_RADIUS = 1.85F;

    // Mask is scaled about the head centre to sit on top of a helmet
    private static final float HEAD_CENTRE_Y = 4.0F;
    private static final float HELMET_SCALE = 1.25F;

    // Suit sits between the skin and leggings (0.5) / chestplate (1.0)
    private static final float SUIT_EXPANSION = 0.3F;

    private static final int ARMOUR_SLOT_CHEST = 2;
    private static final int ARMOUR_SLOT_HEAD = 3;

    private static final GearModel TANK_T1 = new GearModel("oxygen_tank_1");
    private static final GearModel TANK_T2 = new GearModel("oxygen_tank_2");
    private static final GearModel TANK_T3 = new GearModel("oxygen_tank_3");
    private static final GearModel TANK_INFINITE = new GearModel("oxygen_tank_4");
    private static final GearModel RCS = new GearModel("reaction_control_system");
    private static final GearModel MASK = new GearModel("oxygen_mask");

    private static final ResourceLocation SUIT_COLD = LocationGalaxia(
        "textures/model/gear/thermal_suit_cold/texture.png");
    private static final ResourceLocation SUIT_HOT = LocationGalaxia(
        "textures/model/gear/thermal_suit_hot/texture.png");

    private final ModelBiped suitModel = new ModelBiped(SUIT_EXPANSION);

    /**
     * Hides the cape of players wearing gear on their back
     *
     * @param event The specials pre event of the player being rendered
     */
    @SubscribeEvent
    public void onRenderSpecialsPre(RenderPlayerEvent.Specials.Pre event) {
        if (!event.renderCape) return;

        // A cape would clip straight through tanks and thrusters
        WornGear gear = WornGear.of(event.entityPlayer);
        if (gear != null && gear.hasBackGear()) event.renderCape = false;
    }

    /**
     * Renders the worn gear once the player model and its armour have been drawn
     *
     * @param event The specials post event of the player being rendered
     */
    @SubscribeEvent
    public void onRenderSpecialsPost(RenderPlayerEvent.Specials.Post event) {
        EntityPlayer player = event.entityPlayer;
        if (player.isInvisible()) return;

        WornGear gear = WornGear.of(player);
        if (gear == null || gear.isEmpty()) return;

        ModelBiped biped = event.renderer.modelBipedMain;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // The held item may have left a tint behind
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (gear.suit() != null) renderSuit(biped, gear.suit());
        if (gear.hasBackGear() && isVisible(biped.bipedBody))
            renderBackGear(biped, gear, hasArmour(player, ARMOUR_SLOT_CHEST));
        if (gear.mask() && isVisible(biped.bipedHead)) renderMask(biped, hasArmour(player, ARMOUR_SLOT_HEAD));

        GL11.glPopAttrib();
    }

    /**
     * Renders the thermal suit overlay in the current pose of the player model
     *
     * @param biped   The player model that was just rendered
     * @param texture The suit texture to use
     */
    private void renderSuit(ModelBiped biped, ResourceLocation texture) {
        copyPose(biped.bipedBody, suitModel.bipedBody);
        copyPose(biped.bipedRightArm, suitModel.bipedRightArm);
        copyPose(biped.bipedLeftArm, suitModel.bipedLeftArm);
        copyPose(biped.bipedRightLeg, suitModel.bipedRightLeg);
        copyPose(biped.bipedLeftLeg, suitModel.bipedLeftLeg);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        suitModel.bipedBody.render(PX);
        suitModel.bipedRightArm.render(PX);
        suitModel.bipedLeftArm.render(PX);
        suitModel.bipedRightLeg.render(PX);
        suitModel.bipedLeftLeg.render(PX);
    }

    /**
     * Renders the RCS pack and oxygen tanks attached to the body. Tanks are strapped on top of the RCS pack when
     * both are worn
     *
     * @param biped      The player model that was just rendered
     * @param gear       The gear worn by the player
     * @param chestplate Whether a chestplate is worn, pushing everything further back
     */
    private static void renderBackGear(ModelBiped biped, WornGear gear, boolean chestplate) {
        GL11.glPushMatrix();
        biped.bipedBody.postRender(PX);
        GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);

        float back = BODY_BACK + (chestplate ? ARMOUR_OFFSET : 0.0F);
        if (gear.rcs()) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0.0F, RCS_CENTRE_Y * PX, back * PX);
            RCS.render();
            GL11.glPopMatrix();
            back += RCS_DEPTH;
        }

        renderTank(gear.rightTank(), TANK_X, back);
        renderTank(gear.leftTank(), -TANK_X, back);

        GL11.glPopMatrix();
    }

    private static void renderTank(@Nullable GearModel tank, float x, float back) {
        if (tank == null) return;

        GL11.glPushMatrix();
        GL11.glTranslatef(x * PX, TANK_TOP_Y * PX, (back + TANK_RADIUS) * PX);
        tank.render();
        GL11.glPopMatrix();
    }

    /**
     * Renders the oxygen mask following the head rotation
     *
     * @param biped  The player model that was just rendered
     * @param helmet Whether a helmet is worn, in which case the mask is scaled up to sit on top of it
     */
    private static void renderMask(ModelBiped biped, boolean helmet) {
        GL11.glPushMatrix();
        biped.bipedHead.postRender(PX);
        GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);

        if (helmet) {
            GL11.glTranslatef(0.0F, HEAD_CENTRE_Y * PX, 0.0F);
            GL11.glScalef(HELMET_SCALE, HELMET_SCALE, HELMET_SCALE);
            GL11.glTranslatef(0.0F, -HEAD_CENTRE_Y * PX, 0.0F);
        }

        MASK.render();
        GL11.glPopMatrix();
    }

    private static void copyPose(ModelRenderer source, ModelRenderer target) {
        target.rotationPointX = source.rotationPointX;
        target.rotationPointY = source.rotationPointY;
        target.rotationPointZ = source.rotationPointZ;
        target.rotateAngleX = source.rotateAngleX;
        target.rotateAngleY = source.rotateAngleY;
        target.rotateAngleZ = source.rotateAngleZ;
        target.showModel = source.showModel;
        target.isHidden = source.isHidden;
    }

    // postRender does nothing for hidden parts, so attachments would end up at the model origin
    private static boolean isVisible(ModelRenderer part) {
        return part.showModel && !part.isHidden;
    }

    private static boolean hasArmour(EntityPlayer player, int slot) {
        return player.inventory.armorItemInSlot(slot) != null;
    }

    /**
     * Gear currently worn by a player, resolved to the models that should be drawn
     *
     * @param rightTank Tank in the first oxygen slot, or null
     * @param leftTank  Tank in the second oxygen slot, or null
     * @param rcs       Whether a reaction control system is worn
     * @param mask      Whether an oxygen mask is worn
     * @param suit      Thermal suit texture, or null
     */
    private record WornGear(@Nullable GearModel rightTank, @Nullable GearModel leftTank, boolean rcs, boolean mask,
        @Nullable ResourceLocation suit) {

        /**
         * Reads the rendered gear slots of a player's bauble inventory
         *
         * @param player The player to read
         * @return The worn gear, or null if the bauble inventory is unavailable
         */
        @Nullable
        static WornGear of(EntityPlayer player) {
            IInventory baubles = BaublesApi.getBaubles(player);
            if (baubles == null) return null;

            ItemStack rcsStack = getStack(baubles, Galaxia.rcsSlot, 0);
            ItemStack maskStack = getStack(baubles, Galaxia.oxygenMaskSlots, 0);
            return new WornGear(
                getTankModel(getStack(baubles, Galaxia.oxygenSlots, 0)),
                getTankModel(getStack(baubles, Galaxia.oxygenSlots, 1)),
                rcsStack != null && rcsStack.getItem() instanceof ItemReactionControlSystem,
                maskStack != null && maskStack.getItem() instanceof ItemOxygenMask,
                getSuitTexture(getStack(baubles, Galaxia.thermalSlot, 0)));
        }

        /**
         * @return Boolean : True => something is mounted on the player's back
         */
        boolean hasBackGear() {
            return rcs || rightTank != null || leftTank != null;
        }

        /**
         * @return Boolean : True => nothing needs to be rendered
         */
        boolean isEmpty() {
            return !hasBackGear() && !mask && suit == null;
        }

        @Nullable
        private static ItemStack getStack(IInventory baubles, @Nullable int[] slots, int index) {
            if (slots == null || index >= slots.length) return null;
            int slot = slots[index];
            if (slot < 0 || slot >= baubles.getSizeInventory()) return null;
            return baubles.getStackInSlot(slot);
        }

        @Nullable
        private static GearModel getTankModel(@Nullable ItemStack stack) {
            if (stack == null || !(stack.getItem() instanceof ItemOxygenTank)) return null;

            Item item = stack.getItem();
            if (item == GalaxiaItemList.OXYGEN_TANK_INFINITE.getItem()) return TANK_INFINITE;
            if (item == GalaxiaItemList.OXYGEN_TANK_T3.getItem()) return TANK_T3;
            if (item == GalaxiaItemList.OXYGEN_TANK_T2.getItem()) return TANK_T2;
            return TANK_T1;
        }

        @Nullable
        private static ResourceLocation getSuitTexture(@Nullable ItemStack stack) {
            if (stack == null || !(stack.getItem() instanceof ItemThermalProtection thermal)) return null;
            return thermal.getHeatProtection() > thermal.getColdProtection() ? SUIT_HOT : SUIT_COLD;
        }
    }

    /**
     * An OBJ model and its texture under {@code textures/model/gear/<name>/}, loaded on first use
     */
    private static final class GearModel {

        private final ResourceLocation modelLocation;
        private final ResourceLocation textureLocation;
        private IModelCustom model;

        private GearModel(String name) {
            this.modelLocation = LocationGalaxia("textures/model/gear/" + name + "/model.obj");
            this.textureLocation = LocationGalaxia("textures/model/gear/" + name + "/texture.png");
        }

        private void render() {
            if (model == null) model = AdvancedModelLoader.loadModel(modelLocation);

            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(textureLocation);
            model.renderAll();
        }
    }
}
