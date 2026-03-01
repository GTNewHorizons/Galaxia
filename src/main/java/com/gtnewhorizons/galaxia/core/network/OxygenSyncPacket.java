package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * This packet should be used whenever equipped oxygen tanks are modified on the server-side.
 * This is required for the oxygen hud to be correctly synced to the client!
 */
public class OxygenSyncPacket implements IMessage {

    // Baubles slot of altered tank
    private int slot;
    // New oxygen count
    private int oxygen;

    public OxygenSyncPacket() {}

    public OxygenSyncPacket(int slot, int oxygen) {
        this.slot = slot;
        this.oxygen = oxygen;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        buf.writeInt(oxygen);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        oxygen = buf.readInt();
    }

    public static class Handler implements IMessageHandler<OxygenSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(OxygenSyncPacket message, MessageContext ctx) {
            net.minecraft.client.entity.EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return null;

            ItemStack stack = BaublesApi.getBaubles(player)
                .getStackInSlot(message.slot);
            if (stack != null && stack.getItem() instanceof ItemOxygenTank) {
                if (!stack.hasTagCompound()) stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
                stack.getTagCompound()
                    .setInteger(ItemOxygenTank.NBT_OXYGEN, message.oxygen);
            }
            return null;
        }
    }
}
