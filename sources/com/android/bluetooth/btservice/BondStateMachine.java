package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Message;
import android.os.UserHandle;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hid.HidService;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.vcard.VCardConfig;
import java.util.ArrayList;

final class BondStateMachine extends StateMachine {
    static final int BONDING_STATE_CHANGE = 4;
    static final int BOND_STATE_BONDED = 2;
    static final int BOND_STATE_BONDING = 1;
    static final int BOND_STATE_NONE = 0;
    static final int CANCEL_BOND = 2;
    static final int CREATE_BOND = 1;
    private static final boolean DBG = false;
    static final int PIN_REQUEST = 6;
    static final int REMOVE_BOND = 3;
    static final int SSP_REQUEST = 5;
    private static final String TAG = "BluetoothBondStateMachine";
    private BluetoothAdapter mAdapter;
    private AdapterProperties mAdapterProperties;
    private AdapterService mAdapterService;
    private PendingCommandState mPendingCommandState = new PendingCommandState();
    private RemoteDevices mRemoteDevices;
    private StableState mStableState = new StableState();

    private class PendingCommandState extends State {
        private final ArrayList<BluetoothDevice> mDevices;

        private PendingCommandState() {
            this.mDevices = new ArrayList();
        }

        public void enter() {
            BondStateMachine.this.infoLog("Entering PendingCommandState State");
            BluetoothDevice dev = BondStateMachine.this.getCurrentMessage().obj;
        }

        public boolean processMessage(Message msg) {
            BluetoothDevice dev = msg.obj;
            DeviceProperties devProp = BondStateMachine.this.mRemoteDevices.getDeviceProperties(dev);
            boolean result = BondStateMachine.DBG;
            if (!this.mDevices.contains(dev) || msg.what == 2 || msg.what == 4 || msg.what == 5 || msg.what == 6) {
                Intent intent = new Intent("android.bluetooth.device.action.PAIRING_REQUEST");
                switch (msg.what) {
                    case 1:
                        result = BondStateMachine.this.createBond(dev, msg.arg1, BondStateMachine.DBG);
                        break;
                    case 2:
                        result = BondStateMachine.this.cancelBond(dev);
                        break;
                    case 3:
                        result = BondStateMachine.this.removeBond(dev, BondStateMachine.DBG);
                        break;
                    case 4:
                        int newState = msg.arg1;
                        BondStateMachine.this.sendIntent(dev, newState, BondStateMachine.this.getUnbondReasonFromHALCode(msg.arg2));
                        if (newState == 11) {
                            if (!this.mDevices.contains(dev)) {
                                result = true;
                                break;
                            }
                        }
                        result = !this.mDevices.remove(dev) ? true : BondStateMachine.DBG;
                        if (this.mDevices.isEmpty()) {
                            result = BondStateMachine.DBG;
                            BondStateMachine.this.transitionTo(BondStateMachine.this.mStableState);
                        }
                        if (newState != 10) {
                            if (newState == 12) {
                                break;
                            }
                        }
                        BondStateMachine.this.mAdapterService.setPhonebookAccessPermission(dev, 0);
                        BondStateMachine.this.mAdapterService.setMessageAccessPermission(dev, 0);
                        BondStateMachine.this.clearProfilePriorty(dev);
                        break;
                        break;
                    case 5:
                        BondStateMachine.this.sendDisplayPinIntent(devProp.getAddress(), msg.arg1, msg.arg2);
                        break;
                    case 6:
                        int btDeviceClass = dev.getBluetoothClass().getDeviceClass();
                        if (btDeviceClass != 1344 && btDeviceClass != 1472) {
                            BondStateMachine.this.sendDisplayPinIntent(devProp.getAddress(), 0, 0);
                            break;
                        }
                        BondStateMachine.this.sendDisplayPinIntent(devProp.getAddress(), 100000 + ((int) Math.floor(Math.random() * 899999.0d)), 5);
                        break;
                        break;
                    default:
                        Log.e(BondStateMachine.TAG, "Received unhandled event:" + msg.what);
                        return BondStateMachine.DBG;
                }
                if (result) {
                    this.mDevices.add(dev);
                }
                return true;
            }
            BondStateMachine.this.deferMessage(msg);
            return true;
        }
    }

    private class StableState extends State {
        private StableState() {
        }

        public void enter() {
            BondStateMachine.this.infoLog("StableState(): Entering Off State");
        }

