package com.android.bluetooth.pbap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import com.android.bluetooth.C0000R;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;

public class BluetoothPbapActivity extends AlertActivity implements OnClickListener, OnPreferenceChangeListener, TextWatcher {
    private static final int BLUETOOTH_OBEX_AUTHKEY_MAX_LENGTH = 16;
    private static final int DIALOG_YES_NO_AUTH = 1;
    private static final int DISMISS_TIMEOUT_DIALOG = 0;
    private static final int DISMISS_TIMEOUT_DIALOG_VALUE = 2000;
    private static final String KEY_USER_TIMEOUT = "user_timeout";
    private static final String TAG = "BluetoothPbapActivity";
    /* renamed from: V */
    private static final boolean f69V = false;
    private CheckBox mAlwaysAllowed;
    private boolean mAlwaysAllowedValue = true;
    private int mCurrentDialog;
    private EditText mKeyView;
    private Button mOkButton;
    private BroadcastReceiver mReceiver = new C00611();
    private String mSessionKey = "";
    private boolean mTimeout = false;
    private final Handler mTimeoutHandler = new C00622();
    private View mView;
    private TextView messageView;

    /* renamed from: com.android.bluetooth.pbap.BluetoothPbapActivity$1 */
    class C00611 extends BroadcastReceiver {
        C00611() {
        }

        public void onReceive(Context context, Intent intent) {
            if (BluetoothPbapService.USER_CONFIRM_TIMEOUT_ACTION.equals(intent.getAction())) {
                BluetoothPbapActivity.this.onTimeout();
            }
        }
    }

    /* renamed from: com.android.bluetooth.pbap.BluetoothPbapActivity$2 */
    class C00622 extends Handler {
        C00622() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    BluetoothPbapActivity.this.finish();
                    return;
                default:
                    return;
            }
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getAction().equals(BluetoothPbapService.AUTH_CHALL_ACTION)) {
            showPbapDialog(1);
            this.mCurrentDialog = 1;
        } else {
            Log.e(TAG, "Error: this activity may be started only with intent PBAP_ACCESS_REQUEST or PBAP_AUTH_CHALL ");
            finish();
        }
        registerReceiver(this.mReceiver, new IntentFilter(BluetoothPbapService.USER_CONFIRM_TIMEOUT_ACTION));
    }

    private void showPbapDialog(int id) {
        AlertParams p = this.mAlertParams;
        switch (id) {
            case 1:
                p.mTitle = getString(C0000R.string.pbap_session_key_dialog_header);
                p.mView = createView(1);
                p.mPositiveButtonText = getString(17039370);
                p.mPositiveButtonListener = this;
                p.mNegativeButtonText = getString(17039360);
                p.mNegativeButtonListener = this;
                setupAlert();
                this.mOkButton = this.mAlert.getButton(-1);
                this.mOkButton.setEnabled(false);
                return;
            default:
                return;
        }
    }

    private String createDisplayText(int id) {
        String mRemoteName = BluetoothPbapService.getRemoteDeviceName();
        switch (id) {
            case 1:
                return getString(C0000R.string.pbap_session_key_dialog_title, new Object[]{mRemoteName});
            default:
                return null;
        }
    }

    private View createView(int id) {
        switch (id) {
            case 1:
                this.mView = getLayoutInflater().inflate(C0000R.layout.auth, null);
                this.messageView = (TextView) this.mView.findViewById(C0000R.id.message);
                this.messageView.setText(createDisplayText(id));
                this.mKeyView = (EditText) this.mView.findViewById(C0000R.id.text);
                this.mKeyView.addTextChangedListener(this);
                this.mKeyView.setFilters(new InputFilter[]{new LengthFilter(16)});
                return this.mView;
            default:
                return null;
        }
    }

    private void onPositive() {
        if (!this.mTimeout && this.mCurrentDialog == 1) {
            sendIntentToReceiver(BluetoothPbapService.AUTH_RESPONSE_ACTION, BluetoothPbapService.EXTRA_SESSION_KEY, this.mSessionKey);
            this.mKeyView.removeTextChangedListener(this);
        }
        this.mTimeout = false;
        finish();
    }

    private void onNegative() {
        if (this.mCurrentDialog == 1) {
            sendIntentToReceiver(BluetoothPbapService.AUTH_CANCELLED_ACTION, null, null);
            this.mKeyView.removeTextChangedListener(this);
        }
        finish();
    }

    private void sendIntentToReceiver(String intentName, String extraName, String extraValue) {
        Intent intent = new Intent(intentName);
        intent.setClassName("com.android.bluetooth", BluetoothPbapReceiver.class.getName());
        if (extraName != null) {
            intent.putExtra(extraName, extraValue);
        }
        sendBroadcast(intent);
    }

    private void sendIntentToReceiver(String intentName, String extraName, boolean extraValue) {
        Intent intent = new Intent(intentName);
        intent.setClassName("com.android.bluetooth", BluetoothPbapReceiver.class.getName());
        if (extraName != null) {
            intent.putExtra(extraName, extraValue);
        }
        sendBroadcast(intent);
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case -2:
                onNegative();
                return;
            case -1:
                if (this.mCurrentDialog == 1) {
                    this.mSessionKey = this.mKeyView.getText().toString();
                }
                onPositive();
                return;
            default:
                return;
        }
    }

    private void onTimeout() {
        this.mTimeout = true;
        if (this.mCurrentDialog == 1) {
            this.messageView.setText(getString(C0000R.string.pbap_authentication_timeout_message, new Object[]{BluetoothPbapService.getRemoteDeviceName()}));
            this.mKeyView.setVisibility(8);
            this.mKeyView.clearFocus();
            this.mKeyView.removeTextChangedListener(this);
            this.mOkButton.setEnabled(true);
            this.mAlert.getButton(-2).setVisibility(8);
        }
        this.mTimeoutHandler.sendMessageDelayed(this.mTimeoutHandler.obtainMessage(0), 2000);
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        this.mTimeout = savedInstanceState.getBoolean(KEY_USER_TIMEOUT);
        if (this.mTimeout) {
            onTimeout();
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_USER_TIMEOUT, this.mTimeout);
    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.mReceiver);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }

    public void beforeTextChanged(CharSequence s, int start, int before, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void afterTextChanged(Editable s) {
        if (s.length() > 0) {
            this.mOkButton.setEnabled(true);
        }
    }
}
