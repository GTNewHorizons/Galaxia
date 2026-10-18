package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
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

            var player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            ServerTickTaskQueue.schedule(() -> {
                if (player.isDead || player.worldObj != world
                    || message.blueprint == null
                    || !world.blockExists(message.x, message.y, message.z)
                    || player.getDistanceSq(message.x + 0.5, message.y + 0.5, message.z + 0.5) > 64) return;
                TileEntity tile = world.getTileEntity(message.x, message.y, message.z);
                if (!(tile instanceof TileEntitySilo silo) || !silo.machineIdentity()
                    .canUse(player, TeamAction.BUILD_MODULE)) return;
                try {
                    var blueprint = RocketBlueprint.deserializeNBT(message.blueprint, RocketPartRegistry.instance());
                    silo.setDesignBlueprint(blueprint);
                    silo.orderModules();
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    Galaxia.LOG.warn("Rejected invalid Rocket construction request from {}", player.getUniqueID(), ex);
                }
            });
            return null;
        }
    }
}
