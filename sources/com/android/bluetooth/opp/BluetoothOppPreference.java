package com.android.bluetooth.opp;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import java.util.HashMap;

public class BluetoothOppPreference {
    private static BluetoothOppPreference INSTANCE = null;
    private static Object INSTANCE_LOCK = new Object();
    private static final String TAG = "BluetoothOppPreference";
    /* renamed from: V */
    private static final boolean f45V = false;
    private SharedPreferences mChannelPreference;
    private HashMap<String, Integer> mChannels = new HashMap();
    private Context mContext;
    private boolean mInitialized;
    private SharedPreferences mNamePreference;
    private HashMap<String, String> mNames = new HashMap();

    public static BluetoothOppPreference getInstance(Context context) {
        BluetoothOppPreference bluetoothOppPreference;
        synchronized (INSTANCE_LOCK) {
            if (INSTANCE == null) {
                INSTANCE = new BluetoothOppPreference();
            }
            if (INSTANCE.init(context)) {
                bluetoothOppPreference = INSTANCE;
            } else {
                bluetoothOppPreference = null;
            }
        }
        return bluetoothOppPreference;
    }

    private boolean init(Context context) {
        if (!this.mInitialized) {
            this.mInitialized = true;
            this.mContext = context;
            this.mNamePreference = this.mContext.getSharedPreferences(Constants.BLUETOOTHOPP_NAME_PREFERENCE, 0);
            this.mChannelPreference = this.mContext.getSharedPreferences(Constants.BLUETOOTHOPP_CHANNEL_PREFERENCE, 0);
            this.mNames = (HashMap) this.mNamePreference.getAll();
            this.mChannels = (HashMap) this.mChannelPreference.getAll();
        }
        return true;
    }

    private String getChannelKey(BluetoothDevice remoteDevice, int uuid) {
        return remoteDevice.getAddress() + "_" + Integer.toHexString(uuid);
    }

    public String getName(BluetoothDevice remoteDevice) {
        if (remoteDevice.getAddress().equals("FF:FF:FF:00:00:00")) {
            return "localhost";
        }
        if (!this.mNames.isEmpty()) {
            String name = (String) this.mNames.get(remoteDevice.getAddress());
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    public int getChannel(BluetoothDevice remoteDevice, int uuid) {
        String key = getChannelKey(remoteDevice, uuid);
        Integer channel = null;
        if (this.mChannels != null) {
            channel = (Integer) this.mChannels.get(key);
        }
        return channel != null ? channel.intValue() : -1;
    }

    public void setName(BluetoothDevice remoteDevice, String name) {
        if (name != null && !name.equals(getName(remoteDevice))) {
            Editor ed = this.mNamePreference.edit();
            ed.putString(remoteDevice.getAddress(), name);
            ed.apply();
            this.mNames.put(remoteDevice.getAddress(), name);
        }
    }

    public void setChannel(BluetoothDevice remoteDevice, int uuid, int channel) {
        if (channel != getChannel(remoteDevice, uuid)) {
            String key = getChannelKey(remoteDevice, uuid);
            Editor ed = this.mChannelPreference.edit();
            ed.putInt(key, channel);
            ed.apply();
            this.mChannels.put(key, Integer.valueOf(channel));
        }
    }

    public void removeChannel(BluetoothDevice remoteDevice, int uuid) {
        String key = getChannelKey(remoteDevice, uuid);
        Editor ed = this.mChannelPreference.edit();
        ed.remove(key);
        ed.apply();
        this.mChannels.remove(key);
    }

    public void dump() {
        Log.d(TAG, "Dumping Names:  ");
        Log.d(TAG, this.mNames.toString());
        Log.d(TAG, "Dumping Channels:  ");
        Log.d(TAG, this.mChannels.toString());
    }
}
