package com.yeyang.crossshulkersort;

import com.yeyang.crossshulkersort.config.ModConfig;
import com.yeyang.crossshulkersort.sort.SortPlan;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

public class CrossShulkerSortClient implements ClientModInitializer {

    public static final String MOD_ID = "crossshulkersort";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModConfig config;

    public static ModConfig config() {
        return config;
    }

    /** (Re)loads the JSON file; called once per side at startup. */
    public static void loadConfig() {
        config = ModConfig.load();
    }

    /** Applies the sort-order switch; called at startup and on every menu save. */
    public static void refreshSortOrder() {
        if (ModConfig.effective().useItemScrollerOrder) {
            SortPlan.setSortOrder(com.yeyang.crossshulkersort.sort.ItemScrollerSortOrder.COMPARATOR);
        } else {
            SortPlan.setSortOrder(Comparator
                    .comparing((ItemStack s) -> net.minecraft.registry.Registries.ITEM
                            .getId(s.getItem()).toString())
                    .thenComparing(s -> java.util.Objects.hashCode(s.getNbt()))
                    .thenComparing(s -> -s.getCount()));
        }
    }

    @Override
    public void onInitializeClient() {
        loadConfig();
        // server-side sorting mirrors Item Scroller's order when the mod is present
        refreshSortOrder();
        LOGGER.info("[Cross Shulker Sort] client loaded, Q button sends a sort request to the server");
    }

    /** Sent when the Q button is pressed; the server does the actual sorting. */
    public static void requestSort() {
        ClientPlayNetworking.send(CrossShulkerSort.SORT_REQUEST,
                net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
    }
}
