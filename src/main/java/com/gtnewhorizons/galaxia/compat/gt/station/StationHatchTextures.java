package com.gtnewhorizons.galaxia.compat.gt.station;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_OUT;

import gregtech.GTMod;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/**
 * Faces of the station buses and hatches. They are built on the GT part of the opposite direction, since a station
 * input takes from its multiblock what goes into the station, so they show the pipe of the way things go for the player
 */
final class StationHatchTextures {

    private StationHatchTextures() {}

    /** A part things go into the station through, painted like a GT input */
    static ITexture[] input(ITexture base, byte color, IIconContainer sign) {
        ITexture coloredPipe = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return GTMod.proxy.mRenderIndicatorsOnHatch
            ? new ITexture[] { base, TextureFactory.of(OVERLAY_PIPE_IN), coloredPipe, TextureFactory.of(sign) }
            : new ITexture[] { base, TextureFactory.of(OVERLAY_PIPE_IN), coloredPipe };
    }

    /** A part things come out of the station through, painted like a GT output */
    static ITexture[] output(ITexture base, IIconContainer sign) {
        return GTMod.proxy.mRenderIndicatorsOnHatch
            ? new ITexture[] { base, TextureFactory.of(OVERLAY_PIPE_OUT), TextureFactory.of(sign) }
            : new ITexture[] { base, TextureFactory.of(OVERLAY_PIPE_OUT) };
    }
}
