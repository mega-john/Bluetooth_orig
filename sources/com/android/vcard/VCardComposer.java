package com.android.vcard;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.text.TextUtils;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VCardComposer {
    private static final boolean DEBUG = false;
    public static final String FAILURE_REASON_FAILED_TO_GET_DATABASE_INFO = "Failed to get database information";
    public static final String FAILURE_REASON_NOT_INITIALIZED = "The vCard composer object is not correctly initialized";
    public static final String FAILURE_REASON_NO_ENTRY = "There's no exportable in the database";
    public static final String FAILURE_REASON_UNSUPPORTED_URI = "The Uri vCard composer received is not supported by the composer.";
    private static final String LOG_TAG = "VCardComposer";
    public static final String NO_ERROR = "No error";
    private static final String SHIFT_JIS = "SHIFT_JIS";
    private static final String UTF_8 = "UTF-8";
    private static final String[] sContactsProjection = new String[]{"_id"};
    private static final Map<Integer, String> sImMap = new HashMap();
    private final String mCharset;
    private final ContentResolver mContentResolver;
    private Uri mContentUriForRawContactsEntity;
    private Cursor mCursor;
    private boolean mCursorSuppliedFromOutside;
    private String mErrorReason;
    private boolean mFirstVCardEmittedInDoCoMoCase;
    private int mIdColumn;
    private boolean mInitDone;
    private final boolean mIsDoCoMo;
    private VCardPhoneNumberTranslationCallback mPhoneTranslationCallback;
    private boolean mTerminateCalled;
    private final int mVCardType;

    static {
        sImMap.put(Integer.valueOf(0), VCardConstants.PROPERTY_X_AIM);
        sImMap.put(Integer.valueOf(1), VCardConstants.PROPERTY_X_MSN);
        sImMap.put(Integer.valueOf(2), VCardConstants.PROPERTY_X_YAHOO);
        sImMap.put(Integer.valueOf(6), VCardConstants.PROPERTY_X_ICQ);
        sImMap.put(Integer.valueOf(7), VCardConstants.PROPERTY_X_JABBER);
        sImMap.put(Integer.valueOf(3), VCardConstants.PROPERTY_X_SKYPE_USERNAME);
    }

    public VCardComposer(Context context) {
        this(context, VCardConfig.VCARD_TYPE_DEFAULT, null, true);
    }

    public VCardComposer(Context context, int vcardType) {
        this(context, vcardType, null, true);
    }

    public VCardComposer(Context context, int vcardType, String charset) {
        this(context, vcardType, charset, true);
    }

    public VCardComposer(Context context, int vcardType, boolean careHandlerErrors) {
        this(context, vcardType, null, careHandlerErrors);
    }

    public VCardComposer(Context context, int vcardType, String charset, boolean careHandlerErrors) {
        this(context, context.getContentResolver(), vcardType, charset, careHandlerErrors);
    }

    public VCardComposer(Context context, ContentResolver resolver, int vcardType, String charset, boolean careHandlerErrors) {
        boolean shouldAppendCharsetParam = true;
        this.mErrorReason = NO_ERROR;
        this.mTerminateCalled = true;
        this.mVCardType = vcardType;
        this.mContentResolver = resolver;
        this.mIsDoCoMo = VCardConfig.isDoCoMo(vcardType);
        if (TextUtils.isEmpty(charset)) {
            charset = "UTF-8";
        }
        if (VCardConfig.isVersion30(vcardType) && "UTF-8".equalsIgnoreCase(charset)) {
            shouldAppendCharsetParam = DEBUG;
        }
        if (this.mIsDoCoMo || shouldAppendCharsetParam) {
            if (SHIFT_JIS.equalsIgnoreCase(charset)) {
                this.mCharset = charset;
            } else if (TextUtils.isEmpty(charset)) {
                this.mCharset = SHIFT_JIS;
            } else {
                this.mCharset = charset;
            }
        } else if (TextUtils.isEmpty(charset)) {
            this.mCharset = "UTF-8";
        } else {
            this.mCharset = charset;
        }
        Log.d(LOG_TAG, "Use the charset \"" + this.mCharset + "\"");
    }

    public boolean init() {
        return init(null, null);
    }

    @Deprecated
    public boolean initWithRawContactsEntityUri(Uri contentUriForRawContactsEntity) {
        return init(Contacts.CONTENT_URI, sContactsProjection, null, null, null, contentUriForRawContactsEntity);
    }

    public boolean init(String selection, String[] selectionArgs) {
        return init(Contacts.CONTENT_URI, sContactsProjection, selection, selectionArgs, null, null);
    }

    public boolean init(Uri contentUri, String selection, String[] selectionArgs, String sortOrder) {
        return init(contentUri, sContactsProjection, selection, selectionArgs, sortOrder, null);
    }

    public boolean init(Uri contentUri, String selection, String[] selectionArgs, String sortOrder, Uri contentUriForRawContactsEntity) {
        return init(contentUri, sContactsProjection, selection, selectionArgs, sortOrder, contentUriForRawContactsEntity);
    }

    public boolean init(Uri contentUri, String[] projection, String selection, String[] selectionArgs, String sortOrder, Uri contentUriForRawContactsEntity) {
        if (!"com.android.contacts".equals(contentUri.getAuthority())) {
            this.mErrorReason = FAILURE_REASON_UNSUPPORTED_URI;
            return DEBUG;
        } else if (initInterFirstPart(contentUriForRawContactsEntity) && initInterCursorCreationPart(contentUri, projection, selection, selectionArgs, sortOrder) && initInterMainPart()) {
            return initInterLastPart();
        } else {
            return DEBUG;
        }
    }

    public boolean init(Cursor cursor) {
        if (!initInterFirstPart(null)) {
            return DEBUG;
        }
        this.mCursorSuppliedFromOutside = true;
        this.mCursor = cursor;
        if (initInterMainPart()) {
            return initInterLastPart();
        }
        return DEBUG;
    }

    private boolean initInterFirstPart(Uri contentUriForRawContactsEntity) {
        if (contentUriForRawContactsEntity == null) {
            contentUriForRawContactsEntity = RawContactsEntity.CONTENT_URI;
        }
        this.mContentUriForRawContactsEntity = contentUriForRawContactsEntity;
        if (!this.mInitDone) {
            return true;
        }
        Log.e(LOG_TAG, "init() is already called");
        return DEBUG;
    }

    private boolean initInterCursorCreationPart(Uri contentUri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        this.mCursorSuppliedFromOutside = DEBUG;
        this.mCursor = this.mContentResolver.query(contentUri, projection, selection, selectionArgs, sortOrder);
        if (this.mCursor != null) {
            return true;
        }
        Log.e(LOG_TAG, String.format("Cursor became null unexpectedly", new Object[0]));
        this.mErrorReason = FAILURE_REASON_FAILED_TO_GET_DATABASE_INFO;
        return DEBUG;
    }

    private boolean initInterMainPart() {
        if (this.mCursor.getCount() == 0 || !this.mCursor.moveToFirst()) {
            closeCursorIfAppropriate();
            return DEBUG;
        }
        this.mIdColumn = this.mCursor.getColumnIndex("_id");
        if (this.mIdColumn >= 0) {
            return true;
        }
        return DEBUG;
    }

    private boolean initInterLastPart() {
        this.mInitDone = true;
        this.mTerminateCalled = DEBUG;
        return true;
    }

    public String createOneEntry() {
        return createOneEntry(null);
    }

    public String createOneEntry(Method getEntityIteratorMethod) {
        if (this.mIsDoCoMo && !this.mFirstVCardEmittedInDoCoMoCase) {
            this.mFirstVCardEmittedInDoCoMoCase = true;
        }
        String vcard = createOneEntryInternal(this.mCursor.getString(this.mIdColumn), getEntityIteratorMethod);
        if (!this.mCursor.moveToNext()) {
            Log.e(LOG_TAG, "Cursor#moveToNext() returned false");
        }
        return vcard;
    }

    private java.lang.String createOneEntryInternal(java.lang.String r18, java.lang.reflect.Method r19) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxOverflowException: Regions stack size limit reached
	at jadx.core.utils.ErrorsCounter.addError(ErrorsCounter.java:37)
	at jadx.core.utils.ErrorsCounter.methodError(ErrorsCounter.java:61)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:56)
	at jadx.core.ProcessClass.process(ProcessClass.java:39)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:282)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
