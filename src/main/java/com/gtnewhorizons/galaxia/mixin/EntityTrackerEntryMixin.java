package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.EntityTrackerEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;

/**
 * A rocket blueprint is a map only to be held like one. In an item frame it must not be sent around as a real map,
 * which would look up or create a vanilla map for it
 */
@Mixin(EntityTrackerEntry.class)
public abstract class EntityTrackerEntryMixin {

    @Redirect(
        method = "sendLocationToAllClients",
        at = @At(value = "CONSTANT", args = "classValue=net/minecraft/item/ItemMap"))
    private boolean galaxia$framedMapIsReal(Object item, Class<?> type) {
        return type.isInstance(item) && !(item instanceof ItemRocketSchematic);
    }
}
