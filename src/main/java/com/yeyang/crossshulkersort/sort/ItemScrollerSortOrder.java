package com.yeyang.crossshulkersort.sort;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.registry.Registry;

import java.util.Comparator;

/**
 * Stack ordering for 1.20.1: masa Item Scroller 0.20.0 has no inventory sorting
 * to mirror, so this is plain vanilla id order (same as the
 * {@code useItemScrollerOrder=false} fallback on newer branches). Kept as a named
 * comparator so the config switch and call sites stay version-independent.
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
            return Registry.ITEM.getRawId(a.getItem()) - Registry.ITEM.getRawId(b.getItem());
        }
        if (!ItemStack.areItemsEqual(a, b) || !java.util.Objects.equals(a.getTag(), b.getTag())) {
            return Integer.compare(a.getTag() == null ? 0 : a.getTag().hashCode(),
                    b.getTag() == null ? 0 : b.getTag().hashCode());
        }
        return Integer.compare(b.getCount(), a.getCount());
    }

    static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        return ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock;
    }
}

