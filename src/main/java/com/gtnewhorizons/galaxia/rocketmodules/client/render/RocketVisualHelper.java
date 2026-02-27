package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.ModuleType;
import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly.ModulePlacement;

public final class RocketVisualHelper {

    public static void render(RocketAssembly assembly, double baseX, double baseY, double baseZ, boolean isSilo) {
        if (assembly == null) return;

        GL11.glDisable(GL11.GL_CULL_FACE);

        for (ModulePlacement p : assembly.getPlacements()) {
            ModuleType type = p.type();
            if (type == null) continue;

            double x = baseX + (isSilo ? 0.5 : 0) + p.x();
            double y = baseY + p.y();
            double z = baseZ + (isSilo ? 0.5 : 0) + p.z();

            renderModule(type, x, y, z);
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private static void renderModule(ModuleType type, double x, double y, double z) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(type.getTexture());
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        type.getModel()
            .renderAll();
        GL11.glPopMatrix();
    }
}
