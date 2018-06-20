package com.android.bluetooth.opp;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/* compiled from: TestActivity */
class TestTcpListener {
    private static final int ACCEPT_WAIT_TIMEOUT = 5000;
    /* renamed from: D */
    private static final boolean f64D = true;
    public static final int DEFAULT_OPP_CHANNEL = 12;
    public static final int MSG_INCOMING_BTOPP_CONNECTION = 100;
    private static final String TAG = "BtOppRfcommListener";
    /* renamed from: V */
    private static final boolean f65V = false;
    private int mBtOppRfcommChannel;
    private Handler mCallback;
    private volatile boolean mInterrupted;
    private Thread mSocketAcceptThread;

    public TestTcpListener() {
        this(12);
    }

    public TestTcpListener(int channel) {
        this.mBtOppRfcommChannel = -1;
        this.mBtOppRfcommChannel = channel;
    }

    public synchronized boolean start(Handler callback) {
        if (this.mSocketAcceptThread == null) {
            this.mCallback = callback;
            this.mSocketAcceptThread = new Thread(TAG) {
                ServerSocket mServerSocket;

                public void run() {
                    Log.d(TestTcpListener.TAG, "RfcommSocket listen thread starting");
                    try {
                        this.mServerSocket = new ServerSocket(Constants.TCP_DEBUG_PORT, 1);
                    } catch (IOException e) {
                        Log.e(TestTcpListener.TAG, "Error listing on channel" + TestTcpListener.this.mBtOppRfcommChannel);
                        TestTcpListener.this.mInterrupted = true;
                    }
                    while (!TestTcpListener.this.mInterrupted) {
                        try {
                            this.mServerSocket.setSoTimeout(5000);
                            Socket clientSocket = this.mServerSocket.accept();
                            if (clientSocket != null) {
                                Log.d(TestTcpListener.TAG, "RfcommSocket connected!");
                                Log.d(TestTcpListener.TAG, "remote addr is " + clientSocket.getRemoteSocketAddress());
                                TestTcpTransport transport = new TestTcpTransport(clientSocket);
                                Message msg = Message.obtain();
                                msg.setTarget(TestTcpListener.this.mCallback);
                                msg.what = 100;
                                msg.obj = transport;
                                msg.sendToTarget();
                            }
                        } catch (SocketException e2) {
                            Log.e(TestTcpListener.TAG, "Error accept connection " + e2);
                        } catch (IOException e3) {
                            Log.e(TestTcpListener.TAG, "Error accept connection " + e3);
                        }
                        if (TestTcpListener.this.mInterrupted) {
                            Log.e(TestTcpListener.TAG, "socketAcceptThread thread was interrupted (2), exiting");
                        }
                    }
                    Log.d(TestTcpListener.TAG, "RfcommSocket listen thread finished");
                }
            };
            this.mInterrupted = false;
            this.mSocketAcceptThread.start();
        }
        return true;
    }

    public synchronized void stop() {
        if (this.mSocketAcceptThread != null) {
            Log.d(TAG, "stopping Connect Thread");
            this.mInterrupted = true;
            try {
                this.mSocketAcceptThread.interrupt();
                this.mSocketAcceptThread.join();
                this.mSocketAcceptThread = null;
                this.mCallback = null;
            } catch (InterruptedException e) {
            }
        }
    }
}
