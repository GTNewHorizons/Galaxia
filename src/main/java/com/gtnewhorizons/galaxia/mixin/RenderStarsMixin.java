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
 * Angelica-compatible hook: injects after the star display list is rebuilt
 * This calls the shared baked-layer renderer so the display list contains your extra sky content
 */
@Mixin(RenderGlobal.class)
public abstract class RenderStarsMixin {

    @Inject(method = "renderSky(F)V", at = @At("RETURN"))
    private void galaxia$afterSky(float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;

        if (world == null) {
            System.out.println("Galaxia: world NULL");
            return;
        }

        int dim = world.provider.dimensionId;
        EnhancedSkyRender.SkyPreset preset = EnhancedSkyRender.getPreset(world);

        System.out.println("Galaxia: sky dim=" + dim + ", preset=" + (preset == null ? "null" : preset.name()));

        EnhancedSkyRender.renderBakedSkyLayers(world);
    }
}
