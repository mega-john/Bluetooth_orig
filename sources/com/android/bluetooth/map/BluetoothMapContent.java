package com.android.bluetooth.map;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.map.BluetoothMapbMessageMms.MimePart;
import com.android.bluetooth.mapapi.BluetoothMapContract;
import com.android.bluetooth.opp.BluetoothShare;
import com.google.android.mms.pdu.CharacterSets;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

public class BluetoothMapContent {
    /* renamed from: D */
    private static final boolean f0D = true;
    public static final String INSERT_ADDRES_TOKEN = "insert-address-token";
    public static final int MAP_MESSAGE_CHARSET_NATIVE = 0;
    public static final int MAP_MESSAGE_CHARSET_UTF8 = 1;
    private static final int MASK_ATTACHMENT_SIZE = 1024;
    private static final int MASK_DATETIME = 2;
    private static final int MASK_PRIORITY = 2048;
    private static final int MASK_PROTECTED = 16384;
    private static final int MASK_READ = 4096;
    private static final int MASK_RECEPTION_STATUS = 256;
    private static final int MASK_RECIPIENT_ADDRESSING = 32;
    private static final int MASK_RECIPIENT_NAME = 16;
    private static final int MASK_REPLYTO_ADDRESSING = 32768;
    private static final int MASK_SENDER_ADDRESSING = 8;
    private static final int MASK_SENDER_NAME = 4;
    private static final int MASK_SENT = 8192;
    private static final int MASK_SIZE = 128;
    private static final int MASK_SUBJECT = 1;
    private static final int MASK_TEXT = 512;
    private static final int MASK_TYPE = 64;
    public static final int MMS_BCC = 129;
    public static final int MMS_CC = 130;
    public static final int MMS_FROM = 137;
    static final String[] MMS_PROJECTION = new String[]{"_id", "thread_id", "m_id", "m_size", "sub", "ct_t", "text_only", "date", "date_sent", "read", "msg_box", "st", "pri"};
    public static final int MMS_TO = 151;
    static final String[] SMS_PROJECTION = new String[]{"_id", "thread_id", "address", "body", "date", "read", "type", BluetoothShare.STATUS, "locked", "error_code"};
    private static final String TAG = "BluetoothMapContent";
    /* renamed from: V */
    private static final boolean f1V = false;
    private String mBaseEmailUri = null;
    private Context mContext;
    private ContentResolver mResolver;

    private class FilterInfo {
        public static final int TYPE_EMAIL = 2;
        public static final int TYPE_MMS = 1;
        public static final int TYPE_SMS = 0;
        public int mEmailColAttachementSize;
        public int mEmailColAttachment;
        public int mEmailColBccAddress;
        public int mEmailColCcAddress;
        public int mEmailColDate;
        public int mEmailColFolder;
        public int mEmailColFromAddress;
        public int mEmailColId;
        public int mEmailColPriority;
        public int mEmailColProtected;
        public int mEmailColRead;
        public int mEmailColSize;
        public int mEmailColSubject;
        public int mEmailColThreadId;
        public int mEmailColToAddress;
        public int mMmsColAttachmentSize;
        public int mMmsColDate;
        public int mMmsColFolder;
        public int mMmsColId;
        public int mMmsColRead;
        public int mMmsColSize;
        public int mMmsColSubject;
        public int mMmsColTextOnly;
        int mMsgType;
        String mPhoneAlphaTag;
        String mPhoneNum;
        int mPhoneType;
        public int mSmsColAddress;
        public int mSmsColDate;
        public int mSmsColFolder;
        public int mSmsColId;
        public int mSmsColRead;
        public int mSmsColSubject;
        public int mSmsColType;

        private FilterInfo() {
            this.mMsgType = 0;
            this.mPhoneType = 0;
            this.mPhoneNum = null;
            this.mPhoneAlphaTag = null;
            this.mEmailColThreadId = -1;
            this.mEmailColProtected = -1;
            this.mEmailColFolder = -1;
            this.mMmsColFolder = -1;
            this.mSmsColFolder = -1;
            this.mEmailColRead = -1;
            this.mSmsColRead = -1;
            this.mMmsColRead = -1;
            this.mEmailColPriority = -1;
            this.mMmsColAttachmentSize = -1;
            this.mEmailColAttachment = -1;
            this.mEmailColAttachementSize = -1;
            this.mMmsColTextOnly = -1;
            this.mMmsColId = -1;
            this.mSmsColId = -1;
            this.mEmailColSize = -1;
            this.mSmsColSubject = -1;
            this.mMmsColSize = -1;
            this.mEmailColToAddress = -1;
            this.mEmailColCcAddress = -1;
            this.mEmailColBccAddress = -1;
            this.mSmsColAddress = -1;
            this.mSmsColDate = -1;
            this.mMmsColDate = -1;
            this.mEmailColDate = -1;
            this.mMmsColSubject = -1;
            this.mEmailColSubject = -1;
            this.mSmsColType = -1;
            this.mEmailColFromAddress = -1;
            this.mEmailColId = -1;
        }

        public void setEmailColumns(Cursor c) {
            this.mEmailColThreadId = c.getColumnIndex("thread_id");
            this.mEmailColProtected = c.getColumnIndex("flag_protected");
            this.mEmailColFolder = c.getColumnIndex("folder_id");
            this.mEmailColRead = c.getColumnIndex("flag_read");
            this.mEmailColPriority = c.getColumnIndex("high_priority");
            this.mEmailColAttachment = c.getColumnIndex("flag_attachment");
            this.mEmailColAttachementSize = c.getColumnIndex("attachment_size");
            this.mEmailColSize = c.getColumnIndex("message_size");
            this.mEmailColToAddress = c.getColumnIndex("to_list");
            this.mEmailColCcAddress = c.getColumnIndex("cc_list");
            this.mEmailColBccAddress = c.getColumnIndex("bcc_list");
            this.mEmailColDate = c.getColumnIndex("date");
            this.mEmailColSubject = c.getColumnIndex("subject");
            this.mEmailColFromAddress = c.getColumnIndex("from_list");
            this.mEmailColId = c.getColumnIndex("_id");
        }

        public void setSmsColumns(Cursor c) {
            this.mSmsColId = c.getColumnIndex("_id");
            this.mSmsColFolder = c.getColumnIndex("type");
            this.mSmsColRead = c.getColumnIndex("read");
            this.mSmsColSubject = c.getColumnIndex("body");
            this.mSmsColAddress = c.getColumnIndex("address");
            this.mSmsColDate = c.getColumnIndex("date");
            this.mSmsColType = c.getColumnIndex("type");
        }

        public void setMmsColumns(Cursor c) {
            this.mMmsColId = c.getColumnIndex("_id");
            this.mMmsColFolder = c.getColumnIndex("msg_box");
            this.mMmsColRead = c.getColumnIndex("read");
            this.mMmsColAttachmentSize = c.getColumnIndex("m_size");
            this.mMmsColTextOnly = c.getColumnIndex("text_only");
            this.mMmsColSize = c.getColumnIndex("m_size");
            this.mMmsColDate = c.getColumnIndex("date");
            this.mMmsColSubject = c.getColumnIndex("sub");
        }
    }

    public BluetoothMapContent(Context context, String emailBaseUri) {
        this.mContext = context;
        this.mResolver = this.mContext.getContentResolver();
        if (this.mResolver == null) {
            Log.d(TAG, "getContentResolver failed");
        }
        this.mBaseEmailUri = emailBaseUri;
    }

