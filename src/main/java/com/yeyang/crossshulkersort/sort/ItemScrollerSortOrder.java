package com.yeyang.crossshulkersort.sort;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.Comparator;

/**
 * Stack ordering for 1.20.5: no Item Scroller integration on this branch, so this
 * is plain vanilla id order (same as the {@code useItemScrollerOrder=false} fallback
 * on newer branches). Kept as a named comparator so the config switch and call
 * sites stay version-independent.
 */
public final class ItemScrollerSortOrder {

    public static final Comparator<ItemStack> COMPARATOR = ItemScrollerSortOrder::compare;

    private ItemScrollerSortOrder() {
    }

    private static int compare(ItemStack a, ItemStack b) {
        boolean aEmpty = a.isEmpty();
        boolean bEmpty = b.isEmpty();
        if (aEmpty != bEmpty) {
            return Boolean.compare(aEmpty, bEmpty);
        }
        if (aEmpty) {
            return 0;
        }
        if (a.getItem() != b.getItem()) {
            return Registries.ITEM.getRawId(a.getItem()) - Registries.ITEM.getRawId(b.getItem());
        }
        if (!ItemStack.areItemsAndComponentsEqual(a, b)) {
            return Integer.compare(a.getComponents().hashCode(), b.getComponents().hashCode());
        }
        return Integer.compare(b.getCount(), a.getCount());
    }

    static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }
}
