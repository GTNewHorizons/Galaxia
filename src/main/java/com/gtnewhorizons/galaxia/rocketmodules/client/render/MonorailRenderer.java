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

/**
 * TESR that renders the monorail beam and in-transit modules.
 *
 * <h3>Asset layout</h3>
 *
 * <pre>
 * assets/galaxia/
 *   textures/model/monorail/
 *     beam_segment.obj      — one 1m segment of rail, modelled along +Z
 *     beam_segment.png      — texture for above
 *     hook.obj              — clamp that attaches module to rail, origin at attachment point
 *     hook.png              — texture for above
 * </pre>
 *
 * The beam segment must be modelled so that:
 * <ul>
 * <li>It runs from Z=0 to Z=1 (one metre).</li>
 * <li>The attachment point (where hooks hang from) is at Y=0, X=0, Z=0..1.</li>
 * </ul>
 *
 * The hook must be modelled so that:
 * <ul>
 * <li>Its origin (0,0,0) is the contact point with the rail.</li>
 * <li>It hangs downward along -Y.</li>
 * </ul>
 */
public class MonorailRenderer extends TileEntitySpecialRenderer {

    // -----------------------------------------------------------------------
    // Asset paths
    // -----------------------------------------------------------------------

    private static final ResourceLocation BEAM_MODEL_LOC = GalaxiaAPI
        .LocationGalaxia("textures/model/monorail/beam_segment.obj");
    private static final ResourceLocation BEAM_TEX_LOC = GalaxiaAPI
        .LocationGalaxia("textures/model/monorail/beam_segment.png");

    private static final ResourceLocation HOOK_MODEL_LOC = GalaxiaAPI
        .LocationGalaxia("textures/model/monorail/hook.obj");
    private static final ResourceLocation HOOK_TEX_LOC = GalaxiaAPI.LocationGalaxia("textures/model/monorail/hook.png");

    // -----------------------------------------------------------------------
    // Visual constants
    // -----------------------------------------------------------------------

    /** Length of one beam segment model in world metres (must match the OBJ). */
    private static final double SEGMENT_LENGTH = 1.0;

    /** Uniform scale for module models during transit. */
    private static final float MODULE_RENDER_SCALE = 1.0f;

    /**
     * How far below the rail centre the module centre sits.
     * Set to module.getWidth() / 2 so the top of the module touches the rail.
     * Computed per-module in renderTransitModules(); this constant is the
     * extra clearance added on top of that.
     */
    private static final float HOOK_CLEARANCE = 0.25f;

    // -----------------------------------------------------------------------
    // Lazily loaded models
    // -----------------------------------------------------------------------

    private IModelCustom beamSegmentModel;
    private IModelCustom hookModel;

    private IModelCustom getBeamModel() {
        if (beamSegmentModel == null) beamSegmentModel = AdvancedModelLoader.loadModel(BEAM_MODEL_LOC);
        return beamSegmentModel;
    }

    private IModelCustom getHookModel() {
        if (hookModel == null) hookModel = AdvancedModelLoader.loadModel(HOOK_MODEL_LOC);
        return hookModel;
    }

