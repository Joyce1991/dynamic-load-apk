/*
 * Copyright (C) 2014 singwhatiwanna(任玉刚) <singwhatiwanna@gmail.com>
 *
 * collaborator:田啸,宋思宇,Mr.Simple
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ryg.dynamicload.internal;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.ryg.dynamicload.DLBasePluginActivity;
import com.ryg.dynamicload.DLBasePluginFragmentActivity;
import com.ryg.dynamicload.DLBasePluginService;
import com.ryg.dynamicload.DLProxyActivity;
import com.ryg.dynamicload.DLProxyFragmentActivity;
import com.ryg.dynamicload.DLProxyService;
import com.ryg.utils.DLConstants;
import com.ryg.utils.SoLibManager;

import dalvik.system.DexClassLoader;

public class DLPluginManager {

    private static final String TAG = "DLPluginManager";

    /**
     * return value of {@link #startPluginActivity(Activity, DLIntent)} start
     * success
     */
    public static final int START_RESULT_SUCCESS = 0;

    /**
     * return value of {@link #startPluginActivity(Activity, DLIntent)} package
     * not found
     */
    public static final int START_RESULT_NO_PKG = 1;

    /**
     * return value of {@link #startPluginActivity(Activity, DLIntent)} class
     * not found
     */
    public static final int START_RESULT_NO_CLASS = 2;

    /**
     * return value of {@link #startPluginActivity(Activity, DLIntent)} class
     * type error
     */
    public static final int START_RESULT_TYPE_ERROR = 3;

    private static DLPluginManager sInstance;
    private Context mContext;
    /**
     * key 为包名，value 为表示插件信息的DLPluginPackage，存储已经加载过的插件信息
     */
    private final HashMap<String, DLPluginPackage> mPackagesHolder = new HashMap<String, DLPluginPackage>();

    /**
     * 默认是FROM_INTERNAL（从内部启动），如果执行了loadApk方法就会被赋值为FROM_EXTERNAL（从外部启动，也就插件）
     */
    private int mFrom = DLConstants.FROM_INTERNAL;

    /**
     * 为插件 Native Library 拷贝到宿主中后的存放目录路径
     */
    private String mNativeLibDir = null;

    private int mResult;

    private DLPluginManager(Context context) {
        mContext = context.getApplicationContext();
        mNativeLibDir = mContext.getDir("pluginlib", Context.MODE_PRIVATE).getAbsolutePath();
    }

    public static DLPluginManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (DLPluginManager.class) {
                if (sInstance == null) {
                    sInstance = new DLPluginManager(context);
                }
            }
        }

        return sInstance;
    }

    /**
     * 加载apk。需要在第一次启动plugin的activity前执行这个方法.<br/>
     * NOTE : 必须是由宿主程序来调用.
     * 
     * @param dexPath
     */
    public DLPluginPackage loadApk(String dexPath) {
        // when loadApk is called by host apk, we assume that plugin is invoked
        // by host.
        return loadApk(dexPath, true);
    }

    /**
     * 载插件 Apk
     * @param dexPath
     *            plugin path 参数 dexPath 为插件的文件路径
     * @param hasSoLib
     *            hasSoLib 表示插件是否含有 so 库
     * @return
     */
    public DLPluginPackage loadApk(final String dexPath, boolean hasSoLib) {
        mFrom = DLConstants.FROM_EXTERNAL;

        PackageInfo packageInfo = mContext.getPackageManager().getPackageArchiveInfo(dexPath,
                PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES);
        if (packageInfo == null) {
            return null;
        }

        DLPluginPackage pluginPackage = preparePluginEnv(packageInfo, dexPath);
        if (hasSoLib) {
            copySoLib(dexPath);
        }

        return pluginPackage;
    }

    /**
     * 加载插件及其资源, has DexClassLoader, Resources, and so on.
     * 
     * @param packageInfo
     * @param dexPath
     * @return
     */
    private DLPluginPackage preparePluginEnv(PackageInfo packageInfo, String dexPath) {

        DLPluginPackage pluginPackage = mPackagesHolder.get(packageInfo.packageName);
        if (pluginPackage != null) {
            return pluginPackage;
        }

        //调用createDexClassLoader(…)、createAssetManager(…)、createResources(…)函数完成相应初始化部分
        DexClassLoader dexClassLoader = createDexClassLoader(dexPath);
        AssetManager assetManager = createAssetManager(dexPath);
        Resources resources = createResources(assetManager);
        // create pluginPackage
        pluginPackage = new DLPluginPackage(dexClassLoader, resources, packageInfo);
        mPackagesHolder.put(packageInfo.packageName, pluginPackage);
        return pluginPackage;
    }

    private String dexOutputPath;

    /**
     * 利用DexClassLoader加载插件
     * @param dexPath 插件的路径
     * @return
     */
    private DexClassLoader createDexClassLoader(String dexPath) {
        File dexOutputDir = mContext.getDir("dex", Context.MODE_PRIVATE);
        dexOutputPath = dexOutputDir.getAbsolutePath();
        DexClassLoader loader = new DexClassLoader(dexPath, dexOutputPath, mNativeLibDir, mContext.getClassLoader());
        return loader;
    }

    /**
     * <font color="green">创建 AssetManager，加载插件资源</font> <br/>
     * 在 Android 中，资源是通过 R.java 中的 id 来调用访问的。但是实现插件化之后，宿主是无法通过 R 文件访问插件的资源，
     * 所以这里使用反射来生成属于插件的AssetManager，并利用addAssetPath函数加载插件资源。<br/>
     * <font color="red">AssetManager 的无参构造函数以及addAssetPath函数都被hide了，通过反射调用</font>
     * @param dexPath
     * @return
     */
    private AssetManager createAssetManager(String dexPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, dexPath);
            return assetManager;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public DLPluginPackage getPackage(String packageName) {
        return mPackagesHolder.get(packageName);
    }

    /**
     *  <font color="green">利用<strong>AssetManager中已经加载的资源创建Resources</strong>，代理组件中会从这个Resources中读取资源。</font> <br/>
     *
     * @param assetManager
     * @return
     */
    private Resources createResources(AssetManager assetManager) {
        Resources superRes = mContext.getResources();
        Resources resources = new Resources(assetManager, superRes.getDisplayMetrics(), superRes.getConfiguration());
        return resources;
    }

    /**
     * 调用SoLibManager拷贝 so 库到 Native Library 目录.
     * 
     * @param dexPath
     */
    private void copySoLib(String dexPath) {
        // TODO: copy so lib async will lead to bugs maybe, waiting for
        // resolved later.

        // TODO : use wait and signal is ok ? that means when copying the
        // .so files, the main thread will enter waiting status, when the
        // copy is done, send a signal to the main thread.
        // new Thread(new CopySoRunnable(dexPath)).start();

        SoLibManager.getSoLoader().copyPluginSoLib(mContext, dexPath, mNativeLibDir);
    }

    /**
     * <font color="green"><strong>启动插件 Activity，会直接调用startPluginActivityForResult(…)函数</strong></font> <br/>
     * 插件自己内部 Activity 启动依然是调用Context#startActivity(…)方法。
     */
    public int startPluginActivity(Context context, DLIntent dlIntent) {
        return startPluginActivityForResult(context, dlIntent, -1);
    }

    /**
     * 启动插件 Activity
     * @param context
     * @param dlIntent
     * @param requestCode
     * @return One of below: {@link #START_RESULT_SUCCESS}
     *         {@link #START_RESULT_NO_PKG} {@link #START_RESULT_NO_CLASS}
     *         {@link #START_RESULT_TYPE_ERROR}
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public int startPluginActivityForResult(Context context, DLIntent dlIntent, int requestCode) {
        // 1. 判断是否为插件中的activity，否则直接启动
        if (mFrom == DLConstants.FROM_INTERNAL) {
            dlIntent.setClassName(context, dlIntent.getPluginClass());
            performStartActivityForResult(context, dlIntent, requestCode);
            return DLPluginManager.START_RESULT_SUCCESS;
        }

        // 2. 根据packageName从mPackagesHolder得到插件信息
        String packageName = dlIntent.getPluginPackage();
        if (TextUtils.isEmpty(packageName)) {
            throw new NullPointerException("disallow null packageName.");
        }

        DLPluginPackage pluginPackage = mPackagesHolder.get(packageName);
        if (pluginPackage == null) {
            return START_RESULT_NO_PKG;
        }

        // 3. 得到待启动activity全路径，并用插件ClassLoader加载该类
        final String className = getPluginActivityFullPath(dlIntent, pluginPackage);
        Class<?> clazz = loadPluginClass(pluginPackage.classLoader, className);
        if (clazz == null) {
            return START_RESULT_NO_CLASS;
        }

        // 根据类名获取要启动的代理activity
        Class<? extends Activity> activityClass = getProxyActivityClass(clazz);
        if (activityClass == null) {
            return START_RESULT_TYPE_ERROR;
        }

        // 设置intent参数用原生方式启动代理activity，添加额外参数告诉代理activity初始化哪个插件activity
        dlIntent.putExtra(DLConstants.EXTRA_CLASS, className);
        dlIntent.putExtra(DLConstants.EXTRA_PACKAGE, packageName);
        dlIntent.setClass(mContext, activityClass);
        performStartActivityForResult(context, dlIntent, requestCode);
        return START_RESULT_SUCCESS;
    }

    public int startPluginService(final Context context, final DLIntent dlIntent) {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            dlIntent.setClassName(context, dlIntent.getPluginClass());
            context.startService(dlIntent);
            return DLPluginManager.START_RESULT_SUCCESS;
        }

        fetchProxyServiceClass(dlIntent, new OnFetchProxyServiceClass() {
            @Override
            public void onFetch(int result, Class<? extends Service> proxyServiceClass) {
                // TODO Auto-generated method stub
                if (result == START_RESULT_SUCCESS) {
                    dlIntent.setClass(context, proxyServiceClass);
                    // start代理Service
                    context.startService(dlIntent);
                }
                mResult = result;
            }
        });
        
        return mResult;
    }
    
    public int stopPluginService(final Context context, final DLIntent dlIntent) {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            dlIntent.setClassName(context, dlIntent.getPluginClass());
            context.stopService(dlIntent);
            return DLPluginManager.START_RESULT_SUCCESS;
        }
        
        fetchProxyServiceClass(dlIntent, new OnFetchProxyServiceClass() {
            @Override
            public void onFetch(int result, Class<? extends Service> proxyServiceClass) {
                // TODO Auto-generated method stub
                if (result == START_RESULT_SUCCESS) {
                    dlIntent.setClass(context, proxyServiceClass);
                    // stop代理Service
                    context.stopService(dlIntent);
                }
                mResult = result;
            }
        });
        
        return mResult;
    }

    public int bindPluginService(final Context context, final DLIntent dlIntent, final ServiceConnection conn,
            final int flags) {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            dlIntent.setClassName(context, dlIntent.getPluginClass());
            context.bindService(dlIntent, conn, flags);
            return DLPluginManager.START_RESULT_SUCCESS;
        }

        fetchProxyServiceClass(dlIntent, new OnFetchProxyServiceClass() {
            @Override
            public void onFetch(int result, Class<? extends Service> proxyServiceClass) {
                // TODO Auto-generated method stub
                if (result == START_RESULT_SUCCESS) {
			        dlIntent.setClass(context, proxyServiceClass);
                    // Bind代理Service
                    context.bindService(dlIntent, conn, flags);
                }
                mResult = result;
            }
        });

        return mResult;
    }

    public int unBindPluginService(final Context context, DLIntent dlIntent, final ServiceConnection conn) {
        if (mFrom == DLConstants.FROM_INTERNAL) {
            context.unbindService(conn);
            return DLPluginManager.START_RESULT_SUCCESS;
        }

        fetchProxyServiceClass(dlIntent, new OnFetchProxyServiceClass() {
            @Override
            public void onFetch(int result, Class<? extends Service> proxyServiceClass) {
                // TODO Auto-generated method stub
                if (result == START_RESULT_SUCCESS) {
                    // unBind代理Service
                    context.unbindService(conn);
                }
                mResult = result;
            }
        });
        return mResult;

    }

    /**
     * 获取代理ServiceClass
     * @param dlIntent
     * @param fetchProxyServiceClass
     */
    private void fetchProxyServiceClass(DLIntent dlIntent, OnFetchProxyServiceClass fetchProxyServiceClass) {
        String packageName = dlIntent.getPluginPackage();
        if (TextUtils.isEmpty(packageName)) {
            throw new NullPointerException("disallow null packageName.");
        }
        DLPluginPackage pluginPackage = mPackagesHolder.get(packageName);
        if (pluginPackage == null) {
            fetchProxyServiceClass.onFetch(START_RESULT_NO_PKG, null);
            return;
        }

        // 获取要启动的Service的全名
        String className = dlIntent.getPluginClass();
        Class<?> clazz = loadPluginClass(pluginPackage.classLoader, className);
        if (clazz == null) {
            fetchProxyServiceClass.onFetch(START_RESULT_NO_CLASS, null);
            return;
        }

        Class<? extends Service> proxyServiceClass = getProxyServiceClass(clazz);
        if (proxyServiceClass == null) {
            fetchProxyServiceClass.onFetch(START_RESULT_TYPE_ERROR, null);
            return;
        }

        // put extra data
        dlIntent.putExtra(DLConstants.EXTRA_CLASS, className);
        dlIntent.putExtra(DLConstants.EXTRA_PACKAGE, packageName);
        fetchProxyServiceClass.onFetch(START_RESULT_SUCCESS, proxyServiceClass);
    }

    // zhangjie1980 重命名 loadPluginActivityClass -> loadPluginClass

    /**
     * 用指定的ClassLoader加载指定的类
     * @param classLoader
     * @param className
     * @return
     */
    private Class<?> loadPluginClass(ClassLoader classLoader, String className) {
        Class<?> clazz = null;
        try {
            clazz = Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return clazz;
    }

    /**
     * 由于清单文件中activity的name的写法存在 .xxxActivity 的写法，这个方法是为了补全这种情况下类全称
     * @param dlIntent
     * @param pluginPackage
     * @return
     */
    private String getPluginActivityFullPath(DLIntent dlIntent, DLPluginPackage pluginPackage) {
        String className = dlIntent.getPluginClass();
        className = (className == null ? pluginPackage.defaultActivity : className);
        if (className.startsWith(".")) {
            className = dlIntent.getPluginPackage() + className;
        }
        return className;
    }

    /**
     * get the proxy activity class, the proxy activity will delegate the plugin
     * activity <br/>
     * <font>获取代理activity类，这个代理activity将作为插件activity的委派代表</font>
     * @param clazz
     *            target activity's class
     * @return
     */
    private Class<? extends Activity> getProxyActivityClass(Class<?> clazz) {
        Class<? extends Activity> activityClass = null;
        // 判断插件里面的activity是继承自DLBasePluginActivity，还是DLBasePluginFragmentActivity
        if (DLBasePluginActivity.class.isAssignableFrom(clazz)) {
            activityClass = DLProxyActivity.class;
        } else if (DLBasePluginFragmentActivity.class.isAssignableFrom(clazz)) {
            activityClass = DLProxyFragmentActivity.class;
        }

        return activityClass;
    }

    private Class<? extends Service> getProxyServiceClass(Class<?> clazz) {
        Class<? extends Service> proxyServiceClass = null;
        if (DLBasePluginService.class.isAssignableFrom(clazz)) {
            proxyServiceClass = DLProxyService.class;
        }
        // 后续可能还有IntentService，待补充

        return proxyServiceClass;
    }

    private void performStartActivityForResult(Context context, DLIntent dlIntent, int requestCode) {
        Log.d(TAG, "launch " + dlIntent.getPluginClass());
        if (context instanceof Activity) {
            ((Activity) context).startActivityForResult(dlIntent, requestCode);
        } else {
            context.startActivity(dlIntent);
        }
    }

    private interface OnFetchProxyServiceClass {
        public void onFetch(int result, Class<? extends Service> proxyServiceClass);
    }

}
