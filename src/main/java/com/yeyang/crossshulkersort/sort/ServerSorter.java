package com.yeyang.crossshulkersort.sort;

import com.yeyang.crossshulkersort.sort.SortPlan.BoxInfo;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side sort, IPN-style: read the authoritative server data, compute the layout,
 * write items and box components back directly, verify conservation, then let vanilla
 * sync the changes to the client. One atomic operation on the server thread.
 *
 * Execution is quota-driven and home-free: every chosen box already physically holds
 * part of its quota (counted in place); only the shortfall is created, and it is
 * sourced from other boxes' surpluses first, then excess boxes, then the inventory.
 * The inventory is never touched during planning, only during the final apply.
 * Because every created item is covered by construction (quotas are capped to the
 * pool), a conservation deficit is mathematically impossible - the pre-checks below
 * remain purely as defense in depth.
 */
public final class ServerSorter {

    private ServerSorter() {
    }


    /** Full per-stage accounting for one type - the definitive mismatch diagnostic. */
    private static void dumpTypeTrace(SortPlan plan, StackKey key,
                                      Map<StackKey, Integer> before, Map<StackKey, Integer> after) {
        String name = SortPlan.keyName(key).replace("minecraft:", "");
        for (int b = 0; b < plan.chosen.size(); b++) {
            BoxInfo box = plan.chosen.get(b);
            int held = 0;
            for (ItemStack stack : box.contents) {
                if (!stack.isEmpty() && new StackKey(stack).equals(key)) {
                    held += stack.getCount();
                }
            }
            int quota = box.quota.getOrDefault(key, 0);
            com.yeyang.crossshulkersort.CrossShulkerSortClient.LOGGER.error(
                    "[CSSort] trace {}: chosenBox[{}] (invSlot={}) held={} quota={}",
                    name, b, box.invIndex, held, quota);
        }
        for (BoxInfo box : plan.excess) {
            int held = 0;
            for (ItemStack stack : box.contents) {
                if (!stack.isEmpty() && new StackKey(stack).equals(key)) {
                    held += stack.getCount();
                }
            }
            if (held > 0) {
                com.yeyang.crossshulkersort.CrossShulkerSortClient.LOGGER.error(
                        "[CSSort] trace {}: excessBox (invSlot={}) held={}",
                        name, box.invIndex, held);
            }
        }
        com.yeyang.crossshulkersort.CrossShulkerSortClient.LOGGER.error(
                "[CSSort] trace {}: before={} after={} (created/consumed amounts are in the created map)",
                name, before.getOrDefault(key, 0), after.getOrDefault(key, 0));
    }

