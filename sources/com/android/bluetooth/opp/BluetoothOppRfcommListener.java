package com.android.bluetooth.opp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothUuid;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import java.io.IOException;
import java.net.ServerSocket;

public class BluetoothOppRfcommListener {
    private static final int CREATE_RETRY_TIME = 10;
    public static final int MSG_INCOMING_BTOPP_CONNECTION = 100;
    private static final String TAG = "BtOppRfcommListener";
    /* renamed from: V */
    private static final boolean f52V = false;
    private final BluetoothAdapter mAdapter;
    private BluetoothServerSocket mBtServerSocket = null;
    private Handler mCallback;
    private volatile boolean mInterrupted;
    private Thread mSocketAcceptThread;
    private ServerSocket mTcpServerSocket = null;

    public BluetoothOppRfcommListener(BluetoothAdapter adapter) {
        this.mAdapter = adapter;
    }

    public synchronized boolean start(Handler callback) {
        if (this.mSocketAcceptThread == null) {
            this.mCallback = callback;
            this.mSocketAcceptThread = new Thread(TAG) {
                public void run() {
                    boolean serverOK = true;
                    for (int i = 0; i < 10 && !BluetoothOppRfcommListener.this.mInterrupted; i++) {
                        try {
                            BluetoothOppRfcommListener.this.mBtServerSocket = BluetoothOppRfcommListener.this.mAdapter.listenUsingInsecureRfcommWithServiceRecord("OBEX Object Push", BluetoothUuid.ObexObjectPush.getUuid());
                        } catch (IOException e1) {
                            Log.e(BluetoothOppRfcommListener.TAG, "Error create RfcommServerSocket " + e1);
                            serverOK = false;
                        }
                        if (serverOK) {
                            break;
                        }
                        synchronized (this) {
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                Log.e(BluetoothOppRfcommListener.TAG, "socketAcceptThread thread was interrupted (3)");
                                BluetoothOppRfcommListener.this.mInterrupted = true;
                            }
                        }
                    }
                    if (!serverOK) {
                        Log.e(BluetoothOppRfcommListener.TAG, "Error start listening after 10 try");
                        BluetoothOppRfcommListener.this.mInterrupted = true;
                    }
                    if (!BluetoothOppRfcommListener.this.mInterrupted) {
                        Log.i(BluetoothOppRfcommListener.TAG, "Accept thread started.");
                    }
                    while (!BluetoothOppRfcommListener.this.mInterrupted) {
                        try {
                            BluetoothServerSocket sSocket;
                            if (BluetoothOppRfcommListener.this.mBtServerSocket == null) {
                                sSocket = BluetoothOppRfcommListener.this.mBtServerSocket;
                            } else {
                                sSocket = BluetoothOppRfcommListener.this.mBtServerSocket;
                            }
                            if (sSocket == null) {
                                BluetoothOppRfcommListener.this.mInterrupted = true;
                            } else {
                                BluetoothOppRfcommTransport transport = new BluetoothOppRfcommTransport(sSocket.accept());
                                Message msg = Message.obtain();
                                msg.setTarget(BluetoothOppRfcommListener.this.mCallback);
                                msg.what = 100;
                                msg.obj = transport;
                                msg.sendToTarget();
                            }
                        } catch (IOException e2) {
                            Log.e(BluetoothOppRfcommListener.TAG, "Error accept connection " + e2);
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e3) {
                            }
                        }
                    }
                    Log.i(BluetoothOppRfcommListener.TAG, "BluetoothSocket listen thread finished");
                }
            };
            this.mInterrupted = false;
            this.mSocketAcceptThread.start();
        }
        return true;
    }

    public synchronized void stop() {
        if (this.mSocketAcceptThread != null) {
            Log.i(TAG, "stopping Accept Thread");
            this.mInterrupted = true;
            if (this.mBtServerSocket != null) {
                try {
                    this.mBtServerSocket.close();
                    this.mBtServerSocket = null;
                } catch (IOException e) {
                    Log.e(TAG, "Error close mBtServerSocket");
                }
            }
            try {
                this.mSocketAcceptThread.interrupt();
                this.mSocketAcceptThread.join();
                this.mSocketAcceptThread = null;
                this.mCallback = null;
            } catch (InterruptedException e2) {
            }
        }
    }
}
