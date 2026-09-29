package com.example.converttable;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.*;

public final class ClientRecipeCatalog {
    private static volatile RecipeCatalog current=RecipeCatalog.empty();
    private static final List<Runnable> listeners=new CopyOnWriteArrayList<>();
    public static RecipeCatalog current() {return current;}
    public static void listen(Runnable listener) {listeners.add(listener);}
    private static void update(RecipeCatalog catalog) {
        current=catalog;
        for(Runnable listener:listeners) listener.run();
    }
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(RecipeSync.TYPE,(packet,context)->
            context.client().execute(()->update(RecipeCatalog.parse(packet.json()))));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->update(RecipeCatalog.empty()));
    }
}
