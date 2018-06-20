package com.android.bluetooth.map;

import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.bluetooth.opp.BluetoothShare;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsHeader.ConcatRef;
import com.android.internal.telephony.cdma.SmsMessage;
import com.android.internal.telephony.cdma.sms.UserData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;

public class BluetoothMapSmsPdu {
    private static int INVALID_VALUE = -1;
    public static int SMS_TYPE_CDMA = 2;
    public static int SMS_TYPE_GSM = 1;
    private static final String TAG = "BluetoothMapSmsPdu";
    /* renamed from: V */
    private static final boolean f23V = false;
    private static int sConcatenatedRef = new Random().nextInt(256);

    public static class SmsPdu {
        private static final byte BEARER_DATA = (byte) 8;
        private static final byte BEARER_DATA_MSG_ID = (byte) 0;
        private static final byte BEARER_REPLY_OPTION = (byte) 6;
        private static final byte CAUSE_CODES = (byte) 7;
        private static final byte DESTINATION_ADDRESS = (byte) 4;
        private static final byte DESTINATION_SUB_ADDRESS = (byte) 5;
        private static final byte ORIGINATING_ADDRESS = (byte) 2;
        private static final byte ORIGINATING_SUB_ADDRESS = (byte) 3;
        private static final byte SERVICE_CATEGORY = (byte) 1;
        private static final byte TELESERVICE_IDENTIFIER = (byte) 0;
        private static final byte TP_MIT_DELIVER = (byte) 0;
        private static final byte TP_MMS_NO_MORE = (byte) 4;
        private static final byte TP_RP_NO_REPLY_PATH = (byte) 0;
        private static final byte TP_SRI_NO_REPORT = (byte) 0;
        private static final byte TP_UDHI_MASK = (byte) 64;
        private byte[] mData;
        private int mEncoding;
        private int mLanguageShiftTable;
        private int mLanguageTable;
        private int mMsgSeptetCount = 0;
        private byte[] mScAddress = new byte[]{(byte) 0};
        private int mType;
        private int mUserDataMsgOffset = 0;
        private int mUserDataSeptetPadding = BluetoothMapSmsPdu.INVALID_VALUE;

        SmsPdu(byte[] data, int type) {
            this.mData = data;
            this.mEncoding = BluetoothMapSmsPdu.INVALID_VALUE;
            this.mType = type;
            this.mLanguageTable = BluetoothMapSmsPdu.INVALID_VALUE;
            this.mLanguageShiftTable = BluetoothMapSmsPdu.INVALID_VALUE;
            this.mUserDataMsgOffset = gsmSubmitGetTpUdOffset();
        }

        SmsPdu(byte[] data, int encoding, int type, int languageTable) {
            this.mData = data;
            this.mEncoding = encoding;
            this.mType = type;
            this.mLanguageTable = languageTable;
        }

        public byte[] getData() {
            return this.mData;
        }

        public byte[] getScAddress() {
            return this.mScAddress;
        }

        public void setEncoding(int encoding) {
            this.mEncoding = encoding;
        }

        public int getEncoding() {
            return this.mEncoding;
        }

        public int getType() {
            return this.mType;
        }

        public int getUserDataMsgOffset() {
            return this.mUserDataMsgOffset;
        }

        public int getUserDataMsgSize() {
            return this.mData.length - this.mUserDataMsgOffset;
        }

        public int getLanguageShiftTable() {
            return this.mLanguageShiftTable;
        }

        public int getLanguageTable() {
            return this.mLanguageTable;
        }

        public int getUserDataSeptetPadding() {
            return this.mUserDataSeptetPadding;
        }

        public int getMsgSeptetCount() {
            return this.mMsgSeptetCount;
        }

