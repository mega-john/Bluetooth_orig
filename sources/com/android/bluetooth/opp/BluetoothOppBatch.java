package com.android.bluetooth.opp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import com.google.android.collect.Lists;
import java.io.File;
import java.util.ArrayList;

public class BluetoothOppBatch {
    private static final String TAG = "BtOppBatch";
    /* renamed from: V */
    private static final boolean f30V = false;
    private final Context mContext;
    public final BluetoothDevice mDestination;
    public final int mDirection;
    public int mId;
    private BluetoothOppBatchListener mListener;
    private final ArrayList<BluetoothOppShareInfo> mShares = Lists.newArrayList();
    public int mStatus;
    public final long mTimestamp;

    public interface BluetoothOppBatchListener {
        void onBatchCanceled();

        void onShareAdded(int i);

        void onShareDeleted(int i);
    }

    public BluetoothOppBatch(Context context, BluetoothOppShareInfo info) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        this.mContext = context;
        this.mTimestamp = info.mTimestamp;
        this.mDirection = info.mDirection;
        this.mDestination = adapter.getRemoteDevice(info.mDestination);
        this.mStatus = 0;
        this.mShares.add(info);
    }

    public void addShare(BluetoothOppShareInfo info) {
        this.mShares.add(info);
        if (this.mListener != null) {
            this.mListener.onShareAdded(info.mId);
        }
    }

    public void deleteShare(BluetoothOppShareInfo info) {
        if (info.mStatus == BluetoothShare.STATUS_RUNNING) {
            info.mStatus = BluetoothShare.STATUS_CANCELED;
            if (info.mDirection == 1 && info.mFilename != null) {
                new File(info.mFilename).delete();
            }
        }
        if (this.mListener != null) {
            this.mListener.onShareDeleted(info.mId);
        }
    }

    public void cancelBatch() {
        if (this.mListener != null) {
            this.mListener.onBatchCanceled();
        }
        for (int i = this.mShares.size() - 1; i >= 0; i--) {
            BluetoothOppShareInfo info = (BluetoothOppShareInfo) this.mShares.get(i);
            if (info.mStatus < BluetoothShare.STATUS_SUCCESS) {
                if (info.mDirection == 1 && info.mFilename != null) {
                    new File(info.mFilename).delete();
                }
                Constants.updateShareStatus(this.mContext, info.mId, BluetoothShare.STATUS_CANCELED);
            }
        }
        this.mShares.clear();
    }

    public boolean hasShare(BluetoothOppShareInfo info) {
        return this.mShares.contains(info);
    }

    public boolean isEmpty() {
        return this.mShares.size() == 0;
    }

    public int getNumShares() {
        return this.mShares.size();
    }

    public void registerListern(BluetoothOppBatchListener listener) {
        this.mListener = listener;
    }

    public BluetoothOppShareInfo getPendingShare() {
        for (int i = 0; i < this.mShares.size(); i++) {
            BluetoothOppShareInfo share = (BluetoothOppShareInfo) this.mShares.get(i);
            if (share.mStatus == BluetoothShare.STATUS_PENDING) {
                return share;
            }
        }
        return null;
    }
}
