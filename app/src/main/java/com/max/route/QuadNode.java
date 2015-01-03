package com.max.route;

import com.max.drawing.Renderer;

import java.io.Serializable;
import java.util.List;

/**
 * Multi-hierarchical quad tree node. Supports traversing the tree at various hierarchy levels
 * to be able to only find a subset of results, where level 0 means all results, level 1 means
 * roughly every second result, level 2 every fourth result, etc.
 * <br>
 * Each node contains four children (each of which may be null if it lacks content),
 * and the maximum hierarchy level of any result contained under the node, so that
 * traversing can stop when the desired hierarchy level is reached.
 * <br>
 * Each node contains a bucket of points rather than a single point. This is done in order
 * to reduce memory usage and speed up querying (less recursion and better memory locality). 
 */
public class QuadNode implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Bucket size. Smaller bucket sizes mean more memory used and potentially less time spent building the tree
     * (less iterating during node splitting). Larger bucket sizes mean less memory used and potentially less time
     * spent querying the tree (less recursion, better memory locality).
     */
    private static final int CAPACITY = 16;

    /** Extreme points for this node. */
    public final int x0, y0, x1, y1;

    /** Maximum level of any point that is contained in this node. */
    private int maxChildLevel = -1;

    /** Children of this node. */
    private QuadNode[] q;

    /** Points in this bucket. */
    private int[] pointIdx = new int[CAPACITY];

    /**
     * Level for each point. Although this can easily be calculated form the point index, it is slightly more
     * efficient to keep it stored.
     */
    private int[] level = new int[CAPACITY];

    /** Number of points in the bucket. */
    private int pointCount = 0;

    /** Bucket index of point with minimum level (or, in case of ties, maximum point index). */
    private int minIdx = 0;

    public QuadNode(int x0, int y0, int x1, int y1) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
    }

    public static final int level(int idx) {
        return Integer.numberOfTrailingZeros(idx);
    }

    /** Insert a new leaf by traversing the tree and splitting the final node if necessary. */
    public void insertPoint(int newPointIdx, List<QuadPoint> points) {
        int newLevel = level(newPointIdx);

        if (pointCount < QuadNode.CAPACITY) {
            // point fits in existing node; add it
            level[pointCount] = newLevel;
            pointIdx[pointCount] = newPointIdx;
            maxChildLevel = Math.max(maxChildLevel, newLevel);

            // see if new smallest level found
            if (pointCount > 0 && (newLevel < level[minIdx] || (newLevel == level[minIdx] && newPointIdx > pointIdx[minIdx])))
                minIdx = pointCount;

            ++pointCount;
        } else {
            // point doesn't fit in node; choose the lowest level point (and in case of ties,
            // the one with highest index to preserve locality) and move it to a child (this to
            // ensure higher level points are closer to the root, to speed up queries)
            int minLevel = level[minIdx];

            if (minLevel < newLevel || (newLevel == minLevel && pointIdx[minIdx] > newPointIdx)) {
                // a point with lower level/higher index was found -- swap it so that it gets pushed down the tree
                int tmp = pointIdx[minIdx];
                level[minIdx] = newLevel;
                pointIdx[minIdx] = newPointIdx;
                newPointIdx = tmp;
                maxChildLevel = Math.max(maxChildLevel, newLevel);

                // calculate new min since we're getting rid of the current one
                minLevel = Integer.SIZE + 1;
                for (int k = 0; k < QuadNode.CAPACITY; ++k) {
                    int lev = level[k];
                    if (lev < minLevel || (lev == minLevel && pointIdx[k] > pointIdx[minIdx])) {
                        minLevel = lev;
                        minIdx = k;
                    }
                }
            }

            int xm = (x0+x1)/2, ym = (y0+y1)/2;
            int k = (points.get(newPointIdx).x <= xm ? 0 : 1) + (points.get(newPointIdx).y <= ym ? 0 : 2);

            // split node if it's not already split; create child node if it doesn't already exist
            if (q == null)
                q = new QuadNode[4];
            if (q[k] == null)
                q[k] = new QuadNode((k&1)==0 ? x0 : xm+1, k>>1==0 ? y0 : ym+1, (k&1)==0 ? xm : x1, k>>1==0 ? ym : y1);

            q[k].insertPoint(newPointIdx, points);
        }
    }

    public void queryTree(int minLevel, int qx0, int qy0, int qx1, int qy1, List<QuadPoint> points, Renderer.QuadMatches matches) {
        for (int p = 0; p < pointCount; ++p) {
            int idx = pointIdx[p];
            QuadPoint qp = points.get(idx);
            if (level[p] >= minLevel && qx0 <= qp.x && qx1 >= qp.x && qy0 <= qp.y && qy1 >= qp.y)
                matches.add(idx);
        }
        if (q != null)
            for (int k = 0; k < 4; ++k)
                if (q[k] != null && q[k].maxChildLevel >= minLevel && qx0 <= q[k].x1 && qx1 >= q[k].x0 && qy0 <= q[k].y1 && qy1 >= q[k].y0)
                    q[k].queryTree(minLevel, qx0, qy0, qx1, qy1, points, matches);
    }

    /** This method does not support hierarchies; it searches ALL points. */
    public QuadNode getNearestNeighbor(int qx, int qy, List<QuadPoint> points) {
        bestNode = null;
        bestDist = 1L<<62;
        getNearestNeighborRecursive(qx, qy, points);
        return bestNode;
    }

    // note: the below constants make this class not thread safe
    private static QuadNode bestNode;
    private static long bestDist;
    private static final long[] xd = {0, 0, 0};
    private static final long[] yd = {0, 0, 0};

    private void getNearestNeighborRecursive(int qx, int qy, List<QuadPoint> points) {
        // test point for proximity
        if (pointIdx != null) {
            QuadPoint qp = points.get(pointIdx[0]); // TODO broken -- must check ALL coordinates
            long dist = (long)(qp.x-qx)*(qp.x-qx) + (long)(qp.y-qy)*(qp.y-qy);
            if (dist < bestDist) {
                bestDist = dist;
                bestNode = this;
            }
        }

        if (q != null) {
            // visit most likely children first, so to quickly find a good best estimate and reduce overall visit count
            int xOrder = qx <= (x0+x1)>>1 ? 0 : 1;
            int yOrder = qy <= (y0+y1)>>1 ? 0 : 1;
            for (int i = 0; i < 4; ++i) {
                int k = ((i&1)^xOrder) + (((i>>1)^yOrder)<<1);
                if (q[k] != null) {
                    int x0 = q[k].x0, y0 = q[k].y0, x1 = q[k].x1, y1 = q[k].y1;

                    // find minimum distance from query point to child
                    xd[0] = q[k].x0 - qx; xd[1] = q[k].x1 - qx;
                    yd[0] = q[k].y0 - qy; yd[1] = q[k].y1 - qy;
                    int xi = qx < x0 ? 0 : (qx > x1 ? 1 : 2), yi = qy < y0 ? 0 : (qy > y1 ? 1 : 2);
                    if (xd[xi]*xd[xi] + yd[yi]*yd[yi] < bestDist)
                        q[k].getNearestNeighborRecursive(qx, qy, points);
                }
            }
        }
    }
}