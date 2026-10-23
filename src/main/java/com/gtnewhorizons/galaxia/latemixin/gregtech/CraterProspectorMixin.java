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

/** Adds crater analysis after successful GT prospector block scans. */
@Mixin(
    targets = { "detrav.items.behaviours.BehaviourDetravToolProspector",
        "detrav.items.behaviours.BehaviourDetravToolElectricProspector" },
    remap = false)
public abstract class CraterProspectorMixin {

    @Inject(method = "onItemUse", at = @At("RETURN"))
    private void galaxia$analyzeCrater(MetaBaseItem item, ItemStack stack, EntityPlayer player, World world, int x,
        int y, int z, int side, float hitX, float hitY, float hitZ, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) CraterProspecting.report(world, player);
    }
}
