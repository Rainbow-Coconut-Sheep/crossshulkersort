package com.yeyang.crossshulkersort;

import com.yeyang.crossshulkersort.sort.ServerSorter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Common (server-side logic) entrypoint. Registers the sort-request payload; the actual
 * data rewrite runs on the server thread in {@link ServerSorter} - like IPN, items are
 * modified directly and synced back, no emulated clicking is involved.
 */
public class CrossShulkerSort implements ModInitializer {

    public record SortRequestPayload() implements CustomPacketPayload {
        public static final SortRequestPayload INSTANCE = new SortRequestPayload();
        public static final CustomPacketPayload.Type<SortRequestPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CrossShulkerSortClient.MOD_ID, "sort_request"));
        public static final StreamCodec<FriendlyByteBuf, SortRequestPayload> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @Override
    public void onInitialize() {
        // server-side truth for every heuristic switch (singleplayer reloads the
        // same file on the client side, so both sides agree there)
        CrossShulkerSortClient.loadConfig();
        PayloadTypeRegistry.serverboundPlay().register(SortRequestPayload.TYPE, SortRequestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SortRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> ServerSorter.sort(context.player())));
    }
}
