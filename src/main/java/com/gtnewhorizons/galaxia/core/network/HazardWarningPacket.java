package com.gtnewhorizons.galaxia.core.network;

import java.util.List;

import com.gtnewhorizons.galaxia.client.HazardWarningClient;
import com.gtnewhorizons.galaxia.utility.hazards.HazardWarnings;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class HazardWarningPacket implements IMessage {

    private long mask;

    public HazardWarningPacket() {
        this.mask = 0;
    }

    public HazardWarningPacket(List<HazardWarnings> warnings) {
        long mask = 0;

        for (HazardWarnings warning : warnings) {
            mask |= 1 << warning.ordinal();
        }

        this.mask = mask;
    }

    private static final HazardWarnings[] values = HazardWarnings.values();
    static {
        int maxBits = Long.SIZE;
        int enumSize = HazardWarnings.values().length;

        if (enumSize > maxBits) {
            throw new IllegalStateException("Too many HazardWarnings (" + enumSize + "), max supported is " + maxBits);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.mask = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.mask);
    }

    public static class Handler implements IMessageHandler<HazardWarningPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(HazardWarningPacket message, MessageContext ctx) {
            HazardWarningClient.setWarnings(message.mask);

            return null;
        }
    }
}
