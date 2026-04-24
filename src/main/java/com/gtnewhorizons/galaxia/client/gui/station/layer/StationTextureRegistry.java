package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public final class StationTextureRegistry {

    private static final String DOMAIN = "galaxia";
    private static final String MODULE_BASE = "textures/gui/station/modules/";
    private static final String CONNECTOR_BASE = "textures/gui/station/connectors/";

    private static final Map<FacilityModuleKind, ResourceLocation> moduleTextures = new EnumMap<>(
        FacilityModuleKind.class);
    private static final Map<String, ResourceLocation> connectorTextures = new java.util.HashMap<>();

    static {
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            moduleTextures.put(
                kind,
                new ResourceLocation(
                    DOMAIN,
                    MODULE_BASE + kind.name()
                        .toLowerCase() + ".png"));
        }
        connectorTextures.put("horizontal", new ResourceLocation(DOMAIN, CONNECTOR_BASE + "horizontal.png"));
        connectorTextures.put("vertical", new ResourceLocation(DOMAIN, CONNECTOR_BASE + "vertical.png"));
    }

    private StationTextureRegistry() {}

    @Nullable
    public static ResourceLocation moduleTexture(FacilityModuleKind kind) {
        return moduleTextures.get(kind);
    }

    @Nullable
    public static ResourceLocation connectorHorizontal() {
        return connectorTextures.get("horizontal");
    }

    @Nullable
    public static ResourceLocation connectorVertical() {
        return connectorTextures.get("vertical");
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
