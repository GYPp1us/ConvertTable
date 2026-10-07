package com.example.converttable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-authoritative storage, execution controls, charge and projected network. */
public final class ConversionTableMenu extends AbstractContainerMenu {
    public static final int HEADER = 19, WORDS = (33 * 33 + 7) / 8, DATA_SIZE = HEADER + WORDS;
    public final int variant;
    private final Container storage;
    private final ConversionTableBlockEntity table;
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);
    public ConversionTableMenu(int id, Inventory inventory, int variant) { this(id, inventory, variant, new SimpleContainer(5), null); }
    public ConversionTableMenu(int id, Inventory inventory, ConversionTableBlockEntity table) {
        this(id, inventory, table.variantIndex(), table, table);
    }
    private ConversionTableMenu(int id, Inventory inventory, int variant, Container storage, ConversionTableBlockEntity table) {
        super(ConversionMenus.type(variant), id);
        this.variant = variant; this.storage = storage; this.table = table;
        int y = variant == 0 ? 42 : 70;
        int[] xs = variant == 0 ? new int[]{26,80,134} : variant==2?new int[]{16,46,100}:new int[]{20,78,138};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            addSlot(new Slot(storage, i, xs[i], y) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return index != 2 && (index != 1 || variant < 2 && stack.is(fuelItem()));
                }
                @Override public boolean isActive() { return variant != 2 || index != 1; }
            });
        }
        if(variant==2)for(int index:new int[]{3,4}) {
            addSlot(new Slot(storage,index,index==3?58:142,70) {
                @Override public boolean mayPlace(ItemStack stack) {return index==3;}
            });
        }
        addStandardInventorySlots(inventory, variant == 0 ? 8 : 16, variant == 0 ? 115 : 153);
        addDataSlots(data);
        refresh();
    }
    private void refresh() {
        if (table == null) return;
        data.set(12,table.filterSource==null?0:BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.getValue(table.filterSource)));
        data.set(8,table.phase);data.set(9,table.deaths);data.set(10,table.running?1:0);data.set(11,table.status);
        data.set(15,table.progressTicks());data.set(16,table.totalTicks());
        data.set(17,table.processing()?1:0);data.set(18,table.soulCost());
        data.set(7, BuiltInRegistries.ITEM.getId(RecipeConfig.fuelItem(variant)));
        data.set(0, table.inputMode); data.set(1, table.matchMode);
        data.set(2, BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.getValue(table.target)));
        var range = table.rangeSnapshot();
        int inputs = table.inputLinks().size(), outputs = table.outputLinks().size();
        data.set(3, inputs + outputs); data.set(13, inputs); data.set(14, outputs);
        data.set(4, range.nodes()); data.set(5, range.ground()); data.set(6, range.flags());
        for (int word = 0; word < WORDS; word++) {
            int packed = 0;
            for (int j = 0; j < 8; j++) {
                int at = word * 8 + j;
                if (at < range.pixels().length) packed |= range.pixels()[at] << (j * 2);
            }
            // Container properties travel as signed shorts; both halves preserve all sixteen bits.
            data.set(HEADER + word, (short) packed);
        }
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    public Item fuelItem() { return table!=null?RecipeConfig.fuelItem(variant):BuiltInRegistries.ITEM.byId(data.get(7)&0xffff); }
    public Item filterItem(){return inputMode()==1?storage.getItem(0).getItem():BuiltInRegistries.ITEM.byId(data.get(12)&0xffff);}
    public int phase(){return data.get(8);}
    public int deaths(){return data.get(9);}
    public int souls(){return deaths();}
    public int progressTicks(){return data.get(15);}
    public int totalTicks(){return data.get(16);}
    public boolean processing(){return data.get(17)!=0;}
    public int soulCost(){return data.get(18);}
    public boolean running(){return data.get(10)!=0;}
    public int status(){return data.get(11);}
    public int deviceSlots(){return variant==2?5:3;}
    public int inputMode() { return data.get(0); }
    public int matchMode() { return data.get(1); }
    public Item previewTarget() { return BuiltInRegistries.ITEM.byId(data.get(2) & 0xffff); }
    public int containerCount() { return data.get(3); }
    public int inputContainerCount() { return data.get(13); }
    public int outputContainerCount() { return data.get(14); }
    public int nodeCount() { return data.get(4); }
    public int groundCount() { return data.get(5); }
    public int rangeFlags() { return data.get(6); }
    public int pixel(int x, int y) {
        int at = y * 33 + x;
        return (data.get(HEADER + at / 8) >>> ((at % 8) * 2)) & 3;
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (table == null || !stillValid(player)) return false;
        if(id==10)table.convert(false, player);
        else if(id==11)table.setRunning(!table.running, player);
        else if(variant==0)return false;
        else if (id >= 0 && id <= 2) {table.cancelProcessing();table.inputMode = id;table.filterSource=null;}
        else if (id == 3) {table.cancelProcessing();table.matchMode = 1 - table.matchMode;table.filterSource=null;}
        else if (id >= 1000 && id < 1000 + BuiltInRegistries.ITEM.size()) {
            Item item = BuiltInRegistries.ITEM.byId(id - 1000);
            if (!canSelectTarget(item)) return false;
            table.cancelProcessing();table.target = BuiltInRegistries.ITEM.getKey(item);table.filterSource=null;
        } else return false;
        table.setChanged(); refresh(); broadcastChanges(); return true;
    }
    private boolean canSelectTarget(Item target) {
        if (target == Items.AIR) return false;
        ItemStack input = storage.getItem(0);
        boolean restrict = table.inputMode == 0 && !input.isEmpty();
        var catalog = RecipeConfig.server();
        boolean ordinary = catalog.groups().stream().anyMatch(group -> group.tier() <= variant
            && group.items().contains(target)
            && (!restrict || !input.is(target) && group.items().contains(input.getItem())));
        return ordinary || variant == 2 && catalog.advanced().stream().anyMatch(recipe -> recipe.matchesTarget(target)
            && (!restrict || recipe.input().is(input.getItem())));
    }
    @Override public boolean stillValid(Player player) { return table == null || (!table.isRemoved() && storage.stillValid(player)); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < deviceSlots()) {
            if (!moveItemStackTo(stack, deviceSlots(), slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean catalyst=variant==2&&RecipeConfig.server().advanced().stream().anyMatch(r->!r.catalyst().isEmpty()&&stack.is(r.catalyst().getItem())
                &&r.matchesTarget(previewTarget())&&storage.getItem(0).is(r.input().getItem()));
            int input = variant < 2 && stack.is(fuelItem()) ? 1 : catalyst?3:0;
            if (!moveItemStackTo(stack, input, input + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
