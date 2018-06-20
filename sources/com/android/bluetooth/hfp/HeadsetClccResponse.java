package com.android.bluetooth.hfp;

/* compiled from: HeadsetPhoneState */
class HeadsetClccResponse {
    int mDirection;
    int mIndex;
    int mMode;
    boolean mMpty;
    String mNumber;
    int mStatus;
    int mType;

    public HeadsetClccResponse(int index, int direction, int status, int mode, boolean mpty, String number, int type) {
        this.mIndex = index;
        this.mDirection = direction;
        this.mStatus = status;
        this.mMode = mode;
        this.mMpty = mpty;
        this.mNumber = number;
        this.mType = type;
    }
}
