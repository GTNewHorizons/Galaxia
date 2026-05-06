package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.galaxia.client.render.sky.EnhancedSkyRender;

/**
 * A mixin used to add custom celestial objects to star renderer such as galaxies / nebulae etc
 */
@Mixin(RenderGlobal.class)
public abstract class RenderStarsMixin {

    @Inject(method = "renderSky(F)V", at = @At("RETURN"))
    private void galaxia$afterSky(float partialTicks, CallbackInfo ci) {
        World world = Minecraft.getMinecraft().theWorld;
        EnhancedSkyRender.renderBakedSkyLayers(world, partialTicks);
    }
}
