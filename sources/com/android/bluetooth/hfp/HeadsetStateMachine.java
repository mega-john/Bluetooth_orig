package com.android.bluetooth.hfp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothHeadsetPhone;
import android.bluetooth.IBluetoothHeadsetPhone.Stub;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AbstractionLayer;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.map.BluetoothMapContent;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class HeadsetStateMachine extends StateMachine {
    static final int CALL_STATE_CHANGED = 9;
    private static final int CLCC_RSP_TIMEOUT = 104;
    private static final int CLCC_RSP_TIMEOUT_VALUE = 5000;
    static final int CONNECT = 1;
    static final int CONNECT_AUDIO = 3;
    private static final int CONNECT_TIMEOUT = 201;
    private static final boolean DBG = true;
    static final int DEVICE_STATE_CHANGED = 11;
    private static final int DIALING_OUT_TIMEOUT = 102;
    private static final int DIALING_OUT_TIMEOUT_VALUE = 10000;
    static final int DISABLE_WBS = 17;
    static final int DISCONNECT = 2;
    static final int DISCONNECT_AUDIO = 4;
    static final int ENABLE_WBS = 16;
    private static final int EVENT_TYPE_ANSWER_CALL = 4;
    private static final int EVENT_TYPE_AT_CHLD = 10;
    private static final int EVENT_TYPE_AT_CIND = 12;
    private static final int EVENT_TYPE_AT_CLCC = 14;
    private static final int EVENT_TYPE_AT_COPS = 13;
    private static final int EVENT_TYPE_AUDIO_STATE_CHANGED = 2;
    private static final int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    private static final int EVENT_TYPE_DIAL_CALL = 7;
    private static final int EVENT_TYPE_HANGUP_CALL = 5;
    private static final int EVENT_TYPE_KEY_PRESSED = 16;
    private static final int EVENT_TYPE_NOICE_REDUCTION = 9;
    private static final int EVENT_TYPE_NONE = 0;
    private static final int EVENT_TYPE_SEND_DTMF = 8;
    private static final int EVENT_TYPE_SUBSCRIBER_NUMBER_REQUEST = 11;
    private static final int EVENT_TYPE_UNKNOWN_AT = 15;
    private static final int EVENT_TYPE_VOLUME_CHANGED = 6;
    private static final int EVENT_TYPE_VR_STATE_CHANGED = 3;
    private static final int EVENT_TYPE_WBS = 17;
    private static final String HEADSET_NAME = "bt_headset_name";
    private static final String HEADSET_NREC = "bt_headset_nrec";
    private static final ParcelUuid[] HEADSET_UUIDS = new ParcelUuid[]{BluetoothUuid.HSP, BluetoothUuid.Handsfree};
    private static final String HEADSET_WBS = "bt_wbs";
    static final int INTENT_BATTERY_CHANGED = 10;
    static final int INTENT_SCO_VOLUME_CHANGED = 7;
    private static final int NBS_CODEC = 1;
    private static final String SCHEME_TEL = "tel";
    static final int SEND_CCLC_RESPONSE = 12;
    static final int SEND_VENDOR_SPECIFIC_RESULT_CODE = 13;
    static final int SET_MIC_VOLUME = 8;
    private static final int STACK_EVENT = 101;
    private static final int START_VR_TIMEOUT = 103;
    private static final int START_VR_TIMEOUT_VALUE = 5000;
    private static final String TAG = "HeadsetStateMachine";
    private static final Map<String, Integer> VENDOR_SPECIFIC_AT_COMMAND_COMPANY_ID = new HashMap();
    static final int VIRTUAL_CALL_START = 14;
    static final int VIRTUAL_CALL_STOP = 15;
    static final int VOICE_RECOGNITION_START = 5;
    static final int VOICE_RECOGNITION_STOP = 6;
    private static final int WBS_CODEC = 2;
    private static int sRefCount = 0;
    private static Intent sVoiceCommandIntent;
    private BluetoothDevice mActiveScoDevice = null;
    private BluetoothAdapter mAdapter;
    private AudioManager mAudioManager;
    private AudioOn mAudioOn;
    private int mAudioState;
    private Connected mConnected;
    private ArrayList<BluetoothDevice> mConnectedDevicesList = new ArrayList();
    private ServiceConnection mConnection = new C00171();
    private BluetoothDevice mCurrentDevice = null;
    private boolean mDialingOut = false;
    private Disconnected mDisconnected;
    private HashMap<BluetoothDevice, HashMap> mHeadsetAudioParam = new HashMap();
    private HashMap<BluetoothDevice, Integer> mHeadsetBrsf = new HashMap();
    private BluetoothDevice mIncomingDevice = null;
    private BluetoothDevice mMultiDisconnectDevice = null;
    private MultiHFPending mMultiHFPending;
    private boolean mNativeAvailable;
    private Pending mPending;
    private IBluetoothHeadsetPhone mPhoneProxy;
    private HeadsetPhoneState mPhoneState;
    private AtPhonebook mPhonebook;
    private PowerManager mPowerManager;
    private HeadsetService mService;
    private WakeLock mStartVoiceRecognitionWakeLock;
    private BluetoothDevice mTargetDevice = null;
    private boolean mVirtualCallStarted = false;
    private boolean mVoiceRecognitionStarted = false;
    private boolean mWaitingForVoiceRecognition = false;
    private int max_hf_connections = 1;

    /* renamed from: com.android.bluetooth.hfp.HeadsetStateMachine$1 */
    class C00171 implements ServiceConnection {
        C00171() {
        }

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(HeadsetStateMachine.TAG, "Proxy object connected");
            HeadsetStateMachine.this.mPhoneProxy = Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(HeadsetStateMachine.TAG, "Proxy object disconnected");
            HeadsetStateMachine.this.mPhoneProxy = null;
        }
    }

    private class AudioOn extends State {
        private AudioOn() {
        }

        public void enter() {
            HeadsetStateMachine.this.log("Enter AudioOn: " + HeadsetStateMachine.this.getCurrentMessage().what + ", size: " + HeadsetStateMachine.this.mConnectedDevicesList.size());
        }

        public boolean processMessage(Message message) {
            HeadsetStateMachine.this.log("AudioOn process message: " + message.what + ", size: " + HeadsetStateMachine.this.mConnectedDevicesList.size());
            if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0) {
                HeadsetStateMachine.this.log("ERROR: mConnectedDevicesList is empty in AudioOn");
                return false;
            }
            BluetoothDevice device;
            switch (message.what) {
                case 1:
                    device = message.obj;
                    if (device == null || HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        return true;
                    }
                    if (HeadsetStateMachine.this.max_hf_connections == 1) {
                        HeadsetStateMachine.this.deferMessage(HeadsetStateMachine.this.obtainMessage(2, HeadsetStateMachine.this.mCurrentDevice));
                        HeadsetStateMachine.this.deferMessage(HeadsetStateMachine.this.obtainMessage(1, device));
                        if (HeadsetStateMachine.this.disconnectAudioNative(HeadsetStateMachine.this.getByteAddress(HeadsetStateMachine.this.mCurrentDevice))) {
                            Log.d(HeadsetStateMachine.TAG, "Disconnecting SCO audio for device = " + HeadsetStateMachine.this.mCurrentDevice);
                            return true;
                        }
                        Log.e(HeadsetStateMachine.TAG, "disconnectAudioNative failed");
                        return true;
                    }
                    if (HeadsetStateMachine.this.mConnectedDevicesList.size() >= HeadsetStateMachine.this.max_hf_connections) {
                        IState CurrentAudioState = HeadsetStateMachine.this.getCurrentState();
                        Log.d(HeadsetStateMachine.TAG, "Reach to max size, disconnect one of them first");
                        BluetoothDevice DisconnectConnectedDevice = (BluetoothDevice) HeadsetStateMachine.this.mConnectedDevicesList.get(0);
                        if (HeadsetStateMachine.this.mActiveScoDevice.equals(DisconnectConnectedDevice)) {
                            DisconnectConnectedDevice = (BluetoothDevice) HeadsetStateMachine.this.mConnectedDevicesList.get(1);
                        }
                        HeadsetStateMachine.this.broadcastConnectionState(device, 1, 0);
                        if (HeadsetStateMachine.this.disconnectHfpNative(HeadsetStateMachine.this.getByteAddress(DisconnectConnectedDevice))) {
                            HeadsetStateMachine.this.broadcastConnectionState(DisconnectConnectedDevice, 3, 2);
                            synchronized (HeadsetStateMachine.this) {
                                HeadsetStateMachine.this.mTargetDevice = device;
                                HeadsetStateMachine.this.mMultiDisconnectDevice = DisconnectConnectedDevice;
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mMultiHFPending);
                            }
                        } else {
                            HeadsetStateMachine.this.broadcastConnectionState(device, 0, 1);
                            return true;
                        }
                    } else if (HeadsetStateMachine.this.mConnectedDevicesList.size() < HeadsetStateMachine.this.max_hf_connections) {
                        HeadsetStateMachine.this.broadcastConnectionState(device, 1, 0);
                        if (HeadsetStateMachine.this.connectHfpNative(HeadsetStateMachine.this.getByteAddress(device))) {
                            synchronized (HeadsetStateMachine.this) {
                                HeadsetStateMachine.this.mTargetDevice = device;
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mMultiHFPending);
                            }
                        } else {
                            HeadsetStateMachine.this.broadcastConnectionState(device, 0, 1);
                            return true;
                        }
                    }
                    Message m = HeadsetStateMachine.this.obtainMessage(HeadsetStateMachine.CONNECT_TIMEOUT);
                    m.obj = device;
                    HeadsetStateMachine.this.sendMessageDelayed(m, 30000);
                    return true;
                case 2:
                    device = (BluetoothDevice) message.obj;
                    if (!HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        return true;
                    }
                    if (HeadsetStateMachine.this.mActiveScoDevice == null || !HeadsetStateMachine.this.mActiveScoDevice.equals(device)) {
                        Log.d(HeadsetStateMachine.TAG, "AudioOn, the disconnected deviceis not active SCO device");
                        HeadsetStateMachine.this.broadcastConnectionState(device, 3, 2);
                        if (!HeadsetStateMachine.this.disconnectHfpNative(HeadsetStateMachine.this.getByteAddress(device))) {
                            Log.w(HeadsetStateMachine.TAG, "AudioOn, disconnect device failed");
                            HeadsetStateMachine.this.broadcastConnectionState(device, 2, 3);
                            return true;
                        } else if (HeadsetStateMachine.this.mConnectedDevicesList.size() <= 1) {
                            return true;
                        } else {
                            HeadsetStateMachine.this.mMultiDisconnectDevice = device;
                            HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mMultiHFPending);
                            return true;
                        }
                    }
                    Log.d(HeadsetStateMachine.TAG, "AudioOn, the disconnected deviceis active SCO device");
                    HeadsetStateMachine.this.deferMessage(HeadsetStateMachine.this.obtainMessage(2, message.obj));
                    if (HeadsetStateMachine.this.disconnectAudioNative(HeadsetStateMachine.this.getByteAddress(HeadsetStateMachine.this.mActiveScoDevice))) {
                        HeadsetStateMachine.this.log("Disconnecting SCO audio");
                        return true;
                    }
                    HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                    return true;
                case 4:
                    if (HeadsetStateMachine.this.mActiveScoDevice == null) {
                        return true;
                    }
                    if (HeadsetStateMachine.this.disconnectAudioNative(HeadsetStateMachine.this.getByteAddress(HeadsetStateMachine.this.mActiveScoDevice))) {
                        HeadsetStateMachine.this.log("Disconnecting SCO audio for device = " + HeadsetStateMachine.this.mActiveScoDevice);
                        return true;
                    }
                    Log.e(HeadsetStateMachine.TAG, "disconnectAudioNative failedfor device = " + HeadsetStateMachine.this.mActiveScoDevice);
                    return true;
                case 5:
                    HeadsetStateMachine.this.processLocalVrEvent(1);
                    return true;
                case 6:
                    HeadsetStateMachine.this.processLocalVrEvent(0);
                    return true;
                case AbstractionLayer.BT_STATUS_PARM_INVALID /*7*/:
                    processIntentScoVolume((Intent) message.obj, HeadsetStateMachine.this.mActiveScoDevice);
                    return true;
                case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                    HeadsetStateMachine.this.processCallState((HeadsetCallState) message.obj, message.arg1 == 1);
                    return true;
                case 10:
                    HeadsetStateMachine.this.processIntentBatteryChanged((Intent) message.obj);
                    return true;
                case 11:
                    HeadsetStateMachine.this.processDeviceStateChanged((HeadsetDeviceState) message.obj);
                    return true;
                case 12:
                    HeadsetStateMachine.this.processSendClccResponse((HeadsetClccResponse) message.obj);
                    return true;
                case BluetoothCmeError.SIM_FAILURE /*13*/:
                    HeadsetStateMachine.this.processSendVendorSpecificResultCode((HeadsetVendorSpecificResultCode) message.obj);
                    return true;
                case BluetoothCmeError.SIM_BUSY /*14*/:
                    HeadsetStateMachine.this.initiateScoUsingVirtualVoiceCall();
                    return true;
                case VCardConstants.MAX_DATA_COLUMN /*15*/:
                    HeadsetStateMachine.this.terminateScoUsingVirtualVoiceCall();
                    return true;
                case HeadsetStateMachine.STACK_EVENT /*101*/:
                    StackEvent event = message.obj;
                    HeadsetStateMachine.this.log("event type: " + event.type);
                    switch (event.type) {
                        case 1:
                            BluetoothDevice device1 = HeadsetStateMachine.this.getDeviceForMessage(HeadsetStateMachine.CONNECT_TIMEOUT);
                            if (device1 != null && device1.equals(event.device)) {
                                Log.d(HeadsetStateMachine.TAG, "remove connect timeout for device = " + device1);
                                HeadsetStateMachine.this.removeMessages(HeadsetStateMachine.CONNECT_TIMEOUT);
                            }
                            processConnectionEvent(event.valueInt, event.device);
                            return true;
                        case 2:
                            processAudioEvent(event.valueInt, event.device);
                            return true;
                        case 3:
                            HeadsetStateMachine.this.processVrEvent(event.valueInt, event.device);
                            return true;
                        case 4:
                            HeadsetStateMachine.this.processAnswerCall(event.device);
                            return true;
                        case 5:
                            HeadsetStateMachine.this.processHangupCall(event.device);
                            return true;
                        case 6:
                            HeadsetStateMachine.this.processVolumeEvent(event.valueInt, event.valueInt2, event.device);
                            return true;
                        case AbstractionLayer.BT_STATUS_PARM_INVALID /*7*/:
                            HeadsetStateMachine.this.processDialCall(event.valueString, event.device);
                            return true;
                        case 8:
                            HeadsetStateMachine.this.processSendDtmf(event.valueInt, event.device);
                            return true;
                        case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                            HeadsetStateMachine.this.processNoiceReductionEvent(event.valueInt, event.device);
                            return true;
                        case 10:
                            HeadsetStateMachine.this.processAtChld(event.valueInt, event.device);
                            return true;
                        case 11:
                            HeadsetStateMachine.this.processSubscriberNumberRequest(event.device);
                            return true;
                        case 12:
                            HeadsetStateMachine.this.processAtCind(event.device);
                            return true;
                        case BluetoothCmeError.SIM_FAILURE /*13*/:
                            HeadsetStateMachine.this.processAtCops(event.device);
                            return true;
                        case BluetoothCmeError.SIM_BUSY /*14*/:
                            HeadsetStateMachine.this.processAtClcc(event.device);
                            return true;
                        case VCardConstants.MAX_DATA_COLUMN /*15*/:
                            HeadsetStateMachine.this.processUnknownAt(event.valueString, event.device);
                            return true;
                        case BluetoothCmeError.WRONG_PASSWORD /*16*/:
                            HeadsetStateMachine.this.processKeyPressed(event.device);
                            return true;
                        default:
                            Log.e(HeadsetStateMachine.TAG, "Unknown stack event: " + event.type);
                            return true;
                    }
                case HeadsetStateMachine.DIALING_OUT_TIMEOUT /*102*/:
                    if (!HeadsetStateMachine.this.mDialingOut) {
                        return true;
                    }
                    device = (BluetoothDevice) message.obj;
                    HeadsetStateMachine.this.mDialingOut = false;
                    HeadsetStateMachine.this.atResponseCodeNative(0, 0, HeadsetStateMachine.this.getByteAddress(device));
                    return true;
                case HeadsetStateMachine.START_VR_TIMEOUT /*103*/:
                    if (!HeadsetStateMachine.this.mWaitingForVoiceRecognition) {
                        return true;
                    }
                    device = (BluetoothDevice) message.obj;
                    HeadsetStateMachine.this.mWaitingForVoiceRecognition = false;
                    Log.e(HeadsetStateMachine.TAG, "Timeout waiting for voice recognitionto start");
                    HeadsetStateMachine.this.atResponseCodeNative(0, 0, HeadsetStateMachine.this.getByteAddress(device));
                    return true;
                case HeadsetStateMachine.CLCC_RSP_TIMEOUT /*104*/:
                    HeadsetStateMachine.this.clccResponseNative(0, 0, 0, 0, false, "", 0, HeadsetStateMachine.this.getByteAddress((BluetoothDevice) message.obj));
                    return true;
                case HeadsetStateMachine.CONNECT_TIMEOUT /*201*/:
                    HeadsetStateMachine.this.onConnectionStateChanged(0, HeadsetStateMachine.this.getByteAddress(HeadsetStateMachine.this.mTargetDevice));
                    return true;
                default:
                    return false;
            }
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            Log.d(HeadsetStateMachine.TAG, "processConnectionEvent state = " + state + ", device = " + device);
            switch (state) {
                case 0:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        if (!(HeadsetStateMachine.this.mActiveScoDevice == null || !HeadsetStateMachine.this.mActiveScoDevice.equals(device) || HeadsetStateMachine.this.mAudioState == 10)) {
                            processAudioEvent(0, device);
                        }
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mConnectedDevicesList.remove(device);
                            HeadsetStateMachine.this.mHeadsetAudioParam.remove(device);
                            HeadsetStateMachine.this.mHeadsetBrsf.remove(device);
                            Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is removed in AudioOn state");
                            HeadsetStateMachine.this.broadcastConnectionState(device, 0, 2);
                            HeadsetStateMachine.this.processWBSEvent(0, device);
                            if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0) {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mDisconnected);
                            } else {
                                processMultiHFConnected(device);
                            }
                        }
                        return;
                    }
                    Log.e(HeadsetStateMachine.TAG, "Disconnected from unknown device: " + device);
                    return;
                case 2:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        HeadsetStateMachine.this.mIncomingDevice = null;
                        HeadsetStateMachine.this.mTargetDevice = null;
                        return;
                    }
                    Log.w(HeadsetStateMachine.TAG, "HFP to be Connected in AudioOn state");
                    if (!HeadsetStateMachine.this.okToConnect(device) || HeadsetStateMachine.this.mConnectedDevicesList.size() >= HeadsetStateMachine.this.max_hf_connections) {
                        Log.i(HeadsetStateMachine.TAG, "Incoming Hf rejected. priority=" + HeadsetStateMachine.this.mService.getPriority(device) + " bondState=" + device.getBondState());
                        HeadsetStateMachine.this.disconnectHfpNative(HeadsetStateMachine.this.getByteAddress(device));
                        AdapterService adapterService = AdapterService.getAdapterService();
                        if (adapterService != null) {
                            adapterService.connectOtherProfile(device, 2);
                            return;
                        }
                        return;
                    }
                    Log.i(HeadsetStateMachine.TAG, "Incoming Hf accepted");
                    HeadsetStateMachine.this.broadcastConnectionState(device, 2, 0);
                    synchronized (HeadsetStateMachine.this) {
                        if (!HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                            HeadsetStateMachine.this.mCurrentDevice = device;
                            HeadsetStateMachine.this.mConnectedDevicesList.add(device);
                            Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is added in AudioOn state");
                        }
                    }
                    HeadsetStateMachine.this.configAudioParameters(device);
                    return;
                case 3:
                    processSlcConnected();
                    return;
                default:
                    Log.e(HeadsetStateMachine.TAG, "Connection State Device: " + device + " bad state: " + state);
                    return;
            }
        }

        private void processAudioEvent(int state, BluetoothDevice device) {
            if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                switch (state) {
                    case 0:
                        if (HeadsetStateMachine.this.mAudioState != 10) {
                            HeadsetStateMachine.this.mAudioState = 10;
                            HeadsetStateMachine.this.mAudioManager.setBluetoothScoOn(false);
                            HeadsetStateMachine.this.broadcastAudioState(device, 10, 12);
                        }
                        HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                        return;
                    case 3:
                        return;
                    default:
                        Log.e(HeadsetStateMachine.TAG, "Audio State Device: " + device + " bad state: " + state);
                        return;
                }
            }
            Log.e(HeadsetStateMachine.TAG, "Audio changed on disconnected device: " + device);
        }

        private void processSlcConnected() {
            if (HeadsetStateMachine.this.mPhoneProxy != null) {
                try {
                    HeadsetStateMachine.this.mPhoneProxy.queryPhoneState();
                    return;
                } catch (RemoteException e) {
                    Log.e(HeadsetStateMachine.TAG, Log.getStackTraceString(new Throwable()));
                    return;
                }
            }
            Log.e(HeadsetStateMachine.TAG, "Handsfree phone proxy null for query phone state");
        }

        private void processIntentScoVolume(Intent intent, BluetoothDevice device) {
            int volumeValue = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0);
            if (HeadsetStateMachine.this.mPhoneState.getSpeakerVolume() != volumeValue) {
                HeadsetStateMachine.this.mPhoneState.setSpeakerVolume(volumeValue);
                HeadsetStateMachine.this.setVolumeNative(0, volumeValue, HeadsetStateMachine.this.getByteAddress(device));
            }
        }

        private void processMultiHFConnected(BluetoothDevice device) {
            HeadsetStateMachine.this.log("AudioOn state: processMultiHFConnected");
            if (HeadsetStateMachine.this.mCurrentDevice != null && HeadsetStateMachine.this.mCurrentDevice.equals(device)) {
                HeadsetStateMachine.this.mCurrentDevice = (BluetoothDevice) HeadsetStateMachine.this.mConnectedDevicesList.get(HeadsetStateMachine.this.mConnectedDevicesList.size() - 1);
            }
            if (HeadsetStateMachine.this.mAudioState != 12) {
                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
            }
            HeadsetStateMachine.this.log("processMultiHFConnected , the latest mCurrentDevice is:" + HeadsetStateMachine.this.mCurrentDevice);
            HeadsetStateMachine.this.log("AudioOn state: processMultiHFConnected ,fake broadcasting for mCurrentDevice");
            HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mCurrentDevice, 2, 0);
        }
    }

    private class Connected extends State {
        private Connected() {
        }

        public void enter() {
            HeadsetStateMachine.this.log("Enter Connected: " + HeadsetStateMachine.this.getCurrentMessage().what + ", size: " + HeadsetStateMachine.this.mConnectedDevicesList.size());
            HeadsetStateMachine.this.mPhoneState.listenForPhoneState(true);
        }

        public boolean processMessage(Message message) {
            HeadsetStateMachine.this.log("Connected process message: " + message.what + ", size: " + HeadsetStateMachine.this.mConnectedDevicesList.size());
            if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0) {
                HeadsetStateMachine.this.log("ERROR: mConnectedDevicesList is empty in Connected");
                return false;
            }
            BluetoothDevice device;
            switch (message.what) {
                case 1:
                    device = message.obj;
                    if (device == null) {
                        return true;
                    }
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        Log.e(HeadsetStateMachine.TAG, "ERROR: Connect received for already connected device, Ignore");
                        return true;
                    }
                    if (HeadsetStateMachine.this.mConnectedDevicesList.size() >= HeadsetStateMachine.this.max_hf_connections) {
                        IState CurrentAudioState = HeadsetStateMachine.this.getCurrentState();
                        Log.d(HeadsetStateMachine.TAG, "Reach to max size, disconnect one of them first");
                        BluetoothDevice DisconnectConnectedDevice = (BluetoothDevice) HeadsetStateMachine.this.mConnectedDevicesList.get(0);
                        HeadsetStateMachine.this.broadcastConnectionState(device, 1, 0);
                        if (HeadsetStateMachine.this.disconnectHfpNative(HeadsetStateMachine.this.getByteAddress(DisconnectConnectedDevice))) {
                            HeadsetStateMachine.this.broadcastConnectionState(DisconnectConnectedDevice, 3, 2);
                            synchronized (HeadsetStateMachine.this) {
                                HeadsetStateMachine.this.mTargetDevice = device;
                                if (HeadsetStateMachine.this.max_hf_connections == 1) {
                                    HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mPending);
                                } else {
                                    HeadsetStateMachine.this.mMultiDisconnectDevice = DisconnectConnectedDevice;
                                    HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mMultiHFPending);
                                }
                            }
                        } else {
                            HeadsetStateMachine.this.broadcastConnectionState(device, 0, 1);
                            return true;
                        }
                    } else if (HeadsetStateMachine.this.mConnectedDevicesList.size() < HeadsetStateMachine.this.max_hf_connections) {
                        HeadsetStateMachine.this.broadcastConnectionState(device, 1, 0);
                        if (HeadsetStateMachine.this.connectHfpNative(HeadsetStateMachine.this.getByteAddress(device))) {
                            synchronized (HeadsetStateMachine.this) {
                                HeadsetStateMachine.this.mTargetDevice = device;
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mMultiHFPending);
                            }
                        } else {
                            HeadsetStateMachine.this.broadcastConnectionState(device, 0, 1);
                            return true;
                        }
                    }
                    Message m = HeadsetStateMachine.this.obtainMessage(HeadsetStateMachine.CONNECT_TIMEOUT);
                    m.obj = device;
                    HeadsetStateMachine.this.sendMessageDelayed(m, 30000);
                    return true;
                case 2:
                    device = (BluetoothDevice) message.obj;
                    if (!HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        return true;
                    }
                    HeadsetStateMachine.this.broadcastConnectionState(device, 3, 2);
                    if (!HeadsetStateMachine.this.disconnectHfpNative(HeadsetStateMachine.this.getByteAddress(device))) {
                        HeadsetStateMachine.this.broadcastConnectionState(device, 2, 0);
                        return true;
                    } else if (HeadsetStateMachine.this.mConnectedDevicesList.size() > 1) {
                        HeadsetStateMachine.this.mMultiDisconnectDevice = device;
                        HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mMultiHFPending);
                        return true;
                    } else {
                        HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mPending);
                        return true;
                    }
                case 3:
                    device = HeadsetStateMachine.this.mCurrentDevice;
                    if (HeadsetStateMachine.this.mActiveScoDevice != null) {
                        HeadsetStateMachine.this.log("connectAudioNative in Connected; mActiveScoDevice is not null");
                        device = HeadsetStateMachine.this.mActiveScoDevice;
                    }
                    HeadsetStateMachine.this.log("connectAudioNative in Connected for device = " + device);
                    HeadsetStateMachine.this.connectAudioNative(HeadsetStateMachine.this.getByteAddress(device));
                    return true;
                case 5:
                    HeadsetStateMachine.this.processLocalVrEvent(1);
                    return true;
                case 6:
                    HeadsetStateMachine.this.processLocalVrEvent(0);
                    return true;
                case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                    HeadsetStateMachine.this.processCallState((HeadsetCallState) message.obj, message.arg1 == 1);
                    return true;
                case 10:
                    HeadsetStateMachine.this.processIntentBatteryChanged((Intent) message.obj);
                    return true;
                case 11:
                    HeadsetStateMachine.this.processDeviceStateChanged((HeadsetDeviceState) message.obj);
                    return true;
                case 12:
                    HeadsetStateMachine.this.processSendClccResponse((HeadsetClccResponse) message.obj);
                    return true;
                case BluetoothCmeError.SIM_FAILURE /*13*/:
                    HeadsetStateMachine.this.processSendVendorSpecificResultCode((HeadsetVendorSpecificResultCode) message.obj);
                    return true;
                case BluetoothCmeError.SIM_BUSY /*14*/:
                    HeadsetStateMachine.this.initiateScoUsingVirtualVoiceCall();
                    return true;
                case VCardConstants.MAX_DATA_COLUMN /*15*/:
                    HeadsetStateMachine.this.terminateScoUsingVirtualVoiceCall();
                    return true;
                case BluetoothCmeError.WRONG_PASSWORD /*16*/:
                    HeadsetStateMachine.this.configureWBSNative(HeadsetStateMachine.this.getByteAddress((BluetoothDevice) message.obj), 2);
                    return true;
                case BluetoothCmeError.SIM_PIN2_REQUIRED /*17*/:
                    HeadsetStateMachine.this.configureWBSNative(HeadsetStateMachine.this.getByteAddress((BluetoothDevice) message.obj), 1);
                    return true;
                case HeadsetStateMachine.STACK_EVENT /*101*/:
                    StackEvent event = message.obj;
                    HeadsetStateMachine.this.log("event type: " + event.type + "event device : " + event.device);
                    switch (event.type) {
                        case 1:
                            processConnectionEvent(event.valueInt, event.device);
                            return true;
                        case 2:
                            processAudioEvent(event.valueInt, event.device);
                            return true;
                        case 3:
                            HeadsetStateMachine.this.processVrEvent(event.valueInt, event.device);
                            return true;
                        case 4:
                            HeadsetStateMachine.this.processAnswerCall(event.device);
                            return true;
                        case 5:
                            HeadsetStateMachine.this.processHangupCall(event.device);
                            return true;
                        case 6:
                            HeadsetStateMachine.this.processVolumeEvent(event.valueInt, event.valueInt2, event.device);
                            return true;
                        case AbstractionLayer.BT_STATUS_PARM_INVALID /*7*/:
                            HeadsetStateMachine.this.processDialCall(event.valueString, event.device);
                            return true;
                        case 8:
                            HeadsetStateMachine.this.processSendDtmf(event.valueInt, event.device);
                            return true;
                        case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                            HeadsetStateMachine.this.processNoiceReductionEvent(event.valueInt, event.device);
                            return true;
                        case 10:
                            HeadsetStateMachine.this.processAtChld(event.valueInt, event.device);
                            return true;
                        case 11:
                            HeadsetStateMachine.this.processSubscriberNumberRequest(event.device);
                            return true;
                        case 12:
                            HeadsetStateMachine.this.processAtCind(event.device);
                            return true;
                        case BluetoothCmeError.SIM_FAILURE /*13*/:
                            HeadsetStateMachine.this.processAtCops(event.device);
                            return true;
                        case BluetoothCmeError.SIM_BUSY /*14*/:
                            HeadsetStateMachine.this.processAtClcc(event.device);
                            return true;
                        case VCardConstants.MAX_DATA_COLUMN /*15*/:
                            HeadsetStateMachine.this.processUnknownAt(event.valueString, event.device);
                            return true;
                        case BluetoothCmeError.WRONG_PASSWORD /*16*/:
                            HeadsetStateMachine.this.processKeyPressed(event.device);
                            return true;
                        case BluetoothCmeError.SIM_PIN2_REQUIRED /*17*/:
                            Log.d(HeadsetStateMachine.TAG, "EVENT_TYPE_WBS codec is " + event.valueInt);
                            HeadsetStateMachine.this.processWBSEvent(event.valueInt, event.device);
                            return true;
                        default:
                            Log.e(HeadsetStateMachine.TAG, "Unknown stack event: " + event.type);
                            return true;
                    }
                case HeadsetStateMachine.DIALING_OUT_TIMEOUT /*102*/:
                    device = (BluetoothDevice) message.obj;
                    if (!HeadsetStateMachine.this.mDialingOut) {
                        return true;
                    }
                    HeadsetStateMachine.this.mDialingOut = false;
                    HeadsetStateMachine.this.atResponseCodeNative(0, 0, HeadsetStateMachine.this.getByteAddress(device));
                    return true;
                case HeadsetStateMachine.START_VR_TIMEOUT /*103*/:
                    device = (BluetoothDevice) message.obj;
                    if (!HeadsetStateMachine.this.mWaitingForVoiceRecognition) {
                        return true;
                    }
                    device = message.obj;
                    HeadsetStateMachine.this.mWaitingForVoiceRecognition = false;
                    Log.e(HeadsetStateMachine.TAG, "Timeout waiting for voice recognition to start");
                    HeadsetStateMachine.this.atResponseCodeNative(0, 0, HeadsetStateMachine.this.getByteAddress(device));
                    return true;
                case HeadsetStateMachine.CLCC_RSP_TIMEOUT /*104*/:
                    HeadsetStateMachine.this.clccResponseNative(0, 0, 0, 0, false, "", 0, HeadsetStateMachine.this.getByteAddress((BluetoothDevice) message.obj));
                    return true;
                default:
                    return false;
            }
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            Log.d(HeadsetStateMachine.TAG, "processConnectionEvent state = " + state + ", device = " + device);
            switch (state) {
                case 0:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        HeadsetStateMachine.this.processWBSEvent(0, device);
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mConnectedDevicesList.remove(device);
                            HeadsetStateMachine.this.mHeadsetAudioParam.remove(device);
                            HeadsetStateMachine.this.mHeadsetBrsf.remove(device);
                            Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is removed in Connected state");
                            if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0) {
                                HeadsetStateMachine.this.mCurrentDevice = null;
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mDisconnected);
                            } else {
                                processMultiHFConnected(device);
                            }
                        }
                        HeadsetStateMachine.this.broadcastConnectionState(device, 0, 2);
                        return;
                    }
                    Log.e(HeadsetStateMachine.TAG, "Disconnected from unknown device: " + device);
                    return;
                case 2:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        HeadsetStateMachine.this.mIncomingDevice = null;
                        HeadsetStateMachine.this.mTargetDevice = null;
                        return;
                    }
                    Log.w(HeadsetStateMachine.TAG, "HFP to be Connected in Connected state");
                    if (!HeadsetStateMachine.this.okToConnect(device) || HeadsetStateMachine.this.mConnectedDevicesList.size() >= HeadsetStateMachine.this.max_hf_connections) {
                        Log.i(HeadsetStateMachine.TAG, "Incoming Hf rejected. priority=" + HeadsetStateMachine.this.mService.getPriority(device) + " bondState=" + device.getBondState());
                        HeadsetStateMachine.this.disconnectHfpNative(HeadsetStateMachine.this.getByteAddress(device));
                        AdapterService adapterService = AdapterService.getAdapterService();
                        if (adapterService != null) {
                            adapterService.connectOtherProfile(device, 2);
                            return;
                        }
                        return;
                    }
                    Log.i(HeadsetStateMachine.TAG, "Incoming Hf accepted");
                    HeadsetStateMachine.this.broadcastConnectionState(device, 2, 0);
                    synchronized (HeadsetStateMachine.this) {
                        if (!HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                            HeadsetStateMachine.this.mCurrentDevice = device;
                            HeadsetStateMachine.this.mConnectedDevicesList.add(device);
                            Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is added in Connected state");
                        }
                        HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                    }
                    HeadsetStateMachine.this.configAudioParameters(device);
                    return;
                case 3:
                    processSlcConnected();
                    return;
                default:
                    Log.e(HeadsetStateMachine.TAG, "Connection State Device: " + device + " bad state: " + state);
                    return;
            }
        }

        private void processAudioEvent(int state, BluetoothDevice device) {
            if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                switch (state) {
                    case 1:
                        HeadsetStateMachine.this.mAudioState = 11;
                        HeadsetStateMachine.this.broadcastAudioState(device, 11, 10);
                        return;
                    case 2:
                        HeadsetStateMachine.this.mAudioState = 12;
                        HeadsetStateMachine.this.setAudioParameters(device);
                        HeadsetStateMachine.this.mAudioManager.setBluetoothScoOn(true);
                        HeadsetStateMachine.this.broadcastAudioState(device, 12, 11);
                        HeadsetStateMachine.this.mActiveScoDevice = device;
                        HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mAudioOn);
                        return;
                    default:
                        Log.e(HeadsetStateMachine.TAG, "Audio State Device: " + device + " bad state: " + state);
                        return;
                }
            }
            Log.e(HeadsetStateMachine.TAG, "Audio changed on disconnected device: " + device);
        }

        private void processSlcConnected() {
            if (HeadsetStateMachine.this.mPhoneProxy != null) {
                try {
                    HeadsetStateMachine.this.mPhoneProxy.queryPhoneState();
                    return;
                } catch (RemoteException e) {
                    Log.e(HeadsetStateMachine.TAG, Log.getStackTraceString(new Throwable()));
                    return;
                }
            }
            Log.e(HeadsetStateMachine.TAG, "Handsfree phone proxy null for query phone state");
        }

        private void processMultiHFConnected(BluetoothDevice device) {
            HeadsetStateMachine.this.log("Connect state: processMultiHFConnected");
            if (HeadsetStateMachine.this.mActiveScoDevice != null && HeadsetStateMachine.this.mActiveScoDevice.equals(device)) {
                HeadsetStateMachine.this.log("mActiveScoDevice is disconnected, setting it to null");
                HeadsetStateMachine.this.mActiveScoDevice = null;
            }
            if (HeadsetStateMachine.this.mCurrentDevice == null || !HeadsetStateMachine.this.mCurrentDevice.equals(device)) {
                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
            } else {
                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                HeadsetStateMachine.this.mCurrentDevice = (BluetoothDevice) HeadsetStateMachine.this.mConnectedDevicesList.get(HeadsetStateMachine.this.mConnectedDevicesList.size() - 1);
            }
            HeadsetStateMachine.this.log("processMultiHFConnected , the latest mCurrentDevice is:" + HeadsetStateMachine.this.mCurrentDevice);
            HeadsetStateMachine.this.log("Connect state: processMultiHFConnected ,fake broadcasting for mCurrentDevice");
            HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mCurrentDevice, 2, 0);
        }
    }

    private class Disconnected extends State {
        private Disconnected() {
        }

        public void enter() {
            HeadsetStateMachine.this.log("Enter Disconnected: " + HeadsetStateMachine.this.getCurrentMessage().what + ", size: " + HeadsetStateMachine.this.mConnectedDevicesList.size());
            HeadsetStateMachine.this.mPhonebook.resetAtState();
            HeadsetStateMachine.this.mPhoneState.listenForPhoneState(false);
            HeadsetStateMachine.this.mVoiceRecognitionStarted = false;
            HeadsetStateMachine.this.mWaitingForVoiceRecognition = false;
        }

        public boolean processMessage(Message message) {
            boolean z = true;
            HeadsetStateMachine.this.log("Disconnected process message: " + message.what + ", size: " + HeadsetStateMachine.this.mConnectedDevicesList.size());
            if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0 && HeadsetStateMachine.this.mTargetDevice == null && HeadsetStateMachine.this.mIncomingDevice == null) {
                switch (message.what) {
                    case 1:
                        BluetoothDevice device = message.obj;
                        HeadsetStateMachine.this.broadcastConnectionState(device, 1, 0);
                        if (!HeadsetStateMachine.this.connectHfpNative(HeadsetStateMachine.this.getByteAddress(device))) {
                            HeadsetStateMachine.this.broadcastConnectionState(device, 0, 1);
                            break;
                        }
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mTargetDevice = device;
                            HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mPending);
                        }
                        Message m = HeadsetStateMachine.this.obtainMessage(HeadsetStateMachine.CONNECT_TIMEOUT);
                        m.obj = device;
                        HeadsetStateMachine.this.sendMessageDelayed(m, 30000);
                        break;
                    case 2:
                        break;
                    case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                        HeadsetStateMachine headsetStateMachine = HeadsetStateMachine.this;
                        HeadsetCallState headsetCallState = (HeadsetCallState) message.obj;
                        if (message.arg1 != 1) {
                            z = false;
                        }
                        headsetStateMachine.processCallState(headsetCallState, z);
                        break;
                    case 10:
                        HeadsetStateMachine.this.processIntentBatteryChanged((Intent) message.obj);
                        break;
                    case HeadsetStateMachine.STACK_EVENT /*101*/:
                        StackEvent event = message.obj;
                        HeadsetStateMachine.this.log("event type: " + event.type);
                        switch (event.type) {
                            case 1:
                                processConnectionEvent(event.valueInt, event.device);
                                break;
                            default:
                                Log.e(HeadsetStateMachine.TAG, "Unexpected stack event: " + event.type);
                                break;
                        }
                    default:
                        return false;
                }
                return true;
            }
            Log.e(HeadsetStateMachine.TAG, "ERROR: mConnectedDevicesList is not empty,target, or mIncomingDevice not null in Disconnected");
            return false;
        }

        public void exit() {
            HeadsetStateMachine.this.log("Exit Disconnected: " + HeadsetStateMachine.this.getCurrentMessage().what);
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            Log.d(HeadsetStateMachine.TAG, "processConnectionEvent state = " + state + ", device = " + device);
            AdapterService adapterService;
            switch (state) {
                case 0:
                    Log.w(HeadsetStateMachine.TAG, "Ignore HF DISCONNECTED event, device: " + device);
                    return;
                case 1:
                    if (HeadsetStateMachine.this.okToConnect(device)) {
                        Log.i(HeadsetStateMachine.TAG, "Incoming Hf accepted");
                        HeadsetStateMachine.this.broadcastConnectionState(device, 1, 0);
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mIncomingDevice = device;
                            HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mPending);
                        }
                        return;
                    }
                    Log.i(HeadsetStateMachine.TAG, "Incoming Hf rejected. priority=" + HeadsetStateMachine.this.mService.getPriority(device) + " bondState=" + device.getBondState());
                    HeadsetStateMachine.this.disconnectHfpNative(HeadsetStateMachine.this.getByteAddress(device));
                    adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device, 2);
                        return;
                    }
                    return;
                case 2:
                    Log.w(HeadsetStateMachine.TAG, "HFP Connected from Disconnected state");
                    if (HeadsetStateMachine.this.okToConnect(device)) {
                        Log.i(HeadsetStateMachine.TAG, "Incoming Hf accepted");
                        HeadsetStateMachine.this.broadcastConnectionState(device, 2, 0);
                        synchronized (HeadsetStateMachine.this) {
                            if (!HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                                HeadsetStateMachine.this.mConnectedDevicesList.add(device);
                                Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is adding in Disconnected state");
                            }
                            HeadsetStateMachine.this.mCurrentDevice = device;
                            HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                        }
                        HeadsetStateMachine.this.configAudioParameters(device);
                        return;
                    }
                    Log.i(HeadsetStateMachine.TAG, "Incoming Hf rejected. priority=" + HeadsetStateMachine.this.mService.getPriority(device) + " bondState=" + device.getBondState());
                    HeadsetStateMachine.this.disconnectHfpNative(HeadsetStateMachine.this.getByteAddress(device));
                    adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device, 2);
                        return;
                    }
                    return;
                case 4:
                    Log.w(HeadsetStateMachine.TAG, "Ignore HF DISCONNECTING event, device: " + device);
                    return;
                default:
                    Log.e(HeadsetStateMachine.TAG, "Incorrect state: " + state);
                    return;
            }
        }
    }

    private class MultiHFPending extends State {
        private MultiHFPending() {
        }

        public void enter() {
            HeadsetStateMachine.this.log("Enter MultiHFPending: " + HeadsetStateMachine.this.getCurrentMessage().what + ", size: " + HeadsetStateMachine.this.mConnectedDevicesList.size());
        }

        public boolean processMessage(Message message) {
            boolean z = false;
            HeadsetStateMachine.this.log("MultiHFPending process message: " + message.what + ", size: " + HeadsetStateMachine.this.mConnectedDevicesList.size());
            BluetoothDevice device;
            switch (message.what) {
                case 1:
                    HeadsetStateMachine.this.deferMessage(message);
                    break;
                case 2:
                    device = message.obj;
                    if (!HeadsetStateMachine.this.mConnectedDevicesList.contains(device) || HeadsetStateMachine.this.mTargetDevice == null || !HeadsetStateMachine.this.mTargetDevice.equals(device)) {
                        HeadsetStateMachine.this.deferMessage(message);
                        break;
                    }
                    HeadsetStateMachine.this.broadcastConnectionState(device, 0, 1);
                    synchronized (HeadsetStateMachine.this) {
                        HeadsetStateMachine.this.mTargetDevice = null;
                    }
                    break;
                    break;
                case 3:
                    if (HeadsetStateMachine.this.mCurrentDevice != null) {
                        HeadsetStateMachine.this.connectAudioNative(HeadsetStateMachine.this.getByteAddress(HeadsetStateMachine.this.mCurrentDevice));
                        break;
                    }
                    break;
                case 4:
                    if (HeadsetStateMachine.this.mActiveScoDevice != null) {
                        if (!HeadsetStateMachine.this.disconnectAudioNative(HeadsetStateMachine.this.getByteAddress(HeadsetStateMachine.this.mActiveScoDevice))) {
                            Log.e(HeadsetStateMachine.TAG, "disconnectAudioNative failedfor device = " + HeadsetStateMachine.this.mActiveScoDevice);
                            break;
                        }
                        Log.d(HeadsetStateMachine.TAG, "MultiHFPending, Disconnecting SCO audio for " + HeadsetStateMachine.this.mActiveScoDevice);
                        break;
                    }
                    break;
                case 5:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains((BluetoothDevice) message.obj)) {
                        HeadsetStateMachine.this.processLocalVrEvent(1);
                        break;
                    }
                    break;
                case 6:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains((BluetoothDevice) message.obj)) {
                        HeadsetStateMachine.this.processLocalVrEvent(0);
                        break;
                    }
                    break;
                case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                    HeadsetStateMachine headsetStateMachine = HeadsetStateMachine.this;
                    HeadsetCallState headsetCallState = (HeadsetCallState) message.obj;
                    if (message.arg1 == 1) {
                        z = true;
                    }
                    headsetStateMachine.processCallState(headsetCallState, z);
                    break;
                case 10:
                    HeadsetStateMachine.this.processIntentBatteryChanged((Intent) message.obj);
                    break;
                case 11:
                    HeadsetStateMachine.this.processDeviceStateChanged((HeadsetDeviceState) message.obj);
                    break;
                case 12:
                    HeadsetStateMachine.this.processSendClccResponse((HeadsetClccResponse) message.obj);
                    break;
                case BluetoothCmeError.SIM_BUSY /*14*/:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains((BluetoothDevice) message.obj)) {
                        HeadsetStateMachine.this.initiateScoUsingVirtualVoiceCall();
                        break;
                    }
                    break;
                case VCardConstants.MAX_DATA_COLUMN /*15*/:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains((BluetoothDevice) message.obj)) {
                        HeadsetStateMachine.this.terminateScoUsingVirtualVoiceCall();
                        break;
                    }
                    break;
                case HeadsetStateMachine.STACK_EVENT /*101*/:
                    StackEvent event = message.obj;
                    HeadsetStateMachine.this.log("event type: " + event.type);
                    switch (event.type) {
                        case 1:
                            BluetoothDevice device1 = HeadsetStateMachine.this.getDeviceForMessage(HeadsetStateMachine.CONNECT_TIMEOUT);
                            if (device1 != null && device1.equals(event.device)) {
                                Log.d(HeadsetStateMachine.TAG, "remove connect timeout for device = " + device1);
                                HeadsetStateMachine.this.removeMessages(HeadsetStateMachine.CONNECT_TIMEOUT);
                            }
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case 2:
                            processAudioEvent(event.valueInt, event.device);
                            break;
                        case 3:
                            HeadsetStateMachine.this.processVrEvent(event.valueInt, event.device);
                            break;
                        case 4:
                            HeadsetStateMachine.this.processAnswerCall(event.device);
                            break;
                        case 5:
                            HeadsetStateMachine.this.processHangupCall(event.device);
                            break;
                        case 6:
                            HeadsetStateMachine.this.processVolumeEvent(event.valueInt, event.valueInt2, event.device);
                            break;
                        case AbstractionLayer.BT_STATUS_PARM_INVALID /*7*/:
                            HeadsetStateMachine.this.processDialCall(event.valueString, event.device);
                            break;
                        case 8:
                            HeadsetStateMachine.this.processSendDtmf(event.valueInt, event.device);
                            break;
                        case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                            HeadsetStateMachine.this.processNoiceReductionEvent(event.valueInt, event.device);
                            break;
                        case 10:
                            HeadsetStateMachine.this.processAtChld(event.valueInt, event.device);
                            break;
                        case 11:
                            HeadsetStateMachine.this.processSubscriberNumberRequest(event.device);
                            break;
                        case 12:
                            HeadsetStateMachine.this.processAtCind(event.device);
                            break;
                        case BluetoothCmeError.SIM_FAILURE /*13*/:
                            HeadsetStateMachine.this.processAtCops(event.device);
                            break;
                        case BluetoothCmeError.SIM_BUSY /*14*/:
                            HeadsetStateMachine.this.processAtClcc(event.device);
                            break;
                        case VCardConstants.MAX_DATA_COLUMN /*15*/:
                            HeadsetStateMachine.this.processUnknownAt(event.valueString, event.device);
                            break;
                        case BluetoothCmeError.WRONG_PASSWORD /*16*/:
                            HeadsetStateMachine.this.processKeyPressed(event.device);
                            break;
                        default:
                            Log.e(HeadsetStateMachine.TAG, "Unexpected event: " + event.type);
                            break;
                    }
                case HeadsetStateMachine.DIALING_OUT_TIMEOUT /*102*/:
                    if (HeadsetStateMachine.this.mDialingOut) {
                        device = (BluetoothDevice) message.obj;
                        HeadsetStateMachine.this.mDialingOut = false;
                        HeadsetStateMachine.this.atResponseCodeNative(0, 0, HeadsetStateMachine.this.getByteAddress(device));
                        break;
                    }
                    break;
                case HeadsetStateMachine.START_VR_TIMEOUT /*103*/:
                    if (HeadsetStateMachine.this.mWaitingForVoiceRecognition) {
                        device = (BluetoothDevice) message.obj;
                        HeadsetStateMachine.this.mWaitingForVoiceRecognition = false;
                        Log.e(HeadsetStateMachine.TAG, "Timeout waiting for voicerecognition to start");
                        HeadsetStateMachine.this.atResponseCodeNative(0, 0, HeadsetStateMachine.this.getByteAddress(device));
                        break;
                    }
                    break;
                case HeadsetStateMachine.CLCC_RSP_TIMEOUT /*104*/:
                    int i = 0;
                    int i2 = 0;
                    int i3 = 0;
                    boolean z2 = false;
                    HeadsetStateMachine.this.clccResponseNative(0, i, i2, i3, z2, "", 0, HeadsetStateMachine.this.getByteAddress((BluetoothDevice) message.obj));
                    break;
                case HeadsetStateMachine.CONNECT_TIMEOUT /*201*/:
                    HeadsetStateMachine.this.onConnectionStateChanged(0, HeadsetStateMachine.this.getByteAddress(HeadsetStateMachine.this.mTargetDevice));
                    break;
                default:
                    return false;
            }
            return true;
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            Log.d(HeadsetStateMachine.TAG, "processConnectionEvent state = " + state + ", device = " + device);
            switch (state) {
                case 0:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        if (HeadsetStateMachine.this.mMultiDisconnectDevice == null || !HeadsetStateMachine.this.mMultiDisconnectDevice.equals(device)) {
                            synchronized (HeadsetStateMachine.this) {
                                HeadsetStateMachine.this.mConnectedDevicesList.remove(device);
                                HeadsetStateMachine.this.mHeadsetAudioParam.remove(device);
                                HeadsetStateMachine.this.mHeadsetBrsf.remove(device);
                                Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is removed in MultiHFPending state");
                            }
                            HeadsetStateMachine.this.broadcastConnectionState(device, 0, 2);
                            return;
                        }
                        HeadsetStateMachine.this.mMultiDisconnectDevice = null;
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mConnectedDevicesList.remove(device);
                            HeadsetStateMachine.this.mHeadsetAudioParam.remove(device);
                            HeadsetStateMachine.this.mHeadsetBrsf.remove(device);
                            Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is removed in MultiHFPending state");
                            HeadsetStateMachine.this.broadcastConnectionState(device, 0, 3);
                        }
                        if (HeadsetStateMachine.this.mTargetDevice == null) {
                            synchronized (HeadsetStateMachine.this) {
                                HeadsetStateMachine.this.mIncomingDevice = null;
                                if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0) {
                                    HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mDisconnected);
                                } else {
                                    processMultiHFConnected(device);
                                }
                            }
                            return;
                        } else if (!HeadsetStateMachine.this.connectHfpNative(HeadsetStateMachine.this.getByteAddress(HeadsetStateMachine.this.mTargetDevice))) {
                            HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mTargetDevice, 0, 1);
                            synchronized (HeadsetStateMachine.this) {
                                HeadsetStateMachine.this.mTargetDevice = null;
                                if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0) {
                                    Log.d(HeadsetStateMachine.TAG, "Should be not in this state, error handling");
                                    HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mDisconnected);
                                } else {
                                    processMultiHFConnected(device);
                                }
                            }
                            return;
                        } else {
                            return;
                        }
                    } else if (HeadsetStateMachine.this.mTargetDevice == null || !HeadsetStateMachine.this.mTargetDevice.equals(device)) {
                        Log.e(HeadsetStateMachine.TAG, "Unknown device Disconnected: " + device);
                        return;
                    } else {
                        HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mTargetDevice, 0, 1);
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mTargetDevice = null;
                            if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0) {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mDisconnected);
                            } else if (HeadsetStateMachine.this.mAudioState == 12) {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mAudioOn);
                            } else {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                            }
                        }
                        return;
                    }
                case 1:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        Log.e(HeadsetStateMachine.TAG, "current device tries to connect back");
                        return;
                    } else if (HeadsetStateMachine.this.mTargetDevice != null && HeadsetStateMachine.this.mTargetDevice.equals(device)) {
                        HeadsetStateMachine.this.log("Stack and target device are connecting");
                        return;
                    } else if (HeadsetStateMachine.this.mIncomingDevice != null && HeadsetStateMachine.this.mIncomingDevice.equals(device)) {
                        Log.e(HeadsetStateMachine.TAG, "Another connecting event onthe incoming device");
                        return;
                    } else {
                        return;
                    }
                case 2:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        HeadsetStateMachine.this.broadcastConnectionState(device, 2, 3);
                        if (HeadsetStateMachine.this.mTargetDevice != null) {
                            HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mTargetDevice, 0, 1);
                        }
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mTargetDevice = null;
                            if (HeadsetStateMachine.this.mAudioState == 12) {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mAudioOn);
                            } else {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                            }
                        }
                        return;
                    } else if (HeadsetStateMachine.this.mTargetDevice == null || !HeadsetStateMachine.this.mTargetDevice.equals(device)) {
                        Log.w(HeadsetStateMachine.TAG, "Some other incoming HF connectedin Multi Pending state");
                        if (!HeadsetStateMachine.this.okToConnect(device) || HeadsetStateMachine.this.mConnectedDevicesList.size() >= HeadsetStateMachine.this.max_hf_connections) {
                            Log.i(HeadsetStateMachine.TAG, "Incoming Hf rejected. priority=" + HeadsetStateMachine.this.mService.getPriority(device) + " bondState=" + device.getBondState());
                            HeadsetStateMachine.this.disconnectHfpNative(HeadsetStateMachine.this.getByteAddress(device));
                            AdapterService adapterService = AdapterService.getAdapterService();
                            if (adapterService != null) {
                                adapterService.connectOtherProfile(device, 2);
                                return;
                            }
                            return;
                        }
                        Log.i(HeadsetStateMachine.TAG, "Incoming Hf accepted");
                        HeadsetStateMachine.this.broadcastConnectionState(device, 2, 0);
                        synchronized (HeadsetStateMachine.this) {
                            if (!HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                                HeadsetStateMachine.this.mCurrentDevice = device;
                                HeadsetStateMachine.this.mConnectedDevicesList.add(device);
                                Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is added in MultiHFPending state");
                            }
                        }
                        HeadsetStateMachine.this.configAudioParameters(device);
                        return;
                    } else {
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mCurrentDevice = device;
                            HeadsetStateMachine.this.mConnectedDevicesList.add(device);
                            Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is added in MultiHFPending state");
                            HeadsetStateMachine.this.mTargetDevice = null;
                            if (HeadsetStateMachine.this.mAudioState == 12) {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mAudioOn);
                            } else {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                            }
                        }
                        HeadsetStateMachine.this.broadcastConnectionState(device, 2, 1);
                        HeadsetStateMachine.this.configAudioParameters(device);
                        return;
                    }
                case 4:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        HeadsetStateMachine.this.log("stack is disconnecting mCurrentDevice");
                        return;
                    } else if (HeadsetStateMachine.this.mTargetDevice != null && HeadsetStateMachine.this.mTargetDevice.equals(device)) {
                        Log.e(HeadsetStateMachine.TAG, "TargetDevice is getting disconnected");
                        return;
                    } else if (HeadsetStateMachine.this.mIncomingDevice == null || !HeadsetStateMachine.this.mIncomingDevice.equals(device)) {
                        Log.e(HeadsetStateMachine.TAG, "Disconnecting unknow device: " + device);
                        return;
                    } else {
                        Log.e(HeadsetStateMachine.TAG, "IncomingDevice is getting disconnected");
                        return;
                    }
                default:
                    Log.e(HeadsetStateMachine.TAG, "Incorrect state: " + state);
                    return;
            }
        }

        private void processAudioEvent(int state, BluetoothDevice device) {
            if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                switch (state) {
                    case 0:
                        if (HeadsetStateMachine.this.mAudioState != 10) {
                            HeadsetStateMachine.this.mAudioState = 10;
                            HeadsetStateMachine.this.mAudioManager.setBluetoothScoOn(false);
                            HeadsetStateMachine.this.broadcastAudioState(device, 10, 12);
                            return;
                        }
                        return;
                    case 1:
                        HeadsetStateMachine.this.mAudioState = 11;
                        HeadsetStateMachine.this.broadcastAudioState(device, 11, 10);
                        return;
                    case 2:
                        HeadsetStateMachine.this.mAudioState = 12;
                        HeadsetStateMachine.this.setAudioParameters(device);
                        HeadsetStateMachine.this.mAudioManager.setBluetoothScoOn(true);
                        HeadsetStateMachine.this.mActiveScoDevice = device;
                        HeadsetStateMachine.this.broadcastAudioState(device, 12, 11);
                        return;
                    default:
                        Log.e(HeadsetStateMachine.TAG, "Audio State Device: " + device + " bad state: " + state);
                        return;
                }
            }
            Log.e(HeadsetStateMachine.TAG, "Audio changed on disconnected device: " + device);
        }

        private void processMultiHFConnected(BluetoothDevice device) {
            HeadsetStateMachine.this.log("MultiHFPending state: processMultiHFConnected");
            if (HeadsetStateMachine.this.mActiveScoDevice != null && HeadsetStateMachine.this.mActiveScoDevice.equals(device)) {
                HeadsetStateMachine.this.log("mActiveScoDevice is disconnected, setting it to null");
                HeadsetStateMachine.this.mActiveScoDevice = null;
            }
            if (HeadsetStateMachine.this.mCurrentDevice != null && HeadsetStateMachine.this.mCurrentDevice.equals(device)) {
                HeadsetStateMachine.this.mCurrentDevice = (BluetoothDevice) HeadsetStateMachine.this.mConnectedDevicesList.get(HeadsetStateMachine.this.mConnectedDevicesList.size() - 1);
            }
            if (HeadsetStateMachine.this.mAudioState == 12) {
                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mAudioOn);
            } else {
                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
            }
            HeadsetStateMachine.this.log("processMultiHFConnected , the latest mCurrentDevice is:" + HeadsetStateMachine.this.mCurrentDevice);
            HeadsetStateMachine.this.log("MultiHFPending state: processMultiHFConnected ,fake broadcasting for mCurrentDevice");
            HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mCurrentDevice, 2, 0);
        }
    }

    private class Pending extends State {
        private Pending() {
        }

        public void enter() {
            HeadsetStateMachine.this.log("Enter Pending: " + HeadsetStateMachine.this.getCurrentMessage().what);
        }

        public boolean processMessage(Message message) {
            boolean z = true;
            HeadsetStateMachine.this.log("Pending process message: " + message.what + ", size: " + HeadsetStateMachine.this.mConnectedDevicesList.size());
            switch (message.what) {
                case 1:
                case 3:
                    HeadsetStateMachine.this.deferMessage(message);
                    break;
                case 2:
                    BluetoothDevice device = message.obj;
                    if (HeadsetStateMachine.this.mCurrentDevice != null && HeadsetStateMachine.this.mTargetDevice != null && HeadsetStateMachine.this.mTargetDevice.equals(device)) {
                        HeadsetStateMachine.this.broadcastConnectionState(device, 0, 1);
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mTargetDevice = null;
                        }
                        break;
                    }
                    HeadsetStateMachine.this.deferMessage(message);
                    break;
                case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                    HeadsetStateMachine headsetStateMachine = HeadsetStateMachine.this;
                    HeadsetCallState headsetCallState = (HeadsetCallState) message.obj;
                    if (message.arg1 != 1) {
                        z = false;
                    }
                    headsetStateMachine.processCallState(headsetCallState, z);
                    break;
                case 10:
                    HeadsetStateMachine.this.processIntentBatteryChanged((Intent) message.obj);
                    break;
                case HeadsetStateMachine.STACK_EVENT /*101*/:
                    StackEvent event = message.obj;
                    HeadsetStateMachine.this.log("event type: " + event.type);
                    switch (event.type) {
                        case 1:
                            BluetoothDevice device1 = HeadsetStateMachine.this.getDeviceForMessage(HeadsetStateMachine.CONNECT_TIMEOUT);
                            if (device1 != null && device1.equals(event.device)) {
                                Log.d(HeadsetStateMachine.TAG, "remove connect timeout for device = " + device1);
                                HeadsetStateMachine.this.removeMessages(HeadsetStateMachine.CONNECT_TIMEOUT);
                            }
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        default:
                            Log.e(HeadsetStateMachine.TAG, "Unexpected event: " + event.type);
                            break;
                    }
                case HeadsetStateMachine.CONNECT_TIMEOUT /*201*/:
                    HeadsetStateMachine.this.onConnectionStateChanged(0, HeadsetStateMachine.this.getByteAddress(HeadsetStateMachine.this.mTargetDevice));
                    break;
                default:
                    return false;
            }
            return true;
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            Log.d(HeadsetStateMachine.TAG, "processConnectionEvent state = " + state + ", device = " + device);
            switch (state) {
                case 0:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mConnectedDevicesList.remove(device);
                            HeadsetStateMachine.this.mHeadsetAudioParam.remove(device);
                            HeadsetStateMachine.this.mHeadsetBrsf.remove(device);
                            Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is removed in Pending state");
                        }
                        HeadsetStateMachine.this.broadcastConnectionState(device, 0, 3);
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mCurrentDevice = null;
                        }
                        HeadsetStateMachine.this.processWBSEvent(0, device);
                        if (HeadsetStateMachine.this.mTargetDevice == null) {
                            synchronized (HeadsetStateMachine.this) {
                                HeadsetStateMachine.this.mIncomingDevice = null;
                                if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0) {
                                    HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mDisconnected);
                                } else {
                                    processMultiHFConnected(device);
                                }
                            }
                            return;
                        } else if (!HeadsetStateMachine.this.connectHfpNative(HeadsetStateMachine.this.getByteAddress(HeadsetStateMachine.this.mTargetDevice))) {
                            HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mTargetDevice, 0, 1);
                            synchronized (HeadsetStateMachine.this) {
                                HeadsetStateMachine.this.mTargetDevice = null;
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mDisconnected);
                            }
                            return;
                        } else {
                            return;
                        }
                    } else if (HeadsetStateMachine.this.mTargetDevice != null && HeadsetStateMachine.this.mTargetDevice.equals(device)) {
                        HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mTargetDevice, 0, 1);
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mTargetDevice = null;
                            if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0) {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mDisconnected);
                            } else {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                            }
                        }
                        return;
                    } else if (HeadsetStateMachine.this.mIncomingDevice == null || !HeadsetStateMachine.this.mIncomingDevice.equals(device)) {
                        Log.e(HeadsetStateMachine.TAG, "Unknown device Disconnected: " + device);
                        return;
                    } else {
                        HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mIncomingDevice, 0, 1);
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mIncomingDevice = null;
                            if (HeadsetStateMachine.this.mConnectedDevicesList.size() == 0) {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mDisconnected);
                            } else {
                                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                            }
                        }
                        return;
                    }
                case 1:
                    if (HeadsetStateMachine.this.mCurrentDevice != null && HeadsetStateMachine.this.mCurrentDevice.equals(device)) {
                        HeadsetStateMachine.this.log("current device tries to connect back");
                        return;
                    } else if (HeadsetStateMachine.this.mTargetDevice != null && HeadsetStateMachine.this.mTargetDevice.equals(device)) {
                        HeadsetStateMachine.this.log("Stack and target device are connecting");
                        return;
                    } else if (HeadsetStateMachine.this.mIncomingDevice == null || !HeadsetStateMachine.this.mIncomingDevice.equals(device)) {
                        HeadsetStateMachine.this.log("Incoming connection while pending, ignore");
                        return;
                    } else {
                        Log.e(HeadsetStateMachine.TAG, "Another connecting event on the incoming device");
                        return;
                    }
                case 2:
                    if (HeadsetStateMachine.this.mConnectedDevicesList.contains(device)) {
                        HeadsetStateMachine.this.broadcastConnectionState(device, 2, 3);
                        if (HeadsetStateMachine.this.mTargetDevice != null) {
                            HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mTargetDevice, 0, 1);
                        }
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mTargetDevice = null;
                            HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                        }
                        return;
                    } else if (HeadsetStateMachine.this.mTargetDevice != null && HeadsetStateMachine.this.mTargetDevice.equals(device)) {
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mCurrentDevice = device;
                            HeadsetStateMachine.this.mConnectedDevicesList.add(device);
                            Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is added in Pending state");
                            HeadsetStateMachine.this.mTargetDevice = null;
                            HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                        }
                        HeadsetStateMachine.this.broadcastConnectionState(device, 2, 1);
                        HeadsetStateMachine.this.configAudioParameters(device);
                        return;
                    } else if (HeadsetStateMachine.this.mIncomingDevice == null || !HeadsetStateMachine.this.mIncomingDevice.equals(device)) {
                        Log.w(HeadsetStateMachine.TAG, "Some other incoming HF connected in Pending state");
                        if (HeadsetStateMachine.this.okToConnect(device)) {
                            Log.i(HeadsetStateMachine.TAG, "Incoming Hf accepted");
                            HeadsetStateMachine.this.broadcastConnectionState(device, 2, 0);
                            synchronized (HeadsetStateMachine.this) {
                                HeadsetStateMachine.this.mCurrentDevice = device;
                                HeadsetStateMachine.this.mConnectedDevicesList.add(device);
                                Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is added in Pending state");
                            }
                            HeadsetStateMachine.this.configAudioParameters(device);
                            return;
                        }
                        Log.i(HeadsetStateMachine.TAG, "Incoming Hf rejected. priority=" + HeadsetStateMachine.this.mService.getPriority(device) + " bondState=" + device.getBondState());
                        HeadsetStateMachine.this.disconnectHfpNative(HeadsetStateMachine.this.getByteAddress(device));
                        AdapterService adapterService = AdapterService.getAdapterService();
                        if (adapterService != null) {
                            adapterService.connectOtherProfile(device, 2);
                            return;
                        }
                        return;
                    } else {
                        synchronized (HeadsetStateMachine.this) {
                            HeadsetStateMachine.this.mCurrentDevice = device;
                            HeadsetStateMachine.this.mConnectedDevicesList.add(device);
                            Log.d(HeadsetStateMachine.TAG, "device " + device.getAddress() + " is added in Pending state");
                            HeadsetStateMachine.this.mIncomingDevice = null;
                            HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                        }
                        HeadsetStateMachine.this.broadcastConnectionState(device, 2, 1);
                        HeadsetStateMachine.this.configAudioParameters(device);
                        return;
                    }
                case 4:
                    if (HeadsetStateMachine.this.mCurrentDevice != null && HeadsetStateMachine.this.mCurrentDevice.equals(device)) {
                        HeadsetStateMachine.this.log("stack is disconnecting mCurrentDevice");
                        return;
                    } else if (HeadsetStateMachine.this.mTargetDevice != null && HeadsetStateMachine.this.mTargetDevice.equals(device)) {
                        Log.e(HeadsetStateMachine.TAG, "TargetDevice is getting disconnected");
                        return;
                    } else if (HeadsetStateMachine.this.mIncomingDevice == null || !HeadsetStateMachine.this.mIncomingDevice.equals(device)) {
                        Log.e(HeadsetStateMachine.TAG, "Disconnecting unknow device: " + device);
                        return;
                    } else {
                        Log.e(HeadsetStateMachine.TAG, "IncomingDevice is getting disconnected");
                        return;
                    }
                default:
                    Log.e(HeadsetStateMachine.TAG, "Incorrect state: " + state);
                    return;
            }
        }

        private void processMultiHFConnected(BluetoothDevice device) {
            HeadsetStateMachine.this.log("Pending state: processMultiHFConnected");
            if (HeadsetStateMachine.this.mCurrentDevice != null && HeadsetStateMachine.this.mCurrentDevice.equals(device)) {
                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
                HeadsetStateMachine.this.mCurrentDevice = (BluetoothDevice) HeadsetStateMachine.this.mConnectedDevicesList.get(HeadsetStateMachine.this.mConnectedDevicesList.size() - 1);
            } else if (HeadsetStateMachine.this.mAudioState == 12) {
                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mAudioOn);
            } else {
                HeadsetStateMachine.this.transitionTo(HeadsetStateMachine.this.mConnected);
            }
            HeadsetStateMachine.this.log("processMultiHFConnected , the latest mCurrentDevice is:" + HeadsetStateMachine.this.mCurrentDevice);
            HeadsetStateMachine.this.log("Pending state: processMultiHFConnected ,fake broadcasting for mCurrentDevice");
            HeadsetStateMachine.this.broadcastConnectionState(HeadsetStateMachine.this.mCurrentDevice, 2, 0);
        }
    }

    private class StackEvent {
        BluetoothDevice device;
        int type;
        int valueInt;
        int valueInt2;
        String valueString;

        private StackEvent(int type) {
            this.type = 0;
            this.valueInt = 0;
            this.valueInt2 = 0;
            this.valueString = null;
            this.device = null;
            this.type = type;
        }
    }

    private native boolean cindResponseNative(int i, int i2, int i3, int i4, int i5, int i6, int i7, byte[] bArr);

    private static native void classInitNative();

    private native boolean clccResponseNative(int i, int i2, int i3, int i4, boolean z, String str, int i5, byte[] bArr);

    private native void cleanupNative();

    private native boolean configureWBSNative(byte[] bArr, int i);

    private native boolean connectAudioNative(byte[] bArr);

    private native boolean connectHfpNative(byte[] bArr);

    private native boolean copsResponseNative(String str, byte[] bArr);

    private native boolean disconnectAudioNative(byte[] bArr);

    private native boolean disconnectHfpNative(byte[] bArr);

    private native void initializeNative(int i);

    private native boolean notifyDeviceStatusNative(int i, int i2, int i3, int i4);

    private native boolean phoneStateChangeNative(int i, int i2, int i3, String str, int i4);

    private native boolean setVolumeNative(int i, int i2, byte[] bArr);

    private native boolean startVoiceRecognitionNative(byte[] bArr);

    private native boolean stopVoiceRecognitionNative(byte[] bArr);

    native boolean atResponseCodeNative(int i, int i2, byte[] bArr);

    native boolean atResponseStringNative(String str, byte[] bArr);

    static {
        classInitNative();
        VENDOR_SPECIFIC_AT_COMMAND_COMPANY_ID.put("+XEVENT", Integer.valueOf(85));
        VENDOR_SPECIFIC_AT_COMMAND_COMPANY_ID.put("+ANDROID", Integer.valueOf(224));
    }

    private HeadsetStateMachine(HeadsetService context) {
        super(TAG);
        this.mService = context;
        this.mVoiceRecognitionStarted = false;
        this.mWaitingForVoiceRecognition = false;
        this.mPowerManager = (PowerManager) context.getSystemService("power");
        this.mStartVoiceRecognitionWakeLock = this.mPowerManager.newWakeLock(1, "HeadsetStateMachine:VoiceRecognition");
        this.mStartVoiceRecognitionWakeLock.setReferenceCounted(false);
        this.mDialingOut = false;
        this.mAudioManager = (AudioManager) context.getSystemService("audio");
        this.mPhonebook = new AtPhonebook(this.mService, this);
        this.mPhoneState = new HeadsetPhoneState(context, this);
        this.mAudioState = 10;
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        Intent intent = new Intent(IBluetoothHeadsetPhone.class.getName());
        intent.setComponent(intent.resolveSystemService(context.getPackageManager(), 0));
        if (intent.getComponent() == null || !context.bindService(intent, this.mConnection, 0)) {
            Log.e(TAG, "Could not bind to Bluetooth Headset Phone Service");
        }
        String max_hfp_clients = SystemProperties.get("bt.max.hfpclient.connections");
        if (!max_hfp_clients.isEmpty() && Integer.parseInt(max_hfp_clients) == 2) {
            this.max_hf_connections = Integer.parseInt(max_hfp_clients);
        }
        Log.d(TAG, "max_hf_connections = " + this.max_hf_connections);
        initializeNative(this.max_hf_connections);
        this.mNativeAvailable = true;
        this.mDisconnected = new Disconnected();
        this.mPending = new Pending();
        this.mConnected = new Connected();
        this.mAudioOn = new AudioOn();
        this.mMultiHFPending = new MultiHFPending();
        if (sVoiceCommandIntent == null) {
            sVoiceCommandIntent = new Intent("android.intent.action.VOICE_COMMAND");
            sVoiceCommandIntent.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
        }
        addState(this.mDisconnected);
        addState(this.mPending);
        addState(this.mConnected);
        addState(this.mAudioOn);
        addState(this.mMultiHFPending);
        setInitialState(this.mDisconnected);
    }

    static HeadsetStateMachine make(HeadsetService context) {
        Log.d(TAG, "make");
        HeadsetStateMachine hssm = new HeadsetStateMachine(context);
        hssm.start();
        return hssm;
    }

    public void doQuit() {
        quitNow();
    }

    public void cleanup() {
        if (this.mPhoneProxy != null) {
            Log.d(TAG, "Unbinding service...");
            synchronized (this.mConnection) {
                try {
                    this.mPhoneProxy = null;
                    this.mService.unbindService(this.mConnection);
                } catch (Exception re) {
                    Log.e(TAG, "Error unbinding from IBluetoothHeadsetPhone", re);
                }
            }
        }
        if (this.mPhoneState != null) {
            this.mPhoneState.listenForPhoneState(false);
            this.mPhoneState.cleanup();
        }
        if (this.mPhonebook != null) {
            this.mPhonebook.cleanup();
        }
        if (this.mHeadsetAudioParam != null) {
            this.mHeadsetAudioParam.clear();
        }
        if (this.mHeadsetBrsf != null) {
            this.mHeadsetBrsf.clear();
        }
        if (this.mConnectedDevicesList != null) {
            this.mConnectedDevicesList.clear();
        }
        if (this.mNativeAvailable) {
            cleanupNative();
            this.mNativeAvailable = false;
        }
    }

    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mCurrentDevice: " + this.mCurrentDevice);
        ProfileService.println(sb, "mTargetDevice: " + this.mTargetDevice);
        ProfileService.println(sb, "mIncomingDevice: " + this.mIncomingDevice);
        ProfileService.println(sb, "mActiveScoDevice: " + this.mActiveScoDevice);
        ProfileService.println(sb, "mMultiDisconnectDevice: " + this.mMultiDisconnectDevice);
        ProfileService.println(sb, "mVirtualCallStarted: " + this.mVirtualCallStarted);
        ProfileService.println(sb, "mVoiceRecognitionStarted: " + this.mVoiceRecognitionStarted);
        ProfileService.println(sb, "mWaitingForVoiceRecognition: " + this.mWaitingForVoiceRecognition);
        ProfileService.println(sb, "StateMachine: " + toString());
        ProfileService.println(sb, "mPhoneState: " + this.mPhoneState);
        ProfileService.println(sb, "mAudioState: " + this.mAudioState);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    int getConnectionState(BluetoothDevice device) {
        if (getCurrentState() == this.mDisconnected) {
            Log.d(TAG, "currentState is Disconnected");
            return 0;
        }
        synchronized (this) {
            IState currentState = getCurrentState();
            Log.d(TAG, "currentState = " + currentState);
            if (currentState == this.mPending) {
                if (this.mTargetDevice != null && this.mTargetDevice.equals(device)) {
                    return 1;
                } else if (this.mConnectedDevicesList.contains(device)) {
                    return 3;
                } else if (this.mIncomingDevice == null || !this.mIncomingDevice.equals(device)) {
                } else {
                    return 1;
                }
            } else if (currentState == this.mMultiHFPending) {
                if (this.mTargetDevice != null && this.mTargetDevice.equals(device)) {
                    return 1;
                } else if (this.mIncomingDevice != null && this.mIncomingDevice.equals(device)) {
                    return 1;
                } else if (!this.mConnectedDevicesList.contains(device)) {
                    return 0;
                } else if (this.mMultiDisconnectDevice == null || this.mMultiDisconnectDevice.equals(device)) {
                } else {
                    return 2;
                }
            } else if (currentState != this.mConnected && currentState != this.mAudioOn) {
                Log.e(TAG, "Bad currentState: " + currentState);
                return 0;
            } else if (this.mConnectedDevicesList.contains(device)) {
                return 2;
            } else {
                return 0;
            }
        }
    }

    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList();
        synchronized (this) {
            for (int i = 0; i < this.mConnectedDevicesList.size(); i++) {
                devices.add(this.mConnectedDevicesList.get(i));
            }
        }
        return devices;
    }

    boolean isAudioOn() {
        return getCurrentState() == this.mAudioOn;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    boolean isAudioConnected(BluetoothDevice device) {
        synchronized (this) {
            if (getCurrentState() == this.mAudioOn && this.mCurrentDevice.equals(device) && this.mAudioState != 10) {
                return true;
            }
        }
    }

    int getAudioState(BluetoothDevice device) {
        synchronized (this) {
            if (this.mConnectedDevicesList.size() == 0) {
                return 10;
            }
            return this.mAudioState;
        }
    }

    private void processVrEvent(int state, BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processVrEvent device is null");
            return;
        }
        Log.d(TAG, "processVrEvent: state=" + state + " mVoiceRecognitionStarted: " + this.mVoiceRecognitionStarted + " mWaitingforVoiceRecognition: " + this.mWaitingForVoiceRecognition + " isInCall: " + isInCall());
        if (state == 1) {
            if (!isVirtualCallInProgress() && !isInCall()) {
                try {
                    this.mService.startActivity(sVoiceCommandIntent);
                    expectVoiceRecognition(device);
                } catch (ActivityNotFoundException e) {
                    atResponseCodeNative(0, 0, getByteAddress(device));
                }
            }
        } else if (state != 0) {
            Log.e(TAG, "Bad Voice Recognition state: " + state);
        } else if (this.mVoiceRecognitionStarted || this.mWaitingForVoiceRecognition) {
            atResponseCodeNative(1, 0, getByteAddress(device));
            this.mVoiceRecognitionStarted = false;
            this.mWaitingForVoiceRecognition = false;
            if (!isInCall() && this.mActiveScoDevice != null) {
                disconnectAudioNative(getByteAddress(this.mActiveScoDevice));
                this.mAudioManager.setParameters("A2dpSuspended=false");
            }
        } else {
            atResponseCodeNative(0, 0, getByteAddress(device));
        }
    }

    private void processLocalVrEvent(int state) {
        BluetoothDevice device = null;
        if (state == 1) {
            boolean needAudio = true;
            if (this.mVoiceRecognitionStarted || isInCall()) {
                Log.e(TAG, "Voice recognition started when call is active. isInCall:" + isInCall() + " mVoiceRecognitionStarted: " + this.mVoiceRecognitionStarted);
                return;
            }
            this.mVoiceRecognitionStarted = true;
            if (this.mWaitingForVoiceRecognition) {
                device = getDeviceForMessage(START_VR_TIMEOUT);
                if (device != null) {
                    Log.d(TAG, "Voice recognition started successfully");
                    this.mWaitingForVoiceRecognition = false;
                    atResponseCodeNative(1, 0, getByteAddress(device));
                    removeMessages(START_VR_TIMEOUT);
                } else {
                    return;
                }
            }
            Log.d(TAG, "Voice recognition started locally");
            needAudio = startVoiceRecognitionNative(getByteAddress(this.mCurrentDevice));
            if (this.mCurrentDevice != null) {
                device = this.mCurrentDevice;
            }
            if (needAudio && !isAudioOn()) {
                Log.d(TAG, "Initiating audio connection for Voice Recognition");
                this.mAudioManager.setParameters("A2dpSuspended=true");
                connectAudioNative(getByteAddress(device));
            }
            if (this.mStartVoiceRecognitionWakeLock.isHeld()) {
                this.mStartVoiceRecognitionWakeLock.release();
                return;
            }
            return;
        }
        Log.d(TAG, "Voice Recognition stopped. mVoiceRecognitionStarted: " + this.mVoiceRecognitionStarted + " mWaitingForVoiceRecognition: " + this.mWaitingForVoiceRecognition);
        if (this.mVoiceRecognitionStarted || this.mWaitingForVoiceRecognition) {
            this.mVoiceRecognitionStarted = false;
            this.mWaitingForVoiceRecognition = false;
            if (stopVoiceRecognitionNative(getByteAddress(this.mCurrentDevice)) && !isInCall() && this.mActiveScoDevice != null) {
                disconnectAudioNative(getByteAddress(this.mActiveScoDevice));
                this.mAudioManager.setParameters("A2dpSuspended=false");
            }
        }
    }

    private synchronized void expectVoiceRecognition(BluetoothDevice device) {
        this.mWaitingForVoiceRecognition = true;
        Message m = obtainMessage(START_VR_TIMEOUT);
        m.obj = getMatchingDevice(device);
        sendMessageDelayed(m, 5000);
        if (!this.mStartVoiceRecognitionWakeLock.isHeld()) {
            this.mStartVoiceRecognitionWakeLock.acquire(5000);
        }
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList = new ArrayList();
        synchronized (this) {
            for (BluetoothDevice device : bondedDevices) {
                if (BluetoothUuid.containsAnyUuid(device.getUuids(), HEADSET_UUIDS)) {
                    int connectionState = getConnectionState(device);
                    for (int i : states) {
                        if (connectionState == i) {
                            deviceList.add(device);
                        }
                    }
                }
            }
        }
        return deviceList;
    }

    private BluetoothDevice getDeviceForMessage(int what) {
        if (what == CONNECT_TIMEOUT) {
            log("getDeviceForMessage: returning mTargetDevice for what=" + what);
            return this.mTargetDevice;
        } else if (this.mConnectedDevicesList.size() == 0) {
            log("getDeviceForMessage: No connected device. what=" + what);
            return null;
        } else {
            Iterator i$ = this.mConnectedDevicesList.iterator();
            while (i$.hasNext()) {
                BluetoothDevice device = (BluetoothDevice) i$.next();
                if (getHandler().hasMessages(what, device)) {
                    log("getDeviceForMessage: returning " + device);
                    return device;
                }
            }
            log("getDeviceForMessage: No matching device for " + what + ". Returning null");
            return null;
        }
    }

    private BluetoothDevice getMatchingDevice(BluetoothDevice device) {
        Iterator i$ = this.mConnectedDevicesList.iterator();
        while (i$.hasNext()) {
            BluetoothDevice matchingDevice = (BluetoothDevice) i$.next();
            if (matchingDevice.equals(device)) {
                return matchingDevice;
            }
        }
        return null;
    }

    private void broadcastConnectionState(BluetoothDevice device, int newState, int prevState) {
        log("Connection state " + device + ": " + prevState + "->" + newState);
        if (prevState == 2) {
            terminateScoUsingVirtualVoiceCall();
        }
        this.mService.notifyProfileConnectionStateChanged(device, 1, newState, prevState);
        Intent intent = new Intent("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        intent.putExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", prevState);
        intent.putExtra("android.bluetooth.profile.extra.STATE", newState);
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        this.mService.sendBroadcastAsUser(intent, UserHandle.ALL, ProfileService.BLUETOOTH_PERM);
    }

    private void broadcastAudioState(BluetoothDevice device, int newState, int prevState) {
        if (prevState == 12) {
            terminateScoUsingVirtualVoiceCall();
        }
        Intent intent = new Intent("android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED");
        intent.putExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", prevState);
        intent.putExtra("android.bluetooth.profile.extra.STATE", newState);
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        this.mService.sendBroadcastAsUser(intent, UserHandle.ALL, ProfileService.BLUETOOTH_PERM);
        log("Audio state " + device + ": " + prevState + "->" + newState);
    }

    private void broadcastVendorSpecificEventIntent(String command, int companyId, int commandType, Object[] arguments, BluetoothDevice device) {
        log("broadcastVendorSpecificEventIntent(" + command + ")");
        Intent intent = new Intent("android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT");
        intent.putExtra("android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_CMD", command);
        intent.putExtra("android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE", commandType);
        intent.putExtra("android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_ARGS", arguments);
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.addCategory("android.bluetooth.headset.intent.category.companyid." + Integer.toString(companyId));
        this.mService.sendBroadcastAsUser(intent, UserHandle.ALL, ProfileService.BLUETOOTH_PERM);
    }

    private void configAudioParameters(BluetoothDevice device) {
        HashMap<String, Integer> AudioParamConfig = new HashMap();
        AudioParamConfig.put("NREC", Integer.valueOf(1));
        this.mHeadsetAudioParam.put(device, AudioParamConfig);
        this.mAudioManager.setParameters("bt_headset_name=" + getCurrentDeviceName(device) + ";" + HEADSET_NREC + "=on");
        Log.d(TAG, "configAudioParameters for device:" + device + " are: nrec = " + AudioParamConfig.get("NREC"));
    }

    private void setAudioParameters(BluetoothDevice device) {
        if (((Integer) ((HashMap) this.mHeadsetAudioParam.get(device)).get("NREC")).intValue() == 1) {
            Log.d(TAG, "Set NREC: 1 for device:" + device);
            this.mAudioManager.setParameters("bt_headset_nrec=on");
        } else {
            Log.d(TAG, "Set NREC: 0 for device:" + device);
            this.mAudioManager.setParameters("bt_headset_nrec=off");
        }
        this.mAudioManager.setParameters("bt_headset_name=" + getCurrentDeviceName(device));
    }

    private String parseUnknownAt(String atString) {
        StringBuilder atCommand = new StringBuilder(atString.length());
        int i = 0;
        while (i < atString.length()) {
            char c = atString.charAt(i);
            if (c == '\"') {
                int j = atString.indexOf(34, i + 1);
                if (j == -1) {
                    atCommand.append(atString.substring(i, atString.length()));
                    atCommand.append('\"');
                    break;
                }
                atCommand.append(atString.substring(i, j + 1));
                i = j;
            } else if (c != ' ') {
                atCommand.append(Character.toUpperCase(c));
            }
            i++;
        }
        return atCommand.toString();
    }

    private int getAtCommandType(String atCommand) {
        this.mPhonebook.getClass();
        atCommand = atCommand.trim();
        if (atCommand.length() <= 5) {
            return -1;
        }
        String atString = atCommand.substring(5);
        if (atString.startsWith("?")) {
            this.mPhonebook.getClass();
            return 0;
        } else if (atString.startsWith("=?")) {
            this.mPhonebook.getClass();
            return 2;
        } else if (atString.startsWith("=")) {
            this.mPhonebook.getClass();
            return 1;
        } else {
            this.mPhonebook.getClass();
            return -1;
        }
    }

    private boolean isVirtualCallInProgress() {
        return this.mVirtualCallStarted;
    }

    void setVirtualCallInProgress(boolean state) {
        this.mVirtualCallStarted = state;
    }

    synchronized boolean initiateScoUsingVirtualVoiceCall() {
        boolean z = false;
        synchronized (this) {
            log("initiateScoUsingVirtualVoiceCall: Received");
            if (isInCall() || this.mVoiceRecognitionStarted) {
                Log.e(TAG, "initiateScoUsingVirtualVoiceCall: Call in progress.");
            } else {
                processCallState(new HeadsetCallState(0, 0, 2, "", 0), true);
                processCallState(new HeadsetCallState(0, 0, 3, "", 0), true);
                processCallState(new HeadsetCallState(1, 0, 6, "", 0), true);
                setVirtualCallInProgress(true);
                log("initiateScoUsingVirtualVoiceCall: Done");
                z = true;
            }
        }
        return z;
    }

    synchronized boolean terminateScoUsingVirtualVoiceCall() {
        boolean z = false;
        synchronized (this) {
            log("terminateScoUsingVirtualVoiceCall: Received");
            if (isVirtualCallInProgress()) {
                processCallState(new HeadsetCallState(0, 0, 6, "", 0), true);
                setVirtualCallInProgress(false);
                log("terminateScoUsingVirtualVoiceCall: Done");
                z = true;
            } else {
                Log.e(TAG, "terminateScoUsingVirtualVoiceCall:No present call to terminate");
            }
        }
        return z;
    }

    private void processAnswerCall(BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processAnswerCall device is null");
        } else if (this.mPhoneProxy != null) {
            try {
                this.mPhoneProxy.answerCall();
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        } else {
            Log.e(TAG, "Handsfree phone proxy null for answering call");
        }
    }

    private void processHangupCall(BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processHangupCall device is null");
        } else if (isVirtualCallInProgress()) {
            terminateScoUsingVirtualVoiceCall();
        } else if (this.mPhoneProxy != null) {
            try {
                this.mPhoneProxy.hangupCall();
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        } else {
            Log.e(TAG, "Handsfree phone proxy null for hanging up call");
        }
    }

    private void processDialCall(String number, BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processDialCall device is null");
            return;
        }
        String dialNumber;
        if (number == null || number.length() == 0) {
            dialNumber = this.mPhonebook.getLastDialledNumber();
            if (dialNumber == null) {
                log("processDialCall, last dial number null");
                atResponseCodeNative(0, 0, getByteAddress(device));
                return;
            }
        } else if (number.charAt(0) != '>') {
            if (number.charAt(number.length() - 1) == ';') {
                number = number.substring(0, number.length() - 1);
            }
            dialNumber = PhoneNumberUtils.convertPreDial(number);
        } else if (number.startsWith(">9999")) {
            atResponseCodeNative(0, 0, getByteAddress(device));
            return;
        } else {
            log("processDialCall, memory dial do last dial for now");
            dialNumber = this.mPhonebook.getLastDialledNumber();
            if (dialNumber == null) {
                log("processDialCall, last dial number null");
                atResponseCodeNative(0, 0, getByteAddress(device));
                return;
            }
        }
        terminateScoUsingVirtualVoiceCall();
        Intent intent = new Intent("android.intent.action.CALL_PRIVILEGED", Uri.fromParts(SCHEME_TEL, dialNumber, null));
        intent.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
        this.mService.startActivity(intent);
        this.mDialingOut = true;
        Message m = obtainMessage(DIALING_OUT_TIMEOUT);
        m.obj = getMatchingDevice(device);
        sendMessageDelayed(m, 10000);
    }

    private void processVolumeEvent(int volumeType, int volume, BluetoothDevice device) {
        int flag = 1;
        if (device != null && !device.equals(this.mActiveScoDevice) && this.mPhoneState.isInCall()) {
            Log.w(TAG, "ignore processVolumeEvent");
        } else if (volumeType == 0) {
            this.mPhoneState.setSpeakerVolume(volume);
            if (getCurrentState() != this.mAudioOn) {
                flag = 0;
            }
            this.mAudioManager.setStreamVolume(6, volume, flag);
        } else if (volumeType == 1) {
            this.mPhoneState.setMicVolume(volume);
        } else {
            Log.e(TAG, "Bad voluem type: " + volumeType);
        }
    }

    private void processSendDtmf(int dtmf, BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processSendDtmf device is null");
        } else if (this.mPhoneProxy != null) {
            try {
                this.mPhoneProxy.sendDtmf(dtmf);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        } else {
            Log.e(TAG, "Handsfree phone proxy null for sending DTMF");
        }
    }

    private void processCallState(HeadsetCallState callState) {
        processCallState(callState, false);
    }

    private void processCallState(HeadsetCallState callState, boolean isVirtualCall) {
        this.mPhoneState.setNumActiveCall(callState.mNumActive);
        this.mPhoneState.setNumHeldCall(callState.mNumHeld);
        this.mPhoneState.setCallState(callState.mCallState);
        if (this.mDialingOut) {
            if (callState.mCallState == 2) {
                BluetoothDevice device = getDeviceForMessage(DIALING_OUT_TIMEOUT);
                if (device != null) {
                    atResponseCodeNative(1, 0, getByteAddress(device));
                    removeMessages(DIALING_OUT_TIMEOUT);
                } else {
                    return;
                }
            } else if (callState.mCallState == 0 || callState.mCallState == 6) {
                this.mDialingOut = false;
            }
        }
        if (!(this.mActiveScoDevice == null || isInCall() || callState.mCallState != 6)) {
            this.mActiveScoDevice = null;
        }
        log("mNumActive: " + callState.mNumActive + " mNumHeld: " + callState.mNumHeld + " mCallState: " + callState.mCallState);
        log("mNumber: " + callState.mNumber + " mType: " + callState.mType);
        if (!isVirtualCall) {
            terminateScoUsingVirtualVoiceCall();
        }
        if (getCurrentState() != this.mDisconnected) {
            phoneStateChangeNative(callState.mNumActive, callState.mNumHeld, callState.mCallState, callState.mNumber, callState.mType);
        }
    }

    private void processNoiceReductionEvent(int enable, BluetoothDevice device) {
        HashMap<String, Integer> AudioParamNrec = (HashMap) this.mHeadsetAudioParam.get(device);
        if (enable == 1) {
            AudioParamNrec.put("NREC", Integer.valueOf(1));
        } else {
            AudioParamNrec.put("NREC", Integer.valueOf(0));
        }
        Log.d(TAG, "NREC value for device :" + device + " is: " + AudioParamNrec.get("NREC"));
    }

    private void processWBSEvent(int enable, BluetoothDevice device) {
        if (enable == 2) {
            Log.d(TAG, "AudioManager.setParameters bt_wbs=on for " + device.getName() + " - " + device.getAddress());
            this.mAudioManager.setParameters("bt_wbs=on");
            return;
        }
        Log.d(TAG, "AudioManager.setParameters bt_wbs=off for " + device.getName() + " - " + device.getAddress());
        this.mAudioManager.setParameters("bt_wbs=off");
    }

    private void processAtChld(int chld, BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processAtChld device is null");
        } else if (this.mPhoneProxy != null) {
            try {
                if (this.mPhoneProxy.processChld(chld)) {
                    atResponseCodeNative(1, 0, getByteAddress(device));
                } else {
                    atResponseCodeNative(0, 0, getByteAddress(device));
                }
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                atResponseCodeNative(0, 0, getByteAddress(device));
            }
        } else {
            Log.e(TAG, "Handsfree phone proxy null for At+Chld");
            atResponseCodeNative(0, 0, getByteAddress(device));
        }
    }

    private void processSubscriberNumberRequest(BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processSubscriberNumberRequest device is null");
        } else if (this.mPhoneProxy != null) {
            try {
                String number = this.mPhoneProxy.getSubscriberNumber();
                if (number != null) {
                    atResponseStringNative("+CNUM: ,\"" + number + "\"," + PhoneNumberUtils.toaFromString(number) + ",,4", getByteAddress(device));
                    atResponseCodeNative(1, 0, getByteAddress(device));
                    return;
                }
                Log.e(TAG, "getSubscriberNumber returns null");
                atResponseCodeNative(0, 0, getByteAddress(device));
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                atResponseCodeNative(0, 0, getByteAddress(device));
            }
        } else {
            Log.e(TAG, "Handsfree phone proxy null for At+CNUM");
        }
    }

    private void processAtCind(BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processAtCind device is null");
            return;
        }
        int call;
        int call_setup;
        if (isVirtualCallInProgress()) {
            call = 1;
            call_setup = 0;
        } else {
            call = this.mPhoneState.getNumActiveCall();
            call_setup = this.mPhoneState.getNumHeldCall();
        }
        cindResponseNative(this.mPhoneState.getService(), call, call_setup, this.mPhoneState.getCallState(), this.mPhoneState.getSignal(), this.mPhoneState.getRoam(), this.mPhoneState.getBatteryCharge(), getByteAddress(device));
    }

    private void processAtCops(BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processAtCops device is null");
        } else if (this.mPhoneProxy != null) {
            try {
                String operatorName = this.mPhoneProxy.getNetworkOperator();
                if (operatorName == null) {
                    operatorName = "";
                }
                copsResponseNative(operatorName, getByteAddress(device));
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                copsResponseNative("", getByteAddress(device));
            }
        } else {
            Log.e(TAG, "Handsfree phone proxy null for At+COPS");
            copsResponseNative("", getByteAddress(device));
        }
    }

    private void processAtClcc(BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processAtClcc device is null");
        } else if (this.mPhoneProxy != null) {
            try {
                if (isVirtualCallInProgress()) {
                    String phoneNumber = "";
                    int type = BluetoothMapContent.MMS_BCC;
                    try {
                        phoneNumber = this.mPhoneProxy.getSubscriberNumber();
                        type = PhoneNumberUtils.toaFromString(phoneNumber);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to retrieve phone numberusing IBluetoothHeadsetPhone proxy");
                        phoneNumber = "";
                    }
                    clccResponseNative(1, 0, 0, 0, false, phoneNumber, type, getByteAddress(device));
                    clccResponseNative(0, 0, 0, 0, false, "", 0, getByteAddress(device));
                } else if (this.mPhoneProxy.listCurrentCalls()) {
                    Log.d(TAG, "Starting CLCC response timeout for device: " + device);
                    Message m = obtainMessage(CLCC_RSP_TIMEOUT);
                    m.obj = getMatchingDevice(device);
                    sendMessageDelayed(m, 5000);
                } else {
                    clccResponseNative(0, 0, 0, 0, false, "", 0, getByteAddress(device));
                }
            } catch (RemoteException e2) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                clccResponseNative(0, 0, 0, 0, false, "", 0, getByteAddress(device));
            }
        } else {
            Log.e(TAG, "Handsfree phone proxy null for At+CLCC");
            clccResponseNative(0, 0, 0, 0, false, "", 0, getByteAddress(device));
        }
    }

    private void processAtCscs(String atString, int type, BluetoothDevice device) {
        log("processAtCscs - atString = " + atString);
        if (this.mPhonebook != null) {
            this.mPhonebook.handleCscsCommand(atString, type, device);
            return;
        }
        Log.e(TAG, "Phonebook handle null for At+CSCS");
        atResponseCodeNative(0, 0, getByteAddress(device));
    }

    private void processAtCpbs(String atString, int type, BluetoothDevice device) {
        log("processAtCpbs - atString = " + atString);
        if (this.mPhonebook != null) {
            this.mPhonebook.handleCpbsCommand(atString, type, device);
            return;
        }
        Log.e(TAG, "Phonebook handle null for At+CPBS");
        atResponseCodeNative(0, 0, getByteAddress(device));
    }

    private void processAtCpbr(String atString, int type, BluetoothDevice device) {
        log("processAtCpbr - atString = " + atString);
        if (this.mPhonebook != null) {
            this.mPhonebook.handleCpbrCommand(atString, type, device);
            return;
        }
        Log.e(TAG, "Phonebook handle null for At+CPBR");
        atResponseCodeNative(0, 0, getByteAddress(device));
    }

    private static int findChar(char ch, String input, int fromIndex) {
        int i = fromIndex;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\"') {
                i = input.indexOf(34, i + 1);
                if (i == -1) {
                    return input.length();
                }
            } else if (c == ch) {
                return i;
            }
            i++;
        }
        return input.length();
    }

    private static Object[] generateArgs(String input) {
        int i = 0;
        ArrayList<Object> out = new ArrayList();
        while (i <= input.length()) {
            int j = findChar(',', input, i);
            String arg = input.substring(i, j);
            try {
                out.add(new Integer(arg));
            } catch (NumberFormatException e) {
                out.add(arg);
            }
            i = j + 1;
        }
        return out.toArray();
    }

    private boolean processVendorSpecificAt(String atString) {
        log("processVendorSpecificAt - atString = " + atString);
        int indexOfEqual = atString.indexOf("=");
        if (indexOfEqual == -1) {
            Log.e(TAG, "processVendorSpecificAt: command type error in " + atString);
            return false;
        }
        String command = atString.substring(0, indexOfEqual);
        Integer companyId = (Integer) VENDOR_SPECIFIC_AT_COMMAND_COMPANY_ID.get(command);
        if (companyId == null) {
            Log.e(TAG, "processVendorSpecificAt: unsupported command: " + atString);
            return false;
        }
        String arg = atString.substring(indexOfEqual + 1);
        if (arg.startsWith("?")) {
            Log.e(TAG, "processVendorSpecificAt: command type error in " + atString);
            return false;
        }
        broadcastVendorSpecificEventIntent(command, companyId.intValue(), 2, generateArgs(arg), this.mCurrentDevice);
        atResponseCodeNative(1, 0, getByteAddress(this.mCurrentDevice));
        return true;
    }

    private void processUnknownAt(String atString, BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processUnknownAt device is null");
            return;
        }
        log("processUnknownAt - atString = " + atString);
        String atCommand = parseUnknownAt(atString);
        int commandType = getAtCommandType(atCommand);
        if (atCommand.startsWith("+CSCS")) {
            processAtCscs(atCommand.substring(5), commandType, device);
        } else if (atCommand.startsWith("+CPBS")) {
            processAtCpbs(atCommand.substring(5), commandType, device);
        } else if (atCommand.startsWith("+CPBR")) {
            processAtCpbr(atCommand.substring(5), commandType, device);
        } else if (!processVendorSpecificAt(atCommand)) {
            atResponseCodeNative(0, 0, getByteAddress(device));
        }
    }

    private void processKeyPressed(BluetoothDevice device) {
        if (device == null) {
            Log.w(TAG, "processKeyPressed device is null");
        } else if (this.mPhoneState.getCallState() == 4) {
            if (this.mPhoneProxy != null) {
                try {
                    this.mPhoneProxy.answerCall();
                    return;
                } catch (RemoteException e) {
                    Log.e(TAG, Log.getStackTraceString(new Throwable()));
                    return;
                }
            }
            Log.e(TAG, "Handsfree phone proxy null for answering call");
        } else if (this.mPhoneState.getNumActiveCall() <= 0) {
            String dialNumber = this.mPhonebook.getLastDialledNumber();
            if (dialNumber == null) {
                log("processKeyPressed, last dial number null");
                return;
            }
            Intent intent = new Intent("android.intent.action.CALL_PRIVILEGED", Uri.fromParts(SCHEME_TEL, dialNumber, null));
            intent.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
            this.mService.startActivity(intent);
        } else if (!isAudioOn()) {
            connectAudioNative(getByteAddress(this.mCurrentDevice));
        } else if (this.mPhoneProxy != null) {
            try {
                this.mPhoneProxy.hangupCall();
            } catch (RemoteException e2) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        } else {
            Log.e(TAG, "Handsfree phone proxy null for hangup call");
        }
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

    private void onVrStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(3);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAnswerCall(byte[] address) {
        StackEvent event = new StackEvent(4);
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onHangupCall(byte[] address) {
        StackEvent event = new StackEvent(5);
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onVolumeChanged(int type, int volume, byte[] address) {
        StackEvent event = new StackEvent(6);
        event.valueInt = type;
        event.valueInt2 = volume;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onDialCall(String number, byte[] address) {
        StackEvent event = new StackEvent(7);
        event.valueString = number;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onSendDtmf(int dtmf, byte[] address) {
        StackEvent event = new StackEvent(8);
        event.valueInt = dtmf;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onNoiceReductionEnable(boolean enable, byte[] address) {
        StackEvent event = new StackEvent(9);
        event.valueInt = enable ? 1 : 0;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onWBS(int codec, byte[] address) {
        StackEvent event = new StackEvent(17);
        event.valueInt = codec;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAtChld(int chld, byte[] address) {
        StackEvent event = new StackEvent(10);
        event.valueInt = chld;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAtCnum(byte[] address) {
        StackEvent event = new StackEvent(11);
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAtCind(byte[] address) {
        StackEvent event = new StackEvent(12);
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAtCops(byte[] address) {
        StackEvent event = new StackEvent(13);
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAtClcc(byte[] address) {
        StackEvent event = new StackEvent(14);
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onUnknownAt(String atString, byte[] address) {
        StackEvent event = new StackEvent(15);
        event.valueString = atString;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onKeyPressed(byte[] address) {
        StackEvent event = new StackEvent(16);
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void processIntentBatteryChanged(Intent intent) {
        int batteryLevel = intent.getIntExtra("level", -1);
        int scale = intent.getIntExtra("scale", -1);
        if (batteryLevel == -1 || scale == -1 || scale == 0) {
            Log.e(TAG, "Bad Battery Changed intent: " + batteryLevel + "," + scale);
            return;
        }
        this.mPhoneState.setBatteryCharge((batteryLevel * 5) / scale);
    }

    private void processDeviceStateChanged(HeadsetDeviceState deviceState) {
        notifyDeviceStatusNative(deviceState.mService, deviceState.mRoam, deviceState.mSignal, deviceState.mBatteryCharge);
    }

    private void processSendClccResponse(HeadsetClccResponse clcc) {
        BluetoothDevice device = getDeviceForMessage(CLCC_RSP_TIMEOUT);
        if (device != null) {
            if (clcc.mIndex == 0) {
                removeMessages(CLCC_RSP_TIMEOUT);
            }
            clccResponseNative(clcc.mIndex, clcc.mDirection, clcc.mStatus, clcc.mMode, clcc.mMpty, clcc.mNumber, clcc.mType, getByteAddress(device));
        }
    }

    private void processSendVendorSpecificResultCode(HeadsetVendorSpecificResultCode resultCode) {
        String stringToSend = resultCode.mCommand + ": ";
        if (resultCode.mArg != null) {
            stringToSend = stringToSend + resultCode.mArg;
        }
        atResponseStringNative(stringToSend, getByteAddress(resultCode.mDevice));
    }

    private String getCurrentDeviceName(BluetoothDevice device) {
        String defaultName = "<unknown>";
        if (device == null) {
            return defaultName;
        }
        String deviceName = device.getName();
        if (deviceName != null) {
            return deviceName;
        }
        return defaultName;
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    private BluetoothDevice getDevice(byte[] address) {
        return this.mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
    }

    private boolean isInCall() {
        return this.mPhoneState.getNumActiveCall() > 0 || this.mPhoneState.getNumHeldCall() > 0 || this.mPhoneState.getCallState() != 6;
    }

    boolean isConnected() {
        IState currentState = getCurrentState();
        return currentState == this.mConnected || currentState == this.mAudioOn;
    }

    boolean okToConnect(BluetoothDevice device) {
        AdapterService adapterService = AdapterService.getAdapterService();
        int priority = this.mService.getPriority(device);
        if (adapterService == null || (adapterService.isQuietModeEnabled() && this.mTargetDevice == null)) {
            return false;
        }
        if (priority > 0 || (-1 == priority && device.getBondState() != 10)) {
            return true;
        }
        return false;
    }

    protected void log(String msg) {
        super.log(msg);
    }

    public void handleAccessPermissionResult(Intent intent) {
        log("handleAccessPermissionResult");
        BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
        if (this.mPhonebook == null) {
            Log.e(TAG, "Phonebook handle null");
            if (device != null) {
                atResponseCodeNative(0, 0, getByteAddress(device));
            }
        } else if (this.mPhonebook.getCheckingAccessPermission()) {
            int atCommandResult = 0;
            if (intent.getAction().equals("android.bluetooth.device.action.CONNECTION_ACCESS_REPLY")) {
                if (intent.getIntExtra("android.bluetooth.device.extra.CONNECTION_ACCESS_RESULT", 2) == 1) {
                    if (intent.getBooleanExtra("android.bluetooth.device.extra.ALWAYS_ALLOWED", false)) {
                        this.mCurrentDevice.setPhonebookAccessPermission(1);
                    }
                    atCommandResult = this.mPhonebook.processCpbrCommand(device);
                } else if (intent.getBooleanExtra("android.bluetooth.device.extra.ALWAYS_ALLOWED", false)) {
                    this.mCurrentDevice.setPhonebookAccessPermission(2);
                }
            }
            this.mPhonebook.setCpbrIndex(-1);
            this.mPhonebook.setCheckingAccessPermission(false);
            if (atCommandResult >= 0) {
                atResponseCodeNative(atCommandResult, 0, getByteAddress(device));
            } else {
                log("handleAccessPermissionResult - RESULT_NONE");
            }
        }
    }
}
