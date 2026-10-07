package com.example.converttable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/** Blocks configuration before world admission until the exact installed mod version matches. */
public final class VersionGate {
    private static final Map<ServerConfigurationPacketListenerImpl, Task> PENDING = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final ConfigurationTask.Type TASK_TYPE = new ConfigurationTask.Type("convert_table:version_gate");
    private VersionGate() { }
    public static String version() {
        return FabricLoader.getInstance().getModContainer(ConvertTable.MOD_ID).orElseThrow()
            .getMetadata().getVersion().getFriendlyString();
    }
    public record Query(String version) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(ConvertTable.id("version_query"));
        public static final StreamCodec<FriendlyByteBuf, Query> CODEC = new StreamCodec<>() {
            public Query decode(FriendlyByteBuf buffer) { return new Query(buffer.readUtf(128)); }
            public void encode(FriendlyByteBuf buffer, Query value) { buffer.writeUtf(value.version(), 128); }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Reply(String version) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(ConvertTable.id("version_reply"));
        public static final StreamCodec<FriendlyByteBuf, Reply> CODEC = new StreamCodec<>() {
            public Reply decode(FriendlyByteBuf buffer) { return new Reply(buffer.readUtf(128)); }
            public void encode(FriendlyByteBuf buffer, Reply value) { buffer.writeUtf(value.version(), 128); }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void initialize() {
        PayloadTypeRegistry.clientboundConfiguration().register(Query.TYPE, Query.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(Reply.TYPE, Reply.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (!ServerConfigurationNetworking.canSend(handler, Query.TYPE)) {
                handler.disconnect(Component.literal("需要与服务器一致的 ConvertTable " + version()
                    + "。客户端未安装或版本过旧 / Client mod missing or outdated."));
                return;
            }
            Task task = new Task(handler);
            PENDING.put(handler, task);
            ((FabricServerConfigurationPacketListenerImpl) handler).addTask(task);
        });
        ServerConfigurationNetworking.registerGlobalReceiver(Reply.TYPE, (reply, context) -> context.server().execute(() -> {
            var handler = context.packetListener();
            Task task = PENDING.get(handler);
            if (task == null || !task.started) {
                handler.disconnect(Component.literal("Invalid ConvertTable version handshake."));
            } else if (!version().equals(reply.version())) {
                PENDING.remove(handler);
                handler.disconnect(Component.literal("ConvertTable 版本不一致 / Version mismatch: 服务器 "
                    + version() + "，客户端 " + reply.version() + "。请安装相同版本。"));
            } else {
                task.matched = true;
                ConvertTable.LOGGER.info("Validated ConvertTable client version {} before world admission",version());
            }
        }));
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler));
    }
    private static final class Task implements ConfigurationTask {
        private final ServerConfigurationPacketListenerImpl handler;
        private volatile boolean started;
        private volatile boolean matched;
        private long deadline;
        Task(ServerConfigurationPacketListenerImpl handler) { this.handler = handler; }
        @Override public void start(Consumer<Packet<?>> sender) {
            started = true;
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(new Query(version())));
        }
        @Override public boolean tick() {
            if (matched) { PENDING.remove(handler); return true; }
            if (started && System.nanoTime() >= deadline) {
                PENDING.remove(handler);
                handler.disconnect(Component.literal("ConvertTable 版本校验超时 / Version check timed out. 服务器要求 " + version()));
            }
            return false;
        }
        @Override public ConfigurationTask.Type type() { return TASK_TYPE; }
    }
}
