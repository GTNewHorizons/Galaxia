package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class CommitBlueprintAndOrderPacket implements IMessage {

    private int x;
    private int y;
    private int z;

    private NBTTagCompound blueprint;

    public CommitBlueprintAndOrderPacket() {}

    public CommitBlueprintAndOrderPacket(int x, int y, int z, NBTTagCompound blueprint) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blueprint = blueprint;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);

        ByteBufUtils.writeTag(buf, blueprint);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();

        blueprint = ByteBufUtils.readTag(buf);
    }

    public static class Handler implements IMessageHandler<CommitBlueprintAndOrderPacket, IMessage> {

        @Override
        public IMessage onMessage(CommitBlueprintAndOrderPacket message, MessageContext ctx) {

            World world = ctx.getServerHandler().playerEntity.worldObj;

            TileEntity te = world.getTileEntity(message.x, message.y, message.z);

            if (!(te instanceof TileEntitySilo silo)) {
                return null;
            }

            RocketBlueprint blueprint = RocketBlueprint
                .deserializeNBT(message.blueprint, RocketPartRegistry.instance());

            System.out.println(
                "[PACKET] Received blueprint parts: " + blueprint.getParts()
                    .size());

            silo.setDesignBlueprint(blueprint);

            silo.orderModules();

            silo.sync();

            return null;
        }
    }
}
