package com.android.bluetooth.opp;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.android.bluetooth.C0000R;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;
import com.android.vcard.VCardConfig;

public class BluetoothOppBtEnableActivity extends AlertActivity implements OnClickListener {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlertParams p = this.mAlertParams;
        p.mIconAttrId = 16843605;
        p.mTitle = getString(C0000R.string.bt_enable_title);
        p.mView = createView();
        p.mPositiveButtonText = getString(C0000R.string.bt_enable_ok);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(C0000R.string.bt_enable_cancel);
        p.mNegativeButtonListener = this;
        setupAlert();
    }

    private View createView() {
        View view = getLayoutInflater().inflate(C0000R.layout.confirm_dialog, null);
        ((TextView) view.findViewById(C0000R.id.content)).setText(getString(C0000R.string.bt_enable_line1) + "\n\n" + getString(C0000R.string.bt_enable_line2) + "\n");
        return view;
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case -2:
                finish();
                return;
            case -1:
                BluetoothOppManager mOppManager = BluetoothOppManager.getInstance(this);
                mOppManager.enableBluetooth();
                mOppManager.mSendingFlag = true;
                Toast.makeText(this, getString(C0000R.string.enabling_progress_content), 0).show();
                Intent in = new Intent(this, BluetoothOppBtEnablingActivity.class);
                in.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
                startActivity(in);
                finish();
                return;
            default:
                return;
        }
    }
}