        private int cdmaGetParameterOffset(byte parameterId) {
            ByteArrayInputStream pdu = new ByteArrayInputStream(this.mData);
            int offset = 0;
            boolean found = false;
            try {
                pdu.skip(1);
                while (pdu.available() > 0) {
                    byte currentId = pdu.read();
                    int currentLen = pdu.read();
                    if (currentId == parameterId) {
                        found = true;
                        break;
                    }
                    pdu.skip((long) currentLen);
                    offset += currentLen + 2;
                }
                pdu.close();
            } catch (Exception e) {
                Log.e(BluetoothMapSmsPdu.TAG, "cdmaGetParameterOffset: ", e);
            }
            return found ? offset : 0;
        }

        private int cdmaGetSubParameterOffset(byte subParameterId) {
            ByteArrayInputStream pdu = new ByteArrayInputStream(this.mData);
            boolean found = false;
            int offset = cdmaGetParameterOffset(BEARER_DATA) + 2;
            pdu.skip((long) offset);
            while (pdu.available() > 0) {
                try {
                    byte currentId = pdu.read();
                    int currentLen = pdu.read();
                    if (currentId == subParameterId) {
                        found = true;
                        break;
                    }
                    pdu.skip((long) currentLen);
                    offset += currentLen + 2;
                } catch (Exception e) {
                    Log.e(BluetoothMapSmsPdu.TAG, "cdmaGetParameterOffset: ", e);
                }
            }
            pdu.close();
            return found ? offset : 0;
        }

        public void cdmaChangeToDeliverPdu(long date) {
            if (this.mData == null) {
                throw new IllegalArgumentException("Unable to convert PDU to Deliver type");
            }
            int offset = cdmaGetParameterOffset((byte) 4);
            if (this.mData.length < offset) {
                throw new IllegalArgumentException("Unable to convert PDU to Deliver type");
            }
            this.mData[offset] = (byte) 2;
            offset = cdmaGetParameterOffset(DESTINATION_SUB_ADDRESS);
            if (this.mData.length < offset) {
                throw new IllegalArgumentException("Unable to convert PDU to Deliver type");
            }
            this.mData[offset] = (byte) 3;
            offset = cdmaGetSubParameterOffset((byte) 0);
            if (this.mData.length > offset + 2) {
                this.mData[offset + 2] = (byte) (((this.mData[offset + 2] & 255) & 15) | 16);
                return;
            }
            throw new IllegalArgumentException("Unable to convert PDU to Deliver type");
        }

        private int gsmSubmitGetTpPidOffset() {
            int offset = ((((this.mData[2] + 1) & 255) / 2) + 2) + 2;
            if (offset <= this.mData.length && offset <= 14) {
                return offset;
            }
            throw new IllegalArgumentException("wrongly formatted gsm submit PDU. offset = " + offset);
        }

        public int gsmSubmitGetTpDcs() {
            return this.mData[gsmSubmitGetTpDcsOffset()] & 255;
        }

        public boolean gsmSubmitHasUserDataHeader() {
            return ((this.mData[0] & 255) & 64) == 64;
        }

        private int gsmSubmitGetTpDcsOffset() {
            return gsmSubmitGetTpPidOffset() + 1;
        }

        private int gsmSubmitGetTpUdlOffset() {
            switch (((this.mData[0] & 255) & 12) >> 2) {
                case 0:
                    return gsmSubmitGetTpPidOffset() + 2;
                case 1:
                    return (gsmSubmitGetTpPidOffset() + 2) + 1;
                default:
                    return (gsmSubmitGetTpPidOffset() + 2) + 7;
            }
        }

        private int gsmSubmitGetTpUdOffset() {
            return gsmSubmitGetTpUdlOffset() + 1;
        }

