package com.max.route;

import android.os.Bundle;

import com.max.main.Common;
import com.max.main.Persistable;

public class GPSHistory implements Persistable {
    /** Minimum ms between consecutive readings to store historical positions. */
    private final long historyRecordInterval;

    /** Timestamp of last recorded historical position. */
    private long lastHistoryMs;

    /** Index into circular history buffers. This points to the oldest idx, that will be replaced next. */
    private int historyIdx = 0;

    private final int historySize;

    /** Circular buffer containing the last couple of X positions. */
    private int[] historyUtmX;

    /** Circular buffer containing the last couple of Y positions. */
    private int[] historyUtmY;

    public GPSHistory(long historyRecordInterval, int historySize) {
        this.historyRecordInterval = historyRecordInterval;
        this.historySize = historySize;
        historyUtmX = Common.filledArray(historySize, Integer.MAX_VALUE);
        historyUtmY = Common.filledArray(historySize, Integer.MAX_VALUE);
    }

    @Override
    public void saveInstanceState(Bundle savedInstanceState, String prefix) {
        prefix += "_";
        savedInstanceState.putLong(prefix + "lastHistoryMs", lastHistoryMs);
        savedInstanceState.putInt(prefix + "historyIdx", historyIdx);
        savedInstanceState.putIntArray(prefix + "historyUtmX", historyUtmX);
        savedInstanceState.putIntArray(prefix + "historyUtmY", historyUtmY);
    }

    @Override
    public void restoreInstanceState(Bundle savedInstanceState, String prefix) {
        prefix += "_";
        lastHistoryMs = savedInstanceState.getLong(prefix + "lastHistoryMs");
        historyIdx = savedInstanceState.getInt(prefix + "historyIdx");
        historyUtmX = savedInstanceState.getIntArray(prefix + "historyUtmX");
        historyUtmY = savedInstanceState.getIntArray(prefix + "historyUtmY");
    }

    /** Store a new history point if it's due.
     * @return True if update occurred. */
    public boolean update(long curTime, int utmX, int utmY) {
        if (curTime - lastHistoryMs >= historyRecordInterval) {
            historyUtmX[historyIdx] = utmX;
            historyUtmY[historyIdx] = utmY;
            lastHistoryMs = curTime;
            historyIdx = (historyIdx + 1) % historySize;
            return true;
        }
        return false;
    }

    public int getSize() { return historySize; }

    public int getX(int idx) {
        return historyUtmX[(historyIdx + idx) % historySize];
    }

    public int getY(int idx) {
        return historyUtmY[(historyIdx + idx) % historySize];
    }
}
