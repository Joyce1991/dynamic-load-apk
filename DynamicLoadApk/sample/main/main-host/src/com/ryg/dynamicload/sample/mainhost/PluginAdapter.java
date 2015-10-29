package com.ryg.dynamicload.sample.mainhost;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.ryg.utils.DLUtils;

import java.io.File;
import java.util.List;

/**
 * Created by jalen-pc on 2015/10/29.
 */
public class PluginAdapter extends BaseAdapter {
    private Context mContext;
    private List<PluginItem> mPluginItems;

    public PluginAdapter(Context mContext) {
        this.mContext = mContext;
    }

    public void setData(List<PluginItem> datas) {
        this.mPluginItems = datas;
    }

    @Override
    public int getCount() {
        return mPluginItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mPluginItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.plugin_item, parent, false);
            holder = new ViewHolder();
            holder.appIcon = (ImageView) convertView.findViewById(R.id.app_icon);
            holder.appName = (TextView) convertView.findViewById(R.id.app_name);
            holder.apkName = (TextView) convertView.findViewById(R.id.apk_name);
            holder.packageName = (TextView) convertView.findViewById(R.id.package_name);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        PluginItem item = mPluginItems.get(position);
        PackageInfo packageInfo = item.getPackageInfo();
        holder.appIcon.setImageDrawable(DLUtils.getAppIcon(mContext, item.getPluginPath()));
        holder.appName.setText(DLUtils.getAppLabel(mContext, item.getPluginPath()));
        holder.apkName.setText(item.getPluginPath().substring(item.getPluginPath().lastIndexOf(File.separatorChar) + 1));
        holder.packageName.setText(packageInfo.applicationInfo.packageName + "\n" +
                item.getLauncherActivityName() + "\n" +
                item.getLauncherServiceName());
        return convertView;
    }

    static class ViewHolder {
        public ImageView appIcon;
        public TextView appName;
        public TextView apkName;
        public TextView packageName;
    }
}
