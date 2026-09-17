package com.yeyang.crossshulkersort.sort;

import net.minecraft.world.item.ItemStack;

/**
 * Identity of an item stack ignoring its count (same item + same data components).
 * Replacement for Item Scroller's removed {@code ItemType}, same semantics.
 */
public final class StackKey {

    private final ItemStack stack;

    public StackKey(ItemStack stack) {
        this.stack = stack.copyWithCount(1);
    }

    public ItemStack stack() {
        return this.stack;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StackKey other && ItemStack.isSameItemSameComponents(this.stack, other.stack);
    }

    @Override
    public int hashCode() {
        int hash = this.stack.getItem().hashCode();
        return 31 * hash + this.stack.getComponents().hashCode();
    }
}
