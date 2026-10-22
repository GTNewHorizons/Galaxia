package com.gtnewhorizons.galaxia.latemixin.gregtech;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizons.galaxia.compat.GTCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.common.misc.DrillingLogicDelegate;
import gregtech.common.tileentities.machines.basic.MTEMiner;

@Mixin(value = DrillingLogicDelegate.class, remap = false)
public abstract class MinerProspectingMixin {

    @ModifyExpressionValue(
        method = "mineBlock",
        at = @At(
            value = "INVOKE",
            ordinal = 0,
            target = "Lgregtech/common/ores/OreManager;mineBlock(Ljava/util/Random;Lnet/minecraft/world/World;IIIZIZZ)Ljava/util/List;"))
    private List<ItemStack> galaxia$mineralYield(List<ItemStack> drops, IGregTechTileEntity base, Block block, int x,
        int y, int z) {
        if (!(base.getMetaTileEntity() instanceof MTEMiner) || !GTUtility.isOre(
            block,
            base.getWorld()
                .getBlockMetadata(x, y, z)))
            return drops;
        List<ItemStack> output = new ArrayList<>(drops.size());
        for (ItemStack stack : drops) output.add(GTCompat.prospectingOutput(base, stack, base.getWorld().rand));
        return output;
    }
}
