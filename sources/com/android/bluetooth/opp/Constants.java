package com.android.bluetooth.opp;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.io.IOException;
import java.util.regex.Pattern;
import javax.obex.HeaderSet;

public class Constants {
    public static final String[] ACCEPTABLE_SHARE_INBOUND_TYPES = new String[]{"*/*"};
    public static final String[] ACCEPTABLE_SHARE_OUTBOUND_TYPES = new String[]{"image/*", "text/x-vcard"};
    public static final String ACTION_BT_OPP_TRANSFER_DONE = "android.nfc.handover.intent.action.TRANSFER_DONE";
    public static final String ACTION_BT_OPP_TRANSFER_PROGRESS = "android.nfc.handover.intent.action.TRANSFER_PROGRESS";
    public static final String ACTION_COMPLETE_HIDE = "android.btopp.intent.action.HIDE_COMPLETE";
    public static final String ACTION_HANDOVER_SEND = "android.nfc.handover.intent.action.HANDOVER_SEND";
    public static final String ACTION_HANDOVER_SEND_MULTIPLE = "android.nfc.handover.intent.action.HANDOVER_SEND_MULTIPLE";
    public static final String ACTION_HANDOVER_STARTED = "android.nfc.handover.intent.action.HANDOVER_STARTED";
    public static final String ACTION_HIDE = "android.btopp.intent.action.HIDE";
    public static final String ACTION_INCOMING_FILE_CONFIRM = "android.btopp.intent.action.CONFIRM";
    public static final String ACTION_LIST = "android.btopp.intent.action.LIST";
    public static final String ACTION_OPEN = "android.btopp.intent.action.OPEN";
    public static final String ACTION_OPEN_INBOUND_TRANSFER = "android.btopp.intent.action.OPEN_INBOUND";
    public static final String ACTION_OPEN_OUTBOUND_TRANSFER = "android.btopp.intent.action.OPEN_OUTBOUND";
    public static final String ACTION_OPEN_RECEIVED_FILES = "android.btopp.intent.action.OPEN_RECEIVED_FILES";
    public static final String ACTION_RETRY = "android.btopp.intent.action.RETRY";
    public static final String ACTION_STOP_HANDOVER = "android.btopp.intent.action.STOP_HANDOVER_TRANSFER";
    public static final String ACTION_WHITELIST_DEVICE = "android.btopp.intent.action.WHITELIST_DEVICE";
    public static final int BATCH_STATUS_FAILED = 3;
    public static final int BATCH_STATUS_FINISHED = 2;
    public static final int BATCH_STATUS_PENDING = 0;
    public static final int BATCH_STATUS_RUNNING = 1;
    public static final String BLUETOOTHOPP_CHANNEL_PREFERENCE = "btopp_channels";
    public static final String BLUETOOTHOPP_NAME_PREFERENCE = "btopp_names";
    public static final int COUNT_HEADER_UNAVAILABLE = -1;
    public static final boolean DEBUG = true;
    public static final String DEFAULT_STORE_SUBDIR = "/bluetooth";
    public static final int DIRECTION_BLUETOOTH_INCOMING = 0;
    public static final int DIRECTION_BLUETOOTH_OUTGOING = 1;
    public static final String EXTRA_BT_OPP_ADDRESS = "android.nfc.handover.intent.extra.ADDRESS";
    public static final String EXTRA_BT_OPP_OBJECT_COUNT = "android.nfc.handover.intent.extra.OBJECT_COUNT";
    public static final String EXTRA_BT_OPP_TRANSFER_DIRECTION = "android.nfc.handover.intent.extra.TRANSFER_DIRECTION";
    public static final String EXTRA_BT_OPP_TRANSFER_ID = "android.nfc.handover.intent.extra.TRANSFER_ID";
    public static final String EXTRA_BT_OPP_TRANSFER_MIMETYPE = "android.nfc.handover.intent.extra.TRANSFER_MIME_TYPE";
    public static final String EXTRA_BT_OPP_TRANSFER_PROGRESS = "android.nfc.handover.intent.extra.TRANSFER_PROGRESS";
    public static final String EXTRA_BT_OPP_TRANSFER_STATUS = "android.nfc.handover.intent.extra.TRANSFER_STATUS";
    public static final String EXTRA_BT_OPP_TRANSFER_URI = "android.nfc.handover.intent.extra.TRANSFER_URI";
    public static final String EXTRA_CONNECTION_HANDOVER = "com.android.intent.extra.CONNECTION_HANDOVER";
    public static final String EXTRA_SHOW_ALL_FILES = "android.btopp.intent.extra.SHOW_ALL";
    public static final String HANDOVER_STATUS_PERMISSION = "android.permission.NFC_HANDOVER_STATUS";
    public static final int HANDOVER_TRANSFER_STATUS_FAILURE = 1;
    public static final int HANDOVER_TRANSFER_STATUS_SUCCESS = 0;
    public static final int MAX_RECORDS_IN_DATABASE = 1000;
    public static final String MEDIA_SCANNED = "scanned";
    public static final int MEDIA_SCANNED_NOT_SCANNED = 0;
    public static final int MEDIA_SCANNED_SCANNED_FAILED = 2;
    public static final int MEDIA_SCANNED_SCANNED_OK = 1;
    public static final String TAG = "BluetoothOpp";
    public static final int TCP_DEBUG_PORT = 6500;
    public static final String THIS_PACKAGE_NAME = "com.android.bluetooth";
    public static final String[] UNACCEPTABLE_SHARE_INBOUND_TYPES = new String[]{"text/x-vcalendar"};
    public static final String[] UNACCEPTABLE_SHARE_OUTBOUND_TYPES = new String[]{"virus/*"};
    public static final boolean USE_EMULATOR_DEBUG = false;
    public static final boolean USE_TCP_DEBUG = false;
    public static final boolean USE_TCP_SIMPLE_SERVER = false;
    public static final boolean VERBOSE = false;
    public static String filename_SEQUENCE_SEPARATOR = "-";