*/
        /*
        r17 = this;
        r9 = new java.util.HashMap;
        r9.<init>();
        r12 = 0;
        r0 = r17;	 Catch:{ all -> 0x0067 }
        r2 = r0.mContentUriForRawContactsEntity;	 Catch:{ all -> 0x0067 }
        r16 = "contact_id=?";	 Catch:{ all -> 0x0067 }
        r1 = 1;	 Catch:{ all -> 0x0067 }
        r5 = new java.lang.String[r1];	 Catch:{ all -> 0x0067 }
        r1 = 0;	 Catch:{ all -> 0x0067 }
        r5[r1] = r18;	 Catch:{ all -> 0x0067 }
        if (r19 == 0) goto L_0x009c;
    L_0x0014:
        r1 = 0;
        r3 = 5;
        r3 = new java.lang.Object[r3];	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r4 = 0;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r0 = r17;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r6 = r0.mContentResolver;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r3[r4] = r6;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r4 = 1;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r3[r4] = r2;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r4 = 2;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r6 = "contact_id=?";	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r3[r4] = r6;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r4 = 3;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r3[r4] = r5;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r4 = 4;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r6 = 0;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r3[r4] = r6;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r0 = r19;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r1 = r0.invoke(r1, r3);	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r0 = r1;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r0 = (android.content.EntityIterator) r0;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
        r12 = r0;	 Catch:{ IllegalArgumentException -> 0x0049, IllegalAccessException -> 0x006e, InvocationTargetException -> 0x008c }
    L_0x0038:
        if (r12 != 0) goto L_0x00ad;
    L_0x003a:
        r1 = "VCardComposer";	 Catch:{ all -> 0x0067 }
        r3 = "EntityIterator is null";	 Catch:{ all -> 0x0067 }
        android.util.Log.e(r1, r3);	 Catch:{ all -> 0x0067 }
        r1 = "";	 Catch:{ all -> 0x0067 }
        if (r12 == 0) goto L_0x0048;
    L_0x0045:
        r12.close();
    L_0x0048:
        return r1;
    L_0x0049:
        r10 = move-exception;
        r1 = "VCardComposer";	 Catch:{ all -> 0x0067 }
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0067 }
        r3.<init>();	 Catch:{ all -> 0x0067 }
        r4 = "IllegalArgumentException has been thrown: ";	 Catch:{ all -> 0x0067 }
        r3 = r3.append(r4);	 Catch:{ all -> 0x0067 }
        r4 = r10.getMessage();	 Catch:{ all -> 0x0067 }
        r3 = r3.append(r4);	 Catch:{ all -> 0x0067 }
        r3 = r3.toString();	 Catch:{ all -> 0x0067 }
        android.util.Log.e(r1, r3);	 Catch:{ all -> 0x0067 }
        goto L_0x0038;
    L_0x0067:
        r1 = move-exception;
        if (r12 == 0) goto L_0x006d;
    L_0x006a:
        r12.close();
    L_0x006d:
        throw r1;
    L_0x006e:
        r10 = move-exception;
        r1 = "VCardComposer";	 Catch:{ all -> 0x0067 }
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0067 }
        r3.<init>();	 Catch:{ all -> 0x0067 }
        r4 = "IllegalAccessException has been thrown: ";	 Catch:{ all -> 0x0067 }
        r3 = r3.append(r4);	 Catch:{ all -> 0x0067 }
        r4 = r10.getMessage();	 Catch:{ all -> 0x0067 }
        r3 = r3.append(r4);	 Catch:{ all -> 0x0067 }
        r3 = r3.toString();	 Catch:{ all -> 0x0067 }
        android.util.Log.e(r1, r3);	 Catch:{ all -> 0x0067 }
        goto L_0x0038;	 Catch:{ all -> 0x0067 }
    L_0x008c:
        r10 = move-exception;	 Catch:{ all -> 0x0067 }
        r1 = "VCardComposer";	 Catch:{ all -> 0x0067 }
        r3 = "InvocationTargetException has been thrown: ";	 Catch:{ all -> 0x0067 }
        android.util.Log.e(r1, r3, r10);	 Catch:{ all -> 0x0067 }
        r1 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0067 }
        r3 = "InvocationTargetException has been thrown";	 Catch:{ all -> 0x0067 }
        r1.<init>(r3);	 Catch:{ all -> 0x0067 }
        throw r1;	 Catch:{ all -> 0x0067 }
    L_0x009c:
        r0 = r17;	 Catch:{ all -> 0x0067 }
        r1 = r0.mContentResolver;	 Catch:{ all -> 0x0067 }
        r3 = 0;	 Catch:{ all -> 0x0067 }
        r4 = "contact_id=?";	 Catch:{ all -> 0x0067 }
        r6 = 0;	 Catch:{ all -> 0x0067 }
        r1 = r1.query(r2, r3, r4, r5, r6);	 Catch:{ all -> 0x0067 }
        r12 = android.provider.ContactsContract.RawContacts.newEntityIterator(r1);	 Catch:{ all -> 0x0067 }
        goto L_0x0038;	 Catch:{ all -> 0x0067 }
    L_0x00ad:
        r1 = r12.hasNext();	 Catch:{ all -> 0x0067 }
        if (r1 != 0) goto L_0x00d6;	 Catch:{ all -> 0x0067 }
    L_0x00b3:
        r1 = "VCardComposer";	 Catch:{ all -> 0x0067 }
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0067 }
        r3.<init>();	 Catch:{ all -> 0x0067 }
        r4 = "Data does not exist. contactId: ";	 Catch:{ all -> 0x0067 }
        r3 = r3.append(r4);	 Catch:{ all -> 0x0067 }
        r0 = r18;	 Catch:{ all -> 0x0067 }
        r3 = r3.append(r0);	 Catch:{ all -> 0x0067 }
        r3 = r3.toString();	 Catch:{ all -> 0x0067 }
        android.util.Log.w(r1, r3);	 Catch:{ all -> 0x0067 }
        r1 = "";	 Catch:{ all -> 0x0067 }
        if (r12 == 0) goto L_0x0048;
    L_0x00d1:
        r12.close();
        goto L_0x0048;
    L_0x00d6:
        r1 = r12.hasNext();	 Catch:{ all -> 0x0067 }
        if (r1 == 0) goto L_0x0114;	 Catch:{ all -> 0x0067 }
    L_0x00dc:
        r11 = r12.next();	 Catch:{ all -> 0x0067 }
        r11 = (android.content.Entity) r11;	 Catch:{ all -> 0x0067 }
        r1 = r11.getSubValues();	 Catch:{ all -> 0x0067 }
        r13 = r1.iterator();	 Catch:{ all -> 0x0067 }
    L_0x00ea:
        r1 = r13.hasNext();	 Catch:{ all -> 0x0067 }
        if (r1 == 0) goto L_0x00d6;	 Catch:{ all -> 0x0067 }
    L_0x00f0:
        r15 = r13.next();	 Catch:{ all -> 0x0067 }
        r15 = (android.content.Entity.NamedContentValues) r15;	 Catch:{ all -> 0x0067 }
        r7 = r15.values;	 Catch:{ all -> 0x0067 }
        r1 = "mimetype";	 Catch:{ all -> 0x0067 }
        r14 = r7.getAsString(r1);	 Catch:{ all -> 0x0067 }
        if (r14 == 0) goto L_0x00ea;	 Catch:{ all -> 0x0067 }
    L_0x0100:
        r8 = r9.get(r14);	 Catch:{ all -> 0x0067 }
        r8 = (java.util.List) r8;	 Catch:{ all -> 0x0067 }
        if (r8 != 0) goto L_0x0110;	 Catch:{ all -> 0x0067 }
    L_0x0108:
        r8 = new java.util.ArrayList;	 Catch:{ all -> 0x0067 }
        r8.<init>();	 Catch:{ all -> 0x0067 }
        r9.put(r14, r8);	 Catch:{ all -> 0x0067 }
    L_0x0110:
        r8.add(r7);	 Catch:{ all -> 0x0067 }
        goto L_0x00ea;
    L_0x0114:
        if (r12 == 0) goto L_0x0119;
    L_0x0116:
        r12.close();
    L_0x0119:
        r0 = r17;
        r1 = r0.buildVCard(r9);
        goto L_0x0048;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.vcard.VCardComposer.createOneEntryInternal(java.lang.String, java.lang.reflect.Method):java.lang.String");
    }

    public void setPhoneNumberTranslationCallback(VCardPhoneNumberTranslationCallback callback) {
        this.mPhoneTranslationCallback = callback;
    }

    public String buildVCard(Map<String, List<ContentValues>> contentValuesListMap) {
        if (contentValuesListMap == null) {
            Log.e(LOG_TAG, "The given map is null. Ignore and return empty String");
            return "";
        }
        VCardBuilder builder = new VCardBuilder(this.mVCardType, this.mCharset);
        builder.appendNameProperties((List) contentValuesListMap.get("vnd.android.cursor.item/name")).appendNickNames((List) contentValuesListMap.get("vnd.android.cursor.item/nickname")).appendPhones((List) contentValuesListMap.get("vnd.android.cursor.item/phone_v2"), this.mPhoneTranslationCallback).appendEmails((List) contentValuesListMap.get("vnd.android.cursor.item/email_v2")).appendPostals((List) contentValuesListMap.get("vnd.android.cursor.item/postal-address_v2")).appendOrganizations((List) contentValuesListMap.get("vnd.android.cursor.item/organization")).appendWebsites((List) contentValuesListMap.get("vnd.android.cursor.item/website"));
        if ((this.mVCardType & VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT) == 0) {
            builder.appendPhotos((List) contentValuesListMap.get("vnd.android.cursor.item/photo"));
        }
        builder.appendNotes((List) contentValuesListMap.get("vnd.android.cursor.item/note")).appendEvents((List) contentValuesListMap.get("vnd.android.cursor.item/contact_event")).appendIms((List) contentValuesListMap.get("vnd.android.cursor.item/im")).appendSipAddresses((List) contentValuesListMap.get("vnd.android.cursor.item/sip_address")).appendRelation((List) contentValuesListMap.get("vnd.android.cursor.item/relation"));
        return builder.toString();
    }

    public void terminate() {
        closeCursorIfAppropriate();
        this.mTerminateCalled = true;
    }

    private void closeCursorIfAppropriate() {
        if (!this.mCursorSuppliedFromOutside && this.mCursor != null) {
            try {
                this.mCursor.close();
            } catch (SQLiteException e) {
                Log.e(LOG_TAG, "SQLiteException on Cursor#close(): " + e.getMessage());
            }
            this.mCursor = null;
        }
    }

    protected void finalize() throws Throwable {
        try {
            if (!this.mTerminateCalled) {
                Log.e(LOG_TAG, "finalized() is called before terminate() being called");
            }
            super.finalize();
        } catch (Throwable th) {
            super.finalize();
        }
    }

    public int getCount() {
        if (this.mCursor != null) {
            return this.mCursor.getCount();
        }
        Log.w(LOG_TAG, "This object is not ready yet.");
        return 0;
    }

    public boolean isAfterLast() {
        if (this.mCursor != null) {
            return this.mCursor.isAfterLast();
        }
        Log.w(LOG_TAG, "This object is not ready yet.");
        return DEBUG;
    }

    public String getErrorReason() {
        return this.mErrorReason;
    }
}
