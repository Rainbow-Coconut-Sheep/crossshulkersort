package com.yeyang.crossshulkersort.sort;

import net.minecraft.Bootstrap;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SortPlanFuzzTest {
    private SortPlanFuzzTest() {}

    public static void main(String[] args) throws Exception {
        Bootstrap.initialize();
        Class.forName(ServerSorter.class.getName(), true, ServerSorter.class.getClassLoader());

        Item[] pool64 = new Item[]{Items.DROPPER, Items.STONE, Items.DIRT, Items.COBBLESTONE, Items.HOPPER, Items.BONE_BLOCK, Items.COMPOSTER, Items.IRON_TRAPDOOR, Items.CHEST};
        Item[] pool16 = new Item[]{Items.ENDER_PEARL, Items.EGG};
        Item[] pool1 = new Item[]{Items.DIAMOND_SWORD, Items.BUCKET};

        int fails = 0;
        int checked = 0;
        for (long seed = 1; seed <= 5000; seed++) {
            Random r = new Random(seed);
            int boxCount = 4 + r.nextInt(4); // 4..7
            PlayerInventory inv = new PlayerInventory(null);
            // fill all 36 slots: boxes in random slots, loose elsewhere
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < 36; i++) slots.add(i);
            java.util.Collections.shuffle(slots, r);
            List<Integer> boxSlots = slots.subList(0, boxCount);
            // build each box with random fill 5..27 stacks
            for (int bi = 0; bi < boxCount; bi++) {
                int invSlot = boxSlots.get(bi);
                int stacks = 5 + r.nextInt(23); // 5..27
                List<ItemStack> contents = new ArrayList<>();
                for (int s = 0; s < stacks; s++) {
                    contents.add(randomStack(r, pool64, pool16, pool1));
                }
                ItemStack box = new ItemStack(Items.SHULKER_BOX);
                ShulkerRules.writeContents(box, contents);
                inv.setStack(invSlot, box);
            }
            // loose items in remaining slots
            for (int i = boxCount; i < 36; i++) {
                int invSlot = slots.get(i);
                if (r.nextDouble() < 0.4) {
                    inv.setStack(invSlot, randomStack(r, pool64, pool16, pool1));
                } else {
                    inv.setStack(invSlot, ItemStack.EMPTY);
                }
            }
            SortPlan plan;
            try {
                plan = SortPlan.compute(inv);
            } catch (Throwable t) {
                System.out.println("seed " + seed + " threw " + t);
                t.printStackTrace(System.out);
                fails++;
                if (fails > 5) break;
                continue;
            }
            checked++;
            // verify quotaSum <= pool for every type
            Map<StackKey, Integer> pool = new HashMap<>();
            for (int i = 0; i < 36; i++) {
                boolean isBox = false;
                for (SortPlan.BoxInfo b : plan.boxes) if (b.invIndex == i) { isBox = true; break; }
                if (isBox) continue;
                ItemStack st = inv.getStack(i);
                if (!st.isEmpty() && !ShulkerRules.isShulkerBoxItem(st)) pool.merge(new StackKey(st), st.getCount(), Integer::sum);
            }
            for (SortPlan.BoxInfo b : plan.boxes) {
                for (ItemStack st : b.contents) {
                    if (!st.isEmpty() && !ShulkerRules.isShulkerBoxItem(st)) pool.merge(new StackKey(st), st.getCount(), Integer::sum);
                }
            }
            Map<StackKey, Integer> quotaSum = new HashMap<>();
            for (SortPlan.BoxInfo b : plan.chosen) {
                for (Map.Entry<StackKey, Integer> e : b.quota.entrySet()) {
                    if (ShulkerRules.isShulkerBoxItem(e.getKey().stack())) continue;
                    quotaSum.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
            for (Map.Entry<StackKey, Integer> e : quotaSum.entrySet()) {
                int exist = pool.getOrDefault(e.getKey(), -1);
                if (exist < 0) {
                    System.out.println("seed " + seed + " quota for unknown type " + SortPlan.keyName(e.getKey()) + " quota=" + e.getValue());
                    dumpPlan(seed, inv, plan, pool, quotaSum);
                    fails++;
                    break;
                }
                if (e.getValue() > exist) {
                    System.out.println("seed " + seed + " OVERFLOW " + SortPlan.keyName(e.getKey()) + " quotaSum=" + e.getValue() + " pool=" + exist + " diff=" + (e.getValue()-exist));
                    dumpPlan(seed, inv, plan, pool, quotaSum);
                    fails++;
                    break;
                }
            }
            if (plan.reservedBoxes > 0) {
                java.util.List<Integer> resOrder = new java.util.ArrayList<>();
                for (SortPlan.BoxInfo b : plan.chosen) resOrder.add(b.invIndex);
                resOrder.sort(java.util.Collections.reverseOrder());
                java.util.Set<Integer> resSet = new java.util.HashSet<>(
                        resOrder.subList(0, plan.reservedBoxes));
                for (int b = 0; b < plan.chosen.size(); b++) {
                    SortPlan.BoxInfo box = plan.chosen.get(b);
                    boolean inRes = resSet.contains(box.invIndex);
                    boolean hasStack = false;
                    boolean hasUnstack = false;
                    for (StackKey key : box.quota.keySet()) {
                        if (ShulkerRules.isShulkerBoxItem(key.stack())) continue;
                        if (key.stack().getMaxCount() <= 1) hasUnstack = true;
                        else hasStack = true;
                    }
                    if (hasStack && hasUnstack) {
                        System.out.println("seed " + seed + " MIXED box[" + b + "] inv=" + box.invIndex
                                + " reserved=" + inRes + " resBoxes=" + plan.reservedBoxes);
                        dumpPlan(seed, inv, plan, pool, quotaSum);
                        fails++;
                        break;
                    }
                }
            }
            if (fails > 5) break;
        }
        System.out.println("fuzz done checked=" + checked + " fails=" + fails);
        if (fails > 0) throw new AssertionError(fails + " fuzz failures");
    }

    private static ItemStack randomStack(Random r, Item[] p64, Item[] p16, Item[] p1) {
        double d = r.nextDouble();
        Item item;
        int max;
        if (d < 0.8) { item = p64[r.nextInt(p64.length)]; max = 64; }
        else if (d < 0.93) { item = p16[r.nextInt(p16.length)]; max = 16; }
        else { item = p1[r.nextInt(p1.length)]; max = 1; }
        int count = 1 + r.nextInt(max);
        // occasionally partial stacks
        return new ItemStack(item, count);
    }

    private static void dumpPlan(long seed, PlayerInventory inv, SortPlan plan, Map<StackKey,Integer> pool, Map<StackKey,Integer> quotaSum) {
        System.out.println("  seed=" + seed + " boxes=" + plan.boxes.size() + " chosen=" + plan.chosen.size() + " excess=" + plan.excess.size());
        for (int b = 0; b < plan.chosen.size(); b++) {
            SortPlan.BoxInfo box = plan.chosen.get(b);
            System.out.println("  chosen[" + b + "] invSlot=" + box.invIndex + " initialFilled=" + box.initialFilled + " contentsStacks=" + box.contents.size() + " quotaTypes=" + box.quota.size());
            for (Map.Entry<StackKey,Integer> e : box.quota.entrySet()) {
                int held = 0;
                for (ItemStack s : box.contents) if (!s.isEmpty() && new StackKey(s).equals(e.getKey())) held += s.getCount();
                System.out.println("    quota " + SortPlan.keyName(e.getKey()) + " held=" + held + " quota=" + e.getValue() + " pool=" + pool.getOrDefault(e.getKey(),0) + " sum=" + quotaSum.getOrDefault(e.getKey(),0));
            }
        }
        for (SortPlan.BoxInfo box : plan.excess) {
            System.out.println("  excess invSlot=" + box.invIndex + " stacks=" + box.contents.size());
        }
    }
}



