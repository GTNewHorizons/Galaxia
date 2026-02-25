package com.gtnewhorizons.galaxia.registry.block.tileentities;

import com.gtnewhorizons.galaxia.registry.entity.rocket.EntityRocket;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class
TileEntitySilo extends TileEntity {
    private EntityRocket entityRocket;

    public int mods;
    public boolean shouldRender = true;

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {

            if (entityRocket == null || entityRocket.isDead) {
                spawnSeat();
            }
        }
    }

    public void launch() {
        shouldRender = false;
    }

    private void spawnSeat() {
        World world = this.worldObj;

        entityRocket = new EntityRocket(world);
        entityRocket.bindSilo(this);

        entityRocket.setPosition(
            xCoord + 0.5,
            yCoord + 1.0,
            zCoord + 0.5);

        world.spawnEntityInWorld(entityRocket);
    }

    public EntityRocket getEntityRocket() {
        return entityRocket;
    }

    @Override
    public void invalidate() {
        super.invalidate();

        if (entityRocket != null && !entityRocket.isDead) {
            entityRocket.setDead();
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("mods", this.mods);
        nbt.setBoolean("shouldRender", this.shouldRender);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.mods = nbt.getInteger("mods");
        this.shouldRender = nbt.getBoolean("shouldRender");
    }
}
