package com.android.bluetooth.opp;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import com.android.bluetooth.C0000R;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;

public class BluetoothOppBtEnablingActivity extends AlertActivity {
    private static final int BT_ENABLING_TIMEOUT = 0;
    private static final int BT_ENABLING_TIMEOUT_VALUE = 20000;
    /* renamed from: D */
    private static final boolean f31D = true;
    private static final String TAG = "BluetoothOppEnablingActivity";
    /* renamed from: V */
    private static final boolean f32V = false;
    private final BroadcastReceiver mBluetoothReceiver = new C00362();
    private boolean mRegistered = false;
    private final Handler mTimeoutHandler = new C00351();

    /* renamed from: com.android.bluetooth.opp.BluetoothOppBtEnablingActivity$1 */
    class C00351 extends Handler {
        C00351() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    BluetoothOppBtEnablingActivity.this.cancelSendingProgress();
                    return;
                default:
                    return;
            }
        }
    }

    /* renamed from: com.android.bluetooth.opp.BluetoothOppBtEnablingActivity$2 */
    class C00362 extends BroadcastReceiver {
        C00362() {
        }

        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                switch (intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE)) {
                    case 12:
                        BluetoothOppBtEnablingActivity.this.mTimeoutHandler.removeMessages(0);
                        BluetoothOppBtEnablingActivity.this.finish();
                        return;
                    default:
                        return;
                }
            }
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            finish();
            return;
        }
        registerReceiver(this.mBluetoothReceiver, new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED"));
        this.mRegistered = true;
        AlertParams p = this.mAlertParams;
        p.mTitle = getString(C0000R.string.enabling_progress_title);
        p.mView = createView();
        setupAlert();
        this.mTimeoutHandler.sendMessageDelayed(this.mTimeoutHandler.obtainMessage(0), 20000);
    }

    private View createView() {
        View view = getLayoutInflater().inflate(C0000R.layout.bt_enabling_progress, null);
        ((TextView) view.findViewById(C0000R.id.progress_info)).setText(getString(C0000R.string.enabling_progress_content));
        return view;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 4) {
            Log.d(TAG, "onKeyDown() called; Key: back key");
            this.mTimeoutHandler.removeMessages(0);
            cancelSendingProgress();
        }
        return true;
    }

    protected void onDestroy() {
        super.onDestroy();
        if (this.mRegistered) {
            unregisterReceiver(this.mBluetoothReceiver);
        }
    }

    private void cancelSendingProgress() {
        BluetoothOppManager mOppManager = BluetoothOppManager.getInstance(this);
        if (mOppManager.mSendingFlag) {
            mOppManager.mSendingFlag = false;
        }
        finish();
    }
}
