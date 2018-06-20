package com.android.bluetooth.map;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ExpandableListView;
import com.android.bluetooth.C0000R;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class BluetoothMapEmailSettings extends Activity {
    /* renamed from: D */
    private static final boolean f6D = true;
    private static final String TAG = "BluetoothMapEmailSettings";
    /* renamed from: V */
    private static final boolean f7V = false;
    LinkedHashMap<BluetoothMapEmailSettingsItem, ArrayList<BluetoothMapEmailSettingsItem>> mGroups;
    BluetoothMapEmailSettingsLoader mLoader = new BluetoothMapEmailSettingsLoader(this);

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(C0000R.layout.bluetooth_map_email_settings);
        this.mGroups = this.mLoader.parsePackages(true);
        ExpandableListView listView = (ExpandableListView) findViewById(C0000R.id.bluetooth_map_email_settings_list_view);
        listView.setAdapter(new BluetoothMapEmailSettingsAdapter(this, listView, this.mGroups, this.mLoader.getAccountsEnabledCount()));
    }
}
