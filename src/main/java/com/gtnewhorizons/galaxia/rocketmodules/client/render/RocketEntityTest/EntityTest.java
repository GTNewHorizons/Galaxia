package com.gtnewhorizons.galaxia.rocketmodules.client.render.RocketEntityTest;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import com.gtnewhorizons.galaxia.core.ShaderHelper;

public class EntityTest extends Entity {

    public static int program = 0;
    public static int cellProgram = 0;
    public static int uProgram = 0;
    public static int vProgram = 0;
    public static int wProgram = 0;
    public static int projectionProgram = 0;
    public static int flagProgram = 0;
    public static int toCellUProgram = 0;
    public static int toCellVProgram = 0;
    public static int toCellWProgram = 0;
    public static int cellNormUProgram = 0;
    public static int cellNormVProgram = 0;
    public static int cellNormWProgram = 0;
    public static int cellFillProgram = 0;
    public static int cellFluidFillProgram = 0;
    public static int toParticleUProgram = 0;
    public static int toParticleVProgram = 0;
    public static int toParticleWProgram = 0;
    public static int restoreSolidUProgram = 0;
    public static int restoreSolidVProgram = 0;
    public static int restoreSolidWProgram = 0;
    public static int blellochUpProgram = 0;
    public static int blellochDownProgram = 0;
    public static int countFillProgram = 0;
    public static int particleSeparationProgram = 0;

    public static int moveProgram = 0;
    public static int exhaustProgram = 0;

    public int ssboOUT;
    public int ssboU;
    public int ssboV;
    public int ssboW;
    public int ssboS;
    public int ssboScalar;
    public int ssboParticle;
    public int ssboParticleCount;
    public int ssboUW;
    public int ssboVW;
    public int ssboWW;
    public int ssboOUTCount;
    public int ssboDebug;
    public int ssboCount;
    public int ssboSortedParticle;

    final int width = 10 * 8;
    final int height = 8 * 12;
    final float spacing = 0.5f;
    final int cubeLength = (int) (1.0f / spacing);
    final int perCube = cubeLength * cubeLength * cubeLength;
    final int maxWidth = width * cubeLength;
    final int maxHeight = height * cubeLength;
    final int wxh = maxWidth * maxHeight;
    final int particleXSize = 128;
    final int particleYSize = 64;
    final int particleZSize = 64;

    public static final ByteBuffer zero = BufferUtils.createByteBuffer(4);

    float time = 0;

    public EntityTest(World world) {
        super(world);
        this.noClip = true;
        this.setSize(1.0F, 1.0F);
    }

