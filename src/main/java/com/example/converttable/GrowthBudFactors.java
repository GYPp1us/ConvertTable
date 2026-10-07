package com.example.converttable;

/** Crystal quantities in microfactors: divide by GrowthUnits.DIVISOR for displayed factors. */
public final class GrowthBudFactors {
    private GrowthBudFactors() { }
    public static boolean immature(int stage) { return stage>=1 && stage<=3; }
    public static long natural(int stage) {
        return switch(stage) { case 1->16;case 2->8;case 3->4;case 4->1;default->0; };
    }
    public static long contained(int stage,boolean basalt) {
        return natural(stage)+(immature(stage)&&basalt?1:0);
    }
    public static long extractionLimit(int stage,boolean calcite) { return immature(stage)?(calcite?2:1):0; }
}
