package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetFilterUpdatePacket implements IMessage {

    public enum Action {
        ADD_FILTER,
        REMOVE_FILTER,
        CLEAR_SLOT,
        SET_SLOT
    }

    private CelestialAsset.ID assetId;
    private Action action;
    private int slot;
    private ItemStack filterItem;
    private List<ItemStack> filterItems;

    public AssetFilterUpdatePacket() {}

    public static AssetFilterUpdatePacket addFilter(CelestialAsset.ID assetId, int slot, ItemStack filter) {
        AssetFilterUpdatePacket pkt = new AssetFilterUpdatePacket();
        pkt.assetId = assetId;
        pkt.action = Action.ADD_FILTER;
        pkt.slot = slot;
        pkt.filterItem = filter;
        return pkt;
    }

    public static AssetFilterUpdatePacket removeFilter(CelestialAsset.ID assetId, int slot, ItemStack filter) {
        AssetFilterUpdatePacket pkt = new AssetFilterUpdatePacket();
        pkt.assetId = assetId;
        pkt.action = Action.REMOVE_FILTER;
        pkt.slot = slot;
        pkt.filterItem = filter;
        return pkt;
    }

    public static AssetFilterUpdatePacket clearSlot(CelestialAsset.ID assetId, int slot) {
        AssetFilterUpdatePacket pkt = new AssetFilterUpdatePacket();
        pkt.assetId = assetId;
        pkt.action = Action.CLEAR_SLOT;
        pkt.slot = slot;
        return pkt;
    }

    public static AssetFilterUpdatePacket setSlot(CelestialAsset.ID assetId, int slot, List<ItemStack> filters) {
        AssetFilterUpdatePacket pkt = new AssetFilterUpdatePacket();
        pkt.assetId = assetId;
        pkt.action = Action.SET_SLOT;
        pkt.slot = slot;
        pkt.filterItems = filters == null ? List.of() : filters;
        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeEnum(buf, action);
        buf.writeInt(slot);
        switch (action) {
            case ADD_FILTER, REMOVE_FILTER -> PacketUtil.writeItemStack(buf, filterItem);
            case SET_SLOT -> {
                buf.writeShort(filterItems.size());
                for (ItemStack stack : filterItems) {
                    PacketUtil.writeItemStack(buf, stack);
                }
            }
            case CLEAR_SLOT -> {}
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        action = PacketUtil.readEnum(buf, Action.class);
        slot = buf.readInt();
        switch (action) {
            case ADD_FILTER, REMOVE_FILTER -> filterItem = PacketUtil.readItemStack(buf);
            case SET_SLOT -> {
                int count = buf.readShort();
                filterItems = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    filterItems.add(PacketUtil.readItemStack(buf));
                }
            }
            case CLEAR_SLOT -> {}
        }
    }

    public static class Handler implements IMessageHandler<AssetFilterUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetFilterUpdatePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            UUID teamId = TempTeamCompat.getTeam(player);
            return message.apply(teamId);
        }
    }

    public AssetSyncPacket apply(UUID teamId) {
        if (teamId == null || assetId == null || action == null) {
            return null;
        }

        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (asset == null) return null;

        if (!CelestialAssetStore.isOwnedBy(teamId, assetId)) {
            return null;
        }

        return switch (action) {
            case ADD_FILTER -> {
                if (filterItem == null) yield null;
                asset.addFilter(slot, filterItem);
                yield AssetSyncPacket.filterUpdated(assetId, slot, asset.getFiltersFor(slot));
            }
            case REMOVE_FILTER -> {
                if (filterItem == null) yield null;
                asset.removeFilter(slot, filterItem);
                List<ItemStack> remaining = asset.getFiltersFor(slot);
                if (remaining.isEmpty()) {
                    yield AssetSyncPacket.filterRemoved(assetId, slot);
                } else {
                    yield AssetSyncPacket.filterUpdated(assetId, slot, remaining);
                }
            }
            case CLEAR_SLOT -> {
                asset.clearFilters(slot);
                yield AssetSyncPacket.filterRemoved(assetId, slot);
            }
            case SET_SLOT -> {
                asset.setFilters(slot, filterItems);
                List<ItemStack> updated = asset.getFiltersFor(slot);
                if (updated.isEmpty()) {
                    yield AssetSyncPacket.filterRemoved(assetId, slot);
                } else {
                    yield AssetSyncPacket.filterUpdated(assetId, slot, updated);
                }
            }
        };
    }
}
