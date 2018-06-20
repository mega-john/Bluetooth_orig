package com.android.bluetooth.hid;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetoothInputDevice.Stub;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings.Global;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import com.android.vcard.VCardConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HidService extends ProfileService {
    private static final int CONN_STATE_CONNECTED = 0;
    private static final int CONN_STATE_CONNECTING = 1;
    private static final int CONN_STATE_DISCONNECTED = 2;
    private static final int CONN_STATE_DISCONNECTING = 3;
    private static final boolean DBG = false;
    private static final int MESSAGE_CONNECT = 1;
    private static final int MESSAGE_CONNECT_STATE_CHANGED = 3;
    private static final int MESSAGE_DISCONNECT = 2;
    private static final int MESSAGE_GET_PROTOCOL_MODE = 4;
    private static final int MESSAGE_GET_REPORT = 8;
    private static final int MESSAGE_ON_GET_PROTOCOL_MODE = 6;
    private static final int MESSAGE_ON_GET_REPORT = 9;
    private static final int MESSAGE_ON_HANDSHAKE = 13;
    private static final int MESSAGE_ON_VIRTUAL_UNPLUG = 12;
    private static final int MESSAGE_SEND_DATA = 11;
    private static final int MESSAGE_SET_PROTOCOL_MODE = 7;
    private static final int MESSAGE_SET_REPORT = 10;
    private static final int MESSAGE_VIRTUAL_UNPLUG = 5;
    private static final String TAG = "HidService";
    private static HidService sHidService;
    private final Handler mHandler = new C00201();
    private Map<BluetoothDevice, Integer> mInputDevices;
    private boolean mNativeAvailable;
    private BluetoothDevice mTargetDevice = null;

    /* renamed from: com.android.bluetooth.hid.HidService$1 */
    class C00201 extends Handler {
        C00201() {
        }

        public void handleMessage(Message msg) {
            BluetoothDevice device;
            Bundle data;
            switch (msg.what) {
                case 1:
                    device = msg.obj;
                    if (HidService.this.connectHidNative(Utils.getByteAddress(device))) {
                        HidService.this.mTargetDevice = device;
                        return;
                    }
                    HidService.this.broadcastConnectionState(device, 3);
                    HidService.this.broadcastConnectionState(device, 0);
                    return;
                case 2:
                    device = (BluetoothDevice) msg.obj;
                    if (!HidService.this.disconnectHidNative(Utils.getByteAddress(device))) {
                        HidService.this.broadcastConnectionState(device, 3);
                        HidService.this.broadcastConnectionState(device, 0);
                        return;
                    }
                    return;
                case 3:
                    device = HidService.this.getDevice((byte[]) msg.obj);
                    int halState = msg.arg1;
                    Integer prevStateInteger = (Integer) HidService.this.mInputDevices.get(device);
                    int prevState = prevStateInteger == null ? 0 : prevStateInteger.intValue();
                    if (halState == 0 && prevState == 0 && !HidService.this.okToConnect(device)) {
                        HidService.this.disconnectHidNative(Utils.getByteAddress(device));
                    } else {
                        HidService.this.broadcastConnectionState(device, HidService.convertHalState(halState));
                    }
                    if (halState == 0 && HidService.this.mTargetDevice != null && HidService.this.mTargetDevice.equals(device)) {
                        HidService.this.mTargetDevice = null;
                        AdapterService.getAdapterService().enable(HidService.DBG);
                        return;
                    }
                    return;
                case 4:
                    if (!HidService.this.getProtocolModeNative(Utils.getByteAddress((BluetoothDevice) msg.obj))) {
                        Log.e(HidService.TAG, "Error: get protocol mode native returns false");
                        return;
                    }
                    return;
                case 5:
                    if (!HidService.this.virtualUnPlugNative(Utils.getByteAddress((BluetoothDevice) msg.obj))) {
                        Log.e(HidService.TAG, "Error: virtual unplug native returns false");
                        return;
                    }
                    return;
                case 6:
                    HidService.this.broadcastProtocolMode(HidService.this.getDevice((byte[]) msg.obj), msg.arg1);
                    return;
                case 7:
                    device = (BluetoothDevice) msg.obj;
                    byte protocolMode = (byte) msg.arg1;
                    HidService.this.log("sending set protocol mode(" + protocolMode + ")");
                    if (!HidService.this.setProtocolModeNative(Utils.getByteAddress(device), protocolMode)) {
                        Log.e(HidService.TAG, "Error: set protocol mode native returns false");
                        return;
                    }
                    return;
                case 8:
                    device = (BluetoothDevice) msg.obj;
                    data = msg.getData();
                    if (!HidService.this.getReportNative(Utils.getByteAddress(device), data.getByte("android.bluetooth.BluetoothInputDevice.extra.REPORT_TYPE"), data.getByte("android.bluetooth.BluetoothInputDevice.extra.REPORT_ID"), data.getInt("android.bluetooth.BluetoothInputDevice.extra.REPORT_BUFFER_SIZE"))) {
                        Log.e(HidService.TAG, "Error: get report native returns false");
                        return;
                    }
                    return;
                case 9:
                    device = HidService.this.getDevice((byte[]) msg.obj);
                    data = msg.getData();
                    HidService.this.broadcastReport(device, data.getByteArray("android.bluetooth.BluetoothInputDevice.extra.REPORT"), data.getInt("android.bluetooth.BluetoothInputDevice.extra.REPORT_BUFFER_SIZE"));
                    return;
                case 10:
                    device = (BluetoothDevice) msg.obj;
                    data = msg.getData();
                    if (!HidService.this.setReportNative(Utils.getByteAddress(device), data.getByte("android.bluetooth.BluetoothInputDevice.extra.REPORT_TYPE"), data.getString("android.bluetooth.BluetoothInputDevice.extra.REPORT"))) {
                        Log.e(HidService.TAG, "Error: set report native returns false");
                        return;
                    }
                    return;
                case 11:
                    if (!HidService.this.sendDataNative(Utils.getByteAddress((BluetoothDevice) msg.obj), msg.getData().getString("android.bluetooth.BluetoothInputDevice.extra.REPORT"))) {
                        Log.e(HidService.TAG, "Error: send data native returns false");
                        return;
                    }
                    return;
                case 12:
                    HidService.this.broadcastVirtualUnplugStatus(HidService.this.getDevice((byte[]) msg.obj), msg.arg1);
                    return;
                case 13:
                    HidService.this.broadcastHandshake(HidService.this.getDevice((byte[]) msg.obj), msg.arg1);
                    return;
                default:
                    return;
            }
        }
    }

    private static class BluetoothInputDeviceBinder extends Stub implements IProfileServiceBinder {
        private HidService mService;

        public BluetoothInputDeviceBinder(HidService svc) {
            this.mService = svc;
        }

        public boolean cleanup() {
            this.mService = null;
            return true;
        }

        private HidService getService() {
            if (!Utils.checkCaller()) {
                Log.w(HidService.TAG, "InputDevice call not allowed for non-active user");
                return null;
            } else if (this.mService == null || !this.mService.isAvailable()) {
                return null;
            } else {
                return this.mService;
            }
        }

        public boolean connect(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) {
                return HidService.DBG;
            }
            return service.connect(device);
        }

        public boolean disconnect(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) {
                return HidService.DBG;
            }
            return service.disconnect(device);
        }

        public int getConnectionState(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) {
                return 0;
            }
            return service.getConnectionState(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            return getDevicesMatchingConnectionStates(new int[]{2});
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            HidService service = getService();
            if (service == null) {
                return new ArrayList(0);
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            HidService service = getService();
            if (service == null) {
                return HidService.DBG;
            }
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) {
                return -1;
            }
            return service.getPriority(device);
        }

        public boolean getProtocolMode(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) {
                return HidService.DBG;
            }
            return service.getProtocolMode(device);
        }

        public boolean virtualUnplug(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) {
                return HidService.DBG;
            }
            return service.virtualUnplug(device);
        }

        public boolean setProtocolMode(BluetoothDevice device, int protocolMode) {
            HidService service = getService();
            if (service == null) {
                return HidService.DBG;
            }
            return service.setProtocolMode(device, protocolMode);
        }

        public boolean getReport(BluetoothDevice device, byte reportType, byte reportId, int bufferSize) {
            HidService service = getService();
            if (service == null) {
                return HidService.DBG;
            }
            return service.getReport(device, reportType, reportId, bufferSize);
        }

        public boolean setReport(BluetoothDevice device, byte reportType, String report) {
            HidService service = getService();
            if (service == null) {
                return HidService.DBG;
            }
            return service.setReport(device, reportType, report);
        }

        public boolean sendData(BluetoothDevice device, String report) {
            HidService service = getService();
            if (service == null) {
                return HidService.DBG;
            }
            return service.sendData(device, report);
        }
    }

    private static native void classInitNative();

    private native void cleanupNative();

    private native boolean connectHidNative(byte[] bArr);

    private native boolean disconnectHidNative(byte[] bArr);

    private native boolean getProtocolModeNative(byte[] bArr);

    private native boolean getReportNative(byte[] bArr, byte b, byte b2, int i);

    private native void initializeNative();

    private native boolean sendDataNative(byte[] bArr, String str);

    private native boolean setProtocolModeNative(byte[] bArr, byte b);

    private native boolean setReportNative(byte[] bArr, byte b, String str);

    private native boolean virtualUnPlugNative(byte[] bArr);

    static {
        classInitNative();
    }

    public String getName() {
        return TAG;
    }

    public IProfileServiceBinder initBinder() {
        return new BluetoothInputDeviceBinder(this);
    }

    protected boolean start() {
        this.mInputDevices = Collections.synchronizedMap(new HashMap());
        initializeNative();
        this.mNativeAvailable = true;
        setHidService(this);
        return true;
    }

    protected boolean stop() {
        return true;
    }

    protected boolean cleanup() {
        if (this.mNativeAvailable) {
            cleanupNative();
            this.mNativeAvailable = DBG;
        }
        if (this.mInputDevices != null) {
            this.mInputDevices.clear();
        }
        clearHidService();
        return true;
    }

    public static synchronized HidService getHidService() {
        HidService hidService;
        synchronized (HidService.class) {
            if (sHidService == null || !sHidService.isAvailable()) {
                hidService = null;
            } else {
                hidService = sHidService;
            }
        }
        return hidService;
    }

    private static synchronized void setHidService(HidService instance) {
        synchronized (HidService.class) {
            if (instance != null) {
                if (instance.isAvailable()) {
                    sHidService = instance;
                }
            }
        }
    }

    private static synchronized void clearHidService() {
        synchronized (HidService.class) {
            sHidService = null;
        }
    }

    boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (getConnectionState(device) != 0) {
            Log.e(TAG, "Hid Device not disconnected: " + device);
            return DBG;
        } else if (getPriority(device) == 0) {
            Log.e(TAG, "Hid Device PRIORITY_OFF: " + device);
            return DBG;
        } else {
            this.mHandler.sendMessage(this.mHandler.obtainMessage(1, device));
            return true;
        }
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        this.mHandler.sendMessage(this.mHandler.obtainMessage(2, device));
        return true;
    }

    int getConnectionState(BluetoothDevice device) {
        if (this.mInputDevices.get(device) == null) {
            return 0;
        }
        return ((Integer) this.mInputDevices.get(device)).intValue();
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> inputDevices = new ArrayList();
        for (BluetoothDevice device : this.mInputDevices.keySet()) {
            int inputDeviceState = getConnectionState(device);
            for (int state : states) {
                if (state == inputDeviceState) {
                    inputDevices.add(device);
                    break;
                }
            }
        }
        return inputDevices;
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        Global.putInt(getContentResolver(), Global.getBluetoothInputDevicePriorityKey(device.getAddress()), priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        return Global.getInt(getContentResolver(), Global.getBluetoothInputDevicePriorityKey(device.getAddress()), -1);
    }

    boolean getProtocolMode(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (getConnectionState(device) != 2) {
            return DBG;
        }
        this.mHandler.sendMessage(this.mHandler.obtainMessage(4, device));
        return true;
    }

    boolean virtualUnplug(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (getConnectionState(device) != 2) {
            return DBG;
        }
        this.mHandler.sendMessage(this.mHandler.obtainMessage(5, device));
        return true;
    }

    boolean setProtocolMode(BluetoothDevice device, int protocolMode) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (getConnectionState(device) != 2) {
            return DBG;
        }
        Message msg = this.mHandler.obtainMessage(7);
        msg.obj = device;
        msg.arg1 = protocolMode;
        this.mHandler.sendMessage(msg);
        return true;
    }

    boolean getReport(BluetoothDevice device, byte reportType, byte reportId, int bufferSize) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (getConnectionState(device) != 2) {
            return DBG;
        }
        Message msg = this.mHandler.obtainMessage(8);
        msg.obj = device;
        Bundle data = new Bundle();
        data.putByte("android.bluetooth.BluetoothInputDevice.extra.REPORT_TYPE", reportType);
        data.putByte("android.bluetooth.BluetoothInputDevice.extra.REPORT_ID", reportId);
        data.putInt("android.bluetooth.BluetoothInputDevice.extra.REPORT_BUFFER_SIZE", bufferSize);
        msg.setData(data);
        this.mHandler.sendMessage(msg);
        return true;
    }

    boolean setReport(BluetoothDevice device, byte reportType, String report) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (getConnectionState(device) != 2) {
            return DBG;
        }
        Message msg = this.mHandler.obtainMessage(10);
        msg.obj = device;
        Bundle data = new Bundle();
        data.putByte("android.bluetooth.BluetoothInputDevice.extra.REPORT_TYPE", reportType);
        data.putString("android.bluetooth.BluetoothInputDevice.extra.REPORT", report);
        msg.setData(data);
        this.mHandler.sendMessage(msg);
        return true;
    }

    boolean sendData(BluetoothDevice device, String report) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (getConnectionState(device) != 2) {
            return DBG;
        }
        return sendDataNative(Utils.getByteAddress(device), report);
    }

    private void onGetProtocolMode(byte[] address, int mode) {
        Message msg = this.mHandler.obtainMessage(6);
        msg.obj = address;
        msg.arg1 = mode;
        this.mHandler.sendMessage(msg);
    }

    private void onGetReport(byte[] address, byte[] report, int rpt_size) {
        Message msg = this.mHandler.obtainMessage(9);
        msg.obj = address;
        Bundle data = new Bundle();
        data.putByteArray("android.bluetooth.BluetoothInputDevice.extra.REPORT", report);
        data.putInt("android.bluetooth.BluetoothInputDevice.extra.REPORT_BUFFER_SIZE", rpt_size);
        msg.setData(data);
        this.mHandler.sendMessage(msg);
    }

    private void onHandshake(byte[] address, int status) {
        Message msg = this.mHandler.obtainMessage(13);
        msg.obj = address;
        msg.arg1 = status;
        this.mHandler.sendMessage(msg);
    }

    private void onVirtualUnplug(byte[] address, int status) {
        Message msg = this.mHandler.obtainMessage(12);
        msg.obj = address;
        msg.arg1 = status;
        this.mHandler.sendMessage(msg);
    }

    private void onConnectStateChanged(byte[] address, int state) {
        Message msg = this.mHandler.obtainMessage(3);
        msg.obj = address;
        msg.arg1 = state;
        this.mHandler.sendMessage(msg);
    }

    private void broadcastConnectionState(BluetoothDevice device, int newState) {
        Integer prevStateInteger = (Integer) this.mInputDevices.get(device);
        int prevState = prevStateInteger == null ? 0 : prevStateInteger.intValue();
        if (prevState == newState) {
            Log.w(TAG, "no state change: " + newState);
            return;
        }
        this.mInputDevices.put(device, Integer.valueOf(newState));
        log("Connection state " + device + ": " + prevState + "->" + newState);
        notifyProfileConnectionStateChanged(device, 4, newState, prevState);
        Intent intent = new Intent("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED");
        intent.putExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", prevState);
        intent.putExtra("android.bluetooth.profile.extra.STATE", newState);
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.addFlags(VCardConfig.FLAG_APPEND_TYPE_PARAM);
        sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private void broadcastHandshake(BluetoothDevice device, int status) {
        Intent intent = new Intent("android.bluetooth.input.profile.action.HANDSHAKE");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.putExtra("android.bluetooth.BluetoothInputDevice.extra.STATUS", status);
        intent.addFlags(VCardConfig.FLAG_APPEND_TYPE_PARAM);
        sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private void broadcastProtocolMode(BluetoothDevice device, int protocolMode) {
        Intent intent = new Intent("android.bluetooth.input.profile.action.PROTOCOL_MODE_CHANGED");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.putExtra("android.bluetooth.BluetoothInputDevice.extra.PROTOCOL_MODE", protocolMode);
        intent.addFlags(VCardConfig.FLAG_APPEND_TYPE_PARAM);
        sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private void broadcastReport(BluetoothDevice device, byte[] report, int rpt_size) {
        Intent intent = new Intent("android.bluetooth.input.profile.action.REPORT");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.putExtra("android.bluetooth.BluetoothInputDevice.extra.REPORT", report);
        intent.putExtra("android.bluetooth.BluetoothInputDevice.extra.REPORT_BUFFER_SIZE", rpt_size);
        intent.addFlags(VCardConfig.FLAG_APPEND_TYPE_PARAM);
        sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private void broadcastVirtualUnplugStatus(BluetoothDevice device, int status) {
        Intent intent = new Intent("android.bluetooth.input.profile.action.VIRTUAL_UNPLUG_STATUS");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.putExtra("android.bluetooth.BluetoothInputDevice.extra.VIRTUAL_UNPLUG_STATUS", status);
        intent.addFlags(VCardConfig.FLAG_APPEND_TYPE_PARAM);
        sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private boolean okToConnect(BluetoothDevice device) {
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService == null || ((adapterService.isQuietModeEnabled() && this.mTargetDevice == null) || getPriority(device) == 0 || device.getBondState() == 10)) {
            return DBG;
        }
        return true;
    }

    private static int convertHalState(int halState) {
        switch (halState) {
            case 0:
                return 2;
            case 1:
                return 1;
            case 2:
                return 0;
            case 3:
                return 3;
            default:
                Log.e(TAG, "bad hid connection state: " + halState);
                return 0;
        }
    }

    public void dump(StringBuilder sb) {
        super.dump(sb);
        ProfileService.println(sb, "mTargetDevice: " + this.mTargetDevice);
        ProfileService.println(sb, "mInputDevices:");
        for (BluetoothDevice device : this.mInputDevices.keySet()) {
            ProfileService.println(sb, "  " + device + " : " + this.mInputDevices.get(device));
        }
    }
}
