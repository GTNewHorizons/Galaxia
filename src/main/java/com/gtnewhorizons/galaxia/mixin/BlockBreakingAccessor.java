package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.server.management.ItemInWorldManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemInWorldManager.class)
public interface BlockBreakingAccessor {

    @Accessor("receivedFinishDiggingPacket")
    void galaxia$setPendingFinish(boolean pending);
}
