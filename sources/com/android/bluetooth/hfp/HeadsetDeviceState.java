package com.android.bluetooth.hfp;

/* compiled from: HeadsetPhoneState */
class HeadsetDeviceState {
    int mBatteryCharge;
    int mRoam;
    int mService;
    int mSignal;

    HeadsetDeviceState(int service, int roam, int signal, int batteryCharge) {
        this.mService = service;
        this.mRoam = roam;
        this.mSignal = signal;
        this.mBatteryCharge = batteryCharge;
    }
}
