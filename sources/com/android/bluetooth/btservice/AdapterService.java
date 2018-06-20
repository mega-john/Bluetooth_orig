package com.android.bluetooth.btservice;

import android.app.Service;
import android.bluetooth.BluetoothActivityEnergyInfo;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetooth.Stub;
import android.bluetooth.IBluetoothCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.ParcelUuid;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.EventLog;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.hfp.BluetoothCmeError;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hid.HidService;
import com.android.bluetooth.opp.Constants;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdapterService extends Service {
    private static final String ACTION_ALARM_WAKEUP = "com.android.bluetooth.btservice.action.ALARM_WAKEUP";
    public static final String ACTION_LOAD_ADAPTER_PROPERTIES = "com.android.bluetooth.btservice.action.LOAD_ADAPTER_PROPERTIES";
    public static final String ACTION_SERVICE_STATE_CHANGED = "com.android.bluetooth.btservice.action.STATE_CHANGED";
    private static final int ADAPTER_SERVICE_TYPE = 1;
    static final String BLUETOOTH_ADMIN_PERM = "android.permission.BLUETOOTH_ADMIN";
    static final String BLUETOOTH_PERM = "android.permission.BLUETOOTH";
    public static final String BLUETOOTH_PRIVILEGED = "android.permission.BLUETOOTH_PRIVILEGED";
    private static final int CONNECT_OTHER_PROFILES_TIMEOUT = 6000;
    private static final boolean DBG = false;
    public static final String EXTRA_ACTION = "action";
    private static final String MESSAGE_ACCESS_PERMISSION_PREFERENCE_FILE = "message_access_permission";
    private static final int MESSAGE_CONNECT_OTHER_PROFILES = 30;
    private static final int MESSAGE_PROFILE_CONNECTION_STATE_CHANGED = 20;
    private static final int MESSAGE_PROFILE_INIT_PRIORITIES = 40;
    private static final int MESSAGE_PROFILE_SERVICE_STATE_CHANGED = 1;
    private static final int MESSAGE_RELEASE_WAKE_ALARM = 110;
    private static final int MESSAGE_SET_WAKE_ALARM = 100;
    private static final int MIN_ADVT_INSTANCES_FOR_MA = 5;
    private static final int MIN_OFFLOADED_FILTERS = 10;
    private static final int MIN_OFFLOADED_SCAN_STORAGE_BYTES = 1024;
    private static final String PHONEBOOK_ACCESS_PERMISSION_PREFERENCE_FILE = "phonebook_access_permission";
    public static final int PROFILE_CONN_CONNECTED = 1;
    public static final int PROFILE_CONN_REJECTED = 2;
    static final String RECEIVE_MAP_PERM = "android.permission.RECEIVE_BLUETOOTH_MAP";
    private static final String TAG = "BluetoothAdapterService";
    private static final boolean TRACE_REF = true;
    private static AdapterService sAdapterService;
    private static int sRefCount = 0;
    private Map<String, String> devices = new HashMap();
    private boolean discoverying = DBG;
    private AdapterProperties mAdapterProperties;
    private AdapterState mAdapterStateMachine;
    private final BroadcastReceiver mAlarmBroadcastReceiver = new C00062();
    private AdapterServiceBinder mBinder;
    private BondStateMachine mBondStateMachine;
    private RemoteCallbackList<IBluetoothCallback> mCallbacks;
    private boolean mCleaningUp;
    private int mCurrentRequestId;
    private int mEnergyUsedTotalVoltAmpSecMicro;
    private final Handler mHandler = new C00051();
    private int mIdleTimeTotalMs;
    private JniCallbacks mJniCallbacks;
    private boolean mNativeAvailable;
    private HashMap<String, Integer> mProfileServicesState = new HashMap();
    private final ArrayList<ProfileService> mProfiles = new ArrayList();
    private boolean mProfilesStarted;
    private boolean mQuietmode = DBG;
    private RemoteDevices mRemoteDevices;
    private int mRxTimeTotalMs;
    private int mStackReportedState;
    private int mTxTimeTotalMs;

    /* renamed from: com.android.bluetooth.btservice.AdapterService$1 */
    class C00051 extends Handler {
        C00051() {
        }

        public void handleMessage(Message msg) {
            AdapterService.this.debugLog("handleMessage() - Message: " + msg.what);
            switch (msg.what) {
                case 1:
                    AdapterService.this.debugLog("handleMessage() - MESSAGE_PROFILE_SERVICE_STATE_CHANGED");
                    AdapterService.this.processProfileServiceStateChanged((String) msg.obj, msg.arg1);
                    return;
                case 20:
                    AdapterService.this.debugLog("handleMessage() - MESSAGE_PROFILE_CONNECTION_STATE_CHANGED");
                    AdapterService.this.processProfileStateChanged((BluetoothDevice) msg.obj, msg.arg1, msg.arg2, msg.getData().getInt("prevState", Integer.MIN_VALUE));
                    return;
                case 30:
                    AdapterService.this.debugLog("handleMessage() - MESSAGE_CONNECT_OTHER_PROFILES");
                    AdapterService.this.processConnectOtherProfiles((BluetoothDevice) msg.obj, msg.arg1);
                    return;
                case AdapterService.MESSAGE_PROFILE_INIT_PRIORITIES /*40*/:
                    AdapterService.this.debugLog("handleMessage() - MESSAGE_PROFILE_INIT_PRIORITIES");
                    ParcelUuid[] mUuids = new ParcelUuid[msg.arg1];
                    for (int i = 0; i < mUuids.length; i++) {
                        mUuids[i] = (ParcelUuid) msg.getData().getParcelable("uuids" + i);
                    }
                    AdapterService.this.processInitProfilePriorities((BluetoothDevice) msg.obj, mUuids);
                    return;
                case 100:
                    AdapterService.this.debugLog("handleMessage() - MESSAGE_SET_WAKE_ALARM");
                    AdapterService.this.processSetWakeAlarm(((Long) msg.obj).longValue(), msg.arg1);
                    return;
                case AdapterService.MESSAGE_RELEASE_WAKE_ALARM /*110*/:
                    AdapterService.this.debugLog("handleMessage() - MESSAGE_RELEASE_WAKE_ALARM");
                    AdapterService.this.alarmFiredNative();
                    return;
                default:
                    return;
            }
        }
    }

    /* renamed from: com.android.bluetooth.btservice.AdapterService$2 */
    class C00062 extends BroadcastReceiver {
        C00062() {
        }

        public void onReceive(Context context, Intent intent) {
            AdapterService.this.mHandler.sendMessage(AdapterService.this.mHandler.obtainMessage(AdapterService.MESSAGE_RELEASE_WAKE_ALARM));
        }
    }

    private static class AdapterServiceBinder extends Stub {
        private AdapterService mService;

        public AdapterServiceBinder(AdapterService svc) {
            this.mService = svc;
        }

        public boolean cleanup() {
            this.mService = null;
            return true;
        }

        public AdapterService getService() {
            if (this.mService == null || !this.mService.isAvailable()) {
                return null;
            }
            return this.mService;
        }

        public boolean isEnabled() {
            AdapterService service = getService();
            if (service == null) {
                return AdapterService.DBG;
            }
            return service.isEnabled();
        }

        public boolean isRadioEnabled() {
            return AdapterService.DBG;
        }

        public int getState() {
            AdapterService service = getService();
            if (service == null) {
                return 10;
            }
            return service.getState();
        }

        public boolean enable() {
            if (Binder.getCallingUid() == Constants.MAX_RECORDS_IN_DATABASE || Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.enable();
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "enable() - Not allowed for non-active user and non system user");
            return AdapterService.DBG;
        }

        public boolean enableNoAutoConnect() {
            if (Binder.getCallingUid() == Constants.MAX_RECORDS_IN_DATABASE || Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.enableNoAutoConnect();
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "enableNoAuto() - Not allowed for non-active user and non system user");
            return AdapterService.DBG;
        }

        public boolean disable() {
            if (Binder.getCallingUid() == Constants.MAX_RECORDS_IN_DATABASE || Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.disable();
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "disable() - Not allowed for non-active user and non system user");
            return AdapterService.DBG;
        }

        public boolean enableRadio() {
            return AdapterService.DBG;
        }

        public boolean disableRadio() {
            return AdapterService.DBG;
        }

        public String getAddress() {
            if (Binder.getCallingUid() == Constants.MAX_RECORDS_IN_DATABASE || Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getAddress();
                }
                return null;
            }
            Log.w(AdapterService.TAG, "getAddress() - Not allowed for non-active user and non system user");
            return null;
        }

        public ParcelUuid[] getUuids() {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service == null) {
                    return new ParcelUuid[0];
                }
                return service.getUuids();
            }
            Log.w(AdapterService.TAG, "getUuids() - Not allowed for non-active user");
            return new ParcelUuid[0];
        }

        public String getName() {
            if (Binder.getCallingUid() == Constants.MAX_RECORDS_IN_DATABASE || Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getName();
                }
                return null;
            }
            Log.w(AdapterService.TAG, "getName() - Not allowed for non-active user and non system user");
            return null;
        }

        public boolean setName(String name) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.setName(name);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "setName() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public int getScanMode() {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getScanMode();
                }
                return 20;
            }
            Log.w(AdapterService.TAG, "getScanMode() - Not allowed for non-active user");
            return 20;
        }

        public boolean setScanMode(int mode, int duration) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.setScanMode(mode, duration);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "setScanMode() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public int getDiscoverableTimeout() {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getDiscoverableTimeout();
                }
                return 0;
            }
            Log.w(AdapterService.TAG, "getDiscoverableTimeout() - Not allowed for non-active user");
            return 0;
        }

        public boolean setDiscoverableTimeout(int timeout) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.setDiscoverableTimeout(timeout);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "setDiscoverableTimeout() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public boolean startDiscovery() {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.startDiscovery();
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "startDiscovery() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public boolean cancelDiscovery() {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.cancelDiscovery();
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "cancelDiscovery() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public boolean isDiscovering() {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.isDiscovering();
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "isDiscovering() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public BluetoothDevice[] getBondedDevices() {
            AdapterService service = getService();
            if (service == null) {
                return new BluetoothDevice[0];
            }
            return service.getBondedDevices();
        }

        public int getAdapterConnectionState() {
            AdapterService service = getService();
            if (service == null) {
                return 0;
            }
            return service.getAdapterConnectionState();
        }

        public int getProfileConnectionState(int profile) {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getProfileConnectionState(profile);
                }
                return 0;
            }
            Log.w(AdapterService.TAG, "getProfileConnectionState- Not allowed for non-active user");
            return 0;
        }

        public boolean createBond(BluetoothDevice device, int transport) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.createBond(device, transport);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "createBond() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public boolean cancelBondProcess(BluetoothDevice device) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.cancelBondProcess(device);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "cancelBondProcess() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public boolean removeBond(BluetoothDevice device) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.removeBond(device);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "removeBond() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public int getBondState(BluetoothDevice device) {
            AdapterService service = getService();
            if (service == null) {
                return 10;
            }
            return service.getBondState(device);
        }

        public int getConnectionState(BluetoothDevice device) {
            AdapterService service = getService();
            if (service == null) {
                return 0;
            }
            return service.getConnectionState(device);
        }

        public String getRemoteName(BluetoothDevice device) {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getRemoteName(device);
                }
                return null;
            }
            Log.w(AdapterService.TAG, "getRemoteName() - Not allowed for non-active user");
            return null;
        }

        public int getRemoteType(BluetoothDevice device) {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getRemoteType(device);
                }
                return 0;
            }
            Log.w(AdapterService.TAG, "getRemoteType() - Not allowed for non-active user");
            return 0;
        }

        public String getRemoteAlias(BluetoothDevice device) {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getRemoteAlias(device);
                }
                return null;
            }
            Log.w(AdapterService.TAG, "getRemoteAlias() - Not allowed for non-active user");
            return null;
        }

        public boolean setRemoteAlias(BluetoothDevice device, String name) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.setRemoteAlias(device, name);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "setRemoteAlias() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public int getRemoteClass(BluetoothDevice device) {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getRemoteClass(device);
                }
                return 0;
            }
            Log.w(AdapterService.TAG, "getRemoteClass() - Not allowed for non-active user");
            return 0;
        }

        public ParcelUuid[] getRemoteUuids(BluetoothDevice device) {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service == null) {
                    return new ParcelUuid[0];
                }
                return service.getRemoteUuids(device);
            }
            Log.w(AdapterService.TAG, "getRemoteUuids() - Not allowed for non-active user");
            return new ParcelUuid[0];
        }

        public boolean fetchRemoteUuids(BluetoothDevice device) {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.fetchRemoteUuids(device);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "fetchRemoteUuids() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public boolean fetchRemoteMasInstances(BluetoothDevice device) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.fetchRemoteMasInstances(device);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "fetchMasInstances(): not allowed for non-active user");
            return AdapterService.DBG;
        }

        public boolean setPin(BluetoothDevice device, boolean accept, int len, byte[] pinCode) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.setPin(device, accept, len, pinCode);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "setPin() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public boolean setPasskey(BluetoothDevice device, boolean accept, int len, byte[] passkey) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.setPasskey(device, accept, len, passkey);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "setPasskey() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public boolean setPairingConfirmation(BluetoothDevice device, boolean accept) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.setPairingConfirmation(device, accept);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "setPairingConfirmation() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public int getPhonebookAccessPermission(BluetoothDevice device) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getPhonebookAccessPermission(device);
                }
                return 0;
            }
            Log.w(AdapterService.TAG, "getPhonebookAccessPermission() - Not allowed for non-active user");
            return 0;
        }

        public boolean setPhonebookAccessPermission(BluetoothDevice device, int value) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.setPhonebookAccessPermission(device, value);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "setPhonebookAccessPermission() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public int getMessageAccessPermission(BluetoothDevice device) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.getMessageAccessPermission(device);
                }
                return 0;
            }
            Log.w(AdapterService.TAG, "getMessageAccessPermission() - Not allowed for non-active user");
            return 0;
        }

        public boolean setMessageAccessPermission(BluetoothDevice device, int value) {
            if (Utils.checkCaller()) {
                AdapterService service = getService();
                if (service != null) {
                    return service.setMessageAccessPermission(device, value);
                }
                return AdapterService.DBG;
            }
            Log.w(AdapterService.TAG, "setMessageAccessPermission() - Not allowed for non-active user");
            return AdapterService.DBG;
        }

        public void sendConnectionStateChange(BluetoothDevice device, int profile, int state, int prevState) {
            AdapterService service = getService();
            if (service != null) {
                service.sendConnectionStateChange(device, profile, state, prevState);
            }
        }

        public ParcelFileDescriptor connectSocket(BluetoothDevice device, int type, ParcelUuid uuid, int port, int flag) {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.connectSocket(device, type, uuid, port, flag);
                }
                return null;
            }
            Log.w(AdapterService.TAG, "connectSocket() - Not allowed for non-active user");
            return null;
        }

        public ParcelFileDescriptor createSocketChannel(int type, String serviceName, ParcelUuid uuid, int port, int flag) {
            if (Utils.checkCallerAllowManagedProfiles(this.mService)) {
                AdapterService service = getService();
                if (service != null) {
                    return service.createSocketChannel(type, serviceName, uuid, port, flag);
                }
                return null;
            }
            Log.w(AdapterService.TAG, "createSocketChannel() - Not allowed for non-active user");
            return null;
        }

        public boolean configHciSnoopLog(boolean enable) {
            if (Binder.getCallingUid() != Constants.MAX_RECORDS_IN_DATABASE) {
                EventLog.writeEvent(1397638484, new Object[]{"Bluetooth", Integer.valueOf(Binder.getCallingUid()), "configHciSnoopLog() - Not allowed for non-active user b/18643224"});
                return AdapterService.DBG;
            }
            AdapterService service = getService();
            if (service != null) {
                return service.configHciSnoopLog(enable);
            }
            return AdapterService.DBG;
        }

        public void registerCallback(IBluetoothCallback cb) {
            AdapterService service = getService();
            if (service != null) {
                service.registerCallback(cb);
            }
        }

        public void unregisterCallback(IBluetoothCallback cb) {
            AdapterService service = getService();
            if (service != null) {
                service.unregisterCallback(cb);
            }
        }

        public boolean isMultiAdvertisementSupported() {
            AdapterService service = getService();
            if (service == null) {
                return AdapterService.DBG;
            }
            return service.isMultiAdvertisementSupported();
        }

        public boolean isPeripheralModeSupported() {
            AdapterService service = getService();
            if (service == null) {
                return AdapterService.DBG;
            }
            return service.isPeripheralModeSupported();
        }

        public boolean isOffloadedFilteringSupported() {
            AdapterService service = getService();
            if (service != null && service.getNumOfOffloadedScanFilterSupported() >= 10) {
                return true;
            }
            return AdapterService.DBG;
        }

        public boolean isOffloadedScanBatchingSupported() {
            AdapterService service = getService();
            if (service != null && service.getOffloadedScanResultStorage() >= AdapterService.MIN_OFFLOADED_SCAN_STORAGE_BYTES) {
                return true;
            }
            return AdapterService.DBG;
        }

        public boolean isActivityAndEnergyReportingSupported() {
            AdapterService service = getService();
            if (service == null) {
                return AdapterService.DBG;
            }
            return service.isActivityAndEnergyReportingSupported();
        }

        public void getActivityEnergyInfoFromController() {
            AdapterService service = getService();
            if (service != null) {
                service.getActivityEnergyInfoFromController();
            }
        }

        public BluetoothActivityEnergyInfo reportActivityInfo() {
            AdapterService service = getService();
            if (service == null) {
                return null;
            }
            return service.reportActivityInfo();
        }

        public String dump() {
            AdapterService service = getService();
            if (service == null) {
                return "AdapterService is null";
            }
            return service.dump();
        }
    }

    class IniFile {
        private Pattern _keyValue = Pattern.compile("\\s*([^=]*)=(.*)");
        private Map<String, String> kvs = new HashMap();

        public IniFile(String path) {
            load(path);
        }

        public void load(String path) {
            Throwable th;
            BufferedReader br = null;
            try {
                BufferedReader br2 = new BufferedReader(new FileReader(path));
                while (true) {
                    try {
                        String line = br2.readLine();
                        if (line == null) {
                            break;
                        }
                        Matcher m = this._keyValue.matcher(line);
                        if (m.matches()) {
                            this.kvs.put(m.group(1).trim(), m.group(2).trim());
                        }
                    } catch (IOException e) {
                        br = br2;
                    } catch (Throwable th2) {
                        th = th2;
                        br = br2;
                    }
                }
                if (br2 != null) {
                    try {
                        br2.close();
                    } catch (IOException e2) {
                        br = br2;
                        return;
                    }
                }
                br = br2;
            } catch (IOException e3) {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException e4) {
                    }
                }
            } catch (Throwable th3) {
                th = th3;
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException e5) {
                    }
                }
                throw th;
            }
        }

        public String getString(String key, String defaultvalue) {
            String value = (String) this.kvs.get(key);
            return value == null ? defaultvalue : value;
        }

        public int getInt(String key, int defaultvalue) {
            String value = (String) this.kvs.get(key);
            return value == null ? defaultvalue : Integer.parseInt(value);
        }

        public float getFloat(String key, float defaultvalue) {
            String value = (String) this.kvs.get(key);
            return value == null ? defaultvalue : Float.parseFloat(value);
        }

        public double getDouble(String key, double defaultvalue) {
            String value = (String) this.kvs.get(key);
            return value == null ? defaultvalue : Double.parseDouble(value);
        }
    }

    private native void alarmFiredNative();

    private native boolean cancelDiscoveryNative();

    private static native void classInitNative();

    private native void cleanupNative();

    private native int connectSocketNative(byte[] bArr, int i, byte[] bArr2, int i2, int i3);

    private native int createSocketChannelNative(int i, String str, byte[] bArr, int i2, int i3);

    private native boolean initNative();

    private native boolean pinReplyNative(byte[] bArr, boolean z, int i, byte[] bArr2);

    private native int readEnergyInfo();

    private native boolean sspReplyNative(byte[] bArr, int i, boolean z, int i2);

    private native boolean startDiscoveryNative();

    native boolean cancelBondNative(byte[] bArr);

    native boolean configHciSnoopLogNative(boolean z);

    native boolean createBondNative(byte[] bArr, int i);

    native boolean disableNative();

    native boolean enableNative();

    native boolean getAdapterPropertiesNative();

    native boolean getAdapterPropertyNative(int i);

    native int getConnectionStateNative(byte[] bArr);

    native boolean getDevicePropertyNative(byte[] bArr, int i);

    native boolean getRemoteMasInstancesNative(byte[] bArr);

    native boolean getRemoteServicesNative(byte[] bArr);

    native boolean removeBondNative(byte[] bArr);

    native boolean setAdapterPropertyNative(int i);

    native boolean setAdapterPropertyNative(int i, byte[] bArr);

    native boolean setDevicePropertyNative(byte[] bArr, int i, byte[] bArr2);

    static {
        classInitNative();
    }

    public static synchronized AdapterService getAdapterService() {
        AdapterService adapterService;
        synchronized (AdapterService.class) {
            if (sAdapterService == null || sAdapterService.mCleaningUp) {
                adapterService = null;
            } else {
                Log.d(TAG, "getAdapterService() - returning " + sAdapterService);
                adapterService = sAdapterService;
            }
        }
        return adapterService;
    }

    private static synchronized void setAdapterService(AdapterService instance) {
        synchronized (AdapterService.class) {
            if (instance != null) {
                if (!instance.mCleaningUp) {
                    sAdapterService = instance;
                }
            }
        }
    }

    private static synchronized void clearAdapterService() {
        synchronized (AdapterService.class) {
            sAdapterService = null;
        }
    }

    public AdapterService() {
        synchronized (AdapterService.class) {
            sRefCount++;
            debugLog("AdapterService() - REFCOUNT: CREATED. INSTANCE_COUNT" + sRefCount);
        }
    }

    public void onProfileConnectionStateChanged(BluetoothDevice device, int profileId, int newState, int prevState) {
        Message m = this.mHandler.obtainMessage(20);
        m.obj = device;
        m.arg1 = profileId;
        m.arg2 = newState;
        Bundle b = new Bundle(1);
        b.putInt("prevState", prevState);
        m.setData(b);
        this.mHandler.sendMessage(m);
    }

    public void initProfilePriorities(BluetoothDevice device, ParcelUuid[] mUuids) {
        if (mUuids != null) {
            Message m = this.mHandler.obtainMessage(MESSAGE_PROFILE_INIT_PRIORITIES);
            m.obj = device;
            m.arg1 = mUuids.length;
            Bundle b = new Bundle(1);
            for (int i = 0; i < mUuids.length; i++) {
                b.putParcelable("uuids" + i, mUuids[i]);
            }
            m.setData(b);
            this.mHandler.sendMessage(m);
        }
    }

    private void processInitProfilePriorities(BluetoothDevice device, ParcelUuid[] uuids) {
        HidService hidService = HidService.getHidService();
        A2dpService a2dpService = A2dpService.getA2dpService();
        HeadsetService headsetService = HeadsetService.getHeadsetService();
        if (hidService != null && ((BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Hid) || BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Hogp)) && hidService.getPriority(device) == -1)) {
            hidService.setPriority(device, 100);
        }
        if (a2dpService != null && ((BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.AudioSink) || BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.AdvAudioDist)) && a2dpService.getPriority(device) == -1)) {
            a2dpService.setPriority(device, 100);
        }
        if (headsetService == null) {
            return;
        }
        if ((BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.HSP) || BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Handsfree)) && headsetService.getPriority(device) == -1) {
            headsetService.setPriority(device, 100);
        }
    }

    private void processProfileStateChanged(BluetoothDevice device, int profileId, int newState, int prevState) {
        if ((profileId == 2 || profileId == 1) && newState == 2) {
            debugLog("Profile connected. Schedule missing profile connection if any");
            connectOtherProfile(device, 1);
            setProfileAutoConnectionPriority(device, profileId);
        }
        Stub binder = this.mBinder;
        if (binder != null) {
            try {
                binder.sendConnectionStateChange(device, profileId, newState, prevState);
            } catch (RemoteException re) {
                errorLog("" + re);
            }
        }
    }

    public void addProfile(ProfileService profile) {
        synchronized (this.mProfiles) {
            this.mProfiles.add(profile);
        }
    }

    public void removeProfile(ProfileService profile) {
        synchronized (this.mProfiles) {
            this.mProfiles.remove(profile);
        }
    }

    public void onProfileServiceStateChanged(String serviceName, int state) {
        Message m = this.mHandler.obtainMessage(1);
        m.obj = serviceName;
        m.arg1 = state;
        this.mHandler.sendMessage(m);
    }

    private void processProfileServiceStateChanged(String serviceName, int state) {
        boolean doUpdate = DBG;
        synchronized (this.mProfileServicesState) {
            Integer prevState = (Integer) this.mProfileServicesState.get(serviceName);
            if (!(prevState == null || prevState.intValue() == state)) {
                this.mProfileServicesState.put(serviceName, Integer.valueOf(state));
                doUpdate = true;
            }
        }
        debugLog("onProfileServiceStateChange() serviceName=" + serviceName + ", state=" + state + ", doUpdate=" + doUpdate);
        if (doUpdate) {
            boolean isTurningOff;
            boolean isTurningOn;
            synchronized (this.mAdapterStateMachine) {
                isTurningOff = this.mAdapterStateMachine.isTurningOff();
                isTurningOn = this.mAdapterStateMachine.isTurningOn();
            }
            if (isTurningOff) {
                synchronized (this.mProfileServicesState) {
                    for (Entry<String, Integer> entry : this.mProfileServicesState.entrySet()) {
                        if (10 != ((Integer) entry.getValue()).intValue()) {
                            debugLog("onProfileServiceStateChange() - Profile still running: " + ((String) entry.getKey()));
                            return;
                        }
                    }
                    debugLog("onProfileServiceStateChange() - All profile services stopped...");
                    this.mProfilesStarted = DBG;
                    this.mAdapterStateMachine.sendMessage(this.mAdapterStateMachine.obtainMessage(25));
                }
            } else if (isTurningOn) {
                synchronized (this.mProfileServicesState) {
                    for (Entry<String, Integer> entry2 : this.mProfileServicesState.entrySet()) {
                        if (12 != ((Integer) entry2.getValue()).intValue()) {
                            debugLog("onProfileServiceStateChange() - Profile still not running:" + ((String) entry2.getKey()));
                            return;
                        }
                    }
                    debugLog("onProfileServiceStateChange() - All profile services started.");
                    this.mProfilesStarted = true;
                    this.mAdapterStateMachine.sendMessage(this.mAdapterStateMachine.obtainMessage(2));
                }
            }
        }
    }

    public void onCreate() {
        super.onCreate();
        debugLog("onCreate()");
        this.mBinder = new AdapterServiceBinder(this);
        this.mAdapterProperties = new AdapterProperties(this);
        this.mAdapterStateMachine = AdapterState.make(this, this.mAdapterProperties);
        this.mJniCallbacks = new JniCallbacks(this.mAdapterStateMachine, this.mAdapterProperties);
        initNative();
        this.mNativeAvailable = true;
        this.mCallbacks = new RemoteCallbackList();
        getAdapterPropertyNative(2);
        getAdapterPropertyNative(1);
        registerReceiver(this.mAlarmBroadcastReceiver, new IntentFilter(ACTION_ALARM_WAKEUP));
    }

    public IBinder onBind(Intent intent) {
        debugLog("onBind()");
        return this.mBinder;
    }

    public boolean onUnbind(Intent intent) {
        debugLog("onUnbind() - calling cleanup");
        cleanup();
        return super.onUnbind(intent);
    }

    public void onDestroy() {
        debugLog("onDestroy()");
    }

    void processStart() {
        debugLog("processStart()");
        Class[] supportedProfileServices = Config.getSupportedProfiles();
        for (Class name : supportedProfileServices) {
            this.mProfileServicesState.put(name.getName(), Integer.valueOf(10));
        }
        this.mRemoteDevices = new RemoteDevices(this);
        this.mAdapterProperties.init(this.mRemoteDevices);
        debugLog("processStart() - Make Bond State Machine");
        this.mBondStateMachine = BondStateMachine.make(this, this.mAdapterProperties, this.mRemoteDevices);
        this.mJniCallbacks.init(this.mBondStateMachine, this.mRemoteDevices);
        setAdapterService(this);
        this.mAdapterStateMachine.sendMessage(this.mAdapterStateMachine.obtainMessage(2));
    }

    void startBluetoothDisable() {
        this.mAdapterStateMachine.sendMessage(this.mAdapterStateMachine.obtainMessage(21));
    }

    boolean stopProfileServices() {
        Class[] supportedProfileServices = Config.getSupportedProfiles();
        if (!this.mProfilesStarted || supportedProfileServices.length <= 0) {
            debugLog("stopProfileServices() - No profiles services to stop or already stopped.");
            return DBG;
        }
        setProfileServiceState(supportedProfileServices, 10);
        return true;
    }

    void updateAdapterState(int prevState, int newState) {
        if (this.mCallbacks != null) {
            int n = this.mCallbacks.beginBroadcast();
            debugLog("updateAdapterState() - Broadcasting state to " + n + " receivers.");
            for (int i = 0; i < n; i++) {
                try {
                    ((IBluetoothCallback) this.mCallbacks.getBroadcastItem(i)).onBluetoothStateChange(prevState, newState);
                } catch (RemoteException e) {
                    debugLog("updateAdapterState() - Callback #" + i + " failed (" + e + ")");
                }
            }
            this.mCallbacks.finishBroadcast();
        }
    }

    void cleanup() {
        debugLog("cleanup()");
        if (this.mCleaningUp) {
            errorLog("cleanup() - Service already starting to cleanup, ignoring request...");
            return;
        }
        this.mCleaningUp = true;
        unregisterReceiver(this.mAlarmBroadcastReceiver);
        synchronized (this) {
        }
        if (this.mAdapterStateMachine != null) {
            this.mAdapterStateMachine.doQuit();
            this.mAdapterStateMachine.cleanup();
        }
        if (this.mBondStateMachine != null) {
            this.mBondStateMachine.doQuit();
            this.mBondStateMachine.cleanup();
        }
        if (this.mRemoteDevices != null) {
            this.mRemoteDevices.cleanup();
        }
        if (this.mNativeAvailable) {
            debugLog("cleanup() - Cleaning up adapter native");
            cleanupNative();
            this.mNativeAvailable = DBG;
        }
        if (this.mAdapterProperties != null) {
            this.mAdapterProperties.cleanup();
        }
        if (this.mJniCallbacks != null) {
            this.mJniCallbacks.cleanup();
        }
        if (this.mProfileServicesState != null) {
            this.mProfileServicesState.clear();
        }
        clearAdapterService();
        if (this.mBinder != null) {
            this.mBinder.cleanup();
            this.mBinder = null;
        }
        if (this.mCallbacks != null) {
            this.mCallbacks.kill();
        }
        debugLog("cleanup() - Bluetooth process exited normally.");
        System.exit(0);
    }

    private void setProfileServiceState(Class[] services, int state) {
        if (state == 12 || state == 10) {
            int expectedCurrentState = 10;
            int pendingState = 11;
            if (state == 10) {
                expectedCurrentState = 12;
                pendingState = 13;
            }
            for (int i = 0; i < services.length; i++) {
                String serviceName = services[i].getName();
                Integer serviceState = (Integer) this.mProfileServicesState.get(serviceName);
                if (serviceState == null || serviceState.intValue() == expectedCurrentState) {
                    debugLog("setProfileServiceState() - " + (state == 10 ? "Stopping" : "Starting") + " service " + serviceName);
                    this.mProfileServicesState.put(serviceName, Integer.valueOf(pendingState));
                    Intent intent = new Intent(this, services[i]);
                    intent.putExtra(EXTRA_ACTION, ACTION_SERVICE_STATE_CHANGED);
                    intent.putExtra("android.bluetooth.adapter.extra.STATE", state);
                    startService(intent);
                } else {
                    debugLog("setProfileServiceState() - Unable to " + (state == 10 ? "start" : "stop") + " service " + serviceName + ". Invalid state: " + serviceState);
                }
            }
            return;
        }
        debugLog("setProfileServiceState() - Invalid state, leaving...");
    }

    private boolean isAvailable() {
        return !this.mCleaningUp ? true : DBG;
    }

    boolean isEnabled() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return true;
    }

    int getState() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return 12;
    }

    boolean enable() {
        return enable(DBG);
    }

    public boolean enableNoAutoConnect() {
        return enable(true);
    }

    public synchronized boolean enable(boolean quietMode) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN", "Need BLUETOOTH ADMIN permission");
        debugLog("enable() - Enable called with quiet mode status =  " + this.mQuietmode);
        this.mQuietmode = quietMode;
        this.mAdapterStateMachine.sendMessage(this.mAdapterStateMachine.obtainMessage(1));
        return true;
    }

    boolean disable() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN", "Need BLUETOOTH ADMIN permission");
        debugLog("disable() called...");
        this.mAdapterStateMachine.sendMessage(this.mAdapterStateMachine.obtainMessage(20));
        return true;
    }

    String getAddress() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return Utils.getAddressStringFromByte(this.mAdapterProperties.getAddress());
    }

    ParcelUuid[] getUuids() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.getUuids();
    }

    String getName() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        try {
            return this.mAdapterProperties.getName();
        } catch (Throwable t) {
            debugLog("getName() - Unexpected exception (" + t + ")");
            return null;
        }
    }

    boolean setName(String name) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN", "Need BLUETOOTH ADMIN permission");
        return this.mAdapterProperties.setName(name);
    }

    int getScanMode() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.getScanMode();
    }

    boolean setScanMode(int mode, int duration) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        setDiscoverableTimeout(duration);
        return this.mAdapterProperties.setScanMode(convertScanModeToHal(mode));
    }

    int getDiscoverableTimeout() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.getDiscoverableTimeout();
    }

    boolean setDiscoverableTimeout(int timeout) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.setDiscoverableTimeout(timeout);
    }

    boolean startDiscovery() {
        this.discoverying = true;
        sendBroadcast(new Intent("android.bluetooth.adapter.action.DISCOVERY_STARTED"));
        for (BluetoothDevice device : getBondedDevices()) {
            Intent deviceIntent = new Intent("android.bluetooth.device.action.FOUND");
            deviceIntent.putExtra("android.bluetooth.device.extra.DEVICE", device);
            sendBroadcast(deviceIntent);
        }
        sendBroadcast(new Intent("android.bluetooth.adapter.action.DISCOVERY_FINISHED"));
        this.discoverying = DBG;
        return true;
    }

    boolean cancelDiscovery() {
        sendBroadcast(new Intent("android.bluetooth.adapter.action.DISCOVERY_FINISHED"));
        this.discoverying = DBG;
        return true;
    }

    boolean isDiscovering() {
        return this.discoverying;
    }

    private String addrConv(String addr) {
        String newAddr = "";
        for (int i = 0; i < 6; i++) {
            newAddr = newAddr + addr.substring(i * 2, (i * 2) + 2);
            if (i != 5) {
                newAddr = newAddr + ":";
            }
        }
        return newAddr;
    }

    BluetoothDevice[] getBondedDevices() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        debugLog("Get Bonded Devices being called");
        this.devices.clear();
        IniFile ini = new IniFile("/data/goc/bt_conf.ini");
        int num = ini.getInt("pairlisttotal", 0);
        if (num == 0) {
            return new BluetoothDevice[0];
        }
        for (int i = 0; i < num; i++) {
            String addr = ini.getString("addr" + i, null);
            if (addr != null && addr.length() == 12) {
                this.devices.put(addrConv(addr), ini.getString("name" + i, "noName"));
            }
        }
        ArrayList<BluetoothDevice> result = new ArrayList();
        for (String addr2 : this.devices.keySet()) {
            result.add(new BluetoothDevice(addr2));
        }
        return (BluetoothDevice[]) result.toArray(new BluetoothDevice[0]);
    }

    int getAdapterConnectionState() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.getConnectionState();
    }

    int getProfileConnectionState(int profile) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.getProfileConnectionState(profile);
    }

    boolean createBond(BluetoothDevice device, int transport) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN", "Need BLUETOOTH ADMIN permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp != null && deviceProp.getBondState() != 10) {
            return DBG;
        }
        cancelDiscoveryNative();
        Message msg = this.mBondStateMachine.obtainMessage(1);
        msg.obj = device;
        msg.arg1 = transport;
        this.mBondStateMachine.sendMessage(msg);
        return true;
    }

    public boolean isQuietModeEnabled() {
        debugLog("isQuetModeEnabled() - Enabled = " + this.mQuietmode);
        return this.mQuietmode;
    }

    public void autoConnect() {
        if (getState() != 12) {
            errorLog("autoConnect() - BT is not ON. Exiting autoConnect");
        } else if (isQuietModeEnabled()) {
            debugLog("autoConnect() - BT is in quiet mode. Not initiating auto connections");
        } else {
            debugLog("autoConnect() - Initiate auto connection on BT on...");
            autoConnectHeadset();
            autoConnectA2dp();
        }
    }

    private void autoConnectHeadset() {
        HeadsetService hsService = HeadsetService.getHeadsetService();
        BluetoothDevice[] bondedDevices = getBondedDevices();
        if (bondedDevices != null && hsService != null) {
            for (BluetoothDevice device : bondedDevices) {
                if (hsService.getPriority(device) == Constants.MAX_RECORDS_IN_DATABASE) {
                    debugLog("autoConnectHeadset() - Connecting HFP with " + device.toString());
                    hsService.connect(device);
                }
            }
        }
    }

    private void autoConnectA2dp() {
        A2dpService a2dpSservice = A2dpService.getA2dpService();
        BluetoothDevice[] bondedDevices = getBondedDevices();
        if (bondedDevices != null && a2dpSservice != null) {
            for (BluetoothDevice device : bondedDevices) {
                if (a2dpSservice.getPriority(device) == Constants.MAX_RECORDS_IN_DATABASE) {
                    debugLog("autoConnectA2dp() - Connecting A2DP with " + device.toString());
                    a2dpSservice.connect(device);
                }
            }
        }
    }

    public void connectOtherProfile(BluetoothDevice device, int firstProfileStatus) {
        if (!this.mHandler.hasMessages(30) && !isQuietModeEnabled()) {
            Message m = this.mHandler.obtainMessage(30);
            m.obj = device;
            m.arg1 = firstProfileStatus;
            this.mHandler.sendMessageDelayed(m, 6000);
        }
    }

    private void processConnectOtherProfiles(BluetoothDevice device, int firstProfileStatus) {
        if (getState() == 12) {
            HeadsetService hsService = HeadsetService.getHeadsetService();
            A2dpService a2dpService = A2dpService.getA2dpService();
            if (hsService != null && a2dpService != null) {
                List<BluetoothDevice> a2dpConnDevList = a2dpService.getConnectedDevices();
                List<BluetoothDevice> hfConnDevList = hsService.getConnectedDevices();
                if (!hfConnDevList.isEmpty() || !a2dpConnDevList.isEmpty() || 1 != firstProfileStatus) {
                    if (hfConnDevList.isEmpty() && hsService.getPriority(device) >= 100) {
                        hsService.connect(device);
                    } else if (a2dpConnDevList.isEmpty() && a2dpService.getPriority(device) >= 100) {
                        a2dpService.connect(device);
                    }
                }
            }
        }
    }

    private void adjustOtherHeadsetPriorities(HeadsetService hsService, List<BluetoothDevice> connectedDeviceList) {
        for (BluetoothDevice device : getBondedDevices()) {
            if (hsService.getPriority(device) >= Constants.MAX_RECORDS_IN_DATABASE && !connectedDeviceList.contains(device)) {
                hsService.setPriority(device, 100);
            }
        }
    }

    private void adjustOtherSinkPriorities(A2dpService a2dpService, BluetoothDevice connectedDevice) {
        for (BluetoothDevice device : getBondedDevices()) {
            if (a2dpService.getPriority(device) >= Constants.MAX_RECORDS_IN_DATABASE && !device.equals(connectedDevice)) {
                a2dpService.setPriority(device, 100);
            }
        }
    }

    void setProfileAutoConnectionPriority(BluetoothDevice device, int profileId) {
        if (profileId == 1) {
            HeadsetService hsService = HeadsetService.getHeadsetService();
            List<BluetoothDevice> deviceList = hsService.getConnectedDevices();
            if (hsService != null && Constants.MAX_RECORDS_IN_DATABASE != hsService.getPriority(device)) {
                adjustOtherHeadsetPriorities(hsService, deviceList);
                hsService.setPriority(device, Constants.MAX_RECORDS_IN_DATABASE);
            }
        } else if (profileId == 2) {
            A2dpService a2dpService = A2dpService.getA2dpService();
            if (a2dpService != null && Constants.MAX_RECORDS_IN_DATABASE != a2dpService.getPriority(device)) {
                adjustOtherSinkPriorities(a2dpService, device);
                a2dpService.setPriority(device, Constants.MAX_RECORDS_IN_DATABASE);
            }
        }
    }

    boolean cancelBondProcess(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN", "Need BLUETOOTH ADMIN permission");
        return cancelBondNative(Utils.getBytesFromAddress(device.getAddress()));
    }

    boolean removeBond(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN", "Need BLUETOOTH ADMIN permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null || deviceProp.getBondState() != 12) {
            return DBG;
        }
        Message msg = this.mBondStateMachine.obtainMessage(3);
        msg.obj = device;
        this.mBondStateMachine.sendMessage(msg);
        return true;
    }

    int getBondState(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return 10;
        }
        return deviceProp.getBondState();
    }

    int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return getConnectionStateNative(Utils.getBytesFromAddress(device.getAddress()));
    }

    String getRemoteName(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        if (this.devices.containsKey(device.getAddress())) {
            return (String) this.devices.get(device.getAddress());
        }
        return "noName";
    }

    int getRemoteType(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return 0;
        }
        return deviceProp.getDeviceType();
    }

    String getRemoteAlias(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return null;
        }
        return deviceProp.getAlias();
    }

    boolean setRemoteAlias(BluetoothDevice device, String name) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return DBG;
        }
        deviceProp.setAlias(name);
        return true;
    }

    int getRemoteClass(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return 0;
        }
        return deviceProp.getBluetoothClass();
    }

    ParcelUuid[] getRemoteUuids(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return null;
        }
        return deviceProp.getUuids();
    }

    boolean fetchRemoteUuids(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        this.mRemoteDevices.fetchUuids(device);
        return true;
    }

    boolean fetchRemoteMasInstances(BluetoothDevice device) {
        enforceCallingOrSelfPermission(RECEIVE_MAP_PERM, "Need RECEIVE BLUETOOTH MAP permission");
        this.mRemoteDevices.fetchMasInstances(device);
        return true;
    }

    boolean setPin(BluetoothDevice device, boolean accept, int len, byte[] pinCode) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN", "Need BLUETOOTH ADMIN permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null || deviceProp.getBondState() != 11) {
            return DBG;
        }
        return pinReplyNative(Utils.getBytesFromAddress(device.getAddress()), accept, len, pinCode);
    }

    boolean setPasskey(BluetoothDevice device, boolean accept, int len, byte[] passkey) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null || deviceProp.getBondState() != 11) {
            return DBG;
        }
        return sspReplyNative(Utils.getBytesFromAddress(device.getAddress()), 1, accept, Utils.byteArrayToInt(passkey));
    }

    boolean setPairingConfirmation(BluetoothDevice device, boolean accept) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN", "Need BLUETOOTH ADMIN permission");
        DeviceProperties deviceProp = this.mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null || deviceProp.getBondState() != 11) {
            return DBG;
        }
        return sspReplyNative(Utils.getBytesFromAddress(device.getAddress()), 0, accept, 0);
    }

    int getPhonebookAccessPermission(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        SharedPreferences pref = getSharedPreferences(PHONEBOOK_ACCESS_PERMISSION_PREFERENCE_FILE, 0);
        if (pref.contains(device.getAddress())) {
            return pref.getBoolean(device.getAddress(), DBG) ? 1 : 2;
        } else {
            return 0;
        }
    }

    boolean setPhonebookAccessPermission(BluetoothDevice device, int value) {
        boolean z = true;
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_PRIVILEGED", "Need BLUETOOTH PRIVILEGED permission");
        Editor editor = getSharedPreferences(PHONEBOOK_ACCESS_PERMISSION_PREFERENCE_FILE, 0).edit();
        if (value == 0) {
            editor.remove(device.getAddress());
        } else {
            String address = device.getAddress();
            if (value != 1) {
                z = DBG;
            }
            editor.putBoolean(address, z);
        }
        return editor.commit();
    }

    int getMessageAccessPermission(BluetoothDevice device) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        SharedPreferences pref = getSharedPreferences(MESSAGE_ACCESS_PERMISSION_PREFERENCE_FILE, 0);
        if (pref.contains(device.getAddress())) {
            return pref.getBoolean(device.getAddress(), DBG) ? 1 : 2;
        } else {
            return 0;
        }
    }

    boolean setMessageAccessPermission(BluetoothDevice device, int value) {
        boolean z = true;
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_PRIVILEGED", "Need BLUETOOTH PRIVILEGED permission");
        Editor editor = getSharedPreferences(MESSAGE_ACCESS_PERMISSION_PREFERENCE_FILE, 0).edit();
        if (value == 0) {
            editor.remove(device.getAddress());
        } else {
            String address = device.getAddress();
            if (value != 1) {
                z = DBG;
            }
            editor.putBoolean(address, z);
        }
        return editor.commit();
    }

    void sendConnectionStateChange(BluetoothDevice device, int profile, int state, int prevState) {
        if (getState() != 10) {
            this.mAdapterProperties.sendConnectionStateChange(device, profile, state, prevState);
        }
    }

    ParcelFileDescriptor connectSocket(BluetoothDevice device, int type, ParcelUuid uuid, int port, int flag) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        int fd = connectSocketNative(Utils.getBytesFromAddress(device.getAddress()), type, Utils.uuidToByteArray(uuid), port, flag);
        if (fd >= 0) {
            return ParcelFileDescriptor.adoptFd(fd);
        }
        errorLog("Failed to connect socket");
        return null;
    }

    ParcelFileDescriptor createSocketChannel(int type, String serviceName, ParcelUuid uuid, int port, int flag) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        int fd = createSocketChannelNative(type, serviceName, Utils.uuidToByteArray(uuid), port, flag);
        if (fd >= 0) {
            return ParcelFileDescriptor.adoptFd(fd);
        }
        errorLog("Failed to create socket channel");
        return null;
    }

    boolean configHciSnoopLog(boolean enable) {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return configHciSnoopLogNative(enable);
    }

    void registerCallback(IBluetoothCallback cb) {
        this.mCallbacks.register(cb);
    }

    void unregisterCallback(IBluetoothCallback cb) {
        this.mCallbacks.unregister(cb);
    }

    public int getNumOfAdvertisementInstancesSupported() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.getNumOfAdvertisementInstancesSupported();
    }

    public boolean isMultiAdvertisementSupported() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return getNumOfAdvertisementInstancesSupported() >= 5 ? true : DBG;
    }

    public boolean isRpaOffloadSupported() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.isRpaOffloadSupported();
    }

    public int getNumOfOffloadedIrkSupported() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.getNumOfOffloadedIrkSupported();
    }

    public int getNumOfOffloadedScanFilterSupported() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.getNumOfOffloadedScanFilterSupported();
    }

    public boolean isPeripheralModeSupported() {
        return getResources().getBoolean(17956941);
    }

    public int getOffloadedScanResultStorage() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
        return this.mAdapterProperties.getOffloadedScanResultStorage();
    }

    private boolean isActivityAndEnergyReportingSupported() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_PRIVILEGED", "Need BLUETOOTH permission");
        return this.mAdapterProperties.isActivityAndEnergyReportingSupported();
    }

    private void getActivityEnergyInfoFromController() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_PRIVILEGED", "Need BLUETOOTH permission");
        if (isActivityAndEnergyReportingSupported()) {
            readEnergyInfo();
        }
    }

    private BluetoothActivityEnergyInfo reportActivityInfo() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_PRIVILEGED", "Need BLUETOOTH permission");
        BluetoothActivityEnergyInfo info = new BluetoothActivityEnergyInfo(this.mStackReportedState, this.mTxTimeTotalMs, this.mRxTimeTotalMs, this.mIdleTimeTotalMs, this.mEnergyUsedTotalVoltAmpSecMicro);
        this.mStackReportedState = 0;
        this.mTxTimeTotalMs = 0;
        this.mRxTimeTotalMs = 0;
        this.mIdleTimeTotalMs = 0;
        this.mEnergyUsedTotalVoltAmpSecMicro = 0;
        return info;
    }

    private String dump() {
        StringBuilder sb = new StringBuilder();
        synchronized (this.mProfiles) {
            Iterator i$ = this.mProfiles.iterator();
            while (i$.hasNext()) {
                ((ProfileService) i$.next()).dump(sb);
            }
        }
        return sb.toString();
    }

    private static int convertScanModeToHal(int mode) {
        switch (mode) {
            case 20:
                return 0;
            case BluetoothCmeError.INVALID_INDEX /*21*/:
                return 1;
            case BluetoothCmeError.MEMORY_FAILURE /*23*/:
                return 2;
            default:
                return -1;
        }
    }

    static int convertScanModeFromHal(int mode) {
        switch (mode) {
            case 0:
                return 20;
            case 1:
                return 21;
            case 2:
                return 23;
            default:
                return -1;
        }
    }

    private boolean setWakeAlarm(long delayMillis, boolean shouldWake) {
        Message m = this.mHandler.obtainMessage(100);
        m.obj = new Long(delayMillis);
        m.arg1 = shouldWake ? 2 : 3;
        this.mHandler.sendMessage(m);
        return true;
    }

    private void processSetWakeAlarm(long delayMillis, int alarmType) {
        long wakeupTime = SystemClock.elapsedRealtime() + delayMillis;
        Intent intent = new Intent(ACTION_ALARM_WAKEUP);
    }

    private boolean acquireWakeLock(String lockName) {
        return true;
    }

    private boolean releaseWakeLock(String lockName) {
        synchronized (this) {
        }
        return true;
    }

    private void energyInfoCallback(int status, int ctrl_state, long tx_time, long rx_time, long idle_time, long energy_used) throws RemoteException {
        if (ctrl_state >= 0 && ctrl_state <= 3) {
            this.mStackReportedState = ctrl_state;
            this.mTxTimeTotalMs = (int) (((long) this.mTxTimeTotalMs) + tx_time);
            this.mRxTimeTotalMs = (int) (((long) this.mRxTimeTotalMs) + rx_time);
            this.mIdleTimeTotalMs = (int) (((long) this.mIdleTimeTotalMs) + idle_time);
            this.mEnergyUsedTotalVoltAmpSecMicro = (int) (((long) this.mEnergyUsedTotalVoltAmpSecMicro) + energy_used);
        }
    }

    private void debugLog(String msg) {
    }

    private void errorLog(String msg) {
        Log.e("BluetoothAdapterService(" + hashCode() + ")", msg);
    }

    protected void finalize() {
        cleanup();
        synchronized (AdapterService.class) {
            sRefCount--;
            debugLog("finalize() - REFCOUNT: FINALIZED. INSTANCE_COUNT= " + sRefCount);
        }
    }
}
