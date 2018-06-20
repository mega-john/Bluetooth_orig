package com.android.bluetooth.map;

import android.telephony.PhoneNumberUtils;
import android.util.Log;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.vcard.VCardBuilder;
import com.android.vcard.VCardConstants;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;

public abstract class BluetoothMapbMessage {
    /* renamed from: D */
    protected static final boolean f26D = true;
    public static int INVALID_VALUE = -1;
    protected static String TAG = "BluetoothMapbMessage";
    /* renamed from: V */
    protected static final boolean f27V = false;
    private static final String VERSION = "VERSION:1.0";
    protected int mAppParamCharset = -1;
    private int mBMsgLength = INVALID_VALUE;
    protected String mCharset = null;
    protected String mEncoding = null;
    private String mFolder = null;
    private String mLanguage = null;
    private ArrayList<vCard> mOriginator = null;
    private long mPartId = ((long) INVALID_VALUE);
    private ArrayList<vCard> mRecipient = null;
    private String mStatus = null;
    protected TYPE mType = null;

    private static class BMsgReader {
        InputStream mInStream;

        public BMsgReader(InputStream is) {
            this.mInStream = is;
        }

        private byte[] getLineAsBytes() {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while (true) {
                try {
                    int readByte = this.mInStream.read();
                    if (readByte == -1) {
                        break;
                    }
                    if (readByte == 13) {
                        readByte = this.mInStream.read();
                        if (readByte == -1 || readByte != 10) {
                            output.write(13);
                        } else if (output.size() != 0) {
                            break;
                        }
                    } else if (readByte == 10) {
                        if (output.size() == 0) {
                        }
                    }
                    output.write(readByte);
                } catch (IOException e) {
                    Log.w(BluetoothMapbMessage.TAG, e);
                    return null;
                }
            }
            return output.toByteArray();
        }

        public String getLine() {
            try {
                byte[] line = getLineAsBytes();
                if (line.length == 0) {
                    return null;
                }
                return new String(line, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.w(BluetoothMapbMessage.TAG, e);
                return null;
            }
        }

        public String getLineEnforce() {
            String line = getLine();
            if (line != null) {
                return line;
            }
            throw new IllegalArgumentException("Bmessage too short");
        }

        public void expect(String subString) throws IllegalArgumentException {
            String line = getLine();
            if (line == null || subString == null) {
                throw new IllegalArgumentException("Line or substring is null");
            } else if (!line.toUpperCase().contains(subString.toUpperCase())) {
                throw new IllegalArgumentException("Expected \"" + subString + "\" in: \"" + line + "\"");
            }
        }

        public void expect(String subString, String subString2) throws IllegalArgumentException {
            String line = getLine();
            if (!line.toUpperCase().contains(subString.toUpperCase())) {
                throw new IllegalArgumentException("Expected \"" + subString + "\" in: \"" + line + "\"");
            } else if (!line.toUpperCase().contains(subString2.toUpperCase())) {
                throw new IllegalArgumentException("Expected \"" + subString + "\" in: \"" + line + "\"");
            }
        }

        public byte[] getDataBytes(int length) {
            byte[] data = new byte[length];
            int offset = 0;
            while (true) {
                try {
                    int bytesRead = this.mInStream.read(data, offset, length - offset);
                    if (bytesRead == length - offset) {
                        return data;
                    }
                    if (bytesRead == -1) {
                        return null;
                    }
                    offset += bytesRead;
                } catch (IOException e) {
                    Log.w(BluetoothMapbMessage.TAG, e);
                    return null;
                }
            }
        }
    }

    public static class vCard {
        private String[] mEmailAddresses;
        private int mEnvLevel;
        private String mFormattedName;
        private String mName;
        private String[] mPhoneNumbers;
        private String mVersion;

