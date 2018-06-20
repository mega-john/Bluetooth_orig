package com.android.bluetooth.pbap;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWindowAllocationException;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.text.TextUtils;
import android.util.Log;
import com.android.bluetooth.C0000R;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardConstants;
import com.android.vcard.VCardPhoneNumberTranslationCallback;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import javax.obex.Operation;
import javax.obex.ServerOperation;

public class BluetoothPbapVcardManager {
    static final String CALLLOG_SORT_ORDER = "_id DESC";
    private static final String CLAUSE_ONLY_VISIBLE = "in_visible_group=1";
    static final int CONTACTS_ID_COLUMN_INDEX = 0;
    static final int CONTACTS_NAME_COLUMN_INDEX = 1;
    static final String[] CONTACTS_PROJECTION = new String[]{"_id", "display_name"};
    static final String[] PHONES_PROJECTION = new String[]{"_id", "data2", "data3", "data1", "display_name"};
    private static final int PHONE_NUMBER_COLUMN_INDEX = 3;
    static final String SORT_ORDER_PHONE_NUMBER = "data1 ASC";
    private static final String TAG = "BluetoothPbapVcardManager";
    /* renamed from: V */
    private static final boolean f74V = false;
    private Context mContext;
    private ContentResolver mResolver = this.mContext.getContentResolver();

    /* renamed from: com.android.bluetooth.pbap.BluetoothPbapVcardManager$1 */
    class C00651 implements VCardPhoneNumberTranslationCallback {
        C00651() {
        }

        public String onValueReceived(String rawValue, int type, String label, boolean isPrimary) {
            return rawValue.replace(',', 'p').replace(';', 'w');
        }
    }

    public class HandlerForStringBuffer {
        private Operation operation;
        private OutputStream outputStream;
        private String phoneOwnVCard = null;

        public HandlerForStringBuffer(Operation op, String ownerVCard) {
            this.operation = op;
            if (ownerVCard != null) {
                this.phoneOwnVCard = ownerVCard;
            }
        }

        private boolean write(String vCard) {
            if (vCard != null) {
                try {
                    this.outputStream.write(vCard.getBytes());
                    return true;
                } catch (IOException e) {
                    Log.e(BluetoothPbapVcardManager.TAG, "write outputstrem failed" + e.toString());
                }
            }
            return false;
        }

        public boolean onInit(Context context) {
            try {
                this.outputStream = this.operation.openOutputStream();
                if (this.phoneOwnVCard != null) {
                    return write(this.phoneOwnVCard);
                }
                return true;
            } catch (IOException e) {
                Log.e(BluetoothPbapVcardManager.TAG, "open outputstrem failed" + e.toString());
                return false;
            }
        }

        public boolean onEntryCreated(String vcard) {
            return write(vcard);
        }

        public void onTerminate() {
            if (!BluetoothPbapObexServer.closeStream(this.outputStream, this.operation)) {
            }
        }
    }

    public static class VCardFilter {
        private static final String SEPARATOR = System.getProperty("line.separator");
        private final byte[] filter;

        private enum FilterBit {
            FN(1, VCardConstants.PROPERTY_FN, true, false),
            PHOTO(3, VCardConstants.PROPERTY_PHOTO, false, false),
            BDAY(4, VCardConstants.PROPERTY_BDAY, false, false),
            ADR(5, VCardConstants.PROPERTY_ADR, false, false),
            EMAIL(8, VCardConstants.PROPERTY_EMAIL, false, false),
            TITLE(12, VCardConstants.PROPERTY_TITLE, false, false),
            ORG(16, VCardConstants.PROPERTY_ORG, false, false),
            NOTES(17, "NOTES", false, false),
            URL(20, VCardConstants.PROPERTY_URL, false, false),
            NICKNAME(23, VCardConstants.PROPERTY_NICKNAME, false, true);
            
            public final boolean excludeForV21;
            public final boolean onlyCheckV21;
            public final int pos;
            public final String prop;

