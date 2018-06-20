package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

final class A2dpSinkStateMachine extends StateMachine {
    static final int AUDIO_STATE_REMOTE_SUSPEND = 0;
    static final int AUDIO_STATE_STARTED = 2;
    static final int AUDIO_STATE_STOPPED = 1;
    static final int CONNECT = 1;
    static final int CONNECTION_STATE_CONNECTED = 2;
    static final int CONNECTION_STATE_CONNECTING = 1;
    static final int CONNECTION_STATE_DISCONNECTED = 0;
    static final int CONNECTION_STATE_DISCONNECTING = 3;
    private static final int CONNECT_TIMEOUT = 201;
    private static final boolean DBG = false;
    static final int DISCONNECT = 2;
    private static final int EVENT_TYPE_AUDIO_CONFIG_CHANGED = 3;
    private static final int EVENT_TYPE_AUDIO_STATE_CHANGED = 2;
    private static final int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    private static final int EVENT_TYPE_NONE = 0;
    private static final int MSG_CONNECTION_STATE_CHANGED = 0;
    private static final int STACK_EVENT = 101;
    private BluetoothAdapter mAdapter;
    private final HashMap<BluetoothDevice, BluetoothAudioConfig> mAudioConfigs = new HashMap();
    private final AudioManager mAudioManager;
    private Connected mConnected;
    private Context mContext;
    private BluetoothDevice mCurrentDevice = null;
    private Disconnected mDisconnected;
    private BluetoothDevice mIncomingDevice = null;
    private IntentBroadcastHandler mIntentBroadcastHandler;
    private Pending mPending;
    private A2dpSinkService mService;
    private BluetoothDevice mTargetDevice = null;
    private final WakeLock mWakeLock;

    private class Connected extends State {
        private Connected() {
        }

        public void enter() {
            A2dpSinkStateMachine.this.log("Enter Connected: " + A2dpSinkStateMachine.this.getCurrentMessage().what);
            A2dpSinkStateMachine.this.broadcastAudioState(A2dpSinkStateMachine.this.mCurrentDevice, 11, 10);
        }

