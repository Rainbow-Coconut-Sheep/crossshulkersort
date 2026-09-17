package com.yeyang.crossshulkersort.sort;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class StackKeyHashTest {
    private StackKeyHashTest() {}

    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();

        // basic: two fresh droppers
        ItemStack a = new ItemStack(Items.DROPPER, 64);
        ItemStack b = new ItemStack(Items.DROPPER, 21);
        StackKey ka = new StackKey(a);
        StackKey kb = new StackKey(b);
        System.out.println("fresh dropper equals=" + ka.equals(kb) + " hashA=" + ka.hashCode() + " hashB=" + kb.hashCode());

        // via box read
        List<ItemStack> contents = List.of(new ItemStack(Items.DROPPER, 64), new ItemStack(Items.DROPPER, 21));
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        ShulkerRules.writeContents(box, contents);
        List<ItemStack> read = ShulkerRules.readContents(box);
        System.out.println("read size=" + read.size());
        for (ItemStack s : read) {
            StackKey k = new StackKey(s);
            System.out.println("read stack count=" + s.getCount() + " equalsFresh=" + k.equals(ka) + " hash=" + k.hashCode() + " nbt=" + s.getNbt());
        }
        ItemStack a1 = a.copy();
        a1.setCount(1);
        System.out.println("fresh nbt=" + a1.getNbt());

        // copy sharing test: mutate original, check key hash stability
        ItemStack orig = new ItemStack(Items.STONE, 64);
        StackKey kOrig = new StackKey(orig);
        int hBefore = kOrig.hashCode();
        orig.setCount(10);
        int hAfter = kOrig.hashCode();
        System.out.println("mutate count hashBefore=" + hBefore + " hashAfter=" + hAfter + " stable=" + (hBefore==hAfter));

        // nbt mutation sharing test
        ItemStack boxStack = new ItemStack(Items.SHULKER_BOX);
        ShulkerRules.writeContents(boxStack, List.of(new ItemStack(Items.STONE, 5)));
        StackKey kBox = new StackKey(boxStack);
        int hbBefore = kBox.hashCode();
        // mutate original box contents
        ShulkerRules.writeContents(boxStack, List.of(new ItemStack(Items.DIRT, 7)));
        int hbAfter = kBox.hashCode();
        System.out.println("mutate box nbt hashBefore=" + hbBefore + " hashAfter=" + hbAfter + " stable=" + (hbBefore==hbAfter) + " equalsAfterMutate=" + kBox.equals(new StackKey(boxStack)));

        // random fuzz for hash/equals contract across many stacks
        Item[] items = new Item[]{Items.DROPPER, Items.STONE, Items.DIRT, Items.ENDER_PEARL, Items.DIAMOND_SWORD, Items.HOPPER, Items.COMPOSTER, Items.BONE_BLOCK};
        Random r = new Random(42);
        List<StackKey> keys = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Item it = items[r.nextInt(items.length)];
            int max = new ItemStack(it).getMaxCount();
            ItemStack s = new ItemStack(it, 1 + r.nextInt(max));
            // sometimes put through box round-trip
            if (r.nextBoolean()) {
                ItemStack bx = new ItemStack(Items.SHULKER_BOX);
                ShulkerRules.writeContents(bx, List.of(s));
                List<ItemStack> rd = ShulkerRules.readContents(bx);
                if (!rd.isEmpty()) s = rd.get(0);
            }
            keys.add(new StackKey(s));
        }
        int violations = 0;
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i+1; j < keys.size(); j++) {
                if (keys.get(i).equals(keys.get(j)) && keys.get(i).hashCode() != keys.get(j).hashCode()) {
                    System.out.println("VIOLATION " + i + " vs " + j + " item=" + SortPlan.keyName(keys.get(i)));
                    violations++;
                    if (violations > 5) break;
                }
            }
            if (violations > 5) break;
        }
        System.out.println("hash contract violations=" + violations);
    }
}
