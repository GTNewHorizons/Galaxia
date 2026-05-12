package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAnalyzer;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketEditorUI;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntitySilo extends TileEntity {
    private RocketBlueprint blueprint = new RocketBlueprint();

    public void setBlueprint(RocketBlueprint bp) { this.blueprint = bp; markDirty(); }
    public RocketBlueprint getBlueprint() { return blueprint; }

    public RocketAssembly getAssembly() {
        return RocketAnalyzer.analyze(blueprint);
    }

    public void openEditor(EntityPlayer player) {
        if (!worldObj.isRemote) {
            new RocketEditorUI(blueprint).open(player);
        }
    }

    public void launch(EntityPlayer player) {
        if (worldObj.isRemote) return;
        RocketAssembly assembly = getAssembly();
        if (!assembly.viable()) return;

        EntityRocket rocket = new EntityRocket(worldObj);
        rocket.setBlueprint(blueprint);
        rocket.setPosition(xCoord + 0.5, yCoord + 1, zCoord + 0.5);
        worldObj.spawnEntityInWorld(rocket);
        player.mountEntity(rocket);
        blueprint = new RocketBlueprint();
        markDirty();
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        blueprint = RocketBlueprint.deserialize(tag.getCompoundTag("blueprint"), RocketPartRegistry.instance());
    }
    @Override public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("blueprint", blueprint.serialize());
    }
}
