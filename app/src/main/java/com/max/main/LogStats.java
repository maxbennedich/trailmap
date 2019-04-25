package com.max.main;

import android.os.SystemClock;
import android.util.Log;

public class LogStats {
    private long last;

    public LogStats(String str) {
        Log.d("LogStats", str);
        reset();
    }

    public LogStats() {
        reset();
    }

    public void reset() {
        last = time();
    }

    public void log(String str) {
        long now = time();
        Log.d("LogStats", str + ": " + (now-last) + " ms");
        last = now;
    }

    public static long time() {
        return SystemClock.uptimeMillis();
    }
}
