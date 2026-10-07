package com.example.converttable;

/** Integer water filling: work depends on recipient count, never on the numeric budget. */
final class GrowthAllocation {
    private GrowthAllocation() { }

    record Result(long[] amounts, int nextIndex, long spent) { }

    static Result divide(long budget, long[] demand, int startIndex) {
        long[] amounts = new long[demand.length];
        if (demand.length == 0) return new Result(amounts, 0, 0);
        int cursor = Math.floorMod(startIndex, demand.length);
        budget = Math.max(0, budget);
        if (budget == 0) return new Result(amounts, cursor, 0);
        long low = 0, high = 0;
        for (long value : demand) high = Math.max(high, value);
        while (low < high) {
            long difference = high - low;
            long middle = low + difference / 2 + difference % 2;
            if (fits(middle, budget, demand)) low = middle;
            else high = middle - 1;
        }
        long spent = 0;
        for (int i = 0; i < demand.length; i++) {
            amounts[i] = Math.min(Math.max(0, demand[i]), low);
            spent += amounts[i]; // fits() proves that this cannot exceed the long budget.
        }
        long remaining = budget - spent;
        int next = cursor;
        boolean partialRound = false;
        for (int offset = 0; offset < demand.length; offset++) {
            int i = (cursor + offset) % demand.length;
            if (remaining > 0 && amounts[i] < demand[i]) {
                amounts[i]++;
                remaining--;
                spent++;
                next = (i + 1) % demand.length;
                partialRound = true;
            }
        }
        if (spent > 0 && !partialRound) {
            // A complete round ends after its last still-active recipient in cursor order.
            for (int offset = 0; offset < demand.length; offset++) {
                int i = (cursor + offset) % demand.length;
                if (demand[i] >= low && low > 0) next = (i + 1) % demand.length;
            }
        }
        return new Result(amounts, next, spent);
    }

    private static boolean fits(long water, long budget, long[] demand) {
        long remaining = budget;
        for (long value : demand) {
            long amount = Math.min(Math.max(0, value), water);
            if (amount > remaining) return false;
            remaining -= amount;
        }
        return true;
    }
}
