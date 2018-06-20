package com.android.bluetooth.opp;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.android.bluetooth.C0000R;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;

public class BluetoothOppIncomingFileConfirmActivity extends AlertActivity implements OnClickListener {
    /* renamed from: D */
    private static final boolean f35D = true;
    private static final int DISMISS_TIMEOUT_DIALOG = 0;
    private static final int DISMISS_TIMEOUT_DIALOG_VALUE = 2000;
    private static final String PREFERENCE_USER_TIMEOUT = "user_timeout";
    private static final String TAG = "BluetoothIncomingFileConfirmActivity";
    /* renamed from: V */
    private static final boolean f36V = false;
    private BroadcastReceiver mReceiver = new C00371();
    private boolean mTimeout = false;
    private final Handler mTimeoutHandler = new C00382();
    private BluetoothOppTransferInfo mTransInfo;
    private ContentValues mUpdateValues;
    private Uri mUri;

    /* renamed from: com.android.bluetooth.opp.BluetoothOppIncomingFileConfirmActivity$1 */
    class C00371 extends BroadcastReceiver {
        C00371() {
        }

        public void onReceive(Context context, Intent intent) {
            if (BluetoothShare.USER_CONFIRMATION_TIMEOUT_ACTION.equals(intent.getAction())) {
                BluetoothOppIncomingFileConfirmActivity.this.onTimeout();
            }
        }
    }

    /* renamed from: com.android.bluetooth.opp.BluetoothOppIncomingFileConfirmActivity$2 */
    class C00382 extends Handler {
        C00382() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    BluetoothOppIncomingFileConfirmActivity.this.finish();
                    return;
                default:
                    return;
            }
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        setTheme(C0000R.style.Theme.Material.Settings.Floating);
        super.onCreate(savedInstanceState);
        this.mUri = getIntent().getData();
        this.mTransInfo = new BluetoothOppTransferInfo();
        this.mTransInfo = BluetoothOppUtility.queryRecord(this, this.mUri);
        if (this.mTransInfo == null) {
            finish();
            return;
        }
        AlertParams p = this.mAlertParams;
        p.mTitle = getString(C0000R.string.incoming_file_confirm_content);
        p.mView = createView();
        p.mPositiveButtonText = getString(C0000R.string.incoming_file_confirm_ok);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(C0000R.string.incoming_file_confirm_cancel);
        p.mNegativeButtonListener = this;
        setupAlert();
        if (this.mTimeout) {
            onTimeout();
        }
        registerReceiver(this.mReceiver, new IntentFilter(BluetoothShare.USER_CONFIRMATION_TIMEOUT_ACTION));
    }

    private View createView() {
        View view = getLayoutInflater().inflate(C0000R.layout.incoming_dialog, null);
        ((TextView) view.findViewById(C0000R.id.from_content)).setText(this.mTransInfo.mDeviceName);
        ((TextView) view.findViewById(C0000R.id.filename_content)).setText(this.mTransInfo.mFileName);
        ((TextView) view.findViewById(C0000R.id.size_content)).setText(Formatter.formatFileSize(this, (long) this.mTransInfo.mTotalBytes));
        return view;
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case -2:
                this.mUpdateValues = new ContentValues();
                this.mUpdateValues.put(BluetoothShare.USER_CONFIRMATION, Integer.valueOf(3));
                getContentResolver().update(this.mUri, this.mUpdateValues, null, null);
                return;
            case -1:
                if (!this.mTimeout) {
                    this.mUpdateValues = new ContentValues();
                    this.mUpdateValues.put(BluetoothShare.USER_CONFIRMATION, Integer.valueOf(1));
                    getContentResolver().update(this.mUri, this.mUpdateValues, null, null);
                    Toast.makeText(this, getString(C0000R.string.bt_toast_1), 0).show();
                    return;
                }
                return;
            default:
                return;
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != 4) {
            return false;
        }
        Log.d(TAG, "onKeyDown() called; Key: back key");
        this.mUpdateValues = new ContentValues();
        this.mUpdateValues.put(BluetoothShare.VISIBILITY, Integer.valueOf(1));
        getContentResolver().update(this.mUri, this.mUpdateValues, null, null);
        Toast.makeText(this, getString(C0000R.string.bt_toast_2), 0).show();
        finish();
        return true;
    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.mReceiver);
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        this.mTimeout = savedInstanceState.getBoolean(PREFERENCE_USER_TIMEOUT);
        if (this.mTimeout) {
            onTimeout();
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PREFERENCE_USER_TIMEOUT, this.mTimeout);
    }

    private void onTimeout() {
        this.mTimeout = true;
        this.mAlert.setTitle(getString(C0000R.string.incoming_file_confirm_timeout_content, new Object[]{this.mTransInfo.mDeviceName}));
        this.mAlert.getButton(-2).setVisibility(8);
        this.mAlert.getButton(-1).setText(getString(C0000R.string.incoming_file_confirm_timeout_ok));
        this.mTimeoutHandler.sendMessageDelayed(this.mTimeoutHandler.obtainMessage(0), 2000);
    }
}
