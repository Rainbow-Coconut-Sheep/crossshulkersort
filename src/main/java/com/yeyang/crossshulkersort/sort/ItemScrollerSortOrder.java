package com.yeyang.crossshulkersort.sort;

import fi.dy.masa.itemscroller.config.Configs;
import fi.dy.masa.itemscroller.util.SortingCategory;
import fi.dy.masa.itemscroller.util.SortingMethod;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.Comparator;

/**
 * Stack ordering that mirrors Item Scroller's own inventory-sort comparator, evaluated
 * against the user's live Item Scroller configuration (sort method, category order,
 * shulker placement, custom priorities). Falls back to raw-id ordering for any
 * configuration that cannot be read.
 *
 * <p>1.21.1 has no bundle item, so bundle placement is not special-cased here.
 */
public final class ItemScrollerSortOrder {

    public static final Comparator<ItemStack> COMPARATOR = ItemScrollerSortOrder::compare;

    private ItemScrollerSortOrder() {
    }

    private static int compare(ItemStack a, ItemStack b) {
        try {
            return compareSafe(a, b);
        } catch (Throwable t) {
            return a.getItem() == b.getItem() ? 0
                    : Registries.ITEM.getId(a.getItem()).toString()
                            .compareTo(Registries.ITEM.getId(b.getItem()).toString());
        }
    }

    private static int compareSafe(ItemStack a, ItemStack b) {
        boolean aBox = isShulkerBox(a);
        boolean bBox = isShulkerBox(b);
        if (boxesAtEnd() && aBox != bBox) {
            return Boolean.compare(aBox, bBox);
        }

        boolean aEmpty = a.isEmpty();
        boolean bEmpty = b.isEmpty();
        if (aEmpty != bEmpty) {
            return Boolean.compare(aEmpty, bEmpty);
        }
        if (aEmpty) {
            return 0;
        }

        if (aBox && bBox) {
            return Integer.compare(boxContentCount(a), boxContentCount(b));
        }

        SortingMethod method = sortMethod();

        if (isCategoryMethod(method)) {
            Integer c1 = categoryIndex(a);
            Integer c2 = categoryIndex(b);
            if (c1 == null || c2 == null) {
                if (c1 != c2) {
                    return c1 == null ? 1 : -1; // unclassified items go last
                }
            } else if (!c1.equals(c2)) {
                return Integer.compare(orderIndex(c1), orderIndex(c2));
            }
        }

        if (a.getItem() != b.getItem()) {
            if (method == SortingMethod.CATEGORY_NAME || method == SortingMethod.ITEM_NAME) {
                return a.getName().getString().compareTo(b.getName().getString());
            }
            if (method == SortingMethod.CATEGORY_COUNT || method == SortingMethod.ITEM_COUNT) {
                int byCount = Integer.compare(b.getCount(), a.getCount());
                return byCount != 0 ? byCount : rawId(a) - rawId(b);
            }
            if (method == SortingMethod.CATEGORY_RARITY || method == SortingMethod.ITEM_RARITY) {
                int byRarity = a.getRarity().compareTo(b.getRarity());
                return byRarity != 0 ? byRarity : rawId(a) - rawId(b);
            }
            return rawId(a) - rawId(b);
        }
        if (!ItemStack.areItemsAndComponentsEqual(a, b)) {
            return Integer.compare(a.getComponents().hashCode(), b.getComponents().hashCode());
        }
        return Integer.compare(b.getCount(), a.getCount());
    }

    private static boolean isCategoryMethod(SortingMethod method) {
        return method == SortingMethod.CATEGORY_NAME || method == SortingMethod.CATEGORY_COUNT
                || method == SortingMethod.CATEGORY_RARITY || method == SortingMethod.CATEGORY_RAWID;
    }

    private static SortingMethod sortMethod() {
        try {
            Object value = Configs.Generic.SORT_METHOD_DEFAULT.getOptionListValue();
            if (value instanceof SortingMethod method) {
                return method;
            }
        } catch (Throwable ignored) {
        }
        return SortingMethod.CATEGORY_NAME;
    }

    private static Object cachedDisplayContext;

    private static Integer categoryIndex(ItemStack stack) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            return null;
        }
        if (cachedDisplayContext == null) {
            cachedDisplayContext = SortingCategory.INSTANCE.buildDisplayContext(mc);
        }
        var ctx = cachedDisplayContext;
        if (ctx == null) {
            return null;
        }
        var entry = SortingCategory.INSTANCE.fromItemStack(stack);
        if (entry == null) {
            return null;
        }
        int index = Configs.Generic.SORT_CATEGORY_ORDER.getEntryIndex(entry);
        return index < 0 ? null : index;
    }

    private static int orderIndex(Integer classifiedIndex) {
        return classifiedIndex;
    }

    private static int rawId(ItemStack stack) {
        return Registries.ITEM.getRawId(stack.getItem());
    }

    private static boolean boxesAtEnd() {
        try {
            return Configs.Generic.SORT_SHULKER_BOXES_AT_END.getBooleanValue();
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static int boxContentCount(ItemStack box) {
        return (int) box.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT)
                .streamNonEmpty().count();
    }

    /**
     * Mirrors Item Scroller's custom priority lists; without configuration every item is
     * unprioritized (-1) and this comparator stage is skipped.
     */
}
