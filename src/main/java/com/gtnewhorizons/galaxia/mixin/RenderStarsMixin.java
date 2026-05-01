package com.gtnewhorizons.galaxia.mixin;

import com.gtnewhorizons.galaxia.client.render.sky.EnhancedSkyRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Angelica-compatible hook: injects after the star display list is rebuilt
 * This calls the shared baked-layer renderer so the display list contains your extra sky content
 */
@Mixin(value = jss.notfine.render.RenderStars.class, remap = false)
public abstract class RenderStarsMixin {

    @Inject(method = "renderStars", at = @At("TAIL"), remap = false)
    private static void galaxia$appendSkyLayers(CallbackInfo ci) {
        EnhancedSkyRender.renderBakedSkyLayers(net.minecraft.client.Minecraft.getMinecraft().theWorld);
    }
}
