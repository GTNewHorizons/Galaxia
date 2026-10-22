package com.gtnewhorizons.galaxia.client.render.elevator;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.joml.Math;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import com.gtnewhorizon.gtnhlib.client.model.wavefront.WavefrontVBOBuilder;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizons.galaxia.registry.block.special.BlockElevatorCable;
import com.gtnewhorizons.galaxia.registry.block.special.TileElevatorCable;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * Renderer of the elevator cable and its climber, taken from GTNH Intergalactic. The cable itself is drawn by a shader
 * as one tall column, which the elevator dock in orbit reuses to show the cable hanging down from it.
 * <p>
 * Credit for the model goes to Adam, credit for the model's texture and UV go to Jimbno
 */
public class RenderElevatorCable extends TileEntitySpecialRenderer implements ISimpleBlockRenderingHandler {

    /** Location of the climber texture */
    private static final ResourceLocation CLIMBER_TEXTURE = new ResourceLocation(
        BlockElevatorCable.ASSETS,
        "textures/models/climber.png");

    /** Offset of the climber from the cable block */
    private static final int CLIMBER_OFFSET = 50;
    /** Min Y level that the climber should have */
    private static final int MIN_CLIMBER_HEIGHT = 100;

    /** Distance from center to edge of cable octagon */
    private static final float LONG_DISTANCE = (1.0f + Math.sqrt(2.0f)) / 5.4f;
    /** Distance from center to end of parallel side */
    private static final float SHORT_DISTANCE = 1.0f / 5.4f;
    /** Height of the full rendered cable */
    public static final double CABLE_HEIGHT = 512.0;

    private static final float SIDE = 2.0f / 5.4f;
    private static final float SECTION_HEIGHT = 8 * SIDE;
    private static final int SECTIONS = (int) Math.ceil(CABLE_HEIGHT / SECTION_HEIGHT);
    private static final int VERTEX_COUNT = 48 * 4 * SECTIONS;

    private static IVertexArrayObject climberModel;
    private static boolean initialized = false;
    private static ShaderProgram cableProgram;
    private static int uModelProjectionMatrix;
    private static int uBlockTex;
    private static int uSectionHeight;
    private static int uTime;
    private static int uBaseY;
    private static int uGlowU;
    private static int uGlowV;
    private static int uUV;
    private static int aVertexID = -1;
    private static int vertexIDBuffer = -1;

