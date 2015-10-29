package com.ryg.dynamicload.sample.mainhost;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.ryg.dynamicload.internal.DLIntent;
import com.ryg.dynamicload.internal.DLPluginManager;
import com.ryg.utils.DLUtils;

public class MainActivity extends Activity implements OnItemClickListener {

    public static final String FROM = "extra.from";
    public static final int FROM_INTERNAL = 0;
    public static final int FROM_EXTERNAL = 1;

    private ArrayList<PluginItem> mPluginItems = new ArrayList<PluginItem>();
    private PluginAdapter mPluginAdapter;

    private ListView mListView;
    private TextView mNoPluginTextView;
    
    private ServiceConnection mConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initData();
    }

    /**
     * 界面初始化
     */
    private void initView() {
        mPluginAdapter = new PluginAdapter(this);
        mListView = (ListView) findViewById(R.id.plugin_list);
        mNoPluginTextView = (TextView)findViewById(R.id.no_plugin);
    }

    /**
     * 数据初始化
     */
    private void initData() {
        String pluginFolder = Environment.getExternalStorageDirectory() + "/DynamicLoadHost";
        File file = new File(pluginFolder);
        File[] plugins = file.listFiles();

        // 没有plugin处理
        if (plugins == null || plugins.length == 0) {
            mNoPluginTextView.setVisibility(View.VISIBLE);
            return;
        }

        // 有plugin处理
        for (File plugin : plugins) {
            PluginItem item = new PluginItem();
            item.setPluginPath(plugin.getAbsolutePath());
            item.setPackageInfo(DLUtils.getPackageInfo(this, item.getPluginPath()));
            if (item.getPackageInfo().activities != null && item.getPackageInfo().activities.length > 0) {
                // 设置Main Activity
                // 感觉这种方法会更准一点：http://stackoverflow.com/questions/13027374/get-the-launcher-activity-name-of-an-android-application
                item.setLauncherActivityName(item.getPackageInfo().activities[0].name);
            }
            if (item.getPackageInfo().services != null && item.getPackageInfo().services.length > 0) {
                item.setLauncherServiceName(item.getPackageInfo().services[0].name);
            }
            mPluginItems.add(item);
            DLPluginManager.getInstance(this).loadApk(item.getPluginPath());
        }
        mPluginAdapter.setData(mPluginItems);

        // 给listview设置adapter
        mListView.setAdapter(mPluginAdapter);
        mListView.setOnItemClickListener(this);
        mPluginAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_settings:
            DLUtils.showDialog(this, getString(R.string.action_about), getString(R.string.introducation));
            break;

        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        PluginItem item = mPluginItems.get(position);
        DLPluginManager pluginManager = DLPluginManager.getInstance(this);
        // 启动选中Plugin的Launcher Activity
        pluginManager.startPluginActivity(this, new DLIntent(item.getPackageInfo().packageName, item.getLauncherActivityName()));
        
        //如果存在Service则调用起Service
        if (item.getLauncherServiceName() != null) {
            //startService
	        DLIntent intent = new DLIntent(item.getPackageInfo().packageName, item.getLauncherServiceName());
	        //startService
//	        pluginManager.startPluginService(this, intent); 
	        
	        //bindService
//	        pluginManager.bindPluginService(this, intent, mConnection = new ServiceConnection() {
//                public void onServiceDisconnected(ComponentName name) {
//                }
//                
//                public void onServiceConnected(ComponentName name, IBinder binder) {
//                    int sum = ((ITestServiceInterface)binder).sum(5, 5);
//                    Log.e("MainActivity", "onServiceConnected sum(5 + 5) = " + sum);
//                }
//            }, Context.BIND_AUTO_CREATE);
        }
        
    }
    
    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        if (mConnection != null) {
	        this.unbindService(mConnection);
        }
    }

}
