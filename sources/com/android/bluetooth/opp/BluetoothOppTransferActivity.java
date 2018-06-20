package com.android.bluetooth.opp;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.android.bluetooth.C0000R;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;

public class BluetoothOppTransferActivity extends AlertActivity implements OnClickListener {
    /* renamed from: D */
    private static final boolean f59D = true;
    public static final int DIALOG_RECEIVE_COMPLETE_FAIL = 2;
    public static final int DIALOG_RECEIVE_COMPLETE_SUCCESS = 1;
    public static final int DIALOG_RECEIVE_ONGOING = 0;
    public static final int DIALOG_SEND_COMPLETE_FAIL = 5;
    public static final int DIALOG_SEND_COMPLETE_SUCCESS = 4;
    public static final int DIALOG_SEND_ONGOING = 3;
    private static final String TAG = "BluetoothOppTransferActivity";
    /* renamed from: V */
    private static final boolean f60V = false;
    private BluetoothAdapter mAdapter;
    boolean mIsComplete;
    private TextView mLine1View;
    private TextView mLine2View;
    private TextView mLine3View;
    private TextView mLine5View;
    private boolean mNeedUpdateButton = false;
    private BluetoothTransferContentObserver mObserver;
    private AlertParams mPara;
    private TextView mPercentView;
    private ProgressBar mProgressTransfer;
    private BluetoothOppTransferInfo mTransInfo;
    private Uri mUri;
    private View mView = null;
    private int mWhichDialog;

    private class BluetoothTransferContentObserver extends ContentObserver {
        public BluetoothTransferContentObserver() {
            super(new Handler());
        }

        public void onChange(boolean selfChange) {
            BluetoothOppTransferActivity.this.mNeedUpdateButton = true;
            BluetoothOppTransferActivity.this.updateProgressbar();
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mUri = getIntent().getData();
        this.mTransInfo = new BluetoothOppTransferInfo();
        this.mTransInfo = BluetoothOppUtility.queryRecord(this, this.mUri);
        if (this.mTransInfo == null) {
            finish();
            return;
        }
        this.mIsComplete = BluetoothShare.isStatusCompleted(this.mTransInfo.mStatus);
        displayWhichDialog();
        if (!this.mIsComplete) {
            this.mObserver = new BluetoothTransferContentObserver();
            getContentResolver().registerContentObserver(BluetoothShare.CONTENT_URI, true, this.mObserver);
        }
        if (!(this.mWhichDialog == 3 || this.mWhichDialog == 0)) {
            BluetoothOppUtility.updateVisibilityToHidden(this, this.mUri);
        }
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        setUpDialog();
    }

    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        if (this.mObserver != null) {
            getContentResolver().unregisterContentObserver(this.mObserver);
        }
        super.onDestroy();
    }

    private void displayWhichDialog() {
        int direction = this.mTransInfo.mDirection;
        boolean isSuccess = BluetoothShare.isStatusSuccess(this.mTransInfo.mStatus);
        boolean isComplete = BluetoothShare.isStatusCompleted(this.mTransInfo.mStatus);
        if (direction == 1) {
            if (isComplete) {
                if (isSuccess) {
                    this.mWhichDialog = 1;
                } else if (!isSuccess) {
                    this.mWhichDialog = 2;
                }
            } else if (!isComplete) {
                this.mWhichDialog = 0;
            }
        } else if (direction != 0) {
        } else {
            if (isComplete) {
                if (isSuccess) {
                    this.mWhichDialog = 4;
                } else if (!isSuccess) {
                    this.mWhichDialog = 5;
                }
            } else if (!isComplete) {
                this.mWhichDialog = 3;
            }
        }
    }

