package com.android.bluetooth.btservice;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import com.android.bluetooth.C0000R;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.a2dp.A2dpSinkService;
import com.android.bluetooth.avrcp.AvrcpControllerService;
import com.android.bluetooth.gatt.GattService;
import com.android.bluetooth.hdp.HealthService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hfpclient.HeadsetClientService;
import com.android.bluetooth.hid.HidService;
import com.android.bluetooth.map.BluetoothMapService;
import com.android.bluetooth.pan.PanService;
import java.util.ArrayList;

public class Config {
    private static final Class[] PROFILE_SERVICES = new Class[]{HeadsetService.class, A2dpService.class, A2dpSinkService.class, HidService.class, HealthService.class, PanService.class, GattService.class, BluetoothMapService.class, HeadsetClientService.class, AvrcpControllerService.class};
    private static final int[] PROFILE_SERVICES_FLAG = new int[]{C0000R.bool.profile_supported_hs_hfp, C0000R.bool.profile_supported_a2dp, C0000R.bool.profile_supported_a2dp_sink, C0000R.bool.profile_supported_hid, C0000R.bool.profile_supported_hdp, C0000R.bool.profile_supported_pan, C0000R.bool.profile_supported_gatt, C0000R.bool.profile_supported_map, C0000R.bool.profile_supported_hfpclient, C0000R.bool.profile_supported_avrcp_controller};
    private static Class[] SUPPORTED_PROFILES = new Class[0];
    private static final String TAG = "AdapterServiceConfig";

    static void init(Context ctx) {
        if (ctx != null) {
            Resources resources = ctx.getResources();
            if (resources != null) {
                ArrayList<Class> profiles = new ArrayList(PROFILE_SERVICES.length);
                for (int i = 0; i < PROFILE_SERVICES_FLAG.length; i++) {
                    if (resources.getBoolean(PROFILE_SERVICES_FLAG[i])) {
                        Log.d(TAG, "Adding " + PROFILE_SERVICES[i].getSimpleName());
                        profiles.add(PROFILE_SERVICES[i]);
                    }
                }
                SUPPORTED_PROFILES = new Class[profiles.size()];
                profiles.toArray(SUPPORTED_PROFILES);
            }
        }
    }

    static Class[] getSupportedProfiles() {
        return SUPPORTED_PROFILES;
    }
}
