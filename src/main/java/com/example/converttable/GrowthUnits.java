package com.example.converttable;

import java.math.BigDecimal;

/** A microfactor is 1/1000 factor; storage also preserves released 1/64 fractions exactly. */
public final class GrowthUnits {
    public static final int DIVISOR = 1000;
    // LCM(64, 1000) preserves both released fractions and the new income exactly.
    public static final int STORAGE_DIVISOR = 8000;
    public static final int UNITS_PER_MICRO_TICK = STORAGE_DIVISOR / DIVISOR;
    public static final int TICK_UNITS = STORAGE_DIVISOR * 20;
    private GrowthUnits() { }

    public static String rate(long microfactors) { return format(microfactors, DIVISOR); }
    public static String factors(long tickUnits) { return format(tickUnits, TICK_UNITS); }

    /** Preserve already earned fractions from the whole-factor and 1/64 saves. */
    public static int restoreFraction(int saved, int scale) {
        int sourceScale = scale == 1 || scale == 64 || scale == DIVISOR || scale == STORAGE_DIVISOR ? scale : 1;
        long oldUnits = (long) sourceScale * 20;
        long bounded = Math.clamp((long) saved, 0, oldUnits - 1);
        return (int) Math.min(TICK_UNITS - 1, bounded * STORAGE_DIVISOR / sourceScale);
    }

    private static String format(long value, int divisor) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor))
            .stripTrailingZeros().toPlainString();
    }
}
