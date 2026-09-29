package com.example.converttable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RecipeSync(String json) implements CustomPacketPayload {
    public static final Type<RecipeSync> TYPE=new Type<>(ConvertTable.id("recipe_catalogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RecipeSync> CODEC=new StreamCodec<>() {
        public RecipeSync decode(RegistryFriendlyByteBuf b) {return new RecipeSync(b.readUtf(250_000));}
        public void encode(RegistryFriendlyByteBuf b,RecipeSync p) {b.writeUtf(p.json,250_000);}
    };
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
