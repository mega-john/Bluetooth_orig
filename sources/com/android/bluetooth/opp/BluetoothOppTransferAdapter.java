package com.android.bluetooth.opp;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import com.android.bluetooth.C0000R;
import java.util.Date;

public class BluetoothOppTransferAdapter extends ResourceCursorAdapter {
    private Context mContext;

    public BluetoothOppTransferAdapter(Context context, int layout, Cursor c) {
        super(context, layout, c);
        this.mContext = context;
    }

    public void bindView(View view, Context context, Cursor cursor) {
        Resources r = context.getResources();
        ImageView iv = (ImageView) view.findViewById(C0000R.id.transfer_icon);
        int status = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.STATUS));
        int dir = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.DIRECTION));
        if (BluetoothShare.isStatusError(status)) {
            iv.setImageResource(17301624);
        } else if (dir == 0) {
            iv.setImageResource(17301641);
        } else {
            iv.setImageResource(17301634);
        }
        TextView tv = (TextView) view.findViewById(C0000R.id.transfer_title);
        String title = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT));
        if (title == null) {
            title = this.mContext.getString(C0000R.string.unknown_file);
        }
        tv.setText(title);
        tv = (TextView) view.findViewById(C0000R.id.targetdevice);
        String deviceName = BluetoothOppManager.getInstance(context).getDeviceName(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.DESTINATION))));
        tv.setText(deviceName);
        long totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES));
        if (BluetoothShare.isStatusCompleted(status)) {
            tv = (TextView) view.findViewById(C0000R.id.complete_text);
            tv.setVisibility(0);
            if (BluetoothShare.isStatusError(status)) {
                tv.setText(BluetoothOppUtility.getStatusDescription(this.mContext, status, deviceName));
            } else {
                String completeText;
                if (dir == 1) {
                    completeText = r.getString(C0000R.string.download_success, new Object[]{Formatter.formatFileSize(this.mContext, totalBytes)});
                } else {
                    completeText = r.getString(C0000R.string.upload_success, new Object[]{Formatter.formatFileSize(this.mContext, totalBytes)});
                }
                tv.setText(completeText);
            }
            long time = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
            Date d = new Date(time);
            CharSequence str = DateUtils.isToday(time) ? DateFormat.getTimeFormat(this.mContext).format(d) : DateFormat.getDateFormat(this.mContext).format(d);
            tv = (TextView) view.findViewById(C0000R.id.complete_date);
            tv.setVisibility(0);
            tv.setText(str);
        }
    }
}
