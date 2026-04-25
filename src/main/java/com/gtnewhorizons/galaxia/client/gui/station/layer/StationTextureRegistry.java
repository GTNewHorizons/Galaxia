package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public final class StationTextureRegistry {

    public enum ConnectorKind {

        HORIZONTAL("horizontal"),
        VERTICAL("vertical");

        private final String textureName;

        ConnectorKind(String textureName) {
            this.textureName = textureName;
        }
    }

    private static final String DOMAIN = "galaxia";
    private static final String MODULE_BASE = "textures/gui/station/modules/";
    private static final String CONNECTOR_BASE = "textures/gui/station/connectors/";

    private static final Map<FacilityModuleKind, ResourceLocation> moduleTextures = new EnumMap<>(
        FacilityModuleKind.class);
    private static final Map<ConnectorKind, ResourceLocation> connectorTextures = new EnumMap<>(ConnectorKind.class);

    static {
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            moduleTextures.put(
                kind,
                new ResourceLocation(
                    DOMAIN,
                    MODULE_BASE + kind.name()
                        .toLowerCase() + ".png"));
        }
        for (ConnectorKind kind : ConnectorKind.values()) {
            connectorTextures.put(kind, new ResourceLocation(DOMAIN, CONNECTOR_BASE + kind.textureName + ".png"));
        }
    }

    private StationTextureRegistry() {}

    @Nullable
    public static ResourceLocation moduleTexture(FacilityModuleKind kind) {
        return moduleTextures.get(kind);
    }

    @Nullable
    public static ResourceLocation connectorTexture(ConnectorKind kind) {
        return connectorTextures.get(kind);
    }

    private static final Map<String, Boolean> textureExistsCache = new java.util.HashMap<>();

    public static boolean hasTexture(@Nullable ResourceLocation location) {
        if (location == null) return false;
        return textureExistsCache.computeIfAbsent(location.toString(), key -> {
            try {
                Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(location);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }
}
