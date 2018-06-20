package com.android.bluetooth.opp;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.obex.ClientOperation;
import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.ObexTransport;

public class BluetoothOppObexClientSession implements BluetoothOppObexSession {
    /* renamed from: D */
    private static final boolean f41D = true;
    private static final String TAG = "BtOppObexClient";
    /* renamed from: V */
    private static final boolean f42V = false;
    private Handler mCallback;
    private Context mContext;
    private volatile boolean mInterrupted;
    private ClientThread mThread;
    private ObexTransport mTransport;
    private volatile boolean mWaitingForRemote;

    private class ClientThread extends Thread {
        private static final int sSleepTime = 500;
        private boolean mConnected = false;
        private Context mContext1;
        private ClientSession mCs;
        private BluetoothOppSendFileInfo mFileInfo = null;
        private BluetoothOppShareInfo mInfo;
        private int mNumShares;
        private ObexTransport mTransport1;
        private volatile boolean waitingForShare;
        private WakeLock wakeLock;

        public ClientThread(Context context, ObexTransport transport, int initialNumShares) {
            super("BtOpp ClientThread");
            this.mContext1 = context;
            this.mTransport1 = transport;
            this.waitingForShare = true;
            BluetoothOppObexClientSession.this.mWaitingForRemote = false;
            this.mNumShares = initialNumShares;
            this.wakeLock = ((PowerManager) this.mContext1.getSystemService("power")).newWakeLock(1, BluetoothOppObexClientSession.TAG);
        }

        public void addShare(BluetoothOppShareInfo info) {
            this.mInfo = info;
            this.mFileInfo = processShareInfo();
            this.waitingForShare = false;
        }

        public void run() {
            Process.setThreadPriority(10);
            this.wakeLock.acquire();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                BluetoothOppObexClientSession.this.mInterrupted = true;
            }
            if (!BluetoothOppObexClientSession.this.mInterrupted) {
                connect(this.mNumShares);
            }
            while (!BluetoothOppObexClientSession.this.mInterrupted) {
                if (this.waitingForShare) {
                    try {
                        Log.d(BluetoothOppObexClientSession.TAG, "Client thread waiting for next share, sleep for 500");
                        Thread.sleep(500);
                    } catch (InterruptedException e2) {
                    }
                } else {
                    doSend();
                }
            }
            disconnect();
            if (this.wakeLock.isHeld()) {
                this.wakeLock.release();
            }
            Message msg = Message.obtain(BluetoothOppObexClientSession.this.mCallback);
            msg.what = 1;
            msg.obj = this.mInfo;
            msg.sendToTarget();
        }

        private void disconnect() {
            try {
                if (this.mCs != null) {
                    this.mCs.disconnect(null);
                }
                this.mCs = null;
                Log.d(BluetoothOppObexClientSession.TAG, "OBEX session disconnected");
            } catch (IOException e) {
                Log.w(BluetoothOppObexClientSession.TAG, "OBEX session disconnect error" + e);
            }
            try {
                if (this.mCs != null) {
                    Log.d(BluetoothOppObexClientSession.TAG, "OBEX session close mCs");
                    this.mCs.close();
                    Log.d(BluetoothOppObexClientSession.TAG, "OBEX session closed");
                }
            } catch (IOException e2) {
                Log.w(BluetoothOppObexClientSession.TAG, "OBEX session close error" + e2);
            }
            if (this.mTransport1 != null) {
                try {
                    this.mTransport1.close();
                } catch (IOException e3) {
                    Log.e(BluetoothOppObexClientSession.TAG, "mTransport.close error");
                }
            }
        }

        private void connect(int numShares) {
            Log.d(BluetoothOppObexClientSession.TAG, "Create ClientSession with transport " + this.mTransport1.toString());
            try {
                this.mCs = new ClientSession(this.mTransport1);
                this.mConnected = true;
            } catch (IOException e) {
                Log.e(BluetoothOppObexClientSession.TAG, "OBEX session create error");
            }
            if (this.mConnected) {
                this.mConnected = false;
                HeaderSet hs = new HeaderSet();
                hs.setHeader(BluetoothShare.STATUS_RUNNING, Long.valueOf((long) numShares));
                synchronized (this) {
                    BluetoothOppObexClientSession.this.mWaitingForRemote = true;
                }
                try {
                    this.mCs.connect(hs);
                    Log.d(BluetoothOppObexClientSession.TAG, "OBEX session created");
                    this.mConnected = true;
                } catch (IOException e2) {
                    Log.e(BluetoothOppObexClientSession.TAG, "OBEX session connect error");
                }
            }
            synchronized (this) {
                BluetoothOppObexClientSession.this.mWaitingForRemote = false;
            }
        }

