package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.galaxia.gt5.SolarArrayTracking;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** Captures storage mutations independently of World notification flags. */
@Mixin(Chunk.class)
public abstract class SolarBlockChangesMixin {

    @Shadow
    public World worldObj;
    @Shadow
    @Final
    public int xPosition;
    @Shadow
    @Final
    public int zPosition;

    @Inject(method = "func_150807_a", at = @At("RETURN"))
    private void solarBlock(int x, int y, int z, Block block, int metadata, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) SolarArrayTracking.changed(worldObj, (xPosition << 4) + x, y, (zPosition << 4) + z);
    }

    @Inject(method = "setBlockMetadata", at = @At("RETURN"))
    private void solarMetadata(int x, int y, int z, int metadata, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) SolarArrayTracking.changed(worldObj, (xPosition << 4) + x, y, (zPosition << 4) + z);
    }

    @Mixin(World.class)
    public abstract static class Notifications {

        @Inject(method = "markBlockForUpdate", at = @At("HEAD"))
        private void solarOpacityRequest(int x, int y, int z, CallbackInfo ci) {
            SolarArrayTracking.changed((World) (Object) this, x, y, z);
        }
    }

    @Mixin(Block.class)
    public abstract static class Opacity {

        @WrapOperation(
            method = "setLightOpacity",
            at = @At(value = "FIELD", target = "Lnet/minecraft/block/Block;lightOpacity:I"))
        private void solarGlobalOpacity(Block block, int opacity, Operation<Void> original) {
            int before = block.getLightOpacity();
            original.call(block, opacity);
            if (before != opacity) SolarArrayTracking.opacityChanged();
        }
    }
}
