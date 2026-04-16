package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleBigHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePower;
import com.gtnewhorizons.galaxia.registry.outpost.module.OutpostModuleKind;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: requests that a new module be queued for construction on an outpost.
 *
 * <p>
 * The server creates an {@link AutomatedOutpostModule} in {@code IN_CONSTRUCTION} state and
 * adds it to the outpost. Construction then proceeds tick-by-tick as the module consumes
 * resources from the outpost's inventory.
 *
 * <p>
 * Returns an {@link OutpostFullSyncPacket} so the requesting client immediately sees the
 * new module in the UI.
 */
public final class OutpostBuildModulePacket implements IMessage {

    private CelestialAsset.ID assetId;
    private OutpostModuleKind moduleKind;
    private boolean instantBuild;

    public OutpostBuildModulePacket() {}

    public OutpostBuildModulePacket(CelestialAsset.ID assetId, OutpostModuleKind kind, boolean instantBuild) {
        this.assetId = assetId;
        this.moduleKind = kind;
        this.instantBuild = instantBuild;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeEnum(buf, moduleKind);
        buf.writeBoolean(instantBuild);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleKind = PacketUtil.readEnum(buf, OutpostModuleKind.class);
        instantBuild = buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<OutpostBuildModulePacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostBuildModulePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            AutomatedOutpost state = CelestialClient.getByAssetId(packet.assetId) instanceof AutomatedOutpost o ? o
                : null;
            if (state == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: unknown assetId {} from player {}",
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }

            OutpostModuleKind kind = packet.moduleKind;

            CelestialAsset asset = CelestialAssetStore.findAsset(packet.assetId);
            if (asset == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: missing asset {} for player {}",
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }
            if (kind == OutpostModuleKind.MINER && asset.kind != CelestialAsset.Kind.AUTOMATED_OUTPOST) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: rejected MINER on {} ({}) from player {}",
                    packet.assetId,
                    asset.kind,
                    player.getGameProfile()
                        .getName());
                return null;
            }

            AutomatedOutpostModule module = createModule(kind);
            if (module == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: no module for kind {} (player {})",
                    kind,
                    player.getGameProfile()
                        .getName());
                return null;
            }
            if (packet.instantBuild && player.capabilities.isCreativeMode) {
                module.completeConstruction();
            }
            state.addModule(module);

            Galaxia.LOG.debug(
                "[Outpost] BuildModule: queued {} construction on outpost {} (by {})",
                kind.getDisplayName(),
                packet.assetId,
                player.getGameProfile()
                    .getName());

            // Send a sync packet back so the requesting client sees the new module immediately.
            int moduleIndex = state.modules()
                .size() - 1;
            return OutpostSyncPacket.moduleAdded(packet.assetId, moduleIndex, module);
        }

        private AutomatedOutpostModule createModule(OutpostModuleKind kind) {
            return switch (kind) {
                case HAMMER -> ModuleHammer.getDefault();
                case BIG_HAMMER -> ModuleBigHammer.getDefault();
                case MINER -> ModuleMiner.getDefault();
                case POWER -> ModulePower.getDefault();
            };
        }
    }
}
