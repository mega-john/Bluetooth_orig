package com.android.bluetooth.opp;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.webkit.MimeTypeMap;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.obex.HeaderSet;
import javax.obex.ObexTransport;
import javax.obex.Operation;
import javax.obex.ServerRequestHandler;
import javax.obex.ServerSession;

public class BluetoothOppObexServerSession extends ServerRequestHandler implements BluetoothOppObexSession {
    /* renamed from: D */
    private static final boolean f43D = true;
    private static final String TAG = "BtOppObexServer";
    /* renamed from: V */
    private static final boolean f44V = false;
    private int mAccepted = 0;
    private Handler mCallback = null;
    private Context mContext;
    private BluetoothOppReceiveFileInfo mFileInfo;
    private BluetoothOppShareInfo mInfo;
    private boolean mInterrupted = false;
    private int mLocalShareInfoId;
    private WakeLock mPartialWakeLock;
    private boolean mServerBlocking = true;
    private ServerSession mSession;
    boolean mTimeoutMsgSent = false;
    private long mTimestamp;
    private ObexTransport mTransport;
    private WakeLock mWakeLock;

    public BluetoothOppObexServerSession(Context context, ObexTransport transport) {
        this.mContext = context;
        this.mTransport = transport;
        PowerManager pm = (PowerManager) this.mContext.getSystemService("power");
        this.mWakeLock = pm.newWakeLock(805306394, TAG);
        this.mPartialWakeLock = pm.newWakeLock(1, TAG);
    }

    public void unblock() {
        this.mServerBlocking = false;
    }

    public void preStart() {
        Log.d(TAG, "acquire full WakeLock");
        this.mWakeLock.acquire();
        try {
            Log.d(TAG, "Create ServerSession with transport " + this.mTransport.toString());
            this.mSession = new ServerSession(this.mTransport, this, null);
        } catch (IOException e) {
            Log.e(TAG, "Create server session error" + e);
        }
    }

    public void start(Handler handler, int numShares) {
        Log.d(TAG, "Start!");
        this.mCallback = handler;
    }

    public void stop() {
        Log.d(TAG, "Stop!");
        this.mInterrupted = true;
        if (this.mSession != null) {
            try {
                this.mSession.close();
                this.mTransport.close();
            } catch (IOException e) {
                Log.e(TAG, "close mTransport error" + e);
            }
        }
        this.mCallback = null;
        this.mSession = null;
    }

    public void addShare(BluetoothOppShareInfo info) {
        Log.d(TAG, "addShare for id " + info.mId);
        this.mInfo = info;
        this.mFileInfo = processShareInfo();
    }

