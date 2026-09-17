package com.yeyang.crossshulkersort.sort;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReserveDebugTest {
    private ReserveDebugTest() {}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
                .forEach(DataComponentInitializers.PendingComponents::apply);
        Class.forName(ServerSorter.class.getName(), true, ServerSorter.class.getClassLoader());

        Item[] check = new Item[]{Items.OAK_BOAT, Items.WATER_BUCKET,
                Items.MUSIC_DISC_CREATOR_MUSIC_BOX, Items.BOW, Items.DIAMOND_SWORD,
                Items.MINECART, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE,
                Items.SHEARS, Items.FLINT_AND_STEEL, Items.OAK_SIGN, Items.SNOWBALL,
                Items.ENDER_PEARL, Items.EGG, Items.STONE, Items.HOPPER};
        for (Item item : check) {
            System.out.println(" max[" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item) + "]="
                    + new ItemStack(item).getMaxStackSize());
        }

        // 6 boxes mirroring the live game: unstackables scattered, stackables bulk
        Item[] singles64 = new Item[]{Items.OAK_BUTTON, Items.OAK_FENCE, Items.STONE, Items.DIRT,
                Items.COBBLESTONE, Items.HOPPER, Items.BONE_BLOCK, Items.COMPOSTER};
        Item[] unstack = new Item[]{Items.OAK_BOAT, Items.WATER_BUCKET,
                Items.MUSIC_DISC_CREATOR_MUSIC_BOX, Items.BOW, Items.DIAMOND_SWORD,
                Items.MINECART, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE,
                Items.SHEARS, Items.FLINT_AND_STEEL};
        Inventory inventory = new Inventory(null, new EntityEquipment());
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
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
            inventory.setItem(10 + b, box);
        }
        // some loose stackables
        inventory.setItem(0, new ItemStack(Items.STONE, 64));
        inventory.setItem(1, new ItemStack(Items.DIRT, 32));
        SortPlan plan = SortPlan.compute(inventory);
        System.out.println("boxes=" + plan.boxes.size() + " chosen=" + plan.chosen.size()
                + " reservedBoxes=" + plan.reservedBoxes + " reservedInv=" + plan.reservedInv);
        for (int i = 0; i < plan.chosen.size(); i++) {
            SortPlan.BoxInfo box = plan.chosen.get(i);
            boolean res = plan.reservedInv.contains(box.invIndex);
            int n64 = 0, n1 = 0;
            for (Map.Entry<StackKey, Integer> e : box.quota.entrySet()) {
                if (e.getKey().stack().getMaxStackSize() <= 1) n1++;
                else n64++;
            }
            System.out.println(" chosen[" + i + "] inv=" + box.invIndex + " reserved=" + res
                    + " quotaTypes64=" + n64 + " quotaTypes1=" + n1);
        }
    }
}
