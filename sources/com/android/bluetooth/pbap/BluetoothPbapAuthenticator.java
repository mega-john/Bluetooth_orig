package com.android.bluetooth.pbap;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import javax.obex.Authenticator;
import javax.obex.PasswordAuthentication;

public class BluetoothPbapAuthenticator implements Authenticator {
    private static final String TAG = "BluetoothPbapAuthenticator";
    private boolean mAuthCancelled = false;
    private Handler mCallback;
    private boolean mChallenged = false;
    private String mSessionKey = null;

    public BluetoothPbapAuthenticator(Handler callback) {
        this.mCallback = callback;
    }

    public final synchronized void setChallenged(boolean bool) {
        this.mChallenged = bool;
    }

    public final synchronized void setCancelled(boolean bool) {
        this.mAuthCancelled = bool;
    }

    public final synchronized void setSessionKey(String string) {
        this.mSessionKey = string;
    }

    private void waitUserConfirmation() {
        Message msg = Message.obtain(this.mCallback);
        msg.what = 5003;
        msg.sendToTarget();
        synchronized (this) {
            while (!this.mChallenged && !this.mAuthCancelled) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting on isChalled");
                }
            }
        }
    }

    public PasswordAuthentication onAuthenticationChallenge(String description, boolean isUserIdRequired, boolean isFullAccess) {
        waitUserConfirmation();
        if (this.mSessionKey.trim().length() != 0) {
            return new PasswordAuthentication(null, this.mSessionKey.getBytes());
        }
        return null;
    }

    public byte[] onAuthenticationResponse(byte[] userName) {
        return null;
    }
}
