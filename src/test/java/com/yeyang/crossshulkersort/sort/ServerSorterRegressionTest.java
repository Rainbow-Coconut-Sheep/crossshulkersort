package com.yeyang.crossshulkersort.sort;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.EntityEquipment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ServerSorterRegressionTest {

    private static final List<String> failures = new ArrayList<>();
    private static int tests;

    private ServerSorterRegressionTest() {
    }

    public static void main(String[] args) throws ClassNotFoundException {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        Class.forName(ServerSorter.class.getName(), true, ServerSorter.class.getClassLoader());
        run("protected quota untouched", ServerSorterRegressionTest::protectedQuotaUntouched);
        run("surplus drains fully", ServerSorterRegressionTest::surplusDrainsFully);
        run("surplus partial across boxes", ServerSorterRegressionTest::surplusPartialAcrossBoxes);
        run("surplus exact across boxes", ServerSorterRegressionTest::surplusExactAcrossBoxes);
        run("unconsumed stacks", ServerSorterRegressionTest::unconsumedStacks);
        run("empty and zero budgets", ServerSorterRegressionTest::emptyAndZeroBudgets);
        run("different components", ServerSorterRegressionTest::differentComponents);
        run("dropper quota 213", ServerSorterRegressionTest::dropperQuota);
        run("SortPlan full home", () -> planningDropperQuota(24, false));
        run("SortPlan home with capacity", () -> planningDropperQuota(22, true));
        run("defrag pulls hopper home", ServerSorterRegressionTest::defragPullsHopperHome);
        run("top-up fills partial from loose", ServerSorterRegressionTest::topUpFillsPartialFromLoose);
        run("unstackables get own box", ServerSorterRegressionTest::unstackablesGetOwnBox);
        run("trimAppended multi-stack", ServerSorterRegressionTest::trimAppendedMultiStack);
        run("reservation prefers holders", ServerSorterRegressionTest::reservationPrefersHolders);
        run("bulk consolidates to two boxes", ServerSorterRegressionTest::bulkConsolidatesToTwoBoxes);
        run("bulk fragments stay put", ServerSorterRegressionTest::bulkFragmentsStayPut);
        run("occupancy counts physical", ServerSorterRegressionTest::occupancyCountsPhysical);
        run("signature detects change", ServerSorterRegressionTest::signatureDetectsChange);
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " regression failures in " + tests
                    + " tests:\n" + String.join("\n", failures));
        }
        System.out.println("ServerSorter regression tests passed: " + tests);
    }

    private static void run(String name, Runnable test) {
        tests++;
        int before = failures.size();
        try {
            test.run();
        } catch (AssertionError | RuntimeException e) {
            failures.add(e.toString());
        }
        for (int i = before; i < failures.size(); i++) {
            failures.set(i, name + ": " + failures.get(i));
        }
    }

    private static void protectedQuotaUntouched() {
        Fixture fixture = new Fixture(List.of(List.of(dropper(64)), List.of(dropper(21))));
        fixture.consume(List.of(Map.of(key(), 64), Map.<StackKey, Integer>of()), Map.of(key(), 9));
        fixture.expectCounts(0, 64);
        fixture.expectCounts(1, 12);
    }

    private static void surplusDrainsFully() {
        Fixture fixture = new Fixture(List.of(List.of(dropper(21)), List.of(dropper(64))));
        fixture.consume(List.of(Map.<StackKey, Integer>of(), Map.of(key(), 64)), Map.of(key(), 21));
        fixture.expectCounts(0);
        fixture.expectCounts(1, 64);
    }

    private static void surplusPartialAcrossBoxes() {
        Fixture fixture = new Fixture(List.of(
                List.of(dropper(8), dropper(12)),
                List.of(dropper(30)),
                List.of(dropper(64)),
                List.of(dropper(10))));
        fixture.consume(List.of(Map.<StackKey, Integer>of(), Map.<StackKey, Integer>of(),
                Map.of(key(), 64), Map.<StackKey, Integer>of()), Map.of(key(), 25));
        fixture.expectCounts(0);
        fixture.expectCounts(1, 25);
        fixture.expectCounts(2, 64);
        fixture.expectCounts(3, 10);
    }

    private static void surplusExactAcrossBoxes() {
        Fixture fixture = new Fixture(List.of(
                List.of(dropper(8), dropper(12)),
                List.of(dropper(64)),
                List.of(dropper(30))));
        fixture.consume(List.of(Map.<StackKey, Integer>of(), Map.of(key(), 64),
                Map.<StackKey, Integer>of()), Map.of(key(), 50));
        fixture.expectCounts(0);
        fixture.expectCounts(1, 64);
        fixture.expectCounts(2);
    }

    private static void unconsumedStacks() {
        ItemStack stone = new ItemStack(Items.STONE, 17);
        ItemStack nested = new ItemStack(Items.SHULKER_BOX);
        ItemStack unassigned = new ItemStack(Items.DIRT, 11);
        Fixture fixture = new Fixture(List.of(
                List.of(dropper(21), stone, nested, unassigned),
                List.of(dropper(64))));
        fixture.consume(List.of(Map.of(new StackKey(stone), 17), Map.of(key(), 64)),
                Map.of(key(), 9, new StackKey(nested), 1));
        fixture.expectCounts(0, 12, 17, 1, 11);
        fixture.expectCounts(1, 64);
        fixture.expectKey(0, 1, new StackKey(stone));
        fixture.expectKey(0, 2, new StackKey(nested));
        fixture.expectKey(0, 3, new StackKey(unassigned));
    }

    private static void emptyAndZeroBudgets() {
        Fixture fixture = new Fixture(List.of(
                List.of(ItemStack.EMPTY, dropper(21)), List.of(), List.of(dropper(64))));
        fixture.consume(List.of(Map.<StackKey, Integer>of(), Map.<StackKey, Integer>of(),
                Map.of(key(), 64)), Map.of());
        fixture.expectCounts(0, 0, 21);
        fixture.expectCounts(1);
        fixture.expectCounts(2, 64);
        fixture.consume(List.of(Map.<StackKey, Integer>of(), Map.<StackKey, Integer>of(),
                Map.of(key(), 64)), Map.of(key(), 0));
        fixture.expectCounts(0, 0, 21);
        fixture.expectCounts(1);
        fixture.expectCounts(2, 64);
        new Fixture(List.of()).consume(List.of(), Map.of());
    }

    private static void differentComponents() {
        ItemStack namedHome = dropper(40);
        namedHome.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Named dropper"));
        ItemStack namedMoved = namedHome.copyWithCount(15);
        StackKey namedKey = new StackKey(namedHome);
        check(!key().equals(namedKey), "component variants must have different keys");
        Fixture fixture = new Fixture(List.of(
                List.of(dropper(64), namedMoved),
                List.of(dropper(21), namedHome)));
        fixture.consume(List.of(Map.of(key(), 64), Map.of(namedKey, 40)),
                Map.of(key(), 9, namedKey, 5));
        fixture.expectCounts(0, 64, 10);
        fixture.expectCounts(1, 12, 40);
        fixture.expectKey(0, 0, key());
        fixture.expectKey(0, 1, namedKey);
        fixture.expectKey(1, 0, key());
        fixture.expectKey(1, 1, namedKey);
    }

    private static void dropperQuota() {
        Fixture fixture = new Fixture(List.of(
                List.of(dropper(64), dropper(64), dropper(21), dropper(64)), List.of(dropper(21))));
        check(total(fixture.baseline.get(0)) + total(fixture.baseline.get(1)) == 234,
                "fixture totals must be 234");
        fixture.consume(List.of(Map.of(key(), 213), Map.<StackKey, Integer>of()), Map.of(key(), 21));
        fixture.expectCounts(0, 64, 64, 21, 64);
        fixture.expectCounts(1);
        check(total(fixture.kept.get(0)) == 213, "box must retain its 213 quota");
        check(total(fixture.kept.get(1)) == 0, "surplus box must be fully drained");
        check(total(fixture.baseline.get(0)) == 213, "home baseline must remain 213");
        check(total(fixture.baseline.get(1)) == 21, "moved baseline must remain 21");
    }

    private static void planningDropperQuota(int stoneStacks, boolean hasCapacity) {
        List<ItemStack> home = new ArrayList<>(List.of(dropper(64), dropper(64), dropper(21)));
        for (int i = 0; i < stoneStacks; i++) {
            home.add(new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> nonhome = List.of(dropper(21),
                new ItemStack(Items.DIRT, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.GRANITE, 64), new ItemStack(Items.DIORITE, 64));
        PlayerInventory inventory = new PlayerInventory(null, new EntityEquipment());
        inventory.setStack(0, box(home));
        inventory.setStack(1, box(nonhome));
        inventory.setStack(2, dropper(43));
        List<ItemStack> inventoryBaseline = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            inventoryBaseline.add(inventory.getStack(i).copy());
        }
        List<ItemStack> homeBaseline = ShulkerRules.readContents(inventoryBaseline.get(0));
        List<ItemStack> nonhomeBaseline = ShulkerRules.readContents(inventoryBaseline.get(1));
        StackKey dropperKey = key();
        Map<StackKey, Integer> pool = new HashMap<>();
        for (ItemStack stack : homeBaseline) {
            pool.merge(new StackKey(stack), stack.getCount(), Integer::sum);
        }
        for (ItemStack stack : nonhomeBaseline) {
            pool.merge(new StackKey(stack), stack.getCount(), Integer::sum);
        }
        pool.merge(new StackKey(inventoryBaseline.get(2)), inventoryBaseline.get(2).getCount(), Integer::sum);
        int totalStacks = 0;
        for (Map.Entry<StackKey, Integer> entry : pool.entrySet()) {
            int max = entry.getKey().stack().getMaxCount();
            totalStacks += (entry.getValue() + max - 1) / max;
        }
        check(totalStacks > 27, "fixture pool must require two boxes, got " + totalStacks + " stacks");
        check(homeBaseline.size() == stoneStacks + 3, "unexpected home occupancy");
        check(count(homeBaseline, dropperKey) == 149, "fixture home must contain 149 droppers");
        check(count(nonhomeBaseline, dropperKey) == 21, "fixture nonhome must contain 21 droppers");
        check(pool.getOrDefault(dropperKey, 0) == 213, "source snapshot pool must contain 213 droppers");
        for (int slot = 0; slot < 2; slot++) {
            check(ShulkerRules.isEligibleBox(inventory.getStack(slot)), "fixture box must be eligible: " + slot);
            check(!ShulkerRules.isLockedFull(inventory.getStack(slot)), "fixture box must not be locked: " + slot);
        }
        SortPlan plan = SortPlan.compute(inventory);
        check(plan.boxes.size() == 2, "planner must detect two eligible boxes");
        check(plan.chosen.size() == 2, "planner must choose both boxes");
        check(plan.excess.isEmpty(), "neither source box may be excess");
        Map<StackKey, Integer> quotaTotals = new HashMap<>();
        int homeQuota = 0;
        int nonhomeQuota = 0;
        int sourceHome = 0;
        int sourceNonhome = 0;
        for (SortPlan.BoxInfo chosen : plan.chosen) {
            for (Map.Entry<StackKey, Integer> entry : chosen.quota.entrySet()) {
                quotaTotals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            if (chosen.invIndex == 0) {
                homeQuota = chosen.quota.getOrDefault(dropperKey, 0);
                sourceHome = count(chosen.contents, dropperKey);
            } else if (chosen.invIndex == 1) {
                nonhomeQuota = chosen.quota.getOrDefault(dropperKey, 0);
                sourceNonhome = count(chosen.contents, dropperKey);
            }
        }
        int quotaSum = quotaTotals.getOrDefault(dropperKey, 0);
        System.out.println("SortPlan homeSlots=" + homeBaseline.size() + ", poolStacks=" + totalStacks
                + ", chosen=" + plan.chosen.size() + ", dropperPool=" + pool.get(dropperKey)
                + ", homeQuota=" + homeQuota + ", nonhomeQuota=" + nonhomeQuota
                + ", quotaSum=" + quotaSum + ", sourceHome=" + sourceHome
                + ", sourceNonhome=" + sourceNonhome + ", loose=" + inventory.getStack(2).getCount());
        check(quotaSum <= 213, "dropper quota exceeds source pool: " + quotaSum);
        for (Map.Entry<StackKey, Integer> entry : quotaTotals.entrySet()) {
            check(entry.getValue() >= 0 && entry.getValue() <= pool.getOrDefault(entry.getKey(), 0),
                    "quota outside source pool for " + SortPlan.keyName(entry.getKey()));
        }
        if (hasCapacity) {
            check(quotaSum == 213, "home with capacity must receive all 213 droppers, got " + quotaSum);
            check(homeQuota == 213, "only home must own the 213-dropper quota, got " + homeQuota);
            check(nonhomeQuota == 0, "nonhome must not retain a dropper quota, got " + nonhomeQuota);
        } else {
            // home is full (27 stacks): overflow spills into the next box instead of
            // staying loose while empty slots exist
            check(quotaSum == 213, "full home must spill 213 droppers across boxes, got " + quotaSum);
            check(homeQuota == 149, "full home keeps its 149 droppers, got " + homeQuota);
            check(nonhomeQuota == 64, "spill box must take the remaining 64 droppers, got " + nonhomeQuota);
        }
        check(sourceHome == 149, "planner home snapshot must remain 149, got " + sourceHome);
        check(sourceNonhome == 21, "planner nonhome snapshot must remain 21, got " + sourceNonhome);
        check(sourceHome + sourceNonhome + inventory.getStack(2).getCount() == 213,
                "planner source snapshots plus loose droppers must remain 213");
        check(count(homeBaseline, dropperKey) == 149, "independent home baseline changed");
        check(count(nonhomeBaseline, dropperKey) == 21, "independent nonhome baseline changed");
        check(count(ShulkerRules.readContents(inventory.getStack(0)), dropperKey) == 149,
                "live home contents changed during planning");
        check(count(ShulkerRules.readContents(inventory.getStack(1)), dropperKey) == 21,
                "live nonhome contents changed during planning");
        for (int i = 0; i < 36; i++) {
            check(SortPlan.sameStackExact(inventory.getStack(i), inventoryBaseline.get(i)),
                    "planning changed inventory item/components/count at slot " + i);
        }
    }

    private static void defragPullsHopperHome() {
        Item[] singles = new Item[]{Items.OAK_BUTTON, Items.OAK_FENCE, Items.BAMBOO_FENCE_GATE,
                Items.SOUL_SAND, Items.BONE, Items.OAK_TRAPDOOR, Items.OAK_SIGN, Items.TARGET,
                Items.OAK_SIGN, Items.GUNPOWDER, Items.GOLD_NUGGET, Items.PHANTOM_MEMBRANE,
                Items.LEAD, Items.OAK_DOOR, Items.OAK_SLAB, Items.OAK_PRESSURE_PLATE,
                Items.POTATO, Items.IRON_NUGGET, Items.SNOWBALL, Items.ROTTEN_FLESH,
                Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL};
        List<ItemStack> home = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            home.add(new ItemStack(Items.HOPPER, 64));
        }
        for (Item single : singles) {
            home.add(new ItemStack(single, 1));
        }
        List<ItemStack> other = new ArrayList<>(List.of(
                new ItemStack(Items.HOPPER, 64), new ItemStack(Items.HOPPER, 57),
                new ItemStack(Items.DIRT, 64)));
        PlayerInventory inventory = new PlayerInventory(null, new EntityEquipment());
        inventory.setStack(0, box(home));
        inventory.setStack(1, box(other));
        SortPlan plan = SortPlan.compute(inventory);
        check(plan.chosen.size() == 2, "defrag fixture must choose both boxes, got " + plan.chosen.size());
        StackKey hopperKey = new StackKey(new ItemStack(Items.HOPPER, 1));
        Map<StackKey, Integer> totals = new HashMap<>();
        Map<StackKey, Integer> boxCount = new HashMap<>();
        int homeQuota = -1;
        int otherQuota = -1;
        for (SortPlan.BoxInfo chosen : plan.chosen) {
            for (Map.Entry<StackKey, Integer> entry : chosen.quota.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
                boxCount.merge(entry.getKey(), 1, Integer::sum);
            }
            if (chosen.invIndex == 0) {
                homeQuota = chosen.quota.getOrDefault(hopperKey, 0);
            } else if (chosen.invIndex == 1) {
                otherQuota = chosen.quota.getOrDefault(hopperKey, 0);
            }
        }
        System.out.println("defrag homeQuota=" + homeQuota + " otherQuota=" + otherQuota);
        check(homeQuota == 441, "hopper must consolidate to 441 in home, got " + homeQuota);
        check(otherQuota == 0, "spill box must release hoppers, got " + otherQuota);
        check(totals.getOrDefault(hopperKey, 0) == 441, "hopper total must stay 441");
        for (Map.Entry<StackKey, Integer> entry : totals.entrySet()) {
            check(boxCount.getOrDefault(entry.getKey(), 0) == 1,
                    "type split after defrag: " + SortPlan.keyName(entry.getKey()));
        }
    }

    private static void topUpFillsPartialFromLoose() {
        Item[] singles = new Item[]{Items.OAK_BUTTON, Items.OAK_FENCE, Items.BAMBOO_FENCE_GATE,
                Items.SOUL_SAND, Items.BONE, Items.OAK_TRAPDOOR, Items.OAK_SIGN, Items.TARGET,
                Items.OAK_SIGN, Items.GUNPOWDER, Items.GOLD_NUGGET, Items.PHANTOM_MEMBRANE,
                Items.LEAD, Items.OAK_BOAT, Items.MUSIC_DISC_PIGSTEP, Items.DIAMOND_SWORD,
                Items.POTATO, Items.IRON_NUGGET, Items.SNOWBALL, Items.ROTTEN_FLESH,
                Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL, Items.OAK_DOOR, Items.OAK_SLAB,
                Items.OAK_STAIRS, Items.OAK_PRESSURE_PLATE};
        List<ItemStack> home = new ArrayList<>(List.of(new ItemStack(Items.TRIPWIRE_HOOK, 62)));
        for (Item single : singles) {
            home.add(new ItemStack(single, 1));
        }
        PlayerInventory inventory = new PlayerInventory(null, new EntityEquipment());
        inventory.setStack(0, box(home));
        inventory.setStack(2, new ItemStack(Items.TRIPWIRE_HOOK, 36));
        SortPlan plan = SortPlan.compute(inventory);
        check(plan.chosen.size() == 1, "top-up fixture must choose one box, got " + plan.chosen.size());
        StackKey hookKey = new StackKey(new ItemStack(Items.TRIPWIRE_HOOK, 1));
        int quota = plan.chosen.get(0).quota.getOrDefault(hookKey, -1);
        System.out.println("top-up hookQuota=" + quota);
        check(quota == 64, "partial 62 must top up to 64 from loose 36, got " + quota);
    }

    private static void unstackablesGetOwnBox() {
        List<ItemStack> mixed = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mixed.add(new ItemStack(Items.DIAMOND_SWORD, 1));
        }
        for (int i = 0; i < 20; i++) {
            mixed.add(new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> other = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            other.add(new ItemStack(Items.STONE, 64));
        }
        PlayerInventory inventory = new PlayerInventory(null, new EntityEquipment());
        inventory.setStack(0, box(mixed));
        inventory.setStack(1, box(other));
        SortPlan plan = SortPlan.compute(inventory);
        check(plan.chosen.size() == 2, "fixture must choose both boxes, got " + plan.chosen.size());
        check(plan.reservedBoxes == 1, "one trailing box must reserve for unstackables, got " + plan.reservedBoxes);
        StackKey swordKey = new StackKey(new ItemStack(Items.DIAMOND_SWORD, 1));
        int swordBox = -1;
        for (int i = 0; i < plan.chosen.size(); i++) {
            SortPlan.BoxInfo chosen = plan.chosen.get(i);
            boolean isRes = plan.reservedInv.contains(chosen.invIndex);
            boolean hasSword = chosen.quota.getOrDefault(swordKey, 0) > 0;
            boolean hasStackable = false;
            boolean hasUnstackable = false;
            for (StackKey key : chosen.quota.keySet()) {
                if (ShulkerRules.isShulkerBoxItem(key.stack())) {
                    continue;
                }
                if (key.stack().getMaxCount() <= 1) {
                    hasUnstackable = true;
                } else {
                    hasStackable = true;
                }
            }
            check(!(hasStackable && hasUnstackable),
                    "box " + chosen.invIndex + " mixes stackables with unstackables");
            if (hasSword) {
                check(isRes, "swords must live in the reserved box, got " + chosen.invIndex);
                check(swordBox < 0, "swords split across boxes");
                swordBox = i;
            }
        }
        int swords = 0;
        for (SortPlan.BoxInfo chosen : plan.chosen) {
            swords += chosen.quota.getOrDefault(swordKey, 0);
        }
        check(swords == 3, "all 3 swords must be quoted, got " + swords);
        check(swordBox >= 0, "swords must be quoted somewhere");
    }

    private static void trimAppendedMultiStack() {
        StackKey tnt = new StackKey(new ItemStack(Items.TNT, 1));
        List<List<ItemStack>> appended = new ArrayList<>();
        appended.add(new ArrayList<>(List.of(new ItemStack(Items.TNT, 64), new ItemStack(Items.TNT, 46))));
        appended.add(new ArrayList<>(List.of(new ItemStack(Items.STONE, 64))));
        int removed = ServerSorter.trimAppended(appended, tnt, 110);
        check(removed == 110, "must trim both tnt stacks fully, removed " + removed);
        check(appended.get(0).isEmpty(), "tnt stacks must both be gone");
        check(appended.get(1).size() == 1, "stone stack must survive trimming");
        List<List<ItemStack>> partial = new ArrayList<>();
        partial.add(new ArrayList<>(List.of(
                new ItemStack(Items.TNT, 64), new ItemStack(Items.TNT, 64), new ItemStack(Items.TNT, 64))));
        int removed2 = ServerSorter.trimAppended(partial, tnt, 100);
        int left = partial.get(0).stream().mapToInt(ItemStack::getCount).sum();
        check(removed2 == 100 && left == 92, "partial trim must take 100 leaving 92, got " + removed2 + "/" + left);
    }

    private static void reservationPrefersHolders() {
        // high-slot box full of stackable homes must NOT be reserved over an emptier
        // box that already holds the unstackables (regression: max-slot rule once
        // evicted a whole stackable home into work and doubled it downstream)
        List<ItemStack> full = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            full.add(new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> holder = new ArrayList<>(List.of(
                new ItemStack(Items.DIAMOND_SWORD, 1), new ItemStack(Items.DIAMOND_SWORD, 1),
                new ItemStack(Items.STONE, 64), new ItemStack(Items.STONE, 64)));
        PlayerInventory inventory = new PlayerInventory(null, new EntityEquipment());
        inventory.setStack(5, box(full));
        inventory.setStack(1, box(holder));
        SortPlan plan = SortPlan.compute(inventory);
        check(plan.chosen.size() == 2, "fixture must choose both boxes, got " + plan.chosen.size());
        check(plan.reservedBoxes == 1, "one box must reserve, got " + plan.reservedBoxes);
        check(plan.reservedInv.contains(1) && !plan.reservedInv.contains(5),
                "reservation must pick the unstackable holder (1), got " + plan.reservedInv);
    }

    private static void occupancyCountsPhysical() {
        // two partial stacks of one type occupy 2 slots even though quotas say 1
        SortPlan.BoxInfo box = new SortPlan.BoxInfo(0, 5, new ItemStack(Items.SHULKER_BOX),
                List.of(new ItemStack(Items.STONE, 30), new ItemStack(Items.STONE, 30),
                        new ItemStack(Items.DIRT, 64)));
        box.quota.put(new StackKey(new ItemStack(Items.STONE, 1)), 60);
        box.quota.put(new StackKey(new ItemStack(Items.DIRT, 1)), 64);
        check(SortPlan.occupancyOf(box) == 3, "physical 3 beats quota 2, got " + SortPlan.occupancyOf(box));
        box.quota.put(new StackKey(new ItemStack(Items.DIRT, 1)), 64 + 64);
        check(SortPlan.occupancyOf(box) == 3, "quota 3 ties physical 3");
    }

    private static void signatureDetectsChange() {
        List<ItemStack> inv = List.of(new ItemStack(Items.STONE, 64), ItemStack.EMPTY);
        List<List<ItemStack>> boxes = List.of(List.of(new ItemStack(Items.DIRT, 11)));
        String a = ServerSorter.signature(new ArrayList<>(inv), new ArrayList<>(boxes));
        String b = ServerSorter.signature(new ArrayList<>(inv), new ArrayList<>(boxes));
        check(a.equals(b), "identical states must fingerprint equal");
        List<ItemStack> moved = new ArrayList<>(List.of(new ItemStack(Items.STONE, 63), ItemStack.EMPTY));
        check(!ServerSorter.signature(moved, new ArrayList<>(boxes)).equals(a),
                "changed counts must fingerprint different");
    }

    private static void bulkConsolidatesToTwoBoxes() {
        // 52 stacks of one bulk type scattered 25/1/1/24/1 must end in 2 boxes
        List<ItemStack> a = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            a.add(new ItemStack(Items.HOPPER, 64));
        }
        a.add(new ItemStack(Items.COAL, 64));
        a.add(new ItemStack(Items.IRON_INGOT, 64));
        List<ItemStack> b = new ArrayList<>(List.of(
                new ItemStack(Items.HOPPER, 64), new ItemStack(Items.STONE, 64)));
        for (int i = 0; i < 19; i++) {
            b.add(new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> c = new ArrayList<>(List.of(new ItemStack(Items.HOPPER, 64)));
        for (int i = 0; i < 20; i++) {
            c.add(new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> d = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            d.add(new ItemStack(Items.HOPPER, 64));
        }
        d.add(new ItemStack(Items.GOLD_INGOT, 64));
        List<ItemStack> e = new ArrayList<>(List.of(new ItemStack(Items.HOPPER, 64)));
        for (int i = 0; i < 20; i++) {
            e.add(new ItemStack(Items.STONE, 64));
        }
        PlayerInventory inventory = new PlayerInventory(null, new EntityEquipment());
        inventory.setStack(0, box(a));
        inventory.setStack(1, box(b));
        inventory.setStack(2, box(c));
        inventory.setStack(3, box(d));
        inventory.setStack(4, box(e));
        SortPlan plan = SortPlan.compute(inventory);
        check(plan.chosen.size() == 5, "fixture must choose all five boxes, got " + plan.chosen.size());
        StackKey bulk = new StackKey(new ItemStack(Items.HOPPER, 1));
        int total = 0;
        int boxes = 0;
        for (SortPlan.BoxInfo chosen : plan.chosen) {
            int q = chosen.quota.getOrDefault(bulk, 0);
            if (q > 0) {
                boxes++;
                total += q;
            }
            check(SortPlan.occupancyOf(chosen) <= ShulkerRules.BOX_SLOTS,
                    "box " + chosen.invIndex + " overfull");
        }
        check(total == 52 * 64, "bulk total must stay 3328, got " + total);
        check(boxes == 2, "bulk must consolidate into 2 boxes, got " + boxes);
    }

    private static void bulkFragmentsStayPut() {
        // bulk (>27 stacks) fragments are exempt from home-healing: every box keeps
        // what it holds instead of evicting "non-home" shares every sort
        List<ItemStack> a = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            a.add(new ItemStack(Items.HOPPER, 64));
        }
        a.add(new ItemStack(Items.COAL, 64));
        List<ItemStack> b = new ArrayList<>(List.of(
                new ItemStack(Items.HOPPER, 64), new ItemStack(Items.HOPPER, 64)));
        for (int i = 0; i < 10; i++) {
            b.add(new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> c = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            c.add(new ItemStack(Items.STONE, 64));
        }
        PlayerInventory inventory = new PlayerInventory(null, new EntityEquipment());
        inventory.setStack(0, box(a));
        inventory.setStack(1, box(b));
        inventory.setStack(2, box(c));
        SortPlan plan = SortPlan.compute(inventory);
        check(plan.chosen.size() == 3, "fixture must choose all three boxes, got " + plan.chosen.size());
        StackKey bulk = new StackKey(new ItemStack(Items.HOPPER, 1));
        int boxes = 0;
        for (SortPlan.BoxInfo chosen : plan.chosen) {
            int q = chosen.quota.getOrDefault(bulk, 0);
            if (q > 0) {
                boxes++;
            }
        }
        check(boxes == 2, "bulk must stay in its 2 fragment boxes, got " + boxes);
        int quotaA = -1;
        int quotaB = -1;
        for (SortPlan.BoxInfo chosen : plan.chosen) {
            if (chosen.invIndex == 0) {
                quotaA = chosen.quota.getOrDefault(bulk, 0);
            } else if (chosen.invIndex == 1) {
                quotaB = chosen.quota.getOrDefault(bulk, 0);
            }
        }
        check(quotaA == 26 * 64, "first box must keep its 1664 bulk fragment, got " + quotaA);
        check(quotaB == 2 * 64, "second box must keep its 128 fragment instead of evicting it, got " + quotaB);
    }

    private static ItemStack box(List<ItemStack> contents) {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
        return stack;
    }

    private static int count(List<ItemStack> stacks, StackKey key) {
        return stacks.stream().filter(stack -> !stack.isEmpty() && new StackKey(stack).equals(key))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static ItemStack dropper(int count) {
        return new ItemStack(Items.DROPPER, count);
    }

    private static StackKey key() {
        return new StackKey(dropper(1));
    }

    private static int total(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }

    private static final class Fixture {

        private final List<List<ItemStack>> baseline = new ArrayList<>();
        private final List<List<ItemStack>> kept = new ArrayList<>();
        private final List<List<Integer>> counts = new ArrayList<>();
        private final List<List<StackKey>> keys = new ArrayList<>();

        private Fixture(List<List<ItemStack>> boxes) {
            for (List<ItemStack> box : boxes) {
                baseline.add(new ArrayList<>(box));
                kept.add(new ArrayList<>(box));
                counts.add(box.stream().map(ItemStack::getCount).toList());
                keys.add(box.stream().map(StackKey::new).toList());
            }
        }

        private void consume(List<Map<StackKey, Integer>> quotas, Map<StackKey, Integer> budget) {
            List<Map<StackKey, Integer>> quotaCopy = new ArrayList<>();
            for (Map<StackKey, Integer> q : quotas) {
                quotaCopy.add(new HashMap<>(q));
            }
            Map<StackKey, Integer> budgetCopy = new HashMap<>(budget);
            try {
                ServerSorter.drainSurplus(kept, quotas, budget);
            } finally {
                check(budget.equals(budgetCopy), "consumption budget map must not change");
                check(quotas.equals(quotaCopy), "quota maps must not change");
                for (int b = 0; b < baseline.size(); b++) {
                    for (int i = 0; i < baseline.get(b).size(); i++) {
                        ItemStack stack = baseline.get(b).get(i);
                        check(stack.getCount() == counts.get(b).get(i),
                                "baseline count changed at box " + b + " stack " + i
                                        + ": expected " + counts.get(b).get(i) + ", got " + stack.getCount());
                        check(new StackKey(stack).equals(keys.get(b).get(i)),
                                "baseline item/components changed at box " + b + " stack " + i);
                    }
                }
            }
        }

        private void expectCounts(int box, int... expected) {
            List<Integer> actual = kept.get(box).stream().map(ItemStack::getCount).toList();
            List<Integer> wanted = new ArrayList<>();
            for (int count : expected) {
                wanted.add(count);
            }
            check(actual.equals(wanted), "box " + box + ": expected " + wanted + ", got " + actual);
        }

        private void expectKey(int box, int slot, StackKey expected) {
            check(slot < kept.get(box).size()
                            && new StackKey(kept.get(box).get(slot)).equals(expected),
                    "unexpected item/components at box " + box + " stack " + slot);
        }
    }
}
