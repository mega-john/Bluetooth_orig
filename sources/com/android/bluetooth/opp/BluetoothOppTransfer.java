package com.android.bluetooth.opp;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothUuid;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import com.android.bluetooth.opp.BluetoothOppBatch.BluetoothOppBatchListener;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import javax.obex.ObexTransport;

public class BluetoothOppTransfer implements BluetoothOppBatchListener {
    private static final int CONNECT_RETRY_TIME = 100;
    private static final int CONNECT_WAIT_TIMEOUT = 45000;
    /* renamed from: D */
    private static final boolean f57D = true;
    private static final short OPUSH_UUID16 = (short) 4357;
    private static final int RFCOMM_CONNECTED = 11;
    private static final int RFCOMM_ERROR = 10;
    private static final int SOCKET_ERROR_RETRY = 13;
    private static final String SOCKET_LINK_KEY_ERROR = "Invalid exchange";
    private static final String TAG = "BtOppTransfer";
    /* renamed from: V */
    private static final boolean f58V = false;
    private BluetoothAdapter mAdapter;
    private BluetoothOppBatch mBatch;
    private SocketConnectThread mConnectThread;
    private Context mContext;
    private BluetoothOppShareInfo mCurrentShare;
    private HandlerThread mHandlerThread;
    private BluetoothOppObexSession mSession;
    private EventHandler mSessionHandler;
    private long mTimestamp;
    private ObexTransport mTransport;

