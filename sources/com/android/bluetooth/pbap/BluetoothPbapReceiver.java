package com.android.bluetooth.pbap;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.android.bluetooth.btservice.AdapterService;

public class BluetoothPbapReceiver extends BroadcastReceiver {
    private static final String TAG = "BluetoothPbapReceiver";
    /* renamed from: V */
    private static final boolean f72V = false;

    public void onReceive(Context context, Intent intent) {
        Intent in = new Intent();
        in.putExtras(intent);
        in.setClass(context, BluetoothPbapService.class);
        String action = intent.getAction();
        in.putExtra(AdapterService.EXTRA_ACTION, action);
        boolean startService = true;
        if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
            int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
            in.putExtra("android.bluetooth.adapter.extra.STATE", state);
            if (state == 11 || state == 10) {
                startService = false;
            }
        } else {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                startService = false;
            }
        }
        if (startService) {
            context.startService(in);
        }
    }
}
