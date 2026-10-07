package com.example.converttable;

import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Real configuration connections to an isolated dedicated server, before any player enters its world. */
final class VersionGateGameTest {
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    static void run(ClientGameTestContext context) {
        Properties settings=new Properties();
        settings.setProperty("online-mode","false");
        // The test API connects using the configured port, so reserve a free local port first.
        try(var socket=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))) {
            settings.setProperty("server-port",Integer.toString(socket.getLocalPort()));
        } catch(java.io.IOException error) { throw new IllegalStateException("Cannot choose a local test port",error); }
        settings.setProperty("server-ip","127.0.0.1");
        settings.setProperty("view-distance","2");
        settings.setProperty("simulation-distance","2");
        try(var server=context.worldBuilder().createServer(settings)) {
            try(var connection=server.connect()) {
                server.runOnServer(game->check(game.getPlayerList().getPlayerCount()==1,"Matching mod could not join"));
            }
            for (String oldVersion : new String[]{"0.3.1", "0.2.0"}) {
                context.runOnClient(mc->{
                    ClientConfigurationNetworking.unregisterGlobalReceiver(VersionGate.Query.TYPE);
                    ClientConfigurationNetworking.registerGlobalReceiver(VersionGate.Query.TYPE,(query,reply)->
                        reply.responseSender().sendPacket(new VersionGate.Reply(oldVersion)));
                });
                reject(context,server,"Version mismatch");
            }
            context.runOnClient(mc->ClientConfigurationNetworking.unregisterGlobalReceiver(VersionGate.Query.TYPE));
            reject(context,server,"Client mod missing or outdated");
            ConvertTable.LOGGER.info("VERSION_GATE_TEST_PASS: real dedicated configuration accepts {}, rejects 0.3.1, 0.2.0 and missing handshake before player/world admission",VersionGate.version());
        } finally {
            context.runOnClient(mc->{
                ClientConfigurationNetworking.unregisterGlobalReceiver(VersionGate.Query.TYPE);
                ClientVersionGate.initialize();
                mc.gui.setScreen(new TitleScreen());
            });
        }
    }
    private static void reject(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext server,String reason) {
        int port=server.computeOnServer(game->game.getPort());
        context.runOnClient(mc->{
            String address="localhost:"+port;
            mc.gui.setScreen(new TitleScreen());
            ConnectScreen.startConnecting(mc.gui.screen(),mc,ServerAddress.parseString(address),
                new ServerData("Version gate test",address,ServerData.Type.OTHER),false,null);
        });
        context.waitFor(mc->mc.gui.screen() instanceof DisconnectedScreen,300);
        context.runOnClient(mc->check(mc.gui.screen().getNarrationMessage().getString().contains(reason),
            "Unexpected disconnect reason: "+mc.gui.screen().getNarrationMessage().getString()));
        server.runOnServer(game->check(game.getPlayerList().getPlayerCount()==0,"Rejected mod entered the world"));
    }
}
