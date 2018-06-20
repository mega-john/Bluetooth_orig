package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMasInstance;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.vcard.VCardConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

final class RemoteDevices {
    private static final boolean DBG = false;
    private static final int MAS_INSTANCE_INTENT_DELAY = 6000;
    private static final int MESSAGE_MAS_INSTANCE_INTENT = 2;
    private static final int MESSAGE_UUID_INTENT = 1;
    private static final String TAG = "BluetoothRemoteDevices";
    private static final int UUID_INTENT_DELAY = 6000;
    private static BluetoothAdapter mAdapter;
    private static AdapterService mAdapterService;
    private static ArrayList<BluetoothDevice> mSdpMasTracker;
    private static ArrayList<BluetoothDevice> mSdpTracker;
    private HashMap<BluetoothDevice, DeviceProperties> mDevices;
    private final Handler mHandler = new C00091();
    private Object mObject = new Object();

    /* renamed from: com.android.bluetooth.btservice.RemoteDevices$1 */
    class C00091 extends Handler {
        C00091() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    BluetoothDevice device = msg.obj;
                    if (device != null) {
                        RemoteDevices.this.sendUuidIntent(device);
                        return;
                    }
                    return;
                case 2:
                    BluetoothDevice dev = msg.obj;
                    if (dev != null) {
                        RemoteDevices.this.sendMasInstanceIntent(dev, null);
                        return;
                    }
                    return;
                default:
                    return;
            }
        }
    }

    class DeviceProperties {
        private byte[] mAddress;
        private String mAlias;
        private int mBluetoothClass;
        private int mBondState = 10;
        private int mDeviceType;
        private String mName;
        private short mRssi;
        private ParcelUuid[] mUuids;

        DeviceProperties() {
        }

        String getName() {
            String str;
            synchronized (RemoteDevices.this.mObject) {
                str = this.mName;
            }
            return str;
        }

        int getBluetoothClass() {
            int i;
            synchronized (RemoteDevices.this.mObject) {
                i = this.mBluetoothClass;
            }
            return i;
        }

        ParcelUuid[] getUuids() {
            ParcelUuid[] parcelUuidArr;
            synchronized (RemoteDevices.this.mObject) {
                parcelUuidArr = this.mUuids;
            }
            return parcelUuidArr;
        }

        byte[] getAddress() {
            byte[] bArr;
            synchronized (RemoteDevices.this.mObject) {
                bArr = this.mAddress;
            }
            return bArr;
        }

        short getRssi() {
            short s;
            synchronized (RemoteDevices.this.mObject) {
                s = this.mRssi;
            }
            return s;
        }

        int getDeviceType() {
            int i;
            synchronized (RemoteDevices.this.mObject) {
                i = this.mDeviceType;
            }
            return i;
        }

        String getAlias() {
            String str;
            synchronized (RemoteDevices.this.mObject) {
                str = this.mAlias;
            }
            return str;
        }

        void setAlias(String mAlias) {
            synchronized (RemoteDevices.this.mObject) {
                this.mAlias = mAlias;
                RemoteDevices.mAdapterService.setDevicePropertyNative(this.mAddress, 10, mAlias.getBytes());
            }
        }

        void setBondState(int mBondState) {
            synchronized (RemoteDevices.this.mObject) {
                this.mBondState = mBondState;
                if (mBondState == 10) {
                    this.mUuids = null;
                }
            }
        }

        int getBondState() {
            int i;
            synchronized (RemoteDevices.this.mObject) {
                i = this.mBondState;
            }
            return i;
        }
    }

    RemoteDevices(AdapterService service) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mAdapterService = service;
        mSdpTracker = new ArrayList();
        mSdpMasTracker = new ArrayList();
        this.mDevices = new HashMap();
    }

    void cleanup() {
        if (mSdpTracker != null) {
            mSdpTracker.clear();
        }
        if (mSdpMasTracker != null) {
            mSdpMasTracker.clear();
        }
        if (this.mDevices != null) {
            this.mDevices.clear();
        }
    }

    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    DeviceProperties getDeviceProperties(BluetoothDevice device) {
        DeviceProperties deviceProperties;
        synchronized (this.mDevices) {
            deviceProperties = (DeviceProperties) this.mDevices.get(device);
        }
        return deviceProperties;
    }

    BluetoothDevice getDevice(byte[] address) {
        for (BluetoothDevice dev : this.mDevices.keySet()) {
            if (dev.getAddress().equals(Utils.getAddressStringFromByte(address))) {
                return dev;
            }
        }
        return null;
    }

    DeviceProperties addDeviceProperties(byte[] address) {
        DeviceProperties prop;
        synchronized (this.mDevices) {
            prop = new DeviceProperties();
            BluetoothDevice device = mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
            prop.mAddress = address;
            this.mDevices.put(device, prop);
        }
        return prop;
    }

    private void sendUuidIntent(BluetoothDevice device) {
        DeviceProperties prop = getDeviceProperties(device);
        Intent intent = new Intent("android.bluetooth.device.action.UUID");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.putExtra("android.bluetooth.device.extra.UUID", prop == null ? null : prop.mUuids);
        mAdapterService.initProfilePriorities(device, prop.mUuids);
        mAdapterService.sendBroadcast(intent, ProfileService.BLUETOOTH_ADMIN_PERM);
        mSdpTracker.remove(device);
    }

    private void sendMasInstanceIntent(BluetoothDevice device, ArrayList<BluetoothMasInstance> instances) {
        Intent intent = new Intent("android.bluetooth.device.action.MAS_INSTANCE");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        if (instances != null) {
            intent.putExtra("android.bluetooth.device.extra.MAS_INSTANCE", instances);
        }
        mAdapterService.sendBroadcast(intent, ProfileService.BLUETOOTH_ADMIN_PERM);
        mSdpMasTracker.remove(device);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void devicePropertyChangedCallback(byte[] address, int[] types, byte[][] values) {
        DeviceProperties device;
        BluetoothDevice bdDevice = getDevice(address);
        if (bdDevice == null) {
            device = addDeviceProperties(address);
            bdDevice = getDevice(address);
        } else {
            device = getDeviceProperties(bdDevice);
        }
        for (int j = 0; j < types.length; j++) {
            int type = types[j];
            byte[] val = values[j];
            if (val.length <= 0) {
                errorLog("devicePropertyChangedCallback: bdDevice: " + bdDevice + ", value is empty for type: " + type);
            } else {
                synchronized (this.mObject) {
                    Intent intent;
                    AdapterService adapterService;
                    AdapterService adapterService2;
                    switch (type) {
                        case 1:
                            device.mName = new String(val);
                            intent = new Intent("android.bluetooth.device.action.NAME_CHANGED");
                            intent.putExtra("android.bluetooth.device.extra.DEVICE", bdDevice);
                            intent.putExtra("android.bluetooth.device.extra.NAME", device.mName);
                            intent.addFlags(VCardConfig.FLAG_APPEND_TYPE_PARAM);
                            adapterService = mAdapterService;
                            adapterService2 = mAdapterService;
                            adapterService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            debugLog("Remote Device name is: " + device.mName);
                        case 2:
                            device.mAddress = val;
                            debugLog("Remote Address is:" + Utils.getAddressStringFromByte(val));
                        case 3:
                            int numUuids = val.length / 16;
                            device.mUuids = Utils.byteArrayToUuid(val);
                            sendUuidIntent(bdDevice);
                        case 4:
                            device.mBluetoothClass = Utils.byteArrayToInt(val);
                            intent = new Intent("android.bluetooth.device.action.CLASS_CHANGED");
                            intent.putExtra("android.bluetooth.device.extra.DEVICE", bdDevice);
                            intent.putExtra("android.bluetooth.device.extra.CLASS", new BluetoothClass(device.mBluetoothClass));
                            intent.addFlags(VCardConfig.FLAG_APPEND_TYPE_PARAM);
                            adapterService = mAdapterService;
                            adapterService2 = mAdapterService;
                            adapterService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            debugLog("Remote class is:" + device.mBluetoothClass);
                        case 5:
                            device.mDeviceType = Utils.byteArrayToInt(val);
                        case 10:
                            if (device.mAlias != null) {
                                System.arraycopy(val, 0, device.mAlias, 0, val.length);
                            } else {
                                device.mAlias = new String(val);
                            }
                        case 11:
                            device.mRssi = (short) val[0];
                    }
                }
            }
        }
    }

    void deviceFoundCallback(byte[] address) {
        BluetoothDevice device = getDevice(address);
        debugLog("deviceFoundCallback: Remote Address is:" + device);
        DeviceProperties deviceProp = getDeviceProperties(device);
        if (deviceProp == null) {
            errorLog("Device Properties is null for Device:" + device);
            return;
        }
        Intent intent = new Intent("android.bluetooth.device.action.FOUND");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.putExtra("android.bluetooth.device.extra.CLASS", new BluetoothClass(Integer.valueOf(deviceProp.mBluetoothClass).intValue()));
        intent.putExtra("android.bluetooth.device.extra.RSSI", deviceProp.mRssi);
        intent.putExtra("android.bluetooth.device.extra.NAME", deviceProp.mName);
        AdapterService adapterService = mAdapterService;
        AdapterService adapterService2 = mAdapterService;
        adapterService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    void aclStateChangeCallback(int status, byte[] address, int newState) {
        BluetoothDevice device = getDevice(address);
        if (device == null) {
            errorLog("aclStateChangeCallback: Device is NULL");
            return;
        }
        Intent intent;
        if (getDeviceProperties(device) == null) {
            errorLog("aclStateChangeCallback reported unknown device " + Arrays.toString(address));
        }
        if (newState == 0) {
            intent = new Intent("android.bluetooth.device.action.ACL_CONNECTED");
            debugLog("aclStateChangeCallback: State:Connected to Device:" + device);
        } else {
            intent = new Intent("android.bluetooth.device.action.ACL_DISCONNECTED");
            debugLog("aclStateChangeCallback: State:DisConnected to Device:" + device);
        }
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.addFlags(VCardConfig.FLAG_APPEND_TYPE_PARAM);
        AdapterService adapterService = mAdapterService;
        AdapterService adapterService2 = mAdapterService;
        adapterService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    void deviceMasInstancesFoundCallback(int status, byte[] address, String[] name, int[] scn, int[] id, int[] msgtype) {
        BluetoothDevice device = getDevice(address);
        if (device == null) {
            errorLog("deviceMasInstancesFoundCallback: Device is NULL");
            return;
        }
        debugLog("deviceMasInstancesFoundCallback: found " + name.length + " instances");
        ArrayList<BluetoothMasInstance> instances = new ArrayList();
        for (int i = 0; i < name.length; i++) {
            BluetoothMasInstance inst = new BluetoothMasInstance(id[i], name[i], scn[i], msgtype[i]);
            debugLog(inst.toString());
            instances.add(inst);
        }
        sendMasInstanceIntent(device, instances);
    }

    void fetchUuids(BluetoothDevice device) {
        if (!mSdpTracker.contains(device)) {
            mSdpTracker.add(device);
            Message message = this.mHandler.obtainMessage(1);
            message.obj = device;
            this.mHandler.sendMessageDelayed(message, 6000);
            mAdapterService.getRemoteServicesNative(Utils.getBytesFromAddress(device.getAddress()));
        }
    }

    void fetchMasInstances(BluetoothDevice device) {
        if (!mSdpMasTracker.contains(device)) {
            mSdpMasTracker.add(device);
            Message message = this.mHandler.obtainMessage(2);
            message.obj = device;
            this.mHandler.sendMessageDelayed(message, 6000);
            mAdapterService.getRemoteMasInstancesNative(Utils.getBytesFromAddress(device.getAddress()));
        }
    }

    private void errorLog(String msg) {
        Log.e(TAG, msg);
    }

    private void debugLog(String msg) {
    }

    private void infoLog(String msg) {
    }

    private void warnLog(String msg) {
        Log.w(TAG, msg);
    }
}
