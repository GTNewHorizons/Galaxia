package com.gtnewhorizons.galaxia.gametests;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;

import javax.imageio.ImageIO;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterDeposits;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterSize;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

@GameTestHolder(value = "galaxia", clientOnly = true)
public final class CraterTerrainGameTests {

    private CraterTerrainGameTests() {}

    /** Natural lunar terrain must expose debris for every size class, including the large crater rim. */
    @GameTest(timeoutTicks = 3000)
    public static void naturalLunarRimsAcrossSizes(GameTestHelper helper) {
        TerrainSample sample = new TerrainSample();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(2400)
            .awaitServer("generate a fresh lunar terrain sample", sample::generate)
            .server("inspect natural crater rims and export the terrain map", () -> sample.inspect(helper))
            .succeed();
    }

    private static final class TerrainSample {

        private static final int START = 8192;
        private static final int WIDTH = 1024;
        private int row;
        private World world;

        void generate() {
            world = MinecraftServer.getServer()
                .worldServerForDimension(DimensionEnum.MOON.getId());
            int firstChunk = START >> 4;
            for (int cx = firstChunk; cx < firstChunk + WIDTH / 16; cx++) {
                world.getChunkProvider()
                    .loadChunk(cx, firstChunk + row);
            }
            if (++row < WIDTH / 16) throw new AssertionError("Lunar sample generation is still in progress");
        }

        void inspect(GameTestHelper helper) {
            // Remote sample chunks can unload between generation ticks. Load the sample for this inspection step.
            for (int cx = START >> 4; cx < (START + WIDTH) >> 4; cx++) {
                for (int cz = START >> 4; cz < (START + WIDTH) >> 4; cz++) {
                    world.getChunkProvider()
                        .loadChunk(cx, cz);
                }
            }
            var counts = new EnumMap<CraterSize, Integer>(CraterSize.class);
            NBTTagCompound saved = new NBTTagCompound();
            CraterDeposits.get(world)
                .writeToNBT(saved);
            var deposits = saved.getTagList("deposits", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < deposits.tagCount(); i++) {
                var crater = deposits.getCompoundTagAt(i);
                int x = crater.getInteger("x"), z = crater.getInteger("z");
                int radius = (crater.getInteger("diameter") + 1) / 2;
                if (x - radius < START + 16 || z - radius < START + 16
                    || x + radius >= START + WIDTH - 16
                    || z + radius >= START + WIDTH - 16) continue;
                CraterSize size = CraterSize.valueOf(crater.getString("size"));
                int debris = 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz > radius * radius) continue;
                        int y = world.getHeightValue(x + dx, z + dz) - 1;
                        if (world.getBlock(x + dx, y, z + dz) == PlanetBlocks.MOON_TEKTITE) debris++;
                    }
                }
                helper.recordDiagnostic(
                    size + " crater "
                        + crater.getString("id")
                        + ", diameter "
                        + crater.getInteger("diameter")
                        + ", visible tektite blocks "
                        + debris);
                if (debris > 0) counts.merge(size, 1, Integer::sum);
            }
            exportMap(helper);
            for (CraterSize size : CraterSize.values()) {
                if (!counts.containsKey(size))
                    throw new AssertionError("No natural crater with visible rim in class " + size);
            }
        }

        void exportMap(GameTestHelper helper) {
            BufferedImage image = new BufferedImage(WIDTH, WIDTH, BufferedImage.TYPE_INT_RGB);
            for (int dx = 0; dx < WIDTH; dx++) {
                for (int dz = 0; dz < WIDTH; dz++) {
                    int x = START + dx, z = START + dz;
                    int y = world.getHeightValue(x, z) - 1;
                    int shade = Math.min(210, Math.max(20, y));
                    int rgb = world.getBlock(x, y, z) == PlanetBlocks.MOON_TEKTITE ? 0xe8f0ff
                        : shade << 16 | shade << 8 | shade;
                    image.setRGB(dx, dz, rgb);
                }
            }
            File output = new File("lunar-natural-terrain.png");
            try {
                if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
            } catch (IOException e) {
                throw new AssertionError("Cannot export lunar terrain sample", e);
            }
            helper.recordDiagnostic("Natural terrain map: " + output.getAbsolutePath());
        }
    }
}
