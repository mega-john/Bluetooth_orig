package com.android.bluetooth.opp;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings.System;
import android.util.Log;
import android.util.Patterns;
import com.android.bluetooth.C0000R;
import com.android.vcard.VCardConfig;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BluetoothOppLauncherActivity extends Activity {
    /* renamed from: D */
    private static final boolean f37D = true;
    private static final Pattern PLAIN_TEXT_TO_ESCAPE = Pattern.compile("[<>&]| {2,}|\r?\n");
    private static final String TAG = "BluetoothLauncherActivity";
    /* renamed from: V */
    private static final boolean f38V = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action.equals("android.intent.action.SEND") || action.equals("android.intent.action.SEND_MULTIPLE")) {
            if (!isBluetoothAllowed()) {
                Intent in = new Intent(this, BluetoothOppBtErrorActivity.class);
                in.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
                in.putExtra("title", getString(C0000R.string.airplane_error_title));
                in.putExtra("content", getString(C0000R.string.airplane_error_msg));
                startActivity(in);
                finish();
            } else if (action.equals("android.intent.action.SEND")) {
                final String type = intent.getType();
                final Uri stream = (Uri) intent.getParcelableExtra("android.intent.extra.STREAM");
                CharSequence extra_text = intent.getCharSequenceExtra("android.intent.extra.TEXT");
                if (stream != null && type != null) {
                    new Thread(new Runnable() {
                        public void run() {
                            BluetoothOppManager.getInstance(BluetoothOppLauncherActivity.this).saveSendingFileInfo(type, stream.toString(), false);
                            BluetoothOppLauncherActivity.this.launchDevicePicker();
                            BluetoothOppLauncherActivity.this.finish();
                        }
                    }).start();
                } else if (extra_text == null || type == null) {
                    Log.e(TAG, "type is null; or sending file URI is null");
                    finish();
                } else {
                    final Uri fileUri = creatFileForSharedContent(this, extra_text);
                    if (fileUri != null) {
                        new Thread(new Runnable() {
                            public void run() {
                                BluetoothOppManager.getInstance(BluetoothOppLauncherActivity.this).saveSendingFileInfo(type, fileUri.toString(), false);
                                BluetoothOppLauncherActivity.this.launchDevicePicker();
                                BluetoothOppLauncherActivity.this.finish();
                            }
                        }).start();
                        return;
                    }
                    Log.w(TAG, "Error trying to do set text...File not created!");
                    finish();
                }
            } else if (action.equals("android.intent.action.SEND_MULTIPLE")) {
                final String mimeType = intent.getType();
                final ArrayList<Uri> uris = intent.getParcelableArrayListExtra("android.intent.extra.STREAM");
                if (mimeType == null || uris == null) {
                    Log.e(TAG, "type is null; or sending files URIs are null");
                    finish();
                    return;
                }
                new Thread(new Runnable() {
                    public void run() {
                        BluetoothOppManager.getInstance(BluetoothOppLauncherActivity.this).saveSendingFileInfo(mimeType, uris, false);
                        BluetoothOppLauncherActivity.this.launchDevicePicker();
                        BluetoothOppLauncherActivity.this.finish();
                    }
                }).start();
            }
        } else if (action.equals(Constants.ACTION_OPEN)) {
            Uri uri = getIntent().getData();
            Intent intent1 = new Intent();
            intent1.setAction(action);
            intent1.setClassName("com.android.bluetooth", BluetoothOppReceiver.class.getName());
            intent1.setDataAndNormalize(uri);
            sendBroadcast(intent1);
            finish();
        } else {
            Log.w(TAG, "Unsupported action: " + action);
            finish();
        }
    }

    private final void launchDevicePicker() {
        if (BluetoothOppManager.getInstance(this).isEnabled()) {
            Intent in1 = new Intent("android.bluetooth.devicepicker.action.LAUNCH");
            in1.setFlags(VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT);
            in1.putExtra("android.bluetooth.devicepicker.extra.NEED_AUTH", false);
            in1.putExtra("android.bluetooth.devicepicker.extra.FILTER_TYPE", 2);
            in1.putExtra("android.bluetooth.devicepicker.extra.LAUNCH_PACKAGE", "com.android.bluetooth");
            in1.putExtra("android.bluetooth.devicepicker.extra.DEVICE_PICKER_LAUNCH_CLASS", BluetoothOppReceiver.class.getName());
            startActivity(in1);
            return;
        }
        Intent in = new Intent(this, BluetoothOppBtEnableActivity.class);
        in.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
        startActivity(in);
    }

    private final boolean isBluetoothAllowed() {
        boolean isAirplaneModeOn;
        ContentResolver resolver = getContentResolver();
        if (System.getInt(resolver, "airplane_mode_on", 0) == 1) {
            isAirplaneModeOn = true;
        } else {
            isAirplaneModeOn = false;
        }
        if (!isAirplaneModeOn) {
            return true;
        }
        String airplaneModeRadios = System.getString(resolver, "airplane_mode_radios");
        if (!(airplaneModeRadios == null ? true : airplaneModeRadios.contains("bluetooth"))) {
            return true;
        }
        String airplaneModeToggleableRadios = System.getString(resolver, "airplane_mode_toggleable_radios");
        if (airplaneModeToggleableRadios == null ? false : airplaneModeToggleableRadios.contains("bluetooth")) {
            return true;
        }
        return false;
    }

    private Uri creatFileForSharedContent(Context context, CharSequence shareContent) {
        if (shareContent == null) {
            return null;
        }
        Uri fileUri = null;
        FileOutputStream outStream = null;
        try {
            String fileName = getString(C0000R.string.bluetooth_share_file_name) + ".html";
            context.deleteFile(fileName);
            StringBuffer sb = new StringBuffer("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/></head><body>");
            String text = escapeCharacterToDisplay(shareContent.toString());
            Pattern webUrlProtocol = Pattern.compile("(?i)(http|https)://");
            Matcher m = Pattern.compile("(" + Patterns.WEB_URL.pattern() + ")|(" + Patterns.EMAIL_ADDRESS.pattern() + ")|(" + Patterns.PHONE.pattern() + ")").matcher(text);
            while (m.find()) {
                String matchStr = m.group();
                String link = null;
                if (Patterns.WEB_URL.matcher(matchStr).matches()) {
                    Matcher proto = webUrlProtocol.matcher(matchStr);
                    if (proto.find()) {
                        link = proto.group().toLowerCase(Locale.US) + matchStr.substring(proto.end());
                    } else {
                        link = "http://" + matchStr;
                    }
                } else if (Patterns.EMAIL_ADDRESS.matcher(matchStr).matches()) {
                    link = "mailto:" + matchStr;
                } else if (Patterns.PHONE.matcher(matchStr).matches()) {
                    link = "tel:" + matchStr;
                }
                if (link != null) {
                    m.appendReplacement(sb, String.format("<a href=\"%s\">%s</a>", new Object[]{link, matchStr}));
                }
            }
            m.appendTail(sb);
            sb.append("</body></html>");
            byte[] byteBuff = sb.toString().getBytes();
            outStream = context.openFileOutput(fileName, 0);
            if (outStream != null) {
                outStream.write(byteBuff, 0, byteBuff.length);
                fileUri = Uri.fromFile(new File(context.getFilesDir(), fileName));
                if (fileUri != null) {
                    Log.d(TAG, "Created one file for shared content: " + fileUri.toString());
                }
            }
            if (outStream == null) {
                return fileUri;
            }
            try {
                outStream.close();
                return fileUri;
            } catch (IOException e) {
                e.printStackTrace();
                return fileUri;
            }
        } catch (FileNotFoundException e2) {
            Log.e(TAG, "FileNotFoundException: " + e2.toString());
            e2.printStackTrace();
            if (outStream == null) {
                return null;
            }
            try {
                outStream.close();
                return null;
            } catch (IOException e3) {
                e3.printStackTrace();
                return null;
            }
        } catch (IOException e32) {
            Log.e(TAG, "IOException: " + e32.toString());
            if (outStream == null) {
                return null;
            }
            try {
                outStream.close();
                return null;
            } catch (IOException e322) {
                e322.printStackTrace();
                return null;
            }
        } catch (Exception e4) {
            Log.e(TAG, "Exception: " + e4.toString());
            if (outStream == null) {
                return null;
            }
            try {
                outStream.close();
                return null;
            } catch (IOException e3222) {
                e3222.printStackTrace();
                return null;
            }
        } catch (Throwable th) {
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e32222) {
                    e32222.printStackTrace();
                }
            }
        }
    }

    private static String escapeCharacterToDisplay(String text) {
        Matcher match = PLAIN_TEXT_TO_ESCAPE.matcher(text);
        if (!match.find()) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        int end = 0;
        do {
            int start = match.start();
            out.append(text.substring(end, start));
            end = match.end();
            int c = text.codePointAt(start);
            if (c == 32) {
                int n = end - start;
                for (int i = 1; i < n; i++) {
                    out.append("&nbsp;");
                }
                out.append(' ');
            } else if (c == 13 || c == 10) {
                out.append("<br>");
            } else if (c == 60) {
                out.append("&lt;");
            } else if (c == 62) {
                out.append("&gt;");
            } else if (c == 38) {
                out.append("&amp;");
            }
        } while (match.find());
        out.append(text.substring(end));
        return out.toString();
    }
}
