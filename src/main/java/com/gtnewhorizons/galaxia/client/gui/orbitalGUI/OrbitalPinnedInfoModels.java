package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

final class PinnedInfoRow {

    final String label;
    final String value;
    final List<ItemStack> items;
    final boolean inlineItems;

    PinnedInfoRow(String label, String value) {
        this(label, value, Collections.emptyList(), false);
    }

    PinnedInfoRow(String label, String value, List<ItemStack> items) {
        this(label, value, items, false);
    }

    PinnedInfoRow(String label, String value, List<ItemStack> items, boolean inlineItems) {
        this.label = label;
        this.value = value;
        this.items = items == null ? Collections.emptyList() : items;
        this.inlineItems = inlineItems;
    }

    static PinnedInfoRow section(String label) {
        return new PinnedInfoRow(label, "", Collections.emptyList(), false);
    }

    static PinnedInfoRow inlineItems(String value, List<ItemStack> items) {
        return new PinnedInfoRow("", value, items, true);
    }
}

final class PinnedInfoItemBounds {

    final ItemStack stack;
    final int left;
    final int top;
    final int size;

    PinnedInfoItemBounds(ItemStack stack, int left, int top, int size) {
        this.stack = stack;
        this.left = left;
        this.top = top;
        this.size = size;
    }

    boolean contains(int x, int y) {
        return x >= left && x <= left + size && y >= top && y <= top + size;
    }
}
