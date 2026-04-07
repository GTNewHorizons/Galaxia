package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.galaxia.core.config.ConfigPlayer;
import com.gtnewhorizons.galaxia.utility.GalaxiaAPI;
import com.gtnewhorizons.galaxia.utility.ZeroGMovementAPI;

@Mixin(EntityPlayer.class)
public class MixinEntityPlayer {

    @Inject(method = "dropOneItem", at = @At("RETURN"))
    private void galaxia$addRecoilOnDrop(boolean dropAll, CallbackInfoReturnable<EntityItem> cir) {
        EntityItem dropped = cir.getReturnValue();
        if (dropped == null) return;

        EntityPlayer player = (EntityPlayer) (Object) this;
        if (GalaxiaAPI.getGravity(player) != 0 || (GalaxiaAPI.hasZeroGMovementCapability(player)
            && !ConfigPlayer.ConfigPlayerGlobal.recoil_with_zerog_capabilities)) return;

        double motionX = dropped.motionX;
        double motionY = dropped.motionY;
        double motionZ = dropped.motionZ;

        double massFactor = ZeroGMovementAPI.DEFAULT_MASS;
        int count = dropAll && player.inventory.getCurrentItem() != null ? player.inventory.getCurrentItem().stackSize
            : 1;

        ZeroGMovementAPI.addThrowRecoil(player, motionX, motionY, motionZ, massFactor * count);
    }
}
