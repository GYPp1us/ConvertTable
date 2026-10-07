package com.example.converttable;

import net.minecraft.world.inventory.ContainerData;

/** Four unsigned 16-bit menu properties preserve every bit of a long on the wire. */
public final class GrowthData {
    public static final int SIZE = 4;
    private GrowthData() { }

    public static void set(ContainerData data, int offset, long value) {
        for (int part = 0; part < SIZE; part++) data.set(offset + part, (int) (value >>> (part * 16)) & 0xffff);
    }

    public static long get(ContainerData data, int offset) {
        long value = 0;
        for (int part = 0; part < SIZE; part++) value |= (long) (data.get(offset + part) & 0xffff) << (part * 16);
        return value;
    }
}
