package com.max.route;

public class SegmentDistances {
    /** {node to, node from, distance in meters} */
    public static final int[][] SEGMENT_DISTANCES_lidingo = {
            {0,1,6943},
            {1,2,4516},
            {2,3,1587},
            {3,4,2699},
            {4,5,6581},
            {5,6,4856},
            {6,0,8861},
    };

    /** Sörmlandsleden, main trail only {node to, node from, distance in meters} */
    public static final int[][] SEGMENT_DISTANCES_SORMLANDSLEDEN = {
            {0, 1, 8544},
            {1, 2, 6348},
            {2, 3, 12886},
            {3, 4, 9693},
            {4, 5, 11267},
            {5, 6, 12085},
            {6, 7, 6613},
            {7, 8, 12242},
            {8, 9, 8443},
            {9, 10, 10923},
            {10, 11, 8779},
            {11, 12, 8251},
            {12, 13, 14886},
            {13, 14, 5764},
            {14, 15, 10245},
            {15, 16, 9640},
            {16, 17, 5970},
            {17, 18, 10442},
            {18, 19, 11377},
            {19, 20, 6107},
            {20, 21, 10148},
            {21, 22, 16032},
            {22, 23, 11688},
            {23, 24, 16318},
            {24, 25, 8948},
            {25, 26, 7979},
            {26, 27, 14156},
            {27, 28, 9804},
            {28, 29, 8754},
            {29, 30, 11072},
            {30, 31, 10532},
            {31, 32, 11234},
            {32, 33, 7908},
            {33, 34, 12650},
            {34, 35, 12321},
            {35, 36, 9832},
            {36, 37, 5786},
            {37, 38, 5954},
            {38, 39, 5802},
            {39, 40, 3388},
            {40, 41, 5287},
            {41, 42, 6522},
            {42, 43, 4103},
            {43, 44, 11474},
            {44, 45, 8225},
            {45, 46, 7793},
            {46, 47, 5435},
            {47, 48, 7903},
            {48, 49, 11058},
            {49, 50, 5742},
            {50, 51, 3490},
            {51, 52, 6889},
            {52, 53, 10419},
            {53, 54, 8283},
            {54, 55, 17141},
            {55, 56, 19855},
            {56, 57, 11698},
            {57, 58, 9976},
            {58, 59, 9343},
            {59, 60, 7398},
            {60, 61, 5439},
            {61, 62, 6288},
    };

    /** ONE WAY GOTLAND {node to, node from, distance in meters} */
    public static final int[][] SEGMENT_DISTANCES_GOTLAND = {
            {76, 82, 5327},
            {82, 39, 6271},
            {39, 90, 7176},
            {90, 23, 4445},
            {23, 64, 6786},
            {64, 34, 7364},
            {34, 41, 4317},
            {41, 16, 3683},
            {16, 67, 6282},
            {67, 12, 7575},
            {12, 63, 7621},
            {63, 52, 4350},
            {52, 2, 7058},
            {2, 31, 3750},
            {31, 21, 6529},
            {21, 58, 5011},
            {58, 75, 4481},
            {75, 54, 6095},
            {54, 55, 4034},
            {55, 32, 7056},
            {32, 22, 4746},
            {22, 46, 4460},
            {46, 3, 2976},
            {3, 36, 7733},
            {36, 70, 4798},
            {70, 72, 5600},
            {72, 53, 5101},
            {53, 18, 4313},
            {18, 27, 6267},
            {27, 49, 5919},
            {49, 69, 7072},
            {69, 87, 4982},
            {87, 20, 7899},
            {20, 78, 6284},
            {78, 88, 9490},
            {88, 73, 4819},
            {73, 79, 3581},
            {79, 26, 5840},
            {26, 0, 4509},
            {0, 80, 5222},
            {80, 47, 3440},
            {47, 61, 6643},
            {61, 89, 5204},
            {89, 42, 4819},
            {42, 35, 6778},
            {35, 85, 6634},
            {85, 13, 6123},
            {13, 1, 7992},
            {1, 5, 7686},
            {5, 29, 8458},
            {29, 91, 4721},
            {91, 50, 9949},
            {50, 4, 4375},
            {4, 62, 5116},
            {62, 30, 5823},
            {30, 48, 5559},
            {48, 15, 4253},
            {15, 38, 6228},
            {38, 71, 4028},
            {71, 83, 5669},
            {83, 6, 7045},
            {6, 8, 4471},
            {8, 66, 2716},
            {66, 7, 4557},
            {7, 17, 5299},
            {17, 19, 3842},
            {19, 43, 4327},
            {43, 84, 9818},
            {84, 86, 10319},
            {86, 10, 4874},
            {10, 25, 5636},
            {25, 56, 4600},
            {56, 44, 6041},
            {44, 14, 4766},
            {14, 51, 6379},
            {51, 81, 4280},
            {81, 33, 7940},
            {33, 9, 13410},
            {9, 65, 7128},
            {65, 77, 8138},
            {77, 60, 8409},
            {60, 57, 3552},
            {57, 74, 7141},
            {74, 37, 18117},
            {37, 40, 6907},
            {40, 59, 9264},
            {59, 45, 7336},
            {45, 68, 8097},
            {68, 24, 5503},
            {24, 11, 10756},
            {11, 28, 10927},
    };

