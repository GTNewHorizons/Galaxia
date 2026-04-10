package com.gtnewhorizons.galaxia.outpost.network;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule.Status;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: synchronizes the state of all modules in an outpost.
 */
public final class OutpostModuleSyncPacket implements IMessage {

    private String assetId;
    private long energyStored;
    private List<ModuleState> modules;

    public OutpostModuleSyncPacket() {}

    /** Builds a sync packet from the current server-side state of the given outpost. */
    public OutpostModuleSyncPacket(AutomatedOutpostState state) {
        this.assetId = state.assetId;
        this.energyStored = state.getEnergyStored();
        this.modules = new ArrayList<>();
        List<AutomatedOutpostModule> stateModules = state.modules();
        for (int i = 0; i < stateModules.size(); i++) {
            AutomatedOutpostModule m = stateModules.get(i);
            modules.add(new ModuleState(i, m.getStatus(), m.getConstructionProgress(), m.energyBuffer));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId);
        buf.writeLong(energyStored);
        buf.writeInt(modules.size());
        for (ModuleState ms : modules) {
            buf.writeInt(ms.index());
            writeString(
                buf,
                ms.status()
                    .name());
            buf.writeFloat(ms.progress());
            buf.writeLong(ms.energy());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = readString(buf);
        energyStored = buf.readLong();
        int count = buf.readInt();
        modules = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int index = buf.readInt();
            Status status = Status.valueOf(readString(buf));
            float progress = buf.readFloat();
            long energy = buf.readLong();
            modules.add(new ModuleState(index, status, progress, energy));
        }
    }

    public static final class Handler implements IMessageHandler<OutpostModuleSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(OutpostModuleSyncPacket packet, MessageContext ctx) {
            AutomatedOutpostState state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) return null;

            state.setEnergyStored(packet.energyStored);

            List<AutomatedOutpostModule> stateModules = state.modules();
            for (ModuleState ms : packet.modules) {
                if (ms.index() >= 0 && ms.index() < stateModules.size()) {
                    AutomatedOutpostModule module = stateModules.get(ms.index());
                    module.setStatus(ms.status());
                    module.setConstructionProgress(ms.progress());
                    module.energyBuffer = ms.energy();
                }
            }
            state.bumpSyncRevision();
            return null;
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        PacketUtil.writeString(buf, s);
    }

    private static String readString(ByteBuf buf) {
        return PacketUtil.readString(buf);
    }
}