    private class EventHandler extends Handler {
        public EventHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    BluetoothOppShareInfo info = msg.obj;
                    if (BluetoothOppTransfer.this.mBatch.mDirection == 0) {
                        BluetoothOppTransfer.this.mCurrentShare = BluetoothOppTransfer.this.mBatch.getPendingShare();
                        if (BluetoothOppTransfer.this.mCurrentShare != null) {
                            BluetoothOppTransfer.this.processCurrentShare();
                            return;
                        } else {
                            BluetoothOppTransfer.this.mSession.stop();
                            return;
                        }
                    }
                    return;
                case 1:
                    BluetoothOppShareInfo info1 = msg.obj;
                    BluetoothOppTransfer.this.mBatch.mStatus = 2;
                    BluetoothOppTransfer.this.tickShareStatus(info1);
                    return;
                case 2:
                    BluetoothOppShareInfo info2 = msg.obj;
                    BluetoothOppTransfer.this.mSession.stop();
                    BluetoothOppTransfer.this.mBatch.mStatus = 3;
                    BluetoothOppTransfer.this.markBatchFailed(info2.mStatus);
                    BluetoothOppTransfer.this.tickShareStatus(BluetoothOppTransfer.this.mCurrentShare);
                    return;
                case 3:
                    BluetoothOppShareInfo info3 = msg.obj;
                    if (BluetoothOppTransfer.this.mBatch.mDirection == 0) {
                        try {
                            if (BluetoothOppTransfer.this.mTransport == null) {
                                Log.v(BluetoothOppTransfer.TAG, "receive MSG_SHARE_INTERRUPTED but mTransport = null");
                            } else {
                                BluetoothOppTransfer.this.mTransport.close();
                            }
                        } catch (IOException e) {
                            Log.e(BluetoothOppTransfer.TAG, "failed to close mTransport");
                        }
                        BluetoothOppTransfer.this.mBatch.mStatus = 3;
                        if (info3 != null) {
                            BluetoothOppTransfer.this.markBatchFailed(info3.mStatus);
                        } else {
                            BluetoothOppTransfer.this.markBatchFailed();
                        }
                        BluetoothOppTransfer.this.tickShareStatus(BluetoothOppTransfer.this.mCurrentShare);
                        return;
                    }
                    return;
                case 4:
                    if (BluetoothOppTransfer.this.mBatch.mDirection == 0) {
                        try {
                            if (BluetoothOppTransfer.this.mTransport == null) {
                                Log.v(BluetoothOppTransfer.TAG, "receive MSG_SHARE_INTERRUPTED but mTransport = null");
                                return;
                            } else {
                                BluetoothOppTransfer.this.mTransport.close();
                                return;
                            }
                        } catch (IOException e2) {
                            Log.e(BluetoothOppTransfer.TAG, "failed to close mTransport");
                            return;
                        }
                    }
                    ((NotificationManager) BluetoothOppTransfer.this.mContext.getSystemService("notification")).cancel(BluetoothOppTransfer.this.mCurrentShare.mId);
                    BluetoothOppTransfer.this.mContext.sendBroadcast(new Intent(BluetoothShare.USER_CONFIRMATION_TIMEOUT_ACTION));
                    BluetoothOppTransfer.this.markShareTimeout(BluetoothOppTransfer.this.mCurrentShare);
                    return;
                case 10:
                    BluetoothOppTransfer.this.mConnectThread = null;
                    BluetoothOppTransfer.this.markBatchFailed(BluetoothShare.STATUS_CONNECTION_ERROR);
                    BluetoothOppTransfer.this.mBatch.mStatus = 3;
                    return;
                case 11:
                    BluetoothOppTransfer.this.mConnectThread = null;
                    BluetoothOppTransfer.this.mTransport = (ObexTransport) msg.obj;
                    BluetoothOppTransfer.this.startObexSession();
                    return;
                case 13:
                    BluetoothOppTransfer.this.mConnectThread = new SocketConnectThread((BluetoothDevice) msg.obj, true);
                    BluetoothOppTransfer.this.mConnectThread.start();
                    return;
                default:
                    return;
            }
        }
    }

    private class SocketConnectThread extends Thread {
        private BluetoothSocket btSocket = null;
        private final int channel;
        private final BluetoothDevice device;
        private final String host;
        private boolean isConnected;
        private boolean mRetry = false;
        private long timestamp;

        public SocketConnectThread(String host, int port, int dummy) {
            super("Socket Connect Thread");
            this.host = host;
            this.channel = port;
            this.device = null;
            this.isConnected = false;
        }

        public SocketConnectThread(BluetoothDevice device, int channel, boolean retry) {
            super("Socket Connect Thread");
            this.device = device;
            this.host = null;
            this.channel = channel;
            this.isConnected = false;
            this.mRetry = retry;
        }

        public SocketConnectThread(BluetoothDevice device, boolean retry) {
            super("Socket Connect Thread");
            this.device = device;
            this.host = null;
            this.channel = -1;
            this.isConnected = false;
            this.mRetry = retry;
        }

        public void interrupt() {
            if (this.btSocket != null) {
                try {
                    this.btSocket.close();
                } catch (IOException e) {
                    Log.v(BluetoothOppTransfer.TAG, "Error when close socket");
                }
            }
        }

        public void run() {
            this.timestamp = System.currentTimeMillis();
            try {
                this.btSocket = this.device.createInsecureRfcommSocketToServiceRecord(BluetoothUuid.ObexObjectPush.getUuid());
                try {
                    this.btSocket.connect();
                    BluetoothOppRfcommTransport transport = new BluetoothOppRfcommTransport(this.btSocket);
                    BluetoothOppPreference.getInstance(BluetoothOppTransfer.this.mContext).setName(this.device, this.device.getName());
                    BluetoothOppTransfer.this.mSessionHandler.obtainMessage(11, transport).sendToTarget();
                } catch (IOException e) {
                    Log.e(BluetoothOppTransfer.TAG, "Rfcomm socket connect exception", e);
                    if (this.mRetry || !e.getMessage().equals(BluetoothOppTransfer.SOCKET_LINK_KEY_ERROR)) {
                        markConnectionFailed(this.btSocket);
                        return;
                    }
                    BluetoothOppTransfer.this.mSessionHandler.sendMessageDelayed(BluetoothOppTransfer.this.mSessionHandler.obtainMessage(13, -1, -1, this.device), 1500);
                }
            } catch (IOException e1) {
                Log.e(BluetoothOppTransfer.TAG, "Rfcomm socket create error", e1);
                markConnectionFailed(this.btSocket);
            }
        }

        private void markConnectionFailed(Socket s) {
            try {
                s.close();
            } catch (IOException e) {
                Log.e(BluetoothOppTransfer.TAG, "TCP socket close error");
            }
            BluetoothOppTransfer.this.mSessionHandler.obtainMessage(10).sendToTarget();
        }

        private void markConnectionFailed(BluetoothSocket s) {
            try {
                s.close();
            } catch (IOException e) {
            }
            BluetoothOppTransfer.this.mSessionHandler.obtainMessage(10).sendToTarget();
        }
    }

    public BluetoothOppTransfer(Context context, PowerManager powerManager, BluetoothOppBatch batch, BluetoothOppObexSession session) {
        this.mContext = context;
        this.mBatch = batch;
        this.mSession = session;
        this.mBatch.registerListern(this);
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public BluetoothOppTransfer(Context context, PowerManager powerManager, BluetoothOppBatch batch) {
        this(context, powerManager, batch, null);
    }

    public int getBatchId() {
        return this.mBatch.mId;
    }

    private void markShareTimeout(BluetoothOppShareInfo share) {
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + share.mId);
        ContentValues updateValues = new ContentValues();
        updateValues.put(BluetoothShare.USER_CONFIRMATION, Integer.valueOf(4));
        this.mContext.getContentResolver().update(contentUri, updateValues, null, null);
    }

    private void markBatchFailed(int failReason) {
        synchronized (this) {
            try {
                wait(1000);
            } catch (InterruptedException e) {
            }
        }
        Log.d(TAG, "Mark all ShareInfo in the batch as failed");
        if (this.mCurrentShare != null) {
            if (BluetoothShare.isStatusError(this.mCurrentShare.mStatus)) {
                failReason = this.mCurrentShare.mStatus;
            }
            if (this.mCurrentShare.mDirection == 1 && this.mCurrentShare.mFilename != null) {
                new File(this.mCurrentShare.mFilename).delete();
            }
        }
        if (this.mBatch != null) {
            BluetoothOppShareInfo info = this.mBatch.getPendingShare();
            while (info != null) {
                if (info.mStatus < BluetoothShare.STATUS_SUCCESS) {
                    info.mStatus = failReason;
                    Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + info.mId);
                    ContentValues updateValues = new ContentValues();
                    updateValues.put(BluetoothShare.STATUS, Integer.valueOf(info.mStatus));
                    if (info.mDirection == 0) {
                        BluetoothOppSendFileInfo fileInfo = BluetoothOppUtility.getSendFileInfo(info.mUri);
                        BluetoothOppUtility.closeSendFileInfo(info.mUri);
                        if (fileInfo.mFileName != null) {
                            updateValues.put(BluetoothShare.FILENAME_HINT, fileInfo.mFileName);
                            updateValues.put(BluetoothShare.TOTAL_BYTES, Long.valueOf(fileInfo.mLength));
                            updateValues.put(BluetoothShare.MIMETYPE, fileInfo.mMimetype);
                        }
                    } else if (info.mStatus < BluetoothShare.STATUS_SUCCESS && info.mFilename != null) {
                        new File(info.mFilename).delete();
                    }
                    this.mContext.getContentResolver().update(contentUri, updateValues, null, null);
                    Constants.sendIntentIfCompleted(this.mContext, contentUri, info.mStatus);
                }
                info = this.mBatch.getPendingShare();
            }
        }
    }

    private void markBatchFailed() {
        markBatchFailed(BluetoothShare.STATUS_UNKNOWN_ERROR);
    }

    public void start() {
        if (!this.mAdapter.isEnabled()) {
            Log.e(TAG, "Can't start transfer when Bluetooth is disabled for " + this.mBatch.mId);
            markBatchFailed();
            this.mBatch.mStatus = 3;
        } else if (this.mHandlerThread == null) {
            this.mHandlerThread = new HandlerThread("BtOpp Transfer Handler", 10);
            this.mHandlerThread.start();
            this.mSessionHandler = new EventHandler(this.mHandlerThread.getLooper());
            if (this.mBatch.mDirection == 0) {
                startConnectSession();
            } else if (this.mBatch.mDirection == 1) {
                startObexSession();
            }
        }
    }

    public void stop() {
        if (this.mConnectThread != null) {
            try {
                this.mConnectThread.interrupt();
                this.mConnectThread.join();
            } catch (InterruptedException e) {
            }
            this.mConnectThread = null;
        }
        if (this.mSession != null) {
            this.mSession.stop();
        }
        if (this.mHandlerThread != null) {
            this.mHandlerThread.getLooper().quit();
            this.mHandlerThread.interrupt();
            this.mHandlerThread = null;
        }
    }

    private void startObexSession() {
        this.mBatch.mStatus = 1;
        this.mCurrentShare = this.mBatch.getPendingShare();
        if (this.mCurrentShare == null) {
            Log.e(TAG, "Unexpected error happened !");
            return;
        }
        if (this.mBatch.mDirection == 0) {
            this.mSession = new BluetoothOppObexClientSession(this.mContext, this.mTransport);
        } else if (this.mBatch.mDirection == 1 && this.mSession == null) {
            Log.e(TAG, "Unexpected error happened !");
            markBatchFailed();
            this.mBatch.mStatus = 3;
            return;
        }
        this.mSession.start(this.mSessionHandler, this.mBatch.getNumShares());
        processCurrentShare();
    }

    private void processCurrentShare() {
        this.mSession.addShare(this.mCurrentShare);
        if (this.mCurrentShare.mConfirm == 5) {
            confirmStatusChanged();
        }
    }

    public void confirmStatusChanged() {
        new Thread("Server Unblock thread") {
            public void run() {
                synchronized (BluetoothOppTransfer.this.mSession) {
                    BluetoothOppTransfer.this.mSession.unblock();
                    BluetoothOppTransfer.this.mSession.notify();
                }
            }
        }.start();
    }

    private void startConnectSession() {
        this.mConnectThread = new SocketConnectThread(this.mBatch.mDestination, false);
        this.mConnectThread.start();
    }

    private void tickShareStatus(BluetoothOppShareInfo share) {
        if (share == null) {
            Log.d(TAG, "Share is null");
            return;
        }
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + share.mId);
        ContentValues updateValues = new ContentValues();
        updateValues.put(BluetoothShare.DIRECTION, Integer.valueOf(share.mDirection));
        this.mContext.getContentResolver().update(contentUri, updateValues, null, null);
    }

    public void onShareAdded(int id) {
        if (this.mBatch.getPendingShare().mDirection == 1) {
            this.mCurrentShare = this.mBatch.getPendingShare();
            if (this.mCurrentShare == null) {
                return;
            }
            if (this.mCurrentShare.mConfirm == 2 || this.mCurrentShare.mConfirm == 5) {
                processCurrentShare();
                confirmStatusChanged();
            }
        }
    }

    public void onShareDeleted(int id) {
    }

    public void onBatchCanceled() {
        stop();
        this.mBatch.mStatus = 2;
    }
}
