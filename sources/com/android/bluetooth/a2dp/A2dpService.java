package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetoothA2dp.Stub;
import android.provider.Settings.Global;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.avrcp.Avrcp;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import java.util.ArrayList;
import java.util.List;

public class A2dpService extends ProfileService {
    private static final boolean DBG = false;
    private static final String TAG = "A2dpService";
    private static A2dpService sAd2dpService;
    private Avrcp mAvrcp;
    private A2dpStateMachine mStateMachine;

    private static class BluetoothA2dpBinder extends Stub implements IProfileServiceBinder {
        private A2dpService mService;

        private A2dpService getService() {
            if (!Utils.checkCaller()) {
                Log.w(A2dpService.TAG, "A2dp call not allowed for non-active user");
                return null;
            } else if (this.mService == null || !this.mService.isAvailable()) {
                return null;
            } else {
                return this.mService;
            }
        }

        BluetoothA2dpBinder(A2dpService svc) {
            this.mService = svc;
        }

        public boolean cleanup() {
            this.mService = null;
            return true;
        }

        public boolean connect(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return A2dpService.DBG;
            }
            return service.connect(device);
        }

        public boolean disconnect(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return A2dpService.DBG;
            }
            return service.disconnect(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            A2dpService service = getService();
            if (service == null) {
                return new ArrayList(0);
            }
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            A2dpService service = getService();
            if (service == null) {
                return new ArrayList(0);
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return 0;
            }
            return service.getConnectionState(device);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            A2dpService service = getService();
            if (service == null) {
                return A2dpService.DBG;
            }
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return -1;
            }
            return service.getPriority(device);
        }

        public boolean isAvrcpAbsoluteVolumeSupported() {
            A2dpService service = getService();
            if (service == null) {
                return A2dpService.DBG;
            }
            return service.isAvrcpAbsoluteVolumeSupported();
        }

        public void adjustAvrcpAbsoluteVolume(int direction) {
            A2dpService service = getService();
            if (service != null) {
                service.adjustAvrcpAbsoluteVolume(direction);
            }
        }

        public void setAvrcpAbsoluteVolume(int volume) {
            A2dpService service = getService();
            if (service != null) {
                service.setAvrcpAbsoluteVolume(volume);
            }
        }

        public boolean isA2dpPlaying(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return A2dpService.DBG;
            }
            return service.isA2dpPlaying(device);
        }
    }

    protected String getName() {
        return TAG;
    }

    protected IProfileServiceBinder initBinder() {
        return new BluetoothA2dpBinder(this);
    }

    protected boolean start() {
        this.mAvrcp = Avrcp.make(this);
        this.mStateMachine = A2dpStateMachine.make(this, this);
        setA2dpService(this);
        return true;
    }

    protected boolean stop() {
        if (this.mStateMachine != null) {
            this.mStateMachine.doQuit();
        }
        if (this.mAvrcp != null) {
            this.mAvrcp.doQuit();
        }
        return true;
    }

    protected boolean cleanup() {
        if (this.mStateMachine != null) {
            this.mStateMachine.cleanup();
        }
        if (this.mAvrcp != null) {
            this.mAvrcp.cleanup();
            this.mAvrcp = null;
        }
        clearA2dpService();
        return true;
    }

    public static synchronized A2dpService getA2dpService() {
        A2dpService a2dpService;
        synchronized (A2dpService.class) {
            if (sAd2dpService == null || !sAd2dpService.isAvailable()) {
                a2dpService = null;
            } else {
                a2dpService = sAd2dpService;
            }
        }
        return a2dpService;
    }

    private static synchronized void setA2dpService(A2dpService instance) {
        synchronized (A2dpService.class) {
            if (instance != null) {
                if (instance.isAvailable()) {
                    sAd2dpService = instance;
                }
            }
        }
    }

    private static synchronized void clearA2dpService() {
        synchronized (A2dpService.class) {
            sAd2dpService = null;
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

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mStateMachine.getDevicesMatchingConnectionStates(states);
    }

    int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mStateMachine.getConnectionState(device);
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        Global.putInt(getContentResolver(), Global.getBluetoothA2dpSinkPriorityKey(device.getAddress()), priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        return Global.getInt(getContentResolver(), Global.getBluetoothA2dpSinkPriorityKey(device.getAddress()), -1);
    }

    public boolean isAvrcpAbsoluteVolumeSupported() {
        return this.mAvrcp.isAbsoluteVolumeSupported();
    }

    public void adjustAvrcpAbsoluteVolume(int direction) {
        this.mAvrcp.adjustVolume(direction);
    }

    public void setAvrcpAbsoluteVolume(int volume) {
        this.mAvrcp.setAbsoluteVolume(volume);
    }

    public void setAvrcpAudioState(int state) {
        this.mAvrcp.setA2dpAudioState(state);
    }

    synchronized boolean isA2dpPlaying(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mStateMachine.isPlaying(device);
    }

    public void dump(StringBuilder sb) {
        super.dump(sb);
        if (this.mStateMachine != null) {
            this.mStateMachine.dump(sb);
        }
        if (this.mAvrcp != null) {
            this.mAvrcp.dump(sb);
        }
    }
}