        public vCard(String name, String formattedName, String[] phoneNumbers, String[] emailAddresses, int envLevel) {
            this.mName = null;
            this.mFormattedName = null;
            this.mPhoneNumbers = new String[0];
            this.mEmailAddresses = new String[0];
            this.mEnvLevel = 0;
            this.mEnvLevel = envLevel;
            this.mVersion = VCardConstants.VERSION_V30;
            if (name == null) {
                name = "";
            }
            this.mName = name;
            if (formattedName == null) {
                formattedName = "";
            }
            this.mFormattedName = formattedName;
            setPhoneNumbers(phoneNumbers);
            if (emailAddresses != null) {
                this.mEmailAddresses = emailAddresses;
            }
        }

        public vCard(String name, String[] phoneNumbers, String[] emailAddresses, int envLevel) {
            this.mName = null;
            this.mFormattedName = null;
            this.mPhoneNumbers = new String[0];
            this.mEmailAddresses = new String[0];
            this.mEnvLevel = 0;
            this.mEnvLevel = envLevel;
            this.mVersion = VCardConstants.VERSION_V21;
            if (name == null) {
                name = "";
            }
            this.mName = name;
            setPhoneNumbers(phoneNumbers);
            if (emailAddresses != null) {
                this.mEmailAddresses = emailAddresses;
            }
        }

        public vCard(String name, String formattedName, String[] phoneNumbers, String[] emailAddresses) {
            this.mName = null;
            this.mFormattedName = null;
            this.mPhoneNumbers = new String[0];
            this.mEmailAddresses = new String[0];
            this.mEnvLevel = 0;
            this.mVersion = VCardConstants.VERSION_V30;
            if (name == null) {
                name = "";
            }
            this.mName = name;
            if (formattedName == null) {
                formattedName = "";
            }
            this.mFormattedName = formattedName;
            setPhoneNumbers(phoneNumbers);
            if (emailAddresses != null) {
                this.mEmailAddresses = emailAddresses;
            }
        }

        public vCard(String name, String[] phoneNumbers, String[] emailAddresses) {
            this.mName = null;
            this.mFormattedName = null;
            this.mPhoneNumbers = new String[0];
            this.mEmailAddresses = new String[0];
            this.mEnvLevel = 0;
            this.mVersion = VCardConstants.VERSION_V21;
            if (name == null) {
                name = "";
            }
            this.mName = name;
            setPhoneNumbers(phoneNumbers);
            if (emailAddresses != null) {
                this.mEmailAddresses = emailAddresses;
            }
        }

        private void setPhoneNumbers(String[] numbers) {
            if (numbers != null && numbers.length > 0) {
                this.mPhoneNumbers = new String[numbers.length];
                int n = numbers.length;
                for (int i = 0; i < n; i++) {
                    String networkNumber = PhoneNumberUtils.extractNetworkPortion(numbers[i]);
                    Boolean alpha = Boolean.valueOf(PhoneNumberUtils.stripSeparators(numbers[i]).matches("[0-9]*[a-zA-Z]+[0-9]*"));
                    if (networkNumber == null || networkNumber.length() <= 1 || alpha.booleanValue()) {
                        this.mPhoneNumbers[i] = numbers[i];
                    } else {
                        this.mPhoneNumbers[i] = networkNumber;
                    }
                }
            }
        }

        public String getFirstPhoneNumber() {
            if (this.mPhoneNumbers.length > 0) {
                return this.mPhoneNumbers[0];
            }
            return null;
        }

        public int getEnvLevel() {
            return this.mEnvLevel;
        }

        public String getName() {
            return this.mName;
        }

        public String getFirstEmail() {
            if (this.mEmailAddresses.length > 0) {
                return this.mEmailAddresses[0];
            }
            return null;
        }

        public void encode(StringBuilder sb) {
            sb.append("BEGIN:VCARD").append(VCardBuilder.VCARD_END_OF_LINE);
            sb.append("VERSION:").append(this.mVersion).append(VCardBuilder.VCARD_END_OF_LINE);
            if (this.mVersion.equals(VCardConstants.VERSION_V30) && this.mFormattedName != null) {
                sb.append("FN:").append(this.mFormattedName).append(VCardBuilder.VCARD_END_OF_LINE);
            }
            if (this.mName != null) {
                sb.append("N:").append(this.mName).append(VCardBuilder.VCARD_END_OF_LINE);
            }
            for (String phoneNumber : this.mPhoneNumbers) {
                sb.append("TEL:").append(phoneNumber).append(VCardBuilder.VCARD_END_OF_LINE);
            }
            for (String emailAddress : this.mEmailAddresses) {
                sb.append("EMAIL:").append(emailAddress).append(VCardBuilder.VCARD_END_OF_LINE);
            }
            sb.append("END:VCARD").append(VCardBuilder.VCARD_END_OF_LINE);
        }

