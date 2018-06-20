package com.android.bluetooth.pbap;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;
import com.android.bluetooth.C0000R;
import com.android.vcard.VCardBuilder;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardConstants;
import com.android.vcard.VCardUtils;
import java.util.Arrays;

public class BluetoothPbapCallLogComposer {
    private static final int CALLER_NAME_COLUMN_INDEX = 3;
    private static final int CALLER_NUMBERLABEL_COLUMN_INDEX = 5;
    private static final int CALLER_NUMBERTYPE_COLUMN_INDEX = 4;
    private static final int CALL_TYPE_COLUMN_INDEX = 2;
    private static final int DATE_COLUMN_INDEX = 1;
    private static final String FAILURE_REASON_FAILED_TO_GET_DATABASE_INFO = "Failed to get database information";
    private static final String FAILURE_REASON_NOT_INITIALIZED = "The vCard composer object is not correctly initialized";
    private static final String FAILURE_REASON_NO_ENTRY = "There's no exportable in the database";
    private static final String FAILURE_REASON_UNSUPPORTED_URI = "The Uri vCard composer received is not supported by the composer.";
    private static final String NO_ERROR = "No error";
    private static final int NUMBER_COLUMN_INDEX = 0;
    private static final int NUMBER_PRESENTATION_COLUMN_INDEX = 6;
    private static final String TAG = "CallLogComposer";
    private static final String VCARD_PROPERTY_CALLTYPE_INCOMING = "RECEIVED";
    private static final String VCARD_PROPERTY_CALLTYPE_MISSED = "MISSED";
    private static final String VCARD_PROPERTY_CALLTYPE_OUTGOING = "DIALED";
    private static final String VCARD_PROPERTY_X_TIMESTAMP = "X-IRMC-CALL-DATETIME";
    private static final String[] sCallLogProjection = new String[]{"number", "date", "type", "name", "numbertype", "numberlabel", "presentation"};
    private ContentResolver mContentResolver;
    private final Context mContext;
    private Cursor mCursor;
    private String mErrorReason = "No error";
    private boolean mTerminateIsCalled;

    public BluetoothPbapCallLogComposer(Context context) {
        this.mContext = context;
        this.mContentResolver = context.getContentResolver();
    }

    public boolean init(Uri contentUri, String selection, String[] selectionArgs, String sortOrder) {
        if (Calls.CONTENT_URI.equals(contentUri)) {
            this.mCursor = this.mContentResolver.query(contentUri, sCallLogProjection, selection, selectionArgs, sortOrder);
            if (this.mCursor == null) {
                this.mErrorReason = "Failed to get database information";
                return false;
            } else if (this.mCursor.getCount() != 0 && this.mCursor.moveToFirst()) {
                return true;
            } else {
                try {
                    this.mCursor.close();
                } catch (SQLiteException e) {
                    Log.e(TAG, "SQLiteException on Cursor#close(): " + e.getMessage());
                } finally {
                    this.mErrorReason = "There's no exportable in the database";
                    this.mCursor = null;
                }
                return false;
            }
        }
        this.mErrorReason = "The Uri vCard composer received is not supported by the composer.";
        return false;
    }

    public String createOneEntry(boolean vcardVer21) {
        if (this.mCursor == null || this.mCursor.isAfterLast()) {
            this.mErrorReason = "The vCard composer object is not correctly initialized";
            return null;
        }
        try {
            String createOneCallLogEntryInternal = createOneCallLogEntryInternal(vcardVer21);
            return createOneCallLogEntryInternal;
        } finally {
            this.mCursor.moveToNext();
        }
    }

    private String createOneCallLogEntryInternal(boolean vcardVer21) {
        boolean needCharset = true;
        VCardBuilder builder = new VCardBuilder((vcardVer21 ? VCardConfig.VCARD_TYPE_V21_GENERIC : VCardConfig.VCARD_TYPE_V30_GENERIC) | VCardConfig.FLAG_REFRAIN_PHONE_NUMBER_FORMATTING);
        String name = this.mCursor.getString(3);
        String number = this.mCursor.getString(0);
        int numberPresentation = this.mCursor.getInt(6);
        if (TextUtils.isEmpty(name)) {
            name = "";
        }
        if (numberPresentation != 1) {
            name = "";
            number = this.mContext.getString(C0000R.string.unknownNumber);
        }
        if (VCardUtils.containsOnlyPrintableAscii(name)) {
            needCharset = false;
        }
        builder.appendLine(VCardConstants.PROPERTY_FN, name, needCharset, false);
        builder.appendLine(VCardConstants.PROPERTY_N, name, needCharset, false);
        int type = this.mCursor.getInt(4);
        String label = this.mCursor.getString(5);
        if (TextUtils.isEmpty(label)) {
            label = Integer.toString(type);
        }
        builder.appendTelLine(Integer.valueOf(type), label, number, false);
        tryAppendCallHistoryTimeStampField(builder);
        return builder.toString();
    }

    public String composeVCardForPhoneOwnNumber(int phonetype, String phoneName, String phoneNumber, boolean vcardVer21) {
        VCardBuilder builder = new VCardBuilder((vcardVer21 ? VCardConfig.VCARD_TYPE_V21_GENERIC : VCardConfig.VCARD_TYPE_V30_GENERIC) | VCardConfig.FLAG_REFRAIN_PHONE_NUMBER_FORMATTING);
        boolean needCharset = false;
        if (!VCardUtils.containsOnlyPrintableAscii(phoneName)) {
            needCharset = true;
        }
        builder.appendLine(VCardConstants.PROPERTY_FN, phoneName, needCharset, false);
        builder.appendLine(VCardConstants.PROPERTY_N, phoneName, needCharset, false);
        if (!TextUtils.isEmpty(phoneNumber)) {
            builder.appendTelLine(Integer.valueOf(phonetype), Integer.toString(phonetype), phoneNumber, false);
        }
        return builder.toString();
    }

    private final String toRfc2455Format(long millSecs) {
        Time startDate = new Time();
        startDate.set(millSecs);
        return startDate.format2445();
    }

    private void tryAppendCallHistoryTimeStampField(VCardBuilder builder) {
        String callLogTypeStr;
        switch (this.mCursor.getInt(2)) {
            case 1:
                callLogTypeStr = VCARD_PROPERTY_CALLTYPE_INCOMING;
                break;
            case 2:
                callLogTypeStr = VCARD_PROPERTY_CALLTYPE_OUTGOING;
                break;
            case 3:
                callLogTypeStr = VCARD_PROPERTY_CALLTYPE_MISSED;
                break;
            default:
                Log.w(TAG, "Call log type not correct.");
                return;
        }
        long dateAsLong = this.mCursor.getLong(1);
        builder.appendLine(VCARD_PROPERTY_X_TIMESTAMP, Arrays.asList(new String[]{callLogTypeStr}), toRfc2455Format(dateAsLong));
    }

    public void terminate() {
        if (this.mCursor != null) {
            try {
                this.mCursor.close();
            } catch (SQLiteException e) {
                Log.e(TAG, "SQLiteException on Cursor#close(): " + e.getMessage());
            }
            this.mCursor = null;
        }
        this.mTerminateIsCalled = true;
    }

    public void finalize() {
        if (!this.mTerminateIsCalled) {
            terminate();
        }
    }

    public int getCount() {
        if (this.mCursor == null) {
            return 0;
        }
        return this.mCursor.getCount();
    }

    public boolean isAfterLast() {
        if (this.mCursor == null) {
            return false;
        }
        return this.mCursor.isAfterLast();
    }

    public String getErrorReason() {
        return this.mErrorReason;
    }
}