    /**
     * One Q press sorts to a fixed point: a single plan/apply round can leave work
     * behind (homes shift once their contents move), so quiet rounds repeat until a
     * round finds nothing or aborts. At most 3 rounds; every round is individually
     * conservation-checked with rollback, and only one summary chat line is sent.
     */
    public static void sort(ServerPlayerEntity player) {
        PlayerInventory scan = player.getInventory();
        Map<Integer, Boolean> wasFilled = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = scan.getStack(i);
            if (ShulkerRules.isUsableBox(stack)) {
                wasFilled.put(i, !ShulkerRules.readContents(stack).isEmpty());
            }
        }
        boolean didWork = false;
        com.yeyang.crossshulkersort.config.ModConfig eff =
                com.yeyang.crossshulkersort.config.ModConfig.effective();
        for (int i = 0; i < Math.max(1, eff.maxRounds); i++) {
            int r = sortPass(player, true);
            if (r == 1) {
                didWork = true;
                continue;
            }
            if (r == 2) {
                return; // carried / internal / rollback message already shown
            }
            if (r == 3) {
                // a round applied bit-for-bit nothing: same state in means same plan
                // out (deterministic), so further rounds cannot progress either
                player.sendMessage(new TranslatableText("crossshulkersort.err.stuck"), false);
                return;
            }
            break; // nothing left to do
        }
        if (!didWork) {
            if (eff.chatReport) {
                player.sendMessage(new TranslatableText("crossshulkersort.done.nothing"), false);
            }
            return;
        }
        PlayerInventory inv = player.getInventory();
        int used = 0;
        int freed = 0;
        for (Map.Entry<Integer, Boolean> e : wasFilled.entrySet()) {
            boolean nowFilled = !ShulkerRules.readContents(inv.getStack(e.getKey())).isEmpty();
            if (nowFilled) {
                used++;
            } else if (e.getValue()) {
                freed++;
            }
        }
        if (eff.chatReport) {
            player.sendMessage(new TranslatableText("crossshulkersort.done", used, freed, ""), false);
        }
    }

    /** @return 0 nothing to do, 1 applied a round, 2 stopped with a message shown,
     * 3 applied bit-for-bit nothing (stuck - same state would plan identically) */
    private static int sortPass(ServerPlayerEntity player, boolean quiet) {
        PlayerInventory inv = player.getInventory();
        if (player.currentScreenHandler != player.playerScreenHandler) {
            // another container (e.g. an open shulker box) is showing: sorting now
            // would yank items out from under it, so refuse LOUDLY instead of dying
            // silently - a silent no-op here looks exactly like "Q does nothing"
            player.sendMessage(new TranslatableText("crossshulkersort.err.container"), false);
            return 2;
        }
        if (!player.currentScreenHandler.getCursorStack().isEmpty()) {
            // an item is on the cursor (e.g. another inventory mod was mid-action) -
            // rewriting slots now would strand it
            player.sendMessage(new TranslatableText("crossshulkersort.err.carried"), false);
            return 2;
        }

        // ---- snapshot: COPIES, so later partial-consumption bookkeeping cannot
        // contaminate the conservation baseline
        List<ItemStack> inventory = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            inventory.add(inv.getStack(i).copy());
        }

        SortPlan plan = SortPlan.compute(inv, null);
        if (!plan.hasWork) {
            if (!quiet) {
                player.sendMessage(new TranslatableText("crossshulkersort.done.nothing"), false);
            }
            return 0;
        }
        String sigBefore = signature(inventory,
                plan.boxes.stream().map(bx -> bx.contents).toList());

        List<Integer> boxInvSlots = plan.boxes.stream().map(b -> b.invIndex).toList();

        // ---- phase 1: per chosen box, every physically present item counts towards
        // its quota wherever it sits (homes are a planning heuristic only and play
        // no role here); only the shortfall is appended as new stacks while there
        // is room, tracking ACTUAL created amounts
        int chosenCount = plan.chosen.size();
        List<List<ItemStack>> kept = new ArrayList<>(chosenCount);
        List<List<ItemStack>> appended = new ArrayList<>(chosenCount);
        Map<StackKey, Integer> created = new HashMap<>();
        for (int b = 0; b < chosenCount; b++) {
            BoxInfo box = plan.chosen.get(b);
            List<ItemStack> keep = new ArrayList<>();
            List<ItemStack> app = new ArrayList<>();
            for (ItemStack stack : box.contents) {
                if (stack.isEmpty()) {
                    continue;
                }
                keep.add(stack.copy());
            }
            List<Map.Entry<StackKey, Integer>> entries = new ArrayList<>(box.quota.entrySet());
            entries.sort((x, y) -> SortPlan.SORT_ORDER.compare(x.getKey().stack(), y.getKey().stack()));
            for (Map.Entry<StackKey, Integer> e : entries) {
                StackKey key = e.getKey();
                int own = 0;
                for (ItemStack stack : keep) {
                    if (new StackKey(stack).equals(key)) {
                        own += stack.getCount();
                    }
                }
                int toPlace = e.getValue() - own;
                int max = key.stack().getMaxCount();
                while (toPlace > 0 && keep.size() + app.size() < ShulkerRules.BOX_SLOTS) {
                    int count = Math.min(toPlace, max);
                    ItemStack stack = key.stack().copy();
                    stack.setCount(count);
                    app.add(stack);
                    created.merge(key, count, Integer::sum);
                    toPlace -= count;
                }
            }
            // keep physical content and newly placed stacks separate: only appended
            // (newly-created) stacks may ever be trimmed by the conservation cap.
            // The final box layout (written in the apply phase) is keep + appended,
            // sorted by the active order.
            kept.add(keep);
            appended.add(app);
        }

        // ---- phase 2: source consumption for everything created - surpluses sitting
        // in other chosen boxes first (physical holdings beyond their own quotas),
        // then excess boxes, then the inventory
        Map<StackKey, Integer> surplusHeld = new HashMap<>();
        for (int b = 0; b < chosenCount; b++) {
            Map<StackKey, Integer> phys = new HashMap<>();
            for (ItemStack stack : kept.get(b)) {
                if (ShulkerRules.isShulkerBoxItem(stack)) {
                    continue;
                }
                phys.merge(new StackKey(stack), stack.getCount(), Integer::sum);
            }
            Map<StackKey, Integer> quota = plan.chosen.get(b).quota;
            for (Map.Entry<StackKey, Integer> e : phys.entrySet()) {
                int surplus = e.getValue() - Math.min(e.getValue(), quota.getOrDefault(e.getKey(), 0));
                if (surplus > 0) {
                    surplusHeld.merge(e.getKey(), surplus, Integer::sum);
                }
            }
        }
        Map<StackKey, Integer> excessHeld = new HashMap<>();
        for (BoxInfo box : plan.excess) {
            for (ItemStack stack : box.contents) {
                if (!stack.isEmpty() && !ShulkerRules.isShulkerBoxItem(stack)) {
                    excessHeld.merge(new StackKey(stack), stack.getCount(), Integer::sum);
                }
            }
        }
        Map<StackKey, Integer> surplusConsumed = new HashMap<>();
        Map<StackKey, Integer> excessDrained = new HashMap<>();
        Map<StackKey, Integer> invConsumed = new HashMap<>();
        for (Map.Entry<StackKey, Integer> e : created.entrySet()) {
            StackKey key = e.getKey();
            int need = e.getValue();
            int takeSurplus = Math.min(surplusHeld.getOrDefault(key, 0), need);
            if (takeSurplus > 0) {
                surplusConsumed.put(key, takeSurplus);
            }
            int rest = need - takeSurplus;
            int takeExcess = Math.min(excessHeld.getOrDefault(key, 0), rest);
            if (takeExcess > 0) {
                excessDrained.put(key, takeExcess);
            }
            int takeInv = rest - takeExcess;
            if (takeInv > 0) {
                invConsumed.put(key, takeInv);
            }
        }

        // ---- phase 3a: surpluses stay in their box unless consumed (box capacity
        // may have cut the plan - unconsumed surpluses MUST remain where they are)
        List<Map<StackKey, Integer>> quotaPerBox = new ArrayList<>(chosenCount);
        for (int b = 0; b < chosenCount; b++) {
            quotaPerBox.add(plan.chosen.get(b).quota);
        }
        drainSurplus(kept, quotaPerBox, surplusConsumed);

        // ---- phase 3b: excess box residuals - everything except the drained amount
        // (drained items were re-created inside chosen boxes to cover shortfalls);
        // nested shulker items always stay
        Map<Integer, List<ItemStack>> newBoxContents = new HashMap<>();
        for (BoxInfo box : plan.excess) {
            List<ItemStack> residual = new ArrayList<>();
            for (ItemStack stack : box.contents) {
                if (stack.isEmpty()) {
                    continue;
                }
                StackKey key = new StackKey(stack);
                int drainLeft = excessDrained.getOrDefault(key, 0);
                if (!ShulkerRules.isShulkerBoxItem(stack) && drainLeft > 0) {
                    if (stack.getCount() <= drainLeft) {
                        excessDrained.put(key, drainLeft - stack.getCount());
                        continue; // fully drained into the home box
                    }
                    ItemStack rest = stack.copy();
                    rest.setCount(stack.getCount() - drainLeft);
                    residual.add(rest);
                    excessDrained.put(key, 0);
                    continue;
                }
                residual.add(stack); // nested shulker item or un-drained remainder - keep
            }
            newBoxContents.put(box.invIndex, residual);
        }

        // ---- inventory consumption plan (NO mutation yet): full clears and partial
        // reductions are recorded and applied only after the conservation check
        Map<StackKey, Integer> remainingInv = new HashMap<>(invConsumed);
        int[] newCount = new int[36];
        for (int i = 0; i < 36; i++) {
            newCount[i] = -1; // unchanged
        }
        for (int i = 0; i < 36; i++) {
            if (boxInvSlots.contains(i)) {
                continue; // boxes are rewritten via components
            }
            ItemStack stack = inventory.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            StackKey key = new StackKey(stack);
            int take = remainingInv.getOrDefault(key, 0);
            if (take <= 0) {
                continue; // unassigned or surplus - stays
            }
            if (ShulkerRules.isShulkerBoxItem(stack)) {
                continue; // containers are never cargo
            }
            if (stack.getCount() <= take) {
                newCount[i] = 0; // fully consumed
                remainingInv.put(key, take - stack.getCount());
            } else {
                newCount[i] = stack.getCount() - take; // partial consumption
                remainingInv.put(key, 0);
            }
        }

        // ---- conservation check on PURE snapshots (inventory as snapshotted + kept
        // chosen contents + appended contents + excess residuals)
        Map<StackKey, Integer> before = new LinkedHashMap<>();
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                before.merge(countKey(stack), stack.getCount(), Integer::sum);
            }
        }
        for (BoxInfo box : plan.boxes) {
            for (ItemStack stack : box.contents) {
                if (!stack.isEmpty()) {
                    before.merge(countKey(stack), stack.getCount(), Integer::sum);
                }
            }
        }
        Map<StackKey, Integer> after = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            if (boxInvSlots.contains(i) || newCount[i] != 0) {
                ItemStack stack = inventory.get(i);
                int count = newCount[i] >= 0 ? newCount[i] : stack.getCount();
                if (!stack.isEmpty() && count > 0) {
                    after.merge(countKey(stack), count, Integer::sum);
                }
            }
        }
        for (int b = 0; b < chosenCount; b++) {
            for (ItemStack stack : kept.get(b)) {
                if (!stack.isEmpty()) {
                    after.merge(countKey(stack), stack.getCount(), Integer::sum);
                }
            }
            for (ItemStack stack : appended.get(b)) {
                if (!stack.isEmpty()) {
                    after.merge(countKey(stack), stack.getCount(), Integer::sum);
                }
            }
        }
        for (BoxInfo box : plan.excess) {
            for (ItemStack stack : newBoxContents.getOrDefault(box.invIndex, List.of())) {
                if (!stack.isEmpty()) {
                    after.merge(countKey(stack), stack.getCount(), Integer::sum);
                }
            }
        }
        boolean equal = before.size() == after.size();
        if (equal) {
            for (Map.Entry<StackKey, Integer> e : before.entrySet()) {
                Integer other = after.get(e.getKey());
                if (other == null || !other.equals(e.getValue())) {
                    equal = false;
                    break;
                }
            }
        }
        if (!equal) {
            boolean deficit = false;
            // GRACEFUL CAP: over-assigned types (after > before) get their surplus stacks
            // trimmed from the appended (newly-created) lists - the surplus simply stays
            // where it physically is. Deficits (after < before) mean a possible loss and
            // still abort.
            for (Map.Entry<StackKey, Integer> e : before.entrySet()) {
                int surplus = after.getOrDefault(e.getKey(), 0) - e.getValue();
                if (surplus > 0) {
                    int removed = trimAppended(appended, e.getKey(), surplus);
                    com.yeyang.crossshulkersort.CrossShulkerSortClient.LOGGER.warn(
                            "[CSSort] over-assignment capped {}: trimmed {} of {} phantom items",
                            SortPlan.keyName(e.getKey()), removed, surplus);
                    if (removed < surplus) {
                        deficit = true;
                    }
                }
            }
            for (Map.Entry<StackKey, Integer> e : after.entrySet()) {
                if (!before.containsKey(e.getKey())) {
                    int removed = trimAppended(appended, e.getKey(), e.getValue());
                    com.yeyang.crossshulkersort.CrossShulkerSortClient.LOGGER.warn(
                            "[CSSort] unexpected type {}: trimmed {} of {}",
                            SortPlan.keyName(e.getKey()), removed, e.getValue());
                    if (removed < e.getValue()) {
                        deficit = true;
                    }
                } else {
                    int surplus = after.get(e.getKey()) - before.get(e.getKey());
                    if (surplus < 0) {
                        deficit = true; // would lose items
                        com.yeyang.crossshulkersort.CrossShulkerSortClient.LOGGER.error(
                                "[CSSort] conservation DEFICIT {}: before={} after={} diff={}",
                                SortPlan.keyName(e.getKey()), before.get(e.getKey()),
                                after.get(e.getKey()), surplus);
                    }
                }
            }
            if (deficit) {
                for (Map.Entry<StackKey, Integer> e : before.entrySet()) {
                    int other = after.getOrDefault(e.getKey(), 0);
                    if (other != e.getValue()) {
                        com.yeyang.crossshulkersort.CrossShulkerSortClient.LOGGER.error(
                                "[CSSort] conservation MISMATCH {}: before={} after={} diff={}",
                                SortPlan.keyName(e.getKey()), e.getValue(), other, other - e.getValue());
                        dumpTypeTrace(plan, e.getKey(), before, after);
                    }
                }
                player.sendMessage(new TranslatableText("crossshulkersort.err.internal"), false);
                return 2; // abort without touching anything
            }
            // recompute `after` after trimming and re-verify (same accounting as above)
            after.clear();
            for (int i = 0; i < 36; i++) {
                if (boxInvSlots.contains(i) || newCount[i] != 0) {
                    ItemStack stack = inventory.get(i);
                    int count = newCount[i] >= 0 ? newCount[i] : stack.getCount();
                    if (!stack.isEmpty() && count > 0) {
                        after.merge(countKey(stack), count, Integer::sum);
                    }
                }
            }
            for (int b = 0; b < plan.chosen.size(); b++) {
                for (ItemStack stack : kept.get(b)) {
                    if (!stack.isEmpty()) {
                        after.merge(countKey(stack), stack.getCount(), Integer::sum);
                    }
                }
                for (ItemStack stack : appended.get(b)) {
                    if (!stack.isEmpty()) {
                        after.merge(countKey(stack), stack.getCount(), Integer::sum);
                    }
                }
            }
            for (BoxInfo box : plan.excess) {
                for (ItemStack stack : newBoxContents.getOrDefault(box.invIndex, List.of())) {
                    if (!stack.isEmpty()) {
                        after.merge(countKey(stack), stack.getCount(), Integer::sum);
                    }
                }
            }
            equal = before.size() == after.size();
            if (equal) {
                for (Map.Entry<StackKey, Integer> e : before.entrySet()) {
                    Integer other = after.get(e.getKey());
                    if (other == null || !other.equals(e.getValue())) {
                        equal = false;
                        break;
                    }
                }
            }
            if (!equal) {
                player.sendMessage(new TranslatableText("crossshulkersort.err.internal"), false);
                return 2;
            }
        }

        // ---- apply: CHOSEN box components (the kept+appended layout - this was the
        // 2.9.x-2.13.x item-loss bug: they were planned and consumed but never written)
        for (int b = 0; b < plan.chosen.size(); b++) {
            BoxInfo box = plan.chosen.get(b);
            List<ItemStack> contents = new ArrayList<>(kept.get(b));
            contents.addAll(appended.get(b));
            contents.sort(SortPlan.SORT_ORDER);
            ShulkerRules.writeContents(inv.getStack(box.invIndex), contents);
        }
        // ---- apply: excess box components
        for (Map.Entry<Integer, List<ItemStack>> e : newBoxContents.entrySet()) {
            ShulkerRules.writeContents(inv.getStack(e.getKey()), e.getValue());
        }
        // ---- apply: inventory
        for (int i = 0; i < 36; i++) {
            if (newCount[i] == 0) {
                inv.setStack(i, ItemStack.EMPTY);
            } else if (newCount[i] > 0) {
                inv.getStack(i).setCount(newCount[i]);
            }
        }

        // ---- post-apply verification: re-read the REAL state and compare with the plan.
        // Any mismatch triggers a full rollback from the snapshots - zero loss guaranteed
        // even if a future bug slips past the pre-checks.
        Map<StackKey, Integer> actual = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                actual.merge(countKey(stack), stack.getCount(), Integer::sum);
            }
        }
        for (BoxInfo box : plan.boxes) {
            for (ItemStack stack : ShulkerRules.readContents(inv.getStack(box.invIndex))) {
                if (!stack.isEmpty()) {
                    actual.merge(countKey(stack), stack.getCount(), Integer::sum);
                }
            }
        }
        boolean ok = actual.size() == before.size();
        if (ok) {
            for (Map.Entry<StackKey, Integer> e : before.entrySet()) {
                Integer other = actual.get(e.getKey());
                if (other == null || !other.equals(e.getValue())) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) {
            for (Map.Entry<StackKey, Integer> e : before.entrySet()) {
                int other = actual.getOrDefault(e.getKey(), 0);
                if (other != e.getValue()) {
                    com.yeyang.crossshulkersort.CrossShulkerSortClient.LOGGER.error(
                            "[CSSort] POST-APPLY MISMATCH {}: before={} actual={} diff={}",
                            SortPlan.keyName(e.getKey()), e.getValue(), other, other - e.getValue());
                }
            }
            for (Map.Entry<StackKey, Integer> e : actual.entrySet()) {
                if (!before.containsKey(e.getKey())) {
                    com.yeyang.crossshulkersort.CrossShulkerSortClient.LOGGER.error(
                            "[CSSort] POST-APPLY UNEXPECTED {}: actual={}",
                            SortPlan.keyName(e.getKey()), e.getValue());
                }
            }
            com.yeyang.crossshulkersort.CrossShulkerSortClient.LOGGER.error(
                    "[CSSort] POST-APPLY MISMATCH - rolling back everything");
            for (int i = 0; i < 36; i++) {
                inv.setStack(i, inventory.get(i).copy());
            }
            for (BoxInfo box : plan.boxes) {
                List<ItemStack> contents = new ArrayList<>();
                for (ItemStack stack : box.contents) {
                    if (!stack.isEmpty()) {
                        contents.add(stack.copy());
                    }
                }
                ShulkerRules.writeContents(inv.getStack(box.invIndex), contents);
            }
            player.playerScreenHandler.syncState();
        player.playerScreenHandler.sendContentUpdates();
            player.sendMessage(new TranslatableText("crossshulkersort.err.internal"), false);
            return 2; // state restored to the pre-sort snapshot
        }

        player.playerScreenHandler.syncState();
        player.playerScreenHandler.sendContentUpdates();
        List<ItemStack> liveInv = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            liveInv.add(inv.getStack(i).copy());
        }
        List<List<ItemStack>> liveBoxes = new ArrayList<>(plan.boxes.size());
        for (BoxInfo bx : plan.boxes) {
            liveBoxes.add(ShulkerRules.readContents(inv.getStack(bx.invIndex)));
        }
        if (signature(liveInv, liveBoxes).equals(sigBefore)) {
            return 3;
        }
        return 1;
    }

    /** Slot-ordered item/count fingerprint; equal fingerprints mean zero progress. */
    static String signature(List<ItemStack> inv, List<List<ItemStack>> boxContents) {
        StringBuilder sb = new StringBuilder(inv.size() * 6 + boxContents.size() * 32);
        for (ItemStack s : inv) {
            sb.append(s.isEmpty() ? "-" : s.getItem().toString() + s.getCount()).append(',');
        }
        sb.append(';');
        for (List<ItemStack> c : boxContents) {
            for (ItemStack s : c) {
                sb.append(s.isEmpty() ? "-" : s.getItem().toString() + s.getCount()).append(',');
            }
            sb.append(';');
        }
        return sb.toString();
    }

    /**
     * Consumes up to {@code toConsume} items per type from kept stacks, never touching
     * what a box needs for its own quota (min(physical, quota) per box is protected).
     * Whatever is not consumed stays where it is. Nested shulker items are never cargo.
     */
    static void drainSurplus(List<List<ItemStack>> kept, List<Map<StackKey, Integer>> quotaPerBox,
                             Map<StackKey, Integer> toConsume) {
        Map<StackKey, Integer> remaining = new HashMap<>(toConsume);
        for (int b = 0; b < kept.size(); b++) {
            Map<StackKey, Integer> phys = new HashMap<>();
            for (ItemStack stack : kept.get(b)) {
                if (ShulkerRules.isShulkerBoxItem(stack)) {
                    continue;
                }
                phys.merge(new StackKey(stack), stack.getCount(), Integer::sum);
            }
            Map<StackKey, Integer> prot = new HashMap<>();
            Map<StackKey, Integer> quota = quotaPerBox.get(b);
            for (Map.Entry<StackKey, Integer> e : phys.entrySet()) {
                prot.put(e.getKey(), Math.min(e.getValue(), quota.getOrDefault(e.getKey(), 0)));
            }
            List<ItemStack> keep = kept.get(b);
            for (int i = 0; i < keep.size(); i++) {
                ItemStack stack = keep.get(i);
                if (ShulkerRules.isShulkerBoxItem(stack)) {
                    continue;
                }
                StackKey key = new StackKey(stack);
                int p = Math.min(stack.getCount(), prot.getOrDefault(key, 0));
                prot.put(key, prot.getOrDefault(key, 0) - p);
                int avail = stack.getCount() - p;
                if (avail <= 0) {
                    continue;
                }
                int take = Math.min(avail, remaining.getOrDefault(key, 0));
                if (take <= 0) {
                    continue;
                }
                remaining.put(key, remaining.getOrDefault(key, 0) - take);
                if (take >= avail) {
                    if (p == 0) {
                        keep.remove(i);
                        i--;
                    } else {
                        ItemStack rest = stack.copy();
                        rest.setCount(p);
                        keep.set(i, rest);
                    }
                } else {
                    ItemStack rest = stack.copy();
                    rest.setCount(stack.getCount() - take);
                    keep.set(i, rest);
                }
            }
        }
    }

    /** Removes up to {@code amount} items of a type from the appended (newly-created)
     * stacks, newest boxes first. Returns the removed item count. Only appended stacks
     * are touched - home-held kept stacks are never trimmed. */
    static int trimAppended(List<List<ItemStack>> appended, StackKey key, int amount) {
        int removed = 0;
        for (int b = appended.size() - 1; b >= 0 && removed < amount; b--) {
            List<ItemStack> app = appended.get(b);
            for (int i = app.size() - 1; i >= 0 && removed < amount; i--) {
                ItemStack stack = app.get(i);
                if (new StackKey(stack).equals(key)) {
                    int take = Math.min(stack.getCount(), amount - removed);
                    if (take >= stack.getCount()) {
                        app.remove(i);
                        // no extra i-- here: removal shifts the next stack into i
                        // and the loop's own i-- lands exactly on it
                    } else {
                        stack.setCount(stack.getCount() - take);
                    }
                    removed += take;
                }
            }
        }
        return removed;
    }

    /**
     * Conservation identity for counting: shulker box items are keyed WITHOUT their
     * container NBT - the sort legitimately rewrites box contents, and counting
     * the box stack by its full NBT hash would report a phantom loss + gain.
     */
    private static StackKey countKey(ItemStack stack) {
        if (ShulkerRules.isShulkerBoxItem(stack)) {
            ItemStack stripped = stack.copy();
            NbtCompound tag = stripped.getNbt();
            if (tag != null) {
                tag.remove("BlockEntityTag");
                if (tag.isEmpty()) {
                    stripped.setNbt(null);
                }
            }
            return new StackKey(stripped);
        }
        return new StackKey(stack);
    }
}

