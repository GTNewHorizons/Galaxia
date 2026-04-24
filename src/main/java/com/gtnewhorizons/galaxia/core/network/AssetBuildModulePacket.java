package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: requests that a new module be queued for construction on an outpost.
 *
 * <p>
 * The server creates an {@link AutomatedFacilityModule} in {@code IN_CONSTRUCTION} state and
 * adds it to the outpost. Construction then proceeds tick-by-tick as the module consumes
 * resources from the outpost's inventory.
 *
 * <p>
 * Returns an {@link OutpostFullSyncPacket} so the requesting client immediately sees the
 * new module in the UI.
 */
public final class AssetBuildModulePacket implements IMessage {

    private CelestialAsset.ID assetId;
    private ModuleInstance.ID moduleId;
    private FacilityModuleKind moduleKind;
    private boolean instantBuild;

    public AssetBuildModulePacket() {}

    public AssetBuildModulePacket(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleInstance.ID moduleId,
        boolean instantBuild) {
        this.assetId = assetId;
        this.moduleKind = kind;
        this.moduleId = moduleId;
        this.instantBuild = instantBuild;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeId(buf, moduleId);
        PacketUtil.writeEnum(buf, moduleKind);
        buf.writeBoolean(instantBuild);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleId = PacketUtil.readModuleId(buf);
        moduleKind = PacketUtil.readEnum(buf, FacilityModuleKind.class);
        instantBuild = buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<AssetBuildModulePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetBuildModulePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            CelestialAsset asset = CelestialAssetStore.findAsset(packet.assetId);
            if (asset == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: missing asset {} for player {}",
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }
            if (!(asset instanceof AutomatedFacility state)) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: asset {} is not an automated facility for player {}",
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }
            if (!CelestialAssetStore.isOwnedBy(TempTeamCompat.getTeam(player), packet.assetId)) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: unauthorized access to asset {} by player {}",
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }

            FacilityModuleKind kind = packet.moduleKind;
            if (kind == FacilityModuleKind.MINER && asset.kind != CelestialAsset.Kind.AUTOMATED_OUTPOST) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: rejected MINER on {} ({}) from player {}",
                    packet.assetId,
                    asset.kind,
                    player.getGameProfile()
                        .getName());
                return null;
            }

            ModuleInstance module = kind.createInstance(packet.moduleId);
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

            int moduleIndex = state.modules()
                .size() - 1;
            return AssetSyncPacket.moduleAdded(packet.assetId, moduleIndex, module);
        }
    }
}