            private FilterBit(int pos, String prop, boolean onlyCheckV21, boolean excludeForV21) {
                this.pos = pos;
                this.prop = prop;
                this.onlyCheckV21 = onlyCheckV21;
                this.excludeForV21 = excludeForV21;
            }
        }

        private boolean isFilteredOut(FilterBit bit, boolean vCardType21) {
            boolean z = true;
            int offset = (bit.pos / 8) + 1;
            int bit_pos = bit.pos % 8;
            if (!vCardType21 && bit.onlyCheckV21) {
                return false;
            }
            if (vCardType21 && bit.excludeForV21) {
                return true;
            }
            if (this.filter == null || offset >= this.filter.length) {
                return false;
            }
            if (((this.filter[this.filter.length - offset] >> bit_pos) & 1) == 0) {
                z = false;
            }
            return z;
        }

        VCardFilter(byte[] filter) {
            this.filter = filter;
        }

        public boolean isPhotoEnabled() {
            return !isFilteredOut(FilterBit.PHOTO, false);
        }

        public String apply(String vCard, boolean vCardType21) {
            if (this.filter == null) {
                return vCard;
            }
            String[] lines = vCard.split(SEPARATOR);
            StringBuilder filteredVCard = new StringBuilder();
            boolean filteredOut = false;
            for (String line : lines) {
                if (!(Character.isWhitespace(line.charAt(0)) || line.startsWith("="))) {
                    String currentProp = line.split("[;:]")[0];
                    filteredOut = false;
                    for (FilterBit bit : FilterBit.values()) {
                        if (bit.prop.equals(currentProp)) {
                            filteredOut = isFilteredOut(bit, vCardType21);
                            break;
                        }
                    }
                    if (currentProp.startsWith("X-")) {
                        filteredOut = true;
                    }
                }
                if (!filteredOut) {
                    filteredVCard.append(line + SEPARATOR);
                }
            }
            return filteredVCard.toString();
        }
    }

    public BluetoothPbapVcardManager(Context context) {
        this.mContext = context;
    }

    private final String getOwnerPhoneNumberVcardFromProfile(boolean vcardType21, byte[] filter) {
        int vcardType;
        if (vcardType21) {
            vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
        } else {
            vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
        }
        if (!BluetoothPbapConfig.includePhotosInVcard()) {
            vcardType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
        }
        return BluetoothPbapUtils.createProfileVCard(this.mContext, vcardType, filter);
    }

    public final String getOwnerPhoneNumberVcard(boolean vcardType21, byte[] filter) {
        if (BluetoothPbapConfig.useProfileForOwnerVcard()) {
            String vcard = getOwnerPhoneNumberVcardFromProfile(vcardType21, filter);
            if (!(vcard == null || vcard.length() == 0)) {
                return vcard;
            }
        }
        return new BluetoothPbapCallLogComposer(this.mContext).composeVCardForPhoneOwnNumber(2, BluetoothPbapService.getLocalPhoneName(), BluetoothPbapService.getLocalPhoneNum(), vcardType21);
    }

    public final int getPhonebookSize(int type) {
        switch (type) {
            case 1:
                return getContactsSize();
            default:
                return getCallHistorySize(type);
        }
    }

