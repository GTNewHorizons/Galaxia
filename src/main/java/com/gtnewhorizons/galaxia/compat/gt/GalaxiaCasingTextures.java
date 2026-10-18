package com.gtnewhorizons.galaxia.compat.gt;

import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;

import gregtech.api.enums.Textures;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

/**
 * Galaxia casings registered as GT casing textures, so GT hatches built into our multiblocks show the block around
 * them instead of a GT casing.
 * <p>
 * GT splits casing textures into pages of 128, Galaxia owns page {@value #PAGE}
 */
public final class GalaxiaCasingTextures {

    public static final int PAGE = 64;

    public static final int LAUNCHPAD_SHEETING = (PAGE << 7);
    public static final int RUSTY_PANEL = (PAGE << 7) + 1;

    private GalaxiaCasingTextures() {}

    public static void register() {
        GTUtility.addTexturePage((byte) PAGE);
        Textures.BlockIcons.setCasingTextureForId(
            LAUNCHPAD_SHEETING,
            TextureFactory.of(GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(), 0));
        Textures.BlockIcons
            .setCasingTextureForId(RUSTY_PANEL, TextureFactory.of(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0));
    }
}
