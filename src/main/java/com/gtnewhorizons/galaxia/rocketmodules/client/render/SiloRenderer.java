package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

/**
 * Class to deal with specifics of rendering Rockets on Silo
 */
public class SiloRenderer extends TileEntitySpecialRenderer {

    /**
     * TE renderer that works on a step-through process to build a solid model matrix and render
     *
     * @param te           Silo tile entity
     * @param x            X position of tile entity
     * @param y            Y position of tile entity
     * @param z            Z position of tile entity
     * @param partialTicks How far through current tick world is
     */
    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        // Guard clauses to ensure rendering enabled
        if (!(te instanceof TileEntitySilo silo)) return;
        if (!silo.shouldRender) return;
        if (silo.getNumModules() == 0) return;

        // Pre pass configurations
        List<ModuleRegistry.ModuleInfo> commandModules = new ArrayList<>();
        List<ModuleRegistry.ModuleInfo> storageModules = new ArrayList<>();
        List<ModuleRegistry.ModuleInfo> fuelTanks = new ArrayList<>();
        List<ModuleRegistry.ModuleInfo> engines = new ArrayList<>();

        for (int i = 0; i < silo.getNumModules(); i++) {
            int type = silo.getModuleType(i);
            ModuleRegistry.ModuleInfo info = ModuleRegistry.getModule(type);
            if (info == null) continue;

            switch (type) {
                case 0 -> fuelTanks.add(info);
                case 1 -> commandModules.add(info);
                case 2 -> storageModules.add(info);
                case 3 -> engines.add(info);
            }
        }

        double yOff = 1;
        int totalTanks = fuelTanks.size();
        int tankIndex = 0;
        int tanksRemaining = totalTanks;
        int engineIndex = 0;

        // Rendering Engines and Fuel Stacks
        if (totalTanks <= 2) {
            // Simple vertical stack
            double tierEngineHeight = 0;
            if (engineIndex < engines.size()) {
                ModuleRegistry.ModuleInfo engine = engines.get(engineIndex++);
                renderModule(engine, x, y + yOff, z, 0, 0);
                tierEngineHeight = engine.height();
            }
            yOff += tierEngineHeight;
            for (ModuleRegistry.ModuleInfo info : fuelTanks) {
                renderModule(info, x, y + yOff, z, 0, 0);
                yOff += info.height();
            }
        } else {
            // Orbital Stacks
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

                // Central engine (if one exists for this slot)
                double tierEngineHeight = 0;
                if (engineIndex < engines.size()) {
                    ModuleRegistry.ModuleInfo engine = engines.get(engineIndex++);
                    renderModule(engine, x, y + yOff, z, 0, 0);
                    tierEngineHeight = engine.height();
                }

                // Central tank above its engine
                ModuleRegistry.ModuleInfo centreTank = fuelTanks.get(tankIndex);
                renderModule(centreTank, x, y + yOff + tierEngineHeight, z, 0, 0);
                tankIndex++;
                tanksRemaining--;

                // Orbital engines and tanks
                for (int o = 0; o < orbitalCount; o++) {
                    double angle = (2 * Math.PI / orbitalCount) * o;
                    double offsetX = Math.cos(angle) * orbitRadius;
                    double offsetZ = Math.sin(angle) * orbitRadius;

                    double orbitalEngineHeight = 0;
                    if (engineIndex < engines.size()) {
                        ModuleRegistry.ModuleInfo engine = engines.get(engineIndex++);
                        renderModule(engine, x, y + yOff, z, offsetX, offsetZ);
                        orbitalEngineHeight = engine.height();
                    }

                    ModuleRegistry.ModuleInfo orbitalTank = fuelTanks.get(tankIndex);
                    renderModule(orbitalTank, x, y + yOff + orbitalEngineHeight, z, offsetX, offsetZ);
                    tankIndex++;
                    tanksRemaining--;
                }

                yOff += tierEngineHeight + centreTank.height();
            }
        }

        // Render Storage Modules
        for (ModuleRegistry.ModuleInfo info : storageModules) {
            renderModule(info, x, y + yOff, z, 0, 0);
            yOff += info.height();
        }

        // Render Command Modules
        for (ModuleRegistry.ModuleInfo info : commandModules) {
            renderModule(info, x, y + yOff, z, 0, 0);
            yOff += info.height();
        }
    }

    /**
     * Helper function to push a new model onto the matrix and render
     *
     * @param info    ModuleInfo used to get type of module etc.
     * @param x       x position of render
     * @param y       y position of render
     * @param z       z position of render
     * @param offsetX x offset from TE origin
     * @param offsetZ z offset from TE origin
     */
    private void renderModule(ModuleRegistry.ModuleInfo info, double x, double y, double z, double offsetX,
        double offsetZ) {
        bindTexture(info.texture());
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5 + offsetX, y, z + 0.5 + offsetZ);
        info.model()
            .renderAll();
        GL11.glPopMatrix();
    }
}