        private void doSend() {
            int status = BluetoothShare.STATUS_SUCCESS;
            while (this.mFileInfo == null) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    status = BluetoothShare.STATUS_CANCELED;
                }
            }
            if (!this.mConnected) {
                status = BluetoothShare.STATUS_CONNECTION_ERROR;
            }
            if (status == BluetoothShare.STATUS_SUCCESS) {
                if (this.mFileInfo.mFileName != null) {
                    status = sendFile(this.mFileInfo);
                } else {
                    status = this.mFileInfo.mStatus;
                }
                this.waitingForShare = true;
            } else {
                Constants.updateShareStatus(this.mContext1, this.mInfo.mId, status);
            }
            if (status == BluetoothShare.STATUS_SUCCESS) {
                Message msg = Message.obtain(BluetoothOppObexClientSession.this.mCallback);
                msg.what = 0;
                msg.obj = this.mInfo;
                msg.sendToTarget();
                return;
            }
            msg = Message.obtain(BluetoothOppObexClientSession.this.mCallback);
            msg.what = 2;
            this.mInfo.mStatus = status;
            msg.obj = this.mInfo;
            msg.sendToTarget();
        }

        private BluetoothOppSendFileInfo processShareInfo() {
            BluetoothOppSendFileInfo fileInfo = BluetoothOppUtility.getSendFileInfo(this.mInfo.mUri);
            if (fileInfo.mFileName == null || fileInfo.mLength == 0) {
                Constants.updateShareStatus(this.mContext1, this.mInfo.mId, fileInfo.mStatus);
            } else {
                ContentValues updateValues = new ContentValues();
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + this.mInfo.mId);
                updateValues.put(BluetoothShare.FILENAME_HINT, fileInfo.mFileName);
                updateValues.put(BluetoothShare.TOTAL_BYTES, Long.valueOf(fileInfo.mLength));
                updateValues.put(BluetoothShare.MIMETYPE, fileInfo.mMimetype);
                this.mContext1.getContentResolver().update(contentUri, updateValues, null, null);
            }
            return fileInfo;
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        private int sendFile(BluetoothOppSendFileInfo fileInfo) {
            boolean error = false;
            int responseCode = -1;
            int status = BluetoothShare.STATUS_SUCCESS;
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + this.mInfo.mId);
            HeaderSet request = new HeaderSet();
            request.setHeader(1, fileInfo.mFileName);
            request.setHeader(66, fileInfo.mMimetype);
            BluetoothOppObexClientSession.applyRemoteDeviceQuirks(request, this.mInfo.mDestination, fileInfo.mFileName);
            Constants.updateShareStatus(this.mContext1, this.mInfo.mId, BluetoothShare.STATUS_RUNNING);
            request.setHeader(195, Long.valueOf(fileInfo.mLength));
            ClientOperation putOperation = null;
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                ContentValues updateValues;
                synchronized (this) {
                    BluetoothOppObexClientSession.this.mWaitingForRemote = true;
                }
                putOperation = (ClientOperation) this.mCs.put(request);
                synchronized (this) {
                    BluetoothOppObexClientSession.this.mWaitingForRemote = false;
                }
                if (!error) {
                    outputStream = putOperation.openOutputStream();
                    inputStream = putOperation.openInputStream();
                }
                if (!error) {
                    updateValues = new ContentValues();
                    updateValues.put(BluetoothShare.CURRENT_BYTES, Integer.valueOf(0));
                    updateValues.put(BluetoothShare.STATUS, Integer.valueOf(BluetoothShare.STATUS_RUNNING));
                    this.mContext1.getContentResolver().update(contentUri, updateValues, null, null);
                }
                if (!error) {
                    int readLength;
                    int position = 0;
                    boolean okToProceed = false;
                    int outputBufferSize = putOperation.getMaxPacketSize();
                    byte[] buffer = new byte[outputBufferSize];
                    BufferedInputStream a = new BufferedInputStream(fileInfo.mInputStream, 16384);
                    if (!(BluetoothOppObexClientSession.this.mInterrupted || ((long) null) == fileInfo.mLength)) {
                        readLength = BluetoothOppObexClientSession.readFully(a, buffer, outputBufferSize);
                        BluetoothOppObexClientSession.this.mCallback.sendMessageDelayed(BluetoothOppObexClientSession.this.mCallback.obtainMessage(4), 50000);
                        synchronized (this) {
                            BluetoothOppObexClientSession.this.mWaitingForRemote = true;
                        }
                        outputStream.write(buffer, 0, readLength);
                        position = 0 + readLength;
                        if (((long) position) != fileInfo.mLength) {
                            BluetoothOppObexClientSession.this.mCallback.removeMessages(4);
                            synchronized (this) {
                                BluetoothOppObexClientSession.this.mWaitingForRemote = false;
                            }
                        } else {
                            outputStream.close();
                            BluetoothOppObexClientSession.this.mCallback.removeMessages(4);
                            synchronized (this) {
                                BluetoothOppObexClientSession.this.mWaitingForRemote = false;
                            }
                        }
                        responseCode = putOperation.getResponseCode();
                        if (responseCode == 144 || responseCode == 160) {
                            okToProceed = true;
                            updateValues = new ContentValues();
                            updateValues.put(BluetoothShare.CURRENT_BYTES, Integer.valueOf(position));
                            this.mContext1.getContentResolver().update(contentUri, updateValues, null, null);
                        } else {
                            Log.i(BluetoothOppObexClientSession.TAG, "Remote reject, Response code is " + responseCode);
                        }
                    }
                    while (!BluetoothOppObexClientSession.this.mInterrupted && okToProceed && ((long) position) != fileInfo.mLength) {
                        readLength = a.read(buffer, 0, outputBufferSize);
                        outputStream.write(buffer, 0, readLength);
                        responseCode = putOperation.getResponseCode();
                        if (responseCode == 144 || responseCode == 160) {
                            position += readLength;
                            updateValues = new ContentValues();
                            updateValues.put(BluetoothShare.CURRENT_BYTES, Integer.valueOf(position));
                            this.mContext1.getContentResolver().update(contentUri, updateValues, null, null);
                        } else {
                            okToProceed = false;
                        }
                    }
                    if (responseCode == 195 || responseCode == 198) {
                        Log.i(BluetoothOppObexClientSession.TAG, "Remote reject file " + fileInfo.mFileName + " length " + fileInfo.mLength);
                        status = BluetoothShare.STATUS_FORBIDDEN;
                    } else if (responseCode == 207) {
                        Log.i(BluetoothOppObexClientSession.TAG, "Remote reject file type " + fileInfo.mMimetype);
                        status = BluetoothShare.STATUS_NOT_ACCEPTABLE;
                    } else if (BluetoothOppObexClientSession.this.mInterrupted || ((long) position) != fileInfo.mLength) {
                        error = true;
                        status = BluetoothShare.STATUS_CANCELED;
                        putOperation.abort();
                        Log.i(BluetoothOppObexClientSession.TAG, "SendFile interrupted when send out file " + fileInfo.mFileName + " at " + position + " of " + fileInfo.mLength);
                    } else {
                        Log.i(BluetoothOppObexClientSession.TAG, "SendFile finished send out file " + fileInfo.mFileName + " length " + fileInfo.mLength);
                        outputStream.close();
                    }
                }
                try {
                    BluetoothOppUtility.closeSendFileInfo(this.mInfo.mUri);
                    if (!error) {
                        responseCode = putOperation.getResponseCode();
                        if (responseCode == -1) {
                            status = BluetoothShare.STATUS_CONNECTION_ERROR;
                        } else if (responseCode != 160) {
                            Log.i(BluetoothOppObexClientSession.TAG, "Response error code is " + responseCode);
                            status = BluetoothShare.STATUS_UNHANDLED_OBEX_CODE;
                            if (responseCode == 207) {
                                status = BluetoothShare.STATUS_NOT_ACCEPTABLE;
                            }
                            if (responseCode == 195 || responseCode == 198) {
                                status = BluetoothShare.STATUS_FORBIDDEN;
                            }
                        }
                    }
                    Constants.updateShareStatus(this.mContext1, this.mInfo.mId, status);
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (putOperation != null) {
                        putOperation.close();
                    }
                } catch (IOException e) {
                    Log.e(BluetoothOppObexClientSession.TAG, "Error when closing stream after send");
                }
            } catch (IOException e2) {
                handleSendException(e2.toString());
                try {
                    BluetoothOppUtility.closeSendFileInfo(this.mInfo.mUri);
                    if (!error) {
                        responseCode = putOperation.getResponseCode();
                        if (responseCode == -1) {
                            status = BluetoothShare.STATUS_CONNECTION_ERROR;
                        } else if (responseCode != 160) {
                            Log.i(BluetoothOppObexClientSession.TAG, "Response error code is " + responseCode);
                            status = BluetoothShare.STATUS_UNHANDLED_OBEX_CODE;
                            if (responseCode == 207) {
                                status = BluetoothShare.STATUS_NOT_ACCEPTABLE;
                            }
                            if (responseCode == 195 || responseCode == 198) {
                                status = BluetoothShare.STATUS_FORBIDDEN;
                            }
                        }
                    }
                    Constants.updateShareStatus(this.mContext1, this.mInfo.mId, status);
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (putOperation != null) {
                        putOperation.close();
                    }
                } catch (IOException e3) {
                    Log.e(BluetoothOppObexClientSession.TAG, "Error when closing stream after send");
                }
            } catch (NullPointerException e4) {
                handleSendException(e4.toString());
                try {
                    BluetoothOppUtility.closeSendFileInfo(this.mInfo.mUri);
                    if (!error) {
                        responseCode = putOperation.getResponseCode();
                        if (responseCode == -1) {
                            status = BluetoothShare.STATUS_CONNECTION_ERROR;
                        } else if (responseCode != 160) {
                            Log.i(BluetoothOppObexClientSession.TAG, "Response error code is " + responseCode);
                            status = BluetoothShare.STATUS_UNHANDLED_OBEX_CODE;
                            if (responseCode == 207) {
                                status = BluetoothShare.STATUS_NOT_ACCEPTABLE;
                            }
                            status = BluetoothShare.STATUS_FORBIDDEN;
                        }
                    }
                    Constants.updateShareStatus(this.mContext1, this.mInfo.mId, status);
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (putOperation != null) {
                        putOperation.close();
                    }
                } catch (IOException e5) {
                    Log.e(BluetoothOppObexClientSession.TAG, "Error when closing stream after send");
                }
            } catch (IndexOutOfBoundsException e6) {
                handleSendException(e6.toString());
                try {
                    BluetoothOppUtility.closeSendFileInfo(this.mInfo.mUri);
                    if (!error) {
                        responseCode = putOperation.getResponseCode();
                        if (responseCode == -1) {
                            status = BluetoothShare.STATUS_CONNECTION_ERROR;
                        } else if (responseCode != 160) {
                            Log.i(BluetoothOppObexClientSession.TAG, "Response error code is " + responseCode);
                            status = BluetoothShare.STATUS_UNHANDLED_OBEX_CODE;
                            if (responseCode == 207) {
                                status = BluetoothShare.STATUS_NOT_ACCEPTABLE;
                            }
                            status = BluetoothShare.STATUS_FORBIDDEN;
                        }
                    }
                    Constants.updateShareStatus(this.mContext1, this.mInfo.mId, status);
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (putOperation != null) {
                        putOperation.close();
                    }
                } catch (IOException e7) {
                    Log.e(BluetoothOppObexClientSession.TAG, "Error when closing stream after send");
                }
            }
            return status;
        }

        private void handleSendException(String exception) {
            Log.e(BluetoothOppObexClientSession.TAG, "Error when sending file: " + exception);
            Constants.updateShareStatus(this.mContext1, this.mInfo.mId, BluetoothShare.STATUS_OBEX_DATA_ERROR);
            BluetoothOppObexClientSession.this.mCallback.removeMessages(4);
        }

        public void interrupt() {
            super.interrupt();
            synchronized (this) {
                if (BluetoothOppObexClientSession.this.mWaitingForRemote) {
                    try {
                        this.mTransport1.close();
                    } catch (IOException e) {
                        Log.e(BluetoothOppObexClientSession.TAG, "mTransport.close error");
                    }
                    Message msg = Message.obtain(BluetoothOppObexClientSession.this.mCallback);
                    msg.what = 3;
                    if (this.mInfo != null) {
                        msg.obj = this.mInfo;
                    }
                    msg.sendToTarget();
                }
            }
        }
    }

    public BluetoothOppObexClientSession(Context context, ObexTransport transport) {
        if (transport == null) {
            throw new NullPointerException("transport is null");
        }
        this.mContext = context;
        this.mTransport = transport;
    }

    public void start(Handler handler, int numShares) {
        Log.d(TAG, "Start!");
        this.mCallback = handler;
        this.mThread = new ClientThread(this.mContext, this.mTransport, numShares);
        this.mThread.start();
    }

    public void stop() {
        Log.d(TAG, "Stop!");
        if (this.mThread != null) {
            this.mInterrupted = true;
            try {
                this.mThread.interrupt();
                this.mThread.join();
                this.mThread = null;
            } catch (InterruptedException e) {
            }
        }
        this.mCallback = null;
    }

    public void addShare(BluetoothOppShareInfo share) {
        this.mThread.addShare(share);
    }

    private static int readFully(InputStream is, byte[] buffer, int size) throws IOException {
        int done = 0;
        while (done < size) {
            int got = is.read(buffer, done, size - done);
            if (got <= 0) {
                break;
            }
            done += got;
        }
        return done;
    }

    public static void applyRemoteDeviceQuirks(HeaderSet request, String address, String filename) {
        if (address != null && address.startsWith("00:04:48")) {
            char[] c = filename.toCharArray();
            boolean firstDot = true;
            boolean modified = false;
            for (int i = c.length - 1; i >= 0; i--) {
                if (c[i] == '.') {
                    if (!firstDot) {
                        modified = true;
                        c[i] = '_';
                    }
                    firstDot = false;
                }
            }
            if (modified) {
                String newFilename = new String(c);
                request.setHeader(1, newFilename);
                Log.i(TAG, "Sending file \"" + filename + "\" as \"" + newFilename + "\" to workaround Poloroid filename quirk");
            }
        }
    }

    public void unblock() {
    }
}