    private static final FloatBuffer MODEL_VIEW_PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final Matrix4fStack MODEL_PROJECTION = new Matrix4fStack(2);

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof TileElevatorCable cable) || !cable.shouldRender()) return;

        renderCable(tile.getWorldObj(), x, y - 23, z, partialTicks);

        GL11.glPushMatrix();
        // A low elevator still lifts its climber to a minimum height
        GL11.glTranslated(
            x + 0.5,
            y + 0.5
                + cable.getClimberHeight()
                + ((CLIMBER_OFFSET + cable.yCoord) < MIN_CLIMBER_HEIGHT ? MIN_CLIMBER_HEIGHT : CLIMBER_OFFSET),
            z + 0.5);
        GL11.glRotated(cable.getClimberRotation(), 0.0, 1.0, 0.0);
        renderClimber();
        GL11.glPopMatrix();
    }

    private void renderClimber() {
        if (climberModel == null) {
            climberModel = WavefrontVBOBuilder
                .compileToVBO(new ResourceLocation(BlockElevatorCable.ASSETS, "models/climber.obj"));
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        bindTexture(CLIMBER_TEXTURE);
        GL11.glScaled(4, 4, 4);
        climberModel.render();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    /**
     * Draws a cable column {@value #CABLE_HEIGHT} blocks tall
     *
     * @param world        The world, for the light animation
     * @param x            Camera relative x of the column's corner
     * @param baseY        Camera relative y of the bottom of the column
     * @param z            Camera relative z of the column's corner
     * @param partialTicks Partial tick
     */
    public static void renderCable(World world, double x, double baseY, double z, float partialTicks) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);

        if (!initialized) initialize();

        cableProgram.use();
        GL20.glUniform1f(
            uTime,
            ((world.getWorldInfo()
                .getWorldTotalTime() % 60) + partialTicks) / 60f);
        GL20.glUniform1i(uBaseY, (int) baseY);

        MODEL_PROJECTION.identity();
        MODEL_PROJECTION.translate((float) x, (float) baseY, (float) z);

        GL11.glDisable(GL11.GL_CULL_FACE);
        MODEL_PROJECTION.get(0, MODEL_VIEW_PROJECTION);
        GL20.glUniformMatrix4(uModelProjectionMatrix, false, MODEL_VIEW_PROJECTION);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexIDBuffer);
        GL20.glVertexAttribPointer(aVertexID, 1, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(aVertexID);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, VERTEX_COUNT);

        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL20.glDisableVertexAttribArray(aVertexID);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glEnable(GL11.GL_CULL_FACE);
        ShaderProgram.clear();
    }

    /** Compiles the cable shader and its vertex id buffer on first use */
    private static void initialize() {
        IIcon icon = BlockElevatorCable.textures[0];
        final float minU = icon.getMinU();
        final float maxU = icon.getMaxU();
        final float minV = icon.getMinV();
        final float maxV = icon.getMaxV();

        final float glowMinU = Math.lerp(minU, maxU, 7f / 16f);
        final float glowMaxU = Math.lerp(minU, maxU, 9f / 16f);
        final float glowMinV = Math.lerp(minV, maxV, 7f / 16f);
        final float glowMaxV = Math.lerp(minV, maxV, 9f / 16f);

        cableProgram = new ShaderProgram(
            BlockElevatorCable.ASSETS,
            "shaders/spacecable.vert.glsl",
            "shaders/spacecable.frag.glsl");
        cableProgram.use();

        aVertexID = cableProgram.getAttribLocation("vertexId");
        uModelProjectionMatrix = cableProgram.getUniformLocation("u_ModelProjection");
        uBlockTex = cableProgram.getUniformLocation("u_BlockTex");
        uSectionHeight = cableProgram.getUniformLocation("u_SectionHeight");
        uTime = cableProgram.getUniformLocation("u_Time");
        uBaseY = cableProgram.getUniformLocation("u_BaseY");
        uGlowU = cableProgram.getUniformLocation("u_GlowU");
        uGlowV = cableProgram.getUniformLocation("u_GlowV");
        uUV = cableProgram.getUniformLocation("u_UV");

        vertexIDBuffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexIDBuffer);
        // A 3x-sized buffer acts as a vec3 position input to the shader, without one the draw is undefined in GL 2.x
        final ByteBuffer vertexIDData = BufferUtils.createByteBuffer(VERTEX_COUNT * 4 * 3);
        for (int i = 0; i < VERTEX_COUNT * 3; i++) {
            vertexIDData.putFloat(i * 4, (float) i);
        }
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexIDData, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        final FloatBuffer uvBuffer = BufferUtils.createFloatBuffer(4);
        uvBuffer.put(0, minU);
        uvBuffer.put(1, minV);
        uvBuffer.put(2, maxU);
        uvBuffer.put(3, maxV);

        GL20.glUniform1f(uSectionHeight, SECTION_HEIGHT);
        GL20.glUniform1i(uBlockTex, OpenGlHelper.defaultTexUnit - GL13.GL_TEXTURE0);
        GL20.glUniform2f(uGlowU, glowMinU, glowMaxU);
        GL20.glUniform2f(uGlowV, glowMinV, glowMaxV);
        GL20.glUniform2(uUV, uvBuffer);

        ShaderProgram.clear();
        initialized = true;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        IIcon side = BlockElevatorCable.textures[0];
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        tes.setNormal(0.0F, -1.0F, 0.0F);
        renderer.renderFaceYNeg(block, 0.0D, 0.0D, 0.0D, side);
        tes.setNormal(0.0F, 0.0F, -1.0F);
        renderer.renderFaceZNeg(block, 0.0D, 0.0D, 0.0D, side);
        tes.setNormal(0.0F, 0.0F, 1.0F);
        renderer.renderFaceZPos(block, 0.0D, 0.0D, 0.0D, side);
        tes.setNormal(-1.0F, 0.0F, 0.0F);
        renderer.renderFaceXNeg(block, 0.0D, 0.0D, 0.0D, side);
        tes.setNormal(1.0F, 0.0F, 0.0F);
        renderer.renderFaceXPos(block, 0.0D, 0.0D, 0.0D, side);
        tes.setNormal(0.0F, 1.0F, 0.0F);
        renderer.renderFaceYPos(block, 0.0D, 0.0D, 0.0D, side);
        tes.draw();
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    /**
     * Draws the plain cable block while the full cable is hidden
     */
    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileElevatorCable cable && cable.shouldRender()) return false;
        return renderer.renderStandardBlock(block, x, y, z);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return BlockElevatorCable.renderID;
    }
}
