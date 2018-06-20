package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClientCall;
import android.bluetooth.IBluetoothHeadsetClient.Stub;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Message;
import android.provider.Settings.Global;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import java.util.ArrayList;
import java.util.List;

public class HeadsetClientService extends ProfileService {
    private static final boolean DBG = false;
    private static final String TAG = "HeadsetClientService";
    private static HeadsetClientService sHeadsetClientService;
    private final BroadcastReceiver mBroadcastReceiver = new C00181();
    private HeadsetClientStateMachine mStateMachine;

    /* renamed from: com.android.bluetooth.hfpclient.HeadsetClientService$1 */
    class C00181 extends BroadcastReceiver {
        C00181() {
        }

        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION") && intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) == 6) {
                int streamValue = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                int streamPrevValue = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1);
                if (streamValue != -1 && streamValue != streamPrevValue) {
                    HeadsetClientService.this.mStateMachine.sendMessage(HeadsetClientService.this.mStateMachine.obtainMessage(8, streamValue, 0));
                }
            }
        }
    }

    private static class BluetoothHeadsetClientBinder extends Stub implements IProfileServiceBinder {
        private HeadsetClientService mService;

        public BluetoothHeadsetClientBinder(HeadsetClientService svc) {
            this.mService = svc;
        }

        public boolean cleanup() {
            this.mService = null;
            return true;
        }

        private HeadsetClientService getService() {
            if (!Utils.checkCaller()) {
                Log.w(HeadsetClientService.TAG, "HeadsetClient call not allowed for non-active user");
                return null;
            } else if (this.mService == null || !this.mService.isAvailable()) {
                return null;
            } else {
                return this.mService;
            }
        }

        public boolean connect(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.connect(device);
        }

        public boolean disconnect(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.disconnect(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            HeadsetClientService service = getService();
            if (service == null) {
                return new ArrayList(0);
            }
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            HeadsetClientService service = getService();
            if (service == null) {
                return new ArrayList(0);
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return 0;
            }
            return service.getConnectionState(device);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return -1;
            }
            return service.getPriority(device);
        }

        public boolean startVoiceRecognition(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.startVoiceRecognition(device);
        }

        public boolean stopVoiceRecognition(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.stopVoiceRecognition(device);
        }

        public boolean acceptIncomingConnect(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.acceptIncomingConnect(device);
        }

        public boolean rejectIncomingConnect(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.rejectIncomingConnect(device);
        }

        public int getAudioState(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return 0;
            }
            return service.getAudioState(device);
        }

        public boolean connectAudio() {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.connectAudio();
        }

        public boolean disconnectAudio() {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.disconnectAudio();
        }

        public boolean acceptCall(BluetoothDevice device, int flag) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.acceptCall(device, flag);
        }

        public boolean rejectCall(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.rejectCall(device);
        }

        public boolean holdCall(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.holdCall(device);
        }

        public boolean terminateCall(BluetoothDevice device, int index) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.terminateCall(device, index);
        }

        public boolean explicitCallTransfer(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.explicitCallTransfer(device);
        }

        public boolean enterPrivateMode(BluetoothDevice device, int index) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.enterPrivateMode(device, index);
        }

        public boolean redial(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.redial(device);
        }

        public boolean dial(BluetoothDevice device, String number) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.dial(device, number);
        }

        public boolean dialMemory(BluetoothDevice device, int location) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.dialMemory(device, location);
        }

        public List<BluetoothHeadsetClientCall> getCurrentCalls(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return null;
            }
            return service.getCurrentCalls(device);
        }

        public boolean sendDTMF(BluetoothDevice device, byte code) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.sendDTMF(device, code);
        }

        public boolean getLastVoiceTagNumber(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return HeadsetClientService.DBG;
            }
            return service.getLastVoiceTagNumber(device);
        }

        public Bundle getCurrentAgEvents(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return null;
            }
            return service.getCurrentAgEvents(device);
        }

        public Bundle getCurrentAgFeatures(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return null;
            }
            return service.getCurrentAgFeatures(device);
        }
    }

    protected String getName() {
        return TAG;
    }

    public IProfileServiceBinder initBinder() {
        return new BluetoothHeadsetClientBinder(this);
    }

    protected boolean start() {
        this.mStateMachine = HeadsetClientStateMachine.make(this);
        IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        filter.addAction("android.bluetooth.device.action.CONNECTION_ACCESS_REPLY");
        try {
            registerReceiver(this.mBroadcastReceiver, filter);
        } catch (Exception e) {
            Log.w(TAG, "Unable to register broadcat receiver", e);
        }
        setHeadsetClientService(this);
        return true;
    }

    protected boolean stop() {
        try {
            unregisterReceiver(this.mBroadcastReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Unable to unregister broadcast receiver", e);
        }
        this.mStateMachine.doQuit();
        return true;
    }

    protected boolean cleanup() {
        if (this.mStateMachine != null) {
            this.mStateMachine.cleanup();
        }
        clearHeadsetClientService();
        return true;
    }

    public static synchronized HeadsetClientService getHeadsetClientService() {
        HeadsetClientService headsetClientService;
        synchronized (HeadsetClientService.class) {
            if (sHeadsetClientService == null || !sHeadsetClientService.isAvailable()) {
                headsetClientService = null;
            } else {
                headsetClientService = sHeadsetClientService;
            }
        }
        return headsetClientService;
    }

    private static synchronized void setHeadsetClientService(HeadsetClientService instance) {
        synchronized (HeadsetClientService.class) {
            if (instance != null) {
                if (instance.isAvailable()) {
                    sHeadsetClientService = instance;
                }
            }
        }
    }

    private static synchronized void clearHeadsetClientService() {
        synchronized (HeadsetClientService.class) {
            sHeadsetClientService = null;
        }
    }

    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        if (getPriority(device) == 0) {
            return DBG;
        }
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState == 2 || connectionState == 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(1, device);
        return true;
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(2, device);
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mStateMachine.getConnectedDevices();
    }

    private List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mStateMachine.getDevicesMatchingConnectionStates(states);
    }

    int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mStateMachine.getConnectionState(device);
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        Global.putInt(getContentResolver(), Global.getBluetoothHeadsetPriorityKey(device.getAddress()), priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        return Global.getInt(getContentResolver(), Global.getBluetoothHeadsetPriorityKey(device.getAddress()), -1);
    }

    boolean startVoiceRecognition(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(5);
        return true;
    }

    boolean stopVoiceRecognition(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(6);
        return true;
    }

    boolean acceptIncomingConnect(BluetoothDevice device) {
        return DBG;
    }

    boolean rejectIncomingConnect(BluetoothDevice device) {
        return DBG;
    }

    int getAudioState(BluetoothDevice device) {
        return this.mStateMachine.getAudioState(device);
    }

    boolean connectAudio() {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (!this.mStateMachine.isConnected() || this.mStateMachine.isAudioOn()) {
            return DBG;
        }
        this.mStateMachine.sendMessage(3);
        return true;
    }

    boolean disconnectAudio() {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (!this.mStateMachine.isAudioOn()) {
            return DBG;
        }
        this.mStateMachine.sendMessage(4);
        return true;
    }

    boolean holdCall(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(this.mStateMachine.obtainMessage(14));
        return true;
    }

    boolean acceptCall(BluetoothDevice device, int flag) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        Message msg = this.mStateMachine.obtainMessage(12);
        msg.arg1 = flag;
        this.mStateMachine.sendMessage(msg);
        return true;
    }

    boolean rejectCall(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(this.mStateMachine.obtainMessage(13));
        return true;
    }

    boolean terminateCall(BluetoothDevice device, int index) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        Message msg = this.mStateMachine.obtainMessage(15);
        msg.arg1 = index;
        this.mStateMachine.sendMessage(msg);
        return true;
    }

    boolean enterPrivateMode(BluetoothDevice device, int index) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        Message msg = this.mStateMachine.obtainMessage(16);
        msg.arg1 = index;
        this.mStateMachine.sendMessage(msg);
        return true;
    }

    boolean redial(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(this.mStateMachine.obtainMessage(9));
        return true;
    }

    boolean dial(BluetoothDevice device, String number) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        Message msg = this.mStateMachine.obtainMessage(10);
        msg.obj = number;
        this.mStateMachine.sendMessage(msg);
        return true;
    }

    boolean dialMemory(BluetoothDevice device, int location) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        Message msg = this.mStateMachine.obtainMessage(11);
        msg.arg1 = location;
        this.mStateMachine.sendMessage(msg);
        return true;
    }

    public boolean sendDTMF(BluetoothDevice device, byte code) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        Message msg = this.mStateMachine.obtainMessage(17);
        msg.arg1 = code;
        this.mStateMachine.sendMessage(msg);
        return true;
    }

    public boolean getLastVoiceTagNumber(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(this.mStateMachine.obtainMessage(19));
        return true;
    }

    public List<BluetoothHeadsetClientCall> getCurrentCalls(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (this.mStateMachine.getConnectionState(device) != 2) {
            return null;
        }
        return this.mStateMachine.getCurrentCalls();
    }

    public boolean explicitCallTransfer(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(this.mStateMachine.obtainMessage(18));
        return true;
    }

    public Bundle getCurrentAgEvents(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (this.mStateMachine.getConnectionState(device) != 2) {
            return null;
        }
        return this.mStateMachine.getCurrentAgEvents();
    }

    public Bundle getCurrentAgFeatures(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (this.mStateMachine.getConnectionState(device) != 2) {
            return null;
        }
        return this.mStateMachine.getCurrentAgFeatures();
    }

    public void dump(StringBuilder sb) {
        super.dump(sb);
        if (this.mStateMachine != null) {
            this.mStateMachine.dump(sb);
        }
    }
}