    private void setUpDialog() {
        this.mPara = this.mAlertParams;
        this.mPara.mTitle = getString(C0000R.string.download_title);
        if (this.mWhichDialog == 0 || this.mWhichDialog == 3) {
            this.mPara.mPositiveButtonText = getString(C0000R.string.download_ok);
            this.mPara.mPositiveButtonListener = this;
            this.mPara.mNegativeButtonText = getString(C0000R.string.download_cancel);
            this.mPara.mNegativeButtonListener = this;
        } else if (this.mWhichDialog == 1) {
            this.mPara.mPositiveButtonText = getString(C0000R.string.download_succ_ok);
            this.mPara.mPositiveButtonListener = this;
        } else if (this.mWhichDialog == 2) {
            this.mPara.mIconAttrId = 16843605;
            this.mPara.mPositiveButtonText = getString(C0000R.string.download_fail_ok);
            this.mPara.mPositiveButtonListener = this;
        } else if (this.mWhichDialog == 4) {
            this.mPara.mPositiveButtonText = getString(C0000R.string.upload_succ_ok);
            this.mPara.mPositiveButtonListener = this;
        } else if (this.mWhichDialog == 5) {
            this.mPara.mIconAttrId = 16843605;
            this.mPara.mPositiveButtonText = getString(C0000R.string.upload_fail_ok);
            this.mPara.mPositiveButtonListener = this;
            this.mPara.mNegativeButtonText = getString(C0000R.string.upload_fail_cancel);
            this.mPara.mNegativeButtonListener = this;
        }
        this.mPara.mView = createView();
        setupAlert();
    }

    private View createView() {
        this.mView = getLayoutInflater().inflate(C0000R.layout.file_transfer, null);
        this.mProgressTransfer = (ProgressBar) this.mView.findViewById(C0000R.id.progress_transfer);
        this.mPercentView = (TextView) this.mView.findViewById(C0000R.id.progress_percent);
        customizeViewContent();
        this.mNeedUpdateButton = false;
        updateProgressbar();
        return this.mView;
    }

