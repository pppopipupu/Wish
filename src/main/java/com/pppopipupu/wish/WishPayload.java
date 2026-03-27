package com.pppopipupu.wish;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WishPayload(String wishText) implements CustomPacketPayload {
    public static final Type<WishPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wish.MODID, "wish_payload"));

    public static final StreamCodec<ByteBuf, WishPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WishPayload::wishText,
            WishPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
