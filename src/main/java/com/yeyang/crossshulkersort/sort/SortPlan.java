package com.yeyang.crossshulkersort.sort;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global plan for one sort run, computed once from a client-side snapshot.
 *
 * Layout policy (priorities in this order):
 * <ol>
 *     <li><b>Strict sequential fill</b> - chosen boxes are filled one at a time, in fill
 *     order: the current box is filled to exactly 27 stacks before the next box receives
 *     anything;</li>
 *     <li><b>Unsplit types</b> - a type is placed whole into the current box whenever it
 *     fits the remaining capacity; only when nothing fits is the smallest remaining type
 *     split across the box boundary (smallest possible fragment);</li>
 *     <li><b>Typed boxes</b> - types are packed in size order, so each box ends up
 *     "specialized" in the types assigned to it (item-type classification).</li>
 * </ol>
 * Chosen boxes KEEP the content they already hold (it counts towards their 27 stacks and
 * is seeded as quota), so nothing is shuffled out just to be shuffled back. The output is
 * a per-box, per-type <b>quota in exact item counts</b>; the engine tops every box up to
 * (or trims down to) its quota and nothing beyond it, which is what makes the strict
 * order enforceable even when stack sizes do not divide evenly. Non-chosen ("excess")
 * boxes should end up empty.
 */
public final class SortPlan {

    /** Active stack order; the client injects Item Scroller's mirrored comparator. */
    public static Comparator<ItemStack> SORT_ORDER = Comparator
            .comparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
            .thenComparing(s -> s.getComponents().hashCode())
            .thenComparing(s -> -s.getCount());

    /** Called from the client entrypoint so in-game sorts mirror Item Scroller's order. */
    public static void setSortOrder(Comparator<ItemStack> comparator) {
        if (comparator != null) {
            SORT_ORDER = comparator;
        }
    }

    public final List<BoxInfo> boxes = new ArrayList<>();
    public final List<BoxInfo> chosen = new ArrayList<>();
    public final List<BoxInfo> excess = new ArrayList<>();
    /**
     * Trailing chosen boxes reserved for unstackables (max stack size 1). Count, not
     * indices: reserved boxes are {@code chosen[size-reservedBoxes .. size]}. Zero
     * means no reservation (too few boxes or no unstackables) and everything mixes.
     */
    public int reservedBoxes = 0;
    /**
     * Inventory slots of the reserved boxes. Membership is by slot (boxes never move
     * slots during a sort), NOT by fill-order position - fill order flips as boxes
     * fill and empty across passes, which used to bounce the reservation between
     * boxes and shuttle unstackables back and forth forever.
     */
    public final java.util.Set<Integer> reservedInv = new java.util.HashSet<>();
    public boolean hasWork = false;

    /** Fallback bulk threshold when no config is loaded (dedicated boot). */
    public static final int BULK_MIN_STACKS = 6;

    public static SortPlan compute(Inventory inv) {
        return compute(inv, null);
    }

    /**
     * @param detected ground-truth contents per box inv slot, read from the live menus
     *                 during the detection phase (null = read item components instead)
     */
    public static SortPlan compute(Inventory inv, Map<Integer, List<ItemStack>> detected) {
        SortPlan plan = new SortPlan();
        com.yeyang.crossshulkersort.config.ModConfig eff =
                com.yeyang.crossshulkersort.config.ModConfig.effective();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (ShulkerRules.isUsableBox(stack)) {
                List<ItemStack> detectedContents = detected != null ? detected.get(i) : null;
                plan.boxes.add(new BoxInfo(plan.boxes.size(), i, stack,
                        detectedContents != null ? detectedContents : ShulkerRules.readContents(stack)));
            }
        }
        if (plan.boxes.isEmpty()) {
            return plan;
        }

