package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;

public final class PlanetaryFeatureOverlayRenderer {

    private static final TextureSize DEFAULT_TEXTURE_SIZE = new TextureSize(
        StationMapViewport.TILE_SIZE,
        StationMapViewport.TILE_SIZE);
    private static final Map<String, TextureSize> textureSizeCache = new HashMap<>();

    private PlanetaryFeatureOverlayRenderer() {}

    public static void draw(int tileX, int tileY, Iterable<PlanetaryFeatureKey> features) {
        if (features == null) return;
        List<PlanetaryFeatureDefinition> definitions = new ArrayList<>();
        for (PlanetaryFeatureKey key : features) {
            PlanetaryFeatureDefinition definition = PlanetaryFeatureRegistry.get(key);
            if (definition != null) definitions.add(definition);
        }
        definitions.sort(
            Comparator.comparingInt(
                definition -> definition.layer()
                    .drawOrder()));
        for (PlanetaryFeatureDefinition definition : definitions) {
            drawFeatureOverlay(tileX, tileY, definition.texture());
        }
    }

    public static void drawIcon(ResourceLocation texture, int x, int y, int size) {
        drawTexture(resolveTexture(texture), x, y, size);
    }

    public static void drawIcon(PlanetaryFeatureDefinition feature, int x, int y, int size) {
        if (feature == null) return;
        drawIcon(feature.texture(), x, y, size);
    }

    private static void drawFeatureOverlay(int tileX, int tileY, ResourceLocation requestedTexture) {
        ResourceLocation texture = resolveTexture(requestedTexture);
        TextureSize size = textureSize(texture);
        TileOverlay overlay = centeredOverlay(tileX, tileY, texture, size.width(), size.height());
        drawTexture(overlay.texture(), overlay.x(), overlay.y(), overlay.width(), overlay.height());
    }

    static TileOverlay centeredOverlay(int tileX, int tileY, ResourceLocation texture, int width, int height) {
        int x = tileX + (StationMapViewport.TILE_SIZE - width) / 2;
        int y = tileY + (StationMapViewport.TILE_SIZE - height) / 2;
        return new TileOverlay(x, y, width, height, texture);
    }

    private static ResourceLocation resolveTexture(ResourceLocation texture) {
        return texture != null && StationTextureRegistry.hasTexture(texture) ? texture
            : EnumTextures.ICON_MISSING.get();
    }

    private static void drawTexture(ResourceLocation texture, int x, int y, int size) {
        drawTexture(texture, x, y, size, size);
    }

    private static void drawTexture(ResourceLocation texture, int x, int y, int width, int height) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + height, 0, 0, 1);
        tess.addVertexWithUV(x + width, y + height, 0, 1, 1);
        tess.addVertexWithUV(x + width, y, 0, 1, 0);
        tess.addVertexWithUV(x, y, 0, 0, 0);
        tess.draw();
    }

    private static TextureSize textureSize(ResourceLocation texture) {
        if (texture == null) return DEFAULT_TEXTURE_SIZE;
        return textureSizeCache.computeIfAbsent(texture.toString(), key -> readTextureSize(texture));
    }

    private static TextureSize readTextureSize(ResourceLocation texture) {
        try (InputStream in = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(texture)
            .getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image != null) return new TextureSize(image.getWidth(), image.getHeight());
        } catch (Exception ignored) {}
        return DEFAULT_TEXTURE_SIZE;
    }

    record TileOverlay(int x, int y, int width, int height, ResourceLocation texture) {}

    private record TextureSize(int width, int height) {}
}