    public int onPut(Operation op) {
        Log.d(TAG, "onPut " + op.toString());
        int obexResponse = 160;
        if (this.mAccepted == 3) {
            return 195;
        }
        String destination;
        if (this.mTransport instanceof BluetoothOppRfcommTransport) {
            destination = ((BluetoothOppRfcommTransport) this.mTransport).getRemoteAddress();
        } else {
            destination = "FF:FF:FF:00:00:00";
        }
        boolean isWhitelisted = BluetoothOppManager.getInstance(this.mContext).isWhitelisted(destination);
        boolean pre_reject = false;
        try {
            HeaderSet request = op.getReceivedHeader();
            String name = (String) request.getHeader(1);
            Long length = (Long) request.getHeader(195);
            String mimeType = (String) request.getHeader(66);
            if (length.longValue() == 0) {
                Log.w(TAG, "length is 0, reject the transfer");
                pre_reject = true;
                obexResponse = 203;
            }
            if (name == null || name.equals("")) {
                Log.w(TAG, "name is null or empty, reject the transfer");
                pre_reject = true;
                obexResponse = BluetoothShare.STATUS_RUNNING;
            }
            if (!pre_reject) {
                int dotIndex = name.lastIndexOf(".");
                if (dotIndex >= 0 || mimeType != null) {
                    String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dotIndex + 1).toLowerCase());
                    if (type != null) {
                        mimeType = type;
                    } else if (mimeType == null) {
                        Log.w(TAG, "Can't get mimetype, reject the transfer");
                        pre_reject = true;
                        obexResponse = 207;
                    }
                    if (mimeType != null) {
                        mimeType = mimeType.toLowerCase();
                    }
                } else {
                    Log.w(TAG, "There is no file extension or mime type,reject the transfer");
                    pre_reject = true;
                    obexResponse = BluetoothShare.STATUS_RUNNING;
                }
            }
            if (!pre_reject && (mimeType == null || (!(isWhitelisted || Constants.mimeTypeMatches(mimeType, Constants.ACCEPTABLE_SHARE_INBOUND_TYPES)) || Constants.mimeTypeMatches(mimeType, Constants.UNACCEPTABLE_SHARE_INBOUND_TYPES)))) {
                Log.w(TAG, "mimeType is null or in unacceptable list, reject the transfer");
                pre_reject = true;
                obexResponse = 207;
            }
            if (pre_reject && obexResponse != 160) {
                return obexResponse;
            }
            ContentValues values = new ContentValues();
            values.put(BluetoothShare.FILENAME_HINT, name);
            values.put(BluetoothShare.TOTAL_BYTES, Integer.valueOf(length.intValue()));
            values.put(BluetoothShare.MIMETYPE, mimeType);
            values.put(BluetoothShare.DESTINATION, destination);
            values.put(BluetoothShare.DIRECTION, Integer.valueOf(1));
            values.put("timestamp", Long.valueOf(this.mTimestamp));
            boolean needConfirm = true;
            if (!this.mServerBlocking && (this.mAccepted == 1 || this.mAccepted == 2)) {
                values.put(BluetoothShare.USER_CONFIRMATION, Integer.valueOf(2));
                needConfirm = false;
            }
            if (isWhitelisted) {
                values.put(BluetoothShare.USER_CONFIRMATION, Integer.valueOf(5));
                needConfirm = false;
            }
            this.mLocalShareInfoId = Integer.parseInt((String) this.mContext.getContentResolver().insert(BluetoothShare.CONTENT_URI, values).getPathSegments().get(1));
            if (needConfirm) {
                Intent in = new Intent(BluetoothShare.INCOMING_FILE_CONFIRMATION_REQUEST_ACTION);
                in.setClassName("com.android.bluetooth", BluetoothOppReceiver.class.getName());
                this.mContext.sendBroadcast(in);
            }
            synchronized (this) {
                if (this.mWakeLock.isHeld()) {
                    this.mPartialWakeLock.acquire();
                    this.mWakeLock.release();
                }
                this.mServerBlocking = true;
                while (this.mServerBlocking) {
                    try {
                        wait(1000);
                        if (!(this.mCallback == null || this.mTimeoutMsgSent)) {
                            this.mCallback.sendMessageDelayed(this.mCallback.obtainMessage(4), 50000);
                            this.mTimeoutMsgSent = true;
                        }
                    } catch (InterruptedException e) {
                    }
                }
            }
            Log.d(TAG, "Server unblocked ");
            synchronized (this) {
                if (this.mCallback != null && this.mTimeoutMsgSent) {
                    this.mCallback.removeMessages(4);
                }
            }
            if (this.mInfo.mId != this.mLocalShareInfoId) {
                Log.e(TAG, "Unexpected error!");
            }
            this.mAccepted = this.mInfo.mConfirm;
            int status = BluetoothShare.STATUS_SUCCESS;
            Message msg;
            if (this.mAccepted == 1 || this.mAccepted == 2 || this.mAccepted == 5) {
                if (this.mFileInfo.mFileName == null) {
                    status = this.mFileInfo.mStatus;
                    this.mInfo.mStatus = this.mFileInfo.mStatus;
                    Constants.updateShareStatus(this.mContext, this.mInfo.mId, status);
                    obexResponse = 208;
                }
                if (this.mFileInfo.mFileName != null) {
                    ContentValues updateValues = new ContentValues();
                    Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + this.mInfo.mId);
                    updateValues.put(BluetoothShare._DATA, this.mFileInfo.mFileName);
                    updateValues.put(BluetoothShare.STATUS, Integer.valueOf(BluetoothShare.STATUS_RUNNING));
                    this.mContext.getContentResolver().update(contentUri, updateValues, null, null);
                    status = receiveFile(this.mFileInfo, op);
                    if (status != 200) {
                        obexResponse = 208;
                    }
                    Constants.updateShareStatus(this.mContext, this.mInfo.mId, status);
                }
                if (status == 200) {
                    msg = Message.obtain(this.mCallback, 0);
                    msg.obj = this.mInfo;
                    msg.sendToTarget();
                } else if (this.mCallback != null) {
                    msg = Message.obtain(this.mCallback, 2);
                    this.mInfo.mStatus = status;
                    msg.obj = this.mInfo;
                    msg.sendToTarget();
                }
            } else if (this.mAccepted == 3 || this.mAccepted == 4) {
                Log.i(TAG, "Rejected incoming request");
                if (this.mFileInfo.mFileName != null) {
                    try {
                        this.mFileInfo.mOutputStream.close();
                    } catch (IOException e2) {
                        Log.e(TAG, "error close file stream");
                    }
                    new File(this.mFileInfo.mFileName).delete();
                }
                Constants.updateShareStatus(this.mContext, this.mInfo.mId, BluetoothShare.STATUS_CANCELED);
                obexResponse = 195;
                msg = Message.obtain(this.mCallback);
                msg.what = 3;
                this.mInfo.mStatus = BluetoothShare.STATUS_CANCELED;
                msg.obj = this.mInfo;
                msg.sendToTarget();
            }
            return obexResponse;
        } catch (IOException e3) {
            Log.e(TAG, "get getReceivedHeaders error " + e3);
            return BluetoothShare.STATUS_RUNNING;
        }
    }

    private int receiveFile(BluetoothOppReceiveFileInfo fileInfo, Operation op) {
        int status = -1;
        BufferedOutputStream bos = null;
        InputStream is = null;
        boolean error = false;
        try {
            is = op.openInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error when openInputStream");
            status = BluetoothShare.STATUS_OBEX_DATA_ERROR;
            error = true;
        }
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + this.mInfo.mId);
        if (!error) {
            ContentValues updateValues = new ContentValues();
            updateValues.put(BluetoothShare._DATA, fileInfo.mFileName);
            this.mContext.getContentResolver().update(contentUri, updateValues, null, null);
        }
        int position = 0;
        if (!error) {
            bos = new BufferedOutputStream(fileInfo.mOutputStream, 65536);
        }
        if (!error) {
            byte[] b = new byte[op.getMaxPacketSize()];
            while (!this.mInterrupted && ((long) position) != fileInfo.mLength) {
                int readLength = is.read(b);
                if (readLength == -1) {
                    Log.d(TAG, "Receive file reached stream end at position" + position);
                    break;
                }
                try {
                    bos.write(b, 0, readLength);
                    position += readLength;
                    updateValues = new ContentValues();
                    updateValues.put(BluetoothShare.CURRENT_BYTES, Integer.valueOf(position));
                    this.mContext.getContentResolver().update(contentUri, updateValues, null, null);
                } catch (IOException e1) {
                    Log.e(TAG, "Error when receiving file");
                    if ("Abort Received".equals(e1.getMessage())) {
                        status = BluetoothShare.STATUS_CANCELED;
                    } else {
                        status = BluetoothShare.STATUS_OBEX_DATA_ERROR;
                    }
                }
            }
        }
        if (this.mInterrupted) {
            Log.d(TAG, "receiving file interrupted by user.");
            status = BluetoothShare.STATUS_CANCELED;
        } else if (((long) position) == fileInfo.mLength) {
            Log.d(TAG, "Receiving file completed for " + fileInfo.mFileName);
            status = BluetoothShare.STATUS_SUCCESS;
        } else {
            Log.d(TAG, "Reading file failed at " + position + " of " + fileInfo.mLength);
            if (status == -1) {
                status = BluetoothShare.STATUS_UNKNOWN_ERROR;
            }
        }
        if (bos != null) {
            try {
                bos.close();
            } catch (IOException e2) {
                Log.e(TAG, "Error when closing stream after send");
            }
        }
        return status;
    }

    private BluetoothOppReceiveFileInfo processShareInfo() {
        Log.d(TAG, "processShareInfo() " + this.mInfo.mId);
        return BluetoothOppReceiveFileInfo.generateFileInfo(this.mContext, this.mInfo.mId);
    }

    public int onConnect(HeaderSet request, HeaderSet reply) {
        Log.d(TAG, "onConnect");
        try {
            if (((byte[]) request.getHeader(70)) != null) {
                return 198;
            }
            String destination;
            Long objectCount = (Long) request.getHeader(BluetoothShare.STATUS_RUNNING);
            if (this.mTransport instanceof BluetoothOppRfcommTransport) {
                destination = ((BluetoothOppRfcommTransport) this.mTransport).getRemoteAddress();
            } else {
                destination = "FF:FF:FF:00:00:00";
            }
            if (BluetoothOppManager.getInstance(this.mContext).isWhitelisted(destination)) {
                Intent intent = new Intent(Constants.ACTION_HANDOVER_STARTED);
                if (objectCount != null) {
                    intent.putExtra(Constants.EXTRA_BT_OPP_OBJECT_COUNT, objectCount.intValue());
                } else {
                    intent.putExtra(Constants.EXTRA_BT_OPP_OBJECT_COUNT, -1);
                }
                intent.putExtra(Constants.EXTRA_BT_OPP_ADDRESS, destination);
                this.mContext.sendBroadcast(intent, Constants.HANDOVER_STATUS_PERMISSION);
            }
            this.mTimestamp = System.currentTimeMillis();
            return 160;
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return 208;
        }
    }

    public void onDisconnect(HeaderSet req, HeaderSet resp) {
        Log.d(TAG, "onDisconnect");
        resp.responseCode = 160;
    }

    private synchronized void releaseWakeLocks() {
        if (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
        if (this.mPartialWakeLock.isHeld()) {
            this.mPartialWakeLock.release();
        }
    }

    public void onClose() {
        releaseWakeLocks();
        if (this.mCallback != null) {
            Message msg = Message.obtain(this.mCallback);
            msg.what = 1;
            msg.obj = this.mInfo;
            msg.sendToTarget();
        }
    }
}
