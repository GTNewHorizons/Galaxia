package com.gtnewhorizons.galaxia.latemixin.gregtech;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.galaxia.compat.gt.CraterProspecting;

import gregtech.api.items.MetaBaseItem;

/** Adds crater analysis to the electric prospector's use-in-air action. */
@Mixin(targets = "detrav.items.behaviours.BehaviourDetravToolElectricProspector", remap = false)
public abstract class ElectricCraterProspectorMixin {

    @Inject(method = "onItemRightClick", at = @At("RETURN"))
    private void galaxia$analyzeCrater(MetaBaseItem item, ItemStack stack, World world, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> callback) {
        if (!player.isSneaking()) CraterProspecting.report(world, player);
    }
}