    private static void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
            }
        }
    }

    private void setProtected(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 16384) != 0) {
            String protect = "no";
            if (fi.mMsgType == 2 && c.getInt(fi.mEmailColProtected) == 1) {
                protect = "yes";
            }
            e.setProtect(protect);
        }
    }

    private void setThreadId(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if (fi.mMsgType == 2) {
            e.setThreadId(c.getLong(fi.mEmailColThreadId));
        }
    }

    private void setSent(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 8192) != 0) {
            String sent;
            int msgType = 0;
            if (fi.mMsgType == 0) {
                msgType = c.getInt(fi.mSmsColFolder);
            } else if (fi.mMsgType == 1) {
                msgType = c.getInt(fi.mMmsColFolder);
            } else if (fi.mMsgType == 2) {
                msgType = c.getInt(fi.mEmailColFolder);
            }
            if (msgType == 2) {
                sent = "yes";
            } else {
                sent = "no";
            }
            e.setSent(sent);
        }
    }

    private void setRead(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        boolean z;
        boolean z2 = true;
        int read = 0;
        if (fi.mMsgType == 0) {
            read = c.getInt(fi.mSmsColRead);
        } else if (fi.mMsgType == 1) {
            read = c.getInt(fi.mMmsColRead);
        } else if (fi.mMsgType == 2) {
            read = c.getInt(fi.mEmailColRead);
        }
        if (read == 1) {
            z = true;
        } else {
            z = false;
        }
        if ((ap.getParameterMask() & 4096) == 0) {
            z2 = false;
        }
        e.setRead(z, z2);
    }

    private void setPriority(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 2048) != 0) {
            String priority = "no";
            if (fi.mMsgType == 2 && c.getInt(fi.mEmailColPriority) == 1) {
                priority = "yes";
            }
            int pri = 0;
            if (fi.mMsgType == 1) {
                pri = c.getInt(c.getColumnIndex("pri"));
            }
            if (pri == MMS_CC) {
                priority = "yes";
            }
            e.setPriority(priority);
        }
    }

    private void setAttachmentSize(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 1024) != 0) {
            int size = 0;
            if (fi.mMsgType == 1) {
                if (c.getInt(fi.mMmsColTextOnly) == 0) {
                    size = c.getInt(fi.mMmsColAttachmentSize);
                    if (size <= 0) {
                        Log.d(TAG, "Error in message database, size reported as: " + size + " Changing size to 1");
                        size = 1;
                    }
                }
            } else if (fi.mMsgType == 2) {
                int attachment = c.getInt(fi.mEmailColAttachment);
                size = c.getInt(fi.mEmailColAttachementSize);
                if (attachment == 1 && size == 0) {
                    Log.d(TAG, "Error in message database, attachment size reported as: " + size + " Changing size to 1");
                    size = 1;
                }
            }
            e.setAttachmentSize(size);
        }
    }

    private void setText(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 512) != 0) {
            String hasText = "";
            if (fi.mMsgType == 0) {
                hasText = "yes";
            } else if (fi.mMsgType == 1) {
                if (c.getInt(fi.mMmsColTextOnly) == 1) {
                    hasText = "yes";
                } else {
                    String text = getTextPartsMms(c.getLong(fi.mMmsColId));
                    if (text == null || text.length() <= 0) {
                        hasText = "no";
                    } else {
                        hasText = "yes";
                    }
                }
            } else if (fi.mMsgType == 2) {
                hasText = "yes";
            }
            e.setText(hasText);
        }
    }

    private void setReceptionStatus(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 256) != 0) {
            e.setReceptionStatus("complete");
        }
    }

    private void setSize(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 128) != 0) {
            int size = 0;
            if (fi.mMsgType == 0) {
                size = c.getString(fi.mSmsColSubject).length();
            } else if (fi.mMsgType == 1) {
                size = c.getInt(fi.mMmsColSize);
            } else if (fi.mMsgType == 2) {
                size = c.getInt(fi.mEmailColSize);
            }
            if (size <= 0) {
                Log.d(TAG, "Error in message database, size reported as: " + size + " Changing size to 1");
                size = 1;
            }
            e.setSize(size);
        }
    }

    private void setType(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 64) != 0) {
            TYPE type = null;
            if (fi.mMsgType == 0) {
                if (fi.mPhoneType == 1) {
                    type = TYPE.SMS_GSM;
                } else if (fi.mPhoneType == 2) {
                    type = TYPE.SMS_CDMA;
                }
            } else if (fi.mMsgType == 1) {
                type = TYPE.MMS;
            } else if (fi.mMsgType == 2) {
                type = TYPE.EMAIL;
            }
            e.setType(type);
        }
    }

    private String setRecipientAddressingEmail(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi) {
        String toAddress = c.getString(fi.mEmailColToAddress);
        String ccAddress = c.getString(fi.mEmailColCcAddress);
        String bccAddress = c.getString(fi.mEmailColBccAddress);
        String address = "";
        if (toAddress != null) {
            address = address + toAddress;
            if (ccAddress != null) {
                address = address + ",";
            }
        }
        if (ccAddress != null) {
            address = address + ccAddress;
            if (bccAddress != null) {
                address = address + ",";
            }
        }
        if (bccAddress != null) {
            return address + bccAddress;
        }
        return address;
    }

    private void setRecipientAddressing(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 32) != 0) {
            String address = null;
            if (fi.mMsgType == 0) {
                if (c.getInt(fi.mSmsColType) == 1) {
                    address = fi.mPhoneNum;
                } else {
                    address = c.getString(c.getColumnIndex("address"));
                }
            } else if (fi.mMsgType == 1) {
                address = getAddressMms(this.mResolver, c.getLong(c.getColumnIndex("_id")), MMS_TO);
            } else if (fi.mMsgType == 2) {
                address = setRecipientAddressingEmail(e, c, fi);
            }
            if (address == null) {
                address = "";
            }
            e.setRecipientAddressing(address);
        }
    }

    private void setRecipientName(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 16) != 0) {
            String name = null;
            String phone;
            if (fi.mMsgType == 0) {
                if (c.getInt(fi.mSmsColType) != 1) {
                    phone = c.getString(fi.mSmsColAddress);
                    if (!(phone == null || phone.isEmpty())) {
                        name = getContactNameFromPhone(phone);
                    }
                } else {
                    name = fi.mPhoneAlphaTag;
                }
            } else if (fi.mMsgType == 1) {
                long id = c.getLong(fi.mMmsColId);
                if (e.getRecipientAddressing() != null) {
                    phone = getAddressMms(this.mResolver, id, MMS_TO);
                } else {
                    phone = e.getRecipientAddressing();
                }
                if (!(phone == null || phone.isEmpty())) {
                    name = getContactNameFromPhone(phone);
                }
            } else if (fi.mMsgType == 2) {
                name = setRecipientAddressingEmail(e, c, fi);
            }
            if (name == null) {
                name = "";
            }
            e.setRecipientName(name);
        }
    }

    private void setSenderAddressing(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 8) != 0) {
            String address = null;
            String tempAddress;
            if (fi.mMsgType == 0) {
                if (c.getInt(fi.mSmsColType) == 1) {
                    tempAddress = c.getString(fi.mSmsColAddress);
                } else {
                    tempAddress = fi.mPhoneNum;
                }
                if (tempAddress != null) {
                    address = PhoneNumberUtils.extractNetworkPortion(tempAddress);
                    Boolean alpha = Boolean.valueOf(PhoneNumberUtils.stripSeparators(tempAddress).matches("[0-9]*[a-zA-Z]+[0-9]*"));
                    if (address == null || address.length() < 2 || alpha.booleanValue()) {
                        address = tempAddress;
                    }
                }
            } else if (fi.mMsgType == 1) {
                tempAddress = getAddressMms(this.mResolver, c.getLong(fi.mMmsColId), MMS_FROM);
                address = PhoneNumberUtils.extractNetworkPortion(tempAddress);
                if (address == null || address.length() < 1) {
                    address = tempAddress;
                }
            } else if (fi.mMsgType == 2) {
                address = c.getString(fi.mEmailColFromAddress);
            }
            if (address == null) {
                address = "";
            }
            e.setSenderAddressing(address);
        }
    }

    private void setSenderName(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 4) != 0) {
            String name = null;
            String phone;
            if (fi.mMsgType == 0) {
                if (c.getInt(c.getColumnIndex("type")) == 1) {
                    phone = c.getString(fi.mSmsColAddress);
                    if (!(phone == null || phone.isEmpty())) {
                        name = getContactNameFromPhone(phone);
                    }
                } else {
                    name = fi.mPhoneAlphaTag;
                }
            } else if (fi.mMsgType == 1) {
                long id = c.getLong(fi.mMmsColId);
                if (e.getSenderAddressing() != null) {
                    phone = getAddressMms(this.mResolver, id, MMS_FROM);
                } else {
                    phone = e.getSenderAddressing();
                }
                if (!(phone == null || phone.isEmpty())) {
                    name = getContactNameFromPhone(phone);
                }
            } else if (fi.mMsgType == 2) {
                name = c.getString(fi.mEmailColFromAddress);
            }
            if (name == null) {
                name = "";
            }
            e.setSenderName(name);
        }
    }

    private void setDateTime(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & 2) != 0) {
            long date = 0;
            if (fi.mMsgType == 0) {
                date = c.getLong(fi.mSmsColDate);
            } else if (fi.mMsgType == 1) {
                date = c.getLong(fi.mMmsColDate) * 1000;
            } else if (fi.mMsgType == 2) {
                date = c.getLong(fi.mEmailColDate);
            }
            e.setDateTime(date);
        }
    }

    private String getTextPartsMms(long id) {
        String text = "";
        String selection = new String("mid=" + id);
        Cursor c = this.mResolver.query(Uri.parse(new String(Mms.CONTENT_URI + "/" + id + "/part")), null, selection, null, null);
        while (c != null) {
            try {
                if (!c.moveToNext()) {
                    break;
                } else if (c.getString(c.getColumnIndex("ct")).equals("text/plain")) {
                    String part = c.getString(c.getColumnIndex("text"));
                    if (part != null) {
                        text = text + part;
                    }
                }
            } catch (Throwable th) {
                close(c);
            }
        }
        close(c);
        return text;
    }

    private void setSubject(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        String subject = "";
        int subLength = ap.getSubjectLength();
        if (subLength == -1) {
            subLength = MASK_RECEPTION_STATUS;
        }
        if ((ap.getParameterMask() & 1) != 0) {
            if (fi.mMsgType == 0) {
                subject = c.getString(fi.mSmsColSubject);
            } else if (fi.mMsgType == 1) {
                subject = c.getString(fi.mMmsColSubject);
                if (subject == null || subject.length() == 0) {
                    subject = getTextPartsMms(c.getLong(fi.mMmsColId));
                }
            } else if (fi.mMsgType == 2) {
                subject = c.getString(fi.mEmailColSubject);
            }
            if (subject != null && subject.length() > subLength) {
                subject = subject.substring(0, subLength);
            }
            e.setSubject(subject);
        }
    }

    private void setHandle(BluetoothMapMessageListingElement e, Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        long handle = -1;
        if (fi.mMsgType == 0) {
            handle = c.getLong(fi.mSmsColId);
        } else if (fi.mMsgType == 1) {
            handle = c.getLong(fi.mMmsColId);
        } else if (fi.mMsgType == 2) {
            handle = c.getLong(fi.mEmailColId);
        }
        e.setHandle(handle);
    }

    private BluetoothMapMessageListingElement element(Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        BluetoothMapMessageListingElement e = new BluetoothMapMessageListingElement();
        setHandle(e, c, fi, ap);
        setDateTime(e, c, fi, ap);
        setType(e, c, fi, ap);
        setRead(e, c, fi, ap);
        e.setCursorIndex(c.getPosition());
        return e;
    }

    private String getContactNameFromPhone(String phone) {
        String name = null;
        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone));
        String[] projection = new String[]{"_id", "display_name"};
        Cursor c = this.mResolver.query(uri, projection, "in_visible_group=1", null, "display_name ASC");
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    name = c.getString(c.getColumnIndex("display_name"));
                }
            } catch (Throwable th) {
                close(c);
            }
        }
        close(c);
        return name;
    }

    public static String getAddressMms(ContentResolver r, long id, int type) {
        String selection = new String("msg_id=" + id + " AND type=" + type);
        String addr = null;
        Cursor c = r.query(Uri.parse(new String(Mms.CONTENT_URI + "/" + id + "/addr")), null, selection, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    addr = c.getString(c.getColumnIndex("address"));
                    if (addr.equals(INSERT_ADDRES_TOKEN)) {
                        addr = "";
                    }
                }
            } catch (Throwable th) {
                close(c);
            }
        }
        close(c);
        return addr;
    }

    private boolean matchRecipientMms(Cursor c, FilterInfo fi, String recip) {
        String phone = getAddressMms(this.mResolver, c.getLong(c.getColumnIndex("_id")), MMS_TO);
        if (phone == null || phone.length() <= 0) {
            return false;
        }
        if (phone.matches(recip)) {
            return true;
        }
        String name = getContactNameFromPhone(phone);
        if (name == null || name.length() <= 0 || !name.matches(recip)) {
            return false;
        }
        return true;
    }

    private boolean matchRecipientSms(Cursor c, FilterInfo fi, String recip) {
        String phone;
        String name;
        if (c.getInt(c.getColumnIndex("type")) == 1) {
            phone = fi.mPhoneNum;
            name = fi.mPhoneAlphaTag;
            if (phone != null && phone.length() > 0 && phone.matches(recip)) {
                return true;
            }
            if (name == null || name.length() <= 0 || !name.matches(recip)) {
                return false;
            }
            return true;
        }
        phone = c.getString(c.getColumnIndex("address"));
        if (phone == null || phone.length() <= 0) {
            return false;
        }
        if (phone.matches(recip)) {
            return true;
        }
        name = getContactNameFromPhone(phone);
        if (name == null || name.length() <= 0 || !name.matches(recip)) {
            return false;
        }
        return true;
    }

    private boolean matchRecipient(Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        String recip = ap.getFilterRecipient();
        if (recip == null || recip.length() <= 0) {
            return true;
        }
        recip = ".*" + recip.replace("*", ".*") + ".*";
        if (fi.mMsgType == 0) {
            return matchRecipientSms(c, fi, recip);
        }
        if (fi.mMsgType == 1) {
            return matchRecipientMms(c, fi, recip);
        }
        Log.d(TAG, "matchRecipient: Unknown msg type: " + fi.mMsgType);
        return false;
    }

    private boolean matchOriginatorMms(Cursor c, FilterInfo fi, String orig) {
        String phone = getAddressMms(this.mResolver, c.getLong(c.getColumnIndex("_id")), MMS_FROM);
        if (phone == null || phone.length() <= 0) {
            return false;
        }
        if (phone.matches(orig)) {
            return true;
        }
        String name = getContactNameFromPhone(phone);
        if (name == null || name.length() <= 0 || !name.matches(orig)) {
            return false;
        }
        return true;
    }

    private boolean matchOriginatorSms(Cursor c, FilterInfo fi, String orig) {
        String phone;
        String name;
        if (c.getInt(c.getColumnIndex("type")) == 1) {
            phone = c.getString(c.getColumnIndex("address"));
            if (phone == null || phone.length() <= 0) {
                return false;
            }
            if (phone.matches(orig)) {
                return true;
            }
            name = getContactNameFromPhone(phone);
            if (name == null || name.length() <= 0 || !name.matches(orig)) {
                return false;
            }
            return true;
        }
        phone = fi.mPhoneNum;
        name = fi.mPhoneAlphaTag;
        if (phone != null && phone.length() > 0 && phone.matches(orig)) {
            return true;
        }
        if (name == null || name.length() <= 0 || !name.matches(orig)) {
            return false;
        }
        return true;
    }

    private boolean matchOriginator(Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        String orig = ap.getFilterOriginator();
        if (orig == null || orig.length() <= 0) {
            return true;
        }
        orig = ".*" + orig.replace("*", ".*") + ".*";
        if (fi.mMsgType == 0) {
            return matchOriginatorSms(c, fi, orig);
        }
        if (fi.mMsgType == 1) {
            return matchOriginatorMms(c, fi, orig);
        }
        Log.d(TAG, "matchOriginator: Unknown msg type: " + fi.mMsgType);
        return false;
    }

    private boolean matchAddresses(Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if (matchOriginator(c, fi, ap) && matchRecipient(c, fi, ap)) {
            return true;
        }
        return false;
    }

    private String setWhereFilterFolderTypeSms(String folder) {
        String where = "";
        if ("inbox".equalsIgnoreCase(folder)) {
            return "type = 1 AND thread_id <> -1";
        }
        if ("outbox".equalsIgnoreCase(folder)) {
            return "(type = 4 OR type = 5 OR type = 6) AND thread_id <> -1";
        }
        if ("sent".equalsIgnoreCase(folder)) {
            return "type = 2 AND thread_id <> -1";
        }
        if ("draft".equalsIgnoreCase(folder)) {
            return "type = 3 AND thread_id <> -1";
        }
        if ("deleted".equalsIgnoreCase(folder)) {
            return "thread_id = -1";
        }
        return where;
    }

    private String setWhereFilterFolderTypeMms(String folder) {
        String where = "";
        if ("inbox".equalsIgnoreCase(folder)) {
            return "msg_box = 1 AND thread_id <> -1";
        }
        if ("outbox".equalsIgnoreCase(folder)) {
            return "msg_box = 4 AND thread_id <> -1";
        }
        if ("sent".equalsIgnoreCase(folder)) {
            return "msg_box = 2 AND thread_id <> -1";
        }
        if ("draft".equalsIgnoreCase(folder)) {
            return "msg_box = 3 AND thread_id <> -1";
        }
        if ("deleted".equalsIgnoreCase(folder)) {
            return "thread_id = -1";
        }
        return where;
    }

    private String setWhereFilterFolderTypeEmail(long folderId) {
        String where = "";
        if (folderId >= 0) {
            return "folder_id = " + folderId;
        }
        Log.e(TAG, "setWhereFilterFolderTypeEmail: not valid!");
        throw new IllegalArgumentException("Invalid folder ID");
    }

    private String setWhereFilterFolderType(BluetoothMapFolderElement folderElement, FilterInfo fi) {
        String where = "";
        if (fi.mMsgType == 0) {
            return setWhereFilterFolderTypeSms(folderElement.getName());
        }
        if (fi.mMsgType == 1) {
            return setWhereFilterFolderTypeMms(folderElement.getName());
        }
        if (fi.mMsgType == 2) {
            return setWhereFilterFolderTypeEmail(folderElement.getEmailFolderId());
        }
        return where;
    }

    private String setWhereFilterReadStatus(BluetoothMapAppParams ap, FilterInfo fi) {
        String where = "";
        if (ap.getFilterReadStatus() == -1) {
            return where;
        }
        if (fi.mMsgType == 0) {
            if ((ap.getFilterReadStatus() & 1) != 0) {
                where = " AND read= 0";
            }
            if ((ap.getFilterReadStatus() & 2) != 0) {
                return " AND read= 1";
            }
            return where;
        } else if (fi.mMsgType == 1) {
            if ((ap.getFilterReadStatus() & 1) != 0) {
                where = " AND read= 0";
            }
            if ((ap.getFilterReadStatus() & 2) != 0) {
                return " AND read= 1";
            }
            return where;
        } else if (fi.mMsgType != 2) {
            return where;
        } else {
            if ((ap.getFilterReadStatus() & 1) != 0) {
                where = " AND flag_read= 0";
            }
            if ((ap.getFilterReadStatus() & 2) != 0) {
                return " AND flag_read= 1";
            }
            return where;
        }
    }

    private String setWhereFilterPeriod(BluetoothMapAppParams ap, FilterInfo fi) {
        String where = "";
        if (ap.getFilterPeriodBegin() != -1) {
            if (fi.mMsgType == 0) {
                where = " AND date >= " + ap.getFilterPeriodBegin();
            } else if (fi.mMsgType == 1) {
                where = " AND date >= " + (ap.getFilterPeriodBegin() / 1000);
            } else if (fi.mMsgType == 2) {
                where = " AND date >= " + ap.getFilterPeriodBegin();
            }
        }
        if (ap.getFilterPeriodEnd() == -1) {
            return where;
        }
        if (fi.mMsgType == 0) {
            return where + " AND date < " + ap.getFilterPeriodEnd();
        }
        if (fi.mMsgType == 1) {
            return where + " AND date < " + (ap.getFilterPeriodEnd() / 1000);
        }
        if (fi.mMsgType == 2) {
            return where + " AND date < " + ap.getFilterPeriodEnd();
        }
        return where;
    }

    private String setWhereFilterPhones(String str) {
        String where = "";
        str = str.replace("*", "%");
        Cursor c = this.mResolver.query(Contacts.CONTENT_URI, null, "display_name like ?", new String[]{str}, "display_name ASC");
        loop2:
        while (c != null) {
            if (!c.moveToNext()) {
                break loop2;
            }
            String contactId = c.getString(c.getColumnIndex("_id"));
            Closeable p = this.mResolver.query(Phone.CONTENT_URI, null, "contact_id = ?", new String[]{contactId}, null);
            while (p != null) {
                try {
                    if (p.moveToNext()) {
                        where = where + " address = '" + p.getString(p.getColumnIndex("data1")) + "'";
                        if (!p.isLast()) {
                            where = where + " OR ";
                        }
                    }
                } catch (Throwable th) {
                } finally {
                    p = 
/*
Method generation error in method: com.android.bluetooth.map.BluetoothMapContent.setWhereFilterPhones(java.lang.String):java.lang.String, dex: classes.dex
jadx.core.utils.exceptions.CodegenException: Error generate insn: ?: MERGE  (r9_3 'p' java.io.Closeable) = (r9_2 android.database.Cursor), (r6_0 'c' android.database.Cursor) in method: com.android.bluetooth.map.BluetoothMapContent.setWhereFilterPhones(java.lang.String):java.lang.String, dex: classes.dex
	at jadx.core.codegen.InsnGen.makeInsn(InsnGen.java:226)
	at jadx.core.codegen.InsnGen.makeInsn(InsnGen.java:203)
	at jadx.core.codegen.RegionGen.makeSimpleBlock(RegionGen.java:100)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:50)
	at jadx.core.codegen.RegionGen.makeSimpleRegion(RegionGen.java:87)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:53)
	at jadx.core.codegen.RegionGen.makeRegionIndent(RegionGen.java:93)
	at jadx.core.codegen.RegionGen.makeTryCatch(RegionGen.java:299)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:63)
	at jadx.core.codegen.RegionGen.makeSimpleRegion(RegionGen.java:87)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:53)
	at jadx.core.codegen.RegionGen.makeRegionIndent(RegionGen.java:93)
	at jadx.core.codegen.RegionGen.makeLoop(RegionGen.java:219)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:61)
	at jadx.core.codegen.RegionGen.makeSimpleRegion(RegionGen.java:87)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:53)
	at jadx.core.codegen.RegionGen.makeSimpleRegion(RegionGen.java:87)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:53)
	at jadx.core.codegen.RegionGen.makeSimpleRegion(RegionGen.java:87)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:53)
	at jadx.core.codegen.RegionGen.makeRegionIndent(RegionGen.java:93)
	at jadx.core.codegen.RegionGen.makeLoop(RegionGen.java:219)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:61)
	at jadx.core.codegen.RegionGen.makeSimpleRegion(RegionGen.java:87)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:53)
	at jadx.core.codegen.MethodGen.addInstructions(MethodGen.java:187)
	at jadx.core.codegen.ClassGen.addMethod(ClassGen.java:320)
	at jadx.core.codegen.ClassGen.addMethods(ClassGen.java:257)
	at jadx.core.codegen.ClassGen.addClassBody(ClassGen.java:220)
	at jadx.core.codegen.ClassGen.addClassCode(ClassGen.java:110)
	at jadx.core.codegen.ClassGen.makeClass(ClassGen.java:75)
	at jadx.core.codegen.CodeGen.visit(CodeGen.java:12)
	at jadx.core.ProcessClass.process(ProcessClass.java:40)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:282)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
Caused by: jadx.core.utils.exceptions.CodegenException: MERGE can be used only in fallback mode
	at jadx.core.codegen.InsnGen.fallbackOnlyInsn(InsnGen.java:537)
	at jadx.core.codegen.InsnGen.makeInsnBody(InsnGen.java:509)
	at jadx.core.codegen.InsnGen.makeInsn(InsnGen.java:220)
	... 34 more

*/

                    private String setWhereFilterOriginatorEmail(BluetoothMapAppParams ap) {
                        String where = "";
                        String orig = ap.getFilterOriginator();
                        if (orig == null || orig.length() <= 0) {
                            return where;
                        }
                        return " AND from_list LIKE '%" + orig.replace("*", "%") + "%'";
                    }

                    private String setWhereFilterPriority(BluetoothMapAppParams ap, FilterInfo fi) {
                        String where = "";
                        int pri = ap.getFilterPriority();
                        if (fi.mMsgType != 1) {
                            return where;
                        }
                        if (pri == 2) {
                            return where + " AND pri<=" + Integer.toString(MMS_BCC);
                        }
                        if (pri == 1) {
                            return where + " AND pri=" + Integer.toString(MMS_CC);
                        }
                        return where;
                    }

                    private String setWhereFilterRecipientEmail(BluetoothMapAppParams ap) {
                        String where = "";
                        String recip = ap.getFilterRecipient();
                        if (recip == null || recip.length() <= 0) {
                            return where;
                        }
                        recip = recip.replace("*", "%");
                        return " AND (to_list LIKE '%" + recip + "%' OR " + "cc_list" + " LIKE '%" + recip + "%' OR " + "bcc_list" + " LIKE '%" + recip + "%' )";
                    }

                    private String setWhereFilter(BluetoothMapFolderElement folderElement, FilterInfo fi, BluetoothMapAppParams ap) {
                        String where = "" + setWhereFilterFolderType(folderElement, fi);
                        if (where.isEmpty()) {
                            return where;
                        }
                        where = ((where + setWhereFilterReadStatus(ap, fi)) + setWhereFilterPeriod(ap, fi)) + setWhereFilterPriority(ap, fi);
                        if (fi.mMsgType != 2) {
                            return where;
                        }
                        return (where + setWhereFilterOriginatorEmail(ap)) + setWhereFilterRecipientEmail(ap);
                    }

                    private boolean smsSelected(FilterInfo fi, BluetoothMapAppParams ap) {
                        int msgType = ap.getFilterMessageType();
                        int phoneType = fi.mPhoneType;
                        Log.d(TAG, "smsSelected msgType: " + msgType);
                        if (msgType == -1 || (msgType & 3) == 0) {
                            return true;
                        }
                        if ((msgType & 1) == 0 && phoneType == 1) {
                            return true;
                        }
                        if ((msgType & 2) == 0 && phoneType == 2) {
                            return true;
                        }
                        return false;
                    }

                    private boolean mmsSelected(FilterInfo fi, BluetoothMapAppParams ap) {
                        int msgType = ap.getFilterMessageType();
                        Log.d(TAG, "mmsSelected msgType: " + msgType);
                        if (msgType == -1 || (msgType & 8) == 0) {
                            return true;
                        }
                        return false;
                    }

                    private boolean emailSelected(FilterInfo fi, BluetoothMapAppParams ap) {
                        int msgType = ap.getFilterMessageType();
                        Log.d(TAG, "emailSelected msgType: " + msgType);
                        if (msgType == -1 || (msgType & 4) == 0) {
                            return true;
                        }
                        return false;
                    }

                    private void setFilterInfo(FilterInfo fi) {
                        TelephonyManager tm = (TelephonyManager) this.mContext.getSystemService("phone");
                        if (tm != null) {
                            fi.mPhoneType = tm.getPhoneType();
                            fi.mPhoneNum = tm.getLine1Number();
                            fi.mPhoneAlphaTag = tm.getLine1AlphaTag();
                            Log.d(TAG, "phone type = " + fi.mPhoneType + " phone num = " + fi.mPhoneNum + " phone alpha tag = " + fi.mPhoneAlphaTag);
                        }
                    }

                    public BluetoothMapMessageListing msgListing(BluetoothMapFolderElement folderElement, BluetoothMapAppParams ap) {
                        Log.d(TAG, "msgListing: folderName = " + folderElement.getName() + " folderId = " + folderElement.getEmailFolderId() + " messageType = " + ap.getFilterMessageType());
                        BluetoothMapMessageListing bmList = new BluetoothMapMessageListing();
                        if (ap.getParameterMask() == -1 || ap.getParameterMask() == 0) {
                            ap.setParameterMask(BluetoothMapAppParams.PARAMETER_MASK_ALL_ENABLED);
                        }
                        BluetoothMapContent bluetoothMapContent = this;
                        FilterInfo filterInfo = new FilterInfo();
                        setFilterInfo(filterInfo);
                        Cursor smsCursor = null;
                        Cursor mmsCursor = null;
                        Cursor emailCursor = null;
                        try {
                            String where;
                            String limit = "";
                            int countNum = ap.getMaxListCount();
                            int startOffset = ap.getStartOffset();
                            if (ap.getMaxListCount() > 0) {
                                limit = " LIMIT " + (ap.getMaxListCount() + ap.getStartOffset());
                            }
                            if (smsSelected(filterInfo, ap) && folderElement.hasSmsMmsContent()) {
                                if (ap.getFilterMessageType() == 13 || ap.getFilterMessageType() == 14) {
                                    limit = " LIMIT " + ap.getMaxListCount() + " OFFSET " + ap.getStartOffset();
                                    Log.d(TAG, "SMS Limit => " + limit);
                                    startOffset = 0;
                                }
                                filterInfo.mMsgType = 0;
                                if (ap.getFilterPriority() != 1) {
                                    where = setWhereFilter(folderElement, filterInfo, ap);
                                    if (!where.isEmpty()) {
                                        Log.d(TAG, "msgType: " + filterInfo.mMsgType);
                                        smsCursor = this.mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, where, null, "date DESC" + limit);
                                        if (smsCursor != null) {
                                            Log.d(TAG, "Found " + smsCursor.getCount() + " sms messages.");
                                            filterInfo.setSmsColumns(smsCursor);
                                            while (smsCursor.moveToNext()) {
                                                if (matchAddresses(smsCursor, filterInfo, ap)) {
                                                    bmList.add(element(smsCursor, filterInfo, ap));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (mmsSelected(filterInfo, ap) && folderElement.hasSmsMmsContent()) {
                                if (ap.getFilterMessageType() == 7) {
                                    limit = " LIMIT " + ap.getMaxListCount() + " OFFSET " + ap.getStartOffset();
                                    Log.d(TAG, "MMS Limit => " + limit);
                                    startOffset = 0;
                                }
                                filterInfo.mMsgType = 1;
                                where = setWhereFilter(folderElement, filterInfo, ap);
                                if (!where.isEmpty()) {
                                    Log.d(TAG, "msgType: " + filterInfo.mMsgType);
                                    mmsCursor = this.mResolver.query(Mms.CONTENT_URI, MMS_PROJECTION, where, null, "date DESC" + limit);
                                    if (mmsCursor != null) {
                                        filterInfo.setMmsColumns(mmsCursor);
                                        Log.d(TAG, "Found " + mmsCursor.getCount() + " mms messages.");
                                        while (mmsCursor.moveToNext()) {
                                            if (matchAddresses(mmsCursor, filterInfo, ap)) {
                                                bmList.add(element(mmsCursor, filterInfo, ap));
                                            }
                                        }
                                    }
                                }
                            }
                            if (emailSelected(filterInfo, ap) && folderElement.getEmailFolderId() != -1) {
                                if (ap.getFilterMessageType() == 11) {
                                    limit = " LIMIT " + ap.getMaxListCount() + " OFFSET " + ap.getStartOffset();
                                    Log.d(TAG, "Email Limit => " + limit);
                                    startOffset = 0;
                                }
                                filterInfo.mMsgType = 2;
                                where = setWhereFilter(folderElement, filterInfo, ap);
                                if (!where.isEmpty()) {
                                    Log.d(TAG, "msgType: " + filterInfo.mMsgType);
                                    emailCursor = this.mResolver.query(Uri.parse(this.mBaseEmailUri + "Message"), BluetoothMapContract.BT_MESSAGE_PROJECTION, where, null, "date DESC" + limit);
                                    if (emailCursor != null) {
                                        filterInfo.setEmailColumns(emailCursor);
                                        while (emailCursor.moveToNext()) {
                                            Log.d(TAG, "Found " + emailCursor.getCount() + " email messages.");
                                            bmList.add(element(emailCursor, filterInfo, ap));
                                        }
                                    }
                                }
                            }
                            bmList.sort();
                            bmList.segment(ap.getMaxListCount(), startOffset);
                            List<BluetoothMapMessageListingElement> list = bmList.getList();
                            int listSize = list.size();
                            Cursor tmpCursor = null;
                            for (int x = 0; x < listSize; x++) {
                                BluetoothMapMessageListingElement ele = (BluetoothMapMessageListingElement) list.get(x);
                                if ((ele.getType().equals(TYPE.SMS_GSM) || ele.getType().equals(TYPE.SMS_CDMA)) && smsCursor != null) {
                                    tmpCursor = smsCursor;
                                    filterInfo.mMsgType = 0;
                                } else if (ele.getType().equals(TYPE.MMS) && mmsCursor != null) {
                                    tmpCursor = mmsCursor;
                                    filterInfo.mMsgType = 1;
                                } else if (ele.getType().equals(TYPE.EMAIL) && emailCursor != null) {
                                    tmpCursor = emailCursor;
                                    filterInfo.mMsgType = 2;
                                }
                                if (tmpCursor != null) {
                                    if (tmpCursor.moveToPosition(ele.getCursorIndex())) {
                                        setSenderAddressing(ele, tmpCursor, filterInfo, ap);
                                        setSenderName(ele, tmpCursor, filterInfo, ap);
                                        setRecipientAddressing(ele, tmpCursor, filterInfo, ap);
                                        setRecipientName(ele, tmpCursor, filterInfo, ap);
                                        setSubject(ele, tmpCursor, filterInfo, ap);
                                        setSize(ele, tmpCursor, filterInfo, ap);
                                        setReceptionStatus(ele, tmpCursor, filterInfo, ap);
                                        setText(ele, tmpCursor, filterInfo, ap);
                                        setAttachmentSize(ele, tmpCursor, filterInfo, ap);
                                        setPriority(ele, tmpCursor, filterInfo, ap);
                                        setSent(ele, tmpCursor, filterInfo, ap);
                                        setProtected(ele, tmpCursor, filterInfo, ap);
                                        setThreadId(ele, tmpCursor, filterInfo, ap);
                                    }
                                }
                            }
                            Log.d(TAG, "messagelisting end");
                            return bmList;
                        } finally {
                            close(emailCursor);
                            close(smsCursor);
                            close(mmsCursor);
                        }
                    }

                    public int msgListingSize(BluetoothMapFolderElement folderElement, BluetoothMapAppParams ap) {
                        Cursor c;
                        Log.d(TAG, "msgListingSize: folder = " + folderElement.getName());
                        int cnt = 0;
                        FilterInfo fi = new FilterInfo();
                        setFilterInfo(fi);
                        if (smsSelected(fi, ap) && folderElement.hasSmsMmsContent()) {
                            fi.mMsgType = 0;
                            c = this.mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, setWhereFilter(folderElement, fi, ap), null, "date DESC");
                            if (c != null) {
                                cnt = c.getCount();
                            }
                            close(c);
                        }
                        if (mmsSelected(fi, ap) && folderElement.hasSmsMmsContent()) {
                            fi.mMsgType = 1;
                            c = this.mResolver.query(Mms.CONTENT_URI, MMS_PROJECTION, setWhereFilter(folderElement, fi, ap), null, "date DESC");
                            if (c != null) {
                                cnt += c.getCount();
                            }
                            close(c);
                        }
                        if (emailSelected(fi, ap) && folderElement.getEmailFolderId() != -1) {
                            fi.mMsgType = 2;
                            String where = setWhereFilter(folderElement, fi, ap);
                            if (!where.isEmpty()) {
                                c = this.mResolver.query(Uri.parse(this.mBaseEmailUri + "Message"), BluetoothMapContract.BT_MESSAGE_PROJECTION, where, null, "date DESC");
                                if (c != null) {
                                    cnt += c.getCount();
                                }
                                close(c);
                            }
                        }
                        Log.d(TAG, "msgListingSize: size = " + cnt);
                        return cnt;
                    }

                    public boolean msgListingHasUnread(BluetoothMapFolderElement folderElement, BluetoothMapAppParams ap) {
                        Log.d(TAG, "msgListingHasUnread: folder = " + folderElement.getName());
                        int cnt = 0;
                        FilterInfo fi = new FilterInfo();
                        setFilterInfo(fi);
                        if (smsSelected(fi, ap) && folderElement.hasSmsMmsContent()) {
                            Cursor c;
                            fi.mMsgType = 0;
                            c = this.mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, (setWhereFilterFolderType(folderElement, fi) + " AND read=0 ") + setWhereFilterPeriod(ap, fi), null, "date DESC");
                            if (c != null) {
                                cnt = 0 + c.getCount();
                            }
                            close(c);
                        }
                        if (mmsSelected(fi, ap) && folderElement.hasSmsMmsContent()) {
                            fi.mMsgType = 1;
                            c = this.mResolver.query(Mms.CONTENT_URI, MMS_PROJECTION, (setWhereFilterFolderType(folderElement, fi) + " AND read=0 ") + setWhereFilterPeriod(ap, fi), null, "date DESC");
                            if (c != null) {
                                cnt += c.getCount();
                            }
                            close(c);
                        }
                        if (emailSelected(fi, ap) && folderElement.getEmailFolderId() != -1) {
                            fi.mMsgType = 2;
                            String where = setWhereFilterFolderType(folderElement, fi);
                            if (!where.isEmpty()) {
                                c = this.mResolver.query(Uri.parse(this.mBaseEmailUri + "Message"), BluetoothMapContract.BT_MESSAGE_PROJECTION, (where + " AND flag_read=0 ") + setWhereFilterPeriod(ap, fi), null, "date DESC");
                                if (c != null) {
                                    cnt += c.getCount();
                                }
                                close(c);
                            }
                        }
                        Log.d(TAG, "msgListingHasUnread: numUnread = " + cnt);
                        return cnt > 0;
                    }

                    private String getFolderName(int type, int threadId) {
                        if (threadId == -1) {
                            return "deleted";
                        }
                        switch (type) {
                            case 1:
                                return "inbox";
                            case 2:
                                return "sent";
                            case 3:
                                return "draft";
                            case 4:
                            case 5:
                            case 6:
                                return "outbox";
                            default:
                                return "";
                        }
                    }

                    public byte[] getMessage(String handle, BluetoothMapAppParams appParams, BluetoothMapFolderElement folderElement) throws UnsupportedEncodingException {
                        TYPE type = BluetoothMapUtils.getMsgTypeFromHandle(handle);
                        long id = BluetoothMapUtils.getCpHandle(handle);
                        if (appParams.getFractionRequest() == 1) {
                            throw new IllegalArgumentException("FRACTION_REQUEST_NEXT does not make sence as we always return the full message.");
                        }
                        switch (type) {
                            case SMS_GSM:
                            case SMS_CDMA:
                                return getSmsMessage(id, appParams.getCharset());
                            case MMS:
                                return getMmsMessage(id, appParams);
                            case EMAIL:
                                return getEmailMessage(id, appParams, folderElement);
                            default:
                                throw new IllegalArgumentException("Invalid message handle.");
                        }
                    }

                    private String setVCardFromPhoneNumber(BluetoothMapbMessage message, String phone, boolean incoming) {
                        String[] phoneNumbers;
                        String contactId = null;
                        String contactName = null;
                        String[] emailAddresses = null;
                        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone));
                        String[] projection = new String[]{"_id", "display_name"};
                        Cursor p = this.mResolver.query(uri, projection, "in_visible_group=1", null, "_id ASC");
                        if (p != null) {
                            try {
                                if (p.moveToFirst()) {
                                    contactId = p.getString(p.getColumnIndex("_id"));
                                    contactName = p.getString(p.getColumnIndex("display_name"));
                                }
                            } catch (Throwable th) {
                                close(p);
                            }
                        }
                        if (contactId == null) {
                            phoneNumbers = new String[]{phone};
                        } else {
                            phoneNumbers = new String[]{phone};
                            close(p);
                            p = this.mResolver.query(Email.CONTENT_URI, null, "contact_id = ?", new String[]{contactId}, null);
                            if (p != null) {
                                emailAddresses = new String[p.getCount()];
                                int i = 0;
                                while (p != null && p.moveToNext()) {
                                    int i2 = i + 1;
                                    emailAddresses[i] = p.getString(p.getColumnIndex("data1"));
                                    i = i2;
                                }
                            }
                        }
                        close(p);
                        if (incoming) {
                            message.addOriginator(contactName, contactName, phoneNumbers, emailAddresses);
                        } else {
                            message.addRecipient(contactName, contactName, phoneNumbers, emailAddresses);
                        }
                        return contactName;
                    }

                    public byte[] getSmsMessage(long id, int charset) throws UnsupportedEncodingException {
                        BluetoothMapbMessageSms message = new BluetoothMapbMessageSms();
                        TelephonyManager tm = (TelephonyManager) this.mContext.getSystemService("phone");
                        Cursor c = this.mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, "_ID = " + id, null, null);
                        if (c == null || !c.moveToFirst()) {
                            throw new IllegalArgumentException("SMS handle not found");
                        }
                        try {
                            if (tm.getPhoneType() == 1) {
                                message.setType(TYPE.SMS_GSM);
                            } else if (tm.getPhoneType() == 2) {
                                message.setType(TYPE.SMS_CDMA);
                            }
                            if (c.getString(c.getColumnIndex("read")).equalsIgnoreCase("1")) {
                                message.setStatus(true);
                            } else {
                                message.setStatus(false);
                            }
                            int type = c.getInt(c.getColumnIndex("type"));
                            message.setFolder(getFolderName(type, c.getInt(c.getColumnIndex("thread_id"))));
                            String msgBody = c.getString(c.getColumnIndex("body"));
                            String phone = c.getString(c.getColumnIndex("address"));
                            long time = c.getLong(c.getColumnIndex("date"));
                            if (type == 1) {
                                setVCardFromPhoneNumber(message, phone, true);
                            } else {
                                setVCardFromPhoneNumber(message, phone, false);
                            }
                            if (charset != 0) {
                                message.setSmsBody(msgBody);
                            } else if (type == 1) {
                                message.setSmsBodyPdus(BluetoothMapSmsPdu.getDeliverPdus(msgBody, phone, time));
                            } else {
                                message.setSmsBodyPdus(BluetoothMapSmsPdu.getSubmitPdus(msgBody, phone));
                            }
                            close(c);
                            return message.encode();
                        } catch (Throwable th) {
                            close(c);
                        }
                    }

                    private void extractMmsAddresses(long id, BluetoothMapbMessageMms message) {
                        String selection = new String("msg_id=" + id);
                        Cursor c = this.mResolver.query(Uri.parse(new String(Mms.CONTENT_URI + "/" + id + "/addr")), null, selection, null, null);
                        while (c != null) {
                            if (c.moveToNext()) {
                                String address = c.getString(c.getColumnIndex("address"));
                                if (!address.equals(INSERT_ADDRES_TOKEN)) {
                                    switch (Integer.valueOf(c.getInt(c.getColumnIndex("type"))).intValue()) {
                                        case MMS_BCC /*129*/:
                                            message.addBcc(setVCardFromPhoneNumber(message, address, false), address);
                                            break;
                                        case MMS_CC /*130*/:
                                            message.addCc(setVCardFromPhoneNumber(message, address, false), address);
                                            break;
                                        case MMS_FROM /*137*/:
                                            try {
                                                message.addFrom(setVCardFromPhoneNumber(message, address, true), address);
                                                break;
                                            } catch (Throwable th) {
                                                close(c);
                                            }
                                        case MMS_TO /*151*/:
                                            message.addTo(setVCardFromPhoneNumber(message, address, false), address);
                                            break;
                                        default:
                                            break;
                                    }
                                }
                            } else {
                                close(c);
                            }
                        }
                        close(c);
                    }

                    private byte[] readMmsDataPart(long partid) {
                        Uri uriAddress = Uri.parse(new String(Mms.CONTENT_URI + "/part/" + partid));
                        InputStream is = null;
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        byte[] buffer = new byte[MASK_SENT];
                        byte[] retVal = null;
                        try {
                            is = this.mResolver.openInputStream(uriAddress);
                            while (true) {
                                int len = is.read(buffer);
                                if (len == -1) {
                                    break;
                                }
                                os.write(buffer, 0, len);
                            }
                            retVal = os.toByteArray();
                        } catch (IOException e) {
                            Log.w(TAG, "Error reading part data", e);
                        } finally {
                            close(os);
                            close(is);
                        }
                        return retVal;
                    }

                    private void extractMmsParts(long id, BluetoothMapbMessageMms message) {
                        String selection = new String("mid=" + id);
                        Cursor c = this.mResolver.query(Uri.parse(new String(Mms.CONTENT_URI + "/" + id + "/part")), null, selection, null, null);
                        while (c != null) {
                            if (!c.moveToNext()) {
                                break;
                            }
                            Long partId = Long.valueOf(c.getLong(c.getColumnIndex("_id")));
                            String contentType = c.getString(c.getColumnIndex("ct"));
                            String name = c.getString(c.getColumnIndex("name"));
                            String charset = c.getString(c.getColumnIndex("chset"));
                            String filename = c.getString(c.getColumnIndex("fn"));
                            String text = c.getString(c.getColumnIndex("text"));
                            Integer fd = Integer.valueOf(c.getInt(c.getColumnIndex(BluetoothShare._DATA)));
                            String cid = c.getString(c.getColumnIndex("cid"));
                            String cl = c.getString(c.getColumnIndex("cl"));
                            String cdisp = c.getString(c.getColumnIndex("cd"));
                            MimePart part = message.addMimePart();
                            part.mContentType = contentType;
                            part.mPartName = name;
                            part.mContentId = cid;
                            part.mContentLocation = cl;
                            part.mContentDisposition = cdisp;
                            if (text != null) {
                                try {
                                    part.mData = text.getBytes("UTF-8");
                                    part.mCharsetName = "utf-8";
                                } catch (NumberFormatException e) {
                                    Log.d(TAG, "extractMmsParts", e);
                                    part.mData = null;
                                    part.mCharsetName = null;
                                } catch (UnsupportedEncodingException e2) {
                                    Log.d(TAG, "extractMmsParts", e2);
                                    part.mData = null;
                                    part.mCharsetName = null;
                                } catch (Throwable th) {
                                    close(c);
                                }
                            } else {
                                part.mData = readMmsDataPart(partId.longValue());
                                if (charset != null) {
                                    part.mCharsetName = CharacterSets.getMimeName(Integer.parseInt(charset));
                                }
                            }
                            part.mFileName = filename;
                        }
                        close(c);
                        message.updateCharset();
                    }

                    public byte[] getMmsMessage(long id, BluetoothMapAppParams appParams) throws UnsupportedEncodingException {
                        if (appParams.getCharset() == 0) {
                            throw new IllegalArgumentException("MMS charset native not allowed for MMS - must be utf-8");
                        }
                        BluetoothMapbMessageMms message = new BluetoothMapbMessageMms();
                        Cursor c = this.mResolver.query(Mms.CONTENT_URI, MMS_PROJECTION, "_ID = " + id, null, null);
                        if (c == null || !c.moveToFirst()) {
                            throw new IllegalArgumentException("MMS handle not found");
                        }
                        try {
                            message.setType(TYPE.MMS);
                            if (c.getString(c.getColumnIndex("read")).equalsIgnoreCase("1")) {
                                message.setStatus(true);
                            } else {
                                message.setStatus(false);
                            }
                            message.setFolder(getFolderName(c.getInt(c.getColumnIndex("msg_box")), c.getInt(c.getColumnIndex("thread_id"))));
                            message.setSubject(c.getString(c.getColumnIndex("sub")));
                            message.setMessageId(c.getString(c.getColumnIndex("m_id")));
                            message.setContentType(c.getString(c.getColumnIndex("ct_t")));
                            message.setDate(c.getLong(c.getColumnIndex("date")) * 1000);
                            message.setTextOnly(c.getInt(c.getColumnIndex("text_only")) != 0);
                            message.setIncludeAttachments(appParams.getAttachment() != 0);
                            extractMmsParts(id, message);
                            extractMmsAddresses(id, message);
                            return message.encode();
                        } finally {
                            close(c);
                        }
                    }

                    public byte[] getEmailMessage(long id, BluetoothMapAppParams appParams, BluetoothMapFolderElement currentFolder) throws UnsupportedEncodingException {
                        FileInputStream is;
                        FileNotFoundException e;
                        NullPointerException e2;
                        IOException e3;
                        Throwable th;
                        if (appParams != null) {
                            Log.d(TAG, "TYPE_MESSAGE (GET): Attachment = " + appParams.getAttachment() + ", Charset = " + appParams.getCharset() + ", FractionRequest = " + appParams.getFractionRequest());
                        }
                        if (appParams.getCharset() == 0) {
                            throw new IllegalArgumentException("EMAIL charset not UTF-8");
                        }
                        BluetoothMapbMessageEmail message = new BluetoothMapbMessageEmail();
                        Uri contentUri = Uri.parse(this.mBaseEmailUri + "Message");
                        Cursor c = this.mResolver.query(contentUri, BluetoothMapContract.BT_MESSAGE_PROJECTION, "_ID = " + id, null, null);
                        if (c == null || !c.moveToFirst()) {
                            ParcelFileDescriptor parcelFileDescriptor;
                            try {
                                Rfc822Token[] tokens;
                                String[] emails;
                                String name;
                                FileInputStream fileInputStream;
                                StringBuilder email;
                                byte[] buffer;
                                int count;
                                if (!(appParams.getFractionRequest() == -1 || c.getString(c.getColumnIndex("reception_state")).equalsIgnoreCase("complete"))) {
                                    Log.w(TAG, "getEmailMessage - receptionState not COMPLETE -  Not Implemented!");
                                }
                                String read = c.getString(c.getColumnIndex("flag_read"));
                                if (read != null) {
                                    if (read.equalsIgnoreCase("1")) {
                                        message.setStatus(true);
                                        message.setType(TYPE.EMAIL);
                                        message.setCompleteFolder(currentFolder.getEmailFolderById(c.getLong(c.getColumnIndex("folder_id"))).getFullPath());
                                        tokens = Rfc822Tokenizer.tokenize(c.getString(c.getColumnIndex("to_list")));
                                        if (tokens.length != 0) {
                                            Log.d(TAG, "Recipient count= " + tokens.length);
                                            for (Rfc822Token name2 : tokens) {
                                                emails = new String[]{tokens[i].getAddress()};
                                                name = name2.getName();
                                                message.addRecipient(name, name, null, emails);
                                            }
                                        }
                                        tokens = Rfc822Tokenizer.tokenize(c.getString(c.getColumnIndex("from_list")));
                                        if (tokens.length != 0) {
                                            Log.d(TAG, "Originator count= " + tokens.length);
                                            for (Rfc822Token name22 : tokens) {
                                                emails = new String[]{tokens[i].getAddress()};
                                                name = name22.getName();
                                                message.addOriginator(name, name, null, emails);
                                            }
                                        }
                                        is = null;
                                        parcelFileDescriptor = null;
                                        parcelFileDescriptor = this.mResolver.openFileDescriptor(Uri.parse(contentUri + "/" + id + (appParams.getAttachment() != 0 ? "/NO_ATTACHMENTS" : "")), "r");
                                        fileInputStream = new FileInputStream(parcelFileDescriptor.getFileDescriptor());
                                        try {
                                            email = new StringBuilder("");
                                            buffer = new byte[MASK_ATTACHMENT_SIZE];
                                            while (true) {
                                                count = fileInputStream.read(buffer);
                                                if (count != -1) {
                                                    break;
                                                }
                                                email.append(new String(buffer, 0, count));
                                            }
                                            message.setEmailBody(email.toString());
                                            close(fileInputStream);
                                            close(parcelFileDescriptor);
                                            is = fileInputStream;
                                        } catch (FileNotFoundException e4) {
                                            e = e4;
                                            is = fileInputStream;
                                            Log.w(TAG, e);
                                            close(is);
                                            close(parcelFileDescriptor);
                                            close(c);
                                            return message.encode();
                                        } catch (NullPointerException e5) {
                                            e2 = e5;
                                            is = fileInputStream;
                                            Log.w(TAG, e2);
                                            close(is);
                                            close(parcelFileDescriptor);
                                            close(c);
                                            return message.encode();
                                        } catch (IOException e6) {
                                            e3 = e6;
                                            is = fileInputStream;
                                            Log.w(TAG, e3);
                                            close(is);
                                            close(parcelFileDescriptor);
                                            close(c);
                                            return message.encode();
                                        } catch (Throwable th2) {
                                            th = th2;
                                            is = fileInputStream;
                                            close(is);
                                            close(parcelFileDescriptor);
                                            throw th;
                                        }
                                        close(c);
                                        return message.encode();
                                    }
                                }
                                message.setStatus(false);
                                message.setType(TYPE.EMAIL);
                                message.setCompleteFolder(currentFolder.getEmailFolderById(c.getLong(c.getColumnIndex("folder_id"))).getFullPath());
                                tokens = Rfc822Tokenizer.tokenize(c.getString(c.getColumnIndex("to_list")));
                                if (tokens.length != 0) {
                                    Log.d(TAG, "Recipient count= " + tokens.length);
                                    while (i < tokens.length) {
                                        emails = new String[]{tokens[i].getAddress()};
                                        name = name22.getName();
                                        message.addRecipient(name, name, null, emails);
                                    }
                                }
                                tokens = Rfc822Tokenizer.tokenize(c.getString(c.getColumnIndex("from_list")));
                                if (tokens.length != 0) {
                                    Log.d(TAG, "Originator count= " + tokens.length);
                                    while (i < tokens.length) {
                                        emails = new String[]{tokens[i].getAddress()};
                                        name = name22.getName();
                                        message.addOriginator(name, name, null, emails);
                                    }
                                }
                                if (appParams.getAttachment() != 0) {
                                }
                                is = null;
                                parcelFileDescriptor = null;
                                try {
                                    parcelFileDescriptor = this.mResolver.openFileDescriptor(Uri.parse(contentUri + "/" + id + (appParams.getAttachment() != 0 ? "/NO_ATTACHMENTS" : "")), "r");
                                    fileInputStream = new FileInputStream(parcelFileDescriptor.getFileDescriptor());
                                    email = new StringBuilder("");
                                    buffer = new byte[MASK_ATTACHMENT_SIZE];
                                    while (true) {
                                        count = fileInputStream.read(buffer);
                                        if (count != -1) {
                                            break;
                                        }
                                        email.append(new String(buffer, 0, count));
                                    }
                                    message.setEmailBody(email.toString());
                                    close(fileInputStream);
                                    close(parcelFileDescriptor);
                                    is = fileInputStream;
                                } catch (FileNotFoundException e7) {
                                    e = e7;
                                    Log.w(TAG, e);
                                    close(is);
                                    close(parcelFileDescriptor);
                                    close(c);
                                    return message.encode();
                                } catch (NullPointerException e8) {
                                    e2 = e8;
                                    Log.w(TAG, e2);
                                    close(is);
                                    close(parcelFileDescriptor);
                                    close(c);
                                    return message.encode();
                                } catch (IOException e9) {
                                    e3 = e9;
                                    Log.w(TAG, e3);
                                    close(is);
                                    close(parcelFileDescriptor);
                                    close(c);
                                    return message.encode();
                                }
                                close(c);
                                return message.encode();
                            } catch (Throwable th3) {
                                close(c);
                            }
                        } else {
                            throw new IllegalArgumentException("EMAIL handle not found");
                        }
                    }
                }
