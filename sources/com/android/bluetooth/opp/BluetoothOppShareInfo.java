package com.android.bluetooth.opp;

import android.net.Uri;

public class BluetoothOppShareInfo {
    public int mConfirm;
    public int mCurrentBytes;
    public String mDestination;
    public int mDirection;
    public String mFilename;
    public String mHint;
    public int mId;
    public boolean mMediaScanned;
    public String mMimetype;
    public int mStatus;
    public long mTimestamp;
    public int mTotalBytes;
    public Uri mUri;
    public int mVisibility;

    public BluetoothOppShareInfo(int id, Uri uri, String hint, String filename, String mimetype, int direction, String destination, int visibility, int confirm, int status, int totalBytes, int currentBytes, int timestamp, boolean mediaScanned) {
        this.mId = id;
        this.mUri = uri;
        this.mHint = hint;
        this.mFilename = filename;
        this.mMimetype = mimetype;
        this.mDirection = direction;
        this.mDestination = destination;
        this.mVisibility = visibility;
        this.mConfirm = confirm;
        this.mStatus = status;
        this.mTotalBytes = totalBytes;
        this.mCurrentBytes = currentBytes;
        this.mTimestamp = (long) timestamp;
        this.mMediaScanned = mediaScanned;
    }

    public boolean isReadyToStart() {
        if (this.mDirection == 0) {
            if (this.mStatus == BluetoothShare.STATUS_PENDING && this.mUri != null) {
                return true;
            }
        } else if (this.mDirection == 1 && this.mStatus == BluetoothShare.STATUS_PENDING) {
            return true;
        }
        return false;
    }

    public boolean hasCompletionNotification() {
        if (BluetoothShare.isStatusCompleted(this.mStatus) && this.mVisibility == 0) {
            return true;
        }
        return false;
    }

    public boolean isObsolete() {
        if (BluetoothShare.STATUS_RUNNING == this.mStatus) {
            return true;
        }
        return false;
    }
}
