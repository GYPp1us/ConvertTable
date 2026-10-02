package com.example.converttable;

/** Divides growth factors, rather than output items, up to each recipient's remaining demand. */
final class GrowthAllocation {
    private GrowthAllocation() { }

    record Result(int[] amounts, int nextIndex, int spent) { }

    static Result divide(int budget, int[] demand, int startIndex) {
        int[] amounts = new int[demand.length];
        if (demand.length == 0) return new Result(amounts, 0, 0);
        int cursor = Math.floorMod(startIndex, demand.length);
        int remaining = Math.max(0, budget), spent = 0;
        while (remaining > 0) {
            int recipient = -1;
            for (int offset = 0; offset < demand.length; offset++) {
                int index = (cursor + offset) % demand.length;
                if (amounts[index] < demand[index]) { recipient = index; break; }
            }
            if (recipient < 0) break;
            amounts[recipient]++;
            remaining--;
            spent++;
            cursor = (recipient + 1) % demand.length;
        }
        return new Result(amounts, cursor, spent);
    }
}
