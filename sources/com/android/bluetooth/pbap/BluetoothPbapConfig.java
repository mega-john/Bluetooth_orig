package com.android.bluetooth.pbap;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import com.android.bluetooth.C0000R;

public class BluetoothPbapConfig {
    private static boolean sIncludePhotosInVcard = false;
    private static boolean sUseProfileForOwnerVcard = true;

    public static void init(Context ctx) {
        Resources r = ctx.getResources();
        if (r != null) {
            try {
                sUseProfileForOwnerVcard = r.getBoolean(C0000R.bool.pbap_use_profile_for_owner_vcard);
            } catch (Exception e) {
                Log.e("BluetoothPbapConfig", "", e);
            }
            try {
                sIncludePhotosInVcard = r.getBoolean(C0000R.bool.pbap_include_photos_in_vcard);
            } catch (Exception e2) {
                Log.e("BluetoothPbapConfig", "", e2);
            }
        }
    }

    public static boolean useProfileForOwnerVcard() {
        return sUseProfileForOwnerVcard;
    }

    public static boolean includePhotosInVcard() {
        return sIncludePhotosInVcard;
    }
}
