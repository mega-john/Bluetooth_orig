package com.android.bluetooth.opp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import javax.obex.ObexTransport;

/* compiled from: TestActivity */
class TestTcpTransport implements ObexTransport {
    /* renamed from: s */
    Socket f68s = null;

    public TestTcpTransport(Socket s) {
        this.f68s = s;
    }

    public void close() throws IOException {
        this.f68s.close();
    }

    public DataInputStream openDataInputStream() throws IOException {
        return new DataInputStream(openInputStream());
    }

    public DataOutputStream openDataOutputStream() throws IOException {
        return new DataOutputStream(openOutputStream());
    }

    public InputStream openInputStream() throws IOException {
        return this.f68s.getInputStream();
    }

    public OutputStream openOutputStream() throws IOException {
        return this.f68s.getOutputStream();
    }

    public void connect() throws IOException {
    }

    public void create() throws IOException {
    }

    public void disconnect() throws IOException {
    }

    public void listen() throws IOException {
    }

    public boolean isConnected() throws IOException {
        return this.f68s.isConnected();
    }
}