    public final int getContactsSize() {
        int size = 0;
        Cursor contactCursor = null;
        try {
            contactCursor = this.mResolver.query(Contacts.CONTENT_URI, null, CLAUSE_ONLY_VISIBLE, null, null);
            if (contactCursor != null) {
                size = contactCursor.getCount() + 1;
            }
            if (contactCursor != null) {
                contactCursor.close();
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting Contacts size");
            if (contactCursor != null) {
                contactCursor.close();
            }
        } catch (Throwable th) {
            if (contactCursor != null) {
                contactCursor.close();
            }
        }
        return size;
    }

    public final int getCallHistorySize(int type) {
        int size = 0;
        Cursor callCursor = null;
        try {
            callCursor = this.mResolver.query(Calls.CONTENT_URI, null, BluetoothPbapObexServer.createSelectionPara(type), null, "date DESC");
            if (callCursor != null) {
                size = callCursor.getCount();
            }
            if (callCursor != null) {
                callCursor.close();
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting CallHistory size");
            if (callCursor != null) {
                callCursor.close();
            }
        } catch (Throwable th) {
            if (callCursor != null) {
                callCursor.close();
            }
        }
        return size;
    }

    public final ArrayList<String> loadCallHistoryList(int type) {
        Uri myUri = Calls.CONTENT_URI;
        String selection = BluetoothPbapObexServer.createSelectionPara(type);
        String[] projection = new String[]{"number", "name", "presentation"};
        Cursor callCursor = null;
        ArrayList<String> list = new ArrayList();
        try {
            callCursor = this.mResolver.query(myUri, projection, selection, null, CALLLOG_SORT_ORDER);
            if (callCursor != null) {
                callCursor.moveToFirst();
                while (!callCursor.isAfterLast()) {
                    String name = callCursor.getString(1);
                    if (TextUtils.isEmpty(name)) {
                        if (callCursor.getInt(2) != 1) {
                            name = this.mContext.getString(C0000R.string.unknownNumber);
                        } else {
                            name = callCursor.getString(0);
                        }
                    }
                    list.add(name);
                    callCursor.moveToNext();
                }
            }
            if (callCursor != null) {
                callCursor.close();
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while loading CallHistory");
            return list;
        } finally {
            if (callCursor != null) {
                callCursor.close();
            }
        }
        return list;
    }

    public final ArrayList<String> getPhonebookNameList(int orderByWhat) {
        ArrayList<String> nameList = new ArrayList();
        String ownerName = null;
        if (BluetoothPbapConfig.useProfileForOwnerVcard()) {
            ownerName = BluetoothPbapUtils.getProfileName(this.mContext);
        }
        if (ownerName == null || ownerName.length() == 0) {
            ownerName = BluetoothPbapService.getLocalPhoneName();
        }
        nameList.add(ownerName);
        Uri myUri = Contacts.CONTENT_URI;
        Cursor contactCursor = null;
        try {
            if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_INDEXED) {
                contactCursor = this.mResolver.query(myUri, CONTACTS_PROJECTION, CLAUSE_ONLY_VISIBLE, null, "_id");
            } else if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL) {
                contactCursor = this.mResolver.query(myUri, CONTACTS_PROJECTION, CLAUSE_ONLY_VISIBLE, null, "display_name");
            }
            if (contactCursor != null) {
                contactCursor.moveToFirst();
                while (!contactCursor.isAfterLast()) {
                    String name = contactCursor.getString(1);
                    long id = contactCursor.getLong(0);
                    if (TextUtils.isEmpty(name)) {
                        name = this.mContext.getString(17039374);
                    }
                    nameList.add(name + "," + id);
                    contactCursor.moveToNext();
                }
            }
            if (contactCursor != null) {
                contactCursor.close();
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting Phonebook name list");
            return nameList;
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
            }
        }
        return nameList;
    }

    public final ArrayList<String> getContactNamesByNumber(String phoneNumber) {
        Uri uri;
        int tempListSize;
        int index;
        String object;
        ArrayList<String> nameList = new ArrayList();
        ArrayList<String> tempNameList = new ArrayList();
        Cursor contactCursor = null;
        if (phoneNumber == null || phoneNumber.length() != 0) {
            uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        } else {
            uri = Contacts.CONTENT_URI;
        }
        try {
            contactCursor = this.mResolver.query(uri, CONTACTS_PROJECTION, CLAUSE_ONLY_VISIBLE, null, "_id");
            if (contactCursor != null) {
                contactCursor.moveToFirst();
                while (!contactCursor.isAfterLast()) {
                    String name = contactCursor.getString(1);
                    long id = contactCursor.getLong(0);
                    if (TextUtils.isEmpty(name)) {
                        name = this.mContext.getString(17039374);
                    }
                    tempNameList.add(name + "," + id);
                    contactCursor.moveToNext();
                }
            }
            if (contactCursor != null) {
                contactCursor.close();
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting contact names");
            tempListSize = tempNameList.size();
            for (index = 0; index < tempListSize; index++) {
                object = (String) tempNameList.get(index);
                if (!nameList.contains(object)) {
                    nameList.add(object);
                }
            }
            return nameList;
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
            }
        }
        tempListSize = tempNameList.size();
        for (index = 0; index < tempListSize; index++) {
            object = (String) tempNameList.get(index);
            if (!nameList.contains(object)) {
                nameList.add(object);
            }
        }
        return nameList;
    }

    public final int composeAndSendCallLogVcards(int type, Operation op, int startPoint, int endPoint, boolean vcardType21, boolean ignorefilter, byte[] filter) {
        if (startPoint < 1 || startPoint > endPoint) {
            Log.e(TAG, "internal error: startPoint or endPoint is not correct.");
            return 208;
        }
        String recordSelection;
        String selection;
        String typeSelection = BluetoothPbapObexServer.createSelectionPara(type);
        Cursor callsCursor = null;
        long startPointId = 0;
        long endPointId = 0;
        try {
            callsCursor = this.mResolver.query(Calls.CONTENT_URI, new String[]{"_id"}, typeSelection, null, CALLLOG_SORT_ORDER);
            if (callsCursor != null) {
                callsCursor.moveToPosition(startPoint - 1);
                startPointId = callsCursor.getLong(0);
                if (startPoint == endPoint) {
                    endPointId = startPointId;
                } else {
                    callsCursor.moveToPosition(endPoint - 1);
                    endPointId = callsCursor.getLong(0);
                }
            }
            if (callsCursor != null) {
                callsCursor.close();
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while composing calllog vcards");
            if (callsCursor != null) {
                callsCursor.close();
            }
        } catch (Throwable th) {
            if (callsCursor != null) {
                callsCursor.close();
            }
        }
        if (startPoint == endPoint) {
            recordSelection = "_id=" + startPointId;
        } else {
            recordSelection = "_id>=" + endPointId + " AND " + "_id" + "<=" + startPointId;
        }
        if (typeSelection == null) {
            selection = recordSelection;
        } else {
            selection = "(" + typeSelection + ") AND (" + recordSelection + ")";
        }
        return composeAndSendVCards(op, selection, vcardType21, null, false, ignorefilter, filter);
    }

    public final int composeAndSendPhonebookVcards(Operation op, int startPoint, int endPoint, boolean vcardType21, String ownerVCard, boolean ignorefilter, byte[] filter) {
        if (startPoint < 1 || startPoint > endPoint) {
            Log.e(TAG, "internal error: startPoint or endPoint is not correct.");
            return 208;
        }
        String selection;
        Cursor contactCursor = null;
        long startPointId = 0;
        long endPointId = 0;
        try {
            contactCursor = this.mResolver.query(Contacts.CONTENT_URI, CONTACTS_PROJECTION, CLAUSE_ONLY_VISIBLE, null, "_id");
            if (contactCursor != null) {
                contactCursor.moveToPosition(startPoint - 1);
                startPointId = contactCursor.getLong(0);
                if (startPoint == endPoint) {
                    endPointId = startPointId;
                } else {
                    contactCursor.moveToPosition(endPoint - 1);
                    endPointId = contactCursor.getLong(0);
                }
            }
            if (contactCursor != null) {
                contactCursor.close();
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while composing phonebook vcards");
            if (contactCursor != null) {
                contactCursor.close();
            }
        } catch (Throwable th) {
            if (contactCursor != null) {
                contactCursor.close();
            }
        }
        if (startPoint == endPoint) {
            selection = "_id=" + startPointId + " AND " + CLAUSE_ONLY_VISIBLE;
        } else {
            selection = "_id>=" + startPointId + " AND " + "_id" + "<=" + endPointId + " AND " + CLAUSE_ONLY_VISIBLE;
        }
        return composeAndSendVCards(op, selection, vcardType21, ownerVCard, true, ignorefilter, filter);
    }

    public final int composeAndSendPhonebookOneVcard(Operation op, int offset, boolean vcardType21, String ownerVCard, int orderByWhat, boolean ignorefilter, byte[] filter) {
        if (offset < 1) {
            Log.e(TAG, "Internal error: offset is not correct.");
            return 208;
        }
        Uri myUri = Contacts.CONTENT_URI;
        Cursor contactCursor = null;
        long contactId = 0;
        if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_INDEXED) {
            try {
                contactCursor = this.mResolver.query(myUri, CONTACTS_PROJECTION, CLAUSE_ONLY_VISIBLE, null, "_id");
                if (contactCursor != null) {
                    contactCursor.moveToPosition(offset - 1);
                    contactId = contactCursor.getLong(0);
                }
                if (contactCursor != null) {
                    contactCursor.close();
                }
            } catch (CursorWindowAllocationException e) {
                Log.e(TAG, "CursorWindowAllocationException while composing phonebook one vcard order by index");
                if (contactCursor != null) {
                    contactCursor.close();
                }
            } catch (Throwable th) {
                if (contactCursor != null) {
                    contactCursor.close();
                }
            }
        } else if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL) {
            try {
                contactCursor = this.mResolver.query(myUri, CONTACTS_PROJECTION, CLAUSE_ONLY_VISIBLE, null, "display_name");
                if (contactCursor != null) {
                    contactCursor.moveToPosition(offset - 1);
                    contactId = contactCursor.getLong(0);
                }
                if (contactCursor != null) {
                    contactCursor.close();
                }
            } catch (CursorWindowAllocationException e2) {
                Log.e(TAG, "CursorWindowAllocationException while composing phonebook one vcard order by alphabetical");
                if (contactCursor != null) {
                    contactCursor.close();
                }
            } catch (Throwable th2) {
                if (contactCursor != null) {
                    contactCursor.close();
                }
            }
        } else {
            Log.e(TAG, "Parameter orderByWhat is not supported!");
            return 208;
        }
        return composeAndSendVCards(op, "_id=" + contactId, vcardType21, ownerVCard, true, ignorefilter, filter);
    }

    public final int composeAndSendVCards(Operation op, String selection, boolean vcardType21, String ownerVCard, boolean isContacts, boolean ignorefilter, byte[] filter) {
        HandlerForStringBuffer buffer;
        Throwable th;
        HandlerForStringBuffer buffer2;
        String vcard;
        if (isContacts) {
            int vcardType;
            VCardComposer composer = null;
            if (ignorefilter) {
                filter = null;
            }
            VCardFilter vcardfilter = new VCardFilter(filter);
            buffer = null;
            if (vcardType21) {
                vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
            } else {
                vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
            }
            try {
                if (!vcardfilter.isPhotoEnabled()) {
                    vcardType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
                }
                composer = BluetoothPbapUtils.createFilteredVCardComposer(this.mContext, vcardType, null);
                composer.setPhoneNumberTranslationCallback(new C00651());
                buffer2 = new HandlerForStringBuffer(op, ownerVCard);
                if (composer.init(Contacts.CONTENT_URI, selection, null, "_id") && buffer2.onInit(this.mContext)) {
                    do {
                        if (!composer.isAfterLast()) {
                            if (BluetoothPbapObexServer.sIsAborted) {
                                ((ServerOperation) op).isAborted = true;
                                BluetoothPbapObexServer.sIsAborted = false;
                            } else {
                                vcard = composer.createOneEntry();
                                if (vcard == null) {
                                    Log.e(TAG, "Failed to read a contact. Error reason: " + composer.getErrorReason());
                                    if (composer != null) {
                                        composer.terminate();
                                    }
                                    if (buffer2 == null) {
                                        return 208;
                                    }
                                    buffer2.onTerminate();
                                    return 208;
                                }
                                try {
                                } catch (Throwable th2) {
                                    th = th2;
                                    buffer = buffer2;
                                }
                            }
                        }
                        if (composer != null) {
                            composer.terminate();
                        }
                        if (buffer2 != null) {
                            buffer2.onTerminate();
                        }
                    } while (buffer2.onEntryCreated(StripTelephoneNumber(vcardfilter.apply(vcard, vcardType21))));
                    if (composer != null) {
                        composer.terminate();
                    }
                    if (buffer2 == null) {
                        return 208;
                    }
                    buffer2.onTerminate();
                    return 208;
                }
                if (composer != null) {
                    composer.terminate();
                }
                if (buffer2 == null) {
                    return 208;
                }
                buffer2.onTerminate();
                return 208;
            } catch (Throwable th3) {
                th = th3;
                if (composer != null) {
                    composer.terminate();
                }
                if (buffer != null) {
                    buffer.onTerminate();
                }
                throw th;
            }
        }
        BluetoothPbapCallLogComposer composer2 = null;
        buffer = null;
        try {
            BluetoothPbapCallLogComposer composer3 = new BluetoothPbapCallLogComposer(this.mContext);
            try {
                buffer2 = new HandlerForStringBuffer(op, ownerVCard);
                if (composer3.init(Calls.CONTENT_URI, selection, null, CALLLOG_SORT_ORDER) && buffer2.onInit(this.mContext)) {
                    while (!composer3.isAfterLast()) {
                        if (BluetoothPbapObexServer.sIsAborted) {
                            ((ServerOperation) op).isAborted = true;
                            BluetoothPbapObexServer.sIsAborted = false;
                            break;
                        }
                        vcard = composer3.createOneEntry(vcardType21);
                        if (vcard == null) {
                            Log.e(TAG, "Failed to read a contact. Error reason: " + composer3.getErrorReason());
                            if (composer3 != null) {
                                composer3.terminate();
                            }
                            if (buffer2 == null) {
                                return 208;
                            }
                            buffer2.onTerminate();
                            return 208;
                        }
                        try {
                            buffer2.onEntryCreated(vcard);
                        } catch (Throwable th4) {
                            th = th4;
                            buffer = buffer2;
                            composer2 = composer3;
                        }
                    }
                    if (composer3 != null) {
                        composer3.terminate();
                    }
                    if (buffer2 != null) {
                        buffer2.onTerminate();
                    }
                } else {
                    if (composer3 != null) {
                        composer3.terminate();
                    }
                    if (buffer2 == null) {
                        return 208;
                    }
                    buffer2.onTerminate();
                    return 208;
                }
            } catch (Throwable th5) {
                th = th5;
                composer2 = composer3;
                if (composer2 != null) {
                    composer2.terminate();
                }
                if (buffer != null) {
                    buffer.onTerminate();
                }
                throw th;
            }
        } catch (Throwable th6) {
            th = th6;
            if (composer2 != null) {
                composer2.terminate();
            }
            if (buffer != null) {
                buffer.onTerminate();
            }
            throw th;
        }
        return 160;
    }

    public String StripTelephoneNumber(String vCard) {
        int i;
        String[] attr = vCard.split(System.getProperty("line.separator"));
        String Vcard = "";
        for (i = 0; i < attr.length; i++) {
            if (attr[i].startsWith(VCardConstants.PROPERTY_TEL)) {
                attr[i] = attr[i].replace("(", "");
                attr[i] = attr[i].replace(")", "");
                attr[i] = attr[i].replace("-", "");
                attr[i] = attr[i].replace(" ", "");
            }
        }
        for (i = 0; i < attr.length; i++) {
            if (!attr[i].equals("")) {
                Vcard = Vcard.concat(attr[i] + "\n");
            }
        }
        return Vcard;
    }
}