        public static vCard parseVcard(BMsgReader reader, int envLevel) {
            String[] strArr = null;
            String formattedName = null;
            String name = null;
            ArrayList<String> phoneNumbers = null;
            ArrayList<String> emailAddresses = null;
            String line = reader.getLineEnforce();
            while (!line.contains("END:VCARD")) {
                line = line.trim();
                String[] parts;
                if (line.startsWith("N:")) {
                    parts = line.split("[^\\\\]:");
                    if (parts.length == 2) {
                        name = parts[1];
                    } else {
                        name = "";
                    }
                } else if (line.startsWith("FN:")) {
                    parts = line.split("[^\\\\]:");
                    if (parts.length == 2) {
                        formattedName = parts[1];
                    } else {
                        formattedName = "";
                    }
                } else if (line.startsWith("TEL:")) {
                    parts = line.split("[^\\\\]:");
                    if (parts.length == 2) {
                        subParts = parts[1].split("[^\\\\];");
                        if (phoneNumbers == null) {
                            phoneNumbers = new ArrayList(1);
                        }
                        phoneNumbers.add(subParts[subParts.length - 1]);
                    }
                } else if (line.startsWith("EMAIL:")) {
                    parts = line.split("[^\\\\]:");
                    if (parts.length == 2) {
                        subParts = parts[1].split("[^\\\\];");
                        if (emailAddresses == null) {
                            emailAddresses = new ArrayList(1);
                        }
                        emailAddresses.add(subParts[subParts.length - 1]);
                    }
                }
                line = reader.getLineEnforce();
            }
            String[] strArr2 = phoneNumbers == null ? null : (String[]) phoneNumbers.toArray(new String[phoneNumbers.size()]);
            if (emailAddresses != null) {
                strArr = (String[]) emailAddresses.toArray(new String[emailAddresses.size()]);
            }
            return new vCard(name, formattedName, strArr2, strArr, envLevel);
        }
    }

    public abstract byte[] encode() throws UnsupportedEncodingException;

    public abstract void parseMsgInit();

    public abstract void parseMsgPart(String str);

