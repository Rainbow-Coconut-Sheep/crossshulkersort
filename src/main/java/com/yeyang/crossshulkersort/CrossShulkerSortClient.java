package com.yeyang.crossshulkersort;

import com.yeyang.crossshulkersort.config.ModConfig;
import com.yeyang.crossshulkersort.sort.SortPlan;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.minecraft.item.ItemStack;

import java.util.Comparator;
import java.util.logging.Logger;

public class CrossShulkerSortClient implements ClientModInitializer {

    public static final String MOD_ID = "crossshulkersort";
    // NOTE (<=1.16 branch): java.util.logging - zero deps (no slf4j here).
    public static final Logger LOGGER = Logger.getLogger(MOD_ID);

    /** slf4j-style {} formatting on top of jul (keeps call sites version-independent). */
    public static void logSevere(String pattern, Object... args) {
        LOGGER.severe(format(pattern, args));
    }

    /** slf4j-style {} formatting on top of jul (keeps call sites version-independent). */
    public static void logWarning(String pattern, Object... args) {
        LOGGER.warning(format(pattern, args));
    }

    private static String format(String pattern, Object... args) {
        StringBuilder sb = new StringBuilder();
        int ai = 0;
        int i = 0;
        while (true) {
            int j = pattern.indexOf("{}", i);
            if (j < 0 || ai >= args.length) {
                sb.append(pattern.substring(i));
                break;
            }
            sb.append(pattern, i, j).append(args[ai++]);
            i = j + 2;
        }
        return sb.toString();
    }

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
                    .comparing((ItemStack s) -> net.minecraft.util.registry.Registry.ITEM
                            .getId(s.getItem()).toString())
                    .thenComparing(s -> java.util.Objects.hashCode(s.getTag()))
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
        ClientSidePacketRegistry.INSTANCE.sendToServer(CrossShulkerSort.SORT_REQUEST,
                new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer()));
    }
}

