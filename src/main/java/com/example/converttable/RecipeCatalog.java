package com.example.converttable;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

/** Server-validated recipe catalogue shared by execution and viewers. */
public record RecipeCatalog(List<Group> groups, List<Advanced> advanced, JsonObject settings, List<String> unavailable) {
    public record Group(String id, String name, int tier, int batch, List<Item> items) {}
    public record Advanced(String id, String source, String name, ItemStack input, ItemStack catalyst,
                           ItemStack output, List<ItemStack> outputs, List<ItemStack> returns, int deaths, boolean auto, String unlock) {
        public boolean random() { return outputs.size() > 1; }
        public boolean matchesTarget(Item target) { return outputs.stream().anyMatch(stack -> stack.is(target)); }
    }
    public static RecipeCatalog empty() { return new RecipeCatalog(List.of(),List.of(),new JsonObject(),List.of()); }
    public static RecipeCatalog parse(String json) {
        if(json.length()>250_000) throw new IllegalArgumentException("Recipe config exceeds 250000 characters");
        JsonObject root=JsonParser.parseString(json).getAsJsonObject();
        if(number(root,"schema_version",1,1)!=1) throw new IllegalArgumentException("Unsupported recipe schema");

        JsonObject settings=root.getAsJsonObject("settings");
        if(settings==null) throw new IllegalArgumentException("settings is required");
        JsonObject piglin=settings.getAsJsonObject("piglin"), end=settings.getAsJsonObject("end"), sculk=settings.getAsJsonObject("sculk");
        if(piglin==null || end==null || sculk==null) throw new IllegalArgumentException("Missing table settings");
        number(piglin,"cost_n",1,64); number(end,"fuel_charge",1,4096); number(end,"charge_per_batch",1,4096);
        if (sculk.has("advanced_charge_per_operation")) number(sculk,"advanced_charge_per_operation",0,4096);
        if (sculk.has("ordinary_souls_per_batch")) number(sculk,"ordinary_souls_per_batch",1,4096);
        if(!"death_count".equals(text(sculk,"advanced_cost")) || sculk.get("consume_player_xp").getAsBoolean())
            throw new IllegalArgumentException("Sculk uses death_count, not player experience");
        settings=settings.deepCopy();
        settings.addProperty("execution_enabled",!root.has("execution_enabled") || root.get("execution_enabled").getAsBoolean());
        number(sculk,"death_count_per_mob",1,4096); number(sculk,"death_count_capacity",1,4096);
        List<String> missing=new ArrayList<>();
        if(item(text(piglin,"cost"),missing)==null || item(text(end,"fuel"),missing)==null)
            throw new IllegalArgumentException("Unknown cost/fuel item");
        List<Group> groups=new ArrayList<>(); List<Advanced> advanced=new ArrayList<>(); Set<String> ids=new HashSet<>();
        JsonArray groupArray=root.getAsJsonArray("groups"), recipeArray=root.getAsJsonArray("advanced");
        if(groupArray==null || recipeArray==null || groupArray.size()>256 || recipeArray.size()>1024)
            throw new IllegalArgumentException("groups/advanced are required and must fit catalogue limits");
        long expanded=0;
        for(JsonElement element:groupArray) {
            JsonObject g=element.getAsJsonObject(); String id=unique(g,ids);
            int tier=switch(text(g,"tier")) {case "piglin"->0;case "end"->1;case "sculk"->2;default->throw new IllegalArgumentException("Invalid tier: "+id);};
            int batch=number(g,"batch",1,4096);
            JsonArray values=g.getAsJsonArray("items");
            if(values==null || values.size()>128) throw new IllegalArgumentException("Invalid group size: "+id);
            LinkedHashSet<Item> items=new LinkedHashSet<>();
            for(JsonElement value:values) {
                Item i=item(value.getAsString(),missing);
                if(i!=null && !items.add(i)) throw new IllegalArgumentException("Duplicate item in "+id);
            }
            if(enabled(g) && items.size()>=2) {
                expanded+=(long)items.size()*(items.size()-1)*(tier<=1?2:1);
                groups.add(new Group(id,text(g,"name"),tier,batch,List.copyOf(items)));
            }
        }
        if(expanded>50_000) throw new IllegalArgumentException("Too many expanded viewer recipes (limit 50000)");
        for(JsonElement element:recipeArray) {
            JsonObject r=element.getAsJsonObject(); String id=unique(r,ids);
            ItemStack input=stack(r,"input","input_n",missing), output=stack(r,"output","output_n",missing);
            List<ItemStack> outputs = new ArrayList<>();
            if (r.has("outputs")) {
                JsonArray pool = r.getAsJsonArray("outputs");
                if (pool.size() < 2 || pool.size() > 128) throw new IllegalArgumentException("Invalid random output pool: "+id);
                Set<Identifier> seen = new HashSet<>();
                Identifier primary = Identifier.tryParse(text(r,"output"));
                for (var value : pool) {
                    Item item = item(value.getAsString(), missing);
                    if (!seen.add(Identifier.tryParse(value.getAsString())))
                        throw new IllegalArgumentException("Duplicate random output: "+id);
                    if (item != null) {
                        outputs.add(new ItemStack(item, number(r,"output_n",1,4096)));
                    }
                }
                if (!seen.contains(primary)) throw new IllegalArgumentException("Primary output is not in random pool: "+id);
                if (outputs.isEmpty()) output = ItemStack.EMPTY;
                else if (output.isEmpty()) output = outputs.getFirst();
            } else if (!output.isEmpty()) outputs.add(output);
            ItemStack catalyst=r.has("catalyst")?stack(r,"catalyst","catalyst_n",missing):ItemStack.EMPTY;
            int deaths=number(r,"deaths",1,4096);
            List<ItemStack> returns=new ArrayList<>(); boolean valid=!input.isEmpty()&&!output.isEmpty()&&(!r.has("catalyst")||!catalyst.isEmpty());
            JsonArray rest=r.getAsJsonArray("returns");
            if(rest!=null && rest.size()>1)throw new IllegalArgumentException("At most one remainder per recipe");
            if(rest!=null) for(JsonElement re:rest) {
                ItemStack back=stack(re.getAsJsonObject(),"item","count",missing);
                if(back.isEmpty())valid=false; else returns.add(back);
            }
            if(enabled(r) && valid) advanced.add(new Advanced(id,optional(r,"source_id",id),text(r,"name"),input,catalyst,output,
                List.copyOf(outputs),List.copyOf(returns),deaths,r.get("auto").getAsBoolean(),optional(r,"unlock","")));
        }
        return new RecipeCatalog(List.copyOf(groups),List.copyOf(advanced),settings.deepCopy(),List.copyOf(new LinkedHashSet<>(missing)));
    }
    private static boolean enabled(JsonObject o) { return !o.has("enabled")||o.get("enabled").getAsBoolean(); }
    private static String unique(JsonObject o,Set<String> ids) {
        String id=text(o,"id").toLowerCase(Locale.ROOT);
        if(!id.matches("[a-z0-9_./-]+")||!ids.add(id))throw new IllegalArgumentException("Invalid/duplicate recipe id: "+id);
        return id;
    }
    private static ItemStack stack(JsonObject o,String key,String count,List<String> missing) {
        int n=number(o,count,1,4096); Item i=item(text(o,key),missing);
        return i==null?ItemStack.EMPTY:new ItemStack(i,n);
    }
    private static Item item(String value,List<String> missing) {
        Identifier id=Identifier.tryParse(value);
        if(id==null || value.contains("|"))throw new IllegalArgumentException("Invalid item ID: "+value);
        if(!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.getValue(id)==Items.AIR) {missing.add(value);return null;}
        return BuiltInRegistries.ITEM.getValue(id);
    }
    public static int number(JsonObject o,String key,int min,int max) {
        JsonElement v=o.get(key);
        if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Missing integer: "+key);
        int n=v.getAsInt();
        if(v.getAsDouble()!=n || n<min||n>max)throw new IllegalArgumentException("Out of range integer: "+key);
        return n;
    }
    public static String text(JsonObject o,String key) {
        if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.get(key).getAsJsonPrimitive().isString())throw new IllegalArgumentException("Missing text: "+key);
        return o.get(key).getAsString();
    }
    private static String optional(JsonObject o,String key,String fallback) {return o.has(key)?text(o,key):fallback;}
}
