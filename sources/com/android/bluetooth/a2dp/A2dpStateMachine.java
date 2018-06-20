package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothAdapter;
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
import com.android.vcard.VCardConfig;
import java.util.ArrayList;
import java.util.List;

final class A2dpStateMachine extends StateMachine {
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
    private static final int EVENT_TYPE_AUDIO_STATE_CHANGED = 2;
    private static final int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    private static final int EVENT_TYPE_NONE = 0;
    private static final int MSG_CONNECTION_STATE_CHANGED = 0;
    private static final int STACK_EVENT = 101;
    private BluetoothAdapter mAdapter;
    private final AudioManager mAudioManager;
    private Connected mConnected;
    private Context mContext;
    private BluetoothDevice mCurrentDevice = null;
    private Disconnected mDisconnected;
    private BluetoothDevice mIncomingDevice = null;
    private IntentBroadcastHandler mIntentBroadcastHandler;
    private Pending mPending;
    private BluetoothDevice mPlayingA2dpDevice = null;
    private A2dpService mService;
    private BluetoothDevice mTargetDevice = null;
    private final WakeLock mWakeLock;

    private class Connected extends State {
        private Connected() {
        }

        public void enter() {
            A2dpStateMachine.this.log("Enter Connected: " + A2dpStateMachine.this.getCurrentMessage().what);
            A2dpStateMachine.this.broadcastAudioState(A2dpStateMachine.this.mCurrentDevice, 11, 10);
        }

