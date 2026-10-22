package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.galaxia.registry.android.AndroidControl;

import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.wands.ItemWandCasting;

@Pseudo
@Mixin(targets = "thaumic.tinkerer.common.item.foci.ItemFocusEnderChest", remap = false)
public class AndroidEnderFocusMixin {

    @Redirect(
        method = "onFocusRightClick",
        at = @At(
            value = "INVOKE",
            target = "Lthaumcraft/common/items/wands/ItemWandCasting;consumeAllVis(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/api/aspects/AspectList;ZZ)Z"),
        require = 1)
    private boolean galaxia$denyAndroidVanillaFocus(ItemWandCasting wand, ItemStack stack, EntityPlayer player,
        AspectList cost, boolean consume, boolean crafting) {
        return !AndroidControl.isAndroid(player) && wand.consumeAllVis(stack, player, cost, consume, crafting);
    }

    @Pseudo
    @Mixin(targets = "thaumic.tinkerer.common.compat.EnderStorageFunctions", remap = false)
    public static class EnderStorage {

        @Redirect(
            method = "onFocusRightClick",
            at = @At(
                value = "INVOKE",
                target = "Lthaumcraft/common/items/wands/ItemWandCasting;consumeAllVis(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/api/aspects/AspectList;ZZ)Z"),
            require = 1)
        private static boolean galaxia$denyAndroidVanillaFocus(ItemWandCasting wand, ItemStack stack,
            EntityPlayer player, AspectList cost, boolean consume, boolean crafting) {
            ItemStack focus = wand.getFocusItem(stack);
            if (AndroidControl.isAndroid(player) && !focus.getTagCompound()
                .getBoolean("ender")) {
                return false;
            }
            return wand.consumeAllVis(stack, player, cost, consume, crafting);
        }
    }
}
