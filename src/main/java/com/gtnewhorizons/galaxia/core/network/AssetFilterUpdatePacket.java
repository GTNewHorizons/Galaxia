package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetFilterUpdatePacket implements IMessage {

    public enum Action {
        ADD_FILTER,
        REMOVE_FILTER,
        SET_FILTER
    }

    private CelestialAsset.ID assetId;
    private Action action;
    private boolean isItem;
    private String filterKey;
    private List<String> filterKeys;

    public AssetFilterUpdatePacket() {}

    public static AssetFilterUpdatePacket addFilter(CelestialAsset.ID assetId, boolean isItem, String filterKey) {
        AssetFilterUpdatePacket pkt = new AssetFilterUpdatePacket();
        pkt.assetId = assetId;
        pkt.action = Action.ADD_FILTER;
        pkt.isItem = isItem;
        pkt.filterKey = filterKey;
        return pkt;
    }

    public static AssetFilterUpdatePacket removeFilter(CelestialAsset.ID assetId, boolean isItem, String filterKey) {
        AssetFilterUpdatePacket pkt = new AssetFilterUpdatePacket();
        pkt.assetId = assetId;
        pkt.action = Action.REMOVE_FILTER;
        pkt.isItem = isItem;
        pkt.filterKey = filterKey;
        return pkt;
    }

    public static AssetFilterUpdatePacket clearFilters(CelestialAsset.ID assetId, boolean isItem) {
        AssetFilterUpdatePacket pkt = new AssetFilterUpdatePacket();
        pkt.assetId = assetId;
        pkt.action = Action.SET_FILTER;
        pkt.isItem = isItem;
        pkt.filterKeys = List.of();
        return pkt;
    }

    public static AssetFilterUpdatePacket setFilters(CelestialAsset.ID assetId, boolean isItem,
        List<String> filterKeys) {
        AssetFilterUpdatePacket pkt = new AssetFilterUpdatePacket();
        pkt.assetId = assetId;
        pkt.action = Action.SET_FILTER;
        pkt.isItem = isItem;
        pkt.filterKeys = filterKeys == null ? List.of() : filterKeys;
        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        PacketUtil.writeEnum(buf, action);
        buf.writeBoolean(isItem);
        switch (action) {
            case ADD_FILTER, REMOVE_FILTER -> PacketUtil.writeString(buf, filterKey);
            case SET_FILTER -> {
                buf.writeShort(filterKeys.size());
                for (String key : filterKeys) {
                    PacketUtil.writeString(buf, key);
                }
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        action = PacketUtil.readEnum(buf, Action.class);
        isItem = buf.readBoolean();
        switch (action) {
            case ADD_FILTER, REMOVE_FILTER -> filterKey = PacketUtil.readString(buf);
            case SET_FILTER -> {
                int count = buf.readShort();
                filterKeys = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    filterKeys.add(PacketUtil.readString(buf));
                }
            }
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

        if (!(asset instanceof AutomatedFacility facility)) {
            return null;
        }

        return switch (action) {
            case ADD_FILTER -> {
                if (filterKey == null) yield null;
                facility.addFilter(filterKey, isItem);
                yield AssetSyncPacket.filterUpdated(assetId, isItem, List.of(filterKey));
            }
            case REMOVE_FILTER -> {
                if (filterKey == null) yield null;
                facility.removeFilter(filterKey, isItem);
                ResourceFilter<?> filter = isItem ? facility.getItemFilter() : facility.getFluidFilter();
                if (filter.isEmpty()) {
                    yield AssetSyncPacket.filterRemoved(assetId, isItem);
                } else {
                    yield AssetSyncPacket.filterUpdated(assetId, isItem, filter.serialize());
                }
            }
            case SET_FILTER -> {
                if (filterKeys == null) yield null;
                facility.setFilters(filterKeys, isItem);
                ResourceFilter<?> filter = isItem ? facility.getItemFilter() : facility.getFluidFilter();
                if (filter.isEmpty()) {
                    yield AssetSyncPacket.filterRemoved(assetId, isItem);
                } else {
                    yield AssetSyncPacket.filterUpdated(assetId, isItem, filter.serialize());
                }
            }
        };
    }
}
