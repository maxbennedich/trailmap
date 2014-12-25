package com.max.route;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Multi-hiearchical quad tree node. Supports traversing the tree at various hierarchy levels
 * to be able to only find a subset of results, where level 0 means all results, level 1 means
 * roughly every second result, level 2 every fourth result, etc.
 * <br>
 * Each node contains four children (each of which may be null if it lacks content),
 * and the maximum hierarchy level of any result contained under the node, so that
 * traversing can stop when the desired hierarchy level is reached.
 */
public class QuadNode implements Serializable {
    private static final long serialVersionUID = 1L;

    final int x0, y0, x1, y1;
    int maxChildLevel;
    QuadNode[] q;
    int[] pointIdx;

    QuadNode(int x0, int y0, int x1, int y1) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        pointIdx = null;
    }

    public static final int level(int idx) {
        return Integer.numberOfTrailingZeros(idx);
    }

    public void queryTree(int level, int qx0, int qy0, int qx1, int qy1, List<QuadPoint> points, Collection<Integer> result) {
        if (pointIdx != null)
            for (int idx : pointIdx)
                if (level(idx) >= level && qx0 <= points.get(idx).x && qx1 >= points.get(idx).x && qy0 <= points.get(idx).y && qy1 >= points.get(idx).y)
                    result.add(idx);
        if (q != null)
            for (int k = 0; k < 4; ++k) {
                if (q[k] != null && q[k].maxChildLevel >= level && qx0 <= q[k].x1 && qx1 >= q[k].x0 && qy0 <= q[k].y1 && qy1 >= q[k].y0)
                    q[k].queryTree(level, qx0, qy0, qx1, qy1, points, result);
            }
    }
}