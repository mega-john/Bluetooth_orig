package com.android.bluetooth.opp;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import com.android.bluetooth.C0000R;
import com.android.vcard.VCardConfig;

public class BluetoothOppTransferHistory extends Activity implements OnCreateContextMenuListener, OnItemClickListener {
    private static final String TAG = "BluetoothOppTransferHistory";
    /* renamed from: V */
    private static final boolean f61V = false;
    private boolean mContextMenu = false;
    private int mContextMenuPosition;
    private int mIdColumnId;
    private ListView mListView;
    private BluetoothOppNotification mNotifier;
    private boolean mShowAllIncoming;
    private BluetoothOppTransferAdapter mTransferAdapter;
    private Cursor mTransferCursor;

    /* renamed from: com.android.bluetooth.opp.BluetoothOppTransferHistory$1 */
    class C00481 implements OnClickListener {
        C00481() {
        }

        public void onClick(DialogInterface dialog, int whichButton) {
            BluetoothOppTransferHistory.this.clearAllDownloads();
        }
    }

    public void onCreate(Bundle icicle) {
        String direction;
        super.onCreate(icicle);
        setContentView(C0000R.layout.bluetooth_transfers_page);
        this.mListView = (ListView) findViewById(C0000R.id.list);
        this.mListView.setEmptyView(findViewById(C0000R.id.empty));
        this.mShowAllIncoming = getIntent().getBooleanExtra(Constants.EXTRA_SHOW_ALL_FILES, false);
        if (getIntent().getIntExtra(BluetoothShare.DIRECTION, 0) == 0) {
            setTitle(getText(C0000R.string.outbound_history_title));
            direction = "(direction == 0)";
        } else {
            if (this.mShowAllIncoming) {
                setTitle(getText(C0000R.string.btopp_live_folder));
            } else {
                setTitle(getText(C0000R.string.inbound_history_title));
            }
            direction = "(direction == 1)";
        }
        String selection = "status >= '200' AND " + direction;
        if (!this.mShowAllIncoming) {
            selection = selection + " AND (" + BluetoothShare.VISIBILITY + " IS NULL OR " + BluetoothShare.VISIBILITY + " == '" + 0 + "')";
        }
        String sortOrder = "timestamp DESC";
        this.mTransferCursor = managedQuery(BluetoothShare.CONTENT_URI, new String[]{"_id", BluetoothShare.FILENAME_HINT, BluetoothShare.STATUS, BluetoothShare.TOTAL_BYTES, BluetoothShare._DATA, "timestamp", BluetoothShare.VISIBILITY, BluetoothShare.DESTINATION, BluetoothShare.DIRECTION}, selection, "timestamp DESC");
        if (this.mTransferCursor != null) {
            this.mIdColumnId = this.mTransferCursor.getColumnIndexOrThrow("_id");
            this.mTransferAdapter = new BluetoothOppTransferAdapter(this, C0000R.layout.bluetooth_transfer_item, this.mTransferCursor);
            this.mListView.setAdapter(this.mTransferAdapter);
            this.mListView.setScrollBarStyle(16777216);
            this.mListView.setOnCreateContextMenuListener(this);
            this.mListView.setOnItemClickListener(this);
        }
        this.mNotifier = new BluetoothOppNotification(this);
        this.mContextMenu = false;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        if (!(this.mTransferCursor == null || this.mShowAllIncoming)) {
            getMenuInflater().inflate(C0000R.menu.transferhistory, menu);
        }
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!this.mShowAllIncoming) {
            menu.findItem(C0000R.id.transfer_menu_clear_all).setEnabled(getClearableCount() > 0);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case C0000R.id.transfer_menu_clear_all:
                promptClearList();
                return true;
            default:
                return false;
        }
    }

    public boolean onContextItemSelected(MenuItem item) {
        this.mTransferCursor.moveToPosition(this.mContextMenuPosition);
        switch (item.getItemId()) {
            case C0000R.id.transfer_menu_open:
                openCompleteTransfer();
                updateNotificationWhenBtDisabled();
                return true;
            case C0000R.id.transfer_menu_clear:
                BluetoothOppUtility.updateVisibilityToHidden(this, Uri.parse(BluetoothShare.CONTENT_URI + "/" + this.mTransferCursor.getInt(this.mIdColumnId)));
                updateNotificationWhenBtDisabled();
                return true;
            default:
                return false;
        }
    }

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (this.mTransferCursor != null) {
            this.mContextMenu = true;
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
            this.mTransferCursor.moveToPosition(info.position);
            this.mContextMenuPosition = info.position;
            String fileName = this.mTransferCursor.getString(this.mTransferCursor.getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT));
            if (fileName == null) {
                fileName = getString(C0000R.string.unknown_file);
            }
            menu.setHeaderTitle(fileName);
            MenuInflater inflater = getMenuInflater();
            if (this.mShowAllIncoming) {
                inflater.inflate(C0000R.menu.receivedfilescontextfinished, menu);
            } else {
                inflater.inflate(C0000R.menu.transferhistorycontextfinished, menu);
            }
        }
    }

    private void promptClearList() {
        new Builder(this).setTitle(C0000R.string.transfer_clear_dlg_title).setMessage(C0000R.string.transfer_clear_dlg_msg).setPositiveButton(17039370, new C00481()).setNegativeButton(17039360, null).show();
    }

    private int getClearableCount() {
        int count = 0;
        if (this.mTransferCursor.moveToFirst()) {
            while (!this.mTransferCursor.isAfterLast()) {
                if (BluetoothShare.isStatusCompleted(this.mTransferCursor.getInt(this.mTransferCursor.getColumnIndexOrThrow(BluetoothShare.STATUS)))) {
                    count++;
                }
                this.mTransferCursor.moveToNext();
            }
        }
        return count;
    }

    private void clearAllDownloads() {
        if (this.mTransferCursor.moveToFirst()) {
            while (!this.mTransferCursor.isAfterLast()) {
                BluetoothOppUtility.updateVisibilityToHidden(this, Uri.parse(BluetoothShare.CONTENT_URI + "/" + this.mTransferCursor.getInt(this.mIdColumnId)));
                this.mTransferCursor.moveToNext();
            }
            updateNotificationWhenBtDisabled();
        }
    }

    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        if (!this.mContextMenu) {
            this.mTransferCursor.moveToPosition(position);
            openCompleteTransfer();
            updateNotificationWhenBtDisabled();
        }
        this.mContextMenu = false;
    }

    private void openCompleteTransfer() {
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + this.mTransferCursor.getInt(this.mIdColumnId));
        BluetoothOppTransferInfo transInfo = BluetoothOppUtility.queryRecord(this, contentUri);
        if (transInfo == null) {
            Log.e(TAG, "Error: Can not get data from db");
        } else if (transInfo.mDirection == 1 && BluetoothShare.isStatusSuccess(transInfo.mStatus)) {
            BluetoothOppUtility.updateVisibilityToHidden(this, contentUri);
            BluetoothOppUtility.openReceivedFile(this, transInfo.mFileName, transInfo.mFileType, transInfo.mTimeStamp, contentUri);
        } else {
            Intent in = new Intent(this, BluetoothOppTransferActivity.class);
            in.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
            in.setDataAndNormalize(contentUri);
            startActivity(in);
        }
    }

    private void updateNotificationWhenBtDisabled() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            this.mNotifier.updateNotification();
        }
    }
}
