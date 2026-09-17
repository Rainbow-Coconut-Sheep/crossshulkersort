package com.yeyang.crossshulkersort.sort;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReserveDebugTest {
    private ReserveDebugTest() {}

    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        Class.forName(ServerSorter.class.getName(), true, ServerSorter.class.getClassLoader());

        Item[] check = new Item[]{Items.OAK_BOAT, Items.WATER_BUCKET,
                Items.MUSIC_DISC_PIGSTEP, Items.BOW, Items.DIAMOND_SWORD,
                Items.MINECART, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE,
                Items.SHEARS, Items.FLINT_AND_STEEL, Items.OAK_SIGN, Items.SNOWBALL,
                Items.ENDER_PEARL, Items.EGG, Items.STONE, Items.HOPPER};
        for (Item item : check) {
            System.out.println(" max[" + Registries.ITEM.getId(item) + "]="
                    + new ItemStack(item).getMaxCount());
        }

        // 6 boxes mirroring the live game: unstackables scattered, stackables bulk
        Item[] singles64 = new Item[]{Items.OAK_BUTTON, Items.OAK_FENCE, Items.STONE, Items.DIRT,
                Items.COBBLESTONE, Items.HOPPER, Items.BONE_BLOCK, Items.COMPOSTER};
        Item[] unstack = new Item[]{Items.OAK_BOAT, Items.WATER_BUCKET,
                Items.MUSIC_DISC_PIGSTEP, Items.BOW, Items.DIAMOND_SWORD,
                Items.MINECART, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE,
                Items.SHEARS, Items.FLINT_AND_STEEL};
        PlayerInventory inventory = new PlayerInventory(null);
        java.util.Random r = new java.util.Random(7);
        for (int b = 0; b < 6; b++) {
            List<ItemStack> contents = new ArrayList<>();
            // bulk stackables
            for (int i = 0; i < 10; i++) {
                contents.add(new ItemStack(singles64[r.nextInt(singles64.length)], 64));
            }
            // scattered unstackables
            contents.add(new ItemStack(unstack[r.nextInt(unstack.length) % unstack.length], 1));
            contents.add(new ItemStack(unstack[(r.nextInt(unstack.length) + 3) % unstack.length], 1));
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
            inventory.setStack(10 + b, box);
        }
        // some loose stackables
        inventory.setStack(0, new ItemStack(Items.STONE, 64));
        inventory.setStack(1, new ItemStack(Items.DIRT, 32));
        SortPlan plan = SortPlan.compute(inventory);
        System.out.println("boxes=" + plan.boxes.size() + " chosen=" + plan.chosen.size()
                + " reservedBoxes=" + plan.reservedBoxes + " reservedInv=" + plan.reservedInv);
        for (int i = 0; i < plan.chosen.size(); i++) {
            SortPlan.BoxInfo box = plan.chosen.get(i);
            boolean res = plan.reservedInv.contains(box.invIndex);
            int n64 = 0, n1 = 0;
            for (Map.Entry<StackKey, Integer> e : box.quota.entrySet()) {
                if (e.getKey().stack().getMaxCount() <= 1) n1++;
                else n64++;
            }
            System.out.println(" chosen[" + i + "] inv=" + box.invIndex + " reserved=" + res
                    + " quotaTypes64=" + n64 + " quotaTypes1=" + n1);
        }
    }
}