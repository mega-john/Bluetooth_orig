package com.android.bluetooth.hfp;

/* compiled from: HeadsetPhoneState */
class HeadsetCallState {
    int mCallState;
    int mNumActive;
    int mNumHeld;
    String mNumber;
    int mType;

    public HeadsetCallState(int numActive, int numHeld, int callState, String number, int type) {
        this.mNumActive = numActive;
        this.mNumHeld = numHeld;
        this.mCallState = callState;
        this.mNumber = number;
        this.mType = type;
    }
}
