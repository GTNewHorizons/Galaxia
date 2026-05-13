package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketEditorUI;

public class TileEntitySilo extends TileEntity {

    private RocketBlueprint blueprint = new RocketBlueprint();

    public RocketBlueprint getBlueprint() {
        return blueprint;
    }

    public void setBlueprint(RocketBlueprint bp) {
        this.blueprint = bp != null ? bp : new RocketBlueprint();
        markDirty();
    }

    public void openEditor(EntityPlayer player) {
        if (!worldObj.isRemote) {
            new RocketEditorUI(blueprint, this).open(player);
        }
    }

    public void launch(EntityPlayer player) {
        if (worldObj.isRemote) return;
        RocketAssembly assembly = blueprint.analyze();
        if (!assembly.viable()) return;

        EntityRocket rocket = new EntityRocket(worldObj);
        rocket.setBlueprint(blueprint.copy());
        rocket.setPosition(xCoord + 0.5, yCoord + 1, zCoord + 0.5);
        worldObj.spawnEntityInWorld(rocket);
        rocket.launch();

        // Clear for next build
        blueprint = new RocketBlueprint();
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        blueprint = RocketBlueprint.deserializeNBT(tag.getCompoundTag("blueprint"), RocketPartRegistry.instance());
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("blueprint", blueprint.serializeNBT());
    }
}
