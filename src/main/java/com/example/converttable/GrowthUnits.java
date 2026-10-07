package com.example.converttable;

import java.math.BigDecimal;

/** A microfactor is 1/64 factor; twenty tick units earn one microfactor. */
public final class GrowthUnits {
    public static final int DIVISOR = 64;
    public static final int TICK_UNITS = DIVISOR * 20;
    private GrowthUnits() { }

    public static String rate(long microfactors) { return format(microfactors, DIVISOR); }
    public static String factors(long tickUnits) { return format(tickUnits, TICK_UNITS); }

    private static String format(long value, int divisor) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor))
            .stripTrailingZeros().toPlainString();
    }
}