        public void gsmDecodeUserDataHeader() {
            int i = 1;
            ByteArrayInputStream pdu = new ByteArrayInputStream(this.mData);
            pdu.skip((long) gsmSubmitGetTpUdlOffset());
            int userDataLength = pdu.read();
            if (gsmSubmitHasUserDataHeader()) {
                int userDataHeaderLength = pdu.read();
                if (this.mEncoding == 1) {
                    byte[] udh = new byte[userDataHeaderLength];
                    try {
                        pdu.read(udh);
                    } catch (IOException e) {
                        Log.w(BluetoothMapSmsPdu.TAG, "unable to read userDataHeader", e);
                    }
                    SmsHeader userDataHeader = SmsHeader.fromByteArray(udh);
                    this.mLanguageTable = userDataHeader.languageTable;
                    this.mLanguageShiftTable = userDataHeader.languageShiftTable;
                    int headerBits = (userDataHeaderLength + 1) * 8;
                    int headerSeptets = headerBits / 7;
                    if (headerBits % 7 <= 0) {
                        i = 0;
                    }
                    headerSeptets += i;
                    this.mUserDataSeptetPadding = (headerSeptets * 7) - headerBits;
                    this.mMsgSeptetCount = userDataLength - headerSeptets;
                }
                this.mUserDataMsgOffset = (gsmSubmitGetTpUdOffset() + userDataHeaderLength) + 1;
                return;
            }
            this.mUserDataSeptetPadding = 0;
            this.mMsgSeptetCount = userDataLength;
            this.mUserDataMsgOffset = gsmSubmitGetTpUdOffset();
        }

        private void gsmWriteDate(ByteArrayOutputStream header, long time) throws UnsupportedEncodingException {
            String timeStr = new SimpleDateFormat("yyMMddHHmmss").format(new Date(time));
            byte[] timeChars = timeStr.getBytes("US-ASCII");
            int n = timeStr.length();
            for (int i = 0; i < n; i += 2) {
                header.write(((timeChars[i + 1] - 48) << 4) | (timeChars[i] - 48));
            }
            Calendar cal = Calendar.getInstance();
            if ((cal.get(15) + cal.get(16)) / 900000 < 0) {
                char[] offsetChars = String.format("%1$02d", new Object[]{Integer.valueOf(-((cal.get(15) + cal.get(16)) / 900000))}).toCharArray();
                header.write((((offsetChars[1] - 48) << 4) | 64) | (offsetChars[0] - 48));
                return;
            }
            offsetChars = String.format("%1$02d", new Object[]{Integer.valueOf((cal.get(15) + cal.get(16)) / 900000)}).toCharArray();
            header.write(((offsetChars[1] - 48) << 4) | (offsetChars[0] - 48));
        }

        public void gsmChangeToDeliverPdu(long date, String originator) {
            int padding = 0;
            ByteArrayOutputStream newPdu = new ByteArrayOutputStream(22);
            try {
                newPdu.write(((this.mData[0] & 255) & 64) | 4);
                byte[] encodedAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(originator);
                if (encodedAddress != null) {
                    if ((encodedAddress[encodedAddress.length - 1] & 240) == 240) {
                        padding = 1;
                    }
                    encodedAddress[0] = (byte) (((encodedAddress[0] - 1) * 2) - padding);
                    newPdu.write(encodedAddress);
                } else {
                    newPdu.write(0);
                    newPdu.write(BluetoothMapContent.MMS_BCC);
                }
                newPdu.write(this.mData[gsmSubmitGetTpPidOffset()]);
                newPdu.write(this.mData[gsmSubmitGetTpDcsOffset()]);
                gsmWriteDate(newPdu, date);
                newPdu.write(this.mData[gsmSubmitGetTpUdlOffset()] & 255);
                newPdu.write(this.mData, gsmSubmitGetTpUdOffset(), this.mData.length - gsmSubmitGetTpUdOffset());
                this.mData = newPdu.toByteArray();
            } catch (IOException e) {
                Log.e(BluetoothMapSmsPdu.TAG, "", e);
                throw new IllegalArgumentException("Failed to change type to deliver PDU.");
            }
        }

        public String getEncodingString() {
            if (this.mType == BluetoothMapSmsPdu.SMS_TYPE_GSM) {
                switch (this.mEncoding) {
                    case 1:
                        if (this.mLanguageTable == 0) {
                            return "G-7BIT";
                        }
                        return "G-7BITEXT";
                    case 2:
                        return "G-8BIT";
                    case 3:
                        return "G-16BIT";
                    default:
                        return "";
                }
            }
            switch (this.mEncoding) {
                case 1:
                    return "C-7ASCII";
                case 2:
                    return "C-8BIT";
                case 3:
                    return "C-UNICODE";
                case 4:
                    return "C-KOREAN";
                default:
                    return "";
            }
        }
    }

