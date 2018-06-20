package com.android.bluetooth.map;

import android.util.Log;
import com.android.internal.util.FastXmlSerializer;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Locale;
import org.xmlpull.v1.XmlSerializer;

public class BluetoothMapFolderElement {
    /* renamed from: D */
    private static final boolean f14D = true;
    private static final String TAG = "BluetoothMapFolderElement";
    /* renamed from: V */
    private static final boolean f15V = false;
    private long mEmailFolderId = -1;
    private boolean mHasSmsMmsContent = false;
    private String mName;
    private BluetoothMapFolderElement mParent = null;
    private HashMap<String, BluetoothMapFolderElement> mSubFolders;

    public BluetoothMapFolderElement(String name, BluetoothMapFolderElement parrent) {
        this.mName = name;
        this.mParent = parrent;
        this.mSubFolders = new HashMap();
    }

    public String getName() {
        return this.mName;
    }

    public boolean hasSmsMmsContent() {
        return this.mHasSmsMmsContent;
    }

    public long getEmailFolderId() {
        return this.mEmailFolderId;
    }

    public void setEmailFolderId(long emailFolderId) {
        this.mEmailFolderId = emailFolderId;
    }

    public void setHasSmsMmsContent(boolean hasSmsMmsContent) {
        this.mHasSmsMmsContent = hasSmsMmsContent;
    }

    public BluetoothMapFolderElement getParent() {
        return this.mParent;
    }

    public String getFullPath() {
        StringBuilder sb = new StringBuilder(this.mName);
        for (BluetoothMapFolderElement current = this.mParent; current != null; current = current.getParent()) {
            if (current.getParent() != null) {
                sb.insert(0, current.mName + "/");
            }
        }
        return sb.toString();
    }

    public BluetoothMapFolderElement getEmailFolderByName(String name) {
        BluetoothMapFolderElement folderElement = getRoot().getSubFolder("telecom").getSubFolder("msg").getSubFolder(name);
        if (folderElement == null || folderElement.getEmailFolderId() != -1) {
            return folderElement;
        }
        return null;
    }

    public BluetoothMapFolderElement getEmailFolderById(long id) {
        return getEmailFolderById(id, this);
    }

    public static BluetoothMapFolderElement getEmailFolderById(long id, BluetoothMapFolderElement folderStructure) {
        if (folderStructure == null) {
            return null;
        }
        return findEmailFolderById(id, folderStructure.getRoot());
    }

    private static BluetoothMapFolderElement findEmailFolderById(long id, BluetoothMapFolderElement folder) {
        if (folder.getEmailFolderId() == id) {
            return folder;
        }
        for (BluetoothMapFolderElement subFolder : (BluetoothMapFolderElement[]) folder.mSubFolders.values().toArray(new BluetoothMapFolderElement[folder.mSubFolders.size()])) {
            BluetoothMapFolderElement ret = findEmailFolderById(id, subFolder);
            if (ret != null) {
                return ret;
            }
        }
        return null;
    }

    public BluetoothMapFolderElement getRoot() {
        BluetoothMapFolderElement rootFolder = this;
        while (rootFolder.getParent() != null) {
            rootFolder = rootFolder.getParent();
        }
        return rootFolder;
    }

    public BluetoothMapFolderElement addFolder(String name) {
        name = name.toLowerCase(Locale.US);
        BluetoothMapFolderElement newFolder = (BluetoothMapFolderElement) this.mSubFolders.get(name);
        Log.i(TAG, "addFolder():" + name);
        if (newFolder != null) {
            return newFolder;
        }
        newFolder = new BluetoothMapFolderElement(name, this);
        this.mSubFolders.put(name, newFolder);
        return newFolder;
    }

    public BluetoothMapFolderElement addSmsMmsFolder(String name) {
        name = name.toLowerCase(Locale.US);
        BluetoothMapFolderElement newFolder = (BluetoothMapFolderElement) this.mSubFolders.get(name);
        Log.i(TAG, "addSmsMmsFolder():" + name);
        if (newFolder == null) {
            newFolder = new BluetoothMapFolderElement(name, this);
            this.mSubFolders.put(name, newFolder);
        }
        newFolder.setHasSmsMmsContent(true);
        return newFolder;
    }

    public BluetoothMapFolderElement addEmailFolder(String name, long emailFolderId) {
        name = name.toLowerCase();
        BluetoothMapFolderElement newFolder = (BluetoothMapFolderElement) this.mSubFolders.get(name);
        if (newFolder == null) {
            newFolder = new BluetoothMapFolderElement(name, this);
            this.mSubFolders.put(name, newFolder);
        }
        newFolder.setEmailFolderId(emailFolderId);
        return newFolder;
    }

    public int getSubFolderCount() {
        return this.mSubFolders.size();
    }

    public BluetoothMapFolderElement getSubFolder(String folderName) {
        return (BluetoothMapFolderElement) this.mSubFolders.get(folderName.toLowerCase());
    }

    public byte[] encode(int offset, int count) throws UnsupportedEncodingException {
        StringWriter sw = new StringWriter();
        XmlSerializer xmlMsgElement = new FastXmlSerializer();
        BluetoothMapFolderElement[] folders = (BluetoothMapFolderElement[]) this.mSubFolders.values().toArray(new BluetoothMapFolderElement[this.mSubFolders.size()]);
        if (offset > this.mSubFolders.size()) {
            throw new IllegalArgumentException("FolderListingEncode: offset > subFolders.size()");
        }
        int stopIndex = offset + count;
        if (stopIndex > this.mSubFolders.size()) {
            stopIndex = this.mSubFolders.size();
        }
        try {
            xmlMsgElement.setOutput(sw);
            xmlMsgElement.startDocument("UTF-8", Boolean.valueOf(true));
            xmlMsgElement.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            xmlMsgElement.startTag(null, "folder-listing");
            xmlMsgElement.attribute(null, "version", "1.0");
            for (int i = offset; i < stopIndex; i++) {
                xmlMsgElement.startTag(null, "folder");
                xmlMsgElement.attribute(null, "name", folders[i].getName());
                xmlMsgElement.endTag(null, "folder");
            }
            xmlMsgElement.endTag(null, "folder-listing");
            xmlMsgElement.endDocument();
            return sw.toString().getBytes("UTF-8");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, e);
            throw new IllegalArgumentException("error encoding folderElement");
        } catch (IllegalStateException e2) {
            Log.w(TAG, e2);
            throw new IllegalArgumentException("error encoding folderElement");
        } catch (IOException e3) {
            Log.w(TAG, e3);
            throw new IllegalArgumentException("error encoding folderElement");
        }
    }
}
