package com.android.bluetooth.btservice;

import android.app.Application;

public class AdapterApp extends Application {
    private static final boolean DBG = false;
    private static final String TAG = "BluetoothAdapterApp";
    private static int sRefCount = 0;

    static {
        System.loadLibrary("bluetooth_jni");
    }

    public void onCreate() {
        super.onCreate();
        Config.init(this);
    }

    protected void finalize() {
    }
}
