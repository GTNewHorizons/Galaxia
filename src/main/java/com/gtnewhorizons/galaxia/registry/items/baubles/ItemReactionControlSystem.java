package com.gtnewhorizons.galaxia.registry.items.baubles;

import javax.annotation.Nonnull;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.android.AndroidControl;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;

import baubles.api.BaubleType;
import baubles.api.expanded.IBaubleExpanded;

public class ItemReactionControlSystem extends Item implements IBaubleExpanded {

    public static final String BAUBLE_TYPE_REACTION_CONTROL_SYSTEM = "reaction_control_system";

    private static final String NBT_DISABLED = "rcs_disabled";
    private static final String NBT_MOTION_X = "rcs_motion_x";
    private static final String NBT_MOTION_Y = "rcs_motion_y";
    private static final String NBT_MOTION_Z = "rcs_motion_z";

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        return GalaxiaAPI.onBaubleRightClick(stack, world, player, Galaxia.rcsSlot);
    }

    @Override
    public String[] getBaubleTypes(ItemStack itemstack) {
        return new String[] { BAUBLE_TYPE_REACTION_CONTROL_SYSTEM };
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.UNIVERSAL;
    }

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (!(player instanceof EntityPlayer operator)) return;
        if (!isEnabled(itemstack)) return;
        if (player.ticksExisted % 40 != 0 || player.worldObj.isRemote) return;
        if (AndroidControl.isAndroid(operator)) return;
        if (GalaxiaAPI.getGravity(player) != 0) return;

        NBTTagCompound state = state(itemstack);
        final boolean isThrusting = Math.abs(state.getDouble(NBT_MOTION_X) - player.motionX) > 1e-6
            || Math.abs(state.getDouble(NBT_MOTION_Y) - player.motionY) > 1e-6
            || Math.abs(state.getDouble(NBT_MOTION_Z) - player.motionZ) > 1e-6;

        state.setDouble(NBT_MOTION_X, player.motionX);
        state.setDouble(NBT_MOTION_Y, player.motionY);
        state.setDouble(NBT_MOTION_Z, player.motionZ);

        if (isThrusting) {
            return;
        }

        EffectBuilder def = GalaxiaAPI.getEffects(player);
        int oxygen = def.getOxygenPercent(player.worldObj);

        // TODO: Adjust for balance
        setEnabled(itemstack, GalaxiaAPI.checkOxygenAndDrain(operator, oxygen));
    }

    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {

    }

    @Override
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {

    }

    @Override
    public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }

    public boolean isEnabled(@Nonnull ItemStack stack) {
        return !stack.hasTagCompound() || !stack.getTagCompound()
            .getBoolean(NBT_DISABLED);
    }

    public void setEnabled(@Nonnull ItemStack stack, boolean enabled) {
        state(stack).setBoolean(NBT_DISABLED, !enabled);
    }

    @Nonnull
    private static NBTTagCompound state(@Nonnull ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }
}