        public boolean processMessage(Message msg) {
            BluetoothDevice dev = msg.obj;
            switch (msg.what) {
                case 1:
                    BondStateMachine.this.createBond(dev, msg.arg1, true);
                    break;
                case 3:
                    BondStateMachine.this.removeBond(dev, true);
                    break;
                case 4:
                    int newState = msg.arg1;
                    if (newState != 11) {
                        Log.e(BondStateMachine.TAG, "In stable state, received invalid newState: " + newState);
                        break;
                    }
                    BondStateMachine.this.sendIntent(dev, newState, 0);
                    BondStateMachine.this.transitionTo(BondStateMachine.this.mPendingCommandState);
                    break;
                default:
                    Log.e(BondStateMachine.TAG, "Received unhandled state: " + msg.what);
                    return BondStateMachine.DBG;
            }
            return true;
        }
    }

    private BondStateMachine(AdapterService service, AdapterProperties prop, RemoteDevices remoteDevices) {
        super("BondStateMachine:");
        addState(this.mStableState);
        addState(this.mPendingCommandState);
        this.mRemoteDevices = remoteDevices;
        this.mAdapterService = service;
        this.mAdapterProperties = prop;
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        setInitialState(this.mStableState);
    }

    public static BondStateMachine make(AdapterService service, AdapterProperties prop, RemoteDevices remoteDevices) {
        Log.d(TAG, "make");
        BondStateMachine bsm = new BondStateMachine(service, prop, remoteDevices);
        bsm.start();
        return bsm;
    }

    public void doQuit() {
        quitNow();
    }

    public void cleanup() {
        this.mAdapterService = null;
        this.mRemoteDevices = null;
        this.mAdapterProperties = null;
    }

    private boolean cancelBond(BluetoothDevice dev) {
        if (dev.getBondState() == 11) {
            if (this.mAdapterService.cancelBondNative(Utils.getBytesFromAddress(dev.getAddress()))) {
                return true;
            }
            Log.e(TAG, "Unexpected error while cancelling bond:");
        }
        return DBG;
    }

    private boolean removeBond(BluetoothDevice dev, boolean transition) {
        if (dev.getBondState() == 12) {
            if (this.mAdapterService.removeBondNative(Utils.getBytesFromAddress(dev.getAddress()))) {
                if (transition) {
                    transitionTo(this.mPendingCommandState);
                }
                return true;
            }
            Log.e(TAG, "Unexpected error while removing bond:");
        }
        return DBG;
    }

    private boolean createBond(BluetoothDevice dev, int transport, boolean transition) {
        if (dev.getBondState() != 10) {
            return DBG;
        }
        infoLog("Bond address is:" + dev);
        if (this.mAdapterService.createBondNative(Utils.getBytesFromAddress(dev.getAddress()), transport)) {
            if (transition) {
                transitionTo(this.mPendingCommandState);
            }
            return true;
        }
        sendIntent(dev, 10, 9);
        return DBG;
    }

    private void sendDisplayPinIntent(byte[] address, int pin, int variant) {
        Intent intent = new Intent("android.bluetooth.device.action.PAIRING_REQUEST");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", this.mRemoteDevices.getDevice(address));
        if (pin != 0) {
            intent.putExtra("android.bluetooth.device.extra.PAIRING_KEY", pin);
        }
        intent.putExtra("android.bluetooth.device.extra.PAIRING_VARIANT", variant);
        intent.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
        AdapterService adapterService = this.mAdapterService;
        AdapterService adapterService2 = this.mAdapterService;
        adapterService.sendOrderedBroadcast(intent, ProfileService.BLUETOOTH_ADMIN_PERM);
    }

    private void sendIntent(BluetoothDevice device, int newState, int reason) {
        DeviceProperties devProp = this.mRemoteDevices.getDeviceProperties(device);
        int oldState = 10;
        if (devProp != null) {
            oldState = devProp.getBondState();
        }
        if (oldState != newState) {
            this.mAdapterProperties.onBondStateChanged(device, newState);
            Intent intent = new Intent("android.bluetooth.device.action.BOND_STATE_CHANGED");
            intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
            intent.putExtra("android.bluetooth.device.extra.BOND_STATE", newState);
            intent.putExtra("android.bluetooth.device.extra.PREVIOUS_BOND_STATE", oldState);
            if (newState == 10) {
                intent.putExtra("android.bluetooth.device.extra.REASON", reason);
            }
            this.mAdapterService.sendBroadcastAsUser(intent, UserHandle.ALL, ProfileService.BLUETOOTH_PERM);
            infoLog("Bond State Change Intent:" + device + " OldState: " + oldState + " NewState: " + newState);
        }
    }

