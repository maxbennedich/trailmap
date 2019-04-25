package com.max.main;

import android.os.Bundle;

public interface Persistable {
    public void saveInstanceState(Bundle savedInstanceState, String prefix);
    public void restoreInstanceState(Bundle savedInstanceState, String prefix);
}
