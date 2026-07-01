package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;

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
    private static final Map<FacilityModuleKind, Map<String, ResourceLocation>> moduleVariantTextures = new EnumMap<>(
        FacilityModuleKind.class);
    private static final Map<ConnectorKind, ResourceLocation> connectorTextures = new EnumMap<>(ConnectorKind.class);
    private static final Map<FacilityModuleKind, Map<ConnectorKind, ResourceLocation>> capacityConnectorTextures = new EnumMap<>(
        FacilityModuleKind.class);

    static {
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            moduleTextures.put(
                kind,
                moduleResource(
                    kind.name()
                        .toLowerCase()));
        }
        moduleVariantTextures
            .put(FacilityModuleKind.HAMMER, Map.of(HammerVariant.BIG.name(), moduleResource("big_hammer")));
        for (ConnectorKind kind : ConnectorKind.values()) {
            connectorTextures.put(kind, new ResourceLocation(DOMAIN, CONNECTOR_BASE + kind.textureName + ".png"));
        }
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            if (!kind.isCapacityModule()) continue;
            Map<ConnectorKind, ResourceLocation> textures = new EnumMap<>(ConnectorKind.class);
            for (ConnectorKind connectorKind : ConnectorKind.values()) {
                textures.put(
                    connectorKind,
                    new ResourceLocation(
                        DOMAIN,
                        CONNECTOR_BASE + kind.name()
                            .toLowerCase() + "_connector_" + connectorKind.textureName + ".png"));
            }
            capacityConnectorTextures.put(kind, textures);
        }
    }

    private StationTextureRegistry() {}

    @Nullable
    public static ResourceLocation moduleTexture(FacilityModuleKind kind) {
        return moduleTextures.get(kind);
    }

    @Nullable
    public static ResourceLocation moduleVariantTexture(FacilityModuleKind kind, String variantKey) {
        Map<String, ResourceLocation> textures = moduleVariantTextures.get(kind);
        return textures == null ? null : textures.get(variantKey);
    }

    public static Collection<ResourceLocation> moduleVariantTextures(FacilityModuleKind kind) {
        Map<String, ResourceLocation> textures = moduleVariantTextures.get(kind);
        return textures == null ? Collections.emptyList() : textures.values();
    }

    @Nullable
    public static ResourceLocation connectorTexture(ConnectorKind kind) {
        return connectorTextures.get(kind);
    }

    @Nullable
    public static ResourceLocation capacityConnectorTexture(FacilityModuleKind moduleKind,
        ConnectorKind connectorKind) {
        Map<ConnectorKind, ResourceLocation> textures = capacityConnectorTextures.get(moduleKind);
        return textures == null ? null : textures.get(connectorKind);
    }

    private static final Map<String, Boolean> textureExistsCache = new java.util.HashMap<>();

    private static ResourceLocation moduleResource(String textureName) {
        return new ResourceLocation(DOMAIN, MODULE_BASE + textureName + ".png");
    }

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
