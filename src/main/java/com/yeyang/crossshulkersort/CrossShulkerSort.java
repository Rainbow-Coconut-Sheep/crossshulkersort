package com.yeyang.crossshulkersort;

import com.yeyang.crossshulkersort.sort.ServerSorter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

/**
 * Common (server-side logic) entrypoint. Registers the sort-request packet; the actual
 * data rewrite runs on the server thread in {@link ServerSorter} - like IPN, items are
 * modified directly and synced back, no emulated clicking is involved.
 *
 * NOTE (1.15 branch): v1 server receiver + v0 client sender (ClientPlayNetworking
 * did not exist yet); PacketByteBuf lives in net.minecraft.util here.
 */
public class CrossShulkerSort implements ModInitializer {

    public static final Identifier SORT_REQUEST =
            new Identifier(CrossShulkerSortClient.MOD_ID, "sort_request");

    @Override
    public void onInitialize() {
        // server-side truth for every heuristic switch (singleplayer reloads the
        // same file on the client side, so both sides agree there)
        CrossShulkerSortClient.loadConfig();
        ServerPlayNetworking.registerGlobalReceiver(SORT_REQUEST,
                (server, player, handler, buf, sender) ->
                        server.execute(() -> ServerSorter.sort(player)));
    }
}
