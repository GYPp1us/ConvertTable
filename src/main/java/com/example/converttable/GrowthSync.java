package com.example.converttable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The server's actual growth catalogue, rather than the client's bundled copy. */
public record GrowthSync(String json) implements CustomPacketPayload {
    public static final Type<GrowthSync> TYPE = new Type<>(ConvertTable.id("growth_catalogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GrowthSync> CODEC = new StreamCodec<>() {
        @Override public GrowthSync decode(RegistryFriendlyByteBuf buffer) { return new GrowthSync(buffer.readUtf(250_000)); }
        @Override public void encode(RegistryFriendlyByteBuf buffer, GrowthSync payload) { buffer.writeUtf(payload.json(), 250_000); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
