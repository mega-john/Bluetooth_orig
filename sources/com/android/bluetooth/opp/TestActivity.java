package com.android.bluetooth.opp;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import com.android.bluetooth.C0000R;

public class TestActivity extends Activity {
    public OnClickListener ackRecordListener = new C00524();
    public String currentInsert;
    public OnClickListener deleteAllRecordListener = new C00535();
    public OnClickListener deleteRecordListener = new C00502();
    public OnClickListener insertRecordListener = new C00491();
    EditText mAckView;
    EditText mAddressView;
    public int mCurrentByte = 0;
    EditText mDeleteView;
    EditText mInsertView;
    EditText mMediaView;
    EditText mUpdateView;
    public OnClickListener notifyTcpServerListener = new C00567();
    TestTcpServer server;
    public OnClickListener startTcpServerListener = new C00546();
    public OnClickListener updateRecordListener = new C00513();

    /* renamed from: com.android.bluetooth.opp.TestActivity$1 */
    class C00491 implements OnClickListener {
        C00491() {
        }

        public void onClick(View view) {
            String address = null;
            if (TestActivity.this.mAddressView.getText().length() != 0) {
                address = TestActivity.this.mAddressView.getText().toString();
                Log.v(Constants.TAG, "Send to address  " + address);
            }
            if (address == null) {
                address = "00:17:83:58:5D:CC";
            }
            Integer media = null;
            if (TestActivity.this.mMediaView.getText().length() != 0) {
                media = Integer.valueOf(Integer.parseInt(TestActivity.this.mMediaView.getText().toString().trim()));
                Log.v(Constants.TAG, "Send media no.  " + media);
            }
            if (media == null) {
                media = Integer.valueOf(1);
            }
            ContentValues values = new ContentValues();
            values.put("uri", "content://media/external/images/media/" + media);
            values.put(BluetoothShare.DESTINATION, address);
            values.put(BluetoothShare.DIRECTION, Integer.valueOf(0));
            values.put("timestamp", Long.valueOf(System.currentTimeMillis()));
            Integer records = null;
            if (TestActivity.this.mInsertView.getText().length() != 0) {
                records = Integer.valueOf(Integer.parseInt(TestActivity.this.mInsertView.getText().toString().trim()));
                Log.v(Constants.TAG, "parseInt  " + records);
            }
            if (records == null) {
                records = Integer.valueOf(1);
            }
            for (int i = 0; i < records.intValue(); i++) {
                Uri contentUri = TestActivity.this.getContentResolver().insert(BluetoothShare.CONTENT_URI, values);
                Log.v(Constants.TAG, "insert contentUri: " + contentUri);
                TestActivity.this.currentInsert = (String) contentUri.getPathSegments().get(1);
                Log.v(Constants.TAG, "currentInsert = " + TestActivity.this.currentInsert);
            }
        }
    }

    /* renamed from: com.android.bluetooth.opp.TestActivity$2 */
    class C00502 implements OnClickListener {
        C00502() {
        }

        public void onClick(View view) {
            TestActivity.this.getContentResolver().delete(Uri.parse(BluetoothShare.CONTENT_URI + "/" + TestActivity.this.mDeleteView.getText().toString()), null, null);
        }
    }

    /* renamed from: com.android.bluetooth.opp.TestActivity$3 */
    class C00513 implements OnClickListener {
        C00513() {
        }

        public void onClick(View view) {
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + TestActivity.this.mUpdateView.getText().toString());
            ContentValues updateValues = new ContentValues();
            updateValues.put(BluetoothShare.USER_CONFIRMATION, Integer.valueOf(1));
            TestActivity.this.getContentResolver().update(contentUri, updateValues, null, null);
        }
    }

    /* renamed from: com.android.bluetooth.opp.TestActivity$4 */
    class C00524 implements OnClickListener {
        C00524() {
        }

        public void onClick(View view) {
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + TestActivity.this.mAckView.getText().toString());
            ContentValues updateValues = new ContentValues();
            updateValues.put(BluetoothShare.VISIBILITY, Integer.valueOf(1));
            TestActivity.this.getContentResolver().update(contentUri, updateValues, null, null);
        }
    }

    /* renamed from: com.android.bluetooth.opp.TestActivity$5 */
    class C00535 implements OnClickListener {
        C00535() {
        }

        public void onClick(View view) {
            TestActivity.this.getContentResolver().delete(Uri.parse(BluetoothShare.CONTENT_URI + ""), null, null);
        }
    }

    /* renamed from: com.android.bluetooth.opp.TestActivity$6 */
    class C00546 implements OnClickListener {
        C00546() {
        }

        public void onClick(View view) {
            TestActivity.this.server = new TestTcpServer();
            new Thread(TestActivity.this.server).start();
        }
    }

    /* renamed from: com.android.bluetooth.opp.TestActivity$7 */
    class C00567 implements OnClickListener {

        /* renamed from: com.android.bluetooth.opp.TestActivity$7$1 */
        class C00551 extends Thread {
            C00551() {
            }

            public void run() {
                synchronized (TestActivity.this.server) {
                    TestActivity.this.server.f67a = true;
                    TestActivity.this.server.notify();
                }
            }
        }

        C00567() {
        }

        public void onClick(View view) {
            new C00551().start();
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent.getAction();
        Context c = getBaseContext();
        if ("android.intent.action.SEND".equals(action)) {
            String type = intent.getType();
            Uri stream = (Uri) intent.getParcelableExtra("android.intent.extra.STREAM");
            if (!(stream == null || type == null)) {
                Log.v(Constants.TAG, " Get share intent with Uri " + stream + " mimetype is " + type);
                c.getContentResolver().query(stream, null, null, null, null).close();
            }
        }
        setContentView(C0000R.layout.testactivity_main);
        Button mInsertRecord = (Button) findViewById(C0000R.id.insert_record);
        Button mDeleteRecord = (Button) findViewById(C0000R.id.delete_record);
        Button mUpdateRecord = (Button) findViewById(C0000R.id.update_record);
        Button mAckRecord = (Button) findViewById(C0000R.id.ack_record);
        Button mDeleteAllRecord = (Button) findViewById(C0000R.id.deleteAll_record);
        this.mUpdateView = (EditText) findViewById(C0000R.id.update_text);
        this.mAckView = (EditText) findViewById(C0000R.id.ack_text);
        this.mDeleteView = (EditText) findViewById(C0000R.id.delete_text);
        this.mInsertView = (EditText) findViewById(C0000R.id.insert_text);
        this.mAddressView = (EditText) findViewById(C0000R.id.address_text);
        this.mMediaView = (EditText) findViewById(C0000R.id.media_text);
        mInsertRecord.setOnClickListener(this.insertRecordListener);
        mDeleteRecord.setOnClickListener(this.deleteRecordListener);
        mUpdateRecord.setOnClickListener(this.updateRecordListener);
        mAckRecord.setOnClickListener(this.ackRecordListener);
        mDeleteAllRecord.setOnClickListener(this.deleteAllRecordListener);
        ((Button) findViewById(C0000R.id.start_server)).setOnClickListener(this.startTcpServerListener);
        ((Button) findViewById(C0000R.id.notify_server)).setOnClickListener(this.notifyTcpServerListener);
    }
}