    @Override
    protected void entityInit() {
        if (worldObj.isRemote) {
            createPrograms();

            // generate SSBO
            genSSBOs();

            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssboOUT);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, ssboU);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, ssboV);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, ssboW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 4, ssboS);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 5, ssboScalar);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 6, ssboParticle);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, ssboParticleCount);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 8, ssboUW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 9, ssboVW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 10, ssboWW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 11, ssboOUTCount);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 12, ssboDebug);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 13, ssboCount);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 14, ssboSortedParticle);

            GL42.glBindImageTexture(2, RenderEntityTest.rocketmask1, 0, false, 0, GL15.GL_READ_ONLY, GL11.GL_RGBA8);

            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

            // initialise all cells, UVW and S arrays
            runCellInit();
        }
    }

    public void runCellInit() {
        // instantiate base cells
        GL20.glUseProgram(cellProgram);
        // setting input variables, effectively (maxWidth = cubeLength * width)
        GL20.glUniform1i(GL20.glGetUniformLocation(cellProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellProgram, "maxHeight"), maxHeight);
        // dispatch across all cells (8x8x4 local, h = 2)
        GL43.glDispatchCompute(width / 4, height / 4, width / 2);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT); // wait for completion

        // instantiate U velocity data
        GL20.glUseProgram(uProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(uProgram, "maxHeight"), maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(uProgram, "dX"), maxWidth + 1);
        GL43.glDispatchCompute(width + 1, height, width); // overall MAC grid is [X+1, Y+1, Z+1]

        // instantiate V velocity data
        GL20.glUseProgram(vProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(vProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(vProgram, "dY"), maxHeight + 1);
        GL43.glDispatchCompute(width, height + 1, width);

        // instantiate W velocity data
        GL20.glUseProgram(wProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(wProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(wProgram, "maxHeight"), maxHeight);
        GL43.glDispatchCompute(width, height, width + 1);
    }

    @Override
    public void onUpdate() {
        if (worldObj.isRemote) {
            time += 0.05f;

            compute();
        }
    }

    private void compute() {

        runExhaust(); // add particles from exhaust

        moveParticles(); // move particles dT (0.05 because 20tps)

        separateParticles();

        resetCellVelocities(); // set all velocities and their weights to 0

        // set all cells to either solid or air

        refillCells();

        // then for each particle, if its cell is an air cell, make it a fluid cell

        fillFluidCells();

        // then save the flags for each fluid cell on what the surrounding cells are, to minimise repeated global reads

        createCellFlags();

        transferToCells();

        project();

        transferToParticles();

        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, 1021);

        GL20.glUseProgram(program);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboOUTCount);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssboOUT);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL20.glUniform1i(GL20.glGetUniformLocation(program, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "maxHeight"), maxHeight);

        GL43.glDispatchCompute(maxWidth / 32, maxHeight / 4, maxWidth / 4);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);

        GL20.glUseProgram(0);
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        System.out.println(GL33.glGetQueryObjectui64(1021, GL15.GL_QUERY_RESULT));
    }

    private void separateParticles() {
        final int localParticleX = 8;
        final int localParticleY = 8;
        final int localParticleZ = 8;

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

        // first we compute prefix sums of our counted particles

        // the particle hashing counts are zeroed before the movement process where we write to the count array

        // count is sparsely placed at its exact index, therefore we ALWAYS need to prefix sum 2^23 (the closest power of 2 to our grid size)
        int countInt = 8388608; // 2^23
        int maxInt = width * width * height * perCube;

        // upsweep
        GL20.glUseProgram(blellochUpProgram);

        for (int stride = 1; stride < countInt; stride *= 2) {
            GL30.glUniform1ui(GL20.glGetUniformLocation(blellochUpProgram, "stride"), stride);
            GL30.glUniform1ui(GL20.glGetUniformLocation(blellochUpProgram, "countInt"), countInt);

            // total threads required is half of countInt at stride = 1, divide by 256 because local workgroup is 256
            GL43.glDispatchCompute(Math.max(1, (countInt / (stride * 2)) / 256), 1, 1);

            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        }

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboCount);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, (countInt - 1) * 4L, zero); // multiply by 4 because 4 bytes per int

        // downsweep
        GL20.glUseProgram(blellochDownProgram);

        for (int stride = countInt / 2; stride >= 1; stride /= 2) {
            GL30.glUniform1ui(GL20.glGetUniformLocation(blellochDownProgram, "stride"), stride);
            GL30.glUniform1ui(GL20.glGetUniformLocation(blellochDownProgram, "countInt"), countInt);

            // total threads required is initially 1 and grows until its countInt / 2 (stride = 1)
            GL43.glDispatchCompute(Math.max(1, (countInt / (stride * 2)) / 256), 1, 1);

            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        }

        // then we place the particles in the correct position so they are ordered by their hash (the prefix sums are used for this count sort)
        // This is The Part Where I Count Sort You.

        GL20.glUseProgram(countFillProgram);

        GL30.glUniform1ui(GL20.glGetUniformLocation(countFillProgram, "width"), particleXSize);
        GL30.glUniform1ui(GL20.glGetUniformLocation(countFillProgram, "height"), particleYSize);

        GL20.glUniform1i(GL20.glGetUniformLocation(countFillProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(countFillProgram, "maxHeight"), maxHeight);

        GL43.glDispatchCompute(particleXSize / localParticleX, particleYSize / localParticleY, particleZSize / localParticleZ);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        // then we separate all the particles in not O(N^2)!
        // i use a jacobi approach for collision resolution, the accumulation array is our existing non-sorted particles overwritten!

        GL20.glUseProgram(particleSeparationProgram);

        GL30.glUniform1ui(GL20.glGetUniformLocation(particleSeparationProgram, "width"), particleXSize);
        GL30.glUniform1ui(GL20.glGetUniformLocation(particleSeparationProgram, "height"), particleYSize);

        GL20.glUniform1i(GL20.glGetUniformLocation(particleSeparationProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(particleSeparationProgram, "maxHeight"), maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(particleSeparationProgram, "wxh"), maxWidth * maxHeight);

        // max int is used in a min() statement and not a >= statement so -1
        GL20.glUniform1i(GL20.glGetUniformLocation(particleSeparationProgram, "maxInt"), maxInt - 1);

        GL43.glDispatchCompute(particleXSize / localParticleX, particleYSize / localParticleY, particleZSize / localParticleZ);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void createCellFlags() {
        final int localX = 32;
        final int localY = 2;
        final int localZ = 2;

        GL20.glUseProgram(flagProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(flagProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(flagProgram, "maxHeight"), maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(flagProgram, "wxh"), maxWidth * maxHeight);

        GL43.glDispatchCompute(maxWidth / localX, maxHeight / localY, maxWidth / localZ);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void fillFluidCells() {
        final int localParticleX = 8;
        final int localParticleY = 8;
        final int localParticleZ = 8;

        GL20.glUseProgram(cellFluidFillProgram);

        GL30.glUniform1ui(GL20.glGetUniformLocation(cellFluidFillProgram, "width"), particleXSize);
        GL30.glUniform1ui(GL20.glGetUniformLocation(cellFluidFillProgram, "height"), particleYSize);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellFluidFillProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellFluidFillProgram, "maxHeight"), maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellFluidFillProgram, "wxh"), wxh);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboS);

        GL43.glDispatchCompute(particleXSize / localParticleX, particleYSize / localParticleY, particleZSize / localParticleZ);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void refillCells() {
        final int localX = 32;
        final int localY = 4;
        final int localZ = 4;

        GL20.glUseProgram(cellFillProgram);

        GL30.glUniform1ui(GL20.glGetUniformLocation(cellFillProgram, "maxWidth"), maxWidth);
        GL30.glUniform1ui(GL20.glGetUniformLocation(cellFillProgram, "maxHeight"), maxHeight);
        GL30.glUniform1ui(GL20.glGetUniformLocation(cellFillProgram, "wxh"), wxh);

        GL43.glDispatchCompute(maxWidth / localX, maxHeight / localY, maxWidth / localZ);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void project() {
        final int localX = 80;
        final int localY = 1;
        final int localZ = 1;

        GL20.glUseProgram(projectionProgram);

        int rb = GL20.glGetUniformLocation(projectionProgram, "RB"); // our red-black differentiator

        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "maxHeight"), maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "maxWidthDPos"), (maxWidth) + 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "maxHeightDPos"), (maxHeight) + 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "wxh"), maxWidth * maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "w1xh"), ((maxWidth) + 1) * (maxHeight));
        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "wxh1"), (maxWidth) * ((maxHeight) + 1));

        for (int i = 0; i < 20; i++) {
            GL20.glUniform1i(rb, 0);

            GL43.glDispatchCompute(maxWidth / localX * 2, maxHeight / localY, maxWidth / localZ);

            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

            GL20.glUniform1i(rb, 1);

            GL43.glDispatchCompute(maxWidth / localX * 2, maxHeight / localY, maxWidth / localZ);

            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        }
    }

    private void transferToParticles() {
        final int localParticleX = 8;
        final int localParticleY = 8;
        final int localParticleZ = 8;

        // 3 different programs for each component
        GL20.glUseProgram(toParticleUProgram);

        // same thread width as before for particles
        GL30.glUniform1ui(GL20.glGetUniformLocation(toParticleUProgram, "width"), particleXSize);
        GL30.glUniform1ui(GL20.glGetUniformLocation(toParticleUProgram, "height"), particleYSize);

        // various uniforms to be precomputed outside the loop (more details in the shader code)
        GL20.glUniform1i(GL20.glGetUniformLocation(toParticleUProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(toParticleUProgram, "maxHeight"), maxHeight);

        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleUProgram, "maxWidthDNeg"), (maxWidth) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleUProgram, "maxHeightDNeg"), (maxHeight) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleUProgram, "maxWidthDPos"), (maxWidth) + 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleUProgram, "wxh"), ((maxWidth) + 1) * (maxHeight));

        GL43.glDispatchCompute(particleXSize / localParticleX, particleYSize / localParticleY, particleZSize / localParticleZ);


        GL20.glUseProgram(toParticleVProgram);

        GL30.glUniform1ui(GL20.glGetUniformLocation(toParticleVProgram, "width"), particleXSize);
        GL30.glUniform1ui(GL20.glGetUniformLocation(toParticleVProgram, "height"), particleYSize);

        GL20.glUniform1i(GL20.glGetUniformLocation(toParticleVProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(toParticleVProgram, "maxHeight"), maxHeight);

        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleVProgram, "maxWidthDNeg"), (maxWidth) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleVProgram, "maxHeightDNeg"), (maxHeight) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleVProgram, "maxHeightDPos"), (maxHeight) + 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleVProgram, "wxh"), (maxWidth) * ((maxHeight) + 1));

        GL43.glDispatchCompute(particleXSize / localParticleX, particleYSize / localParticleY, particleZSize / localParticleZ);


        GL20.glUseProgram(toParticleWProgram);

        GL30.glUniform1ui(GL20.glGetUniformLocation(toParticleWProgram, "width"), particleXSize);
        GL30.glUniform1ui(GL20.glGetUniformLocation(toParticleWProgram, "height"), particleYSize);

        GL20.glUniform1i(GL20.glGetUniformLocation(toParticleWProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(toParticleWProgram, "maxHeight"), maxHeight);

        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleWProgram, "maxWidthDNeg"), (maxWidth) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleWProgram, "maxHeightDNeg"), (maxHeight) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toParticleWProgram, "wxh"), wxh);

        GL43.glDispatchCompute(particleXSize / localParticleX, particleYSize / localParticleY, particleZSize / localParticleZ);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void transferToCells() {
        final int localParticleX = 8;
        final int localParticleY = 8;
        final int localParticleZ = 8;

        // 3 different programs for each component
        GL20.glUseProgram(toCellUProgram);

        // same thread width as before for particles
        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellUProgram, "width"), particleXSize);
        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellUProgram, "height"), particleYSize);

        // various uniforms to be precomputed outside the loop (more details in the shader code)
        GL20.glUniform1i(GL20.glGetUniformLocation(toCellUProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(toCellUProgram, "maxHeight"), maxHeight);

        GL20.glUniform1f(GL20.glGetUniformLocation(toCellUProgram, "maxWidthDNeg"), (maxWidth) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellUProgram, "maxHeightDNeg"), (maxHeight) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellUProgram, "maxWidthDPos"), (maxWidth) + 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellUProgram, "wxh"), ((maxWidth) + 1) * (maxHeight));

        GL43.glDispatchCompute(particleXSize / localParticleX, particleYSize / localParticleY, particleZSize / localParticleZ);


        GL20.glUseProgram(toCellVProgram);

        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellVProgram, "width"), particleXSize);
        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellVProgram, "height"), particleYSize);

        GL20.glUniform1i(GL20.glGetUniformLocation(toCellVProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(toCellVProgram, "maxHeight"), maxHeight);

        GL20.glUniform1f(GL20.glGetUniformLocation(toCellVProgram, "maxWidthDNeg"), (maxWidth) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellVProgram, "maxHeightDNeg"), (maxHeight) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellVProgram, "maxHeightDPos"), (maxHeight) + 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellVProgram, "wxh"), (maxWidth) * ((maxHeight) + 1));

        GL43.glDispatchCompute(particleXSize / localParticleX, particleYSize / localParticleY, particleZSize / localParticleZ);


        GL20.glUseProgram(toCellWProgram);

        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellWProgram, "width"), particleXSize);
        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellWProgram, "height"), particleYSize);

        GL20.glUniform1i(GL20.glGetUniformLocation(toCellWProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(toCellWProgram, "maxHeight"), maxHeight);

        GL20.glUniform1f(GL20.glGetUniformLocation(toCellWProgram, "maxWidthDNeg"), (maxWidth) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellWProgram, "maxHeightDNeg"), (maxHeight) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellWProgram, "wxh"), wxh);

        GL43.glDispatchCompute(particleXSize / localParticleX, particleYSize / localParticleY, particleZSize / localParticleZ);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        final int localX = 32;
        final int localY = 4;
        final int localZ = 4;

        // distribute the velocity so that weight = 1 per cell
        GL20.glUseProgram(cellNormUProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormUProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormUProgram, "maxHeight"), maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormUProgram, "w1xh"), ((maxWidth) + 1) * (maxHeight));
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormUProgram, "maxWidthDPos"), (maxWidth) + 1);
        GL43.glDispatchCompute((maxWidth / localX) + 1, maxHeight / localY, maxWidth / localZ);


        GL20.glUseProgram(cellNormVProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormVProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormVProgram, "maxHeight"), (maxHeight) + 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormVProgram, "wxh"), (maxWidth) * ((maxHeight) + 1));
        GL43.glDispatchCompute(maxWidth / localX, (maxHeight / localY) + 1, maxWidth / localZ);


        GL20.glUseProgram(cellNormWProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormWProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormWProgram, "maxHeight"), maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormWProgram, "wxh"), wxh);
        GL43.glDispatchCompute(maxWidth / localX, maxHeight / localY, (maxWidth / localZ) + 1);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        // transferring to cells does not respect the solid cells being solid, we have to strip the velocities from them

        GL20.glUseProgram(restoreSolidUProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidUProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidUProgram, "maxHeight"), maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidUProgram, "wxh"), wxh);
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidUProgram, "w1xh"), ((maxWidth) + 1) * (maxHeight));
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidUProgram, "maxWidthDPos"), (maxWidth) + 1);
        GL43.glDispatchCompute((maxWidth / localX) + 1, maxHeight / localY, maxWidth / localZ);


        GL20.glUseProgram(restoreSolidVProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidVProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidVProgram, "maxHeight"), maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidVProgram, "wxh"), wxh);
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidVProgram, "wxh1"), (maxWidth) * ((maxHeight) + 1));
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidVProgram, "maxHeightDPos"), (maxHeight) + 1);
        GL43.glDispatchCompute(maxWidth / localX, (maxHeight / localY) + 1, maxWidth / localZ);


        GL20.glUseProgram(restoreSolidWProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidWProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidWProgram, "maxHeight"), maxHeight);
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidWProgram, "wxh"), wxh);
        GL20.glUniform1i(GL20.glGetUniformLocation(restoreSolidWProgram, "maxWidthDPos"), (maxWidth) + 1);
        GL43.glDispatchCompute(maxWidth / localX, maxHeight / localY, (maxWidth / localZ) + 1);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void moveParticles() {
        final int localParticleX = 8;
        final int localParticleY = 8;
        final int localParticleZ = 8;

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboCount);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32I, GL30.GL_RED_INTEGER, GL11.GL_INT, zero); // zero the hashing counts before we write to them

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

        GL20.glUseProgram(moveProgram);

        // TODO: rewrite this old comment under here
        // passing precomputed thread width and height values,
        // I dispatch x = 10 and y = 10. internally the local size of
        // the shader is 8x8x8, so thread width = 10 * 8
        // and thread height = 10 * 8
        GL30.glUniform1ui(GL20.glGetUniformLocation(moveProgram, "width"), particleXSize);
        GL30.glUniform1ui(GL20.glGetUniformLocation(moveProgram, "height"), particleYSize);

        GL20.glUniform1i(GL20.glGetUniformLocation(moveProgram, "maxWidth"), maxWidth);
        GL20.glUniform1i(GL20.glGetUniformLocation(moveProgram, "maxHeight"), maxHeight);

        // unknown array size (local 8x8x8, total 512000 max particles)
        GL43.glDispatchCompute(particleXSize / localParticleX, particleYSize / localParticleY, particleZSize / localParticleZ);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void runExhaust() {
        GL20.glUseProgram(exhaustProgram);

        // currently arbitrary 50, when the movement tracking is added based on a start pos, this needs to move with it
        GL20.glUniform1f(GL20.glGetUniformLocation(exhaustProgram, "xPos"), 40);
        GL20.glUniform1f(GL20.glGetUniformLocation(exhaustProgram, "yPos"), 80);
        GL20.glUniform1f(GL20.glGetUniformLocation(exhaustProgram, "zPos"), 40);

        GL20.glUniform1f(GL20.glGetUniformLocation(exhaustProgram, "seed"), (float)Math.random());

        // just a serial program ran on the gpu, could parallelize later if needed
        GL43.glDispatchCompute(1, 1, 1);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    public void genSSBOs() {
        ssboOUT = GL15.glGenBuffers();
        ssboU = GL15.glGenBuffers();
        ssboV = GL15.glGenBuffers();
        ssboW = GL15.glGenBuffers();
        ssboS = GL15.glGenBuffers();
        ssboScalar = GL15.glGenBuffers();
        ssboParticle = GL15.glGenBuffers();
        ssboParticleCount = GL15.glGenBuffers();
        ssboUW = GL15.glGenBuffers();
        ssboVW = GL15.glGenBuffers();
        ssboWW = GL15.glGenBuffers();
        ssboOUTCount = GL15.glGenBuffers();
        ssboDebug = GL15.glGenBuffers();
        ssboCount = GL15.glGenBuffers();
        ssboSortedParticle = GL15.glGenBuffers();

        // spotless:off

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboOUT);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
            width * height * width * 3 * 4,
            GL15.GL_DYNAMIC_DRAW); // max cubes sent to render


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboU);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            (width + 1) * width * height * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // MAC grid U


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboV);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * (width + 1) * height * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // MAC grid V


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboW);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * (height + 1) * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // MAC grid W


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboS);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * height * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // cell s value


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboScalar);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
            width * height * width * perCube * 2 * 4,
            GL15.GL_DYNAMIC_COPY); // cell data


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboParticle);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            particleXSize * particleYSize * particleZSize * 6 * 4, // 2^19 (524288) max particles
            GL15.GL_DYNAMIC_COPY); // particle data

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboParticleCount);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            4,
            GL15.GL_DYNAMIC_COPY); // particle count

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboUW);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            (width + 1) * width * height * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // weights for U


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboVW);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * (width + 1) * height * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // weights for V


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboWW);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * (height + 1) * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // weights for W

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboOUTCount);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            4,
            GL15.GL_DYNAMIC_COPY); // number of cells to draw

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboDebug);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * height * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // debug data

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboCount);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            8388608 * 4 + 4, // each cell has its own count of particles, it also needs to be the nearest power of 2 for the blelloch scan (one extra entry for bound safety)
            GL15.GL_DYNAMIC_COPY); // particle count data

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboSortedParticle);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            particleXSize * particleYSize * particleZSize * 6 * 4, // 2^19 (524288) max particles
            GL15.GL_DYNAMIC_COPY); // particle count data

        // spotless:on
    }

    private static void createPrograms() {
        if (program == 0) {
            program = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/test.comp");
        }
        if (cellProgram == 0) {
            cellProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cellinit.comp");
        }
        if (projectionProgram == 0) {
            projectionProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/projection.comp");
        }
        if (uProgram == 0) {
            uProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/uinit.comp");
        }
        if (vProgram == 0) {
            vProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/vinit.comp");
        }
        if (wProgram == 0) {
            wProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/winit.comp");
        }
        if (flagProgram == 0) {
            flagProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_flags.comp");
        }
        if (moveProgram == 0) {
            moveProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_move.comp");
        }
        if (exhaustProgram == 0) {
            exhaustProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_exhaust.comp");
        }
        if (toCellUProgram == 0) {
            toCellUProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_to_cell_u.comp");
        }
        if (toCellVProgram == 0) {
            toCellVProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_to_cell_v.comp");
        }
        if (toCellWProgram == 0) {
            toCellWProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_to_cell_w.comp");
        }
        if (cellNormUProgram == 0) {
            cellNormUProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_normalization_u.comp");
        }
        if (cellNormVProgram == 0) {
            cellNormVProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_normalization_v.comp");
        }
        if (cellNormWProgram == 0) {
            cellNormWProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_normalization_w.comp");
        }
        if (cellFillProgram == 0) {
            cellFillProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_fill.comp");
        }
        if (cellFluidFillProgram == 0) {
            cellFluidFillProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_fluid_fill.comp");
        }
        if (toParticleUProgram == 0) {
            toParticleUProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_to_particle_u.comp");
        }
        if (toParticleVProgram == 0) {
            toParticleVProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_to_particle_v.comp");
        }
        if (toParticleWProgram == 0) {
            toParticleWProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_to_particle_w.comp");
        }
        if (restoreSolidUProgram == 0) {
            restoreSolidUProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/restore_solid_cell_u.comp");
        }
        if (restoreSolidVProgram == 0) {
            restoreSolidVProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/restore_solid_cell_v.comp");
        }
        if (restoreSolidWProgram == 0) {
            restoreSolidWProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/restore_solid_cell_w.comp");
        }
        if (blellochUpProgram == 0) {
            blellochUpProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/blelloch_upsweep.comp");
        }
        if (blellochDownProgram == 0) {
            blellochDownProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/blelloch_downsweep.comp");
        }
        if (countFillProgram == 0) {
            countFillProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/count_fill.comp");
        }
        if (particleSeparationProgram == 0) {
            particleSeparationProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_separation.comp");
        }
    }

    public void resetCellVelocities() {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboU);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboV);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboW);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboUW);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboVW);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboWW);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboDebug);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tagCompund) {

    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tagCompound) {

    }
}
