package com.ryg.dynamicload.sample.mainhost;

import android.content.pm.PackageInfo;

/**
 * 插件属性 <br/>
 * Created by jalen-pc on 2015/10/29.
 */
public class PluginItem {
    public PackageInfo getPackageInfo() {
        return packageInfo;
    }

    public void setPackageInfo(PackageInfo packageInfo) {
        this.packageInfo = packageInfo;
    }

    public String getPluginPath() {
        return pluginPath;
    }

    public void setPluginPath(String pluginPath) {
        this.pluginPath = pluginPath;
    }

    public String getLauncherActivityName() {
        return launcherActivityName;
    }

    public void setLauncherActivityName(String launcherActivityName) {
        this.launcherActivityName = launcherActivityName;
    }

    public String getLauncherServiceName() {
        return launcherServiceName;
    }

    public void setLauncherServiceName(String launcherServiceName) {
        this.launcherServiceName = launcherServiceName;
    }

    private PackageInfo packageInfo;
    private String pluginPath;
    private String launcherActivityName;
    private String launcherServiceName;


}
