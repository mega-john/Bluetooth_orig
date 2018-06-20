package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClientCall;
import android.bluetooth.BluetoothUuid;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AbstractionLayer;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.hfp.BluetoothCmeError;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.vcard.VCardConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;

final class HeadsetClientStateMachine extends StateMachine {
    static final int ACCEPT_CALL = 12;
    static final int CONNECT = 1;
    static final int CONNECT_AUDIO = 3;
    private static final boolean DBG = false;
    static final int DIAL_MEMORY = 11;
    static final int DIAL_NUMBER = 10;
    static final int DISCONNECT = 2;
    static final int DISCONNECT_AUDIO = 4;
    static final int ENTER_PRIVATE_MODE = 16;
    private static final int EVENT_TYPE_AUDIO_STATE_CHANGED = 2;
    private static final int EVENT_TYPE_BATTERY_LEVEL = 7;
    private static final int EVENT_TYPE_CALL = 9;
    private static final int EVENT_TYPE_CALLHELD = 11;
    private static final int EVENT_TYPE_CALLSETUP = 10;
    private static final int EVENT_TYPE_CALL_WAITING = 13;
    private static final int EVENT_TYPE_CLIP = 12;
    private static final int EVENT_TYPE_CMD_RESULT = 16;
    private static final int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    private static final int EVENT_TYPE_CURRENT_CALLS = 14;
    private static final int EVENT_TYPE_IN_BAND_RING = 19;
    private static final int EVENT_TYPE_LAST_VOICE_TAG_NUMBER = 20;
    private static final int EVENT_TYPE_NETWORK_SIGNAL = 6;
    private static final int EVENT_TYPE_NETWORK_STATE = 4;
    private static final int EVENT_TYPE_NONE = 0;
    private static final int EVENT_TYPE_OPERATOR_NAME = 8;
    private static final int EVENT_TYPE_RESP_AND_HOLD = 18;
    private static final int EVENT_TYPE_RING_INDICATION = 21;
    private static final int EVENT_TYPE_ROAMING_STATE = 5;
    private static final int EVENT_TYPE_SUBSCRIBER_INFO = 17;
    private static final int EVENT_TYPE_VOLUME_CHANGED = 15;
    private static final int EVENT_TYPE_VR_STATE_CHANGED = 3;
    static final int EXPLICIT_CALL_TRANSFER = 18;
    static final int HOLD_CALL = 14;
    static final int LAST_VTAG_NUMBER = 19;
    static final int NO_ACTION = 0;
    static final int QUERY_CURRENT_CALLS = 50;
    static final int QUERY_OPERATOR_NAME = 51;
    static final int REDIAL = 9;
    static final int REJECT_CALL = 13;
    static final int SEND_DTMF = 17;
    static final int SET_MIC_VOLUME = 7;
    static final int SET_SPEAKER_VOLUME = 8;
    private static final int STACK_EVENT = 100;
    static final int SUBSCRIBER_INFO = 52;
    private static final String TAG = "HeadsetClientStateMachine";
    static final int TERMINATE_CALL = 15;
    static final int TERMINATE_SPECIFIC_CALL = 53;
    static final int VOICE_RECOGNITION_START = 5;
    static final int VOICE_RECOGNITION_STOP = 6;
    private final String[] EVENT_TYPE_NAMES = new String[]{"EVENT_TYPE_NONE", "EVENT_TYPE_CONNECTION_STATE_CHANGED", "EVENT_TYPE_AUDIO_STATE_CHANGED", "EVENT_TYPE_VR_STATE_CHANGED", "EVENT_TYPE_NETWORK_STATE", "EVENT_TYPE_ROAMING_STATE", "EVENT_TYPE_NETWORK_SIGNAL", "EVENT_TYPE_BATTERY_LEVEL", "EVENT_TYPE_OPERATOR_NAME", "EVENT_TYPE_CALL", "EVENT_TYPE_CALLSETUP", "EVENT_TYPE_CALLHELD", "EVENT_TYPE_CLIP", "EVENT_TYPE_CALL_WAITING", "EVENT_TYPE_CURRENT_CALLS", "EVENT_TYPE_VOLUME_CHANGED", "EVENT_TYPE_CMD_RESULT", "EVENT_TYPE_SUBSCRIBER_INFO", "EVENT_TYPE_RESP_AND_HOLD", "EVENT_TYPE_IN_BAND_RING", "EVENT_TYPE_LAST_VOICE_TAG_NUMBER", "EVENT_TYPE_RING_INDICATION"};
    private Uri alert = RingtoneManager.getDefaultUri(4);
    private final BluetoothAdapter mAdapter;
    private final AudioManager mAudioManager;
    private final AudioOn mAudioOn;
    private int mAudioState;
    private boolean mAudioWbs;
    private Hashtable<Integer, BluetoothHeadsetClientCall> mCalls;
    private Hashtable<Integer, BluetoothHeadsetClientCall> mCallsUpdate;
    private int mChldFeatures;
    private final Connected mConnected;
    private final Connecting mConnecting;
    private BluetoothDevice mCurrentDevice = null;
    private final Disconnected mDisconnected;
    private int mInBandRingtone;
    private int mIndicatorBatteryLevel;
    private int mIndicatorCall;
    private int mIndicatorCallHeld;
    private int mIndicatorCallSetup;
    private int mIndicatorNetworkSignal;
    private int mIndicatorNetworkState;
    private int mIndicatorNetworkType;
    private boolean mNativeAvailable;
    private String mOperatorName;
    private int mPeerFeatures;
    private Pair<Integer, Object> mPendingAction;
    private boolean mQueryCallsSupported;
    private Queue<Pair<Integer, Object>> mQueuedActions;
    private Ringtone mRingtone = null;
    private final HeadsetClientService mService;
    private String mSubscriberInfo;
    private boolean mVgmFromStack = DBG;
    private boolean mVgsFromStack = DBG;
    private int mVoiceRecognitionActive;

    private class AudioOn extends State {
        private AudioOn() {
        }

        public void enter() {
            Log.d(HeadsetClientStateMachine.TAG, "Enter AudioOn: " + HeadsetClientStateMachine.this.getCurrentMessage().what);
            HeadsetClientStateMachine.this.mAudioManager.setStreamSolo(6, true);
        }

        public synchronized boolean processMessage(Message message) {
            boolean z = HeadsetClientStateMachine.DBG;
            synchronized (this) {
                Log.d(HeadsetClientStateMachine.TAG, "AudioOn process message: " + message.what);
                switch (message.what) {
                    case 2:
                        if (HeadsetClientStateMachine.this.mCurrentDevice.equals(message.obj)) {
                            HeadsetClientStateMachine.this.deferMessage(message);
                            break;
                        }
                        break;
                    case 4:
                        break;
                    case 100:
                        StackEvent event = message.obj;
                        switch (event.type) {
                            case 1:
                                Log.d(HeadsetClientStateMachine.TAG, "AudioOn connection state changed" + event.device + ": " + event.valueInt);
                                processConnectionEvent(event.valueInt, event.device);
                                break;
                            case 2:
                                Log.d(HeadsetClientStateMachine.TAG, "AudioOn audio state changed" + event.device + ": " + event.valueInt);
                                processAudioEvent(event.valueInt, event.device);
                                break;
                            default:
                                break;
                        }
                }
                if (HeadsetClientStateMachine.this.disconnectAudioNative(HeadsetClientStateMachine.this.getByteAddress(HeadsetClientStateMachine.this.mCurrentDevice))) {
                    HeadsetClientStateMachine.this.mAudioState = 0;
                    if (HeadsetClientStateMachine.this.mAudioManager.getMode() != 0) {
                        HeadsetClientStateMachine.this.mAudioManager.setMode(0);
                        Log.d(HeadsetClientStateMachine.TAG, "abandonAudioFocus");
                        HeadsetClientStateMachine.this.mAudioManager.abandonAudioFocusForCall();
                    }
                    Log.d(HeadsetClientStateMachine.TAG, "hfp_enable=false");
                    HeadsetClientStateMachine.this.mAudioManager.setParameters("hfp_enable=false");
                    HeadsetClientStateMachine.this.broadcastAudioState(HeadsetClientStateMachine.this.mCurrentDevice, 0, 2);
                }
                z = true;
            }
            return z;
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case 0:
                    if (HeadsetClientStateMachine.this.mCurrentDevice.equals(device)) {
                        processAudioEvent(0, device);
                        HeadsetClientStateMachine.this.broadcastConnectionState(HeadsetClientStateMachine.this.mCurrentDevice, 0, 2);
                        HeadsetClientStateMachine.this.mCurrentDevice = null;
                        HeadsetClientStateMachine.this.transitionTo(HeadsetClientStateMachine.this.mDisconnected);
                        return;
                    }
                    Log.e(HeadsetClientStateMachine.TAG, "Disconnected from unknown device: " + device);
                    return;
                default:
                    Log.e(HeadsetClientStateMachine.TAG, "Connection State Device: " + device + " bad state: " + state);
                    return;
            }
        }

        private void processAudioEvent(int state, BluetoothDevice device) {
            if (HeadsetClientStateMachine.this.mCurrentDevice.equals(device)) {
                switch (state) {
                    case 0:
                        if (HeadsetClientStateMachine.this.mAudioState != 0) {
                            HeadsetClientStateMachine.this.mAudioState = 0;
                            if (HeadsetClientStateMachine.this.mAudioManager.getMode() != 0) {
                                HeadsetClientStateMachine.this.mAudioManager.setMode(0);
                                Log.d(HeadsetClientStateMachine.TAG, "abandonAudioFocus");
                                HeadsetClientStateMachine.this.mAudioManager.abandonAudioFocusForCall();
                            }
                            Log.d(HeadsetClientStateMachine.TAG, "hfp_enable=false");
                            HeadsetClientStateMachine.this.mAudioManager.setParameters("hfp_enable=false");
                            HeadsetClientStateMachine.this.broadcastAudioState(device, 0, 2);
                        }
                        HeadsetClientStateMachine.this.transitionTo(HeadsetClientStateMachine.this.mConnected);
                        return;
                    default:
                        Log.e(HeadsetClientStateMachine.TAG, "Audio State Device: " + device + " bad state: " + state);
                        return;
                }
            }
            Log.e(HeadsetClientStateMachine.TAG, "Audio changed on disconnected device: " + device);
        }