    /** {node to, node from, distance in meters} */
    public static final int[][] SEGMENT_DISTANCES_return = {
            {84, 88, 8399},
            {88, 73, 4819},
            {73, 79, 3581},
            {79, 26, 5840},
            {26, 0, 4509},
            {0, 80, 5222},
            {80, 47, 3440},
            {47, 20, 7162},
            {20, 78, 6284},
            {78, 87, 10649},
            {87, 69, 4982},
            {69, 49, 7072},
            {49, 27, 5919},
            {27, 18, 6267},
            {18, 53, 4313},
            {53, 72, 5101},
            {72, 70, 5600},
            {70, 36, 4798},
            {36, 41, 5411},
            {41, 64, 7686},
            {64, 82, 19528},
            {82, 76, 5327},
            {76, 39, 10424},
            {39, 90, 7176},
            {90, 23, 4445},
            {23, 34, 7725},
            {34, 16, 5159},
            {16, 67, 6282},
            {67, 3, 7787},
            {3, 46, 2976},
            {46, 22, 4460},
            {22, 32, 4746},
            {32, 55, 7056},
            {55, 54, 4034},
            {54, 75, 6095},
            {75, 12, 5760},
            {12, 63, 7621},
            {63, 52, 4350},
            {52, 2, 7058},
            {2, 31, 3750},
            {31, 58, 4354},
            {58, 21, 5011},
            {21, 13, 8370},
            {13, 1, 7992},
            {1, 5, 7686},
            {5, 29, 8458},
            {29, 91, 4721},
            {91, 50, 9949},
            {50, 4, 4375},
            {4, 62, 5116},
            {62, 30, 5823},
            {30, 48, 5559},
            {48, 15, 4253},
            {15, 38, 6228},
            {38, 71, 4028},
            {71, 85, 5720},
            {85, 83, 5461},
            {83, 35, 5647},
            {35, 42, 6778},
            {42, 89, 4819},
            {89, 61, 5204},
            {61, 6, 7406},
            {6, 8, 4471},
            {8, 66, 2716},
            {66, 7, 4557},
            {7, 17, 5299},
            {17, 19, 3842},
            {19, 43, 4327},
            {43, 10, 5825},
            {10, 25, 5636},
            {25, 56, 4600},
            {56, 77, 8629},
            {77, 44, 7330},
            {44, 14, 4766},
            {14, 51, 6379},
            {51, 81, 4280},
            {81, 33, 7940},
            {33, 9, 13410},
            {9, 65, 7128},
            {65, 59, 7013},
            {59, 45, 7336},
            {45, 68, 8097},
            {68, 28, 17739},
            {28, 11, 10927},
            {11, 24, 10756},
            {24, 37, 19661},
            {37, 40, 6907},
            {40, 74, 11887},
            {74, 57, 7141},
            {57, 60, 3552},
            {60, 86, 8636},
            {86, 84, 10319},
    };

    public static final int[][] SEGMENT_DISTANCES = SEGMENT_DISTANCES_SORMLANDSLEDEN;

    public static final int[] SEGMENT_CUMULATIVE_DISTANCES = new int[SEGMENT_DISTANCES.length + 1];

    static {
        for (int k = 0; k < SEGMENT_CUMULATIVE_DISTANCES.length; ++k)
            SEGMENT_CUMULATIVE_DISTANCES[k] = k == 0 ? 0 : SEGMENT_CUMULATIVE_DISTANCES[k-1] + SEGMENT_DISTANCES[k-1][2];
    }
}
