package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.client.render.MonorailAnimationState.TransitEntry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.utility.GalaxiaAPI;

public class MonorailRenderer extends TileEntitySpecialRenderer {

    private static final ResourceLocation BEAM_MODEL_LOC = GalaxiaAPI
        .LocationGalaxia("textures/model/monorail/beam_segment.obj");
    private static final ResourceLocation BEAM_TEX_LOC = GalaxiaAPI
        .LocationGalaxia("textures/model/monorail/beam_segment.png");
    private static final ResourceLocation HOOK_MODEL_LOC = GalaxiaAPI
        .LocationGalaxia("textures/model/monorail/hook.obj");
    private static final ResourceLocation HOOK_TEX_LOC = GalaxiaAPI.LocationGalaxia("textures/model/monorail/hook.png");

    private static final double SEGMENT_SIZE = 1.0;
    private static final float MODULE_SCALE = 1.0f;
    private static final float HOOK_CLEARANCE = 0.25f;
    private static final float HOOK_INSET_BLOCKS = 0.35f;

    private IModelCustom beamModel;
    private IModelCustom hookModel;

    private IModelCustom getBeamModel() {
        return beamModel != null ? beamModel : (beamModel = AdvancedModelLoader.loadModel(BEAM_MODEL_LOC));
    }

    private IModelCustom getHookModel() {
        return hookModel != null ? hookModel : (hookModel = AdvancedModelLoader.loadModel(HOOK_MODEL_LOC));
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double rx, double ry, double rz, float partialTick) {
        if (!(te instanceof TileEntitySilo silo)) return;

        ChunkCoordinates master = silo.getMasterPos();
        if (master == null) return;

        TileEntity masterTe = silo.getWorldObj()
            .getTileEntity(master.posX, master.posY, master.posZ);
        if (!(masterTe instanceof TileEntityModuleAssembler)) return;

        MonorailPath path = createPath(silo, master, rx, ry, rz);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        try {
            setupGLState();
            renderBeam(path);
            renderTransitModules(silo, path, partialTick);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private MonorailPath createPath(TileEntitySilo silo, ChunkCoordinates master, double rx, double ry, double rz) {
        double yOff = silo.getMonorailYOffset();
        double sx = rx + 0.5, sy = ry + 1.0 + yOff, sz = rz + 0.5;
        double ex = rx + (master.posX - silo.xCoord) + 0.5;
        double ey = ry + (master.posY - silo.yCoord) + 1.0 + yOff;
        double ez = rz + (master.posZ - silo.zCoord) + 0.5;
        return new MonorailPath(sx, sy, sz, ex, ey, ez, SEGMENT_SIZE);
    }

    private void setupGLState() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
    }

    private void renderBeam(MonorailPath path) {
        Minecraft.getMinecraft().renderEngine.bindTexture(BEAM_TEX_LOC);
        double length = path.getTotalLength();
        int full = (int) Math.floor(length);
        double rem = length - full;

        for (int i = 0; i < full; i++) {
            double[] pos = path.pointAt(i / length);
            renderSegment(pos, path);
        }
        if (rem > 1e-6) {
            double[] pos = path.pointAt(full / length);
            GL11.glPushMatrix();
            GL11.glTranslated(pos[0], pos[1], pos[2]);
            path.applyRailRotation();
            GL11.glScalef(1f, 1f, (float) rem); // last segment is short
            getBeamModel().renderAll();
            GL11.glPopMatrix();
        }
    }

    private void renderSegment(double[] pos, MonorailPath path) {
        GL11.glPushMatrix();
        GL11.glTranslated(pos[0], pos[1], pos[2]);
        path.applyRailRotation();
        getBeamModel().renderAll();
        GL11.glPopMatrix();
    }

    private void renderTransitModules(TileEntitySilo silo, MonorailPath path, float partialTick) {
        float railLength = (float) path.getTotalLength();
        float yawDeg = path.getYawDegrees();
        float pitchDeg = path.getPitchDegrees();
        for (TransitEntry entry : silo.getAnimationState()
            .getEntries()) {
            RocketModule module = ModuleRegistry.fromId(entry.moduleId);
            if (module == null) continue;

            float t = entry.prevProgress + (entry.progress - entry.prevProgress) * partialTick;
            float heightProg = (railLength > 1e-3f) ? (float) module.getHeight() / railLength : 0f;

            double[] center = path.pointAt(t);
            float dropY = (float) (module.getWidth() / 2.0) + HOOK_CLEARANCE;
            GL11.glPushMatrix();
            GL11.glTranslated(center[0], center[1] - dropY, center[2]);
            path.applyRailRotation();
            GL11.glRotatef(90f, 1f, 0f, 0f);
            GL11.glTranslatef(0f, 0f, -heightProg * 0.5f);
            GL11.glScalef(MODULE_SCALE, MODULE_SCALE, MODULE_SCALE);
            Minecraft.getMinecraft().renderEngine.bindTexture(module.getTexture());
            module.getModel()
                .renderAll();
            GL11.glPopMatrix();
            float insetProg = HOOK_INSET_BLOCKS / railLength;

            double[] hookPos1 = path.pointAt(t + 2 * insetProg); // idk why 2x but it works
            double[] hookPos2 = path.pointAt(t + heightProg - insetProg);

            renderHook(hookPos1, yawDeg, pitchDeg);
            renderHook(hookPos2, yawDeg, pitchDeg);
        }
    }

    private void renderHook(double[] pos, float yawDeg, float pitchDeg) {
        GL11.glPushMatrix();
        GL11.glTranslated(pos[0], pos[1], pos[2]);
        GL11.glRotatef(yawDeg, 0f, 1f, 0f);
        GL11.glRotatef(-pitchDeg, 1f, 0f, 0f);
        Minecraft.getMinecraft().renderEngine.bindTexture(HOOK_TEX_LOC);
        getHookModel().renderAll();
        GL11.glPopMatrix();
    }
}