    void bondStateChangeCallback(int status, byte[] address, int newState) {
        BluetoothDevice device = this.mRemoteDevices.getDevice(address);
        if (device == null) {
            infoLog("No record of the device:" + device);
            device = this.mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
        }
        infoLog("bondStateChangeCallback: Status: " + status + " Address: " + device + " newState: " + newState);
        Message msg = obtainMessage(4);
        msg.obj = device;
        if (newState == 2) {
            msg.arg1 = 12;
        } else if (newState == 1) {
            msg.arg1 = 11;
        } else {
            msg.arg1 = 10;
        }
        msg.arg2 = status;
        sendMessage(msg);
    }

    void sspRequestCallback(byte[] address, byte[] name, int cod, int pairingVariant, int passkey) {
        int variant;
        if (this.mRemoteDevices.getDevice(address) == null) {
            this.mRemoteDevices.addDeviceProperties(address);
        }
        infoLog("sspRequestCallback: " + address + " name: " + name + " cod: " + cod + " pairingVariant " + pairingVariant + " passkey: " + passkey);
        boolean displayPasskey = DBG;
        switch (pairingVariant) {
            case 0:
                variant = 2;
                displayPasskey = true;
                break;
            case 1:
                variant = 1;
                break;
            case 2:
                variant = 3;
                break;
            case 3:
                variant = 4;
                displayPasskey = true;
                break;
            default:
                errorLog("SSP Pairing variant not present");
                return;
        }
        BluetoothDevice device = this.mRemoteDevices.getDevice(address);
        if (device == null) {
            warnLog("Device is not known for:" + Utils.getAddressStringFromByte(address));
            this.mRemoteDevices.addDeviceProperties(address);
            device = this.mRemoteDevices.getDevice(address);
        }
        Message msg = obtainMessage(5);
        msg.obj = device;
        if (displayPasskey) {
            msg.arg1 = passkey;
        }
        msg.arg2 = variant;
        sendMessage(msg);
    }

    void pinRequestCallback(byte[] address, byte[] name, int cod) {
        BluetoothDevice bdDevice = this.mRemoteDevices.getDevice(address);
        if (bdDevice == null) {
            this.mRemoteDevices.addDeviceProperties(address);
        }
        infoLog("pinRequestCallback: " + address + " name:" + name + " cod:" + cod);
        Message msg = obtainMessage(6);
        msg.obj = bdDevice;
        sendMessage(msg);
    }

    private void setProfilePriorty(BluetoothDevice device) {
        HidService hidService = HidService.getHidService();
        A2dpService a2dpService = A2dpService.getA2dpService();
        HeadsetService headsetService = HeadsetService.getHeadsetService();
        if (hidService != null && hidService.getPriority(device) == -1) {
            hidService.setPriority(device, 100);
        }
        if (a2dpService != null && a2dpService.getPriority(device) == -1) {
            a2dpService.setPriority(device, 100);
        }
        if (headsetService != null && headsetService.getPriority(device) == -1) {
            headsetService.setPriority(device, 100);
        }
    }

    private void clearProfilePriorty(BluetoothDevice device) {
        HidService hidService = HidService.getHidService();
        A2dpService a2dpService = A2dpService.getA2dpService();
        HeadsetService headsetService = HeadsetService.getHeadsetService();
        if (hidService != null) {
            hidService.setPriority(device, -1);
        }
        if (a2dpService != null) {
            a2dpService.setPriority(device, -1);
        }
        if (headsetService != null) {
            headsetService.setPriority(device, -1);
        }
    }

    private void infoLog(String msg) {
        Log.i(TAG, msg);
    }

    private void errorLog(String msg) {
        Log.e(TAG, msg);
    }

    private void warnLog(String msg) {
        Log.w(TAG, msg);
    }

    private int getUnbondReasonFromHALCode(int reason) {
        if (reason == 0) {
            return 0;
        }
        if (reason == 10) {
            return 4;
        }
        if (reason == 9) {
            return 1;
        }
        if (reason == 11) {
            return 2;
        }
        if (reason == 12) {
            return 6;
        }
        return 9;
    }
}
