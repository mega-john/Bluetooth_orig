package com.android.bluetooth.opp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.net.Uri;
import android.os.Bundle;
import com.android.bluetooth.C0000R;

public class BluetoothOppLiveFolder extends Activity {
    public static final Uri CONTENT_URI = Uri.parse("content://com.android.bluetooth.opp/live_folders/received");

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ("android.intent.action.CREATE_LIVE_FOLDER".equals(getIntent().getAction())) {
            setResult(-1, createLiveFolder(this, CONTENT_URI, getString(C0000R.string.btopp_live_folder), C0000R.drawable.ic_launcher_folder_bluetooth));
        } else {
            setResult(0);
        }
        finish();
    }

    private static Intent createLiveFolder(Context context, Uri uri, String name, int icon) {
        Intent intent = new Intent();
        intent.setDataAndNormalize(uri);
        intent.putExtra("android.intent.extra.livefolder.BASE_INTENT", new Intent(Constants.ACTION_OPEN, BluetoothShare.CONTENT_URI));
        intent.putExtra("android.intent.extra.livefolder.NAME", name);
        intent.putExtra("android.intent.extra.livefolder.ICON", ShortcutIconResource.fromContext(context, icon));
        intent.putExtra("android.intent.extra.livefolder.DISPLAY_MODE", 2);
        return intent;
    }
}
