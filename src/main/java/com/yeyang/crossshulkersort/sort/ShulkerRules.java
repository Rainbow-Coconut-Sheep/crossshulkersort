package com.yeyang.crossshulkersort.sort;

import net.minecraft.component.Component;
import net.minecraft.component.DataComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.ShulkerBoxBlock;

import java.util.List;
import java.util.Objects;

/**
 * Scope rules for the sort.
 *
 * Eligible ("plain") shulker box: vanilla undyed shulker box, stack count 1, no custom
 * name and no special-purpose components - i.e. every data component either matches the
 * fresh-item default or is the container contents itself.
 *
 * Colored boxes are different items, named boxes carry CUSTOM_NAME, and anything else a
 * mod/anvil put on the stack shows up as an extra component - all skipped.
 *
 * Locked box: contains exactly one item type and is completely full (27/27 slots at max
 * stack size) - it is already optimal, so it never participates.
 */
public final class ShulkerRules {

    public static final int BOX_SLOTS = 27;

    private ShulkerRules() {
    }

    /** Eligible and (unless configured otherwise) not already a full single-type box. */
    public static boolean isUsableBox(ItemStack stack) {
        return isEligibleBox(stack)
                && (com.yeyang.crossshulkersort.config.ModConfig.effective().includeLockedFull
                        || !isLockedFull(stack));
    }

    public static boolean isEligibleBox(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() != 1) {
            return false;
        }
        com.yeyang.crossshulkersort.config.ModConfig eff =
                com.yeyang.crossshulkersort.config.ModConfig.effective();
        if (eff.includeDyed) {
            if (!isShulkerBoxItem(stack)) {
                return false;
            }
        } else if (stack.getItem() != Items.SHULKER_BOX) {
            return false;
        }
        if (eff.includeNamed) {
            return true;
        }
        ItemStack fresh = new ItemStack(stack.getItem());
        for (Component<?> typed : stack.getComponents()) {
            DataComponentType<?> type = typed.type();
            if (type == DataComponentTypes.CONTAINER) {
                continue;
            }
            if (Objects.equals(fresh.get(type), stack.get(type))) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static List<ItemStack> readContents(ItemStack box) {
        return box.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT)
                .streamNonEmpty()
                .toList();
    }

    /** Any shulker box item (undyed or colored) - treated as a container, never as a sortable item. */
    public static boolean isShulkerBoxItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    public static boolean isLockedFull(ItemStack box) {
        List<ItemStack> contents = readContents(box);
        if (contents.size() < BOX_SLOTS) {
            return false;
        }
        StackKey first = new StackKey(contents.get(0));
        int total = 0;
        for (ItemStack stack : contents) {
            if (!first.equals(new StackKey(stack))) {
                return false;
            }
            total += stack.getCount();
        }
        return total >= BOX_SLOTS * contents.get(0).getMaxCount();
    }
}
