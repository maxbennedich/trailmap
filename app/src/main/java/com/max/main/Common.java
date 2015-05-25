package com.max.main;

import java.util.Arrays;

public class Common {
    public static int[] filledArray(int size, int value) {
        int[] a = new int[size];
        Arrays.fill(a, value);
        return a;
    }
}
