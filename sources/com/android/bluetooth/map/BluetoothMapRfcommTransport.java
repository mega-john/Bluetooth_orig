package com.android.bluetooth.map;

import android.bluetooth.BluetoothSocket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.obex.ObexTransport;

public class BluetoothMapRfcommTransport implements ObexTransport {
    private BluetoothSocket mSocket = null;

    public BluetoothMapRfcommTransport(BluetoothSocket rfs) {
        this.mSocket = rfs;
    }

    public void close() throws IOException {
        this.mSocket.close();
    }

    public DataInputStream openDataInputStream() throws IOException {
        return new DataInputStream(openInputStream());
    }

    public DataOutputStream openDataOutputStream() throws IOException {
        return new DataOutputStream(openOutputStream());
    }

    public InputStream openInputStream() throws IOException {
        return this.mSocket.getInputStream();
    }

    public OutputStream openOutputStream() throws IOException {
        return this.mSocket.getOutputStream();
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
        return true;
    }
}
