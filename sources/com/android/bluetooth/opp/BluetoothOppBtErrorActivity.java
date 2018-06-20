package com.android.bluetooth.opp;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.android.bluetooth.C0000R;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;

public class BluetoothOppBtErrorActivity extends AlertActivity implements OnClickListener {
    private String mErrorContent;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String mErrorTitle = intent.getStringExtra("title");
        this.mErrorContent = intent.getStringExtra("content");
        AlertParams p = this.mAlertParams;
        p.mIconAttrId = 16843605;
        p.mTitle = mErrorTitle;
        p.mView = createView();
        p.mPositiveButtonText = getString(C0000R.string.bt_error_btn_ok);
        p.mPositiveButtonListener = this;
        setupAlert();
    }

    private View createView() {
        View view = getLayoutInflater().inflate(C0000R.layout.confirm_dialog, null);
        ((TextView) view.findViewById(C0000R.id.content)).setText(this.mErrorContent);
        return view;
    }

    public void onClick(DialogInterface dialog, int which) {
    }
}
