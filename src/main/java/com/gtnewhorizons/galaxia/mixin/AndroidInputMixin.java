package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.galaxia.registry.android.AndroidControl;

@Mixin(NetHandlerPlayServer.class)
public class AndroidInputMixin {

    @Shadow
    public EntityPlayerMP playerEntity;

    @Inject(
        method = { "processPlayer", "processPlayerDigging", "processPlayerBlockPlacement", "processClickWindow",
            "processCreativeInventoryAction", "processUseEntity", "processEnchantItem", "processHeldItemChange",
            "processPlayerAbilities", "processEntityAction" },
        at = @At("HEAD"),
        cancellable = true,
        require = 10)
    private void galaxia$freezeBodyTransition(CallbackInfo ci) {
        if (AndroidControl.locked(playerEntity)) ci.cancel();
    }
}
