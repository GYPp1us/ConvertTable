package com.example.converttable;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;

/** Replies with actual Fabric metadata before entering the server's world. */
public final class ClientVersionGate {
    private ClientVersionGate() { }
    public static void initialize() {
        ClientConfigurationNetworking.registerGlobalReceiver(VersionGate.Query.TYPE, (query, context) ->
            context.responseSender().sendPacket(new VersionGate.Reply(VersionGate.version())));
    }
}
