package com.gtnewhorizons.galaxia.rocketmodules.client.render.RocketEntityTest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import com.gtnewhorizons.galaxia.core.ShaderHelper;

public class RenderEntityTest extends Render {

    private static int program = 0;

    public static int rocketmask1 = 0;

    int vaoID = 0;
    int vboID = 0;

    int viewID = 0;
    int projectionID = 0;

    public RenderEntityTest() {
        if (program == 0) {
            program = ShaderHelper.createProgram(
                "/assets/galaxia/shaders/test.vert",
                "/assets/galaxia/shaders/test.geom",
                "/assets/galaxia/shaders/test.frag");
        }

//        GL11.glEnable(GL43.GL_DEBUG_OUTPUT);
//        GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
//
//        org.lwjgl.opengl.KHRDebug.glDebugMessageCallback(new org.lwjgl.opengl.KHRDebugCallback((source, type, id, severity, message) -> {
//            if (severity != GL43.GL_DEBUG_SEVERITY_NOTIFICATION && severity != GL43.GL_DEBUG_SEVERITY_LOW && severity != GL43.GL_DEBUG_SEVERITY_MEDIUM) System.err.println("GL Debug: " + message + "\nfrom " + source);
//        }));

        GL20.glUseProgram(program);

        viewID = GL20.glGetUniformLocation(program, "view");
        projectionID = GL20.glGetUniformLocation(program, "projection");

        GL20.glUseProgram(0);

        VAOInit();

        rocketmask1 = loadRocketMask("/assets/galaxia/textures/effect/rocketmasks/rocketmask1.bmp");
    }

    public void VAOInit() {
        vaoID = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoID);
        GL20.glEnableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float ticks) {
        if (!(entity instanceof EntityTest entityTest)) {
            return;
        }

        if (entityTest.ssboOUT == 0) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();

        GL11.glTranslated(x + 0.25, y, z + 0.25);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);

        GL20.glUseProgram(program);

        FloatBuffer view = BufferUtils.createFloatBuffer(16);
        FloatBuffer projection = BufferUtils.createFloatBuffer(16);

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, view);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);

        GL20.glUniformMatrix4(viewID, false, view);
        GL20.glUniformMatrix4(projectionID, false, projection);

        GL30.glBindVertexArray(vaoID);

        // reading particle count for point draws
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, entityTest.ssboOUTCount);

        IntBuffer count = BufferUtils.createIntBuffer(1);

        GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, count);


        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, entityTest.ssboOUT);
        GL20.glVertexAttribPointer(0, 1, GL11.GL_FLOAT, false, 4, 0);

        GL11.glDrawArrays(GL11.GL_POINTS, 0, count.get());

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }

    float hs = 0.125f;
    float[] vertices = {
        // Front face (z = hs)
        -hs, -hs, hs, hs, -hs, hs, hs, hs, hs, // triangle 1
        hs, hs, hs, -hs, hs, hs, -hs, -hs, hs, // triangle 2

        // Back face (z = -hs)
        -hs, -hs, -hs, -hs, hs, -hs, hs, hs, -hs, // triangle 3
        hs, hs, -hs, hs, -hs, -hs, -hs, -hs, -hs, // triangle 4

        // Left face (x = -hs)
        -hs, -hs, -hs, -hs, -hs, hs, -hs, hs, hs, // triangle 5
        -hs, hs, hs, -hs, hs, -hs, -hs, -hs, -hs, // triangle 6

        // Right face (x = hs)
        hs, -hs, -hs, hs, hs, -hs, hs, hs, hs, // triangle 7
        hs, hs, hs, hs, -hs, hs, hs, -hs, -hs, // triangle 8

        // Top face (y = hs)
        -hs, hs, -hs, -hs, hs, hs, hs, hs, hs, // triangle 9
        hs, hs, hs, hs, hs, -hs, -hs, hs, -hs, // triangle 10

        // Bottom face (y = -hs)
        -hs, -hs, -hs, hs, -hs, -hs, hs, -hs, hs, // triangle 11
        hs, -hs, hs, -hs, -hs, hs, -hs, -hs, -hs // triangle 12
    };

    private int loadRocketMask(String path) {
        try (InputStream stream = RenderEntityTest.class.getResourceAsStream(path)) {
            BufferedImage image = ImageIO.read(stream);

            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);

            IntBuffer buffer = BufferUtils.createIntBuffer(pixels.length);
            buffer.put(pixels);
            buffer.flip();

            int texture = GL11.glGenTextures();

            if (texture == 0) return 0;

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            // skip conversion from argb to rgba by using GL_BGRA and just using the REV (reverse) version of uint 8888
            // so it reads the int correctly
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                width,
                height,
                0,
                GL12.GL_BGRA,
                GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                buffer);

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            return texture;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