    private void customizeViewContent() {
        String tmp;
        if (this.mWhichDialog == 0 || this.mWhichDialog == 1) {
            this.mLine1View = (TextView) this.mView.findViewById(C0000R.id.line1_view);
            this.mLine1View.setText(getString(C0000R.string.download_line1, new Object[]{this.mTransInfo.mDeviceName}));
            this.mLine2View = (TextView) this.mView.findViewById(C0000R.id.line2_view);
            this.mLine2View.setText(getString(C0000R.string.download_line2, new Object[]{this.mTransInfo.mFileName}));
            this.mLine3View = (TextView) this.mView.findViewById(C0000R.id.line3_view);
            tmp = getString(C0000R.string.download_line3, new Object[]{Formatter.formatFileSize(this, (long) this.mTransInfo.mTotalBytes)});
            this.mLine3View.setText(tmp);
            this.mLine5View = (TextView) this.mView.findViewById(C0000R.id.line5_view);
            if (this.mWhichDialog == 0) {
                tmp = getString(C0000R.string.download_line5);
            } else if (this.mWhichDialog == 1) {
                tmp = getString(C0000R.string.download_succ_line5);
            }
            this.mLine5View.setText(tmp);
        } else if (this.mWhichDialog == 3 || this.mWhichDialog == 4) {
            this.mLine1View = (TextView) this.mView.findViewById(C0000R.id.line1_view);
            this.mLine1View.setText(getString(C0000R.string.upload_line1, new Object[]{this.mTransInfo.mDeviceName}));
            this.mLine2View = (TextView) this.mView.findViewById(C0000R.id.line2_view);
            this.mLine2View.setText(getString(C0000R.string.download_line2, new Object[]{this.mTransInfo.mFileName}));
            this.mLine3View = (TextView) this.mView.findViewById(C0000R.id.line3_view);
            tmp = getString(C0000R.string.upload_line3, new Object[]{this.mTransInfo.mFileType, Formatter.formatFileSize(this, (long) this.mTransInfo.mTotalBytes)});
            this.mLine3View.setText(tmp);
            this.mLine5View = (TextView) this.mView.findViewById(C0000R.id.line5_view);
            if (this.mWhichDialog == 3) {
                tmp = getString(C0000R.string.upload_line5);
            } else if (this.mWhichDialog == 4) {
                tmp = getString(C0000R.string.upload_succ_line5);
            }
            this.mLine5View.setText(tmp);
        } else if (this.mWhichDialog == 2) {
            if (this.mTransInfo.mStatus == BluetoothShare.STATUS_ERROR_SDCARD_FULL) {
                this.mLine1View = (TextView) this.mView.findViewById(C0000R.id.line1_view);
                this.mLine1View.setText(getString(C0000R.string.bt_sm_2_1, new Object[]{this.mTransInfo.mDeviceName}));
                this.mLine2View = (TextView) this.mView.findViewById(C0000R.id.line2_view);
                this.mLine2View.setText(getString(C0000R.string.download_fail_line2, new Object[]{this.mTransInfo.mFileName}));
                this.mLine3View = (TextView) this.mView.findViewById(C0000R.id.line3_view);
                this.mLine3View.setText(getString(C0000R.string.bt_sm_2_2, new Object[]{Formatter.formatFileSize(this, (long) this.mTransInfo.mTotalBytes)}));
            } else {
                this.mLine1View = (TextView) this.mView.findViewById(C0000R.id.line1_view);
                this.mLine1View.setText(getString(C0000R.string.download_fail_line1));
                this.mLine2View = (TextView) this.mView.findViewById(C0000R.id.line2_view);
                this.mLine2View.setText(getString(C0000R.string.download_fail_line2, new Object[]{this.mTransInfo.mFileName}));
                this.mLine3View = (TextView) this.mView.findViewById(C0000R.id.line3_view);
                this.mLine3View.setText(getString(C0000R.string.download_fail_line3, new Object[]{BluetoothOppUtility.getStatusDescription(this, this.mTransInfo.mStatus, this.mTransInfo.mDeviceName)}));
            }
            this.mLine5View = (TextView) this.mView.findViewById(C0000R.id.line5_view);
            this.mLine5View.setVisibility(8);
        } else if (this.mWhichDialog == 5) {
            this.mLine1View = (TextView) this.mView.findViewById(C0000R.id.line1_view);
            this.mLine1View.setText(getString(C0000R.string.upload_fail_line1, new Object[]{this.mTransInfo.mDeviceName}));
            this.mLine2View = (TextView) this.mView.findViewById(C0000R.id.line2_view);
            this.mLine2View.setText(getString(C0000R.string.upload_fail_line1_2, new Object[]{this.mTransInfo.mFileName}));
            this.mLine3View = (TextView) this.mView.findViewById(C0000R.id.line3_view);
            this.mLine3View.setText(getString(C0000R.string.download_fail_line3, new Object[]{BluetoothOppUtility.getStatusDescription(this, this.mTransInfo.mStatus, this.mTransInfo.mDeviceName)}));
            this.mLine5View = (TextView) this.mView.findViewById(C0000R.id.line5_view);
            this.mLine5View.setVisibility(8);
        }
        if (BluetoothShare.isStatusError(this.mTransInfo.mStatus)) {
            this.mProgressTransfer.setVisibility(8);
            this.mPercentView.setVisibility(8);
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case -2:
                if (this.mWhichDialog != 0 && this.mWhichDialog != 3) {
                    if (this.mWhichDialog == 5) {
                        BluetoothOppUtility.updateVisibilityToHidden(this, this.mUri);
                        break;
                    }
                }
                getContentResolver().delete(this.mUri, null, null);
                String msg = "";
                if (this.mWhichDialog == 0) {
                    msg = getString(C0000R.string.bt_toast_3, new Object[]{this.mTransInfo.mDeviceName});
                } else if (this.mWhichDialog == 3) {
                    msg = getString(C0000R.string.bt_toast_6, new Object[]{this.mTransInfo.mDeviceName});
                }
                Toast.makeText(this, msg, 0).show();
                ((NotificationManager) getSystemService("notification")).cancel(this.mTransInfo.mID);
                break;
                break;
            case -1:
                if (this.mWhichDialog != 1) {
                    if (this.mWhichDialog != 5) {
                        if (this.mWhichDialog == 4) {
                            BluetoothOppUtility.updateVisibilityToHidden(this, this.mUri);
                            ((NotificationManager) getSystemService("notification")).cancel(this.mTransInfo.mID);
                            break;
                        }
                    }
                    BluetoothOppUtility.updateVisibilityToHidden(this, this.mUri);
                    ((NotificationManager) getSystemService("notification")).cancel(this.mTransInfo.mID);
                    Uri uri = BluetoothOppUtility.originalUri(Uri.parse(this.mTransInfo.mFileUri));
                    BluetoothOppSendFileInfo sendFileInfo = BluetoothOppSendFileInfo.generateFileInfo(this, uri, this.mTransInfo.mFileType);
                    uri = BluetoothOppUtility.generateUri(uri, sendFileInfo);
                    BluetoothOppUtility.putSendFileInfo(uri, sendFileInfo);
                    this.mTransInfo.mFileUri = uri.toString();
                    BluetoothOppUtility.retryTransfer(this, this.mTransInfo);
                    BluetoothDevice remoteDevice = this.mAdapter.getRemoteDevice(this.mTransInfo.mDestAddr);
                    Toast.makeText(this, getString(C0000R.string.bt_toast_4, new Object[]{BluetoothOppManager.getInstance(this).getDeviceName(remoteDevice)}), 0).show();
                    break;
                }
                BluetoothOppUtility.openReceivedFile(this, this.mTransInfo.mFileName, this.mTransInfo.mFileType, this.mTransInfo.mTimeStamp, this.mUri);
                BluetoothOppUtility.updateVisibilityToHidden(this, this.mUri);
                ((NotificationManager) getSystemService("notification")).cancel(this.mTransInfo.mID);
                break;
                break;
        }
        finish();
    }

