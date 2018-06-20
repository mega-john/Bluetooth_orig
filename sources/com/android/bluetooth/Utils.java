package com.android.bluetooth;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.ParcelUuid;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.hfp.BluetoothCmeError;
import com.android.bluetooth.opp.Constants;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class Utils {
    static final int BD_ADDR_LEN = 6;
    static final int BD_UUID_LEN = 16;
    private static final int MICROS_PER_UNIT = 625;
    private static final String TAG = "BluetoothUtils";

    public static String getAddressStringFromByte(byte[] address) {
        if (address == null || address.length != 6) {
            return null;
        }
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X", new Object[]{Byte.valueOf(address[0]), Byte.valueOf(address[1]), Byte.valueOf(address[2]), Byte.valueOf(address[3]), Byte.valueOf(address[4]), Byte.valueOf(address[5])});
    }

    public static byte[] getByteAddress(BluetoothDevice device) {
        return getBytesFromAddress(device.getAddress());
    }

    public static byte[] getBytesFromAddress(String address) {
        int j = 0;
        byte[] output = new byte[6];
        int i = 0;
        while (i < address.length()) {
            if (address.charAt(i) != ':') {
                output[j] = (byte) Integer.parseInt(address.substring(i, i + 2), 16);
                j++;
                i++;
            }
            i++;
        }
        return output;
    }

    public static int byteArrayToInt(byte[] valueBuf) {
        return byteArrayToInt(valueBuf, 0);
    }

    public static short byteArrayToShort(byte[] valueBuf) {
        ByteBuffer converter = ByteBuffer.wrap(valueBuf);
        converter.order(ByteOrder.nativeOrder());
        return converter.getShort();
    }

    public static int byteArrayToInt(byte[] valueBuf, int offset) {
        ByteBuffer converter = ByteBuffer.wrap(valueBuf);
        converter.order(ByteOrder.nativeOrder());
        return converter.getInt(offset);
    }

    public static byte[] intToByteArray(int value) {
        ByteBuffer converter = ByteBuffer.allocate(4);
        converter.order(ByteOrder.nativeOrder());
        converter.putInt(value);
        return converter.array();
    }

    public static byte[] uuidToByteArray(ParcelUuid pUuid) {
        ByteBuffer converter = ByteBuffer.allocate(16);
        converter.order(ByteOrder.BIG_ENDIAN);
        UUID uuid = pUuid.getUuid();
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        converter.putLong(msb);
        converter.putLong(8, lsb);
        return converter.array();
    }

    public static byte[] uuidsToByteArray(ParcelUuid[] uuids) {
        ByteBuffer converter = ByteBuffer.allocate(uuids.length * 16);
        converter.order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < uuids.length; i++) {
            UUID uuid = uuids[i].getUuid();
            long msb = uuid.getMostSignificantBits();
            long lsb = uuid.getLeastSignificantBits();
            converter.putLong(i * 16, msb);
            converter.putLong((i * 16) + 8, lsb);
        }
        return converter.array();
    }

    public static ParcelUuid[] byteArrayToUuid(byte[] val) {
        int numUuids = val.length / 16;
        ParcelUuid[] puuids = new ParcelUuid[numUuids];
        int offset = 0;
        ByteBuffer converter = ByteBuffer.wrap(val);
        converter.order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < numUuids; i++) {
            puuids[i] = new ParcelUuid(new UUID(converter.getLong(offset), converter.getLong(offset + 8)));
            offset += 16;
        }
        return puuids;
    }

    public static String debugGetAdapterStateString(int state) {
        switch (state) {
            case 10:
                return "STATE_OFF";
            case 11:
                return "STATE_TURNING_ON";
            case 12:
                return "STATE_ON";
            case BluetoothCmeError.SIM_FAILURE /*13*/:
                return "STATE_TURNING_OFF";
            default:
                return "UNKNOWN";
        }
    }

    public static void copyStream(InputStream is, OutputStream os, int bufferSize) throws IOException {
        if (is != null && os != null) {
            byte[] buffer = new byte[bufferSize];
            while (true) {
                int bytesRead = is.read(buffer);
                if (bytesRead >= 0) {
                    os.write(buffer, 0, bytesRead);
                } else {
                    return;
                }
            }
        }
    }

    public static void safeCloseStream(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (Throwable t) {
                Log.d(TAG, "Error closing stream", t);
            }
        }
    }

    public static void safeCloseStream(OutputStream os) {
        if (os != null) {
            try {
                os.close();
            } catch (Throwable t) {
                Log.d(TAG, "Error closing stream", t);
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public static boolean checkCaller() {
        int callingUser = UserHandle.getCallingUserId();
        int callingUid = Binder.getCallingUid();
        long ident = Binder.clearCallingIdentity();
        try {
            boolean ok;
            if (ActivityManager.getCurrentUser() == callingUser) {
                ok = true;
            } else {
                ok = false;
            }
            if (!ok) {
                if (ActivityThread.getPackageManager().getPackageUid("com.android.systemui", 0) == callingUid || Constants.MAX_RECORDS_IN_DATABASE == callingUid) {
                    ok = true;
                } else {
                    ok = false;
                }
            }
            Binder.restoreCallingIdentity(ident);
            return ok;
        } catch (Exception ex) {
            Log.e(TAG, "checkIfCallerIsSelfOrForegroundUser: Exception ex=" + ex);
            return false;
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(ident);
        }
        return false;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public static boolean checkCallerAllowManagedProfiles(Context mContext) {
        if (mContext == null) {
            return checkCaller();
        }
        int callingUser = UserHandle.getCallingUserId();
        int callingUid = Binder.getCallingUid();
        long ident = Binder.clearCallingIdentity();
        try {
            UserInfo ui = ((UserManager) mContext.getSystemService("user")).getProfileParent(callingUser);
            int parentUser = ui != null ? ui.id : -10000;
            int foregroundUser = ActivityManager.getCurrentUser();
            boolean ok = foregroundUser == callingUser || foregroundUser == parentUser;
            if (!ok) {
                ok = ActivityThread.getPackageManager().getPackageUid("com.android.systemui", 0) == callingUid || Constants.MAX_RECORDS_IN_DATABASE == callingUid;
            }
            Binder.restoreCallingIdentity(ident);
            return ok;
        } catch (Exception ex) {
            Log.e(TAG, "checkCallerAllowManagedProfiles: Exception ex=" + ex);
            return false;
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(ident);
        }
        return false;
    }

    public static void enforceAdminPermission(ContextWrapper context) {
        context.enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
    }

    public static int millsToUnit(int milliseconds) {
        return (int) (TimeUnit.MILLISECONDS.toMicros((long) milliseconds) / 625);
    }
}
