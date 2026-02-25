package com.gtnewhorizons.galaxia.registry.entity.rocket;

import net.minecraft.nbt.NBTTagCompound;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record ModuleData(ModuleType type) {

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setString("type", type.name());
    }

    public static ModuleData readFromNBT(NBTTagCompound nbt) {
        return new ModuleData(ModuleType.valueOf(nbt.getString("type")));
    }
}