    // -----------------------------------------------------------------------
    // TESR entry point
    // -----------------------------------------------------------------------

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTick) {
        if (!(te instanceof TileEntitySilo silo)) return;

        ChunkCoordinates masterPos = silo.getMasterPos();
        if (masterPos == null) return;

        TileEntity masterTe = silo.getWorldObj()
            .getTileEntity(masterPos.posX, masterPos.posY, masterPos.posZ);
        if (!(masterTe instanceof TileEntityModuleAssembler)) return;

        double yOff = silo.getMonorailYOffset();

        // Silo endpoint in render-space
        double sx = x + 0.5;
        double sy = y + 1.0 + yOff;
        double sz = z + 0.5;

        // MA endpoint
        double ddx = masterPos.posX - silo.xCoord;
        double ddy = masterPos.posY - silo.yCoord;
        double ddz = masterPos.posZ - silo.zCoord;
        double ex = x + ddx + 0.5;
        double ey = y + ddy + 1.0 + yOff;
        double ez = z + ddz + 0.5;

        MonorailPath path = new MonorailPath(sx, sy, sz, ex, ey, ez, SEGMENT_LENGTH);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        // Keep lighting ON so world light and AO shade the models naturally.
        // GL_COLOR_MATERIAL lets the white vertex colour from renderAll() work
        // without overriding the ambient/diffuse material set by Minecraft.
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);

        renderBeam(path);
        renderTransitModules(silo, path, partialTick);

        GL11.glPopAttrib();
    }

    // -----------------------------------------------------------------------
    // Beam rendering
    // -----------------------------------------------------------------------

    /**
     * Renders the rail by placing one beam segment model per metre.
     *
     * <p>
     * Each segment is translated to its position on the path and then
     * rotated so its local +Z aligns with the rail direction. The model is
     * authored with +Z = forward, so:
     * <ol>
     * <li>Yaw around Y → horizontal direction.</li>
     * <li>Pitch around local X → vertical slope.</li>
     * </ol>
     */
    private void renderBeam(MonorailPath path) {
        int segments = path.getSegmentCount();
        double[] dir = path.getDirection();

        float yawDeg = (float) Math.toDegrees(Math.atan2(dir[0], dir[2]));
        float pitchDeg = (float) Math.toDegrees(Math.asin(dir[1]));

        Minecraft.getMinecraft().renderEngine.bindTexture(BEAM_TEX_LOC);

        for (int i = 0; i < segments; i++) {
            double t = (double) i / segments;
            double[] pos = path.pointAt(t);

            GL11.glPushMatrix();
            GL11.glTranslated(pos[0], pos[1], pos[2]);
            GL11.glRotatef(yawDeg, 0f, 1f, 0f);
            GL11.glRotatef(-pitchDeg, 1f, 0f, 0f);
            // Segment runs 0→1 along +Z, which now points along the rail
            getBeamModel().renderAll();
            GL11.glPopMatrix();
        }
    }

    // -----------------------------------------------------------------------
    // Transit module + hook rendering
    // -----------------------------------------------------------------------

    /**
     * Renders each in-transit module and its two hooks.
     *
     * <h3>Layout along the rail (rail = local +Z after rotation)</h3>
     *
     * <pre>
     *   rail:    ──────────────────────────────
     *   hooks:         H           H
     *   module:        [═══════════]
     *                  ^           ^
     *               -halfH      +halfH   (halfH = module.getHeight()/2)
     * </pre>
     *
     * The module centre is offset downward by {@code module.getWidth()/2 + HOOK_CLEARANCE}
     * so the top of the module aligns with the bottom of the hook.
     *
     * <h3>Rotation</h3>
     * Modules are modelled upright (+Y = height). When laid horizontal along the
     * rail we need +Y → rail direction (+Z after yaw/pitch). That requires an
     * additional 90° tip around the local X axis <em>after</em> the rail alignment
     * rotations. OpenGL applies them in reverse order so the tip comes last in code.
     */
    private void renderTransitModules(TileEntitySilo silo, MonorailPath path, float partialTick) {
        MonorailAnimationState anim = silo.getAnimationState();
        java.util.List<TransitEntry> entries = anim.getEntries();
        if (entries.isEmpty()) return;

        double[] dir = path.getDirection();
        float pathLength = (float) path.getTotalLength();

        float yawDeg = (float) Math.toDegrees(Math.atan2(dir[0], dir[2]));
        float pitchDeg = (float) Math.toDegrees(Math.asin(dir[1]));

        for (TransitEntry entry : entries) {
            RocketModule module = ModuleRegistry.fromId(entry.moduleId);
            if (module == null) continue;

            // t is the centre of this module on the rail.
            float t = entry.prevProgress + (entry.progress - entry.prevProgress) * partialTick;
            double[] centre = path.pointAt(t);

            float height = (float) (module.getHeight());
            float heightProg = (pathLength > 1e-3f) ? height / pathLength : 0f;

            // Vertical drop: module centre hangs below the rail
            float dropY = (float) (module.getWidth() / 2.0) + HOOK_CLEARANCE;

            // ── Module ──────────────────────────────────────────────────────
            // No centering translate: OBJ origin (Y=0) sits at path.pointAt(t),
            // module extends from t to t+height along the rail after the tip rotation.
            GL11.glPushMatrix();
            GL11.glTranslated(centre[0], centre[1] - dropY, centre[2]);
            GL11.glRotatef(yawDeg, 0f, 1f, 0f); // 1. face rail horizontally
            GL11.glRotatef(-pitchDeg, 1f, 0f, 0f); // 2. tilt for slope
            GL11.glRotatef(90f, 1f, 0f, 0f); // 3. tip: +Y → +Z (along rail)
            GL11.glScalef(MODULE_RENDER_SCALE, MODULE_RENDER_SCALE, MODULE_RENDER_SCALE);
            Minecraft.getMinecraft().renderEngine.bindTexture(module.getTexture());
            module.getModel()
                .renderAll();
            GL11.glPopMatrix();

            // Hooks at the two actual visual ends of the module.
            // Module base is at t, top is at t + heightProg.
            // for some reason proper positioning of t + 2 * hookInsetProg hook is only achieved that way instead of
            // more logical t + hookInsetProg
            float hookInsetProg = (pathLength > 1e-3f) ? 0.5f / pathLength : 0f;
            renderHook(path.pointAt(t + 2 * hookInsetProg), yawDeg, pitchDeg);
            renderHook(path.pointAt(t + heightProg - hookInsetProg), yawDeg, pitchDeg);
        }
    }

    /**
     * Renders one hook at the given rail position.
     *
     * <p>
     * The hook model has its origin at the rail contact point and hangs
     * downward, so we simply translate to the rail point and apply the same
     * yaw/pitch as the beam. No extra tip rotation — hooks stay vertical.
     *
     * @param railPos  World position on the rail (render-space)
     * @param yawDeg   Rail horizontal angle
     * @param pitchDeg Rail vertical angle
     * @param dropY    How far below the rail point the module centre sits
     *                 (hook must be long enough to bridge this gap visually)
     */
    private void renderHook(double[] railPos, float yawDeg, float pitchDeg) {
        GL11.glPushMatrix();
        GL11.glTranslated(railPos[0], railPos[1], railPos[2]);
        GL11.glRotatef(yawDeg, 0f, 1f, 0f);
        GL11.glRotatef(-pitchDeg, 1f, 0f, 0f);
        Minecraft.getMinecraft().renderEngine.bindTexture(HOOK_TEX_LOC);
        getHookModel().renderAll();
        GL11.glPopMatrix();
    }

    // -----------------------------------------------------------------------
    // Math utils
    // -----------------------------------------------------------------------

    private static void normalise(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len > 1e-9) {
            v[0] /= len;
            v[1] /= len;
            v[2] /= len;
        }
    }
}
