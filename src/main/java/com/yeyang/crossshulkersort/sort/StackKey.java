package com.yeyang.crossshulkersort.sort;

import net.minecraft.item.ItemStack;

import java.util.Objects;

/**
 * Identity of an item stack ignoring its count (same item + same data components).
 * Replacement for Item Scroller's removed {@code ItemType}, same semantics.
 */
public final class StackKey {

    private final ItemStack stack;

    public StackKey(ItemStack stack) {
        ItemStack c = stack.copy();
        c.setCount(1);
        this.stack = c;
    }

    public ItemStack stack() {
        return this.stack;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StackKey)) {
            return false;
        }
        StackKey other = (StackKey) obj;
        return ItemStack.areItemsEqual(this.stack, other.stack)
                && Objects.equals(this.stack.getTag(), other.stack.getTag());
    }

    @Override
    public int hashCode() {
        return 31 * this.stack.getItem().hashCode() + Objects.hashCode(this.stack.getTag());
    }
}

