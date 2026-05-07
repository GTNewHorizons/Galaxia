package com.gtnewhorizons.galaxia.mixins;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum GalaxiaMixins implements IMixins {

    GREGTECH_MACHINE_MIXIN(new MixinBuilder("Mixin for TileMachine to interact with GregTech machines")
        .addCommonMixins("gregtech.MixinTileMachine")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.GREGTECH));

    private final MixinBuilder builder;

    GalaxiaMixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @NotNull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