    public static BluetoothMapbMessage parse(InputStream bMsgStream, int appParamCharset) throws IllegalArgumentException {
        String line = "";
        BluetoothMapbMessage newBMsg = null;
        boolean status = f27V;
        TYPE type = null;
        String folder = null;
        BMsgReader reader = new BMsgReader(bMsgStream);
        reader.expect("BEGIN:BMSG");
        reader.expect(VCardConstants.PROPERTY_VERSION, "1.0");
        line = reader.getLineEnforce();
        while (!line.contains("BEGIN:VCARD") && !line.contains("BEGIN:BENV")) {
            String[] arg;
            if (line.contains("STATUS")) {
                arg = line.split(":");
                if (arg == null || arg.length != 2) {
                    throw new IllegalArgumentException("Missing value for 'STATUS': " + line);
                } else if (arg[1].trim().equals("READ")) {
                    status = true;
                } else if (arg[1].trim().equals("UNREAD")) {
                    status = f27V;
                } else {
                    throw new IllegalArgumentException("Wrong value in 'STATUS': " + arg[1]);
                }
            }
            if (line.contains(VCardConstants.PARAM_TYPE)) {
                arg = line.split(":");
                if (arg == null || arg.length != 2) {
                    throw new IllegalArgumentException("Missing value for 'TYPE':" + line);
                }
                type = TYPE.valueOf(arg[1].trim());
                if (appParamCharset != 0 || type == TYPE.SMS_CDMA || type == TYPE.SMS_GSM) {
                    switch (type) {
                        case SMS_CDMA:
                        case SMS_GSM:
                            newBMsg = new BluetoothMapbMessageSms();
                            break;
                        case MMS:
                            newBMsg = new BluetoothMapbMessageMms();
                            break;
                        case EMAIL:
                            newBMsg = new BluetoothMapbMessageEmail();
                            break;
                    }
                }
                throw new IllegalArgumentException("Native appParamsCharset only supported for SMS");
            }
            if (line.contains("FOLDER")) {
                arg = line.split(":");
                if (arg != null && arg.length == 2) {
                    folder = arg[1].trim();
                }
            }
            line = reader.getLineEnforce();
        }
        if (newBMsg == null) {
            throw new IllegalArgumentException("Missing bMessage TYPE: - unable to parse body-content");
        }
        newBMsg.setType(type);
        newBMsg.mAppParamCharset = appParamCharset;
        if (folder != null) {
            newBMsg.setCompleteFolder(folder);
        }
        if (f27V) {
            newBMsg.setStatus(status);
        }
        while (line.contains("BEGIN:VCARD")) {
            Log.d(TAG, "Decoding vCard");
            newBMsg.addOriginator(vCard.parseVcard(reader, 0));
            line = reader.getLineEnforce();
        }
        if (line.contains("BEGIN:BENV")) {
            newBMsg.parseEnvelope(reader, 0);
            try {
                bMsgStream.close();
            } catch (IOException e) {
            }
            return newBMsg;
        }
        throw new IllegalArgumentException("Bmessage has no BEGIN:BENV - line:" + line);
    }

    private void parseEnvelope(BMsgReader reader, int level) {
        String line = reader.getLineEnforce();
        Log.d(TAG, "Decoding envelope level " + level);
        while (line.contains("BEGIN:VCARD")) {
            Log.d(TAG, "Decoding recipient vCard level " + level);
            if (this.mRecipient == null) {
                this.mRecipient = new ArrayList(1);
            }
            this.mRecipient.add(vCard.parseVcard(reader, level));
            line = reader.getLineEnforce();
        }
        if (line.contains("BEGIN:BENV")) {
            Log.d(TAG, "Decoding nested envelope");
            parseEnvelope(reader, level + 1);
        }
        if (line.contains("BEGIN:BBODY")) {
            Log.d(TAG, "Decoding bbody");
            parseBody(reader);
        }
    }

