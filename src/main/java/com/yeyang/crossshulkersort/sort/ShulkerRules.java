package com.yeyang.crossshulkersort.sort;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope rules for the sort (1.20.1: NBT storage - box contents live in the
 * {@code BlockEntityTag.Items} list, not in a data component).
 *
 * Eligible ("plain") shulker box: vanilla undyed shulker box, stack count 1, no custom
 * name and no special-purpose NBT - i.e. the only NBT keys are the container contents
 * itself (plus a plain display name when configured).
 *
 * Colored boxes are different items, named boxes carry display.Name, and anything else
 * a mod/anvil put on the stack shows up as extra NBT - all skipped.
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
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return true;
        }
        for (String key : tag.getKeys()) {
            if (key.equals("BlockEntityTag")) {
                continue;
            }
            if (key.equals("display") && eff.includeNamed && isPlainDisplayName(tag)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** display tag holding only a custom Name (no Lore) counts as "just named". */
    private static boolean isPlainDisplayName(CompoundTag tag) {
        CompoundTag display = tag.getCompound("display");
        // NOTE (<=1.16 branch): NBT type ids as literals (8=string) - no *_TYPE constants yet.
        return display.contains("Name", 8) && !display.contains("Lore");
    }

    public static List<ItemStack> readContents(ItemStack box) {
        List<ItemStack> out = new ArrayList<>();
        CompoundTag tag = box.getSubTag("BlockEntityTag");
        // 9=list, 10=compound.
        if (tag == null || !tag.contains("Items", 9)) {
            return out;
        }
        ListTag list = tag.getList("Items", 10);
        for (Tag e : list) {
            ItemStack s = ItemStack.fromTag((CompoundTag) e);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** Writes up to 27 slot stacks (EMPTY entries skipped); empty content removes the tag. */
    public static void writeContents(ItemStack box, List<ItemStack> slots) {
        ListTag list = new ListTag();
        for (int i = 0; i < Math.min(slots.size(), BOX_SLOTS); i++) {
            ItemStack s = slots.get(i);
            if (s.isEmpty()) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.putByte("Slot", (byte) i);
            s.toTag(c);
            list.add(c);
        }
        if (list.isEmpty()) {
            box.removeSubTag("BlockEntityTag");
        } else {
            box.getOrCreateSubTag("BlockEntityTag").put("Items", list);
        }
    }

    /** Any shulker box item (undyed or colored) - treated as a container, never as a sortable item. */
    public static boolean isShulkerBoxItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof BlockItem
                && ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock;
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


