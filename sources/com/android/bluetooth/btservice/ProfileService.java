package com.android.bluetooth.btservice;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.android.bluetooth.Utils;
import java.util.HashMap;

public abstract class ProfileService extends Service {
    public static final String BLUETOOTH_ADMIN_PERM = "android.permission.BLUETOOTH_ADMIN";
    public static final String BLUETOOTH_PERM = "android.permission.BLUETOOTH";
    public static final String BLUETOOTH_PRIVILEGED = "android.permission.BLUETOOTH_PRIVILEGED";
    private static final boolean DBG = false;
    private static final int PROFILE_SERVICE_MODE = 2;
    private static final String TAG = "BluetoothProfileService";
    private static HashMap<String, Integer> sReferenceCount = new HashMap();
    protected BluetoothAdapter mAdapter;
    private AdapterService mAdapterService;
    protected IProfileServiceBinder mBinder;
    private boolean mCleaningUp = DBG;
    protected String mName = getName();
    protected boolean mStartError = DBG;

    public interface IProfileServiceBinder extends IBinder {
        boolean cleanup();
    }

    protected abstract IProfileServiceBinder initBinder();

    protected abstract boolean start();

    protected abstract boolean stop();

    protected String getName() {
        return getClass().getSimpleName();
    }

    protected boolean isAvailable() {
        return (this.mStartError || this.mCleaningUp) ? DBG : true;
    }

    protected boolean cleanup() {
        return true;
    }

    protected ProfileService() {
    }

    protected void finalize() {
    }

    public void onCreate() {
        super.onCreate();
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mBinder = initBinder();
        this.mAdapterService = AdapterService.getAdapterService();
        if (this.mAdapterService != null) {
            this.mAdapterService.addProfile(this);
        } else {
            Log.w(TAG, "onCreate, null mAdapterService");
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (this.mStartError || this.mAdapter == null) {
            Log.w(this.mName, "Stopping profile service: device does not have BT");
            doStop(intent);
        } else if (checkCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM) != 0) {
            Log.e(this.mName, "Permission denied!");
        } else if (intent == null) {
            Log.d(this.mName, "Restarting profile service...");
        } else {
            if (AdapterService.ACTION_SERVICE_STATE_CHANGED.equals(intent.getStringExtra(AdapterService.EXTRA_ACTION))) {
                int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
                if (state == 10) {
                    Log.d(this.mName, "Received stop request...Stopping profile...");
                    doStop(intent);
                } else if (state == 12) {
                    Log.d(this.mName, "Received start request. Starting profile...");
                    doStart(intent);
                }
            }
        }
        return 2;
    }

    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }

    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    public void dump(StringBuilder sb) {
        sb.append("Profile: " + this.mName + "\n");
    }

    public static void println(StringBuilder sb, String s) {
        sb.append("  ");
        sb.append(s);
        sb.append("\n");
    }

    public void onDestroy() {
        if (this.mAdapterService != null) {
            this.mAdapterService.removeProfile(this);
        }
        if (!this.mCleaningUp) {
            this.mCleaningUp = true;
            cleanup();
            if (this.mBinder != null) {
                this.mBinder.cleanup();
                this.mBinder = null;
            }
        }
        super.onDestroy();
        this.mAdapter = null;
    }

    private void doStart(Intent intent) {
        if (this.mAdapter == null) {
            Log.e(this.mName, "Error starting profile. BluetoothAdapter is null");
            return;
        }
        this.mStartError = !start() ? true : DBG;
        if (this.mStartError) {
            Log.e(this.mName, "Error starting profile. BluetoothAdapter is null");
        } else {
            notifyProfileServiceStateChanged(12);
        }
    }

    private void doStop(Intent intent) {
        if (stop()) {
            notifyProfileServiceStateChanged(10);
            stopSelf();
            return;
        }
        Log.e(this.mName, "Unable to stop profile");
    }

    protected void notifyProfileServiceStateChanged(int state) {
        if (this.mAdapterService != null) {
            this.mAdapterService.onProfileServiceStateChanged(getClass().getName(), state);
        }
    }

    public void notifyProfileConnectionStateChanged(BluetoothDevice device, int profileId, int newState, int prevState) {
        if (this.mAdapterService != null) {
            this.mAdapterService.onProfileConnectionStateChanged(device, profileId, newState, prevState);
        }
    }

    protected BluetoothDevice getDevice(byte[] address) {
        return this.mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
    }

    protected void log(String msg) {
        Log.d(this.mName, msg);
    }
}
