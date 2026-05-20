package com.gtnewhorizons.galaxia.core.network;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import com.gtnewhorizons.galaxia.core.config.ConfigPlayer;
import com.gtnewhorizons.galaxia.registry.items.tether.KineticTetherState;
import com.gtnewhorizons.galaxia.registry.items.tether.TetherData;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class TetherPacket implements IMessage {

    private byte action;

    public static final byte ACTION_START_ANCHOR = 0;
    public static final byte ACTION_END_ANCHOR = 1;
    public static final byte ACTION_START_PROPULSION = 2;
    public static final byte ACTION_END_PROPULSION = 3;

    public TetherPacket() {}

    public TetherPacket(byte action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
    }

    public static class Handler implements IMessageHandler<TetherPacket, IMessage> {

        @Override
        public IMessage onMessage(TetherPacket message, MessageContext ctx) {

            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            TetherData data = KineticTetherState.get(player);

            switch (message.action) {

                case ACTION_START_ANCHOR:
                    MovingObjectPosition mop = raytraceAnchor(player);

                    if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                        data.tetherActive = true;
                        data.anchorX = mop.blockX + 0.5;
                        data.anchorY = mop.blockY + 0.5;
                        data.anchorZ = mop.blockZ + 0.5;
                        data.anchorDim = player.worldObj.provider.dimensionId;
                    }
                    break;

                case ACTION_END_ANCHOR:
                    data.tetherActive = false;
                    data.propulsionActive = false;
                    break;

                case ACTION_START_PROPULSION:
                    if (data.tetherActive) {
                        data.propulsionActive = true;
                    }
                    break;

                case ACTION_END_PROPULSION:
                    data.propulsionActive = false;
                    break;
            }

            sendSyncToPlayer(player, data);

            return null;
        }

        private void sendSyncToPlayer(EntityPlayerMP player, TetherData data) {
            GALAXIA_NETWORK.sendTo(new TetherAnchorSyncPacket(data), player);
        }

        private MovingObjectPosition raytraceAnchor(EntityPlayerMP player) {
            double reach = ConfigPlayer.ConfigTether.maxTetherLength;

            Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);

            Vec3 look = player.getLook(1.0F);

            Vec3 end = Vec3.createVectorHelper(
                start.xCoord + look.xCoord * reach,
                start.yCoord + look.yCoord * reach,
                start.zCoord + look.zCoord * reach);

            return player.worldObj.rayTraceBlocks(start, end, false);
        }
    }
}
