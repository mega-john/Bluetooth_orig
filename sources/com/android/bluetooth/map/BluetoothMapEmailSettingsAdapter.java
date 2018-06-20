package com.android.bluetooth.map;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnGroupExpandListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.bluetooth.C0000R;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public class BluetoothMapEmailSettingsAdapter extends BaseExpandableListAdapter {
    /* renamed from: D */
    private static final boolean f8D = true;
    private static final String TAG = "BluetoothMapEmailSettingsAdapter";
    /* renamed from: V */
    private static final boolean f9V = false;
    public Activity mActivity;
    private boolean mCheckAll = true;
    private int[] mGroupStatus;
    public LayoutInflater mInflater;
    private ArrayList<BluetoothMapEmailSettingsItem> mMainGroup;
    ArrayList<Boolean> mPositionArray;
    private LinkedHashMap<BluetoothMapEmailSettingsItem, ArrayList<BluetoothMapEmailSettingsItem>> mProupList;
    private int mSlotsLeft = 10;

    /* renamed from: com.android.bluetooth.map.BluetoothMapEmailSettingsAdapter$1 */
    class C00261 implements OnGroupExpandListener {
        C00261() {
        }

        public void onGroupExpand(int groupPosition) {
            if (((ArrayList) BluetoothMapEmailSettingsAdapter.this.mProupList.get((BluetoothMapEmailSettingsItem) BluetoothMapEmailSettingsAdapter.this.mMainGroup.get(groupPosition))).size() > 0) {
                BluetoothMapEmailSettingsAdapter.this.mGroupStatus[groupPosition] = 1;
            }
        }
    }

    private class ChildHolder {
        public CheckBox cb;
        public TextView title;

        private ChildHolder() {
        }
    }

    private class GroupHolder {
        public CheckBox cb;
        public ImageView imageView;
        public TextView title;

        private GroupHolder() {
        }
    }

    public BluetoothMapEmailSettingsAdapter(Activity act, ExpandableListView listView, LinkedHashMap<BluetoothMapEmailSettingsItem, ArrayList<BluetoothMapEmailSettingsItem>> groupsList, int enabledAccountsCounts) {
        this.mActivity = act;
        this.mProupList = groupsList;
        this.mInflater = act.getLayoutInflater();
        this.mGroupStatus = new int[groupsList.size()];
        this.mSlotsLeft -= enabledAccountsCounts;
        listView.setOnGroupExpandListener(new C00261());
        this.mMainGroup = new ArrayList();
        for (Entry<BluetoothMapEmailSettingsItem, ArrayList<BluetoothMapEmailSettingsItem>> mapEntry : this.mProupList.entrySet()) {
            this.mMainGroup.add(mapEntry.getKey());
        }
    }

    public BluetoothMapEmailSettingsItem getChild(int groupPosition, int childPosition) {
        return (BluetoothMapEmailSettingsItem) ((ArrayList) this.mProupList.get((BluetoothMapEmailSettingsItem) this.mMainGroup.get(groupPosition))).get(childPosition);
    }

    private ArrayList<BluetoothMapEmailSettingsItem> getChild(BluetoothMapEmailSettingsItem group) {
        return (ArrayList) this.mProupList.get(group);
    }

    public long getChildId(int groupPosition, int childPosition) {
        return 0;
    }

    public View getChildView(final int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        ChildHolder holder;
        if (convertView == null) {
            convertView = this.mInflater.inflate(C0000R.layout.bluetooth_map_email_settings_account_item, null);
            holder = new ChildHolder();
            holder.cb = (CheckBox) convertView.findViewById(C0000R.id.bluetooth_map_email_settings_item_check);
            holder.title = (TextView) convertView.findViewById(C0000R.id.bluetooth_map_email_settings_item_text_view);
            convertView.setTag(holder);
        } else {
            holder = (ChildHolder) convertView.getTag();
        }
        final BluetoothMapEmailSettingsItem child = getChild(groupPosition, childPosition);
        holder.cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                BluetoothMapEmailSettingsItem parentGroup = BluetoothMapEmailSettingsAdapter.this.getGroup(groupPosition);
                boolean oldIsChecked = child.mIsChecked;
                child.mIsChecked = isChecked;
                if (isChecked) {
                    ArrayList<BluetoothMapEmailSettingsItem> childList = BluetoothMapEmailSettingsAdapter.this.getChild(parentGroup);
                    int childIndex = childList.indexOf(child);
                    boolean isAllChildClicked = true;
                    if (BluetoothMapEmailSettingsAdapter.this.mSlotsLeft - childList.size() >= 0) {
                        int i = 0;
                        while (i < childList.size()) {
                            if (i != childIndex && !((BluetoothMapEmailSettingsItem) childList.get(i)).mIsChecked) {
                                isAllChildClicked = false;
                                BluetoothMapEmailSettingsDataHolder.mCheckedChilds.put(child.getName(), parentGroup.getName());
                                break;
                            }
                            i++;
                        }
                    } else {
                        BluetoothMapEmailSettingsAdapter.this.showWarning(BluetoothMapEmailSettingsAdapter.this.mActivity.getString(C0000R.string.bluetooth_map_email_settings_no_account_slots_left));
                        isAllChildClicked = false;
                        child.mIsChecked = false;
                    }
                    if (isAllChildClicked) {
                        parentGroup.mIsChecked = true;
                        if (!BluetoothMapEmailSettingsDataHolder.mCheckedChilds.containsKey(child.getName())) {
                            BluetoothMapEmailSettingsDataHolder.mCheckedChilds.put(child.getName(), parentGroup.getName());
                        }
                        BluetoothMapEmailSettingsAdapter.this.mCheckAll = false;
                    }
                } else if (parentGroup.mIsChecked) {
                    parentGroup.mIsChecked = false;
                    BluetoothMapEmailSettingsAdapter.this.mCheckAll = false;
                    BluetoothMapEmailSettingsDataHolder.mCheckedChilds.remove(child.getName());
                } else {
                    BluetoothMapEmailSettingsAdapter.this.mCheckAll = true;
                    BluetoothMapEmailSettingsDataHolder.mCheckedChilds.remove(child.getName());
                }
                BluetoothMapEmailSettingsAdapter.this.notifyDataSetChanged();
                if (child.mIsChecked != oldIsChecked) {
                    BluetoothMapEmailSettingsAdapter.this.updateAccount(child);
                }
            }
        });
        holder.cb.setChecked(child.mIsChecked);
        holder.title.setText(child.getName());
        Log.i("childs are", BluetoothMapEmailSettingsDataHolder.mCheckedChilds.toString());
        return convertView;
    }

    public int getChildrenCount(int groupPosition) {
        return ((ArrayList) this.mProupList.get((BluetoothMapEmailSettingsItem) this.mMainGroup.get(groupPosition))).size();
    }

    public BluetoothMapEmailSettingsItem getGroup(int groupPosition) {
        return (BluetoothMapEmailSettingsItem) this.mMainGroup.get(groupPosition);
    }

    public int getGroupCount() {
        return this.mMainGroup.size();
    }

    public void onGroupCollapsed(int groupPosition) {
        super.onGroupCollapsed(groupPosition);
    }

    public void onGroupExpanded(int groupPosition) {
        super.onGroupExpanded(groupPosition);
    }

    public long getGroupId(int groupPosition) {
        return 0;
    }

    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        GroupHolder holder;
        if (convertView == null) {
            convertView = this.mInflater.inflate(C0000R.layout.bluetooth_map_email_settings_account_group, null);
            holder = new GroupHolder();
            holder.cb = (CheckBox) convertView.findViewById(C0000R.id.bluetooth_map_email_settings_group_checkbox);
            holder.imageView = (ImageView) convertView.findViewById(C0000R.id.bluetooth_map_email_settings_group_icon);
            holder.title = (TextView) convertView.findViewById(C0000R.id.bluetooth_map_email_settings_group_text_view);
            convertView.setTag(holder);
        } else {
            holder = (GroupHolder) convertView.getTag();
        }
        final BluetoothMapEmailSettingsItem groupItem = getGroup(groupPosition);
        holder.imageView.setImageDrawable(groupItem.getIcon());
        holder.title.setText(groupItem.getName());
        holder.cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            /* renamed from: com.android.bluetooth.map.BluetoothMapEmailSettingsAdapter$3$1 */
            class C00281 implements Runnable {
                C00281() {
                }

                public void run() {
                    if (!BluetoothMapEmailSettingsAdapter.this.mCheckAll) {
                        BluetoothMapEmailSettingsAdapter.this.mCheckAll = true;
                    }
                }
            }

            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (BluetoothMapEmailSettingsAdapter.this.mCheckAll) {
                    Iterator i$ = BluetoothMapEmailSettingsAdapter.this.getChild(groupItem).iterator();
                    while (i$.hasNext()) {
                        BluetoothMapEmailSettingsItem children = (BluetoothMapEmailSettingsItem) i$.next();
                        boolean oldIsChecked = children.mIsChecked;
                        if (BluetoothMapEmailSettingsAdapter.this.mSlotsLeft > 0) {
                            children.mIsChecked = isChecked;
                            if (oldIsChecked != children.mIsChecked) {
                                BluetoothMapEmailSettingsAdapter.this.updateAccount(children);
                            }
                        } else {
                            BluetoothMapEmailSettingsAdapter.this.showWarning(BluetoothMapEmailSettingsAdapter.this.mActivity.getString(C0000R.string.bluetooth_map_email_settings_no_account_slots_left));
                            isChecked = false;
                        }
                    }
                }
                groupItem.mIsChecked = isChecked;
                BluetoothMapEmailSettingsAdapter.this.notifyDataSetChanged();
                new Handler().postDelayed(new C00281(), 50);
            }
        });
        holder.cb.setChecked(groupItem.mIsChecked);
        return convertView;
    }

    public boolean hasStableIds() {
        return true;
    }

    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    public void updateAccount(BluetoothMapEmailSettingsItem account) {
        updateSlotCounter(account.mIsChecked);
        Log.d(TAG, "Updating account settings for " + account.getName() + ". Value is:" + account.mIsChecked);
        ContentResolver mResolver = this.mActivity.getContentResolver();
        Uri uri = Uri.parse(account.mBase_uri_no_account + "/" + "Account");
        ContentValues values = new ContentValues();
        values.put("flag_expose", Integer.valueOf(account.mIsChecked ? 1 : 0));
        values.put("_id", account.getId());
        mResolver.update(uri, values, null, null);
    }

    private void updateSlotCounter(boolean isChecked) {
        CharSequence text;
        if (isChecked) {
            this.mSlotsLeft--;
        } else {
            this.mSlotsLeft++;
        }
        if (this.mSlotsLeft <= 0) {
            text = this.mActivity.getString(C0000R.string.bluetooth_map_email_settings_no_account_slots_left);
        } else {
            text = this.mActivity.getString(C0000R.string.bluetooth_map_email_settings_count) + " " + String.valueOf(this.mSlotsLeft);
        }
        Toast.makeText(this.mActivity, text, 0).show();
    }

    private void showWarning(String text) {
        Toast.makeText(this.mActivity, text, 0).show();
    }
}
