package com.android.bluetooth.opp;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ServerRequestHandler;

/* compiled from: TestActivity */
class TestTcpServer extends ServerRequestHandler implements Runnable {
    private static final String TAG = "ServerRequestHandler";
    /* renamed from: V */
    private static final boolean f66V = false;
    static final int port = 6500;
    /* renamed from: a */
    public boolean f67a = f66V;

    public void run() {
        try {
            updateStatus("[server:] listen on port 6500");
            TestTcpSessionNotifier rsn = new TestTcpSessionNotifier(6500);
            updateStatus("[server:] Now waiting for a client to connect");
            rsn.acceptAndOpen(this);
            updateStatus("[server:] A client is now connected");
        } catch (Exception ex) {
            updateStatus("[server:] Caught the error: " + ex);
        }
    }

    public TestTcpServer() {
        updateStatus("enter construtor of TcpServer");
    }

    public int onConnect(HeaderSet request, HeaderSet reply) {
        updateStatus("[server:] The client has created an OBEX session");
        synchronized (this) {
            while (!this.f67a) {
                try {
                    wait(500);
                } catch (InterruptedException e) {
                }
            }
        }
        updateStatus("[server:] we accpet the seesion");
        return 160;
    }

    public int onPut(Operation op) {
        Exception e;
        FileOutputStream fos = null;
        try {
            InputStream is = op.openInputStream();
            updateStatus("Got data bytes " + is.available() + " name " + op.getReceivedHeader().getHeader(1) + " type " + op.getType());
            File f = new File((String) op.getReceivedHeader().getHeader(1));
            FileOutputStream fos2 = new FileOutputStream(f);
            try {
                byte[] b = new byte[Constants.MAX_RECORDS_IN_DATABASE];
                while (is.available() > 0) {
                    int len = is.read(b);
                    if (len > 0) {
                        fos2.write(b, 0, len);
                    }
                }
                fos2.close();
                is.close();
                updateStatus("[server:] Wrote data to " + f.getAbsolutePath());
                fos = fos2;
            } catch (Exception e2) {
                e = e2;
                fos = fos2;
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
                e.printStackTrace();
                return 160;
            }
        } catch (Exception e3) {
            e = e3;
            if (fos != null) {
                fos.close();
            }
            e.printStackTrace();
            return 160;
        }
        return 160;
    }

    public void onDisconnect(HeaderSet req, HeaderSet resp) {
        updateStatus("[server:] The client has disconnected the OBEX session");
    }

    public void updateStatus(String message) {
        Log.v(TAG, "\n" + message);
    }

    public void onAuthenticationFailure(byte[] userName) {
    }

    public int onSetPath(HeaderSet request, HeaderSet reply, boolean backup, boolean create) {
        return 209;
    }

    public int onDelete(HeaderSet request, HeaderSet reply) {
        return 209;
    }

    public int onGet(Operation op) {
        return 209;
    }
}
