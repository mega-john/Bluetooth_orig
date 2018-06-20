package com.android.bluetooth.opp;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.util.ArrayList;

public class BluetoothOppHandoverReceiver extends BroadcastReceiver {
    /* renamed from: D */
    private static final boolean f33D = true;
    public static final String TAG = "BluetoothOppHandoverReceiver";
    /* renamed from: V */
    private static final boolean f34V = false;

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        BluetoothDevice device;
        if (action.equals(Constants.ACTION_HANDOVER_SEND) || action.equals(Constants.ACTION_HANDOVER_SEND_MULTIPLE)) {
            device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            if (device == null) {
                Log.d(TAG, "No device attached to handover intent.");
                return;
            }
            if (action.equals(Constants.ACTION_HANDOVER_SEND)) {
                String type = intent.getType();
                Uri stream = (Uri) intent.getParcelableExtra("android.intent.extra.STREAM");
                if (stream == null || type == null) {
                    Log.d(TAG, "No mimeType or stream attached to handover request");
                } else {
                    BluetoothOppManager.getInstance(context).saveSendingFileInfo(type, stream.toString(), true);
                }
            } else if (action.equals(Constants.ACTION_HANDOVER_SEND_MULTIPLE)) {
                ArrayList<Uri> uris = new ArrayList();
                String mimeType = intent.getType();
                ArrayList uris2 = intent.getParcelableArrayListExtra("android.intent.extra.STREAM");
                if (mimeType == null || uris2 == null) {
                    Log.d(TAG, "No mimeType or stream attached to handover request");
                    return;
                }
                BluetoothOppManager.getInstance(context).saveSendingFileInfo(mimeType, uris2, true);
            }
            BluetoothOppManager.getInstance(context).startTransfer(device);
        } else if (action.equals(Constants.ACTION_WHITELIST_DEVICE)) {
            device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            Log.d(TAG, "Adding " + device + " to whitelist");
            if (device != null) {
                BluetoothOppManager.getInstance(context).addToWhitelist(device.getAddress());
            }
        } else if (action.equals(Constants.ACTION_STOP_HANDOVER)) {
            int id = intent.getIntExtra(Constants.EXTRA_BT_OPP_TRANSFER_ID, -1);
            if (id != -1) {
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + id);
                Log.d(TAG, "Stopping handover transfer with Uri " + contentUri);
                context.getContentResolver().delete(contentUri, null, null);
            }
        } else {
            Log.d(TAG, "Unknown action: " + action);
        }
    }
}
