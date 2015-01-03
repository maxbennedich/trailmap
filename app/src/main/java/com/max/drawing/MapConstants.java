package com.max.drawing;

public class MapConstants {
    // extreme points from lantmateriet zoom level 0 (tile size 2^20; partial tiles for row/col 3)
    // these are used as global extreme points, e.g. for the GPS
    public static final int UTM_X0 = -1_200_000;
    public static final int UTM_Y0 = 4_700_000;
    public static final int UTM_X1 = 2_600_000;
    public static final int UTM_Y1 = 8_500_000;
}
