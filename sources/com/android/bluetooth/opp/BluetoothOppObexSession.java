package com.android.bluetooth.opp;

import android.os.Handler;

public interface BluetoothOppObexSession {
    public static final int MSG_CONNECT_TIMEOUT = 4;
    public static final int MSG_SESSION_COMPLETE = 1;
    public static final int MSG_SESSION_ERROR = 2;
    public static final int MSG_SHARE_COMPLETE = 0;
    public static final int MSG_SHARE_INTERRUPTED = 3;
    public static final int SESSION_TIMEOUT = 50000;

    void addShare(BluetoothOppShareInfo bluetoothOppShareInfo);

    void start(Handler handler, int i);

    void stop();

    void unblock();
}
