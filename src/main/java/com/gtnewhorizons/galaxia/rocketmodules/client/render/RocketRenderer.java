package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import static com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry.getModule;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry.ModuleInfo;
import com.gtnewhorizons.galaxia.rocketmodules.entities.EntityRocket;

public class RocketRenderer extends Render {

    public RocketRenderer() {
        this.shadowSize = 0.5F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        EntityRocket rocket = (EntityRocket) entity;
        if (!rocket.shouldRender()) return;

        List<Integer> types = rocket.getModuleTypes();
        if (types.isEmpty()) return;

        List<ModuleInfo> commandModules = new ArrayList<>();
        List<ModuleInfo> storageModules = new ArrayList<>();
        List<ModuleInfo> fuelTanks = new ArrayList<>();
        List<ModuleInfo> engines = new ArrayList<>();

        for (int type : types) {
            ModuleInfo info = getModule(type);
            if (info == null) continue;

            switch (type) {
                case 0 -> fuelTanks.add(info);
                case 1 -> commandModules.add(info);
                case 2 -> storageModules.add(info);
                case 3 -> engines.add(info);
            }
        }

        double yOff = 0.75;
        GL11.glDisable(GL11.GL_CULL_FACE);

        int totalTanks = fuelTanks.size();
        int tankIndex = 0;
        int tanksRemaining = totalTanks;
        int engineIndex = 0;

        if (totalTanks <= 2) {
            double tierEngineHeight = 0;
            if (engineIndex < engines.size()) {
                ModuleInfo engine = engines.get(engineIndex++);
                renderModule(engine, x, y + yOff, z, 0, 0);
                tierEngineHeight = engine.height();
            }
            yOff += tierEngineHeight;
            for (ModuleInfo info : fuelTanks) {
                renderModule(info, x, y + yOff, z, 0, 0);
                yOff += info.height();
            }
        } else {
            while (tanksRemaining > 0) {
                int orbitalCount = Math.min(tanksRemaining - 1, 6);

                double orbitRadius;
                if (orbitalCount > 0) {
                    double clearCentre = (fuelTanks.get(tankIndex)
                        .width() / 2.0)
                        + (fuelTanks.get(tankIndex + 1)
                            .width() / 2.0);
                    double clearEachOther = orbitalCount > 1 ? (fuelTanks.get(tankIndex + 1)
                        .width()) / (2.0 * Math.sin(Math.PI / orbitalCount)) : 0;
                    orbitRadius = Math.max(clearCentre, clearEachOther) + 0.1;
                } else {
                    orbitRadius = 0;
                }

                double tierEngineHeight = 0;
                if (engineIndex < engines.size()) {
                    ModuleInfo engine = engines.get(engineIndex++);
                    renderModule(engine, x, y + yOff, z, 0, 0);
                    tierEngineHeight = engine.height();
                }

                ModuleInfo centreTank = fuelTanks.get(tankIndex);
                renderModule(centreTank, x, y + yOff + tierEngineHeight, z, 0, 0);
                tankIndex++;
                tanksRemaining--;

                for (int o = 0; o < orbitalCount; o++) {
                    double angle = (2 * Math.PI / orbitalCount) * o;
                    double offsetX = Math.cos(angle) * orbitRadius;
                    double offsetZ = Math.sin(angle) * orbitRadius;

                    double orbitalEngineHeight = 0;
                    if (engineIndex < engines.size()) {
                        ModuleInfo engine = engines.get(engineIndex++);
                        renderModule(engine, x, y + yOff, z, offsetX, offsetZ);
                        orbitalEngineHeight = engine.height();
                    }

                    ModuleInfo orbitalTank = fuelTanks.get(tankIndex);
                    renderModule(orbitalTank, x, y + yOff + orbitalEngineHeight, z, offsetX, offsetZ);
                    tankIndex++;
                    tanksRemaining--;
                }

                yOff += tierEngineHeight + centreTank.height();
            }
        }

        for (ModuleInfo info : storageModules) {
            renderModule(info, x, y + yOff, z, 0, 0);
            yOff += info.height();
        }

        for (ModuleInfo info : commandModules) {
            renderModule(info, x, y + yOff, z, 0, 0);
            yOff += info.height();
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void renderModule(ModuleInfo info, double x, double y, double z, double offsetX, double offsetZ) {
        bindTexture(info.texture());
        GL11.glPushMatrix();
        GL11.glTranslated(x + offsetX, y, z + offsetZ);
        info.model()
            .renderAll();
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
