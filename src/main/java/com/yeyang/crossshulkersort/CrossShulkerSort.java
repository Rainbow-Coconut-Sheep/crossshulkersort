package com.yeyang.crossshulkersort;

import com.yeyang.crossshulkersort.sort.ServerSorter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Common (server-side logic) entrypoint. Registers the sort-request packet; the actual
 * data rewrite runs on the server thread in {@link ServerSorter} - like IPN, items are
 * modified directly and synced back, no emulated clicking is involved.
 *
 * NOTE (<=1.16.3 branch): fabric-networking-v0 API - v1 did not exist yet.
 */
public class CrossShulkerSort implements ModInitializer {

    public static final Identifier SORT_REQUEST =
            new Identifier(CrossShulkerSortClient.MOD_ID, "sort_request");

    @Override
    public void onInitialize() {
        // server-side truth for every heuristic switch (singleplayer reloads the
        // same file on the client side, so both sides agree there)
        CrossShulkerSortClient.loadConfig();
        ServerSidePacketRegistry.INSTANCE.register(SORT_REQUEST, (context, buf) ->
                context.getTaskQueue().execute(() -> {
                    if (context.getPlayer() instanceof ServerPlayerEntity) {
                        ServerSorter.sort((ServerPlayerEntity) context.getPlayer());
                    }
                }));
    }
}