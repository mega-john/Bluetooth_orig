package com.android.bluetooth.opp;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import com.android.bluetooth.hfp.BluetoothCmeError;
import com.google.android.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import javax.obex.ObexTransport;

public class BluetoothOppService extends Service {
    /* renamed from: D */
    private static final boolean f55D = true;
    private static final int MEDIA_SCANNED = 2;
    private static final int MEDIA_SCANNED_FAILED = 3;
    private static final int MSG_INCOMING_CONNECTION_RETRY = 4;
    private static final int START_LISTENER = 1;
    private static final int STOP_LISTENER = 200;
    private static final String TAG = "BtOppService";
    /* renamed from: V */
    private static final boolean f56V = false;
    private BluetoothAdapter mAdapter;
    private int mBatchId;
    private ArrayList<BluetoothOppBatch> mBatchs;
    private final BroadcastReceiver mBluetoothReceiver = new C00463();
    private Handler mHandler = new C00452();
    private int mIncomingRetries = 0;
    private boolean mListenStarted = false;
    private boolean mMediaScanInProgress;
    private CharArrayBuffer mNewChars;
    private BluetoothOppNotification mNotifier;
    private BluetoothShareContentObserver mObserver;
    private CharArrayBuffer mOldChars;
    private ObexTransport mPendingConnection = null;
    private boolean mPendingUpdate;
    private PowerManager mPowerManager;
    private BluetoothOppObexServerSession mServerSession;
    private BluetoothOppTransfer mServerTransfer;
    private ArrayList<BluetoothOppShareInfo> mShares;
    private BluetoothOppRfcommListener mSocketListener;
    private BluetoothOppTransfer mTransfer;
    private UpdateThread mUpdateThread;
    private boolean userAccepted = false;

