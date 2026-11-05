package com.gtnewhorizons.galaxia.mixin;

import java.util.HashMap;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityLivingBase.class)
public interface LivingEquipmentAccessor {

    @Accessor("previousEquipment")
    ItemStack[] galaxia$previousEquipment();

    @Accessor("activePotionsMap")
    HashMap<Integer, PotionEffect> galaxia$activePotions();
}
