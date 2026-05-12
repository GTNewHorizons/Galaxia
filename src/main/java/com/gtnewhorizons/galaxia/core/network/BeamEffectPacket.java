package com.gtnewhorizons.galaxia.core.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class BeamEffectPacket implements IMessage {

    public int machineX, machineY, machineZ;
    public int playerX100, playerY100, playerZ100;

    public BeamEffectPacket() {}

    public BeamEffectPacket(int machineX, int machineY, int machineZ, double playerX, double playerY, double playerZ) {
        this.machineX = machineX;
        this.machineY = machineY;
        this.machineZ = machineZ;
        this.playerX100 = (int) (playerX * 100);
        this.playerY100 = (int) (playerY * 100);
        this.playerZ100 = (int) (playerZ * 100);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        machineX = buf.readInt();
        machineY = buf.readInt();
        machineZ = buf.readInt();
        playerX100 = buf.readInt();
        playerY100 = buf.readInt();
        playerZ100 = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(machineX);
        buf.writeInt(machineY);
        buf.writeInt(machineZ);
        buf.writeInt(playerX100);
        buf.writeInt(playerY100);
        buf.writeInt(playerZ100);
    }
}