    /* renamed from: com.android.bluetooth.opp.BluetoothOppService$2 */
    class C00452 extends Handler {
        C00452() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    if (BluetoothOppService.this.mAdapter.isEnabled()) {
                        BluetoothOppService.this.startSocketListener();
                        return;
                    }
                    return;
                case 2:
                    ContentValues updateValues = new ContentValues();
                    Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + msg.arg1);
                    updateValues.put(Constants.MEDIA_SCANNED, Integer.valueOf(1));
                    updateValues.put("uri", msg.obj.toString());
                    updateValues.put(BluetoothShare.MIMETYPE, BluetoothOppService.this.getContentResolver().getType(Uri.parse(msg.obj.toString())));
                    BluetoothOppService.this.getContentResolver().update(contentUri, updateValues, null, null);
                    synchronized (BluetoothOppService.this) {
                        BluetoothOppService.this.mMediaScanInProgress = false;
                    }
                    return;
                case 3:
                    Log.v(BluetoothOppService.TAG, "Update mInfo.id " + msg.arg1 + " for MEDIA_SCANNED_FAILED");
                    ContentValues updateValues1 = new ContentValues();
                    Uri contentUri1 = Uri.parse(BluetoothShare.CONTENT_URI + "/" + msg.arg1);
                    updateValues1.put(Constants.MEDIA_SCANNED, Integer.valueOf(2));
                    BluetoothOppService.this.getContentResolver().update(contentUri1, updateValues1, null, null);
                    synchronized (BluetoothOppService.this) {
                        BluetoothOppService.this.mMediaScanInProgress = false;
                    }
                    return;
                case 4:
                    if (BluetoothOppService.this.mBatchs.size() == 0) {
                        Log.i(BluetoothOppService.TAG, "Start Obex Server");
                        BluetoothOppService.this.createServerSession(BluetoothOppService.this.mPendingConnection);
                        BluetoothOppService.this.mIncomingRetries = 0;
                        BluetoothOppService.this.mPendingConnection = null;
                        return;
                    } else if (BluetoothOppService.this.mIncomingRetries == 20) {
                        Log.w(BluetoothOppService.TAG, "Retried 20 seconds, reject connection");
                        try {
                            BluetoothOppService.this.mPendingConnection.close();
                        } catch (IOException e) {
                            Log.e(BluetoothOppService.TAG, "close tranport error");
                        }
                        BluetoothOppService.this.mIncomingRetries = 0;
                        BluetoothOppService.this.mPendingConnection = null;
                        return;
                    } else {
                        Log.i(BluetoothOppService.TAG, "OPP busy! Retry after 1 second");
                        BluetoothOppService.this.mIncomingRetries = BluetoothOppService.this.mIncomingRetries + 1;
                        Message msg2 = Message.obtain(BluetoothOppService.this.mHandler);
                        msg2.what = 4;
                        BluetoothOppService.this.mHandler.sendMessageDelayed(msg2, 1000);
                        return;
                    }
                case 100:
                    Log.d(BluetoothOppService.TAG, "Get incoming connection");
                    ObexTransport transport = msg.obj;
                    if (BluetoothOppService.this.mBatchs.size() == 0 && BluetoothOppService.this.mPendingConnection == null) {
                        Log.i(BluetoothOppService.TAG, "Start Obex Server");
                        BluetoothOppService.this.createServerSession(transport);
                        return;
                    } else if (BluetoothOppService.this.mPendingConnection != null) {
                        Log.w(BluetoothOppService.TAG, "OPP busy! Reject connection");
                        try {
                            transport.close();
                            return;
                        } catch (IOException e2) {
                            Log.e(BluetoothOppService.TAG, "close tranport error");
                            return;
                        }
                    } else {
                        Log.i(BluetoothOppService.TAG, "OPP busy! Retry after 1 second");
                        BluetoothOppService.this.mIncomingRetries = BluetoothOppService.this.mIncomingRetries + 1;
                        BluetoothOppService.this.mPendingConnection = transport;
                        Message msg1 = Message.obtain(BluetoothOppService.this.mHandler);
                        msg1.what = 4;
                        BluetoothOppService.this.mHandler.sendMessageDelayed(msg1, 1000);
                        return;
                    }
                case 200:
                    if (BluetoothOppService.this.mSocketListener != null) {
                        BluetoothOppService.this.mSocketListener.stop();
                    }
                    BluetoothOppService.this.mListenStarted = false;
                    if (BluetoothOppService.this.mServerTransfer != null) {
                        BluetoothOppService.this.mServerTransfer.onBatchCanceled();
                        BluetoothOppService.this.mServerTransfer = null;
                    }
                    if (BluetoothOppService.this.mTransfer != null) {
                        BluetoothOppService.this.mTransfer.onBatchCanceled();
                        BluetoothOppService.this.mTransfer = null;
                    }
                    synchronized (BluetoothOppService.this) {
                        if (BluetoothOppService.this.mUpdateThread == null) {
                            BluetoothOppService.this.stopSelf();
                        }
                    }
                    return;
                default:
                    return;
            }
        }
    }

    /* renamed from: com.android.bluetooth.opp.BluetoothOppService$3 */
    class C00463 extends BroadcastReceiver {
        C00463() {
        }

        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                switch (intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE)) {
                    case 12:
                        BluetoothOppService.this.startSocketListener();
                        return;
                    case BluetoothCmeError.SIM_FAILURE /*13*/:
                        BluetoothOppService.this.mHandler.sendMessage(BluetoothOppService.this.mHandler.obtainMessage(200));
                        return;
                    default:
                        return;
                }
            }
        }
    }

    private class BluetoothShareContentObserver extends ContentObserver {
        public BluetoothShareContentObserver() {
            super(new Handler());
        }

        public void onChange(boolean selfChange) {
            BluetoothOppService.this.updateFromProvider();
        }
    }

    private static class MediaScannerNotifier implements MediaScannerConnectionClient {
        private Handler mCallback;
        private MediaScannerConnection mConnection = new MediaScannerConnection(this.mContext, this);
        private Context mContext;
        private BluetoothOppShareInfo mInfo;

        public MediaScannerNotifier(Context context, BluetoothOppShareInfo info, Handler handler) {
            this.mContext = context;
            this.mInfo = info;
            this.mCallback = handler;
            this.mConnection.connect();
        }

        public void onMediaScannerConnected() {
            this.mConnection.scanFile(this.mInfo.mFilename, this.mInfo.mMimetype);
        }

        public void onScanCompleted(String path, Uri uri) {
            Message msg;
            if (uri != null) {
                try {
                    msg = Message.obtain();
                    msg.setTarget(this.mCallback);
                    msg.what = 2;
                    msg.arg1 = this.mInfo.mId;
                    msg.obj = uri;
                    msg.sendToTarget();
                } catch (Exception ex) {
                    Log.v(BluetoothOppService.TAG, "!!!MediaScannerConnection exception: " + ex);
                    return;
                } finally {
                    this.mConnection.disconnect();
                }
            } else {
                msg = Message.obtain();
                msg.setTarget(this.mCallback);
                msg.what = 3;
                msg.arg1 = this.mInfo.mId;
                msg.sendToTarget();
            }
            this.mConnection.disconnect();
        }
    }

    private class UpdateThread extends Thread {
        public UpdateThread() {
            super("Bluetooth Share Service");
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void run() {
            Process.setThreadPriority(10);
            boolean keepService = false;
            while (true) {
                synchronized (BluetoothOppService.this) {
                    if (BluetoothOppService.this.mUpdateThread != this) {
                        throw new IllegalStateException("multiple UpdateThreads in BluetoothOppService");
                    } else if (!BluetoothOppService.this.mPendingUpdate) {
                        break;
                    } else {
                        BluetoothOppService.this.mPendingUpdate = false;
                    }
                }
                BluetoothOppService.this.mNotifier.updateNotification();
                cursor.close();
            }
            BluetoothOppService.this.mUpdateThread = null;
            if (keepService || BluetoothOppService.this.mListenStarted) {
                return;
            }
            BluetoothOppService.this.stopSelf();
        }
    }

    public IBinder onBind(Intent arg0) {
        throw new UnsupportedOperationException("Cannot bind to Bluetooth OPP Service");
    }

    public void onCreate() {
        super.onCreate();
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mSocketListener = new BluetoothOppRfcommListener(this.mAdapter);
        this.mShares = Lists.newArrayList();
        this.mBatchs = Lists.newArrayList();
        this.mObserver = new BluetoothShareContentObserver();
        getContentResolver().registerContentObserver(BluetoothShare.CONTENT_URI, true, this.mObserver);
        this.mBatchId = 1;
        this.mNotifier = new BluetoothOppNotification(this);
        this.mNotifier.mNotificationMgr.cancelAll();
        this.mNotifier.updateNotification();
        final ContentResolver contentResolver = getContentResolver();
        new Thread("trimDatabase") {
            public void run() {
                BluetoothOppService.trimDatabase(contentResolver);
            }
        }.start();
        registerReceiver(this.mBluetoothReceiver, new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED"));
        synchronized (this) {
            if (this.mAdapter == null) {
                Log.w(TAG, "Local BT device is not enabled");
            } else {
                startListener();
            }
        }
        updateFromProvider();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (this.mAdapter == null) {
            Log.w(TAG, "Local BT device is not enabled");
        } else {
            startListener();
        }
        updateFromProvider();
        return 2;
    }

    private void startListener() {
        if (!this.mListenStarted && this.mAdapter.isEnabled()) {
            this.mHandler.sendMessage(this.mHandler.obtainMessage(1));
            this.mListenStarted = true;
        }
    }

    private void startSocketListener() {
        this.mSocketListener.start(this.mHandler);
    }

    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(this.mObserver);
        unregisterReceiver(this.mBluetoothReceiver);
        this.mSocketListener.stop();
        if (this.mBatchs != null) {
            this.mBatchs.clear();
        }
        if (this.mShares != null) {
            this.mShares.clear();
        }
        if (this.mHandler != null) {
            this.mHandler.removeCallbacksAndMessages(null);
        }
    }

    private void createServerSession(ObexTransport transport) {
        this.mServerSession = new BluetoothOppObexServerSession(this, transport);
        this.mServerSession.preStart();
        Log.d(TAG, "Get ServerSession " + this.mServerSession.toString() + " for incoming connection" + transport.toString());
    }

    private void updateFromProvider() {
        synchronized (this) {
            this.mPendingUpdate = true;
            if (this.mUpdateThread == null) {
                this.mUpdateThread = new UpdateThread();
                this.mUpdateThread.start();
            }
        }
    }

    private void insertShare(Cursor cursor, int arrayPos) {
        Uri uri;
        boolean z;
        String uriString = cursor.getString(cursor.getColumnIndexOrThrow("uri"));
        if (uriString != null) {
            uri = Uri.parse(uriString);
            Log.d(TAG, "insertShare parsed URI: " + uri);
        } else {
            uri = null;
            Log.e(TAG, "insertShare found null URI at cursor!");
        }
        int i = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
        String string = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT));
        String string2 = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare._DATA));
        String string3 = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.MIMETYPE));
        int i2 = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.DIRECTION));
        String string4 = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.DESTINATION));
        int i3 = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.VISIBILITY));
        int i4 = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION));
        int i5 = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.STATUS));
        int i6 = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES));
        int i7 = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES));
        int i8 = cursor.getInt(cursor.getColumnIndexOrThrow("timestamp"));
        if (cursor.getInt(cursor.getColumnIndexOrThrow(Constants.MEDIA_SCANNED)) != 0) {
            z = true;
        } else {
            z = false;
        }
        BluetoothOppShareInfo info = new BluetoothOppShareInfo(i, uri, string, string2, string3, i2, string4, i3, i4, i5, i6, i7, i8, z);
        this.mShares.add(arrayPos, info);
        if (info.isObsolete()) {
            Constants.updateShareStatus(this, info.mId, BluetoothShare.STATUS_UNKNOWN_ERROR);
        }
        if (info.isReadyToStart()) {
            if (info.mDirection == 0) {
                BluetoothOppSendFileInfo sendFileInfo = BluetoothOppUtility.getSendFileInfo(info.mUri);
                if (sendFileInfo == null || sendFileInfo.mInputStream == null) {
                    Log.e(TAG, "Can't open file for OUTBOUND info " + info.mId);
                    Constants.updateShareStatus(this, info.mId, BluetoothShare.STATUS_BAD_REQUEST);
                    BluetoothOppUtility.closeSendFileInfo(info.mUri);
                    return;
                }
            }
            BluetoothOppBatch bluetoothOppBatch;
            if (this.mBatchs.size() == 0) {
                bluetoothOppBatch = new BluetoothOppBatch(this, info);
                bluetoothOppBatch.mId = this.mBatchId;
                this.mBatchId++;
                this.mBatchs.add(bluetoothOppBatch);
                if (info.mDirection == 0) {
                    this.mTransfer = new BluetoothOppTransfer(this, this.mPowerManager, bluetoothOppBatch);
                } else if (info.mDirection == 1) {
                    this.mServerTransfer = new BluetoothOppTransfer(this, this.mPowerManager, bluetoothOppBatch, this.mServerSession);
                }
                if (info.mDirection == 0 && this.mTransfer != null) {
                    this.mTransfer.start();
                    return;
                } else if (info.mDirection == 1 && this.mServerTransfer != null) {
                    this.mServerTransfer.start();
                    return;
                } else {
                    return;
                }
            }
            int i9 = findBatchWithTimeStamp(info.mTimestamp);
            if (i9 != -1) {
                ((BluetoothOppBatch) this.mBatchs.get(i9)).addShare(info);
                return;
            }
            bluetoothOppBatch = new BluetoothOppBatch(this, info);
            bluetoothOppBatch.mId = this.mBatchId;
            this.mBatchId++;
            this.mBatchs.add(bluetoothOppBatch);
        }
    }

    private void updateShare(Cursor cursor, int arrayPos, boolean userAccepted) {
        int i;
        BluetoothOppShareInfo info = (BluetoothOppShareInfo) this.mShares.get(arrayPos);
        int statusColumn = cursor.getColumnIndexOrThrow(BluetoothShare.STATUS);
        info.mId = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
        if (info.mUri != null) {
            info.mUri = Uri.parse(stringFromCursor(info.mUri.toString(), cursor, "uri"));
        } else {
            Log.w(TAG, "updateShare() called for ID " + info.mId + " with null URI");
        }
        info.mHint = stringFromCursor(info.mHint, cursor, BluetoothShare.FILENAME_HINT);
        info.mFilename = stringFromCursor(info.mFilename, cursor, BluetoothShare._DATA);
        info.mMimetype = stringFromCursor(info.mMimetype, cursor, BluetoothShare.MIMETYPE);
        info.mDirection = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.DIRECTION));
        info.mDestination = stringFromCursor(info.mDestination, cursor, BluetoothShare.DESTINATION);
        int newVisibility = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.VISIBILITY));
        boolean confirmUpdated = false;
        int newConfirm = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION));
        if (info.mVisibility == 0 && newVisibility != 0 && (BluetoothShare.isStatusCompleted(info.mStatus) || newConfirm == 0)) {
            this.mNotifier.mNotificationMgr.cancel(info.mId);
        }
        info.mVisibility = newVisibility;
        if (info.mConfirm == 0 && newConfirm != 0) {
            confirmUpdated = true;
        }
        info.mConfirm = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION));
        int newStatus = cursor.getInt(statusColumn);
        if (!BluetoothShare.isStatusCompleted(info.mStatus) && BluetoothShare.isStatusCompleted(newStatus)) {
            this.mNotifier.mNotificationMgr.cancel(info.mId);
        }
        info.mStatus = newStatus;
        info.mTotalBytes = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES));
        info.mCurrentBytes = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES));
        info.mTimestamp = (long) cursor.getInt(cursor.getColumnIndexOrThrow("timestamp"));
        info.mMediaScanned = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.MEDIA_SCANNED)) != 0;
        if (confirmUpdated) {
            i = findBatchWithTimeStamp(info.mTimestamp);
            if (i != -1) {
                BluetoothOppBatch batch = (BluetoothOppBatch) this.mBatchs.get(i);
                if (this.mServerTransfer != null && batch.mId == this.mServerTransfer.getBatchId()) {
                    this.mServerTransfer.confirmStatusChanged();
                }
            }
        }
        i = findBatchWithTimeStamp(info.mTimestamp);
        if (i != -1) {
            batch = (BluetoothOppBatch) this.mBatchs.get(i);
            if (batch.mStatus == 2 || batch.mStatus == 3) {
                if (batch.mDirection == 0) {
                    if (this.mTransfer == null) {
                        Log.e(TAG, "Unexpected error! mTransfer is null");
                    } else if (batch.mId == this.mTransfer.getBatchId()) {
                        this.mTransfer.stop();
                    } else {
                        Log.e(TAG, "Unexpected error! batch id " + batch.mId + " doesn't match mTransfer id " + this.mTransfer.getBatchId());
                    }
                    this.mTransfer = null;
                } else {
                    if (this.mServerTransfer == null) {
                        Log.e(TAG, "Unexpected error! mServerTransfer is null");
                    } else if (batch.mId == this.mServerTransfer.getBatchId()) {
                        this.mServerTransfer.stop();
                    } else {
                        Log.e(TAG, "Unexpected error! batch id " + batch.mId + " doesn't match mServerTransfer id " + this.mServerTransfer.getBatchId());
                    }
                    this.mServerTransfer = null;
                }
                removeBatch(batch);
            }
        }
    }

    private void deleteShare(int arrayPos) {
        BluetoothOppShareInfo info = (BluetoothOppShareInfo) this.mShares.get(arrayPos);
        int i = findBatchWithTimeStamp(info.mTimestamp);
        if (i != -1) {
            BluetoothOppBatch batch = (BluetoothOppBatch) this.mBatchs.get(i);
            if (batch.hasShare(info)) {
                batch.cancelBatch();
            }
            if (batch.isEmpty()) {
                removeBatch(batch);
            }
        }
        this.mShares.remove(arrayPos);
    }

    private String stringFromCursor(String old, Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        if (old == null) {
            return cursor.getString(index);
        }
        if (this.mNewChars == null) {
            this.mNewChars = new CharArrayBuffer(128);
        }
        cursor.copyStringToBuffer(index, this.mNewChars);
        int length = this.mNewChars.sizeCopied;
        if (length != old.length()) {
            return cursor.getString(index);
        }
        if (this.mOldChars == null || this.mOldChars.sizeCopied < length) {
            this.mOldChars = new CharArrayBuffer(length);
        }
        char[] oldArray = this.mOldChars.data;
        char[] newArray = this.mNewChars.data;
        old.getChars(0, length, oldArray, 0);
        for (int i = length - 1; i >= 0; i--) {
            if (oldArray[i] != newArray[i]) {
                return new String(newArray, 0, length);
            }
        }
        return old;
    }

    private int findBatchWithTimeStamp(long timestamp) {
        for (int i = this.mBatchs.size() - 1; i >= 0; i--) {
            if (((BluetoothOppBatch) this.mBatchs.get(i)).mTimestamp == timestamp) {
                return i;
            }
        }
        return -1;
    }

    private void removeBatch(BluetoothOppBatch batch) {
        this.mBatchs.remove(batch);
        if (this.mBatchs.size() > 0) {
            int i = 0;
            while (i < this.mBatchs.size()) {
                BluetoothOppBatch nextBatch = (BluetoothOppBatch) this.mBatchs.get(i);
                if (nextBatch.mStatus != 1) {
                    if (nextBatch.mDirection == 0) {
                        this.mTransfer = new BluetoothOppTransfer(this, this.mPowerManager, nextBatch);
                        this.mTransfer.start();
                        return;
                    } else if (nextBatch.mDirection != 1 || this.mServerSession == null) {
                        i++;
                    } else {
                        this.mServerTransfer = new BluetoothOppTransfer(this, this.mPowerManager, nextBatch, this.mServerSession);
                        this.mServerTransfer.start();
                        if (nextBatch.getPendingShare().mConfirm == 1) {
                            this.mServerTransfer.confirmStatusChanged();
                            return;
                        }
                        return;
                    }
                }
                return;
            }
        }
    }

    private boolean needAction(int arrayPos) {
        if (BluetoothShare.isStatusCompleted(((BluetoothOppShareInfo) this.mShares.get(arrayPos)).mStatus)) {
            return false;
        }
        return true;
    }

    private boolean visibleNotification(int arrayPos) {
        return ((BluetoothOppShareInfo) this.mShares.get(arrayPos)).hasCompletionNotification();
    }

    private boolean scanFile(Cursor cursor, int arrayPos) {
        boolean z = true;
        BluetoothOppShareInfo info = (BluetoothOppShareInfo) this.mShares.get(arrayPos);
        synchronized (this) {
            Log.d(TAG, "Scanning file " + info.mFilename);
            if (this.mMediaScanInProgress) {
                z = false;
            } else {
                this.mMediaScanInProgress = true;
                MediaScannerNotifier mediaScannerNotifier = new MediaScannerNotifier(this, info, this.mHandler);
            }
        }
        return z;
    }

    private boolean shouldScanFile(int arrayPos) {
        BluetoothOppShareInfo info = (BluetoothOppShareInfo) this.mShares.get(arrayPos);
        if (!BluetoothShare.isStatusSuccess(info.mStatus) || info.mDirection != 1 || info.mMediaScanned || info.mConfirm == 5) {
            return false;
        }
        return true;
    }

    private static void trimDatabase(ContentResolver contentResolver) {
        String INVISIBLE = "visibility=1";
        String WHERE_INVISIBLE_COMPLETE_OUTBOUND = "direction=0 AND status>=200 AND visibility=1";
        int delNum = contentResolver.delete(BluetoothShare.CONTENT_URI, "direction=0 AND status>=200 AND visibility=1", null);
        String WHERE_INVISIBLE_COMPLETE_INBOUND_FAILED = "direction=1 AND status>200 AND visibility=1";
        delNum = contentResolver.delete(BluetoothShare.CONTENT_URI, "direction=1 AND status>200 AND visibility=1", null);
        String WHERE_INBOUND_SUCCESS = "direction=1 AND status=200 AND visibility=1";
        ContentResolver contentResolver2 = contentResolver;
        Cursor cursor = contentResolver2.query(BluetoothShare.CONTENT_URI, new String[]{"_id"}, "direction=1 AND status=200 AND visibility=1", null, "_id");
        if (cursor != null) {
            int recordNum = cursor.getCount();
            if (recordNum > 1000 && cursor.moveToPosition(recordNum - 1000)) {
                ContentResolver contentResolver3 = contentResolver;
                delNum = contentResolver3.delete(BluetoothShare.CONTENT_URI, "_id < " + cursor.getLong(cursor.getColumnIndexOrThrow("_id")), null);
            }
            cursor.close();
        }
    }
}
