package com.example.converttable;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Bounded player-facing quantities; production and network values keep their original precision. */
final class GrowthNumbers {
    private static final BigDecimal THOUSAND=BigDecimal.valueOf(1000);
    private static final String[] SUFFIXES={"","k","M","G","T","P","E"};
    private GrowthNumbers() { }
    static String rate(long value) { return decimal(BigDecimal.valueOf(value).divide(BigDecimal.valueOf(GrowthUnits.DIVISOR))); }
    static String factors(long value) { return decimal(BigDecimal.valueOf(value).divide(BigDecimal.valueOf(GrowthUnits.TICK_UNITS)),false); }
    static String production(long rate,int cost) {
        return cost<=0?"0.000":decimal(BigDecimal.valueOf(rate).divide(
            BigDecimal.valueOf((long)GrowthUnits.DIVISOR*cost),12,RoundingMode.HALF_UP));
    }
    static String count(long value) {
        if(value<1000) return Long.toString(value);
        var scaled=BigDecimal.valueOf(value);
        int unit=0;
        while(scaled.compareTo(THOUSAND)>=0 && unit<SUFFIXES.length-1) { scaled=scaled.divide(THOUSAND);unit++; }
        int places=scaled.compareTo(BigDecimal.TEN)<0?1:0;
        var rounded=scaled.setScale(places,RoundingMode.HALF_UP);
        if(rounded.compareTo(THOUSAND)>=0 && unit<SUFFIXES.length-1) { rounded=rounded.divide(THOUSAND).setScale(1,RoundingMode.HALF_UP);unit++; }
        return rounded.toPlainString()+SUFFIXES[unit];
    }
    private static String decimal(BigDecimal value) {
        return decimal(value,true);
    }
    private static String decimal(BigDecimal value,boolean tinyHint) {
        if(tinyHint && value.signum()>0 && value.compareTo(new BigDecimal("0.001"))<0) return "<0.001";
        int unit=0;
        while(value.compareTo(THOUSAND)>=0 && unit<SUFFIXES.length-1) { value=value.divide(THOUSAND);unit++; }
        var rounded=value.setScale(3,RoundingMode.HALF_UP);
        if(rounded.compareTo(THOUSAND)>=0 && unit<SUFFIXES.length-1) { rounded=rounded.divide(THOUSAND).setScale(3,RoundingMode.HALF_UP);unit++; }
        return rounded.toPlainString()+SUFFIXES[unit];
    }
}
