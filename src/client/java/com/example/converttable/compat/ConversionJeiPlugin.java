package com.example.converttable.compat;

import com.example.converttable.*;
import java.util.*;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Optional Fabric entry point: no JEI types are referenced by core code. */
@JeiPlugin
public final class ConversionJeiPlugin implements IModPlugin {
    public static final List<IRecipeType<ViewerRecipe>> TYPES=java.util.stream.IntStream.range(0,4)
        .mapToObj(i->IRecipeType.create(ConvertTable.MOD_ID,ViewerRecipe.NAMES[i],ViewerRecipe.class)).toList();
    public static final IRecipeType<GrowthViewerRecipe> GROWTH_TYPE=
        IRecipeType.create(ConvertTable.MOD_ID,"growth",GrowthViewerRecipe.class);
    private static IJeiRuntime runtime;
    public static IJeiRuntime activeRuntime() {return runtime;}
    private List<ViewerRecipe> registered=List.of();
    public ConversionJeiPlugin() {ClientRecipeCatalog.listen(this::refresh);}
    @Override public Identifier getPluginUid() {return ConvertTable.id("jei");}
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        for(int i=0;i<4;i++) registration.addRecipeCategories(new Category(i,
            registration.getJeiHelpers().getGuiHelper().createDrawableItemLike(ViewerRecipe.station(i))));
        registration.addRecipeCategories(new GrowthJeiCategory(
            registration.getJeiHelpers().getGuiHelper().createDrawableItemLike(GrowthBlocks.CATALYST)));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        registered=ViewerRecipe.all();
        for(int i=0;i<4;i++)registration.addRecipes(TYPES.get(i),inCategory(registered,i));
        var growth=GrowthViewerRecipe.all();
        registration.addRecipes(GROWTH_TYPE,growth);
        ConvertTable.LOGGER.info("JEI growth recipes registered: {}",growth.size());
        ConvertTable.LOGGER.info("JEI conversion recipes registered: {}",registered.size());
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for(int i=0;i<4;i++)registration.addCraftingStation(TYPES.get(i),ViewerRecipe.station(i));
        registration.addCraftingStation(GROWTH_TYPE,GrowthBlocks.CATALYST,GrowthBlocks.CRYSTAL);
    }
    @Override public void onRuntimeAvailable(IJeiRuntime value) {runtime=value;refresh();}
    @Override public void onRuntimeUnavailable() {runtime=null;registered=List.of();}
    private void refresh() {
        if(runtime==null)return;
        var fresh=ViewerRecipe.all();
        if(fresh==registered)return;
        for(int i=0;i<4;i++) {
            runtime.getRecipeManager().hideRecipes(TYPES.get(i),inCategory(registered,i));
            runtime.getRecipeManager().addRecipes(TYPES.get(i),inCategory(fresh,i));
        }
        registered=fresh;
    }
    private static List<ViewerRecipe> inCategory(List<ViewerRecipe> all,int category) {
        return all.stream().filter(r->r.category()==category).toList();
    }
    public static final class Category implements IRecipeCategory<ViewerRecipe> {
        private final int category; private final IDrawable icon;
        public Category(int category,IDrawable icon) {this.category=category;this.icon=icon;}
        @Override public IRecipeType<ViewerRecipe> getRecipeType() {return TYPES.get(category);}
        @Override public Component getTitle() {return ViewerRecipe.title(category);}
        @Override public int getWidth() {return 180;}
        @Override public int getHeight() {return 110;}
        @Override public IDrawable getIcon() {return icon;}
        @Override public Identifier getIdentifier(ViewerRecipe r) {return r.identifier();}
        @Override public void setRecipe(IRecipeLayoutBuilder builder,ViewerRecipe r,IFocusGroup focuses) {
            builder.addInputSlot(8,7).setStandardSlotBackground().add(r.input());
            if(!r.reagent().isEmpty()) builder.addSlot(r.category()==1||r.category()==2?RecipeIngredientRole.RENDER_ONLY:RecipeIngredientRole.INPUT,54,7)
                .setStandardSlotBackground().add(r.reagent());
            if(!r.reagent().isEmpty() && r.category()==1)builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).add(r.reagent());
            builder.addOutputSlot(113,7).setOutputSlotBackground().addItemStacks(r.outputs());
            for(int i=0;i<r.returns().size();i++)builder.addOutputSlot(146+i*18,7).setStandardSlotBackground().add(r.returns().get(i));
        }
        @Override public void draw(ViewerRecipe r,IRecipeSlotsView slots,GuiGraphicsExtractor g,double mouseX,double mouseY) {
            var font=Minecraft.getInstance().font;
            if(!r.reagent().isEmpty())g.text(font,"+",35,11,0xff505050,false);g.text(font,">",88,11,0xff505050,false);
            int y=34;
            for(Component line:r.lines()) {g.text(font,font.plainSubstrByWidth(line.getString(),176),2,y,0xff454545,false);y+=12;}
        }
    }
}