    private void updateProgressbar() {
        this.mTransInfo = BluetoothOppUtility.queryRecord(this, this.mUri);
        if (this.mTransInfo != null) {
            if (this.mTransInfo.mTotalBytes == 0) {
                this.mProgressTransfer.setMax(100);
            } else {
                this.mProgressTransfer.setMax(this.mTransInfo.mTotalBytes);
            }
            this.mProgressTransfer.setProgress(this.mTransInfo.mCurrentBytes);
            this.mPercentView.setText(BluetoothOppUtility.formatProgressText((long) this.mTransInfo.mTotalBytes, (long) this.mTransInfo.mCurrentBytes));
            if (!this.mIsComplete && BluetoothShare.isStatusCompleted(this.mTransInfo.mStatus) && this.mNeedUpdateButton) {
                displayWhichDialog();
                updateButton();
                customizeViewContent();
            }
        }
    }

    private void updateButton() {
        if (this.mWhichDialog == 1) {
            this.mAlert.getButton(-2).setVisibility(8);
            this.mAlert.getButton(-1).setText(getString(C0000R.string.download_succ_ok));
        } else if (this.mWhichDialog == 2) {
            this.mAlert.setIcon(this.mAlert.getIconAttributeResId(16843605));
            this.mAlert.getButton(-2).setVisibility(8);
            this.mAlert.getButton(-1).setText(getString(C0000R.string.download_fail_ok));
        } else if (this.mWhichDialog == 4) {
            this.mAlert.getButton(-2).setVisibility(8);
            this.mAlert.getButton(-1).setText(getString(C0000R.string.upload_succ_ok));
        } else if (this.mWhichDialog == 5) {
            this.mAlert.setIcon(this.mAlert.getIconAttributeResId(16843605));
            this.mAlert.getButton(-1).setText(getString(C0000R.string.upload_fail_ok));
            this.mAlert.getButton(-2).setText(getString(C0000R.string.upload_fail_cancel));
        }
    }
}
