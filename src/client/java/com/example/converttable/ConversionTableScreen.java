package com.example.converttable;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Integer-pixel vanilla GUI. Counts and map are exclusively server snapshots. */
public final class ConversionTableScreen extends AbstractContainerScreen<ConversionTableMenu> {
    private final TargetCatalog catalogue;
    private final Button[] modes = new Button[3];
    private Button continuous;
    private Button matching, targetTab, rangeTab, previous, next;
    private EditBox search;
    private boolean showRange;
    private List<Item> candidates = List.of();
    private int page;
    private static final int TEXT = 0xff303136, MUTED = 0xff626570;
    public ConversionTableScreen(ConversionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.variant == 0 ? 176 : 320, menu.variant == 0 ? 198 : 238);
        catalogue = menu.variant == 0 ? null : new TargetCatalog();
        showRange = menu.variant == 2;
        inventoryLabelX = menu.variant == 0 ? 8 : 16;
        inventoryLabelY = menu.variant == 0 ? 104 : 142;
    }
    private Component tr(String key, Object... args) { return Component.translatable("gui.convert_table." + key, args); }
    private Button button(int x, int y, int w, String key, Runnable action) {
        return addRenderableWidget(Button.builder(tr(key), b -> action.run()).bounds(leftPos+x, topPos+y, w, 18).build());
    }
    private void send(int id) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    @Override protected void init() {
        super.init();
        button(8,menu.variant==0?65:108,80,"convert",()->send(10));
        continuous=button(94,menu.variant==0?65:108,menu.variant==0?74:84,"run.0",()->send(11));
        if (menu.variant == 0) return;
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            modes[i] = button(8 + 58*i, 25, 56, "mode." + i, () -> send(mode));
            modes[i].setTooltip(Tooltip.create(tr("mode_help."+i)));
        }
        matching = button(112,45,66,"match.0",()->send(3));
        matching.setHeight(12);
        targetTab = button(194,25,menu.variant==2 ? 56 : 116,"targets",()-> {showRange=false; updateControls();});
        if (menu.variant == 2) rangeTab = button(254,25,56,"range",()-> {showRange=true; updateControls();});
        search = addRenderableWidget(new EditBox(font,leftPos+194,topPos+48,116,16,tr("search")));
        search.setHint(tr("search")); search.setMaxLength(64);
        search.setResponder(s -> {page=0; updateCandidates();});
        previous = button(194,154,54,"previous",()-> {page=Math.max(0,page-1);updateControls();});
        next = button(256,154,54,"next",()-> {page++;updateControls();});
        updateCandidates(); updateControls();
    }
    private void updateCandidates() {
        if (catalogue == null || search == null) return;
        ItemStack input = menu.getSlot(0).getItem();
        candidates = catalogue.candidates(input.getItem(), menu.inputMode()==0 && !input.isEmpty(), search.getValue(), menu.variant);
        page = Math.clamp(page,0,Math.max(0,(candidates.size()-1)/20));
    }
    private void updateControls() {
        if(continuous!=null)continuous.setMessage(tr(menu.running()?"run.1":"run.0"));
        if (menu.variant == 0) return;
        for (int i=0;i<3;i++) modes[i].active=menu.inputMode()!=i;
        matching.setMessage(tr("match."+menu.matchMode()));
        matching.setTooltip(Tooltip.create(tr("match_help",menu.filterItem()==Items.AIR?tr("filter_auto"):menu.filterItem().getDefaultInstance().getHoverName())));
        search.visible=!showRange;
        previous.visible=next.visible=!showRange;
        previous.active=page>0; next.active=(page+1)*20<candidates.size();
        targetTab.active=showRange;
        if (rangeTab!=null) rangeTab.active=!showRange;
        if (showRange && search.isFocused()) {search.setFocused(false);setFocused(null);}
    }
    @Override protected void containerTick() { super.containerTick(); updateCandidates(); updateControls(); }
    private int accent() { return menu.variant==0 ? 0xffe0c873 : menu.variant==1 ? 0xffa481bc : 0xff4e9ea5; }
    private void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x,y,x+w,y+h,color);
        g.horizontalLine(x,x+w-1,y,0xffededee); g.verticalLine(x,y,y+h-1,0xffededee);
        g.horizontalLine(x,x+w-1,y+h-1,0xff34363e); g.verticalLine(x+w-1,y,y+h-1,0xff34363e);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        g.fill(0,0,width,height,0x88000000);
        bevel(g,leftPos,topPos,imageWidth,imageHeight,0xffc6c6c6);
        g.outline(leftPos+2,topPos+2,imageWidth-4,imageHeight-4,0xff3a3c46);
        for(int x:new int[]{leftPos+3,leftPos+imageWidth-9})
            for(int y:new int[]{topPos+3,topPos+imageHeight-9}) bevel(g,x,y,6,6,accent());
        g.fill(leftPos+8,topPos+19,leftPos+imageWidth-8,topPos+20,accent());
        if(menu.variant>0) g.fill(leftPos+186,topPos+24,leftPos+187,topPos+230,0xff96979e);
        for(var slot:menu.slots) {
            int x=leftPos+slot.x-1,y=topPos+slot.y-1;
            g.fill(x,y,x+18,y+18,0xff8b8b8b);
            g.horizontalLine(x,x+17,y,0xff373737); g.verticalLine(x,y,y+17,0xff373737);
            g.horizontalLine(x,x+17,y+17,0xffffffff); g.verticalLine(x+17,y,y+17,0xffffffff);
        }
        super.extractRenderState(g,mouseX,mouseY,partialTick);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        g.text(font,title,10,7,TEXT,false);
        g.text(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,TEXT,false);
        if(menu.variant==0) {
            g.text(font,tr("input"),22,29,TEXT,false); g.text(font,tr("gold"),75,29,TEXT,false);
            g.text(font,tr("output"),127,29,TEXT,false);
            g.text(font,"+",55,46,MUTED,false);g.text(font,">",111,46,MUTED,false);
            g.centeredText(font,tr("status."+menu.status()),88,90,MUTED);
            // No progress bar: this variant has just two inputs and one output.
            return;
        }
        g.text(font,tr("containers",menu.containerCount()),10,48,TEXT,false);
        if((menu.rangeFlags()&3)!=0) g.text(font,tr("partial"),122,48,0xff8b5700,false);
        if(menu.variant==2) {
            String[] labels={"input","fuel","catalyst","output","remainder"};
            for(int i=0;i<5;i++)g.text(font,tr(labels[i]),9+i*34,59,TEXT,false);
        } else {
            g.text(font,tr("input"),17,59,TEXT,false);g.text(font,tr("fuel"),73,59,TEXT,false);
            g.text(font,tr("output"),133,59,TEXT,false);g.text(font,">",112,74,MUTED,false);
        }
        g.text(font,tr("phase",menu.phase()),10,91,MUTED,false);
        if(menu.variant==2)g.text(font,tr("deaths",menu.deaths()),89,91,MUTED,false);
        g.fill(10,102,178,105,0xff8b8b8b);
        var settings=ClientRecipeCatalog.current().settings();
        int capacity=settings.isEmpty()?16:settings.getAsJsonObject("end").get("fuel_charge").getAsInt();
        g.fill(10,102,10+(int)(168*Math.min(1.0,menu.phase()/(double)capacity)),105,accent());
        g.text(font,tr("status."+menu.status()),10,129,MUTED,false);
        if(showRange) drawMap(g); else drawTargets(g,mouseX,mouseY);
    }
    private void drawMap(GuiGraphicsExtractor g) {
        final int x=201,y=61;
        int minX=16,maxX=16,minY=16,maxY=16;
        for(int row=0;row<33;row++)for(int col=0;col<33;col++)if(menu.pixel(col,row)>0) {
            minX=Math.min(minX,col);maxX=Math.max(maxX,col);minY=Math.min(minY,row);maxY=Math.max(maxY,row);
        }
        int span=Math.min(33,Math.max(7,Math.max(maxX-minX,maxY-minY)+3));
        int startX=Math.clamp((minX+maxX-span+1)/2,0,33-span),startY=Math.clamp((minY+maxY-span+1)/2,0,33-span);
        int cell=99/span,offset=(99-span*cell)/2;
        g.text(font,tr("projection"),194,49,TEXT,false);
        g.fill(x-1,y-1,x+100,y+100,0xff1e2932);
        for(int row=0;row<span;row++) for(int col=0;col<span;col++) {
            int state=menu.pixel(col+startX,row+startY);
            int color=state==2 ? 0xff85ddd7 : state==1 ? 0xff31848e : 0xff263840;
            if(col+startX==16&&row+startY==16)color=0xffffe8a3;
            g.fill(x+offset+col*cell,y+offset+row*cell,x+offset+(col+1)*cell-1,y+offset+(row+1)*cell-1,color);
        }
        g.text(font,"Y+",189,60,MUTED,false);g.text(font,"X+",298,161,MUTED,false);
        g.text(font,tr("nodes",menu.nodeCount()),194,176,TEXT,false);
        g.text(font,tr("ground",menu.groundCount()),194,189,TEXT,false);
        g.text(font,tr("map_legend"),194,204,MUTED,false);
        g.text(font,tr((menu.rangeFlags()&12)!=0 ? "range_partial" : "range_live"),194,219,
            (menu.rangeFlags()&12)!=0 ? 0xff8b5700 : MUTED,false);
    }
    private void drawTargets(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        for(int i=0;i<20;i++) {
            int at=page*20+i,x=200+(i%5)*21,y=71+(i/5)*20;
            g.fill(x-1,y-1,x+17,y+17,0xff9c9ca4);
            if(at>=candidates.size()) continue;
            Item item=candidates.get(at); ItemStack stack=new ItemStack(item);
            if(item==menu.previewTarget())g.outline(x-2,y-2,20,20,accent());
            g.item(stack,x,y);
            if(mouseX-leftPos>=x && mouseX-leftPos<x+18 && mouseY-topPos>=y && mouseY-topPos<y+18)
                g.setTooltipForNextFrame(font,stack,mouseX,mouseY);
        }
        Item selected=menu.previewTarget();
        if(selected!=Items.AIR) {
            g.item(new ItemStack(selected),196,181);
            g.text(font,font.plainSubstrByWidth(selected.getDefaultInstance().getHoverName().getString(),92),216,185,TEXT,false);
        } else g.text(font,tr("choose"),194,185,MUTED,false);
        g.text(font,tr("mode_hint."+menu.inputMode()),194,204,MUTED,false);
        g.text(font,(page+1)+"/"+Math.max(1,(candidates.size()+19)/20),194,219,MUTED,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(menu.variant>0 && !showRange && event.button()==0) {
            double x=event.x()-leftPos-200,y=event.y()-topPos-71;
            if(x>=0 && x<105 && y>=0 && y<80 && x%21<18 && y%20<18) {
                int at=page*20+(int)y/20*5+(int)x/21;
                if(at<candidates.size()) {send(1000+BuiltInRegistries.ITEM.getId(candidates.get(at)));return true;}
            }
        }
        return super.mouseClicked(event,doubleClick);
    }
}