    public static void updateShareStatus(Context context, int id, int status) {
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + id);
        ContentValues updateValues = new ContentValues();
        updateValues.put(BluetoothShare.STATUS, Integer.valueOf(status));
        context.getContentResolver().update(contentUri, updateValues, null, null);
        sendIntentIfCompleted(context, contentUri, status);
    }

    public static void sendIntentIfCompleted(Context context, Uri contentUri, int status) {
        if (BluetoothShare.isStatusCompleted(status)) {
            Intent intent = new Intent(BluetoothShare.TRANSFER_COMPLETED_ACTION);
            intent.setClassName("com.android.bluetooth", BluetoothOppReceiver.class.getName());
            intent.setDataAndNormalize(contentUri);
            context.sendBroadcast(intent);
        }
    }

    public static boolean mimeTypeMatches(String mimeType, String[] matchAgainst) {
        for (String matchType : matchAgainst) {
            if (mimeTypeMatches(mimeType, matchType)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mimeTypeMatches(String mimeType, String matchAgainst) {
        return Pattern.compile(matchAgainst.replaceAll("\\*", "\\.\\*"), 2).matcher(mimeType).matches();
    }

    public static void logHeader(HeaderSet hs) {
        Log.v(TAG, "Dumping HeaderSet " + hs.toString());
        try {
            Log.v(TAG, "COUNT : " + hs.getHeader(BluetoothShare.STATUS_RUNNING));
            Log.v(TAG, "NAME : " + hs.getHeader(1));
            Log.v(TAG, "TYPE : " + hs.getHeader(66));
            Log.v(TAG, "LENGTH : " + hs.getHeader(195));
            Log.v(TAG, "TIME_ISO_8601 : " + hs.getHeader(68));
            Log.v(TAG, "TIME_4_BYTE : " + hs.getHeader(196));
            Log.v(TAG, "DESCRIPTION : " + hs.getHeader(5));
            Log.v(TAG, "TARGET : " + hs.getHeader(70));
            Log.v(TAG, "HTTP : " + hs.getHeader(71));
            Log.v(TAG, "WHO : " + hs.getHeader(74));
            Log.v(TAG, "OBJECT_CLASS : " + hs.getHeader(79));
            Log.v(TAG, "APPLICATION_PARAMETER : " + hs.getHeader(76));
        } catch (IOException e) {
            Log.e(TAG, "dump HeaderSet error " + e);
        }
    }
}
