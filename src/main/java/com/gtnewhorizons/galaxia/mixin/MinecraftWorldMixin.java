package com.gtnewhorizons.galaxia.mixin;

import com.gtnewhorizons.galaxia.client.render.sky.EnhancedSkyRender;
import jss.notfine.render.RenderStars;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftWorldMixin {

    @Shadow
    public RenderGlobal renderGlobal;

    @Inject(
        method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
        at = @At("TAIL")
    )
    private void galaxia$onWorldLoaded(WorldClient world, String message, CallbackInfo ci) {
        EnhancedSkyRender.setCurrentWorld(world);

        if (this.renderGlobal != null) {
            RenderStars.reloadStarRenderList(this.renderGlobal);
        }
    }
}