        public boolean processMessage(Message message) {
            A2dpSinkStateMachine.this.log("Connected process message: " + message.what);
            if (A2dpSinkStateMachine.this.mCurrentDevice == null) {
                A2dpSinkStateMachine.this.loge("ERROR: mCurrentDevice is null in Connected");
                return A2dpSinkStateMachine.DBG;
            }
            BluetoothDevice device;
            switch (message.what) {
                case 1:
                    device = message.obj;
                    if (A2dpSinkStateMachine.this.mCurrentDevice.equals(device)) {
                        return true;
                    }
                    A2dpSinkStateMachine.this.broadcastConnectionState(device, 1, 0);
                    if (A2dpSinkStateMachine.this.disconnectA2dpNative(A2dpSinkStateMachine.this.getByteAddress(A2dpSinkStateMachine.this.mCurrentDevice))) {
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mTargetDevice = device;
                            A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mPending);
                        }
                        return true;
                    }
                    A2dpSinkStateMachine.this.broadcastConnectionState(device, 0, 1);
                    return true;
                case 2:
                    device = (BluetoothDevice) message.obj;
                    if (!A2dpSinkStateMachine.this.mCurrentDevice.equals(device)) {
                        return true;
                    }
                    A2dpSinkStateMachine.this.broadcastConnectionState(device, 3, 2);
                    if (A2dpSinkStateMachine.this.disconnectA2dpNative(A2dpSinkStateMachine.this.getByteAddress(device))) {
                        A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mPending);
                        return true;
                    }
                    A2dpSinkStateMachine.this.broadcastConnectionState(device, 2, 0);
                    return true;
                case A2dpSinkStateMachine.STACK_EVENT /*101*/:
                    StackEvent event = message.obj;
                    switch (event.type) {
                        case 1:
                            processConnectionEvent(event.valueInt, event.device);
                            return true;
                        case 2:
                            processAudioStateEvent(event.valueInt, event.device);
                            return true;
                        case 3:
                            A2dpSinkStateMachine.this.processAudioConfigEvent(event.audioConfig, event.device);
                            return true;
                        default:
                            A2dpSinkStateMachine.this.loge("Unexpected stack event: " + event.type);
                            return true;
                    }
                default:
                    return A2dpSinkStateMachine.DBG;
            }
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case 0:
                    A2dpSinkStateMachine.this.mAudioConfigs.remove(device);
                    if (A2dpSinkStateMachine.this.mCurrentDevice.equals(device)) {
                        A2dpSinkStateMachine.this.broadcastConnectionState(A2dpSinkStateMachine.this.mCurrentDevice, 0, 2);
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mCurrentDevice = null;
                            A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mDisconnected);
                        }
                        return;
                    }
                    A2dpSinkStateMachine.this.loge("Disconnected from unknown device: " + device);
                    return;
                default:
                    A2dpSinkStateMachine.this.loge("Connection State Device: " + device + " bad state: " + state);
                    return;
            }
        }

        private void processAudioStateEvent(int state, BluetoothDevice device) {
            if (A2dpSinkStateMachine.this.mCurrentDevice.equals(device)) {
                switch (state) {
                    case 0:
                    case 1:
                        A2dpSinkStateMachine.this.broadcastAudioState(device, 11, 10);
                        return;
                    case 2:
                        A2dpSinkStateMachine.this.broadcastAudioState(device, 10, 11);
                        return;
                    default:
                        A2dpSinkStateMachine.this.loge("Audio State Device: " + device + " bad state: " + state);
                        return;
                }
            }
            A2dpSinkStateMachine.this.loge("Audio State Device:" + device + "is different from ConnectedDevice:" + A2dpSinkStateMachine.this.mCurrentDevice);
        }
    }

    private class Disconnected extends State {
        private Disconnected() {
        }

        public void enter() {
            A2dpSinkStateMachine.this.log("Enter Disconnected: " + A2dpSinkStateMachine.this.getCurrentMessage().what);
        }

        public boolean processMessage(Message message) {
            A2dpSinkStateMachine.this.log("Disconnected process message: " + message.what);
            if (A2dpSinkStateMachine.this.mCurrentDevice == null && A2dpSinkStateMachine.this.mTargetDevice == null && A2dpSinkStateMachine.this.mIncomingDevice == null) {
                switch (message.what) {
                    case 1:
                        BluetoothDevice device = message.obj;
                        A2dpSinkStateMachine.this.broadcastConnectionState(device, 1, 0);
                        if (A2dpSinkStateMachine.this.connectA2dpNative(A2dpSinkStateMachine.this.getByteAddress(device))) {
                            synchronized (A2dpSinkStateMachine.this) {
                                A2dpSinkStateMachine.this.mTargetDevice = device;
                                A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mPending);
                            }
                            A2dpSinkStateMachine.this.sendMessageDelayed(A2dpSinkStateMachine.CONNECT_TIMEOUT, 30000);
                            return true;
                        }
                        A2dpSinkStateMachine.this.broadcastConnectionState(device, 0, 1);
                        return true;
                    case 2:
                        return true;
                    case A2dpSinkStateMachine.STACK_EVENT /*101*/:
                        StackEvent event = message.obj;
                        switch (event.type) {
                            case 1:
                                processConnectionEvent(event.valueInt, event.device);
                                return true;
                            case 3:
                                A2dpSinkStateMachine.this.processAudioConfigEvent(event.audioConfig, event.device);
                                return true;
                            default:
                                A2dpSinkStateMachine.this.loge("Unexpected stack event: " + event.type);
                                return true;
                        }
                    default:
                        return A2dpSinkStateMachine.DBG;
                }
            }
            A2dpSinkStateMachine.this.loge("ERROR: current, target, or mIncomingDevice not null in Disconnected");
            return A2dpSinkStateMachine.DBG;
        }

        public void exit() {
            A2dpSinkStateMachine.this.log("Exit Disconnected: " + A2dpSinkStateMachine.this.getCurrentMessage().what);
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            AdapterService adapterService;
            switch (state) {
                case 0:
                    A2dpSinkStateMachine.this.logw("Ignore HF DISCONNECTED event, device: " + device);
                    return;
                case 1:
                    if (A2dpSinkStateMachine.this.okToConnect(device)) {
                        A2dpSinkStateMachine.this.logi("Incoming A2DP accepted");
                        A2dpSinkStateMachine.this.broadcastConnectionState(device, 1, 0);
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mIncomingDevice = device;
                            A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mPending);
                        }
                        return;
                    }
                    A2dpSinkStateMachine.this.logi("Incoming A2DP rejected");
                    A2dpSinkStateMachine.this.disconnectA2dpNative(A2dpSinkStateMachine.this.getByteAddress(device));
                    adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device, 2);
                        return;
                    }
                    return;
                case 2:
                    A2dpSinkStateMachine.this.logw("A2DP Connected from Disconnected state");
                    if (A2dpSinkStateMachine.this.okToConnect(device)) {
                        A2dpSinkStateMachine.this.logi("Incoming A2DP accepted");
                        A2dpSinkStateMachine.this.broadcastConnectionState(device, 2, 0);
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mCurrentDevice = device;
                            A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mConnected);
                        }
                        return;
                    }
                    A2dpSinkStateMachine.this.logi("Incoming A2DP rejected");
                    A2dpSinkStateMachine.this.disconnectA2dpNative(A2dpSinkStateMachine.this.getByteAddress(device));
                    adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device, 2);
                        return;
                    }
                    return;
                case 3:
                    A2dpSinkStateMachine.this.logw("Ignore HF DISCONNECTING event, device: " + device);
                    return;
                default:
                    A2dpSinkStateMachine.this.loge("Incorrect state: " + state);
                    return;
            }
        }
    }

    private class IntentBroadcastHandler extends Handler {
        private IntentBroadcastHandler() {
        }

        private void onConnectionStateChanged(BluetoothDevice device, int prevState, int state) {
            Intent intent = new Intent("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED");
            intent.putExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", prevState);
            intent.putExtra("android.bluetooth.profile.extra.STATE", state);
            intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
            A2dpSinkStateMachine.this.mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
            A2dpSinkStateMachine.this.log("Connection state " + device + ": " + prevState + "->" + state);
            A2dpSinkStateMachine.this.mService.notifyProfileConnectionStateChanged(device, 10, state, prevState);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    onConnectionStateChanged((BluetoothDevice) msg.obj, msg.arg1, msg.arg2);
                    A2dpSinkStateMachine.this.mWakeLock.release();
                    return;
                default:
                    return;
            }
        }
    }

    private class Pending extends State {
        private Pending() {
        }

        public void enter() {
            A2dpSinkStateMachine.this.log("Enter Pending: " + A2dpSinkStateMachine.this.getCurrentMessage().what);
        }

        public boolean processMessage(Message message) {
            A2dpSinkStateMachine.this.log("Pending process message: " + message.what);
            switch (message.what) {
                case 1:
                    A2dpSinkStateMachine.this.deferMessage(message);
                    return true;
                case 2:
                    BluetoothDevice device = message.obj;
                    if (A2dpSinkStateMachine.this.mCurrentDevice == null || A2dpSinkStateMachine.this.mTargetDevice == null || !A2dpSinkStateMachine.this.mTargetDevice.equals(device)) {
                        A2dpSinkStateMachine.this.deferMessage(message);
                        return true;
                    }
                    A2dpSinkStateMachine.this.broadcastConnectionState(device, 0, 1);
                    synchronized (A2dpSinkStateMachine.this) {
                        A2dpSinkStateMachine.this.mTargetDevice = null;
                    }
                    return true;
                case A2dpSinkStateMachine.STACK_EVENT /*101*/:
                    StackEvent event = message.obj;
                    switch (event.type) {
                        case 1:
                            A2dpSinkStateMachine.this.removeMessages(A2dpSinkStateMachine.CONNECT_TIMEOUT);
                            processConnectionEvent(event.valueInt, event.device);
                            return true;
                        case 3:
                            A2dpSinkStateMachine.this.processAudioConfigEvent(event.audioConfig, event.device);
                            return true;
                        default:
                            A2dpSinkStateMachine.this.loge("Unexpected stack event: " + event.type);
                            return true;
                    }
                case A2dpSinkStateMachine.CONNECT_TIMEOUT /*201*/:
                    A2dpSinkStateMachine.this.onConnectionStateChanged(0, A2dpSinkStateMachine.this.getByteAddress(A2dpSinkStateMachine.this.mTargetDevice));
                    return true;
                default:
                    return A2dpSinkStateMachine.DBG;
            }
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case 0:
                    A2dpSinkStateMachine.this.mAudioConfigs.remove(device);
                    if (A2dpSinkStateMachine.this.mCurrentDevice != null && A2dpSinkStateMachine.this.mCurrentDevice.equals(device)) {
                        A2dpSinkStateMachine.this.broadcastConnectionState(A2dpSinkStateMachine.this.mCurrentDevice, 0, 3);
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mCurrentDevice = null;
                        }
                        if (A2dpSinkStateMachine.this.mTargetDevice == null) {
                            synchronized (A2dpSinkStateMachine.this) {
                                A2dpSinkStateMachine.this.mIncomingDevice = null;
                                A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mDisconnected);
                            }
                            return;
                        } else if (!A2dpSinkStateMachine.this.connectA2dpNative(A2dpSinkStateMachine.this.getByteAddress(A2dpSinkStateMachine.this.mTargetDevice))) {
                            A2dpSinkStateMachine.this.broadcastConnectionState(A2dpSinkStateMachine.this.mTargetDevice, 0, 1);
                            synchronized (A2dpSinkStateMachine.this) {
                                A2dpSinkStateMachine.this.mTargetDevice = null;
                                A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mDisconnected);
                            }
                            return;
                        } else {
                            return;
                        }
                    } else if (A2dpSinkStateMachine.this.mTargetDevice != null && A2dpSinkStateMachine.this.mTargetDevice.equals(device)) {
                        A2dpSinkStateMachine.this.broadcastConnectionState(A2dpSinkStateMachine.this.mTargetDevice, 0, 1);
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mTargetDevice = null;
                            A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mDisconnected);
                        }
                        return;
                    } else if (A2dpSinkStateMachine.this.mIncomingDevice == null || !A2dpSinkStateMachine.this.mIncomingDevice.equals(device)) {
                        A2dpSinkStateMachine.this.loge("Unknown device Disconnected: " + device);
                        return;
                    } else {
                        A2dpSinkStateMachine.this.broadcastConnectionState(A2dpSinkStateMachine.this.mIncomingDevice, 0, 1);
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mIncomingDevice = null;
                            A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mDisconnected);
                        }
                        return;
                    }
                case 1:
                    if (A2dpSinkStateMachine.this.mCurrentDevice != null && A2dpSinkStateMachine.this.mCurrentDevice.equals(device)) {
                        A2dpSinkStateMachine.this.log("current device tries to connect back");
                        return;
                    } else if (A2dpSinkStateMachine.this.mTargetDevice != null && A2dpSinkStateMachine.this.mTargetDevice.equals(device)) {
                        A2dpSinkStateMachine.this.log("Stack and target device are connecting");
                        return;
                    } else if (A2dpSinkStateMachine.this.mIncomingDevice == null || !A2dpSinkStateMachine.this.mIncomingDevice.equals(device)) {
                        A2dpSinkStateMachine.this.log("Incoming connection while pending, ignore");
                        return;
                    } else {
                        A2dpSinkStateMachine.this.loge("Another connecting event on the incoming device");
                        return;
                    }
                case 2:
                    if (A2dpSinkStateMachine.this.mCurrentDevice != null && A2dpSinkStateMachine.this.mCurrentDevice.equals(device)) {
                        A2dpSinkStateMachine.this.broadcastConnectionState(A2dpSinkStateMachine.this.mCurrentDevice, 2, 3);
                        if (A2dpSinkStateMachine.this.mTargetDevice != null) {
                            A2dpSinkStateMachine.this.broadcastConnectionState(A2dpSinkStateMachine.this.mTargetDevice, 0, 1);
                        }
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mTargetDevice = null;
                            A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mConnected);
                        }
                        return;
                    } else if (A2dpSinkStateMachine.this.mTargetDevice != null && A2dpSinkStateMachine.this.mTargetDevice.equals(device)) {
                        A2dpSinkStateMachine.this.broadcastConnectionState(A2dpSinkStateMachine.this.mTargetDevice, 2, 1);
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mCurrentDevice = A2dpSinkStateMachine.this.mTargetDevice;
                            A2dpSinkStateMachine.this.mTargetDevice = null;
                            A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mConnected);
                        }
                        return;
                    } else if (A2dpSinkStateMachine.this.mIncomingDevice == null || !A2dpSinkStateMachine.this.mIncomingDevice.equals(device)) {
                        A2dpSinkStateMachine.this.loge("Unknown device Connected: " + device);
                        A2dpSinkStateMachine.this.broadcastConnectionState(device, 2, 0);
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mCurrentDevice = device;
                            A2dpSinkStateMachine.this.mTargetDevice = null;
                            A2dpSinkStateMachine.this.mIncomingDevice = null;
                            A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mConnected);
                        }
                        return;
                    } else {
                        A2dpSinkStateMachine.this.broadcastConnectionState(A2dpSinkStateMachine.this.mIncomingDevice, 2, 1);
                        synchronized (A2dpSinkStateMachine.this) {
                            A2dpSinkStateMachine.this.mCurrentDevice = A2dpSinkStateMachine.this.mIncomingDevice;
                            A2dpSinkStateMachine.this.mIncomingDevice = null;
                            A2dpSinkStateMachine.this.transitionTo(A2dpSinkStateMachine.this.mConnected);
                        }
                        return;
                    }
                case 3:
                    if (A2dpSinkStateMachine.this.mCurrentDevice != null && A2dpSinkStateMachine.this.mCurrentDevice.equals(device)) {
                        return;
                    }
                    if (A2dpSinkStateMachine.this.mTargetDevice != null && A2dpSinkStateMachine.this.mTargetDevice.equals(device)) {
                        A2dpSinkStateMachine.this.loge("TargetDevice is getting disconnected");
                        return;
                    } else if (A2dpSinkStateMachine.this.mIncomingDevice == null || !A2dpSinkStateMachine.this.mIncomingDevice.equals(device)) {
                        A2dpSinkStateMachine.this.loge("Disconnecting unknown device: " + device);
                        return;
                    } else {
                        A2dpSinkStateMachine.this.loge("IncomingDevice is getting disconnected");
                        return;
                    }
                default:
                    A2dpSinkStateMachine.this.loge("Incorrect state: " + state);
                    return;
            }
        }
    }

    private class StackEvent {
        BluetoothAudioConfig audioConfig;
        BluetoothDevice device;
        int type;
        int valueInt;

        private StackEvent(int type) {
            this.type = 0;
            this.valueInt = 0;
            this.device = null;
            this.audioConfig = null;
            this.type = type;
        }
    }

    private static native void classInitNative();

    private native void cleanupNative();

    private native boolean connectA2dpNative(byte[] bArr);

    private native boolean disconnectA2dpNative(byte[] bArr);

    private native void initNative();

    static {
        classInitNative();
    }

    private A2dpSinkStateMachine(A2dpSinkService svc, Context context) {
        super("A2dpSinkStateMachine");
        this.mService = svc;
        this.mContext = context;
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        initNative();
        this.mDisconnected = new Disconnected();
        this.mPending = new Pending();
        this.mConnected = new Connected();
        addState(this.mDisconnected);
        addState(this.mPending);
        addState(this.mConnected);
        setInitialState(this.mDisconnected);
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, "BluetoothA2dpSinkService");
        this.mIntentBroadcastHandler = new IntentBroadcastHandler();
        this.mAudioManager = (AudioManager) context.getSystemService("audio");
    }

    static A2dpSinkStateMachine make(A2dpSinkService svc, Context context) {
        Log.d("A2dpSinkStateMachine", "make");
        A2dpSinkStateMachine a2dpSm = new A2dpSinkStateMachine(svc, context);
        a2dpSm.start();
        return a2dpSm;
    }

    public void doQuit() {
        quitNow();
    }

    public void cleanup() {
        cleanupNative();
        this.mAudioConfigs.clear();
    }

    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mCurrentDevice: " + this.mCurrentDevice);
        ProfileService.println(sb, "mTargetDevice: " + this.mTargetDevice);
        ProfileService.println(sb, "mIncomingDevice: " + this.mIncomingDevice);
        ProfileService.println(sb, "StateMachine: " + toString());
    }

    private void processAudioConfigEvent(BluetoothAudioConfig audioConfig, BluetoothDevice device) {
        this.mAudioConfigs.put(device, audioConfig);
        broadcastAudioConfig(device, audioConfig);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    int getConnectionState(BluetoothDevice device) {
        if (getCurrentState() == this.mDisconnected) {
            return 0;
        }
        synchronized (this) {
            IState currentState = getCurrentState();
            if (currentState == this.mPending) {
                if (this.mTargetDevice != null && this.mTargetDevice.equals(device)) {
                    return 1;
                } else if (this.mCurrentDevice != null && this.mCurrentDevice.equals(device)) {
                    return 3;
                } else if (this.mIncomingDevice == null || !this.mIncomingDevice.equals(device)) {
                } else {
                    return 1;
                }
            } else if (currentState != this.mConnected) {
                loge("Bad currentState: " + currentState);
                return 0;
            } else if (this.mCurrentDevice.equals(device)) {
                return 2;
            } else {
                return 0;
            }
        }
    }

    BluetoothAudioConfig getAudioConfig(BluetoothDevice device) {
        return (BluetoothAudioConfig) this.mAudioConfigs.get(device);
    }

    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList();
        synchronized (this) {
            if (getCurrentState() == this.mConnected) {
                devices.add(this.mCurrentDevice);
            }
        }
        return devices;
    }

    boolean okToConnect(BluetoothDevice device) {
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService == null || (adapterService.isQuietModeEnabled() && this.mTargetDevice == null)) {
            return DBG;
        }
        return true;
    }

    synchronized List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList;
        deviceList = new ArrayList();
        for (BluetoothDevice device : this.mAdapter.getBondedDevices()) {
            if (BluetoothUuid.isUuidPresent(device.getUuids(), BluetoothUuid.AudioSource)) {
                int connectionState = getConnectionState(device);
                for (int i : states) {
                    if (connectionState == i) {
                        deviceList.add(device);
                    }
                }
            }
        }
        return deviceList;
    }

    private void broadcastConnectionState(BluetoothDevice device, int newState, int prevState) {
        int delay = this.mAudioManager.setBluetoothA2dpDeviceConnectionState(device, newState, 10);
        this.mWakeLock.acquire();
        this.mIntentBroadcastHandler.sendMessageDelayed(this.mIntentBroadcastHandler.obtainMessage(0, prevState, newState, device), (long) delay);
    }

    private void broadcastAudioState(BluetoothDevice device, int state, int prevState) {
        Intent intent = new Intent("android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.putExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", prevState);
        intent.putExtra("android.bluetooth.profile.extra.STATE", state);
        this.mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
        log("A2DP Playing state : device: " + device + " State:" + prevState + "->" + state);
    }

    private void broadcastAudioConfig(BluetoothDevice device, BluetoothAudioConfig audioConfig) {
        Intent intent = new Intent("android.bluetooth.a2dp-sink.profile.action.AUDIO_CONFIG_CHANGED");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.putExtra("android.bluetooth.a2dp-sink.profile.extra.AUDIO_CONFIG", audioConfig);
        this.mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
        log("A2DP Audio Config : device: " + device + " config: " + audioConfig);
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    private void onConnectionStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(1);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAudioStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(2);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAudioConfigChanged(byte[] address, int sampleRate, int channelCount) {
        StackEvent event = new StackEvent(3);
        event.audioConfig = new BluetoothAudioConfig(sampleRate, channelCount == 1 ? 16 : 12, 2);
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private BluetoothDevice getDevice(byte[] address) {
        return this.mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
    }
}
