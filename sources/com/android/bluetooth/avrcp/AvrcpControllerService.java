package com.android.bluetooth.avrcp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetoothAvrcpController.Stub;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import java.util.ArrayList;
import java.util.List;

public class AvrcpControllerService extends ProfileService {
    private static final boolean DBG = false;
    private static final int MESSAGE_SEND_PASS_THROUGH_CMD = 1;
    private static final String TAG = "AvrcpControllerService";
    private static AvrcpControllerService sAvrcpControllerService;
    private final ArrayList<BluetoothDevice> mConnectedDevices = new ArrayList();
    private AvrcpMessageHandler mHandler;

    private final class AvrcpMessageHandler extends Handler {
        private AvrcpMessageHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    AvrcpControllerService.this.sendPassThroughCommandNative(AvrcpControllerService.this.getByteAddress(msg.obj), msg.arg1, msg.arg2);
                    return;
                default:
                    return;
            }
        }
    }

    private static class BluetoothAvrcpControllerBinder extends Stub implements IProfileServiceBinder {
        private AvrcpControllerService mService;

        private AvrcpControllerService getService() {
            if (!Utils.checkCaller()) {
                Log.w(AvrcpControllerService.TAG, "AVRCP call not allowed for non-active user");
                return null;
            } else if (this.mService == null || !this.mService.isAvailable()) {
                return null;
            } else {
                return this.mService;
            }
        }

        BluetoothAvrcpControllerBinder(AvrcpControllerService svc) {
            this.mService = svc;
        }

        public boolean cleanup() {
            this.mService = null;
            return true;
        }

        public List<BluetoothDevice> getConnectedDevices() {
            AvrcpControllerService service = getService();
            if (service == null) {
                return new ArrayList(0);
            }
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            AvrcpControllerService service = getService();
            if (service == null) {
                return new ArrayList(0);
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            AvrcpControllerService service = getService();
            if (service == null) {
                return 0;
            }
            return service.getConnectionState(device);
        }

        public void sendPassThroughCmd(BluetoothDevice device, int keyCode, int keyState) {
            Log.v(AvrcpControllerService.TAG, "Binder Call: sendPassThroughCmd");
            AvrcpControllerService service = getService();
            if (service != null) {
                service.sendPassThroughCmd(device, keyCode, keyState);
            }
        }
    }

    private static native void classInitNative();

    private native void cleanupNative();

    private native void initNative();

    private native boolean sendPassThroughCommandNative(byte[] bArr, int i, int i2);

    static {
        classInitNative();
    }

    public AvrcpControllerService() {
        initNative();
    }

    protected String getName() {
        return TAG;
    }

    protected IProfileServiceBinder initBinder() {
        return new BluetoothAvrcpControllerBinder(this);
    }

    protected boolean start() {
        HandlerThread thread = new HandlerThread("BluetoothAvrcpHandler");
        thread.start();
        this.mHandler = new AvrcpMessageHandler(thread.getLooper());
        setAvrcpControllerService(this);
        return true;
    }

    protected boolean stop() {
        return true;
    }

    protected boolean cleanup() {
        this.mHandler.removeCallbacksAndMessages(null);
        Looper looper = this.mHandler.getLooper();
        if (looper != null) {
            looper.quit();
        }
        clearAvrcpControllerService();
        cleanupNative();
        return true;
    }

    public static synchronized AvrcpControllerService getAvrcpControllerService() {
        AvrcpControllerService avrcpControllerService;
        synchronized (AvrcpControllerService.class) {
            if (sAvrcpControllerService == null || !sAvrcpControllerService.isAvailable()) {
                avrcpControllerService = null;
            } else {
                avrcpControllerService = sAvrcpControllerService;
            }
        }
        return avrcpControllerService;
    }

    private static synchronized void setAvrcpControllerService(AvrcpControllerService instance) {
        synchronized (AvrcpControllerService.class) {
            if (instance != null) {
                if (instance.isAvailable()) {
                    sAvrcpControllerService = instance;
                }
            }
        }
    }

    private static synchronized void clearAvrcpControllerService() {
        synchronized (AvrcpControllerService.class) {
            sAvrcpControllerService = null;
        }
    }

    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mConnectedDevices;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        for (int i : states) {
            if (i == 2) {
                return this.mConnectedDevices;
            }
        }
        return new ArrayList();
    }

    int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mConnectedDevices.contains(device) ? 2 : 0;
    }

    public void sendPassThroughCmd(BluetoothDevice device, int keyCode, int keyState) {
        Log.v(TAG, "keyCode: " + keyCode + " keyState: " + keyState);
        if (device == null) {
            throw new NullPointerException("device == null");
        }
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        this.mHandler.sendMessage(this.mHandler.obtainMessage(1, keyCode, keyState, device));
    }

    private void onConnectionStateChanged(boolean connected, byte[] address) {
        int oldState;
        int newState = 0;
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(Utils.getAddressStringFromByte(address));
        Log.d(TAG, "onConnectionStateChanged " + connected + " " + device);
        Intent intent = new Intent("android.bluetooth.acrcp-controller.profile.action.CONNECTION_STATE_CHANGED");
        if (this.mConnectedDevices.contains(device)) {
            oldState = 2;
        } else {
            oldState = 0;
        }
        if (connected) {
            newState = 2;
        }
        intent.putExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", oldState);
        intent.putExtra("android.bluetooth.profile.extra.STATE", newState);
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
        if (connected && oldState == 0) {
            this.mConnectedDevices.add(device);
        } else if (!connected && oldState == 2) {
            this.mConnectedDevices.remove(device);
        }
    }

    private void handlePassthroughRsp(int id, int keyState) {
        Log.d(TAG, "passthrough response received as: key: " + id + " state: " + keyState);
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    public void dump(StringBuilder sb) {
        super.dump(sb);
    }
}
