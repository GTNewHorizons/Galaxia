package com.gtnewhorizons.galaxia.core.network;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket.TransitView;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** The current passenger vehicle view. A null payload clears a previous assignment. */
public final class RocketTransitPacket implements IMessage {

    @Nullable
    private TransitView view;

    public RocketTransitPacket() {}

    public RocketTransitPacket(@Nullable TransitView view) {
        this.view = view;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(view != null);
        if (view != null) ByteBufUtils.writeTag(buf, view.serializeNBT());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        view = buf.readBoolean() ? TransitView.deserializeNBT(ByteBufUtils.readTag(buf)) : null;
    }

    public static final class Handler implements IMessageHandler<RocketTransitPacket, IMessage> {

        @Nullable
        private static TransitView current;

        @Nullable
        @SideOnly(Side.CLIENT)
        public static TransitView current() {
            return current;
        }

        @SideOnly(Side.CLIENT)
        public static void clear() {
            current = null;
        }

        @Override
        public IMessage onMessage(RocketTransitPacket message, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> current = message.view);
            return null;
        }
    }
}
