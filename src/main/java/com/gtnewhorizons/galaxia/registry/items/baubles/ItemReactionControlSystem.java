package com.gtnewhorizons.galaxia.registry.items.baubles;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.interfaces.IZeroGMovementProvider;

import baubles.api.BaubleType;
import baubles.api.expanded.IBaubleExpanded;

public class ItemReactionControlSystem extends Item implements IBaubleExpanded, IZeroGMovementProvider {

    public static final String BAUBLE_TYPE_REACTION_CONTROL_SYSTEM = "reaction_control_system";

    private boolean enabled = true;

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

    static private double prevMotionX = 0;
    static private double prevMotionY = 0;
    static private double prevMotionZ = 0;

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (!this.enabled) return;
        if (player.ticksExisted % 40 != 0 || player.worldObj.isRemote) return;
        if (GalaxiaAPI.getGravity(player) != 0) return;

        final boolean isThrusting = Math.abs(prevMotionX - player.motionX) > 1e-6
            || Math.abs(prevMotionY - player.motionY) > 1e-6
            || Math.abs(prevMotionZ - player.motionZ) > 1e-6;

        prevMotionX = player.motionX;
        prevMotionY = player.motionY;
        prevMotionZ = player.motionZ;

        if (isThrusting) {
            return;
        }

        EffectBuilder def = GalaxiaAPI.getEffects(player);
        int oxygen = def.getOxygenPercent(player.worldObj);

        // TODO: Adjust for balance
        this.enabled = GalaxiaAPI.checkOxygenAndDrain((EntityPlayer) player, oxygen);
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

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