        public boolean processMessage(Message message) {
            A2dpStateMachine.this.log("Connected process message: " + message.what);
            if (A2dpStateMachine.this.mCurrentDevice == null) {
                A2dpStateMachine.this.loge("ERROR: mCurrentDevice is null in Connected");
                return A2dpStateMachine.DBG;
            }
            BluetoothDevice device;
            switch (message.what) {
                case 1:
                    device = message.obj;
                    if (A2dpStateMachine.this.mCurrentDevice.equals(device)) {
                        return true;
                    }
                    A2dpStateMachine.this.broadcastConnectionState(device, 1, 0);
                    if (A2dpStateMachine.this.disconnectA2dpNative(A2dpStateMachine.this.getByteAddress(A2dpStateMachine.this.mCurrentDevice))) {
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mTargetDevice = device;
                            A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mPending);
                        }
                        return true;
                    }
                    A2dpStateMachine.this.broadcastConnectionState(device, 0, 1);
                    return true;
                case 2:
                    device = (BluetoothDevice) message.obj;
                    if (!A2dpStateMachine.this.mCurrentDevice.equals(device)) {
                        return true;
                    }
                    A2dpStateMachine.this.broadcastConnectionState(device, 3, 2);
                    if (A2dpStateMachine.this.disconnectA2dpNative(A2dpStateMachine.this.getByteAddress(device))) {
                        A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mPending);
                        return true;
                    }
                    A2dpStateMachine.this.broadcastConnectionState(device, 2, 0);
                    return true;
                case A2dpStateMachine.STACK_EVENT /*101*/:
                    StackEvent event = message.obj;
                    switch (event.type) {
                        case 1:
                            processConnectionEvent(event.valueInt, event.device);
                            return true;
                        case 2:
                            processAudioStateEvent(event.valueInt, event.device);
                            return true;
                        default:
                            A2dpStateMachine.this.loge("Unexpected stack event: " + event.type);
                            return true;
                    }
                default:
                    return A2dpStateMachine.DBG;
            }
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case 0:
                    if (A2dpStateMachine.this.mCurrentDevice.equals(device)) {
                        A2dpStateMachine.this.broadcastConnectionState(A2dpStateMachine.this.mCurrentDevice, 0, 2);
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mCurrentDevice = null;
                            A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mDisconnected);
                        }
                        return;
                    }
                    A2dpStateMachine.this.loge("Disconnected from unknown device: " + device);
                    return;
                default:
                    A2dpStateMachine.this.loge("Connection State Device: " + device + " bad state: " + state);
                    return;
            }
        }

        private void processAudioStateEvent(int state, BluetoothDevice device) {
            if (A2dpStateMachine.this.mCurrentDevice.equals(device)) {
                switch (state) {
                    case 0:
                    case 1:
                        if (A2dpStateMachine.this.mPlayingA2dpDevice != null) {
                            A2dpStateMachine.this.mPlayingA2dpDevice = null;
                            A2dpStateMachine.this.mService.setAvrcpAudioState(11);
                            A2dpStateMachine.this.broadcastAudioState(device, 11, 10);
                            return;
                        }
                        return;
                    case 2:
                        if (A2dpStateMachine.this.mPlayingA2dpDevice == null) {
                            A2dpStateMachine.this.mPlayingA2dpDevice = device;
                            A2dpStateMachine.this.mService.setAvrcpAudioState(10);
                            A2dpStateMachine.this.broadcastAudioState(device, 10, 11);
                            return;
                        }
                        return;
                    default:
                        A2dpStateMachine.this.loge("Audio State Device: " + device + " bad state: " + state);
                        return;
                }
            }
            A2dpStateMachine.this.loge("Audio State Device:" + device + "is different from ConnectedDevice:" + A2dpStateMachine.this.mCurrentDevice);
        }
    }

    private class Disconnected extends State {
        private Disconnected() {
        }

        public void enter() {
            A2dpStateMachine.this.log("Enter Disconnected: " + A2dpStateMachine.this.getCurrentMessage().what);
        }

        public boolean processMessage(Message message) {
            A2dpStateMachine.this.log("Disconnected process message: " + message.what);
            if (A2dpStateMachine.this.mCurrentDevice == null && A2dpStateMachine.this.mTargetDevice == null && A2dpStateMachine.this.mIncomingDevice == null) {
                switch (message.what) {
                    case 1:
                        BluetoothDevice device = message.obj;
                        A2dpStateMachine.this.broadcastConnectionState(device, 1, 0);
                        if (A2dpStateMachine.this.connectA2dpNative(A2dpStateMachine.this.getByteAddress(device))) {
                            synchronized (A2dpStateMachine.this) {
                                A2dpStateMachine.this.mTargetDevice = device;
                                A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mPending);
                            }
                            A2dpStateMachine.this.sendMessageDelayed(A2dpStateMachine.CONNECT_TIMEOUT, 30000);
                            return true;
                        }
                        A2dpStateMachine.this.broadcastConnectionState(device, 0, 1);
                        return true;
                    case 2:
                        return true;
                    case A2dpStateMachine.STACK_EVENT /*101*/:
                        StackEvent event = message.obj;
                        switch (event.type) {
                            case 1:
                                processConnectionEvent(event.valueInt, event.device);
                                return true;
                            default:
                                A2dpStateMachine.this.loge("Unexpected stack event: " + event.type);
                                return true;
                        }
                    default:
                        return A2dpStateMachine.DBG;
                }
            }
            A2dpStateMachine.this.loge("ERROR: current, target, or mIncomingDevice not null in Disconnected");
            return A2dpStateMachine.DBG;
        }

        public void exit() {
            A2dpStateMachine.this.log("Exit Disconnected: " + A2dpStateMachine.this.getCurrentMessage().what);
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            AdapterService adapterService;
            switch (state) {
                case 0:
                    A2dpStateMachine.this.logw("Ignore HF DISCONNECTED event, device: " + device);
                    return;
                case 1:
                    if (A2dpStateMachine.this.okToConnect(device)) {
                        A2dpStateMachine.this.logi("Incoming A2DP accepted");
                        A2dpStateMachine.this.broadcastConnectionState(device, 1, 0);
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mIncomingDevice = device;
                            A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mPending);
                        }
                        return;
                    }
                    A2dpStateMachine.this.logi("Incoming A2DP rejected");
                    A2dpStateMachine.this.disconnectA2dpNative(A2dpStateMachine.this.getByteAddress(device));
                    adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device, 2);
                        return;
                    }
                    return;
                case 2:
                    A2dpStateMachine.this.logw("A2DP Connected from Disconnected state");
                    if (A2dpStateMachine.this.okToConnect(device)) {
                        A2dpStateMachine.this.logi("Incoming A2DP accepted");
                        A2dpStateMachine.this.broadcastConnectionState(device, 2, 0);
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mCurrentDevice = device;
                            A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mConnected);
                        }
                        return;
                    }
                    A2dpStateMachine.this.logi("Incoming A2DP rejected");
                    A2dpStateMachine.this.disconnectA2dpNative(A2dpStateMachine.this.getByteAddress(device));
                    adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device, 2);
                        return;
                    }
                    return;
                case 3:
                    A2dpStateMachine.this.logw("Ignore HF DISCONNECTING event, device: " + device);
                    return;
                default:
                    A2dpStateMachine.this.loge("Incorrect state: " + state);
                    return;
            }
        }
    }

    private class IntentBroadcastHandler extends Handler {
        private IntentBroadcastHandler() {
        }

        private void onConnectionStateChanged(BluetoothDevice device, int prevState, int state) {
            Intent intent = new Intent("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
            intent.putExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", prevState);
            intent.putExtra("android.bluetooth.profile.extra.STATE", state);
            intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
            intent.addFlags(VCardConfig.FLAG_APPEND_TYPE_PARAM);
            A2dpStateMachine.this.mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
            A2dpStateMachine.this.log("Connection state " + device + ": " + prevState + "->" + state);
            A2dpStateMachine.this.mService.notifyProfileConnectionStateChanged(device, 2, state, prevState);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    onConnectionStateChanged((BluetoothDevice) msg.obj, msg.arg1, msg.arg2);
                    A2dpStateMachine.this.mWakeLock.release();
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
            A2dpStateMachine.this.log("Enter Pending: " + A2dpStateMachine.this.getCurrentMessage().what);
        }

        public boolean processMessage(Message message) {
            A2dpStateMachine.this.log("Pending process message: " + message.what);
            switch (message.what) {
                case 1:
                    A2dpStateMachine.this.deferMessage(message);
                    return true;
                case 2:
                    BluetoothDevice device = message.obj;
                    if (A2dpStateMachine.this.mCurrentDevice == null || A2dpStateMachine.this.mTargetDevice == null || !A2dpStateMachine.this.mTargetDevice.equals(device)) {
                        A2dpStateMachine.this.deferMessage(message);
                        return true;
                    }
                    A2dpStateMachine.this.broadcastConnectionState(device, 0, 1);
                    synchronized (A2dpStateMachine.this) {
                        A2dpStateMachine.this.mTargetDevice = null;
                    }
                    return true;
                case A2dpStateMachine.STACK_EVENT /*101*/:
                    StackEvent event = message.obj;
                    switch (event.type) {
                        case 1:
                            A2dpStateMachine.this.removeMessages(A2dpStateMachine.CONNECT_TIMEOUT);
                            processConnectionEvent(event.valueInt, event.device);
                            return true;
                        default:
                            A2dpStateMachine.this.loge("Unexpected stack event: " + event.type);
                            return true;
                    }
                case A2dpStateMachine.CONNECT_TIMEOUT /*201*/:
                    A2dpStateMachine.this.onConnectionStateChanged(0, A2dpStateMachine.this.getByteAddress(A2dpStateMachine.this.mTargetDevice));
                    return true;
                default:
                    return A2dpStateMachine.DBG;
            }
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case 0:
                    if (A2dpStateMachine.this.mCurrentDevice != null && A2dpStateMachine.this.mCurrentDevice.equals(device)) {
                        A2dpStateMachine.this.broadcastConnectionState(A2dpStateMachine.this.mCurrentDevice, 0, 3);
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mCurrentDevice = null;
                        }
                        if (A2dpStateMachine.this.mTargetDevice == null) {
                            synchronized (A2dpStateMachine.this) {
                                A2dpStateMachine.this.mIncomingDevice = null;
                                A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mDisconnected);
                            }
                            return;
                        } else if (!A2dpStateMachine.this.connectA2dpNative(A2dpStateMachine.this.getByteAddress(A2dpStateMachine.this.mTargetDevice))) {
                            A2dpStateMachine.this.broadcastConnectionState(A2dpStateMachine.this.mTargetDevice, 0, 1);
                            synchronized (A2dpStateMachine.this) {
                                A2dpStateMachine.this.mTargetDevice = null;
                                A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mDisconnected);
                            }
                            return;
                        } else {
                            return;
                        }
                    } else if (A2dpStateMachine.this.mTargetDevice != null && A2dpStateMachine.this.mTargetDevice.equals(device)) {
                        A2dpStateMachine.this.broadcastConnectionState(A2dpStateMachine.this.mTargetDevice, 0, 1);
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mTargetDevice = null;
                            A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mDisconnected);
                        }
                        return;
                    } else if (A2dpStateMachine.this.mIncomingDevice == null || !A2dpStateMachine.this.mIncomingDevice.equals(device)) {
                        A2dpStateMachine.this.loge("Unknown device Disconnected: " + device);
                        return;
                    } else {
                        A2dpStateMachine.this.broadcastConnectionState(A2dpStateMachine.this.mIncomingDevice, 0, 1);
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mIncomingDevice = null;
                            A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mDisconnected);
                        }
                        return;
                    }
                case 1:
                    if (A2dpStateMachine.this.mCurrentDevice != null && A2dpStateMachine.this.mCurrentDevice.equals(device)) {
                        A2dpStateMachine.this.log("current device tries to connect back");
                        return;
                    } else if (A2dpStateMachine.this.mTargetDevice != null && A2dpStateMachine.this.mTargetDevice.equals(device)) {
                        A2dpStateMachine.this.log("Stack and target device are connecting");
                        return;
                    } else if (A2dpStateMachine.this.mIncomingDevice == null || !A2dpStateMachine.this.mIncomingDevice.equals(device)) {
                        A2dpStateMachine.this.log("Incoming connection while pending, ignore");
                        return;
                    } else {
                        A2dpStateMachine.this.loge("Another connecting event on the incoming device");
                        return;
                    }
                case 2:
                    if (A2dpStateMachine.this.mCurrentDevice != null && A2dpStateMachine.this.mCurrentDevice.equals(device)) {
                        A2dpStateMachine.this.broadcastConnectionState(A2dpStateMachine.this.mCurrentDevice, 2, 3);
                        if (A2dpStateMachine.this.mTargetDevice != null) {
                            A2dpStateMachine.this.broadcastConnectionState(A2dpStateMachine.this.mTargetDevice, 0, 1);
                        }
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mTargetDevice = null;
                            A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mConnected);
                        }
                        return;
                    } else if (A2dpStateMachine.this.mTargetDevice != null && A2dpStateMachine.this.mTargetDevice.equals(device)) {
                        A2dpStateMachine.this.broadcastConnectionState(A2dpStateMachine.this.mTargetDevice, 2, 1);
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mCurrentDevice = A2dpStateMachine.this.mTargetDevice;
                            A2dpStateMachine.this.mTargetDevice = null;
                            A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mConnected);
                        }
                        return;
                    } else if (A2dpStateMachine.this.mIncomingDevice == null || !A2dpStateMachine.this.mIncomingDevice.equals(device)) {
                        A2dpStateMachine.this.loge("Unknown device Connected: " + device);
                        A2dpStateMachine.this.broadcastConnectionState(device, 2, 0);
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mCurrentDevice = device;
                            A2dpStateMachine.this.mTargetDevice = null;
                            A2dpStateMachine.this.mIncomingDevice = null;
                            A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mConnected);
                        }
                        return;
                    } else {
                        A2dpStateMachine.this.broadcastConnectionState(A2dpStateMachine.this.mIncomingDevice, 2, 1);
                        synchronized (A2dpStateMachine.this) {
                            A2dpStateMachine.this.mCurrentDevice = A2dpStateMachine.this.mIncomingDevice;
                            A2dpStateMachine.this.mIncomingDevice = null;
                            A2dpStateMachine.this.transitionTo(A2dpStateMachine.this.mConnected);
                        }
                        return;
                    }
                case 3:
                    if (A2dpStateMachine.this.mCurrentDevice != null && A2dpStateMachine.this.mCurrentDevice.equals(device)) {
                        return;
                    }
                    if (A2dpStateMachine.this.mTargetDevice != null && A2dpStateMachine.this.mTargetDevice.equals(device)) {
                        A2dpStateMachine.this.loge("TargetDevice is getting disconnected");
                        return;
                    } else if (A2dpStateMachine.this.mIncomingDevice == null || !A2dpStateMachine.this.mIncomingDevice.equals(device)) {
                        A2dpStateMachine.this.loge("Disconnecting unknow device: " + device);
                        return;
                    } else {
                        A2dpStateMachine.this.loge("IncomingDevice is getting disconnected");
                        return;
                    }
                default:
                    A2dpStateMachine.this.loge("Incorrect state: " + state);
                    return;
            }
        }
    }

    private class StackEvent {
        BluetoothDevice device;
        int type;
        int valueInt;

        private StackEvent(int type) {
            this.type = 0;
            this.valueInt = 0;
            this.device = null;
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

    private A2dpStateMachine(A2dpService svc, Context context) {
        super("A2dpStateMachine");
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
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, "BluetoothA2dpService");
        this.mIntentBroadcastHandler = new IntentBroadcastHandler();
        this.mAudioManager = (AudioManager) context.getSystemService("audio");
    }

    static A2dpStateMachine make(A2dpService svc, Context context) {
        Log.d("A2dpStateMachine", "make");
        A2dpStateMachine a2dpSm = new A2dpStateMachine(svc, context);
        a2dpSm.start();
        return a2dpSm;
    }

    public void doQuit() {
        quitNow();
    }

    public void cleanup() {
        cleanupNative();
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

    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList();
        synchronized (this) {
            if (getCurrentState() == this.mConnected) {
                devices.add(this.mCurrentDevice);
            }
        }
        return devices;
    }

    boolean isPlaying(BluetoothDevice device) {
        synchronized (this) {
            if (device.equals(this.mPlayingA2dpDevice)) {
                return true;
            }
            return DBG;
        }
    }

    boolean okToConnect(BluetoothDevice device) {
        AdapterService adapterService = AdapterService.getAdapterService();
        int priority = this.mService.getPriority(device);
        if (adapterService == null || (adapterService.isQuietModeEnabled() && this.mTargetDevice == null)) {
            return DBG;
        }
        if (priority > 0 || (-1 == priority && device.getBondState() != 10)) {
            return true;
        }
        return DBG;
    }

    synchronized List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList;
        deviceList = new ArrayList();
        for (BluetoothDevice device : this.mAdapter.getBondedDevices()) {
            if (BluetoothUuid.isUuidPresent(device.getUuids(), BluetoothUuid.AudioSink)) {
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
        int delay = this.mAudioManager.setBluetoothA2dpDeviceConnectionState(device, newState, 2);
        this.mWakeLock.acquire();
        this.mIntentBroadcastHandler.sendMessageDelayed(this.mIntentBroadcastHandler.obtainMessage(0, prevState, newState, device), (long) delay);
    }

    private void broadcastAudioState(BluetoothDevice device, int state, int prevState) {
        Intent intent = new Intent("android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.putExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", prevState);
        intent.putExtra("android.bluetooth.profile.extra.STATE", state);
        intent.addFlags(VCardConfig.FLAG_APPEND_TYPE_PARAM);
        this.mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
        log("A2DP Playing state : device: " + device + " State:" + prevState + "->" + state);
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

    private BluetoothDevice getDevice(byte[] address) {
        return this.mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
    }

    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mCurrentDevice: " + this.mCurrentDevice);
        ProfileService.println(sb, "mTargetDevice: " + this.mTargetDevice);
        ProfileService.println(sb, "mIncomingDevice: " + this.mIncomingDevice);
        ProfileService.println(sb, "mPlayingA2dpDevice: " + this.mPlayingA2dpDevice);
        ProfileService.println(sb, "StateMachine: " + toString());
    }
}