    private void parseBody(BMsgReader reader) {
        String[] arg;
        String line = reader.getLineEnforce();
        while (!line.contains("END:")) {
            if (line.contains("PARTID:")) {
                arg = line.split(":");
                if (arg == null || arg.length != 2) {
                    throw new IllegalArgumentException("Missing value for 'PARTID': " + line);
                }
                try {
                    this.mPartId = Long.parseLong(arg[1].trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Wrong value in 'PARTID': " + arg[1]);
                }
            } else if (line.contains("ENCODING:")) {
                arg = line.split(":");
                if (arg == null || arg.length != 2) {
                    throw new IllegalArgumentException("Missing value for 'ENCODING': " + line);
                }
                this.mEncoding = arg[1].trim();
            } else if (line.contains("CHARSET:")) {
                arg = line.split(":");
                if (arg == null || arg.length != 2) {
                    throw new IllegalArgumentException("Missing value for 'CHARSET': " + line);
                }
                this.mCharset = arg[1].trim();
            } else if (line.contains("LANGUAGE:")) {
                arg = line.split(":");
                if (arg == null || arg.length != 2) {
                    throw new IllegalArgumentException("Missing value for 'LANGUAGE': " + line);
                }
                this.mLanguage = arg[1].trim();
            } else if (line.contains("LENGTH:")) {
                arg = line.split(":");
                if (arg == null || arg.length != 2) {
                    throw new IllegalArgumentException("Missing value for 'LENGTH': " + line);
                }
                try {
                    this.mBMsgLength = Integer.parseInt(arg[1].trim());
                } catch (NumberFormatException e2) {
                    throw new IllegalArgumentException("Wrong value in 'LENGTH': " + arg[1]);
                }
            } else if (!line.contains("BEGIN:MSG")) {
                continue;
            } else if (this.mBMsgLength == INVALID_VALUE) {
                throw new IllegalArgumentException("Missing value for 'LENGTH'. Unable to read remaining part of the message");
            } else {
                try {
                    String[] messages = new String(reader.getDataBytes(this.mBMsgLength - (line.getBytes().length + 2)), "UTF-8").split("\r\nEND:MSG\r\n");
                    parseMsgInit();
                    for (int i = 0; i < messages.length; i++) {
                        messages[i] = messages[i].replaceFirst("^BEGIN:MSG\r\n", "");
                        messages[i] = messages[i].replaceAll("\r\n([/]*)/END\\:MSG", "\r\n$1END:MSG");
                        messages[i] = messages[i].trim();
                        parseMsgPart(messages[i]);
                    }
                } catch (UnsupportedEncodingException e3) {
                    Log.w(TAG, e3);
                    throw new IllegalArgumentException("Unable to convert to UTF-8");
                }
            }
            line = reader.getLineEnforce();
        }
    }

    public void setStatus(boolean read) {
        if (read) {
            this.mStatus = "READ";
        } else {
            this.mStatus = "UNREAD";
        }
    }

    public void setType(TYPE type) {
        this.mType = type;
    }

    public TYPE getType() {
        return this.mType;
    }

    public void setCompleteFolder(String folder) {
        this.mFolder = folder;
    }

    public void setFolder(String folder) {
        this.mFolder = "telecom/msg/" + folder;
    }

    public String getFolder() {
        return this.mFolder;
    }

    public void setEncoding(String encoding) {
        this.mEncoding = encoding;
    }

    public ArrayList<vCard> getOriginators() {
        return this.mOriginator;
    }

    public void addOriginator(vCard originator) {
        if (this.mOriginator == null) {
            this.mOriginator = new ArrayList();
        }
        this.mOriginator.add(originator);
    }

    public void addOriginator(String name, String formattedName, String[] phoneNumbers, String[] emailAddresses) {
        if (this.mOriginator == null) {
            this.mOriginator = new ArrayList();
        }
        this.mOriginator.add(new vCard(name, formattedName, phoneNumbers, emailAddresses));
    }

    public void addOriginator(String name, String[] phoneNumbers, String[] emailAddresses) {
        if (this.mOriginator == null) {
            this.mOriginator = new ArrayList();
        }
        this.mOriginator.add(new vCard(name, phoneNumbers, emailAddresses));
    }

    public ArrayList<vCard> getRecipients() {
        return this.mRecipient;
    }

    public void setRecipient(vCard recipient) {
        if (this.mRecipient == null) {
            this.mRecipient = new ArrayList();
        }
        this.mRecipient.add(recipient);
    }

    public void addRecipient(String name, String formattedName, String[] phoneNumbers, String[] emailAddresses) {
        if (this.mRecipient == null) {
            this.mRecipient = new ArrayList();
        }
        this.mRecipient.add(new vCard(name, formattedName, phoneNumbers, emailAddresses));
    }

    public void addRecipient(String name, String[] phoneNumbers, String[] emailAddresses) {
        if (this.mRecipient == null) {
            this.mRecipient = new ArrayList();
        }
        this.mRecipient.add(new vCard(name, phoneNumbers, emailAddresses));
    }

    protected String encodeBinary(byte[] pduData, byte[] scAddressData) {
        int i;
        StringBuilder out = new StringBuilder((pduData.length + scAddressData.length) * 2);
        for (i = 0; i < scAddressData.length; i++) {
            out.append(Integer.toString((scAddressData[i] >> 4) & 15, 16));
            out.append(Integer.toString(scAddressData[i] & 15, 16));
        }
        for (i = 0; i < pduData.length; i++) {
            out.append(Integer.toString((pduData[i] >> 4) & 15, 16));
            out.append(Integer.toString(pduData[i] & 15, 16));
        }
        return out.toString();
    }

    protected byte[] decodeBinary(String data) {
        byte[] out = new byte[(data.length() / 2)];
        Log.d(TAG, "Decoding binary data: START:" + data + ":END");
        int i = 0;
        int n = out.length;
        int j = 0;
        while (i < n) {
            int j2 = (j + 1) + 1;
            out[i] = (byte) (Integer.valueOf(data.substring(j, j2), 16).intValue() & 255);
            i++;
            j = j2;
        }
        StringBuilder sb = new StringBuilder(out.length);
        n = out.length;
        for (i = 0; i < n; i++) {
            sb.append(String.format("%02X", new Object[]{Integer.valueOf(out[i] & 255)}));
        }
        Log.d(TAG, "Decoded binary data: START:" + sb.toString() + ":END");
        return out;
    }

    public byte[] encodeGeneric(ArrayList<byte[]> bodyFragments) throws UnsupportedEncodingException {
        Iterator i$;
        StringBuilder sb = new StringBuilder(256);
        sb.append("BEGIN:BMSG").append(VCardBuilder.VCARD_END_OF_LINE);
        sb.append(VERSION).append(VCardBuilder.VCARD_END_OF_LINE);
        sb.append("STATUS:").append(this.mStatus).append(VCardBuilder.VCARD_END_OF_LINE);
        sb.append("TYPE:").append(this.mType.name()).append(VCardBuilder.VCARD_END_OF_LINE);
        if (this.mFolder.length() > 512) {
            sb.append("FOLDER:").append(this.mFolder.substring(this.mFolder.length() - 512, this.mFolder.length())).append(VCardBuilder.VCARD_END_OF_LINE);
        } else {
            sb.append("FOLDER:").append(this.mFolder).append(VCardBuilder.VCARD_END_OF_LINE);
        }
        if (this.mOriginator != null) {
            i$ = this.mOriginator.iterator();
            while (i$.hasNext()) {
                ((vCard) i$.next()).encode(sb);
            }
        }
        sb.append("BEGIN:BENV").append(VCardBuilder.VCARD_END_OF_LINE);
        if (this.mRecipient != null) {
            i$ = this.mRecipient.iterator();
            while (i$.hasNext()) {
                ((vCard) i$.next()).encode(sb);
            }
        }
        sb.append("BEGIN:BBODY").append(VCardBuilder.VCARD_END_OF_LINE);
        if (!(this.mEncoding == null || this.mEncoding == "")) {
            sb.append("ENCODING:").append(this.mEncoding).append(VCardBuilder.VCARD_END_OF_LINE);
        }
        if (!(this.mCharset == null || this.mCharset == "")) {
            sb.append("CHARSET:").append(this.mCharset).append(VCardBuilder.VCARD_END_OF_LINE);
        }
        int length = 0;
        i$ = bodyFragments.iterator();
        while (i$.hasNext()) {
            length += ((byte[]) i$.next()).length + 22;
        }
        sb.append("LENGTH:").append(length).append(VCardBuilder.VCARD_END_OF_LINE);
        byte[] msgStart = sb.toString().getBytes("UTF-8");
        sb = new StringBuilder(31);
        sb.append("END:BBODY").append(VCardBuilder.VCARD_END_OF_LINE);
        sb.append("END:BENV").append(VCardBuilder.VCARD_END_OF_LINE);
        sb.append("END:BMSG").append(VCardBuilder.VCARD_END_OF_LINE);
        byte[] msgEnd = sb.toString().getBytes("UTF-8");
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream((msgStart.length + msgEnd.length) + length);
            stream.write(msgStart);
            i$ = bodyFragments.iterator();
            while (i$.hasNext()) {
                byte[] fragment = (byte[]) i$.next();
                stream.write("BEGIN:MSG\r\n".getBytes("UTF-8"));
                stream.write(fragment);
                stream.write("\r\nEND:MSG\r\n".getBytes("UTF-8"));
            }
            stream.write(msgEnd);
            return stream.toByteArray();
        } catch (IOException e) {
            Log.w(TAG, e);
            return null;
        }
    }
}
