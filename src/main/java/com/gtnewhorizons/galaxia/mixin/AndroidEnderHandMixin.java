package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.galaxia.registry.android.AndroidControl;

@Pseudo
@Mixin(targets = "vazkii.botania.common.item.ItemEnderHand", remap = false)
public class AndroidEnderHandMixin {

    // Optional targets use MCP names in development and SRG names after Forge deobfuscation.
    @Inject(method = { "onItemRightClick", "func_77659_a" }, at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxia$denyAndroidPortableChest(ItemStack stack, World world, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        if (AndroidControl.isAndroid(player)) {
            cir.setReturnValue(stack);
        }
    }

    @Inject(method = { "itemInteractionForEntity", "func_111207_a" }, at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxia$denyAndroidChestPickpocket(ItemStack stack, EntityPlayer player, EntityLivingBase target,
        CallbackInfoReturnable<Boolean> cir) {
        if (AndroidControl.isAndroid(player)
            || (target instanceof EntityPlayer targetPlayer && AndroidControl.isAndroid(targetPlayer))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "provideBlock", at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxia$denyAndroidEnderBlocks(EntityPlayer player, ItemStack requestor, ItemStack stack, Block block,
        int meta, boolean consume, CallbackInfoReturnable<Boolean> cir) {
        if (AndroidControl.isAndroid(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getBlockCount", at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxia$hideAndroidEnderBlocks(EntityPlayer player, ItemStack requestor, ItemStack stack, Block block,
        int meta, CallbackInfoReturnable<Integer> cir) {
        if (AndroidControl.isAndroid(player)) {
            cir.setReturnValue(0);
        }
    }
}
