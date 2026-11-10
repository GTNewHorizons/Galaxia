package com.gtnewhorizons.galaxia.registry.block.special;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.interfaces.IItemStorageHandler;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

/**
 * Item store of a station, a stand-in until the real storage machine exists. Placed inside a station room it joins
 * the station's shared storage, which the station hatches and hammers draw from and deliver into
 */
public class TileStationItemVault extends TileEntity implements IGuiHolder<PosGuiData> {

    /** Different items the vault keeps apart */
    public static final int TYPES = 64;
    /** Items the vault holds in total */
    public static final long CAPACITY = 1_048_576L;
    /** Stored items listed in the screen */
    private static final int LISTED = 14;

    private final Map<ItemStackWrapper, Long> stored = new LinkedHashMap<>();
    private long total;

    public static final IItemStorageHandler<TileStationItemVault> HANDLER = new IItemStorageHandler<>() {

        @Override
        public BlockPos getPosition(TileStationItemVault vault) {
            return new BlockPos(vault.xCoord, vault.yCoord, vault.zCoord);
        }

        @Override
        public void tick(TileStationItemVault vault) {}

        @Override
        public boolean isReady(TileStationItemVault vault) {
            return !vault.isInvalid();
        }

        @Override
        public void onAttached(TileStationItemVault vault, StationGraph graph) {}

        @Override
        public void onDetached(TileStationItemVault vault, StationGraph graph) {}

        @Override
        public void markDirty(TileStationItemVault vault) {
            vault.markDirty();
        }

        @Override
        public long getItemStored(TileStationItemVault vault) {
            return vault.total;
        }

        @Override
        public long getItemCapacity(TileStationItemVault vault) {
            return CAPACITY;
        }

        @Override
        public long getItemSlots(TileStationItemVault vault) {
            return TYPES;
        }

        @Override
        public List<ItemStack> getAllItems(TileStationItemVault vault) {
            List<ItemStack> items = new ArrayList<>(vault.stored.size());
            vault.stored.forEach((item, amount) -> items.add(item.toStack(amount)));
            return items;
        }

        @Override
        public ItemStack extractItem(TileStationItemVault vault, ItemStack resource, boolean doDrain) {
            ItemStackWrapper item = ItemStackWrapper.of(resource);
            if (item == null) return null;
            long taken = Math.min(resource.stackSize, vault.stored.getOrDefault(item, 0L));
            if (taken <= 0) return null;
            if (doDrain) vault.remove(item, taken);
            return item.toStack(taken);
        }

        @Override
        public long insertItem(TileStationItemVault vault, ItemStack resource, boolean doDrain) {
            ItemStackWrapper item = ItemStackWrapper.of(resource);
            if (item == null) return 0;
            long accepted = vault.room(item, resource.stackSize);
            if (doDrain && accepted > 0) vault.add(item, accepted);
            return accepted;
        }

        @Override
        public ResourceFilter<ItemStackWrapper> getItemFilter(TileStationItemVault vault) {
            return ResourceFilter.forItems();
        }
    };

    /** How many of an item still fit, a new kind of item needs a free type */
    private long room(ItemStackWrapper item, long amount) {
        if (!stored.containsKey(item) && stored.size() >= TYPES) return 0;
        return Math.max(0, Math.min(amount, CAPACITY - total));
    }

    private void add(ItemStackWrapper item, long amount) {
        stored.merge(item, amount, Long::sum);
        total += amount;
        markDirty();
    }

    private void remove(ItemStackWrapper item, long amount) {
        long left = stored.getOrDefault(item, 0L) - amount;
        if (left > 0) stored.put(item, left);
        else stored.remove(item);
        total -= amount;
        markDirty();
    }

    public long getAmount(ItemStackWrapper item) {
        return stored.getOrDefault(item, 0L);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        StringSyncValue contents = new StringSyncValue(this::describe);
        syncManager.syncValue("contents", contents);
        ModularPanel panel = GalaxiaGuiStyle.panel("galaxia:station_item_vault", 200, 40 + LISTED * 9);
        panel.child(
            GalaxiaGuiStyle.title("tile.station_item_vault.name")
                .pos(8, 7));
        for (int i = 0; i <= LISTED; i++) {
            final int line = i;
            panel.child(
                GalaxiaGuiStyle.label(() -> line(contents.getValue(), line), () -> GalaxiaGuiStyle.TEXT, 0.6f)
                    .pos(8, 20 + i * 9));
        }
        return panel;
    }

    /** The summary line followed by the largest stored items, one per line */
    private String describe() {
        StringBuilder text = new StringBuilder(
            StatCollector.translateToLocalFormatted(
                "galaxia.gui.station_item_vault.summary",
                total,
                CAPACITY,
                stored.size(),
                TYPES));
        stored.entrySet()
            .stream()
            .sorted(Map.Entry.<ItemStackWrapper, Long>comparingByValue(Comparator.reverseOrder()))
            .limit(LISTED)
            .forEach(
                entry -> text.append('\n')
                    .append(entry.getValue())
                    .append(" x ")
                    .append(
                        entry.getKey()
                            .toStack(1)
                            .getDisplayName()));
        return text.toString();
    }

    private static String line(String text, int index) {
        String[] lines = text == null ? new String[0] : text.split("\n");
        return index < lines.length ? lines[index] : "";
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList items = new NBTTagList();
        stored.forEach((item, amount) -> {
            NBTTagCompound entry = new NBTTagCompound();
            item.toStack(1)
                .writeToNBT(entry);
            entry.setLong("amount", amount);
            items.appendTag(entry);
        });
        tag.setTag("items", items);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        stored.clear();
        total = 0;
        NBTTagList items = tag.getTagList("items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            ItemStackWrapper item = ItemStackWrapper.of(ItemStack.loadItemStackFromNBT(entry));
            long amount = entry.getLong("amount");
            if (item == null || amount <= 0) continue;
            stored.merge(item, amount, Long::sum);
            total += amount;
        }
    }
}
