package com.android.bluetooth.map;

import android.util.Log;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class BluetoothMapAppParams {
    private static final int ATTACHMENT = 10;
    private static final int ATTACHMENT_LEN = 1;
    private static final int CHARSET = 20;
    private static final int CHARSET_LEN = 1;
    public static final int CHARSET_NATIVE = 0;
    public static final int CHARSET_UTF8 = 1;
    private static final int FILTER_MESSAGE_TYPE = 3;
    private static final int FILTER_MESSAGE_TYPE_LEN = 1;
    public static final int FILTER_NO_EMAIL = 4;
    public static final int FILTER_NO_MMS = 8;
    public static final int FILTER_NO_SMS_CDMA = 2;
    public static final int FILTER_NO_SMS_GSM = 1;
    private static final int FILTER_ORIGINATOR = 8;
    private static final int FILTER_PERIOD_BEGIN = 4;
    private static final int FILTER_PERIOD_END = 5;
    private static final int FILTER_PRIORITY = 9;
    private static final int FILTER_PRIORITY_LEN = 1;
    private static final int FILTER_READ_STATUS = 6;
    private static final int FILTER_READ_STATUS_LEN = 1;
    private static final int FILTER_RECIPIENT = 7;
    private static final int FOLDER_LISTING_SIZE = 17;
    private static final int FOLDER_LISTING_SIZE_LEN = 2;
    private static final int FRACTION_DELIVER = 22;
    public static final int FRACTION_DELIVER_LAST = 1;
    private static final int FRACTION_DELIVER_LEN = 1;
    public static final int FRACTION_DELIVER_MORE = 0;
    private static final int FRACTION_REQUEST = 21;
    public static final int FRACTION_REQUEST_FIRST = 0;
    private static final int FRACTION_REQUEST_LEN = 1;
    public static final int FRACTION_REQUEST_NEXT = 1;
    public static final int INVALID_VALUE_PARAMETER = -1;
    private static final int MAS_INSTANCE_ID = 15;
    private static final int MAS_INSTANCE_ID_LEN = 1;
    private static final int MAX_LIST_COUNT = 1;
    private static final int MAX_LIST_COUNT_LEN = 2;
    private static final int MESSAGE_LISTING_SIZE = 18;
    private static final int MESSAGE_LISTING_SIZE_LEN = 2;
    private static final int MSE_TIME = 25;
    private static final int NEW_MESSAGE = 13;
    private static final int NEW_MESSAGE_LEN = 1;
    private static final int NOTIFICATION_STATUS = 14;
    private static final int NOTIFICATION_STATUS_LEN = 1;
    public static final int NOTIFICATION_STATUS_NO = 0;
    public static final int NOTIFICATION_STATUS_YES = 1;
    private static final int PARAMETER_MASK = 16;
    public static final long PARAMETER_MASK_ALL_ENABLED = 65535;
    private static final int PARAMETER_MASK_LEN = 4;
    private static final int RETRY = 12;
    private static final int RETRY_LEN = 1;
    private static final int START_OFFSET = 2;
    private static final int START_OFFSET_LEN = 2;
    private static final int STATUS_INDICATOR = 23;
    public static final int STATUS_INDICATOR_DELETED = 1;
    private static final int STATUS_INDICATOR_LEN = 1;
    public static final int STATUS_INDICATOR_READ = 0;
    private static final int STATUS_VALUE = 24;
    private static final int STATUS_VALUE_LEN = 1;
    public static final int STATUS_VALUE_NO = 0;
    public static final int STATUS_VALUE_YES = 1;
    private static final int SUBJECT_LENGTH = 19;
    private static final int SUBJECT_LENGTH_LEN = 1;
    private static final String TAG = "BluetoothMapAppParams";
    private static final int TRANSPARENT = 11;
    private static final int TRANSPARENT_LEN = 1;
    private int mAttachment = -1;
    private int mCharset = -1;
    private int mFilterMessageType = -1;
    private String mFilterOriginator = null;
    private long mFilterPeriodBegin = -1;
    private long mFilterPeriodEnd = -1;
    private int mFilterPriority = -1;
    private int mFilterReadStatus = -1;
    private String mFilterRecipient = null;
    private int mFolderListingSize = -1;
    private int mFractionDeliver = -1;
    private int mFractionRequest = -1;
    private int mMasInstanceId = -1;
    private int mMaxListCount = -1;
    private int mMessageListingSize = -1;
    private long mMseTime = -1;
    private int mNewMessage = -1;
    private int mNotificationStatus = -1;
    private long mParameterMask = -1;
    private int mRetry = -1;
    private int mStartOffset = -1;
    private int mStatusIndicator = -1;
    private int mStatusValue = -1;
    private int mSubjectLength = -1;
    private int mTransparent = -1;

    public BluetoothMapAppParams(byte[] appParams) throws IllegalArgumentException, ParseException {
        ParseParams(appParams);
    }

    private void ParseParams(byte[] appParams) throws ParseException, IllegalArgumentException {
        int i = 0;
        ByteBuffer appParamBuf = ByteBuffer.wrap(appParams);
        appParamBuf.order(ByteOrder.BIG_ENDIAN);
        while (i < appParams.length) {
            int i2 = i + 1;
            int tagId = appParams[i] & 255;
            i = i2 + 1;
            int tagLength = appParams[i2] & 255;
            switch (tagId) {
                case 1:
                    if (tagLength == 2) {
                        setMaxListCount(appParamBuf.getShort(i) & 65535);
                        break;
                    } else {
                        Log.w(TAG, "MAX_LIST_COUNT: Wrong length received: " + tagLength + " expected: " + 2);
                        break;
                    }
                case 2:
                    if (tagLength == 2) {
                        setStartOffset(appParamBuf.getShort(i) & 65535);
                        break;
                    } else {
                        Log.w(TAG, "START_OFFSET: Wrong length received: " + tagLength + " expected: " + 2);
                        break;
                    }
                case 3:
                    if (tagLength == 1) {
                        setFilterMessageType(appParams[i] & 15);
                        break;
                    } else {
                        Log.w(TAG, "FILTER_MESSAGE_TYPE: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 4:
                    if (tagLength == 0) {
                        break;
                    }
                    setFilterPeriodBegin(new String(appParams, i, tagLength));
                    break;
                case 5:
                    if (tagLength == 0) {
                        break;
                    }
                    setFilterPeriodEnd(new String(appParams, i, tagLength));
                    break;
                case 6:
                    if (tagLength == 1) {
                        setFilterReadStatus(appParams[i] & 3);
                        break;
                    } else {
                        Log.w(TAG, "FILTER_READ_STATUS: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 7:
                    if (tagLength == 0) {
                        break;
                    }
                    setFilterRecipient(new String(appParams, i, tagLength));
                    break;
                case 8:
                    if (tagLength == 0) {
                        break;
                    }
                    setFilterOriginator(new String(appParams, i, tagLength));
                    break;
                case 9:
                    if (tagLength == 1) {
                        setFilterPriority(appParams[i] & 3);
                        break;
                    } else {
                        Log.w(TAG, "FILTER_PRIORITY: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 10:
                    if (tagLength == 1) {
                        setAttachment(appParams[i] & 1);
                        break;
                    } else {
                        Log.w(TAG, "ATTACHMENT: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 11:
                    if (tagLength == 1) {
                        setTransparent(appParams[i] & 1);
                        break;
                    } else {
                        Log.w(TAG, "TRANSPARENT: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 12:
                    if (tagLength == 1) {
                        setRetry(appParams[i] & 1);
                        break;
                    } else {
                        Log.w(TAG, "RETRY: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 13:
                    if (tagLength == 1) {
                        setNewMessage(appParams[i] & 1);
                        break;
                    } else {
                        Log.w(TAG, "NEW_MESSAGE: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 14:
                    if (tagLength == 1) {
                        setNotificationStatus(appParams[i] & 1);
                        break;
                    } else {
                        Log.w(TAG, "NOTIFICATION_STATUS: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 15:
                    if (tagLength == 1) {
                        setMasInstanceId(appParams[i] & 255);
                        break;
                    } else {
                        Log.w(TAG, "MAS_INSTANCE_ID: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 16:
                    if (tagLength == 4) {
                        setParameterMask(((long) appParamBuf.getInt(i)) & 4294967295L);
                        break;
                    } else {
                        Log.w(TAG, "PARAMETER_MASK: Wrong length received: " + tagLength + " expected: " + 4);
                        break;
                    }
                case 17:
                    if (tagLength == 2) {
                        setFolderListingSize(appParamBuf.getShort(i) & 65535);
                        break;
                    } else {
                        Log.w(TAG, "FOLDER_LISTING_SIZE: Wrong length received: " + tagLength + " expected: " + 2);
                        break;
                    }
                case 18:
                    if (tagLength == 2) {
                        setMessageListingSize(appParamBuf.getShort(i) & 65535);
                        break;
                    } else {
                        Log.w(TAG, "MESSAGE_LISTING_SIZE: Wrong length received: " + tagLength + " expected: " + 2);
                        break;
                    }
                case SUBJECT_LENGTH /*19*/:
                    if (tagLength == 1) {
                        setSubjectLength(appParams[i] & 255);
                        break;
                    } else {
                        Log.w(TAG, "SUBJECT_LENGTH: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 20:
                    if (tagLength == 1) {
                        setCharset(appParams[i] & 1);
                        break;
                    } else {
                        Log.w(TAG, "CHARSET: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 21:
                    if (tagLength == 1) {
                        setFractionRequest(appParams[i] & 1);
                        break;
                    } else {
                        Log.w(TAG, "FRACTION_REQUEST: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case FRACTION_DELIVER /*22*/:
                    if (tagLength == 1) {
                        setFractionDeliver(appParams[i] & 1);
                        break;
                    } else {
                        Log.w(TAG, "FRACTION_DELIVER: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 23:
                    if (tagLength == 1) {
                        setStatusIndicator(appParams[i] & 1);
                        break;
                    } else {
                        Log.w(TAG, "STATUS_INDICATOR: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 24:
                    if (tagLength == 1) {
                        setStatusValue(appParams[i] & 1);
                        break;
                    } else {
                        Log.w(TAG, "STATUS_VALUER: Wrong length received: " + tagLength + " expected: " + 1);
                        break;
                    }
                case 25:
                    setMseTime(new String(appParams, i, tagLength));
                    break;
                default:
                    Log.w(TAG, "Unknown TagId received ( 0x" + Integer.toString(tagId, 16) + "), skipping...");
                    break;
            }
            i += tagLength;
        }
    }

    private int getParamMaxLength() throws UnsupportedEncodingException {
        int i = 15;
        int i2 = 0;
        int length = (0 + 50) + 27;
        length = (getFilterPeriodBegin() == -1 ? 0 : 15) + 77;
        if (getFilterPeriodEnd() == -1) {
            i = 0;
        }
        length += i;
        if (getFilterRecipient() != null) {
            length += getFilterRecipient().getBytes("UTF-8").length;
        }
        if (getFilterOriginator() != null) {
            length += getFilterOriginator().getBytes("UTF-8").length;
        }
        if (getMseTime() != -1) {
            i2 = 20;
        }
        return length + i2;
    }

    public byte[] EncodeParams() throws UnsupportedEncodingException {
        ByteBuffer appParamBuf = ByteBuffer.allocate(getParamMaxLength());
        appParamBuf.order(ByteOrder.BIG_ENDIAN);
        if (getMaxListCount() != -1) {
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) 2);
            appParamBuf.putShort((short) getMaxListCount());
        }
        if (getStartOffset() != -1) {
            appParamBuf.put((byte) 2);
            appParamBuf.put((byte) 2);
            appParamBuf.putShort((short) getStartOffset());
        }
        if (getFilterMessageType() != -1) {
            appParamBuf.put((byte) 3);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getFilterMessageType());
        }
        if (getFilterPeriodBegin() != -1) {
            appParamBuf.put((byte) 4);
            appParamBuf.put((byte) getFilterPeriodBeginString().getBytes("UTF-8").length);
            appParamBuf.put(getFilterPeriodBeginString().getBytes("UTF-8"));
        }
        if (getFilterPeriodEnd() != -1) {
            appParamBuf.put((byte) 5);
            appParamBuf.put((byte) getFilterPeriodEndString().getBytes("UTF-8").length);
            appParamBuf.put(getFilterPeriodEndString().getBytes("UTF-8"));
        }
        if (getFilterReadStatus() != -1) {
            appParamBuf.put((byte) 6);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getFilterReadStatus());
        }
        if (getFilterRecipient() != null) {
            appParamBuf.put((byte) 7);
            appParamBuf.put((byte) getFilterRecipient().getBytes("UTF-8").length);
            appParamBuf.put(getFilterRecipient().getBytes("UTF-8"));
        }
        if (getFilterOriginator() != null) {
            appParamBuf.put((byte) 8);
            appParamBuf.put((byte) getFilterOriginator().getBytes("UTF-8").length);
            appParamBuf.put(getFilterOriginator().getBytes("UTF-8"));
        }
        if (getFilterPriority() != -1) {
            appParamBuf.put((byte) 9);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getFilterPriority());
        }
        if (getAttachment() != -1) {
            appParamBuf.put((byte) 10);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getAttachment());
        }
        if (getTransparent() != -1) {
            appParamBuf.put((byte) 11);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getTransparent());
        }
        if (getRetry() != -1) {
            appParamBuf.put((byte) 12);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getRetry());
        }
        if (getNewMessage() != -1) {
            appParamBuf.put((byte) 13);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getNewMessage());
        }
        if (getNotificationStatus() != -1) {
            appParamBuf.put((byte) 14);
            appParamBuf.put((byte) 1);
            appParamBuf.putShort((short) getNotificationStatus());
        }
        if (getMasInstanceId() != -1) {
            appParamBuf.put((byte) 15);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getMasInstanceId());
        }
        if (getParameterMask() != -1) {
            appParamBuf.put((byte) 16);
            appParamBuf.put((byte) 4);
            appParamBuf.putInt((int) getParameterMask());
        }
        if (getFolderListingSize() != -1) {
            appParamBuf.put((byte) 17);
            appParamBuf.put((byte) 2);
            appParamBuf.putShort((short) getFolderListingSize());
        }
        if (getMessageListingSize() != -1) {
            appParamBuf.put((byte) 18);
            appParamBuf.put((byte) 2);
            appParamBuf.putShort((short) getMessageListingSize());
        }
        if (getSubjectLength() != -1) {
            appParamBuf.put((byte) 19);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getSubjectLength());
        }
        if (getCharset() != -1) {
            appParamBuf.put((byte) 20);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getCharset());
        }
        if (getFractionRequest() != -1) {
            appParamBuf.put((byte) 21);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getFractionRequest());
        }
        if (getFractionDeliver() != -1) {
            appParamBuf.put((byte) 22);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getFractionDeliver());
        }
        if (getStatusIndicator() != -1) {
            appParamBuf.put((byte) 23);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getStatusIndicator());
        }
        if (getStatusValue() != -1) {
            appParamBuf.put((byte) 24);
            appParamBuf.put((byte) 1);
            appParamBuf.put((byte) getStatusValue());
        }
        if (getMseTime() != -1) {
            appParamBuf.put((byte) 25);
            appParamBuf.put((byte) getMseTimeString().getBytes("UTF-8").length);
            appParamBuf.put(getMseTimeString().getBytes("UTF-8"));
        }
        return Arrays.copyOfRange(appParamBuf.array(), appParamBuf.arrayOffset(), appParamBuf.arrayOffset() + appParamBuf.position());
    }

    public int getMaxListCount() {
        return this.mMaxListCount;
    }

    public void setMaxListCount(int maxListCount) throws IllegalArgumentException {
        if (maxListCount < 0 || maxListCount > 65535) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0xFFFF");
        }
        this.mMaxListCount = maxListCount;
    }

    public int getStartOffset() {
        return this.mStartOffset;
    }

    public void setStartOffset(int startOffset) throws IllegalArgumentException {
        if (startOffset < 0 || startOffset > 65535) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0xFFFF");
        }
        this.mStartOffset = startOffset;
    }

    public int getFilterMessageType() {
        return this.mFilterMessageType;
    }

    public void setFilterMessageType(int filterMessageType) throws IllegalArgumentException {
        if (filterMessageType < 0 || filterMessageType > 15) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x000F");
        }
        this.mFilterMessageType = filterMessageType;
    }

    public long getFilterPeriodBegin() {
        return this.mFilterPeriodBegin;
    }

    public String getFilterPeriodBeginString() {
        return new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date(this.mFilterPeriodBegin));
    }

    public void setFilterPeriodBegin(long filterPeriodBegin) {
        this.mFilterPeriodBegin = filterPeriodBegin;
    }

    public void setFilterPeriodBegin(String filterPeriodBegin) throws ParseException {
        this.mFilterPeriodBegin = new SimpleDateFormat("yyyyMMdd'T'HHmmss").parse(filterPeriodBegin).getTime();
    }

    public long getFilterPeriodEnd() {
        return this.mFilterPeriodEnd;
    }

    public String getFilterPeriodEndString() {
        return new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date(this.mFilterPeriodEnd));
    }

    public void setFilterPeriodEnd(long filterPeriodEnd) {
        this.mFilterPeriodEnd = filterPeriodEnd;
    }

    public void setFilterPeriodEnd(String filterPeriodEnd) throws ParseException {
        this.mFilterPeriodEnd = new SimpleDateFormat("yyyyMMdd'T'HHmmss").parse(filterPeriodEnd).getTime();
    }

    public int getFilterReadStatus() {
        return this.mFilterReadStatus;
    }

    public void setFilterReadStatus(int filterReadStatus) throws IllegalArgumentException {
        if (filterReadStatus < 0 || filterReadStatus > 2) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0002");
        }
        this.mFilterReadStatus = filterReadStatus;
    }

    public String getFilterRecipient() {
        return this.mFilterRecipient;
    }

    public void setFilterRecipient(String filterRecipient) {
        this.mFilterRecipient = filterRecipient;
    }

    public String getFilterOriginator() {
        return this.mFilterOriginator;
    }

    public void setFilterOriginator(String filterOriginator) {
        this.mFilterOriginator = filterOriginator;
    }

    public int getFilterPriority() {
        return this.mFilterPriority;
    }

    public void setFilterPriority(int filterPriority) throws IllegalArgumentException {
        if (filterPriority < 0 || filterPriority > 2) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0002");
        }
        this.mFilterPriority = filterPriority;
    }

    public int getAttachment() {
        return this.mAttachment;
    }

    public void setAttachment(int attachment) throws IllegalArgumentException {
        if (attachment < 0 || attachment > 1) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0001");
        }
        this.mAttachment = attachment;
    }

    public int getTransparent() {
        return this.mTransparent;
    }

    public void setTransparent(int transparent) throws IllegalArgumentException {
        if (transparent < 0 || transparent > 1) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0001");
        }
        this.mTransparent = transparent;
    }

    public int getRetry() {
        return this.mRetry;
    }

    public void setRetry(int retry) throws IllegalArgumentException {
        if (retry < 0 || retry > 1) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0001");
        }
        this.mRetry = retry;
    }

    public int getNewMessage() {
        return this.mNewMessage;
    }

    public void setNewMessage(int newMessage) throws IllegalArgumentException {
        if (newMessage < 0 || newMessage > 1) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0001");
        }
        this.mNewMessage = newMessage;
    }

    public int getNotificationStatus() {
        return this.mNotificationStatus;
    }

    public void setNotificationStatus(int notificationStatus) throws IllegalArgumentException {
        if (notificationStatus < 0 || notificationStatus > 1) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0001");
        }
        this.mNotificationStatus = notificationStatus;
    }

    public int getMasInstanceId() {
        return this.mMasInstanceId;
    }

    public void setMasInstanceId(int masInstanceId) {
        if (masInstanceId < 0 || masInstanceId > 255) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x00FF");
        }
        this.mMasInstanceId = masInstanceId;
    }

    public long getParameterMask() {
        return this.mParameterMask;
    }

    public void setParameterMask(long parameterMask) {
        if (parameterMask < 0 || parameterMask > 4294967295L) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0xFFFFFFFF");
        }
        this.mParameterMask = parameterMask;
    }

    public int getFolderListingSize() {
        return this.mFolderListingSize;
    }

    public void setFolderListingSize(int folderListingSize) {
        if (folderListingSize < 0 || folderListingSize > 65535) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0xFFFF");
        }
        this.mFolderListingSize = folderListingSize;
    }

    public int getMessageListingSize() {
        return this.mMessageListingSize;
    }

    public void setMessageListingSize(int messageListingSize) {
        if (messageListingSize < 0 || messageListingSize > 65535) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0xFFFF");
        }
        this.mMessageListingSize = messageListingSize;
    }

    public int getSubjectLength() {
        return this.mSubjectLength;
    }

    public void setSubjectLength(int subjectLength) {
        if (subjectLength < 0 || subjectLength > 255) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x00FF");
        }
        this.mSubjectLength = subjectLength;
    }

    public int getCharset() {
        return this.mCharset;
    }

    public void setCharset(int charset) {
        if (charset < 0 || charset > 1) {
            throw new IllegalArgumentException("Out of range: " + charset + ", valid range is 0x0000 to 0x0001");
        }
        this.mCharset = charset;
    }

    public int getFractionRequest() {
        return this.mFractionRequest;
    }

    public void setFractionRequest(int fractionRequest) {
        if (fractionRequest < 0 || fractionRequest > 1) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0001");
        }
        this.mFractionRequest = fractionRequest;
    }

    public int getFractionDeliver() {
        return this.mFractionDeliver;
    }

    public void setFractionDeliver(int fractionDeliver) {
        if (fractionDeliver < 0 || fractionDeliver > 1) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0001");
        }
        this.mFractionDeliver = fractionDeliver;
    }

    public int getStatusIndicator() {
        return this.mStatusIndicator;
    }

    public void setStatusIndicator(int statusIndicator) {
        if (statusIndicator < 0 || statusIndicator > 1) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0001");
        }
        this.mStatusIndicator = statusIndicator;
    }

    public int getStatusValue() {
        return this.mStatusValue;
    }

    public void setStatusValue(int statusValue) {
        if (statusValue < 0 || statusValue > 1) {
            throw new IllegalArgumentException("Out of range, valid range is 0x0000 to 0x0001");
        }
        this.mStatusValue = statusValue;
    }

    public long getMseTime() {
        return this.mMseTime;
    }

    public String getMseTimeString() {
        return new SimpleDateFormat("yyyyMMdd'T'HHmmssZ").format(new Date(getMseTime()));
    }

    public void setMseTime(long mseTime) {
        this.mMseTime = mseTime;
    }

    public void setMseTime(String mseTime) throws ParseException {
        this.mMseTime = new SimpleDateFormat("yyyyMMdd'T'HHmmssZ").parse(mseTime).getTime();
    }
}
