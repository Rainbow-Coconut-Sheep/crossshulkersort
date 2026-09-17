package com.yeyang.crossshulkersort;

import com.yeyang.crossshulkersort.sort.ServerSorter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Common (server-side logic) entrypoint. Registers the sort-request payload; the actual
 * data rewrite runs on the server thread in {@link ServerSorter} - like IPN, items are
 * modified directly and synced back, no emulated clicking is involved.
 */
public class CrossShulkerSort implements ModInitializer {

    public record SortRequestPayload() implements CustomPayload {
        public static final SortRequestPayload INSTANCE = new SortRequestPayload();
        public static final CustomPayload.Id<SortRequestPayload> ID =
                new CustomPayload.Id<>(new Identifier(CrossShulkerSortClient.MOD_ID, "sort_request"));
        public static final PacketCodec<PacketByteBuf, SortRequestPayload> CODEC = PacketCodec.unit(INSTANCE);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    @Override
    public void onInitialize() {
        // server-side truth for every heuristic switch (singleplayer reloads the
        // same file on the client side, so both sides agree there)
        CrossShulkerSortClient.loadConfig();
        PayloadTypeRegistry.playC2S().register(SortRequestPayload.ID, SortRequestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SortRequestPayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> ServerSorter.sort(context.player())));
    }
}