    protected static int getNextConcatenatedRef() {
        sConcatenatedRef++;
        return sConcatenatedRef;
    }

    public static ArrayList<SmsPdu> getSubmitPdus(String messageText, String address) {
        TextEncodingDetails ted;
        int activePhone = TelephonyManager.getDefault().getCurrentPhoneType();
        if (2 == activePhone) {
            ted = SmsMessage.calculateLength(messageText, false, true);
        } else {
            ted = com.android.internal.telephony.gsm.SmsMessage.calculateLength(messageText, false);
        }
        int msgCount = ted.msgCount;
        int refNumber = getNextConcatenatedRef() & 255;
        ArrayList<String> smsFragments = android.telephony.SmsMessage.fragmentText(messageText);
        ArrayList<SmsPdu> arrayList = new ArrayList(msgCount);
        int phoneType = activePhone == 2 ? SMS_TYPE_CDMA : SMS_TYPE_GSM;
        int encoding = ted.codeUnitSize;
        int languageTable = ted.languageTable;
        int languageShiftTable = ted.languageShiftTable;
        String destinationAddress = PhoneNumberUtils.stripSeparators(address);
        if (destinationAddress == null || destinationAddress.length() < 2) {
            destinationAddress = "12";
        }
        if (msgCount == 1) {
            arrayList.add(new SmsPdu(android.telephony.SmsMessage.getSubmitPdu(null, destinationAddress, (String) smsFragments.get(0), false).encodedMessage, encoding, phoneType, languageTable));
        } else {
            for (int i = 0; i < msgCount; i++) {
                byte[] data;
                ConcatRef concatRef = new ConcatRef();
                concatRef.refNumber = refNumber;
                concatRef.seqNumber = i + 1;
                concatRef.msgCount = msgCount;
                concatRef.isEightBits = true;
                SmsHeader smsHeader = new SmsHeader();
                smsHeader.concatRef = concatRef;
                if (encoding == 1) {
                    smsHeader.languageTable = languageTable;
                    smsHeader.languageShiftTable = languageShiftTable;
                }
                if (phoneType == SMS_TYPE_GSM) {
                    data = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(null, destinationAddress, (String) smsFragments.get(i), false, SmsHeader.toByteArray(smsHeader), encoding, languageTable, languageShiftTable).encodedMessage;
                } else {
                    UserData uData = new UserData();
                    uData.payloadStr = (String) smsFragments.get(i);
                    uData.userDataHeader = smsHeader;
                    if (encoding == 1) {
                        uData.msgEncoding = 9;
                    } else {
                        uData.msgEncoding = 4;
                    }
                    uData.msgEncodingSet = true;
                    data = SmsMessage.getSubmitPdu(destinationAddress, uData, false).encodedMessage;
                }
                arrayList.add(new SmsPdu(data, encoding, phoneType, languageTable));
            }
        }
        return arrayList;
    }

    public static ArrayList<SmsPdu> getDeliverPdus(String messageText, String address, long date) {
        ArrayList<SmsPdu> deliverPdus = getSubmitPdus(messageText, address);
        Iterator i$ = deliverPdus.iterator();
        while (i$.hasNext()) {
            SmsPdu currentPdu = (SmsPdu) i$.next();
            if (currentPdu.getType() == SMS_TYPE_CDMA) {
                currentPdu.cdmaChangeToDeliverPdu(date);
            } else {
                currentPdu.gsmChangeToDeliverPdu(date, address);
            }
        }
        return deliverPdus;
    }

    public static String decodePdu(byte[] data, int type) {
        if (type == SMS_TYPE_CDMA) {
            return SmsMessage.createFromEfRecord(0, data).getMessageBody();
        }
        return gsmParseSubmitPdu(data);
    }

