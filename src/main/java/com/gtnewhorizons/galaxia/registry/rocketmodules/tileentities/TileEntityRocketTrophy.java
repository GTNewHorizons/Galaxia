package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;

public class TileEntityRocketTrophy extends TileEntity {

    private ItemStack schematic = null;
    private RocketBlueprint cachedBlueprint = null;

    private float offsetX = 0f;
    private float offsetY = 1f;
    private float offsetZ = 0f;
    private float yaw = 0f;
    private float pitch = 0f;
    private float scale = 1f;

    public ItemStack getSchematic() {
        return schematic;
    }

    public void setSchematic(ItemStack stack) {
        this.schematic = stack != null ? stack.copy() : null;
        this.cachedBlueprint = null;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public RocketBlueprint getBlueprint() {
        if (cachedBlueprint == null && schematic != null) {
            cachedBlueprint = ItemRocketSchematic.getBlueprint(schematic);
        }
        return cachedBlueprint != null ? cachedBlueprint : new RocketBlueprint();
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public float getOffsetZ() {
        return offsetZ;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getScale() {
        return scale;
    }

    public void setOffsetX(float offsetX) {
        this.offsetX = offsetX;
    }

    public void setOffsetY(float offsetY) {
        this.offsetY = offsetY;
    }

    public void setOffsetZ(float offsetZ) {
        this.offsetZ = offsetZ;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);

        tag.setFloat("offsetX", offsetX);
        tag.setFloat("offsetY", offsetY);
        tag.setFloat("offsetZ", offsetZ);
        tag.setFloat("pitch", pitch);
        tag.setFloat("yaw", yaw);
        tag.setFloat("scale", scale);

        if (schematic != null) {
            NBTTagCompound schematicTag = new NBTTagCompound();
            schematic.writeToNBT(schematicTag);
            tag.setTag("schematic", schematicTag);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        offsetX = tag.getFloat("offsetX");
        offsetY = tag.getFloat("offsetY");
        offsetZ = tag.getFloat("offsetZ");
        pitch = tag.getFloat("pitch");
        yaw = tag.getFloat("yaw");
        scale = tag.getFloat("scale");

        if (tag.hasKey("schematic")) {
            schematic = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("schematic"));
        } else {
            schematic = null;
        }
        cachedBlueprint = null;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }
}