        public void exit() {
            Log.d(HeadsetClientStateMachine.TAG, "Exit AudioOn: " + HeadsetClientStateMachine.this.getCurrentMessage().what);
            HeadsetClientStateMachine.this.mAudioManager.setStreamSolo(6, HeadsetClientStateMachine.DBG);
        }
    }

    private class Connected extends State {
        private Connected() {
        }

        public void enter() {
            Log.d(HeadsetClientStateMachine.TAG, "Enter Connected: " + HeadsetClientStateMachine.this.getCurrentMessage().what);
            HeadsetClientStateMachine.this.mAudioWbs = HeadsetClientStateMachine.DBG;
        }

        public synchronized boolean processMessage(Message message) {
            boolean z;
            Log.d(HeadsetClientStateMachine.TAG, "Connected process message: " + message.what);
            switch (message.what) {
                case 1:
                    BluetoothDevice device = message.obj;
                    if (!HeadsetClientStateMachine.this.mCurrentDevice.equals(device)) {
                        if (!HeadsetClientStateMachine.this.disconnectNative(HeadsetClientStateMachine.this.getByteAddress(HeadsetClientStateMachine.this.mCurrentDevice))) {
                            HeadsetClientStateMachine.this.broadcastConnectionState(device, 1, 0);
                            HeadsetClientStateMachine.this.broadcastConnectionState(device, 0, 1);
                            break;
                        }
                        HeadsetClientStateMachine.this.deferMessage(message);
                        break;
                    }
                    break;
                case 2:
                    BluetoothDevice dev = message.obj;
                    if (HeadsetClientStateMachine.this.mCurrentDevice.equals(dev)) {
                        HeadsetClientStateMachine.this.broadcastConnectionState(dev, 3, 2);
                        if (!HeadsetClientStateMachine.this.disconnectNative(HeadsetClientStateMachine.this.getByteAddress(dev))) {
                            HeadsetClientStateMachine.this.broadcastConnectionState(dev, 2, 0);
                            break;
                        }
                    }
                    break;
                case 3:
                    if (!HeadsetClientStateMachine.this.connectAudioNative(HeadsetClientStateMachine.this.getByteAddress(HeadsetClientStateMachine.this.mCurrentDevice))) {
                        Log.e(HeadsetClientStateMachine.TAG, "ERROR: Couldn't connect Audio.");
                        break;
                    }
                    break;
                case 4:
                    if (!HeadsetClientStateMachine.this.disconnectAudioNative(HeadsetClientStateMachine.this.getByteAddress(HeadsetClientStateMachine.this.mCurrentDevice))) {
                        Log.e(HeadsetClientStateMachine.TAG, "ERROR: Couldn't connect Audio.");
                        break;
                    }
                    break;
                case 5:
                    if (HeadsetClientStateMachine.this.mVoiceRecognitionActive == 0) {
                        if (!HeadsetClientStateMachine.this.startVoiceRecognitionNative()) {
                            Log.e(HeadsetClientStateMachine.TAG, "ERROR: Couldn't start voice recognition");
                            break;
                        }
                        HeadsetClientStateMachine.this.addQueuedAction(5);
                        break;
                    }
                    break;
                case 6:
                    if (HeadsetClientStateMachine.this.mVoiceRecognitionActive == 1) {
                        if (!HeadsetClientStateMachine.this.stopVoiceRecognitionNative()) {
                            Log.e(HeadsetClientStateMachine.TAG, "ERROR: Couldn't stop voice recognition");
                            break;
                        }
                        HeadsetClientStateMachine.this.addQueuedAction(6);
                        break;
                    }
                    break;
                case AbstractionLayer.BT_STATUS_PARM_INVALID /*7*/:
                    if (!HeadsetClientStateMachine.this.mVgmFromStack) {
                        if (HeadsetClientStateMachine.this.setVolumeNative(1, message.arg1)) {
                            HeadsetClientStateMachine.this.addQueuedAction(7);
                            break;
                        }
                    }
                    HeadsetClientStateMachine.this.mVgmFromStack = HeadsetClientStateMachine.DBG;
                    break;
                    break;
                case 8:
                    Log.d(HeadsetClientStateMachine.TAG, "Volume is set to " + message.arg1);
                    HeadsetClientStateMachine.this.mAudioManager.setParameters("hfp_volume=" + message.arg1);
                    if (!HeadsetClientStateMachine.this.mVgsFromStack) {
                        if (HeadsetClientStateMachine.this.setVolumeNative(0, message.arg1)) {
                            HeadsetClientStateMachine.this.addQueuedAction(8);
                            break;
                        }
                    }
                    HeadsetClientStateMachine.this.mVgsFromStack = HeadsetClientStateMachine.DBG;
                    break;
                    break;
                case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                    if (!HeadsetClientStateMachine.this.dialNative(null)) {
                        Log.e(HeadsetClientStateMachine.TAG, "ERROR: Cannot redial");
                        break;
                    }
                    HeadsetClientStateMachine.this.addQueuedAction(9);
                    break;
                case 10:
                    if (!HeadsetClientStateMachine.this.dialNative((String) message.obj)) {
                        Log.e(HeadsetClientStateMachine.TAG, "ERROR: Cannot dial with a given number:" + ((String) message.obj));
                        break;
                    }
                    HeadsetClientStateMachine.this.addQueuedAction(10, message.obj);
                    break;
                case 11:
                    if (!HeadsetClientStateMachine.this.dialMemoryNative(message.arg1)) {
                        Log.e(HeadsetClientStateMachine.TAG, "ERROR: Cannot dial with a given location:" + message.arg1);
                        break;
                    }
                    HeadsetClientStateMachine.this.addQueuedAction(11);
                    break;
                case 12:
                    HeadsetClientStateMachine.this.acceptCall(message.arg1, HeadsetClientStateMachine.DBG);
                    break;
                case BluetoothCmeError.SIM_FAILURE /*13*/:
                    HeadsetClientStateMachine.this.rejectCall();
                    break;
                case BluetoothCmeError.SIM_BUSY /*14*/:
                    HeadsetClientStateMachine.this.holdCall();
                    break;
                case VCardConstants.MAX_DATA_COLUMN /*15*/:
                    HeadsetClientStateMachine.this.terminateCall(message.arg1);
                    break;
                case BluetoothCmeError.WRONG_PASSWORD /*16*/:
                    HeadsetClientStateMachine.this.enterPrivateMode(message.arg1);
                    break;
                case BluetoothCmeError.SIM_PIN2_REQUIRED /*17*/:
                    if (!HeadsetClientStateMachine.this.sendDtmfNative((byte) message.arg1)) {
                        Log.e(HeadsetClientStateMachine.TAG, "ERROR: Couldn't send DTMF");
                        break;
                    }
                    HeadsetClientStateMachine.this.addQueuedAction(17);
                    break;
                case BluetoothCmeError.SIM_PUK2_REQUIRED /*18*/:
                    HeadsetClientStateMachine.this.explicitCallTransfer();
                    break;
                case 19:
                    if (!HeadsetClientStateMachine.this.requestLastVoiceTagNumberNative()) {
                        Log.e(HeadsetClientStateMachine.TAG, "ERROR: Couldn't get last VTAG number");
                        break;
                    }
                    HeadsetClientStateMachine.this.addQueuedAction(19);
                    break;
                case HeadsetClientStateMachine.QUERY_CURRENT_CALLS /*50*/:
                    HeadsetClientStateMachine.this.queryCallsStart();
                    break;
                case HeadsetClientStateMachine.SUBSCRIBER_INFO /*52*/:
                    if (!HeadsetClientStateMachine.this.retrieveSubscriberInfoNative()) {
                        Log.e(HeadsetClientStateMachine.TAG, "ERROR: Couldn't retrieve subscriber info");
                        break;
                    }
                    HeadsetClientStateMachine.this.addQueuedAction(HeadsetClientStateMachine.SUBSCRIBER_INFO);
                    break;
                case 100:
                    StackEvent event = message.obj;
                    Intent intent;
                    switch (event.type) {
                        case 1:
                            Log.d(HeadsetClientStateMachine.TAG, "Connected: Connection state changed: " + event.device + ": " + event.valueInt);
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case 2:
                            Log.d(HeadsetClientStateMachine.TAG, "Connected: Audio state changed: " + event.device + ": " + event.valueInt);
                            processAudioEvent(event.valueInt, event.device);
                            break;
                        case 3:
                            Log.d(HeadsetClientStateMachine.TAG, "Connected: Voice recognition state: " + event.valueInt);
                            if (HeadsetClientStateMachine.this.mVoiceRecognitionActive != event.valueInt) {
                                HeadsetClientStateMachine.this.mVoiceRecognitionActive = event.valueInt;
                                intent = new Intent("android.bluetooth.headsetclient.profile.action.AG_EVENT");
                                intent.putExtra("android.bluetooth.headsetclient.extra.VOICE_RECOGNITION", HeadsetClientStateMachine.this.mVoiceRecognitionActive);
                                intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
                                HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                                break;
                            }
                            break;
                        case 4:
                            Log.d(HeadsetClientStateMachine.TAG, "Connected: Network state: " + event.valueInt);
                            HeadsetClientStateMachine.this.mIndicatorNetworkState = event.valueInt;
                            intent = new Intent("android.bluetooth.headsetclient.profile.action.AG_EVENT");
                            intent.putExtra("android.bluetooth.headsetclient.extra.NETWORK_STATUS", event.valueInt);
                            if (HeadsetClientStateMachine.this.mIndicatorNetworkState == 0) {
                                HeadsetClientStateMachine.this.mOperatorName = null;
                                intent.putExtra("android.bluetooth.headsetclient.extra.OPERATOR_NAME", HeadsetClientStateMachine.this.mOperatorName);
                            }
                            intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
                            HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            if (HeadsetClientStateMachine.this.mIndicatorNetworkState == 1) {
                                if (!HeadsetClientStateMachine.this.queryCurrentOperatorNameNative()) {
                                    Log.e(HeadsetClientStateMachine.TAG, "ERROR: Couldn't querry operator name");
                                    break;
                                }
                                HeadsetClientStateMachine.this.addQueuedAction(HeadsetClientStateMachine.QUERY_OPERATOR_NAME);
                                break;
                            }
                            break;
                        case 5:
                            Log.d(HeadsetClientStateMachine.TAG, "Connected: Roaming state: " + event.valueInt);
                            HeadsetClientStateMachine.this.mIndicatorNetworkType = event.valueInt;
                            intent = new Intent("android.bluetooth.headsetclient.profile.action.AG_EVENT");
                            intent.putExtra("android.bluetooth.headsetclient.extra.NETWORK_ROAMING", event.valueInt);
                            intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
                            HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case 6:
                            Log.d(HeadsetClientStateMachine.TAG, "Connected: Signal level: " + event.valueInt);
                            HeadsetClientStateMachine.this.mIndicatorNetworkSignal = event.valueInt;
                            intent = new Intent("android.bluetooth.headsetclient.profile.action.AG_EVENT");
                            intent.putExtra("android.bluetooth.headsetclient.extra.NETWORK_SIGNAL_STRENGTH", event.valueInt);
                            intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
                            HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case AbstractionLayer.BT_STATUS_PARM_INVALID /*7*/:
                            Log.d(HeadsetClientStateMachine.TAG, "Connected: Battery level: " + event.valueInt);
                            HeadsetClientStateMachine.this.mIndicatorBatteryLevel = event.valueInt;
                            intent = new Intent("android.bluetooth.headsetclient.profile.action.AG_EVENT");
                            intent.putExtra("android.bluetooth.headsetclient.extra.BATTERY_LEVEL", event.valueInt);
                            intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
                            HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case 8:
                            Log.d(HeadsetClientStateMachine.TAG, "Connected: Operator name: " + event.valueString);
                            HeadsetClientStateMachine.this.mOperatorName = event.valueString;
                            intent = new Intent("android.bluetooth.headsetclient.profile.action.AG_EVENT");
                            intent.putExtra("android.bluetooth.headsetclient.extra.OPERATOR_NAME", event.valueString);
                            intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
                            HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                            HeadsetClientStateMachine.this.updateCallIndicator(event.valueInt);
                            break;
                        case 10:
                            HeadsetClientStateMachine.this.updateCallSetupIndicator(event.valueInt);
                            break;
                        case 11:
                            HeadsetClientStateMachine.this.updateCallHeldIndicator(event.valueInt);
                            break;
                        case 12:
                            HeadsetClientStateMachine.this.updateClip(event.valueString);
                            break;
                        case BluetoothCmeError.SIM_FAILURE /*13*/:
                            HeadsetClientStateMachine.this.addCallWaiting(event.valueString);
                            break;
                        case BluetoothCmeError.SIM_BUSY /*14*/:
                            HeadsetClientStateMachine.this.queryCallsUpdate(event.valueInt, event.valueInt3, event.valueString, event.valueInt4 == 1 ? true : HeadsetClientStateMachine.DBG, event.valueInt2 == 0 ? true : HeadsetClientStateMachine.DBG);
                            break;
                        case VCardConstants.MAX_DATA_COLUMN /*15*/:
                            if (event.valueInt != 0) {
                                if (event.valueInt == 1) {
                                    HeadsetClientStateMachine.this.mAudioManager.setMicrophoneMute(event.valueInt2 == 0 ? true : HeadsetClientStateMachine.DBG);
                                    HeadsetClientStateMachine.this.mVgmFromStack = true;
                                    break;
                                }
                            }
                            HeadsetClientStateMachine.this.mAudioManager.setStreamVolume(6, event.valueInt2, 1);
                            HeadsetClientStateMachine.this.mVgsFromStack = true;
                            break;
                            break;
                        case BluetoothCmeError.WRONG_PASSWORD /*16*/:
                            Pair<Integer, Object> queuedAction = (Pair) HeadsetClientStateMachine.this.mQueuedActions.poll();
                            if (queuedAction != null && ((Integer) queuedAction.first).intValue() != 0) {
                                Log.d(HeadsetClientStateMachine.TAG, "Connected: command result: " + event.valueInt + " queuedAction: " + queuedAction.first);
                                switch (((Integer) queuedAction.first).intValue()) {
                                    case 5:
                                    case 6:
                                        if (event.valueInt == 0) {
                                            if (((Integer) queuedAction.first).intValue() == 6) {
                                                HeadsetClientStateMachine.this.mVoiceRecognitionActive = 0;
                                            } else {
                                                HeadsetClientStateMachine.this.mVoiceRecognitionActive = 1;
                                            }
                                        }
                                        intent = new Intent("android.bluetooth.headsetclient.profile.action.AG_EVENT");
                                        intent.putExtra("android.bluetooth.headsetclient.extra.VOICE_RECOGNITION", HeadsetClientStateMachine.this.mVoiceRecognitionActive);
                                        intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
                                        HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                                        break;
                                    case AbstractionLayer.BT_STATUS_PARM_INVALID /*7*/:
                                    case 8:
                                    case HeadsetClientStateMachine.QUERY_OPERATOR_NAME /*51*/:
                                    case HeadsetClientStateMachine.SUBSCRIBER_INFO /*52*/:
                                        break;
                                    case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                                    case 10:
                                    case 11:
                                    case BluetoothCmeError.SIM_FAILURE /*13*/:
                                    case BluetoothCmeError.SIM_BUSY /*14*/:
                                    case VCardConstants.MAX_DATA_COLUMN /*15*/:
                                    case BluetoothCmeError.WRONG_PASSWORD /*16*/:
                                        if (event.valueInt != 0) {
                                            sendActionResultIntent(event);
                                            break;
                                        }
                                        HeadsetClientStateMachine.this.mPendingAction = queuedAction;
                                        break;
                                    case 12:
                                        if (event.valueInt != 0) {
                                            if (HeadsetClientStateMachine.this.callsInState(0) == 0) {
                                                if (HeadsetClientStateMachine.this.getCall(4) != null && ((Integer) HeadsetClientStateMachine.this.mPendingAction.second).intValue() == 7) {
                                                    HeadsetClientStateMachine.this.acceptCall(0, true);
                                                    break;
                                                }
                                                if (HeadsetClientStateMachine.this.getCall(5) != null && ((Integer) HeadsetClientStateMachine.this.mPendingAction.second).intValue() == 2) {
                                                    HeadsetClientStateMachine.this.acceptCall(0, true);
                                                    break;
                                                }
                                            }
                                            sendActionResultIntent(event);
                                            break;
                                        }
                                        HeadsetClientStateMachine.this.mPendingAction = queuedAction;
                                        break;
                                    case 19:
                                        if (event.valueInt != 0) {
                                            sendActionResultIntent(event);
                                            break;
                                        }
                                        break;
                                    case HeadsetClientStateMachine.QUERY_CURRENT_CALLS /*50*/:
                                        HeadsetClientStateMachine.this.queryCallsDone();
                                        break;
                                    case HeadsetClientStateMachine.TERMINATE_SPECIFIC_CALL /*53*/:
                                        if (event.valueInt != 0) {
                                            sendActionResultIntent(event);
                                            break;
                                        }
                                        BluetoothHeadsetClientCall c = queuedAction.second;
                                        HeadsetClientStateMachine.this.setCallState(c, 7);
                                        HeadsetClientStateMachine.this.mCalls.remove(Integer.valueOf(c.getId()));
                                        break;
                                    default:
                                        sendActionResultIntent(event);
                                        break;
                                }
                            }
                            HeadsetClientStateMachine.this.clearPendingAction();
                            break;
                            break;
                        case BluetoothCmeError.SIM_PIN2_REQUIRED /*17*/:
                            HeadsetClientStateMachine.this.mSubscriberInfo = event.valueString;
                            intent = new Intent("android.bluetooth.headsetclient.profile.action.AG_EVENT");
                            intent.putExtra("android.bluetooth.headsetclient.extra.SUBSCRIBER_INFO", HeadsetClientStateMachine.this.mSubscriberInfo);
                            intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
                            HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case BluetoothCmeError.SIM_PUK2_REQUIRED /*18*/:
                            HeadsetClientStateMachine.this.updateRespAndHold(event.valueInt);
                            break;
                        case 19:
                            if (HeadsetClientStateMachine.this.mInBandRingtone != event.valueInt) {
                                HeadsetClientStateMachine.this.mInBandRingtone = event.valueInt;
                                intent = new Intent("android.bluetooth.headsetclient.profile.action.AG_EVENT");
                                intent.putExtra("android.bluetooth.headsetclient.extra.IN_BAND_RING", HeadsetClientStateMachine.this.mInBandRingtone);
                                intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
                                HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                                break;
                            }
                            break;
                        case 20:
                            intent = new Intent("android.bluetooth.headsetclient.profile.action.LAST_VTAG");
                            intent.putExtra("android.bluetooth.headsetclient.extra.NUMBER", event.valueString);
                            intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
                            HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                            break;
                        case 21:
                            Log.e(HeadsetClientStateMachine.TAG, "start ringing");
                            if (HeadsetClientStateMachine.this.mRingtone != null && HeadsetClientStateMachine.this.mRingtone.isPlaying()) {
                                Log.d(HeadsetClientStateMachine.TAG, "ring already playing");
                                break;
                            }
                            int currMode = HeadsetClientStateMachine.this.mAudioManager.getMode();
                            if (currMode != 1) {
                                HeadsetClientStateMachine.this.mAudioManager.requestAudioFocusForCall(1, 2);
                                Log.d(HeadsetClientStateMachine.TAG, "setAudioMode Setting audio mode from " + currMode + " to " + 1);
                                HeadsetClientStateMachine.this.mAudioManager.setMode(1);
                            }
                            if (HeadsetClientStateMachine.this.mRingtone != null) {
                                HeadsetClientStateMachine.this.mRingtone.play();
                                break;
                            }
                            break;
                        default:
                            Log.e(HeadsetClientStateMachine.TAG, "Unknown stack event: " + event.type);
                            break;
                    }
                default:
                    z = HeadsetClientStateMachine.DBG;
                    break;
            }
            z = true;
            return z;
        }

        private void sendActionResultIntent(StackEvent event) {
            Intent intent = new Intent("android.bluetooth.headsetclient.profile.action.RESULT");
            intent.putExtra("android.bluetooth.headsetclient.extra.RESULT_CODE", event.valueInt);
            if (event.valueInt == 7) {
                intent.putExtra("android.bluetooth.headsetclient.extra.CME_CODE", event.valueInt2);
            }
            intent.putExtra("android.bluetooth.device.extra.DEVICE", event.device);
            HeadsetClientStateMachine.this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case 0:
                    Log.d(HeadsetClientStateMachine.TAG, "Connected disconnects.");
                    if (HeadsetClientStateMachine.this.mCurrentDevice.equals(device)) {
                        HeadsetClientStateMachine.this.broadcastConnectionState(HeadsetClientStateMachine.this.mCurrentDevice, 0, 2);
                        HeadsetClientStateMachine.this.mCurrentDevice = null;
                        HeadsetClientStateMachine.this.transitionTo(HeadsetClientStateMachine.this.mDisconnected);
                        return;
                    }
                    Log.e(HeadsetClientStateMachine.TAG, "Disconnected from unknown device: " + device);
                    return;
                default:
                    Log.e(HeadsetClientStateMachine.TAG, "Connection State Device: " + device + " bad state: " + state);
                    return;
            }
        }

        private void processAudioEvent(int state, BluetoothDevice device) {
            if (HeadsetClientStateMachine.this.mCurrentDevice.equals(device)) {
                switch (state) {
                    case 0:
                        if (HeadsetClientStateMachine.this.mAudioState == 1) {
                            HeadsetClientStateMachine.this.mAudioState = 0;
                            HeadsetClientStateMachine.this.broadcastAudioState(device, 0, 1);
                            return;
                        }
                        return;
                    case 1:
                        HeadsetClientStateMachine.this.mAudioState = 1;
                        HeadsetClientStateMachine.this.broadcastAudioState(device, 1, 0);
                        return;
                    case 2:
                        break;
                    case 3:
                        HeadsetClientStateMachine.this.mAudioWbs = true;
                        break;
                    default:
                        Log.e(HeadsetClientStateMachine.TAG, "Audio State Device: " + device + " bad state: " + state);
                        return;
                }
                HeadsetClientStateMachine.this.mAudioState = 2;
                if (HeadsetClientStateMachine.this.mRingtone != null && HeadsetClientStateMachine.this.mRingtone.isPlaying()) {
                    Log.d(HeadsetClientStateMachine.TAG, "stopping ring and request focus for call");
                    HeadsetClientStateMachine.this.mRingtone.stop();
                }
                int currMode = HeadsetClientStateMachine.this.mAudioManager.getMode();
                if (currMode != 2) {
                    HeadsetClientStateMachine.this.mAudioManager.requestAudioFocusForCall(0, 2);
                    Log.d(HeadsetClientStateMachine.TAG, "setAudioMode Setting audio mode from " + currMode + " to " + 2);
                    HeadsetClientStateMachine.this.mAudioManager.setMode(2);
                }
                Log.d(HeadsetClientStateMachine.TAG, "hfp_enable=true");
                Log.d(HeadsetClientStateMachine.TAG, "mAudioWbs is " + HeadsetClientStateMachine.this.mAudioWbs);
                if (HeadsetClientStateMachine.this.mAudioWbs) {
                    Log.d(HeadsetClientStateMachine.TAG, "Setting sampling rate as 16000");
                    HeadsetClientStateMachine.this.mAudioManager.setParameters("hfp_set_sampling_rate=16000");
                } else {
                    Log.d(HeadsetClientStateMachine.TAG, "Setting sampling rate as 8000");
                    HeadsetClientStateMachine.this.mAudioManager.setParameters("hfp_set_sampling_rate=8000");
                }
                HeadsetClientStateMachine.this.mAudioManager.setParameters("hfp_enable=true");
                HeadsetClientStateMachine.this.broadcastAudioState(device, 2, 1);
                HeadsetClientStateMachine.this.transitionTo(HeadsetClientStateMachine.this.mAudioOn);
                return;
            }
            Log.e(HeadsetClientStateMachine.TAG, "Audio changed on disconnected device: " + device);
        }

        public void exit() {
            Log.d(HeadsetClientStateMachine.TAG, "Exit Connected: " + HeadsetClientStateMachine.this.getCurrentMessage().what);
        }
    }

    private class Connecting extends State {
        private Connecting() {
        }

        public void enter() {
            Log.d(HeadsetClientStateMachine.TAG, "Enter Connecting: " + HeadsetClientStateMachine.this.getCurrentMessage().what);
        }

        public synchronized boolean processMessage(Message message) {
            boolean retValue;
            Log.d(HeadsetClientStateMachine.TAG, "Connecting process message: " + message.what);
            retValue = true;
            switch (message.what) {
                case 1:
                case 2:
                case 3:
                    HeadsetClientStateMachine.this.deferMessage(message);
                    break;
                case 100:
                    StackEvent event = message.obj;
                    switch (event.type) {
                        case 1:
                            Log.d(HeadsetClientStateMachine.TAG, "Connecting: Connection " + event.device + " state changed:" + event.valueInt);
                            processConnectionEvent(event.valueInt, event.valueInt2, event.valueInt3, event.device);
                            break;
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                        case 6:
                        case AbstractionLayer.BT_STATUS_PARM_INVALID /*7*/:
                        case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                        case 10:
                        case 11:
                        case 12:
                        case BluetoothCmeError.SIM_FAILURE /*13*/:
                        case VCardConstants.MAX_DATA_COLUMN /*15*/:
                        case BluetoothCmeError.SIM_PUK2_REQUIRED /*18*/:
                        case 19:
                            HeadsetClientStateMachine.this.deferMessage(message);
                            break;
                        default:
                            Log.e(HeadsetClientStateMachine.TAG, "Connecting: ignoring stack event: " + event.type);
                            break;
                    }
                default:
                    retValue = HeadsetClientStateMachine.DBG;
                    break;
            }
            return retValue;
        }

        private void processConnectionEvent(int state, int peer_feat, int chld_feat, BluetoothDevice device) {
            int i = 15;
            switch (state) {
                case 0:
                    HeadsetClientStateMachine.this.broadcastConnectionState(HeadsetClientStateMachine.this.mCurrentDevice, 0, 1);
                    HeadsetClientStateMachine.this.mCurrentDevice = null;
                    HeadsetClientStateMachine.this.transitionTo(HeadsetClientStateMachine.this.mDisconnected);
                    return;
                case 1:
                    Log.d(HeadsetClientStateMachine.TAG, "outgoing connection started, ignore");
                    return;
                case 2:
                    if (!HeadsetClientStateMachine.this.mCurrentDevice.equals(device)) {
                        Log.w(HeadsetClientStateMachine.TAG, "incoming connection event, device: " + device);
                        HeadsetClientStateMachine.this.broadcastConnectionState(HeadsetClientStateMachine.this.mCurrentDevice, 0, 1);
                        HeadsetClientStateMachine.this.broadcastConnectionState(device, 1, 0);
                        HeadsetClientStateMachine.this.mCurrentDevice = device;
                        return;
                    }
                    return;
                case 3:
                    Log.w(HeadsetClientStateMachine.TAG, "HFPClient Connected from Connecting state");
                    HeadsetClientStateMachine.this.mPeerFeatures = peer_feat;
                    HeadsetClientStateMachine.this.mChldFeatures = chld_feat;
                    HeadsetClientStateMachine.this.broadcastConnectionState(HeadsetClientStateMachine.this.mCurrentDevice, 2, 1);
                    HeadsetClientStateMachine.this.sendATCmdNative(15, 1, 0, null);
                    HeadsetClientStateMachine.this.transitionTo(HeadsetClientStateMachine.this.mConnected);
                    HeadsetClientStateMachine.this.sendMessage(HeadsetClientStateMachine.this.obtainMessage(8, HeadsetClientStateMachine.this.mAudioManager.getStreamVolume(6), 0));
                    HeadsetClientStateMachine headsetClientStateMachine = HeadsetClientStateMachine.this;
                    HeadsetClientStateMachine headsetClientStateMachine2 = HeadsetClientStateMachine.this;
                    if (HeadsetClientStateMachine.this.mAudioManager.isMicrophoneMute()) {
                        i = 0;
                    }
                    headsetClientStateMachine.sendMessage(headsetClientStateMachine2.obtainMessage(7, i, 0));
                    HeadsetClientStateMachine.this.sendMessage(HeadsetClientStateMachine.SUBSCRIBER_INFO);
                    return;
                default:
                    Log.e(HeadsetClientStateMachine.TAG, "Incorrect state: " + state);
                    return;
            }
        }

        public void exit() {
            Log.d(HeadsetClientStateMachine.TAG, "Exit Connecting: " + HeadsetClientStateMachine.this.getCurrentMessage().what);
        }
    }

    private class Disconnected extends State {
        private Disconnected() {
        }

        public void enter() {
            Log.d(HeadsetClientStateMachine.TAG, "Enter Disconnected: " + HeadsetClientStateMachine.this.getCurrentMessage().what);
            HeadsetClientStateMachine.this.mIndicatorNetworkState = 0;
            HeadsetClientStateMachine.this.mIndicatorNetworkType = 0;
            HeadsetClientStateMachine.this.mIndicatorNetworkSignal = 0;
            HeadsetClientStateMachine.this.mIndicatorBatteryLevel = 0;
            HeadsetClientStateMachine.this.mAudioWbs = HeadsetClientStateMachine.DBG;
            HeadsetClientStateMachine.this.mIndicatorCall = -1;
            HeadsetClientStateMachine.this.mIndicatorCallSetup = -1;
            HeadsetClientStateMachine.this.mIndicatorCallHeld = -1;
            HeadsetClientStateMachine.this.mOperatorName = null;
            HeadsetClientStateMachine.this.mSubscriberInfo = null;
            HeadsetClientStateMachine.this.mQueuedActions = new LinkedList();
            HeadsetClientStateMachine.this.clearPendingAction();
            HeadsetClientStateMachine.this.mVoiceRecognitionActive = 0;
            HeadsetClientStateMachine.this.mInBandRingtone = 0;
            HeadsetClientStateMachine.this.mCalls = new Hashtable();
            HeadsetClientStateMachine.this.mCallsUpdate = null;
            HeadsetClientStateMachine.this.mQueryCallsSupported = true;
            HeadsetClientStateMachine.this.mPeerFeatures = 0;
            HeadsetClientStateMachine.this.mChldFeatures = 0;
            HeadsetClientStateMachine.this.removeMessages(HeadsetClientStateMachine.QUERY_CURRENT_CALLS);
        }

        public synchronized boolean processMessage(Message message) {
            boolean z = HeadsetClientStateMachine.DBG;
            synchronized (this) {
                Log.d(HeadsetClientStateMachine.TAG, "Disconnected process message: " + message.what);
                if (HeadsetClientStateMachine.this.mCurrentDevice != null) {
                    Log.e(HeadsetClientStateMachine.TAG, "ERROR: current device not null in Disconnected");
                } else {
                    switch (message.what) {
                        case 1:
                            BluetoothDevice device = message.obj;
                            HeadsetClientStateMachine.this.broadcastConnectionState(device, 1, 0);
                            if (!HeadsetClientStateMachine.this.connectNative(HeadsetClientStateMachine.this.getByteAddress(device))) {
                                HeadsetClientStateMachine.this.broadcastConnectionState(device, 0, 1);
                                break;
                            }
                            HeadsetClientStateMachine.this.mCurrentDevice = device;
                            HeadsetClientStateMachine.this.transitionTo(HeadsetClientStateMachine.this.mConnecting);
                            break;
                        case 2:
                            break;
                        case 100:
                            StackEvent event = message.obj;
                            switch (event.type) {
                                case 1:
                                    Log.d(HeadsetClientStateMachine.TAG, "Disconnected: Connection " + event.device + " state changed:" + event.valueInt);
                                    processConnectionEvent(event.valueInt, event.device);
                                    break;
                                default:
                                    Log.e(HeadsetClientStateMachine.TAG, "Disconnected: Unexpected stack event: " + event.type);
                                    break;
                            }
                        default:
                            break;
                    }
                    z = true;
                }
            }
            return z;
        }

        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case 2:
                    Log.w(HeadsetClientStateMachine.TAG, "HFPClient Connecting from Disconnected state");
                    if (HeadsetClientStateMachine.this.okToConnect(device)) {
                        Log.i(HeadsetClientStateMachine.TAG, "Incoming AG accepted");
                        HeadsetClientStateMachine.this.broadcastConnectionState(device, 1, 0);
                        HeadsetClientStateMachine.this.mCurrentDevice = device;
                        HeadsetClientStateMachine.this.transitionTo(HeadsetClientStateMachine.this.mConnecting);
                        return;
                    }
                    Log.i(HeadsetClientStateMachine.TAG, "Incoming AG rejected. priority=" + HeadsetClientStateMachine.this.mService.getPriority(device) + " bondState=" + device.getBondState());
                    HeadsetClientStateMachine.this.disconnectNative(HeadsetClientStateMachine.this.getByteAddress(device));
                    AdapterService adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device, 2);
                        return;
                    }
                    return;
                default:
                    Log.i(HeadsetClientStateMachine.TAG, "ignoring state: " + state);
                    return;
            }
        }

        public void exit() {
            Log.d(HeadsetClientStateMachine.TAG, "Exit Disconnected: " + HeadsetClientStateMachine.this.getCurrentMessage().what);
        }
    }

    private class StackEvent {
        BluetoothDevice device;
        int type;
        int valueInt;
        int valueInt2;
        int valueInt3;
        int valueInt4;
        String valueString;

        private StackEvent(int type) {
            this.type = 0;
            this.valueInt = 0;
            this.valueInt2 = 0;
            this.valueInt3 = 0;
            this.valueInt4 = 0;
            this.valueString = null;
            this.device = null;
            this.type = type;
        }

        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("StackEvent {type:" + HeadsetClientStateMachine.this.EVENT_TYPE_NAMES[this.type]);
            result.append(", value1:" + this.valueInt);
            result.append(", value2:" + this.valueInt2);
            result.append(", value3:" + this.valueInt3);
            result.append(", value4:" + this.valueInt4);
            result.append(", string: \"" + this.valueString + "\"");
            result.append(", device:" + this.device + "}");
            return result.toString();
        }
    }

    private static native void classInitNative();

    private native void cleanupNative();

    private native boolean connectAudioNative(byte[] bArr);

    private native boolean connectNative(byte[] bArr);

    private native boolean dialMemoryNative(int i);

    private native boolean dialNative(String str);

    private native boolean disconnectAudioNative(byte[] bArr);

    private native boolean disconnectNative(byte[] bArr);

    private native boolean handleCallActionNative(int i, int i2);

    private native void initializeNative();

    private native boolean queryCurrentCallsNative();

    private native boolean queryCurrentOperatorNameNative();

    private native boolean requestLastVoiceTagNumberNative();

    private native boolean retrieveSubscriberInfoNative();

    private native boolean sendATCmdNative(int i, int i2, int i3, String str);

    private native boolean sendDtmfNative(byte b);

    private native boolean setVolumeNative(int i, int i2);

    private native boolean startVoiceRecognitionNative();

    private native boolean stopVoiceRecognitionNative();

    static {
        classInitNative();
    }

    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mCurrentDevice: " + this.mCurrentDevice);
        ProfileService.println(sb, "mAudioOn: " + this.mAudioOn);
        ProfileService.println(sb, "mAudioState: " + this.mAudioState);
        ProfileService.println(sb, "mAudioWbs: " + this.mAudioWbs);
        ProfileService.println(sb, "mIndicatorNetworkState: " + this.mIndicatorNetworkState);
        ProfileService.println(sb, "mIndicatorNetworkType: " + this.mIndicatorNetworkType);
        ProfileService.println(sb, "mIndicatorNetworkSignal: " + this.mIndicatorNetworkSignal);
        ProfileService.println(sb, "mIndicatorBatteryLevel: " + this.mIndicatorBatteryLevel);
        ProfileService.println(sb, "mIndicatorCall: " + this.mIndicatorCall);
        ProfileService.println(sb, "mIndicatorCallSetup: " + this.mIndicatorCallSetup);
        ProfileService.println(sb, "mIndicatorCallHeld: " + this.mIndicatorCallHeld);
        ProfileService.println(sb, "mVgsFromStack: " + this.mVgsFromStack);
        ProfileService.println(sb, "mVgmFromStack: " + this.mVgmFromStack);
        ProfileService.println(sb, "mRingtone: " + this.mRingtone);
        ProfileService.println(sb, "mOperatorName: " + this.mOperatorName);
        ProfileService.println(sb, "mSubscriberInfo: " + this.mSubscriberInfo);
        ProfileService.println(sb, "mVoiceRecognitionActive: " + this.mVoiceRecognitionActive);
        ProfileService.println(sb, "mInBandRingtone: " + this.mInBandRingtone);
        ProfileService.println(sb, "mCalls:");
        for (BluetoothHeadsetClientCall call : this.mCalls.values()) {
            ProfileService.println(sb, "  " + call);
        }
        ProfileService.println(sb, "mCallsUpdate:");
        for (BluetoothHeadsetClientCall call2 : this.mCallsUpdate.values()) {
            ProfileService.println(sb, "  " + call2);
        }
    }

    private void clearPendingAction() {
        this.mPendingAction = new Pair(Integer.valueOf(0), Integer.valueOf(0));
    }

    private void addQueuedAction(int action) {
        addQueuedAction(action, 0);
    }

    private void addQueuedAction(int action, Object data) {
        this.mQueuedActions.add(new Pair(Integer.valueOf(action), data));
    }

    private void addQueuedAction(int action, int data) {
        this.mQueuedActions.add(new Pair(Integer.valueOf(action), Integer.valueOf(data)));
    }

    private void addCall(int state, String number) {
        Log.d(TAG, "addToCalls state:" + state + " number:" + number);
        boolean outgoing = (state == 2 || state == 3) ? true : DBG;
        Integer id = Integer.valueOf(1);
        while (this.mCalls.containsKey(id)) {
            id = Integer.valueOf(id.intValue() + 1);
        }
        BluetoothHeadsetClientCall c = new BluetoothHeadsetClientCall(id.intValue(), state, number, DBG, outgoing);
        this.mCalls.put(id, c);
        sendCallChangedIntent(c);
    }

    private void removeCalls(int... states) {
        Log.d(TAG, "removeFromCalls states:" + Arrays.toString(states));
        Iterator<Entry<Integer, BluetoothHeadsetClientCall>> it = this.mCalls.entrySet().iterator();
        while (it.hasNext()) {
            BluetoothHeadsetClientCall c = (BluetoothHeadsetClientCall) ((Entry) it.next()).getValue();
            for (int s : states) {
                if (c.getState() == s) {
                    it.remove();
                    setCallState(c, 7);
                    break;
                }
            }
        }
    }

    private void changeCallsState(int old_state, int new_state) {
        Log.d(TAG, "changeStateFromCalls old:" + old_state + " new: " + new_state);
        for (BluetoothHeadsetClientCall c : this.mCalls.values()) {
            if (c.getState() == old_state) {
                setCallState(c, new_state);
            }
        }
    }

    private BluetoothHeadsetClientCall getCall(int... states) {
        Log.d(TAG, "getFromCallsWithStates states:" + Arrays.toString(states));
        for (BluetoothHeadsetClientCall c : this.mCalls.values()) {
            for (int s : states) {
                if (c.getState() == s) {
                    return c;
                }
            }
        }
        return null;
    }

    private int callsInState(int state) {
        int i = 0;
        for (BluetoothHeadsetClientCall c : this.mCalls.values()) {
            if (c.getState() == state) {
                i++;
            }
        }
        return i;
    }

    private void updateCallsMultiParty() {
        boolean multi = true;
        if (callsInState(0) <= 1) {
            multi = DBG;
        }
        for (BluetoothHeadsetClientCall c : this.mCalls.values()) {
            if (c.getState() == 0) {
                if (c.isMultiParty() != multi) {
                    c.setMultiParty(multi);
                    sendCallChangedIntent(c);
                }
            } else if (c.isMultiParty()) {
                c.setMultiParty(DBG);
                sendCallChangedIntent(c);
            }
        }
    }

    private void setCallState(BluetoothHeadsetClientCall c, int state) {
        if (state != c.getState()) {
            if (state == 7 && this.mAudioManager.getMode() != 0) {
                this.mAudioManager.setMode(0);
                Log.d(TAG, "abandonAudioFocus ");
                this.mAudioManager.abandonAudioFocusForCall();
            }
            c.setState(state);
            sendCallChangedIntent(c);
        }
    }

    private void sendCallChangedIntent(BluetoothHeadsetClientCall c) {
        Intent intent = new Intent("android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED");
        intent.putExtra("android.bluetooth.headsetclient.extra.CALL", c);
        this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private boolean waitForIndicators(int call, int callsetup, int callheld) {
        if (this.mIndicatorCall != -1 && this.mIndicatorCallSetup != -1 && this.mIndicatorCallHeld != -1) {
            return DBG;
        }
        if (call != -1) {
            this.mIndicatorCall = call;
        } else if (callsetup != -1) {
            this.mIndicatorCallSetup = callsetup;
        } else if (callheld != -1) {
            this.mIndicatorCallHeld = callheld;
        }
        if (this.mIndicatorCall == -1 || this.mIndicatorCallSetup == -1 || this.mIndicatorCallHeld == -1) {
            return true;
        }
        this.mQueryCallsSupported = queryCallsStart();
        if (this.mQueryCallsSupported) {
            return true;
        }
        switch (this.mIndicatorCallSetup) {
            case 1:
                addCall(4, "");
                break;
            case 2:
                addCall(2, "");
                break;
            case 3:
                addCall(3, "");
                break;
        }
        switch (this.mIndicatorCall) {
            case 1:
                addCall(0, "");
                break;
        }
        switch (this.mIndicatorCallHeld) {
            case 1:
            case 2:
                addCall(1, "");
                break;
        }
        return true;
    }

    private void updateCallIndicator(int call) {
        Log.d(TAG, "updateCallIndicator " + call);
        if (!waitForIndicators(call, -1, -1)) {
            if (this.mQueryCallsSupported) {
                sendMessage(QUERY_CURRENT_CALLS);
                return;
            }
            switch (call) {
                case 0:
                    removeCalls(0, 1, 6);
                    break;
                case 1:
                    BluetoothHeadsetClientCall c;
                    if (this.mIndicatorCall == 1) {
                        if (this.mIndicatorCallSetup != 0) {
                            c = getCall(5);
                            if (c != null) {
                                setCallState(c, 7);
                                this.mCalls.remove(Integer.valueOf(c.getId()));
                                break;
                            }
                        }
                    }
                    if (this.mIndicatorCallSetup != 0) {
                        c = getCall(2, 3, 4);
                        if (c != null) {
                            setCallState(c, 0);
                        }
                    }
                    updateCallsMultiParty();
                    break;
                    break;
            }
            this.mIndicatorCall = call;
        }
    }

    private void updateCallSetupIndicator(int callsetup) {
        Log.d(TAG, "updateCallSetupIndicator " + callsetup + " " + this.mPendingAction.first);
        if (this.mRingtone != null && this.mRingtone.isPlaying()) {
            Log.d(TAG, "stopping ring after no response");
            this.mRingtone.stop();
        }
        if (!waitForIndicators(-1, callsetup, -1)) {
            if (this.mQueryCallsSupported) {
                sendMessage(QUERY_CURRENT_CALLS);
                return;
            }
            switch (callsetup) {
                case 0:
                    switch (((Integer) this.mPendingAction.first).intValue()) {
                        case 0:
                        case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                        case 10:
                        case 11:
                        case VCardConstants.MAX_DATA_COLUMN /*15*/:
                            removeCalls(4, 2, 5, 3);
                            clearPendingAction();
                            break;
                        case 12:
                            switch (((Integer) this.mPendingAction.second).intValue()) {
                                case 1:
                                    removeCalls(0);
                                    changeCallsState(5, 0);
                                    clearPendingAction();
                                    break;
                                case 2:
                                    if (this.mIndicatorCallHeld == 1) {
                                        clearPendingAction();
                                        break;
                                    }
                                    break;
                                case 3:
                                    if (this.mIndicatorCallHeld == 0) {
                                        clearPendingAction();
                                        break;
                                    }
                                    break;
                                case AbstractionLayer.BT_STATUS_PARM_INVALID /*7*/:
                                    removeCalls(2, 3);
                                    clearPendingAction();
                                    break;
                                default:
                                    Log.e(TAG, "Unexpected callsetup=0 while in action ACCEPT_CALL");
                                    break;
                            }
                        case BluetoothCmeError.SIM_FAILURE /*13*/:
                            switch (((Integer) this.mPendingAction.second).intValue()) {
                                case 0:
                                    removeCalls(5);
                                    clearPendingAction();
                                    break;
                                case 8:
                                    removeCalls(4);
                                    clearPendingAction();
                                    break;
                                default:
                                    Log.e(TAG, "Unexpected callsetup=0 while in action REJECT_CALL");
                                    break;
                            }
                        default:
                            Log.e(TAG, "Unexpected callsetup=0 while in action " + this.mPendingAction.first);
                            break;
                    }
                case 1:
                    if (getCall(5) == null) {
                        addCall(4, "");
                        break;
                    }
                    break;
                case 2:
                    if (((Integer) this.mPendingAction.first).intValue() != 10) {
                        addCall(2, "");
                        break;
                    } else {
                        addCall(2, (String) this.mPendingAction.second);
                        break;
                    }
                case 3:
                    BluetoothHeadsetClientCall c = getCall(2);
                    if (c != null) {
                        setCallState(c, 3);
                    } else if (((Integer) this.mPendingAction.first).intValue() == 10) {
                        addCall(3, (String) this.mPendingAction.second);
                    } else {
                        addCall(3, "");
                    }
                    switch (((Integer) this.mPendingAction.first).intValue()) {
                        case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                        case 10:
                        case 11:
                            clearPendingAction();
                            break;
                        default:
                            break;
                    }
            }
            updateCallsMultiParty();
            this.mIndicatorCallSetup = callsetup;
        }
    }

    private void updateCallHeldIndicator(int callheld) {
        Log.d(TAG, "updateCallHeld " + callheld);
        if (!waitForIndicators(-1, -1, callheld)) {
            if (this.mQueryCallsSupported) {
                sendMessage(QUERY_CURRENT_CALLS);
                return;
            }
            switch (callheld) {
                case 0:
                    switch (((Integer) this.mPendingAction.first).intValue()) {
                        case 0:
                            if (this.mIndicatorCall != 1 || this.mIndicatorCallHeld != 2) {
                                removeCalls(1);
                                break;
                            }
                            changeCallsState(1, 0);
                            break;
                            break;
                        case 12:
                            switch (((Integer) this.mPendingAction.second).intValue()) {
                                case 1:
                                    removeCalls(0);
                                    changeCallsState(1, 0);
                                    clearPendingAction();
                                    break;
                                case 3:
                                    changeCallsState(1, 0);
                                    clearPendingAction();
                                    break;
                                default:
                                    break;
                            }
                        case BluetoothCmeError.SIM_FAILURE /*13*/:
                            removeCalls(1);
                            clearPendingAction();
                            break;
                        default:
                            Log.e(TAG, "Unexpected callheld=0 while in action " + this.mPendingAction.first);
                            break;
                    }
                case 1:
                    BluetoothHeadsetClientCall c;
                    switch (((Integer) this.mPendingAction.first).intValue()) {
                        case 0:
                            c = getCall(5);
                            if (c == null) {
                                for (BluetoothHeadsetClientCall cc : this.mCalls.values()) {
                                    if (cc.getState() == 0) {
                                        setCallState(cc, 1);
                                    } else if (cc.getState() == 1) {
                                        setCallState(cc, 0);
                                    }
                                }
                                break;
                            }
                            changeCallsState(0, 1);
                            setCallState(c, 0);
                            break;
                        case 12:
                            if (((Integer) this.mPendingAction.second).intValue() == 2) {
                                c = getCall(5);
                                if (c != null) {
                                    changeCallsState(0, 1);
                                    setCallState(c, 0);
                                } else {
                                    for (BluetoothHeadsetClientCall cc2 : this.mCalls.values()) {
                                        if (cc2.getState() == 0) {
                                            setCallState(cc2, 1);
                                        } else if (cc2.getState() == 1) {
                                            setCallState(cc2, 0);
                                        }
                                    }
                                }
                                clearPendingAction();
                                break;
                            }
                            break;
                        case BluetoothCmeError.WRONG_PASSWORD /*16*/:
                            for (BluetoothHeadsetClientCall cc22 : this.mCalls.values()) {
                                if (cc22 != ((BluetoothHeadsetClientCall) this.mPendingAction.second)) {
                                    setCallState(cc22, 1);
                                }
                            }
                            clearPendingAction();
                            break;
                        default:
                            Log.e(TAG, "Unexpected callheld=0 while in action " + this.mPendingAction.first);
                            break;
                    }
                case 2:
                    switch (((Integer) this.mPendingAction.first).intValue()) {
                        case 0:
                        case VCardConstants.MAX_DATA_COLUMN /*15*/:
                            removeCalls(0);
                            break;
                        case AbstractionLayer.BT_STATUS_AUTH_FAILURE /*9*/:
                        case 10:
                        case 11:
                            changeCallsState(0, 1);
                            break;
                        case BluetoothCmeError.SIM_FAILURE /*13*/:
                            switch (((Integer) this.mPendingAction.second).intValue()) {
                                case 1:
                                    removeCalls(0);
                                    changeCallsState(1, 0);
                                    clearPendingAction();
                                    break;
                                case 3:
                                    changeCallsState(1, 0);
                                    clearPendingAction();
                                    break;
                                default:
                                    break;
                            }
                        default:
                            Log.e(TAG, "Unexpected callheld=0 while in action " + this.mPendingAction.first);
                            break;
                    }
            }
            updateCallsMultiParty();
            this.mIndicatorCallHeld = callheld;
        }
    }

    private void updateRespAndHold(int resp_and_hold) {
        Log.d(TAG, "updatRespAndHold " + resp_and_hold);
        if (this.mQueryCallsSupported) {
            sendMessage(QUERY_CURRENT_CALLS);
            return;
        }
        BluetoothHeadsetClientCall c;
        switch (resp_and_hold) {
            case 0:
                c = getCall(4, 0);
                if (c != null) {
                    setCallState(c, 6);
                    return;
                } else {
                    addCall(6, "");
                    return;
                }
            case 1:
                c = getCall(6);
                if (c != null) {
                    setCallState(c, 0);
                }
                if (((Integer) this.mPendingAction.first).intValue() == 12 && ((Integer) this.mPendingAction.second).intValue() == 10) {
                    clearPendingAction();
                    return;
                }
                return;
            case 2:
                removeCalls(6);
                return;
            default:
                return;
        }
    }

    private void updateClip(String number) {
        Log.d(TAG, "updateClip number: " + number);
        BluetoothHeadsetClientCall c = getCall(4);
        if (c == null) {
            BluetoothHeadsetClientCall cw = getCall(5);
            if (cw != null) {
                setCallState(cw, 4);
                return;
            } else {
                addCall(4, number);
                return;
            }
        }
        c.setNumber(number);
        sendCallChangedIntent(c);
    }

    private void addCallWaiting(String number) {
        Log.d(TAG, "addCallWaiting number: " + number);
        if (getCall(5) == null) {
            addCall(5, number);
        }
    }

    private boolean queryCallsStart() {
        Log.d(TAG, "queryCallsStart");
        if (!this.mQueryCallsSupported) {
            return DBG;
        }
        clearPendingAction();
        if (this.mCallsUpdate != null) {
            return true;
        }
        if (queryCurrentCallsNative()) {
            this.mCallsUpdate = new Hashtable();
            addQueuedAction((int) QUERY_CURRENT_CALLS, 0);
            return true;
        }
        Log.i(TAG, "updateCallsStart queryCurrentCallsNative failed");
        this.mQueryCallsSupported = DBG;
        this.mCallsUpdate = null;
        return DBG;
    }

    private void queryCallsDone() {
        Log.d(TAG, "queryCallsDone");
        for (Entry<Integer, BluetoothHeadsetClientCall> entry : this.mCalls.entrySet()) {
            if (!this.mCallsUpdate.containsKey(entry.getKey())) {
                Log.d(TAG, "updateCallsDone call removed id:" + ((BluetoothHeadsetClientCall) entry.getValue()).getId());
                setCallState((BluetoothHeadsetClientCall) entry.getValue(), 7);
            }
        }
        for (Entry<Integer, BluetoothHeadsetClientCall> entry2 : this.mCallsUpdate.entrySet()) {
            if (this.mCalls.containsKey(entry2.getKey())) {
                if (((BluetoothHeadsetClientCall) entry2.getValue()).getNumber().equals("")) {
                    ((BluetoothHeadsetClientCall) entry2.getValue()).setNumber(((BluetoothHeadsetClientCall) this.mCalls.get(entry2.getKey())).getNumber());
                }
                if (!((BluetoothHeadsetClientCall) this.mCalls.get(entry2.getKey())).equals(entry2.getValue())) {
                    Log.d(TAG, "updateCallsDone call changed id:" + ((BluetoothHeadsetClientCall) entry2.getValue()).getId());
                    sendCallChangedIntent((BluetoothHeadsetClientCall) entry2.getValue());
                }
            } else {
                Log.d(TAG, "updateCallsDone new call id:" + ((BluetoothHeadsetClientCall) entry2.getValue()).getId());
                sendCallChangedIntent((BluetoothHeadsetClientCall) entry2.getValue());
            }
        }
        this.mCalls = this.mCallsUpdate;
        this.mCallsUpdate = null;
        if (loopQueryCalls()) {
            Log.d(TAG, "queryCallsDone ambigious calls, starting call query loop");
            sendMessageDelayed(QUERY_CURRENT_CALLS, 1523);
        }
    }

    private void queryCallsUpdate(int id, int state, String number, boolean multiParty, boolean outgoing) {
        Log.d(TAG, "queryCallsUpdate: " + id);
        if (this.mCallsUpdate != null) {
            this.mCallsUpdate.put(Integer.valueOf(id), new BluetoothHeadsetClientCall(id, state, number, multiParty, outgoing));
        }
    }

    private boolean loopQueryCalls() {
        if (callsInState(0) > 1) {
            return true;
        }
        return (getCall(4) == null || this.mIndicatorCallSetup != 0) ? DBG : true;
    }

    private void acceptCall(int flag, boolean retry) {
        int action;
        Log.d(TAG, "acceptCall: (" + flag + ")");
        BluetoothHeadsetClientCall c = getCall(4, 5);
        if (c == null) {
            c = getCall(6, 1);
            if (c == null) {
                return;
            }
        }
        switch (c.getState()) {
            case 1:
                if (flag != 1) {
                    if (flag != 2) {
                        if (getCall(0) == null) {
                            action = 2;
                            break;
                        } else {
                            action = 3;
                            break;
                        }
                    }
                    action = 1;
                    break;
                }
                action = 2;
                break;
            case 4:
                if (flag == 0) {
                    action = 7;
                    if (this.mCalls.size() == 1 && retry) {
                        action = 1;
                        break;
                    }
                }
                return;
            case 5:
                if (callsInState(0) == 0) {
                    if (flag == 0) {
                        if (!retry) {
                            action = 2;
                            break;
                        } else {
                            action = 7;
                            break;
                        }
                    }
                    return;
                } else if (flag == 1) {
                    action = 2;
                    break;
                } else if (flag == 2) {
                    action = 1;
                    break;
                } else {
                    return;
                }
            case 6:
                if (flag == 0) {
                    action = 10;
                    break;
                }
                return;
            default:
                return;
        }
        if (handleCallActionNative(action, 0)) {
            addQueuedAction(12, action);
        } else {
            Log.e(TAG, "ERROR: Couldn't accept a call, action:" + action);
        }
    }

    private void rejectCall() {
        Log.d(TAG, "rejectCall");
        if (this.mRingtone != null && this.mRingtone.isPlaying()) {
            Log.d(TAG, "stopping ring after call reject");
            this.mRingtone.stop();
        }
        BluetoothHeadsetClientCall c = getCall(4, 5, 6, 1);
        if (c != null) {
            int action;
            switch (c.getState()) {
                case 1:
                case 5:
                    action = 0;
                    break;
                case 4:
                    action = 8;
                    break;
                case 6:
                    action = 11;
                    break;
                default:
                    return;
            }
            if (handleCallActionNative(action, 0)) {
                addQueuedAction(13, action);
            } else {
                Log.e(TAG, "ERROR: Couldn't reject a call, action:" + action);
            }
        }
    }

    private void holdCall() {
        int action;
        Log.d(TAG, "holdCall");
        if (getCall(4) != null) {
            action = 9;
        } else {
            if (getCall(0) != null) {
                action = 2;
            } else {
                return;
            }
        }
        if (handleCallActionNative(action, 0)) {
            addQueuedAction(14, action);
        } else {
            Log.e(TAG, "ERROR: Couldn't hold a call, action:" + action);
        }
    }

    private void terminateCall(int idx) {
        Log.d(TAG, "terminateCall: " + idx);
        if (idx == 0) {
            if (getCall(2, 3) != null) {
                if (handleCallActionNative(8, 0)) {
                    addQueuedAction(15, 8);
                } else {
                    Log.e(TAG, "ERROR: Couldn't terminate outgoing call");
                }
            }
            if (callsInState(0) <= 0) {
                return;
            }
            if (handleCallActionNative(8, 0)) {
                addQueuedAction(15, 8);
                return;
            } else {
                Log.e(TAG, "ERROR: Couldn't terminate active calls");
                return;
            }
        }
        Object c = (BluetoothHeadsetClientCall) this.mCalls.get(Integer.valueOf(idx));
        if (c != null) {
            int action;
            switch (c.getState()) {
                case 0:
                    action = 5;
                    break;
                case 2:
                case 3:
                    action = 8;
                    break;
                default:
                    return;
            }
            if (!handleCallActionNative(action, idx)) {
                Log.e(TAG, "ERROR: Couldn't terminate a call, action:" + action + " id:" + idx);
            } else if (action == 5) {
                addQueuedAction((int) TERMINATE_SPECIFIC_CALL, c);
            } else {
                addQueuedAction(15, action);
            }
        }
    }

    private void enterPrivateMode(int idx) {
        Log.d(TAG, "enterPrivateMode: " + idx);
        Object c = (BluetoothHeadsetClientCall) this.mCalls.get(Integer.valueOf(idx));
        if (c == null || c.getState() != 0 || !c.isMultiParty()) {
            return;
        }
        if (handleCallActionNative(6, idx)) {
            addQueuedAction(16, c);
        } else {
            Log.e(TAG, "ERROR: Couldn't enter private  id:" + idx);
        }
    }

    private void explicitCallTransfer() {
        Log.d(TAG, "explicitCallTransfer");
        if (this.mCalls.size() >= 2) {
            if (handleCallActionNative(4, -1)) {
                addQueuedAction(18);
            } else {
                Log.e(TAG, "ERROR: Couldn't transfer call");
            }
        }
    }

    public Bundle getCurrentAgFeatures() {
        Bundle b = new Bundle();
        if ((this.mPeerFeatures & 1) == 1) {
            b.putBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_3WAY_CALLING", true);
        }
        if ((this.mPeerFeatures & 4) == 4) {
            b.putBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_VOICE_RECOGNITION", true);
        }
        if ((this.mPeerFeatures & 16) == 16) {
            b.putBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_ATTACH_NUMBER_TO_VT", true);
        }
        if ((this.mPeerFeatures & 32) == 32) {
            b.putBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_REJECT_CALL", true);
        }
        if ((this.mPeerFeatures & 128) == 128) {
            b.putBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_ECC", true);
        }
        if ((this.mChldFeatures & 8) == 8) {
            b.putBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_ACCEPT_HELD_OR_WAITING_CALL", true);
        }
        if ((this.mChldFeatures & 1) == 1) {
            b.putBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_RELEASE_HELD_OR_WAITING_CALL", true);
        }
        if ((this.mChldFeatures & 2) == 2) {
            b.putBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_RELEASE_AND_ACCEPT", true);
        }
        if ((this.mChldFeatures & 32) == 32) {
            b.putBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_MERGE", true);
        }
        if ((this.mChldFeatures & 64) == 64) {
            b.putBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_MERGE_AND_DETACH", true);
        }
        return b;
    }

    private HeadsetClientStateMachine(HeadsetClientService context) {
        super(TAG);
        this.mService = context;
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mAudioManager = (AudioManager) context.getSystemService("audio");
        this.mAudioState = 0;
        this.mAudioWbs = DBG;
        if (this.alert == null) {
            this.alert = RingtoneManager.getDefaultUri(2);
            if (this.alert == null) {
                this.alert = RingtoneManager.getDefaultUri(1);
            }
        }
        if (this.alert != null) {
            this.mRingtone = RingtoneManager.getRingtone(this.mService, this.alert);
        } else {
            Log.e(TAG, "alert is NULL no ringtone");
        }
        this.mIndicatorNetworkState = 0;
        this.mIndicatorNetworkType = 0;
        this.mIndicatorNetworkSignal = 0;
        this.mIndicatorBatteryLevel = 0;
        this.mIndicatorCall = -1;
        this.mIndicatorCallSetup = -1;
        this.mIndicatorCallHeld = -1;
        this.mOperatorName = null;
        this.mSubscriberInfo = null;
        this.mVoiceRecognitionActive = 0;
        this.mInBandRingtone = 0;
        this.mQueuedActions = new LinkedList();
        clearPendingAction();
        this.mCalls = new Hashtable();
        this.mCallsUpdate = null;
        this.mQueryCallsSupported = true;
        initializeNative();
        this.mNativeAvailable = true;
        this.mDisconnected = new Disconnected();
        this.mConnecting = new Connecting();
        this.mConnected = new Connected();
        this.mAudioOn = new AudioOn();
        addState(this.mDisconnected);
        addState(this.mConnecting);
        addState(this.mConnected);
        addState(this.mAudioOn, this.mConnected);
        setInitialState(this.mDisconnected);
    }

    static HeadsetClientStateMachine make(HeadsetClientService context) {
        Log.d(TAG, "make");
        HeadsetClientStateMachine hfcsm = new HeadsetClientStateMachine(context);
        hfcsm.start();
        return hfcsm;
    }

    public void doQuit() {
        quitNow();
    }

    public void cleanup() {
        if (this.mNativeAvailable) {
            cleanupNative();
            this.mNativeAvailable = DBG;
        }
    }

    public synchronized int getConnectionState(BluetoothDevice device) {
        int i = 0;
        synchronized (this) {
            if (this.mCurrentDevice != null) {
                if (this.mCurrentDevice.equals(device)) {
                    IState currentState = getCurrentState();
                    if (currentState == this.mConnecting) {
                        i = 1;
                    } else if (currentState == this.mConnected || currentState == this.mAudioOn) {
                        i = 2;
                    } else {
                        Log.e(TAG, "Bad currentState: " + currentState);
                    }
                }
            }
        }
        return i;
    }

    private void broadcastAudioState(BluetoothDevice device, int newState, int prevState) {
        Intent intent = new Intent("android.bluetooth.headsetclient.profile.action.AUDIO_STATE_CHANGED");
        intent.putExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", prevState);
        intent.putExtra("android.bluetooth.profile.extra.STATE", newState);
        if (newState == 2) {
            intent.putExtra("android.bluetooth.headsetclient.extra.AUDIO_WBS", this.mAudioWbs);
        }
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
        Log.d(TAG, "Audio state " + device + ": " + prevState + "->" + newState);
    }

    private void broadcastConnectionState(BluetoothDevice device, int newState, int prevState) {
        Log.d(TAG, "Connection state " + device + ": " + prevState + "->" + newState);
        this.mService.notifyProfileConnectionStateChanged(device, 16, newState, prevState);
        Intent intent = new Intent("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED");
        intent.putExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", prevState);
        intent.putExtra("android.bluetooth.profile.extra.STATE", newState);
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        if (newState == 2) {
            if ((this.mPeerFeatures & 1) == 1) {
                intent.putExtra("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_3WAY_CALLING", true);
            }
            if ((this.mPeerFeatures & 4) == 4) {
                intent.putExtra("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_VOICE_RECOGNITION", true);
            }
            if ((this.mPeerFeatures & 16) == 16) {
                intent.putExtra("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_ATTACH_NUMBER_TO_VT", true);
            }
            if ((this.mPeerFeatures & 32) == 32) {
                intent.putExtra("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_REJECT_CALL", true);
            }
            if ((this.mPeerFeatures & 128) == 128) {
                intent.putExtra("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_ECC", true);
            }
            if ((this.mChldFeatures & 8) == 8) {
                intent.putExtra("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_ACCEPT_HELD_OR_WAITING_CALL", true);
            }
            if ((this.mChldFeatures & 1) == 1) {
                intent.putExtra("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_RELEASE_HELD_OR_WAITING_CALL", true);
            }
            if ((this.mChldFeatures & 2) == 2) {
                intent.putExtra("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_RELEASE_AND_ACCEPT", true);
            }
            if ((this.mChldFeatures & 32) == 32) {
                intent.putExtra("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_MERGE", true);
            }
            if ((this.mChldFeatures & 64) == 64) {
                intent.putExtra("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_MERGE_AND_DETACH", true);
            }
        }
        this.mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    boolean isConnected() {
        IState currentState = getCurrentState();
        return (currentState == this.mConnected || currentState == this.mAudioOn) ? true : DBG;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList = new ArrayList();
        synchronized (this) {
            for (BluetoothDevice device : bondedDevices) {
                if (BluetoothUuid.isUuidPresent(device.getUuids(), BluetoothUuid.Handsfree_AG)) {
                    int connectionState = getConnectionState(device);
                    for (int state : states) {
                        if (connectionState == state) {
                            deviceList.add(device);
                        }
                    }
                }
            }
        }
        return deviceList;
    }

    boolean okToConnect(BluetoothDevice device) {
        int priority = this.mService.getPriority(device);
        if (priority > 0 || (-1 == priority && device.getBondState() != 10)) {
            return true;
        }
        return DBG;
    }

    boolean isAudioOn() {
        return getCurrentState() == this.mAudioOn ? true : DBG;
    }

    synchronized int getAudioState(BluetoothDevice device) {
        int i;
        if (this.mCurrentDevice == null || !this.mCurrentDevice.equals(device)) {
            i = 0;
        } else {
            i = this.mAudioState;
        }
        return i;
    }

    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList();
        synchronized (this) {
            if (isConnected()) {
                devices.add(this.mCurrentDevice);
            }
        }
        return devices;
    }

    private BluetoothDevice getDevice(byte[] address) {
        return this.mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
    }

    private void onConnectionStateChanged(int state, int peer_feat, int chld_feat, byte[] address) {
        StackEvent event = new StackEvent(1);
        event.valueInt = state;
        event.valueInt2 = peer_feat;
        event.valueInt3 = chld_feat;
        event.device = getDevice(address);
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onAudioStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(2);
        event.valueInt = state;
        event.device = getDevice(address);
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onVrStateChanged(int state) {
        StackEvent event = new StackEvent(3);
        event.valueInt = state;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onNetworkState(int state) {
        StackEvent event = new StackEvent(4);
        event.valueInt = state;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onNetworkRoaming(int state) {
        StackEvent event = new StackEvent(5);
        event.valueInt = state;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onNetworkSignal(int signal) {
        StackEvent event = new StackEvent(6);
        event.valueInt = signal;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onBatteryLevel(int level) {
        StackEvent event = new StackEvent(7);
        event.valueInt = level;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onCurrentOperator(String name) {
        StackEvent event = new StackEvent(8);
        event.valueString = name;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onCall(int call) {
        StackEvent event = new StackEvent(9);
        event.valueInt = call;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onCallSetup(int callsetup) {
        StackEvent event = new StackEvent(10);
        event.valueInt = callsetup;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onCallHeld(int callheld) {
        StackEvent event = new StackEvent(11);
        event.valueInt = callheld;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onRespAndHold(int resp_and_hold) {
        StackEvent event = new StackEvent(18);
        event.valueInt = resp_and_hold;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onClip(String number) {
        StackEvent event = new StackEvent(12);
        event.valueString = number;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onCallWaiting(String number) {
        StackEvent event = new StackEvent(13);
        event.valueString = number;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onCurrentCalls(int index, int dir, int state, int mparty, String number) {
        StackEvent event = new StackEvent(14);
        event.valueInt = index;
        event.valueInt2 = dir;
        event.valueInt3 = state;
        event.valueInt4 = mparty;
        event.valueString = number;
        Log.d(TAG, "incoming " + event);
        sendMessage(100, event);
    }

    private void onVolumeChange(int type, int volume) {
        StackEvent event = new StackEvent(15);
        event.valueInt = type;
        event.valueInt2 = volume;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onCmdResult(int type, int cme) {
        StackEvent event = new StackEvent(16);
        event.valueInt = type;
        event.valueInt2 = cme;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onSubscriberInfo(String number, int type) {
        StackEvent event = new StackEvent(17);
        event.valueInt = type;
        event.valueString = number;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onInBandRing(int in_band) {
        StackEvent event = new StackEvent(19);
        event.valueInt = in_band;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onLastVoiceTagNumber(String number) {
        StackEvent event = new StackEvent(20);
        event.valueString = number;
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private void onRingIndication() {
        StackEvent event = new StackEvent(21);
        Log.d(TAG, "incoming" + event);
        sendMessage(100, event);
    }

    private String getCurrentDeviceName() {
        String defaultName = "<unknown>";
        if (this.mCurrentDevice == null) {
            return defaultName;
        }
        String deviceName = this.mCurrentDevice.getName();
        if (deviceName != null) {
            return deviceName;
        }
        return defaultName;
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    public List<BluetoothHeadsetClientCall> getCurrentCalls() {
        return new ArrayList(this.mCalls.values());
    }

    public Bundle getCurrentAgEvents() {
        Bundle b = new Bundle();
        b.putInt("android.bluetooth.headsetclient.extra.NETWORK_STATUS", this.mIndicatorNetworkState);
        b.putInt("android.bluetooth.headsetclient.extra.NETWORK_SIGNAL_STRENGTH", this.mIndicatorNetworkSignal);
        b.putInt("android.bluetooth.headsetclient.extra.NETWORK_ROAMING", this.mIndicatorNetworkType);
        b.putInt("android.bluetooth.headsetclient.extra.BATTERY_LEVEL", this.mIndicatorBatteryLevel);
        b.putString("android.bluetooth.headsetclient.extra.OPERATOR_NAME", this.mOperatorName);
        b.putInt("android.bluetooth.headsetclient.extra.VOICE_RECOGNITION", this.mVoiceRecognitionActive);
        b.putInt("android.bluetooth.headsetclient.extra.IN_BAND_RING", this.mInBandRingtone);
        b.putString("android.bluetooth.headsetclient.extra.SUBSCRIBER_INFO", this.mSubscriberInfo);
        return b;
    }
}
