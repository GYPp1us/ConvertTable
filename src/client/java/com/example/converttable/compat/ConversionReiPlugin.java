package com.example.converttable.compat;

import com.example.converttable.*;
import java.util.*;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.*;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.*;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.*;
import me.shedaniel.rei.api.common.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Optional REI entry point; queries the current server snapshot on every search. */
public final class ConversionReiPlugin implements REIClientPlugin {
    public static final List<CategoryIdentifier<Display>> TYPES=java.util.stream.IntStream.range(0,4)
        .mapToObj(i->CategoryIdentifier.<Display>of(ConvertTable.id(ViewerRecipe.NAMES[i]))).toList();
    @Override public void registerCategories(CategoryRegistry registry) {
        for(int i=0;i<4;i++) {
            registry.add(new Category(i));
            registry.addWorkstations(TYPES.get(i),EntryStacks.of(ViewerRecipe.station(i)));
            registry.removePlusButton(TYPES.get(i));
        }
    }
    @Override public void registerDisplays(DisplayRegistry registry) {
        for(int i=0;i<4;i++) {
            final int c=i;
            registry.registerDisplayGenerator(TYPES.get(i),new DynamicDisplayGenerator<Display>() {
                @Override public Optional<List<Display>> generate(ViewSearchBuilder search) {
                    if(!search.getCategories().isEmpty() && !search.getCategories().contains(TYPES.get(c)))return Optional.empty();
                    if(!search.getRecipesFor().isEmpty() || !search.getUsagesFor().isEmpty())return Optional.empty();
                    return Optional.of(ViewerRecipe.all().stream().filter(r->r.category()==c)
                        .filter(r->search.getRecipesFor().isEmpty() || search.getRecipesFor().stream().anyMatch(e->matchesOutput(r,e)))
                        .filter(r->search.getUsagesFor().isEmpty() || search.getUsagesFor().stream().anyMatch(e->matchesInput(r,e)))
                        .map(Display::new).toList());
                }
                @Override public Optional<List<Display>> getRecipeFor(EntryStack<?> entry) {
                    return Optional.of(ViewerRecipe.all().stream().filter(r->r.category()==c && matchesOutput(r,entry)).map(Display::new).toList());
                }
                @Override public Optional<List<Display>> getUsageFor(EntryStack<?> entry) {
                    return Optional.of(ViewerRecipe.all().stream().filter(r->r.category()==c && matchesInput(r,entry)).map(Display::new).toList());
                }
            });
        }
    }
    private static boolean matchesInput(ViewerRecipe r,EntryStack<?> e) {
        return e.getValue() instanceof ItemStack stack && (r.input().is(stack.getItem()) || r.reagent().is(stack.getItem()));
    }
    private static boolean matchesOutput(ViewerRecipe r,EntryStack<?> e) {
        return e.getValue() instanceof ItemStack stack && java.util.stream.Stream.concat(r.outputs().stream(),r.returns().stream()).anyMatch(s->s.is(stack.getItem()));
    }
    public static final class Display extends BasicDisplay {
        public final ViewerRecipe recipe;
        public Display(ViewerRecipe r) {
            super(inputs(r),outputs(r),Optional.of(r.identifier()));recipe=r;
        }
        private static List<EntryIngredient> inputs(ViewerRecipe r) {
            List<EntryIngredient> entries=new ArrayList<>();entries.add(EntryIngredients.of(r.input()));
            if(!r.reagent().isEmpty())entries.add(EntryIngredients.of(r.reagent()));return entries;
        }
        private static List<EntryIngredient> outputs(ViewerRecipe r) {
            List<EntryIngredient> entries=new ArrayList<>();
            entries.add(EntryIngredient.of(r.outputs().stream().map(EntryStacks::of).toList()));
            for(var rest:r.returns())entries.add(EntryIngredients.of(rest));return entries;
        }
        @Override public CategoryIdentifier<Display> getCategoryIdentifier() {return TYPES.get(recipe.category());}
        @Override public me.shedaniel.rei.api.common.display.DisplaySerializer<? extends me.shedaniel.rei.api.common.display.Display> getSerializer() {return null;}
    }
    private record Category(int index) implements DisplayCategory<Display> {
        @Override public CategoryIdentifier<Display> getCategoryIdentifier() {return TYPES.get(index);}
        @Override public Component getTitle() {return ViewerRecipe.title(index);}
        @Override public Renderer getIcon() {return EntryStacks.of(ViewerRecipe.station(index));}
        @Override public int getDisplayHeight() {return 118;}
        @Override public int getDisplayWidth(Display display) {return 190;}
        @Override public List<Widget> setupDisplay(Display display,Rectangle bounds) {
            List<Widget> widgets=new ArrayList<>();widgets.add(Widgets.createRecipeBase(bounds));
            int x=bounds.x+8,y=bounds.y+8;
            var inputs=display.getInputEntries();var outputs=display.getOutputEntries();
            widgets.add(Widgets.createSlot(new Point(x,y)).entries(inputs.getFirst()).markInput());
            if(inputs.size()>1)widgets.add(Widgets.createSlot(new Point(x+46,y)).entries(inputs.get(1)).markInput());
            widgets.add(Widgets.createArrow(new Point(x+77,y)));
            widgets.add(Widgets.createSlot(new Point(x+105,y)).entries(outputs.getFirst()).markOutput());
            for(int i=1;i<outputs.size();i++)widgets.add(Widgets.createSlot(new Point(x+120+i*18,y)).entries(outputs.get(i)).markOutput());
            y+=28;
            for(var line:display.recipe.lines()) {
                widgets.add(Widgets.createLabel(new Point(x,y),line).leftAligned().noShadow().color(0xff404040,0xffe0e0e0));y+=12;
            }
            return widgets;
        }
    }
}
