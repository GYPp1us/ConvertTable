package com.example.converttable;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Credit explicit operators when a real server crafting cycle finishes. */
final class TableAdvancements {
    static final String CRITERION = "complete";
    static final Identifier ROOT = ConvertTable.id("root");

    private TableAdvancements() { }

    private static boolean award(ServerLevel level, UUID operator, Identifier id) {
        var player = level.getServer().getPlayerList().getPlayer(operator);
        if (player == null) return false;
        var manager = level.getServer().getAdvancements();
        var advancement = manager.get(id);
        if (advancement == null) return false;
        player.getAdvancements().award(advancement, CRITERION);
        return true;
    }

    static final class Credit {
        private UUID operator;
        // One durable deferred credit per station, with no extra world or player storage.
        private UUID pending;

        void startedBy(Player player) {
            if (player instanceof ServerPlayer) operator = player.getUUID();
        }

        UUID operator() { return operator; }

        /** Called after a validated conversion timer or a real growth synthesis finishes. */
        void completed(Level level, Identifier id) {
            if (!(level instanceof ServerLevel server) || operator == null) return;
            if (!award(server, operator, id) && pending == null) pending = operator;
        }

        /** Delivers already earned credit after an offline operator returns. */
        boolean deliverPending(Level level, Identifier id) {
            if (pending == null || !(level instanceof ServerLevel server) || !award(server, pending, id)) return false;
            pending = null;
            return true;
        }

        void save(ValueOutput output) {
            if (operator != null) output.putString("AdvancementOperator", operator.toString());
            if (pending != null) output.putString("PendingAdvancementOperator", pending.toString());
        }

        void load(ValueInput input) {
            operator = parse(input.getStringOr("AdvancementOperator", ""));
            pending = parse(input.getStringOr("PendingAdvancementOperator", ""));
        }

        private static UUID parse(String value) {
            try { return UUID.fromString(value); }
            catch (IllegalArgumentException ignored) { return null; }
        }
    }
}
