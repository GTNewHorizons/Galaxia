package com.gtnewhorizons.galaxia.mixin.late.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import gregtech.api.interfaces.tileentity.IMachineBlockUpdateable;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.galaxia.registry.block.tile.TileMachine")
public abstract class MixinTileMachine implements IMachineBlockUpdateable {
}
