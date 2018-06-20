package com.android.bluetooth.opp;

import android.util.Log;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import javax.obex.Authenticator;
import javax.obex.ServerRequestHandler;
import javax.obex.ServerSession;

/* compiled from: TestActivity */
class TestTcpSessionNotifier {
    private static final String TAG = "TestTcpSessionNotifier";
    Socket conn = null;
    ServerSocket server = null;

    public TestTcpSessionNotifier(int port) throws IOException {
        this.server = new ServerSocket(port);
    }

    public ServerSession acceptAndOpen(ServerRequestHandler handler, Authenticator auth) throws IOException {
        try {
            this.conn = this.server.accept();
        } catch (Exception e) {
            Log.v(TAG, "ex");
        }
        return new ServerSession(new TestTcpTransport(this.conn), handler, auth);
    }

    public ServerSession acceptAndOpen(ServerRequestHandler handler) throws IOException {
        return acceptAndOpen(handler, null);
    }
}
