package com.example.converttable;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ItemLike;

/** Shared JEI/REI display model. Each selected conversion has an explicit input/output pair. */
public record ViewerRecipe(String id,int category,ItemStack input,ItemStack reagent,List<ItemStack> outputs,
                           List<ItemStack> returns,int batch,int phase,int deaths,boolean auto,String unlock,int fuelCharge) {
    public static final String[] NAMES={"piglin","end","sculk","advanced"};
    public static ItemLike station(int c) {return c==0?ConversionTables.BLACK_GOLD:c==1?ConversionTables.END:ConversionTables.SCULK;}
    public static Component title(int c) {return Component.translatable("gui.convert_table.viewer."+NAMES[c]);}
    public Identifier identifier() {return ConvertTable.id(id);}
    private static Item item(String id) {return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));}
    private static RecipeCatalog cachedSource;
    private static List<ViewerRecipe> cached=List.of();
    public static synchronized List<ViewerRecipe> all() {
        var catalog=ClientRecipeCatalog.current();
        if(catalog==cachedSource)return cached;
        if(catalog.settings().isEmpty())return List.of();
        var pig=catalog.settings().getAsJsonObject("piglin");var end=catalog.settings().getAsJsonObject("end");var sculk=catalog.settings().getAsJsonObject("sculk");
        int fuel=end.get("fuel_charge").getAsInt(),phase=end.get("charge_per_batch").getAsInt();
        int ordinarySouls=sculk.has("ordinary_souls_per_batch")?sculk.get("ordinary_souls_per_batch").getAsInt():1;
        List<ViewerRecipe> result=new ArrayList<>();
        for(var group:catalog.groups()) for(Item input:group.items()) {
            String from=BuiltInRegistries.ITEM.getKey(input).toString().replace(':','/');
            if(group.tier()==0) result.add(new ViewerRecipe("piglin/"+group.id()+"/"+from,0,new ItemStack(input),
                new ItemStack(item(pig.get("cost").getAsString()),pig.get("cost_n").getAsInt()),
                group.items().stream().filter(i->i!=input).map(ItemStack::new).toList(),List.of(),group.batch(),0,0,true,"",fuel));
            for(int c=Math.max(1,group.tier());c<=2;c++) for(Item output:group.items()) if(output!=input)
                result.add(new ViewerRecipe(NAMES[c]+"/"+group.id()+"/"+from+"/"+BuiltInRegistries.ITEM.getKey(output).toString().replace(':','/'),
                    c,new ItemStack(input),c==1?new ItemStack(item(end.get("fuel").getAsString())):ItemStack.EMPTY,List.of(new ItemStack(output)),
                    List.of(),group.batch(),c==1?phase:0,c==2?ordinarySouls:0,true,"",fuel));
        }
        for(var r:catalog.advanced())result.add(new ViewerRecipe("advanced/"+r.id(),3,r.input(),r.catalyst(),r.outputs(),r.returns(),
            0,0,r.deaths(),r.auto(),r.unlock(),fuel));
        cachedSource=catalog;cached=List.copyOf(result);return cached;
    }
    public List<Component> lines() {
        List<Component> lines=new ArrayList<>();
        if(category<3)lines.add(Component.translatable(category==0?"gui.convert_table.viewer.random":"gui.convert_table.viewer.batch",batch));
        if(phase>0)lines.add(Component.translatable("gui.convert_table.viewer.phase",phase,fuelCharge));
        if(deaths>0)lines.add(Component.translatable(category==2?"gui.convert_table.viewer.souls_batch":"gui.convert_table.viewer.deaths",deaths));
        lines.add(Component.translatable("gui.convert_table.viewer.duration",category==0?4:category==1?2:1));
        if(category==3 && outputs.size()>1) lines.add(Component.translatable("gui.convert_table.viewer.random_pool"));
        if(category==3)lines.add(Component.translatable(auto?"gui.convert_table.viewer.auto":"gui.convert_table.viewer.manual"));
        if(!unlock.isEmpty())lines.add(Component.literal(unlock));
        lines.add(Component.translatable(ClientRecipeCatalog.current().settings().get("execution_enabled").getAsBoolean()?"gui.convert_table.viewer.preview":"gui.convert_table.disabled"));
        return List.copyOf(lines);
    }
}
