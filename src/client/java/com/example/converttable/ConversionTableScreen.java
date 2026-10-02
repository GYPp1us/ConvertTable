package com.example.converttable;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
    private final TargetButton[] targets = new TargetButton[20];
    private Button single, continuous;
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
        single=button(8,menu.variant==0?65:108,80,"convert",()->send(10));
        single.setTooltip(Tooltip.create(tr("convert_help")));
        continuous=button(94,menu.variant==0?65:108,menu.variant==0?74:84,"run.0",()->send(11));
        continuous.setTooltip(Tooltip.create(tr("run_help")));
        if (menu.variant == 0) { updateControls(); return; }
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
        for (int i = 0; i < targets.length; i++) {
            final int cell = i;
            targets[i] = addRenderableWidget(new TargetButton(leftPos + 200 + i % 5 * 21,
                topPos + 71 + i / 5 * 20, b -> {
                    int index = page * targets.length + cell;
                    if (index < candidates.size()) send(1000 + BuiltInRegistries.ITEM.getId(candidates.get(index)));
                }));
        }
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
        if(single!=null) {
            single.active=!menu.processing();
            single.setMessage(tr(menu.processing()?"working":"convert"));
        }
        if (menu.variant == 0) return;
        for (int i=0;i<3;i++) modes[i].active=menu.inputMode()!=i;
        matching.setMessage(tr("match."+menu.matchMode()));
        matching.setTooltip(Tooltip.create(tr("match_help",menu.filterItem()==Items.AIR?tr("filter_auto"):menu.filterItem().getDefaultInstance().getHoverName())));
        search.visible=!showRange;
        previous.visible=next.visible=!showRange;
        previous.active=page>0; next.active=(page+1)*20<candidates.size();
        targetTab.active=showRange;
        if (rangeTab!=null) rangeTab.active=!showRange;
        for (int i = 0; i < targets.length; i++) {
            int index = page * targets.length + i;
            targets[i].target(!showRange && index < candidates.size() ? candidates.get(index) : null);
        }
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
            if (!slot.isActive()) continue;
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
            g.text(font,tr("input"),22,32,TEXT,false); g.text(font,tr("gold"),75,32,TEXT,false);
            g.text(font,tr("output"),127,32,TEXT,false);
            g.text(font,"+",55,46,MUTED,false);g.text(font,">",111,46,MUTED,false);
            drawProgress(g,8,88,160,mouseX,mouseY);
            drawLinkCounts(g,10,21,mouseX,mouseY);
            return;
        }
        drawLinkCounts(g,10,48,mouseX,mouseY);
        if((menu.rangeFlags()&3)!=0) g.text(font,tr("partial"),122,48,0xff8b5700,false);
        if(menu.variant==2) {
            String[] labels={"input","catalyst","output","remainder"};
            for(int i=0;i<labels.length;i++)g.text(font,tr(labels[i]),13+i*42,59,TEXT,false);
        } else {
            g.text(font,tr("input"),17,59,TEXT,false);g.text(font,tr("fuel"),73,59,TEXT,false);
            g.text(font,tr("output"),133,59,TEXT,false);g.text(font,">",112,74,MUTED,false);
        }
        if(menu.variant==2) {
            Component souls=tr("souls",menu.souls());
            Component cost=menu.soulCost()>0?tr("soul_cost_short",menu.soulCost()):tr("soul_cost_pending");
            Component soulsLabel=font.width(souls)>92?tr("souls_short",menu.souls()):souls;
            g.text(font,font.plainSubstrByWidth(soulsLabel.getString(),92),10,93,MUTED,false);
            g.text(font,font.plainSubstrByWidth(cost.getString(),72),106,93,MUTED,false);
            if(GrowthScreenGraphics.contains(mouseX-leftPos,mouseY-topPos,10,91,168,12))
                g.setComponentTooltipForNextFrame(font,List.of(souls,cost,tr("souls_help")),mouseX,mouseY);
        } else {
            g.text(font,tr("phase",menu.phase()),10,91,MUTED,false);
            g.fill(10,102,178,105,0xff8b8b8b);
            var settings=ClientRecipeCatalog.current().settings();
            int capacity=settings.isEmpty()?16:settings.getAsJsonObject("end").get("fuel_charge").getAsInt();
            g.fill(10,102,10+(int)(168*Math.min(1.0,menu.phase()/(double)capacity)),105,accent());
        }
        drawProgress(g,10,128,168,mouseX,mouseY);
        if(showRange) drawMap(g); else drawTargets(g,mouseX,mouseY);
    }
    private void drawProgress(GuiGraphicsExtractor g,int x,int y,int width,int mouseX,int mouseY) {
        int color=menu.status()==6?0xffd6a565:menu.variant==0?0xffe0c873:menu.variant==1?0xffc6aadd:0xff8bced3;
        GrowthScreenGraphics.progress(g,x,y,width,11,menu.progressTicks(),menu.totalTicks(),color);
        Component state=menu.processing()&&menu.status()==12
            ?tr("progress_short",GrowthScreenGraphics.percent(menu.progressTicks(),menu.totalTicks()),
                seconds(Math.max(0,menu.totalTicks()-menu.progressTicks())))
            :tr("status."+menu.status());
        g.centeredText(font,font.plainSubstrByWidth(state.getString(),width-4),x+width/2,y+1,TEXT);
        if(GrowthScreenGraphics.contains(mouseX-leftPos,mouseY-topPos,x,y,width,12))
            g.setComponentTooltipForNextFrame(font,List.of(tr("status."+menu.status()),
                tr("work_duration",seconds(menu.totalTicks())),
                tr("work_progress",seconds(menu.progressTicks()),seconds(menu.totalTicks()))),mouseX,mouseY);
    }
    private static String seconds(int ticks) { return String.format(java.util.Locale.ROOT,"%.1f",ticks/20.0); }
    private void drawLinkCounts(GuiGraphicsExtractor g,int x,int y,int mouseX,int mouseY) {
        g.text(font,tr("links_input",menu.inputContainerCount()),x,y,0xff28744d,false);
        g.text(font,tr("links_output",menu.outputContainerCount()),x+60,y,0xff956027,false);
        if(mouseX-leftPos>=x&&mouseX-leftPos<x+100&&mouseY-topPos>=y&&mouseY-topPos<y+10)
            g.setComponentTooltipForNextFrame(font,List.of(tr("links_input",menu.inputContainerCount()),
                tr("links_output",menu.outputContainerCount())),mouseX,mouseY);
    }
    private void drawMap(GuiGraphicsExtractor g) {
        final int x=201,y=65;
        int minX=16,maxX=16,minY=16,maxY=16;
        for(int row=0;row<33;row++)for(int col=0;col<33;col++)if(menu.pixel(col,row)>0) {
            minX=Math.min(minX,col);maxX=Math.max(maxX,col);minY=Math.min(minY,row);maxY=Math.max(maxY,row);
        }
        int span=Math.min(33,Math.max(7,Math.max(maxX-minX,maxY-minY)+3));
        int startX=Math.clamp((minX+maxX-span+1)/2,0,33-span),startY=Math.clamp((minY+maxY-span+1)/2,0,33-span);
        int cell=99/span,offset=(99-span*cell)/2;
        g.text(font,tr("projection"),194,45,TEXT,false);
        g.fill(x-1,y-1,x+100,y+100,0xff1e2932);
        for(int row=0;row<span;row++) for(int col=0;col<span;col++) {
            int state=menu.pixel(col+startX,row+startY);
            int color=state==2 ? 0xff85ddd7 : state==1 ? 0xff31848e : 0xff263840;
            if(col+startX==16&&row+startY==16)color=0xffffe8a3;
            g.fill(x+offset+col*cell,y+offset+row*cell,x+offset+(col+1)*cell-1,y+offset+(row+1)*cell-1,color);
        }
        g.centeredText(font,"Z-",250,55,MUTED);
        g.centeredText(font,"Z+",250,165,MUTED);
        g.text(font,"X-",187,109,MUTED,false);g.text(font,"X+",302,109,MUTED,false);
        g.text(font,tr("nodes",menu.nodeCount()),194,176,TEXT,false);
        g.text(font,tr("ground",menu.groundCount()),194,189,TEXT,false);
        g.text(font,tr("map_legend"),194,204,MUTED,false);
        g.text(font,tr((menu.rangeFlags()&12)!=0 ? "range_partial" : "range_live"),194,219,
            (menu.rangeFlags()&12)!=0 ? 0xff8b5700 : MUTED,false);
    }
    private void drawTargets(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        Item selected=menu.previewTarget();
        if(selected!=Items.AIR) {
            g.item(new ItemStack(selected),196,181);
            g.text(font,font.plainSubstrByWidth(selected.getDefaultInstance().getHoverName().getString(),92),216,185,TEXT,false);
        } else g.text(font,tr("choose"),194,185,MUTED,false);
        g.text(font,tr("mode_hint."+menu.inputMode()),194,204,MUTED,false);
        g.text(font,(page+1)+"/"+Math.max(1,(candidates.size()+19)/20),194,219,MUTED,false);
    }
    /** Vanilla widgets follow the current mouse-button mapping and support keyboard selection. */
    private final class TargetButton extends Button {
        private ItemStack item = ItemStack.EMPTY;

        TargetButton(int x, int y, OnPress action) {
            super(x, y, 18, 18, Component.empty(), action, DEFAULT_NARRATION);
        }

        void target(Item target) {
            visible = active = target != null;
            item = target == null ? ItemStack.EMPTY : target.getDefaultInstance();
            setMessage(item.isEmpty() ? Component.empty() : item.getHoverName());
            setTooltip(item.isEmpty() ? null : Tooltip.create(item.getHoverName()));
            if (!visible && isFocused()) setFocused(false);
        }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY();
            g.fill(x - 1, y - 1, x + 17, y + 17, isHoveredOrFocused() ? 0xffded4e7 : 0xff9c9ca4);
            if (item.is(menu.previewTarget())) g.outline(x - 2, y - 2, 20, 20, accent());
            if (isFocused()) g.outline(x - 2, y - 2, 20, 20, 0xfffaf6ff);
            g.item(item, x, y);
        }
    }
}