        // ---- pool of every item that may move: loose stacks + contents of eligible boxes
        //      (shulker box items are containers, never sortable cargo)
        Set<Integer> boxSlots = new HashSet<>();
        for (BoxInfo box : plan.boxes) {
            boxSlots.add(box.invIndex);
        }
        Map<StackKey, Integer> poolItems = new LinkedHashMap<>(); // total item count per type
        for (int i = 0; i < 36; i++) {
            if (boxSlots.contains(i)) {
                continue;
            }
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && !ShulkerRules.isShulkerBoxItem(stack)) {
                poolItems.merge(new StackKey(stack), stack.getCount(), Integer::sum);
            }
        }
        for (BoxInfo box : plan.boxes) {
            box.refreshContents(inv);
            for (ItemStack stack : box.contents) {
                if (!stack.isEmpty() && !ShulkerRules.isShulkerBoxItem(stack)) {
                    poolItems.merge(new StackKey(stack), stack.getCount(), Integer::sum);
                }
            }
        }

        record KeyNeed(StackKey key, int items, int max) {
        }
        List<KeyNeed> needs = new ArrayList<>();
        int totalStacks = 0;
        for (Map.Entry<StackKey, Integer> entry : poolItems.entrySet()) {
            ItemStack proto = entry.getKey().stack();
            int max = proto.getMaxStackSize();
            int items = entry.getValue();
            needs.add(new KeyNeed(entry.getKey(), items, max));
            totalStacks += (items + max - 1) / max;
        }

        // ---- pick the boxes to fill: fullest first, then lowest inventory slot
        List<BoxInfo> order = new ArrayList<>(plan.boxes);
        order.sort(Comparator.comparingInt((BoxInfo b) -> -b.initialFilled).thenComparingInt(b -> b.invIndex));
        int wantBoxes = totalStacks == 0
                ? 0
                : Math.min((totalStacks + ShulkerRules.BOX_SLOTS - 1) / ShulkerRules.BOX_SLOTS, plan.boxes.size());
        for (int k = 0; k < order.size(); k++) {
            BoxInfo box = order.get(k);
            if (k < wantBoxes) {
                plan.chosen.add(box);
            } else {
                plan.excess.add(box);
            }
        }

        // ---- unstackable-only boxes: unstackables (max stack size 1) live in their
        // own trailing boxes when capacity allows, never mixed with stackables.
        // Seeding keeps group-mismatched content out (it becomes moving items) and
        // every placement filter below enforces the same split.
        int ustacks = 0;
        for (KeyNeed need : needs) {
            if (need.max() <= 1) {
                ustacks += need.items(); // one slot per item
            }
        }
        if (eff.reservationEnabled && ustacks > 0 && plan.chosen.size() >= 2) {
            plan.reservedBoxes = Math.min(plan.chosen.size() - 1,
                    (ustacks + ShulkerRules.BOX_SLOTS - 1) / ShulkerRules.BOX_SLOTS);
        }
        if (plan.reservedBoxes > 0) {
            // Score, not slot: boxes already holding unstackables first (zero-move
            // keeps), then emptier boxes (least eviction), then highest slot as the
            // stable tiebreak. Never blindly take the max slot - it may be a full
            // stackable-home box whose entire contents would be evicted into work.
            List<BoxInfo> resOrder = new ArrayList<>(plan.chosen);
            Map<Integer, Integer> unstackHeld = new HashMap<>();
            for (BoxInfo box : plan.chosen) {
                int n = 0;
                for (ItemStack stack : box.contents) {
                    if (!stack.isEmpty() && !ShulkerRules.isShulkerBoxItem(stack)
                            && stack.getMaxStackSize() <= 1) {
                        n += stack.getCount();
                    }
                }
                unstackHeld.put(box.invIndex, n);
            }
            resOrder.sort(Comparator
                    .comparingInt((BoxInfo x) -> -unstackHeld.getOrDefault(x.invIndex, 0))
                    .thenComparingInt(x -> x.initialFilled)
                    .thenComparingInt(x -> -x.invIndex));
            for (int k = 0; k < plan.reservedBoxes; k++) {
                plan.reservedInv.add(resOrder.get(k).invIndex);
            }
        }
        diag("[CSSort] plan boxes=" + plan.boxes.size() + " chosen=" + plan.chosen.size()
                + " excess=" + plan.excess.size() + " reserved=" + plan.reservedBoxes
                + " ustacks=" + ustacks);

        // ---- occupancy-aware sequential fill (priorities: full boxes > unsplit types >
        // typed boxes). Every chosen box KEEPS its own content (seeded as quota) and its
        // free slots are then filled - box by box, in fill order, to exactly 27 stacks -
        // with the items that must move (loose items + excess box contents): types the
        // box already holds are topped up first, then the largest fitting type; only
        // when nothing fits is the largest remaining type split across the boundary
        // (the smallest possible fragment), which keeps every box except the last one
        // exactly full.
        // home-box healing: a type held in SEVERAL chosen boxes designates the box with
        // the most of it as its home; the other boxes' stacks become moving items so an
        // existing split heals instead of being preserved forever.
        Map<StackKey, Integer> bestCount = new HashMap<>();
        Map<StackKey, Integer> homeIdx = new HashMap<>();
        for (int b = 0; b < plan.chosen.size(); b++) {
            Map<StackKey, Integer> counts = new HashMap<>();
            for (ItemStack stack : plan.chosen.get(b).contents) {
                if (!stack.isEmpty() && !ShulkerRules.isShulkerBoxItem(stack)) {
                    counts.merge(new StackKey(stack), stack.getCount(), Integer::sum);
                }
            }
            for (Map.Entry<StackKey, Integer> e : counts.entrySet()) {
                if (e.getValue() > bestCount.getOrDefault(e.getKey(), -1)) {
                    bestCount.put(e.getKey(), e.getValue());
                    homeIdx.put(e.getKey(), b);
                }
            }
        }
        // bulk types (> one box) are exempt from home-healing: every chosen box keeps
        // the fragments it already holds, so consolidation (defrag-bulk) sticks instead
        // of being evicted again as "non-home" on the very next sort. Only loose and
        // excess holdings of bulk types ever become work.
        java.util.Set<StackKey> bulkKeep = new java.util.HashSet<>();
        for (KeyNeed need : needs) {
            if ((need.items() + need.max() - 1) / need.max() > ShulkerRules.BOX_SLOTS) {
                bulkKeep.add(need.key());
            }
        }
        // unstackables choose their home only among the reserved trailing boxes so
        // seeding never keeps them in a stackable box (or vice versa)
        Map<StackKey, Integer> reserveHome = new HashMap<>();
        if (plan.reservedBoxes > 0) {
            Map<StackKey, Integer> reserveBest = new HashMap<>();
            for (int b = 0; b < plan.chosen.size(); b++) {
                if (!plan.reservedInv.contains(plan.chosen.get(b).invIndex)) {
                    continue;
                }
                Map<StackKey, Integer> counts = new HashMap<>();
                for (ItemStack stack : plan.chosen.get(b).contents) {
                    if (!stack.isEmpty() && !ShulkerRules.isShulkerBoxItem(stack)
                            && stack.getMaxStackSize() <= 1) {
                        counts.merge(new StackKey(stack), stack.getCount(), Integer::sum);
                    }
                }
                for (Map.Entry<StackKey, Integer> e : counts.entrySet()) {
                    if (e.getValue() > reserveBest.getOrDefault(e.getKey(), -1)) {
                        reserveBest.put(e.getKey(), e.getValue());
                        reserveHome.put(e.getKey(), b);
                    }
                }
            }
        }
        int[] keptStacks = new int[plan.chosen.size()];
        for (int b = 0; b < plan.chosen.size(); b++) {
            BoxInfo box = plan.chosen.get(b);
            for (ItemStack stack : box.contents) {
                if (stack.isEmpty()) {
                    continue;
                }
                StackKey key = new StackKey(stack);
                if (ShulkerRules.isShulkerBoxItem(stack)) {
                    box.quota.merge(key, stack.getCount(), Integer::sum);
                    keptStacks[b]++; // nested container stays in place
                    continue;
                }
                if (plan.reservedBoxes > 0
                        && (key.stack().getMaxStackSize() <= 1)
                                != plan.reservedInv.contains(box.invIndex)) {
                    continue; // wrong stacking group for this box - becomes moving items
                }
                if (bulkKeep.contains(key)) {
                    box.quota.merge(key, stack.getCount(), Integer::sum);
                    keptStacks[b]++; // bulk fragments stay wherever they sit
                    continue;
                }
                StackKey homeKey = key;
                int home = (plan.reservedBoxes > 0 && key.stack().getMaxStackSize() <= 1)
                        ? reserveHome.getOrDefault(homeKey, b)
                        : homeIdx.getOrDefault(homeKey, b);
                if (eff.homeHealing && home == b) {
                    box.quota.merge(key, stack.getCount(), Integer::sum);
                    keptStacks[b]++; // kept at home (off = full repack every sort)
                }
                // non-home boxes: their stacks of this type become moving items
            }
        }
        // items that must move per type: pool totals minus what sits at each type's home
        Map<StackKey, Integer> homeKept = new HashMap<>();
        for (BoxInfo box : plan.chosen) {
            for (Map.Entry<StackKey, Integer> e : box.quota.entrySet()) {
                if (!ShulkerRules.isShulkerBoxItem(e.getKey().stack())) {
                    homeKept.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
        }
        List<Need> work = new ArrayList<>();
        for (KeyNeed need : needs) {
            int toPlace = need.items() - homeKept.getOrDefault(need.key(), 0);
            if (toPlace > 0) {
                work.add(new Need(need.key(), toPlace, need.max()));
            }
        }
        // ---- packing: all types are queued in Item Scroller sort order - BULK types
        // (>= 6 stacks) first, then the rest. Boxes are filled one at a time in that
        // order. Whole placements always win (P: minimal truncation); a BULK type that
        // cannot fit the current box's remaining slots spans it - filling the box
        // exactly and continuing in the next one; only when nothing bulk remains does
        // a smaller type get split to fill the last slots of a box.
        // stacking-class grouping: 64-stackables, then 16-stackables, then unstackables -
        // each class packs into its own consecutive boxes (user rule: keep classes apart);
        // within a class, Item Scroller sort order (bulk types lead via the size order)
        java.util.Map<StackKey, Integer> classRankCache = new HashMap<>();
        java.util.function.ToIntFunction<Need> classRank = n -> classRankCache.computeIfAbsent(n.key,
                k -> {
                    int m = k.stack().getMaxStackSize();
                    return m >= 64 ? 0 : (m >= 16 ? 1 : 2);
                });
        work.sort((x, y) -> {
            int cx = classRank.applyAsInt(x);
            int cy = classRank.applyAsInt(y);
            if (cx != cy) {
                return Integer.compare(cx, cy);
            }
            return ItemScrollerSortOrder.COMPARATOR.compare(x.key.stack(), y.key.stack());
        });
        for (Need n : work) {
            n.bulk = eff.bulkFirst && (n.remaining + n.max - 1) / n.max >= eff.bulkMinStacks;
        }
        int[] slotsLeftArr = new int[plan.chosen.size()];
        for (int b = 0; b < plan.chosen.size(); b++) {
            BoxInfo box = plan.chosen.get(b);
            int slotsLeft = Math.max(0, ShulkerRules.BOX_SLOTS - keptStacks[b]);
            // pass 1: whole placements in queue order (bulk first by construction)
            boolean progressed = true;
            while (slotsLeft > 0 && progressed) {
                progressed = false;
                for (Need n : work) {
                    if (n.remaining <= 0) {
                        continue;
                    }
                    int stacks = (n.remaining + n.max - 1) / n.max;
                    if (stacks > slotsLeft) {
                        continue;
                    }
                    if (!groupAllows(plan, n.key, b) || heldInOtherChosenBox(plan, n.key, b)) {
                        continue; // wrong stacking group, or its home is another box
                    }
                    box.quota.merge(n.key, n.remaining, Integer::sum);
                    diag("[CSSort] place " + keyName(n.key) + " x" + n.remaining
                            + " -> box[" + box.invIndex + "] (pass1 whole)");
                    slotsLeft -= stacks;
                    n.remaining = 0;
                    progressed = true;
                    break;
                }
            }
            // pass 2: fill any remaining slots exactly.
            // - a BULK type spans the boundary (fills the box completely, continues
            //   in the next one) - that is not a truncation, it is a contiguous fill;
            // - otherwise the smallest remaining type is split to fill exactly.
            // Every branch decrements slotsLeft by the stacks it actually consumed.
            while (slotsLeft > 0) {
                // home filter: a type whose home is another chosen box must not be
                // placed here - it belongs to its home box (P2: one type, one box)
                Need next = null;
                for (Need n : work) {
                    if (n.remaining > 0 && groupAllows(plan, n.key, b)
                            && !heldInOtherChosenBox(plan, n.key, b)) {
                        next = n;
                        break;
                    }
                }
                if (next == null) {
                    break; // only home-elsewhere types remain - their homes fill up
                }
                int nextStacks = (next.remaining + next.max - 1) / next.max;
                if (next.bulk && nextStacks > slotsLeft) {
                    // span: fill the box completely, continue in the next box
                    int items = slotsLeft * next.max;
                    box.quota.merge(next.key, items, Integer::sum);
                    diag("[CSSort] place " + keyName(next.key) + " x" + items
                            + " -> box[" + box.invIndex + "] (pass2 bulk-span)");
                    next.remaining -= items;
                    slotsLeft = 0;
                    continue;
                }
                if (nextStacks <= slotsLeft) {
                    // fits whole after all - place it
                    box.quota.merge(next.key, next.remaining, Integer::sum);
                    diag("[CSSort] place " + keyName(next.key) + " x" + next.remaining
                            + " -> box[" + box.invIndex + "] (pass2 whole)");
                    slotsLeft -= nextStacks;
                    next.remaining = 0;
                    continue;
                }
                Need split = null;
                for (Need n : work) {
                    if (n.remaining <= 0 || !groupAllows(plan, n.key, b)
                            || heldInOtherChosenBox(plan, n.key, b)) {
                        continue;
                    }
                    if (split == null || n.remaining < split.remaining) {
                        split = n;
                    }
                }
                if (split == null) {
                    break; // nothing placeable - leave the slots empty (no truncation)
                }
                int items = Math.min(split.remaining, slotsLeft * split.max);
                box.quota.merge(split.key, items, Integer::sum);
                diag("[CSSort] place " + keyName(split.key) + " x" + items
                        + " -> box[" + box.invIndex + "] (pass2 split)");
                split.remaining -= items;
                slotsLeft -= (items + split.max - 1) / split.max;
            }
            slotsLeftArr[b] = slotsLeft;
        }
        // overflow pass: home-full types left over above are split into the next box
        // with free slots instead of staying loose. One-type-one-box is best effort
        // ("尽量"); a full home must spill somewhere, and a box is better than loose
        // while empty boxes exist. Work order (class group + ItemScroller) is kept.
        // Group filters stay on: unstackables only spill inside reserved boxes.
        // Disabled via config: leftovers honestly stay loose.
        for (int b = 0; eff.overflowEnabled && b < plan.chosen.size(); b++) {
            BoxInfo box = plan.chosen.get(b);
            int slotsLeft = slotsLeftArr[b];
            while (slotsLeft > 0) {
                Need next = null;
                for (Need n : work) {
                    if (n.remaining > 0 && groupAllows(plan, n.key, b)) {
                        next = n;
                        break;
                    }
                }
                if (next == null) {
                    break;
                }
                int nextStacks = (next.remaining + next.max - 1) / next.max;
                if (nextStacks <= slotsLeft) {
                    box.quota.merge(next.key, next.remaining, Integer::sum);
                    diag("[CSSort] place " + keyName(next.key) + " x" + next.remaining
                            + " -> box[" + box.invIndex + "] (overflow whole)");
                    slotsLeft -= nextStacks;
                    next.remaining = 0;
                    continue;
                }
                Need split = null;
                for (Need n : work) {
                    if (n.remaining <= 0 || !groupAllows(plan, n.key, b)) {
                        continue;
                    }
                    if (split == null || n.remaining < split.remaining) {
                        split = n;
                    }
                }
                if (split == null) {
                    break;
                }
                int items = Math.min(split.remaining, slotsLeft * split.max);
                box.quota.merge(split.key, items, Integer::sum);
                diag("[CSSort] place " + keyName(split.key) + " x" + items
                        + " -> box[" + box.invIndex + "] (overflow split)");
                split.remaining -= items;
                slotsLeft -= (items + split.max - 1) / split.max;
            }
            slotsLeftArr[b] = slotsLeft;
        }
        // items that no longer fit stay loose / inside their current box
        // (no cross-group spill: a leftover stack beats a mixed reserved box, and the
        // next Q - or the next pass of this Q - repacks from the new state)

        // ---- defrag ("腾家"): pull split types together when room can be made
        // WITHOUT creating new splits. Only whole-type relocations (an entire quota
        // moved to a box that neither holds nor wants it) are used as evictions, so
        // the split count strictly decreases; anything else is left as it is.
        {
            boolean progress = true;
            while (progress) {
                progress = false;
                Map<StackKey, Integer> totals = new LinkedHashMap<>();
                Map<StackKey, Integer> boxCount = new LinkedHashMap<>();
                for (BoxInfo box : plan.chosen) {
                    for (Map.Entry<StackKey, Integer> e : box.quota.entrySet()) {
                        if (ShulkerRules.isShulkerBoxItem(e.getKey().stack())) {
                            continue;
                        }
                        totals.merge(e.getKey(), e.getValue(), Integer::sum);
                        boxCount.merge(e.getKey(), 1, Integer::sum);
                    }
                }
                List<StackKey> splits = new ArrayList<>();
                for (Map.Entry<StackKey, Integer> e : totals.entrySet()) {
                    if (boxCount.getOrDefault(e.getKey(), 0) > 1) {
                        splits.add(e.getKey());
                    }
                }
                splits.sort((a, b) -> Integer.compare(totals.get(b), totals.get(a)));
                for (StackKey key : splits) {
                    int splitTotal = totals.get(key);
                    int max = key.stack().getMaxStackSize();
                    boolean bulk = (splitTotal + max - 1) / max > ShulkerRules.BOX_SLOTS;
                    boolean ok = bulk
                            ? (eff.defragBulkEnabled && tryConsolidateBulk(plan, key, splitTotal))
                            : (eff.defragEnabled && tryConsolidate(plan, key, splitTotal));
                    if (ok) {
                        progress = true;
                        break;
                    }
                }
            }
            // visibility: splits that survive are either already minimal or need a new
            // split elsewhere to fix (correctly skipped) - say which so logs are auditable
            Map<StackKey, Integer> leftTotals = new LinkedHashMap<>();
            Map<StackKey, Integer> leftBoxes = new LinkedHashMap<>();
            for (BoxInfo box : plan.chosen) {
                for (Map.Entry<StackKey, Integer> e : box.quota.entrySet()) {
                    if (ShulkerRules.isShulkerBoxItem(e.getKey().stack())) {
                        continue;
                    }
                    leftTotals.merge(e.getKey(), e.getValue(), Integer::sum);
                    leftBoxes.merge(e.getKey(), 1, Integer::sum);
                }
            }
            for (Map.Entry<StackKey, Integer> e : leftTotals.entrySet()) {
                int frags = leftBoxes.getOrDefault(e.getKey(), 0);
                if (frags > 1) {
                    int stacks = (e.getValue() + e.getKey().stack().getMaxStackSize() - 1)
                            / e.getKey().stack().getMaxStackSize();
                    int need = (stacks + ShulkerRules.BOX_SLOTS - 1) / ShulkerRules.BOX_SLOTS;
                    diag("[CSSort] defrag skip " + keyName(e.getKey()) + " x" + e.getValue()
                            + " (frags=" + frags + " need=" + need + ")");
                    if (frags > need) {
                        diag("[CSSort] defrag why" + skipDetail(plan, e.getKey()));
                    }
                }
            }
        }

        // ---- top-up: fill partial stacks to max from same-type leftovers. Recipients
        // already own the partial slot so their stack count never grows; donors shrink
        // by less than one stack. Loose leftovers first (clears the inventory), then
        // lone-partial fragments elsewhere (their removal also heals a split).
        // Disabled via config: partials stay as packed.
        if (eff.topUpEnabled) {
            for (BoxInfo box : plan.chosen) {
                List<Map.Entry<StackKey, Integer>> entries = new ArrayList<>(box.quota.entrySet());
                for (Map.Entry<StackKey, Integer> e : entries) {
                    StackKey key = e.getKey();
                    if (ShulkerRules.isShulkerBoxItem(key.stack())) {
                        continue;
                    }
                    int max = key.stack().getMaxStackSize();
                    if (max <= 1) {
                        continue;
                    }
                    int have = box.quota.getOrDefault(key, 0);
                    int rem = have % max;
                    if (rem == 0 || have <= 0) {
                        continue;
                    }
                    int need = max - rem;
                    Need w = null;
                    for (Need n : work) {
                        if (n.remaining >= need && n.key.equals(key)) {
                            w = n;
                            break;
                        }
                    }
                    if (w != null) {
                        box.quota.put(key, have + need);
                        w.remaining -= need;
                        diag("[CSSort] top-up " + keyName(key) + " +" + need
                                + " in box[" + box.invIndex + "] (from loose)");
                        continue;
                    }
                    for (BoxInfo donor : plan.chosen) {
                        if (donor == box) {
                            continue;
                        }
                        int qd = donor.quota.getOrDefault(key, 0);
                        if (qd == need) {
                            donor.quota.remove(key);
                            box.quota.put(key, have + need);
                            diag("[CSSort] top-up " + keyName(key) + " +" + need
                                    + " in box[" + box.invIndex + "] (from box[" + donor.invIndex + "])");
                            break;
                        }
                    }
                }
            }
        }

        // ---- hard cap: no type's total quota may exceed the items that exist.
        // Quotas are only ever trimmed from the placed portion (quota above what the
        // box already holds); kept home content is never removed. Surplus simply stays
        // where it physically is (loose / excess / non-home box).
        {
            Map<StackKey, Integer> poolCap = new LinkedHashMap<>();
            for (KeyNeed need : needs) {
                poolCap.merge(need.key(), need.items(), Integer::sum);
            }
            Map<StackKey, Integer> quotaCapSum = new LinkedHashMap<>();
            for (BoxInfo box : plan.chosen) {
                for (Map.Entry<StackKey, Integer> e : box.quota.entrySet()) {
                    quotaCapSum.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
            for (Map.Entry<StackKey, Integer> e : quotaCapSum.entrySet()) {
                int exist = poolCap.getOrDefault(e.getKey(), 0);
                int surplus = e.getValue() - exist;
                if (surplus <= 0) {
                    continue;
                }
                // trim placed surplus from last boxes first, never below held
                for (int bi = plan.chosen.size() - 1; bi >= 0 && surplus > 0; bi--) {
                    BoxInfo box = plan.chosen.get(bi);
                    int q = box.quota.getOrDefault(e.getKey(), 0);
                    if (q <= 0) {
                        continue;
                    }
                    int held = 0;
                    for (ItemStack stack : box.contents) {
                        if (!stack.isEmpty() && new StackKey(stack).equals(e.getKey())) {
                            held += stack.getCount();
                        }
                    }
                    int reducible = Math.max(0, q - held);
                    int cut = Math.min(surplus, reducible);
                    if (cut > 0) {
                        int left = q - cut;
                        if (left > 0) {
                            box.quota.put(e.getKey(), left);
                        } else {
                            box.quota.remove(e.getKey());
                        }
                        surplus -= cut;
                        diag("[CSSort] cap " + keyName(e.getKey()) + " -" + cut
                                + " from box[" + box.invIndex + "] (held=" + held + " quotaWas=" + q + ")");
                    }
                }
                if (surplus > 0) {
                    diag("[CSSort] cap FAILED " + keyName(e.getKey())
                            + ": still over by " + surplus + " (pool=" + exist + ")");
                }
            }
        }

        // ---- plan self-check: no type's total quota may exceed the items that exist.
        // A violation means the fill over-assigned; dump the per-box breakdown.
        {
            Map<StackKey, Integer> quotaSum = new LinkedHashMap<>();
            for (BoxInfo box : plan.chosen) {
                for (Map.Entry<StackKey, Integer> e : box.quota.entrySet()) {
                    quotaSum.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
            Map<StackKey, Integer> pool = new LinkedHashMap<>();
            for (KeyNeed need : needs) {
                pool.put(need.key(), need.items());
            }
            for (Map.Entry<StackKey, Integer> e : quotaSum.entrySet()) {
                int exist = pool.getOrDefault(e.getKey(), 0);
                if (e.getValue() > exist) {
                    StringBuilder sb = new StringBuilder();
                    for (BoxInfo box : plan.chosen) {
                        int q = box.quota.getOrDefault(e.getKey(), 0);
                        int held = 0;
                        for (ItemStack stack : box.contents) {
                            if (!stack.isEmpty() && new StackKey(stack).equals(e.getKey())) {
                                held += stack.getCount();
                            }
                        }
                        if (q > 0 || held > 0) {
                            sb.append(" [invSlot=").append(box.invIndex)
                                    .append(" kept=").append(keptStacks[box.listIndex])
                                    .append(" held=").append(held)
                                    .append(" quota=").append(q).append("]");
                        }
                    }
                    diag("[CSSort] PLAN OVERFLOW " + keyName(e.getKey())
                            + ": quotaSum=" + e.getValue() + " pool=" + exist + sb);
                }
            }
        }

        // ---- is there anything at all to do?
        plan.hasWork = computeHasWork(plan, inv, boxSlots);
        return plan;
    }

    /** Stacking-group gate: unstackables only enter reserved boxes and stackables
     * never do (when a reservation exists). Membership is by inventory slot. */
    private static boolean groupAllows(SortPlan plan, StackKey key, int b) {
        if (plan.reservedBoxes <= 0) {
            return true;
        }
        boolean needRes = key.stack().getMaxStackSize() <= 1;
        boolean inRes = plan.reservedInv.contains(plan.chosen.get(b).invIndex);
        return needRes == inRes;
    }

    /** Diagnostic line, silenced unless debug logging is on.
     * Singleplayer reads the live config; elsewhere (or on any error)
     * it stays loud so failures remain diagnosable. */
    private static void diag(String msg) {
        try {
            com.yeyang.crossshulkersort.config.ModConfig c =
                    com.yeyang.crossshulkersort.CrossShulkerSortClient.config();
            if (c == null || c.debugLog) {
                System.out.println(msg);
            }
        } catch (Throwable t) {
            System.out.println(msg);
        }
    }

    /** True when the type already has quota in a chosen box other than {@code except}. */
    private static boolean heldInOtherChosenBox(SortPlan plan, StackKey key, int except) {
        for (int i = 0; i < plan.chosen.size(); i++) {
            if (i == except) {
                continue;
            }
            if (plan.chosen.get(i).quota.getOrDefault(key, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int quotaStacks(StackKey key, int items) {
        int max = key.stack().getMaxStackSize();
        return (items + max - 1) / max;
    }

    private static int boxQuotaStacks(BoxInfo box) {
        int used = 0;
        for (Map.Entry<StackKey, Integer> e : box.quota.entrySet()) {
            used += quotaStacks(e.getKey(), e.getValue());
        }
        return used;
    }

    /**
     * True occupancy of a box: quotas alone undercount when kept content is
     * fragmented (two partial stacks where one would do) or surplus leftovers sit
     * beyond every quota. The executor enforces room physically, so planning with
     * quota stacks only would approve moves that are silently dropped at apply
     * time - hasWork stays true forever and every Q ends exactly where it began.
     */
    static int occupancyOf(BoxInfo box) {
        int physical = 0;
        for (ItemStack stack : box.contents) {
            if (!stack.isEmpty()) {
                physical++;
            }
        }
        return Math.max(boxQuotaStacks(box), physical);
    }

    private static int heldItems(BoxInfo box, StackKey key) {
        int held = 0;
        for (ItemStack stack : box.contents) {
            if (!stack.isEmpty() && new StackKey(stack).equals(key)) {
                held += stack.getCount();
            }
        }
        return held;
    }

    /** One-line diagnosis for a surviving split: per-box quota/held/occupancy plus
     * how many whole stacks could be evicted from the biggest box and how much room
     * exists elsewhere. Read-only. */
    private static String skipDetail(SortPlan plan, StackKey key) {
        StringBuilder sb = new StringBuilder();
        sb.append(' ').append(keyName(key)).append(':');
        BoxInfo target = null;
        int targetQ = -1;
        for (BoxInfo box : plan.chosen) {
            int q = box.quota.getOrDefault(key, 0);
            int held = heldItems(box, key);
            if (q > 0 || held > 0) {
                sb.append(" [inv=").append(box.invIndex)
                        .append(" quota=").append(q)
                        .append(" held=").append(held)
                        .append(" occ=").append(occupancyOf(box)).append(']');
            }
            if (q > targetQ) {
                targetQ = q;
                target = box;
            }
        }
        if (target != null) {
            Map<StackKey, Integer> boxCount = new HashMap<>();
            for (BoxInfo box : plan.chosen) {
                for (StackKey k : box.quota.keySet()) {
                    boxCount.merge(k, 1, Integer::sum);
                }
            }
            int evictStacks = 0;
            int roomFails = 0;
            int holdFails = 0;
            for (Map.Entry<StackKey, Integer> ce : target.quota.entrySet()) {
                if (ce.getKey().equals(key) || ShulkerRules.isShulkerBoxItem(ce.getKey().stack())) {
                    continue;
                }
                if (boxCount.getOrDefault(ce.getKey(), 0) != 1) {
                    continue;
                }
                evictStacks += quotaStacks(ce.getKey(), ce.getValue());
                boolean roomOk = false;
                boolean holdOk = false;
                for (int di = 0; di < plan.chosen.size(); di++) {
                    BoxInfo box = plan.chosen.get(di);
                    if (box == target || !groupAllows(plan, ce.getKey(), di)) {
                        continue;
                    }
                    if (box.quota.getOrDefault(ce.getKey(), 0) > 0) {
                        continue;
                    }
                    if (heldItems(box, ce.getKey()) > 0) {
                        holdOk = true;
                        continue;
                    }
                    if (ShulkerRules.BOX_SLOTS - occupancyOf(box)
                            >= quotaStacks(ce.getKey(), ce.getValue())) {
                        roomOk = true;
                        break;
                    }
                }
                if (!roomOk) {
                    roomFails++;
                }
                if (holdOk) {
                    holdFails++;
                }
            }
            int freeElse = 0;
            for (BoxInfo box : plan.chosen) {
                if (box == target) {
                    continue;
                }
                freeElse += Math.max(0, ShulkerRules.BOX_SLOTS - occupancyOf(box));
            }
            sb.append(" targetEvictable=").append(evictStacks).append(" freeElse=").append(freeElse);
            sb.append(" roomFails=").append(roomFails).append(" holdFails=").append(holdFails);
        }
        return sb.toString();
    }

    /**
     * Frees {@code needStacks} slots in {@code target} by relocating entire
     * whole-type quotas (never {@code excludeKey}) to boxes with room that neither
     * hold nor want them. Room is always recomputed live from quotas and physical
     * contents (never a hand-maintained ledger, which drifts once the target's own
     * quota grows). Edits quotas in place; the caller owns backup/rollback. Returns
     * false if the room cannot be made without new splits.
     */
    private static boolean evictForRoom(SortPlan plan, BoxInfo target, StackKey excludeKey,
                                        int needStacks, List<String> evicted) {
        int deficit = needStacks - (ShulkerRules.BOX_SLOTS - occupancyOf(target));
        if (deficit <= 0) {
            return true;
        }
        Map<StackKey, Integer> boxCount = new HashMap<>();
        for (BoxInfo box : plan.chosen) {
            for (StackKey k : box.quota.keySet()) {
                boxCount.merge(k, 1, Integer::sum);
            }
        }
        List<Map.Entry<StackKey, Integer>> cands = new ArrayList<>();
        for (Map.Entry<StackKey, Integer> e : target.quota.entrySet()) {
            if (e.getKey().equals(excludeKey) || ShulkerRules.isShulkerBoxItem(e.getKey().stack())) {
                continue;
            }
            if (boxCount.getOrDefault(e.getKey(), 0) != 1) {
                continue; // moving part of it would split it elsewhere
            }
            cands.add(e);
        }
        cands.sort(Comparator.comparingInt(e -> quotaStacks(e.getKey(), e.getValue())));
        for (Map.Entry<StackKey, Integer> cand : cands) {
            if (deficit <= 0) {
                break;
            }
            int s = quotaStacks(cand.getKey(), cand.getValue());
            BoxInfo dest = null;
            boolean blockedGroup = false;
            boolean blockedHold = false;
            boolean blockedRoom = false;
            for (int di = 0; di < plan.chosen.size(); di++) {
                BoxInfo box = plan.chosen.get(di);
                if (box == target) {
                    continue;
                }
                if (!groupAllows(plan, cand.getKey(), di)) {
                    blockedGroup = true;
                    continue; // unstackables stay in reserved boxes and vice versa
                }
                if (box.quota.getOrDefault(cand.getKey(), 0) > 0 || heldItems(box, cand.getKey()) > 0) {
                    blockedHold = true;
                    continue; // destination must neither hold nor want it
                }
                if (ShulkerRules.BOX_SLOTS - occupancyOf(box) >= s) {
                    dest = box;
                    break;
                }
                blockedRoom = true;
            }
            if (dest == null) {
                diag("[CSSort] defrag evict-fail " + keyName(excludeKey)
                        + " target box[" + target.invIndex + "]"
                        + " cand " + keyName(cand.getKey()) + " x" + cand.getValue()
                        + " (" + s + " stacks, deficit=" + deficit
                        + " group=" + blockedGroup + " hold=" + blockedHold + " room=" + blockedRoom + ")");
                continue;
            }
            dest.quota.put(cand.getKey(), cand.getValue());
            target.quota.remove(cand.getKey());
            deficit -= s;
            evicted.add(keyName(cand.getKey()) + " x" + cand.getValue());
        }
        return deficit <= 0;
    }

    /**
     * Pulls a split type into its biggest box, evicting whole types if needed.
     * Only split-reducing moves are applied: evictions relocate an ENTIRE quota to
     * a box with room that neither holds nor wants it. Returns false (leaving all
     * quotas untouched) when consolidation is impossible without new splits.
     */
    private static boolean tryConsolidate(SortPlan plan, StackKey key, int total) {
        BoxInfo target = null;
        int targetQ = -1;
        for (BoxInfo box : plan.chosen) {
            int q = box.quota.getOrDefault(key, 0);
            if (q > targetQ) {
                targetQ = q;
                target = box;
            }
        }
        if (target == null || targetQ <= 0) {
            diag("[CSSort] defrag giveup " + keyName(key) + " x" + total + " (no target)");
            return false;
        }
        int needStacks = quotaStacks(key, total) - quotaStacks(key, targetQ);
        Map<BoxInfo, Map<StackKey, Integer>> backup = new HashMap<>();
        for (BoxInfo box : plan.chosen) {
            backup.put(box, new HashMap<>(box.quota));
        }
        List<String> evicted = new ArrayList<>();
        if (!evictForRoom(plan, target, key, needStacks, evicted)) {
            diag("[CSSort] defrag giveup " + keyName(key) + " x" + total
                    + " -> box[" + target.invIndex + "]"
                    + " (need=" + needStacks + " stacks, targetOcc=" + occupancyOf(target)
                    + " evicted=" + (evicted.isEmpty() ? "none" : String.join(", ", evicted)) + ")");
            for (Map.Entry<BoxInfo, Map<StackKey, Integer>> b : backup.entrySet()) {
                b.getKey().quota.clear();
                b.getKey().quota.putAll(b.getValue());
            }
            return false;
        }
        for (BoxInfo box : plan.chosen) {
            if (box != target) {
                box.quota.remove(key);
            }
        }
        target.quota.put(key, total);
        for (BoxInfo box : plan.chosen) {
            if (occupancyOf(box) > ShulkerRules.BOX_SLOTS) {
                diag("[CSSort] defrag giveup " + keyName(key) + " x" + total
                        + " -> box[" + target.invIndex + "]"
                        + " (overflow box[" + box.invIndex + "] occ=" + occupancyOf(box) + ")");
                for (Map.Entry<BoxInfo, Map<StackKey, Integer>> b : backup.entrySet()) {
                    b.getKey().quota.clear();
                    b.getKey().quota.putAll(b.getValue());
                }
                return false;
            }
        }
        // execution is quota-driven (ServerSorter reuses whatever sits in place and
        // drains the rest), so no home pinning is needed for these quota moves
        diag("[CSSort] defrag " + keyName(key) + " x" + total
                + " -> box[" + target.invIndex + "]"
                + (evicted.isEmpty() ? " (no evict)" : " (evict " + String.join(", ", evicted) + ")"));
        return true;
    }

    /**
     * Consolidates a bulk type (more than one box worth) into the fewest boxes:
     * fragment boxes are filled to 27 stacks in turn, biggest quota first, evicting
     * whole types as needed. Same no-new-splits contract as tryConsolidate: any
     * failure restores every quota untouched.
     */
    private static boolean tryConsolidateBulk(SortPlan plan, StackKey key, int total) {
        int max = key.stack().getMaxStackSize();
        int totalStacks = (total + max - 1) / max;
        int boxesNeeded = (totalStacks + ShulkerRules.BOX_SLOTS - 1) / ShulkerRules.BOX_SLOTS;
        List<BoxInfo> frags = new ArrayList<>();
        for (BoxInfo box : plan.chosen) {
            if (box.quota.getOrDefault(key, 0) > 0) {
                frags.add(box);
            }
        }
        if (frags.size() <= boxesNeeded) {
            return false; // already minimal - nothing to gain
        }
        frags.sort((a, b) -> Integer.compare(
                b.quota.getOrDefault(key, 0), a.quota.getOrDefault(key, 0)));
        Map<BoxInfo, Map<StackKey, Integer>> backup = new HashMap<>();
        for (BoxInfo box : plan.chosen) {
            backup.put(box, new HashMap<>(box.quota));
        }
        List<String> evicted = new ArrayList<>();
        int remaining = total;
        java.util.Set<BoxInfo> usedTargets = new java.util.HashSet<>();
        for (BoxInfo target : frags) {
            if (remaining <= 0) {
                break;
            }
            int want = Math.min(remaining, ShulkerRules.BOX_SLOTS * max);
            int have = target.quota.getOrDefault(key, 0);
            int needStacks = quotaStacks(key, want) - quotaStacks(key, have);
            if (!evictForRoom(plan, target, key, needStacks, evicted)) {
                diag("[CSSort] defrag-bulk giveup " + keyName(key) + " x" + total
                        + " -> box[" + target.invIndex + "]"
                        + " (need=" + needStacks + " stacks, targetOcc=" + occupancyOf(target) + ")");
                for (Map.Entry<BoxInfo, Map<StackKey, Integer>> b : backup.entrySet()) {
                    b.getKey().quota.clear();
                    b.getKey().quota.putAll(b.getValue());
                }
                return false;
            }
            target.quota.put(key, want);
            usedTargets.add(target);
            remaining -= want;
        }
        if (remaining > 0) {
            for (Map.Entry<BoxInfo, Map<StackKey, Integer>> b : backup.entrySet()) {
                b.getKey().quota.clear();
                b.getKey().quota.putAll(b.getValue());
            }
            return false;
        }
        // fragments beyond the filled targets hand their share over
        for (BoxInfo box : frags) {
            if (!usedTargets.contains(box)) {
                box.quota.remove(key);
            }
        }
        for (BoxInfo box : plan.chosen) {
            if (box.quota.getOrDefault(key, 0) > 0 && !usedTargets.contains(box)) {
                // leaked into a new box - not fewer boxes, roll back
                for (Map.Entry<BoxInfo, Map<StackKey, Integer>> b : backup.entrySet()) {
                    b.getKey().quota.clear();
                    b.getKey().quota.putAll(b.getValue());
                }
                return false;
            }
            if (occupancyOf(box) > ShulkerRules.BOX_SLOTS) {
                for (Map.Entry<BoxInfo, Map<StackKey, Integer>> b : backup.entrySet()) {
                    b.getKey().quota.clear();
                    b.getKey().quota.putAll(b.getValue());
                }
                return false;
            }
        }
        diag("[CSSort] defrag-bulk " + keyName(key) + " x" + total
                + (evicted.isEmpty() ? " (no evict)" : " (evict " + String.join(", ", evicted) + ")"));
        return true;
    }

    private static boolean computeHasWork(SortPlan plan, Inventory inv, Set<Integer> boxSlots) {
        // 1) every chosen box must match its quota exactly (per-type item counts, no
        //    unknown keys) and be sorted - this catches manual shuffling between boxes
        for (BoxInfo box : plan.chosen) {
            Map<StackKey, Integer> live = new HashMap<>();
            for (ItemStack stack : box.contents) {
                if (stack.isEmpty() || ShulkerRules.isShulkerBoxItem(stack)) {
                    continue;
                }
                live.merge(new StackKey(stack), stack.getCount(), Integer::sum);
            }
            for (Map.Entry<StackKey, Integer> e : box.quota.entrySet()) {
                if (live.getOrDefault(e.getKey(), 0) != e.getValue()) {
                    return true;
                }
            }
            for (StackKey key : live.keySet()) {
                if (!box.quota.containsKey(key)) {
                    return true;
                }
            }
            if (isUnsorted(box.contents)) {
                return true;
            }
        }
        // 2) loose stacks whose type still has an unmet quota somewhere
        for (int i = 0; i < 36; i++) {
            if (boxSlots.contains(i)) {
                continue;
            }
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || ShulkerRules.isShulkerBoxItem(stack)) {
                continue;
            }
            if (deficitForItem(plan, stack) > 0) {
                return true;
            }
        }
        // 3) excess boxes holding items that belong in a chosen box
        for (BoxInfo box : plan.excess) {
            for (ItemStack stack : box.contents) {
                if (stack.isEmpty() || ShulkerRules.isShulkerBoxItem(stack)) {
                    continue;
                }
                if (deficitForItem(plan, stack) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many more items of this stack's type all chosen boxes still want (item counts). */
    public static int deficitForItem(SortPlan plan, ItemStack stack) {
        StackKey key = new StackKey(stack);
        int deficit = 0;
        for (BoxInfo box : plan.chosen) {
            deficit += box.quota.getOrDefault(key, 0);
        }
        for (BoxInfo box : plan.chosen) {
            for (ItemStack s : box.contents) {
                if (!s.isEmpty() && new StackKey(s).equals(key)) {
                    deficit -= s.getCount();
                }
            }
        }
        return deficit;
    }

    public static String keyName(StackKey key) {
        return BuiltInRegistries.ITEM.getKey(key.stack().getItem()).toString();
    }

    public static boolean sameStackExact(ItemStack a, ItemStack b) {
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    public static boolean isUnsorted(List<ItemStack> contents) {
        List<ItemStack> filled = new ArrayList<>();
        for (ItemStack stack : contents) {
            if (!stack.isEmpty()) {
                filled.add(stack);
            }
        }
        for (int i = 1; i < filled.size(); i++) {
            if (SORT_ORDER.compare(filled.get(i - 1), filled.get(i)) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Mutable work item for the fill loop: items of one type still to be placed. */
    private static final class Need {
        final StackKey key;
        final int max;
        int remaining;
        boolean bulk;

        Need(StackKey key, int remaining, int max) {
            this.key = key;
            this.remaining = remaining;
            this.max = max;
        }
    }

    public static final class BoxInfo {
        public final int listIndex;
        public final int invIndex;
        /** Live snapshot of the box contents; refreshed from inventory components. */
        public List<ItemStack> contents;
        /** Slots filled when the plan was computed - baseline for the "freed" report. */
        public final int initialFilled;
        /** Exact per-type ITEM counts this box should end up with. */
        public final Map<StackKey, Integer> quota = new HashMap<>();

        BoxInfo(int listIndex, int invIndex, ItemStack boxStack, List<ItemStack> contentsOverride) {
            this.listIndex = listIndex;
            this.invIndex = invIndex;
            this.contents = contentsOverride != null ? contentsOverride : ShulkerRules.readContents(boxStack);
            int filled = 0;
            for (ItemStack stack : this.contents) {
                if (!stack.isEmpty()) {
                    filled++;
                }
            }
            this.initialFilled = filled;
        }

        public void refreshContents(Inventory inv) {
            this.contents = ShulkerRules.readContents(inv.getItem(this.invIndex));
        }
    }
}
