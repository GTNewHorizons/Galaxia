package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.client.render.MonorailAnimationState.TransitEntry;
import com.gtnewhorizons.galaxia.rocketmodules.client.render.MonorailPath.Segment;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityMonorailPole;
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

    private static final float MODULE_SCALE = 1.0f;
    private static final float HOOK_CLEARANCE = 0.25f;
    private static final double POLE_HIDE_RADIUS = 0.15;

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

        ChunkCoordinates masterPos = silo.getMasterPos();
        if (masterPos == null) return;

        List<ChunkCoordinates> waypoints = resolveWaypoints(silo, masterPos);
        if (waypoints == null || waypoints.size() < 2) return;

        double yOff = silo.getMonorailYOffset();
        MonorailPath path = MonorailPath
            .fromWaypoints(waypoints, rx, ry, rz, yOff, silo.xCoord, silo.yCoord, silo.zCoord);
        List<double[]> poles = collectPolePositions(waypoints, rx, ry, rz, yOff, silo.xCoord, silo.yCoord, silo.zCoord);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_COLOR_MATERIAL);
            GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
            renderBeam(path);
            renderTransitModules(silo, path, poles, partialTick);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private List<ChunkCoordinates> resolveWaypoints(TileEntitySilo silo, ChunkCoordinates masterPos) {
        List<ChunkCoordinates> chain = new ArrayList<>();
        chain.add(new ChunkCoordinates(silo.xCoord, silo.yCoord, silo.zCoord));
        ChunkCoordinates cursor = masterPos;
        for (int safety = 64; cursor != null && safety-- > 0;) {
            chain.add(0, cursor);
            TileEntity te = silo.getWorldObj()
                .getTileEntity(cursor.posX, cursor.posY, cursor.posZ);
            if (te instanceof TileEntityModuleAssembler) break;
            else if (te instanceof TileEntityMonorailPole pole) cursor = pole.getPrevPos();
            else return null;
        }
        return chain.size() >= 2 ? chain : null;
    }

    private List<double[]> collectPolePositions(List<ChunkCoordinates> waypoints, double rx, double ry, double rz,
        double yOff, int ox, int oy, int oz) {
        List<double[]> poles = new ArrayList<>();
        for (int i = 1; i < waypoints.size() - 1; i++) {
            ChunkCoordinates c = waypoints.get(i);
            poles.add(
                new double[] { rx + (c.posX - ox) + 0.5, ry + (c.posY - oy) + 1.0 + yOff, rz + (c.posZ - oz) + 0.5 });
        }
        return poles;
    }

    private void renderBeam(MonorailPath path) {
        Minecraft.getMinecraft().renderEngine.bindTexture(BEAM_TEX_LOC);
        for (Segment seg : path.getSegments()) {
            if (seg.length < 1e-6) continue;
            int full = (int) Math.floor(seg.length);
            for (int i = 0; i < full; i++) {
                renderSegmentBlock(seg.pointLocal((i + 0.5) / seg.length), seg, 1f);
            }
            double rem = seg.length - full;
            if (rem > 1e-6) {
                double centerT = (full + rem * 0.5) / seg.length;
                renderSegmentBlock(seg.pointLocal(centerT), seg, (float) rem);
            }
        }
    }

    private void renderSegmentBlock(double[] pos, Segment seg, float zScale) {
        GL11.glPushMatrix();
        GL11.glTranslated(pos[0], pos[1], pos[2]);
        GL11.glRotatef(seg.getYawDegrees(), 0f, 1f, 0f);
        GL11.glRotatef(-seg.getPitchDegrees(), 1f, 0f, 0f);
        if (zScale != 1f) GL11.glScalef(1f, 1f, zScale);
        getBeamModel().renderAll();
        GL11.glPopMatrix();
    }

    private void renderTransitModules(TileEntitySilo silo, MonorailPath path, List<double[]> poles, float partialTick) {
        for (TransitEntry entry : silo.getAnimationState()
            .getEntries()) {
            RocketModule module = ModuleRegistry.fromId(entry.moduleId);
            if (module == null) continue;

            float t1 = entry.lerpHook1(partialTick);
            float t2 = entry.lerpHook2(partialTick);
            float tMid = getTMid(entry, t1, t2);

            Segment midSeg = path.segmentAt(tMid);
            double[] posMid = path.pointAtUnclamped(tMid);

            double[] pos1 = path.pointAt(Math.max(0f, Math.min(1f, t1)));
            double[] pos2 = path.pointAt(Math.max(0f, Math.min(1f, t2)));
            double dx = pos1[0] - pos2[0], dy = pos1[1] - pos2[1], dz = pos1[2] - pos2[2];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float modYaw = len > 1e-6 ? (float) Math.toDegrees(Math.atan2(dx, dz)) : midSeg.getYawDegrees();
            float modPitch = len > 1e-6 ? (float) Math.toDegrees(Math.asin(dy / len)) : midSeg.getPitchDegrees();

            GL11.glPushMatrix();
            GL11.glTranslated(posMid[0], posMid[1], posMid[2]);
            GL11.glRotatef(modYaw, 0f, 1f, 0f);
            GL11.glRotatef(-modPitch, 1f, 0f, 0f);
            GL11.glTranslatef(0f, -(float) (module.getHeight() / 2.0 + HOOK_CLEARANCE), 0f);
            GL11.glRotatef(90f, 1f, 0f, 0f);
            GL11.glScalef(MODULE_SCALE, MODULE_SCALE, MODULE_SCALE);
            Minecraft.getMinecraft().renderEngine.bindTexture(module.getTexture());
            module.getModel()
                .renderAll();
            GL11.glPopMatrix();

            // Hooks only shown while inside path bounds
            if (t1 >= 0f && t1 <= 1f) renderHook(pos1, path.segmentAt(t1));
            if (t2 >= 0f && t2 <= 1f) renderHook(pos2, path.segmentAt(t2));
        }
    }

    private static float getTMid(TransitEntry entry, float t1, float t2) {
        boolean toSilo = entry.direction == MonorailAnimationState.Direction.TO_SILO;
        float tLeading = toSilo ? t1 : t2;
        float tTrailing = toSilo ? t2 : t1;
        float halfSpan = (tLeading - tTrailing) * 0.5f;
        return tTrailing + halfSpan;
    }

    private boolean isInsidePole(double[] pos, List<double[]> poles) {
        for (double[] pole : poles) {
            double dx = pos[0] - pole[0], dy = pos[1] - pole[1], dz = pos[2] - pole[2];
            if (dx * dx + dy * dy + dz * dz < POLE_HIDE_RADIUS * POLE_HIDE_RADIUS) return true;
        }
        return false;
    }

    private void renderHook(double[] pos, Segment seg) {
        GL11.glPushMatrix();
        GL11.glTranslated(pos[0], pos[1], pos[2]);
        GL11.glRotatef(seg.getYawDegrees(), 0f, 1f, 0f);
        GL11.glRotatef(-seg.getPitchDegrees(), 1f, 0f, 0f);
        Minecraft.getMinecraft().renderEngine.bindTexture(HOOK_TEX_LOC);
        getHookModel().renderAll();
        GL11.glPopMatrix();
    }
}
