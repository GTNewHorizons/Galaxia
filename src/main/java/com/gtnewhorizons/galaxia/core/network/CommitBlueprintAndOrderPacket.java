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
    /** False only saves the design, true also orders the modules */
    private boolean order;

    public CommitBlueprintAndOrderPacket() {}

    public CommitBlueprintAndOrderPacket(int x, int y, int z, NBTTagCompound blueprint) {
        this(x, y, z, blueprint, true);
    }

    public CommitBlueprintAndOrderPacket(int x, int y, int z, NBTTagCompound blueprint, boolean order) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blueprint = blueprint;
        this.order = order;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);

        ByteBufUtils.writeTag(buf, blueprint);
        buf.writeBoolean(order);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();

        blueprint = ByteBufUtils.readTag(buf);
        order = buf.readBoolean();
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

            silo.setDesignBlueprint(blueprint);

            if (message.order) silo.orderModules();

            silo.sync();

            return null;
        }
    }
}