    private static byte[] gsmStripOffScAddress(byte[] data) {
        int addressLength = data[0] & 255;
        if (addressLength >= data.length) {
            throw new IllegalArgumentException("Length of address exeeds the length of the PDU data.");
        }
        int pduLength = data.length - (addressLength + 1);
        byte[] newData = new byte[pduLength];
        System.arraycopy(data, addressLength + 1, newData, 0, pduLength);
        return newData;
    }

    private static String gsmParseSubmitPdu(byte[] data) {
        UnsupportedEncodingException e;
        SmsPdu pdu = new SmsPdu(gsmStripOffScAddress(data), SMS_TYPE_GSM);
        int dataCodingScheme = pdu.gsmSubmitGetTpDcs();
        int encodingType = 0;
        String messageBody = null;
        if ((dataCodingScheme & 128) == 0) {
            if (!((dataCodingScheme & 32) != 0)) {
                switch ((dataCodingScheme >> 2) & 3) {
                    case 0:
                        encodingType = 1;
                        break;
                    case 1:
                    case 3:
                        Log.w(TAG, "1 - Unsupported SMS data coding scheme " + (dataCodingScheme & 255));
                        encodingType = 2;
                        break;
                    case 2:
                        encodingType = 3;
                        break;
                    default:
                        break;
                }
            }
            Log.w(TAG, "4 - Unsupported SMS data coding scheme (compression) " + (dataCodingScheme & 255));
        } else if ((dataCodingScheme & 240) == 240) {
            encodingType = (dataCodingScheme & 4) == 0 ? 1 : 2;
        } else if ((dataCodingScheme & 240) == BluetoothShare.STATUS_RUNNING || (dataCodingScheme & 240) == 208 || (dataCodingScheme & 240) == 224) {
            if ((dataCodingScheme & 240) == 224) {
                encodingType = 3;
            } else {
                encodingType = 1;
            }
        } else if ((dataCodingScheme & BluetoothShare.STATUS_RUNNING) != 128) {
            Log.w(TAG, "3 - Unsupported SMS data coding scheme " + (dataCodingScheme & 255));
        } else if (dataCodingScheme == BluetoothMapContentObserver.MESSAGE_TYPE_RETRIEVE_CONF) {
            encodingType = 4;
        } else {
            Log.w(TAG, "5 - Unsupported SMS data coding scheme " + (dataCodingScheme & 255));
        }
        pdu.setEncoding(encodingType);
        pdu.gsmDecodeUserDataHeader();
        String messageBody2;
        switch (encodingType) {
            case 0:
            case 2:
                try {
                    Log.w(TAG, "Unknown encoding type: " + encodingType);
                    messageBody = null;
                    break;
                } catch (UnsupportedEncodingException e2) {
                    e = e2;
                    Log.e(TAG, "Unsupported encoding type???", e);
                    return null;
                }
            case 1:
                messageBody = GsmAlphabet.gsm7BitPackedToString(pdu.getData(), pdu.getUserDataMsgOffset(), pdu.getMsgSeptetCount(), pdu.getUserDataSeptetPadding(), pdu.getLanguageTable(), pdu.getLanguageShiftTable());
                Log.i(TAG, "Decoded as 7BIT: " + messageBody);
                break;
            case 3:
                messageBody2 = new String(pdu.getData(), pdu.getUserDataMsgOffset(), pdu.getUserDataMsgSize(), "utf-16");
                try {
                    Log.i(TAG, "Decoded as 16BIT: " + messageBody2);
                    messageBody = messageBody2;
                    break;
                } catch (UnsupportedEncodingException e3) {
                    e = e3;
                    messageBody = messageBody2;
                    Log.e(TAG, "Unsupported encoding type???", e);
                    return null;
                }
            case 4:
                messageBody2 = new String(pdu.getData(), pdu.getUserDataMsgOffset(), pdu.getUserDataMsgSize(), "KSC5601");
                Log.i(TAG, "Decoded as KSC5601: " + messageBody2);
                messageBody = messageBody2;
                break;
        }
        return messageBody;
    }
}
