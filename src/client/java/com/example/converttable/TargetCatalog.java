package com.example.converttable;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/** Always reads the server-synchronized recipe catalogue. */
final class TargetCatalog {
    List<Item> candidates(Item source,boolean restrict,String query,int variant) {
        String q=query.toLowerCase(Locale.ROOT).strip();
        var catalog=ClientRecipeCatalog.current();
        var ordinary=catalog.groups().stream().filter(g->g.tier()<=variant && (!restrict||g.items().contains(source)))
            .flatMap(g->g.items().stream()).filter(i->!restrict||i!=source);
        var advanced=variant==2?catalog.advanced().stream().filter(r->!restrict||r.input().is(source))
            .flatMap(r->r.outputs().stream()).map(net.minecraft.world.item.ItemStack::getItem)
            :java.util.stream.Stream.<Item>empty();
        return java.util.stream.Stream.concat(ordinary,advanced).distinct()
            .filter(i->i.getDefaultInstance().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                ||BuiltInRegistries.ITEM.getKey(i).toString().contains(q)).toList();
    }
}
